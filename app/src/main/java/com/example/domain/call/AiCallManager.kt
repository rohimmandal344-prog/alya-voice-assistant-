package com.example.domain.call

import android.content.Context
import android.util.Log
import com.example.data.ai.AiPersonality
import com.example.data.ai.GeminiApiClient
import com.example.data.local.AlyaDatabase
import com.example.data.local.entity.CallSessionEntity
import com.example.data.local.entity.CallTranscriptEntryEntity
import com.example.service.IncomingCallService
import com.example.voice.TextToSpeechManager
import com.example.voice.lang.LocaleManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Data model for live call transcript turns.
 */
data class LiveCallTranscriptTurn(
    val id: Long = System.currentTimeMillis(),
    val timestamp: Long = System.currentTimeMillis(),
    val speaker: String, // "caller" or "assistant"
    val text: String,
    val detectedLanguage: String = "en-US",
    val formattedTime: String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
)

/**
 * State representing the active AI Call Assistant session.
 */
data class AiCallSessionState(
    val sessionId: String = "",
    val callerName: String = "",
    val callerNumber: String = "",
    val startTime: Long = 0L,
    val isCallActive: Boolean = false,
    val isAiSpeaking: Boolean = false,
    val isCallerSpeaking: Boolean = false,
    val isProcessingAi: Boolean = false,
    val detectedLanguageName: String = "Detecting...",
    val detectedLocaleTag: String = "en-US",
    val isLanguageLocked: Boolean = false,
    val disconnectReason: String = "",
    val summary: String = "",
    val errorLogs: List<String> = emptyList(),
    val transcripts: List<LiveCallTranscriptTurn> = emptyList()
)

/**
 * AiCallManager
 * 
 * Full-lifecycle Autonomous AI Call Engine:
 * 1. Unscripted, fluid human-like conversation in any language/dialect.
 * 2. Instant caller language detection within the first few seconds.
 * 3. Bilingual polite fallback on ambiguous initial utterances.
 * 4. Autonomous call management & intent-driven disconnect.
 * 5. Complete timestamped transcription with speaker labels.
 * 6. Silent error recovery with timestamped audit logs.
 * 7. Post-call structured summary generation and Room DB persistence.
 */
class AiCallManager(
    private val context: Context,
    private val database: AlyaDatabase,
    private val geminiClient: GeminiApiClient = GeminiApiClient(),
    private val ttsManager: TextToSpeechManager
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val callSessionDao = database.callSessionDao()

    private val _sessionState = MutableStateFlow(AiCallSessionState())
    val sessionState: StateFlow<AiCallSessionState> = _sessionState.asStateFlow()

    private val activeTtsManager: TextToSpeechManager = ttsManager
    private var conversationHistory = mutableListOf<Pair<String, String>>()
    private val errorLogList = mutableListOf<String>()
    private var currentSessionJob: Job? = null

    companion object {
        private const val TAG = "AiCallManager"
        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        @Volatile
        private var INSTANCE: AiCallManager? = null

        fun getInstance(context: Context, database: AlyaDatabase, ttsManager: TextToSpeechManager? = null): AiCallManager {
            return INSTANCE ?: synchronized(this) {
                val resolvedTts = ttsManager ?: (context.applicationContext as? com.example.AlyaApplication)?.ttsManager ?: TextToSpeechManager.getInstance(context)
                val inst = AiCallManager(context.applicationContext, database, ttsManager = resolvedTts)
                INSTANCE = inst
                inst
            }
        }
    }

    /**
     * Starts an autonomous AI inbound call session.
     */
    fun startCallSession(callerName: String, callerNumber: String) {
        val sessionId = UUID.randomUUID().toString()
        val startTime = System.currentTimeMillis()
        conversationHistory.clear()
        errorLogList.clear()

        _sessionState.value = AiCallSessionState(
            sessionId = sessionId,
            callerName = callerName.ifBlank { "Unknown Caller" },
            callerNumber = callerNumber.ifBlank { "Unknown Number" },
            startTime = startTime,
            isCallActive = true,
            isAiSpeaking = false,
            isCallerSpeaking = false,
            isProcessingAi = false,
            detectedLanguageName = "Detecting...",
            detectedLocaleTag = "en-US",
            isLanguageLocked = false,
            transcripts = emptyList(),
            errorLogs = emptyList()
        )

        Log.i(TAG, "[CALL_START] Autonomous AI Call Session started: $sessionId for caller '$callerName'")

        // Send initial welcoming greeting
        scope.launch {
            delay(500)
            deliverInitialGreeting(callerName)
        }
    }

    /**
     * Delivers a natural initial greeting, bilingual if caller language is not yet known.
     */
    private suspend fun deliverInitialGreeting(callerName: String) {
        val greetingText = if (callerName.isNotBlank() && callerName != "Unknown Caller" && callerName != "Unknown Number") {
            "Hello $callerName! This is Alya, AI assistant. How may I help you today? नमस्ते, मैं आपकी क्या सहायता कर सकती हूँ?"
        } else {
            "Hello! This is Alya, AI assistant. How can I help you today? नमस्ते, मैं आपकी क्या मदद करूँ?"
        }

        recordAssistantUtterance(greetingText, "en-US")
        speakAssistantResponse(greetingText, "en-US")
    }

    /**
     * Processes incoming caller speech in real-time.
     */
    fun processCallerUtterance(spokenText: String) {
        val trimmed = spokenText.trim()
        if (trimmed.isBlank() || !_sessionState.value.isCallActive) return

        Log.i(TAG, "[CALLER_SPEECH] Caller said: '$trimmed'")
        val currentState = _sessionState.value

        // Step 1: Real-time Language & Dialect Detection
        val (detectedTag, languageName) = detectLanguageAndDialect(trimmed, currentState.detectedLocaleTag)
        val shouldLockLanguage = !currentState.isLanguageLocked || currentState.detectedLanguageName == "Detecting..."

        val updatedLocaleTag = if (shouldLockLanguage) detectedTag else currentState.detectedLocaleTag
        val updatedLanguageName = if (shouldLockLanguage) languageName else currentState.detectedLanguageName

        _sessionState.value = _sessionState.value.copy(
            detectedLocaleTag = updatedLocaleTag,
            detectedLanguageName = updatedLanguageName,
            isLanguageLocked = true,
            isCallerSpeaking = false,
            isProcessingAi = true
        )

        // Step 2: Record caller transcript
        val callerTurn = LiveCallTranscriptTurn(
            speaker = "caller",
            text = trimmed,
            detectedLanguage = updatedLocaleTag
        )
        val newTranscripts = _sessionState.value.transcripts + callerTurn
        _sessionState.value = _sessionState.value.copy(transcripts = newTranscripts)
        conversationHistory.add("user" to trimmed)

        // Step 3: Check for explicit caller disconnect or farewell intents
        if (isDisconnectOrFarewellIntent(trimmed)) {
            Log.i(TAG, "[CALL_DISCONNECT_INTENT] Caller indicated farewell / disconnect: '$trimmed'")
            scope.launch {
                handleAutonomousDisconnect(
                    farewellPhrase = trimmed,
                    reason = "Caller concluded conversation",
                    localeTag = updatedLocaleTag,
                    languageName = updatedLanguageName
                )
            }
            return
        }

        // Step 4: Generate fluid unscripted AI response using Gemini
        currentSessionJob?.cancel()
        currentSessionJob = scope.launch {
            generateAiCallResponse(trimmed, updatedLocaleTag, updatedLanguageName)
        }
    }

    /**
     * Generates unscripted, natural AI conversational response tailored to caller.
     */
    private suspend fun generateAiCallResponse(
        callerInput: String,
        localeTag: String,
        languageName: String
    ) {
        val systemPrompt = buildInboundCallSystemPrompt(
            callerName = _sessionState.value.callerName,
            localeTag = localeTag,
            languageName = languageName
        )

        val apiMessages = conversationHistory.takeLast(10)

        var aiReply: String? = null
        try {
            val result = withContext(Dispatchers.IO) {
                geminiClient.generateResponse(
                    messages = apiMessages,
                    systemInstruction = systemPrompt,
                    temperature = 0.65f,
                    maxTokens = 250
                )
            }

            if (result.isSuccess) {
                aiReply = result.getOrNull()?.trim()
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "API returned failure"
                logCallError("Gemini call generation failure: $errorMsg")
            }
        } catch (e: Exception) {
            logCallError("Exception during Gemini call generation: ${e.message}")
        }

        // Step 5: Silent Recovery fallback if network/API failed
        val finalResponse = aiReply?.takeIf { it.isNotBlank() } ?: getFallbackResponseForLocale(localeTag)

        _sessionState.value = _sessionState.value.copy(isProcessingAi = false)
        recordAssistantUtterance(finalResponse, localeTag)

        // Step 6: Check if AI concluded the call
        val aiDisconnect = isAssistantFarewellIntent(finalResponse)
        speakAssistantResponse(finalResponse, localeTag) {
            if (aiDisconnect) {
                scope.launch {
                    delay(500)
                    endCallSession(reason = "AI assistant completed request & concluded call")
                }
            }
        }
    }

    /**
     * Handles polite farewell and autonomous call termination.
     */
    private suspend fun handleAutonomousDisconnect(
        farewellPhrase: String,
        reason: String,
        localeTag: String,
        languageName: String
    ) {
        val farewellResponse = when {
            localeTag.startsWith("hi") || languageName.contains("Hindi", ignoreCase = true) ->
                "धन्यवाद! आपका दिन शुभ हो। अलविदा!"
            localeTag.startsWith("bn") || languageName.contains("Bengali", ignoreCase = true) ->
                "ধন্যবাদ! আপনার দিনটি শুভ হোক। বিদায়!"
            localeTag.startsWith("es") || languageName.contains("Spanish", ignoreCase = true) ->
                "¡Muchas gracias! Que tenga un excelente día. ¡Hasta luego!"
            localeTag.startsWith("ja") || languageName.contains("Japanese", ignoreCase = true) ->
                "ありがとうございました！良い一日をお過ごしください。失礼いたします。"
            localeTag.startsWith("fr") || languageName.contains("French", ignoreCase = true) ->
                "Merci beaucoup ! Passez une excellente journée. Au revoir !"
            localeTag.startsWith("de") || languageName.contains("German", ignoreCase = true) ->
                "Vielen Dank! Einen schönen Tag noch. Auf Wiederhören!"
            localeTag.startsWith("ar") || languageName.contains("Arabic", ignoreCase = true) ->
                "شكراً جزيلاً! أتمنى لك يوماً رائعاً. مع السلامة!"
            else -> "Thank you so much! Have a wonderful day. Goodbye!"
        }

        recordAssistantUtterance(farewellResponse, localeTag)
        _sessionState.value = _sessionState.value.copy(isProcessingAi = false)

        speakAssistantResponse(farewellResponse, localeTag) {
            scope.launch {
                delay(800)
                endCallSession(reason = reason)
            }
        }
    }

    /**
     * Ends the call session, generates summary, persists transcript and logs to Room DB.
     */
    fun endCallSession(reason: String = "Call ended") {
        if (!_sessionState.value.isCallActive) return

        val currentState = _sessionState.value
        val endTime = System.currentTimeMillis()
        val durationSeconds = ((endTime - currentState.startTime) / 1000).coerceAtLeast(1)

        Log.i(TAG, "[CALL_END] Terminating call session: ${currentState.sessionId}. Duration: ${durationSeconds}s. Reason: $reason")

        activeTtsManager.stop()
        _sessionState.value = currentState.copy(
            isCallActive = false,
            isAiSpeaking = false,
            isCallerSpeaking = false,
            isProcessingAi = false,
            disconnectReason = reason
        )

        // Actually trigger telephony disconnect
        try {
            IncomingCallService.declineOrEndCall(context)
        } catch (e: Exception) {
            logCallError("Error triggering telephony disconnect: ${e.message}")
        }

        // Generate call summary and save to Room DB
        scope.launch(Dispatchers.IO) {
            val fullTranscriptText = currentState.transcripts.joinToString("\n") {
                "[${it.formattedTime}] ${it.speaker.uppercase()}: ${it.text}"
            }

            val summary = generateCallSummary(currentState, fullTranscriptText)
            val errorLogCombined = errorLogList.joinToString("\n")

            val sessionEntity = CallSessionEntity(
                id = currentState.sessionId,
                callerName = currentState.callerName,
                callerNumber = currentState.callerNumber,
                startTime = currentState.startTime,
                endTime = endTime,
                durationSeconds = durationSeconds,
                detectedLanguage = currentState.detectedLanguageName,
                detectedLocaleTag = currentState.detectedLocaleTag,
                callSummary = summary,
                disconnectReason = reason,
                errorLog = errorLogCombined,
                status = "COMPLETED"
            )

            try {
                callSessionDao.insertCallSession(sessionEntity)

                val transcriptEntities = currentState.transcripts.map {
                    CallTranscriptEntryEntity(
                        sessionId = currentState.sessionId,
                        timestamp = it.timestamp,
                        speaker = it.speaker,
                        text = it.text,
                        detectedLanguage = it.detectedLanguage,
                        confidence = 1.0f
                    )
                }
                callSessionDao.insertTranscriptEntries(transcriptEntities)
                Log.i(TAG, "[CALL_SAVED] Successfully saved call session & ${transcriptEntities.size} transcript entries.")
            } catch (e: Exception) {
                Log.e(TAG, "[CALL_SAVE_ERROR] Failed to save call session: ${e.message}", e)
            }

            withContext(Dispatchers.Main) {
                _sessionState.value = _sessionState.value.copy(summary = summary)
            }
        }
    }

    /**
     * Generates a concise AI summary of the completed call.
     */
    private suspend fun generateCallSummary(state: AiCallSessionState, transcriptText: String): String {
        if (transcriptText.isBlank()) return "Call completed with no recorded conversation."

        val prompt = """
            You are an AI call summarizer. Provide a concise, structured summary of the following phone call:
            Caller: ${state.callerName} (${state.callerNumber})
            Detected Language: ${state.detectedLanguageName}
            
            TRANSCRIPT:
            $transcriptText
            
            Format your response strictly as:
            - Intent/Purpose: (Brief 1 sentence)
            - Key Details/Points: (2-3 bullets)
            - Outcome/Action Items: (1-2 sentences)
        """.trimIndent()

        return try {
            val res = geminiClient.generateResponse(
                messages = listOf("user" to prompt),
                systemInstruction = "You are a professional call analytics assistant. Summarize objectively and clearly.",
                temperature = 0.3f,
                maxTokens = 200
            )
            res.getOrNull()?.trim() ?: "Caller spoke with AI Assistant. Call concluded successfully."
        } catch (e: Exception) {
            "Call completed successfully with ${state.transcripts.size} turns recorded."
        }
    }

    private fun recordAssistantUtterance(text: String, localeTag: String) {
        val turn = LiveCallTranscriptTurn(
            speaker = "assistant",
            text = text,
            detectedLanguage = localeTag
        )
        _sessionState.value = _sessionState.value.copy(
            transcripts = _sessionState.value.transcripts + turn
        )
        conversationHistory.add("model" to text)
    }

    private fun speakAssistantResponse(text: String, localeTag: String, onComplete: (() -> Unit)? = null) {
        _sessionState.value = _sessionState.value.copy(isAiSpeaking = true)
        try {
            val loc = Locale.forLanguageTag(localeTag)
            activeTtsManager.speak(
                text = text,
                locale = loc,
                onCompletion = {
                    _sessionState.value = _sessionState.value.copy(isAiSpeaking = false)
                    onComplete?.invoke()
                }
            )
        } catch (e: Exception) {
            logCallError("TTS playback error for locale $localeTag: ${e.message}")
            _sessionState.value = _sessionState.value.copy(isAiSpeaking = false)
            onComplete?.invoke()
        }
    }

    private fun logCallError(message: String) {
        val timestamp = DATE_FORMAT.format(Date())
        val entry = "[$timestamp] $message"
        Log.w(TAG, "[CALL_ERROR_RECOVERED] $entry")
        errorLogList.add(entry)
        _sessionState.value = _sessionState.value.copy(
            errorLogs = errorLogList.toList()
        )
    }

    /**
     * Detects language and dialect from spoken utterance.
     */
    private fun detectLanguageAndDialect(text: String, currentLocale: String): Pair<String, String> {
        val detectedLocale = LocaleManager.detectUserDialect(text, currentLocale)
        val languageName = when {
            detectedLocale.startsWith("hi") -> "Hindi (हिन्दी)"
            detectedLocale.startsWith("bn") -> "Bengali (বাংলা)"
            detectedLocale.startsWith("ta") -> "Tamil (தமிழ்)"
            detectedLocale.startsWith("te") -> "Telugu (తెలుగు)"
            detectedLocale.startsWith("ml") -> "Malayalam (മലയാളം)"
            detectedLocale.startsWith("pa") -> "Punjabi (ਪੰਜਾਬੀ)"
            detectedLocale.startsWith("gu") -> "Gujarati (ગુજરાતી)"
            detectedLocale.startsWith("es") -> "Spanish (Español)"
            detectedLocale.startsWith("ja") -> "Japanese (日本語)"
            detectedLocale.startsWith("ru") -> "Russian (Русский)"
            detectedLocale.startsWith("fr") -> "French (Français)"
            detectedLocale.startsWith("de") -> "German (Deutsch)"
            detectedLocale.startsWith("ar") -> "Arabic (العربية)"
            detectedLocale.startsWith("id") -> "Indonesian (Bahasa Indonesia)"
            detectedLocale.startsWith("pt") -> "Portuguese (Português)"
            detectedLocale.startsWith("it") -> "Italian (Italiano)"
            detectedLocale.startsWith("zh") -> "Chinese (中文)"
            detectedLocale.startsWith("ko") -> "Korean (한국어)"
            detectedLocale.startsWith("tr") -> "Turkish (Türkçe)"
            detectedLocale.startsWith("ur") -> "Urdu (اردو)"
            else -> "English"
        }
        return Pair(detectedLocale, languageName)
    }

    private fun isDisconnectOrFarewellIntent(text: String): Boolean {
        val lower = text.lowercase().trim()
        val keywords = listOf(
            "bye", "goodbye", "bye bye", "talk to you later", "see you", "have a good day",
            "that's all", "that is all", "nothing else", "hang up", "cut call", "end call",
            "disconnect", "alvida", "chalta hu", "chalta hoon", "baad me baat karte hai",
            "khatam", "band kar do", "kuch nahi", "dhanyavaad bas itna hi", "adios", "au revoir",
            "sayonara", "tschüss", "chao"
        )
        return keywords.any { lower.contains(it) } || lower == "bye" || lower == "no thanks" || lower == "nothing"
    }

    private fun isAssistantFarewellIntent(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("goodbye") || lower.contains("have a wonderful day") ||
                lower.contains("अलविदा") || lower.contains("বিদায়") || lower.contains("hasta luego") ||
                lower.contains("auf wiederhören") || lower.contains("au revoir") || lower.contains("失礼いたします")
    }

    private fun getFallbackResponseForLocale(localeTag: String): String {
        return when {
            localeTag.startsWith("hi") -> "हाँ, मैं सुन रही हूँ। कृपया बताइए मैं आपकी क्या मदद कर सकती हूँ।"
            localeTag.startsWith("bn") -> "হ্যাঁ, আমি শুনছি। অনুগ্রহ করে বলুন আমি কীভাবে আপনাকে সাহায্য করতে পারি।"
            localeTag.startsWith("es") -> "Sí, le escucho con atención. ¿Cómo puedo ayudarle?"
            localeTag.startsWith("ja") -> "はい、お聞きしております。どのようなご用件でしょうか。"
            localeTag.startsWith("fr") -> "Oui, je vous écoute. Comment puis-je vous aider ?"
            localeTag.startsWith("de") -> "Ja, ich höre Ihnen zu. Wie kann ich Ihnen helfen?"
            else -> "Yes, I am listening. Please go ahead, how can I help you?"
        }
    }

    private fun buildInboundCallSystemPrompt(
        callerName: String,
        localeTag: String,
        languageName: String
    ): String {
        return """
            YOU ARE "ALYA" — AN ADVANCED AI INBOUND CALL AGENT:
            - You are actively speaking aloud on a live phone call answering for the device owner.
            - Caller Identity: $callerName.
            - Primary Language: $languageName ($localeTag).
            
            CORE CONVERSATION DIRECTIVES:
            1. Unscripted & Natural: Engage in fluid, human-like dialogue. Never sound like a robotic IVR menu or scripted operator. Adapt tone, warmth, and phrasing dynamically.
            2. Strictly Spoken-Friendly: Keep replies concise (1-2 spoken sentences), natural, and clear. Do NOT use markdown (*, #, `), bullet lists, or emojis — this text is read aloud by Text-To-Speech.
            3. Multilingual Fidelity: Speak fluently and natively in $languageName. If the caller switches language, immediately follow their language.
            4. Inbound Call Responsibilities:
               - Welcome the caller warmly.
               - Inquire about their purpose or take a message for the user.
               - Answer questions politely or provide requested help.
               - Conclude warmly when the caller is done and say a polite goodbye.
        """.trimIndent()
    }
}
