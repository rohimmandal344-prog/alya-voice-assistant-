package com.example.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.telecom.TelecomManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.domain.tools.ToolExecutionResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * CallHandler
 *
 * Dedicated TelecomManager & AudioManager integration service:
 * 1. Manages incoming and active calls using native TelecomManager APIs.
 * 2. Executes 'answer_by_assistant' and 'reject_call' actions based on structured JSON intents.
 * 3. Handles clean microphone handoff between telephony call audio and active assistant voice sessions.
 * 4. Configures audio focus, speakerphone, and audio mode transitions without resource conflicts.
 */
class CallHandler private constructor(private val context: Context) {

    init {
        com.example.audio.AudioSessionManager.registerSessionOwner(com.example.audio.AudioSessionType.CALL_ASSISTANT) {
            Log.i(TAG, "AudioSessionManager requested deactivation of CALL_ASSISTANT session.")
            // Restore normal mode if manager deactivates us (e.g. higher priority session)
            handOffMicrophone(toCallAssistant = false)
        }
    }

    companion object {
        private const val TAG = "CallHandler"

        @Volatile
        private var INSTANCE: CallHandler? = null

        fun getInstance(context: Context): CallHandler {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: CallHandler(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val _isCallAssistantActive = MutableStateFlow(false)
    val isCallAssistantActive: StateFlow<Boolean> = _isCallAssistantActive.asStateFlow()

    private val _currentCallMode = MutableStateFlow("IDLE")
    val currentCallMode: StateFlow<String> = _currentCallMode.asStateFlow()

    private var audioFocusRequest: AudioFocusRequest? = null

    /**
     * Answers an incoming call on behalf of the assistant ('answer_by_assistant').
     * Manages TelecomManager call acceptance, switches audio mode, and hands off microphone.
     */
    fun answerByAssistant(callerInfo: String = ""): ToolExecutionResult {
        Log.i(TAG, "Executing 'answer_by_assistant' for caller: $callerInfo")

        // 1. Verify required permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val hasAnswerPerm = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ANSWER_PHONE_CALLS
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasAnswerPerm) {
                Log.w(TAG, "Missing ANSWER_PHONE_CALLS permission")
                // Fallback to accessibility service
                if (AlyaAccessibilityService.instance != null) {
                    val accSuccess = AlyaAccessibilityService.executeCommand("answer_call")
                    if (accSuccess) {
                        handOffMicrophone(toCallAssistant = true)
                        _isCallAssistantActive.value = true
                        _currentCallMode.value = "ANSWERED_BY_ASSISTANT"
                        return ToolExecutionResult(true, "Call answered by assistant via Accessibility Service.")
                    }
                }
                return ToolExecutionResult(
                    success = false,
                    message = "Permission ANSWER_PHONE_CALLS is required to answer incoming calls.",
                    missingPermission = Manifest.permission.ANSWER_PHONE_CALLS
                )
            }
        }

        // 2. Perform call acceptance via TelecomManager API
        var success = false
        var resultMessage = ""

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                telecomManager?.acceptRingingCall()
                success = true
                resultMessage = "Incoming call accepted by Assistant via TelecomManager."
                Log.i(TAG, resultMessage)
            }
        } catch (e: Exception) {
            Log.e(TAG, "TelecomManager acceptRingingCall error: ${e.message}", e)
            resultMessage = "TelecomManager failed: ${e.message}"
        }

        // Fallback to Accessibility Service if TelecomManager fails
        if (!success && AlyaAccessibilityService.instance != null) {
            success = AlyaAccessibilityService.executeCommand("answer_call")
            if (success) {
                resultMessage = "Call answered by Assistant via Accessibility fallback."
            }
        }

        if (success) {
            // Update state machine
            TelephonyService.stateMachine.transition(CallState.OFFHOOK)
            _isCallAssistantActive.value = true
            _currentCallMode.value = "ANSWERED_BY_ASSISTANT"

            // 3. Hand off microphone & configure audio mode for call screening
            handOffMicrophone(toCallAssistant = true)

            return ToolExecutionResult(true, resultMessage)
        } else {
            return ToolExecutionResult(false, "Failed to answer call. $resultMessage")
        }
    }

    /**
     * Rejects or ends an incoming/active call ('reject_call').
     * Ends call via TelecomManager, restores audio mode, and hands microphone back to Assistant voice session.
     */
    fun rejectCall(reason: String = ""): ToolExecutionResult {
        Log.i(TAG, "Executing 'reject_call'. Reason: $reason")

        var success = false
        var resultMessage = ""

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val hasAnswerPerm = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ANSWER_PHONE_CALLS
            ) == PackageManager.PERMISSION_GRANTED

            if (hasAnswerPerm) {
                try {
                    val ended = telecomManager?.endCall() ?: false
                    if (ended) {
                        success = true
                        resultMessage = "Call rejected/ended successfully via TelecomManager."
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "TelecomManager endCall error: ${e.message}", e)
                }
            }
        }

        // Fallback to Accessibility Service if needed
        if (!success && AlyaAccessibilityService.instance != null) {
            success = AlyaAccessibilityService.executeCommand("end_call")
            if (success) {
                resultMessage = "Call rejected/ended via Accessibility Service."
            }
        }

        // Fallback to media key intent (headset hook hang up)
        if (!success) {
            try {
                val mediaIntent = android.content.Intent(android.content.Intent.ACTION_MEDIA_BUTTON).apply {
                    putExtra(
                        android.content.Intent.EXTRA_KEY_EVENT,
                        android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_HEADSETHOOK)
                    )
                }
                context.sendOrderedBroadcast(mediaIntent, null)
                success = true
                resultMessage = "Call hangup trigger sent via Headset Hook Broadcast."
            } catch (e: Exception) {
                Log.w(TAG, "Media button broadcast failed: ${e.message}")
            }
        }

        // Update state machine & hand off microphone back to Assistant session
        TelephonyService.stateMachine.transition(CallState.IDLE)
        _isCallAssistantActive.value = false
        _currentCallMode.value = "IDLE"

        handOffMicrophone(toCallAssistant = false)

        return ToolExecutionResult(
            success = true,
            message = if (resultMessage.isNotBlank()) resultMessage else "Call reject request processed."
        )
    }

    /**
     * Handles explicit microphone handoff between telephony call audio and assistant voice session.
     *
     * @param toCallAssistant If true, routes audio focus and audio mode for active call screening.
     *                         If false, restores audio mode to normal and returns microphone focus to Assistant session.
     */
    fun handOffMicrophone(toCallAssistant: Boolean) {
        val am = audioManager ?: return
        val sessionManager = com.example.audio.AudioSessionManager

        try {
            if (toCallAssistant) {
                Log.i(TAG, "Handoff: Requesting CALL_ASSISTANT session from AudioSessionManager.")
                
                // 1. Request prioritized session from manager
                sessionManager.requestSession(com.example.audio.AudioSessionType.CALL_ASSISTANT)

                // 2. Request Audio Focus for call communication
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val playbackAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()

                    val focusReq = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                        .setAudioAttributes(playbackAttributes)
                        .setAcceptsDelayedFocusGain(true)
                        .setOnAudioFocusChangeListener { focusChange ->
                            Log.d(TAG, "Call AudioFocus change: $focusChange")
                            if (focusChange == AudioManager.AUDIOFOCUS_LOSS || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                                // Potentially notify system of focus loss
                            }
                        }
                        .build()

                    audioFocusRequest = focusReq
                    am.requestAudioFocus(focusReq)
                } else {
                    @Suppress("DEPRECATION")
                    am.requestAudioFocus(
                        null,
                        AudioManager.STREAM_VOICE_CALL,
                        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                    )
                }

                // 3. Set Audio Mode to IN_COMMUNICATION so mic and speaker work together
                am.mode = AudioManager.MODE_IN_COMMUNICATION
                am.isSpeakerphoneOn = true

            } else {
                Log.i(TAG, "Handoff: Releasing CALL_ASSISTANT session and restoring normal mode.")

                // 1. Release session back to manager (allows WAKE_WORD to resume)
                sessionManager.releaseSession(com.example.audio.AudioSessionType.CALL_ASSISTANT)

                // 2. Abandon Call Audio Focus
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
                    audioFocusRequest = null
                } else {
                    @Suppress("DEPRECATION")
                    am.abandonAudioFocus(null)
                }

                // 3. Reset Audio Mode to NORMAL for standard voice assistant operation
                am.mode = AudioManager.MODE_NORMAL
                am.isSpeakerphoneOn = false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during microphone handoff (toCallAssistant=$toCallAssistant): ${e.message}", e)
        }
    }
}
