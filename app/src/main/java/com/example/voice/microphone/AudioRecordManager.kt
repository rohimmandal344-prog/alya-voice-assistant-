package com.example.voice.microphone

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

enum class AudioRecordMode {
    DORMANT,
    BACKGROUND_WAKE_WORD,
    ACTIVE_RECOGNITION,
    CALIBRATION
}

/**
 * AudioRecordManager (Alya v3.0.0)
 * 
 * Thread-safe microphone hardware resource manager using Coroutine Mutex lock.
 * Prevents hardware contention between Background Standby (Wake-Word Detection),
 * User Voice Profile Calibration, and Active Speech Recognition.
 */
class AudioRecordManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "AudioRecordManager"
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

        @Volatile
        private var INSTANCE: AudioRecordManager? = null

        fun getInstance(context: Context): AudioRecordManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AudioRecordManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val mutex = Mutex()
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private val _currentMode = MutableStateFlow(AudioRecordMode.DORMANT)
    val currentMode: StateFlow<AudioRecordMode> = _currentMode.asStateFlow()

    private val _currentOwner = MutableStateFlow<String?>("None")
    val currentOwner: StateFlow<String?> = _currentOwner.asStateFlow()

    private val _audioRmsAmplitude = MutableStateFlow(0.0f)
    val audioRmsAmplitude: StateFlow<Float> = _audioRmsAmplitude.asStateFlow()

    private var activeAudioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val isRecording = AtomicBoolean(false)
    private var isBackgroundWakeWordPaused = AtomicBoolean(false)
    private var audioEnhancer: com.example.voice.audio.AudioEnhancer? = null

    var onAudioFrameCaptured: ((ShortArray, Int, Float) -> Unit)? = null

    /**
     * Acquires microphone hardware for a specific operation mode.
     * Higher or equal priority requests preempt lower priority operations cleanly.
     */
    suspend fun acquireMicrophone(mode: AudioRecordMode, ownerTag: String): Boolean {
        return mutex.withLock {
            val current = _currentMode.value
            Log.i(TAG, "Requesting Mic for mode=$mode, owner=$ownerTag. Current mode=$current, current owner=${_currentOwner.value}")

            if (current == mode && _currentOwner.value == ownerTag && isRecording.get()) {
                Log.d(TAG, "Mic already held by $ownerTag in mode $mode")
                return true
            }

            // Priority check: CALIBRATION and ACTIVE_RECOGNITION can preempt BACKGROUND_WAKE_WORD
            if (current == AudioRecordMode.BACKGROUND_WAKE_WORD && (mode == AudioRecordMode.CALIBRATION || mode == AudioRecordMode.ACTIVE_RECOGNITION)) {
                Log.i(TAG, "Pausing background wake-word engine to allocate Mic for $mode ($ownerTag)")
                isBackgroundWakeWordPaused.set(true)
                stopRecordingInternal()
            } else if (current != AudioRecordMode.DORMANT && current != mode) {
                // Preempt or stop lower priority
                stopRecordingInternal()
            }

            _currentMode.value = mode
            _currentOwner.value = ownerTag

            if (mode != AudioRecordMode.DORMANT) {
                return@withLock startRecordingInternal()
            }
            true
        }
    }

    /**
     * Releases microphone hardware and restores background wake-word engine if it was paused.
     */
    suspend fun releaseMicrophone(ownerTag: String) {
        mutex.withLock {
            if (_currentOwner.value == ownerTag || ownerTag == "FORCE") {
                Log.i(TAG, "Releasing microphone held by $ownerTag. Was mode=${_currentMode.value}")
                stopRecordingInternal()
                _currentMode.value = AudioRecordMode.DORMANT
                _currentOwner.value = "None"

                if (isBackgroundWakeWordPaused.getAndSet(false)) {
                    Log.i(TAG, "Restoring background wake-word listening after release by $ownerTag")
                    _currentMode.value = AudioRecordMode.BACKGROUND_WAKE_WORD
                    _currentOwner.value = "BackgroundWakeWord"
                    startRecordingInternal()
                }
            }
        }
    }

    /**
     * Pauses background wake word explicitly before launching calibration dialog.
     */
    suspend fun pauseBackgroundWakeWord() {
        mutex.withLock {
            if (_currentMode.value == AudioRecordMode.BACKGROUND_WAKE_WORD) {
                Log.i(TAG, "Explicitly pausing background wake-word engine.")
                isBackgroundWakeWordPaused.set(true)
                stopRecordingInternal()
                _currentMode.value = AudioRecordMode.DORMANT
                _currentOwner.value = "Paused"
            }
        }
    }

    /**
     * Resumes background wake word after calibration dialog is dismissed/completed.
     */
    suspend fun resumeBackgroundWakeWord() {
        mutex.withLock {
            if (isBackgroundWakeWordPaused.getAndSet(false) || _currentMode.value == AudioRecordMode.DORMANT) {
                Log.i(TAG, "Resuming background wake-word engine.")
                _currentMode.value = AudioRecordMode.BACKGROUND_WAKE_WORD
                _currentOwner.value = "BackgroundWakeWord"
                startRecordingInternal()
            }
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun startRecordingInternal(): Boolean {
        return com.example.voice.error.VoiceErrorRegistry.instance.runSafely("AudioRecordManager", false) {
            val minBufSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            val bufferSize = (minBufSize * 2).coerceAtLeast(1024)

            val record = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord state was uninitialized before startRecording")
                record.release()
                com.example.voice.error.VoiceErrorRegistry.instance.publishError(
                    com.example.voice.error.VoiceError(
                        type = com.example.voice.error.VoiceErrorType.AUDIO_RECORD_INIT_FAILED,
                        message = "Hardware initialization failed. AudioRecord state is uninitialized.",
                        suggestedAction = "Check if other recording apps or voice assistants are active.",
                        severity = com.example.voice.error.VoiceErrorSeverity.FATAL
                    )
                )
                return@runSafely false
            }

            record.startRecording()
            if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                Log.e(TAG, "AudioRecord failed to enter RECORDSTATE_RECORDING")
                record.release()
                com.example.voice.error.VoiceErrorRegistry.instance.publishError(
                    com.example.voice.error.VoiceError(
                        type = com.example.voice.error.VoiceErrorType.AUDIO_RECORD_INIT_FAILED,
                        message = "Hardware failed to start recording (RECORDSTATE_RECORDING).",
                        suggestedAction = "Close any app holding the microphone and retry.",
                        severity = com.example.voice.error.VoiceErrorSeverity.FATAL
                    )
                )
                return@runSafely false
            }

            activeAudioRecord = record
            isRecording.set(true)

            audioEnhancer = com.example.voice.audio.AudioEnhancer().apply {
                attachHardwareEffects(record.audioSessionId)
            }

            recordingJob?.cancel()
            recordingJob = scope.launch {
                val pcmBuffer = ShortArray(320) // 20ms chunks
                while (isRecording.get() && activeAudioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    val readSize = activeAudioRecord?.read(pcmBuffer, 0, pcmBuffer.size) ?: -1
                    if (readSize > 0) {
                        val rms = audioEnhancer?.processAudioFrame(pcmBuffer, readSize) ?: (calculateRms(pcmBuffer, readSize) * 100.0f)
                        _audioRmsAmplitude.value = rms
                        onAudioFrameCaptured?.invoke(pcmBuffer, readSize, rms)
                    }
                }
            }
            Log.i(TAG, "AudioRecord started successfully with AudioEnhancer for owner=${_currentOwner.value}, mode=${_currentMode.value}")
            true
        }
    }

    private fun stopRecordingInternal() {
        isRecording.set(false)
        recordingJob?.cancel()
        recordingJob = null
        audioEnhancer?.release()
        audioEnhancer = null

        try {
            activeAudioRecord?.let { record ->
                if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    record.stop()
                }
                record.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping AudioRecord: ${e.message}")
        } finally {
            activeAudioRecord = null
            _audioRmsAmplitude.value = 0.0f
        }
    }

    private fun calculateRms(buffer: ShortArray, readSize: Int): Float {
        var sum = 0.0
        for (i in 0 until readSize) {
            val sample = buffer[i].toDouble() / 32768.0
            sum += sample * sample
        }
        val meanSquare = sum / readSize.coerceAtLeast(1)
        return sqrt(meanSquare).toFloat().coerceIn(0.0f, 1.0f)
    }
}
