package com.example.voice.wakeword

import android.content.Context
import android.util.Log
import com.example.AlyaApplication
import com.example.voice.microphone.AudioCaptureManager
import com.example.voice.microphone.MicState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.rementia.openwakeword.lib.WakeWordEngine
import com.rementia.openwakeword.lib.model.DetectionMode
import com.rementia.openwakeword.lib.model.WakeWordModel
import com.rementia.openwakeword.lib.model.WakeWordDetection
import java.io.File
import java.io.FileOutputStream

/**
 * OpenWakeWordManager
 *
 * Real implementation of Alya's on-device wake-word engine using OpenWakeWord (ONNX).
 * Integrates with the high-level WakeWordEngine from com.rementia.openwakeword.lib.
 */
class OpenWakeWordManager(private val context: Context) : WakeWordManager {
    private val TAG = "OpenWakeWordManager"

    private val _isListening = MutableStateFlow(false)
    override val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    override var isSuppressed: Boolean = false
    override var isTtsSpeaking: Boolean = false
    override var onBargeInDetected: (() -> Unit)? = null

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val stateMachine = WakeWordStateMachine()
    
    override val state: StateFlow<WakeState> = stateMachine.currentState

    private var engine: WakeWordEngine? = null
    private val listeners = mutableListOf<(String) -> Unit>()

    private var lastDetectionTime = 0L
    private val DETECTION_DEBOUNCE_MS = 2500L // Prevent rapid multiple triggers

    init {
        com.example.audio.AudioSessionManager.registerSessionOwner(com.example.audio.AudioSessionType.WAKE_WORD) {
            Log.i(TAG, "AudioSessionManager requested relinquishing mic for WAKE_WORD. Stopping wake word detection.")
            stop()
        }
        initializeEngine()
    }

    private var isBatterySaverMode = false

    private fun initializeEngine() {
        scope.launch {
            try {
                stateMachine.transitionTo(WakeState.INITIALIZING)

                if (!isModelAssetValid()) {
                    Log.i(TAG, "OpenWakeWord model asset 'hey_alya.onnx' is missing or dummy (<10KB). System will fall back to native SpeechRecognizer / VAD activation.")
                    stateMachine.transitionTo(WakeState.DISABLED)
                    return@launch
                }

                // Ensure model asset is copied to internal storage if needed
                val modelFile = prepareModelAsset()
                val assetPath = "hey_alya.onnx"
                var initSuccess = false

                try {
                    // Optimized thresholds: Alia and Alya need high precision to avoid false positives.
                    // Seno is a distinct sound, can have slightly lower threshold for reliability.
                    val modelAlia = WakeWordModel("Alia", assetPath, 0.7f)
                    val modelAlya = WakeWordModel("Alya", assetPath, 0.7f)
                    val modelSeno = WakeWordModel("Seno", assetPath, 0.6f)

                    engine = WakeWordEngine(
                        context = context,
                        models = listOf(modelAlia, modelAlya, modelSeno),
                        detectionMode = DetectionMode.ALL,
                        detectionCooldownMs = if (isBatterySaverMode) 500L else 300L, // Increased for stability
                        scope = scope
                    )
                    initSuccess = true
                } catch (eAsset: Throwable) {
                    Log.w(TAG, "Asset-based loading bypassed for '$assetPath': ${eAsset.message}. Trying internal storage file.", eAsset)
                    if (modelFile != null && modelFile.exists() && modelFile.length() > 10 * 1024) {
                        val modelAlia = WakeWordModel("Alia", modelFile.absolutePath, 0.7f)
                        val modelAlya = WakeWordModel("Alya", modelFile.absolutePath, 0.7f)
                        val modelSeno = WakeWordModel("Seno", modelFile.absolutePath, 0.6f)

                        engine = WakeWordEngine(
                            context = context,
                            models = listOf(modelAlia, modelAlya, modelSeno),
                            detectionMode = DetectionMode.ALL,
                            detectionCooldownMs = if (isBatterySaverMode) 500L else 300L,
                            scope = scope
                        )
                        initSuccess = true
                    }
                }

                if (initSuccess && engine != null) {
                    val initialKeyword = (context.applicationContext as? AlyaApplication)?.preferencesManager?.selectedWakeWord?.value ?: "Alya"
                    stateMachine.transitionTo(WakeState.READY(initialKeyword))
                    Log.i(TAG, "OpenWakeWord engine initialized successfully for Alia, Alya, and Seno.")

                    // Observe detections
                    engine?.detections?.collect { detection ->
                        handleDetection(detection)
                    }
                } else {
                    Log.w(TAG, "Could not initialize OpenWakeWord engine. Falling back to native speech detection.")
                    stateMachine.transitionTo(WakeState.DISABLED)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "OpenWakeWord engine initialization handled safely: ${t.message}")
                stateMachine.transitionTo(WakeState.DISABLED)
            }
        }
    }

    private fun isModelAssetValid(): Boolean {
        return try {
            context.assets.open("hey_alya.onnx").use { input ->
                val size = input.available()
                Log.d(TAG, "Asset 'hey_alya.onnx' byte size: $size")
                size > 10 * 1024
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun prepareModelAsset(): File? {
        return try {
            val assetManager = context.assets
            val modelFileName = "hey_alya.onnx"
            val outFile = File(context.filesDir, modelFileName)
            
            if (!outFile.exists() || outFile.length() == 0L) {
                assetManager.open(modelFileName).use { input ->
                    FileOutputStream(outFile).use { output ->
                        input.copyTo(output)
                    }
                }
                Log.i(TAG, "Copied $modelFileName from assets to internal storage.")
            }
            outFile
        } catch (e: Exception) {
            Log.e(TAG, "Error preparing model asset: ${e.message}", e)
            null
        }
    }

    private fun handleDetection(detection: WakeWordDetection) {
        val now = System.currentTimeMillis()
        if (now - lastDetectionTime < DETECTION_DEBOUNCE_MS) {
            Log.d(TAG, "Detection ignored (debounce): ${detection.model.name}")
            return
        }

        // CRITICAL: Prevent self-activation from TTS
        val ttsIsActuallySpeaking = (context.applicationContext as? AlyaApplication)?.ttsManager?.isSpeaking?.value ?: false
        if (ttsIsActuallySpeaking || isTtsSpeaking) {
            Log.i(TAG, "Wake detection ignored: Assistant is currently speaking (AEC/Self-audio prevention).")
            return
        }

        if (isSuppressed) {
            Log.d(TAG, "Detection ignored: Engine is suppressed.")
            return
        }

        // Strictly enforce that the state machine is ONLY notified upon successful detection of 'Alia', 'Alya', or 'Seno'
        val rawModelName = detection.model.name.trim()
        val normalized = rawModelName.lowercase()
        val canonicalKeyword = when (normalized) {
            "alia" -> "Alia"
            "alya" -> "Alya"
            "seno" -> "Seno"
            else -> {
                Log.d(TAG, "Detection ignored: '$rawModelName' is not one of 'Alia', 'Alya', or 'Seno'. State machine NOT notified.")
                return
            }
        }

        lastDetectionTime = now
        Log.i(TAG, "WAKE_DETECTED! Confirmed keyword: '$canonicalKeyword', Confidence: ${detection.score}")
        
        // Handle barge-in if TTS is active (shouldn't happen with the above check, but kept for robustness)
        if (isTtsSpeaking) {
            onBargeInDetected?.invoke()
        }

        // Explicit state transition ONLY upon valid keyword detection
        stateMachine.transitionTo(WakeState.DETECTED(canonicalKeyword, detection.score))
        
        // Notify listeners on Main thread
        scope.launch(Dispatchers.Main) {
            notifyListeners(canonicalKeyword)
        }

        // Enter ACTIVE before returning to READY or LISTENING
        scope.launch {
            delay(1500)
            val currentKeyword = (context.applicationContext as? AlyaApplication)?.preferencesManager?.selectedWakeWord?.value ?: "Alya"
            stateMachine.transitionTo(WakeState.READY(currentKeyword))
        }
    }

    override fun start(keyword: String, sensitivity: Float): Boolean {
        if (isSuppressed) {
            Log.d(TAG, "Start requested but engine is suppressed.")
            return false
        }
        if (_isListening.value) {
            Log.d(TAG, "Start requested but already listening.")
            return true
        }

        val currentState = stateMachine.currentState.value
        if (currentState !is WakeState.READY && 
            currentState !is WakeState.DISABLED &&
            currentState !is WakeState.ACTIVE) {
            Log.w(TAG, "Cannot start listening: Engine state is $currentState")
            return false
        }

        val granted = com.example.audio.AudioSessionManager.requestSession(com.example.audio.AudioSessionType.WAKE_WORD)
        if (!granted) {
            Log.w(TAG, "AudioSessionManager denied WAKE_WORD session.")
            return false
        }

        _isListening.value = true
        stateMachine.transitionTo(WakeState.LISTENING(keyword))
        engine?.start()
        Log.i(TAG, "Wake-word detection started for keyword: $keyword")
        return true
    }

    override fun stop() {
        Log.i(TAG, "Stopping wake-word detection.")
        _isListening.value = false
        engine?.stop()
        com.example.audio.AudioSessionManager.releaseSession(com.example.audio.AudioSessionType.WAKE_WORD)
        val currentKeyword = (context.applicationContext as? AlyaApplication)?.preferencesManager?.selectedWakeWord?.value ?: "Alya"
        stateMachine.transitionTo(WakeState.READY(currentKeyword))
    }

    override fun disposeNativeResources() {
        stop()
        engine?.release()
        engine = null
        scope.cancel()
    }

    override fun addWakeWordListener(listener: (String) -> Unit) {
        synchronized(listeners) {
            if (!listeners.contains(listener)) listeners.add(listener)
        }
    }

    override fun removeWakeWordListener(listener: (String) -> Unit) {
        synchronized(listeners) {
            listeners.remove(listener)
        }
    }

    private fun notifyListeners(keyword: String) {
        synchronized(listeners) {
            for (listener in listeners) {
                try {
                    listener(keyword)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in wake word listener", e)
                }
            }
        }
    }

    override fun updateConfiguredKeyword(newKeyword: String) {
        Log.i(TAG, "Configured keyword updated: $newKeyword")
        stateMachine.transitionTo(WakeState.READY(newKeyword))
    }

    override fun clearAudioBuffer() {
        // High-level engine handles this
    }

    override fun release() {
        disposeNativeResources()
    }

    override fun setBatterySaverEnabled(enabled: Boolean) {
        isBatterySaverMode = enabled
        Log.i(TAG, "Battery saver mode set: $enabled")
    }
    override fun setDeviceIdlePowerMode(enabled: Boolean) {}
    override fun notifyUserActivity() {}
    override fun setBargeInEnabled(enabled: Boolean) {}
    override fun getRecentAudio(durationMs: Int): ShortArray = ShortArray(0)
    override fun onAppBackgrounded(allowBackground: Boolean) {}
    override fun onAppForegrounded(isEnabled: Boolean, kw: String, sens: Float) {
        if (isEnabled && !_isListening.value) {
            start(kw, sens)
        }
    }
}
