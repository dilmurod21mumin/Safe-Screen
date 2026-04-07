package com.dilmurod.safescreen

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast

class CapturePermissionActivity : Activity() {
    companion object {
        private const val REQUEST_CODE = 101
        private const val TAG = "CapturePermission"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            Log.d(TAG, "CapturePermissionActivity onCreate")
            val intent = intent.getParcelableExtra<Intent>("intent")
            if (intent != null) {
                Log.d(TAG, "Starting activity for result")
                startActivityForResult(intent, REQUEST_CODE)
            } else {
                Log.e(TAG, "No intent provided")
                Toast.makeText(this, "Error: No capture intent provided", Toast.LENGTH_SHORT).show()
                finish()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate: ${e.message}", e)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        try {
            Log.d(TAG, "onActivityResult: requestCode=$requestCode, resultCode=$resultCode")

            if (requestCode == REQUEST_CODE) {
                if (resultCode == RESULT_OK && data != null) {
                    Log.d(TAG, "Screen capture permission granted")

                    // Start the service with the data
                    val serviceIntent = Intent(this, ScreenMonitorService::class.java)
                    serviceIntent.putExtra("resultCode", resultCode)
                    serviceIntent.putExtra("data", data)

                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        startForegroundService(serviceIntent)
                    } else {
                        startService(serviceIntent)
                    }

                    Toast.makeText(this, "Screen monitoring started", Toast.LENGTH_SHORT).show()
                } else {
                    Log.e(TAG, "Screen capture permission denied or cancelled")
                    Toast.makeText(this, "Screen capture permission denied", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onActivityResult: ${e.message}", e)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        } finally {
            finish()
        }
    }
}
