package com.mademediacorp.mmastripecardscan.payment.ml

import android.content.Context
import com.mademediacorp.mmastripecardscan.framework.Fetcher
import com.mademediacorp.mmastripecardscan.framework.ResourceFetcher
import com.mademediacorp.mmastripecardscan.payment.ModelManager

private const val CARD_DETECT_ASSET_FULL = "ux_0_5_23_16.tflite"

internal object CardDetectModelManager : ModelManager() {
    override fun getModelFetcher(context: Context): Fetcher = object : ResourceFetcher() {
        override val assetFileName: String = CARD_DETECT_ASSET_FULL
        override val modelVersion: String = "0.5.23.16"
        override val hash: String =
            "ea51ca5c693a4b8733b1cf1a63557a713a13fabf0bcb724385077694e63a51a7"
        override val hashAlgorithm: String = "SHA-256"
        override val modelClass: String = "card_detection"
        override val modelFrameworkVersion: Int = 1
    }
}
