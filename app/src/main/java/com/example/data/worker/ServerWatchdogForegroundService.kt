package com.example.data.worker

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.local.LocalServerStore
import com.example.data.local.SavedServer
import com.example.data.local.db.AppDatabase
import com.example.data.local.db.TelemetrySnapshot
import com.example.data.ssh.SshDirectManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Persistent Foreground Service that runs continuously in the background,
 * even when the user closes or swipes away ServerUI.
 *
 * Backed by an ongoing status notification (low priority, silent) and partial WakeLock
 * to ensure scheduled SSH polling cycles complete reliably.
 */
class ServerWatchdogForegroundService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var loopJob: Job? = null

    companion object {
        const val NOTIFICATION_ID = 2001
        const val ACTION_START = "com.example.action.START_WATCHDOG"
        const val ACTION_STOP = "com.example.action.STOP_WATCHDOG"
        const val ACTION_TRIGGER_CHECK = "com.example.action.TRIGGER_CHECK"

        const val PREFS_NAME = "serverui_prefs"
        const val KEY_PERSISTENT_ENABLED = "key_persistent_service_enabled"
        const val KEY_WATCHDOG_INTERVAL_SECONDS = "key_watchdog_interval_seconds"
        const val DEFAULT_INTERVAL_SECONDS = 60

        private val _isRunningFlow = MutableStateFlow(false)
        val isRunningFlow: StateFlow<Boolean> = _isRunningFlow.asStateFlow()

        private val _lastCheckSummary = MutableStateFlow("Initializing...")
        val lastCheckSummary: StateFlow<String> = _lastCheckSummary.asStateFlow()

        private val _lastCheckTimestamp = MutableStateFlow<Long?>(null)
        val lastCheckTimestamp: StateFlow<Long?> = _lastCheckTimestamp.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, ServerWatchdogForegroundService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, ServerWatchdogForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun triggerCheck(context: Context) {
            val intent = Intent(context, ServerWatchdogForegroundService::class.java).apply {
                action = ACTION_TRIGGER_CHECK
            }
            context.startService(intent)
        }

        fun isPersistentEnabled(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_PERSISTENT_ENABLED, true)
        }

        fun setPersistentEnabled(context: Context, enabled: Boolean) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(KEY_PERSISTENT_ENABLED, enabled).apply()
        }

        fun getIntervalSeconds(context: Context): Int {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getInt(KEY_WATCHDOG_INTERVAL_SECONDS, DEFAULT_INTERVAL_SECONDS)
        }

        fun setIntervalSeconds(context: Context, seconds: Int) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putInt(KEY_WATCHDOG_INTERVAL_SECONDS, seconds).apply()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        AlertNotificationManager.ensureChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        when (action) {
            ACTION_STOP -> {
                setPersistentEnabled(this, false)
                stopForegroundService()
                return START_NOT_STICKY
            }
            ACTION_TRIGGER_CHECK -> {
                serviceScope.launch {
                    runSingleWatchdogSweep()
                }
            }
            ACTION_START -> {
                setPersistentEnabled(this, true)
                startAsForeground()
                startMonitoringLoop()
            }
        }

        return START_STICKY
    }

    private fun startAsForeground() {
        val notification = buildPersistentNotification("Starting ServerUI background monitor...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        _isRunningFlow.value = true
    }

    private fun buildPersistentNotification(contentText: String): Notification {
        AlertNotificationManager.ensureChannel(this)

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val checkNowIntent = Intent(this, ServerWatchdogForegroundService::class.java).apply {
            action = ACTION_TRIGGER_CHECK
        }
        val checkNowPendingIntent = PendingIntent.getService(
            this,
            1,
            checkNowIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, ServerWatchdogForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            2,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, AlertNotificationManager.CHANNEL_PERSISTENT_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("ServerUI Watchdog Active")
            .setContentText(contentText)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(openAppPendingIntent)
            .addAction(android.R.drawable.ic_menu_rotate, "Check Now", checkNowPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .build()
    }

    private fun updateNotification(contentText: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
        nm?.notify(NOTIFICATION_ID, buildPersistentNotification(contentText))
    }

    private fun startMonitoringLoop() {
        if (loopJob?.isActive == true) return

        loopJob = serviceScope.launch {
            while (isActive) {
                runSingleWatchdogSweep()

                val intervalSec = getIntervalSeconds(this@ServerWatchdogForegroundService)
                delay(intervalSec * 1000L)
            }
        }
    }

    private suspend fun runSingleWatchdogSweep() = withContext(Dispatchers.IO) {
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "ServerUI::WatchdogSweepWakeLock"
        )

        try {
            wakeLock?.acquire(30000L) // 30s max wake lock safety timeout

            val store = LocalServerStore(applicationContext)
            val servers = store.getServers()
            val enabledServers = servers.filter { it.alertConfig.enabled }
            val now = System.currentTimeMillis()

            if (enabledServers.isEmpty()) {
                val summary = "Active • 0 servers configured for watchdog"
                _lastCheckSummary.value = summary
                _lastCheckTimestamp.value = now
                updateNotification(summary)
                return@withContext
            }

            var alertsDetected = 0
            val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

            for (server in enabledServers) {
                val config = server.alertConfig

                // 1. Unreachable check
                val testResult = SshDirectManager.testConnection(server.toDraftRequest())
                if (!testResult.reachable || !testResult.authSuccess) {
                    if (config.alertOnUnreachable) {
                        alertsDetected++
                        val reason = "SSH port ${server.port} unreachable (${testResult.error ?: "Connection failed"})"
                        store.updateAlertState(server.id, true, reason, null)
                        AlertNotificationManager.showServerAlert(
                            applicationContext,
                            server,
                            "[ALERT] ${server.name}: Host Unreachable",
                            reason
                        )
                    }
                    continue
                }

                // 2. Fetch metrics
                val metricsRes = SshDirectManager.fetchMetrics(server)
                if (metricsRes.isFailure) {
                    continue
                }

                val metrics = metricsRes.getOrNull() ?: continue

                // 3. Persist Telemetry to Room
                try {
                    val db = AppDatabase.getInstance(applicationContext)
                    db.telemetryDao().insertSnapshot(
                        TelemetrySnapshot(
                            serverId = server.id,
                            timestamp = now,
                            cpuUsage = metrics.cpuUsage,
                            memoryUsage = metrics.memoryUsage,
                            diskUsage = metrics.diskUsage,
                            networkRxRate = metrics.netRxBps,
                            networkTxRate = metrics.netTxBps
                        )
                    )
                    val cap = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .getInt("key_ring_buffer_cap", 2000)
                    db.telemetryDao().enforceRingBufferCap(server.id, cap)
                } catch (_: Exception) {}

                // 4. Threshold checks
                val breachReasons = mutableListOf<String>()

                // CPU
                var newBreachStart = server.cpuBreachStartTime
                if (metrics.cpuUsage >= config.cpuThreshold) {
                    if (newBreachStart == null) {
                        newBreachStart = now
                    }
                    val sustainedMs = now - newBreachStart
                    val requiredMs = config.cpuSustainedMinutes * 60 * 1000L
                    if (sustainedMs >= requiredMs) {
                        val mins = (sustainedMs / 60000L).coerceAtLeast(1)
                        breachReasons.add("CPU at ${String.format(Locale.US, "%.1f", metrics.cpuUsage)}% (sustained ${mins}m > ${config.cpuThreshold}%)")
                    }
                } else {
                    newBreachStart = null
                }

                // RAM
                if (metrics.memoryUsage >= config.ramThreshold) {
                    breachReasons.add("RAM at ${String.format(Locale.US, "%.1f", metrics.memoryUsage)}% (threshold ${config.ramThreshold}%)")
                }

                // Disk
                if (metrics.diskUsage >= config.diskThreshold) {
                    breachReasons.add("Disk at ${String.format(Locale.US, "%.1f", metrics.diskUsage)}% (threshold ${config.diskThreshold}%)")
                }

                // Failed Services
                if (config.alertOnFailedService && metrics.serviceFailed > 0) {
                    breachReasons.add("${metrics.serviceFailed} failed systemd services detected")
                }

                if (breachReasons.isNotEmpty()) {
                    alertsDetected++
                    val fullReason = breachReasons.joinToString("\n• ")
                    store.updateAlertState(server.id, true, "• $fullReason", newBreachStart)
                    AlertNotificationManager.showServerAlert(
                        applicationContext,
                        server,
                        "[ALERT] ${server.name}: Threshold Breached",
                        "• $fullReason"
                    )
                } else {
                    if (server.isAlerting) {
                        store.updateAlertState(server.id, false, null, null)
                        AlertNotificationManager.clearServerAlert(applicationContext, server.id)
                    } else if (newBreachStart != server.cpuBreachStartTime) {
                        store.updateAlertState(server.id, false, null, newBreachStart)
                    }
                }
            }

            val summary = if (alertsDetected > 0) {
                "${timeFmt.format(Date(now))} • $alertsDetected alert(s) on ${enabledServers.size} servers"
            } else {
                "${timeFmt.format(Date(now))} • ${enabledServers.size} server(s) normal"
            }

            _lastCheckSummary.value = summary
            _lastCheckTimestamp.value = now
            updateNotification(summary)

        } catch (e: Exception) {
            _lastCheckSummary.value = "Error during check: ${e.message}"
        } finally {
            try {
                if (wakeLock?.isHeld == true) {
                    wakeLock.release()
                }
            } catch (_: Exception) {}
        }
    }

    private fun stopForegroundService() {
        loopJob?.cancel()
        _isRunningFlow.value = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        _isRunningFlow.value = false
    }
}
