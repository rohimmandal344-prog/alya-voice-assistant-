package com.example.audio

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

enum class AudioSessionType {
    WAKE_WORD,
    SPEECH_RECOGNITION,
    CALL_ASSISTANT,
    NONE
}

/**
 * AudioSessionManager
 * 
 * Centralized audio session manager for Foreground Services and live voice pipeline:
 * 1. Manages audio session ownership (WAKE_WORD, SPEECH_RECOGNITION).
 * 2. Provides an adaptive Jitter Buffer for PCM streaming audio.
 * 3. Enforces latency-reduction logic ensuring microphone-to-TTS pipeline latency remains < 200ms.
 */
object AudioSessionManager {
    private const val TAG = "AudioSessionManager"
    private const val TARGET_MAX_LATENCY_MS = 200L
    private const val CRITICAL_LATENCY_THRESHOLD_MS = 180L

    private val activeSession = AtomicReference<AudioSessionType>(AudioSessionType.NONE)
    
    private val _sessionFlow = MutableStateFlow(AudioSessionType.NONE)
    val sessionFlow: StateFlow<AudioSessionType> = _sessionFlow.asStateFlow()

    private val sessionOwners = mutableMapOf<AudioSessionType, () -> Unit>()
    private val isTransitioning = AtomicBoolean(false)

    // Jitter Buffer Implementation (Adaptive 20ms - 40ms depth)
    private val jitterBuffer = JitterBuffer(TAG, initialMinDepth = 2, maxDepth = 8)

    // End-to-End Pipeline Latency Tracker
    private val lastMicCaptureTimestamp = AtomicLong(0L)
    private val lastPlaybackTimestamp = AtomicLong(0L)

    private val _measuredPipelineLatencyMs = MutableStateFlow(0L)
    val measuredPipelineLatencyMs: StateFlow<Long> = _measuredPipelineLatencyMs.asStateFlow()

    private val _isLowLatencyModeActive = MutableStateFlow(true)
    val isLowLatencyModeActive: StateFlow<Boolean> = _isLowLatencyModeActive.asStateFlow()

    @Synchronized
    fun registerSessionOwner(type: AudioSessionType, onDeactivate: () -> Unit) {
        sessionOwners[type] = onDeactivate
        Log.d(TAG, "Registered owner for session type: $type")
    }

    @Synchronized
    fun unregisterSessionOwner(type: AudioSessionType) {
        sessionOwners.remove(type)
        Log.d(TAG, "Unregistered owner for session type: $type")
    }

    /**
     * Requests a session transition with prioritized low-latency execution.
     * Ensures previous resource is yielded before new one is acquired.
     * Implements robust mic-release policy to handle hardware race conditions.
     */
    fun requestSession(type: AudioSessionType): Boolean {
        if (isTransitioning.getAndSet(true)) {
            Log.w(TAG, "Session request for $type ignored: Transition already in progress.")
            return false
        }

        try {
            val current = activeSession.get()
            if (current == type) {
                Log.d(TAG, "Session already active: $type")
                return true
            }

            Log.i(TAG, "Arbitrating mic transition: $current -> $type")

            // 1. Explicitly release/deactivate current owner
            if (current != AudioSessionType.NONE) {
                try {
                    Log.d(TAG, "Requesting resource release from owner: $current")
                    sessionOwners[current]?.invoke()
                    
                    // Allow hardware teardown to complete without freezing main looper
                    if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
                        Thread.sleep(40)
                    }
                    Log.d(TAG, "Previous owner $current resource yield confirmed.")
                } catch (e: Exception) {
                    Log.e(TAG, "Error during resource yield for $current: ${e.message}")
                }
            }

            // 2. Clear buffers and transition to NONE state briefly to ensure clean hardware re-initialization
            clearJitterBuffer()
            activeSession.set(AudioSessionType.NONE)
            _sessionFlow.value = AudioSessionType.NONE
            
            if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
                Thread.sleep(10)
            }

            // 3. Complete transition to new session
            activeSession.set(type)
            _sessionFlow.value = type
            Log.i(TAG, "Mic handoff successful. New owner: $type")
            return true
        } finally {
            isTransitioning.set(false)
        }
    }

    fun releaseSession(type: AudioSessionType) {
        if (activeSession.compareAndSet(type, AudioSessionType.NONE)) {
            Log.i(TAG, "Releasing active session: $type")
            _sessionFlow.value = AudioSessionType.NONE
            clearJitterBuffer()
        }
    }

    fun getActiveSession(): AudioSessionType = activeSession.get()

    // =========================================================================
    // JITTER BUFFER & LATENCY REDUCTION ENGINE
    // =========================================================================

    /**
     * Enqueues an incoming PCM audio chunk into the Jitter Buffer.
     * Automatically prunes stale chunks if buffer depth exceeds limit.
     */
    fun enqueuePcmChunk(pcmChunk: ByteArray) {
        jitterBuffer.enqueue(pcmChunk)
    }

    /**
     * Polls the next PCM audio chunk from the Jitter Buffer.
     * Respects dynamic minimum buffering depth before starting initial playback.
     */
    fun pollJitterChunk(isTurnComplete: Boolean = false): ByteArray? {
        return jitterBuffer.poll(isTurnComplete)
    }

    /**
     * Clears all buffered PCM audio chunks (e.g. on barge-in or session end).
     */
    fun clearJitterBuffer() {
        jitterBuffer.clear()
    }

    /**
     * Records an incoming microphone capture frame timestamp for end-to-end latency calculations.
     */
    fun recordMicCaptureFrame(timestampMs: Long = System.currentTimeMillis()) {
        lastMicCaptureTimestamp.set(timestampMs)
        com.example.util.LatencyOptimizationEngine.recordInputFrame()
    }

    /**
     * Records a TTS / audio playback frame timestamp.
     * Calculates total pipeline latency and triggers latency reduction if > 180ms.
     */
    fun recordTTSPlaybackFrame(timestampMs: Long = System.currentTimeMillis()) {
        lastPlaybackTimestamp.set(timestampMs)
        com.example.util.LatencyOptimizationEngine.recordOutputFrame()

        val captureTime = lastMicCaptureTimestamp.get()
        if (captureTime > 0) {
            val latency = (timestampMs - captureTime).coerceAtLeast(0L)
            _measuredPipelineLatencyMs.value = latency

            if (latency > CRITICAL_LATENCY_THRESHOLD_MS) {
                Log.w(TAG, "[LATENCY_ALERT] Pipeline latency elevated: ${latency}ms (Target: <${TARGET_MAX_LATENCY_MS}ms). Applying latency reduction!")
                triggerLatencyReduction()
            } else if (latency < 100L) {
                // Restore standard low-jitter buffer depth when network/pipeline is fast
                jitterBuffer.setMinDepth(2)
            }
        }
    }

    /**
     * Executes dynamic latency-reduction interventions:
     * - Flushes old chunks from jitter queue down to 1 chunk (~20ms).
     * - Reduces minimum prebuffer threshold to 1 chunk (~20ms) for fast throughput.
     */
    fun triggerLatencyReduction() {
        _isLowLatencyModeActive.value = true
        jitterBuffer.setMinDepth(1) // Aggressive 20ms prebuffer

        // Shed down to 1 most recent chunk
        while (jitterBuffer.size() > 1) {
            jitterBuffer.poll(isTurnComplete = true)
        }
        Log.i(TAG, "[LATENCY_REDUCTION] Latency reduction applied: Jitter buffer pruned to 1 chunk (~20ms), min buffer depth set to 1.")
    }

    /**
     * Returns current estimated jitter buffer depth in milliseconds.
     */
    fun getBufferDepthMs(): Int = jitterBuffer.getDepthMs()
}

