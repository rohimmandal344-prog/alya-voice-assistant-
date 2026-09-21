package com.example.audio

import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Centralized manager for audio lifecycle (MediaRecorder, MediaPlayer, AudioRecord)
 * to prevent resource locks and memory leaks across state transitions.
 */
object AudioLifecycleManager {
    private const val TAG = "AudioLifecycleManager"

    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var activeAudioRecord: AudioRecord? = null
    private val isLocked = AtomicBoolean(false)

    @Synchronized
    fun registerRecorder(recorder: MediaRecorder) {
        releaseRecorder()
        mediaRecorder = recorder
        Log.d(TAG, "MediaRecorder registered.")
    }

    @Synchronized
    fun registerPlayer(player: MediaPlayer) {
        releasePlayer()
        mediaPlayer = player
        Log.d(TAG, "MediaPlayer registered.")
    }
    
    @Synchronized
    fun registerAudioRecord(audioRecord: AudioRecord) {
        if (activeAudioRecord === audioRecord) {
            Log.d(TAG, "AudioRecord already registered.")
            return
        }
        releaseAudioRecord()
        activeAudioRecord = audioRecord
        Log.d(TAG, "AudioRecord (Microphone) registered as singleton.")
    }

    @Synchronized
    fun releaseRecorder() {
        try {
            mediaRecorder?.apply {
                stop()
                reset()
                release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing MediaRecorder: ${e.localizedMessage}")
        } finally {
            mediaRecorder = null
            Log.d(TAG, "MediaRecorder released successfully.")
        }
    }

    @Synchronized
    fun releasePlayer() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                reset()
                release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing MediaPlayer: ${e.localizedMessage}")
        } finally {
            mediaPlayer = null
            Log.d(TAG, "MediaPlayer released successfully.")
        }
    }

    @Synchronized
    fun releaseAudioRecord() {
        try {
            activeAudioRecord?.apply {
                if (recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    stop()
                }
                release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing AudioRecord: ${e.localizedMessage}")
        } finally {
            activeAudioRecord = null
            Log.d(TAG, "AudioRecord released successfully.")
        }
    }

    @Synchronized
    fun releaseAll() {
        if (isLocked.getAndSet(true)) return
        try {
            releaseRecorder()
            releasePlayer()
            releaseAudioRecord()
            Log.d(TAG, "All audio resources released safely.")
        } finally {
            isLocked.set(false)
        }
    }
}
