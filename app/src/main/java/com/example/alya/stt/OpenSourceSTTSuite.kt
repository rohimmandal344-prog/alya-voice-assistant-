package com.example.alya.stt

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.example.alya.voice.AlyaSTTProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * VoskOfflineSTTProvider
 *
 * 100% on-device speech-to-text powered by Vosk.
 */
class VoskOfflineSTTProvider(private val context: Context) : AlyaSTTProvider {
    override val providerId: String = "vosk_offline"
    override val isOffline: Boolean = true

    private val voskManager = VoskSTTManager(context)

    init {
        voskManager.initialize()
    }

    override fun startListening(
        language: String,
        onPartialResult: (String) -> Unit,
        onFinalResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        voskManager.onPartialCallback = onPartialResult
        voskManager.onResultCallback = onFinalResult
        voskManager.onErrorCallback = { e -> onError(e.message ?: "Vosk STT Error") }
        
        if (!voskManager.startListening()) {
            // Fallback to Native SpeechRecognizer if model asset not extracted yet
            val nativeFallback = NativeAndroidSTTProvider(context)
            nativeFallback.startListening(language, onPartialResult, onFinalResult, onError)
        }
    }

    override fun stopListening() {
        voskManager.stopListening()
    }

    override fun cancel() {
        voskManager.stopListening()
    }
}

/**
 * NativeAndroidSTTProvider
 *
 * Uses Android system offline SpeechRecognizer engine.
 */
class NativeAndroidSTTProvider(private val context: Context) : AlyaSTTProvider {
    override val providerId: String = "native_android_offline"
    override val isOffline: Boolean = true

    private var speechRecognizer: SpeechRecognizer? = null

    override fun startListening(
        language: String,
        onPartialResult: (String) -> Unit,
        onFinalResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        stopListening()
        CoroutineScope(Dispatchers.Main).launch {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                onError("Speech recognition unavailable on this device")
                return@launch
            }

            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onError(error: Int) {
                        onError("Recognition error code: $error")
                    }
                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull() ?: ""
                        if (text.isNotBlank()) onFinalResult(text)
                    }
                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull() ?: ""
                        if (text.isNotBlank()) onPartialResult(text)
                    }
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }

            try {
                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                onError(e.message ?: "Failed to start speech recognizer")
            }
        }
    }

    override fun stopListening() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.destroy()
                speechRecognizer = null
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    override fun cancel() {
        stopListening()
    }
}
