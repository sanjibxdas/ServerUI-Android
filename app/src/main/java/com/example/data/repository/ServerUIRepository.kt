package com.example.data.repository

import android.content.Context
import com.example.data.local.LocalServerStore
import com.example.data.local.SavedServer
import com.example.data.model.*
import com.example.data.ssh.SshDirectManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import com.example.data.local.db.AppDatabase
import com.example.data.local.db.TelemetrySnapshot
import kotlinx.coroutines.flow.Flow

/**
 * Repository for ServerUI on Android.
 * Runs 100% standalone on-device using embedded JSch SSH & SFTP engine.
 * No desktop companion, daemon, or external proxy required.
 */
class ServerUIRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("serverui_prefs", Context.MODE_PRIVATE)
    val localStore = LocalServerStore(context)
    val database = AppDatabase.getInstance(context)
    val telemetryDao = database.telemetryDao()

    companion object {
        private const val KEY_SSH_TIMEOUT = "key_ssh_timeout"
        private const val KEY_KEEP_ALIVE = "key_keep_alive"
        private const val KEY_POLL_INTERVAL = "key_poll_interval"
        private const val KEY_RING_BUFFER_CAP = "key_ring_buffer_cap"
        const val KEY_PERSISTENT_SERVICE_ENABLED = "key_persistent_service_enabled"
        const val KEY_BACKGROUND_POLL_INTERVAL = "key_watchdog_interval_seconds"
        const val MODE_DIRECT = "direct"
        const val DEFAULT_TIMEOUT_SECONDS = 10
        const val DEFAULT_POLL_SECONDS = 3
        const val DEFAULT_RING_BUFFER_CAP = 2000
        const val DEFAULT_BACKGROUND_INTERVAL_SECONDS = 60
    }

    fun isPersistentBackgroundEnabled(): Boolean = prefs.getBoolean(KEY_PERSISTENT_SERVICE_ENABLED, true)

    fun setPersistentBackgroundEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PERSISTENT_SERVICE_ENABLED, enabled).apply()
    }

    fun getBackgroundPollIntervalSeconds(): Int = prefs.getInt(KEY_BACKGROUND_POLL_INTERVAL, DEFAULT_BACKGROUND_INTERVAL_SECONDS)

    fun setBackgroundPollIntervalSeconds(seconds: Int) {
        prefs.edit().putInt(KEY_BACKGROUND_POLL_INTERVAL, seconds).apply()
    }

    fun getRingBufferCap(): Int = prefs.getInt(KEY_RING_BUFFER_CAP, DEFAULT_RING_BUFFER_CAP)

    fun setRingBufferCap(cap: Int) {
        prefs.edit().putInt(KEY_RING_BUFFER_CAP, cap).apply()
    }

    suspend fun recordTelemetry(serverId: String, metrics: ServerMetrics) = withContext(Dispatchers.IO) {
        try {
            telemetryDao.insertSnapshot(
                TelemetrySnapshot(
                    serverId = serverId,
                    timestamp = System.currentTimeMillis(),
                    cpuUsage = metrics.cpuUsage,
                    memoryUsage = metrics.memoryUsage,
                    diskUsage = metrics.diskUsage,
                    networkRxRate = metrics.netRxBps,
                    networkTxRate = metrics.netTxBps
                )
            )
            telemetryDao.enforceRingBufferCap(serverId, getRingBufferCap())
        } catch (_: Exception) {}
    }

    fun getTelemetrySnapshots(serverId: String, window: String): Flow<List<TelemetrySnapshot>> {
        val now = System.currentTimeMillis()
        val since = when (window.lowercase()) {
            "1h" -> now - 3600 * 1000L
            "24h" -> now - 24 * 3600 * 1000L
            "7d" -> now - 7 * 24 * 3600 * 1000L
            else -> now - 3600 * 1000L
        }
        return telemetryDao.getSnapshotsForServerSince(serverId, since)
    }

    init {
        // Enforce Direct Standalone Mode permanently
        prefs.edit().putString("key_operation_mode", MODE_DIRECT).apply()
    }

    fun getOperationMode(): String = MODE_DIRECT

    fun isDirectMode(): Boolean = true

    fun setOperationMode(mode: String) {
        prefs.edit().putString("key_operation_mode", MODE_DIRECT).apply()
    }

    fun getSshTimeoutSeconds(): Int = prefs.getInt(KEY_SSH_TIMEOUT, DEFAULT_TIMEOUT_SECONDS)

    fun setSshTimeoutSeconds(seconds: Int) {
        prefs.edit().putInt(KEY_SSH_TIMEOUT, seconds).apply()
    }

    fun getPollIntervalSeconds(): Int = prefs.getInt(KEY_POLL_INTERVAL, DEFAULT_POLL_SECONDS)

    fun setPollIntervalSeconds(seconds: Int) {
        prefs.edit().putInt(KEY_POLL_INTERVAL, seconds).apply()
    }

    // Health check: Android embedded SSH engine is always ready
    suspend fun checkHealth(): Result<Boolean> = withContext(Dispatchers.IO) {
        Result.success(true)
    }

    suspend fun listServers(): Result<List<ServerPublic>> = withContext(Dispatchers.IO) {
        val list = localStore.getServers().map { it.toPublic(connected = true) }
        Result.success(list)
    }

    suspend fun createServer(input: ServerInput): Result<ServerPublic> = withContext(Dispatchers.IO) {
        val saved = localStore.saveServer(input)
        Result.success(saved.toPublic(connected = true))
    }

    suspend fun updateServer(id: String, input: ServerInput): Result<ServerPublic> = withContext(Dispatchers.IO) {
        val updated = localStore.updateServer(id, input)
        if (updated != null) {
            Result.success(updated.toPublic(connected = true))
        } else {
            Result.failure(Exception("Server not found in local store"))
        }
    }

    suspend fun deleteServer(id: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val deleted = localStore.deleteServer(id)
        Result.success(deleted)
    }

    suspend fun testServerDraft(request: ServerDraftRequest): Result<ServerDraftTest> = withContext(Dispatchers.IO) {
        val test = SshDirectManager.testConnection(request)
        Result.success(test)
    }

    suspend fun testServer(id: String): Result<ServerDraftTest> = withContext(Dispatchers.IO) {
        val server = localStore.getServer(id)
            ?: return@withContext Result.failure(Exception("Server not found in local store"))
        val test = SshDirectManager.testConnection(
            ServerDraftRequest(
                name = server.name,
                host = server.host,
                port = server.port,
                username = server.username,
                authType = server.authType,
                password = server.password,
                privateKey = server.privateKey,
                serverId = server.id
            )
        )
        Result.success(test)
    }

    suspend fun connectServer(id: String): Result<ServerMetrics> = withContext(Dispatchers.IO) {
        val server = localStore.getServer(id)
            ?: return@withContext Result.failure(Exception("Server not found in local store"))
        val res = SshDirectManager.fetchMetrics(server)
        res.getOrNull()?.let { recordTelemetry(id, it) }
        res
    }

    suspend fun disconnectServer(id: String): Result<ServerPublic> = withContext(Dispatchers.IO) {
        val server = localStore.getServer(id)
            ?: return@withContext Result.failure(Exception("Server not found in local store"))
        localStore.updateStatus(id, "offline")
        Result.success(server.toPublic(connected = false))
    }

    suspend fun getLiveMetrics(serverId: String): Result<ServerMetrics> = withContext(Dispatchers.IO) {
        val server = localStore.getServer(serverId)
            ?: return@withContext Result.failure(Exception("Server not found in local store"))
        val res = SshDirectManager.fetchMetrics(server)
        res.getOrNull()?.let { recordTelemetry(serverId, it) }
        res
    }

    suspend fun inspectProcess(serverId: String, pid: Int): Result<ProcessDetail> = withContext(Dispatchers.IO) {
        val server = localStore.getServer(serverId)
            ?: return@withContext Result.failure(Exception("Server not found"))
        val cmd = "ps -p $pid -o pid,ppid,user,%cpu,%mem,comm,args,etime --no-headers"
        val outputRes = SshDirectManager.executeCommand(server, cmd)
        outputRes.mapCatching { out ->
            val parts = out.trim().split("\\s+".toRegex())
            ProcessDetail(
                pid = pid,
                name = parts.getOrNull(5) ?: "process",
                user = parts.getOrNull(2) ?: "root",
                cpu = parts.getOrNull(3)?.toDoubleOrNull() ?: 0.0,
                memory = parts.getOrNull(4)?.toDoubleOrNull() ?: 0.0,
                status = "running",
                command = parts.drop(6).joinToString(" "),
                ppid = parts.getOrNull(1)?.toIntOrNull() ?: 0,
                startedAt = parts.lastOrNull() ?: ""
            )
        }
    }

    suspend fun killProcess(serverId: String, pid: Int): Result<Boolean> = withContext(Dispatchers.IO) {
        val server = localStore.getServer(serverId)
            ?: return@withContext Result.failure(Exception("Server not found"))
        val cmd = "kill -15 $pid 2>/dev/null || kill -9 $pid 2>/dev/null || true"
        SshDirectManager.executeCommand(server, cmd).map { true }
    }

    suspend fun controlService(serverId: String, name: String, action: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val server = localStore.getServer(serverId)
            ?: return@withContext Result.failure(Exception("Server not found"))
        val cmd = "systemctl $action $name"
        SshDirectManager.executeCommand(server, cmd).map { true }
    }

    suspend fun listFiles(serverId: String, path: String): Result<FileListResponse> = withContext(Dispatchers.IO) {
        val server = localStore.getServer(serverId)
            ?: return@withContext Result.failure(Exception("Server not found"))
        SshDirectManager.listFiles(server, path)
    }

    suspend fun readFile(serverId: String, path: String): Result<FilePreview> = withContext(Dispatchers.IO) {
        val server = localStore.getServer(serverId)
            ?: return@withContext Result.failure(Exception("Server not found"))
        SshDirectManager.readFile(server, path)
    }

    suspend fun writeFile(serverId: String, path: String, content: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val server = localStore.getServer(serverId)
            ?: return@withContext Result.failure(Exception("Server not found"))
        SshDirectManager.writeFile(server, path, content)
    }

    suspend fun createFile(serverId: String, path: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val server = localStore.getServer(serverId)
            ?: return@withContext Result.failure(Exception("Server not found"))
        SshDirectManager.writeFile(server, path, "")
    }

    suspend fun mkdir(serverId: String, path: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val server = localStore.getServer(serverId)
            ?: return@withContext Result.failure(Exception("Server not found"))
        SshDirectManager.makeDir(server, path)
    }

    suspend fun rename(serverId: String, from: String, to: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val server = localStore.getServer(serverId)
            ?: return@withContext Result.failure(Exception("Server not found"))
        SshDirectManager.rename(server, from, to)
    }

    suspend fun deleteFile(serverId: String, path: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val server = localStore.getServer(serverId)
            ?: return@withContext Result.failure(Exception("Server not found"))
        SshDirectManager.deleteFile(server, path)
    }

    suspend fun compress(serverId: String, path: String, format: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val server = localStore.getServer(serverId)
            ?: return@withContext Result.failure(Exception("Server not found"))
        val target = if (format == "zip") "$path.zip" else "$path.tar.gz"
        val cmd = if (format == "zip") "zip -r '$target' '$path'" else "tar -czf '$target' '$path'"
        SshDirectManager.executeCommand(server, cmd).map { true }
    }

    suspend fun downloadFile(serverId: String, remotePath: String, outputStream: java.io.OutputStream): Result<Boolean> = withContext(Dispatchers.IO) {
        val server = localStore.getServer(serverId)
            ?: return@withContext Result.failure(Exception("Server not found"))
        SshDirectManager.downloadFile(server, remotePath, outputStream)
    }

    suspend fun uploadFile(serverId: String, remotePath: String, inputStream: java.io.InputStream): Result<Boolean> = withContext(Dispatchers.IO) {
        val server = localStore.getServer(serverId)
            ?: return@withContext Result.failure(Exception("Server not found"))
        SshDirectManager.uploadFile(server, remotePath, inputStream)
    }

    suspend fun bulkDelete(serverId: String, paths: List<String>): Result<Boolean> = withContext(Dispatchers.IO) {
        val server = localStore.getServer(serverId)
            ?: return@withContext Result.failure(Exception("Server not found"))
        SshDirectManager.bulkDelete(server, paths)
    }

    suspend fun bulkCopy(serverId: String, paths: List<String>, destDir: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val server = localStore.getServer(serverId)
            ?: return@withContext Result.failure(Exception("Server not found"))
        SshDirectManager.bulkCopy(server, paths, destDir)
    }

    suspend fun bulkMove(serverId: String, paths: List<String>, destDir: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val server = localStore.getServer(serverId)
            ?: return@withContext Result.failure(Exception("Server not found"))
        SshDirectManager.bulkMove(server, paths, destDir)
    }

    suspend fun chmod(serverId: String, path: String, octalPermissions: Int): Result<Boolean> = withContext(Dispatchers.IO) {
        val server = localStore.getServer(serverId)
            ?: return@withContext Result.failure(Exception("Server not found"))
        SshDirectManager.chmod(server, path, octalPermissions)
    }

    suspend fun chown(serverId: String, path: String, owner: String, group: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val server = localStore.getServer(serverId)
            ?: return@withContext Result.failure(Exception("Server not found"))
        SshDirectManager.chown(server, path, owner, group)
    }

    suspend fun searchFiles(serverId: String, dir: String, query: String): Result<List<FileEntry>> = withContext(Dispatchers.IO) {
        val server = localStore.getServer(serverId)
            ?: return@withContext Result.failure(Exception("Server not found"))
        SshDirectManager.searchFiles(server, dir, query)
    }

    fun getBookmarks(serverId: String): List<String> = localStore.getBookmarks(serverId)

    fun addBookmark(serverId: String, path: String): List<String> = localStore.addBookmark(serverId, path)

    fun removeBookmark(serverId: String, path: String): List<String> = localStore.removeBookmark(serverId, path)

    fun updateAlertConfig(serverId: String, config: ServerAlertConfig) {
        localStore.updateAlertConfig(serverId, config)
    }

    fun updateAlertState(serverId: String, isAlerting: Boolean, reason: String?, breachStart: Long? = null) {
        localStore.updateAlertState(serverId, isAlerting, reason, breachStart)
    }
}
