package com.mademediacorp.mmastripecardscan.cardscan

import android.util.Log
import com.mademediacorp.mmastripecardscan.cardscan.DurationProvider
import com.mademediacorp.mmastripecardscan.scanui.CancellationReason
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.DurationUnit

internal class DefaultCardScanEventsReporter @Inject constructor(
    private val durationProvider: DurationProvider,
    private val cardScanConfiguration: CardScanConfiguration
) : CardScanEventsReporter {
    override fun scanStarted() {
        durationProvider.start(DurationProvider.Key.CardScan)
        fireEvent(
            eventName = "cardscan_scan_started"
        )
    }

    override fun scanSucceeded() {
        val duration = durationProvider.end(DurationProvider.Key.CardScan)
        fireEvent(
            eventName = "cardscan_success",
            additionalParams = durationInSecondsFromStart(duration)
        )
    }

    override fun scanFailed(error: Throwable?) {
        val duration = durationProvider.end(DurationProvider.Key.CardScan)
        val params = error?.let {
            mapOf("error_message" to (error?.message ?: "Unknown error"))
        } ?: emptyMap()
        fireEvent(
            eventName = "cardscan_failed",
            additionalParams = durationInSecondsFromStart(duration) + params
        )
    }

    override fun scanCancelled(reason: CancellationReason) {
        val duration = durationProvider.end(DurationProvider.Key.CardScan)
        fireEvent(
            eventName = "cardscan_cancel",
            additionalParams = durationInSecondsFromStart(duration) + mapOf(
                "cancellation_reason" to reason.analyticsReason()
            )
        )
    }

    private fun fireEvent(eventName: String, additionalParams: Map<String, Any> = emptyMap()) {
        val fullParams = additionalParams.toMutableMap()
        cardScanConfiguration.elementsSessionId?.let {
            fullParams["elements_session_id"] = it
        }

        Log.d("CardScanAnalytics", "Event: $eventName, Params: $fullParams")
        // Optionally send to Firebase, Sentry, or your own analytics backend
    }


    private fun durationInSecondsFromStart(duration: Duration?): Map<String, Float> {
        return duration?.let {
            mapOf("duration" to it.toDouble(DurationUnit.SECONDS).toFloat())
        } ?: emptyMap()
    }

    private fun CancellationReason.analyticsReason(): String {
        return when (this) {
            CancellationReason.Back -> "back"
            CancellationReason.CameraPermissionDenied -> "camera_permission_denied"
            CancellationReason.Closed -> "closed"
            CancellationReason.UserCannotScan -> "user_cannot_scan"
        }
    }
}
