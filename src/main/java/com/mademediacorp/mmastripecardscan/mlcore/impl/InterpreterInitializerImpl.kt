package com.mademediacorp.mmastripecardscan.mlcore.impl

import android.content.Context
import androidx.annotation.RestrictTo
import com.mademediacorp.mmastripecardscan.mlcore.base.InterpreterInitializer

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
object InterpreterInitializerImpl : InterpreterInitializer {
    override fun initialize(
        context: Context,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        onSuccess()
    }
}
