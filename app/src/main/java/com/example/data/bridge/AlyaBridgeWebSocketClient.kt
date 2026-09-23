package com.example.data.bridge

import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.example.BuildConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * AlyaBridgeWebSocketClient implements the requested "Hybrid WebSocket" pipeline:
 * 1. Sends text derived from Android Native Speech Recognizer to Python Server.
 * 2. Receives JSON response with AI text and Edge-TTS audio URL.
 * 3. Plays audio using ExoPlayer.
 */
class AlyaBridgeWebSocketClient(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // For WebSockets
        .build()

    private var webSocket: WebSocket? = null
    private var player: ExoPlayer? = null

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    private val _incomingText = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val incomingText = _incomingText.asSharedFlow()

    private val _errorFlow = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errorFlow = _errorFlow.asSharedFlow()

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    init {
        mainHandler.post {
            player = ExoPlayer.Builder(context).build()
        }
    }

    fun connect(url: String = "ws://10.0.2.2:8000/ws/chat") {
        disconnect()

        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i("AlyaBridge", "Connected to Python Bridge Server")
                _isConnected.value = true
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d("AlyaBridge", "Received: $text")
                try {
                    val json = JSONObject(text)
                    val aiReply = json.optString("text", "")
                    val audioUrl = json.optString("audio_url", "")

                    if (aiReply.isNotEmpty()) {
                        scope.launch { _incomingText.emit(aiReply) }
                    }

                    if (audioUrl.isNotEmpty()) {
                        playAudio(audioUrl)
                    }
                } catch (e: Exception) {
                    Log.e("AlyaBridge", "JSON Parse Error", e)
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _isConnected.value = false
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("AlyaBridge", "WebSocket Failure", t)
                _isConnected.value = false
                scope.launch { _errorFlow.emit("Bridge Server connection failed: ${t.message}") }
            }
        })
    }

    fun sendText(text: String) {
        if (_isConnected.value) {
            webSocket?.send(text)
            Log.d("AlyaBridge", "Sent Text: $text")
        } else {
            Log.w("AlyaBridge", "Cannot send text: Not connected")
        }
    }

    private fun playAudio(url: String) {
        mainHandler.post {
            try {
                val mediaItem = MediaItem.fromUri(url)
                player?.setMediaItem(mediaItem)
                player?.prepare()
                player?.play()
                Log.d("AlyaBridge", "Playing Audio: $url")
            } catch (e: Exception) {
                Log.e("AlyaBridge", "ExoPlayer Error", e)
            }
        }
    }

    fun disconnect() {
        webSocket?.close(1000, "Normal Closure")
        webSocket = null
        _isConnected.value = false
        mainHandler.post {
            player?.stop()
        }
    }

    fun release() {
        disconnect()
        mainHandler.post {
            player?.release()
            player = null
        }
    }
}
