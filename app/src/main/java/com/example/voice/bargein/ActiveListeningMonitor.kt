package com.example.voice.bargein

import android.util.Log
import com.example.voice.TextToSpeechManager
import kotlinx.coroutines.flow.StateFlow

/**
 * ActiveListeningMonitor (Alya v2.0.0)
 * 
 * Monitors active TTS audio output streams and continuously samples audio RMS levels.
 * When the user speaks while TTS is actively outputting speech, it immediately fires
 * an interruption callback to stop TTS playback instantly, providing a seamless barge-in experience.
 */
class ActiveListeningMonitor(
    private val ttsManager: TextToSpeechManager
) {
    private var isBargeInEnabled: Boolean = true
    private var speechThresholdRms: Float = 14.0f // Threshold for detecting user voice barge-in

    var onBargeInTriggered: (() -> Unit)? = null

    /**
     * Called by audio recording loops when processing input frames while TTS is speaking.
     * Evaluates RMS audio power against speechThresholdRms to detect user speech.
     */
    fun processAudioFrameForBargeIn(rmsDb: Float) {
        if (!isBargeInEnabled) return

        if (ttsManager.isSpeaking.value && rmsDb > speechThresholdRms) {
            Log.i(TAG, "[BARGE_IN] User speech detected during TTS output (RMS: ${"%.2f".format(rmsDb)} dB > $speechThresholdRms dB). Interrupting TTS!")
            
            // Immediately stop TTS playback
            ttsManager.stop()

            // Invoke barge-in callback
            onBargeInTriggered?.invoke()
        }
    }

    fun setBargeInEnabled(enabled: Boolean) {
        isBargeInEnabled = enabled
        Log.d(TAG, "[BARGE_IN] Barge-in active monitoring set to: $enabled")
    }

    fun setSensitivityThreshold(rmsDbThreshold: Float) {
        speechThresholdRms = rmsDbThreshold.coerceIn(5.0f, 30.0f)
        Log.d(TAG, "[BARGE_IN] RMS sensitivity threshold set to: $speechThresholdRms dB")
    }

    companion object {
        private const val TAG = "ActiveListeningMonitor"
    }
}
