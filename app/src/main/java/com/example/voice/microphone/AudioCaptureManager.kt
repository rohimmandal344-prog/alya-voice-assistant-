package com.example.voice.microphone

import android.content.Context
import android.util.Log
import com.example.voice.error.VoiceError
import com.example.voice.error.VoiceErrorRegistry
import com.example.voice.error.VoiceErrorType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * AudioCaptureManager (Singleton)
 * 
 * Centralized manager for all audio capture sessions. 
 * Ensures the microphone is released reliably across different states 
 * and provides error reporting for UI updates.
 */
class AudioCaptureManager private constructor(private val context: Context) {

    private val microphoneManager = MicrophoneManager(context)
    
    private val _captureError = MutableStateFlow<VoiceError?>(null)
    /**
     * StateFlow exposing the latest capture-related error for UI observation.
     */
    val captureError: StateFlow<VoiceError?> = _captureError.asStateFlow()

    val isCaptureActive: StateFlow<Boolean> = microphoneManager.isRecordingActive
    val isSpeechDetected: StateFlow<Boolean> = microphoneManager.isSpeechDetected
    val micState: StateFlow<MicState> = microphoneManager.micState

    // Realtime Voice Activity Detector (VAD) exposure
    val vad: com.example.voice.vad.RealtimeVoiceActivityDetector get() = microphoneManager.vad
    val vadState: StateFlow<com.example.voice.vad.VadState> get() = microphoneManager.vad.vadState

    var onSpeechStarted: (() -> Unit)?
        get() = microphoneManager.onSpeechStarted
        set(value) { microphoneManager.onSpeechStarted = value }

    var onSpeechEnded: ((Long) -> Unit)?
        get() = microphoneManager.onSpeechEnded
        set(value) { microphoneManager.onSpeechEnded = value }

    var onSilenceDetected: ((Long) -> Unit)?
        get() = microphoneManager.onSilenceDetected
        set(value) { microphoneManager.onSilenceDetected = value }

    var onTurnComplete: ((Long, Long) -> Unit)?
        get() = microphoneManager.onTurnComplete
        set(value) { microphoneManager.onTurnComplete = value }

    var onBargeInDetected: ((Float) -> Unit)?
        get() = microphoneManager.onBargeInDetected
        set(value) { microphoneManager.onBargeInDetected = value }

    /**
     * Callback for specific error events that require immediate UI response.
     */
    var onErrorCallback: ((VoiceError) -> Unit)? = null

    private val observerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init {
        // Observe MicrophoneManager's internal states
        microphoneManager.onMicStateChanged = { state ->
            Log.d(TAG, "Mic state changed: $state")
        }

        // Observe VoiceErrorRegistry to catch hardware/recognizer errors
        observerScope.launch {
            VoiceErrorRegistry.instance.activeError.collect { error ->
                if (error != null && isCaptureRelatedError(error.type)) {
                    _captureError.value = error
                    onErrorCallback?.invoke(error)
                    Log.e(TAG, "Capture error detected: ${error.message}")
                }
            }
        }
    }

    /**
     * Activates audio capture for the specified session type.
     */
    fun startCapture(state: MicState) {
        clearError()
        Log.i(TAG, "Starting capture for state: $state")
        com.example.audio.AlyaAudioManager.getInstance(context).registerMicrophoneHolder("AudioCaptureManager") {
            stopCapture()
        }
        microphoneManager.requestMicrophone(state)
    }

    /**
     * Reliably releases the microphone hardware and stops all capture jobs.
     */
    fun stopCapture() {
        Log.i(TAG, "Stopping capture and releasing microphone.")
        com.example.audio.AlyaAudioManager.getInstance(context).unregisterMicrophoneHolder("AudioCaptureManager")
        microphoneManager.setDormant()
    }

    /**
     * Systematically flushes internal microphone hardware buffers immediately
     * after wake-word engine triggers.
     */
    fun flushAudioBufferOnWakeWord() {
        microphoneManager.flushAudioBufferOnWakeWord()
    }

    /**
     * Clears any active error states.
     */
    fun clearError() {
        _captureError.value = null
    }

    /**
     * Returns the raw short arrays from the microphone.
     */
    fun setAudioFrameListener(listener: (ShortArray, Int, Float) -> Unit) {
        microphoneManager.onAudioFrameCaptured = listener
    }

    /**
     * Mutes or unmutes the microphone capture buffer.
     */
    fun setMuted(muted: Boolean) {
        microphoneManager.setMuted(muted)
    }

    /**
     * Checks if the microphone capture buffer is currently muted.
     */
    fun isMuted(): Boolean {
        return microphoneManager.isMuted()
    }

    /**
     * Releases resources and cancels active coroutine scopes.
     */
    fun release() {
        Log.i(TAG, "Releasing AudioCaptureManager resources.")
        stopCapture()
        observerScope.cancel()
        onErrorCallback = null
    }

    private fun isCaptureRelatedError(type: VoiceErrorType): Boolean {
        return type == VoiceErrorType.AUDIO_RECORD_INIT_FAILED ||
               type == VoiceErrorType.RECOGNIZER_BUSY ||
               type == VoiceErrorType.SECURITY_PERMISSION_DENIED
    }

    companion object {
        private const val TAG = "AudioCaptureManager"
        
        @Volatile
        private var INSTANCE: AudioCaptureManager? = null

        fun getInstance(context: Context): AudioCaptureManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AudioCaptureManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
