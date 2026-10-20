package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
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
    val isCheckingBackend: Boolean = false,
    val backendHealthy: Boolean = true,
    val backendErrorMessage: String? = null,
    val sshTimeoutSeconds: Int = 10,
    val pollIntervalSeconds: Int = 3,

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
    val isMultiSelectMode: Boolean = false,
    val selectedFilePaths: Set<String> = emptySet(),
    val searchQuery: String = "",
    val searchResults: List<FileEntry>? = null,
    val isSearching: Boolean = false,
    val bookmarks: List<String> = emptyList(),
    val pendingShareFiles: List<PendingShareFile>? = null,

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
            sshTimeoutSeconds = repository.getSshTimeoutSeconds(),
            pollIntervalSeconds = repository.getPollIntervalSeconds(),
            backendHealthy = true
        )
    )
    val uiState: StateFlow<UIState> = _uiState.asStateFlow()

    private var metricsJob: Job? = null

    init {
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
        _uiState.update { it.copy(backendHealthy = true, isCheckingBackend = false, backendErrorMessage = null) }
    }

    fun saveSshSettings(timeoutSeconds: Int, pollSeconds: Int) {
        repository.setSshTimeoutSeconds(timeoutSeconds)
        repository.setPollIntervalSeconds(pollSeconds)
        _uiState.update {
            it.copy(
                sshTimeoutSeconds = timeoutSeconds,
                pollIntervalSeconds = pollSeconds
            )
        }
        showSnackbar("SSH settings saved")
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
                    _uiState.update { it.copy(isTestingDraft = false, testErrorMessage = formatErrorMessage(err)) }
                }
            )
        }
    }

    private fun formatErrorMessage(err: Throwable): String {
        val msg = err.localizedMessage ?: "Connection error"
        return when {
            msg.contains("Auth fail", ignoreCase = true) ->
                "SSH authentication failed: Check your username, password, or SSH private key."
            msg.contains("UnknownHost", ignoreCase = true) ->
                "Unknown host: Check the server IP or domain."
            msg.contains("timeout", ignoreCase = true) || msg.contains("timed out", ignoreCase = true) ->
                "Connection timed out: Server unreachable or firewall blocking the SSH port."
            msg.contains("Connection refused", ignoreCase = true) ->
                "Connection refused: Ensure SSH service is running on the target server."
            msg.contains("invalid privatekey", ignoreCase = true) ->
                "Invalid private key format: Please check the PEM / OpenSSH key pasted."
            else -> msg
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
                    _uiState.update { it.copy(isTestingDraft = false, testErrorMessage = formatErrorMessage(err)) }
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
                    showSnackbar(formatErrorMessage(err))
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
        val currentBookmarks = repository.getBookmarks(serverId)
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoadingFiles = true,
                    fileErrorMessage = null,
                    isMultiSelectMode = false,
                    selectedFilePaths = emptySet(),
                    bookmarks = currentBookmarks
                )
            }
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
                            fileErrorMessage = null,
                            bookmarks = currentBookmarks
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoadingFiles = false,
                            fileErrorMessage = err.localizedMessage ?: "Failed to list files",
                            bookmarks = currentBookmarks
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

    // Multi-select & Bulk operations
    fun toggleMultiSelectMode(initialPath: String? = null) {
        _uiState.update { current ->
            val newMode = !current.isMultiSelectMode
            val newSelection = if (newMode && initialPath != null) setOf(initialPath) else emptySet()
            current.copy(isMultiSelectMode = newMode, selectedFilePaths = newSelection)
        }
    }

    fun toggleFileSelection(path: String) {
        _uiState.update { current ->
            val set = current.selectedFilePaths.toMutableSet()
            if (set.contains(path)) {
                set.remove(path)
            } else {
                set.add(path)
            }
            current.copy(selectedFilePaths = set)
        }
    }

    fun selectAllFiles() {
        val all = _uiState.value.fileEntries.map { it.path }.toSet()
        _uiState.update { it.copy(selectedFilePaths = all) }
    }

    fun clearFileSelection() {
        _uiState.update { it.copy(isMultiSelectMode = false, selectedFilePaths = emptySet()) }
    }

    fun bulkDeleteSelected() {
        val serverId = _uiState.value.selectedServerId ?: return
        val targets = _uiState.value.selectedFilePaths.toList()
        if (targets.isEmpty()) return
        viewModelScope.launch {
            showSnackbar("Deleting ${targets.size} item(s)...")
            val res = repository.bulkDelete(serverId, targets)
            res.fold(
                onSuccess = {
                    showSnackbar("Deleted ${targets.size} item(s)")
                    clearFileSelection()
                    loadFiles(_uiState.value.currentFilePath)
                },
                onFailure = { err ->
                    showSnackbar("Bulk delete failed: ${err.localizedMessage}")
                }
            )
        }
    }

    fun bulkCopySelected(destDir: String) {
        val serverId = _uiState.value.selectedServerId ?: return
        val targets = _uiState.value.selectedFilePaths.toList()
        if (targets.isEmpty()) return
        viewModelScope.launch {
            showSnackbar("Copying ${targets.size} item(s)...")
            val res = repository.bulkCopy(serverId, targets, destDir)
            res.fold(
                onSuccess = {
                    showSnackbar("Copied ${targets.size} item(s) to $destDir")
                    clearFileSelection()
                    loadFiles(_uiState.value.currentFilePath)
                },
                onFailure = { err ->
                    showSnackbar("Bulk copy failed: ${err.localizedMessage}")
                }
            )
        }
    }

    fun bulkMoveSelected(destDir: String) {
        val serverId = _uiState.value.selectedServerId ?: return
        val targets = _uiState.value.selectedFilePaths.toList()
        if (targets.isEmpty()) return
        viewModelScope.launch {
            showSnackbar("Moving ${targets.size} item(s)...")
            val res = repository.bulkMove(serverId, targets, destDir)
            res.fold(
                onSuccess = {
                    showSnackbar("Moved ${targets.size} item(s) to $destDir")
                    clearFileSelection()
                    loadFiles(_uiState.value.currentFilePath)
                },
                onFailure = { err ->
                    showSnackbar("Bulk move failed: ${err.localizedMessage}")
                }
            )
        }
    }

    // SAF File Transfer (Download & Upload)
    fun downloadFile(remotePath: String, destUri: Uri) {
        val serverId = _uiState.value.selectedServerId ?: return
        viewModelScope.launch {
            showSnackbar("Starting download...")
            try {
                val outputStream = getApplication<Application>().contentResolver.openOutputStream(destUri)
                if (outputStream == null) {
                    showSnackbar("Cannot open local destination stream")
                    return@launch
                }
                outputStream.use { out ->
                    val res = repository.downloadFile(serverId, remotePath, out)
                    res.fold(
                        onSuccess = { showSnackbar("Download complete: ${remotePath.substringAfterLast('/')}") },
                        onFailure = { err -> showSnackbar("Download failed: ${err.localizedMessage}") }
                    )
                }
            } catch (e: Exception) {
                showSnackbar("Download error: ${e.localizedMessage}")
            }
        }
    }

    fun uploadFiles(sourceUris: List<Uri>, destDir: String) {
        val serverId = _uiState.value.selectedServerId ?: return
        if (sourceUris.isEmpty()) return
        viewModelScope.launch {
            val contentResolver = getApplication<Application>().contentResolver
            var successCount = 0
            for (uri in sourceUris) {
                var fileName = "upload_${System.currentTimeMillis()}"
                try {
                    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (nameIndex != -1) {
                                fileName = cursor.getString(nameIndex) ?: fileName
                            }
                        }
                    }
                } catch (_: Exception) {}

                val remotePath = "${destDir.removeSuffix("/")}/$fileName"
                try {
                    contentResolver.openInputStream(uri)?.use { stream ->
                        val res = repository.uploadFile(serverId, remotePath, stream)
                        if (res.isSuccess) successCount++
                    }
                } catch (_: Exception) {}
            }
            showSnackbar("Uploaded $successCount of ${sourceUris.size} file(s)")
            loadFiles(_uiState.value.currentFilePath)
        }
    }

    // Permissions: chmod & chown
    fun chmodFile(path: String, octalPermissions: Int) {
        val serverId = _uiState.value.selectedServerId ?: return
        viewModelScope.launch {
            val res = repository.chmod(serverId, path, octalPermissions)
            res.fold(
                onSuccess = {
                    showSnackbar("Permissions updated (0${Integer.toOctalString(octalPermissions)})")
                    loadFiles(_uiState.value.currentFilePath)
                },
                onFailure = { err ->
                    showSnackbar("chmod failed: ${err.localizedMessage}")
                }
            )
        }
    }

    fun chownFile(path: String, owner: String, group: String) {
        val serverId = _uiState.value.selectedServerId ?: return
        viewModelScope.launch {
            val res = repository.chown(serverId, path, owner, group)
            res.fold(
                onSuccess = {
                    showSnackbar("Owner updated to $owner:$group")
                    loadFiles(_uiState.value.currentFilePath)
                },
                onFailure = { err ->
                    showSnackbar("chown failed: ${err.localizedMessage}")
                }
            )
        }
    }

    // Recursive search
    fun searchFiles(query: String) {
        val serverId = _uiState.value.selectedServerId ?: return
        if (query.isBlank()) {
            clearSearch()
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true, searchQuery = query) }
            val res = repository.searchFiles(serverId, _uiState.value.currentFilePath, query)
            res.fold(
                onSuccess = { results ->
                    _uiState.update { it.copy(isSearching = false, searchResults = results) }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isSearching = false) }
                    showSnackbar("Search failed: ${err.localizedMessage}")
                }
            )
        }
    }

    fun clearSearch() {
        _uiState.update { it.copy(searchQuery = "", searchResults = null, isSearching = false) }
    }

    // Directory Bookmarks
    fun addBookmark(path: String) {
        val serverId = _uiState.value.selectedServerId ?: return
        val updated = repository.addBookmark(serverId, path)
        _uiState.update { it.copy(bookmarks = updated) }
        showSnackbar("Bookmarked: $path")
    }

    fun removeBookmark(path: String) {
        val serverId = _uiState.value.selectedServerId ?: return
        val updated = repository.removeBookmark(serverId, path)
        _uiState.update { it.copy(bookmarks = updated) }
        showSnackbar("Removed bookmark: $path")
    }

    // Pending Share-Sheet Target Handlers
    fun setPendingShareFiles(files: List<PendingShareFile>?) {
        _uiState.update { it.copy(pendingShareFiles = files) }
    }

    fun confirmShareUpload(targetServerId: String, targetRemoteDir: String) {
        val pending = _uiState.value.pendingShareFiles ?: return
        viewModelScope.launch {
            val contentResolver = getApplication<Application>().contentResolver
            var uploadedCount = 0
            showSnackbar("Uploading ${pending.size} shared item(s)...")
            for (item in pending) {
                val uri = Uri.parse(item.uriString)
                val targetPath = "${targetRemoteDir.removeSuffix("/")}/${item.name}"
                try {
                    contentResolver.openInputStream(uri)?.use { stream ->
                        val res = repository.uploadFile(targetServerId, targetPath, stream)
                        if (res.isSuccess) uploadedCount++
                    }
                } catch (_: Exception) {}
            }
            _uiState.update { it.copy(pendingShareFiles = null) }
            showSnackbar("Shared $uploadedCount item(s) uploaded successfully")
            if (_uiState.value.selectedServerId == targetServerId) {
                loadFiles(_uiState.value.currentFilePath)
            }
        }
    }

    // Terminal
    fun connectTerminal() {
        val serverId = _uiState.value.selectedServerId ?: return
        val server = repository.localStore.getServer(serverId) ?: return
        terminalManager.connectDirect(server)
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
