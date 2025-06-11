package com.mademediacorp.mmastripecardscan.framework.image

import android.util.Log
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.mademediacorp.mmastripecardscan.framework.image.MLImage
import java.nio.ByteBuffer

/**
 * Extension function to convert MLImage back to Bitmap for ML Kit processing
 */
fun MLImage.toBitmap(): Bitmap? {
    val buffer = this.getData()
    val width = this.width
    val height = this.height
    // MLImage contains preprocessed tensor data (normalized floats), so process it directly
    return createBitmapFromTensorData(buffer, width, height)
}

/**
 * Create bitmap directly from tensor data (normalized float values)
 */
private fun createBitmapFromTensorData(buffer: ByteBuffer, width: Int, height: Int): Bitmap? {
    // Create bitmap
    var resp = true
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val pixels = IntArray(width * height)

    // The buffer contains normalized float values (usually 0.0 to 1.0)
    // Based on your constants: IMAGE_MEAN = 127.5f, IMAGE_STD = 128.5f
    // Original normalization: (pixel - 127.5) / 128.5
    // To reverse: pixel = (normalized_value * 128.5) + 127.5

    val floatBuffer = buffer.asFloatBuffer()
    val channelsPerPixel = floatBuffer.capacity() / (width * height)
    when (channelsPerPixel) {
        3 -> {
            // RGB format
            Log.d("DNI_DEBUG", "Processing as RGB (3 channels)")
            for (i in 0 until width * height) {
                if (floatBuffer.remaining() >= 3) {
                    // Denormalize the values back to 0-255 range
                    val rNorm = floatBuffer.get()
                    val gNorm = floatBuffer.get()
                    val bNorm = floatBuffer.get()

                    // Reverse the normalization: pixel = (normalized * std) + mean
                    val r = ((rNorm * 128.5f) + 127.5f).toInt().coerceIn(0, 255)
                    val g = ((gNorm * 128.5f) + 127.5f).toInt().coerceIn(0, 255)
                    val b = ((bNorm * 128.5f) + 127.5f).toInt().coerceIn(0, 255)

                    pixels[i] = 0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
                } else {
                    pixels[i] = 0xFFFF0000.toInt() // Red for missing data
                }
            }
        }
        1 -> {
            // Grayscale format
            Log.d("DNI_DEBUG", "Processing as Grayscale (1 channel)")
            for (i in 0 until width * height) {
                if (floatBuffer.remaining() >= 1) {
                    val grayNorm = floatBuffer.get()
                    val gray = ((grayNorm * 128.5f) + 127.5f).toInt().coerceIn(0, 255)
                    pixels[i] = 0xFF000000.toInt() or (gray shl 16) or (gray shl 8) or gray
                } else {
                    pixels[i] = 0xFFFF0000.toInt() // Red for missing data
                }
            }
        }
        4 -> {
            // RGBA format
            Log.d("DNI_DEBUG", "Processing as RGBA (4 channels)")
            for (i in 0 until width * height) {
                if (floatBuffer.remaining() >= 4) {
                    val rNorm = floatBuffer.get()
                    val gNorm = floatBuffer.get()
                    val bNorm = floatBuffer.get()
                    val aNorm = floatBuffer.get()

                    val r = ((rNorm * 128.5f) + 127.5f).toInt().coerceIn(0, 255)
                    val g = ((gNorm * 128.5f) + 127.5f).toInt().coerceIn(0, 255)
                    val b = ((bNorm * 128.5f) + 127.5f).toInt().coerceIn(0, 255)
                    val a = ((aNorm * 128.5f) + 127.5f).toInt().coerceIn(0, 255)

                    pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
                } else {
                    pixels[i] = 0xFFFF0000.toInt() // Red for missing data
                }
            }
        }
        else -> {
            Log.w("DNI_DEBUG", "Unexpected channel count: $channelsPerPixel")
            resp = false
            // Fill with a test pattern
            for (i in pixels.indices) {
                val x = i % width
                val y = i / width
                pixels[i] = if ((x + y) % 20 < 10) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            }
        }
    }
    if (resp) {
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }
    return null
}