package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.*
import com.example.data.network.TerminalState
import com.example.data.network.TerminalWebSocketManager
import com.example.data.repository.ServerUIRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class UIState(
    val backendUrl: String = "",
    val localToken: String = "",
    val isCheckingBackend: Boolean = false,
    val backendHealthy: Boolean? = null,
    val backendErrorMessage: String? = null,

    val servers: List<ServerPublic> = emptyList(),
    val selectedServerId: String? = null,
    val isLoadingServers: Boolean = false,
    val serverErrorMessage: String? = null,

    val metrics: ServerMetrics? = null,
    val isLoadingMetrics: Boolean = false,
    val metricsErrorMessage: String? = null,

    val processSearchQuery: String = "",
    val selectedProcessDetail: ProcessDetail? = null,
    val isLoadingProcessDetail: Boolean = false,

    val serviceSearchQuery: String = "",

    val currentFilePath: String = "~",
    val fileEntries: List<FileEntry> = emptyList(),
    val isLoadingFiles: Boolean = false,
    val fileErrorMessage: String? = null,
    val selectedPreview: FilePreview? = null,
    val editorContent: String = "",
    val isSavingFile: Boolean = false,

    val terminalLines: List<String> = emptyList(),
    val terminalState: TerminalState = TerminalState.Disconnected,

    val isTestingDraft: Boolean = false,
    val draftTestResult: ServerDraftTest? = null,
    val testErrorMessage: String? = null,

    val activeSnackbarMessage: String? = null
)

class ServerUIViewModel(application: Application) : AndroidViewModel(application) {

    val repository = ServerUIRepository(application)
    val terminalManager = TerminalWebSocketManager()

    private val _uiState = MutableStateFlow(
        UIState(
            backendUrl = repository.getServerUrl(),
            localToken = repository.getLocalToken()
        )
    )
    val uiState: StateFlow<UIState> = _uiState.asStateFlow()

    private var metricsJob: Job? = null

    init {
        checkBackendHealth()
        loadServers()

        // Collect terminal output
        viewModelScope.launch {
            terminalManager.outputFlow.collect { newChunk ->
                _uiState.update { state ->
                    val lines = (state.terminalLines + newChunk.split("\n"))
                        .takeLast(500) // keep buffer bounded
                    state.copy(terminalLines = lines)
                }
            }
        }

        viewModelScope.launch {
            terminalManager.state.collect { termState ->
                _uiState.update { it.copy(terminalState = termState) }
            }
        }
    }

    fun checkBackendHealth() {
        viewModelScope.launch {
            _uiState.update { it.copy(isCheckingBackend = true, backendErrorMessage = null) }
            val res = repository.checkHealth()
            res.fold(
                onSuccess = {
                    _uiState.update { it.copy(isCheckingBackend = false, backendHealthy = true, backendErrorMessage = null) }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isCheckingBackend = false,
                            backendHealthy = false,
                            backendErrorMessage = err.localizedMessage ?: "Could not connect to ServerUI backend"
                        )
                    }
                }
            )
        }
    }

    fun saveBackendConfig(url: String, token: String) {
        repository.saveConfig(url, token)
        _uiState.update {
            it.copy(
                backendUrl = repository.getServerUrl(),
                localToken = repository.getLocalToken(),
                backendHealthy = null
            )
        }
        checkBackendHealth()
        loadServers()
    }

    fun loadServers(autoSelectFirst: Boolean = true) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingServers = true, serverErrorMessage = null) }
            val res = repository.listServers()
            res.fold(
                onSuccess = { list ->
                    _uiState.update { state ->
                        val selected = if (state.selectedServerId != null && list.any { it.id == state.selectedServerId }) {
                            state.selectedServerId
                        } else if (autoSelectFirst && list.isNotEmpty()) {
                            list.first().id
                        } else {
                            null
                        }
                        state.copy(
                            servers = list,
                            selectedServerId = selected,
                            isLoadingServers = false,
                            serverErrorMessage = null
                        )
                    }
                    if (_uiState.value.selectedServerId != null) {
                        startMetricsPolling()
                        loadFiles("~")
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoadingServers = false,
                            serverErrorMessage = err.localizedMessage ?: "Failed to load servers"
                        )
                    }
                }
            )
        }
    }

    fun selectServer(serverId: String) {
        _uiState.update { it.copy(selectedServerId = serverId) }
        startMetricsPolling()
        loadFiles("~")
        // If terminal was connected, reconnect for new server
        if (_uiState.value.terminalState != TerminalState.Disconnected) {
            connectTerminal()
        }
    }

    fun startMetricsPolling() {
        metricsJob?.cancel()
        val serverId = _uiState.value.selectedServerId ?: return

        metricsJob = viewModelScope.launch {
            while (isActive) {
                val currentId = _uiState.value.selectedServerId
                if (currentId == null) break
                val res = repository.getLiveMetrics(currentId)
                res.fold(
                    onSuccess = { data ->
                        _uiState.update { it.copy(metrics = data, metricsErrorMessage = null) }
                    },
                    onFailure = { err ->
                        // Don't wipe current metrics immediately on transient error
                        _uiState.update { it.copy(metricsErrorMessage = err.localizedMessage) }
                    }
                )
                delay(2000)
            }
        }
    }

    fun stopMetricsPolling() {
        metricsJob?.cancel()
        metricsJob = null
    }

    fun connectServer(id: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingServers = true) }
            val res = repository.connectServer(id)
            res.fold(
                onSuccess = {
                    showSnackbar("Connected to server")
                    loadServers(autoSelectFirst = false)
                },
                onFailure = { err ->
                    showSnackbar("Connect failed: ${err.localizedMessage}")
                    loadServers(autoSelectFirst = false)
                }
            )
        }
    }

    fun disconnectServer(id: String) {
        viewModelScope.launch {
            val res = repository.disconnectServer(id)
            res.fold(
                onSuccess = {
                    showSnackbar("Disconnected server")
                    loadServers(autoSelectFirst = false)
                },
                onFailure = { err ->
                    showSnackbar("Disconnect failed: ${err.localizedMessage}")
                }
            )
        }
    }

    fun testServer(id: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isTestingDraft = true, draftTestResult = null, testErrorMessage = null) }
            val res = repository.testServer(id)
            res.fold(
                onSuccess = { result ->
                    _uiState.update { it.copy(isTestingDraft = false, draftTestResult = result) }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isTestingDraft = false, testErrorMessage = err.localizedMessage) }
                }
            )
        }
    }

    fun testServerDraft(request: ServerDraftRequest) {
        viewModelScope.launch {
            _uiState.update { it.copy(isTestingDraft = true, draftTestResult = null, testErrorMessage = null) }
            val res = repository.testServerDraft(request)
            res.fold(
                onSuccess = { result ->
                    _uiState.update { it.copy(isTestingDraft = false, draftTestResult = result) }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isTestingDraft = false, testErrorMessage = err.localizedMessage) }
                }
            )
        }
    }

    fun clearDraftTest() {
        _uiState.update { it.copy(isTestingDraft = false, draftTestResult = null, testErrorMessage = null) }
    }

    fun createServer(input: ServerInput, onDone: () -> Unit) {
        viewModelScope.launch {
            val res = repository.createServer(input)
            res.fold(
                onSuccess = { server ->
                    showSnackbar("Server '${server.name}' created")
                    loadServers()
                    onDone()
                },
                onFailure = { err ->
                    showSnackbar("Error: ${err.localizedMessage}")
                }
            )
        }
    }

    fun updateServer(id: String, input: ServerInput, onDone: () -> Unit) {
        viewModelScope.launch {
            val res = repository.updateServer(id, input)
            res.fold(
                onSuccess = { server ->
                    showSnackbar("Server '${server.name}' updated")
                    loadServers(autoSelectFirst = false)
                    onDone()
                },
                onFailure = { err ->
                    showSnackbar("Error: ${err.localizedMessage}")
                }
            )
        }
    }

    fun deleteServer(id: String) {
        viewModelScope.launch {
            val res = repository.deleteServer(id)
            res.fold(
                onSuccess = {
                    showSnackbar("Server deleted")
                    loadServers()
                },
                onFailure = { err ->
                    showSnackbar("Error deleting server: ${err.localizedMessage}")
                }
            )
        }
    }

    // Process & Service Controls
    fun setProcessSearch(query: String) {
        _uiState.update { it.copy(processSearchQuery = query) }
    }

    fun inspectProcess(pid: Int) {
        val serverId = _uiState.value.selectedServerId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingProcessDetail = true) }
            val res = repository.inspectProcess(serverId, pid)
            res.fold(
                onSuccess = { detail ->
                    _uiState.update { it.copy(selectedProcessDetail = detail, isLoadingProcessDetail = false) }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isLoadingProcessDetail = false) }
                    showSnackbar("Process inspect error: ${err.localizedMessage}")
                }
            )
        }
    }

    fun clearProcessDetail() {
        _uiState.update { it.copy(selectedProcessDetail = null) }
    }

    fun killProcess(pid: Int) {
        val serverId = _uiState.value.selectedServerId ?: return
        viewModelScope.launch {
            val res = repository.killProcess(serverId, pid)
            res.fold(
                onSuccess = {
                    showSnackbar("Sent SIGTERM to PID $pid")
                    clearProcessDetail()
                },
                onFailure = { err ->
                    showSnackbar("Failed to kill PID $pid: ${err.localizedMessage}")
                }
            )
        }
    }

    fun setServiceSearch(query: String) {
        _uiState.update { it.copy(serviceSearchQuery = query) }
    }

    fun controlService(name: String, action: String) {
        val serverId = _uiState.value.selectedServerId ?: return
        viewModelScope.launch {
            val res = repository.controlService(serverId, name, action)
            res.fold(
                onSuccess = {
                    showSnackbar("Service $name $action sent")
                },
                onFailure = { err ->
                    showSnackbar("Service $name $action failed: ${err.localizedMessage}")
                }
            )
        }
    }

    // File Operations
    fun loadFiles(path: String) {
        val serverId = _uiState.value.selectedServerId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingFiles = true, fileErrorMessage = null) }
            val res = repository.listFiles(serverId, path)
            res.fold(
                onSuccess = { listResp ->
                    _uiState.update {
                        it.copy(
                            currentFilePath = listResp.path,
                            fileEntries = listResp.entries.sortedWith(
                                compareBy<FileEntry> { entry -> entry.type != "dir" }
                                    .thenBy { entry -> entry.name.lowercase() }
                            ),
                            isLoadingFiles = false,
                            fileErrorMessage = null
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoadingFiles = false,
                            fileErrorMessage = err.localizedMessage ?: "Failed to list files"
                        )
                    }
                }
            )
        }
    }

    fun navigateUp() {
        val current = _uiState.value.currentFilePath
        if (current == "/" || current.isEmpty()) return
        val parent = current.substringBeforeLast('/', "")
        loadFiles(if (parent.isEmpty()) "/" else parent)
    }

    fun openFile(entry: FileEntry) {
        if (entry.type == "dir") {
            loadFiles(entry.path)
            return
        }
        val serverId = _uiState.value.selectedServerId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingFiles = true) }
            val res = repository.readFile(serverId, entry.path)
            res.fold(
                onSuccess = { preview ->
                    _uiState.update {
                        it.copy(
                            isLoadingFiles = false,
                            selectedPreview = preview,
                            editorContent = preview.content
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isLoadingFiles = false) }
                    showSnackbar("Cannot open file: ${err.localizedMessage}")
                }
            )
        }
    }

    fun updateEditorContent(text: String) {
        _uiState.update { it.copy(editorContent = text) }
    }

    fun saveFile() {
        val serverId = _uiState.value.selectedServerId ?: return
        val preview = _uiState.value.selectedPreview ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingFile = true) }
            val res = repository.writeFile(serverId, preview.path, _uiState.value.editorContent)
            res.fold(
                onSuccess = {
                    showSnackbar("Saved ${preview.path.substringAfterLast('/')}")
                    _uiState.update { it.copy(isSavingFile = false, selectedPreview = null) }
                    loadFiles(_uiState.value.currentFilePath)
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isSavingFile = false) }
                    showSnackbar("Save failed: ${err.localizedMessage}")
                }
            )
        }
    }

    fun closeEditor() {
        _uiState.update { it.copy(selectedPreview = null, editorContent = "") }
    }

    fun createFile(name: String) {
        val serverId = _uiState.value.selectedServerId ?: return
        val fullPath = "${_uiState.value.currentFilePath.removeSuffix("/")}/$name"
        viewModelScope.launch {
            val res = repository.createFile(serverId, fullPath)
            res.fold(
                onSuccess = {
                    showSnackbar("File created")
                    loadFiles(_uiState.value.currentFilePath)
                },
                onFailure = { err ->
                    showSnackbar("Create failed: ${err.localizedMessage}")
                }
            )
        }
    }

    fun createDirectory(name: String) {
        val serverId = _uiState.value.selectedServerId ?: return
        val fullPath = "${_uiState.value.currentFilePath.removeSuffix("/")}/$name"
        viewModelScope.launch {
            val res = repository.mkdir(serverId, fullPath)
            res.fold(
                onSuccess = {
                    showSnackbar("Folder created")
                    loadFiles(_uiState.value.currentFilePath)
                },
                onFailure = { err ->
                    showSnackbar("Mkdir failed: ${err.localizedMessage}")
                }
            )
        }
    }

    fun renameFile(oldPath: String, newName: String) {
        val serverId = _uiState.value.selectedServerId ?: return
        val dir = oldPath.substringBeforeLast('/', "")
        val targetPath = if (dir.isEmpty()) "/$newName" else "$dir/$newName"
        viewModelScope.launch {
            val res = repository.rename(serverId, oldPath, targetPath)
            res.fold(
                onSuccess = {
                    showSnackbar("Renamed")
                    loadFiles(_uiState.value.currentFilePath)
                },
                onFailure = { err ->
                    showSnackbar("Rename failed: ${err.localizedMessage}")
                }
            )
        }
    }

    fun deleteFile(path: String) {
        val serverId = _uiState.value.selectedServerId ?: return
        viewModelScope.launch {
            val res = repository.deleteFile(serverId, path)
            res.fold(
                onSuccess = {
                    showSnackbar("Deleted")
                    loadFiles(_uiState.value.currentFilePath)
                },
                onFailure = { err ->
                    showSnackbar("Delete failed: ${err.localizedMessage}")
                }
            )
        }
    }

    fun compressFile(path: String, format: String) {
        val serverId = _uiState.value.selectedServerId ?: return
        viewModelScope.launch {
            showSnackbar("Compressing...")
            val res = repository.compress(serverId, path, format)
            res.fold(
                onSuccess = {
                    showSnackbar("Archive created")
                    loadFiles(_uiState.value.currentFilePath)
                },
                onFailure = { err ->
                    showSnackbar("Compression failed: ${err.localizedMessage}")
                }
            )
        }
    }

    // Terminal
    fun connectTerminal() {
        val serverId = _uiState.value.selectedServerId ?: return
        _uiState.update { it.copy(terminalLines = listOf("[Connecting to SSH terminal on server...]")) }
        terminalManager.connect(serverId)
    }

    fun disconnectTerminal() {
        terminalManager.disconnect()
    }

    fun sendTerminalInput(input: String) {
        terminalManager.sendInput(input)
    }

    fun clearTerminal() {
        _uiState.update { it.copy(terminalLines = emptyList()) }
    }

    fun showSnackbar(message: String) {
        _uiState.update { it.copy(activeSnackbarMessage = message) }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(activeSnackbarMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        metricsJob?.cancel()
        terminalManager.disconnect()
    }
}
