package com.example.data.worker

import android.content.Context
import androidx.work.*
import com.example.data.local.LocalServerStore
import com.example.data.local.SavedServer
import com.example.data.ssh.SshDirectManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class ServerMonitorWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "server_autonomous_watchdog_work"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<ServerMonitorWorker>(
                15, TimeUnit.MINUTES,
                5, TimeUnit.MINUTES // Flex interval
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun runOnce(context: Context) {
            val request = OneTimeWorkRequestBuilder<ServerMonitorWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val store = LocalServerStore(applicationContext)
        val servers = store.getServers()
        val now = System.currentTimeMillis()

        for (server in servers) {
            val config = server.alertConfig
            if (!config.enabled) continue

            // 1. Unreachable check
            val testResult = SshDirectManager.testConnection(server.toDraftRequest())
            if (!testResult.reachable || !testResult.authSuccess) {
                if (config.alertOnUnreachable) {
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

            // 2. Telemetry and metric threshold evaluation
            val metricsRes = SshDirectManager.fetchMetrics(server)
            if (metricsRes.isFailure) {
                continue
            }

            val metrics = metricsRes.getOrNull() ?: continue

            // Persist telemetry snapshot in Room
            try {
                val db = com.example.data.local.db.AppDatabase.getInstance(applicationContext)
                db.telemetryDao().insertSnapshot(
                    com.example.data.local.db.TelemetrySnapshot(
                        serverId = server.id,
                        timestamp = now,
                        cpuUsage = metrics.cpuUsage,
                        memoryUsage = metrics.memoryUsage,
                        diskUsage = metrics.diskUsage,
                        networkRxRate = metrics.netRxBps,
                        networkTxRate = metrics.netTxBps
                    )
                )
                val prefs = applicationContext.getSharedPreferences("serverui_prefs", Context.MODE_PRIVATE)
                val cap = prefs.getInt("key_ring_buffer_cap", 2000)
                db.telemetryDao().enforceRingBufferCap(server.id, cap)
            } catch (_: Exception) {}

            val breachReasons = mutableListOf<String>()

            // Sustained CPU check
            var newBreachStart = server.cpuBreachStartTime
            if (metrics.cpuUsage >= config.cpuThreshold) {
                if (newBreachStart == null) {
                    newBreachStart = now
                }
                val sustainedMs = now - newBreachStart
                val requiredMs = config.cpuSustainedMinutes * 60 * 1000L
                if (sustainedMs >= requiredMs) {
                    val mins = (sustainedMs / 60000L).coerceAtLeast(1)
                    breachReasons.add("CPU at ${String.format(java.util.Locale.US, "%.1f", metrics.cpuUsage)}% (sustained ${mins}m > ${config.cpuThreshold}%)")
                }
            } else {
                newBreachStart = null
            }

            // RAM check
            if (metrics.memoryUsage >= config.ramThreshold) {
                breachReasons.add("RAM at ${String.format(java.util.Locale.US, "%.1f", metrics.memoryUsage)}% (threshold ${config.ramThreshold}%)")
            }

            // Disk check
            if (metrics.diskUsage >= config.diskThreshold) {
                breachReasons.add("Disk at ${String.format(java.util.Locale.US, "%.1f", metrics.diskUsage)}% (threshold ${config.diskThreshold}%)")
            }

            // Failed systemd services check
            if (config.alertOnFailedService && metrics.serviceFailed > 0) {
                breachReasons.add("${metrics.serviceFailed} systemd unit(s) in failed state")
            }

            if (breachReasons.isNotEmpty()) {
                val alertSummary = breachReasons.joinToString("; ")
                store.updateAlertState(server.id, true, alertSummary, newBreachStart)
                AlertNotificationManager.showServerAlert(
                    applicationContext,
                    server,
                    "[ALERT] ${server.name}",
                    alertSummary
                )
            } else {
                // If previously alerting, recover and clear
                if (server.isAlerting) {
                    store.updateAlertState(server.id, false, null, null)
                    AlertNotificationManager.clearServerAlert(applicationContext, server.id)
                }
            }
        }

        Result.success()
    }
}
