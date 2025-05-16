package com.example.safescreen

import android.graphics.Bitmap
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

object ImageProcessor {
    private const val TAG = "ImageProcessor"
    private const val IMAGE_SIZE = 224
    private const val CHANNELS = 3

    // Pre-allocate buffer to avoid GC
    private val buffer = ByteBuffer.allocateDirect(4 * IMAGE_SIZE * IMAGE_SIZE * CHANNELS)
        .apply { order(ByteOrder.nativeOrder()) }

    // Pre-allocate array to avoid GC
    private val intValues = IntArray(IMAGE_SIZE * IMAGE_SIZE)

    fun processImage(bitmap: Bitmap): FloatArray {
        try {
            Log.d(TAG, "Processing image: ${bitmap.width}x${bitmap.height}")

            val resizedBitmap = Bitmap.createScaledBitmap(bitmap, IMAGE_SIZE, IMAGE_SIZE, true)

            // Reset buffer position
            buffer.rewind()

            // Get pixel data
            resizedBitmap.getPixels(intValues, 0, IMAGE_SIZE, 0, 0, IMAGE_SIZE, IMAGE_SIZE)

            var pixel = 0
            for (i in 0 until IMAGE_SIZE) {
                for (j in 0 until IMAGE_SIZE) {
                    val value = intValues[pixel++]

                    // Normalize to [-1, 1] range
                    buffer.putFloat(((value shr 16 and 0xFF) - 127.5f) / 127.5f) // Red
                    buffer.putFloat(((value shr 8 and 0xFF) - 127.5f) / 127.5f)  // Green
                    buffer.putFloat(((value and 0xFF) - 127.5f) / 127.5f)        // Blue
                }
            }

            // Extract float array from ByteBuffer
            val floatArray = FloatArray(IMAGE_SIZE * IMAGE_SIZE * CHANNELS)
            buffer.rewind() // Reset buffer position
            buffer.asFloatBuffer().get(floatArray)

            // Clean up the resized bitmap to avoid memory leaks
            if (bitmap != resizedBitmap) {
                resizedBitmap.recycle()
            }

            Log.d(TAG, "Image processed successfully")
            return floatArray
        } catch (e: Exception) {
            Log.e(TAG, "Error processing image: ${e.message}", e)
            // Return empty array on error
            return FloatArray(IMAGE_SIZE * IMAGE_SIZE * CHANNELS)
        }
    }
}
