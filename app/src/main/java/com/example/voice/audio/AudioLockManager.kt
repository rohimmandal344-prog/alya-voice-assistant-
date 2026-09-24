package com.example.voice.audio

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

enum class AudioLockReason {
    IDLE,
    TTS_SPEAKING,
    PCM_STREAMING,
    BARGE_IN_DETECTED,
    MANUAL_LOCK
}

/**
 * Enterprise Automatic Audio Lock Manager (Alya Assistant Core).
 * 
 * Manages an automatic hardware audio lock that mutes the microphone input buffer
 * whenever the TTS engine or PCM audio track is actively playing audio.
 * Automatically releases the lock and triggers listening resumption when playback
 * completes or when a barge-in event is detected.
 */
class AudioLockManager private constructor(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _isAudioLocked = MutableStateFlow(false)
    /**
     * StateFlow indicating whether the microphone input buffer is currently muted/locked.
     */
    val isAudioLocked: StateFlow<Boolean> = _isAudioLocked.asStateFlow()

    private val _lockReason = MutableStateFlow(AudioLockReason.IDLE)
    /**
     * StateFlow exposing the current active reason for audio lock.
     */
    val lockReason: StateFlow<AudioLockReason> = _lockReason.asStateFlow()

    private val isBargeInActive = AtomicBoolean(false)

    /**
     * Listener called when the audio lock state transitions.
     */
    var onLockStateChanged: ((isLocked: Boolean, reason: AudioLockReason) -> Unit)? = null

    /**
     * Listener invoked when listening should resume after playback completion or barge-in.
     */
    var onResumeListeningRequested: (() -> Unit)? = null

    companion object {
        private const val TAG = "AudioLockManager"

        @Volatile
        private var INSTANCE: AudioLockManager? = null

        fun getInstance(context: Context): AudioLockManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AudioLockManager(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }

    private var watchdogJob: kotlinx.coroutines.Job? = null

    /**
     * Acquires the automatic audio lock, muting microphone input buffers.
     * Includes a safety watchdog timeout so the microphone can never be locked indefinitely.
     */
    @Synchronized
    fun acquireLock(reason: AudioLockReason) {
        if (_isAudioLocked.value && _lockReason.value == reason) return

        Log.i(TAG, "[AUDIO_LOCK] Acquiring audio lock for reason: $reason. Muting mic input buffer.")
        isBargeInActive.set(false)
        _lockReason.value = reason
        _isAudioLocked.value = true

        onLockStateChanged?.invoke(true, reason)

        // Safety Watchdog: Auto-release after 7 seconds if not released by player/TTS to prevent mute lockup
        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            kotlinx.coroutines.delay(7000L)
            if (_isAudioLocked.value) {
                Log.w(TAG, "[AUDIO_LOCK] Watchdog timer expired (7s). Forcing safety release of audio lock.")
                releaseLock(AudioLockReason.IDLE)
            }
        }
    }

    /**
     * Releases the audio lock, unmuting microphone input buffers and signaling listening resumption.
     */
    @Synchronized
    fun releaseLock(reason: AudioLockReason) {
        watchdogJob?.cancel()
        watchdogJob = null

        if (!_isAudioLocked.value) return

        Log.i(TAG, "[AUDIO_LOCK] Releasing audio lock (Previous reason: ${_lockReason.value}, Trigger: $reason). Unmuting mic input buffer.")
        _isAudioLocked.value = false
        _lockReason.value = AudioLockReason.IDLE
        isBargeInActive.set(false)

        onLockStateChanged?.invoke(false, AudioLockReason.IDLE)

        // Post listening resumption request on Main thread
        scope.launch {
            onResumeListeningRequested?.invoke()
        }
    }

    /**
     * Immediately and unconditionally unlocks microphone buffers.
     */
    @Synchronized
    fun forceUnlock() {
        watchdogJob?.cancel()
        watchdogJob = null
        _isAudioLocked.value = false
        _lockReason.value = AudioLockReason.IDLE
        isBargeInActive.set(false)
        onLockStateChanged?.invoke(false, AudioLockReason.IDLE)
    }

    /**
     * Triggers a barge-in event: unlocks the mic input buffer, releases audio lock,
     * and signals immediate speech recognition resumption.
     */
    @Synchronized
    fun triggerBargeIn() {
        watchdogJob?.cancel()
        watchdogJob = null

        if (!_isAudioLocked.value && !isBargeInActive.get()) return

        Log.i(TAG, "[AUDIO_LOCK] Barge-in event detected! Unlocking mic input buffer and resuming listening.")
        isBargeInActive.set(true)
        _lockReason.value = AudioLockReason.BARGE_IN_DETECTED
        _isAudioLocked.value = false

        onLockStateChanged?.invoke(false, AudioLockReason.BARGE_IN_DETECTED)

        scope.launch {
            onResumeListeningRequested?.invoke()
        }
    }

    /**
     * Returns true if the microphone input frame should be muted (zeroed out).
     */
    fun shouldMuteInputBuffer(rmsDb: Float, bargeInThresholdDb: Float = 38.0f): Boolean {
        if (!_isAudioLocked.value) return false

        // If energy exceeds barge-in threshold, trigger barge-in unlock automatically
        if (rmsDb >= bargeInThresholdDb) {
            triggerBargeIn()
            return false
        }

        return true
    }
}
