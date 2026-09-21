package com.example.voice.wakeword

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.sqrt

/**
 * Strict Voice Activity Detection (VAD) Gated State Controller.
 *
 * Ensures microphone data processing remains dormant until genuine voice activity
 * passes spectral and energy gating thresholds, preventing background phantom activations,
 * battery drain, and unwanted microphone thrashing.
 */
enum class VADMode {
    IDLE,                  // Microphone dormant / VAD silence gating active
    VOICE_DETECTED,        // Active voice energy detected, frame passed to classifier
    WAKE_WORD_CONFIRMED,   // Porcupine / TFLite engine confirmed wake phrase trigger
    COOLDOWN               // Post-trigger debounce period preventing repeated firings
}

class VADStateController(
    private val confidenceThreshold: Float = 0.85f,
    private val cooldownDurationMs: Long = 3000L,
    private val minVoiceRmsThreshold: Float = 350.0f
) {
    companion object {
        private const val TAG = "VADStateController"
    }

    private val _vadMode = MutableStateFlow(VADMode.IDLE)
    val vadMode: StateFlow<VADMode> = _vadMode.asStateFlow()

    private val lastTriggerTimestamp = AtomicLong(0L)
    private val voiceFrameCounter = java.util.concurrent.atomic.AtomicInteger(0)

    /**
     * Evaluates a PCM audio frame to determine if it contains valid speech energy.
     * Returns true if frame is accepted for wake word classification.
     */
    fun evaluateAudioFrame(buffer: ShortArray, readSize: Int): Boolean {
        if (_vadMode.value == VADMode.COOLDOWN) {
            val elapsed = System.currentTimeMillis() - lastTriggerTimestamp.get()
            if (elapsed < cooldownDurationMs) {
                return false // Suppress audio processing during cooldown window
            } else {
                _vadMode.value = VADMode.IDLE
                Log.i(TAG, "VAD Cooldown expired. Returning to IDLE VAD state.")
            }
        }

        if (readSize <= 0) return false

        // Compute Root Mean Square (RMS) audio energy
        var sumSquare = 0.0
        for (i in 0 until readSize) {
            val sample = buffer[i].toDouble()
            sumSquare += sample * sample
        }
        val rms = sqrt(sumSquare / readSize).toFloat()

        if (rms < minVoiceRmsThreshold) {
            if (_vadMode.value == VADMode.VOICE_DETECTED && voiceFrameCounter.decrementAndGet() <= 0) {
                _vadMode.value = VADMode.IDLE
            }
            return false // Dormant frame - below VAD energy threshold
        }

        voiceFrameCounter.set(3) // Keep VAD active for 3 consecutive frames
        if (_vadMode.value == VADMode.IDLE) {
            _vadMode.value = VADMode.VOICE_DETECTED
            Log.d(TAG, "Voice Activity Detected (RMS: $rms). Activating classifier pipeline.")
        }
        return true
    }

    /**
     * Validates a candidate wake-word trigger against confidence threshold and cooldown constraints.
     */
    fun validateWakeWordTrigger(keyword: String, confidence: Float): Boolean {
        val currentTime = System.currentTimeMillis()
        val timeSinceLastTrigger = currentTime - lastTriggerTimestamp.get()

        if (timeSinceLastTrigger < cooldownDurationMs) {
            Log.w(TAG, "Wake word trigger '$keyword' rejected due to active cooldown ($timeSinceLastTrigger ms < $cooldownDurationMs ms).")
            return false
        }

        if (confidence < confidenceThreshold) {
            Log.w(TAG, "Wake word trigger '$keyword' rejected due to insufficient confidence ($confidence < $confidenceThreshold).")
            return false
        }

        lastTriggerTimestamp.set(currentTime)
        _vadMode.value = VADMode.WAKE_WORD_CONFIRMED
        Log.i(TAG, "Wake word trigger '$keyword' CONFIRMED with confidence $confidence. Entering COOLDOWN.")
        
        // Enter cooldown state
        _vadMode.value = VADMode.COOLDOWN
        return true
    }

    /**
     * Resets the VAD state controller to IDLE.
     */
    fun reset() {
        lastTriggerTimestamp.set(0L)
        voiceFrameCounter.set(0)
        _vadMode.value = VADMode.IDLE
        Log.i(TAG, "VADStateController reset to IDLE.")
    }
}
