package com.example.voice.audio

import android.content.Context
import android.util.Log
import com.example.data.local.PreferencesManager
import com.example.voice.TextToSpeechManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.Random

/**
 * ConversationalFeedbackModule
 * 
 * Lightweight audio feedback module that triggers non-verbal conversational markers
 * (e.g. 'mm-hmm', 'okay', 'yeah', 'right') during long user pauses without interrupting their input.
 */
class ConversationalFeedbackModule(
    private val context: Context,
    private val ttsManager: TextToSpeechManager,
    private val preferencesManager: PreferencesManager
) {
    companion object {
        private const val TAG = "ConversationalFeedback"
        private const val PAUSE_DETECTION_DELAY_MS = 1600L
        private const val MIN_WORD_COUNT_FOR_FEEDBACK = 4
        private const val COOLDOWN_INTERVAL_MS = 8000L
    }

    private val moduleScope = CoroutineScope(Dispatchers.Main + Job())
    private var pauseCheckJob: Job? = null
    private val random = Random()

    private val _isMarkerPlaying = MutableStateFlow(false)
    val isMarkerPlaying: StateFlow<Boolean> = _isMarkerPlaying.asStateFlow()

    private var lastMarkerTimestamp = 0L
    private var hasMarkerTriggeredThisTurn = false
    private var lastObservedPartialText = ""

    private val conversationalMarkers = listOf(
        "mm-hmm",
        "okay",
        "yeah",
        "right",
        "uh-huh",
        "got it"
    )

    /**
     * Called whenever a partial speech transcript arrives from the recognizer.
     */
    fun onPartialSpeechUpdate(partialText: String, isListening: Boolean, isVoiceMode: Boolean) {
        if (!isVoiceMode || !isListening) {
            cancelPendingCheck()
            return
        }

        val trimmed = partialText.trim()
        if (trimmed == lastObservedPartialText) return
        lastObservedPartialText = trimmed

        // Cancel previous pause watchdog
        pauseCheckJob?.cancel()

        val wordCount = trimmed.split("\\s+".toRegex()).filter { it.isNotBlank() }.size
        val now = System.currentTimeMillis()

        if (wordCount >= MIN_WORD_COUNT_FOR_FEEDBACK && 
            !hasMarkerTriggeredThisTurn && 
            !_isMarkerPlaying.value && 
            !ttsManager.isSpeaking.value &&
            (now - lastMarkerTimestamp > COOLDOWN_INTERVAL_MS)) {
            
            pauseCheckJob = moduleScope.launch {
                delay(PAUSE_DETECTION_DELAY_MS)
                // If user is still listening, still in speech turn, and hasn't spoken more words
                if (isListening && !ttsManager.isSpeaking.value && !_isMarkerPlaying.value) {
                    triggerConversationalMarker()
                }
            }
        }
    }

    /**
     * Triggers a subtle non-verbal marker softly in the user's detected language.
     */
    fun triggerConversationalMarker() {
        val detected = ttsManager.languageDetector.detectLanguage(lastObservedPartialText)
        val markerList = when (detected.languageCode) {
            "es" -> listOf("ajá", "sí", "entiendo", "claro")
            "ru" -> listOf("угу", "да", "понятно", "слушаю")
            "fr" -> listOf("ouais", "d'accord", "je vois", "hum")
            "de" -> listOf("mhm", "ja", "verstehe", "okay")
            "hi" -> listOf("हाँ", "समझ गई", "ठीक है", "अच्छा")
            "ja" -> listOf("うん", "はい", "なるほど")
            else -> conversationalMarkers
        }
        val marker = markerList[random.nextInt(markerList.size)]
        Log.i(TAG, "Triggering non-verbal conversational marker in ${detected.languageDisplayName}: '$marker'")
        
        hasMarkerTriggeredThisTurn = true
        _isMarkerPlaying.value = true
        lastMarkerTimestamp = System.currentTimeMillis()

        val persona = preferencesManager.voicePersona.value
        val rate = preferencesManager.speechRate.value * 1.05f
        val pitch = preferencesManager.speechPitch.value

        ttsManager.speak(
            text = marker,
            speechRate = rate,
            speechPitch = pitch,
            locale = detected.locale,
            persona = persona
        )

        moduleScope.launch {
            delay(1100L)
            _isMarkerPlaying.value = false
        }
    }

    /**
     * Resets turn markers when the user finishes an utterance or begins a fresh turn.
     */
    fun resetTurn() {
        pauseCheckJob?.cancel()
        hasMarkerTriggeredThisTurn = false
        lastObservedPartialText = ""
    }

    fun cancelPendingCheck() {
        pauseCheckJob?.cancel()
    }
}
