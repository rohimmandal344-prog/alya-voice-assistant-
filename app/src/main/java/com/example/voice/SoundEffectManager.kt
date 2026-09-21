package com.example.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * High-fidelity, zero-latency synthesizer for pleasant UI sound effects.
 * Generates harmonic acoustic chimes and tones dynamically without requiring external assets.
 */
class SoundEffectManager {

    private val scope = CoroutineScope(Dispatchers.Default)

    enum class SoundType {
        VOICE_START,       // Gentle ascending chord when live voice activates
        VOICE_END,         // Soft descending tone when live voice disconnects
        MESSAGE_SENT,      // Light pop/chime when user sends a message
        TASK_SUCCESS,      // Melodic 3-tone arpeggio when action succeeds
        THINKING_CHIME,    // Subtle soft pulse when AI starts generating
        WAKE_WORD_ACK      // Soft pleasant acknowledgment chime when wake-word is detected
    }

    /**
     * Plays the requested sound effect asynchronously if sound effects are enabled.
     */
    fun play(type: SoundType, enabled: Boolean = true, volume: Float = 0.65f) {
        // Disable intrusive start/end tones during live conversation
        if (!enabled || type == SoundType.VOICE_START || type == SoundType.VOICE_END) return

        scope.launch {
            try {
                when (type) {
                    SoundType.VOICE_START -> { /* Silenced to prevent activation tones */ }
                    SoundType.VOICE_END -> { /* Silenced to prevent call-end tones */ }
                    SoundType.MESSAGE_SENT -> playSingleTone(freq = 783.99f, durationMs = 80, volume = volume * 0.3f)
                    SoundType.TASK_SUCCESS -> playArpeggio(freqs = floatArrayOf(523.25f, 659.25f, 783.99f, 1046.50f), noteDurationMs = 80, volume = volume)
                    SoundType.THINKING_CHIME -> playSingleTone(freq = 523.25f, durationMs = 70, volume = volume * 0.3f)
                    SoundType.WAKE_WORD_ACK -> playDualToneChime(freq1 = 880.00f, freq2 = 1174.66f, durationMs = 120, volume = volume * 0.4f)
                }
            } catch (e: Exception) {
                Log.w("SoundEffectManager", "Error playing sound effect: ${e.message}")
            }
        }
    }

    private fun playSingleTone(freq: Float, durationMs: Int, volume: Float) {
        val sampleRate = 44100
        val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
        val buffer = ShortArray(numSamples)

        val vol = volume.coerceIn(0f, 1f)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val envelope = exp(-5.0 * t / (durationMs / 1000.0)) // Smooth exponential decay
            val sine = sin(2.0 * PI * freq * t)
            buffer[i] = (sine * envelope * vol * Short.MAX_VALUE).toInt().toShort()
        }

        playPcmBuffer(buffer, sampleRate)
    }

    private fun playDualToneChime(freq1: Float, freq2: Float, durationMs: Int, volume: Float) {
        val sampleRate = 44100
        val halfSamples = (sampleRate * (durationMs / 2000.0)).toInt()
        val totalSamples = halfSamples * 2
        val buffer = ShortArray(totalSamples)
        val vol = volume.coerceIn(0f, 1f)

        for (i in 0 until halfSamples) {
            val t = i.toDouble() / sampleRate
            val envelope = exp(-4.0 * t / (halfSamples.toDouble() / sampleRate))
            val sine = sin(2.0 * PI * freq1 * t)
            buffer[i] = (sine * envelope * vol * Short.MAX_VALUE).toInt().toShort()
        }

        for (i in 0 until halfSamples) {
            val t = i.toDouble() / sampleRate
            val envelope = exp(-3.5 * t / (halfSamples.toDouble() / sampleRate))
            val sine = sin(2.0 * PI * freq2 * t)
            buffer[halfSamples + i] = (sine * envelope * vol * Short.MAX_VALUE).toInt().toShort()
        }

        playPcmBuffer(buffer, sampleRate)
    }

    private fun playArpeggio(freqs: FloatArray, noteDurationMs: Int, volume: Float) {
        val sampleRate = 44100
        val noteSamples = (sampleRate * (noteDurationMs / 1000.0)).toInt()
        val totalSamples = noteSamples * freqs.size
        val buffer = ShortArray(totalSamples)
        val vol = volume.coerceIn(0f, 1f)

        for (noteIdx in freqs.indices) {
            val freq = freqs[noteIdx]
            val offset = noteIdx * noteSamples
            for (i in 0 until noteSamples) {
                val t = i.toDouble() / sampleRate
                val envelope = exp(-4.0 * t / (noteDurationMs / 1000.0))
                val sine = sin(2.0 * PI * freq * t)
                buffer[offset + i] = (sine * envelope * vol * Short.MAX_VALUE).toInt().toShort()
            }
        }

        playPcmBuffer(buffer, sampleRate)
    }

    private fun playPcmBuffer(buffer: ShortArray, sampleRate: Int) {
        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = maxOf(buffer.size * 2, minBufferSize)

        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        audioTrack.write(buffer, 0, buffer.size)
        audioTrack.play()

        // Automatically release after tone completes
        scope.launch {
            kotlinx.coroutines.delay((buffer.size.toDouble() / sampleRate * 1000).toLong() + 100L)
            try {
                audioTrack.stop()
                audioTrack.release()
            } catch (e: Exception) { android.util.Log.e("Alya", "Exception handled", e) }
        }
    }
}
