package com.example.safescreen

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.*
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
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

    // ✅ MediaProjection callback
    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            super.onStop()
            Log.d(TAG, "MediaProjection stopped by system")
            stopCapture()
        }
    }

    companion object {
        private const val TAG = "ScreenMonitorService"
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
        try {
            projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            nsfwDetector = try {
                NSFWDetector(assets).also {
                    Log.d(TAG, "NSFW Detector initialized")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize NSFW detector: ${e.message}", e)
                null
            }

            overlayManager = OverlayManager(this)
            createNotification()
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate: ${e.message}", e)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand")
        try {
            if (intent?.action == "STOP_SERVICE") {
                stopSelf()
                return START_NOT_STICKY
            }

            val resultCode = intent?.getIntExtra("resultCode", 0) ?: 0
            val data = intent?.getParcelableExtra<Intent>("data")
            if (resultCode != 0 && data != null) {
                startCapture(data, resultCode)
            } else {
                val captureIntent = projectionManager.createScreenCaptureIntent()
                val activityIntent = Intent(this, CapturePermissionActivity::class.java).apply {
                    putExtra("intent", captureIntent)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(activityIntent)
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

            val metrics = Resources.getSystem().displayMetrics
            val width = metrics.widthPixels
            val height = metrics.heightPixels
            val density = metrics.densityDpi

            Log.d(TAG, "Screen metrics: $width x $height, density: $density")

            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

            // ✅ Register the projection callback before creating the virtual display
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

    private fun startMonitoring() {
        try {
            executor = Executors.newSingleThreadScheduledExecutor()
            executor.scheduleAtFixedRate({
                try {
                    if (isRunning.get()) {
                        processLatestImage()
                    }
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
        if (imageReader == null) {
            Log.e(TAG, "ImageReader is null")
            return
        }

        var image: Image? = null
        var bitmap: Bitmap? = null
        var scaledBitmap: Bitmap? = null

        try {
            image = imageReader?.acquireLatestImage() ?: return
            bitmap = ImageUtils.imageToBitmap(image) ?: return

            scaledBitmap = if (bitmap.width > 720 || bitmap.height > 1280) {
                ImageUtils.downscaleBitmap(bitmap, 720, 1280)
            } else {
                bitmap
            }

            val processed = ImageProcessor.processImage(scaledBitmap)
            val score = nsfwDetector?.predict(processed) ?: 0.0f

            Log.d(TAG, "NSFW detection score: $score")

            handler.post {
                try {
                    if (score > 0.5) {
                        overlayManager.show()
                    } else {
                        overlayManager.hide()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating overlay: ${e.message}", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing image: ${e.message}", e)
        } finally {
            try {
                image?.close()
                if (scaledBitmap != bitmap && scaledBitmap != null) scaledBitmap.recycle()
                bitmap?.recycle()
            } catch (e: Exception) {
                Log.e(TAG, "Error cleaning up resources: ${e.message}", e)
            }
        }
    }

    private fun stopCapture() {
        try {
            Log.d(TAG, "Stopping capture")
            executor.shutdownNow()
            virtualDisplay?.release()
            virtualDisplay = null
            imageReader?.close()
            imageReader = null

            // ✅ Unregister the projection callback and stop projection
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
        stopCapture()
        super.onDestroy()
    }

    private fun createNotification() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "SafeScreen",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Screen monitoring service"
                }
                getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
            }

            val pendingIntent = PendingIntent.getActivity(
                this, 0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("SafeScreen")
                .setContentText("Monitoring your screen")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentIntent(pendingIntent)
                .build()

            startForeground(NOTIFICATION_ID, notification)
            Log.d(TAG, "Notification created")
        } catch (e: Exception) {
            Log.e(TAG, "Error creating notification: ${e.message}", e)
        }
    }
}
