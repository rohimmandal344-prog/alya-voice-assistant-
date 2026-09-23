package com.example.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.AlyaApplication
import com.example.data.AlyaRepository
import com.example.data.local.entity.ConversationEntity
import com.example.data.local.entity.MemoryEntity
import com.example.data.local.entity.MessageEntity
import com.example.service.WakeWordService
import com.example.update.AppUpdateInfo
import com.example.update.UpdateStatus
import android.content.Context
import com.example.voice.AudioDeviceManager
import com.example.voice.SpeechRecognitionManager
import com.example.voice.TextToSpeechManager
import com.example.voice.wakeword.WakeWordKeyword
import com.example.data.bridge.AlyaBridgeWebSocketClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import java.util.Locale
import java.util.UUID

sealed class AssistantState {
    object Idle : AssistantState()
    object Listening : AssistantState()
    data class LiveCall(val isConnected: Boolean) : AssistantState()
    data class Offline(val networkType: String) : AssistantState()
    data class Error(val message: String) : AssistantState()
}

enum class AssistantScreen {
    CHAT,
    VOICE_MODE,
    SETTINGS,
    ACCOUNT_SECURITY,
    MEMORY,
    HISTORY,
    COMMAND_HISTORY,
    COMPATIBILITY,
    TOOLS_CATALOG,
    TASKS,
    DEVICE_LINK,
    APP_SHARE,
    PERMISSIONS_CAPABILITIES,
    DIAGNOSTICS,
    CALL_TRANSCRIPTS,
    CALL_HISTORY
}

class AlyaViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as AlyaApplication
    val context = app
    val repository = app.repository
    val authManager = app.authManager
    val aiCallManager = app.aiCallManager

    // Voice & Audio Managers
    val alyaAudioManager = app.alyaAudioManager
    val speechManager = app.speechManager
    val ttsManager = app.ttsManager
    val audioDeviceManager = app.audioDeviceManager
    val soundEffectManager = com.example.voice.SoundEffectManager()
    val updateManager = repository.updateManager
    val versionCheckManager = repository.versionCheckManager
    val appSharingManager = repository.appSharingManager
    val wakeWordManager = app.wakeWordManager
    val offlineSyncManager = com.example.data.sync.OfflineSyncManager(app, repository.database, repository.preferences)
    val cacheManager = com.example.data.cache.AppCacheManager(app)
    val sessionManager = app.sessionManager
    val conversationalFeedbackModule = com.example.voice.audio.ConversationalFeedbackModule(app, ttsManager, repository.preferences)

    // Bridge Mode (Python Server + EdgeTTS) for ultra-low latency & natural warm human voice
    val alyaBridgeClient = AlyaBridgeWebSocketClient(app)
    val isBridgeModeEnabled: StateFlow<Boolean> = repository.preferences.bridgeModeEnabled

    // Gemini Multimodal Live API & PCM Streaming Audio Engine
    val geminiLiveClient = com.example.data.ai.GeminiLiveWebSocketClient()
    val pcmAudioPlayer = com.example.voice.audio.PcmAudioTrackPlayer(sampleRate = 24000)
    private val _liveAssistantTranscript = MutableStateFlow("")
    val liveAssistantTranscript: StateFlow<String> = _liveAssistantTranscript.asStateFlow()
    private val _liveMicRmsDb = MutableStateFlow(-2.0f)
    val liveMicRmsDb: StateFlow<Float> = _liveMicRmsDb.asStateFlow()

    val updateStatus: StateFlow<UpdateStatus> = updateManager.updateStatus
    val latestUpdateInfo: StateFlow<AppUpdateInfo?> = updateManager.latestUpdateInfo
    val showChangelog: StateFlow<Boolean> = versionCheckManager.showChangelog
    val syncStatus: StateFlow<com.example.data.sync.SyncStatus> = offlineSyncManager.syncStatus
    val isOnline: StateFlow<Boolean> = sessionManager.isNetworkAvailable
    val cacheStats: StateFlow<com.example.data.cache.CacheStats> = cacheManager.cacheStats
    val isWakeWordActive: StateFlow<Boolean> = wakeWordManager.isListening

    private val _showUpdatePrompt = MutableStateFlow(false)
    val showUpdatePrompt = _showUpdatePrompt.asStateFlow()

    // Real-time SessionManager State Telemetry for UI Dashboard
    val sessionState = sessionManager.sessionState
    val isMicActive = sessionManager.isMicActive
    val isAudioFocusHeld = sessionManager.isAudioFocusHeld
    val isNetworkAvailable = sessionManager.isNetworkAvailable
    val isBatterySaverActive = sessionManager.isBatterySaverActive
    val offlineCommandLogs = repository.database.offlineCommandLogDao().getAllLogsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val callSessions = repository.database.callSessionDao().getAllCallSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteCallSession(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.database.callSessionDao().deleteCallSession(id)
            repository.database.callSessionDao().deleteTranscriptsForSession(id)
        }
    }

    val errorRecoveryHandler = com.example.voice.error.ErrorRecoveryHandler.getInstance(app)
    val offlineLanguageManager = com.example.voice.lang.OfflineLanguageManager.getInstance(app)

    fun clearAllCallSessions() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.database.callSessionDao().clearAllCallSessions()
            repository.database.callSessionDao().clearAllTranscripts()
        }
    }

    suspend fun getTranscriptsForSession(sessionId: String): List<com.example.data.local.entity.CallTranscriptEntryEntity> {
        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            repository.database.callSessionDao().getTranscriptsForSessionSync(sessionId)
        }
    }

    // PowerStateManager monitoring & hardware release telemetry
    val powerStateManager = (app as? AlyaApplication)?.powerStateManager
        ?: com.example.power.PowerStateManager(app, audioDeviceManager, speechManager, wakeWordManager)
    val powerState = powerStateManager.powerState
    val isHardwareReleased = powerStateManager.isHardwareReleased
    val hardwareReleaseCount = powerStateManager.hardwareReleaseCount

    fun releaseHardwareResourcesManually(reason: String = "User requested power conservation") {
        powerStateManager.releaseNonActiveHardware(reason)
    }
    
    // Telephony & Call Controls
    val callState = com.example.service.TelephonyService.callStateFlow
    val isCallActive = com.example.service.TelephonyService.isCallActiveFlow
    private val contactManager = com.example.domain.contacts.ContactManager(app)

    fun sendUserMessage(text: String) = sendMessage(text)
    fun startNewConversation() = createNewConversation()

    fun toggleBatterySaver(enabled: Boolean) {
        viewModelScope.launch {
            repository.preferences.setWakeWordBatterySaver(enabled)
            sessionManager.setBatterySaver(enabled)
            com.example.service.WakeWordService.setBatterySaver(app, enabled)
        }
    }

    fun toggleWakeWordEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.preferences.setWakeWordEnabled(enabled)
            if (enabled) {
                val kw = repository.preferences.selectedWakeWord.value
                val sens = repository.preferences.wakeWordSensitivity.value
                wakeWordManager.start(kw, sens)
            } else {
                wakeWordManager.stop()
            }
        }
    }

    fun toggleBridgeMode(enabled: Boolean) {
        viewModelScope.launch {
            repository.preferences.setBridgeModeEnabled(enabled)
            if (enabled) {
                val serverUrl = repository.preferences.bridgeServerUrl.value.ifBlank { "ws://10.0.2.2:8000/ws/chat" }
                alyaBridgeClient.connect(serverUrl)
                // If we're in voice mode, stop Gemini Live to avoid conflicts
                if (_isVoiceMode.value) {
                    geminiLiveClient.disconnect()
                }
            } else {
                alyaBridgeClient.disconnect()
            }
        }
    }

    val capabilityManager = app.capabilityManager
    val capabilities: StateFlow<List<com.example.capability.CapabilityItem>> = capabilityManager.capabilities
    val healthOverview: StateFlow<com.example.capability.SystemHealthOverview> = capabilityManager.healthOverview
    val isCapabilitiesChecking: StateFlow<Boolean> = capabilityManager.isChecking

    fun refreshCapabilities() {
        capabilityManager.refreshCapabilities()
    }

    val selectedWakeWord: StateFlow<String> = repository.preferences.selectedWakeWord
    val wakeWordSensitivity: StateFlow<Float> = repository.preferences.wakeWordSensitivity
    val wakeWordAckSoundEnabled: StateFlow<Boolean> = repository.preferences.wakeWordAckSoundEnabled

    fun setWakeWordAckSoundEnabled(enabled: Boolean) {
        repository.preferences.setWakeWordAckSoundEnabled(enabled)
    }

    fun setSelectedWakeWord(kw: String) {
        val trimmed = kw.trim()
        if (trimmed.isNotBlank()) {
            repository.preferences.setSelectedWakeWord(trimmed)
            wakeWordManager.updateConfiguredKeyword(trimmed)
            try {
                com.example.service.WakeWordService.notifyWakeWordConfigChanged(app, trimmed)
            } catch (e: Exception) {
                Log.d("AlyaViewModel", "Notice notifying voice service of wake-word change: ${e.message}")
            }
        }
    }

    fun getActiveWakeWordTriggers(): Set<String> {
        val configured = selectedWakeWord.value.trim().lowercase()
        val predefined = com.example.voice.wakeword.PREDEFINED_WAKE_WORDS.map { it.displayName.lowercase() }
        return (listOf("alia", "alya", "seno") + predefined + listOf(configured)).filter { it.isNotBlank() }.toSet()
    }

    fun setWakeWordSensitivity(sens: Float) {
        repository.preferences.setWakeWordSensitivity(sens)
    }

    val isWakeUpActivated: StateFlow<Boolean> = repository.preferences.isWakeUpActivated
    val isVoiceProfileSet: StateFlow<Boolean> = repository.preferences.isVoiceProfileSet
    val voiceProfileCreatedAt: StateFlow<Long> = repository.preferences.voiceProfileCreatedAt
    val voiceProfileSampleCount: StateFlow<Int> = repository.preferences.voiceProfileSampleCount
    val partialSpeechResult: StateFlow<String> = speechManager.partialResult

    val onlyOwnerVoiceWakes: StateFlow<Boolean> = repository.preferences.onlyOwnerVoiceWakes
    val pauseDuringCallsAndRecording: StateFlow<Boolean> = repository.preferences.pauseDuringCallsAndRecording
    val neverWakeDuringPlayback: StateFlow<Boolean> = repository.preferences.neverWakeDuringPlayback

    // Voice Training & Enrollment Management
    val voiceTrainingManager = app.voiceTrainingManager
    val voiceTrainingSummary = voiceTrainingManager.summaryState
    val pendingDeleteConfirmation = voiceTrainingManager.pendingDeleteConfirmation

    fun recordVoiceTrainingSample(triggerWord: String, environment: String): String {
        return voiceTrainingManager.recordVoiceSample(triggerWord, environment)
    }

    fun requestDeleteAllVoiceRecordings(): String {
        return voiceTrainingManager.requestDeleteAllRecordings()
    }

    fun confirmDeleteAllVoiceRecordings(): String {
        return voiceTrainingManager.confirmDeleteAllRecordings()
    }

    fun cancelDeleteVoiceRecordings(): String {
        return voiceTrainingManager.cancelDelete()
    }

    // Custom Wake-Word Recording & Persistence
    private val _isRecordingCustomWakeWord = MutableStateFlow(false)
    val isRecordingCustomWakeWord: StateFlow<Boolean> = _isRecordingCustomWakeWord.asStateFlow()

    private val _customWakeWordRecordDuration = MutableStateFlow(0)
    val customWakeWordRecordDuration: StateFlow<Int> = _customWakeWordRecordDuration.asStateFlow()

    val allCustomWakeWords: StateFlow<List<com.example.data.local.entity.CustomWakeWordEntity>> = repository.allCustomWakeWords
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeCustomWakeWord: StateFlow<com.example.data.local.entity.CustomWakeWordEntity?> = repository.activeCustomWakeWordFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private var mediaRecorder: android.media.MediaRecorder? = null
    private var customWakeWordFile: java.io.File? = null
    private var recordingTimerJob: kotlinx.coroutines.Job? = null

    fun startRecordingCustomWakeWord() {
        try {
            val file = java.io.File(app.filesDir, "custom_wakeword_${System.currentTimeMillis()}.3gp")
            customWakeWordFile = file

            val recorder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                android.media.MediaRecorder(app)
            } else {
                @Suppress("DEPRECATION")
                android.media.MediaRecorder()
            }

            recorder.apply {
                setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
                setOutputFormat(android.media.MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            mediaRecorder = recorder
            _isRecordingCustomWakeWord.value = true
            _customWakeWordRecordDuration.value = 0
            recordingTimerJob = viewModelScope.launch {
                while (true) {
                    kotlinx.coroutines.delay(1000)
                    _customWakeWordRecordDuration.value += 1
                }
            }
        } catch (e: Exception) {
            Log.e("AlyaViewModel", "Failed to start custom wake word recording: ${e.message}")
        }
    }

    fun stopRecordingCustomWakeWord(word: String, makeActive: Boolean = true) {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            Log.e("AlyaViewModel", "Failed to stop media recorder: ${e.message}")
        } finally {
            mediaRecorder = null
            _isRecordingCustomWakeWord.value = false
            recordingTimerJob?.cancel()
            recordingTimerJob = null
        }

        val file = customWakeWordFile
        if (file != null && file.exists() && file.length() > 0) {
            viewModelScope.launch {
                repository.saveCustomWakeWord(
                    word = word.trim(),
                    audioFilePath = file.absolutePath,
                    makeActive = makeActive
                )
                if (makeActive) {
                    setSelectedWakeWord(word.trim())
                }
            }
        }
    }

    fun cancelRecordingCustomWakeWord() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            // Ignore
        } finally {
            mediaRecorder = null
            _isRecordingCustomWakeWord.value = false
            recordingTimerJob?.cancel()
            recordingTimerJob = null
        }
        customWakeWordFile?.delete()
        customWakeWordFile = null
    }

    fun setCustomWakeWordActive(id: Long, word: String) {
        viewModelScope.launch {
            repository.setCustomWakeWordActive(id)
            setSelectedWakeWord(word)
        }
    }

    fun deleteCustomWakeWord(id: Long, audioFilePath: String) {
        viewModelScope.launch {
            repository.deleteCustomWakeWord(id)
            try {
                java.io.File(audioFilePath).delete()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    fun setOnlyOwnerVoiceWakes(enabled: Boolean) {
        repository.preferences.setOnlyOwnerVoiceWakes(enabled)
    }

    fun setPauseDuringCallsAndRecording(enabled: Boolean) {
        repository.preferences.setPauseDuringCallsAndRecording(enabled)
    }

    fun setNeverWakeDuringPlayback(enabled: Boolean) {
        repository.preferences.setNeverWakeDuringPlayback(enabled)
    }

    fun testWakeWordSpeakerVerification(onResult: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val verificationManager = com.example.voice.wakeword.SpeakerVerificationManager(app, repository.preferences)
                // Simulate listening frame for live verification test
                val dummyPcm = ShortArray(2048) { (Math.random() * 2000 - 1000).toInt().toShort() }
                val res = verificationManager.verifySpeaker(dummyPcm)
                val kw = repository.preferences.selectedWakeWord.value
                val message = if (res.isMatch) {
                    "✅ Detected your voice! (Keyword '$kw', similarity: ${(res.similarity * 100).toInt()}%)"
                } else {
                    "❌ Not your voice or phrase unrecognized. (Similarity: ${(res.similarity * 100).toInt()}%)"
                }
                onResult(message)
            } catch (e: Exception) {
                onResult("❌ Verification error: ${e.message}")
            }
        }
    }

    private val _showWakeUpActivationDialog = MutableStateFlow(false)
    val showWakeUpActivationDialog: StateFlow<Boolean> = _showWakeUpActivationDialog.asStateFlow()

    fun openWakeUpActivationDialog() {
        _showWakeUpActivationDialog.value = true
    }

    fun dismissWakeUpActivationDialog() {
        _showWakeUpActivationDialog.value = false
    }

    fun completeVoiceProfileEnrollment(step: Int) {
        viewModelScope.launch {
            val currentStep = repository.preferences.voiceEnrollmentStep.value
            
            if (step != currentStep + 1) {
                Log.w("AlyaViewModel", "Invalid enrollment step sequence: current=$currentStep, attempted=$step")
                return@launch
            }

            if (step < 3) {
                repository.preferences.setVoiceEnrollmentStep(step)
                // Trigger next UI step for sample capture
                Log.i("AlyaViewModel", "Voice enrollment step $step complete.")
            } else {
                repository.preferences.setVoiceProfileCompleted(sampleCount = 3)
                repository.preferences.setVoiceEnrollmentStep(0) // Reset
                
                val kw = repository.preferences.selectedWakeWord.value
                val sens = repository.preferences.wakeWordSensitivity.value
                wakeWordManager.start(kw, sens)
                com.example.service.WakeWordService.start(app)
                capabilityManager.refreshCapabilities()
                _showWakeUpActivationDialog.value = false
                val soundEffects = repository.preferences.soundEffectsEnabled.value
                soundEffectManager.play(com.example.voice.SoundEffectManager.SoundType.TASK_SUCCESS, enabled = soundEffects)
                speakResponse("Your voice profile is securely set. I will only respond to your voice from now on.")
            }
        }
    }

    fun deleteUserVoiceProfile() {
        viewModelScope.launch {
            repository.preferences.deleteUserVoiceProfile()
            wakeWordManager.stop()
            capabilityManager.refreshCapabilities()
            val soundEffects = repository.preferences.soundEffectsEnabled.value
            soundEffectManager.play(com.example.voice.SoundEffectManager.SoundType.VOICE_END, enabled = soundEffects)
            speakResponse("Your voice profile has been deleted and wake-up is now deactivated.")
        }
    }

    fun deactivateWakeUp() {
        viewModelScope.launch {
            repository.preferences.deactivateWakeUp()
            wakeWordManager.stop()
            capabilityManager.refreshCapabilities()
            val soundEffects = repository.preferences.soundEffectsEnabled.value
            soundEffectManager.play(com.example.voice.SoundEffectManager.SoundType.VOICE_END, enabled = soundEffects)
        }
    }

    fun activateWakeUp() {
        viewModelScope.launch {
            repository.preferences.activateWakeUp()
            val kw = repository.preferences.selectedWakeWord.value
            val sens = repository.preferences.wakeWordSensitivity.value
            wakeWordManager.start(kw, sens)
            com.example.service.WakeWordService.start(app)
            capabilityManager.refreshCapabilities()
            val soundEffects = repository.preferences.soundEffectsEnabled.value
            soundEffectManager.play(com.example.voice.SoundEffectManager.SoundType.VOICE_START, enabled = soundEffects)
        }
    }

    // Dynamic Language Locale Manager
    data class SupportedLocaleInfo(
        val tag: String,
        val displayName: String,
        val nativeName: String,
        val flagEmoji: String
    )

    val supportedLocales = listOf(
        SupportedLocaleInfo("en-US", "English (US)", "English", "🇺🇸"),
        SupportedLocaleInfo("en-GB", "English (UK)", "English", "🇬🇧"),
        SupportedLocaleInfo("en-IN", "English (India)", "English", "🇮🇳"),
        SupportedLocaleInfo("hi-IN", "Hindi", "हिन्दी", "🇮🇳"),
        SupportedLocaleInfo("bn-IN", "Bengali", "বাংলা", "🇮🇳"),
        SupportedLocaleInfo("ta-IN", "Tamil", "தமிழ்", "🇮🇳"),
        SupportedLocaleInfo("te-IN", "Telugu", "తెలుగు", "🇮🇳"),
        SupportedLocaleInfo("ml-IN", "Malayalam", "മലയാളം", "🇮🇳"),
        SupportedLocaleInfo("as-IN", "Assamese", "অসমীয়া", "🇮🇳"),
        SupportedLocaleInfo("th-TH", "Thai", "ไทย", "🇹🇭"),
        SupportedLocaleInfo("ne-NP", "Nepali", "नेपाली", "🇳🇵"),
        SupportedLocaleInfo("pa-IN", "Punjabi", "ਪੰਜਾਬੀ", "🇮🇳"),
        SupportedLocaleInfo("gu-IN", "Gujarati", "ગુજરાતી", "🇮🇳"),
        SupportedLocaleInfo("es-ES", "Spanish", "Español", "🇪🇸"),
        SupportedLocaleInfo("ja-JP", "Japanese", "日本語", "🇯🇵"),
        SupportedLocaleInfo("ru-RU", "Russian", "Русский", "🇷🇺"),
        SupportedLocaleInfo("fr-FR", "French", "Français", "🇫🇷"),
        SupportedLocaleInfo("de-DE", "German", "Deutsch", "🇩🇪"),
        SupportedLocaleInfo("ar-SA", "Arabic", "العربية", "🇸🇦"),
        SupportedLocaleInfo("id-ID", "Indonesian", "Bahasa Indonesia", "🇮🇩"),
        SupportedLocaleInfo("pt-BR", "Portuguese", "Português", "🇧🇷"),
        SupportedLocaleInfo("it-IT", "Italian", "Italiano", "🇮🇹"),
        SupportedLocaleInfo("zh-CN", "Chinese", "中文", "🇨🇳"),
        SupportedLocaleInfo("ko-KR", "Korean", "한국어", "🇰🇷"),
        SupportedLocaleInfo("tr-TR", "Turkish", "Türkçe", "🇹🇷")
    )

    private val _currentLanguageLocale = MutableStateFlow(repository.preferences.voiceLanguage.value)
    val currentLanguageLocale: StateFlow<String> = _currentLanguageLocale.asStateFlow()

    fun setLanguageLocale(languageTag: String) {
        val cleanTag = languageTag.trim()
        if (cleanTag.isBlank()) return
        Log.i("AlyaViewModel", "Dynamic language locale changed to: $cleanTag")
        _currentLanguageLocale.value = cleanTag
        viewModelScope.launch {
            repository.preferences.setVoiceLanguage(cleanTag)
            speechManager.updateLanguageLocale(cleanTag, rebindIfListening = speechManager.isListening.value)
            try {
                val loc = Locale.forLanguageTag(cleanTag)
                ttsManager.configureNaturalVoice(loc)
            } catch (e: Exception) { android.util.Log.e("Alya", "Exception handled", e) }
        }
    }

    // Sidebar drawer visibility state
    private val _isSidebarOpen = MutableStateFlow(false)
    val isSidebarOpen: StateFlow<Boolean> = _isSidebarOpen.asStateFlow()

    fun toggleSidebar(open: Boolean? = null) {
        _isSidebarOpen.value = open ?: !_isSidebarOpen.value
    }

    fun clearAppCache() {
        viewModelScope.launch {
            cacheManager.clearAllCache()
            ttsManager.diskCache.clearCache()
        }
    }

    fun triggerManualOfflineSync() {
        viewModelScope.launch {
            offlineSyncManager.triggerSync()
        }
    }

    // Navigation & UI States
    private val _currentScreen = MutableStateFlow(AssistantScreen.CHAT)
    val currentScreen: StateFlow<AssistantScreen> = _currentScreen.asStateFlow()

    private val _currentConversationId = MutableStateFlow<String?>(null)
    val currentConversationId: StateFlow<String?> = _currentConversationId.asStateFlow()

    val conversations: StateFlow<List<ConversationEntity>> = repository.allConversations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val memories: StateFlow<List<MemoryEntity>> = repository.allMemories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val tasks: StateFlow<List<com.example.data.local.entity.ScheduledTaskEntity>> = repository.allTasks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val deviceLinkManager = repository.deviceLinkManager
    val linkedDevices: StateFlow<List<com.example.data.local.entity.LinkedDeviceEntity>> = repository.allLinkedDevices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isRateLimited: StateFlow<Boolean> = repository.geminiClient.isRateLimited
    val rateLimitSecondsRemaining: StateFlow<Long> = repository.geminiClient.rateLimitSecondsRemaining

    val messages: StateFlow<List<MessageEntity>> = _currentConversationId
        .flatMapLatest { convId ->
            if (convId != null) {
                repository.getMessagesForConversation(convId)
            } else {
                MutableStateFlow(emptyList())
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Voice & Assistant status
    private val _isThinking = MutableStateFlow(false)
    val isThinking: StateFlow<Boolean> = _isThinking.asStateFlow()

    private var isListeningTriggeredByWakeWord = false

    // Bottom Listening Pop-up State
    private val _isListeningPopupVisible = MutableStateFlow(false)
    val isListeningPopupVisible: StateFlow<Boolean> = _isListeningPopupVisible.asStateFlow()

    private val _detectedWakeWord = MutableStateFlow("Alia")
    val detectedWakeWord: StateFlow<String> = _detectedWakeWord.asStateFlow()

    private val _textInputPrefill = MutableStateFlow<String?>(null)
    val textInputPrefill: StateFlow<String?> = _textInputPrefill.asStateFlow()

    fun showListeningPopup(wakeWord: String = "Alia") {
        _detectedWakeWord.value = wakeWord
        _isListeningPopupVisible.value = true
        startListening()
    }

    fun dismissListeningPopup() {
        _isListeningPopupVisible.value = false
        if (speechManager.isListening.value && !_isVoiceMode.value) {
            speechManager.stopListening()
        }
    }

    fun sendListeningAsText() {
        val currentPartial = speechManager.partialResult.value.trim()
        _isListeningPopupVisible.value = false
        speechManager.stopListening()
        if (currentPartial.isNotBlank()) {
            _textInputPrefill.value = currentPartial
        }
    }

    fun clearTextInputPrefill() {
        _textInputPrefill.value = null
    }

    private var lastBackchannelTime = 0L
    private var hasBackchannelTriggeredThisTurn = false
    private var isBackchannelPlaying = false

    private val _manualQualityOverride = MutableStateFlow<com.example.ui.components.CallConnectionQuality?>(null)

    val callConnectionQuality: StateFlow<com.example.ui.components.CallConnectionQuality> = kotlinx.coroutines.flow.combine(
        isOnline,
        syncStatus,
        isThinking,
        _manualQualityOverride
    ) { online, sync, thinking, override ->
        override ?: when {
            !online -> com.example.ui.components.CallConnectionQuality.DISCONNECTED
            sync is com.example.data.sync.SyncStatus.Syncing || sync is com.example.data.sync.SyncStatus.OfflinePending -> com.example.ui.components.CallConnectionQuality.UNSTABLE
            thinking -> com.example.ui.components.CallConnectionQuality.UNSTABLE
            else -> com.example.ui.components.CallConnectionQuality.CONNECTED
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.example.ui.components.CallConnectionQuality.CONNECTED)

    fun toggleCallQualityTest() {
        val current = callConnectionQuality.value
        _manualQualityOverride.value = when (current) {
            com.example.ui.components.CallConnectionQuality.CONNECTED -> com.example.ui.components.CallConnectionQuality.UNSTABLE
            com.example.ui.components.CallConnectionQuality.UNSTABLE -> com.example.ui.components.CallConnectionQuality.CONNECTED
            else -> com.example.ui.components.CallConnectionQuality.CONNECTED
        }
    }

    private val _isVoiceMode = MutableStateFlow(false)
    val isVoiceMode: StateFlow<Boolean> = _isVoiceMode.asStateFlow()

    private val _assistantState = MutableStateFlow<AssistantState>(AssistantState.Idle)
    val assistantState: StateFlow<AssistantState> = _assistantState.asStateFlow()

    fun recomputeAssistantState() {
        val err = _errorMessage.value
        val isCall = _isVoiceMode.value || com.example.service.TelephonyService.isCallActive
        val isListening = speechManager.isListening.value || wakeWordManager.isListening.value
        val isOnline = sessionManager.isNetworkAvailable.value

        _assistantState.value = when {
            err != null -> AssistantState.Error(err)
            isCall -> AssistantState.LiveCall(isConnected = isOnline)
            !isOnline -> AssistantState.Offline(networkType = "Local On-Device Engine")
            isListening -> AssistantState.Listening
            else -> AssistantState.Idle
        }
    }

    private val _activeWakeUpAlarm = MutableStateFlow<Pair<String, String>?>(null)
    val activeWakeUpAlarm: StateFlow<Pair<String, String>?> = _activeWakeUpAlarm.asStateFlow()

    fun triggerWakeUpAlarm(title: String, timeStr: String) {
        _activeWakeUpAlarm.value = Pair(title, timeStr)
    }

    fun dismissWakeUpAlarm() {
        _activeWakeUpAlarm.value = null
    }

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _isVoiceStandby = MutableStateFlow(false)
    val isVoiceStandby: StateFlow<Boolean> = _isVoiceStandby.asStateFlow()

    private val _pendingConfirmationMessage = MutableStateFlow<MessageEntity?>(null)
    val pendingConfirmationMessage: StateFlow<MessageEntity?> = _pendingConfirmationMessage.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val commandRegistry by lazy { com.example.domain.actions.CommandRegistry(app) }

    private val _subtitles = MutableStateFlow<List<SubtitleLine>>(emptyList())
    val subtitles: StateFlow<List<SubtitleLine>> = _subtitles.asStateFlow()

    private fun updateUserSubtitle(text: String, isInterim: Boolean) {
        viewModelScope.launch(Dispatchers.Default) {
            _subtitles.update { current ->
                val list = current.toMutableList()
                val lastUserSubtitleIndex = list.indexOfLast { it.speaker == "user" }
                
                if (lastUserSubtitleIndex != -1 && list[lastUserSubtitleIndex].isInterim) {
                    if (text.isNotBlank()) {
                        list[lastUserSubtitleIndex] = list[lastUserSubtitleIndex].copy(
                            text = if (isInterim) "$text…" else text,
                            isInterim = isInterim,
                            timestamp = System.currentTimeMillis()
                        )
                    } else {
                        list.removeAt(lastUserSubtitleIndex)
                    }
                } else if (text.isNotBlank()) {
                    list.add(SubtitleLine(speaker = "user", text = if (isInterim) "$text…" else text, isInterim = isInterim))
                }
                
                if (list.size > 50) {
                    list.removeAt(0)
                }
                list
            }
        }
    }

    private fun updateAssistantSubtitle(text: String) {
        viewModelScope.launch(Dispatchers.Default) {
            _subtitles.update { current ->
                val list = current.toMutableList()
                list.add(SubtitleLine(speaker = "alya", text = text, isInterim = false))
                if (list.size > 50) {
                    list.removeAt(0)
                }
                list
            }
        }
    }

    private fun updateAlyaSubtitle(text: String, isInterim: Boolean = false) {
        val cleanText = com.example.util.SystemThoughtFilter.cleanForDisplay(text)
        viewModelScope.launch(Dispatchers.Default) {
            _subtitles.update { current ->
                val list = current.toMutableList()
                val lastAlyaSubtitleIndex = list.indexOfLast { it.speaker == "alya" }
                
                if (lastAlyaSubtitleIndex != -1 && list[lastAlyaSubtitleIndex].isInterim) {
                    if (cleanText.isNotBlank()) {
                        list[lastAlyaSubtitleIndex] = list[lastAlyaSubtitleIndex].copy(
                            text = cleanText,
                            isInterim = isInterim,
                            timestamp = System.currentTimeMillis()
                        )
                    } else {
                        list.removeAt(lastAlyaSubtitleIndex)
                    }
                } else if (cleanText.isNotBlank()) {
                    list.add(SubtitleLine(speaker = "alya", text = cleanText, isInterim = isInterim))
                }
                
                if (list.size > 50) {
                    list.removeAt(0)
                }
                list
            }
        }
    }

    fun clearSubtitles() {
        _subtitles.value = emptyList()
    }

    init {
        // Bridge Mode State Synchronization
        viewModelScope.launch {
            repository.preferences.bridgeModeEnabled.collect { enabled ->
                if (enabled) {
                    val url = repository.preferences.bridgeServerUrl.value
                    if (!alyaBridgeClient.isConnected.value) {
                        alyaBridgeClient.connect(url)
                    }
                } else {
                    alyaBridgeClient.disconnect()
                }
            }
        }

        checkForUpdatesOnLaunch()

        // Auto-resume live voice session on network reconnect
        errorRecoveryHandler.registerAutoResumeCallback("live_voice_session") {
            if (_isVoiceMode.value && !speechManager.isListening.value && !ttsManager.isSpeaking.value) {
                Log.i("AlyaViewModel", "Auto-resuming live voice session on network reconnect.")
                startListening()
            }
        }

        // Setup speech recognition callbacks
        speechManager.onFinalSpeechResult = { spokenText ->
            _isRecordingVoiceMessage.value = false
            _voiceInputSentEvent.value = System.currentTimeMillis()
            conversationalFeedbackModule.resetTurn()
            hasBackchannelTriggeredThisTurn = false
            
            var finalSpokenText = spokenText.trim()
            if (isListeningTriggeredByWakeWord) {
                isListeningTriggeredByWakeWord = false
                Log.i("AlyaViewModel", "Processing wake-word triggered speech: '$spokenText'")
                // Clean optional leading wake word if user said it in the same breath (e.g., "Hey Alya what is the temperature")
                val triggers = getActiveWakeWordTriggers().joinToString("|") { Regex.escape(it) }
                val stripped = finalSpokenText.replaceFirst(Regex("^(?i)(hey\\s+)?($triggers)[,\\s]*"), "").trim()
                if (stripped.isNotBlank()) {
                    finalSpokenText = stripped
                }
            }

            _isListeningPopupVisible.value = false

            if (finalSpokenText.isNotBlank()) {
                updateUserSubtitle(finalSpokenText, isInterim = false)
                // Use current language preference as default fallback
                val currentPref = _currentLanguageLocale.value
                val detectedDialect = com.example.voice.lang.LocaleManager.detectUserDialect(finalSpokenText, currentPref)
                
                // Only switch target language if high-confidence non-English script is detected (e.g. Devanagari/Bengali/Japanese script)
                val targetLang = if (detectedDialect != "en-US" && detectedDialect != currentPref) {
                    detectedDialect
                } else {
                    currentPref
                }

                handleSpokenInput(finalSpokenText, targetLang)
            }
        }

        speechManager.onSilenceTimeout = {
            Log.i("AlyaViewModel", "Silence timeout triggered for one-shot listening. Dismissing popup.")
            if (!_isVoiceMode.value) {
                speechManager.stopListening()
                audioDeviceManager.abandonAudioFocus()
                app.audioCaptureManager.stopCapture()
                _isListeningPopupVisible.value = false
                _isRecordingVoiceMessage.value = false
            }
        }

        // Live conversational feedback tracking during user pauses
        viewModelScope.launch {
            speechManager.partialResult.collect { text ->
                val cleanText = text.trim()
                if (cleanText.isNotBlank()) {
                    updateUserSubtitle(cleanText, isInterim = true)
                    if (_isVoiceMode.value && !ttsManager.isSpeaking.value && !_isThinking.value) {
                        conversationalFeedbackModule.onPartialSpeechUpdate(
                            partialText = cleanText,
                            isListening = speechManager.isListening.value,
                            isVoiceMode = _isVoiceMode.value
                        )
                    }
                }
            }
        }
        
        // Sync contacts to memory
        viewModelScope.launch {
            contactManager.syncContactsToMemory(repository.database.memoryDao())
        }

        // Bridge Mode AI Response Collection
        viewModelScope.launch {
            alyaBridgeClient.incomingText.collect { text ->
                withContext(Dispatchers.Main) {
                    _isThinking.value = false
                    updateAssistantSubtitle(text)
                    // If we're in bridge mode, the server already handles audio playback via URL + ExoPlayer
                    // So we don't need to call local speakResponse here.
                }
            }
        }

        // Collect voice language preferences and synchronize with state/speech managers dynamically
        viewModelScope.launch {
            repository.preferences.voiceLanguage.collect { lang ->
                if (lang.isNotBlank() && lang != _currentLanguageLocale.value) {
                    Log.i("AlyaViewModel", "Observed preference change for voiceLanguage: $lang")
                    _currentLanguageLocale.value = lang
                    speechManager.updateLanguageLocale(lang, rebindIfListening = speechManager.isListening.value)
                    try {
                        val loc = java.util.Locale.forLanguageTag(lang)
                        val prefPersona = repository.preferences.voicePersona.value
                        ttsManager.configureNaturalVoice(loc, prefPersona)
                    } catch (e: Exception) { android.util.Log.e("Alya", "Exception handled", e) }
                }
            }
        }

        // Collect voice persona preferences and dynamically update TTS engine
        viewModelScope.launch {
            repository.preferences.voicePersona.collect { persona ->
                if (persona.isNotBlank()) {
                    Log.i("AlyaViewModel", "Observed preference change for voicePersona: $persona")
                    ttsManager.setPersona(persona)
                }
            }
        }

        // Direct hardware audio routing from Gemini Live to PcmAudioTrackPlayer
        geminiLiveClient.attachAudioTrackPlayer(pcmAudioPlayer)

        // Observe network changes to update assistant state and reconnect Gemini Live if needed
        viewModelScope.launch {
            sessionManager.isNetworkAvailable.collect { online ->
                recomputeAssistantState()
                if (online && _isVoiceMode.value && !geminiLiveClient.isSessionActive()) {
                    Log.i("AlyaViewModel", "Network restored during Voice Mode. Reconnecting Gemini Live.")
                    // Defer slightly to ensure network is fully stable
                    delay(1000)
                    if (_isVoiceMode.value) {
                        reconnectGeminiLive()
                    }
                }
            }
        }

        // Synchronize in-app listening popup state with SessionManager sessionState
        viewModelScope.launch {
            sessionManager.sessionState.collect { state ->
                when (state) {
                    com.example.util.SessionState.IDLE, com.example.util.SessionState.STOPPED -> {
                        if (_isListeningPopupVisible.value) {
                            _isListeningPopupVisible.value = false
                        }
                    }
                    else -> {}
                }
            }
        }

        // Handle streaming text updates from Gemini Live
        viewModelScope.launch {
            geminiLiveClient.incomingTextFlow.collect { textChunk ->
                val raw = (_liveAssistantTranscript.value + textChunk).trimStart()
                _liveAssistantTranscript.value = raw
                _isThinking.value = false
                val display = com.example.util.SystemThoughtFilter.cleanForDisplay(raw)
                if (display.isNotBlank()) {
                    updateAlyaSubtitle(display, isInterim = true)
                }
            }
        }

        // Handle turn completion: persist assistant message to database
        geminiLiveClient.onTurnComplete = {
            viewModelScope.launch(Dispatchers.IO) {
                val rawUtterance = _liveAssistantTranscript.value.trim()
                val cleanUtterance = cleanAssistantText(rawUtterance)
                if (cleanUtterance.isNotBlank()) {
                    updateAlyaSubtitle(cleanUtterance, isInterim = false)
                    val convId = _currentConversationId.value ?: "default"
                    val assistantMsg = MessageEntity(
                        id = java.util.UUID.randomUUID().toString(),
                        conversationId = convId,
                        role = "assistant",
                        content = cleanUtterance,
                        timestamp = System.currentTimeMillis()
                    )
                    try {
                        repository.database.messageDao().insertMessage(assistantMsg)
                    } catch (e: Exception) {
                        Log.e("AlyaViewModel", "Error saving Gemini Live transcript to database: ${e.message}")
                    }
                    _liveAssistantTranscript.value = ""
                }
            }
        }

        // Handle server-side barge-in signals
        geminiLiveClient.onInterrupted = {
            viewModelScope.launch(Dispatchers.Main) {
                Log.i("AlyaViewModel", "Gemini Live server signaled barge-in. Flushing AudioTrack.")
                pcmAudioPlayer.stopAndFlushForBargeIn()
                _liveAssistantTranscript.value = ""
            }
        }

        // Handle successful handshake verification: trigger natural live greeting from Gemini Live
        geminiLiveClient.onSetupComplete = {
            Log.i("AlyaViewModel", "Gemini Live session fully established. Triggering authentic soft female greeting from Gemini Live.")
            val activeLang = _currentLanguageLocale.value.ifBlank { repository.preferences.voiceLanguage.value }
            val greetingPrompt = when (activeLang.lowercase().take(2)) {
                "hi" -> "Greet the user warmly in natural Hindi with your authentic, soft, sweet female voice. Keep it brief (1 sentence) asking how their day is going."
                "bn" -> "Greet the user warmly in natural Bengali with your authentic, soft, sweet female voice. Keep it brief (1 sentence) asking how their day is going."
                "ja" -> "Greet the user warmly in natural Japanese with your authentic, soft, sweet female voice. Keep it brief (1 sentence) asking how their day is going."
                else -> "Greet the user warmly in your authentic, soft, sweet female voice. Keep it brief (1 sentence) asking how their day is going."
            }
            geminiLiveClient.sendTextMessage(greetingPrompt)
        }

        // Handle tool calls from Gemini Live
        geminiLiveClient.onToolCallReceived = { name, id, args ->
            viewModelScope.launch(Dispatchers.IO) {
                Log.i("AlyaViewModel", "Executing tool call for Gemini Live: $name")
                val paramsMap = mutableMapOf<String, String>()
                val keys = args.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    paramsMap[key] = args.optString(key, "")
                }

                val action = com.example.domain.tools.StructuredAction(
                    intent = name,
                    toolName = name,
                    parameters = paramsMap,
                    requiresConfirmation = false // Natural voice conversation usually implies direct intent
                )

                val result = repository.toolExecutor.executeAction(
                    action = action,
                    isUserConfirmed = true, // Force execution for Gemini Live if model decided
                    confirmationPolicy = "NEVER"
                )

                val responseObj = org.json.JSONObject()
                responseObj.put("success", result.success)
                responseObj.put("message", result.message)
                responseObj.put("output", result.output ?: result.message)

                // Send execution result back to Gemini Live to continue conversation flow
                geminiLiveClient.sendToolResponse(name, id, responseObj)
            }
        }

        // Handle structured JSON intents from Gemini Live (parsed from text stream)
        geminiLiveClient.onIntentReceived = { intent, params ->
            viewModelScope.launch(Dispatchers.IO) {
                Log.i("AlyaViewModel", "Executing AI-derived intent: $intent with params: $params")
                
                // Map common intent variations to known tools
                val normalizedIntent = when (intent.lowercase().trim()) {
                    "reject_call", "decline_call", "stop_call" -> "end_call"
                    "pick_up", "answer", "accept" -> "answer_call"
                    "scroll", "scroll_down", "scroll_up" -> "scroll_screen"
                    "open", "launch" -> "open_app"
                    else -> intent
                }

                // Map parameter variations
                val normalizedParams = params.toMutableMap()
                if (normalizedParams.containsKey("amount")) {
                    normalizedParams["distance"] = normalizedParams["amount"] ?: "SHORT"
                }

                val action = com.example.domain.tools.StructuredAction(
                    intent = normalizedIntent,
                    toolName = normalizedIntent,
                    parameters = normalizedParams,
                    requiresConfirmation = false
                )

                val result = repository.toolExecutor.executeAction(
                    action = action,
                    isUserConfirmed = true,
                    confirmationPolicy = "NEVER"
                )
                
                Log.d("AlyaViewModel", "Intent execution result ($normalizedIntent): ${result.success}")
            }
        }

        // Fallback to speechManager if Gemini Live encounters an unrecoverable error
        geminiLiveClient.onErrorOccurred = { errorMsg ->
            viewModelScope.launch(Dispatchers.Main) {
                Log.w("AlyaViewModel", "Gemini Live error: $errorMsg. Checking offline speech recognition fallback.")
                app.audioCaptureManager.stopCapture()
                pcmAudioPlayer.stop()
                if (_isVoiceMode.value && !speechManager.isListening.value && !_isMuted.value) {
                    val activeLang = _currentLanguageLocale.value.ifBlank { repository.preferences.voiceLanguage.value }
                    startListening()
                }
            }
        }

        // Forward microphone hardware audio frames directly to Gemini Live API during active voice call
        var lastLiveMicRmsUpdate = 0L
        var lastPlaybackStartTime = 0L
        
        pcmAudioPlayer.onPlaybackStarted = {
            lastPlaybackStartTime = System.currentTimeMillis()
            ttsManager.stop()
        }
        ttsManager.onSpeechStarted = {
            lastPlaybackStartTime = System.currentTimeMillis()
        }

        app.audioCaptureManager.setAudioFrameListener { pcmBuffer, readSize, rmsDb ->
            if (_isMuted.value) {
                _liveMicRmsDb.value = -100.0f
            } else {
                val now = System.currentTimeMillis()
                // Throttled RMS updates for FPS stability (120ms default, 60ms in boost mode)
                val threshold = if (repository.preferences.fpsBoostEnabled.value) 60L else 120L
                if (now - lastLiveMicRmsUpdate > threshold) {
                    lastLiveMicRmsUpdate = now
                    _liveMicRmsDb.value = rmsDb
                }
                
                if (_isVoiceMode.value) {
                    val isAssistantPlaying = pcmAudioPlayer.isPlaying() || pcmAudioPlayer.isTurnActive() || pcmAudioPlayer.isPlaybackActive.value || ttsManager.isSpeaking.value
                    if (!isAssistantPlaying) {
                        // When assistant is not speaking, stream all user audio immediately
                        geminiLiveClient.sendAudioFrame(pcmBuffer, readSize)
                    } else {
                        // When assistant is speaking, only forward audio if user intentionally speaks loudly (>= 58 dB RMS)
                        val timeSincePlaybackStart = now - lastPlaybackStartTime
                        if (timeSincePlaybackStart > 350L && rmsDb >= 58.0f) {
                            Log.i("AlyaViewModel", "Barge-in detected during assistant speech ($rmsDb dB).")
                            pcmAudioPlayer.stopAndFlushForBargeIn()
                            ttsManager.stop()
                            geminiLiveClient.sendAudioFrame(pcmBuffer, readSize)
                        }
                    }
                }
            }
        }

        // Deactivate voice mode & free microphone when a call is active
        viewModelScope.launch {
            com.example.service.TelephonyService.isCallActiveFlow.collect { isCallActive ->
                if (isCallActive) {
                    Log.i("AlyaViewModel", "System call active detected, stopping voice assistant mode and speech managers.")
                    stopVoiceMode()
                    ttsManager.stop()
                    speechManager.stopListening()
                }
            }
        }

        // Disable/pause non-essential background services or floating overlays during active Billing Flow
        viewModelScope.launch {
            app.billingRepository.isBillingFlowActive.collect { active ->
                if (active) {
                    Log.i("AlyaViewModel", "Active billing flow detected: pausing voice modes and speech managers.")
                    dismissListeningPopup()
                    stopListening()
                    try {
                        wakeWordManager.stop()
                    } catch (e: Exception) {
                        Log.e("AlyaViewModel", "Failed to pause wake word manager for billing: ${e.message}")
                    }
                } else {
                    if (repository.preferences.isWakeUpActivated.value) {
                        val kw = repository.preferences.selectedWakeWord.value
                        val sens = repository.preferences.wakeWordSensitivity.value
                        try {
                            wakeWordManager.start(kw, sens)
                        } catch (e: Exception) {
                            Log.e("AlyaViewModel", "Failed to resume wake word manager: ${e.message}")
                        }
                    }
                }
            }
        }

        // Sync TTS speaking state to wake-word manager to prevent self-triggering
        viewModelScope.launch {
            ttsManager.isSpeaking.collect { speaking ->
                wakeWordManager.isTtsSpeaking = speaking
            }
        }

        // Wake-word activation callback
        wakeWordManager.addWakeWordListener { keyword ->
            if (_isVoiceMode.value || wakeWordManager.isSuppressed) {
                Log.d("AlyaViewModel", "Ignoring wake-word trigger during active live conversation mode.")
            } else {
                Log.i("AlyaViewModel", "Wake-word detected: $keyword")
                powerStateManager.notifyUserActivity()
                
                val isOnlyOwnerVoiceEnabled = repository.preferences.onlyOwnerVoiceWakes.value
                val recentPcm = wakeWordManager.getRecentAudio(24000) // 1.5 seconds of PCM data
                
                var isVerified = true
                if (isOnlyOwnerVoiceEnabled && recentPcm.isNotEmpty()) {
                    val verificationManager = com.example.voice.wakeword.SpeakerVerificationManager(app, repository.preferences)
                    val verificationResult = verificationManager.verifySpeaker(recentPcm)
                    isVerified = verificationResult.isMatch
                    Log.i("AlyaViewModel", "Owner Voice verification result: isMatch=$isVerified, similarity=${verificationResult.similarity}, reason=${verificationResult.reason}")
                }
                
                if (!isVerified) {
                    Log.d("AlyaViewModel", "Ignored wake-word trigger: Speaker verification failed (not owner's voice).")
                } else {
                    // CRITICAL: Ensure clean microphone state before re-requesting
                    speechManager.stopListening()
                    app.audioCaptureManager.stopCapture()
                    
                    app.audioCaptureManager.startCapture(com.example.voice.microphone.MicState.ACTIVE_VOICE_SESSION)
                    app.audioCaptureManager.flushAudioBufferOnWakeWord()

                    val cleanKw = keyword.replaceFirstChar { it.uppercase() }
                    _detectedWakeWord.value = cleanKw
                    // On primary chat screen, show bottom listening pop-up and start listening
                    soundEffectManager.play(com.example.voice.SoundEffectManager.SoundType.VOICE_START)
                    _isListeningPopupVisible.value = true
                    startListening(isWakeWordTrigger = true)
                }
            }
        }

        // Interruption & Barge-In handling
        speechManager.onUserBeganSpeaking = {
            if (pcmAudioPlayer.isPlaybackActive.value || ttsManager.isSpeaking.value) {
                Log.i("AlyaViewModel", "User began speaking during response audio: Triggering immediate barge-in flush.")
                pcmAudioPlayer.stopAndFlushForBargeIn()
                ttsManager.stop()
                wakeWordManager.setBargeInEnabled(false)
            }
        }

        // Barge-in callbacks during speech playback
        wakeWordManager.onBargeInDetected = {
            if (pcmAudioPlayer.isPlaybackActive.value || ttsManager.isSpeaking.value) {
                Log.i("AlyaViewModel", "Barge-in acoustic event detected: Stopping response audio immediately.")
                pcmAudioPlayer.stopAndFlushForBargeIn()
                ttsManager.stop()
                wakeWordManager.setBargeInEnabled(false)
                if (_isVoiceMode.value && !_isMuted.value) {
                    startListening()
                }
            }
        }

        ttsManager.onSpeechStarted = {
            wakeWordManager.setBargeInEnabled(true)
            // Stop listening immediately when TTS starts speaking to prevent self-listening
            speechManager.stopListening()
        }

        // Continuous conversational loop (guarded against speech loop)
        ttsManager.onSpeechCompleted = {
            if (conversationalFeedbackModule.isMarkerPlaying.value) {
                // Marker finished, do not restart listening cycle
            } else {
                wakeWordManager.setBargeInEnabled(false)
                _isThinking.value = false
                sessionManager.onListeningStarted()
                
                // In Live Voice Mode or if SpeechManager is in continuous mode, 
                // resume listening smoothly for natural turn-taking (ChatGPT-style) with ultra-low latency (10-20ms).
                if ((_isVoiceMode.value || speechManager.isContinuousMode) && !_isMuted.value) {
                    viewModelScope.launch {
                        delay(15L)
                        if ((_isVoiceMode.value || speechManager.isContinuousMode) && !_isMuted.value) {
                            startListening()
                        }
                    }
                } else {
                    _isListeningPopupVisible.value = false
                }
            }
        }

        // Automatic wake-word battery saver mode sync
        viewModelScope.launch(Dispatchers.IO) {
            repository.preferences.wakeWordBatterySaver.collect { isBatterySaverEnabled ->
                wakeWordManager.setBatterySaverEnabled(isBatterySaverEnabled)
            }
        }

        // Initialize active conversation (once)
        viewModelScope.launch {
            val list = repository.allConversations.stateIn(viewModelScope).value
            if (_currentConversationId.value == null) {
                if (list.isNotEmpty()) {
                    _currentConversationId.value = list.first().id
                } else {
                    val newConv = repository.createNewConversation("Chat with Alya")
                    _currentConversationId.value = newConv.id
                }
            }
        }
    }

    fun navigateTo(screen: AssistantScreen) {
        _currentScreen.value = screen
        if (screen == AssistantScreen.VOICE_MODE) {
            startVoiceMode()
        } else if (_isVoiceMode.value && screen != AssistantScreen.VOICE_MODE) {
            stopVoiceMode()
        }
    }

    fun selectConversation(id: String) {
        _currentConversationId.value = id
        _currentScreen.value = AssistantScreen.CHAT
    }

    fun createNewConversation() {
        viewModelScope.launch {
            val conv = repository.createNewConversation("New Conversation")
            _currentConversationId.value = conv.id
            _currentScreen.value = AssistantScreen.CHAT
        }
    }

    fun renameConversation(id: String, newTitle: String) {
        viewModelScope.launch {
            repository.renameConversation(id, newTitle)
        }
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch {
            repository.deleteConversation(id)
            if (_currentConversationId.value == id) {
                _currentConversationId.value = null
            }
        }
    }

    fun clearAllConversations() {
        viewModelScope.launch {
            repository.clearAllConversations()
            _currentConversationId.value = null
        }
    }

    // Messaging & User Input
    private var lastMessageWasSpoken: Boolean = false
    private val isProcessingMessage = java.util.concurrent.atomic.AtomicBoolean(false)
    private var lastSpokenText: String = ""
    private var lastSpokenTime: Long = 0L

    fun sendVoiceMessage(spokenText: String, languageHint: String? = null) {
        lastMessageWasSpoken = true
        sendMessage(spokenText, languageHint)
    }

    fun sendMessage(text: String, languageHint: String? = null) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        powerStateManager.notifyUserActivity()

        // Capture whether this turn was voice-initiated before resetting flags
        val wasSpokenTurn = lastMessageWasSpoken || _isListeningPopupVisible.value || _isVoiceMode.value

        // Prevent duplicate concurrent message processing
        if (!isProcessingMessage.compareAndSet(false, true)) {
            Log.d("AlyaViewModel", "Skipping duplicate simultaneous message dispatch: $trimmed")
            return
        }

        // Stop active speech recognition while processing message to prevent speech loops
        speechManager.stopListening()

        // If Alya was speaking, stop immediately
        if (ttsManager.isSpeaking.value) {
            ttsManager.stop()
        }

        val soundEffects = repository.preferences.soundEffectsEnabled.value
        soundEffectManager.play(com.example.voice.SoundEffectManager.SoundType.MESSAGE_SENT, enabled = soundEffects)

        _isThinking.value = true
        viewModelScope.launch {
            try {
                var convId = _currentConversationId.value
                if (convId == null) {
                    val newConv = repository.createNewConversation("Chat with Alya")
                    convId = newConv.id
                    _currentConversationId.value = convId
                }

                val activeConvId = convId
                val assistantMessage = kotlinx.coroutines.withTimeoutOrNull(8000L) {
                    repository.processUserMessage(
                        conversationId = activeConvId,
                        userText = trimmed,
                        isVoiceMode = _isVoiceMode.value,
                        languageHint = languageHint
                    )
                }

                if (assistantMessage != null) {
                    if (assistantMessage.toolStatus == "pending_confirmation") {
                        _pendingConfirmationMessage.value = assistantMessage
                    }

                    // Clear thinking state BEFORE speaking so onSpeechCompleted is never blocked by a stale thinking flag
                    _isThinking.value = false

                    val speechText = if (assistantMessage.toolName == "check_weather" && !assistantMessage.toolResult.isNullOrBlank()) {
                        assistantMessage.toolResult
                    } else {
                        assistantMessage.content
                    }

                    // Always speak the response in voice mode or if the turn was voice-initiated
                    if (wasSpokenTurn || _isVoiceMode.value) {
                        speakResponse(speechText)
                    }
                    lastMessageWasSpoken = false
                } else {
                    lastMessageWasSpoken = false
                    _isThinking.value = false
                    val fallbackText = com.example.voice.error.VoiceErrorRegistry.OFFLINE_FALLBACK_STRING
                    _errorMessage.value = fallbackText
                    if (wasSpokenTurn || _isVoiceMode.value) {
                        speakResponse(fallbackText)
                    }
                }
            } catch (e: Exception) {
                _isThinking.value = false
                val errorMsg = e.localizedMessage ?: "Failed to process message"
                _errorMessage.value = errorMsg
                if (_isVoiceMode.value) {
                    val fallbackText = com.example.voice.error.VoiceErrorRegistry.OFFLINE_FALLBACK_STRING
                    speakResponse(fallbackText)
                } else {
                    if (!_isMuted.value && wasSpokenTurn) {
                        startListening()
                    }
                }
            } finally {
                _isThinking.value = false
                isProcessingMessage.set(false)
                // Only dismiss popup if not in voice mode and turn is complete
                if (!_isVoiceMode.value && !ttsManager.isSpeaking.value) {
                    _isListeningPopupVisible.value = false
                }
            }
        }
    }

    private fun checkExplicitLanguageSwitch(text: String): Boolean {
        val lower = text.lowercase().trim()
        val languageMapping = mapOf(
            "english" to Pair("en-US", "Alright, I will speak in English now."),
            "hindi" to Pair("hi-IN", "Theek hai, ab main Hindi mein baat karungi."),
            "bengali" to Pair("bn-IN", "Thik ache, ekhon theke ami Banglate kotha bolbo."),
            "bangla" to Pair("bn-IN", "Thik ache, ekhon theke ami Banglate kotha bolbo."),
            "japanese" to Pair("ja-JP", "Wakarimashita. Korekara wa Nihongo de hanashimasu ne."),
            "russian" to Pair("ru-RU", "Khorosho, teper ya budu govorit po-russki."),
            "spanish" to Pair("es-ES", "De acuerdo, hablare en espanol a partir de ahora."),
            "french" to Pair("fr-FR", "D'accord, je vais parler en francais maintenant."),
            "german" to Pair("de-DE", "In Ordnung, ich werde jetzt auf Deutsch sprechen."),
            "portuguese" to Pair("pt-BR", "Tudo bem, vou falar em portugues agora."),
            "indonesian" to Pair("id-ID", "Baiklah, sekarang saya akan berbicara dalam bahasa Indonesia."),
            "arabic" to Pair("ar-SA", "Hasanan, sa-atahaddathu bil-lughah al-Arabiyyah al-an."),
            "italian" to Pair("it-IT", "Va bene, ora parlero in italiano."),
            "korean" to Pair("ko-KR", "Algesseumnida. Ijebuteo hangugeo-ro malsseumdeurilgeyo."),
            "chinese" to Pair("zh-CN", "Hao de, wo xianzai hui yong zhongwen he nin jiaoliu."),
            "turkish" to Pair("tr-TR", "Tamam, artik Turkce konusacagim."),
            "urdu" to Pair("ur-PK", "Theek hai, ab main Urdu mein baat karungi."),
            "tamil" to Pair("ta-IN", "Sari, naan ippodhu Tamilil pesuven."),
            "telugu" to Pair("te-IN", "Sare, nenu ippudu Telugu lo matladatanu."),
            "malayalam" to Pair("ml-IN", "Sari, njan ippol Malayalamathil samsarikkam."),
            "assamese" to Pair("as-IN", "Thik ase, moi etiya Asomiyat kotha kom."),
            "thai" to Pair("th-TH", "Tok long kha, chan ja phut phasa thai ton ni."),
            "nepali" to Pair("ne-NP", "Hunchha, ma aba Nepali ma kura garnechhu."),
            "punjabi" to Pair("pa-IN", "Theek hai, main hun Punjabi vich gal karangi."),
            "gujarati" to Pair("gu-IN", "Barobar, have hu Gujarati ma vaat karish.")
        )
        for ((langName, pair) in languageMapping) {
            val (code, confirmationText) = pair
            if (lower == "speak $langName" || lower == "speak in $langName" ||
                lower.startsWith("speak $langName ") || lower.startsWith("speak in $langName ") ||
                lower.endsWith(" speak $langName") || lower.endsWith(" speak in $langName")
            ) {
                Log.i("AlyaViewModel", "User explicitly requested language change to $langName ($code)")
                setLanguageLocale(code)
                
                val convId = _currentConversationId.value ?: "default"
                val confirmationMessage = MessageEntity(
                    id = java.util.UUID.randomUUID().toString(),
                    conversationId = convId,
                    role = "assistant",
                    content = confirmationText,
                    timestamp = System.currentTimeMillis()
                )
                viewModelScope.launch {
                    try {
                        repository.database.messageDao().insertMessage(confirmationMessage)
                    } catch (e: Exception) {
                        Log.e("AlyaViewModel", "Error saving language confirmation to db", e)
                    }
                    speakResponse(confirmationText)
                }
                return true
            }
        }
        return false
    }

    private fun handleSpokenInput(spokenText: String, languageHint: String? = null) {
        if (isBridgeModeEnabled.value) {
            _isThinking.value = true
            alyaBridgeClient.sendText(spokenText)
            updateUserSubtitle(spokenText, isInterim = false)
            return
        }

        val lower = spokenText.lowercase().trim()
        val now = System.currentTimeMillis()

        if (checkExplicitLanguageSwitch(spokenText)) {
            return
        }

        // Debounce exact duplicate spoken inputs within 1200ms
        if (lower == lastSpokenText && (now - lastSpokenTime) < 1200L) {
            return
        }
        lastSpokenText = lower
        lastSpokenTime = now

        // Intercept local device control commands on background dispatcher
        viewModelScope.launch(Dispatchers.IO) {
            val fastMatchResult = try {
                commandRegistry.tryFastResolve(spokenText)
            } catch (e: SecurityException) {
                com.example.domain.tools.ToolExecutionResult(
                    success = false,
                    message = "Permission denied: ${e.localizedMessage}. Please grant necessary permissions.",
                    status = com.example.domain.tools.ActionResultStatus.FAILED
                )
            } catch (e: Throwable) {
                null
            }

            if (fastMatchResult != null) {
                withContext(Dispatchers.Main) {
                    _isThinking.value = false
                    updateUserSubtitle(spokenText, isInterim = false)
                    speakResponse(fastMatchResult.message)
                }
                return@launch
            }

            withContext(Dispatchers.Main) {
                proceedWithSpokenInput(spokenText, languageHint)
            }
        }
    }

    private fun proceedWithSpokenInput(spokenText: String, languageHint: String? = null) {
        val lower = spokenText.lowercase().trim()
        val now = System.currentTimeMillis()
        val triggers = getActiveWakeWordTriggers()

        // Check if user requested to stop/disable automatic wake up / wake word detection
        val isStopWakeUpCommand = lower.contains("automatic wake up ko stop") ||
                lower.contains("stop automatic wake up") ||
                lower.contains("automatic wake up stop") ||
                lower.contains("automatic wake up band") ||
                lower.contains("disable automatic wake up") ||
                lower.contains("stop wake word") ||
                lower.contains("stop wake up")

        if (isStopWakeUpCommand) {
            Log.i("AlyaViewModel", "User requested disabling automatic wake word/wake-up detection via voice command.")
            toggleWakeWordEnabled(false)
            
            val responseText = "Sure! I have stopped automatic wake up. Wake word detection is now disabled and I will not listen in the background anymore. You can always re-enable it in Settings."
            val convId = _currentConversationId.value ?: "default"
            val confirmationMessage = MessageEntity(
                id = java.util.UUID.randomUUID().toString(),
                conversationId = convId,
                role = "assistant",
                content = responseText,
                timestamp = System.currentTimeMillis()
            )
            viewModelScope.launch {
                try {
                    repository.database.messageDao().insertMessage(confirmationMessage)
                } catch (e: Exception) {
                    Log.e("AlyaViewModel", "Error saving wake-word stop confirmation to db", e)
                }
                speakResponse(responseText)
            }
            return
        }

        // Standalone wake word utterance: e.g. user says "Alia", "Alya", "Seno", "Jarvis", "Luna", etc.
        val isStandaloneTrigger = triggers.any { lower == it || lower == "hey $it" }
        if (isStandaloneTrigger) {
            val kw = lower.replace("hey ", "").trim().replaceFirstChar { it.uppercase() }
            _detectedWakeWord.value = kw
            _isListeningPopupVisible.value = true
            soundEffectManager.play(com.example.voice.SoundEffectManager.SoundType.VOICE_START)
            startListening()
            return
        }

        // Support reload phone and reload app command
        val isReloadCommand = lower.contains("reload app") || lower.contains("reload phone") || lower.contains("restart app") || lower.contains("refresh phone") || lower == "reload"
        if (isReloadCommand) {
            reloadAppAndPhone()
            return
        }

        // Handle direct accessibility/navigation commands (Part of Part 3.3)
        if (com.example.util.AccessibilityCommandParser.tryHandleDirectCommand(spokenText)) {
            return
        }

        // Support "Alya, close" or similar variant to stop active background listening and end voice mode
        val isCloseCommand = lower.contains("alya close") || lower.contains("close alya") || lower == "close" || lower.contains("stop listening") || lower.contains("stop background listening")
        if (isCloseCommand) {
            Log.i("AlyaViewModel", "User commanded 'Alya, close'. Explicitly releasing microphone, audio focus, and network sockets immediately.")
            
            // Explicit release via SessionManager immediately upon detection
            sessionManager.releaseActiveSession()
            
            _isVoiceMode.value = false
            _isVoiceStandby.value = false
            _isListeningPopupVisible.value = false
            ttsManager.stop()
            val soundEffects = repository.preferences.soundEffectsEnabled.value
            soundEffectManager.play(com.example.voice.SoundEffectManager.SoundType.VOICE_END, enabled = soundEffects)
            if (_currentScreen.value == AssistantScreen.VOICE_MODE) {
                _currentScreen.value = AssistantScreen.CHAT
            }
            
            // Stop background service immediately to release all resources
            com.example.service.WakeWordService.stop(app)
            return
        }

        val isInterruptWord = lower in listOf("stop", "cancel", "quiet", "pause", "wait", "shut up", "hold on", "hush") || triggers.contains(lower)
        if (ttsManager.isSpeaking.value && isInterruptWord) {
            ttsManager.stop()
            wakeWordManager.setBargeInEnabled(false)
            _isListeningPopupVisible.value = false
            return
        }

        // Strip leading wake words before processing command
        val triggersRegex = triggers.joinToString("|") { Regex.escape(it) }
        val wakeWordRegex = Regex("^(?:hey\\s+)?(?:$triggersRegex)[,\\s]+", RegexOption.IGNORE_CASE)
        val cleanedText = spokenText.trim().replace(wakeWordRegex, "").trim()
        val commandToSend = if (cleanedText.isNotBlank()) cleanedText else spokenText.trim()

        sendVoiceMessage(commandToSend, languageHint)
    }

    fun speakResponse(text: String) {
        val cleanSpeechText = com.example.util.SystemThoughtFilter.cleanForSpeech(text)
        if (cleanSpeechText.isBlank()) {
            _isThinking.value = false
            if (_isVoiceMode.value && !_isMuted.value && !geminiLiveClient.isSessionActive()) {
                startListening()
            }
            return
        }
        val cleanDisplayText = com.example.util.SystemThoughtFilter.cleanForDisplay(text)
        updateAlyaSubtitle(cleanDisplayText)

        // If PCM audio track is currently playing live WebSocket stream audio, suppress secondary Android TTS to prevent double voice overlap
        if (pcmAudioPlayer.isPlaybackActive.value) {
            Log.i("AlyaViewModel", "Live conversation audio active: Native PCM audio streaming active. Suppressing secondary TTS.")
            return
        }

        audioDeviceManager.requestAudioFocus()
        wakeWordManager.setBargeInEnabled(true)

        val activeLang = _currentLanguageLocale.value.ifBlank { repository.preferences.voiceLanguage.value }
        val prefPersona = repository.preferences.voicePersona.value
        val personaToUse = if (prefPersona.isBlank() || prefPersona == "ALYA_ANIME_RUSSIAN") "KORE" else prefPersona

        ttsManager.speak(
            text = cleanSpeechText,
            speechRate = repository.preferences.speechRate.value,
            speechPitch = repository.preferences.speechPitch.value,
            locale = Locale.forLanguageTag(activeLang),
            persona = personaToUse
        )
    }

    fun stopSpeaking() {
        ttsManager.stop()
        pcmAudioPlayer.stopAndFlushForBargeIn()
        audioDeviceManager.abandonAudioFocus()
    }

    fun getTtsVoicePackInfo(): TextToSpeechManager.VoicePackDetails {
        val activeLang = _currentLanguageLocale.value.ifBlank { repository.preferences.voiceLanguage.value }
        return ttsManager.getVoicePackInfo(Locale.forLanguageTag(activeLang))
    }

    fun openTtsVoiceDataSettings(context: Context): Boolean {
        return ttsManager.openTtsVoiceDataSettings(context)
    }

    fun openTtsEngineSettings(context: Context): Boolean {
        return ttsManager.openTtsEngineSettings(context)
    }

    fun enterStandbyMode() {
        if (!_isVoiceMode.value) return
        // Do not force standby during active live conversation session to maintain ultra-low latency continuous listening
        if (sessionManager.isNetworkAvailable.value || geminiLiveClient.isSessionActive()) {
            Log.i("AlyaViewModel", "Preventing standby transition during active Live Conversation session to preserve ultra-low latency streaming.")
            _isVoiceStandby.value = false
            if (!app.audioCaptureManager.isCaptureActive.value && !_isMuted.value) {
                app.audioCaptureManager.startCapture(com.example.voice.microphone.MicState.ACTIVE_VOICE_SESSION)
            }
            return
        }
        Log.i("AlyaViewModel", "Entering low-power standby mode. Stopping speech recognition.")
        _isVoiceStandby.value = true
        speechManager.isContinuousMode = false
        speechManager.stopListening()
        app.audioCaptureManager.stopCapture()
        sessionManager.onLiveVoiceStandby()
        
        viewModelScope.launch(Dispatchers.IO) {
            val kw = repository.preferences.selectedWakeWord.value
            val sens = repository.preferences.wakeWordSensitivity.value
            wakeWordManager.start(kw, sens)
            wakeWordManager.setBatterySaverEnabled(true)
        }
    }

    fun exitStandbyMode(triggerSpeech: Boolean = true, isWakeWordTrigger: Boolean = false) {
        _isVoiceStandby.value = false
        wakeWordManager.stop()
        sessionManager.onLiveVoiceStarted()
        if (_isVoiceMode.value && !_isMuted.value) {
            if (!app.audioCaptureManager.isCaptureActive.value) {
                app.audioCaptureManager.startCapture(com.example.voice.microphone.MicState.ACTIVE_VOICE_SESSION)
            }
            if (!geminiLiveClient.isSessionActive() && triggerSpeech) {
                speechManager.isContinuousMode = true
                startListening(isWakeWordTrigger = isWakeWordTrigger)
            }
        }
    }

    private fun checkAndTriggerBackchannel(partialText: String) {
        val now = System.currentTimeMillis()
        if (now - lastBackchannelTime > 7000L && !hasBackchannelTriggeredThisTurn && !isBackchannelPlaying) {
            val words = partialText.trim().split("\\s+".toRegex())
            if (words.size >= 4 && words.size <= 12) {
                hasBackchannelTriggeredThisTurn = true
                if (java.util.Random().nextFloat() < 0.45f) {
                    triggerBackchannel()
                }
            }
        }
    }

    private fun triggerBackchannel() {
        val backchannels = listOf("hmm", "yeah", "okay", "right")
        val phrase = backchannels.random()
        Log.i("AlyaViewModel", "Naturally playing short conversational backchannel: '$phrase'")
        
        isBackchannelPlaying = true
        lastBackchannelTime = System.currentTimeMillis()
        
        ttsManager.speak(
            text = phrase,
            speechRate = repository.preferences.speechRate.value * 1.1f,
            speechPitch = repository.preferences.speechPitch.value,
            locale = Locale.forLanguageTag(repository.preferences.voiceLanguage.value),
            persona = repository.preferences.voicePersona.value
        )
        
        viewModelScope.launch {
            kotlinx.coroutines.delay(1200L)
            isBackchannelPlaying = false
        }
    }

    // Real-time Voice Mode
    private fun reconnectGeminiLive() {
        if (!sessionManager.isNetworkAvailable.value || !_isVoiceMode.value) return
        
        viewModelScope.launch {
            try {
                Log.i("AlyaViewModel", "Initiating Gemini Live reconnection...")
                geminiLiveClient.attachAudioTrackPlayer(pcmAudioPlayer)
                val activeLang = _currentLanguageLocale.value.ifBlank { repository.preferences.voiceLanguage.value }
                val prefPersona = repository.preferences.voicePersona.value
                val personaToUse = if (prefPersona.isBlank() || prefPersona == "ALYA_ANIME_RUSSIAN") "KORE" else prefPersona
                val geminiVoice = when (personaToUse.uppercase()) {
                    "KORE" -> "Kore"
                    "GENTLE_SOFT", "SOFT_MELODIC", "ALYA_WARM_COMPANION", "WARM_SOFT", "ORIGINAL_HUMAN" -> "Kore"
                    "CRISP_CONFIDENT", "EXECUTIVE", "ALYA_EXECUTIVE_CRISP" -> "Aoede"
                    "LIVELY_PLAYFUL", "ENERGETIC" -> "Aoede"
                    "SWEET_COMPANION", "ANIME_SWEET" -> "Kore"
                    else -> "Kore"
                }

                val systemPrompt = com.example.data.ai.AiPersonality.buildSystemPrompt(
                    isVoiceMode = true,
                    language = activeLang,
                    persona = personaToUse
                )
                
                val tools = getGeminiLiveTools()
                geminiLiveClient.connect(
                    systemInstruction = systemPrompt,
                    targetLanguage = activeLang,
                    voiceName = geminiVoice,
                    tools = tools
                )
            } catch (e: Exception) {
                Log.w("AlyaViewModel", "Failed to reconnect Gemini Live: ${e.message}")
            }
        }
    }

    fun startVoiceMode() {
        clearSubtitles()
        com.example.voice.error.VoiceErrorRegistry.instance.registerNetworkCallback(app)
        wakeWordManager.isSuppressed = true
        wakeWordManager.stop()
        _isVoiceMode.value = true
        _isMuted.value = false // Microphone active by default throughout the live call
        _isThinking.value = false
        _isVoiceStandby.value = false
        _liveAssistantTranscript.value = ""
        speechManager.clearError()
        speechManager.stopListening()
        speechManager.isContinuousMode = false
        _currentScreen.value = AssistantScreen.VOICE_MODE
        audioDeviceManager.requestAudioFocus()
        audioDeviceManager.setSpeakerphone(true) // Default to speakerphone for live conversation
        sessionManager.onLiveVoiceStarted()

        val soundEffects = repository.preferences.soundEffectsEnabled.value
        soundEffectManager.play(com.example.voice.SoundEffectManager.SoundType.VOICE_START, enabled = soundEffects)

        // Keep Alya active in background
        com.example.service.WakeWordService.start(app)

        val activeLang = _currentLanguageLocale.value.ifBlank { repository.preferences.voiceLanguage.value }
        val greetingText = getLiveConversationGreeting(activeLang)

        // Start real hardware microphone audio capture for live stream to Gemini Live
        app.audioCaptureManager.stopCapture()
        app.audioCaptureManager.startCapture(com.example.voice.microphone.MicState.ACTIVE_VOICE_SESSION)

        // If online, prepare Gemini Live connection with high-quality real female voice
        if (sessionManager.isNetworkAvailable.value) {
            try {
                geminiLiveClient.attachAudioTrackPlayer(pcmAudioPlayer)
                val prefPersona = repository.preferences.voicePersona.value
                val personaToUse = if (prefPersona.isBlank() || prefPersona == "ALYA_ANIME_RUSSIAN") "KORE" else prefPersona
                val geminiVoice = when (personaToUse.uppercase()) {
                    "KORE" -> "Kore"
                    "GENTLE_SOFT", "SOFT_MELODIC", "ALYA_WARM_COMPANION", "WARM_SOFT", "ORIGINAL_HUMAN" -> "Kore"
                    "CRISP_CONFIDENT", "EXECUTIVE", "ALYA_EXECUTIVE_CRISP" -> "Aoede"
                    "LIVELY_PLAYFUL", "ENERGETIC" -> "Aoede"
                    "SWEET_COMPANION", "ANIME_SWEET" -> "Kore"
                    else -> "Kore"
                }

                val systemPrompt = com.example.data.ai.AiPersonality.buildSystemPrompt(
                    isVoiceMode = true,
                    language = activeLang,
                    persona = personaToUse
                )
                
                val tools = getGeminiLiveTools()
                geminiLiveClient.connect(
                    systemInstruction = systemPrompt,
                    targetLanguage = activeLang,
                    voiceName = geminiVoice,
                    tools = tools
                )
            } catch (e: Exception) {
                Log.w("AlyaViewModel", "Gemini Live background init notice: ${e.message}")
                speakResponse(greetingText)
            }
        } else {
            // Offline fallback greeting via high-quality local female TTS
            speakResponse(greetingText)
        }
    }

    private fun getLiveConversationGreeting(language: String): String {
        return when (language.lowercase().take(2)) {
            "hi" -> "अरे! मैं अल्या हूँ। आपका दिन कैसा बीत रहा है? मैं आपसे बात करने का ही इंतज़ार कर रही थी!"
            "bn" -> "হেই! আমি আলিয়া। আপনার দিনটি কেমন কাটছে? আমি আপনার সাথে কথা বলার জন্যই অপেক্ষা করছিলাম!"
            "ja" -> "ねえ！アーリャだよ。今日どんな一日だった？お話しできるの待ってたよ！"
            "es" -> "¡Hola! Soy Alya. ¿Cómo va tu día? ¡Estaba esperando para hablar contigo!"
            "fr" -> "Coucou ! Je suis Alya. Comment se passe ta journée ? J'avais hâte de te parler !"
            "de" -> "Hey! Ich bin Alya. Wie läuft dein Tag? Ich habe darauf gewartet, mit dir zu sprechen!"
            "ru" -> "Привет! Я Аля. Как проходит твой день? Я так ждала разговоरा с тобой!"
            else -> "Heyy! I'm Alya. How's your day going? I've been waiting to talk to you!"
        }
    }

    private fun getGeminiLiveTools(): org.json.JSONArray {
        val tools = org.json.JSONArray()
        val funcDecls = org.json.JSONArray()

        fun addTool(name: String, description: String, required: List<String>, properties: Map<String, Pair<String, String>>) {
            val toolObj = org.json.JSONObject().apply {
                put("name", name)
                put("description", description)
                val params = org.json.JSONObject()
                val props = org.json.JSONObject()
                properties.forEach { (propName, propInfo) ->
                    props.put(propName, org.json.JSONObject().apply {
                        put("type", propInfo.first)
                        put("description", propInfo.second)
                    })
                }
                params.put("type", "OBJECT")
                params.put("properties", props)
                if (required.isNotEmpty()) {
                    val reqArray = org.json.JSONArray()
                    required.forEach { reqArray.put(it) }
                    params.put("required", reqArray)
                }
                put("parameters", params)
            }
            funcDecls.put(toolObj)
        }

        // toggle_wifi
        addTool(
            "toggle_wifi",
            "Turns device Wi-Fi on or off or opens instant Wi-Fi panel.",
            listOf("state"),
            mapOf("state" to ("STRING" to "The desired state: 'on' or 'off'."))
        )

        // toggle_bluetooth
        addTool(
            "toggle_bluetooth",
            "Turns device Bluetooth on or off.",
            listOf("state"),
            mapOf("state" to ("STRING" to "The desired state: 'on' or 'off'."))
        )

        // toggle_flashlight
        addTool(
            "toggle_flashlight",
            "Turns device flashlight / torch on or off.",
            listOf("state"),
            mapOf("state" to ("STRING" to "The desired state: 'on', 'off', or 'toggle'."))
        )

        // control_volume
        addTool(
            "control_volume",
            "Adjusts media and device volume.",
            listOf("action"),
            mapOf(
                "action" to ("STRING" to "Action: 'up', 'down', 'mute', 'unmute', or 'set'."),
                "level" to ("INTEGER" to "Target volume level percentage (0-100) if action is 'set'.")
            )
        )

        // control_brightness
        addTool(
            "control_brightness",
            "Adjusts screen brightness level.",
            listOf("action"),
            mapOf(
                "action" to ("STRING" to "Action: 'up', 'down', or 'set'."),
                "level" to ("INTEGER" to "Target brightness percentage (0-100) if action is 'set'.")
            )
        )

        // open_app
        addTool(
            "open_app",
            "Opens an installed application like YouTube, WhatsApp, Camera, Maps, Settings, Calculator, Gallery, Dialer, Messages, etc.",
            listOf("app_name"),
            mapOf("app_name" to ("STRING" to "Name of application (e.g., 'YouTube', 'WhatsApp', 'Camera', 'Maps', 'Settings')."))
        )

        // control_device
        addTool(
            "control_device",
            "Controls system UI navigation like home, back, scroll, lock screen, or screenshot.",
            listOf("action"),
            mapOf(
                "action" to ("STRING" to "Action: 'press_home', 'press_back', 'scroll', 'lock_screen', 'take_screenshot', 'open_app'."),
                "direction" to ("STRING" to "Scroll direction: 'down' or 'up'."),
                "app_name" to ("STRING" to "App name if action is open_app.")
            )
        )

        // control_media
        addTool(
            "control_media",
            "Controls media playback, music, video, or shorts on YouTube, Spotify, or default player.",
            listOf("action"),
            mapOf(
                "action" to ("STRING" to "Action: 'play_video', 'pause', 'resume', 'stop', 'play_music', 'play_shorts'."),
                "query" to ("STRING" to "Search query, song name, or artist.")
            )
        )

        // accessibility_control
        addTool(
            "accessibility_control",
            "Performs hands-free actions like skipping reels/shorts, clicking UI buttons, or liking posts.",
            listOf("action"),
            mapOf(
                "action" to ("STRING" to "Action: 'scroll_down' (skip reel), 'scroll_up' (previous reel), 'like_post', 'click_text'."),
                "text" to ("STRING" to "Optional exact text to click on screen.")
            )
        )

        // browse_web
        addTool(
            "browse_web",
            "Performs web search, weather check, or website opening.",
            listOf("action"),
            mapOf(
                "action" to ("STRING" to "Action: 'search_web', 'get_weather', 'open_website', 'download_file'."),
                "query" to ("STRING" to "Search query or URL."),
                "location" to ("STRING" to "Location city name for weather.")
            )
        )

        // check_weather
        addTool(
            "check_weather",
            "Gets real-time local weather reports, temperature, humidity, and forecasts.",
            emptyList(),
            mapOf("location" to ("STRING" to "City name or 'local' for current location."))
        )

        // create_timer
        addTool(
            "create_timer",
            "Sets a timer on the device clock.",
            listOf("seconds"),
            mapOf(
                "seconds" to ("INTEGER" to "Duration in seconds (e.g. 300 for 5 minutes)."),
                "message" to ("STRING" to "Optional timer label.")
            )
        )

        // create_alarm
        addTool(
            "create_alarm",
            "Sets an alarm for a specific time.",
            listOf("hour", "minute"),
            mapOf(
                "hour" to ("INTEGER" to "Hour (0-23)."),
                "minute" to ("INTEGER" to "Minute (0-59)."),
                "message" to ("STRING" to "Optional alarm label.")
            )
        )

        // make_call
        addTool(
            "make_call",
            "Initiates a phone call to a contact or phone number.",
            listOf("recipient"),
            mapOf("recipient" to ("STRING" to "Contact name or phone number."))
        )

        // answer_call & end_call
        addTool("answer_call", "Answers an incoming ringing call via voice.", emptyList(), emptyMap())
        addTool("end_call", "Ends or declines current or incoming call.", emptyList(), emptyMap())

        // send_whatsapp
        addTool(
            "send_whatsapp",
            "Sends a WhatsApp message to a contact or number.",
            listOf("recipient", "message"),
            mapOf(
                "recipient" to ("STRING" to "Contact name or phone number."),
                "message" to ("STRING" to "Message body text.")
            )
        )

        // prepare_message
        addTool(
            "prepare_message",
            "Sends an SMS text message.",
            listOf("recipient", "body"),
            mapOf(
                "recipient" to ("STRING" to "Contact name or phone number."),
                "body" to ("STRING" to "Message text.")
            )
        )

        // get_device_info
        addTool("get_device_info", "Retrieves device specs, battery level, RAM, storage, and network status.", emptyList(), emptyMap())

        val toolSet = org.json.JSONObject()
        toolSet.put("functionDeclarations", funcDecls)
        tools.put(toolSet)
        return tools
    }

    fun stopVoiceMode() {
        com.example.voice.error.VoiceErrorRegistry.instance.unregisterNetworkCallback()
        _isVoiceMode.value = false
        _isVoiceStandby.value = false
        wakeWordManager.isSuppressed = false
        _liveAssistantTranscript.value = ""

        // Cleanly disconnect Gemini Live WebSocket and PCM AudioTrack Player without pops
        geminiLiveClient.disconnect()
        pcmAudioPlayer.stopCleanly()
        app.audioCaptureManager.stopCapture()
        
        // Centralized clean release of Microphone, Audio Focus, Network Sockets, and Wake word via SessionManager
        sessionManager.releaseActiveSession()
        
        ttsManager.stop()
        val soundEffects = repository.preferences.soundEffectsEnabled.value
        soundEffectManager.play(com.example.voice.SoundEffectManager.SoundType.VOICE_END, enabled = soundEffects)
        if (_currentScreen.value == AssistantScreen.VOICE_MODE) {
            _currentScreen.value = AssistantScreen.CHAT
        }
        
        val isActivated = repository.preferences.isWakeUpActivated.value
        val isCallInProgress = com.example.service.TelephonyService.isCallActive
        if (isActivated && !isCallInProgress) {
            val kw = repository.preferences.selectedWakeWord.value
            val sens = repository.preferences.wakeWordSensitivity.value
            wakeWordManager.start(kw, sens)
            com.example.service.WakeWordService.start(app)
        } else {
            com.example.service.WakeWordService.stop(app)
        }
    }

    fun startListening(isWakeWordTrigger: Boolean = false) {
        if (_isMuted.value) return
        if (com.example.service.TelephonyService.isCallActive) {
            Log.i("AlyaViewModel", "Skipping startListening: System call is currently active.")
            return
        }
        this.isListeningTriggeredByWakeWord = isWakeWordTrigger
        if (!_isVoiceMode.value) {
            _isListeningPopupVisible.value = true
        }
        speechManager.clearError()
        val activeLang = _currentLanguageLocale.value.ifBlank { repository.preferences.voiceLanguage.value }
        speechManager.startListening(activeLang)
    }

    fun stopListening() {
        speechManager.stopListening()
        _isListeningPopupVisible.value = false
    }

    // Direct voice recording state & handlers for chat screen
    private val _isRecordingVoiceMessage = MutableStateFlow(false)
    val isRecordingVoiceMessage: StateFlow<Boolean> = _isRecordingVoiceMessage.asStateFlow()

    private val _voiceInputSentEvent = MutableStateFlow(0L)
    val voiceInputSentEvent: StateFlow<Long> = _voiceInputSentEvent.asStateFlow()

    fun startDirectVoiceRecording() {
        if (ttsManager.isSpeaking.value) {
            ttsManager.stop()
        }
        speechManager.clearError()
        speechManager.isContinuousMode = false
        _isRecordingVoiceMessage.value = true
        startListening()
    }

    fun stopAndSendDirectVoiceRecording(fallbackText: String = "") {
        _isRecordingVoiceMessage.value = false
        val captured = speechManager.partialResult.value.trim().ifBlank { fallbackText.trim() }
        speechManager.stopListening()
        if (captured.isNotBlank()) {
            _voiceInputSentEvent.value = System.currentTimeMillis()
            sendVoiceMessage(captured)
        }
    }

    fun toggleDirectVoiceRecording(currentText: String = "") {
        if (speechManager.isListening.value || _isRecordingVoiceMessage.value) {
            stopAndSendDirectVoiceRecording(currentText)
        } else {
            startDirectVoiceRecording()
        }
    }

    private fun cleanAssistantText(rawText: String): String {
        return com.example.util.SystemThoughtFilter.cleanForDisplay(rawText)
    }

    fun toggleMute() {
        val newMute = !_isMuted.value
        _isMuted.value = newMute
        if (newMute) {
            _liveMicRmsDb.value = 0.0f
            speechManager.stopListening()
        } else {
            if (_isVoiceMode.value && !geminiLiveClient.isSessionActive() && !ttsManager.isSpeaking.value && !_isThinking.value) {
                startListening()
            }
        }
        Log.i("AlyaViewModel", "Toggled mute state: isMuted=$newMute. User mic audio paused; Assistant audio output continues actively.")
    }

    fun toggleSpeaker() {
        audioDeviceManager.toggleSpeakerphone()
    }

    fun toggleBackgroundVoiceMode(enable: Boolean) {
        repository.preferences.setBackgroundVoiceMode(enable)
        if (enable) {
            WakeWordService.start(app)
        } else {
            WakeWordService.stop(app)
        }
    }

    // Action Confirmation
    fun confirmAction(message: MessageEntity) {
        _pendingConfirmationMessage.value = null
        viewModelScope.launch {
            val resultMessage = repository.executeConfirmedAction(message)
            if (_isVoiceMode.value) {
                speakResponse(resultMessage.content)
            }
        }
    }

    fun cancelAction(message: MessageEntity) {
        _pendingConfirmationMessage.value = null
        viewModelScope.launch {
            repository.cancelPendingAction(message)
        }
    }

    // Memory actions
    fun addMemory(category: String, key: String, content: String) {
        viewModelScope.launch {
            repository.addMemory(category, key, content)
        }
    }

    fun deleteMemory(memory: MemoryEntity) {
        viewModelScope.launch {
            repository.deleteMemory(memory)
        }
    }

    fun clearAllMemories() {
        viewModelScope.launch {
            repository.clearAllMemories()
        }
    }

    // Task and Reminder actions
    fun createScheduledTask(title: String, description: String = "", delayMinutes: Int = 15, repeat: String = "NONE") {
        viewModelScope.launch {
            repository.createScheduledTask(title, description, delayMinutes, repeat)
        }
    }

    fun toggleTaskCompleted(task: com.example.data.local.entity.ScheduledTaskEntity) {
        viewModelScope.launch {
            repository.toggleTaskCompleted(task)
        }
    }

    fun deleteScheduledTask(task: com.example.data.local.entity.ScheduledTaskEntity) {
        viewModelScope.launch {
            repository.deleteScheduledTask(task)
        }
    }

    // Device Link actions
    fun startLinkPhone() = deviceLinkManager.startLinkPhoneSession()
    fun startLinkComputer() = deviceLinkManager.startLinkComputerSession()
    fun dismissPairingSession() = deviceLinkManager.dismissPairingSession()
    fun simulateIncomingPairing(deviceType: String) = deviceLinkManager.simulateIncomingPairingRequest(deviceType)
    fun approvePairing() = deviceLinkManager.approvePendingPairing()
    fun rejectPairing() = deviceLinkManager.rejectPendingPairing()
    fun reloadAppAndPhone() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. Reset stuck device link and network connections
                deviceLinkManager.resetStuckConnections()
                deviceLinkManager.refreshAllDevices()

                // 2. Reset voice and wake word subsystems
                speechManager.stopListening()
                speechManager.clearError()
                wakeWordManager.stop()
                val isEnabled = repository.preferences.wakeWordEnabled.value
                val kw = repository.preferences.selectedWakeWord.value
                val sens = repository.preferences.wakeWordSensitivity.value
                if (isEnabled) {
                    wakeWordManager.start(kw, sens)
                }

                // 3. Power state and activity notification
                powerStateManager.notifyUserActivity()
                wakeWordManager.notifyUserActivity()

                // 4. Send success message
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    sendMessage("Phone and app reloaded successfully. All voice engines, wake-word detectors, and device link connections have been refreshed.")
                }
            } catch (e: Exception) {
                Log.e("AlyaViewModel", "Error reloading app and phone: ${e.message}", e)
            }
        }
    }

    fun refreshAllDevices() = deviceLinkManager.refreshAllDevices()
    fun refreshDevice(deviceId: String) = deviceLinkManager.refreshDevice(deviceId)
    fun renameDevice(deviceId: String, newName: String) = deviceLinkManager.renameDevice(deviceId, newName)
    fun unlinkDevice(deviceId: String) = deviceLinkManager.unlinkDevice(deviceId)
    fun reconnectDevice(deviceId: String) = deviceLinkManager.reconnectDevice(deviceId)
    fun updateAccountEmail(newEmail: String) = deviceLinkManager.updateAccountEmail(newEmail)
    fun resetStuckConnections() = deviceLinkManager.resetStuckConnections()

    // In-app Update actions
    fun checkForUpdates(customUrl: String? = null) {
        val url = customUrl ?: repository.preferences.updateCheckUrl.value
        viewModelScope.launch {
            repository.updateManager.checkForUpdate(url)
        }
    }

    fun downloadAndInstallUpdate(targetInfo: AppUpdateInfo? = null) {
        viewModelScope.launch {
            repository.updateManager.downloadAndInstallApk(targetInfo)
        }
    }

    fun resetUpdateStatus() {
        repository.updateManager.resetStatus()
    }

    // App & Update Sharing
    val shareTransferState = appSharingManager.transferState

    suspend fun getSharablePackage() = appSharingManager.getSharablePackage()

    suspend fun createBeamApkIntent() = appSharingManager.createBeamApkIntent()

    suspend fun createShareLinkIntent() = appSharingManager.createShareLinkIntent()

    fun pushUpdateToDevice(deviceId: String, deviceName: String) =
        appSharingManager.pushUpdateToLinkedDevice(deviceId, deviceName)

    fun notifyUserActivity() {
        powerStateManager.notifyUserActivity()
        wakeWordManager.notifyUserActivity()
    }

    fun onAppBackgrounded() {
        viewModelScope.launch(Dispatchers.IO) {
            val allowBackground = _isVoiceMode.value || repository.preferences.isBackgroundVoiceEnabled()
            wakeWordManager.onAppBackgrounded(allowBackground)
        }
    }

    fun onAppForegrounded() {
        powerStateManager.notifyUserActivity()
        if (!_isVoiceMode.value) {
            _currentScreen.value = AssistantScreen.CHAT
        }
        viewModelScope.launch(Dispatchers.IO) {
            val isEnabled = _isVoiceMode.value || repository.preferences.isWakeWordEnabled()
            val kw = repository.preferences.selectedWakeWord.value
            val sens = repository.preferences.wakeWordSensitivity.value
            wakeWordManager.onAppForegrounded(isEnabled, kw, sens)
        }
    }

    fun handleMicrophonePermissionRevoked() {
        Log.w("AlyaViewModel", "Microphone permission has been revoked. Releasing active microphone and voice sessions.")
        try {
            stopListening()
            speechManager.stopListening()
            app.audioCaptureManager.stopCapture()
            sessionManager.releaseActiveSession()
            _isVoiceMode.value = false
            _isVoiceStandby.value = false
            _isListeningPopupVisible.value = false
            _errorMessage.value = "Microphone permission was revoked. Please enable it to use voice features."
        } catch (e: Exception) {
            Log.e("AlyaViewModel", "Error while handling microphone permission revocation: ${e.message}")
        }
    }

    fun clearError() {
        _errorMessage.value = null
        speechManager.clearError()
    }

    fun answerCall() {
        com.example.service.IncomingCallService.answerCall(app)
    }

    fun endCall() {
        com.example.service.IncomingCallService.declineOrEndCall(app)
    }

    fun initiateCall(phoneNumber: String = "") {
        contactManager.placeCall(phoneNumber)
    }

    /**
     * Completely reloads app state, clears transient errors, reconnects services,
     * and resets screen to primary Assistant chat mode.
     */
    fun reloadApp() {
        viewModelScope.launch {
            Log.i("AlyaViewModel", "Reloading app state and reinitializing assistant...")
            try {
                if (_isVoiceMode.value) {
                    stopVoiceMode()
                }
                ttsManager.stop()
                speechManager.stopListening()

                _currentScreen.value = AssistantScreen.CHAT
                _isThinking.value = false

                authManager.loadSavedSession()
                audioDeviceManager.abandonAudioFocus()

                soundEffectManager.play(com.example.voice.SoundEffectManager.SoundType.TASK_SUCCESS)
                Log.i("AlyaViewModel", "App state reloaded successfully.")
            } catch (e: Exception) {
                Log.e("AlyaViewModel", "Error reloading app: ${e.message}", e)
            }
        }
    }

    fun checkForUpdatesOnLaunch() {
        viewModelScope.launch {
            val prefs = app.getSharedPreferences("alya_update_prefs", Context.MODE_PRIVATE)
            val lastCheck = prefs.getLong("last_check_timestamp", 0L)
            val now = System.currentTimeMillis()
            if (now - lastCheck < 86400000L) {
                Log.i("AlyaViewModel", "Skipping auto update check. Last checked less than 24 hours ago.")
                return@launch
            }
            val url = repository.preferences.updateCheckUrl.value
            val result = updateManager.checkForUpdate(url)
            if (result.isSuccess) {
                val info = result.getOrThrow()
                if (info.isUpdateAvailable) {
                    _showUpdatePrompt.value = true
                }
                prefs.edit().putLong("last_check_timestamp", System.currentTimeMillis()).apply()
            }
        }
    }

    fun checkForUpdatesManual() {
        viewModelScope.launch {
            updateManager.checkForUpdate(repository.preferences.updateCheckUrl.value)
        }
    }

    fun startNewUpdateDownload() {
        viewModelScope.launch {
            updateManager.downloadAndInstallApk()
        }
    }

    fun cancelNewUpdateDownload() {
        updateManager.resetStatus()
    }

    fun dismissUpdatePrompt() {
        _showUpdatePrompt.value = false
        updateManager.resetStatus()
    }

    override fun onCleared() {
        super.onCleared()
        wakeWordManager.disposeNativeResources()
        speechManager.stopListening()
        ttsManager.stop()
        audioDeviceManager.abandonAudioFocus()
    }
}
