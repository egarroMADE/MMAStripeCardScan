package com.mademediacorp.mmastripecardscan.cardscan

import android.annotation.SuppressLint
import android.app.Activity
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.PointF
import android.os.Bundle
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.core.view.updateMargins
import androidx.fragment.app.setFragmentResult
import com.mademediacorp.mmastripecardscan.camera.CameraAdapter
import com.mademediacorp.mmastripecardscan.camera.CameraErrorListener
import com.mademediacorp.mmastripecardscan.camera.CameraPreviewImage
import com.mademediacorp.mmastripecardscan.camera.scanui.ScanErrorListener
import com.mademediacorp.mmastripecardscan.camera.scanui.SimpleScanStateful
import com.mademediacorp.mmastripecardscan.camera.scanui.ViewFinderBackground
import com.mademediacorp.mmastripecardscan.camera.scanui.util.asRect
import com.mademediacorp.mmastripecardscan.camera.scanui.util.startAnimation
import com.mademediacorp.mmastripecardscan.R
import com.mademediacorp.mmastripecardscan.camera.getScanCameraAdapter
import com.mademediacorp.mmastripecardscan.cardscan.exception.UnknownScanException
import com.mademediacorp.mmastripecardscan.cardscan.result.MainLoopAggregator
import com.mademediacorp.mmastripecardscan.cardscan.result.MainLoopState
import com.mademediacorp.mmastripecardscan.databinding.StripeFragmentCardscanBinding
import com.mademediacorp.mmastripecardscan.payment.card.ScannedCard
import com.mademediacorp.mmastripecardscan.scanui.CancellationReason
import com.mademediacorp.mmastripecardscan.scanui.ScanFragment
import com.mademediacorp.mmastripecardscan.scanui.util.getColorByRes
import com.mademediacorp.mmastripecardscan.scanui.util.getFloatResource
import com.mademediacorp.mmastripecardscan.scanui.util.setVisible
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.roundToInt
//import com.mademediacorp.mmastripecardscan.camera.R as CameraR
import com.mademediacorp.mmastripecardscan.R as CameraR

private val MINIMUM_RESOLUTION = Size(1067, 600) // minimum size of OCR
const val CARD_SCAN_FRAGMENT_REQUEST_KEY = "CardScanRequestKey"
const val CARD_SCAN_FRAGMENT_BUNDLE_KEY = "CardScanBundleKey"

class CardScanFragment : ScanFragment(), SimpleScanStateful<CardScanState> {

    override val minimumAnalysisResolution = MINIMUM_RESOLUTION

    private lateinit var viewBinding: StripeFragmentCardscanBinding

    override val instructionsText: TextView by lazy { viewBinding.instructions }

    override val previewFrame: ViewGroup by lazy { viewBinding.cameraView.previewFrame }

    private val viewFinderWindow: View by lazy {
        viewBinding.cameraView.viewFinderWindowView
    }

    private val viewFinderBorder: ImageView by lazy {
        viewBinding.cameraView.viewFinderBorderView
    }

    private val viewFinderBackground: ViewFinderBackground by lazy {
        viewBinding.cameraView.viewFinderBackgroundView
    }

    override var scanState: CardScanState? = CardScanState.NotFound

    override var scanStatePrevious: CardScanState? = null

    override val scanErrorListener: ScanErrorListener = ScanErrorListener()
    override val cameraAdapterBuilder: (
        Activity,
        ViewGroup,
        Size,
        CameraErrorListener
    ) -> CameraAdapter<CameraPreviewImage<Bitmap>> = ::getScanCameraAdapter

    /**
     * The listener which handles results from the scan.
     */
    override val resultListener: CardScanResultListener =
        object : CardScanResultListener {

            override fun cardScanComplete(card: ScannedCard) {
                setFragmentResult(
                    CARD_SCAN_FRAGMENT_REQUEST_KEY,
                    bundleOf(
                        CARD_SCAN_FRAGMENT_BUNDLE_KEY to CardScanSheetResult.Completed(
                            ScannedCard(
                                value = card.value,
                                recognitionMethod = card.recognitionMethod
                            )
                        )
                    )
                )
                closeScanner()
            }

            override fun userCanceled(reason: CancellationReason) {
                setFragmentResult(
                    CARD_SCAN_FRAGMENT_REQUEST_KEY,
                    bundleOf(
                        CARD_SCAN_FRAGMENT_BUNDLE_KEY to CardScanSheetResult.Canceled(reason)
                    )
                )
            }

            override fun failed(cause: Throwable?) {
                setFragmentResult(
                    CARD_SCAN_FRAGMENT_REQUEST_KEY,
                    bundleOf(
                        CARD_SCAN_FRAGMENT_BUNDLE_KEY to
                            CardScanSheetResult.Failed(
                                cause ?: UnknownScanException()
                            )
                    )
                )
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
                    activity?.let { cameraAdapter.unbindFromLifecycle(it) }
                    resultListener.cardScanComplete(ScannedCard(result.value, result.method.toString()))
                    closeScanner()
                }.let { }
            }

            /**
             * An interim result was received from the result aggregator.
             */
            override suspend fun onInterimResult(
                result: MainLoopAggregator.InterimResult
            ) = launch(Dispatchers.Main) {
                when (result.state) {
                    is MainLoopState.Initial -> changeScanState(CardScanState.NotFound)
                    is MainLoopState.OcrFound -> changeScanState(CardScanState.Found)
                    is MainLoopState.Finished -> changeScanState(CardScanState.Correct)
                }
            }.let { }

            override suspend fun onReset() = launch(Dispatchers.Main) {
                changeScanState(CardScanState.NotFound)
            }.let { }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        viewBinding = StripeFragmentCardscanBinding.inflate(inflater, container, false)

        setupViewFinderConstraints()

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
                    e.x + viewFinderWindow.left,
                    e.y + viewFinderWindow.top
                )
            )
            true
        }

        displayState(requireNotNull(scanState), scanStatePrevious)
        return viewBinding.root
    }

    override fun onResume() {
        super.onResume()
        scanState = CardScanState.NotFound
    }

    override fun onDestroy() {
        scanFlow.cancelFlow()
        super.onDestroy()
    }

    /**
     * Set up viewFinderWindowView and viewFinderBorderView centered with predefined margins
     */
    private fun setupViewFinderConstraints() {
        val screenSize = Resources.getSystem().displayMetrics.let {
            Size(it.widthPixels, it.heightPixels)
        }

        val viewFinderMargin = (
            min(screenSize.width, screenSize.height) *
                (context?.getFloatResource(CameraR.dimen.stripeViewFinderMargin) ?: 0F)
            ).roundToInt()

        listOf(viewFinderWindow, viewFinderBorder).forEach { view ->
            (view.layoutParams as ViewGroup.MarginLayoutParams)
                .updateMargins(
                    viewFinderMargin,
                    viewFinderMargin,
                    viewFinderMargin,
                    viewFinderMargin
                )
        }

        viewFinderBackground.setViewFinderRect(viewFinderWindow.asRect())
    }

    override fun onFlashSupported(supported: Boolean) {
        viewBinding.torchButton.setVisible(supported)
    }

    override fun onSupportsMultipleCameras(supported: Boolean) {
        viewBinding.swapCameraButton.setVisible(supported)
    }

    /**
     * Prepare to start the camera. Once the camera is ready, [onCameraReady] must be called.
     */
    override fun prepareCamera(onCameraReady: () -> Unit) {
        previewFrame.post {
            viewFinderBackground
                .setViewFinderRect(viewFinderWindow.asRect())
            onCameraReady()
        }
    }

    /**
     * Once the camera stream is available, start processing images.
     */
    override suspend fun onCameraStreamAvailable(cameraStream: Flow<CameraPreviewImage<Bitmap>>) {
        context?.let {
            scanFlow.startFlow(
                context = it,
                imageStream = cameraStream,
                viewFinder = viewFinderWindow.asRect(),
                lifecycleOwner = this,
                coroutineScope = this,
                parameters = null,
                errorHandler = { e ->
                    scanErrorListener.onResultFailure(e)
                }
            )
        }
    }

    /**
     * Called when the flashlight state has changed.
     */
    override fun onFlashlightStateChanged(flashlightOn: Boolean) {}

    override fun displayState(newState: CardScanState, previousState: CardScanState?) {
        when (newState) {
            is CardScanState.NotFound, CardScanState.Found -> {
                context?.let {
                    viewFinderBackground
                        .setBackgroundColor(
                            it.getColorByRes(R.color.stripeNotFoundBackground)
                        )
                }
                viewFinderWindow
                    .setBackgroundResource(R.drawable.stripe_card_background_not_found)
                viewFinderBorder
                    .startAnimation(R.drawable.stripe_paymentsheet_card_border_not_found)
            }
            is CardScanState.Correct -> {
                context?.let {
                    viewFinderBackground
                        .setBackgroundColor(
                            it.getColorByRes(R.color.stripeCorrectBackground)
                        )
                }
                viewFinderWindow
                    .setBackgroundResource(R.drawable.stripe_card_background_correct)
                viewFinderBorder.startAnimation(R.drawable.stripe_card_border_correct)
            }
        }
    }

    override fun closeScanner() {
        scanFlow.resetFlow()
        super.closeScanner()
    }
}
