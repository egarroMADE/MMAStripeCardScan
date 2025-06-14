package com.mademediacorp.mmastripecardscan.camera.framework

import androidx.annotation.RestrictTo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.ComparableTimeMark
import kotlin.time.TimeSource

internal object NoAnalyzersAvailableException : Exception()

internal object AlreadySubscribedException : Exception()

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
interface AnalyzerLoopErrorListener {

    /**
     * A failure occurred during frame analysis. If this returns true, the loop will terminate. If
     * this returns false, the loop will continue to execute on new data.
     */
    fun onAnalyzerFailure(t: Throwable): Boolean

    /**
     * A failure occurred while collecting the result of frame analysis. If this returns true, the
     * loop will terminate. If this returns false, the loop will continue to execute on new data.
     */
    fun onResultFailure(t: Throwable): Boolean
}

/**
 * A loop to execute repeated analysis. The loop uses coroutines to run the [Analyzer.analyze]
 * method. If the [Analyzer] is threadsafe, multiple coroutines will be used. If not, a single
 * coroutine will be used.
 *
 * Any data enqueued while the analyzers are at capacity will be dropped.
 *
 * This will process data until the result aggregator returns true.
 *
 * Note: an analyzer loop can only be started once. Once it terminates, it cannot be restarted.
 *
 * @param analyzerPool: A pool of analyzers to use in this loop.
 * @param analyzerLoopErrorListener: An error handler for this loop
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
sealed class AnalyzerLoop<DataFrame, State, Output>(
    private val analyzerPool: AnalyzerPool<DataFrame, in State, Output>,
    private val analyzerLoopErrorListener: AnalyzerLoopErrorListener,
) : ResultHandler<DataFrame, Output, Boolean> {
    private val started = AtomicBoolean(false)
    protected var startedAt: ComparableTimeMark? = null
    private var finished: Boolean = false

    private val cancelMutex = Mutex()

    private var workerJob: Job? = null

    protected fun subscribeToFlow(
        flow: Flow<DataFrame>,
        processingCoroutineScope: CoroutineScope
    ): Job? {
        if (!started.getAndSet(true)) {
            startedAt = TimeSource.Monotonic.markNow()
        } else {
            analyzerLoopErrorListener.onAnalyzerFailure(AlreadySubscribedException)
            return null
        }

        if (analyzerPool.analyzers.isEmpty()) {
            analyzerLoopErrorListener.onAnalyzerFailure(NoAnalyzersAvailableException)
            return null
        }

        workerJob = processingCoroutineScope.launch {
            // This should be using analyzerPool.analyzers.forEach, but doing so seems to require
            // API 24. It's unclear why this won't use the kotlin.collections version of `forEach`,
            // but it's not during compile.
            for (it in analyzerPool.analyzers) {
                launch(Dispatchers.Default) {
                    startWorker(flow, it)
                }
            }
        }

        return workerJob
    }

    protected suspend fun unsubscribeFromFlow() = cancelMutex.withLock {
        workerJob?.apply { if (isActive) { cancel() } }
        workerJob = null
        started.set(false)
        finished = false
    }

    /**
     * Launch a worker coroutine that has access to the analyzer's `analyze` method and the result
     * handler
     */
    private suspend fun startWorker(
        flow: Flow<DataFrame>,
        analyzer: Analyzer<DataFrame, in State, Output>
    ) {
        flow.collect { frame ->
            try {
                val analyzerResult = analyzer.analyze(frame, getState())

                try {
                    finished = onResult(analyzerResult, frame)
                } catch (t: Throwable) {
                    handleResultFailure(t)
                }
            } catch (t: Throwable) {
                handleAnalyzerFailure(t)
            }

            if (finished) {
                unsubscribeFromFlow()
            }
        }
    }

    private suspend fun handleAnalyzerFailure(t: Throwable) {
        if (withContext(Dispatchers.Main) { analyzerLoopErrorListener.onAnalyzerFailure(t) }) {
            unsubscribeFromFlow()
        }
    }

    private suspend fun handleResultFailure(t: Throwable) {
        if (withContext(Dispatchers.Main) { analyzerLoopErrorListener.onResultFailure(t) }) {
            unsubscribeFromFlow()
        }
    }

    abstract fun getState(): State
}

/**
 * This kind of [AnalyzerLoop] will process data until the result handler indicates that it has
 * reached a terminal state and is no longer listening.
 *
 * Data can be added to a queue for processing by a camera or other producer. It will be consumed by
 * FILO. If no data is available, the analyzer pauses until data becomes available.
 *
 * If the enqueued data exceeds the allowed memory size, the bottom of the data stack will be
 * dropped and will not be processed. This alleviates memory pressure when producers are faster than
 * the consuming analyzer.
 *
 * @param analyzerPool: A pool of analyzers to use in this loop.
 * @param resultHandler: A result handler that will be called with the results from the analyzers in
 *     this loop.
 * @param analyzerLoopErrorListener: An error handler for this loop
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class ProcessBoundAnalyzerLoop<DataFrame, State, Output>(
    private val analyzerPool: AnalyzerPool<DataFrame, in State, Output>,
    private val resultHandler: StatefulResultHandler<DataFrame, out State, Output, Boolean>,
    analyzerLoopErrorListener: AnalyzerLoopErrorListener,
) : AnalyzerLoop<DataFrame, State, Output>(
    analyzerPool,
    analyzerLoopErrorListener,
) {
    /**
     * Subscribe to a flow. Loops can only subscribe to a single flow at a time.
     */
    fun subscribeTo(flow: Flow<DataFrame>, processingCoroutineScope: CoroutineScope) =
        subscribeToFlow(flow, processingCoroutineScope)

    /**
     * Unsubscribe from the flow.
     */
    fun unsubscribe() = runBlocking { unsubscribeFromFlow() }

    override suspend fun onResult(result: Output, data: DataFrame) =
        resultHandler.onResult(result, data)

    override fun getState(): State = resultHandler.state
}
