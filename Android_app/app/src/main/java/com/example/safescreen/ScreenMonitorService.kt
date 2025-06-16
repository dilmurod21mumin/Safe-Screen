package com.example.safescreen

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.*
import android.media.AudioManager
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.safescreen.ScreenMonitorService.Companion.TAG
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

    private var nsfwStrikeCount = 0

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

            //saveBitmapToFile(bitmap)

            scaledBitmap = if (bitmap.width > 720 || bitmap.height > 1280) {
                ImageUtils.downscaleBitmap(bitmap, 720, 1280)
            } else {
                bitmap
            }

            val orientation = if (scaledBitmap.height > scaledBitmap.width) "portrait" else "landscape"


            // Step 1: check full image
            val fullScore = nsfwDetector?.predict(ImageProcessor.processImage(scaledBitmap)) ?: 0f
            Log.d(TAG, "NSFW full score: $fullScore")
            if (fullScore > 0.5f) {
                handleNsfwDetection(true)
                Log.d(TAG,"NSFW in full returned")
                return
            }

            // Step 2: check center crop
            val center = cropCenter(scaledBitmap, 0.4f)

            val centerScore = nsfwDetector?.predict(ImageProcessor.processImage(center)) ?: 0f
            Log.d(TAG, "NSFW center score: $centerScore")
            if (centerScore > 0.5f) {
                handleNsfwDetection(true)
                Log.d(TAG,"NSFW in center returned")
                return
            }
            center.recycle()

            // Step 3: check top and bottom areas (e.g. YouTube in mini-player mode)
            val topBottomParts = listOf(
                Bitmap.createBitmap(scaledBitmap, 0, 0, scaledBitmap.width, scaledBitmap.height / 3), // top 1/3
                Bitmap.createBitmap(scaledBitmap, 0, (scaledBitmap.height * 2) / 3, scaledBitmap.width, scaledBitmap.height / 3) // bottom 1/3
            )

            for ((i, part) in topBottomParts.withIndex()) {
                val score = nsfwDetector?.predict(ImageProcessor.processImage(part)) ?: 0f
                Log.d(TAG, "NSFW topBottom[$i] score: $score")

                if (score > 0.5f) {
                    handleNsfwDetection(true)
                    Log.d(TAG, "NSFW detected in ${if (i == 0) "top" else "bottom"} part")
                    return
                }
            }
            topBottomParts.forEach { it.recycle() }

            // If all checks passed, hide overlay
            handler.post { overlayManager.hide() }

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

    private fun handleNsfwDetection(detected: Boolean) {
        handler.post {
            try {
                if (detected) {
                    overlayManager.show()
                    muteMediaSound(true)
                    nsfwStrikeCount++

                    Log.d(TAG, "NSFW detected — strike $nsfwStrikeCount")

                    if (nsfwStrikeCount >= 4) {
                        Log.d(TAG, "NSFW shown 4 times — closing app")
                        muteMediaSound(false)
                        val intent = Intent(Intent.ACTION_MAIN).apply {
                            addCategory(Intent.CATEGORY_HOME)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        startActivity(intent)

                    }
                } else {
                    overlayManager.hide()
                    muteMediaSound(false)
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
            val stream = AudioManager.STREAM_MUSIC
            audioManager.adjustStreamVolume(
                stream,
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

    // saving screen image

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
