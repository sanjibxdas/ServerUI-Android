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

    private var directSession: DirectTerminalSession? = null
    private var webSocket: WebSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _state = MutableStateFlow<TerminalState>(TerminalState.Disconnected)
    val state: StateFlow<TerminalState> = _state.asStateFlow()

    private val _outputFlow = MutableSharedFlow<String>(extraBufferCapacity = 256)
    val outputFlow: SharedFlow<String> = _outputFlow.asSharedFlow()

    /**
     * Connect directly from the Android phone to the SSH server using JSch
     */
    fun connectDirect(server: SavedServer) {
        disconnect()
        _state.value = TerminalState.Connecting
        _outputFlow.tryEmit("\r\n[Connecting to ${server.username}@${server.host}:${server.port} over SSH...]\r\n")

        scope.launch {
            val res = SshDirectManager.startTerminal(server)
            res.fold(
                onSuccess = { session ->
                    directSession = session
                    _state.value = TerminalState.Connected
                    val buffer = ByteArray(2048)
                    try {
                        while (_state.value == TerminalState.Connected) {
                            val read = session.inputStream.read(buffer)
                            if (read > 0) {
                                val text = String(buffer, 0, read, Charsets.UTF_8)
                                _outputFlow.tryEmit(text)
                            } else if (read == -1) {
                                break
                            }
                        }
                    } catch (_: Exception) {
                        if (_state.value == TerminalState.Connected) {
                            _outputFlow.tryEmit("\r\n[SSH Connection Closed]\r\n")
                        }
                    } finally {
                        _state.value = TerminalState.Disconnected
                    }
                },
                onFailure = { err ->
                    val msg = err.localizedMessage ?: "Failed to open SSH terminal"
                    _state.value = TerminalState.Error(msg)
                    _outputFlow.tryEmit("\r\n[SSH Terminal Error: $msg]\r\n")
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
                sendResize(80, 24)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    val type = json.optString("type")
                    if (type == "error") {
                        val msg = json.optString("message", "Terminal error")
                        _state.value = TerminalState.Error(msg)
                        _outputFlow.tryEmit("\r\n[Terminal Error: $msg]\r\n")
                        return
                    } else if (type == "status") {
                        val status = json.optString("status")
                        if (status == "connected") {
                            _state.value = TerminalState.Connected
                        }
                        return
                    }
                } catch (_: Exception) {}
                _outputFlow.tryEmit(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                _outputFlow.tryEmit(bytes.utf8())
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                _state.value = TerminalState.Disconnected
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _state.value = TerminalState.Disconnected
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val errMsg = t.localizedMessage ?: "Terminal connection failed"
                _state.value = TerminalState.Error(errMsg)
                _outputFlow.tryEmit("\r\n[Connection Failed: $errMsg]\r\n")
            }
        })
    }

    fun sendInput(input: String) {
        if (directSession != null) {
            directSession?.sendInput(input)
        } else {
            webSocket?.send(input)
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
        try { directSession?.close() } catch (_: Exception) {}
        directSession = null
        try { webSocket?.close(1000, "User closed terminal") } catch (_: Exception) {}
        webSocket = null
        _state.value = TerminalState.Disconnected
    }
}
