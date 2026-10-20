package com.example.data.repository

import android.content.Context
import com.example.data.model.*
import com.example.data.network.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ServerUIRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("serverui_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_SERVER_URL = "key_server_url"
        private const val KEY_LOCAL_TOKEN = "key_local_token"
        const val DEFAULT_SERVER_URL = "http://10.0.2.2:8080"
    }

    init {
        val savedUrl = prefs.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL
        val savedToken = prefs.getString(KEY_LOCAL_TOKEN, "") ?: ""
        ApiClient.updateConfig(savedUrl, savedToken)
    }

    fun getServerUrl(): String = prefs.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL

    fun getLocalToken(): String = prefs.getString(KEY_LOCAL_TOKEN, "") ?: ""

    fun saveConfig(url: String, token: String) {
        prefs.edit()
            .putString(KEY_SERVER_URL, url)
            .putString(KEY_LOCAL_TOKEN, token)
            .apply()
        ApiClient.updateConfig(url, token)
    }

    suspend fun checkHealth(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val response = ApiClient.api.checkHealth()
            if (response.isSuccessful) {
                Result.success(true)
            } else {
                Result.failure(Exception("HTTP ${response.code()}: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listServers(): Result<List<ServerPublic>> = withContext(Dispatchers.IO) {
        try {
            val resp = ApiClient.api.listServers()
            Result.success(resp.servers)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createServer(input: ServerInput): Result<ServerPublic> = withContext(Dispatchers.IO) {
        try {
            val server = ApiClient.api.createServer(input)
            Result.success(server)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateServer(id: String, input: ServerInput): Result<ServerPublic> = withContext(Dispatchers.IO) {
        try {
            val server = ApiClient.api.updateServer(id, input)
            Result.success(server)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteServer(id: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val resp = ApiClient.api.deleteServer(id)
            if (resp.status == "ok" || resp.ok == true) {
                Result.success(true)
            } else {
                Result.failure(Exception(resp.error ?: "Failed to delete server"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun testServerDraft(request: ServerDraftRequest): Result<ServerDraftTest> = withContext(Dispatchers.IO) {
        try {
            val testResult = ApiClient.api.testServerDraft(request)
            Result.success(testResult)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun testServer(id: String): Result<ServerDraftTest> = withContext(Dispatchers.IO) {
        try {
            val testResult = ApiClient.api.testServer(id)
            Result.success(testResult)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun connectServer(id: String): Result<ServerMetrics> = withContext(Dispatchers.IO) {
        try {
            val metrics = ApiClient.api.connectServer(id)
            Result.success(metrics)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun disconnectServer(id: String): Result<ServerPublic> = withContext(Dispatchers.IO) {
        try {
            val server = ApiClient.api.disconnectServer(id)
            Result.success(server)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getLiveMetrics(serverId: String): Result<ServerMetrics> = withContext(Dispatchers.IO) {
        try {
            val metrics = ApiClient.api.getLiveMetrics(serverId)
            Result.success(metrics)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun inspectProcess(serverId: String, pid: Int): Result<ProcessDetail> = withContext(Dispatchers.IO) {
        try {
            val detail = ApiClient.api.inspectProcess(serverId, pid)
            Result.success(detail)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun killProcess(serverId: String, pid: Int): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val resp = ApiClient.api.killProcess(KillProcessRequest(serverId, pid))
            if (resp.ok == true || resp.status == "ok") {
                Result.success(true)
            } else {
                Result.failure(Exception(resp.error ?: "Unable to signal process"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun controlService(serverId: String, name: String, action: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val resp = ApiClient.api.controlService(ControlServiceRequest(serverId, name, action))
            if (resp.ok == true || resp.status == "ok") {
                Result.success(true)
            } else {
                Result.failure(Exception(resp.error ?: "Unable to control service"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listFiles(serverId: String, path: String): Result<FileListResponse> = withContext(Dispatchers.IO) {
        try {
            val resp = ApiClient.api.listFiles(serverId, path)
            Result.success(resp)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun readFile(serverId: String, path: String): Result<FilePreview> = withContext(Dispatchers.IO) {
        try {
            val preview = ApiClient.api.readFile(serverId, path)
            Result.success(preview)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun writeFile(serverId: String, path: String, content: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val resp = ApiClient.api.writeFile(WriteFileRequest(serverId, path, content))
            Result.success(resp.status == "ok" || resp.ok == true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createFile(serverId: String, path: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val resp = ApiClient.api.createFile(CreateFileRequest(serverId, path))
            Result.success(resp.status == "ok" || resp.ok == true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun mkdir(serverId: String, path: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val resp = ApiClient.api.mkdir(MkdirRequest(serverId, path))
            Result.success(resp.status == "ok" || resp.ok == true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun rename(serverId: String, from: String, to: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val resp = ApiClient.api.rename(RenameRequest(serverId, from, to))
            Result.success(resp.status == "ok" || resp.ok == true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteFile(serverId: String, path: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val resp = ApiClient.api.deleteFile(serverId, path)
            Result.success(resp.status == "ok" || resp.ok == true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun compress(serverId: String, path: String, format: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val resp = ApiClient.api.compress(CompressRequest(serverId, path, format))
            Result.success(resp.status == "ok" || resp.ok == true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
