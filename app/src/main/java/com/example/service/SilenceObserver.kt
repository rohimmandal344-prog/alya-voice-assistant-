package com.example.service

import android.content.Context
import android.util.Log
import com.example.AlyaApplication
import com.example.audio.AudioSessionManager
import com.example.audio.AudioSessionType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * SilenceObserver
 *
 * Listens to telephony state and enforces absolute silence from the assistant
 * when a manual or active call is in progress.
 */
class SilenceObserver(private val context: Context) {
    private val TAG = "SilenceObserver"
    private val observerScope = CoroutineScope(Dispatchers.Main)
    private var observationJob: Job? = null

    fun start() {
        if (observationJob != null) return

        observationJob = observerScope.launch {
            Log.i(TAG, "SilenceObserver started. Monitoring telephony state for assistant silence enforcement.")
            
            TelephonyService.isCallActiveFlow.collect { isCallActive ->
                if (isCallActive) {
                    Log.i(TAG, "Active call detected (OFFHOOK/RINGING). Enforcing assistant silence.")
                    enforceSilence()
                } else {
                    Log.d(TAG, "Call ended (IDLE). Releasing silence enforcement.")
                    releaseSilence()
                }
            }
        }
    }

    fun stop() {
        observationJob?.cancel()
        observationJob = null
    }

    private fun enforceSilence() {
        try {
            val app = context.applicationContext as? AlyaApplication ?: return
            
            // 1. Immediately stop TTS to prevent assistant from speaking over the call
            app.ttsManager.stop()
            
            // 2. Release any active audio sessions (Mic/Recognition)
            AudioSessionManager.releaseSession(AudioSessionType.SPEECH_RECOGNITION)
            AudioSessionManager.releaseSession(AudioSessionType.WAKE_WORD)
            
            // 3. Stop active listening if any
            app.speechManager.stopListening()
            
            // 4. Force state machine to IDLE to prevent processing background noise as speech
            app.sessionManager.onSessionStopped()
            
            Log.d(TAG, "Assistant silenced successfully during active call.")
        } catch (e: Exception) {
            Log.e(TAG, "Error enforcing assistant silence: ${e.message}")
        }
    }

    private fun releaseSilence() {
        // When call ends, we allow the standard process (WakeWordService, etc.) to resume sessions naturally.
        // We don't force-start anything here to respect user preferences and battery saver states.
        Log.d(TAG, "Telephony IDLE. Silence enforcement lifted.")
    }
}
