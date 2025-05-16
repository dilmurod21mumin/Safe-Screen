package com.example.safescreen

import android.content.res.AssetManager
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class NSFWDetector(assetManager: AssetManager) {
    private var interpreter: Interpreter? = null
    private var isModelLoaded = false
    private val TAG = "NSFWDetector"

    init {
        try {
            Log.d(TAG, "Initializing NSFW detector")
            val modelBuffer = loadModelFile(assetManager, "saved_model.tflite")
            interpreter = Interpreter(modelBuffer)
            isModelLoaded = true
            Log.d(TAG, "Model loaded successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading model: ${e.message}", e)
            // We'll handle this case in predict()
        }
    }

    private fun loadModelFile(assetManager: AssetManager, modelFileName: String): MappedByteBuffer {
        try {
            Log.d(TAG, "Loading model file: $modelFileName")
            val fileDescriptor = assetManager.openFd(modelFileName)
            val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = fileDescriptor.startOffset
            val declaredLength = fileDescriptor.declaredLength

            val mappedByteBuffer = fileChannel.map(
                FileChannel.MapMode.READ_ONLY,
                startOffset,
                declaredLength
            )

            inputStream.close()
            fileDescriptor.close()

            Log.d(TAG, "Model file loaded successfully")
            return mappedByteBuffer
        } catch (e: Exception) {
            Log.e(TAG, "Error loading model file: ${e.message}", e)
            throw e
        }
    }

    fun predict(imageData: FloatArray): Float {
        if (!isModelLoaded || interpreter == null) {
            Log.e(TAG, "Model not loaded, returning safe default")
            return 0.0f // Safe default
        }

        try {
            // Prepare input buffer in [1, 224, 224, 3] format
            val inputBuffer = Array(1) { Array(224) { Array(224) { FloatArray(3) } } }
            var index = 0
            for (i in 0 until 224) {
                for (j in 0 until 224) {
                    for (k in 0 until 3) {
                        if (index < imageData.size) {
                            inputBuffer[0][i][j][k] = imageData[index++]
                        }
                    }
                }
            }

            // Prepare output buffer for 5 classes: [Drawing, Hentai, Neutral, Porn, Sexy]
            val output = Array(1) { FloatArray(5) }
            interpreter?.run(inputBuffer, output)

            val scores = output[0]
            val labels = arrayOf("Drawing", "Hentai", "Neutral", "Porn", "Sexy")

            // Debugging: log each label with its score
            for (i in labels.indices) {
                Log.d(TAG, "${labels[i]}: ${scores[i]}")
            }

            // Consider "Hentai" (index 1) + "Porn" (index 3) + "Sexy" (index 4)
            val nsfwScore = scores[1] + scores[3] + scores[4]
            Log.d(TAG, "NSFW Score: $nsfwScore")
            return nsfwScore // Return combined NSFW probability (0.0 - 1.0)
        } catch (e: Exception) {
            Log.e(TAG, "Error during prediction: ${e.message}", e)
            return 0.0f // Safe default on error
        }
    }

    fun close() {
        try {
            interpreter?.close()
            isModelLoaded = false
            Log.d(TAG, "Interpreter closed")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing interpreter: ${e.message}", e)
        }
    }
}
