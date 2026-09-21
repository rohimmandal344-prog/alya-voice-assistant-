package com.example.voice.wakeword

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

/**
 * Fallback Hotword Detector using standard SpeechRecognizer.
 * Note: This relies on the system's ASR in a loop, which consumes significantly more battery
 * than a dedicated KWS (Keyword Spotting) model like Porcupine or openWakeWord.
 * It uses partial results to check for a fuzzy match of the trigger word.
 */
class SpeechRecognizerHotwordDetector(private val context: Context) : HotwordDetector, RecognitionListener {
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var targetKeyword = "alya"
    private var onDetectCallback: (() -> Unit)? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isDestroyed = false

    override fun startDetection(keyword: String, onDetect: () -> Unit) {
        if (isDestroyed) return
        this.targetKeyword = keyword.lowercase()
        this.onDetectCallback = onDetect
        
        handler.post {
            startListeningInternal()
        }
    }

    private fun startListeningInternal() {
        if (isDestroyed) return
        try {
            if (speechRecognizer == null) {
                if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                    Log.w("HotwordDetector", "Speech recognition not available on this device")
                    return
                }
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
                speechRecognizer?.setRecognitionListener(this)
            }
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                // Suggest minimizing audio buffer and processing overhead if the recognizer supports it
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1000L)
            }
            speechRecognizer?.startListening(intent)
            isListening = true
        } catch (e: Throwable) {
            Log.e("HotwordDetector", "Failed to start listening: ${e.message}")
            isListening = false
            restartListeningDelayed()
        }
    }

    override fun stopDetection() {
        isListening = false
        handler.post {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
        }
    }

    override fun destroy() {
        isDestroyed = true
        isListening = false
        handler.post {
            speechRecognizer?.destroy()
            speechRecognizer = null
        }
    }

    private fun restartListeningDelayed() {
        if (!isListening || isDestroyed) return
        handler.postDelayed({
            if (isListening && !isDestroyed) {
                startListeningInternal()
            }
        }, 300)
    }

    private fun checkFuzzyMatch(text: String): Boolean {
        val lower = text.lowercase()
        val fuzzyTargets = listOf(
            targetKeyword,
            targetKeyword.replace("y", "i"), 
            targetKeyword.replace("i", "y"),
            targetKeyword + "a" // alia -> aliaa
        )
        return fuzzyTargets.any { lower.contains(it) }
    }

    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {}

    override fun onError(error: Int) {
        if (isListening && !isDestroyed) {
            restartListeningDelayed()
        }
    }

    override fun onResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        var matched = false
        matches?.forEach {
            if (checkFuzzyMatch(it)) {
                matched = true
            }
        }
        if (matched) {
            Log.i("HotwordDetector", "Hotword detected in final result")
            onDetectCallback?.invoke()
            isListening = false // Stop loop, we detected it
        } else {
            if (isListening && !isDestroyed) {
                startListeningInternal()
            }
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        matches?.forEach {
            if (checkFuzzyMatch(it)) {
                Log.i("HotwordDetector", "Hotword detected in partial result: $it")
                onDetectCallback?.invoke()
                stopDetection()
            }
        }
    }

    override fun onEvent(eventType: Int, params: Bundle?) {}
}
