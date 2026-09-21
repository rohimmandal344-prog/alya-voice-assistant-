package com.example.alya.stt

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File
import java.io.IOException

/**
 * High-performance Offline Speech-To-Text (STT) Manager powered by Vosk.
 * Runs 100% on-device with zero internet latency, zero packet drops, and ultra-low battery drain.
 */
class VoskSTTManager(private val context: Context) : RecognitionListener {

    companion object {
        private const val TAG = "VoskSTTManager"
        private const val SAMPLE_RATE = 16000.0f
    }

    private var model: Model? = null
    private var speechService: SpeechService? = null
    private var recognizer: Recognizer? = null
    
    var isModelReady: Boolean = false
        private set

    var onResultCallback: ((String) -> Unit)? = null
    var onPartialCallback: ((String) -> Unit)? = null
    var onErrorCallback: ((Exception) -> Unit)? = null
    var onTimeoutCallback: (() -> Unit)? = null

    /**
     * Initializes the Vosk model from internal files or unpacked assets.
     */
    fun initialize(onReady: ((Boolean) -> Unit)? = null) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val modelDir = OfflineAssetManager.prepareModel(context)
                if (modelDir != null && modelDir.exists()) {
                    try {
                        model = Model(modelDir.absolutePath)
                        isModelReady = true
                        Log.i(TAG, "Vosk offline model initialized successfully from: ${modelDir.absolutePath}")
                    } catch (e: Exception) {
                        Log.w(TAG, "Native Vosk model load fallback (directory descriptor present): ${e.message}")
                        isModelReady = false
                    }
                } else {
                    Log.w(TAG, "Model directory not found. Vosk offline ready state: false")
                    isModelReady = false
                }
                withContext(Dispatchers.Main) {
                    onReady?.invoke(isModelReady)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Vosk initialization failed: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    isModelReady = false
                    onReady?.invoke(false)
                }
            }
        }
    }

    /**
     * Starts continuous offline speech recognition.
     */
    fun startListening(): Boolean {
        if (!isModelReady || model == null) {
            Log.w(TAG, "Cannot start listening: Vosk model not loaded.")
            return false
        }

        try {
            stopListening()
            recognizer = Recognizer(model, SAMPLE_RATE)
            speechService = SpeechService(recognizer, SAMPLE_RATE)
            speechService?.startListening(this)
            Log.i(TAG, "Vosk SpeechService listening started at 16kHz.")
            return true
        } catch (e: IOException) {
            Log.e(TAG, "Failed to start Vosk SpeechService: ${e.message}", e)
            onErrorCallback?.invoke(e)
            return false
        }
    }

    /**
     * Stops recognition without destroying loaded model in memory.
     */
    fun stopListening() {
        try {
            speechService?.stop()
            speechService?.shutdown()
            speechService = null
            recognizer?.close()
            recognizer = null
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping Vosk SpeechService: ${e.message}")
        }
    }

    /**
     * Complete lifecycle cleanup.
     */
    fun destroy() {
        stopListening()
        try {
            model?.close()
            model = null
            isModelReady = false
        } catch (e: Exception) {
            Log.w(TAG, "Error closing Vosk model: ${e.message}")
        }
    }

    override fun onPartialResult(hypothesis: String?) {
        hypothesis ?: return
        try {
            val json = JSONObject(hypothesis)
            val partialText = json.optString("partial", "").trim()
            if (partialText.isNotEmpty()) {
                onPartialCallback?.invoke(partialText)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse partial Vosk JSON: ${e.message}")
        }
    }

    override fun onResult(hypothesis: String?) {
        hypothesis ?: return
        try {
            val json = JSONObject(hypothesis)
            val text = json.optString("text", "").trim()
            if (text.isNotEmpty()) {
                onResultCallback?.invoke(text)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse Vosk result JSON: ${e.message}")
        }
    }

    override fun onFinalResult(hypothesis: String?) {
        onResult(hypothesis)
    }

    override fun onError(exception: java.lang.Exception?) {
        Log.e(TAG, "Vosk Recognition error: ${exception?.message}", exception)
        exception?.let { onErrorCallback?.invoke(it) }
    }

    override fun onTimeout() {
        Log.d(TAG, "Vosk recognition timed out.")
        onTimeoutCallback?.invoke()
    }
}
