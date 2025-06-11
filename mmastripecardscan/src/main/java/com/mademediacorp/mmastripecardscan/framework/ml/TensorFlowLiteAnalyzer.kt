package com.mademediacorp.mmastripecardscan.framework.ml

import android.content.Context
import android.util.Log
import com.mademediacorp.mmastripecardscan.camera.framework.Analyzer
import com.mademediacorp.mmastripecardscan.camera.framework.AnalyzerFactory
import com.mademediacorp.mmastripecardscan.mlcore.base.InterpreterOptionsWrapper
import com.mademediacorp.mmastripecardscan.mlcore.base.InterpreterWrapper
import com.mademediacorp.mmastripecardscan.mlcore.impl.InterpreterWrapperImpl
import com.mademediacorp.mmastripecardscan.framework.FetchedData
import com.mademediacorp.mmastripecardscan.framework.Loader
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer

/**
 * A TensorFlowLite analyzer uses an [InterpreterWrapper] to analyze data.
 */
internal abstract class TensorFlowLiteAnalyzer<Input, MLInput, Output, MLOutput>(
    private val tfInterpreter: InterpreterWrapper,
) : Analyzer<Input, Any, Output>, Closeable {

    protected abstract suspend fun interpretMLOutput(data: Input, mlOutput: MLOutput): Output

    protected abstract suspend fun transformData(data: Input): MLInput

    protected abstract suspend fun executeInference(
        tfInterpreter: InterpreterWrapper,
        data: MLInput
    ): MLOutput

    override suspend fun analyze(data: Input, state: Any): Output {
        val mlInput = transformData(data)

        val mlOutput = executeInference(tfInterpreter, mlInput)

        return interpretMLOutput(data, mlOutput)
    }

    override fun close() {
        tfInterpreter.close()
    }
}

/**
 * A factory that creates tensorflow models as analyzers.
 */
internal abstract class TFLAnalyzerFactory<
    Input,
    Output,
    AnalyzerType : Analyzer<Input, Any, Output>
    >(
    val context: Context,
    private val fetchedModel: FetchedData
) : AnalyzerFactory<Input, Any, Output, AnalyzerType> {
    protected abstract val tfOptions: InterpreterOptionsWrapper

    private val loader by lazy { Loader(context) }

    private val loadModelMutex = Mutex()

    private var loadedModel: ByteBuffer? = null

    protected suspend fun createInterpreter(context: Context): InterpreterWrapper? {
        val resp = createInterpreter(fetchedModel)
        if (resp != null) {
            resp.context = context
        }
        return resp
    }

    private suspend fun createInterpreter(fetchedModel: FetchedData): InterpreterWrapper? = try {

        loadModel(fetchedModel)?.let { InterpreterWrapperImpl(it, tfOptions) }
    } catch (t: Throwable) {
        Log.e(
            LOG_TAG,
            "Error loading ${fetchedModel.modelClass} version ${fetchedModel.modelVersion}",
            t
        )
        null
    }.apply {
        if (this == null) {
            Log.w(
                LOG_TAG,
                "Unable to load ${fetchedModel.modelClass} version ${fetchedModel.modelVersion}"
            )
        }
    }

    private suspend fun loadModel(fetchedModel: FetchedData): File? =
        loadModelMutex.withLock {
            if (loadedModel != null && loadedModel is File) {
                loadedModel as File
            } else {
                val buffer = loader.loadData(fetchedModel)  // This must return ByteBuffer
                buffer?.rewind()  // Ensure buffer position is at 0
                val tempFile = buffer?.let { byteBufferToTempFile(it) }
                //loadedModel = tempFile  // Optional: cache for reuse
                tempFile
            }
        }


    companion object {
        private val LOG_TAG = TFLAnalyzerFactory::class.java.simpleName
    }

    fun byteBufferToTempFile(buffer: ByteBuffer, prefix: String = "model", suffix: String = ".tflite"): File? {
        return try {
            val tempFile = File.createTempFile(prefix, suffix)
            val outputStream = FileOutputStream(tempFile)

            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            outputStream.write(bytes)
            outputStream.close()

            tempFile
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }
}
