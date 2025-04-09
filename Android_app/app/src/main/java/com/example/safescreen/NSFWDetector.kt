package com.example.safescreen

import android.content.res.AssetManager
import android.util.Log
import android.widget.Toast
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.logging.LogManager

class NSFWDetector(assetManager: AssetManager) {
    private var interpreter: Interpreter? = null

    init {
        try {
            interpreter = Interpreter(loadModelFile(assetManager, "saved_model.tflite"))
        } catch (e: Exception) {
            Log.e("NSFWDetector", "Error loading model: ${e.message}")
            // Initialize with null and handle this case in predict()
        }
    }

    private fun loadModelFile(assetManager: AssetManager, modelFileName: String): MappedByteBuffer {
        val fileDescriptor = assetManager.openFd(modelFileName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            fileDescriptor.startOffset,
            fileDescriptor.declaredLength
        )
    }

    fun predict(imageData: FloatArray): Float {
        if (interpreter == null) return 0.0f // Safe default

        // Prepare input buffer in [1, 224, 224, 3] format
        val inputBuffer = Array(1) { Array(224) { Array(224) { FloatArray(3) } } }
        var index = 0
        for (i in 0 until 224) {
            for (j in 0 until 224) {
                for (k in 0 until 3) {
                    inputBuffer[0][i][j][k] = imageData[index++]
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
            Log.d("NSFWDetector", "${labels[i]}: ${scores[i]}")
        }

        // Consider "Hentai" (index 1) + "Porn" (index 3)  + "Sexy" (index)
        val nsfwScore = scores[1]+scores[3]+scores[4]
        Log.d("NSFWDetector", "NSFW Score rtd: $nsfwScore")
        return nsfwScore // Return combined NSFW probability (0.0 - 1.0)
    }
}