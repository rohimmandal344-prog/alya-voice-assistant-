package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.example.AlyaApplication
import com.example.MainActivity
import com.example.voice.wakeword.WakeWordManager
import kotlinx.coroutines.*

/**
 * Persistent Foreground Service maintaining high-priority hardware access to the microphone
 * for instantaneous online and offline wake-word ("Alia" / "Alya" / "Seno") activation.
 */
class WakeWordService : Service(), LifecycleOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private var wakeWordManager: WakeWordManager? = null
    private val wakeLockManager by lazy { WakeLockManager(applicationContext) }
    private var wifiLock: android.net.wifi.WifiManager.WifiLock? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isBatterySaver: Boolean = false
    private var isReceiverRegistered: Boolean = false

    private val screenAndTriggerReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            Log.i(TAG, "Foreground service received broadcast action: $action")
            when (action) {
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                    Log.i(TAG, "Screen ON / User Present detected — verifying WakeLock and active AudioRecord session.")
                    acquireWakeLocks()
                    ensureAudioRecordSessionActive(forceRestart = false)
                }
                Intent.ACTION_SCREEN_OFF -> {
                    Log.i(TAG, "Screen OFF detected — preserving partial WakeLock and continuous background audio capture.")
                    acquireWakeLocks()
                    ensureAudioRecordSessionActive(forceRestart = false)
                }
                ACTION_TRIGGER_VOICE -> {
                    try {
                        WakeUpPopup.show(this@WakeWordService)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to launch overlay on trigger voice action: ${e.message}")
                    }
                }
                ACTION_RELOAD_AUDIO_SESSION -> {
                    Log.i(TAG, "Manual audio session reload requested.")
                    ensureAudioRecordSessionActive(forceRestart = true)
                }
                ACTION_WAKE_WORD_DETECTED -> {
                    val canonicalKeyword = intent.getStringExtra(EXTRA_DETECTED_KEYWORD) ?: "Alya"
                    Log.i(TAG, "Internal signal received: Genuine engine detection for '$canonicalKeyword'. Triggering floating popup UI.")
                    provideHapticFeedback()
                    try {
                        WakeUpPopup.show(this@WakeWordService)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to show WakeUpPopup from internal broadcast: ${e.message}")
                    }
                    val ackSoundEnabled = (application as? AlyaApplication)?.preferencesManager?.wakeWordAckSoundEnabled?.value ?: true
                    if (ackSoundEnabled) {
                        val soundEffects = (application as? AlyaApplication)?.preferencesManager?.soundEffectsEnabled?.value ?: true
                        (application as? AlyaApplication)?.soundEffectManager?.play(com.example.voice.SoundEffectManager.SoundType.WAKE_WORD_ACK, enabled = soundEffects)
                    }
                    notifyWakeWordDetected(canonicalKeyword)
                    SessionArbitrator.onWakeWordDetected(canonicalKeyword)
                }
            }
        }
    }

    private var lastDetectionTime = 0L
    private val SERVICE_DEBOUNCE_MS = 3000L

    private val wakeWordListener: (String) -> Unit = listener@{ keyword ->
        val rawKeyword = keyword.trim()
        val normalized = rawKeyword.lowercase()
        // Strictly filter: Only notify upon successful detection of 'Alia', 'Alya', or 'Seno' from OpenWakeWord engine
        val canonicalKeyword = when (normalized) {
            "alia" -> "Alia"
            "alya" -> "Alya"
            "seno" -> "Seno"
            else -> {
                Log.d(TAG, "Detection ignored: '$rawKeyword' is not one of 'Alia', 'Alya', or 'Seno'.")
                return@listener
            }
        }

        val now = System.currentTimeMillis()
        if (now - lastDetectionTime < SERVICE_DEBOUNCE_MS) {
            Log.d(TAG, "Service-level detection ignored (debounce): $canonicalKeyword")
            return@listener
        }

        val app = application as? AlyaApplication
        val isActivated = app?.preferencesManager?.isWakeUpActivated?.value ?: true
        val isCallActive = com.example.service.TelephonyService.isCallActive || isAudioModeInCall()
        val isLiveVoiceActive = app?.sessionManager?.sessionState?.value != com.example.util.SessionState.IDLE &&
                                app?.sessionManager?.sessionState?.value != com.example.util.SessionState.STOPPED
        val isOverlayShowing = WakeUpPopup.isShowing
        val isSpeechListening = app?.speechManager?.isListening?.value ?: false
        val isTtsSpeaking = app?.ttsManager?.isSpeaking?.value ?: false

        if (!isActivated || isCallActive || isLiveVoiceActive || isOverlayShowing || isSpeechListening || isTtsSpeaking) {
            Log.d(TAG, "Wake-up ignored (active state). Keyword: '$canonicalKeyword'")
        } else {
            lastDetectionTime = now
            Log.i(TAG, "Genuine OpenWakeWord engine detection for '$canonicalKeyword'! Broadcasting internal signal.")
            try {
                val broadcastIntent = Intent(ACTION_WAKE_WORD_DETECTED).apply {
                    setPackage(packageName)
                    putExtra(EXTRA_DETECTED_KEYWORD, canonicalKeyword)
                }
                sendBroadcast(broadcastIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to broadcast wake word detection intent: ${e.message}")
            }
        }
    }

    /**
     * Processes a voice command entirely in the background, allowing Alya to speak
     * and perform actions without opening the main UI.
     */
    fun processVoiceCommandInBackground(command: String) {
        val app = application as AlyaApplication
        serviceScope.launch {
            try {
                Log.i(TAG, "Processing background voice command: $command")
                val repository = app.repository
                val conv = repository.getOrCreateActiveConversation()
                
                val assistantMessage = repository.processUserMessage(
                    conversationId = conv.id,
                    userText = command,
                    isVoiceMode = true
                )
                
                val speechText = if (assistantMessage.toolName == "check_weather" && !assistantMessage.toolResult.isNullOrBlank()) {
                    assistantMessage.toolResult
                } else {
                    assistantMessage.content
                }
                
                app.ttsManager.speak(
                    text = speechText,
                    speechRate = repository.preferences.speechRate.value,
                    speechPitch = repository.preferences.speechPitch.value,
                    locale = java.util.Locale.forLanguageTag(repository.preferences.voiceLanguage.value),
                    persona = repository.preferences.voicePersona.value,
                    onCompletion = {
                        // If not in continuous conversation, we can auto-dismiss after speaking
                        if (!repository.preferences.isContinuousConversationEnabled()) {
                            WakeUpPopup.hide(app)
                        }
                    }
                )
                // Dismiss overlay immediately after command is sent to AI if not speaking
                if (!app.ttsManager.isSpeaking.value) {
                    WakeUpPopup.hide(app)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to process background voice command", e)
            }
        }
    }

    private fun isAudioModeInCall(): Boolean {
        return try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
            val mode = audioManager?.mode ?: android.media.AudioManager.MODE_NORMAL
            mode == android.media.AudioManager.MODE_IN_CALL || mode == android.media.AudioManager.MODE_IN_COMMUNICATION
        } catch (_: Exception) {
            false
        }
    }

    private fun provideHapticFeedback() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    android.os.VibrationEffect.createOneShot(70, android.os.VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(android.os.VibrationEffect.createOneShot(70, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(70)
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Haptic feedback skipped: ${e.message}")
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.i(TAG, "App backgrounded/swiped from recent tasks — re-attaching WakeLock and restarting audio listener session immediately.")
        
        // 1. Immediately re-attach WakeLock so CPU never sleeps during transition
        acquireWakeLocks()

        // 2. Restart and ensure active AudioRecord session immediately
        val app = applicationContext as? AlyaApplication
        val isWakeWordEnabled = app?.preferencesManager?.wakeWordEnabled?.value ?: false
        val isBatterySaver = app?.preferencesManager?.wakeWordBatterySaver?.value ?: true
        if (isWakeWordEnabled) {
            ensureAudioRecordSessionActive(forceRestart = true)
            WakeWordService.start(applicationContext, batterySaver = isBatterySaver)
        }

        // 3. Schedule immediate AlarmManager exact resurrection & JobScheduler
        VoiceKeepAliveAlarmScheduler.scheduleImmediateResurrection(applicationContext, 1500L)
        VoiceKeepAliveAlarmScheduler.scheduleExactKeepAlive(applicationContext)
        VoiceKeepAliveJobService.scheduleExpeditedWakeUp(applicationContext)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED

        isRunning = true
        Log.i(TAG, "WakeWordService onCreate - Lifecycle active. Registering mic ownership & acquiring wake lock.")
        acquireWakeLocks()
        
        wakeWordManager = (application as AlyaApplication).wakeWordManager
        wakeWordManager?.addWakeWordListener(wakeWordListener)

        // Register microphone ownership with AudioSessionManager to coordinate audio hardware access
        com.example.audio.AudioSessionManager.registerSessionOwner(com.example.audio.AudioSessionType.WAKE_WORD) {
            Log.i(TAG, "AudioSessionManager requested relinquishing mic ownership. Suspending wake-word detector.")
            wakeWordManager?.isSuppressed = true
            wakeWordManager?.stop()
        }

        SessionArbitrator.registerCallbacks(
            stopWake = {
                Log.i(TAG, "SessionArbitrator callback: Stopping wake-word detector safely.")
                wakeWordManager?.isSuppressed = true
                wakeWordManager?.stop()
            },
            startAsr = {
                Log.i(TAG, "SessionArbitrator callback: Notifying ASR engine to begin.")
                val app = application as? AlyaApplication
                app?.speechManager?.startListening()
            },
            restartWake = {
                Log.i(TAG, "SessionArbitrator callback: Restarting wake-word detector automatically.")
                ensureAudioRecordSessionActive(forceRestart = true)
            }
        )

        registerScreenAndTriggerReceiver()

        // Dynamically monitor wake word selection changes from settings
        serviceScope.launch {
            val prefs = (application as? AlyaApplication)?.preferencesManager ?: return@launch
            prefs.selectedWakeWord.collect { newWakeWord ->
                Log.i(TAG, "WakeWordService received dynamic wake word update: '$newWakeWord'")
                wakeWordManager?.updateConfiguredKeyword(newWakeWord)
                if (isRunning) {
                    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    notificationManager?.notify(NOTIFICATION_ID, buildNotification())
                }
            }
        }

        // Dynamically monitor AudioSession changes to auto-resume wake word listening when mic is free
        serviceScope.launch {
            com.example.audio.AudioSessionManager.sessionFlow.collect { activeSession ->
                Log.d(TAG, "AudioSessionManager flow update: activeSession = $activeSession")
                if (activeSession == com.example.audio.AudioSessionType.NONE) {
                    Log.i(TAG, "Microphone is free, attempting to auto-resume background wake-word listening.")
                    ensureAudioRecordSessionActive(forceRestart = false)
                }
            }
        }
    }

    private fun registerScreenAndTriggerReceiver() {
        if (isReceiverRegistered) return
        try {
            val filter = android.content.IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(ACTION_TRIGGER_VOICE)
                addAction(ACTION_RELOAD_AUDIO_SESSION)
                addAction(ACTION_WAKE_WORD_DETECTED)
            }
            androidx.core.content.ContextCompat.registerReceiver(
                this,
                screenAndTriggerReceiver,
                filter,
                androidx.core.content.ContextCompat.RECEIVER_EXPORTED
            )
            isReceiverRegistered = true
            Log.i(TAG, "Screen and trigger BroadcastReceiver successfully registered in WakeWordService.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register screenAndTriggerReceiver: ${e.message}")
        }
    }

    private fun unregisterScreenAndTriggerReceiver() {
        if (!isReceiverRegistered) return
        try {
            unregisterReceiver(screenAndTriggerReceiver)
            isReceiverRegistered = false
            Log.i(TAG, "Screen and trigger BroadcastReceiver unregistered.")
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering screenAndTriggerReceiver: ${e.message}")
        }
    }

    /**
     * Verifies that the AudioRecord loop and WakeLock are active, restarting them seamlessly if needed.
     */
    fun ensureAudioRecordSessionActive(forceRestart: Boolean = false) {
        if (!isRunning) return

        val app = application as? AlyaApplication
        val isActivated = app?.preferencesManager?.isWakeUpActivated?.value ?: true
        val isCallActive = com.example.service.TelephonyService.isCallActive || isAudioModeInCall()
        val isCallAssistantActive = com.example.audio.AudioSessionManager.getActiveSession() == com.example.audio.AudioSessionType.CALL_ASSISTANT ||
                                     com.example.audio.AudioSessionManager.getActiveSession() == com.example.audio.AudioSessionType.SPEECH_RECOGNITION
        val isLiveVoiceActive = (app?.sessionManager?.sessionState?.value != com.example.util.SessionState.IDLE &&
                                app?.sessionManager?.sessionState?.value != com.example.util.SessionState.STOPPED) ||
                                isCallAssistantActive
        val isOverlayShowing = WakeUpPopup.isShowing
        val isSpeechListening = app?.speechManager?.isListening?.value ?: false
        val isTtsSpeaking = app?.ttsManager?.isSpeaking?.value ?: false

        if (!isActivated || isCallActive || isLiveVoiceActive || isOverlayShowing || isSpeechListening || isTtsSpeaking) {
            Log.d(TAG, "Suppression condition active. Pausing background wake-word listener and yielding microphone session.")
            wakeWordManager?.isSuppressed = true
            wakeWordManager?.stop()
            com.example.audio.AudioSessionManager.releaseSession(com.example.audio.AudioSessionType.WAKE_WORD)
            return
        }

        wakeWordManager?.isSuppressed = false

        // Verify and refresh WakeLock
        if (!wakeLockManager.isHeld()) {
            acquireWakeLocks()
        }

        val isListening = wakeWordManager?.isListening?.value ?: false
        if (!isListening || forceRestart) {
            Log.i(TAG, "Refreshing active AudioRecord session for background wake-word listening (forced: $forceRestart)...")
            serviceScope.launch {
                try {
                    // Small delay to prevent hammering during rapid OS cycles
                    delay(300)
                    if (!isRunning) return@launch
                    
                    // Request microphone ownership before capturing audio
                    val sessionGranted = com.example.audio.AudioSessionManager.requestSession(com.example.audio.AudioSessionType.WAKE_WORD)
                    if (!sessionGranted) {
                        Log.w(TAG, "AudioSessionManager denied WAKE_WORD microphone ownership. Deferring listener startup.")
                        return@launch
                    }

                    val kw = (application as? AlyaApplication)?.preferencesManager?.selectedWakeWord?.value ?: "ALYA"
                    wakeWordManager?.setBatterySaverEnabled(isBatterySaver)
                    // Only start if not already listening to avoid redundant synchronization cycles
                    if (wakeWordManager?.isListening?.value == false || forceRestart) {
                        wakeWordManager?.start(kw, sensitivity = if (isBatterySaver) 0.5f else 0.6f)
                        Log.i(TAG, "AudioRecord session successfully restored with microphone ownership for keyword '$kw'")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to restart AudioRecord session: ${e.message}")
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForegroundSession()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                isBatterySaver = intent.getBooleanExtra(EXTRA_BATTERY_SAVER, false)
                startForegroundSession()
                
            }
            ACTION_TRIGGER_VOICE -> {
                try {
                    WakeUpPopup.show(this)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to launch overlay on trigger voice action: ${e.message}")
                }
            }
            ACTION_PROCESS_VOICE_COMMAND -> {
                val results = androidx.core.app.RemoteInput.getResultsFromIntent(intent)
                val command = if (results != null) {
                    results.getCharSequence("key_voice_command")?.toString()
                } else {
                    intent.getStringExtra("COMMAND")
                } ?: ""
                
                if (command.isNotBlank()) {
                    processVoiceCommandInBackground(command)
                }
                updateNotification()
            }
            ACTION_SET_BATTERY_SAVER -> {
                val enabled = intent.getBooleanExtra(EXTRA_BATTERY_SAVER, true)
                applyBatterySaverMode(enabled)
            }
            ACTION_UPDATE_WAKE_WORD -> {
                val newKw = intent.getStringExtra(EXTRA_WAKE_WORD) ?: ""
                if (newKw.isNotBlank()) {
                    wakeWordManager?.updateConfiguredKeyword(newKw)
                    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    notificationManager?.notify(NOTIFICATION_ID, buildNotification())
                }
            }
            else -> {
                startForegroundSession()
                
            }
        }
        return START_STICKY
    }

    private fun acquireWakeLocks() {
        try {
            wakeLockManager.acquireHighPriorityLock()
        } catch (e: Exception) {
            Log.w(TAG, "Could not acquire partial WakeLock via WakeLockManager: ${e.message}")
        }

        try {
            if (wifiLock == null) {
                val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
                @Suppress("DEPRECATION")
                val lockMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    android.net.wifi.WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                } else {
                    android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF
                }
                wifiLock = wifiManager?.createWifiLock(lockMode, "AlyaAssistant::VoiceWifiLock")?.apply {
                    setReferenceCounted(false)
                    try {
                        acquire()
                        Log.i(TAG, "WifiLock acquired for continuous assistant network connectivity.")
                    } catch (ex: Exception) {
                        Log.w(TAG, "WifiLock acquire failed: ${ex.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "WifiLock initialization skipped: ${e.message}")
        }
    }

    private fun releaseWakeLocks() {
        try {
            wakeLockManager.releaseAll()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing WakeLock: ${e.message}")
        }

        try {
            if (wifiLock?.isHeld == true) {
                wifiLock?.release()
                Log.i(TAG, "WifiLock released.")
            }
            wifiLock = null
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing WifiLock: ${e.message}")
        }
    }

    private fun applyBatterySaverMode(enabled: Boolean) {
        isBatterySaver = enabled
        isBatterySaverModeActive = enabled
        Log.i(TAG, "Applying background service battery-saver mode: $enabled")
        
        wakeWordManager?.setBatterySaverEnabled(enabled)
        updateNotification()
    }

    private fun startForegroundSession() {
        isRunning = true
        isBatterySaverModeActive = isBatterySaver
        
        val channelId = com.example.AlyaApplication.VOICE_SERVICE_CHANNEL_ID
        com.example.util.CompatUtils.createNotificationChannel(
            this,
            channelId,
            "Alya Voice Assistant",
            "Keeps Alya wake-word detection active for instant offline & online commands"
        )
        
        val notification = buildNotification()

        val hasMicPermission = com.example.util.CompatUtils.hasMicrophonePermission(this)

        val foregroundServiceType = if (com.example.util.CompatUtils.isAndroid14OrAbove) {
            if (hasMicPermission) {
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            }
        } else if (com.example.util.CompatUtils.isAndroid10OrAbove) {
            if (hasMicPermission) {
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            } else {
                0
            }
        } else {
            0
        }

        try {
            androidx.core.app.ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                foregroundServiceType
            )
            Log.i(TAG, "Successfully started foreground service with microphone type: $foregroundServiceType")
        } catch (e: Exception) {
            Log.w(TAG, "Initial ServiceCompat.startForeground with type $foregroundServiceType had exception: ${e.message}. Attempting graceful fallback...")
            try {
                if (com.example.util.CompatUtils.isAndroid14OrAbove) {
                    val fallbackType = if (hasMicPermission) {
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                    } else {
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    }
                    androidx.core.app.ServiceCompat.startForeground(
                        this,
                        NOTIFICATION_ID,
                        notification,
                        fallbackType
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
                Log.i(TAG, "Successfully started foreground service with fallback")
            } catch (ex: Exception) {
                Log.w(TAG, "Foreground start fallback caught: ${ex.message}. Continuing in background mode.")
            }
        }

        if (!hasMicPermission) {
            Log.w(TAG, "RECORD_AUDIO permission not granted yet. Operating in restricted foreground mode without microphone.")
            if (!wakeLockManager.isHeld()) {
                acquireWakeLocks()
            }
            return
        }

        // Re-verify WakeLock
        if (!wakeLockManager.isHeld()) {
            acquireWakeLocks()
        }

        // Start listening with high-priority audio stream
        serviceScope.launch {
            try {
                val activeSession = com.example.audio.AudioSessionManager.getActiveSession()
                if (activeSession == com.example.audio.AudioSessionType.CALL_ASSISTANT || activeSession == com.example.audio.AudioSessionType.SPEECH_RECOGNITION) {
                    Log.i(TAG, "Live Voice or Assistant session active ($activeSession). Yielding microphone from WakeWordService.")
                    wakeWordManager?.isSuppressed = true
                    wakeWordManager?.stop()
                    return@launch
                }
                val kw = (application as? AlyaApplication)?.preferencesManager?.selectedWakeWord?.value ?: "ALYA"
                wakeWordManager?.setBatterySaverEnabled(isBatterySaver)
                wakeWordManager?.start(kw, sensitivity = if (isBatterySaver) 0.5f else 0.6f)
                Log.i(TAG, "High-priority wake-word audio stream active for keyword: $kw (BatterySaver: $isBatterySaver)")
            } catch (e: Exception) {
                Log.e(TAG, "Error starting wake word detector in foreground service: ${e.message}")
            }
        }
    }

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                AlyaApplication.VOICE_SERVICE_CHANNEL_ID,
                "Alya Voice Assistant",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps Alya wake-word detection active for instant offline & online commands"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun updateNotification() {
        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, buildNotification())
        } catch (e: Exception) { android.util.Log.e("Alya", "Exception handled", e) }
    }

    private fun buildNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Tap to Talk / Trigger Assistant overlay directly
        val triggerIntent = Intent(this, WakeWordService::class.java).apply {
            action = ACTION_TRIGGER_VOICE
        }
        val triggerPendingIntent = PendingIntent.getService(
            this,
            2,
            triggerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, WakeWordService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val isCallActive = com.example.service.TelephonyService.isCallActive || isAudioModeInCall()
        val currentKw = (application as? AlyaApplication)?.preferencesManager?.selectedWakeWord?.value ?: "Alya"
        val contentTitle = when {
            isCallActive -> "Alya Assistant • Call Active"
            isBatterySaver -> "Alya Assistant ($currentKw) • Low Power"
            else -> "Alya Voice Assistant ($currentKw)"
        }
        val contentText = when {
            isCallActive -> "Paused during call — tap to resume manually."
            isBatterySaver -> "Say '$currentKw', 'Alia', 'Alya', or 'Seno' • Power Saving"
            else -> "Listening for '$currentKw' • Ready"
        }

        val remoteInput = androidx.core.app.RemoteInput.Builder("key_voice_command")
            .setLabel("Type command (e.g., Turn on wifi)...")
            .build()

        val replyIntent = Intent(this, WakeWordService::class.java).apply {
            action = ACTION_PROCESS_VOICE_COMMAND
        }
        val replyPendingIntent = PendingIntent.getService(
            this,
            3,
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val commandAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_send,
            "Type Command",
            replyPendingIntent
        ).addRemoteInput(remoteInput).build()

        return NotificationCompat.Builder(this, AlyaApplication.VOICE_SERVICE_CHANNEL_ID)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setSubText("Always Listening")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_btn_speak_now, "Talk Now", triggerPendingIntent)
            .addAction(commandAction)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .build()
    }

    private fun notifyWakeWordDetected(keyword: String) {
        (application as? AlyaApplication)?.powerStateManager?.notifyUserActivity()
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(this, AlyaApplication.VOICE_SERVICE_CHANNEL_ID)
            .setContentTitle("Alya heard '$keyword'")
            .setContentText("Assistant is listening for your command...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        notificationManager.notify(NOTIFICATION_ID, notif)
    }

    private var isExplicitStopRequested = false

    private fun stopForegroundSession() {
        isExplicitStopRequested = true
        isRunning = false
        try {
            VoiceKeepAliveAlarmScheduler.cancelAllAlarms(this)
            VoiceKeepAliveJobService.cancelAllJobs(this)
            wakeWordManager?.stop()
            com.example.audio.AudioSessionManager.releaseSession(com.example.audio.AudioSessionType.WAKE_WORD)
        } catch (e: Exception) { android.util.Log.e("Alya", "Exception handled", e) }
        releaseWakeLocks()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        super.onDestroy()
        isRunning = false
        unregisterScreenAndTriggerReceiver()
        try {
            com.example.audio.AudioSessionManager.releaseSession(com.example.audio.AudioSessionType.WAKE_WORD)
            com.example.audio.AudioSessionManager.unregisterSessionOwner(com.example.audio.AudioSessionType.WAKE_WORD)
            (application as? AlyaApplication)?.powerStateManager?.releaseNonActiveHardware("WakeWordService destroyed")
            wakeWordManager?.removeWakeWordListener(wakeWordListener)
            wakeWordManager?.stop()
        } catch (e: Exception) { android.util.Log.e("Alya", "Exception handled", e) }
        releaseWakeLocks()
        serviceScope.cancel()
        wakeWordManager = null
    }

    companion object {
        private const val TAG = "WakeWordService"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.example.service.ACTION_START"
        const val ACTION_STOP = "com.example.service.ACTION_STOP"
        const val ACTION_TRIGGER_VOICE = "com.example.service.ACTION_TRIGGER_VOICE"
        const val ACTION_RELOAD_AUDIO_SESSION = "com.example.service.ACTION_RELOAD_AUDIO_SESSION"
        const val ACTION_SET_BATTERY_SAVER = "com.example.service.ACTION_SET_BATTERY_SAVER"
        const val ACTION_PROCESS_VOICE_COMMAND = "com.example.service.ACTION_PROCESS_VOICE_COMMAND"
        const val ACTION_UPDATE_WAKE_WORD = "com.example.service.ACTION_UPDATE_WAKE_WORD"
        const val ACTION_WAKE_WORD_DETECTED = "com.example.service.ACTION_WAKE_WORD_DETECTED"
        const val EXTRA_OPEN_VOICE_MODE = "extra_open_voice_mode"
        const val EXTRA_BATTERY_SAVER = "extra_battery_saver"
        const val EXTRA_WAKE_WORD = "extra_wake_word"
        const val EXTRA_DETECTED_KEYWORD = "extra_detected_keyword"

        var isRunning: Boolean = false
            private set

        var isBatterySaverModeActive: Boolean = false
            private set

        fun start(context: Context, batterySaver: Boolean = false) {
            val intent = Intent(context, WakeWordService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_BATTERY_SAVER, batterySaver)
            }
            val hasMicPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.RECORD_AUDIO
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Direct start of WakeWordService restricted by background limits: ${e.message}. Scheduling expedited system wake-up Job instead.")
                try {
                    VoiceKeepAliveJobService.scheduleExpeditedWakeUp(context)
                } catch (ex: Exception) {
                    Log.e(TAG, "Failed to schedule expedited background job: ${ex.message}")
                }
            }
        }

        fun setBatterySaver(context: Context, enabled: Boolean) {
            val intent = Intent(context, WakeWordService::class.java).apply {
                action = ACTION_SET_BATTERY_SAVER
                putExtra(EXTRA_BATTERY_SAVER, enabled)
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update battery saver mode: ${e.message}")
            }
        }

        fun notifyWakeWordConfigChanged(context: Context, newKeyword: String) {
            val intent = Intent(context, WakeWordService::class.java).apply {
                action = ACTION_UPDATE_WAKE_WORD
                putExtra(EXTRA_WAKE_WORD, newKeyword)
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Log.d(TAG, "Notice sending wake word update to service: ${e.message}")
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, WakeWordService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop WakeWordService: ${e.message}")
            }
        }
    }
}
