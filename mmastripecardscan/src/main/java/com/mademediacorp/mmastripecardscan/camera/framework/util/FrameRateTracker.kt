package com.mademediacorp.mmastripecardscan.camera.framework.util

import android.util.Log
import com.mademediacorp.mmastripecardscan.camera.framework.time.Rate
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

internal interface FrameRateListener {
    fun onFrameRateUpdate(overallRate: Rate, instantRate: Rate)
}

/**
 * A class that tracks the rate at which frames are processed. This is useful for debugging to
 * determine how quickly a device is handling data.
 */
internal class FrameRateTracker(
    private val name: String,
    private val listener: FrameRateListener? = null,
    private val notifyInterval: Duration = 1.seconds
) {
    private var firstFrameTime: ComparableTimeMark? = null
    private var lastNotifyTime: ComparableTimeMark = TimeSource.Monotonic.markNow()

    // This is -1 so that we do not calculate a rate for the first frame
    private val totalFramesProcessed: AtomicLong = AtomicLong(-1)
    private val framesProcessedSinceLastUpdate: AtomicLong = AtomicLong(0)

    private val frameRateMutex = Mutex()

    /**
     * Calculate the current rate at which frames are being processed. If the notify interval has
     * elapsed, notify the listener of the current rate.
     */
    suspend fun trackFrameProcessed() {
        val totalFrames = totalFramesProcessed.incrementAndGet()
        val framesSinceLastUpdate = framesProcessedSinceLastUpdate.incrementAndGet()

        val lastNotifyTime = this.lastNotifyTime
        val shouldNotifyOfFrameRate = totalFrames > 0 && frameRateMutex.withLock {
            val shouldNotify = lastNotifyTime.elapsedNow() > notifyInterval
            if (shouldNotify) {
                this.lastNotifyTime = TimeSource.Monotonic.markNow()
            }
            shouldNotify
        }

        val firstFrameTime = this.firstFrameTime ?: TimeSource.Monotonic.markNow()
        this.firstFrameTime = firstFrameTime

        if (shouldNotifyOfFrameRate) {
            val overallFrameRate = Rate(totalFrames, firstFrameTime.elapsedNow())
            val instantFrameRate = Rate(framesSinceLastUpdate, lastNotifyTime.elapsedNow())

            logProcessingRate(overallFrameRate, instantFrameRate)
            listener?.onFrameRateUpdate(overallFrameRate, instantFrameRate)
            framesProcessedSinceLastUpdate.set(0)
        }
    }

    /**
     * Reset the state of the frame rate tracker.
     */
    fun reset() {
        firstFrameTime = null
        lastNotifyTime = TimeSource.Monotonic.markNow()
        totalFramesProcessed.set(0)
        framesProcessedSinceLastUpdate.set(0)
    }

    /**
     * Get the average frame rate for this device
     */
    fun getAverageFrameRate() = Rate(
        amount = totalFramesProcessed.get(),
        duration = firstFrameTime?.elapsedNow() ?: Duration.ZERO
    )

    /**
     * The processing rate has been updated. This is useful for debugging and measuring performance.
     *
     * @param overallRate: The total frame rate at which the analyzer is running
     * @param instantRate: The instantaneous frame rate at which the analyzer is running
     */
    private fun logProcessingRate(overallRate: Rate, instantRate: Rate) {
        val overallFps = if (overallRate.duration != Duration.ZERO) {
            overallRate.amount.toDouble() / overallRate.duration.inWholeSeconds
        } else {
            0.0
        }

        val instantFps = if (instantRate.duration != Duration.ZERO) {
            instantRate.amount.toDouble() / instantRate.duration.inWholeSeconds
        } else {
            0.0
        }
    }

    private companion object {
        val logTag = FrameRateTracker::class.java.simpleName
    }
}
