package com.mademediacorp.mmastripecardscan.payment.ml

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal data class RecognizedTextResult(
    val fullText: String,
    val lines: List<String>,
    val boundingBoxes: List<Rect>
)

internal class MLKitTextRecognizer {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun recognizeText(bitmap: Bitmap): RecognizedTextResult = suspendCancellableCoroutine { continuation ->
        // Check if bitmap has actual content
        val sampleWidth = minOf(10, bitmap.width)
        val sampleHeight = minOf(10, bitmap.height)
        val sampleSize = sampleWidth * sampleHeight

        if (sampleSize > 0) {
            val pixels = IntArray(sampleSize)
            try {
                bitmap.getPixels(pixels, 0, sampleWidth, 0, 0, sampleWidth, sampleHeight)
                val uniquePixels = pixels.toSet().size
                Log.d("DNI_DEBUG", "Bitmap unique colors in sample: $uniquePixels")
            } catch (e: Exception) {
                Log.w("DNI_DEBUG", "Could not sample bitmap pixels: ${e.message}")
            }
        }

        val image = try {
            InputImage.fromBitmap(bitmap, 0)
        } catch (e: Exception) {
            Log.e("DNI_DEBUG", "Failed to create InputImage: ${e.message}", e)
            continuation.resumeWithException(e)
            return@suspendCancellableCoroutine
        }

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
//                Log.d("DNI_DEBUG", "=== MLKit Success Response ===")
//                Log.d("DNI_DEBUG", "Full text: '${visionText.text}'")

                val lines = mutableListOf<String>()
                val boundingBoxes = mutableListOf<Rect>()

                // Extract text lines and their bounding boxes with detailed logging
                for ((blockIndex, block) in visionText.textBlocks.withIndex()) {
                    for ((lineIndex, line) in block.lines.withIndex()) {
                        lines.add(line.text)
                        line.boundingBox?.let {
                            boundingBoxes.add(it)
                        }
                    }
                }

                val result = RecognizedTextResult(
                    fullText = visionText.text,
                    lines = lines,
                    boundingBoxes = boundingBoxes
                )
                continuation.resume(result)
            }
            .addOnFailureListener { exception ->
                Log.e("DNI_DEBUG", "=== MLKit Failure Response ===")
                Log.e("DNI_DEBUG", "MLKit recognition failed: ${exception.message}", exception)
                continuation.resumeWithException(exception)
            }
    }

    fun shutdown() {
        recognizer.close()
    }
}