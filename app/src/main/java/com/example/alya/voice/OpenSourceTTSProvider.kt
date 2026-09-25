package com.example.alya.voice

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID

/**
 * OpenSourceSystemTTSProvider
 *
 * 100% on-device speech synthesis using Android's native offline TTS engine.
 * Fully compatible with lightweight offline language packs and phonetics.
 */
class OpenSourceSystemTTSProvider(
    private val context: Context
) : AlyaTTSProvider, TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "OpenSourceSystemTTS"
    }

    override val providerId: String = "open_source_system_tts"
    override val isOffline: Boolean = true

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private val pendingUtterances = mutableMapOf<String, Pair<(() -> Unit)?, (() -> Unit)?>>()

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    utteranceId?.let { id ->
                        pendingUtterances[id]?.first?.invoke()
                    }
                }

                override fun onDone(utteranceId: String?) {
                    utteranceId?.let { id ->
                        pendingUtterances.remove(id)?.second?.invoke()
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    utteranceId?.let { id ->
                        pendingUtterances.remove(id)
                    }
                }
            })
            Log.i(TAG, "OpenSource System TTS initialized successfully.")
        } else {
            Log.e(TAG, "Failed to initialize OpenSource System TTS. Code: $status")
        }
    }

    override fun speak(
        text: String,
        language: String,
        pitch: Float,
        speechRate: Float,
        onStart: (() -> Unit)?,
        onDone: (() -> Unit)?,
        onError: ((String) -> Unit)?
    ) {
        if (!isInitialized || tts == null) {
            onError?.invoke("TTS not ready yet")
            return
        }

        try {
            val locale = Locale.forLanguageTag(language)
            tts?.language = locale
            tts?.setPitch(pitch)
            tts?.setSpeechRate(speechRate)

            val utteranceId = UUID.randomUUID().toString()
            pendingUtterances[utteranceId] = Pair(onStart, onDone)

            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            }

            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        } catch (e: Exception) {
            Log.e(TAG, "TTS speak error: ${e.message}", e)
            onError?.invoke(e.message ?: "TTS Error")
        }
    }

    override fun stop() {
        try {
            tts?.stop()
            pendingUtterances.clear()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping TTS: ${e.message}")
        }
    }

    override fun shutdown() {
        try {
            stop()
            tts?.shutdown()
            tts = null
        } catch (e: Exception) {
            Log.w(TAG, "Error shutting down TTS: ${e.message}")
        }
    }
}
