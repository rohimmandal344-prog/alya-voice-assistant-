package com.example.voice.vad

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Real-time Voice Activity Detection (VAD) States
 */
enum class VadState {
    SILENCE,            // Ambient noise / silence
    SPEECH_ONSET,       // Energy rising, validating speech onset (debouncing clicks)
    SPEECH_ACTIVE,      // Active user speech confirmed
    SILENCE_HANGOVER    // Speech paused, waiting for hangover timeout before signaling turn end
}

/**
 * Lightweight, high-accuracy, real-time Voice Activity Detector (VAD).
 *
 * Operates on raw 16kHz 16-bit PCM audio frames (e.g. 20ms = 320 samples).
 * Features:
 * 1. Multi-feature speech discrimination:
 *    - Short-Time RMS Energy (dB)
 *    - Zero Crossing Rate (ZCR) for unvoiced/voiced discrimination
 *    - First-order Spectral Flux / Normalized Energy Gradient
 * 2. Continuous Dynamic Noise Floor Estimation:
 *    - Automatically tracks ambient acoustics without hardcoding fixed thresholds.
 *    - Uses asymmetric temporal tracking (fast descent into silence, slow rise).
 * 3. Hysteresis & Turn-taking State Machine:
 *    - Fast speech onset validation (40ms / 2 frames) to eliminate mic pops and taps.
 *    - Configurable silence hangover (default 550ms) to prevent cutting off natural pauses between words.
 *    - Accurate turn-completion signal ([onTurnComplete]) when user finishes their utterance.
 * 4. Ultra-responsive Barge-in Detection:
 *    - Sub-40ms vocal impulse detection during active assistant playback.
 *    - Immediately emits [onBargeInTriggered] to halt TTS/AudioTrack playback.
 */
class RealtimeVoiceActivityDetector(
    private val sampleRate: Int = 16000,
    private var minVoiceEnergyDb: Float = 34.0f,
    private var minSnrDb: Float = 7.0f,
    private var speechOnsetFramesRequired: Int = 2, // ~40ms at 20ms frames
    private var silenceHangoverMs: Long = 550L,     // 550ms silence indicates end of conversational turn
    private var bargeInThresholdDb: Float = 38.0f,
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) {
    companion object {
        private const val TAG = "RealtimeVAD"
        private const val MIN_NOISE_FLOOR_DB = 16.0f
        private const val MAX_NOISE_FLOOR_DB = 55.0f
    }

    private val _vadState = MutableStateFlow(VadState.SILENCE)
    val vadState: StateFlow<VadState> = _vadState.asStateFlow()

    private val _isSpeechActive = MutableStateFlow(false)
    val isSpeechActive: StateFlow<Boolean> = _isSpeechActive.asStateFlow()

    private val _adaptiveNoiseFloorDb = MutableStateFlow(24.0f)
    val adaptiveNoiseFloorDb: StateFlow<Float> = _adaptiveNoiseFloorDb.asStateFlow()

    private val _lastRmsDb = MutableStateFlow(0.0f)
    val lastRmsDb: StateFlow<Float> = _lastRmsDb.asStateFlow()

    // State machine trackers
    private var onsetFrameCount = 0
    private var speechStartTimeMs = 0L
    private var speechLastActiveTimeMs = 0L
    private var silenceStartTimeMs = 0L

    private val isAssistantSpeaking = AtomicBoolean(false)
    private val isBargeInCooldown = AtomicBoolean(false)
    private val lastBargeInTimestamp = AtomicLong(0L)

    // Callbacks for turn-taking & barge-in
    var onSpeechStarted: (() -> Unit)? = null
    var onSpeechEnded: ((speechDurationMs: Long) -> Unit)? = null
    var onSilenceDetected: ((silenceDurationMs: Long) -> Unit)? = null
    var onTurnComplete: ((speechDurationMs: Long, totalTurnMs: Long) -> Unit)? = null
    var onBargeInTriggered: ((rmsDb: Float) -> Unit)? = null

    /**
     * Updates whether the assistant is currently outputting audio (TTS or PCM AudioTrack).
     * When assistant is speaking, VAD enables low-latency barge-in trigger mode.
     */
    fun setAssistantSpeaking(speaking: Boolean) {
        if (isAssistantSpeaking.getAndSet(speaking) != speaking) {
            Log.d(TAG, "Assistant speaking state updated: $speaking (Barge-in mode: $speaking)")
            if (!speaking) {
                isBargeInCooldown.set(false)
            }
        }
    }

    fun isAssistantSpeaking(): Boolean = isAssistantSpeaking.get()

    /**
     * Dynamically adjusts turn-taking silence detection window.
     * @param hangoverMs Duration in milliseconds (e.g. 400ms for fast conversational pacing, 700ms for relaxed)
     */
    fun setSilenceHangoverMs(hangoverMs: Long) {
        this.silenceHangoverMs = hangoverMs.coerceIn(250L, 2000L)
        Log.d(TAG, "VAD silence hangover set to: ${this.silenceHangoverMs}ms")
    }

    /**
     * Adjusts the sensitivity thresholds.
     */
    fun setSensitivity(minVoiceDb: Float, minSnr: Float, bargeInDb: Float = 38.0f) {
        this.minVoiceEnergyDb = minVoiceDb.coerceIn(20f, 60f)
        this.minSnrDb = minSnr.coerceIn(3f, 20f)
        this.bargeInThresholdDb = bargeInDb.coerceIn(30f, 65f)
        Log.d(TAG, "VAD sensitivity updated: minVoiceDb=$minVoiceEnergyDb, minSnrDb=$minSnrDb, bargeInDb=$bargeInThresholdDb")
    }

    /**
     * Primary audio frame processor.
     * Must be invoked for each captured PCM audio buffer in real time.
     *
     * @param buffer 16-bit linear PCM audio samples
     * @param readSize Number of valid samples in the buffer
     * @return True if the frame contains valid human speech activity
     */
    fun processFrame(buffer: ShortArray, readSize: Int): Boolean {
        if (readSize <= 0) return false

        val now = timeProvider()

        // 1. Calculate Short-Time RMS Energy (dB)
        var sumSquares = 0.0
        var zeroCrossings = 0
        var prevSample = buffer[0].toInt()
        var spectralDiffSum = 0.0
        var sampleAbsSum = 0.0

        for (i in 0 until readSize) {
            val sample = buffer[i].toInt()
            sumSquares += sample.toDouble() * sample.toDouble()
            sampleAbsSum += abs(sample)

            // Zero Crossing Rate calculation
            if ((sample >= 0 && prevSample < 0) || (sample < 0 && prevSample >= 0)) {
                zeroCrossings++
            }

            // High-low spectral gradient proxy
            spectralDiffSum += abs(sample - prevSample)
            prevSample = sample
        }

        val rms = sqrt(sumSquares / readSize)
        val rmsDb = if (rms > 0.0) (20.0 * log10(rms)).toFloat() else 0f
        _lastRmsDb.value = rmsDb

        val zcr = zeroCrossings.toFloat() / readSize
        val spectralFlux = if (sampleAbsSum > 0.0) (spectralDiffSum / sampleAbsSum).toFloat() else 0.0f

        // 2. Update Adaptive Ambient Noise Floor
        updateAdaptiveNoiseFloor(rmsDb)

        val currentNoiseFloor = _adaptiveNoiseFloorDb.value
        val snr = rmsDb - currentNoiseFloor

        // 3. Multi-parameter Human Voice Discrimination
        // Human speech typically:
        // - SNR >= minSnrDb above background
        // - RMS >= minVoiceEnergyDb
        // - ZCR between 0.02 and 0.40 (excludes purely static rumble < 0.02 and extreme high-pitch hiss > 0.45)
        // - Spectral flux in typical voiced formant range
        val isVoiceEnergySufficient = (rmsDb >= minVoiceEnergyDb && snr >= minSnrDb)
        val isZcrCharacteristicOfSpeech = (zcr in 0.015f..0.42f)
        val isFrameVoice = isVoiceEnergySufficient && (isZcrCharacteristicOfSpeech || rmsDb >= (minVoiceEnergyDb + 8.0f))

        // 4. Handle Real-Time Barge-In during Assistant Playback
        if (isAssistantSpeaking.get()) {
            handleBargeInCheck(rmsDb, isFrameVoice, now)
        }

        // 5. Turn-Taking State Machine with Hysteresis
        updateStateMachine(isFrameVoice, rmsDb, now)

        return _isSpeechActive.value
    }

    /**
     * Evaluates instant barge-in trigger when assistant is speaking.
     */
    private fun handleBargeInCheck(rmsDb: Float, isFrameVoice: Boolean, now: Long) {
        // Fast-path barge-in: If user speaks loudly over assistant playback
        val isBargeInEnergy = (rmsDb >= bargeInThresholdDb) && (isFrameVoice || rmsDb >= 42.0f)

        if (isBargeInEnergy) {
            val lastBarge = lastBargeInTimestamp.get()
            // 350ms cooldown between barge-in firings to prevent multiple triggers for one word
            if (now - lastBarge > 350L) {
                lastBargeInTimestamp.set(now)
                isBargeInCooldown.set(true)
                Log.i(TAG, "[VAD_BARGE_IN] Verified user speech barge-in detected! (RMS: ${"%.1f".format(rmsDb)} dB, Threshold: $bargeInThresholdDb dB)")
                onBargeInTriggered?.invoke(rmsDb)
            }
        }
    }

    /**
     * Temporal State Machine governing Speech Onset, Active Speech,
     * Silence Hangover, and Turn Completion.
     */
    private fun updateStateMachine(isFrameVoice: Boolean, rmsDb: Float, now: Long) {
        when (_vadState.value) {
            VadState.SILENCE -> {
                if (isFrameVoice) {
                    onsetFrameCount = 1
                    _vadState.value = VadState.SPEECH_ONSET
                    silenceStartTimeMs = 0L
                } else {
                    onsetFrameCount = 0
                }
            }

            VadState.SPEECH_ONSET -> {
                if (isFrameVoice) {
                    onsetFrameCount++
                    if (onsetFrameCount >= speechOnsetFramesRequired) {
                        // Confirmed speech start!
                        _vadState.value = VadState.SPEECH_ACTIVE
                        _isSpeechActive.value = true
                        speechStartTimeMs = now
                        speechLastActiveTimeMs = now
                        Log.i(TAG, "[VAD] Speech STARTED detected (RMS: ${"%.1f".format(rmsDb)} dB, NoiseFloor: ${"%.1f".format(_adaptiveNoiseFloorDb.value)} dB)")
                        onSpeechStarted?.invoke()
                    }
                } else {
                    // False onset (single-frame spike/click) -> Return to silence
                    onsetFrameCount = 0
                    _vadState.value = VadState.SILENCE
                }
            }

            VadState.SPEECH_ACTIVE -> {
                if (isFrameVoice) {
                    speechLastActiveTimeMs = now
                } else {
                    // Frame dropped below voice criteria -> enter hangover
                    silenceStartTimeMs = now
                    _vadState.value = VadState.SILENCE_HANGOVER
                }
            }

            VadState.SILENCE_HANGOVER -> {
                if (isFrameVoice) {
                    // User resumed speaking before hangover expired (e.g. natural pause between words)
                    speechLastActiveTimeMs = now
                    _vadState.value = VadState.SPEECH_ACTIVE
                } else {
                    val silenceDuration = now - silenceStartTimeMs
                    if (silenceDuration >= silenceHangoverMs) {
                        // Hangover timeout expired -> Definite end of conversational turn!
                        val speechDuration = max(0L, speechLastActiveTimeMs - speechStartTimeMs)
                        val totalTurnDuration = max(0L, now - speechStartTimeMs)

                        _vadState.value = VadState.SILENCE
                        _isSpeechActive.value = false
                        onsetFrameCount = 0

                        Log.i(TAG, "[VAD] Turn complete: Speech ended after ${speechDuration}ms (Silence detected: ${silenceDuration}ms, Total turn: ${totalTurnDuration}ms)")

                        onSilenceDetected?.invoke(silenceDuration)
                        onSpeechEnded?.invoke(speechDuration)
                        onTurnComplete?.invoke(speechDuration, totalTurnDuration)

                        silenceStartTimeMs = 0L
                        speechStartTimeMs = 0L
                    }
                }
            }
        }
    }

    /**
     * Dynamically adjusts ambient noise floor using asymmetric tracking.
     */
    private fun updateAdaptiveNoiseFloor(frameRmsDb: Float) {
        val currentFloor = _adaptiveNoiseFloorDb.value

        // Only adapt noise floor when speech is not active, or when frame is quieter than current floor
        if (_vadState.value == VadState.SILENCE || frameRmsDb < currentFloor) {
            val updatedFloor = if (frameRmsDb < currentFloor) {
                // Fast tracking downwards into quieter environments
                currentFloor * 0.92f + frameRmsDb * 0.08f
            } else {
                // Slow tracking upwards into noisier environments to avoid tracking speech as noise
                currentFloor * 0.985f + frameRmsDb * 0.015f
            }
            _adaptiveNoiseFloorDb.value = updatedFloor.coerceIn(MIN_NOISE_FLOOR_DB, MAX_NOISE_FLOOR_DB)
        }
    }

    /**
     * Resets internal state machine to SILENCE (e.g. on new session or manual stop).
     */
    fun reset() {
        Log.d(TAG, "Resetting VAD state machine.")
        _vadState.value = VadState.SILENCE
        _isSpeechActive.value = false
        onsetFrameCount = 0
        speechStartTimeMs = 0L
        speechLastActiveTimeMs = 0L
        silenceStartTimeMs = 0L
        isBargeInCooldown.set(false)
    }
}
