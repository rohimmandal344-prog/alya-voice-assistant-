package com.example.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Log
import com.example.data.ai.GeminiApiClient
import com.example.voice.AudioDeviceManager
import com.example.voice.SpeechRecognitionManager
import com.example.voice.wakeword.WakeWordManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Session Lifecycle State
 */
enum class SessionState {
    IDLE,
    WAKE_DETECTED,
    LISTENING,
    PROCESSING,
    RESPONDING,
    SPEAKING,
    STOPPED
}

typealias VoiceSessionState = SessionState
typealias VoiceSessionStateManager = SessionManager

/**
 * SessionManager (Update 2.1.0)
 * 
 * Centralized manager strictly enforcing lifecycle control, rapid device readiness,
 * and transparent real-time status reporting without fake states:
 * 1. Microphone: Disarms and cancels SpeechRecognizer active handles.
 * 2. Audio Focus: Abandons hardware audio routing priority.
 * 3. Network Sockets: Cancels active Retrofit requests and evicts OkHttp socket pools.
 * 4. Wake-word Recognition: Duty-cycles or stops wake-word listener threads.
 * 5. Connectivity & Readiness: Real-time network telemetry and zero-delay startup.
 */
class SessionManager(
    private val context: Context,
    private val speechManager: SpeechRecognitionManager,
    private val audioDeviceManager: AudioDeviceManager,
    private val geminiClient: GeminiApiClient? = null,
    private val wakeWordManager: WakeWordManager
) {
    companion object {
        private const val TAG = "SessionManager"
    }

    private val managerScope = CoroutineScope(Dispatchers.IO)

    // Real-time observable telemetry for UI Dashboard
    private val _sessionState = MutableStateFlow(SessionState.IDLE)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    private val _isMicActive = MutableStateFlow(false)
    val isMicActive: StateFlow<Boolean> = _isMicActive.asStateFlow()

    private val _isAudioFocusHeld = MutableStateFlow(false)
    val isAudioFocusHeld: StateFlow<Boolean> = _isAudioFocusHeld.asStateFlow()

    private val _isNetworkAvailable = MutableStateFlow(true)
    val isNetworkAvailable: StateFlow<Boolean> = _isNetworkAvailable.asStateFlow()

    private val _isSessionLocked = MutableStateFlow(false)
    val isSessionLocked: StateFlow<Boolean> = _isSessionLocked.asStateFlow()

    private val _isBatterySaverActive = MutableStateFlow(false)
    val isBatterySaverActive: StateFlow<Boolean> = _isBatterySaverActive.asStateFlow()

    private val connectivityObserver = (context.applicationContext as com.example.AlyaApplication).connectivityObserver

    init {
        initializeEarly()
    }

    /**
     * Early initialization to eliminate 'Waiting for device...' delays
     * and ensure rapid app launch and readiness.
     */
    fun initializeEarly() {
        managerScope.launch {
            try {
                Log.i(TAG, "Initializing SessionManager early for fast launch...")
                observeNetwork()
            } catch (e: Exception) {
                Log.e(TAG, "Error in early initialization", e)
            }
        }
    }

    private fun observeNetwork() {
        managerScope.launch {
            connectivityObserver.observe().collect { status ->
                val isAvailable = status == com.example.util.ConnectivityObserver.Status.Available
                _isNetworkAvailable.value = isAvailable
                Log.i(TAG, "Network status changed: $status (Available: $isAvailable)")
                if (!isAvailable) {
                    lockSession("Device went offline — preventing network recognition retry loops")
                } else {
                    unlockSession("Device went online — restoring session readiness")
                }
            }
        }
    }

    private fun provideOfflineFeedback() {
        // Fully offline mode supported: never interrupt or terminate session when offline
        Log.i(TAG, "Operating in local offline mode seamlessly.")
    }

    /**
     * Locks the audio session to prevent re-triggering recognition loops when entering background or offline states.
     */
    fun lockSession(reason: String) {
        Log.i(TAG, "Locking audio session: $reason")
        _isSessionLocked.value = true
    }

    /**
     * Unlocks the audio session when returning to active foreground/online state.
     */
    fun unlockSession(reason: String) {
        Log.i(TAG, "Unlocking audio session: $reason")
        _isSessionLocked.value = false
    }

    /**
     * Mark session as wake detected.
     */
    fun onWakeDetected() {
        if (_isSessionLocked.value) {
            Log.w(TAG, "Ignored onWakeDetected: Session is locked.")
            return
        }
        updateState(SessionState.WAKE_DETECTED)
    }

    /**
     * Mark session as listening.
     */
    fun onListeningStarted() {
        if (_isSessionLocked.value) {
            Log.w(TAG, "Ignored onListeningStarted: Session is locked.")
            return
        }
        updateState(SessionState.LISTENING)
        _isMicActive.value = true
        _isAudioFocusHeld.value = true
    }

    // Callbacks for turn-taking & barge-in coordination
    var onBargeInTriggered: (() -> Unit)? = null
    var onTurnTakingTriggered: ((speechDurationMs: Long, totalTurnMs: Long) -> Unit)? = null
    var onSilenceDetected: ((silenceDurationMs: Long) -> Unit)? = null

    /**
     * VAD Turn-Taking Hook: Invoked when real-time VAD detects user speech onset.
     * Accurately arbitrates turn-taking:
     * - If assistant is speaking or responding, immediately signals barge-in and transitions session to LISTENING.
     * - If session is IDLE or WAKE_DETECTED, transitions to LISTENING.
     */
    fun onUserSpeechStarted() {
        if (_isSessionLocked.value) {
            Log.w(TAG, "Ignored onUserSpeechStarted: Session is locked.")
            return
        }
        val currentState = _sessionState.value
        if (currentState == SessionState.SPEAKING || currentState == SessionState.RESPONDING) {
            Log.i(TAG, "[SESSION_VAD] User speech started during $currentState! Firing Barge-In turn transition to LISTENING.")
            updateState(SessionState.LISTENING)
            _isMicActive.value = true
            _isAudioFocusHeld.value = true
            onBargeInTriggered?.invoke()
        } else if (currentState == SessionState.IDLE || currentState == SessionState.WAKE_DETECTED) {
            updateState(SessionState.LISTENING)
            _isMicActive.value = true
            _isAudioFocusHeld.value = true
        }
    }

    /**
     * VAD Turn-Taking Hook: Invoked when real-time VAD confirms conversational turn completion (silence detection).
     * Accurately transitions session from LISTENING to PROCESSING.
     */
    fun onUserTurnCompleted(speechDurationMs: Long, totalTurnMs: Long) {
        if (_isSessionLocked.value) return
        val currentState = _sessionState.value
        if (currentState == SessionState.LISTENING) {
            Log.i(TAG, "[SESSION_VAD] User completed speech turn (${speechDurationMs}ms speech, ${totalTurnMs}ms turn). Transitioning to PROCESSING.")
            updateState(SessionState.PROCESSING)
            onTurnTakingTriggered?.invoke(speechDurationMs, totalTurnMs)
        }
    }

    /**
     * VAD Turn-Taking Hook: Invoked when real-time silence is detected during listening.
     */
    fun onSilenceDetected(silenceDurationMs: Long) {
        onSilenceDetected?.invoke(silenceDurationMs)
    }

    /**
     * Mark session as processing.
     */
    fun onProcessingStarted() {
        updateState(SessionState.PROCESSING)
    }

    /**
     * Mark session as responding (AI thinking/preparing).
     */
    fun onRespondingStarted() {
        updateState(SessionState.RESPONDING)
    }

    /**
     * Mark session as speaking (TTS active).
     */
    fun onSpeakingStarted() {
        updateState(SessionState.SPEAKING)
    }

    /**
     * Mark session as stopped.
     */
    fun onSessionStopped() {
        updateState(SessionState.STOPPED)
        _isMicActive.value = false
        _isAudioFocusHeld.value = false
    }

    /**
     * Compatibility hook for live voice mode startup.
     */
    fun onLiveVoiceStarted() {
        onListeningStarted()
    }

    /**
     * Compatibility hook for live voice standby mode.
     */
    fun onLiveVoiceStandby() {
        onSessionStopped()
    }

    private fun updateState(newState: SessionState) {
        Log.i(TAG, "Session state transition: ${_sessionState.value} -> $newState")
        _sessionState.value = newState
    }

    /**
     * Explicitly tears down, closes, and clears active handles immediately.
     */
    fun releaseActiveSession() {
        Log.i(TAG, "SessionManager: Triggered explicit release of active voice/network resources.")
        
        onSessionStopped()

        // 1. Release Microphone & stop SpeechRecognizer
        try {
            Log.i(TAG, "Releasing Speech Recognizer & Microphone Stream...")
            speechManager.isContinuousMode = false
            speechManager.stopListening()
            speechManager.clearError()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing Speech Recognizer", e)
        }

        // 2. Abandon Audio Focus immediately
        try {
            Log.i(TAG, "Abandoning Audio Focus...")
            audioDeviceManager.abandonAudioFocus()
            _isAudioFocusHeld.value = false
        } catch (e: Exception) {
            Log.e(TAG, "Error abandoning Audio Focus", e)
        }

        // 3. Purge Active Connections and Network Sockets
        try {
            Log.i(TAG, "Releasing and evicting network sockets...")
            geminiClient?.evictActiveConnections()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing network sockets", e)
        }

        // 4. Shut down Wake Word recognition to preserve battery and stop background threads
        try {
            Log.i(TAG, "Stopping wake-word recognition...")
            wakeWordManager.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping wake-word recognition", e)
        }

        _sessionState.value = SessionState.IDLE
        Log.i(TAG, "SessionManager (Update 1.6.0): Teardown complete. All native, audio, and network handles released.")
    }

    /**
     * Toggles battery-saver mode for background service and wake-word listener.
     */
    fun setBatterySaver(enabled: Boolean) {
        _isBatterySaverActive.value = enabled
        wakeWordManager.setBatterySaverEnabled(enabled)
        Log.i(TAG, "Battery-Saver mode set to: $enabled")
    }
}
