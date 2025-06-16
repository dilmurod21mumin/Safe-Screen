package com.example.safescreen

import android.graphics.Bitmap
import android.media.Image
import android.util.Log
import java.nio.ByteBuffer

object ImageUtils {
    private const val TAG = "ImageUtils"

    fun imageToBitmap(image: Image): Bitmap? {
        return try {
            Log.d(TAG, "Converting Image to Bitmap: ${image.width}x${image.height}")
            val plane = image.planes[0]
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * image.width
            val width = image.width
            val height = image.height

            val bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888)
            val pixels = IntArray((rowStride / pixelStride) * height)

            buffer.rewind()
            bitmap.copyPixelsFromBuffer(buffer)
            val croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height)
            if (bitmap != croppedBitmap) bitmap.recycle()
            Log.d(TAG, "Image converted to Bitmap successfully")
            croppedBitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error converting Image to Bitmap: ${e.message}", e)
            null
        }
    }

    fun downscaleBitmap(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        try {
            if (bitmap.width <= maxWidth && bitmap.height <= maxHeight) {
                return bitmap
            }

            Log.d(TAG, "Downscaling bitmap from ${bitmap.width}x${bitmap.height} to fit within $maxWidth x $maxHeight")

            val ratio = Math.min(
                maxWidth.toFloat() / bitmap.width,
                maxHeight.toFloat() / bitmap.height
            )

            val width = (bitmap.width * ratio).toInt()
            val height = (bitmap.height * ratio).toInt()

            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, width, height, true)
            Log.d(TAG, "Bitmap downscaled to ${scaledBitmap.width}x${scaledBitmap.height}")

            return scaledBitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error downscaling bitmap: ${e.message}", e)
            return bitmap // Return original on error
        }
    }
}
