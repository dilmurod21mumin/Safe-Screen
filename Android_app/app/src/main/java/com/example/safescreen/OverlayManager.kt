package com.example.safescreen

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.*
import android.widget.TextView
import java.util.concurrent.atomic.AtomicBoolean

class OverlayManager(private val context: Context) {
    private val TAG = "OverlayManager"
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null
    private val isShowing = AtomicBoolean(false)

    fun show() {
        if (isShowing.getAndSet(true)) {
            // Already showing
            Log.d(TAG, "Overlay already showing")
            return
        }

        try {
            Log.d(TAG, "Showing overlay")

            // Create overlay view with a simple TextView for warning
            val view = TextView(context).apply {
                text = "⚠️ Inappropriate Content Detected"
                textSize = 24f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(20, 20, 20, 20)
                setBackgroundColor(Color.argb(200, 0, 0, 0))
            }

            // Configure window parameters
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )

            // Add view to window
            windowManager.addView(view, params)
            overlayView = view

            Log.d(TAG, "Overlay shown successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error showing overlay: ${e.message}", e)
            isShowing.set(false)
        }
    }

    fun hide() {
        if (!isShowing.getAndSet(false)) {
            // Already hidden
            return
        }

        try {
            Log.d(TAG, "Hiding overlay")
            overlayView?.let {
                windowManager.removeView(it)
                overlayView = null
                Log.d(TAG, "Overlay hidden successfully")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error hiding overlay: ${e.message}", e)
        }
    }
}
