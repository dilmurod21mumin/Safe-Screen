package com.dilmurod.safescreen

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.*
import android.media.AudioManager
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class ScreenMonitorService : Service() {

    private lateinit var projectionManager: MediaProjectionManager
    private var nsfwDetector: NSFWDetector? = null
    private lateinit var overlayManager: OverlayManager
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: android.hardware.display.VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val isRunning = AtomicBoolean(false)
    private var executor = Executors.newSingleThreadScheduledExecutor()
    private val handler = Handler(Looper.getMainLooper())

    private var isOverlayVisible = false

    private var nsfwStrikeCount = 0

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            super.onStop()
            Log.d(TAG, "MediaProjection stopped by system")
            stopCapture()
        }
    }

    companion object {
        const val TAG = "ScreenMonitorService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "safescreen_channel"

        @Volatile
        private var INSTANCE: ScreenMonitorService? = null
        fun getInstance(): ScreenMonitorService? = INSTANCE
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        INSTANCE = this

        // Always start foreground with no type first — safe on all API levels
        // and doesn't require mediaProjection token or special_use declaration.
        createNotification(foregroundType = null)

        try {
            projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            nsfwDetector = try {
                NSFWDetector(assets).also { Log.d(TAG, "NSFW Detector initialized") }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize NSFW detector: ${e.message}", e)
                null
            }
            overlayManager = OverlayManager(this)
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate: ${e.message}", e)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand action=${intent?.action}")

        try {
            if (intent?.action == "STOP_SERVICE") {
                Log.d(TAG, "Stop service requested via intent")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                stopSelf()
                return START_NOT_STICKY
            }

            val resultCode = intent?.getIntExtra("resultCode", 0) ?: 0
            val data = intent?.getParcelableExtra<Intent>("data")

            if (resultCode != 0 && data != null) {
                // We have a real MediaProjection token — upgrade FGS type to mediaProjection
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    createNotification(foregroundType = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
                }
                startCapture(data, resultCode)
            } else if (!isRunning.get()) {
                // No projection data — request permission via activity
                val captureIntent = projectionManager.createScreenCaptureIntent()
                val activityIntent = Intent(this, CapturePermissionActivity::class.java).apply {
                    putExtra("intent", captureIntent)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    startActivity(activityIntent)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start CapturePermissionActivity: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onStartCommand: ${e.message}", e)
        }
        return START_STICKY
    }

    fun startCapture(projectionIntent: Intent, resultCode: Int) {
        if (isRunning.getAndSet(true)) {
            Log.d(TAG, "Capture already running")
            return
        }

        try {
            Log.d(TAG, "Starting capture with resultCode: $resultCode")
            mediaProjection = projectionManager.getMediaProjection(resultCode, projectionIntent)

            if (mediaProjection == null) {
                Log.e(TAG, "Failed to get MediaProjection")
                isRunning.set(false)
                return
            }

            // FIX: Use WindowManager for correct screen metrics on API 30+
            val (width, height, density) = getDisplayMetrics()
            Log.d(TAG, "Screen metrics: $width x $height, density: $density")

            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            mediaProjection?.registerCallback(projectionCallback, handler)
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "SafeScreen", width, height, density,
                0, imageReader?.surface, null, null
            )

            if (virtualDisplay == null) {
                Log.e(TAG, "Failed to create VirtualDisplay")
                isRunning.set(false)
                return
            }

            startMonitoring()
            Log.d(TAG, "Screen capture started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting capture: ${e.message}", e)
            isRunning.set(false)
            stopCapture()
        }
    }

    @Suppress("DEPRECATION")
    private fun getDisplayMetrics(): Triple<Int, Int, Int> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = wm.currentWindowMetrics
            val bounds = metrics.bounds
            Triple(bounds.width(), bounds.height(), resources.displayMetrics.densityDpi)
        } else {
            val dm = resources.displayMetrics
            Triple(dm.widthPixels, dm.heightPixels, dm.densityDpi)
        }
    }

    private fun startMonitoring() {
        try {
            executor = Executors.newSingleThreadScheduledExecutor()
            executor.scheduleAtFixedRate({
                try {
                    if (isRunning.get()) processLatestImage()
                } catch (e: Exception) {
                    Log.e(TAG, "Error in monitoring thread: ${e.message}", e)
                }
            }, 500, 2000, TimeUnit.MILLISECONDS)
            Log.d(TAG, "Monitoring thread started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start monitoring: ${e.message}", e)
        }
    }

    private fun processLatestImage() {
        // Don't process if overlay is visible OR detection is paused
        if (isOverlayVisible || !isRunning.get()) return

        if (imageReader == null) {
            Log.e(TAG, "ImageReader is null")
            return
        }

        var image: Image? = null
        var bitmap: Bitmap? = null
        var scaledBitmap: Bitmap? = null
        val cropBitmaps = mutableListOf<Bitmap>()

        try {
            image = imageReader?.acquireLatestImage() ?: return
            bitmap = ImageUtils.imageToBitmap(image) ?: return

            scaledBitmap = if (bitmap.width > 720 || bitmap.height > 1280) {
                ImageUtils.downscaleBitmap(bitmap, 720, 1280)
            } else {
                bitmap
            }

            // Step 1: full image
            val fullScore = nsfwDetector?.predict(ImageProcessor.processImage(scaledBitmap)) ?: 0f
            Log.d(TAG, "NSFW full score: $fullScore")
            if (fullScore > 0.5f) {
                handleNsfwDetection(true)
                return
            }

            // Step 2: center crop (40%)
            val center = cropCenter(scaledBitmap, 0.4f).also { cropBitmaps.add(it) }
            val centerScore = nsfwDetector?.predict(ImageProcessor.processImage(center)) ?: 0f
            Log.d(TAG, "NSFW center score: $centerScore")
            if (centerScore > 0.5f) {
                handleNsfwDetection(true)
                return
            }

            // Step 3: top and bottom thirds
            val top = Bitmap.createBitmap(scaledBitmap, 0, 0, scaledBitmap.width, scaledBitmap.height / 3)
                .also { cropBitmaps.add(it) }
            val bottom = Bitmap.createBitmap(scaledBitmap, 0, (scaledBitmap.height * 2) / 3, scaledBitmap.width, scaledBitmap.height / 3)
                .also { cropBitmaps.add(it) }

            val topScore = nsfwDetector?.predict(ImageProcessor.processImage(top)) ?: 0f
            Log.d(TAG, "NSFW top score: $topScore")
            if (topScore > 0.5f) {
                handleNsfwDetection(true)
                return
            }

            val bottomScore = nsfwDetector?.predict(ImageProcessor.processImage(bottom)) ?: 0f
            Log.d(TAG, "NSFW bottom score: $bottomScore")
            if (bottomScore > 0.5f) {
                handleNsfwDetection(true)
                return
            }

            // ✅ All checks passed — clean frame detected
            // Only reset if overlay is visible (to clear it) or if we have strikes
            if (isOverlayVisible || nsfwStrikeCount > 0) {
                handleNsfwDetection(false)
            } else {
                // Ensure strike count is 0 when everything is clean
                nsfwStrikeCount = 0
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error processing image: ${e.message}", e)
        } finally {
            cropBitmaps.forEach { if (!it.isRecycled) it.recycle() }
            try {
                image?.close()
                if (scaledBitmap != null && scaledBitmap != bitmap && !scaledBitmap.isRecycled) scaledBitmap.recycle()
                if (bitmap != null && !bitmap.isRecycled) bitmap.recycle()
            } catch (e: Exception) {
                Log.e(TAG, "Error cleaning up resources: ${e.message}", e)
            }
        }
    }

    private fun handleNsfwDetection(detected: Boolean) {
        handler.post {
            try {
                if (detected) {
                    // Don't increment strikes if we're already showing the overlay
                    if (isOverlayVisible) {
                        Log.d(TAG, "NSFW detected but overlay already visible — ignoring")
                        return@post
                    }

                    nsfwStrikeCount++
                    Log.d(TAG, "NSFW detected — strike $nsfwStrikeCount")

                    if (nsfwStrikeCount >= 3) {
                        Log.d(TAG, "NSFW shown 3 times — going home")
                        // Reset variables
                        nsfwStrikeCount = 0
                        overlayManager.hide()
                        muteMediaSound(false)
                        isOverlayVisible = false
                        isRunning.set(true)

                        // Kick user to Home Screen
                        val intent = Intent(Intent.ACTION_MAIN).apply {
                            addCategory(Intent.CATEGORY_HOME)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        startActivity(intent)
                    } else {
                        // Strike 1 or 2: Show overlay as a warning
                        overlayManager.show()
                        muteMediaSound(true)
                        isOverlayVisible = true

                        // Pause processing so we don't scan our own black overlay
                        isRunning.set(false)

                        // Automatically hide the warning after 2.5 seconds to re-evaluate the screen
                        handler.postDelayed({
                            if (isOverlayVisible) {
                                overlayManager.hide()
                                muteMediaSound(false)
                                isOverlayVisible = false
                                // Resume processing to see if they scrolled away
                                isRunning.set(true)
                            }
                        }, 2500) // 2.5 seconds penalty/warning
                    }
                } else {
                    // Clean frame detected
                    if (isOverlayVisible) {
                        Log.d(TAG, "Clean frame detected — hiding overlay")
                        overlayManager.hide()
                        muteMediaSound(false)
                        isOverlayVisible = false
                        isRunning.set(true)
                    }
                    // Crucial: Reset strikes to 0 if the user scrolled away to safe content
                    nsfwStrikeCount = 0
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating overlay: ${e.message}", e)
            }
        }
    }


    private fun muteMediaSound(mute: Boolean) {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                if (mute) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE,
                0
            )
        }
    }

    private fun cropCenter(bitmap: Bitmap, scale: Float): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val sw = (w * scale).toInt()
        val sh = (h * scale).toInt()
        val left = (w - sw) / 2
        val top = (h - sh) / 2
        return Bitmap.createBitmap(bitmap, left, top, sw, sh)
    }

    private fun stopCapture() {
        try {
            Log.d(TAG, "Stopping capture")
            executor.shutdownNow()
            virtualDisplay?.release()
            virtualDisplay = null
            imageReader?.close()
            imageReader = null
            mediaProjection?.unregisterCallback(projectionCallback)
            mediaProjection?.stop()
            mediaProjection = null
            overlayManager.hide()
            isRunning.set(false)
            Log.d(TAG, "Capture stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping capture: ${e.message}", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "Service onDestroy")
        INSTANCE = null
        getSharedPreferences("SafeScreenPrefs", MODE_PRIVATE)
            .edit().putBoolean("isServiceRunning", false).apply()
        stopCapture()
        nsfwDetector?.close()
        super.onDestroy()
    }

    private fun createNotification(foregroundType: Int? = null) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID, "SafeScreen", NotificationManager.IMPORTANCE_LOW
                ).apply { description = "Screen monitoring service" }
                getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
            }

            val stopIntent = PendingIntent.getService(
                this, 1,
                Intent(this, ScreenMonitorService::class.java).apply { action = "STOP_SERVICE" },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("SafeScreen")
                .setContentText("Ekran monitoringi faol")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentIntent(
                    PendingIntent.getActivity(
                        this, 0,
                        Intent(this, MainActivity::class.java),
                        PendingIntent.FLAG_IMMUTABLE
                    )
                )
                .addAction(android.R.drawable.ic_delete, "To'xtatish", stopIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()

            // Only pass a foreground service type when we actually have a valid token.
            // Passing mediaProjection without a token crashes on API 29+.
            // Passing special_use without manifest declaration crashes on API 34+.
            // Plain startForeground(id, notification) works on all API levels for basic cases.
            if (foregroundType != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, foregroundType)
                Log.d(TAG, "Notification created and startForeground called with type: $foregroundType")
            } else {
                startForeground(NOTIFICATION_ID, notification)
                Log.d(TAG, "Notification created and startForeground called (no type)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Critical error in createNotification: ${e.message}", e)
            // Last-resort fallback — must call startForeground within 5s of startForegroundService
            try {
                val fallback = NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("SafeScreen")
                    .setSmallIcon(android.R.drawable.ic_menu_view)
                    .build()
                startForeground(NOTIFICATION_ID, fallback)
            } catch (e2: Exception) {
                Log.e(TAG, "Fallback startForeground also failed: ${e2.message}")
            }
        }
    }

    private fun saveBitmapToFile(bitmap: Bitmap) {
        val filename = "screenshot_${System.currentTimeMillis()}.png"
        val file = File(getExternalFilesDir(null), filename)
        try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                Log.d(TAG, "Screenshot saved to ${file.absolutePath}")
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to save screenshot: ${e.message}", e)
        }
    }
}