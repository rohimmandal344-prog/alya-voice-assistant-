package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("alya_preferences", Context.MODE_PRIVATE)

    // Flow for reactive UI updates
    private val _memoryEnabled = MutableStateFlow(prefs.getBoolean(KEY_MEMORY_ENABLED, true))
    val memoryEnabled: StateFlow<Boolean> = _memoryEnabled.asStateFlow()

    private val _backgroundVoiceMode = MutableStateFlow(prefs.getBoolean(KEY_BG_VOICE_MODE, false))
    val backgroundVoiceMode: StateFlow<Boolean> = _backgroundVoiceMode.asStateFlow()

    private val _wakeWordEnabled = MutableStateFlow(prefs.getBoolean(KEY_WAKE_WORD_ENABLED, true))
    val wakeWordEnabled: StateFlow<Boolean> = _wakeWordEnabled.asStateFlow()

    private val _isWakeUpActivated = MutableStateFlow(prefs.getBoolean(KEY_WAKE_UP_ACTIVATED, true))
    val isWakeUpActivated: StateFlow<Boolean> = _isWakeUpActivated.asStateFlow()

    private val _isVoiceProfileSet = MutableStateFlow(prefs.getBoolean(KEY_VOICE_PROFILE_SET, false))
    val isVoiceProfileSet: StateFlow<Boolean> = _isVoiceProfileSet.asStateFlow()

    private val _voiceProfileCreatedAt = MutableStateFlow(prefs.getLong(KEY_VOICE_PROFILE_CREATED_AT, 0L))
    val voiceProfileCreatedAt: StateFlow<Long> = _voiceProfileCreatedAt.asStateFlow()

    private val _voiceProfileSampleCount = MutableStateFlow(prefs.getInt(KEY_VOICE_PROFILE_SAMPLE_COUNT, 0))
    val voiceProfileSampleCount: StateFlow<Int> = _voiceProfileSampleCount.asStateFlow()

    private val _voiceEnrollmentStep = MutableStateFlow(prefs.getInt(KEY_VOICE_ENROLLMENT_STEP, 0))
    val voiceEnrollmentStep: StateFlow<Int> = _voiceEnrollmentStep.asStateFlow()

    private val _wakeWordSensitivity = MutableStateFlow(prefs.getFloat(KEY_WAKE_WORD_SENSITIVITY, 0.5f))
    val wakeWordSensitivity: StateFlow<Float> = _wakeWordSensitivity.asStateFlow()

    private val _wakeWordBatterySaver = MutableStateFlow(prefs.getBoolean(KEY_WAKE_WORD_BATTERY_SAVER, true))
    val wakeWordBatterySaver: StateFlow<Boolean> = _wakeWordBatterySaver.asStateFlow()

    private val _wakeWordAckSoundEnabled = MutableStateFlow(prefs.getBoolean(KEY_WAKE_WORD_ACK_SOUND, true))
    val wakeWordAckSoundEnabled: StateFlow<Boolean> = _wakeWordAckSoundEnabled.asStateFlow()

    private val _ultraLowLatencyMode = MutableStateFlow(prefs.getBoolean(KEY_ULTRA_LOW_LATENCY_MODE, true))
    val ultraLowLatencyMode: StateFlow<Boolean> = _ultraLowLatencyMode.asStateFlow()

    private val _fpsBoostEnabled = MutableStateFlow(prefs.getBoolean(KEY_FPS_BOOST_ENABLED, true))
    val fpsBoostEnabled: StateFlow<Boolean> = _fpsBoostEnabled.asStateFlow()

    private val triggerWordConfig = com.example.voice.wakeword.TriggerWordConfig(context)
    private val _selectedWakeWord = MutableStateFlow(triggerWordConfig.triggerWord)
    val selectedWakeWord: StateFlow<String> = _selectedWakeWord.asStateFlow()

    private val _floatingBubbleEnabled = MutableStateFlow(prefs.getBoolean(KEY_FLOATING_BUBBLE_ENABLED, false))
    val floatingBubbleEnabled: StateFlow<Boolean> = _floatingBubbleEnabled.asStateFlow()

    private val _doubleTapShortcutEnabled = MutableStateFlow(prefs.getBoolean(KEY_DOUBLE_TAP_SHORTCUT, true))
    val doubleTapShortcutEnabled: StateFlow<Boolean> = _doubleTapShortcutEnabled.asStateFlow()

    private val _autoAnswerCallsWithAi = MutableStateFlow(prefs.getBoolean(KEY_AUTO_ANSWER_CALLS_WITH_AI, true))
    val autoAnswerCallsWithAi: StateFlow<Boolean> = _autoAnswerCallsWithAi.asStateFlow()

    fun setAutoAnswerCallsWithAi(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_ANSWER_CALLS_WITH_AI, enabled).apply()
        _autoAnswerCallsWithAi.value = enabled
    }

    fun setDoubleTapShortcutEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DOUBLE_TAP_SHORTCUT, enabled).apply()
        _doubleTapShortcutEnabled.value = enabled
    }

    fun setFpsBoostEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FPS_BOOST_ENABLED, enabled).apply()
        _fpsBoostEnabled.value = enabled
    }

    private val _confirmationLevel = MutableStateFlow(prefs.getString(KEY_CONFIRMATION_LEVEL, "IMPORTANT") ?: "IMPORTANT")
    val confirmationLevel: StateFlow<String> = _confirmationLevel.asStateFlow()

    private val _soundEffectsEnabled = MutableStateFlow(prefs.getBoolean(KEY_SOUND_EFFECTS_ENABLED, true))
    val soundEffectsEnabled: StateFlow<Boolean> = _soundEffectsEnabled.asStateFlow()

    private val _themeMode = MutableStateFlow(prefs.getString(KEY_THEME_MODE, "SYSTEM") ?: "SYSTEM")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _speechRate = MutableStateFlow(prefs.getFloat(KEY_SPEECH_RATE, 1.02f))
    val speechRate: StateFlow<Float> = _speechRate.asStateFlow()

    private val _speechPitch = MutableStateFlow(prefs.getFloat(KEY_SPEECH_PITCH, 1.05f))
    val speechPitch: StateFlow<Float> = _speechPitch.asStateFlow()

    private val _voiceLanguage = MutableStateFlow(prefs.getString(KEY_VOICE_LANGUAGE, "en-US") ?: "en-US")
    val voiceLanguage: StateFlow<String> = _voiceLanguage.asStateFlow()

    private val _learningLanguage = MutableStateFlow(prefs.getString(KEY_LEARNING_LANGUAGE, "ja-JP") ?: "ja-JP")
    val learningLanguage: StateFlow<String> = _learningLanguage.asStateFlow()

    private val _voicePersona = MutableStateFlow(
        prefs.getString(KEY_VOICE_PERSONA, "KORE").let { if (it == "ALYA_ANIME_RUSSIAN" || it.isNullOrBlank()) "KORE" else it }
    )
    val voicePersona: StateFlow<String> = _voicePersona.asStateFlow()

    private val _responseStyle = MutableStateFlow(prefs.getString(KEY_RESPONSE_STYLE, "Balanced") ?: "Balanced")
    val responseStyle: StateFlow<String> = _responseStyle.asStateFlow()

    private val _silenceTimeoutSeconds = MutableStateFlow(prefs.getInt(KEY_SILENCE_TIMEOUT, 5))
    val silenceTimeoutSeconds: StateFlow<Int> = _silenceTimeoutSeconds.asStateFlow()

    fun setSilenceTimeoutSeconds(seconds: Int) {
        prefs.edit().putInt(KEY_SILENCE_TIMEOUT, seconds).apply()
        _silenceTimeoutSeconds.value = seconds
    }

    private val _updateCheckUrl = MutableStateFlow(
        prefs.getString(KEY_UPDATE_CHECK_URL, DEFAULT_UPDATE_URL)?.let {
            if (it.contains("daily-walking-guide-apk") || it.isBlank()) DEFAULT_UPDATE_URL else it
        } ?: DEFAULT_UPDATE_URL
    )
    val updateCheckUrl: StateFlow<String> = _updateCheckUrl.asStateFlow()

    fun setUpdateCheckUrl(url: String) {
        prefs.edit().putString(KEY_UPDATE_CHECK_URL, url).apply()
        _updateCheckUrl.value = url
    }

    fun setMemoryEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MEMORY_ENABLED, enabled).apply()
        _memoryEnabled.value = enabled
    }

    fun setBackgroundVoiceMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BG_VOICE_MODE, enabled).apply()
        _backgroundVoiceMode.value = enabled
    }

    fun setConfirmationLevel(level: String) {
        prefs.edit().putString(KEY_CONFIRMATION_LEVEL, level).apply()
        _confirmationLevel.value = level
    }

    fun setSoundEffectsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SOUND_EFFECTS_ENABLED, enabled).apply()
        _soundEffectsEnabled.value = enabled
    }

    fun setThemeMode(mode: String) {
        prefs.edit().putString(KEY_THEME_MODE, mode).apply()
        _themeMode.value = mode
    }

    fun setSpeechRate(rate: Float) {
        prefs.edit().putFloat(KEY_SPEECH_RATE, rate).apply()
        _speechRate.value = rate
    }

    fun setSpeechPitch(pitch: Float) {
        prefs.edit().putFloat(KEY_SPEECH_PITCH, pitch).apply()
        _speechPitch.value = pitch
    }

    fun setVoiceLanguage(lang: String) {
        prefs.edit().putString(KEY_VOICE_LANGUAGE, lang).apply()
        _voiceLanguage.value = lang
    }

    fun setLearningLanguage(lang: String) {
        prefs.edit().putString(KEY_LEARNING_LANGUAGE, lang).apply()
        _learningLanguage.value = lang
    }

    fun setVoicePersona(persona: String) {
        prefs.edit().putString(KEY_VOICE_PERSONA, persona).apply()
        _voicePersona.value = persona
    }

    fun setResponseStyle(style: String) {
        prefs.edit().putString(KEY_RESPONSE_STYLE, style).apply()
        _responseStyle.value = style
    }

    fun setWakeWordEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WAKE_WORD_ENABLED, enabled).apply()
        _wakeWordEnabled.value = enabled
    }

    fun setWakeUpActivated(activated: Boolean) {
        prefs.edit().putBoolean(KEY_WAKE_UP_ACTIVATED, activated).apply()
        _isWakeUpActivated.value = activated
        if (activated) {
            setWakeWordEnabled(true)
        }
    }

    fun deactivateWakeUp() {
        setWakeUpActivated(false)
        setWakeWordEnabled(false)
    }

    fun activateWakeUp() {
        setWakeUpActivated(true)
        setWakeWordEnabled(true)
    }

    fun setVoiceEnrollmentStep(step: Int) {
        prefs.edit().putInt(KEY_VOICE_ENROLLMENT_STEP, step).apply()
        _voiceEnrollmentStep.value = step
    }

    fun setVoiceProfileCompleted(sampleCount: Int = 3, timestamp: Long = System.currentTimeMillis()) {
        prefs.edit()
            .putBoolean(KEY_VOICE_PROFILE_SET, true)
            .putLong(KEY_VOICE_PROFILE_CREATED_AT, timestamp)
            .putInt(KEY_VOICE_PROFILE_SAMPLE_COUNT, sampleCount)
            .putBoolean(KEY_WAKE_UP_ACTIVATED, true)
            .putBoolean(KEY_WAKE_WORD_ENABLED, true)
            .apply()
        _isVoiceProfileSet.value = true
        _voiceProfileCreatedAt.value = timestamp
        _voiceProfileSampleCount.value = sampleCount
        _isWakeUpActivated.value = true
        _wakeWordEnabled.value = true
    }

    fun deleteUserVoiceProfile() {
        prefs.edit()
            .putBoolean(KEY_VOICE_PROFILE_SET, false)
            .putLong(KEY_VOICE_PROFILE_CREATED_AT, 0L)
            .putInt(KEY_VOICE_PROFILE_SAMPLE_COUNT, 0)
            .putBoolean(KEY_WAKE_UP_ACTIVATED, false)
            .putBoolean(KEY_WAKE_WORD_ENABLED, false)
            .apply()
        _isVoiceProfileSet.value = false
        _voiceProfileCreatedAt.value = 0L
        _voiceProfileSampleCount.value = 0
        _isWakeUpActivated.value = false
        _wakeWordEnabled.value = false
    }

    fun setWakeWordSensitivity(sensitivity: Float) {
        prefs.edit().putFloat(KEY_WAKE_WORD_SENSITIVITY, sensitivity).apply()
        _wakeWordSensitivity.value = sensitivity
    }

    fun setWakeWordBatterySaver(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WAKE_WORD_BATTERY_SAVER, enabled).apply()
        _wakeWordBatterySaver.value = enabled
    }

    fun setWakeWordAckSoundEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WAKE_WORD_ACK_SOUND, enabled).apply()
        _wakeWordAckSoundEnabled.value = enabled
    }

    fun setUltraLowLatencyMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ULTRA_LOW_LATENCY_MODE, enabled).apply()
        _ultraLowLatencyMode.value = enabled
    }

    fun setSelectedWakeWord(keyword: String) {
        if (com.example.voice.wakeword.TriggerWordConfig.isValid(keyword)) {
            triggerWordConfig.triggerWord = keyword.lowercase()
            _selectedWakeWord.value = triggerWordConfig.triggerWord
        } else {
            throw IllegalArgumentException("Invalid trigger word. Must be 2-20 letters without spaces/special chars.")
        }
    }

    fun setFloatingBubbleEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FLOATING_BUBBLE_ENABLED, enabled).apply()
        _floatingBubbleEnabled.value = enabled
    }

    private val _isContinuousConversationEnabled = MutableStateFlow(prefs.getBoolean(KEY_CONTINUOUS_CONVO, true))
    val isContinuousConversationEnabled: StateFlow<Boolean> = _isContinuousConversationEnabled.asStateFlow()

    private val _onlyOwnerVoiceWakes = MutableStateFlow(prefs.getBoolean(KEY_ONLY_OWNER_VOICE_WAKES, true))
    val onlyOwnerVoiceWakes: StateFlow<Boolean> = _onlyOwnerVoiceWakes.asStateFlow()

    private val _pauseDuringCallsAndRecording = MutableStateFlow(prefs.getBoolean(KEY_PAUSE_DURING_CALLS, true))
    val pauseDuringCallsAndRecording: StateFlow<Boolean> = _pauseDuringCallsAndRecording.asStateFlow()

    private val _neverWakeDuringPlayback = MutableStateFlow(prefs.getBoolean(KEY_NEVER_WAKE_DURING_PLAYBACK, true))
    val neverWakeDuringPlayback: StateFlow<Boolean> = _neverWakeDuringPlayback.asStateFlow()

    private val _speakerEmbeddingData = MutableStateFlow(prefs.getString(KEY_SPEAKER_EMBEDDING, "") ?: "")
    val speakerEmbeddingData: StateFlow<String> = _speakerEmbeddingData.asStateFlow()

    fun setOnlyOwnerVoiceWakes(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ONLY_OWNER_VOICE_WAKES, enabled).apply()
        _onlyOwnerVoiceWakes.value = enabled
    }

    fun setPauseDuringCallsAndRecording(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PAUSE_DURING_CALLS, enabled).apply()
        _pauseDuringCallsAndRecording.value = enabled
    }

    fun setNeverWakeDuringPlayback(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NEVER_WAKE_DURING_PLAYBACK, enabled).apply()
        _neverWakeDuringPlayback.value = enabled
    }

    fun setSpeakerEmbeddingData(data: String) {
        prefs.edit().putString(KEY_SPEAKER_EMBEDDING, data).apply()
        _speakerEmbeddingData.value = data
    }

    fun setContinuousConversationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CONTINUOUS_CONVO, enabled).apply()
        _isContinuousConversationEnabled.value = enabled
    }

    fun isContinuousConversationEnabled(): Boolean = prefs.getBoolean(KEY_CONTINUOUS_CONVO, true)

    private val _lastSeenVersion = MutableStateFlow(
        prefs.getString(KEY_LAST_SEEN_VERSION, "") ?: ""
    )
    val lastSeenVersion: StateFlow<String> = _lastSeenVersion.asStateFlow()

    fun getLastSeenVersion(): String = prefs.getString(KEY_LAST_SEEN_VERSION, "") ?: ""

    fun setLastSeenVersion(version: String) {
        // Use commit() for critical version persistence to ensure it's written before any potential crash or close
        prefs.edit().putString(KEY_LAST_SEEN_VERSION, version).commit()
        _lastSeenVersion.value = version
    }

    fun reloadAll() {
        _memoryEnabled.value = prefs.getBoolean(KEY_MEMORY_ENABLED, true)
        _backgroundVoiceMode.value = prefs.getBoolean(KEY_BG_VOICE_MODE, false)
        _wakeWordEnabled.value = prefs.getBoolean(KEY_WAKE_WORD_ENABLED, true)
        _isWakeUpActivated.value = prefs.getBoolean(KEY_WAKE_UP_ACTIVATED, true)
        _isVoiceProfileSet.value = prefs.getBoolean(KEY_VOICE_PROFILE_SET, false)
        _voiceProfileCreatedAt.value = prefs.getLong(KEY_VOICE_PROFILE_CREATED_AT, 0L)
        _voiceProfileSampleCount.value = prefs.getInt(KEY_VOICE_PROFILE_SAMPLE_COUNT, 0)
        _voiceEnrollmentStep.value = prefs.getInt(KEY_VOICE_ENROLLMENT_STEP, 0)
        _wakeWordSensitivity.value = prefs.getFloat(KEY_WAKE_WORD_SENSITIVITY, 0.5f)
        _wakeWordBatterySaver.value = prefs.getBoolean(KEY_WAKE_WORD_BATTERY_SAVER, true)
        _wakeWordAckSoundEnabled.value = prefs.getBoolean(KEY_WAKE_WORD_ACK_SOUND, true)
        _ultraLowLatencyMode.value = prefs.getBoolean(KEY_ULTRA_LOW_LATENCY_MODE, true)
        _fpsBoostEnabled.value = prefs.getBoolean(KEY_FPS_BOOST_ENABLED, true)
        _selectedWakeWord.value = triggerWordConfig.triggerWord
        _floatingBubbleEnabled.value = prefs.getBoolean(KEY_FLOATING_BUBBLE_ENABLED, false)
        _doubleTapShortcutEnabled.value = prefs.getBoolean(KEY_DOUBLE_TAP_SHORTCUT, true)
        _confirmationLevel.value = prefs.getString(KEY_CONFIRMATION_LEVEL, "IMPORTANT") ?: "IMPORTANT"
        _soundEffectsEnabled.value = prefs.getBoolean(KEY_SOUND_EFFECTS_ENABLED, true)
        _themeMode.value = prefs.getString(KEY_THEME_MODE, "SYSTEM") ?: "SYSTEM"
        _speechRate.value = prefs.getFloat(KEY_SPEECH_RATE, 1.00f)
        _speechPitch.value = prefs.getFloat(KEY_SPEECH_PITCH, 1.00f)
        _voiceLanguage.value = prefs.getString(KEY_VOICE_LANGUAGE, "en-US") ?: "en-US"
        _learningLanguage.value = prefs.getString(KEY_LEARNING_LANGUAGE, "ja-JP") ?: "ja-JP"
        _voicePersona.value = prefs.getString(KEY_VOICE_PERSONA, "KORE").let { if (it == "ALYA_ANIME_RUSSIAN" || it.isNullOrBlank()) "KORE" else it }
        _responseStyle.value = prefs.getString(KEY_RESPONSE_STYLE, "Balanced") ?: "Balanced"
        _silenceTimeoutSeconds.value = prefs.getInt(KEY_SILENCE_TIMEOUT, 5)
        _updateCheckUrl.value = prefs.getString(KEY_UPDATE_CHECK_URL, DEFAULT_UPDATE_URL)?.let {
            if (it.contains("daily-walking-guide-apk") || it.isBlank()) DEFAULT_UPDATE_URL else it
        } ?: DEFAULT_UPDATE_URL
        _isContinuousConversationEnabled.value = prefs.getBoolean(KEY_CONTINUOUS_CONVO, true)
        _onlyOwnerVoiceWakes.value = prefs.getBoolean(KEY_ONLY_OWNER_VOICE_WAKES, true)
        _pauseDuringCallsAndRecording.value = prefs.getBoolean(KEY_PAUSE_DURING_CALLS, true)
        _neverWakeDuringPlayback.value = prefs.getBoolean(KEY_NEVER_WAKE_DURING_PLAYBACK, true)
        _speakerEmbeddingData.value = prefs.getString(KEY_SPEAKER_EMBEDDING, "") ?: ""
        _lastSeenVersion.value = prefs.getString(KEY_LAST_SEEN_VERSION, "") ?: ""
    }

    fun isMemoryEnabled(): Boolean = prefs.getBoolean(KEY_MEMORY_ENABLED, true)
    fun isBackgroundVoiceEnabled(): Boolean = prefs.getBoolean(KEY_BG_VOICE_MODE, false)
    fun isWakeWordEnabled(): Boolean = prefs.getBoolean(KEY_WAKE_WORD_ENABLED, true)
    fun isFloatingBubbleEnabled(): Boolean = prefs.getBoolean(KEY_FLOATING_BUBBLE_ENABLED, false)
    fun getConfirmationLevel(): String = prefs.getString(KEY_CONFIRMATION_LEVEL, "IMPORTANT") ?: "IMPORTANT"

    companion object {
        private const val KEY_MEMORY_ENABLED = "key_memory_enabled"
        private const val KEY_BG_VOICE_MODE = "key_bg_voice_mode"
        private const val KEY_WAKE_WORD_ENABLED = "key_wake_word_enabled"
        private const val KEY_WAKE_UP_ACTIVATED = "key_wake_up_activated"
        private const val KEY_VOICE_PROFILE_SET = "key_voice_profile_set"
        private const val KEY_VOICE_PROFILE_CREATED_AT = "key_voice_profile_created_at"
        private const val KEY_VOICE_PROFILE_SAMPLE_COUNT = "key_voice_profile_sample_count"
        private const val KEY_VOICE_ENROLLMENT_STEP = "key_voice_enrollment_step"
        private const val KEY_WAKE_WORD_SENSITIVITY = "key_wake_word_sensitivity"
        private const val KEY_WAKE_WORD_BATTERY_SAVER = "key_wake_word_battery_saver"
        private const val KEY_WAKE_WORD_ACK_SOUND = "key_wake_word_ack_sound"
        private const val KEY_ULTRA_LOW_LATENCY_MODE = "key_ultra_low_latency_mode"
        private const val KEY_FPS_BOOST_ENABLED = "key_fps_boost_enabled"
        private const val KEY_SELECTED_WAKE_WORD = "key_selected_wake_word"
        private const val KEY_FLOATING_BUBBLE_ENABLED = "key_floating_bubble_enabled"
        private const val KEY_DOUBLE_TAP_SHORTCUT = "key_double_tap_shortcut"
        private const val KEY_AUTO_ANSWER_CALLS_WITH_AI = "key_auto_answer_calls_with_ai"
        private const val KEY_CONFIRMATION_LEVEL = "key_confirmation_level"
        private const val KEY_SOUND_EFFECTS_ENABLED = "key_sound_effects_enabled"
        private const val KEY_THEME_MODE = "key_theme_mode"
        private const val KEY_SPEECH_RATE = "key_speech_rate"
        private const val KEY_SPEECH_PITCH = "key_speech_pitch"
        private const val KEY_VOICE_LANGUAGE = "key_voice_language"
        private const val KEY_LEARNING_LANGUAGE = "key_learning_language"
        private const val KEY_VOICE_PERSONA = "key_voice_persona"
        private const val KEY_RESPONSE_STYLE = "key_response_style"
        private const val KEY_SILENCE_TIMEOUT = "key_silence_timeout"
        private const val KEY_UPDATE_CHECK_URL = "key_update_check_url"
        private const val KEY_LAST_SEEN_VERSION = "key_last_seen_version"
        private const val KEY_CONTINUOUS_CONVO = "key_continuous_convo"
        private const val KEY_ONLY_OWNER_VOICE_WAKES = "key_only_owner_voice_wakes"
        private const val KEY_PAUSE_DURING_CALLS = "key_pause_during_calls"
        private const val KEY_NEVER_WAKE_DURING_PLAYBACK = "key_never_wake_during_playback"
        private const val KEY_SPEAKER_EMBEDDING = "key_speaker_embedding"
        const val DEFAULT_UPDATE_URL = "https://api.github.com/repos/alya-assistant/alya/releases/latest"

        val AVAILABLE_VOICE_PERSONAS = listOf(
            VoicePersonaOption("KORE", "Kore • Natural & Warm", "Balanced, human-like warm expressive tone"),
            VoicePersonaOption("GENTLE_SOFT", "Alya • Gentle & Soft", "Soothing, calm, melodic cadence"),
            VoicePersonaOption("CRISP_CONFIDENT", "Alya • Crisp & Confident", "Clear, articulate executive tone"),
            VoicePersonaOption("LIVELY_PLAYFUL", "Alya • Lively & Expressive", "Upbeat, energetic and bright tone"),
            VoicePersonaOption("SWEET_COMPANION", "Alya • Sweet Companion", "Friendly, cheerful sweet tone")
        )
    }

    data class VoicePersonaOption(
        val id: String,
        val displayName: String,
        val description: String
    )
}
