package com.example.data.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.data.local.SavedServer
import com.example.data.model.ServerAlertConfig
import java.util.Calendar

object AlertNotificationManager {
    const val CHANNEL_ID = "server_watchdog_alerts"
    const val CHANNEL_NAME = "Server Watchdog Alerts"
    const val CHANNEL_PERSISTENT_ID = "server_watchdog_persistent"
    const val CHANNEL_PERSISTENT_NAME = "Server Watchdog Background Monitor"
    const val EXTRA_DEEP_LINK_SERVER_ID = "deep_link_server_id"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val alertChannel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical alerts when CPU, RAM, disk thresholds are exceeded, services fail, or host is unreachable."
                enableVibration(true)
                setShowBadge(true)
            }
            nm.createNotificationChannel(alertChannel)

            val persistentChannel = NotificationChannel(
                CHANNEL_PERSISTENT_ID,
                CHANNEL_PERSISTENT_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live background monitoring status when ServerUI watchdog is running."
                setShowBadge(false)
                enableVibration(false)
            }
            nm.createNotificationChannel(persistentChannel)
        }
    }

    /**
     * Evaluates whether the current local time falls inside the user-configured mute window.
     * Correctly handles overnight windows (e.g. 22:00 to 07:00).
     */
    fun isWithinMuteWindow(config: ServerAlertConfig, calendar: Calendar = Calendar.getInstance()): Boolean {
        if (!config.muteWindowEnabled) return false

        val currentMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        val startMinutes = config.muteStartHour * 60 + config.muteStartMinute
        val endMinutes = config.muteEndHour * 60 + config.muteEndMinute

        return if (startMinutes <= endMinutes) {
            // Same day mute window (e.g. 13:00 to 17:00)
            currentMinutes in startMinutes..endMinutes
        } else {
            // Overnight mute window (e.g. 22:00 to 07:00)
            currentMinutes >= startMinutes || currentMinutes <= endMinutes
        }
    }

    fun showServerAlert(
        context: Context,
        server: SavedServer,
        title: String,
        message: String,
        notificationId: Int = server.id.hashCode()
    ) {
        ensureChannel(context)

        // Check if server is currently muted
        if (isWithinMuteWindow(server.alertConfig)) {
            return
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_DEEP_LINK_SERVER_ID, server.id)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS runtime permission not yet granted by user
        }
    }

    fun clearServerAlert(context: Context, serverId: String) {
        try {
            NotificationManagerCompat.from(context).cancel(serverId.hashCode())
        } catch (_: Exception) {}
    }
}
