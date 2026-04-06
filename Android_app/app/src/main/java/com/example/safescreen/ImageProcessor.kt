package com.example.safescreen

import android.graphics.Bitmap
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

object ImageProcessor {
    private const val TAG = "ImageProcessor"
    private const val IMAGE_SIZE = 224
    private const val CHANNELS = 3

    // FIX: Allocate per-call instead of sharing static buffers across threads.
    // The monitoring executor is single-threaded but this is safer and avoids
    // subtle bugs if threading ever changes.
    fun processImage(bitmap: Bitmap): FloatArray {
        try {
            Log.d(TAG, "Processing image: ${bitmap.width}x${bitmap.height}")

            val resizedBitmap = Bitmap.createScaledBitmap(bitmap, IMAGE_SIZE, IMAGE_SIZE, true)

            val buffer = ByteBuffer.allocateDirect(4 * IMAGE_SIZE * IMAGE_SIZE * CHANNELS)
                .apply { order(ByteOrder.nativeOrder()) }
            val intValues = IntArray(IMAGE_SIZE * IMAGE_SIZE)

            resizedBitmap.getPixels(intValues, 0, IMAGE_SIZE, 0, 0, IMAGE_SIZE, IMAGE_SIZE)

            var pixel = 0
            for (i in 0 until IMAGE_SIZE) {
                for (j in 0 until IMAGE_SIZE) {
                    val value = intValues[pixel++]
                    buffer.putFloat(((value shr 16 and 0xFF) - 127.5f) / 127.5f) // Red
                    buffer.putFloat(((value shr 8 and 0xFF) - 127.5f) / 127.5f)  // Green
                    buffer.putFloat(((value and 0xFF) - 127.5f) / 127.5f)        // Blue
                }
            }

            val floatArray = FloatArray(IMAGE_SIZE * IMAGE_SIZE * CHANNELS)
            buffer.rewind()
            buffer.asFloatBuffer().get(floatArray)

            if (bitmap != resizedBitmap) resizedBitmap.recycle()

            Log.d(TAG, "Image processed successfully")
            return floatArray
        } catch (e: Exception) {
            Log.e(TAG, "Error processing image: ${e.message}", e)
            return FloatArray(IMAGE_SIZE * IMAGE_SIZE * CHANNELS)
        }
    }
}