package com.mademediacorp.mmastripecardscan.payment.ml

import android.content.Context
import com.mademediacorp.mmastripecardscan.framework.Fetcher
import com.mademediacorp.mmastripecardscan.framework.ResourceFetcher
import com.mademediacorp.mmastripecardscan.payment.ModelManager

private const val OCR_ASSET_FULL = "darknite_1_1_1_16.tflite"

internal object SSDOcrModelManager : ModelManager() {
    override fun getModelFetcher(context: Context): Fetcher = object : ResourceFetcher() {
        override val assetFileName: String = OCR_ASSET_FULL
        override val modelVersion: String = "1.1.1.16"
        override val hash: String =
            "8d8e3f79aa0783ab0cfa5c8d65d663a9da6ba99401efb2298aaaee387c3b00d6"
        override val hashAlgorithm: String = "SHA-256"
        override val modelClass: String = "ocr"
        override val modelFrameworkVersion: Int = 1
    }
}
