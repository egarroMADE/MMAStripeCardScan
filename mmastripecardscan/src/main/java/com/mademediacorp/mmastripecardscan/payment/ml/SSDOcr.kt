package com.mademediacorp.mmastripecardscan.payment.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Environment
import android.util.Log
import android.util.Size
import androidx.annotation.VisibleForTesting
import com.mademediacorp.mmastripecardscan.camera.framework.image.cropCameraPreviewToViewFinder
import com.mademediacorp.mmastripecardscan.camera.framework.image.hasOpenGl31
import com.mademediacorp.mmastripecardscan.camera.framework.image.scale
import com.mademediacorp.mmastripecardscan.mlcore.base.InterpreterOptionsWrapper
import com.mademediacorp.mmastripecardscan.mlcore.base.InterpreterWrapper
import com.mademediacorp.mmastripecardscan.framework.FetchedData
import com.mademediacorp.mmastripecardscan.framework.image.MLImage
import com.mademediacorp.mmastripecardscan.framework.image.toMLImage
import com.mademediacorp.mmastripecardscan.framework.ml.TFLAnalyzerFactory
import com.mademediacorp.mmastripecardscan.framework.ml.TensorFlowLiteAnalyzer
import com.mademediacorp.mmastripecardscan.framework.ml.ssd.adjustLocations
import com.mademediacorp.mmastripecardscan.framework.ml.ssd.softMax
import com.mademediacorp.mmastripecardscan.framework.ml.ssd.toRectForm
import com.mademediacorp.mmastripecardscan.framework.util.reshape
import com.mademediacorp.mmastripecardscan.payment.card.isValidPan
import com.mademediacorp.mmastripecardscan.payment.card.isValidDNI
import com.mademediacorp.mmastripecardscan.payment.ml.ssd.OcrFeatureMapSizes
import com.mademediacorp.mmastripecardscan.payment.ml.ssd.combinePriors
import com.mademediacorp.mmastripecardscan.payment.ml.ssd.determineLayoutAndFilter
import com.mademediacorp.mmastripecardscan.payment.ml.ssd.extractPredictions
import com.mademediacorp.mmastripecardscan.payment.ml.ssd.rearrangeOCRArray
import java.nio.ByteBuffer
import com.mademediacorp.mmastripecardscan.framework.image.toBitmap
import java.io.File
import java.io.FileOutputStream

/** Training images are normalized with mean 127.5 and std 128.5. */
private const val IMAGE_MEAN = 127.5f
private const val IMAGE_STD = 128.5f

/**
 * We use the output from last two layers with feature maps 19x19 and 10x10
 * and for each feature map activation we have 6 priors, so total priors are
 * 19x19x6 + 10x10x6 = 2766
 */
private const val NUM_OF_PRIORS = 3420

/**
 * For each activation in our feature map, we have predictions for 6 bounding boxes
 * of different aspect ratios
 */
private const val NUM_OF_PRIORS_PER_ACTIVATION = 3

/**
 * We can detect a total of 10 numbers (0 - 9) plus the background class
 */
private const val NUM_OF_CLASSES = 11

/**
 * Each prior or bounding box can be represented by 4 coordinates
 * XMin, YMin, XMax, YMax.
 */
private const val NUM_OF_COORDINATES = 4

/**
 * Represents the total number of data points for locations
 */
private const val NUM_LOC = NUM_OF_COORDINATES * NUM_OF_PRIORS

/**
 * Represents the total number of data points for classes
 */
private const val NUM_CLASS = NUM_OF_CLASSES * NUM_OF_PRIORS

private const val PROB_THRESHOLD = 0.50f
private const val IOU_THRESHOLD = 0.50f
private const val CENTER_VARIANCE = 0.1f
private const val SIZE_VARIANCE = 0.2f
private const val LIMIT = 20

@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
internal const val VERTICAL_THRESHOLD = 2.0f

private val FEATURE_MAP_SIZES =
    OcrFeatureMapSizes(
        layerOneWidth = 38,
        layerOneHeight = 24,
        layerTwoWidth = 19,
        layerTwoHeight = 12
    )

/**
 * This value should never change, and is thread safe.
 */
private val PRIORS = combinePriors(SSDOcr.Factory.TRAINED_IMAGE_SIZE)
private val mlKitRecognizer = MLKitTextRecognizer()
/**
 * This model performs SSD OCR recognition on a card.
 */
internal class SSDOcr private constructor(interpreter: InterpreterWrapper) :
    TensorFlowLiteAnalyzer<
        SSDOcr.Input,
        Array<ByteBuffer>,
        SSDOcr.Prediction,
        Map<Int, Array<FloatArray>>
        >(interpreter) {

            var context: Context? = interpreter.context

    data class Input(val ssdOcrImage: MLImage)

    data class Prediction(
        val pan: String?,
        val dni: String?,
        val recognitionMethod: RecognitionMethod = RecognitionMethod.TENSORFLOW
    ) {
        enum class RecognitionMethod {
            TENSORFLOW,  // Original digit-only TensorFlow model
            ML_KIT      // Google ML Kit for alphanumeric text
        }
        override fun toString(): String {
            return "Prediction"
        }
    }

    companion object {
        /**
         * Convert a camera preview image into a SSDOcr input
         */
        fun cameraPreviewToInput(
            cameraPreviewImage: Bitmap,
            previewBounds: Rect,
            cardFinder: Rect
        ) = Input(
            cropCameraPreviewToViewFinder(cameraPreviewImage, previewBounds, cardFinder)
                .scale(Factory.TRAINED_IMAGE_SIZE)
                .toMLImage(mean = IMAGE_MEAN, std = IMAGE_STD)
        )
        /**
         * Store the cropped bitmap for ML Kit processing
         */
        fun cameraPreviewToBitmap(
            cameraPreviewImage: Bitmap,
            previewBounds: Rect,
            cardFinder: Rect
        ): Bitmap {
            return cropCameraPreviewToViewFinder(cameraPreviewImage, previewBounds, cardFinder)
                .scale(Factory.TRAINED_IMAGE_SIZE)
        }
    }

    override suspend fun transformData(data: Input): Array<ByteBuffer> =
        arrayOf(data.ssdOcrImage.getData())

    override suspend fun interpretMLOutput(
        data: Input,
        mlOutput: Map<Int, Array<FloatArray>>
    ): Prediction {
        val outputClasses = mlOutput[0] ?: arrayOf(FloatArray(NUM_CLASS))
        val outputLocations = mlOutput[1] ?: arrayOf(FloatArray(NUM_LOC))

        val boxes = rearrangeOCRArray(
            locations = outputLocations,
            featureMapSizes = FEATURE_MAP_SIZES,
            numberOfPriors = NUM_OF_PRIORS_PER_ACTIVATION,
            locationsPerPrior = NUM_OF_COORDINATES
        ).reshape(NUM_OF_COORDINATES)
        boxes.adjustLocations(
            priors = PRIORS,
            centerVariance = CENTER_VARIANCE,
            sizeVariance = SIZE_VARIANCE
        )
        boxes.forEach { it.toRectForm() }

        val scores = rearrangeOCRArray(
            locations = outputClasses,
            featureMapSizes = FEATURE_MAP_SIZES,
            numberOfPriors = NUM_OF_PRIORS_PER_ACTIVATION,
            locationsPerPrior = NUM_OF_CLASSES
        ).reshape(NUM_OF_CLASSES)
        scores.forEach { it.softMax() }

        val detectedBoxes = determineLayoutAndFilter(
            extractPredictions(
                scores = scores,
                boxes = boxes,
                probabilityThreshold = PROB_THRESHOLD,
                intersectionOverUnionThreshold = IOU_THRESHOLD,
                limit = LIMIT,
                classifierToLabel = { if (it == 10) 0 else it }
            ),
            VERTICAL_THRESHOLD
        )

        val predictedNumber = detectedBoxes.map { it.label }.joinToString("")
        if (isValidPan(predictedNumber)) {
            return Prediction(predictedNumber, null, Prediction.RecognitionMethod.TENSORFLOW)
        }

        return tryMLKitRecognition(data, this.context)
    }

    /**
     * Use ML Kit Text Recognition for DNI scanning
     */
    private suspend fun tryMLKitRecognition(data: Input, context: Context?): Prediction {
        return try {
            Log.d("DNI", "=== Starting ML Kit Recognition ===")
            val mlImage = data.ssdOcrImage  //600X375
            val bitmap = mlImage.toBitmap()

//            // When debugging, you may want to save bitmap to disk for inspection
//            try {
//                saveBitmapToFile(bitmap, "debug_bitmap_${System.currentTimeMillis()}.png", context)
//            } catch (e: Exception) {
//                Log.e("DNI_DEBUG", "Failed to save bitmap: ${e.message}")
//            }

            if (bitmap == null) {
                return Prediction(null, null, Prediction.RecognitionMethod.ML_KIT)
            }
            val textResult = mlKitRecognizer.recognizeText(bitmap)
            if (textResult.fullText.isBlank() && textResult.lines.isEmpty()) {
                return Prediction(null, null, Prediction.RecognitionMethod.ML_KIT)
            }
            val processedDNIText = processDNIText(textResult)
            if (isValidDNI(processedDNIText)) {
                Log.d("DNI", "FOUND DNI with ML Kit!")
                Prediction(null, processedDNIText, Prediction.RecognitionMethod.ML_KIT)
            } else {
                Prediction(null, null, Prediction.RecognitionMethod.ML_KIT)
            }
        } catch (e: Exception) {
            Prediction(null, null, Prediction.RecognitionMethod.ML_KIT)
        }
    }

    /**
     * Save bitmap to external files directory for debugging
     */
    private fun saveBitmapToFile(bitmap: Bitmap, filename: String, context: Context?) {
        if (context != null) {
            Log.d("DNI", "=== saveBitmapToFile ===")
            try {
                // Use external files directory (doesn't require permissions)
//                val file = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), filename)
                val file = File("/storage/emulated/0/Android/media/com.mademediacorp.examplevue/examplevue/examplevue Images", filename)
                file.parentFile?.mkdirs()

                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    out.flush()
                }

                Log.d("DNI_DEBUG", "✅ Bitmap saved to: ${file.absolutePath}")
                Log.d("DNI_DEBUG", "File size: ${file.length()} bytes")
                // Also log the path so you can easily find it
                Log.i("BITMAP_SAVE", "Saved bitmap: ${file.absolutePath}")
            } catch (e: Exception) {
                Log.e("DNI_DEBUG", "Failed to save bitmap to file: ${e.message}", e)
            }
        } else {
            Log.d("DNI_DEBUG", "❌ No context available to save bitmap")
        }
    }

    /**
     * Process ML Kit recognized text for DNI format
     * DNI typically has 3 lines with ~30 characters each
     */
    private fun processDNIText(textResult: RecognizedTextResult): String {
        // Filter lines that look like DNI format
        val dniLines = textResult.lines.map { line ->
            line.replace(" ", "").replace("«", "<").uppercase()
        }.filter { line ->
            line.length >= 20 && line.length <= 40 && line.matches(Regex(".*[A-Z0-9<].*"))
        }.takeLast(3)

        return if (dniLines.size == 3) {
            dniLines.joinToString("")
        } else {

            textResult.fullText.replace(" ", "").replace("«", "<").take(90) // DNI is ~90 chars
        }
    }

    override suspend fun executeInference(
        tfInterpreter: InterpreterWrapper,
        data: Array<ByteBuffer>
    ): Map<Int, Array<FloatArray>> {
        val mlOutput = mapOf(
            0 to arrayOf(FloatArray(NUM_CLASS)),
            1 to arrayOf(FloatArray(NUM_LOC))
        )

        @Suppress("UNCHECKED_CAST")
        tfInterpreter.runForMultipleInputsOutputs(data as Array<Any>, mlOutput)
        return mlOutput
    }

    // Add cleanup method
    fun cleanup() {
        mlKitRecognizer.shutdown()
    }

    /**
     * A factory for creating instances of this analyzer.
     */
    class Factory(
        context: Context,
        fetchedModel: FetchedData,
        threads: Int = DEFAULT_THREADS
    ) : TFLAnalyzerFactory<Input, Prediction, SSDOcr>(context, fetchedModel) {
        companion object {
            private const val USE_GPU = false
            private const val DEFAULT_THREADS = 4

            val TRAINED_IMAGE_SIZE = Size(600, 375)
        }

        override val tfOptions = InterpreterOptionsWrapper.Builder()
            .useNNAPI(USE_GPU && hasOpenGl31(context.applicationContext))
            .numThreads(threads)
            .build()

        override suspend fun newInstance(): SSDOcr? {
            return createInterpreter(this.context)?.let { SSDOcr(it) }
        }
    }
}
