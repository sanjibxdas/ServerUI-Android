package com.example.data.local

import android.content.Context
import com.example.data.model.ServerInput
import com.example.data.model.ServerPublic
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.UUID

@JsonClass(generateAdapter = true)
data class SavedServer(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val host: String,
    val port: Int = 22,
    val username: String = "root",
    val authType: String = "password", // "password" or "private_key"
    val password: String? = null,
    val privateKey: String? = null,
    val iconId: String? = "server",
    val status: String = "unknown",
    val lastSeen: String? = null,
    val alertConfig: com.example.data.model.ServerAlertConfig = com.example.data.model.ServerAlertConfig(),
    val isAlerting: Boolean = false,
    val alertReason: String? = null,
    val cpuBreachStartTime: Long? = null
) {
    fun toPublic(connected: Boolean = false): ServerPublic = ServerPublic(
        id = id,
        name = name,
        host = host,
        port = port,
        username = username,
        authType = authType,
        iconId = iconId,
        status = status,
        connected = connected,
        lastSeen = lastSeen,
        isAlerting = isAlerting,
        alertReason = alertReason,
        alertConfig = alertConfig
    )

    fun toDraftRequest(): com.example.data.model.ServerDraftRequest = com.example.data.model.ServerDraftRequest(
        name = name,
        host = host,
        port = port,
        username = username,
        authType = authType,
        password = password,
        privateKey = privateKey,
        serverId = id
    )
}

class LocalServerStore(context: Context) {
    private val prefs = context.getSharedPreferences("serverui_local_servers", Context.MODE_PRIVATE)
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val listType = Types.newParameterizedType(List::class.java, SavedServer::class.java)
    private val adapter = moshi.adapter<List<SavedServer>>(listType)

    fun getServers(): List<SavedServer> {
        val json = prefs.getString("saved_servers", null) ?: return emptyList()
        return try {
            adapter.fromJson(json) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getServer(id: String): SavedServer? {
        return getServers().firstOrNull { it.id == id }
    }

    fun saveServer(input: ServerInput): SavedServer {
        val current = getServers().toMutableList()
        val newServer = SavedServer(
            id = UUID.randomUUID().toString(),
            name = input.name.ifBlank { input.host },
            host = input.host.trim(),
            port = input.port,
            username = input.username.trim().ifBlank { "root" },
            authType = input.authType,
            password = input.password,
            privateKey = input.privateKey,
            iconId = input.iconId,
            status = "online",
            lastSeen = System.currentTimeMillis().toString()
        )
        current.add(newServer)
        persist(current)
        return newServer
    }

    fun updateServer(id: String, input: ServerInput): SavedServer? {
        val current = getServers().toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index == -1) return null
        val updated = current[index].copy(
            name = input.name.ifBlank { input.host },
            host = input.host.trim(),
            port = input.port,
            username = input.username.trim().ifBlank { "root" },
            authType = input.authType,
            password = input.password ?: current[index].password,
            privateKey = input.privateKey ?: current[index].privateKey,
            iconId = input.iconId
        )
        current[index] = updated
        persist(current)
        return updated
    }

    fun deleteServer(id: String): Boolean {
        val current = getServers().toMutableList()
        val removed = current.removeAll { it.id == id }
        if (removed) {
            persist(current)
        }
        return removed
    }

    fun updateStatus(id: String, status: String, lastSeen: String? = null) {
        val current = getServers().toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index != -1) {
            current[index] = current[index].copy(
                status = status,
                lastSeen = lastSeen ?: current[index].lastSeen
            )
            persist(current)
        }
    }

    fun getBookmarks(serverId: String): List<String> {
        val raw = prefs.getString("bookmarks_$serverId", null) ?: return listOf("~", "/var/log", "/etc")
        return try {
            raw.split("\n").filter { it.isNotBlank() }
        } catch (_: Exception) {
            listOf("~", "/var/log", "/etc")
        }
    }

    fun addBookmark(serverId: String, path: String): List<String> {
        val current = getBookmarks(serverId).toMutableList()
        val normalized = if (path.isBlank()) "/" else path.trim()
        if (!current.contains(normalized)) {
            current.add(normalized)
            prefs.edit().putString("bookmarks_$serverId", current.joinToString("\n")).apply()
        }
        return current
    }

    fun removeBookmark(serverId: String, path: String): List<String> {
        val current = getBookmarks(serverId).toMutableList()
        current.remove(path.trim())
        prefs.edit().putString("bookmarks_$serverId", current.joinToString("\n")).apply()
        return current
    }

    fun updateAlertConfig(id: String, config: com.example.data.model.ServerAlertConfig) {
        val current = getServers().toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index != -1) {
            current[index] = current[index].copy(alertConfig = config)
            persist(current)
        }
    }

    fun updateAlertState(id: String, isAlerting: Boolean, reason: String?, breachStart: Long?) {
        val current = getServers().toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index != -1) {
            current[index] = current[index].copy(
                isAlerting = isAlerting,
                alertReason = reason,
                cpuBreachStartTime = breachStart
            )
            persist(current)
        }
    }


    private fun persist(servers: List<SavedServer>) {
        val json = adapter.toJson(servers)
        prefs.edit().putString("saved_servers", json).apply()
    }
}
