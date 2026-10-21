package com.example.data.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Automatically resumes the ServerUI Watchdog background monitor when the device boots
 * or after an app update.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            // Check if persistent background monitoring was enabled
            if (ServerWatchdogForegroundService.isPersistentEnabled(context)) {
                try {
                    ServerWatchdogForegroundService.start(context)
                } catch (_: Exception) {}
            }

            // Also keep periodic WorkManager backup active
            try {
                ServerMonitorWorker.schedule(context)
            } catch (_: Exception) {}
        }
    }
}
