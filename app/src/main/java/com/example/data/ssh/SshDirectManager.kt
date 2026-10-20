package com.example.data.ssh

import com.example.data.local.SavedServer
import com.example.data.model.*
import com.jcraft.jsch.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.Vector

class DirectTerminalSession(
    private val session: Session,
    private val channel: ChannelShell,
    private val outputStream: OutputStream,
    val inputStream: InputStream
) {
    fun sendInput(input: String) {
        try {
            outputStream.write(input.toByteArray(Charsets.UTF_8))
            outputStream.flush()
        } catch (_: Exception) {}
    }

    fun close() {
        try {
            channel.disconnect()
            session.disconnect()
        } catch (_: Exception) {}
    }
}

object SshDirectManager {

    private fun createSession(
        host: String,
        port: Int,
        username: String,
        authType: String,
        password: String?,
        privateKey: String?,
        timeoutMs: Int = 10000
    ): Session {
        val jsch = JSch()
        if (authType == "private_key" && !privateKey.isNullOrBlank()) {
            val cleanKey = privateKey.trim().replace("\r\n", "\n")
            val keyString = if (cleanKey.endsWith("\n")) cleanKey else "$cleanKey\n"
            val keyBytes = keyString.toByteArray(Charsets.UTF_8)
            jsch.addIdentity("key_${System.currentTimeMillis()}", keyBytes, null, null)
        }

        val cleanHost = host.trim()
        val cleanUser = username.trim().ifBlank { "root" }
        val targetPort = if (port > 0) port else 22
        val session = jsch.getSession(cleanUser, cleanHost, targetPort)
        if (authType == "password" && !password.isNullOrBlank()) {
            session.setPassword(password)
        }

        val config = Properties()
        config["StrictHostKeyChecking"] = "no"
        config["PreferredAuthentications"] = "publickey,keyboard-interactive,password"
        // Ensure compatibility with modern Ubuntu / OpenSSH 8.8+ servers
        config["server_host_key"] = "ssh-ed25519,ecdsa-sha2-nistp256,ecdsa-sha2-nistp384,ecdsa-sha2-nistp521,rsa-sha2-512,rsa-sha2-256,ssh-rsa"
        config["PubkeyAcceptedAlgorithms"] = "ssh-ed25519,ecdsa-sha2-nistp256,ecdsa-sha2-nistp384,ecdsa-sha2-nistp521,rsa-sha2-512,rsa-sha2-256,ssh-rsa"
        session.setConfig(config)
        session.timeout = timeoutMs
        session.connect(timeoutMs)
        return session
    }

    suspend fun testConnection(request: ServerDraftRequest): ServerDraftTest = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        var session: Session? = null
        try {
            session = createSession(
                host = request.host,
                port = request.port,
                username = request.username,
                authType = request.authType,
                password = request.password,
                privateKey = request.privateKey,
                timeoutMs = 10000
            )
            val latency = System.currentTimeMillis() - startTime
            val banner = session.serverVersion ?: "SSH-2.0"
            ServerDraftTest(
                reachable = true,
                banner = banner,
                authSuccess = true,
                latencyMs = latency,
                error = null
            )
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - startTime
            val raw = e.localizedMessage ?: e.message ?: "Connection failed"
            val readable = when {
                raw.contains("Auth fail", ignoreCase = true) ->
                    "Authentication failed: Check username, password, or SSH private key."
                raw.contains("UnknownHost", ignoreCase = true) ->
                    "Cannot resolve host: Check the server IP or hostname."
                raw.contains("timeout", ignoreCase = true) || raw.contains("timed out", ignoreCase = true) ->
                    "Connection timed out (10s): Server is unreachable or port ${request.port} is blocked by a firewall."
                raw.contains("Connection refused", ignoreCase = true) ->
                    "Connection refused on port ${request.port}: SSH is not running on target."
                raw.contains("invalid privatekey", ignoreCase = true) ->
                    "Invalid private key format: Ensure a valid PEM or OpenSSH private key is pasted."
                else -> raw
            }
            ServerDraftTest(
                reachable = false,
                banner = null,
                authSuccess = false,
                latencyMs = latency,
                error = readable
            )
        } finally {
            try {
                session?.disconnect()
            } catch (_: Exception) {}
        }
    }

    suspend fun executeCommand(server: SavedServer, command: String, timeoutMs: Int = 10000): Result<String> = withContext(Dispatchers.IO) {
        var session: Session? = null
        var channel: ChannelExec? = null
        try {
            session = createSession(
                host = server.host,
                port = server.port,
                username = server.username,
                authType = server.authType,
                password = server.password,
                privateKey = server.privateKey,
                timeoutMs = timeoutMs
            )
            channel = session.openChannel("exec") as ChannelExec
            channel.setCommand(command)
            val outputStream = ByteArrayOutputStream()
            channel.outputStream = outputStream
            channel.setErrStream(outputStream)
            channel.connect(timeoutMs)

            val start = System.currentTimeMillis()
            while (!channel.isClosed && System.currentTimeMillis() - start < timeoutMs) {
                Thread.sleep(50)
            }
            Result.success(outputStream.toString("UTF-8"))
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try { channel?.disconnect() } catch (_: Exception) {}
            try { session?.disconnect() } catch (_: Exception) {}
        }
    }

    suspend fun fetchMetrics(server: SavedServer): Result<ServerMetrics> = withContext(Dispatchers.IO) {
        val probeScript = """
            echo "---STAT---"
            cat /proc/stat | head -n 1
            echo "---MEM---"
            cat /proc/meminfo | head -n 8
            echo "---LOAD---"
            cat /proc/loadavg
            echo "---UPTIME---"
            cat /proc/uptime
            echo "---DF---"
            df -k / | tail -n 1
            echo "---UNAME---"
            uname -a
            echo "---NET---"
            cat /proc/net/dev | grep -v 'lo:' | tail -n +3 | head -n 3
            echo "---PS---"
            ps -eo pid,ppid,user,%cpu,%mem,comm --sort=-%cpu | head -n 25
            echo "---SVC---"
            systemctl list-units --type=service --state=running,failed --no-pager --no-legend 2>/dev/null | head -n 25 || true
            echo "---END---"
        """.trimIndent()

        executeCommand(server, probeScript, 10000).mapCatching { rawOutput ->
            parseServerMetrics(server, rawOutput)
        }
    }

    private fun parseServerMetrics(server: SavedServer, raw: String): ServerMetrics {
        var cpuUsage = 0.0
        var memTotal = 0L
        var memAvailable = 0L
        var memUsed = 0L
        var memPercent = 0.0
        var diskTotal = 0L
        var diskUsed = 0L
        var diskFree = 0L
        var diskPercent = 0.0
        var load1 = "0.0"
        var load5 = "0.0"
        var load15 = "0.0"
        var uptimeStr = "0m"
        var uptimeSeconds = 0L
        var kernelStr = "Linux"
        var netRxBytes = 0L
        var netTxBytes = 0L
        val processes = mutableListOf<ProcessInfo>()
        val services = mutableListOf<ServiceInfo>()
        var serviceRunning = 0
        var serviceFailed = 0

        val lines = raw.lines()
        var currentSection = ""

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("---") && trimmed.endsWith("---")) {
                currentSection = trimmed.replace("-", "")
                continue
            }

            when (currentSection) {
                "STAT" -> {
                    // cpu  user nice system idle iowait irq softirq
                    val parts = trimmed.split("\\s+".toRegex())
                    if (parts.size >= 5 && parts[0] == "cpu") {
                        val user = parts[1].toDoubleOrNull() ?: 0.0
                        val nice = parts[2].toDoubleOrNull() ?: 0.0
                        val system = parts[3].toDoubleOrNull() ?: 0.0
                        val idle = parts[4].toDoubleOrNull() ?: 0.0
                        val total = user + nice + system + idle
                        if (total > 0) {
                            cpuUsage = ((total - idle) / total) * 100.0
                        }
                    }
                }
                "MEM" -> {
                    if (trimmed.startsWith("MemTotal:")) {
                        val kb = trimmed.split("\\s+".toRegex()).getOrNull(1)?.toLongOrNull() ?: 0L
                        memTotal = kb * 1024L
                    } else if (trimmed.startsWith("MemAvailable:")) {
                        val kb = trimmed.split("\\s+".toRegex()).getOrNull(1)?.toLongOrNull() ?: 0L
                        memAvailable = kb * 1024L
                    }
                }
                "LOAD" -> {
                    val parts = trimmed.split("\\s+".toRegex())
                    if (parts.size >= 3) {
                        load1 = parts[0]
                        load5 = parts[1]
                        load15 = parts[2]
                    }
                }
                "UPTIME" -> {
                    val sec = trimmed.split("\\s+".toRegex()).firstOrNull()?.toDoubleOrNull() ?: 0.0
                    val totalMins = (sec / 60).toLong()
                    val days = totalMins / (24 * 60)
                    val hours = (totalMins % (24 * 60)) / 60
                    val mins = totalMins % 60
                uptimeSeconds = sec.toLong()
                    uptimeStr = if (days > 0) "${days}d ${hours}h ${mins}m" else "${hours}h ${mins}m"
                }
                "DF" -> {
                    val parts = trimmed.split("\\s+".toRegex())
                    if (parts.size >= 5) {
                        val totalKb = parts[1].toLongOrNull() ?: 0L
                        val usedKb = parts[2].toLongOrNull() ?: 0L
                        val freeKb = parts[3].toLongOrNull() ?: 0L
                        diskTotal = totalKb * 1024L
                        diskUsed = usedKb * 1024L
                        diskFree = freeKb * 1024L
                        if (diskTotal > 0) {
                            diskPercent = (diskUsed.toDouble() / diskTotal.toDouble()) * 100.0
                        }
                    }
                }
                "UNAME" -> {
                    if (trimmed.isNotEmpty()) {
                        kernelStr = trimmed
                    }
                }
                "NET" -> {
                    // eth0: 12345 12 ... 67890
                    val colonIdx = trimmed.indexOf(':')
                    if (colonIdx != -1) {
                        val data = trimmed.substring(colonIdx + 1).trim().split("\\s+".toRegex())
                        if (data.size >= 9) {
                            netRxBytes += data[0].toLongOrNull() ?: 0L
                            netTxBytes += data[8].toLongOrNull() ?: 0L
                        }
                    }
                }
                "PS" -> {
                    val parts = trimmed.split("\\s+".toRegex())
                    if (parts.size >= 6 && parts[0] != "PID") {
                        val pid = parts[0].toIntOrNull() ?: 0
                        val ppid = parts[1].toIntOrNull() ?: 0
                        val user = parts[2]
                        val cpu = parts[3].toDoubleOrNull() ?: 0.0
                        val mem = parts[4].toDoubleOrNull() ?: 0.0
                        val comm = parts.drop(5).joinToString(" ")
                        if (pid > 0) {
                            processes.add(
                                ProcessInfo(
                                    pid = pid,
                                    name = comm,
                                    user = user,
                                    cpu = cpu,
                                    memory = mem,
                                    command = comm
                                )
                            )
                        }
                    }
                }
                "SVC" -> {
                    // UNIT LOAD ACTIVE SUB DESCRIPTION
                    val parts = trimmed.split("\\s+".toRegex())
                    if (parts.size >= 4) {
                        val name = parts[0]
                        val load = parts[1]
                        val active = parts[2]
                        val sub = parts[3]
                        val desc = parts.drop(4).joinToString(" ")
                        if (name.endsWith(".service")) {
                            if (active == "active") serviceRunning++
                            if (active == "failed") serviceFailed++
                            services.add(
                                ServiceInfo(
                                    name = name,
                                    load = load,
                                    active = active,
                                    sub = sub,
                                    description = desc
                                )
                            )
                        }
                    }
                }
            }
        }

        if (memTotal > 0 && memAvailable > 0) {
            memUsed = (memTotal - memAvailable).coerceAtLeast(0L)
            memPercent = (memUsed.toDouble() / memTotal.toDouble()) * 100.0
        }

        return ServerMetrics(
            id = server.id,
            name = server.name,
            hostname = server.host,
            host = server.host,
            port = server.port,
            username = server.username,
            authType = server.authType,
            status = "online",
            connected = true,
            cpuUsage = cpuUsage,
            cpuCores = 2,
            memoryTotalBytes = memTotal,
            memoryUsedBytes = memUsed,
            memoryAvailableBytes = memAvailable,
            memoryUsage = memPercent,
            diskTotalBytes = diskTotal,
            diskUsedBytes = diskUsed,
            diskFreeBytes = diskFree,
            diskUsage = diskPercent,
            load1 = load1.toDoubleOrNull() ?: 0.0,
            load5 = load5.toDoubleOrNull() ?: 0.0,
            load15 = load15.toDoubleOrNull() ?: 0.0,
            uptimeSeconds = uptimeSeconds,
            kernel = kernelStr,
            netRxBytes = netRxBytes,
            netTxBytes = netTxBytes,
            processes = processes,
            services = services,
            processCount = processes.size,
            processRunning = processes.count { it.cpu > 0.0 },
            serviceRunning = serviceRunning,
            serviceFailed = serviceFailed
        )
    }

    // SFTP Operations
    suspend fun listFiles(server: SavedServer, path: String): Result<FileListResponse> = withContext(Dispatchers.IO) {
        var session: Session? = null
        var sftp: ChannelSftp? = null
        try {
            session = createSession(
                host = server.host,
                port = server.port,
                username = server.username,
                authType = server.authType,
                password = server.password,
                privateKey = server.privateKey
            )
            sftp = session.openChannel("sftp") as ChannelSftp
            sftp.connect(10000)

            val targetPath = if (path.isBlank() || path == "~") sftp.home else path
            val list = sftp.ls(targetPath) as Vector<ChannelSftp.LsEntry>

            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
            val entries = list.mapNotNull { entry ->
                val filename = entry.filename
                if (filename == "." || filename == "..") return@mapNotNull null
                val attrs = entry.attrs
                val isDir = attrs.isDir
                val size = attrs.size
                val mTime = attrs.mTime.toLong() * 1000L
                val dateStr = sdf.format(Date(mTime))
                val fullPath = if (targetPath.endsWith("/")) "$targetPath$filename" else "$targetPath/$filename"

                FileEntry(
                    name = filename,
                    path = fullPath,
                    type = if (isDir) "dir" else "file",
                    size = size,
                    mode = attrs.permissionsString,
                    modified = dateStr
                )
            }

            Result.success(FileListResponse(path = targetPath, entries = entries))
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try { sftp?.disconnect() } catch (_: Exception) {}
            try { session?.disconnect() } catch (_: Exception) {}
        }
    }

    suspend fun readFile(server: SavedServer, path: String): Result<FilePreview> = withContext(Dispatchers.IO) {
        var session: Session? = null
        var sftp: ChannelSftp? = null
        try {
            session = createSession(
                host = server.host,
                port = server.port,
                username = server.username,
                authType = server.authType,
                password = server.password,
                privateKey = server.privateKey
            )
            sftp = session.openChannel("sftp") as ChannelSftp
            sftp.connect(10000)

            val out = ByteArrayOutputStream()
            sftp.get(path, out)
            val bytes = out.toByteArray()
            val textContent = String(bytes, Charsets.UTF_8)

            Result.success(
                FilePreview(
                    path = path,
                    content = textContent,
                    size = bytes.size.toLong(),
                    truncated = false,
                    mime = "text/plain",
                    binary = false,
                    writable = bytes.size < 500000
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try { sftp?.disconnect() } catch (_: Exception) {}
            try { session?.disconnect() } catch (_: Exception) {}
        }
    }

    suspend fun writeFile(server: SavedServer, path: String, content: String): Result<Boolean> = withContext(Dispatchers.IO) {
        var session: Session? = null
        var sftp: ChannelSftp? = null
        try {
            session = createSession(
                host = server.host,
                port = server.port,
                username = server.username,
                authType = server.authType,
                password = server.password,
                privateKey = server.privateKey
            )
            sftp = session.openChannel("sftp") as ChannelSftp
            sftp.connect(10000)

            val input = ByteArrayInputStream(content.toByteArray(Charsets.UTF_8))
            sftp.put(input, path, ChannelSftp.OVERWRITE)
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try { sftp?.disconnect() } catch (_: Exception) {}
            try { session?.disconnect() } catch (_: Exception) {}
        }
    }

    suspend fun makeDir(server: SavedServer, path: String): Result<Boolean> = withContext(Dispatchers.IO) {
        var session: Session? = null
        var sftp: ChannelSftp? = null
        try {
            session = createSession(server.host, server.port, server.username, server.authType, server.password, server.privateKey)
            sftp = session.openChannel("sftp") as ChannelSftp
            sftp.connect(10000)
            sftp.mkdir(path)
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try { sftp?.disconnect() } catch (_: Exception) {}
            try { session?.disconnect() } catch (_: Exception) {}
        }
    }

    suspend fun deleteFile(server: SavedServer, path: String): Result<Boolean> = withContext(Dispatchers.IO) {
        var session: Session? = null
        var sftp: ChannelSftp? = null
        try {
            session = createSession(server.host, server.port, server.username, server.authType, server.password, server.privateKey)
            sftp = session.openChannel("sftp") as ChannelSftp
            sftp.connect(10000)
            try {
                sftp.rm(path)
            } catch (_: Exception) {
                sftp.rmdir(path)
            }
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try { sftp?.disconnect() } catch (_: Exception) {}
            try { session?.disconnect() } catch (_: Exception) {}
        }
    }

    suspend fun rename(server: SavedServer, oldPath: String, newPath: String): Result<Boolean> = withContext(Dispatchers.IO) {
        var session: Session? = null
        var sftp: ChannelSftp? = null
        try {
            session = createSession(server.host, server.port, server.username, server.authType, server.password, server.privateKey)
            sftp = session.openChannel("sftp") as ChannelSftp
            sftp.connect(10000)
            sftp.rename(oldPath, newPath)
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try { sftp?.disconnect() } catch (_: Exception) {}
            try { session?.disconnect() } catch (_: Exception) {}
        }
    }

    suspend fun downloadFile(
        server: SavedServer,
        remotePath: String,
        outputStream: OutputStream
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        var session: Session? = null
        var sftp: ChannelSftp? = null
        try {
            session = createSession(server.host, server.port, server.username, server.authType, server.password, server.privateKey)
            sftp = session.openChannel("sftp") as ChannelSftp
            sftp.connect(10000)
            sftp.get(remotePath, outputStream)
            outputStream.flush()
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try { sftp?.disconnect() } catch (_: Exception) {}
            try { session?.disconnect() } catch (_: Exception) {}
        }
    }

    suspend fun uploadFile(
        server: SavedServer,
        remotePath: String,
        inputStream: InputStream
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        var session: Session? = null
        var sftp: ChannelSftp? = null
        try {
            session = createSession(server.host, server.port, server.username, server.authType, server.password, server.privateKey)
            sftp = session.openChannel("sftp") as ChannelSftp
            sftp.connect(10000)
            sftp.put(inputStream, remotePath, ChannelSftp.OVERWRITE)
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try { sftp?.disconnect() } catch (_: Exception) {}
            try { session?.disconnect() } catch (_: Exception) {}
        }
    }

    suspend fun bulkDelete(server: SavedServer, paths: List<String>): Result<Boolean> = withContext(Dispatchers.IO) {
        if (paths.isEmpty()) return@withContext Result.success(true)
        var session: Session? = null
        var sftp: ChannelSftp? = null
        try {
            session = createSession(server.host, server.port, server.username, server.authType, server.password, server.privateKey)
            sftp = session.openChannel("sftp") as ChannelSftp
            sftp.connect(10000)
            for (p in paths) {
                try {
                    sftp.rm(p)
                } catch (_: Exception) {
                    try { sftp.rmdir(p) } catch (_: Exception) {}
                }
            }
            Result.success(true)
        } catch (e: Exception) {
            // Fallback to rm -rf via command exec if permissions or directory nesting requires it
            val escaped = paths.joinToString(" ") { "'${it.replace("'", "'\\''")}'" }
            executeCommand(server, "rm -rf $escaped", 15000).map { true }
        } finally {
            try { sftp?.disconnect() } catch (_: Exception) {}
            try { session?.disconnect() } catch (_: Exception) {}
        }
    }

    suspend fun bulkCopy(server: SavedServer, paths: List<String>, destDir: String): Result<Boolean> = withContext(Dispatchers.IO) {
        if (paths.isEmpty()) return@withContext Result.success(true)
        val escapedSources = paths.joinToString(" ") { "'${it.replace("'", "'\\''")}'" }
        val escapedDest = "'${destDir.replace("'", "'\\''")}'"
        executeCommand(server, "cp -r $escapedSources $escapedDest", 15000).map { true }
    }

    suspend fun bulkMove(server: SavedServer, paths: List<String>, destDir: String): Result<Boolean> = withContext(Dispatchers.IO) {
        if (paths.isEmpty()) return@withContext Result.success(true)
        val escapedSources = paths.joinToString(" ") { "'${it.replace("'", "'\\''")}'" }
        val escapedDest = "'${destDir.replace("'", "'\\''")}'"
        executeCommand(server, "mv $escapedSources $escapedDest", 15000).map { true }
    }

    suspend fun chmod(server: SavedServer, path: String, octalPermissions: Int): Result<Boolean> = withContext(Dispatchers.IO) {
        var session: Session? = null
        var sftp: ChannelSftp? = null
        try {
            session = createSession(server.host, server.port, server.username, server.authType, server.password, server.privateKey)
            sftp = session.openChannel("sftp") as ChannelSftp
            sftp.connect(10000)
            sftp.chmod(octalPermissions, path)
            Result.success(true)
        } catch (e: Exception) {
            // Fallback to exec chmod
            val octalStr = Integer.toOctalString(octalPermissions)
            val escaped = "'${path.replace("'", "'\\''")}'"
            executeCommand(server, "chmod $octalStr $escaped", 8000).map { true }
        } finally {
            try { sftp?.disconnect() } catch (_: Exception) {}
            try { session?.disconnect() } catch (_: Exception) {}
        }
    }

    suspend fun chown(server: SavedServer, path: String, owner: String, group: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val targetOwner = if (group.isNotBlank()) "$owner:$group" else owner
        val escaped = "'${path.replace("'", "'\\''")}'"
        executeCommand(server, "chown '$targetOwner' $escaped", 8000).map { true }
    }

    suspend fun searchFiles(server: SavedServer, dir: String, query: String): Result<List<FileEntry>> = withContext(Dispatchers.IO) {
        val sanitizedQuery = query.replace("'", "").trim()
        val escapedDir = "'${dir.replace("'", "'\\''")}'"
        val cmd = "find $escapedDir -maxdepth 6 -iname '*$sanitizedQuery*' -printf '%y\\t%s\\t%m\\t%TY-%Tm-%Td %TH:%TM\\t%p\\n' 2>/dev/null | head -n 100"
        executeCommand(server, cmd, 12000).mapCatching { rawOutput ->
            rawOutput.lines().filter { it.isNotBlank() }.mapNotNull { line ->
                val parts = line.split("\t")
                if (parts.size >= 5) {
                    val typeChar = parts[0]
                    val size = parts[1].toLongOrNull() ?: 0L
                    val mode = parts[2]
                    val modified = parts[3]
                    val fullPath = parts[4]
                    val name = fullPath.substringAfterLast('/')
                    FileEntry(
                        name = if (name.isBlank()) fullPath else name,
                        path = fullPath,
                        type = if (typeChar == "d") "dir" else "file",
                        size = size,
                        mode = mode,
                        modified = modified
                    )
                } else null
            }
        }
    }

    // Terminal PTY Session
    fun startTerminal(server: SavedServer): Result<DirectTerminalSession> {
        return try {
            val session = createSession(
                host = server.host,
                port = server.port,
                username = server.username,
                authType = server.authType,
                password = server.password,
                privateKey = server.privateKey,
                timeoutMs = 12000
            )
            val channel = session.openChannel("shell") as ChannelShell
            channel.setPtyType("xterm-256color", 80, 24, 640, 480)
            val outputStream = channel.outputStream
            val inputStream = channel.inputStream
            channel.connect(10000)
            Result.success(DirectTerminalSession(session, channel, outputStream, inputStream))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
