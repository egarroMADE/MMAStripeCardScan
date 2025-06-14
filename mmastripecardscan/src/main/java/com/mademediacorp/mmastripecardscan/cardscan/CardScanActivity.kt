package com.mademediacorp.mmastripecardscan.cardscan

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PointF
import android.os.Bundle
import android.util.Size
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.activity.addCallback
import androidx.annotation.RestrictTo
import androidx.core.os.BundleCompat
import com.mademediacorp.mmastripecardscan.camera.CameraPreviewImage
import com.mademediacorp.mmastripecardscan.camera.scanui.ScanErrorListener
import com.mademediacorp.mmastripecardscan.camera.scanui.ScanState
import com.mademediacorp.mmastripecardscan.camera.scanui.SimpleScanStateful
import com.mademediacorp.mmastripecardscan.camera.scanui.ViewFinderBackground
import com.mademediacorp.mmastripecardscan.camera.scanui.util.asRect
import com.mademediacorp.mmastripecardscan.camera.scanui.util.setDrawable
import com.mademediacorp.mmastripecardscan.camera.scanui.util.startAnimation
import com.mademediacorp.mmastripecardscan.R
import com.mademediacorp.mmastripecardscan.camera.getScanCameraAdapter
import com.mademediacorp.mmastripecardscan.cardscan.exception.UnknownScanException
import com.mademediacorp.mmastripecardscan.cardscan.result.MainLoopAggregator
import com.mademediacorp.mmastripecardscan.cardscan.result.MainLoopState
import com.mademediacorp.mmastripecardscan.databinding.StripeActivityCardscanBinding
import com.mademediacorp.mmastripecardscan.di.DaggerCardScanComponent
import com.mademediacorp.mmastripecardscan.payment.card.ScannedCard
import com.mademediacorp.mmastripecardscan.scanui.CancellationReason
import com.mademediacorp.mmastripecardscan.scanui.ScanActivity
import com.mademediacorp.mmastripecardscan.scanui.ScanResultListener
import com.mademediacorp.mmastripecardscan.scanui.util.getColorByRes
import com.mademediacorp.mmastripecardscan.scanui.util.hide
import com.mademediacorp.mmastripecardscan.scanui.util.setVisible
import com.mademediacorp.mmastripecardscan.scanui.util.show
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

internal const val INTENT_PARAM_REQUEST = "request"
internal const val INTENT_PARAM_RESULT = "result"

private val MINIMUM_RESOLUTION = Size(1067, 600) // minimum size of OCR

internal interface CardScanResultListener : ScanResultListener {

    /**
     * The scan completed.
     */
    fun cardScanComplete(card: ScannedCard)
}

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
sealed class CardScanState(isFinal: Boolean) : ScanState(isFinal) {
    data object NotFound : CardScanState(isFinal = false)
    data object Found : CardScanState(isFinal = false)
    data object Correct : CardScanState(isFinal = true)
}

internal class CardScanActivity : ScanActivity(), SimpleScanStateful<CardScanState> {

    @Inject
    lateinit var cardScanEventsReporter: CardScanEventsReporter

    override val minimumAnalysisResolution = MINIMUM_RESOLUTION

    // Initialize viewBinding inside onCreate()
    private lateinit var viewBinding: StripeActivityCardscanBinding

    // FIXED: Change from lazy to computed properties to avoid accessing viewBinding before initialization
    override val previewFrame: ViewGroup
        get() = viewBinding.cameraView.previewFrame

    private val viewFinderWindow: View
        get() = viewBinding.cameraView.viewFinderWindowView

    private val viewFinderBorder: ImageView
        get() = viewBinding.cameraView.viewFinderBorderView

    private val viewFinderBackground: ViewFinderBackground
        get() = viewBinding.cameraView.viewFinderBackgroundView

    override var scanState: CardScanState? = CardScanState.NotFound

    override var scanStatePrevious: CardScanState? = null

    override val scanErrorListener: ScanErrorListener = ScanErrorListener()

    override val cameraAdapterBuilder = ::getScanCameraAdapter

    // The listener which handles results from the scan.
    override val resultListener: CardScanResultListener =
        object : CardScanResultListener {

            override fun cardScanComplete(card: ScannedCard) {
                cardScanEventsReporter.scanSucceeded()
                val intent = Intent()
                    .putExtra(
                        INTENT_PARAM_RESULT,
                        CardScanSheetResult.Completed(
                            ScannedCard(
                                value = card.value,
                                recognitionMethod = card.recognitionMethod
                            )
                        )
                    )
                setResult(RESULT_OK, intent)
            }

            override fun userCanceled(reason: CancellationReason) {
                cardScanEventsReporter.scanCancelled(reason)
                val intent = Intent()
                    .putExtra(
                        INTENT_PARAM_RESULT,
                        CardScanSheetResult.Canceled(reason)
                    )
                setResult(RESULT_CANCELED, intent)
            }

            override fun failed(cause: Throwable?) {
                cardScanEventsReporter.scanFailed(cause)
                val intent = Intent()
                    .putExtra(
                        INTENT_PARAM_RESULT,
                        CardScanSheetResult.Failed(cause ?: UnknownScanException())
                    )
                setResult(RESULT_CANCELED, intent)
            }
        }

    /**
     * The flow used to scan an item.
     */
    private val scanFlow: CardScanFlow by lazy {
        object : CardScanFlow(scanErrorListener) {
            /**
             * A final result was received from the aggregator. Set the result from this activity.
             */
            override suspend fun onResult(
                result: MainLoopAggregator.FinalResult
            ) {
                launch(Dispatchers.Main) {
                    changeScanState(CardScanState.Correct)
                    cameraAdapter.unbindFromLifecycle(this@CardScanActivity)
                    resultListener.cardScanComplete(ScannedCard(result.value, result.method.toString()))
                    closeScanner()
                }
            }

            /**
             * An interim result was received from the result aggregator.
             */
            override suspend fun onInterimResult(
                result: MainLoopAggregator.InterimResult
            ) {
                launch(Dispatchers.Main) {
                    when (result.state) {
                        is MainLoopState.Initial -> changeScanState(CardScanState.NotFound)
                        is MainLoopState.OcrFound -> changeScanState(CardScanState.Found)
                        is MainLoopState.Finished -> changeScanState(CardScanState.Correct)
                    }
                }
            }

            override suspend fun onReset() {
                launch(Dispatchers.Main) {
                    changeScanState(CardScanState.NotFound)
                }
            }

        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        // Initialize the viewBinding FIRST, before calling super.onCreate()
        viewBinding = StripeActivityCardscanBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        super.onCreate(savedInstanceState)

        val cardScanSheetParams = intent.extras?.let {
            BundleCompat.getParcelable(it, INTENT_PARAM_REQUEST, CardScanSheetParams::class.java)
        }
        val cardScanConfiguration = cardScanSheetParams?.cardScanConfiguration
            ?: CardScanConfiguration(elementsSessionId = null)

        DaggerCardScanComponent.builder()
            .application(this.application)
            .configuration(cardScanConfiguration)
            .build()
            .inject(this)

        onBackPressedDispatcher.addCallback {
            resultListener.userCanceled(CancellationReason.Back)
            closeScanner()
        }

        viewBinding.closeButton.setOnClickListener {
            userClosedScanner()
        }
        viewBinding.torchButton.setOnClickListener {
            toggleFlashlight()
        }
        viewBinding.swapCameraButton.setOnClickListener {
            toggleCamera()
        }
        viewFinderBorder.setOnTouchListener { _, e ->
            setFocus(
                PointF(
                    e.x + viewFinderBorder.left,
                    e.y + viewFinderBorder.top
                )
            )
            true
        }

        displayState(requireNotNull(scanState), scanStatePrevious)
    }

    override fun onResume() {
        super.onResume()
        scanState = CardScanState.NotFound
    }

    override fun onDestroy() {
        scanFlow.cancelFlow()
        super.onDestroy()
    }

    override fun onFlashSupported(supported: Boolean) {
        viewBinding.torchButton.setVisible(supported)
    }

    override fun onSupportsMultipleCameras(supported: Boolean) {
        viewBinding.swapCameraButton.setVisible(supported)
    }

    override fun onCameraReady() {
        previewFrame.post {
            viewFinderBackground
                .setViewFinderRect(viewFinderWindow.asRect())
            startCameraAdapter()
        }
    }

    /**
     * Once the camera stream is available, start processing images.
     */
    override suspend fun onCameraStreamAvailable(cameraStream: Flow<CameraPreviewImage<Bitmap>>) {
        cardScanEventsReporter.scanStarted()
        scanFlow.startFlow(
            context = this,
            imageStream = cameraStream,
            viewFinder = viewBinding.cameraView.viewFinderWindowView.asRect(),
            lifecycleOwner = this,
            coroutineScope = this,
            parameters = null,
            errorHandler = { e ->
                scanErrorListener.onResultFailure(e)
            }
        )
    }

    /**
     * Called when the flashlight state has changed.
     */
    override fun onFlashlightStateChanged(flashlightOn: Boolean) {
        if (flashlightOn) {
            viewBinding.torchButton.setDrawable(R.drawable.stripe_flash_on_dark)
        } else {
            viewBinding.torchButton.setDrawable(R.drawable.stripe_flash_off_dark)
        }
    }

    override fun displayState(newState: CardScanState, previousState: CardScanState?) {
        when (newState) {
            is CardScanState.NotFound -> {
                viewFinderBackground
                    .setBackgroundColor(getColorByRes(R.color.stripeNotFoundBackground))
                viewFinderWindow
                    .setBackgroundResource(R.drawable.stripe_card_background_not_found)
                viewFinderBorder.startAnimation(R.drawable.stripe_card_border_not_found)
                viewBinding.instructions.setText(R.string.stripe_card_scan_instructions)
            }
            is CardScanState.Found -> {
                viewFinderBackground
                    .setBackgroundColor(getColorByRes(R.color.stripeFoundBackground))
                viewFinderWindow
                    .setBackgroundResource(R.drawable.stripe_card_background_found)
                viewFinderBorder.startAnimation(R.drawable.stripe_card_border_found)
                viewBinding.instructions.setText(R.string.stripe_card_scan_instructions)
                viewBinding.instructions.show()
            }
            is CardScanState.Correct -> {
                viewFinderBackground
                    .setBackgroundColor(getColorByRes(R.color.stripeCorrectBackground))
                viewFinderWindow
                    .setBackgroundResource(R.drawable.stripe_card_background_correct)
                viewFinderBorder.startAnimation(R.drawable.stripe_card_border_correct)
                viewBinding.instructions.hide()
            }
        }
    }

    override fun closeScanner() {
        super.closeScanner()
    }
}