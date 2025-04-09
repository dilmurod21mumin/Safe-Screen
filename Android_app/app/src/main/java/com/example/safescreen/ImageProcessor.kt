package com.example.safescreen

import android.graphics.Bitmap
import java.nio.ByteBuffer
import java.nio.ByteOrder

object ImageProcessor {
    fun processImage(bitmap: Bitmap): FloatArray {
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 224, 224, true)
        val buffer = ByteBuffer.allocateDirect(4 * 224 * 224 * 3) // Float (4 bytes) × 224 × 224 × 3 channels
        buffer.order(ByteOrder.nativeOrder())

        val intValues = IntArray(224 * 224)
        resizedBitmap.getPixels(intValues, 0, 224, 0, 0, 224, 224)

        var pixel = 0
        for (i in 0 until 224) {
            for (j in 0 until 224) {
                val value = intValues[pixel++]
                buffer.putFloat(((value shr 16 and 0xFF) - 127.5f) / 127.5f) // Normalize Red
                buffer.putFloat(((value shr 8 and 0xFF) - 127.5f) / 127.5f)  // Normalize Green
                buffer.putFloat(((value and 0xFF) - 127.5f) / 127.5f)        // Normalize Blue
            }
        }

        // Extract float array from ByteBuffer
        val floatArray = FloatArray(224 * 224 * 3)
        buffer.rewind() // Reset buffer position
        buffer.asFloatBuffer().get(floatArray)

        return floatArray
    }
}
