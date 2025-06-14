package com.mademediacorp.mmastripecardscan.mlcore.impl

import android.content.Context
import androidx.annotation.RestrictTo
import com.mademediacorp.mmastripecardscan.mlcore.base.InterpreterOptionsWrapper
import com.mademediacorp.mmastripecardscan.mlcore.base.InterpreterWrapper
import org.tensorflow.lite.Interpreter
import java.io.File
import java.nio.ByteBuffer

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class InterpreterWrapperImpl : InterpreterWrapper {
    override var context: Context? = null
    private val interpreter: Interpreter

    constructor(byteBuffer: ByteBuffer, options: InterpreterOptionsWrapper) {
        interpreter = Interpreter(byteBuffer, options.toInterpreterOptions())
    }

    constructor(file: File, options: InterpreterOptionsWrapper) {
        interpreter = Interpreter(file, options.toInterpreterOptions())
    }

    override fun runForMultipleInputsOutputs(inputs: Array<Any>, outputs: Map<Int, Any>) {
        interpreter.runForMultipleInputsOutputs(inputs, outputs)
    }

    override fun run(input: Any, output: Any) {
        interpreter.run(input, output)
    }

    override fun close() {
        interpreter.close()
    }
}

private fun InterpreterOptionsWrapper.toInterpreterOptions(): Interpreter.Options {
    val ret = Interpreter.Options()
    useNNAPI?.let {
        ret.setUseNNAPI(it)
    }
    numThreads?.let {
        ret.setNumThreads(it)
    }
    return ret
}
