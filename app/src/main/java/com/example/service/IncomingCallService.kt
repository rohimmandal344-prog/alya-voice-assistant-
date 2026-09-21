package com.example.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.telecom.TelecomManager
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.AlyaApplication
import com.example.MainActivity
import com.example.domain.contacts.ContactManager
import com.example.voice.TextToSpeechManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Call State Enum enforcing strict state transitions and preventing recursive telephony loop bugs.
 */
enum class CallState {
    IDLE,
    RINGING,
    OFFHOOK
}

/**
 * Background service that remains active to:
 * 1. Monitor incoming phone calls with strict CallState tracking.
 * 2. Announce incoming caller name/number via realistic female voice.
 * 3. Listen for voice commands to answer ("answer", "pick up", "uthao", "hello")
 *    or reject/end calls ("decline", "reject", "hang up", "end call", "kato", "cut call").
 * 4. Implements safety timeouts and execution throttling to prevent infinite loops.
 */
class IncomingCallService : Service() {

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var telephonyManager: TelephonyManager? = null
    private var telecomManager: TelecomManager? = null
    private var contactManager: ContactManager? = null
    private var ttsManager: TextToSpeechManager? = null
    private var speechRecognizer: SpeechRecognizer? = null

    private var telephonyCallback: Any? = null
    private var phoneStateListener: PhoneStateListener? = null
    private var phoneStateReceiver: BroadcastReceiver? = null

    // Strict Telephony Call State Tracking via TelephonyStateMachine in TelephonyService
    private val telephonyStateMachine = TelephonyService.stateMachine
    private val currentCallState: CallState get() = telephonyStateMachine.currentState
    private val callSessionId: String? get() = telephonyStateMachine.callSessionId

    private var currentCallerDisplay = ""
    private var isListeningForCallCommand = false
    private var isUserInitiatedCall = false

    // Safety Execution Locks & Cooldowns
    @Volatile
    private var lastCallActionTimestamp = 0L
    private val CALL_ACTION_COOLDOWN_MS = 3000L // 3s cooldown between call control executions
    @Volatile
    private var isProcessingAction = false

    private var retryJob: Job? = null
    private var safetyTimeoutJob: Job? = null
    private var ringingJob: Job? = null
    private var speechRetryCount = 0
    private val MAX_SPEECH_RETRY_ATTEMPTS = 3
    private val VOICE_LISTENING_SAFETY_TIMEOUT_MS = 15000L // 15s max active window per cycle

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "IncomingCallService onCreate")
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        telecomManager = getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
        contactManager = ContactManager(this)
        ttsManager = (application as? com.example.AlyaApplication)?.ttsManager ?: TextToSpeechManager.getInstance(this)

        startForegroundNotification()
        registerPhoneStateMonitoring()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_ANSWER_CALL -> answerCurrentCall()
            ACTION_DECLINE_CALL -> declineCurrentCall()
        }
        return START_STICKY
    }

    private fun startForegroundNotification() {
        createNotificationChannel()

        val notification = buildServiceNotification("Call Assistant Active", "Announces incoming calls & accepts voice commands")

        val hasMicPermission = androidx.core.content.ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (!hasMicPermission) {
            Log.i(TAG, "RECORD_AUDIO permission not granted yet. Operating IncomingCallService as standard background service.")
            return
        }

        val foregroundType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            0
        } else {
            0
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, foregroundType)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            Log.i(TAG, "Successfully started foreground service with type: $foregroundType")
        } catch (e: Exception) {
            Log.e(TAG, "startForeground error: ${e.message}")
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
                Log.i(TAG, "Successfully started foreground service with fallback")
            } catch (ex: Exception) {
                Log.e(TAG, "Fallback startForeground completely failed: ${ex.message}")
            }
        }
    }

    private fun buildServiceNotification(title: String, text: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            301,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, IncomingCallService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            302,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_phone_call)
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        when (currentCallState) {
            CallState.RINGING -> {
                val answerIntent = Intent(this, IncomingCallService::class.java).apply {
                    action = ACTION_ANSWER_CALL
                }
                val answerPendingIntent = PendingIntent.getService(
                    this,
                    303,
                    answerIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val declineIntent = Intent(this, IncomingCallService::class.java).apply {
                    action = ACTION_DECLINE_CALL
                }
                val declinePendingIntent = PendingIntent.getService(
                    this,
                    304,
                    declineIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                builder.addAction(android.R.drawable.sym_action_call, "Answer", answerPendingIntent)
                builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Decline", declinePendingIntent)
            }
            CallState.OFFHOOK -> {
                val declineIntent = Intent(this, IncomingCallService::class.java).apply {
                    action = ACTION_DECLINE_CALL
                }
                val declinePendingIntent = PendingIntent.getService(
                    this,
                    304,
                    declineIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "End Call", declinePendingIntent)
            }
            CallState.IDLE -> {
                builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Assistant", stopPendingIntent)
            }
        }

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Alya Call Assistant",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors and announces incoming calls with voice controls"
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    private fun registerPhoneStateMonitoring() {
        // Use authoritative TelephonyCallback (Android 12+) or PhoneStateListener (Android 10-11)
        // as the single source of truth to eliminate redundant broadcast events and processing lag.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) {
                        when (state) {
                            TelephonyManager.CALL_STATE_RINGING -> updateCallState(CallState.RINGING, "")
                            TelephonyManager.CALL_STATE_OFFHOOK -> updateCallState(CallState.OFFHOOK)
                            TelephonyManager.CALL_STATE_IDLE -> updateCallState(CallState.IDLE)
                        }
                    }
                }
                telephonyManager?.registerTelephonyCallback(mainExecutor, callback)
                telephonyCallback = callback
                Log.i(TAG, "Registered authoritative TelephonyCallback for call state monitoring.")
                return
            } catch (e: SecurityException) {
                Log.w(TAG, "TelephonyCallback requires READ_PHONE_STATE: ${e.message}")
            }
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            @Suppress("DEPRECATION")
            phoneStateListener = object : PhoneStateListener() {
                @Deprecated("Deprecated in Java")
                override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                    when (state) {
                        TelephonyManager.CALL_STATE_RINGING -> updateCallState(CallState.RINGING, phoneNumber ?: "")
                        TelephonyManager.CALL_STATE_OFFHOOK -> updateCallState(CallState.OFFHOOK)
                        TelephonyManager.CALL_STATE_IDLE -> updateCallState(CallState.IDLE)
                    }
                }
            }
            @Suppress("DEPRECATION")
            telephonyManager?.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
            Log.i(TAG, "Registered authoritative PhoneStateListener for call state monitoring.")
        }
    }

    private fun handlePhoneStateChange(stateStr: String?, incomingNumber: String) {
        when (stateStr) {
            TelephonyManager.EXTRA_STATE_RINGING -> updateCallState(CallState.RINGING, incomingNumber)
            TelephonyManager.EXTRA_STATE_OFFHOOK -> updateCallState(CallState.OFFHOOK)
            TelephonyManager.EXTRA_STATE_IDLE -> updateCallState(CallState.IDLE)
        }
    }

    /**
     * Synchronized, thread-safe CallState transition method delegating to TelephonyStateMachine.
     * Enforces mandatory 5-second cooldown between state changes (IDLE, RINGING, OFFHOOK)
     * and rigorous 'callSessionId' tracking to eliminate recursive intent loops and UI freezes.
     */
    private fun updateCallState(newState: CallState, rawNumber: String = "") {
        val result = telephonyStateMachine.transition(newState)
        when (result) {
            is TransitionResult.Throttled -> {
                Log.w(
                    TAG,
                    "[TELEPHONY_COOLDOWN] Throttled rapid state transition to $newState during mandatory 5-second cooldown. Remaining: ${result.remainingCooldownMs}ms"
                )
                if (newState == CallState.RINGING && rawNumber.isNotBlank() && (currentCallerDisplay.isBlank() || currentCallerDisplay == "Unknown Caller")) {
                    val callerName = contactManager?.findCallerNameByNumber(rawNumber) ?: rawNumber
                    currentCallerDisplay = callerName
                    Log.i(TAG, "[TELEPHONY_COOLDOWN] Updated caller name for active ring state: $callerName")
                }
                return
            }
            is TransitionResult.Rejected -> {
                Log.w(TAG, "[TELEPHONY_REJECTED] State transition to $newState rejected: ${result.reason}")
                return
            }
            is TransitionResult.NoChange -> {
                Log.d(TAG, "[TELEPHONY_NO_CHANGE] State already $newState; no transition needed.")
                return
            }
            is TransitionResult.Success -> {
                Log.i(
                    TAG,
                    "[TELEPHONY_TRANSITION] State changed: ${result.previousState} -> ${result.newState} with callSessionId: ${result.sessionId}"
                )
                _isCallActiveFlow.value = (newState != CallState.IDLE)

                when (newState) {
                    CallState.RINGING -> onCallRingingInternal(rawNumber)
                    CallState.OFFHOOK -> onCallOffHookInternal()
                    CallState.IDLE -> onCallIdleInternal()
                }
            }
        }
    }

    private fun onCallRingingInternal(rawNumber: String) {
        speechRetryCount = 0
        ringingJob?.cancel()
        isUserInitiatedCall = false // Incoming call, not user initiated manually

        val savedName = if (rawNumber.isNotBlank()) contactManager?.findCallerNameByNumber(rawNumber) else null
        val callerName = savedName ?: "Unknown Number"
        currentCallerDisplay = callerName

        Log.i(TAG, "Incoming call ringing from: $callerName")

        // Update notification
        val nm = getSystemService(NotificationManager::class.java)
        nm?.notify(NOTIFICATION_ID, buildServiceNotification("Incoming Call", "From: $callerName. Say 'Answer' or 'Decline'."))

        // Use CallHandler for safe microphone handoff during RINGING
        // This ensures background wake-word or other sessions are yielded for call announcement/listening
        CallHandler.getInstance(this).handOffMicrophone(toCallAssistant = true)

        val app = (application as? AlyaApplication)
        val preferredLocale = app?.preferencesManager?.voiceLanguage?.value ?: "en-US"
        val locale = Locale.forLanguageTag(preferredLocale)

        // Announce incoming call via realistic female voice in preferred locale
        ringingJob = scope.launch {
            delay(400) // Small breather for audio focus
            val announcement = if (savedName != null) {
                // Construct a polite phrase in the preferred language
                "Incoming call from $savedName." 
            } else {
                "Incoming call from an unknown number."
            }
            ttsManager?.configureNaturalVoice(locale)
            ttsManager?.speak(announcement)
            // Start listening for voice commands after TTS announcement
            delay(2500) // Give TTS time to complete
            if (currentCallState == CallState.RINGING) {
                startCallVoiceCommandListener(preferredLocale)
            }
        }
    }

    private fun onCallOffHookInternal() {
        speechRetryCount = 0
        ringingJob?.cancel()
        Log.i(TAG, "Call connected / active (OFFHOOK). UserInitiated: $isUserInitiatedCall")

        // If it's a manual user-initiated call (no ringing detected first), stay silent.
        if (isUserInitiatedCall) {
            Log.i(TAG, "Manual call detected. Assistant remaining silent as requested.")
            return
        }

        val nm = getSystemService(NotificationManager::class.java)
        nm?.notify(NOTIFICATION_ID, buildServiceNotification("Active Phone Call", "Alya AI Call Assistant active. Inbound dialogue live."))

        // Pause Alya background Wake-Word engine to release microphone
        try {
            val alyaApp = (applicationContext as? com.example.AlyaApplication)
            alyaApp?.wakeWordManager?.stop()
            alyaApp?.aiCallManager?.startCallSession(currentCallerDisplay, "")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing AI call session: ${e.message}")
        }

        // Start listening for caller dialogue and voice commands
        val app = (application as? AlyaApplication)
        val preferredLocale = app?.preferencesManager?.voiceLanguage?.value ?: "en-US"
        startCallVoiceCommandListener(preferredLocale)
    }

    private fun onCallIdleInternal() {
        speechRetryCount = 0
        currentCallerDisplay = ""
        isUserInitiatedCall = true // Reset for next potential manual call
        Log.i(TAG, "Call ended / Idle")

        ringingJob?.cancel()
        retryJob?.cancel()
        safetyTimeoutJob?.cancel()
        stopCallVoiceCommandListener()
        ttsManager?.stop()

        // Release Call Assistant session and restore microphone to Assistant session
        CallHandler.getInstance(this).handOffMicrophone(toCallAssistant = false)

        val alyaApp = (applicationContext as? com.example.AlyaApplication)
        try {
            alyaApp?.aiCallManager?.endCallSession("Call disconnected")
        } catch (e: Exception) {
            Log.e(TAG, "Error ending AI call session: ${e.message}")
        }

        val nm = getSystemService(NotificationManager::class.java)
        nm?.notify(NOTIFICATION_ID, buildServiceNotification("Call Assistant Active", "Announces incoming calls & accepts voice commands"))
    }

    /**
     * Listens for voice commands ("answer", "decline", "end call", "cut call") with safety timeouts.
     */
    private fun startCallVoiceCommandListener(languageTag: String) {
        if (currentCallState == CallState.IDLE) {
            Log.d(TAG, "Call is IDLE, skipping voice listener start.")
            return
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Record audio permission missing for call voice commands")
            return
        }

        try {
            stopCallVoiceCommandListener()
            if (!SpeechRecognizer.isRecognitionAvailable(this)) {
                Log.w(TAG, "Speech recognition is not available on this device for call commands.")
                return
            }
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                // ... rest of the listener
                override fun onReadyForSpeech(params: Bundle?) {
                    isListeningForCallCommand = true
                    armSafetyTimeout()
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    isListeningForCallCommand = false
                }
                override fun onError(error: Int) {
                    isListeningForCallCommand = false
                    disarmSafetyTimeout()
                    stopCallVoiceCommandListener()

                    // Guard against recursive re-triggering loops
                    if (currentCallState != CallState.IDLE && speechRetryCount < MAX_SPEECH_RETRY_ATTEMPTS) {
                        speechRetryCount++
                        Log.w(TAG, "Call speech recognizer error ($error). Retry attempt $speechRetryCount/$MAX_SPEECH_RETRY_ATTEMPTS")
                        retryJob?.cancel()
                        retryJob = scope.launch {
                            delay(1200) // Safe delay before re-attempting
                            if (currentCallState != CallState.IDLE) {
                                startCallVoiceCommandListener(languageTag)
                            }
                        }
                    } else {
                        Log.i(TAG, "Call speech recognizer stopped (max retries reached or state is IDLE).")
                    }
                }
                override fun onResults(results: Bundle?) {
                    isListeningForCallCommand = false
                    disarmSafetyTimeout()
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    handleVoiceCommand(matches)

                    // Resume listening if still in active RINGING or OFFHOOK state
                    if (currentCallState != CallState.IDLE) {
                        retryJob?.cancel()
                        retryJob = scope.launch {
                            delay(600)
                            if (currentCallState != CallState.IDLE) {
                                startCallVoiceCommandListener(languageTag)
                            }
                        }
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val partialMatches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    handleVoiceCommand(partialMatches)
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag) // Set locale here
            }

            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start speech listener for call: ${e.message}")
            isListeningForCallCommand = false
        }
    }

    private fun armSafetyTimeout() {
        safetyTimeoutJob?.cancel()
        safetyTimeoutJob = scope.launch {
            delay(VOICE_LISTENING_SAFETY_TIMEOUT_MS)
            if (isListeningForCallCommand && currentCallState != CallState.IDLE) {
                Log.w(TAG, "Voice command listener reached safety timeout ($VOICE_LISTENING_SAFETY_TIMEOUT_MS ms). Resetting listener.")
                stopCallVoiceCommandListener()
            }
        }
    }

    private fun disarmSafetyTimeout() {
        safetyTimeoutJob?.cancel()
    }

    private fun handleVoiceCommand(phrases: List<String>?) {
        if (phrases.isNullOrEmpty() || currentCallState == CallState.IDLE) return

        for (phrase in phrases) {
            val lower = phrase.lowercase().trim()
            Log.d(TAG, "Heard call voice command candidate: '$lower'")

            // Answer commands (English & Hindi)
            val isAnswer = lower.contains("answer") || lower.contains("pick up") ||
                    lower.contains("accept") || lower.contains("uthao") || lower.contains("uthalo") ||
                    lower.contains("uthale") || lower.contains("pick") || lower == "hello" || 
                    lower == "yes" || lower.contains("receive") || lower.contains("recive")

            // Decline / End commands (English & Hindi)
            val isDecline = lower.contains("decline") || lower.contains("reject") ||
                    lower.contains("hang up") || lower.contains("end call") || lower.contains("cut call") ||
                    lower.contains("disconnect") || lower.contains("kato") || lower.contains("kat do") ||
                    lower.contains("kat do") || lower.contains("kat de") || lower == "no" || 
                    lower == "dismiss" || lower.contains("cancel")

            if (currentCallState == CallState.RINGING && isAnswer) {
                Log.i(TAG, "Voice command detected: ANSWER")
                answerCurrentCall()
                return
            } else if (currentCallState == CallState.RINGING && isDecline) {
                Log.i(TAG, "Voice command detected: DECLINE")
                declineCurrentCall()
                return
            } else if (currentCallState == CallState.OFFHOOK && isDecline) {
                Log.i(TAG, "Voice command detected: END CALL")
                declineCurrentCall()
                return
            } else if (currentCallState == CallState.OFFHOOK) {
                // Active Inbound Call: Forward caller's natural language speech to AiCallManager
                val alyaApp = (applicationContext as? com.example.AlyaApplication)
                alyaApp?.aiCallManager?.processCallerUtterance(phrase)

                // Also check if user gave a direct assistant command
                val words = lower.split("\\s+".toRegex())
                if (lower.isNotBlank() && words.any { it == "alya" || it == "alia" || it == "seno" }) {
                    Log.i(TAG, "General assistant command detected during call: '$lower'")
                    val cleanCommand = lower.replace(Regex("\\b(alya|alia|seno)\\b", RegexOption.IGNORE_CASE), "").trim()
                    if (cleanCommand.isNotBlank()) {
                        val alyaService = WakeWordService.isRunning
                        if (alyaService) {
                            val intent = Intent(this, WakeWordService::class.java).apply {
                                action = WakeWordService.ACTION_PROCESS_VOICE_COMMAND
                                putExtra("COMMAND", cleanCommand)
                            }
                            startService(intent)
                        }
                    }
                }
            }
        }
    }

    /**
     * Executes call answer with strict timestamp cooldown and double-execution protection lock.
     */
    private fun answerCurrentCall() {
        val now = System.currentTimeMillis()
        if (now - lastCallActionTimestamp < CALL_ACTION_COOLDOWN_MS || isProcessingAction) {
            Log.w(TAG, "Call answer command throttled by safety cooldown lock.")
            return
        }
        lastCallActionTimestamp = now
        isProcessingAction = true

        ttsManager?.stop()
        stopCallVoiceCommandListener()

        try {
            val result = CallHandler.getInstance(this).answerByAssistant()
            if (result.success) {
                Log.i(TAG, "Accepted call via CallHandler: ${result.message}")
            } else {
                simulateHeadsetHook()
            }
            ttsManager?.speak("Call connected.")
        } finally {
            scope.launch {
                delay(1000)
                isProcessingAction = false
            }
        }
    }

    /**
     * Executes call decline/end with strict timestamp cooldown and double-execution protection lock.
     */
    private fun declineCurrentCall() {
        val now = System.currentTimeMillis()
        if (now - lastCallActionTimestamp < CALL_ACTION_COOLDOWN_MS || isProcessingAction) {
            Log.w(TAG, "Call decline/end command throttled by safety cooldown lock.")
            return
        }
        lastCallActionTimestamp = now
        isProcessingAction = true

        ttsManager?.stop()
        stopCallVoiceCommandListener()

        try {
            val result = CallHandler.getInstance(this).rejectCall()
            Log.i(TAG, "Declined/ended call via CallHandler: ${result.message}")
            ttsManager?.speak(if (currentCallState == CallState.RINGING) "Call declined." else "Call ended.")
        } finally {
            scope.launch {
                delay(1500)
                isProcessingAction = false
            }
        }
    }

    private fun simulateHeadsetHook() {
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HEADSETHOOK)
            val upEvent = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_HEADSETHOOK)
            audioManager?.dispatchMediaKeyEvent(downEvent)
            audioManager?.dispatchMediaKeyEvent(upEvent)
        } catch (e: Exception) {
            Log.w(TAG, "Headset hook simulation failed: ${e.message}")
        }
    }

    private fun simulateEndCallKey() {
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENDCALL)
            val upEvent = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENDCALL)
            audioManager?.dispatchMediaKeyEvent(downEvent)
            audioManager?.dispatchMediaKeyEvent(upEvent)
        } catch (e: Exception) {
            Log.w(TAG, "End call simulation failed: ${e.message}")
        }
    }

    private fun stopCallVoiceCommandListener() {
        isListeningForCallCommand = false
        disarmSafetyTimeout()
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) { android.util.Log.e("Alya", "Exception handled", e) }
        speechRecognizer = null
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "IncomingCallService onDestroy")
        retryJob?.cancel()
        safetyTimeoutJob?.cancel()
        stopCallVoiceCommandListener()
        ttsManager?.stop()

        phoneStateReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) { android.util.Log.e("Alya", "Exception handled", e) }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (telephonyCallback as? TelephonyCallback)?.let {
                try {
                    telephonyManager?.unregisterTelephonyCallback(it)
                } catch (e: Exception) { android.util.Log.e("Alya", "Exception handled", e) }
            }
        } else {
            phoneStateListener?.let {
                @Suppress("DEPRECATION")
                telephonyManager?.listen(it, PhoneStateListener.LISTEN_NONE)
            }
        }
    }

    companion object {
        private const val TAG = "IncomingCallService"
        private const val CHANNEL_ID = "alya_call_service_channel"
        private const val NOTIFICATION_ID = 4040

        private val _isCallActiveFlow = kotlinx.coroutines.flow.MutableStateFlow(false)
        val isCallActiveFlow: kotlinx.coroutines.flow.StateFlow<Boolean> = _isCallActiveFlow.asStateFlow()

        val isCallActive: Boolean
            get() = _isCallActiveFlow.value

        val callSessionId: String?
            get() = TelephonyService.callSessionId

        val currentCallState: CallState
            get() = TelephonyService.currentState

        const val ACTION_STOP = "com.example.service.ACTION_STOP_CALL_SERVICE"
        const val ACTION_ANSWER_CALL = "com.example.service.ACTION_ANSWER_CALL"
        const val ACTION_DECLINE_CALL = "com.example.service.ACTION_DECLINE_CALL"

        fun start(context: Context) {
            val intent = Intent(context, IncomingCallService::class.java)
            val hasMicPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.RECORD_AUDIO
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && hasMicPermission) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, IncomingCallService::class.java)
            context.stopService(intent)
        }

        fun answerCall(context: Context) {
            val intent = Intent(context, IncomingCallService::class.java).apply {
                action = ACTION_ANSWER_CALL
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun declineOrEndCall(context: Context) {
            val intent = Intent(context, IncomingCallService::class.java).apply {
                action = ACTION_DECLINE_CALL
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
