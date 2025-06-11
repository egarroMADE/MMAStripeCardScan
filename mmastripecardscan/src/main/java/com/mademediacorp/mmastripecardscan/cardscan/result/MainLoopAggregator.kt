package com.mademediacorp.mmastripecardscan.cardscan.result

import com.mademediacorp.mmastripecardscan.camera.framework.AggregateResultListener
import com.mademediacorp.mmastripecardscan.camera.framework.ResultAggregator
import com.mademediacorp.mmastripecardscan.cardscan.result.MainLoopAggregator.FinalResult
import com.mademediacorp.mmastripecardscan.cardscan.result.MainLoopAggregator.InterimResult
import com.mademediacorp.mmastripecardscan.payment.ml.SSDOcr
import kotlin.time.TimeSource

/**
 * Aggregate results from the main loop. Each frame will trigger an [InterimResult] to the
 * [listener]. Once the [MainLoopState.Finished] state is reached, a [FinalResult] will be sent to
 * the [listener].
 *
 * This aggregator is a state machine. The full list of possible states are subclasses of
 * [MainLoopState].
 */
internal class MainLoopAggregator(
    listener: AggregateResultListener<InterimResult, FinalResult>
) : ResultAggregator<
    SSDOcr.Input,
    MainLoopState,
    SSDOcr.Prediction,
    InterimResult,
    FinalResult
    >(
    listener = listener,
    initialState = MainLoopState.Initial(TimeSource.Monotonic)
) {

    internal data class FinalResult(
        val value: String,
        val method: SSDOcr.Prediction.RecognitionMethod
    ) {
        override fun toString(): String {
            return method.toString()  // This will return "TENSORFLOW" or "ML_KIT"
        }
    }

    internal data class InterimResult(
        val analyzerResult: SSDOcr.Prediction,
        val frame: SSDOcr.Input,
        val state: MainLoopState
    )

    override suspend fun aggregateResult(
        frame: SSDOcr.Input,
        result: SSDOcr.Prediction
    ): Pair<InterimResult, FinalResult?> {
        val previousState = state
        val currentState = previousState.consumeTransition(result)

        state = currentState

        val interimResult = InterimResult(
            analyzerResult = result,
            frame = frame,
            state = currentState
        )

        return if (currentState is MainLoopState.Finished) {
            interimResult to FinalResult(currentState.value, currentState.method)
        } else {
            interimResult to null
        }
    }
}
