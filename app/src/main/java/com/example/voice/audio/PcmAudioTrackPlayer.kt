package com.example.voice.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * High-performance, low-latency streaming PCM Audio Player powered by Android AudioTrack.
 * Directly decodes and routes 24kHz 16-bit Mono PCM audio streams from Gemini Live Multimodal API
 * to the Android hardware speaker without clicks, silence, latency spikes, or truncation.
 */
class PcmAudioTrackPlayer(
    val sampleRate: Int = 24000,
    val channelConfig: Int = AudioFormat.CHANNEL_OUT_MONO,
    val audioFormat: Int = AudioFormat.ENCODING_PCM_16BIT
) {

    companion object {
        private const val TAG = "PcmAudioTrackPlayer"
        private const val BYTES_PER_SAMPLE = 2 // 16-bit Mono = 2 bytes per frame
        private const val PREBUFFER_THRESHOLD_BYTES = 960 // ~20ms of 24kHz audio for ultra-low latency start
    }

    private var audioTrack: AudioTrack? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val audioChannel = Channel<ByteArray>(capacity = 2048)
    private var playbackJob: Job? = null
    private var completionMonitorJob: Job? = null

    // Audio tracking state
    private var baselineHeadPosition = 0L

    private val isPlayingState = AtomicBoolean(false)
    private val _isPlaybackActive = MutableStateFlow(false)
    val isPlaybackActive: StateFlow<Boolean> = _isPlaybackActive.asStateFlow()

    // Precise frame counting
    private val totalFramesWritten = AtomicLong(0)
    private val chunksReceivedCount = AtomicLong(0)
    private val totalBytesWritten = AtomicLong(0)

    // Turn lifecycle state guards
    private val isTurnActive = AtomicBoolean(false)
    private val isServerTurnComplete = AtomicBoolean(false)

    var onPlaybackStarted: (() -> Unit)? = null
    var onPlaybackFinished: (() -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    init {
        initializeAudioTrack()
    }

    /**
     * Pushes a new PCM chunk into the jitter buffer.
     */
    fun enqueueAudio(pcmChunk: ByteArray) {
        if (pcmChunk.isEmpty()) return
        com.example.audio.AudioSessionManager.enqueuePcmChunk(pcmChunk)
        // Ensure playback loop is running
        startPlaybackLoop()
    }

    @Synchronized
    private fun initializeAudioTrack(): Boolean {
        return try {
            val minBufSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            // Sized for 4x minBufferSize (min 19200 bytes) to avoid buffer underruns & cracked audio
            val bufferSize = maxOf(minBufSize * 4, 19200)

            val audioAttributes = AudioAttributes.Builder()
                .apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        setUsage(AudioAttributes.USAGE_ASSISTANT)
                    } else {
                        setUsage(AudioAttributes.USAGE_MEDIA)
                    }
                }
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            val format = AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setChannelMask(channelConfig)
                .setEncoding(audioFormat)
                .build()

            try {
                audioTrack?.pause()
                audioTrack?.flush()
                audioTrack?.release()
            } catch (e: Exception) { android.util.Log.e("Alya", "Exception handled", e) }

            val builder = AudioTrack.Builder()
                .setAudioAttributes(audioAttributes)
                .setAudioFormat(format)
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                builder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            }
            audioTrack = builder.build()

            audioTrack?.setVolume(1.0f)
            baselineHeadPosition = 0L
            totalFramesWritten.set(0)

            Log.i(TAG, "AudioTrack initialized successfully: sampleRate=$sampleRate, bufferSize=$bufferSize")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize AudioTrack: ${e.message}", e)
            onError?.invoke("AudioTrack initialization error: ${e.message}")
            false
        }
    }

    private fun applyFadeIn(pcmBytes: ByteArray) {
        val numSamples = pcmBytes.size / 2
        val fadeLength = minOf(numSamples, 240) // 5ms fade-in at 24kHz
        for (i in 0 until fadeLength) {
            val low = pcmBytes[i * 2].toInt() and 0xFF
            val high = pcmBytes[i * 2 + 1].toInt()
            val sample = (high shl 8) or low
            val factor = i.toFloat() / fadeLength.toFloat()
            val fadedSample = (sample * factor).toInt().coerceIn(-32768, 32767)
            pcmBytes[i * 2] = (fadedSample and 0xFF).toByte()
            pcmBytes[i * 2 + 1] = ((fadedSample shr 8) and 0xFF).toByte()
        }
    }

    /**
     * Starts the asynchronous playback loop reading incoming PCM audio chunks from jitter buffer.
     * Guarantees that AudioTrack is playing and resumes seamlessly even after barge-in pause/flushes.
     */
    fun startPlaybackLoop() {
        if (playbackJob?.isActive == true) return

        playbackJob = scope.launch {
            Log.i(TAG, "Starting AudioTrack streaming playback loop with Jitter Buffer...")
            var prebufferedBytes = 0

            try {
                if (audioTrack == null || audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
                    initializeAudioTrack()
                }

                while (isActive) {
                    // Jitter Buffer logic: delegate to AudioSessionManager
                    val pcmChunk = com.example.audio.AudioSessionManager.pollJitterChunk(isServerTurnComplete.get())
                    if (pcmChunk == null || pcmChunk.isEmpty()) {
                        delay(12)
                        continue
                    }

                    val currentTrack = audioTrack ?: run {
                        initializeAudioTrack()
                        audioTrack ?: continue
                    }

                    if (currentTrack.state != AudioTrack.STATE_INITIALIZED) {
                        initializeAudioTrack()
                        continue
                    }

                    // Write PCM audio chunk directly to hardware buffer
                    var bytesWritten = 0
                    var offset = 0
                    val totalToWrite = pcmChunk.size

                    while (offset < totalToWrite && isActive) {
                        val result = currentTrack.write(pcmChunk, offset, totalToWrite - offset, AudioTrack.WRITE_BLOCKING)
                        if (result < 0) {
                            Log.w(TAG, "AudioTrack write returned error code $result. Reinitializing AudioTrack.")
                            initializeAudioTrack()
                            break
                        }
                        bytesWritten += result
                        offset += result
                        
                        // Record output frame for latency monitoring in AudioSessionManager and LatencyEngine
                        com.example.audio.AudioSessionManager.recordTTSPlaybackFrame()
                    }

                    if (bytesWritten > 0) {
                        totalBytesWritten.addAndGet(bytesWritten.toLong())
                        totalFramesWritten.addAndGet((bytesWritten / BYTES_PER_SAMPLE).toLong())
                        chunksReceivedCount.incrementAndGet()
                        prebufferedBytes += bytesWritten

                        // Start hardware playback once prebuffering threshold is met or enough jitter chunks collected
                        val activeTrack = audioTrack
                        if (activeTrack != null && activeTrack.playState != AudioTrack.PLAYSTATE_PLAYING) {
                            // Reduced threshold for lower latency start once jitter buffer is ready
                            if (prebufferedBytes >= PREBUFFER_THRESHOLD_BYTES || isServerTurnComplete.get()) {
                                try {
                                    activeTrack.play()
                                    if (!isPlayingState.get()) {
                                        isPlayingState.set(true)
                                        _isPlaybackActive.value = true
                                        com.example.voice.audio.AudioLockManager.getInstance(com.example.AlyaApplication.instance).acquireLock(com.example.voice.audio.AudioLockReason.PCM_STREAMING)
                                        onPlaybackStarted?.invoke()
                                    }
                                } catch (e: Exception) {
                                    Log.w(TAG, "Error transitioning AudioTrack to PLAYING: ${e.message}")
                                }
                            }
                        } else if (activeTrack != null && activeTrack.playState == AudioTrack.PLAYSTATE_PLAYING) {
                            if (!isPlayingState.get()) {
                                isPlayingState.set(true)
                                _isPlaybackActive.value = true
                                com.example.voice.audio.AudioLockManager.getInstance(com.example.AlyaApplication.instance).acquireLock(com.example.voice.audio.AudioLockReason.PCM_STREAMING)
                                onPlaybackStarted?.invoke()
                            }
                        }

                        // If turn has finished sending from server, keep monitor active
                        if (isServerTurnComplete.get()) {
                            monitorPlaybackCompletion()
                        }
                    }
                }
            } catch (_: CancellationException) {
                Log.d(TAG, "AudioTrack playback loop cancelled.")
            } catch (e: Exception) {
                Log.e(TAG, "Exception in AudioTrack playback loop: ${e.message}", e)
                onError?.invoke("Playback error: ${e.message}")
            } finally {
                isPlayingState.set(false)
                _isPlaybackActive.value = false
                isTurnActive.set(false)
                isServerTurnComplete.set(false)
                com.example.voice.audio.AudioLockManager.getInstance(com.example.AlyaApplication.instance).releaseLock(com.example.voice.audio.AudioLockReason.PCM_STREAMING)
                onPlaybackFinished?.invoke()
            }
        }
    }

    /**
     * Monitors when the hardware audio head has physically finished playing all queued frames.
     * Prevents truncating the ending words of any response while keeping responsiveness sharp.
     * Only triggers when server turn is complete to avoid false pause/stutter during network streaming.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun monitorPlaybackCompletion() {
        completionMonitorJob?.cancel()
        completionMonitorJob = scope.launch {
            while (isActive && isPlayingState.get() && isServerTurnComplete.get()) {
                val track = audioTrack ?: break
                if (track.state != AudioTrack.STATE_INITIALIZED) break

                val rawHead = track.playbackHeadPosition.toLong() and 0xFFFFFFFFL
                val playedFrames = rawHead - baselineHeadPosition
                val writtenFrames = totalFramesWritten.get()
                val pendingFrames = writtenFrames - playedFrames

                if (pendingFrames <= 0L && com.example.audio.AudioSessionManager.getBufferDepthMs() == 0) {
                    // Small acoustic settling delay for hardware speaker DAC
                    delay(50)
                    val recheckedHead = track.playbackHeadPosition.toLong() and 0xFFFFFFFFL
                    val recheckedPlayed = recheckedHead - baselineHeadPosition
                    if (com.example.audio.AudioSessionManager.getBufferDepthMs() == 0 && (writtenFrames - recheckedPlayed) <= 0L) {
                        if (isPlayingState.compareAndSet(true, false)) {
                            _isPlaybackActive.value = false
                            isTurnActive.set(false)
                            isServerTurnComplete.set(false)
                            onPlaybackFinished?.invoke()
                        }
                        break
                    }
                }

                val remainingMs = if (pendingFrames > 0L) {
                    ((pendingFrames * 1000L) / sampleRate).coerceIn(20L, 120L)
                } else {
                    30L
                }
                delay(remainingMs)
            }
        }
    }

    /**
     * Called when Gemini Live server sends turnComplete signal.
     */
    fun onServerTurnComplete() {
        Log.d(TAG, "Server turnComplete received. Ensuring all buffered frames play out without truncation.")
        isServerTurnComplete.set(true)
        // Ensure track is playing if it was waiting on prebuffer
        audioTrack?.let { track ->
            if (track.state == AudioTrack.STATE_INITIALIZED && track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                try {
                    track.play()
                    if (!isPlayingState.get()) {
                        isPlayingState.set(true)
                        _isPlaybackActive.value = true
                        onPlaybackStarted?.invoke()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error starting AudioTrack on turnComplete: ${e.message}")
                }
            }
        }
        monitorPlaybackCompletion()
    }

    /**
     * Feeds incoming PCM audio chunks (from Gemini Live WebSocket) directly into jitter buffer.
     */
    fun feedAudioChunk(pcmData: ByteArray) {
        if (pcmData.isEmpty()) return

        // If starting a fresh turn, reset head position baseline
        if (isTurnActive.compareAndSet(false, true)) {
            isServerTurnComplete.set(false)
            audioTrack?.let { track ->
                if (track.state == AudioTrack.STATE_INITIALIZED) {
                    baselineHeadPosition = track.playbackHeadPosition.toLong() and 0xFFFFFFFFL
                }
            }
            totalFramesWritten.set(0)
        }

        if (playbackJob?.isActive != true) {
            startPlaybackLoop()
        }

        com.example.audio.AudioSessionManager.enqueuePcmChunk(pcmData)
    }

    /**
     * Instant barge-in / user speech interruption:
     * Pauses track, flushes hardware output buffer, and clears jitter buffer in under 10ms.
     */
    fun stopAndFlushForBargeIn() {
        Log.i(TAG, "Barge-in triggered: Flushing Jitter Buffer and AudioTrack immediately.")
        try {
            completionMonitorJob?.cancel()
            // Drain unplayed jitter buffer instantly via AudioSessionManager
            com.example.audio.AudioSessionManager.clearJitterBuffer()

            audioTrack?.let { track ->
                try {
                    track.pause()
                    track.flush()
                    baselineHeadPosition = track.playbackHeadPosition.toLong() and 0xFFFFFFFFL
                } catch (e: Exception) {
                    Log.w(TAG, "Error pausing/flushing AudioTrack: ${e.message}")
                }
            }
            totalFramesWritten.set(0)
            isTurnActive.set(false)
            isServerTurnComplete.set(false)
        } catch (e: Exception) {
            Log.w(TAG, "Error during AudioTrack flush: ${e.message}")
        } finally {
            isPlayingState.set(false)
            _isPlaybackActive.value = false
            com.example.voice.audio.AudioLockManager.getInstance(com.example.AlyaApplication.instance).releaseLock(com.example.voice.audio.AudioLockReason.PCM_STREAMING)
        }
    }

    /**
     * Clean shutdown of audio track and jitter buffer.
     */
    fun stop() = stopCleanly()

    fun stopCleanly() {
        Log.i(TAG, "Cleanly stopping AudioTrack player and clearing jitter buffer...")
        try {
            completionMonitorJob?.cancel()
            com.example.audio.AudioSessionManager.clearJitterBuffer()

            audioTrack?.let { track ->
                if (track.state == AudioTrack.STATE_INITIALIZED && track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.pause()
                    track.flush()
                    track.stop()
                }
                baselineHeadPosition = track.playbackHeadPosition.toLong() and 0xFFFFFFFFL
            }
            totalFramesWritten.set(0)
            isTurnActive.set(false)
            isServerTurnComplete.set(false)
        } catch (e: Exception) {
            Log.w(TAG, "Error cleanly stopping AudioTrack: ${e.message}")
        } finally {
            isPlayingState.set(false)
            _isPlaybackActive.value = false
        }
    }

    fun release() {
        stopCleanly()
        playbackJob?.cancel()
        completionMonitorJob?.cancel()
        try {
            audioTrack?.release()
        } catch (e: Exception) { android.util.Log.e("Alya", "Exception handled", e) }
        audioTrack = null
        Log.i(TAG, "PcmAudioTrackPlayer resources released. Total bytes written: ${totalBytesWritten.get()}")
    }

    fun isPlaying(): Boolean {
        return isPlayingState.get() || _isPlaybackActive.value
    }

    fun isTurnActive(): Boolean {
        return isTurnActive.get() || isPlayingState.get() || _isPlaybackActive.value
    }

    fun getDiagnosticsInfo(): String {
        return "AudioTrack: isPlaying=${_isPlaybackActive.value}, sampleRate=$sampleRate, chunks=${chunksReceivedCount.get()}, totalBytes=${totalBytesWritten.get()}"
    }
}
