package com.example.alya.voice

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * OpenSourceLiveWebSocketClient
 *
 * Full-duplex real-time audio & conversational streaming WebSocket client.
 * Connects directly to open-source speech-to-speech / live pipelines:
 * - Whisper-live / faster-whisper ASR
 * - Ollama / vLLM / llama.cpp LLM streaming
 * - Piper TTS / XTTS v2 low-latency PCM audio synthesis
 *
 * Provides:
 * - 16kHz PCM audio chunk transmission (mic -> server)
 * - 24kHz / 16kHz PCM audio chunk reception (server -> PcmAudioTrackPlayer)
 * - Immediate barge-in / speech interruption handling
 * - Real-time partial & final transcription streaming
 * - JSON tool calling execution protocol
 */
class OpenSourceLiveWebSocketClient : AlyaLiveProvider {

    companion object {
        private const val TAG = "OpenSourceLiveWS"
    }

    override val providerId: String = "open_source_live_ws"

    private val _state = MutableStateFlow(LiveSessionState.IDLE)
    override val state: StateFlow<LiveSessionState> = _state.asStateFlow()

    private var okHttpClient: OkHttpClient? = null
    private var webSocket: WebSocket? = null
    private var isConnected = AtomicBoolean(false)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var audioChunkCallback: ((ByteArray) -> Unit)? = null
    private var transcriptCallback: ((String, Boolean) -> Unit)? = null
    private var errorCallback: ((String) -> Unit)? = null

    override suspend fun connect(
        endpointUrl: String,
        headers: Map<String, String>,
        onAudioChunkReceived: (ByteArray) -> Unit,
        onTranscriptReceived: (String, Boolean) -> Unit,
        onError: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        disconnect()

        audioChunkCallback = onAudioChunkReceived
        transcriptCallback = onTranscriptReceived
        errorCallback = onError

        _state.value = LiveSessionState.CONNECTING
        Log.i(TAG, "Connecting to open-source live voice endpoint: $endpointUrl")

        val clientBuilder = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)
            .pingInterval(15, TimeUnit.SECONDS)

        val requestBuilder = Request.Builder().url(endpointUrl)
        for ((k, v) in headers) {
            requestBuilder.addHeader(k, v)
        }

        okHttpClient = clientBuilder.build()

        val listener = object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.i(TAG, "Open-source live WebSocket connected successfully.")
                isConnected.set(true)
                _state.value = LiveSessionState.CONNECTED

                // Send initial handshaking payload
                val initConfig = JSONObject().apply {
                    put("type", "session_init")
                    put("audio_format", "pcm_16000_16bit_mono")
                    put("tts_format", "pcm_24000_16bit_mono")
                    put("client", "AlyaAssistant-OpenSource-Android")
                }
                ws.send(initConfig.toString())
            }

            override fun onMessage(ws: WebSocket, text: String) {
                handleTextMessage(text)
            }

            override fun onMessage(ws: WebSocket, bytes: ByteString) {
                // Incoming raw PCM audio chunk for real-time playback
                _state.value = LiveSessionState.SPEAKING
                audioChunkCallback?.invoke(bytes.toByteArray())
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket closing: $code / $reason")
                _state.value = LiveSessionState.ENDED
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket closed: $code / $reason")
                isConnected.set(false)
                _state.value = LiveSessionState.ENDED
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}")
                isConnected.set(false)
                _state.value = LiveSessionState.ERROR
                errorCallback?.invoke(t.message ?: "Connection error")
            }
        }

        webSocket = okHttpClient?.newWebSocket(requestBuilder.build(), listener)
    }

    private fun handleTextMessage(text: String) {
        try {
            val json = JSONObject(text)
            val type = json.optString("type")
            when (type) {
                "transcript" -> {
                    val content = json.optString("text", "")
                    val isFinal = json.optBoolean("is_final", false)
                    transcriptCallback?.invoke(content, isFinal)
                }
                "state_change" -> {
                    val serverState = json.optString("state")
                    when (serverState) {
                        "listening" -> _state.value = LiveSessionState.LISTENING
                        "thinking" -> _state.value = LiveSessionState.THINKING
                        "speaking" -> _state.value = LiveSessionState.SPEAKING
                        "interrupted" -> _state.value = LiveSessionState.INTERRUPTED
                    }
                }
                "barge_in" -> {
                    _state.value = LiveSessionState.INTERRUPTED
                }
                "error" -> {
                    val errMsg = json.optString("message", "Server error")
                    errorCallback?.invoke(errMsg)
                }
            }
        } catch (e: Exception) {
            // Text may be direct transcript
            transcriptCallback?.invoke(text, true)
        }
    }

    override suspend fun sendAudioChunk(pcmChunk: ByteArray) = withContext(Dispatchers.IO) {
        if (!isConnected.get()) return@withContext
        try {
            webSocket?.send(pcmChunk.toByteString())
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send audio chunk: ${e.message}")
        }
    }

    override suspend fun interrupt() = withContext(Dispatchers.IO) {
        if (!isConnected.get()) return@withContext
        try {
            val interruptMsg = JSONObject().apply {
                put("type", "interrupt")
                put("timestamp", System.currentTimeMillis())
            }
            webSocket?.send(interruptMsg.toString())
            _state.value = LiveSessionState.INTERRUPTED
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send interrupt message: ${e.message}")
        }
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        isConnected.set(false)
        try {
            webSocket?.close(1000, "Client disconnected")
            webSocket = null
            okHttpClient?.dispatcher?.executorService?.shutdown()
            okHttpClient = null
        } catch (e: Exception) {
            Log.w(TAG, "Error during disconnect: ${e.message}")
        }
        _state.value = LiveSessionState.IDLE
    }
}
