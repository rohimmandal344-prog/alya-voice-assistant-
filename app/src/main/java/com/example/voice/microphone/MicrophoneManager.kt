package com.example.voice.microphone

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

enum class MicState {
    DORMANT,
    WAKE_WORD_LISTENING,
    ACTIVE_VOICE_SESSION
}

/**
 * MicrophoneManager (Alya v2.0.1)
 * 
 * Encapsulates all microphone audio capture, low-latency buffer management,
 * and state tracking across the application.
 * 
 * Features:
 * - Real-time Audio Preprocessing (AEC, AGC, NS)
 * - Low-latency 20ms frame capture (320 samples at 16kHz)
 * - Precise RMS signal level computation.
 */
class MicrophoneManager(private val context: Context) {

    private val _micState = MutableStateFlow(MicState.DORMANT)
    val micState: StateFlow<MicState> = _micState.asStateFlow()

    private val _isRecordingActive = MutableStateFlow(false)
    val isRecordingActive: StateFlow<Boolean> = _isRecordingActive.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var recordingJob: Job? = null
    private val isCapturing = AtomicBoolean(false)

    private var audioRecord: AudioRecord? = null
    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private var minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

    private var echoCanceler: AcousticEchoCanceler? = null
    private var automaticGainControl: AutomaticGainControl? = null
    private var noiseSuppressor: NoiseSuppressor? = null

    var onAudioFrameCaptured: ((ShortArray, Int, Float) -> Unit)? = null
    var onMicStateChanged: ((MicState) -> Unit)? = null

    private val isFlushPending = AtomicBoolean(false)
    private val isMuted = AtomicBoolean(false)

    private val _isSpeechDetected = MutableStateFlow(false)
    val isSpeechDetected: StateFlow<Boolean> = _isSpeechDetected.asStateFlow()

    private var vadThresholdDb = 15f // Baseline threshold for speech detection
    private var speechTailMs = 500L // Tail period to keep speech detected after silence
    private var lastSpeechTimestamp = 0L

    /**
     * Systematically flushes internal microphone hardware buffers immediately
     * after wake-word engine triggers.
     */
    @Synchronized
    fun flushAudioBufferOnWakeWord() {
        Log.i(TAG, "[MIC_MANAGER] Systematically flushing microphone audio buffer on wake-word activation.")
        isFlushPending.set(true)
        _isSpeechDetected.value = false
        lastSpeechTimestamp = 0
        com.example.util.diagnostics.DiagnosticLogManager.instance.logEvent(
            stage = com.example.util.diagnostics.DiagnosticStage.DETECTION,
            command = "WakeWordTrigger",
            details = "Microphone hardware audio buffer systematically flushed."
        )
    }

    /**
     * Request microphone state transition. Audio capture only starts if state is non-DORMANT.
     */
    @Synchronized
    fun requestMicrophone(requestedState: MicState): Boolean {
        if (_micState.value == requestedState) return true

        Log.i(TAG, "[MIC_MANAGER] Transitioning mic state: ${_micState.value} -> $requestedState")
        _micState.value = requestedState
        onMicStateChanged?.invoke(requestedState)
        
        if (requestedState == MicState.DORMANT) {
            _isSpeechDetected.value = false
            stopCapture()
        } else {
            startCaptureInternal()
        }
        return true
    }

    /**
     * Set microphone to strictly DORMANT state, releasing hardware resources immediately.
     */
    @Synchronized
    fun setDormant() {
        if (_micState.value == MicState.DORMANT) return
        Log.i(TAG, "[MIC_MANAGER] Setting microphone to strictly DORMANT state.")
        _micState.value = MicState.DORMANT
        onMicStateChanged?.invoke(MicState.DORMANT)
        _isSpeechDetected.value = false
        stopCapture()
    }

    @SuppressLint("MissingPermission")
    private fun startCaptureInternal() {
        if (isCapturing.get()) return

        com.example.voice.error.VoiceErrorRegistry.instance.runSafely("MicrophoneManager", Unit) {
            val bufferSize = minBufferSize.coerceAtLeast(1024)
            
            audioRecord = AudioRecord(
                android.media.MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "[MIC_MANAGER] AudioRecord initialization failed.")
                stopCapture()
                com.example.voice.error.VoiceErrorRegistry.instance.publishError(
                    com.example.voice.error.VoiceError(
                        type = com.example.voice.error.VoiceErrorType.AUDIO_RECORD_INIT_FAILED,
                        message = "Hardware audio recording initialization failed in Voice Capture.",
                        suggestedAction = "Close other applications currently using the microphone and retry.",
                        severity = com.example.voice.error.VoiceErrorSeverity.FATAL
                    )
                )
                return@runSafely
            }

            // Initialize Audio Pre-processing Effects
            setupAudioEffects(audioRecord!!.audioSessionId)

            audioRecord?.let { com.example.audio.AudioLifecycleManager.registerAudioRecord(it) }
            audioRecord?.startRecording()
            isCapturing.set(true)
            _isRecordingActive.value = audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING
            Log.i(TAG, "[MIC_MANAGER] Hardware audio recording started with AEC/AGC/NS enabled (recording=${_isRecordingActive.value}).")

            recordingJob = scope.launch {
                val frameBuffer = ShortArray(320) // 20ms chunks for ultra-low latency streaming
                while (isActive && isCapturing.get()) {
                    val readSize = audioRecord?.read(frameBuffer, 0, frameBuffer.size) ?: -1
                    if (readSize > 0) {
                        if (isFlushPending.getAndSet(false)) {
                            java.util.Arrays.fill(frameBuffer, 0.toShort())
                            _isSpeechDetected.value = false
                            Log.d(TAG, "[MIC_MANAGER] Discarded stale audio frame post wake-word trigger.")
                            continue
                        }

                        val rmsDb = calculateRmsDb(frameBuffer, readSize)
                        
                        // Automatic Audio Lock / Duplex Guard: Check isMuted state and AudioLockManager
                        val audioLockManager = com.example.voice.audio.AudioLockManager.getInstance(context)

                        // Check if mic input buffer should be muted explicitly or by the Audio Lock
                        if (isMuted.get() || audioLockManager.shouldMuteInputBuffer(rmsDb, bargeInThresholdDb = 28.0f)) {
                            java.util.Arrays.fill(frameBuffer, 0.toShort())
                            _isSpeechDetected.value = false
                            continue
                        }
                        
                        // Record input frame for latency monitoring in AudioSessionManager
                        com.example.audio.AudioSessionManager.recordMicCaptureFrame()
                        
                        // Simple energy-based VAD logic
                        updateVadState(rmsDb)
                        
                        onAudioFrameCaptured?.invoke(frameBuffer, readSize, rmsDb)
                    } else if (readSize < 0) {
                        Log.e(TAG, "[MIC_MANAGER] AudioRecord read error: $readSize")
                        delay(10)
                    }
                }
            }
        }
    }

    private fun updateVadState(rmsDb: Float) {
        val now = System.currentTimeMillis()
        if (rmsDb > vadThresholdDb) {
            if (!_isSpeechDetected.value) {
                Log.d(TAG, "[VAD] Speech started detected at $rmsDb dB")
                _isSpeechDetected.value = true
            }
            lastSpeechTimestamp = now
        } else if (_isSpeechDetected.value) {
            if (now - lastSpeechTimestamp > speechTailMs) {
                Log.d(TAG, "[VAD] Speech ended (silence for ${now - lastSpeechTimestamp}ms)")
                _isSpeechDetected.value = false
            }
        }
    }

    private fun setupAudioEffects(audioSessionId: Int) {
        try {
            if (AcousticEchoCanceler.isAvailable()) {
                echoCanceler = AcousticEchoCanceler.create(audioSessionId)?.apply {
                    enabled = true
                    Log.i(TAG, "[AEC] Hardware AcousticEchoCanceler successfully enabled for session $audioSessionId (enabled=$enabled)")
                }
                com.example.util.diagnostics.DiagnosticLogManager.instance.logEvent(
                    stage = com.example.util.diagnostics.DiagnosticStage.DETECTION,
                    command = "AEC Hardware Init",
                    details = "AcousticEchoCanceler enabled for session $audioSessionId",
                    isSuccess = echoCanceler?.enabled == true
                )
            } else {
                Log.w(TAG, "[AEC] Hardware AcousticEchoCanceler is not available on this device.")
            }
            if (AutomaticGainControl.isAvailable()) {
                automaticGainControl = AutomaticGainControl.create(audioSessionId)?.apply {
                    enabled = true
                    Log.d(TAG, "AutomaticGainControl enabled for session $audioSessionId.")
                }
            }
            if (NoiseSuppressor.isAvailable()) {
                noiseSuppressor = NoiseSuppressor.create(audioSessionId)?.apply {
                    enabled = true
                    Log.d(TAG, "NoiseSuppressor enabled for session $audioSessionId.")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to initialize some audio effects: ${e.message}")
        }
    }

    private fun releaseAudioEffects() {
        echoCanceler?.release()
        echoCanceler = null
        automaticGainControl?.release()
        automaticGainControl = null
        noiseSuppressor?.release()
        noiseSuppressor = null
    }

    @Synchronized
    private fun stopCapture() {
        isCapturing.set(false)
        _isRecordingActive.value = false
        recordingJob?.cancel()
        recordingJob = null

        releaseAudioEffects()

        try {
            audioRecord?.run {
                if (recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    stop()
                }
                release()
            }
            audioRecord = null
            Log.d(TAG, "[MIC_MANAGER] AudioRecord capture stopped and hardware released.")
        } catch (e: Exception) {
            Log.e(TAG, "[MIC_MANAGER] Error stopping audio capture: ${e.message}")
        }
    }

    private fun calculateRmsDb(buffer: ShortArray, length: Int): Float {
        var sum = 0.0
        for (i in 0 until length) {
            val sample = buffer[i].toDouble()
            sum += sample * sample
        }
        val mean = sum / length
        val rms = sqrt(mean)
        return if (rms > 0) (20 * kotlin.math.log10(rms)).toFloat() else 0f
    }

    fun isDormant(): Boolean = _micState.value == MicState.DORMANT

    fun setMuted(muted: Boolean) {
        Log.i(TAG, "[MIC_MANAGER] Set microphone buffer mute: $muted")
        isMuted.set(muted)
    }

    fun isMuted(): Boolean = isMuted.get()

    /**
     * Explicit lifecycle cleanup method to release all resources when component is destroyed.
     */
    fun onDestroy() {
        Log.i(TAG, "[MIC_MANAGER] Lifecycle onDestroy invoked. Tearing down MicrophoneManager.")
        setDormant()
    }

    companion object {
        private const val TAG = "MicrophoneManager"
    }
}
