package com.example.alya.stt

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
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
import java.io.IOException

/**
 * Ultra-low power offline Wake-Word detector powered by Vosk small grammar.
 * Listens specifically for "Alya", "Alia", and "Seno" wake words.
 */
class VoskWakeWordManager(private val context: Context) : RecognitionListener {

    companion object {
        private const val TAG = "VoskWakeWordManager"
        private const val SAMPLE_RATE = 16000.0f
        private const val WAKE_GRAMMAR = "[\"hey alya\", \"alya\", \"hey alia\", \"alia\", \"seno\", \"hey seno\", \"[unk]\"]"
    }

    private var model: Model? = null
    private var speechService: SpeechService? = null
    private var recognizer: Recognizer? = null
    private var toneGenerator: ToneGenerator? = null

    var isReady: Boolean = false
        private set

    var onWakeWordDetected: ((String) -> Unit)? = null
    var onError: ((Exception) -> Unit)? = null

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70)
        } catch (e: Exception) {
            Log.w(TAG, "ToneGenerator init failed: ${e.message}")
        }
    }

    /**
     * Initializes the Vosk wake-word model.
     */
    fun initialize(onReady: ((Boolean) -> Unit)? = null) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val modelDir = OfflineAssetManager.prepareModel(context)
                if (modelDir != null && modelDir.exists()) {
                    try {
                        model = Model(modelDir.absolutePath)
                        isReady = true
                        Log.i(TAG, "VoskWakeWordManager loaded offline model successfully.")
                    } catch (e: Exception) {
                        Log.w(TAG, "Vosk model load fallback: ${e.message}")
                        isReady = false
                    }
                } else {
                    isReady = false
                }
                withContext(Dispatchers.Main) {
                    onReady?.invoke(isReady)
                }
            } catch (e: Exception) {
                Log.e(TAG, "VoskWakeWordManager initialization error: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    isReady = false
                    onReady?.invoke(false)
                }
            }
        }
    }

    /**
     * Starts continuous background listening for wake words.
     */
    fun startListeningForWakeWord(): Boolean {
        if (!isReady || model == null) {
            Log.w(TAG, "Cannot start wake-word listening: model is not ready.")
            return false
        }

        try {
            stopListening()
            recognizer = Recognizer(model, SAMPLE_RATE, WAKE_GRAMMAR)
            speechService = SpeechService(recognizer, SAMPLE_RATE)
            speechService?.startListening(this)
            Log.i(TAG, "VoskWakeWordManager listening for wake words ('Alya', 'Alia', 'Seno').")
            return true
        } catch (e: IOException) {
            Log.e(TAG, "Failed to start wake-word listening: ${e.message}", e)
            onError?.invoke(e)
            return false
        }
    }

    /**
     * Stops wake-word listening.
     */
    fun stopListening() {
        try {
            speechService?.stop()
            speechService?.shutdown()
            speechService = null
            recognizer?.close()
            recognizer = null
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping wake-word speech service: ${e.message}")
        }
    }

    /**
     * Complete lifecycle cleanup.
     */
    fun destroy() {
        stopListening()
        try {
            toneGenerator?.release()
            toneGenerator = null
            model?.close()
            model = null
            isReady = false
        } catch (e: Exception) {
            Log.w(TAG, "Error cleaning up VoskWakeWordManager: ${e.message}")
        }
    }

    private fun handleWakeWordDetection(rawText: String) {
        val lower = rawText.lowercase().trim()
        val detected = when {
            lower.contains("seno") -> "seno"
            lower.contains("alia") -> "alia"
            lower.contains("alya") -> "alya"
            else -> null
        }

        if (detected != null) {
            Log.i(TAG, "Wake word triggered: $detected (from: $rawText)")
            playFeedback()
            onWakeWordDetected?.invoke(detected)
        }
    }

    private fun playFeedback() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
        } catch (e: Exception) {
            Log.w(TAG, "Feedback beep failed: ${e.message}")
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                vibrator?.vibrate(80)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Haptic pulse error: ${e.message}")
        }
    }

    override fun onPartialResult(hypothesis: String?) {
        hypothesis ?: return
        try {
            val json = JSONObject(hypothesis)
            val partial = json.optString("partial", "")
            if (partial.isNotEmpty()) {
                handleWakeWordDetection(partial)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing partial wake-word json: ${e.message}")
        }
    }

    override fun onResult(hypothesis: String?) {
        hypothesis ?: return
        try {
            val json = JSONObject(hypothesis)
            val text = json.optString("text", "")
            if (text.isNotEmpty()) {
                handleWakeWordDetection(text)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing wake-word result json: ${e.message}")
        }
    }

    override fun onFinalResult(hypothesis: String?) {
        onResult(hypothesis)
    }

    override fun onError(exception: java.lang.Exception?) {
        Log.e(TAG, "Wake-word detection error: ${exception?.message}", exception)
        exception?.let { onError?.invoke(it) }
    }

    override fun onTimeout() {
        Log.d(TAG, "Wake-word listener timeout.")
    }
}
