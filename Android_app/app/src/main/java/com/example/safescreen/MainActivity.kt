package com.example.safescreen

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.safescreen.ui.theme.SafeScreenTheme

class MainActivity : ComponentActivity() {

    private val OVERLAY_PERMISSION_REQUEST = 1001
    private val SCREEN_CAPTURE_REQUEST = 2001
    private val TAG = "MainActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "MainActivity onCreate")
        setContent {
            SafeScreenTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainContent(
                        onStartClick = { checkPermissionsAndStart() }
                    )
                }
            }
        }
    }

    @Composable
    fun MainContent(onStartClick: () -> Unit) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Safe Screen",
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Ekraningizni nomaqbul kontentdan himoyalaydi",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = onStartClick,
                modifier = Modifier.padding(16.dp)
            ) {
                Text("Monitoringni boshlash")
            }
        }
    }

    private fun checkPermissionsAndStart() {
        try {
            Log.d(TAG, "Checking overlay permission")
            if (!Settings.canDrawOverlays(this)) {
                requestOverlayPermission()
            } else {
                requestScreenCapturePermission()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking permissions: ${e.message}", e)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun requestOverlayPermission() {
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST)
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting overlay permission: ${e.message}", e)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun requestScreenCapturePermission() {
        try {
            val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val captureIntent = projectionManager.createScreenCaptureIntent()
            startActivityForResult(captureIntent, SCREEN_CAPTURE_REQUEST)
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            Log.e(TAG, "Error requesting screen capture: ${e.message}", e)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        try {
            when (requestCode) {
                OVERLAY_PERMISSION_REQUEST -> {
                    if (Settings.canDrawOverlays(this)) {
                        Log.d(TAG, "Overlay permission granted")
                        requestScreenCapturePermission()
                    } else {
                        Log.e(TAG, "Overlay permission denied")
                        Toast.makeText(this, "Yozib olish ruxsati talab etiladi!", Toast.LENGTH_LONG).show()
                    }
                }

                SCREEN_CAPTURE_REQUEST -> {
                    if (resultCode == Activity.RESULT_OK && data != null) {
                        Log.d(TAG, "Screen capture permission granted")

                        val serviceIntent = Intent(this, ScreenMonitorService::class.java).apply {
                            putExtra("resultCode", resultCode)
                            putExtra("data", data)
                        }

                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            startForegroundService(serviceIntent)
                        } else {
                            startService(serviceIntent)
                        }

                        Toast.makeText(this, "SafeScreen ishga tushdi", Toast.LENGTH_SHORT).show()


                        // Save the service state in SharedPreferences
                        getSharedPreferences("SafeScreenPrefs", MODE_PRIVATE)
                            .edit()
                            .putBoolean("isServiceRunning", true)
                            .apply()

                        // Close app after service starts
                        finishAndRemoveTask()
                    } else {
                        Log.e(TAG, "Screen capture permission denied")
                        Toast.makeText(this, "Yozib olish ruxsati talab etiladi!", Toast.LENGTH_LONG).show()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onActivityResult: ${e.message}", e)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
