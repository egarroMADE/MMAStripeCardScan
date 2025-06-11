package com.mademediacorp.mmastripecardscan.mlcore.base

import android.content.Context
import androidx.annotation.RestrictTo

/**
 * Wrapper for TFLite interpreter API.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
interface InterpreterWrapper {
    var context: Context?
    fun runForMultipleInputsOutputs(
        inputs: Array<Any>,
        outputs: Map<Int, Any>
    )

    fun run(input: Any, output: Any)

    fun close()
}
