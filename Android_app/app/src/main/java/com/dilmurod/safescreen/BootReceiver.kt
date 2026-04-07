package com.dilmurod.safescreen

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val sharedPref = context.getSharedPreferences("SafeScreenPrefs", Context.MODE_PRIVATE)
            val wasRunning = sharedPref.getBoolean("isServiceRunning", false)

            if (wasRunning) {
                val serviceIntent = Intent(context, ScreenMonitorService::class.java)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
        }
    }
}
