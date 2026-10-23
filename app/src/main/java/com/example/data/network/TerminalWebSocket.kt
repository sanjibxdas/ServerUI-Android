package com.example.data.network

import com.example.data.local.SavedServer
import com.example.data.ssh.DirectTerminalSession
import com.example.data.ssh.SshDirectManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject

sealed interface TerminalState {
    data object Disconnected : TerminalState
    data object Connecting : TerminalState
    data object Connected : TerminalState
    data class Error(val message: String) : TerminalState
}

class TerminalWebSocketManager {

    @Volatile
    private var directSession: DirectTerminalSession? = null
    @Volatile
    private var webSocket: WebSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    @Volatile
    private var currentCols: Int = 80
    @Volatile
    private var currentRows: Int = 24
    @Volatile
    private var currentWidthPx: Int = 640
    @Volatile
    private var currentHeightPx: Int = 480

    private val _state = MutableStateFlow<TerminalState>(TerminalState.Disconnected)
    val state: StateFlow<TerminalState> = _state.asStateFlow()

    private val _outputFlow = MutableSharedFlow<ByteArray>(extraBufferCapacity = 512)
    val outputFlow: SharedFlow<ByteArray> = _outputFlow.asSharedFlow()

    private fun emitText(text: String) {
        _outputFlow.tryEmit(text.toByteArray(Charsets.UTF_8))
    }

    /**
     * Connect directly from the Android phone to the SSH server using JSch
     */
    fun connectDirect(server: SavedServer) {
        disconnect()
        _state.value = TerminalState.Connecting
        emitText("\r\n\u001B[2m[Connecting to ${server.username}@${server.host}:${server.port} over SSH...]\u001B[0m\r\n")

        scope.launch {
            val res = SshDirectManager.startTerminal(
                server = server,
                cols = currentCols,
                rows = currentRows,
                widthPx = currentWidthPx,
                heightPx = currentHeightPx
            )
            res.fold(
                onSuccess = { session ->
                    directSession = session
                    _state.value = TerminalState.Connected
                    session.resize(currentCols, currentRows, currentWidthPx, currentHeightPx)
                    val buffer = ByteArray(4096)
                    try {
                        while (_state.value == TerminalState.Connected && session.isConnected) {
                            val read = session.inputStream.read(buffer)
                            if (read > 0) {
                                _outputFlow.tryEmit(buffer.copyOf(read))
                            } else if (read == -1) {
                                break
                            }
                        }
                    } catch (_: Exception) {
                        // Stream closed or interrupted
                    } finally {
                        try {
                            session.close()
                        } catch (_: Exception) {}
                        if (directSession === session) {
                            directSession = null
                        }
                        if (_state.value == TerminalState.Connected) {
                            _state.value = TerminalState.Disconnected
                        }
                    }
                },
                onFailure = { err ->
                    directSession = null
                    val msg = err.localizedMessage ?: "Failed to open SSH terminal"
                    _state.value = TerminalState.Error(msg)
                    emitText("\r\n\u001B[31m[SSH Terminal Error: $msg]\u001B[0m\r\n")
                }
            )
        }
    }

    /**
     * Fallback daemon WebSocket connection if remote backend is used
     */
    fun connect(serverId: String) {
        disconnect()
        _state.value = TerminalState.Connecting

        val wsUrl = ApiClient.getWebSocketUrl(serverId)
        val requestBuilder = Request.Builder().url(wsUrl)
        val token = ApiClient.getLocalToken()
        if (token.isNotBlank()) {
            requestBuilder.header("Sec-WebSocket-Protocol", "serverui-local.$token")
            requestBuilder.header("X-ServerUI-Local-Token", token)
        }

        webSocket = ApiClient.okHttpClient.newWebSocket(requestBuilder.build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _state.value = TerminalState.Connected
                sendResize(currentCols, currentRows)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    val type = json.optString("type")
                    if (type == "error") {
                        val msg = json.optString("message", "Terminal error")
                        _state.value = TerminalState.Error(msg)
                        emitText("\r\n\u001B[31m[Terminal Error: $msg]\u001B[0m\r\n")
                        return
                    } else if (type == "status") {
                        val status = json.optString("status")
                        if (status == "connected") {
                            _state.value = TerminalState.Connected
                        }
                        return
                    }
                } catch (_: Exception) {}
                emitText(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                _outputFlow.tryEmit(bytes.toByteArray())
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                if (this@TerminalWebSocketManager.webSocket === webSocket) {
                    this@TerminalWebSocketManager.webSocket = null
                }
                _state.value = TerminalState.Disconnected
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (this@TerminalWebSocketManager.webSocket === webSocket) {
                    this@TerminalWebSocketManager.webSocket = null
                }
                _state.value = TerminalState.Disconnected
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (this@TerminalWebSocketManager.webSocket === webSocket) {
                    this@TerminalWebSocketManager.webSocket = null
                }
                val errMsg = t.localizedMessage ?: "Terminal connection failed"
                _state.value = TerminalState.Error(errMsg)
                emitText("\r\n\u001B[31m[Connection Failed: $errMsg]\u001B[0m\r\n")
            }
        })
    }

    fun sendBytes(bytes: ByteArray): Boolean {
        val session = directSession
        if (session != null) {
            val result = session.sendBytes(bytes)
            if (result.isFailure) {
                try { session.close() } catch (_: Exception) {}
                if (directSession === session) {
                    directSession = null
                }
                if (_state.value == TerminalState.Connected) {
                    _state.value = TerminalState.Disconnected
                }
                return false
            }
            return true
        }
        val ws = webSocket
        if (ws != null) {
            return ws.send(bytes.toString(Charsets.UTF_8))
        }
        return false
    }

    fun sendInput(input: String): Boolean {
        return sendBytes(input.toByteArray(Charsets.UTF_8))
    }

    fun resize(cols: Int, rows: Int, widthPx: Int = 0, heightPx: Int = 0) {
        if (cols <= 0 || rows <= 0) return
        currentCols = cols
        currentRows = rows
        if (widthPx > 0) currentWidthPx = widthPx
        if (heightPx > 0) currentHeightPx = heightPx

        val session = directSession
        if (session != null) {
            scope.launch {
                session.resize(cols, rows, currentWidthPx, currentHeightPx)
            }
        } else if (webSocket != null) {
            sendResize(cols, rows)
        }
    }

    fun sendResize(cols: Int, rows: Int) {
        val json = JSONObject().apply {
            put("type", "resize")
            put("cols", cols)
            put("rows", rows)
        }
        webSocket?.send(json.toString())
    }

    fun disconnect() {
        val session = directSession
        directSession = null
        try { session?.close() } catch (_: Exception) {}
        val ws = webSocket
        webSocket = null
        try { ws?.close(1000, "User closed terminal") } catch (_: Exception) {}
        _state.value = TerminalState.Disconnected
    }
}
