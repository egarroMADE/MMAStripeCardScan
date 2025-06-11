package com.mademediacorp.mmastripecardscan.cardscan.result

import com.mademediacorp.mmastripecardscan.framework.MachineState
import com.mademediacorp.mmastripecardscan.framework.util.ItemCounter
import com.mademediacorp.mmastripecardscan.payment.ml.SSDOcr
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

internal sealed class MainLoopState(timeSource: TimeSource) : MachineState(timeSource) {

    companion object {
        /**
         * The duration after which the scan will reset if no card or DNI is visible.
         */
        val NO_CARD_NOR_DNI_VISIBLE_DURATION = 5.seconds

        /**
         * The maximum duration for which to search for a card number.
         */
        val OCR_SEARCH_DURATION = 10.seconds

        /**
         * Once this number of frames with matching card numbers are found, stop looking for card
         * numbers.
         */
        const val DESIRED_OCR_AGREEMENT = 3
    }

    internal abstract suspend fun consumeTransition(
        transition: SSDOcr.Prediction
    ): MainLoopState

    class Initial(timeSource: TimeSource) : MainLoopState(timeSource) {
        override suspend fun consumeTransition(
            transition: SSDOcr.Prediction
        ): MainLoopState = if (transition.pan.isNullOrEmpty() && !transition.dni.isNullOrEmpty()) {
            OcrFound(
                timeSource = timeSource,
                value = transition.dni
            )
        } else if (transition.dni.isNullOrEmpty() && !transition.pan.isNullOrEmpty()) {
            OcrFound(
                timeSource = timeSource,
                value = transition.pan
            )
        } else {
            this
        }
    }

    class OcrFound private constructor(
        timeSource: TimeSource,
        private val valueCounter: ItemCounter<String>
    ) : MainLoopState(timeSource) {

        internal constructor(
            timeSource: TimeSource,
            value: String
        ) : this(
            timeSource,
            ItemCounter(value)
        )

        private val mostLikelyValue: String
            get() = valueCounter.getHighestCountItem().second

        private var lastValueVisible = TimeSource.Monotonic.markNow()

        private fun highestValueCount() = valueCounter.getHighestCountItem().first
        private fun isOcrSatisfied() = highestValueCount() >= DESIRED_OCR_AGREEMENT
        private fun isTimedOut() = reachedStateAt.elapsedNow() > OCR_SEARCH_DURATION
        private fun noDataVisible() = lastValueVisible.elapsedNow() > NO_CARD_NOR_DNI_VISIBLE_DURATION

        override suspend fun consumeTransition(
            transition: SSDOcr.Prediction
        ): MainLoopState {
            val transitionPan = transition.pan
            val transitionDNI = transition.dni

            if (!transitionPan.isNullOrEmpty()) {
                valueCounter.countItem(transitionPan)
            }
            if (!transitionDNI.isNullOrEmpty()) {
                valueCounter.countItem(transitionDNI)
            }
            lastValueVisible = TimeSource.Monotonic.markNow()

            val value = mostLikelyValue

            return when {
                isOcrSatisfied() || isTimedOut() ->
                    Finished(
                        timeSource = timeSource,
                        value = value,
                        transition.recognitionMethod
                    )
                noDataVisible() ->
                    Initial(timeSource)
                else -> this
            }
        }
    }

    class Finished(timeSource: TimeSource, val value: String, val method: SSDOcr.Prediction.RecognitionMethod) : MainLoopState(timeSource) {
        override suspend fun consumeTransition(
            transition: SSDOcr.Prediction
        ): MainLoopState = this
    }
}
