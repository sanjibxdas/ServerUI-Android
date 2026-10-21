package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ServerAlertConfig(
    @Json(name = "enabled") val enabled: Boolean = true,
    @Json(name = "cpuThreshold") val cpuThreshold: Double = 90.0,
    @Json(name = "cpuSustainedMinutes") val cpuSustainedMinutes: Int = 15,
    @Json(name = "ramThreshold") val ramThreshold: Double = 85.0,
    @Json(name = "diskThreshold") val diskThreshold: Double = 90.0,
    @Json(name = "alertOnFailedService") val alertOnFailedService: Boolean = true,
    @Json(name = "alertOnUnreachable") val alertOnUnreachable: Boolean = true,
    @Json(name = "muteWindowEnabled") val muteWindowEnabled: Boolean = false,
    @Json(name = "muteStartHour") val muteStartHour: Int = 22,
    @Json(name = "muteStartMinute") val muteStartMinute: Int = 0,
    @Json(name = "muteEndHour") val muteEndHour: Int = 7,
    @Json(name = "muteEndMinute") val muteEndMinute: Int = 0
)

@JsonClass(generateAdapter = true)
data class ServerPublic(
    @Json(name = "id") val id: String = "",
    @Json(name = "name") val name: String = "",
    @Json(name = "host") val host: String = "",
    @Json(name = "port") val port: Int = 22,
    @Json(name = "username") val username: String = "",
    @Json(name = "authType") val authType: String = "password",
    @Json(name = "iconId") val iconId: String? = null,
    @Json(name = "customIcon") val customIcon: Boolean? = false,
    @Json(name = "status") val status: String = "unknown",
    @Json(name = "error") val error: String? = null,
    @Json(name = "connected") val connected: Boolean? = false,
    @Json(name = "lastSeen") val lastSeen: String? = null,
    @Json(name = "createdAt") val createdAt: String? = null,
    @Json(name = "updatedAt") val updatedAt: String? = null,
    @Json(name = "isAlerting") val isAlerting: Boolean = false,
    @Json(name = "alertReason") val alertReason: String? = null,
    @Json(name = "alertConfig") val alertConfig: ServerAlertConfig = ServerAlertConfig()
)

@JsonClass(generateAdapter = true)
data class ServerInput(
    @Json(name = "name") val name: String,
    @Json(name = "host") val host: String,
    @Json(name = "port") val port: Int = 22,
    @Json(name = "username") val username: String,
    @Json(name = "authType") val authType: String = "password",
    @Json(name = "password") val password: String? = null,
    @Json(name = "privateKey") val privateKey: String? = null,
    @Json(name = "iconId") val iconId: String = "server",
    @Json(name = "iconImage") val iconImage: String? = null
)

@JsonClass(generateAdapter = true)
data class ServerDraftRequest(
    @Json(name = "name") val name: String = "",
    @Json(name = "host") val host: String = "",
    @Json(name = "port") val port: Int = 22,
    @Json(name = "username") val username: String = "",
    @Json(name = "authType") val authType: String = "password",
    @Json(name = "password") val password: String? = null,
    @Json(name = "privateKey") val privateKey: String? = null,
    @Json(name = "serverId") val serverId: String? = null
)

@JsonClass(generateAdapter = true)
data class ServerDraftTest(
    @Json(name = "reachable") val reachable: Boolean = false,
    @Json(name = "banner") val banner: String? = null,
    @Json(name = "authSuccess") val authSuccess: Boolean = false,
    @Json(name = "latencyMs") val latencyMs: Long = 0,
    @Json(name = "error") val error: String? = null
)

@JsonClass(generateAdapter = true)
data class ServerListResponse(
    @Json(name = "servers") val servers: List<ServerPublic> = emptyList()
)

@JsonClass(generateAdapter = true)
data class CoreUsage(
    @Json(name = "core") val core: Int = 0,
    @Json(name = "usage") val usage: Double = 0.0
)

@JsonClass(generateAdapter = true)
data class DiskMount(
    @Json(name = "device") val device: String = "",
    @Json(name = "mount") val mount: String = "",
    @Json(name = "fsType") val fsType: String = "",
    @Json(name = "totalBytes") val totalBytes: Long = 0,
    @Json(name = "usedBytes") val usedBytes: Long = 0,
    @Json(name = "freeBytes") val freeBytes: Long = 0,
    @Json(name = "usage") val usage: Double = 0.0
)

@JsonClass(generateAdapter = true)
data class HistoryPoint(
    @Json(name = "timestamp") val timestamp: Long = 0,
    @Json(name = "cpu") val cpu: Double = 0.0,
    @Json(name = "memory") val memory: Double = 0.0,
    @Json(name = "disk") val disk: Double = 0.0,
    @Json(name = "network") val network: Double = 0.0
)

@JsonClass(generateAdapter = true)
data class ProcessInfo(
    @Json(name = "pid") val pid: Int = 0,
    @Json(name = "name") val name: String = "",
    @Json(name = "user") val user: String = "",
    @Json(name = "cpu") val cpu: Double = 0.0,
    @Json(name = "memory") val memory: Double = 0.0,
    @Json(name = "status") val status: String = "",
    @Json(name = "command") val command: String = ""
)

@JsonClass(generateAdapter = true)
data class ProcessDetail(
    @Json(name = "pid") val pid: Int = 0,
    @Json(name = "name") val name: String = "",
    @Json(name = "user") val user: String = "",
    @Json(name = "cpu") val cpu: Double = 0.0,
    @Json(name = "memory") val memory: Double = 0.0,
    @Json(name = "status") val status: String = "",
    @Json(name = "command") val command: String = "",
    @Json(name = "ppid") val ppid: Int = 0,
    @Json(name = "threads") val threads: Int = 0,
    @Json(name = "startedAt") val startedAt: String = "",
    @Json(name = "readBytes") val readBytes: Long = 0,
    @Json(name = "writeBytes") val writeBytes: Long = 0,
    @Json(name = "fdCount") val fdCount: Int = 0
)

@JsonClass(generateAdapter = true)
data class ServiceInfo(
    @Json(name = "name") val name: String = "",
    @Json(name = "load") val load: String = "",
    @Json(name = "active") val active: String = "",
    @Json(name = "sub") val sub: String = "",
    @Json(name = "description") val description: String = ""
)

@JsonClass(generateAdapter = true)
data class ServerMetrics(
    @Json(name = "id") val id: String? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "hostname") val hostname: String? = null,
    @Json(name = "status") val status: String? = null,
    @Json(name = "host") val host: String? = null,
    @Json(name = "port") val port: Int? = null,
    @Json(name = "username") val username: String? = null,
    @Json(name = "authType") val authType: String? = null,
    @Json(name = "iconId") val iconId: String? = null,
    @Json(name = "connected") val connected: Boolean? = false,
    @Json(name = "error") val error: String? = null,
    @Json(name = "cpuUsage") val cpuUsage: Double = 0.0,
    @Json(name = "cpuUser") val cpuUser: Double = 0.0,
    @Json(name = "cpuSystem") val cpuSystem: Double = 0.0,
    @Json(name = "cpuIowait") val cpuIowait: Double = 0.0,
    @Json(name = "cpuCores") val cpuCores: Int = 0,
    @Json(name = "cpuModel") val cpuModel: String = "",
    @Json(name = "cores") val cores: List<CoreUsage> = emptyList(),
    @Json(name = "memoryUsage") val memoryUsage: Double = 0.0,
    @Json(name = "memoryUsedBytes") val memoryUsedBytes: Long = 0,
    @Json(name = "memoryTotalBytes") val memoryTotalBytes: Long = 0,
    @Json(name = "memoryAvailableBytes") val memoryAvailableBytes: Long = 0,
    @Json(name = "swapUsedBytes") val swapUsedBytes: Long = 0,
    @Json(name = "swapTotalBytes") val swapTotalBytes: Long = 0,
    @Json(name = "diskUsage") val diskUsage: Double = 0.0,
    @Json(name = "diskUsedBytes") val diskUsedBytes: Long = 0,
    @Json(name = "diskTotalBytes") val diskTotalBytes: Long = 0,
    @Json(name = "diskFreeBytes") val diskFreeBytes: Long = 0,
    @Json(name = "diskReadBps") val diskReadBps: Long = 0,
    @Json(name = "diskWriteBps") val diskWriteBps: Long = 0,
    @Json(name = "diskMounts") val diskMounts: List<DiskMount> = emptyList(),
    @Json(name = "uptimeSeconds") val uptimeSeconds: Long = 0,
    @Json(name = "load1") val load1: Double = 0.0,
    @Json(name = "load5") val load5: Double = 0.0,
    @Json(name = "load15") val load15: Double = 0.0,
    @Json(name = "osName") val osName: String = "",
    @Json(name = "kernel") val kernel: String = "",
    @Json(name = "arch") val arch: String = "",
    @Json(name = "ipAddress") val ipAddress: String = "",
    @Json(name = "containerCount") val containerCount: Int = 0,
    @Json(name = "netRxBps") val netRxBps: Long = 0,
    @Json(name = "netTxBps") val netTxBps: Long = 0,
    @Json(name = "netRxBytes") val netRxBytes: Long = 0,
    @Json(name = "netTxBytes") val netTxBytes: Long = 0,
    @Json(name = "netConns") val netConns: Int = 0,
    @Json(name = "netEstablished") val netEstablished: Int = 0,
    @Json(name = "netListenPorts") val netListenPorts: List<Int> = emptyList(),
    @Json(name = "processCount") val processCount: Int = 0,
    @Json(name = "processUserCount") val processUserCount: Int = 0,
    @Json(name = "processSysCount") val processSysCount: Int = 0,
    @Json(name = "processRunning") val processRunning: Int = 0,
    @Json(name = "processes") val processes: List<ProcessInfo> = emptyList(),
    @Json(name = "services") val services: List<ServiceInfo> = emptyList(),
    @Json(name = "serviceRunning") val serviceRunning: Int = 0,
    @Json(name = "serviceStopped") val serviceStopped: Int = 0,
    @Json(name = "serviceFailed") val serviceFailed: Int = 0,
    @Json(name = "serviceOther") val serviceOther: Int = 0,
    @Json(name = "history") val history: List<HistoryPoint> = emptyList()
)

@JsonClass(generateAdapter = true)
data class KillProcessRequest(
    @Json(name = "serverId") val serverId: String,
    @Json(name = "pid") val pid: Int
)

@JsonClass(generateAdapter = true)
data class ControlServiceRequest(
    @Json(name = "serverId") val serverId: String,
    @Json(name = "name") val name: String,
    @Json(name = "action") val action: String // "start", "stop", "restart"
)

@JsonClass(generateAdapter = true)
data class FileEntry(
    @Json(name = "name") val name: String = "",
    @Json(name = "path") val path: String = "",
    @Json(name = "type") val type: String = "file", // "file", "dir", "symlink"
    @Json(name = "size") val size: Long = 0,
    @Json(name = "mode") val mode: String = "",
    @Json(name = "modified") val modified: String = "",
    @Json(name = "mime") val mime: String? = null
)

@JsonClass(generateAdapter = true)
data class FileListResponse(
    @Json(name = "path") val path: String = "",
    @Json(name = "entries") val entries: List<FileEntry> = emptyList()
)

@JsonClass(generateAdapter = true)
data class FilePreview(
    @Json(name = "path") val path: String = "",
    @Json(name = "content") val content: String = "",
    @Json(name = "size") val size: Long = 0,
    @Json(name = "truncated") val truncated: Boolean = false,
    @Json(name = "mime") val mime: String = "text/plain",
    @Json(name = "binary") val binary: Boolean = false,
    @Json(name = "writable") val writable: Boolean = true
)

@JsonClass(generateAdapter = true)
data class WriteFileRequest(
    @Json(name = "serverId") val serverId: String,
    @Json(name = "path") val path: String,
    @Json(name = "content") val content: String
)

@JsonClass(generateAdapter = true)
data class CreateFileRequest(
    @Json(name = "serverId") val serverId: String,
    @Json(name = "path") val path: String
)

@JsonClass(generateAdapter = true)
data class MkdirRequest(
    @Json(name = "serverId") val serverId: String,
    @Json(name = "path") val path: String
)

@JsonClass(generateAdapter = true)
data class RenameRequest(
    @Json(name = "serverId") val serverId: String,
    @Json(name = "from") val from: String,
    @Json(name = "to") val to: String
)

@JsonClass(generateAdapter = true)
data class TransferRequest(
    @Json(name = "serverId") val serverId: String,
    @Json(name = "from") val from: String,
    @Json(name = "to") val to: String,
    @Json(name = "overwrite") val overwrite: Boolean = false
)

@JsonClass(generateAdapter = true)
data class CompressRequest(
    @Json(name = "serverId") val serverId: String,
    @Json(name = "path") val path: String,
    @Json(name = "format") val format: String = "zip"
)

@JsonClass(generateAdapter = true)
data class ExtractRequest(
    @Json(name = "serverId") val serverId: String,
    @Json(name = "path") val path: String,
    @Json(name = "destination") val destination: String = ""
)

@JsonClass(generateAdapter = true)
data class SimpleStatus(
    @Json(name = "status") val status: String? = null,
    @Json(name = "ok") val ok: Boolean? = null,
    @Json(name = "error") val error: String? = null
)

@JsonClass(generateAdapter = true)
data class PendingShareFile(
    @Json(name = "uriString") val uriString: String,
    @Json(name = "name") val name: String,
    @Json(name = "size") val size: Long
)

