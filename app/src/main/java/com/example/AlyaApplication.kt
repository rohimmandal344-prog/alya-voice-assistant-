package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.os.Build
import android.util.Log
import com.example.data.local.AlyaDatabase
import com.example.data.local.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlyaApplication : Application() {

    lateinit var database: AlyaDatabase
        private set

    lateinit var preferencesManager: PreferencesManager
        private set

    lateinit var authManager: com.example.data.auth.AuthManager
        private set

    lateinit var wakeWordManager: com.example.voice.wakeword.WakeWordManager
        private set

    lateinit var repository: com.example.data.AlyaRepository
        private set

    val speechManager: com.example.voice.SpeechRecognitionManager by lazy { com.example.voice.SpeechRecognitionManager(this) }
    val audioDeviceManager: com.example.voice.AudioDeviceManager by lazy { com.example.voice.AudioDeviceManager(this) }
    val sessionManager: com.example.util.SessionManager by lazy {
        com.example.util.SessionManager(
            context = this,
            speechManager = speechManager,
            audioDeviceManager = audioDeviceManager,
            geminiClient = repository.geminiClient,
            wakeWordManager = wakeWordManager
        )
    }

    val powerStateManager: com.example.power.PowerStateManager by lazy {
        com.example.power.PowerStateManager(
            context = this,
            audioDeviceManager = audioDeviceManager,
            speechManager = speechManager,
            wakeWordManager = wakeWordManager
        )
    }

    val pcmAudioPlayer: com.example.voice.audio.PcmAudioTrackPlayer by lazy { com.example.voice.audio.PcmAudioTrackPlayer() }
    val audioCaptureManager: com.example.voice.microphone.AudioCaptureManager by lazy { com.example.voice.microphone.AudioCaptureManager.getInstance(this) }
    val soundEffectManager: com.example.voice.SoundEffectManager by lazy { com.example.voice.SoundEffectManager() }
    val dataStorageManager: com.example.data.DataStorageManager by lazy { com.example.data.DataStorageManager(this) }
    val capabilityManager: com.example.capability.CapabilityManager by lazy { com.example.capability.CapabilityManager.getInstance(this) }
    val voiceTrainingManager: com.example.voice.wakeword.VoiceTrainingManager by lazy { com.example.voice.wakeword.VoiceTrainingManager(this, preferencesManager) }
    val ttsManager: com.example.voice.TextToSpeechManager by lazy { com.example.voice.TextToSpeechManager.getInstance(this) }
    val voiceConversationManager: com.example.voice.VoiceConversationManager by lazy {
        com.example.voice.VoiceConversationManager(
            context = this,
            sessionManager = sessionManager,
            speechManager = speechManager,
            ttsManager = ttsManager,
            geminiClient = repository.geminiClient
        )
    }
    val aiCallManager: com.example.domain.call.AiCallManager by lazy { com.example.domain.call.AiCallManager.getInstance(this, database, ttsManager) }
    val activeListeningMonitor: com.example.voice.bargein.ActiveListeningMonitor by lazy {
        com.example.voice.bargein.ActiveListeningMonitor(
            ttsManager = ttsManager
        )
    }

    val audioRoutingManager: com.example.voice.routing.AudioRoutingManager by lazy { audioDeviceManager.routingManager }
    val alyaAudioManager: com.example.audio.AlyaAudioManager by lazy { com.example.audio.AlyaAudioManager.getInstance(this) }

    val connectivityObserver: com.example.util.ConnectivityObserver by lazy { com.example.util.ConnectivityObserver(this) }
    val billingRepository: com.example.data.billing.BillingRepository by lazy { com.example.data.billing.BillingRepository(this) }

    private val applicationScope = CoroutineScope(Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 1. Core local managers initialized immediately for UI binding and first frame
        database = AlyaDatabase.getDatabase(this)
        preferencesManager = PreferencesManager(this)
        authManager = com.example.data.auth.AuthManager(this)
        wakeWordManager = com.example.voice.wakeword.OpenWakeWordManager(this)
        repository = com.example.data.AlyaRepository(this, database, preferencesManager)

        // 2. High-performance async startup for non-blocking UI entry
        applicationScope.launch {
            // Firebase & Crashlytics: Defer to prevent blocking Main thread during boot
            try {
                // Initialize Firebase core services (Crashlytics, AppCheck if needed)
                // We keep the core initialization but handle missing auth gracefully
                val options = com.google.firebase.FirebaseOptions.fromResource(this@AlyaApplication)
                if (options != null && com.google.firebase.FirebaseApp.getApps(this@AlyaApplication).isEmpty()) {
                    com.google.firebase.FirebaseApp.initializeApp(this@AlyaApplication, options)
                }
                
                // Crashlytics: Real-time error monitoring for production reliability
                com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().apply {
                    setCrashlyticsCollectionEnabled(true)
                    log("AlyaApplication background startup - Crashlytics enabled")
                }
            } catch (e: Exception) {
                Log.w("AlyaApplication", "Firebase/Crashlytics core init bypassed: ${e.message}")
            }

            createNotificationChannels()
            com.example.service.VoiceKeepAliveJobService.scheduleKeepAliveJob(this@AlyaApplication)
            
            // Telephony & Call Assistant monitor startup
            com.example.service.TelephonyService.start(this@AlyaApplication)
            
            // Enforce assistant silence during manual or active telephony calls
            com.example.service.SilenceObserver(this@AlyaApplication).start()

            // Register ProcessLifecycleObserver
            com.example.service.ProcessLifecycleObserver(this@AlyaApplication).initialize()

            val shouldStartVoiceService = preferencesManager.backgroundVoiceMode.value || 
                                         preferencesManager.wakeWordEnabled.value
            
            if (shouldStartVoiceService) {
                com.example.service.WakeWordService.start(
                    this@AlyaApplication,
                    batterySaver = preferencesManager.wakeWordBatterySaver.value
                )
            }
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        Log.d("AlyaApplication", "onTrimMemory called with level: $level")
        if (level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE || level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) {
            powerStateManager.releaseNonActiveHardware("onTrimMemory level $level")
            applicationScope.launch {
                try {
                    // Safe, modern cache directory trimming on Android Q+ without legacy ashmem pinning
                    cacheDir?.listFiles()?.filter { it.isFile && (it.name.startsWith("temp_") || it.name.endsWith(".tmp")) }?.forEach {
                        it.delete()
                    }
                } catch (e: Throwable) { android.util.Log.e("Alya", "Throwable handled", e) }
            }
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        Log.d("AlyaApplication", "onLowMemory triggered: trimming volatile temporary caches")
        applicationScope.launch {
            try {
                cacheDir?.listFiles()?.filter { it.isFile && (it.name.startsWith("temp_") || it.name.endsWith(".tmp")) }?.forEach {
                    it.delete()
                }
            } catch (e: Throwable) { android.util.Log.e("Alya", "Throwable handled", e) }
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val voiceChannel = NotificationChannel(
                VOICE_SERVICE_CHANNEL_ID,
                "Alya Voice Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows active background voice conversation with Alya"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(voiceChannel)

            val bubbleChannel = NotificationChannel(
                BUBBLE_SERVICE_CHANNEL_ID,
                "Alya Floating Bubble",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps Alya wake-word detection & floating overlay active"
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(bubbleChannel)
        }
    }

    companion object {
        const val VOICE_SERVICE_CHANNEL_ID = "alya_voice_service_channel"
        const val BUBBLE_SERVICE_CHANNEL_ID = "alya_floating_bubble_channel"
        lateinit var instance: AlyaApplication
            private set
    }
}
