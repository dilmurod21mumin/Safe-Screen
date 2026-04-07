package com.dilmurod.safescreen

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.*
import android.widget.TextView

class OverlayManager(private val context: Context) {
    private val TAG = "OverlayManager"
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null

    /** True when the overlay window is currently added to the WindowManager. */
    val isShowing: Boolean
        get() = overlayView != null

    fun show() {
        if (isShowing) {
            Log.d(TAG, "Overlay already showing — skipping")
            return
        }
        try {
            Log.d(TAG, "Showing overlay")
            val view = TextView(context).apply {
                text = "⚠️ Zararli kontent aniqlandi!"
                textSize = 24f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(20, 20, 20, 20)
                setBackgroundColor(Color.argb(255, 0, 0, 30))
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_OVERSCAN,
                PixelFormat.TRANSLUCENT
            )

            windowManager.addView(view, params)
            overlayView = view
            Log.d(TAG, "Overlay shown successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error showing overlay: ${e.message}", e)
            overlayView = null
        }
    }

    fun hide() {
        val view = overlayView ?: run {
            Log.d(TAG, "Overlay already hidden — skipping")
            return
        }
        try {
            Log.d(TAG, "Hiding overlay")
            windowManager.removeView(view)
            Log.d(TAG, "Overlay hidden successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error hiding overlay: ${e.message}", e)
        } finally {
            // Always clear the reference so isShowing returns false
            overlayView = null
        }
    }
}