package com.mademediacorp.mmastripecardscan.cardscan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import androidx.lifecycle.LifecycleOwner
import com.mademediacorp.mmastripecardscan.camera.CameraPreviewImage
import com.mademediacorp.mmastripecardscan.camera.framework.AggregateResultListener
import com.mademediacorp.mmastripecardscan.camera.framework.AnalyzerLoopErrorListener
import com.mademediacorp.mmastripecardscan.camera.framework.AnalyzerPool
import com.mademediacorp.mmastripecardscan.camera.framework.ProcessBoundAnalyzerLoop
import com.mademediacorp.mmastripecardscan.camera.framework.util.centerOn
import com.mademediacorp.mmastripecardscan.camera.framework.util.minAspectRatioSurroundingSize
import com.mademediacorp.mmastripecardscan.camera.framework.util.size
import com.mademediacorp.mmastripecardscan.camera.framework.util.union
import com.mademediacorp.mmastripecardscan.camera.scanui.ScanFlow
import com.mademediacorp.mmastripecardscan.cardscan.result.MainLoopAggregator
import com.mademediacorp.mmastripecardscan.cardscan.result.MainLoopState
import com.mademediacorp.mmastripecardscan.payment.ml.SSDOcr
import com.mademediacorp.mmastripecardscan.payment.ml.SSDOcrModelManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

internal abstract class CardScanFlow(
    private val scanErrorListener: AnalyzerLoopErrorListener
) : ScanFlow<Unit?, CameraPreviewImage<Bitmap>>,
    AggregateResultListener<MainLoopAggregator.InterimResult, MainLoopAggregator.FinalResult> {

    /**
     * If this is true, do not start the flow.
     */
    private var canceled = false

    private var mainLoopAnalyzerPool: AnalyzerPool<
        SSDOcr.Input,
        Any,
        SSDOcr.Prediction
        >? = null
    private var mainLoopAggregator: MainLoopAggregator? = null
    private var mainLoop: ProcessBoundAnalyzerLoop<
        SSDOcr.Input,
        MainLoopState,
        SSDOcr.Prediction
        >? = null

    private var mainLoopJob: Job? = null

    override fun startFlow(
        context: Context,
        imageStream: Flow<CameraPreviewImage<Bitmap>>,
        viewFinder: Rect,
        lifecycleOwner: LifecycleOwner,
        coroutineScope: CoroutineScope,
        parameters: Unit?,
        errorHandler: (e: Exception) -> Unit
    ) = coroutineScope.launch(Dispatchers.Main) {
        if (canceled) {
            return@launch
        }

        mainLoopAggregator = MainLoopAggregator(
            listener = this@CardScanFlow
        ).also {
            // make this result aggregator pause and reset when the lifecycle pauses.
            it.bindToLifecycle(lifecycleOwner)

            val analyzerPool = AnalyzerPool.of(
                SSDOcr.Factory(
                    context,
                    SSDOcrModelManager.fetchModel(
                        context,
                        forImmediateUse = true,
                        isOptional = false
                    )
                )
            )
            mainLoopAnalyzerPool = analyzerPool

            mainLoop = ProcessBoundAnalyzerLoop(
                analyzerPool = analyzerPool,
                resultHandler = it,
                analyzerLoopErrorListener = scanErrorListener,
            ).apply {
                mainLoopJob = subscribeTo(
                    imageStream.map { cameraImage ->
                        SSDOcr.cameraPreviewToInput(
                            cameraImage.image,
                            minAspectRatioSurroundingSize(
                                cameraImage.viewBounds.size().union(viewFinder.size()),
                                cameraImage.image.width.toFloat() / cameraImage.image.height
                            ).centerOn(cameraImage.viewBounds),
                            viewFinder
                        )
                    },
                    coroutineScope
                )
            }
        }
    }.let { }

    /**
     * Reset the flow to the initial state, ready to be started again
     */
    internal fun resetFlow() {
        canceled = false
        cleanUp()
    }

    override fun cancelFlow() {
        canceled = true
        cleanUp()
    }

    private fun cleanUp() {
        mainLoopAggregator?.run { cancel() }
        mainLoopAggregator = null

        mainLoop?.unsubscribe()
        mainLoop = null

        mainLoopAnalyzerPool?.closeAllAnalyzers()
        mainLoopAnalyzerPool = null

        mainLoopJob?.apply { if (isActive) { cancel() } }
        mainLoopJob = null
    }
}
