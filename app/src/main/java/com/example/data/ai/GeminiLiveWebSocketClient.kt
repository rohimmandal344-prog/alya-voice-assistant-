package com.example.data.ai

import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.example.voice.audio.PcmAudioTrackPlayer
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Connection states for Gemini Live WebSocket session.
 */
sealed class GeminiLiveSessionState {
    data object Disconnected : GeminiLiveSessionState()
    data object Connecting : GeminiLiveSessionState()
    data object Connected : GeminiLiveSessionState()
    data object Streaming : GeminiLiveSessionState()
    data class Error(val message: String) : GeminiLiveSessionState()
}

/**
 * Full-duplex Gemini Live WebSocket Client implementing bidirectional audio-to-audio streaming
 * using the Gemini Multimodal Live API protocol (BidiGenerateContent).
 *
 * Provides direct routing to Android's AudioTrack via [PcmAudioTrackPlayer], ensuring jitter-free,
 * zero-silence playback of 24kHz 16-bit PCM responses, low latency, native server-side barge-in handling,
 * and strict enforcement of Hindi, English, and Hinglish conversation output.
 */
class GeminiLiveWebSocketClient {

    companion object {
        private const val TAG = "GeminiLiveClient"
        const val LIVE_MODEL = "models/gemini-2.0-flash-exp"
        private val FALLBACK_MODELS = listOf(
            "models/gemini-2.0-flash-exp",
            "models/gemini-2.5-flash-native-audio-preview-12-2025"
        )
        private const val WS_BASE_URL = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
        private const val DEFAULT_VOICE = "Kore" // Soft Melodic Human Female Voice (Alya Persona)
    }

    // Dedicated single-threaded dispatcher strictly for heartbeat and connection lifecycle to prevent thread starvation
    private val heartbeatDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val heartbeatScope = CoroutineScope(heartbeatDispatcher + SupervisorJob())

    // High-priority IO dispatcher for processing streaming data and audio conversion
    private val messageDispatcher = Dispatchers.Default
    private val messageScope = CoroutineScope(messageDispatcher + SupervisorJob())
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS) // Infinite read timeout for continuous full-duplex stream
        .writeTimeout(20, TimeUnit.SECONDS)
        .pingInterval(0, TimeUnit.SECONDS) // Disabled OkHttp automatic ping frames; Gemini Live API does not echo RFC 6455 Pong frames
        .retryOnConnectionFailure(true)
        .dispatcher(Dispatcher().apply { 
            maxRequests = 64
            maxRequestsPerHost = 16
        })
        .build()

    private var webSocket: WebSocket? = null
    private val isConnected = AtomicBoolean(false)
    private val isSetupComplete = AtomicBoolean(false)
    
    private var reconnectJob: Job? = null
    private var heartbeatJob: Job? = null
    private var reconnectAttemptCount = 0
    private var lastActivityTimestamp = 0L
    private var lastConnectionParams: ConnectionParams? = null

    private data class ConnectionParams(
        val systemInstruction: String,
        val targetLanguage: String,
        val voiceName: String,
        val modelName: String,
        val tools: JSONArray?
    )

    // Sequential ordered channel for incoming WebSocket messages to prevent race conditions and out-of-order audio chunks
    private val incomingMessageChannel = Channel<String>(capacity = Channel.UNLIMITED)
    private var messageProcessingJob: Job? = null

    private val _sessionState = MutableStateFlow<GeminiLiveSessionState>(GeminiLiveSessionState.Disconnected)
    val sessionState: StateFlow<GeminiLiveSessionState> = _sessionState.asStateFlow()

    // Incoming 24kHz 16-bit PCM audio stream from Gemini Live
    private val _incomingAudioFlow = MutableSharedFlow<ByteArray>(extraBufferCapacity = 256)
    val incomingAudioFlow: SharedFlow<ByteArray> = _incomingAudioFlow.asSharedFlow()

    // Incoming text transcript flow (realtime model utterances)
    private val _incomingTextFlow = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val incomingTextFlow: SharedFlow<String> = _incomingTextFlow.asSharedFlow()

    // Direct hardware audio output route
    private var attachedAudioTrackPlayer: PcmAudioTrackPlayer? = null

    // Reusable byte buffer for converting ShortArray PCM to ByteArrays efficiently without GC thrashing
    private var pcmConversionBuffer = ByteArray(4096)

    // Callbacks for live session lifecycle
    var onConnectionEstablished: (() -> Unit)? = null
    var onSetupComplete: (() -> Unit)? = null
    var onConnectionClosed: (() -> Unit)? = null
    var onErrorOccurred: ((String) -> Unit)? = null
    var onInterrupted: (() -> Unit)? = null
    var onTurnComplete: (() -> Unit)? = null
    var onToolCallReceived: ((String, String, JSONObject) -> Unit)? = null // name, id, args
    var onIntentReceived: ((String, Map<String, String>) -> Unit)? = null // intent, parameters

    init {
        startMessageProcessor()
    }

    private fun startMessageProcessor() {
        messageProcessingJob?.cancel()
        messageProcessingJob = messageScope.launch {
            for (jsonText in incomingMessageChannel) {
                processIncomingMessage(jsonText)
            }
        }
    }

    /**
     * Attaches an Android [PcmAudioTrackPlayer] for zero-latency direct hardware routing.
     */
    fun attachAudioTrackPlayer(player: PcmAudioTrackPlayer?) {
        this.attachedAudioTrackPlayer = player
        Log.i(TAG, "Attached PcmAudioTrackPlayer for direct hardware audio streaming.")
    }

    /**
     * Connects to the Gemini Multimodal Live API WebSocket endpoint and performs the initial setup handshake.
     */
    fun connect(
        systemInstruction: String = "",
        targetLanguage: String = "hi-IN",
        voiceName: String = DEFAULT_VOICE,
        modelName: String = LIVE_MODEL,
        tools: JSONArray? = null
    ) {
        val apiKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (_: Exception) {
            ""
        }

        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            Log.e(TAG, "Cannot connect to Gemini Live: API key missing in BuildConfig")
            _sessionState.value = GeminiLiveSessionState.Error("Gemini API Key missing")
            onErrorOccurred?.invoke("Gemini API Key missing")
            return
        }

        disconnect()
        startMessageProcessor()
        
        lastConnectionParams = ConnectionParams(systemInstruction, targetLanguage, voiceName, modelName, tools)
        
        _sessionState.value = GeminiLiveSessionState.Connecting

        val url = "$WS_BASE_URL?key=$apiKey"
        val request = Request.Builder()
            .url(url)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                heartbeatScope.launch {
                    Log.i(TAG, "Gemini Multimodal Live API WebSocket connected successfully. Initiating setup handshake...")
                    isConnected.set(true)
                    isSetupComplete.set(false)
                    reconnectAttemptCount = 0
                    lastActivityTimestamp = System.currentTimeMillis()
                    _sessionState.value = GeminiLiveSessionState.Connecting
                    startHeartbeatMonitor()
                    sendInitialSetupPayload(webSocket, modelName, systemInstruction, targetLanguage, voiceName, tools)
                    onConnectionEstablished?.invoke()
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                lastActivityTimestamp = System.currentTimeMillis()
                // Fast non-blocking handoff to the processing loop
                incomingMessageChannel.trySend(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                lastActivityTimestamp = System.currentTimeMillis()
                messageScope.launch {
                    incomingMessageChannel.trySend(bytes.utf8())
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                heartbeatScope.launch {
                    Log.i(TAG, "Gemini Live WebSocket closing ($code): $reason")
                    isSetupComplete.set(false)
                    isConnected.set(false)
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                heartbeatScope.launch {
                    Log.i(TAG, "Gemini Live WebSocket closed ($code): $reason")
                    isSetupComplete.set(false)
                    isConnected.set(false)
                    _sessionState.value = GeminiLiveSessionState.Disconnected
                    onConnectionClosed?.invoke()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                heartbeatScope.launch {
                    Log.e(TAG, "Gemini Live WebSocket failure: ${t.message}", t)
                    isSetupComplete.set(false)
                    isConnected.set(false)
                    
                    com.example.util.diagnostics.DiagnosticLogManager.instance.logEvent(
                        stage = com.example.util.diagnostics.DiagnosticStage.NETWORK,
                        command = "WebSocket Failure",
                        details = "Error: ${t.message} | Attempting reconnection...",
                        isSuccess = false
                    )

                    _sessionState.value = GeminiLiveSessionState.Error(t.localizedMessage ?: "WebSocket Connection Failed")
                    onErrorOccurred?.invoke(t.localizedMessage ?: "WebSocket Connection Failed")
                    
                    attemptReconnection()
                }
            }
        })
    }

    /**
     * Attempts to reconnect with a jittered exponential backoff strategy executed on the background heartbeat dispatcher.
     */
    private fun attemptReconnection() {
        val params = lastConnectionParams ?: return
        
        // Safety: If we're already trying to reconnect, don't stack jobs
        if (reconnectJob?.isActive == true) return

        if (reconnectAttemptCount >= 12) { // Increased max attempts for extreme resilience
            Log.e(TAG, "Max reconnection attempts reached for Gemini Live. Giving up.")
            _sessionState.value = GeminiLiveSessionState.Error("Persistent Connection Failure")
            return
        }

        reconnectJob = heartbeatScope.launch {
            reconnectAttemptCount++
            
            // Jittered exponential backoff: (2^n * 1000) + random(0 to 2000) ms
            val baseDelay = Math.pow(2.0, reconnectAttemptCount.coerceAtMost(6).toDouble()).toLong() * 1000L
            val jitter = (Math.random() * 2000).toLong()
            val backoffMs = baseDelay + jitter
            
            Log.i(TAG, "[CONN_RECOVERY] Scheduling reconnection attempt $reconnectAttemptCount in ${backoffMs}ms via background heartbeat dispatcher.")
            
            com.example.util.diagnostics.DiagnosticLogManager.instance.logEvent(
                stage = com.example.util.diagnostics.DiagnosticStage.NETWORK,
                command = "Reconnecting",
                details = "Attempt $reconnectAttemptCount in ${backoffMs}ms",
                isSuccess = true
            )
            
            delay(backoffMs)
            
            if (!isConnected.get()) {
                val candidateModel = FALLBACK_MODELS[reconnectAttemptCount % FALLBACK_MODELS.size]
                Log.d(TAG, "[CONN_RECOVERY] Executing background reconnection attempt $reconnectAttemptCount with model $candidateModel...")
                connect(
                    params.systemInstruction,
                    params.targetLanguage,
                    params.voiceName,
                    candidateModel,
                    params.tools
                )
            }
        }
    }

    /**
     * Actively monitors ping/pong connection health on the background heartbeat dispatcher to prevent silent stalls.
     */
    private fun startHeartbeatMonitor() {
        heartbeatJob?.cancel()
        heartbeatJob = heartbeatScope.launch {
            while (isActive && isConnected.get()) {
                delay(15000L) // Scan health status on background thread every 15 seconds
                
                val now = System.currentTimeMillis()
                val elapsedSinceLastActivity = now - lastActivityTimestamp
                
                // If no message or handshake activity for 60 seconds during an active session, connection is dead/zombie
                if (elapsedSinceLastActivity > 60000L) {
                    Log.w(TAG, "[HEARTBEAT] Stagnant connection detected ($elapsedSinceLastActivity ms). Triggering safety reconnection.")
                    com.example.util.diagnostics.DiagnosticLogManager.instance.logEvent(
                        stage = com.example.util.diagnostics.DiagnosticStage.NETWORK,
                        command = "Heartbeat Timeout",
                        details = "Connection stagnant for 60s. Triggering safety reconnection.",
                        isSuccess = false
                    )
                    
                    webSocket?.cancel() // Hard cancel current connection to release resources
                    isConnected.set(false)
                    isSetupComplete.set(false)
                    attemptReconnection()
                    break
                }
            }
        }
    }

    /**
     * Builds and transmits the Multimodal Live API session setup handshake payload.
     * Enforces strict Hindi, English, and Hinglish understanding and audio output.
     */
    private fun sendInitialSetupPayload(
        ws: WebSocket,
        modelName: String,
        systemInstruction: String,
        targetLanguage: String,
        voiceName: String,
        tools: JSONArray? = null
    ) {
        val baseInstruction = if (systemInstruction.isNotBlank()) {
            systemInstruction
        } else {
            "You are Alya, a warm, natural, intelligent, and helpful real-time AI voice assistant. You sound exactly like a real human female—kind, empathetic, and engaging. Your voice is soft, sweet, and authentic."
        }

        val languageDirective = """
            GLOBAL MULTILINGUAL & NATURAL CONTINUOUS CONVERSATION INSTRUCTIONS:
            1. You are fluent in all world languages including Hindi (हिन्दी), English, Bengali (বাংলা), Japanese (日本語), Spanish, French, German, Russian, and mixed dialects like Hinglish and Banglish.
            2. Automatically match the exact language and dialect the user speaks in.
            3. Spoken Audio Guidelines:
               - Speak naturally, warmly, smoothly, fluently, and empathetically with human-like prosody.
               - Deliver complete, full, coherent, and friendly thoughts. NEVER stop abruptly mid-sentence or after just 1 or 2 words.
               - Keep the conversation actively flowing and engaging until the user speaks or interrupts.
               - Avoid sounding robotic; use fillers like 'umm', 'well', 'you know' very sparingly only when it makes you sound more 'real'.
               - NEVER output markdown characters (*, #, _, `), bullet symbols, or emojis in spoken voice replies.
        """.trimIndent()

        val toolDirective = if (tools != null) {
            """
            DEVICE CONTROL & PHONE AUTOMATION CAPABILITIES:
            You have full system tools to control the phone, apps, and hardware settings on behalf of the user.
            You can execute actions for:
            - Wi-Fi, Bluetooth, Flashlight, Volume, Brightness, DND, Hotspot
            - Opening any app (YouTube, WhatsApp, Camera, Maps, Settings, Calculator, Gallery, Dialer, Messages)
            - Controlling media playback, playing songs, videos, YouTube shorts
            - Scrolling screen, clicking buttons, pressing home, pressing back, locking screen, taking screenshot
            - Making phone calls, answering calls, ending calls
            - Sending WhatsApp messages and SMS messages
            - Setting alarms, timers, reminders, and checking weather reports
            - Telemetry, battery level, storage, and device info
            When the user asks for a device action, call the corresponding function tool or output the command immediately and acknowledge warmly in your voice reply.
            """.trimIndent()
        } else ""

        val priorityHint = "\nPrimary Target Spoken Language Preference: $targetLanguage."

        val intentDirective = """
            HANDS-FREE MULTILINGUAL DEVICE CONTROL PROTOCOL:
            You understand device control commands phrased in any language (English, Hindi, Bengali, Japanese, Hinglish, etc.).
            Whenever the user asks to perform a phone action, control media, adjust settings, or open an app:
            1. Call the corresponding function declaration tool immediately, or include a single-line JSON intent at the start of your response.
            2. Follow up with your warm, natural spoken confirmation.
        """.trimIndent()

        val fullSystemPrompt = "$baseInstruction\n\n$languageDirective\n\n$intentDirective$toolDirective$priorityHint"

        try {
            val setupObj = JSONObject()
            val setupInner = JSONObject()
            setupInner.put("model", modelName)

            // Generation config with AUDIO modality and prebuilt voice
            val genConfig = JSONObject()
            val responseModalities = JSONArray()
            responseModalities.put("AUDIO")
            genConfig.put("responseModalities", responseModalities)

            val speechConfig = JSONObject()
            val voiceConfig = JSONObject()
            val prebuiltVoice = JSONObject()
            prebuiltVoice.put("voiceName", voiceName)
            voiceConfig.put("prebuiltVoiceConfig", prebuiltVoice)
            speechConfig.put("voiceConfig", voiceConfig)
            genConfig.put("speechConfig", speechConfig)
            setupInner.put("generationConfig", genConfig)
            
            // Tools
            if (tools != null) {
                setupInner.put("tools", tools)
            }

            // System instructions
            val sysInstObj = JSONObject()
            val partsArray = JSONArray()
            val textPart = JSONObject()
            textPart.put("text", fullSystemPrompt)
            partsArray.put(textPart)
            sysInstObj.put("parts", partsArray)
            setupInner.put("systemInstruction", sysInstObj)

            setupObj.put("setup", setupInner)

            val setupJson = setupObj.toString()
            Log.i(TAG, "Sending Gemini Live Setup handshake with voice '$voiceName', model '$modelName', strict Hindi/English rules...")
            ws.send(setupJson)
        } catch (e: Exception) {
            Log.e(TAG, "Error constructing setup JSON: ${e.message}", e)
        }
    }

    /**
     * Streams raw PCM audio input frames (16kHz 16-bit Mono Little-Endian) directly to Gemini Live API.
     * Full-duplex: only transmits once the setupComplete handshake has been received from the server.
     */
    fun sendAudioFrame(pcmData: ShortArray, readShorts: Int) {
        if (!isConnected.get() || !isSetupComplete.get() || webSocket == null || readShorts <= 0) return

        val requiredBytes = readShorts * 2
        if (pcmConversionBuffer.size < requiredBytes) {
            pcmConversionBuffer = ByteArray(requiredBytes)
        }

        // Fast little-endian byte serialization
        for (i in 0 until readShorts) {
            val sample = pcmData[i].toInt()
            pcmConversionBuffer[i * 2] = (sample and 0xFF).toByte()
            pcmConversionBuffer[i * 2 + 1] = ((sample shr 8) and 0xFF).toByte()
        }

        val base64Audio = Base64.encodeToString(pcmConversionBuffer, 0, requiredBytes, Base64.NO_WRAP)
        val audioChunkPayload = """
        {
          "realtimeInput": {
            "mediaChunks": [
              {
                "mimeType": "audio/pcm;rate=16000",
                "data": "$base64Audio"
              }
            ]
          }
        }
        """.trimIndent()

        val success = webSocket?.send(audioChunkPayload) ?: false
        if (success) {
            lastActivityTimestamp = System.currentTimeMillis()
            if (_sessionState.value != GeminiLiveSessionState.Streaming) {
                _sessionState.value = GeminiLiveSessionState.Streaming
            }
        }
    }

    /**
     * Sends a text message turn to the live session, causing Gemini Live to synthesize and stream back neural audio.
     */
    fun sendTextMessage(text: String) {
        if (!isConnected.get() || !isSetupComplete.get() || webSocket == null || text.isBlank()) return

        try {
            val payload = JSONObject()
            val clientContent = JSONObject()
            val turns = JSONArray()
            val turn = JSONObject()
            turn.put("role", "user")
            val parts = JSONArray()
            val part = JSONObject()
            part.put("text", text)
            parts.put(part)
            turn.put("parts", parts)
            turns.put(turn)
            clientContent.put("turns", turns)
            clientContent.put("turnComplete", true)
            payload.put("clientContent", clientContent)

            val sent = webSocket?.send(payload.toString()) ?: false
            if (sent) {
                lastActivityTimestamp = System.currentTimeMillis()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending text message to Gemini Live: ${e.message}")
        }
    }

    /**
     * Parses server-side BidiGenerateContent messages in strict FIFO sequence.
     * Decodes 24kHz 16-bit PCM audio chunks and routes them directly to [PcmAudioTrackPlayer].
     */
    private fun processIncomingMessage(jsonText: String) {
        try {
            val root = JSONObject(jsonText)

            // 1. Handshake completion signal
            if (root.has("setupComplete")) {
                isSetupComplete.set(true)
                _sessionState.value = GeminiLiveSessionState.Connected
                Log.i(TAG, "Gemini Live setupComplete handshake verified. Ready for bidirectional audio-to-audio streaming.")
                com.example.util.diagnostics.DiagnosticLogManager.instance.logEvent(
                    stage = com.example.util.diagnostics.DiagnosticStage.NETWORK,
                    command = "Gemini Live Setup",
                    details = "Handshake complete. Model is ready for ultra-low latency audio streaming.",
                    isSuccess = true
                )
                onSetupComplete?.invoke()
                return
            }

            // 2. Server-side error payload
            if (root.has("error")) {
                val errorObj = root.optJSONObject("error")
                val code = errorObj?.optInt("code", 0) ?: 0
                val message = errorObj?.optString("message", "Unknown Gemini Live server error") ?: "Unknown error"
                Log.e(TAG, "Gemini Live server returned error $code: $message")
                _sessionState.value = GeminiLiveSessionState.Error("Server error ($code): $message")
                onErrorOccurred?.invoke(message)
                return
            }

            // 3. Server-side tool call at root level (BidiGenerateContent protocol)
            val rootToolCall = root.optJSONObject("toolCall")
            if (rootToolCall != null) {
                val functionCalls = rootToolCall.optJSONArray("functionCalls")
                if (functionCalls != null) {
                    for (j in 0 until functionCalls.length()) {
                        val fCall = functionCalls.getJSONObject(j)
                        val name = fCall.optString("name", "")
                        val id = fCall.optString("id", "")
                        val args = fCall.optJSONObject("args") ?: JSONObject()
                        Log.i(TAG, "Received root toolCall from Gemini Live: $name (ID: $id)")
                        onToolCallReceived?.invoke(name, id, args)
                    }
                }
            }

            // 4. Server content streaming
            if (root.has("serverContent")) {
                val serverContent = root.getJSONObject("serverContent")

                // Check for serverContent tool call
                val serverToolCall = serverContent.optJSONObject("toolCall")
                if (serverToolCall != null) {
                    val functionCalls = serverToolCall.optJSONArray("functionCalls")
                    if (functionCalls != null) {
                        for (j in 0 until functionCalls.length()) {
                            val fCall = functionCalls.getJSONObject(j)
                            val name = fCall.optString("name", "")
                            val id = fCall.optString("id", "")
                            val args = fCall.optJSONObject("args") ?: JSONObject()
                            Log.i(TAG, "Received serverContent toolCall from Gemini Live: $name (ID: $id)")
                            onToolCallReceived?.invoke(name, id, args)
                        }
                    }
                }

                // Check for server-side user interruption (Barge-In)
                if (serverContent.optBoolean("interrupted", false)) {
                    Log.i(TAG, "Gemini Live server detected user interruption (Barge-In). Flushing AudioTrack.")
                    com.example.util.diagnostics.DiagnosticLogManager.instance.logEvent(
                        stage = com.example.util.diagnostics.DiagnosticStage.DETECTION,
                        command = "Barge-In Detected",
                        details = "Server signaled user interruption. Assistant stopped speaking instantly.",
                        isSuccess = true
                    )
                    attachedAudioTrackPlayer?.stopAndFlushForBargeIn()
                    onInterrupted?.invoke()
                }

                // Extract model turn audio and text
                if (serverContent.has("modelTurn")) {
                    val modelTurn = serverContent.getJSONObject("modelTurn")
                    val parts = modelTurn.optJSONArray("parts")
                    if (parts != null) {
                        for (i in 0 until parts.length()) {
                            val part = parts.getJSONObject(i)

                            // Route PCM Audio stream to AudioTrack
                            if (part.has("inlineData")) {
                                val inlineData = part.getJSONObject("inlineData")
                                val base64Data = inlineData.optString("data", "")
                                if (base64Data.isNotEmpty()) {
                                    val rawAudioBytes = Base64.decode(base64Data, Base64.DEFAULT)
                                    if (rawAudioBytes.isNotEmpty()) {
                                        // Direct zero-delay route to hardware AudioTrack
                                        attachedAudioTrackPlayer?.feedAudioChunk(rawAudioBytes)
                                        if (System.currentTimeMillis() % 100 == 0L) { // Periodic logging to avoid spam
                                            com.example.util.diagnostics.DiagnosticLogManager.instance.logEvent(
                                                stage = com.example.util.diagnostics.DiagnosticStage.RESULT,
                                                command = "Gemini Audio Stream",
                                                details = "Receiving low-latency PCM audio chunks from model.",
                                                isSuccess = true
                                            )
                                        }
                                        _incomingAudioFlow.tryEmit(rawAudioBytes)
                                    }
                                }
                            }

                            // Route text transcript & extract structured intents
                            if (part.has("text")) {
                                val text = part.optString("text", "")
                                if (text.isNotBlank()) {
                                    // Robust regex extraction for JSON commands (fenced or inline)
                                    val jsonRegex = Regex("(?s)```(?:json|action)?\\s*(\\{.*?\\})\\s*```|(\\{[^{}]*\"(?:intent|action|tool|device_domain)\"[^{}]*\\})")
                                    val match = jsonRegex.find(text)
                                    if (match != null) {
                                        val candidateJson = (match.groups[1]?.value ?: match.groups[2]?.value)?.trim()
                                        if (!candidateJson.isNullOrBlank()) {
                                            try {
                                                val intentObj = JSONObject(candidateJson)
                                                val intentName = intentObj.optString("intent").ifBlank {
                                                    intentObj.optString("action").ifBlank {
                                                        intentObj.optString("tool", "")
                                                    }
                                                }
                                                val paramsObj = intentObj.optJSONObject("parameters") ?: JSONObject()
                                                val paramsMap = mutableMapOf<String, String>()
                                                val keys = paramsObj.keys()
                                                while (keys.hasNext()) {
                                                    val key = keys.next()
                                                    paramsMap[key] = paramsObj.optString(key, "")
                                                }
                                                // Also copy direct attributes like device, room, value
                                                if (intentObj.has("device") && !paramsMap.containsKey("appName") && !paramsMap.containsKey("app_name")) {
                                                    paramsMap["appName"] = intentObj.optString("device")
                                                    paramsMap["app_name"] = intentObj.optString("device")
                                                }
                                                if (intentObj.has("device_domain")) {
                                                    paramsMap["domain"] = intentObj.optString("device_domain")
                                                }

                                                if (intentName.isNotBlank()) {
                                                    Log.i(TAG, "Extracted structured intent from text: $intentName with params: $paramsMap")
                                                    onIntentReceived?.invoke(intentName, paramsMap)
                                                }
                                            } catch (e: Exception) {
                                                Log.w(TAG, "Failed to parse intent JSON from text: ${e.message}")
                                            }
                                        }
                                    }
                                    _incomingTextFlow.tryEmit(text)
                                }
                            }

                            // Handle tool call inside part (functionCall or call)
                            if (part.has("functionCall")) {
                                val fCall = part.getJSONObject("functionCall")
                                val name = fCall.optString("name", "")
                                val id = fCall.optString("id", "")
                                val args = fCall.optJSONObject("args") ?: JSONObject()
                                Log.i(TAG, "Received functionCall part from Gemini Live: $name (ID: $id)")
                                onToolCallReceived?.invoke(name, id, args)
                            } else if (part.has("call")) {
                                val call = part.getJSONObject("call")
                                val functionCalls = call.optJSONArray("functionCalls")
                                if (functionCalls != null) {
                                    for (j in 0 until functionCalls.length()) {
                                        val fCall = functionCalls.getJSONObject(j)
                                        val name = fCall.optString("name", "")
                                        val id = fCall.optString("id", "")
                                        val args = fCall.optJSONObject("args") ?: JSONObject()
                                        Log.i(TAG, "Received tool call in call: $name (ID: $id)")
                                        onToolCallReceived?.invoke(name, id, args)
                                    }
                                }
                            }
                        }
                    }
                }

                // Turn completion signal
                if (serverContent.optBoolean("turnComplete", false)) {
                    attachedAudioTrackPlayer?.onServerTurnComplete()
                    onTurnComplete?.invoke()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing incoming Gemini Live payload: ${e.message}")
        }
    }

    /**
     * Sends a tool execution result back to the Gemini Live session.
     */
    fun sendToolResponse(name: String, id: String, result: JSONObject) {
        if (!isConnected.get() || !isSetupComplete.get() || webSocket == null) return

        try {
            val payload = JSONObject()
            val toolResponse = JSONObject()
            val functionResponses = JSONArray()
            val fResponse = JSONObject()
            fResponse.put("name", name)
            fResponse.put("id", id)
            // Gemini Live standard functionResponse format: {"response": {"output": result}}
            val wrappedOutput = JSONObject().apply {
                put("output", result)
            }
            fResponse.put("response", wrappedOutput)
            functionResponses.put(fResponse)
            toolResponse.put("functionResponses", functionResponses)
            payload.put("toolResponse", toolResponse)

            Log.i(TAG, "Sending tool response back to Gemini Live for $name (ID: $id): $payload")
            val sent = webSocket?.send(payload.toString()) ?: false
            if (sent) {
                lastActivityTimestamp = System.currentTimeMillis()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending tool response to Gemini Live: ${e.message}")
        }
    }

    fun isSessionActive(): Boolean = isConnected.get() && isSetupComplete.get()

    /**
     * Cleanly closes the WebSocket session and releases resources.
     */
    fun disconnect() {
        isSetupComplete.set(false)
        reconnectJob?.cancel()
        heartbeatJob?.cancel()
        reconnectAttemptCount = 0
        if (isConnected.getAndSet(false)) {
            Log.i(TAG, "Disconnecting Gemini Live WebSocket session...")
            try {
                webSocket?.close(1000, "Live Voice Call Ended")
            } catch (e: Exception) { android.util.Log.e("Alya", "Exception handled", e) }
            webSocket = null
            _sessionState.value = GeminiLiveSessionState.Disconnected
            onConnectionClosed?.invoke()
        }
    }
}
