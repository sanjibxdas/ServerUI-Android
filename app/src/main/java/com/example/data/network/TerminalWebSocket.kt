package com.example.data.network

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private var webSocket: WebSocket? = null

    private val _state = MutableStateFlow<TerminalState>(TerminalState.Disconnected)
    val state: StateFlow<TerminalState> = _state.asStateFlow()

    private val _outputFlow = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val outputFlow: SharedFlow<String> = _outputFlow.asSharedFlow()

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
                // Send initial terminal window size
                sendResize(80, 24)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    val type = json.optString("type")
                    if (type == "error") {
                        val msg = json.optString("message", "Terminal error")
                        _state.value = TerminalState.Error(msg)
                        _outputFlow.tryEmit("\r\n[ServerUI Terminal Error: $msg]\r\n")
                        return
                    } else if (type == "status") {
                        val status = json.optString("status")
                        if (status == "connected") {
                            _state.value = TerminalState.Connected
                        }
                        return
                    }
                } catch (_: Exception) {
                    // Raw text from server
                }
                _outputFlow.tryEmit(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val text = bytes.utf8()
                _outputFlow.tryEmit(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                _state.value = TerminalState.Disconnected
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _state.value = TerminalState.Disconnected
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val errMsg = t.localizedMessage ?: "WebSocket connection failed"
                _state.value = TerminalState.Error(errMsg)
                _outputFlow.tryEmit("\r\n[Connection Failed: $errMsg]\r\n")
            }
        })
    }

    fun sendInput(input: String) {
        webSocket?.send(input)
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
        try {
            webSocket?.close(1000, "User closed terminal")
        } catch (_: Exception) {}
        webSocket = null
        _state.value = TerminalState.Disconnected
    }
}
