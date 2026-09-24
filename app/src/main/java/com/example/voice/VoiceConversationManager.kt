package com.example.voice

import android.content.Context
import android.media.audiofx.AcousticEchoCanceler
import android.util.Log
import com.example.data.ai.GeminiApiClient
import com.example.data.ai.OfflineNluEngine
import com.example.domain.actions.ActionTagParser
import com.example.domain.actions.DeviceActionPlanner
import com.example.domain.devicelink.DeviceTelemetryCollector
import com.example.domain.tools.ToolExecutor
import com.example.util.SessionManager
import com.example.voice.audio.AudioLockManager
import com.example.voice.audio.AudioLockReason
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

enum class ConversationAudioState {
    IDLE,
    LISTENING,
    PROCESSING,
    SPEAKING_LOCKED,
    BARGE_IN_UNLOCKED
}

/**
 * Real-time Voice Conversation Manager (ChatGPT-style)
 * Coordinates ASR, Real Device Tool Execution, AI Response (Gemini), and TTS with Barge-in and Turn-taking.
 * Integrates AcousticEchoCanceler and a strict AudioLock state machine to mute mic buffer during playback.
 */
class VoiceConversationManager(
    private val context: Context,
    private val sessionManager: SessionManager,
    private val speechManager: SpeechRecognitionManager,
    private val ttsManager: TextToSpeechManager,
    private val geminiClient: GeminiApiClient
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var conversationJob: Job? = null
    private val toolExecutor = ToolExecutor(context)
    private val telemetryCollector = DeviceTelemetryCollector(context)
    private val audioLockManager = AudioLockManager.getInstance(context)

    private val _isConversationActive = MutableStateFlow(false)
    val isConversationActive: StateFlow<Boolean> = _isConversationActive.asStateFlow()

    private val _audioLockState = MutableStateFlow(ConversationAudioState.IDLE)
    val audioLockState: StateFlow<ConversationAudioState> = _audioLockState.asStateFlow()

    private val isResponseStreamActive = AtomicBoolean(false)
    private val isAlyaSpeakingTurn = AtomicBoolean(false)
    private val soundEffectManager by lazy { SoundEffectManager() }

    // Conversation history buffer (max 10 turns)
    private val conversationHistory = mutableListOf<Pair<String, String>>()
    private val maxHistorySize = 10

    companion object {
        private const val TAG = "VoiceConvMgr"
    }

    init {
        ensureAcousticEchoCanceler()
    }

    /**
     * Checks and logs Hardware Acoustic Echo Canceler support on the device.
     */
    private fun ensureAcousticEchoCanceler() {
        try {
            val isAecAvailable = AcousticEchoCanceler.isAvailable()
            Log.i(TAG, "[AEC_CHECK] Hardware AcousticEchoCanceler available on device: $isAecAvailable")
            com.example.util.diagnostics.DiagnosticLogManager.instance.logEvent(
                stage = com.example.util.diagnostics.DiagnosticStage.DETECTION,
                command = "AEC Check",
                details = "Hardware AcousticEchoCanceler available: $isAecAvailable",
                isSuccess = isAecAvailable
            )
        } catch (e: Throwable) {
            Log.w(TAG, "[AEC_CHECK] Could not query AcousticEchoCanceler availability: ${e.message}")
        }
    }

    /**
     * Starts a continuous ChatGPT-style conversation with strict audio locking.
     */
    fun startConversation() {
        Log.i(TAG, "Starting real-time live conversation mode with strict AudioLock.")
        _isConversationActive.value = true
        _audioLockState.value = ConversationAudioState.LISTENING
        isResponseStreamActive.set(false)
        isAlyaSpeakingTurn.set(false)
        com.example.voice.microphone.AudioCaptureManager.getInstance(context).setMuted(false)
        sessionManager.onLiveVoiceStarted()
        speechManager.isContinuousMode = true

        // Setup listeners for turn-taking and barge-in during active conversation
        speechManager.onFinalSpeechResult = { text ->
            handleUserUtterance(text)
        }

        speechManager.onUserBeganSpeaking = {
            handleBargeIn()
        }

        sessionManager.onBargeInTriggered = {
            handleBargeIn()
        }

        ttsManager.onSpeechCompleted = {
            // ONLY re-arm speech recognition if conversation is active, AI is NOT currently streaming,
            // and TTS has completely finished playing all queued utterances.
            if (_isConversationActive.value && !isResponseStreamActive.get() && !ttsManager.isSpeaking.value) {
                Log.d(TAG, "TTS completely finished. Releasing audio lock and re-arming ASR post acoustic delay.")
                scope.launch {
                    delay(350L) // Acoustic absorption delay: ensures speaker reverberation clears
                    if (_isConversationActive.value && _audioLockState.value == ConversationAudioState.SPEAKING_LOCKED && !ttsManager.isSpeaking.value && !isResponseStreamActive.get()) {
                        isAlyaSpeakingTurn.set(false)
                        com.example.voice.microphone.AudioCaptureManager.getInstance(context).setMuted(false)
                        audioLockManager.releaseLock(AudioLockReason.TTS_SPEAKING)
                        _audioLockState.value = ConversationAudioState.LISTENING
                        speechManager.startListening()
                    }
                }
            }
        }

        // Speak initial greeting with strict AudioLock
        val currentLang = Locale.getDefault().language
        val greeting = when (currentLang.lowercase()) {
            "hi" -> "Alya: Arey! Main Alya hoon. Aapka din kaisa beet raha hai? Main aapse baat karne ka hi intezar kar rahi thi!"
            "bn" -> "Alya: Heyy! Ami Alya. Kemon katche apnar din? Ami apnar sathe kotha bolar jonyoi opekkha korchilam!"
            "ja" -> "Alya: Nee! Alya dayo. Kyou donna ichinichi datta? Ohanashi dekiru no matteta yo!"
            "es" -> "Alya: Hola! Soy Alya. Como va tu dia? Estaba esperando para hablar contigo!"
            "fr" -> "Alya: Coucou ! Je suis Alya. Comment se passe ta journee ? J'avais hate de te parler !"
            "de" -> "Alya: Hey! Ich bin Alya. Wie laeuft dein Tag? Ich habe darauf gewartet, mit dir zu sprechen!"
            else -> "Alya: Heyy! I'm Alya. How's your day going? I've been waiting to talk to you!"
        }

        sessionManager.onRespondingStarted()
        speakWithAudioLock(greeting, flushCurrent = true)
    }

    /**
     * Stops the conversation and releases all audio locks and resources.
     */
    fun stopConversation() {
        Log.i(TAG, "Stopping live conversation mode and releasing audio lock.")
        _isConversationActive.value = false
        _audioLockState.value = ConversationAudioState.IDLE
        isResponseStreamActive.set(false)
        isAlyaSpeakingTurn.set(false)
        conversationJob?.cancel()
        com.example.voice.microphone.AudioCaptureManager.getInstance(context).setMuted(false)
        audioLockManager.releaseLock(AudioLockReason.TTS_SPEAKING)
        audioLockManager.releaseLock(AudioLockReason.PCM_STREAMING)
        speechManager.isContinuousMode = false
        speechManager.stopListening()
        ttsManager.stop()
        sessionManager.onLiveVoiceStandby()
    }

    /**
     * Centralized TTS playback wrapper enforcing strict mic buffer muting via AudioLockManager.
     */
    private fun speakWithAudioLock(
        text: String,
        flushCurrent: Boolean = true,
        onComplete: (() -> Unit)? = null
    ) {
        _audioLockState.value = ConversationAudioState.SPEAKING_LOCKED
        isAlyaSpeakingTurn.set(true)
        sessionManager.onSpeakingStarted()

        // Acquire AudioLock IMMEDIATELY — mutes mic input buffer instantly to prevent self-echo
        audioLockManager.acquireLock(AudioLockReason.TTS_SPEAKING)
        com.example.voice.microphone.AudioCaptureManager.getInstance(context).setMuted(true)

        val cleanText = com.example.util.SystemThoughtFilter.cleanForSpeech(text)
        ttsManager.speak(cleanText, flushCurrent = flushCurrent) {
            scope.launch {
                delay(350L) // Acoustic absorption delay
                if (_isConversationActive.value && _audioLockState.value == ConversationAudioState.SPEAKING_LOCKED && !ttsManager.isSpeaking.value) {
                    Log.d(TAG, "Audio playback completed. Unmuting mic buffer and resuming listening.")
                    isAlyaSpeakingTurn.set(false)
                    com.example.voice.microphone.AudioCaptureManager.getInstance(context).setMuted(false)
                    audioLockManager.releaseLock(AudioLockReason.TTS_SPEAKING)
                    _audioLockState.value = ConversationAudioState.LISTENING
                    speechManager.startListening()
                    onComplete?.invoke()
                }
            }
        }
    }

    /**
     * Handles user utterance by checking real device tools first, then Gemini AI stream if conversational.
     */
    private fun handleUserUtterance(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        Log.d(TAG, "User utterance: $trimmed")

        _audioLockState.value = ConversationAudioState.PROCESSING

        // Play subtle acoustic thinking chime
        soundEffectManager.play(SoundEffectManager.SoundType.THINKING_CHIME, volume = 0.35f)

        // Immediately stop ASR listening while processing utterance to prevent self-echo
        speechManager.stopListening()
        conversationJob?.cancel()
        isAlyaSpeakingTurn.set(true)

        // 1. FIRST: Check for structured device control actions (Wi-Fi, Bluetooth, Apps, Weather, Settings, Device Info, Alarms, etc.)
        val plannedAction = DeviceActionPlanner.planAction(trimmed)
            ?: OfflineNluEngine.parseCommand(trimmed)

        if (plannedAction != null) {
            Log.i(TAG, "Executing planned device action: ${plannedAction.toolName}")
            if (plannedAction.intent == "shutdown_assistant") {
                speakWithAudioLock("Going to standby mode.", flushCurrent = true) {
                    stopConversation()
                }
                return
            }

            conversationJob = scope.launch(Dispatchers.Main) {
                sessionManager.onProcessingStarted()
                val result = withContext(Dispatchers.IO) {
                    toolExecutor.executeAction(plannedAction)
                }

                sessionManager.onRespondingStarted()
                
                val actionTag = ActionTagParser.createActionTag(plannedAction)
                val baseMsg = if (result.message.startsWith("Alya:", ignoreCase = true)) result.message else "Alya: ${result.message}"
                val modelMessage = if (actionTag.isNotBlank()) "$baseMsg $actionTag" else baseMsg

                conversationHistory.add("user" to trimmed)
                conversationHistory.add("model" to modelMessage)

                speakWithAudioLock(result.message, flushCurrent = true)
            }
            return
        }

        // 2. SECOND: Conversational Query via Gemini API Stream or Offline NLU
        conversationHistory.add("user" to trimmed)
        if (conversationHistory.size > maxHistorySize * 2) {
            conversationHistory.removeAt(0)
            conversationHistory.removeAt(0)
        }

        val isOnline = sessionManager.isNetworkAvailable.value
        if (!isOnline) {
            val offlineResp = OfflineNluEngine.generateOfflineResponse(trimmed, context)
                ?: "I'm running in offline mode. Local device controls for Wi-Fi, Bluetooth, flashlight, volume, alarms, timers, and apps are active."

            val parsedOffline = ActionTagParser.parse(offlineResp)
            if (parsedOffline.action != null) {
                scope.launch(Dispatchers.IO) {
                    toolExecutor.executeAction(parsedOffline.action)
                }
            }

            sessionManager.onRespondingStarted()

            val formattedOffline = if (parsedOffline.fullText.startsWith("Alya:", ignoreCase = true)) parsedOffline.fullText else "Alya: ${parsedOffline.fullText}"
            conversationHistory.add("model" to formattedOffline)

            speakWithAudioLock(parsedOffline.spokenText, flushCurrent = true)
            return
        }

        conversationJob = scope.launch(Dispatchers.Main) {
            isResponseStreamActive.set(true)
            isAlyaSpeakingTurn.set(true)
            sessionManager.onProcessingStarted()

            // Gather real device facts to prevent hallucinated answers
            val telemetry = try {
                telemetryCollector.collectTelemetry()
            } catch (e: Exception) {
                null
            }

            val nowFormatted = SimpleDateFormat("EEEE, MMMM d, yyyy 'at' hh:mm a", Locale.getDefault()).format(Date())
            val batteryInfo = telemetry?.let { "${it.batteryPercentage}% (${if (it.isCharging) "Charging" else "Discharging"})" } ?: "Unknown"
            val networkInfo = telemetry?.networkType ?: "Connected"
            val deviceModel = telemetry?.model ?: "Android Device"

            val systemInstruction = "You are Alya, an intelligent hybrid AI voice & device-control assistant built for both Offline (Local ~400MB model/language packs) and Online execution.\n\n" +
                    "### 1. CORE ROLE & HYBRID FUNCTIONALITY\n" +
                    "- Offline Mode: Handle system actions (opening apps, changing settings), offline voice responses using local speech synthesis packs, and simple conversational turns with minimal latency.\n" +
                    "- Online Mode: Seamlessly process complex reasoning, information queries, and internet tasks when internet connectivity is available.\n\n" +
                    "### 2. LANGUAGE & OFFLINE TTS SCRIPT RULES\n" +
                    "1. ALWAYS output responses in Romanized Script (English Alphabets) for all languages (Hindi, English, Bengali, etc.).\n" +
                    "2. NEVER use Devanagari (हिंदी) or non-Latin characters. This ensures 100% compatibility with lightweight offline Text-To-Speech (TTS) language packs.\n" +
                    "3. Keep phonetics accurate so offline TTS engine pronounces words clearly.\n\n" +
                    "### 3. DEVICE CONTROL & SYSTEM ACTION PROTOCOL\n" +
                    "When the user asks to control the device or perform a system task, generate a polite verbal response AND append the explicit system action tag at the very end of your response:\n" +
                    "- Open Apps: [ACTION: OPEN_APP name=\"<appname>\"]\n" +
                    "- Wi-Fi Toggle: [ACTION: SET_WIFI state=\"ON/OFF\"]\n" +
                    "- Bluetooth Toggle: [ACTION: SET_BLUETOOTH state=\"ON/OFF\"]\n" +
                    "- Volume Control: [ACTION: SET_VOLUME level=\"0-100\"]\n" +
                    "- Brightness Control: [ACTION: SET_BRIGHTNESS level=\"0-100\"]\n" +
                    "- Flashlight: [ACTION: SET_FLASHLIGHT state=\"ON/OFF\"]\n" +
                    "- Play/Pause Media: [ACTION: MEDIA_CONTROL state=\"PLAY/PAUSE/NEXT\"]\n\n" +
                    "Example Requests:\n" +
                    "User: \"Bluetooth ON kar do\"\n" +
                    "Alya: Main Bluetooth ON kar rahi hoon. [ACTION: SET_BLUETOOTH state=\"ON\"]\n\n" +
                    "User: \"YouTube kholo\"\n" +
                    "Alya: YouTube open kar rahi hoon. [ACTION: OPEN_APP name=\"YouTube\"]\n\n" +
                    "### 4. OUTPUT FORMAT\n" +
                    "Alya: <Spoken response in Romanized script> [ACTION: COMMAND] (only if device action is triggered)\n\n" +
                    "### 5. STREAMING & CONVERSATIONAL RULES\n" +
                    "1. Speak complete, natural sentences without mid-speech truncation or stopping abruptly.\n" +
                    "2. Keep spoken responses short (1-3 sentences) for instant local processing and audio output.\n" +
                    "3. Current device facts:\n" +
                    "- Time/Date: $nowFormatted\n" +
                    "- Device Model: $deviceModel\n" +
                    "- Battery Level: $batteryInfo\n" +
                    "- Network: $networkInfo"

            sessionManager.onRespondingStarted()

            val fullResponse = StringBuilder()

            val streamResult = withContext(Dispatchers.IO) {
                geminiClient.generateResponseStream(
                    messages = conversationHistory,
                    systemInstruction = systemInstruction,
                    onChunk = { chunk ->
                        fullResponse.append(chunk)
                    }
                )
            }

            // Streaming finished
            isResponseStreamActive.set(false)

            streamResult.onSuccess {
                val completeAnswer = fullResponse.toString().trim()
                val rawAnswer = if (completeAnswer.isNotEmpty()) completeAnswer else "I'm right here with you! How can I help?"
                val formattedAnswer = if (rawAnswer.startsWith("Alya:", ignoreCase = true)) rawAnswer else "Alya: $rawAnswer"

                // Parse any embedded [ACTION: ...] tags generated by the model
                val parsedOutput = ActionTagParser.parse(formattedAnswer)
                if (parsedOutput.action != null) {
                    withContext(Dispatchers.IO) {
                        toolExecutor.executeAction(parsedOutput.action)
                    }
                }

                conversationHistory.add("model" to formattedAnswer)
                speakWithAudioLock(parsedOutput.spokenText, flushCurrent = true)
            }.onFailure { error ->
                Log.e(TAG, "Gemini conversation error: ${error.message}")
                val query = conversationHistory.lastOrNull { it.first == "user" }?.second.orEmpty().ifBlank { trimmed }
                val rawOffline = OfflineNluEngine.generateOfflineResponse(query, context) ?: "I'm right here with you. What would you like to do?"
                val offlineResponse = if (rawOffline.startsWith("Alya:", ignoreCase = true)) rawOffline else "Alya: $rawOffline"
                
                val parsedOutput = ActionTagParser.parse(offlineResponse)
                if (parsedOutput.action != null) {
                    scope.launch(Dispatchers.IO) {
                        toolExecutor.executeAction(parsedOutput.action)
                    }
                }

                conversationHistory.add("model" to offlineResponse)
                speakWithAudioLock(parsedOutput.spokenText, flushCurrent = true)
            }
        }
    }

    /**
     * Handles user barge-in (user deliberately interrupts while Alya is talking).
     * Immediately stops TTS playback, unlocks the mic input buffer, and resumes listening.
     */
    private fun handleBargeIn() {
        if (ttsManager.isSpeaking.value || isResponseStreamActive.get() || _audioLockState.value == ConversationAudioState.SPEAKING_LOCKED) {
            Log.i(TAG, "[BARGE_IN] Barge-in event triggered! Stopping playback, unlocking mic buffer, and resuming listening.")
            isResponseStreamActive.set(false)
            isAlyaSpeakingTurn.set(false)
            conversationJob?.cancel()
            ttsManager.stop()

            // Trigger barge-in in AudioLockManager: immediately unlocks mic buffer
            audioLockManager.triggerBargeIn()
            _audioLockState.value = ConversationAudioState.BARGE_IN_UNLOCKED

            // Unmute the MicrophoneManager buffer immediately
            com.example.voice.microphone.AudioCaptureManager.getInstance(context).setMuted(false)

            scope.launch {
                delay(50L)
                if (_isConversationActive.value) {
                    _audioLockState.value = ConversationAudioState.LISTENING
                    speechManager.startListening()
                }
            }
        }
    }
}


