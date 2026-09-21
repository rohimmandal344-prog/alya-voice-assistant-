package com.example.service

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

enum class MicrophoneOwner {
    WAKE_DETECTOR,
    ASR_ENGINE,
    NONE
}

object SessionArbitrator {
    private const val TAG = "SessionArbitrator"

    private val _microphoneOwner = MutableStateFlow<MicrophoneOwner>(MicrophoneOwner.WAKE_DETECTOR)
    val microphoneOwner: StateFlow<MicrophoneOwner> = _microphoneOwner.asStateFlow()

    private var onStopWakeDetector: (() -> Unit)? = null
    private var onStartAsrEngine: (() -> Unit)? = null
    private var onRestartWakeDetector: (() -> Unit)? = null

    private val isTransitioning = AtomicBoolean(false)

    @Synchronized
    fun registerCallbacks(
        stopWake: () -> Unit,
        startAsr: () -> Unit,
        restartWake: () -> Unit
    ) {
        onStopWakeDetector = stopWake
        onStartAsrEngine = startAsr
        onRestartWakeDetector = restartWake
        Log.i(TAG, "SessionArbitrator callbacks successfully registered.")
    }

    @Synchronized
    fun onWakeWordDetected(keyword: String) {
        if (isTransitioning.getAndSet(true)) {
            Log.w(TAG, "Ignored wake word '$keyword' due to ongoing session transition check.")
            return
        }

        try {
            val currentOwner = _microphoneOwner.value
            Log.i(TAG, "Wake word detected: '$keyword'. Current owner: $currentOwner. Arbitrating transition.")

            if (currentOwner == MicrophoneOwner.ASR_ENGINE) {
                Log.w(TAG, "ASR engine already active — skipping.")
                return
            }

            // 1. Request SPEECH_RECOGNITION session from manager. 
            // This will automatically deactivate WAKE_WORD owner with deterministic hardware release pauses.
            val sessionManager = com.example.audio.AudioSessionManager
            val granted = sessionManager.requestSession(com.example.audio.AudioSessionType.SPEECH_RECOGNITION)
            
            if (granted) {
                _microphoneOwner.value = MicrophoneOwner.ASR_ENGINE
                onStartAsrEngine?.invoke()
            } else {
                Log.e(TAG, "Failed to acquire ASR session from AudioSessionManager.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onWakeWordDetected transition: ${e.message}", e)
        } finally {
            isTransitioning.set(false)
        }
    }

    @Synchronized
    fun onAsrEngineReleased() {
        if (isTransitioning.getAndSet(true)) {
            Log.w(TAG, "Ignored ASR release due to ongoing session transition check.")
            return
        }

        try {
            val currentOwner = _microphoneOwner.value
            Log.i(TAG, "ASR engine released. Arbitrating return to WAKE_DETECTOR.")

            // 1. Use AudioSessionManager to release the ASR session
            com.example.audio.AudioSessionManager.releaseSession(com.example.audio.AudioSessionType.SPEECH_RECOGNITION)
            
            _microphoneOwner.value = MicrophoneOwner.NONE
            
            // 2. Request WAKE_WORD session to resume background listening
            val granted = com.example.audio.AudioSessionManager.requestSession(com.example.audio.AudioSessionType.WAKE_WORD)
            if (granted) {
                _microphoneOwner.value = MicrophoneOwner.WAKE_DETECTOR
                onRestartWakeDetector?.invoke()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onAsrEngineReleased transition: ${e.message}", e)
        } finally {
            isTransitioning.set(false)
        }
    }
}
