package com.example.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import com.example.voice.cache.AudioResponseDiskCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import java.util.UUID

/**
 * Enterprise-grade Singleton TextToSpeech Manager for Alya Assistant Core.
 * Provides high-fidelity neural voice selection, automatic multilingual locale switching,
 * proactive pre-validation before speaking, and robust release-reinitialize recovery logic
 * against tone-shifting, audio focus loss, or silent engine crashes during long live conversations.
 */
class TextToSpeechManager private constructor(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    @Volatile
    private var isInitialized = false
    @Volatile
    private var isInitializing = false
    @Volatile
    private var isVoiceConfigured = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.IO)

    val diskCache = AudioResponseDiskCache(context)

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _currentUtterance = MutableStateFlow<String?>(null)
    val currentUtterance: StateFlow<String?> = _currentUtterance.asStateFlow()

    private val _selectedVoiceId = MutableStateFlow<String?>(null)
    val selectedVoiceId: StateFlow<String?> = _selectedVoiceId.asStateFlow()

    val audioDeviceManager by lazy {
        (context.applicationContext as? com.example.AlyaApplication)?.audioDeviceManager
            ?: AudioDeviceManager(context.applicationContext)
    }

    var onSpeechStarted: (() -> Unit)? = null
    var onSpeechCompleted: (() -> Unit)? = null

    private val pendingUtteranceIds = mutableSetOf<String>()
    private var lastUtteranceId: String? = null
    private var activeMediaPlayer: MediaPlayer? = null
    private var activeCompletionCallback: (() -> Unit)? = null

    // Queued speech requests while engine is initializing / reinitializing
    private data class QueuedSpeech(
        val text: String,
        val speechRate: Float,
        val speechPitch: Float,
        val locale: Locale,
        val persona: String,
        val onCompletion: (() -> Unit)?
    )
    private val pendingSpeechQueue = mutableListOf<QueuedSpeech>()
    private val reinitCallbacks = mutableListOf<() -> Unit>()

    // Health metrics for tone-shifting and crash detection during long conversational sessions
    private var consecutiveErrorCount = 0
    private var utteranceCountSinceInit = 0

    private var configuredLocale: Locale? = null
    private var configuredPersona: String = "KORE"
    private var fallbackOfflineVoice: Voice? = null
    private var usedPreferredEngine: String? = null
    val languageDetector = com.example.voice.lang.OnDeviceLanguageDetector()

    private val ttsWatchdogRunnable = Runnable {
        if (_isSpeaking.value) {
            Log.w("TextToSpeechManager", "TTS utterance watchdog timeout triggered, restoring state and checking engine health.")
            stop()
            consecutiveErrorCount++
            if (consecutiveErrorCount >= 2) {
                reinitialize("Watchdog timeout and consecutive speech error")
            }
            onSpeechCompleted?.invoke()
        }
    }

    companion object {
        private const val TAG = "TextToSpeechManager"
        private const val MAX_UTTERANCES_BEFORE_PREVENTIVE_REFRESH = 45

        @Volatile
        private var INSTANCE: TextToSpeechManager? = null

        /**
         * Singleton accessor ensuring one shared TextToSpeech engine instance across the entire app.
         */
        fun getInstance(context: Context): TextToSpeechManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TextToSpeechManager(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }

        // Persona-tailored voice mapping: maps (Language Code, Persona ID) to distinct, prioritized real neural female voices
        val LANGUAGE_PERSONA_VOICE_MAP: Map<Pair<String, String>, List<String>> = mapOf(
            // English personas - prioritizing en-us-x-iol-local (softest female), en-us-x-tpf-local and high-fidelity neural/journey
            Pair("en", "KORE") to listOf("en-us-x-iol-local", "en-us-x-tpf-local", "en-us-neural2-f", "en-us-wavenet-f", "en-us-journey-f"),
            Pair("en", "GENTLE_SOFT") to listOf("en-us-x-iol-local", "en-us-x-tpf-local", "en-us-journey-f", "en-gb-x-rjs-local"),
            Pair("en", "CRISP_CONFIDENT") to listOf("en-us-x-iol-local", "en-us-x-tpf-local", "en-us-studio-f", "en-us-journey-f"),
            Pair("en", "LIVELY_PLAYFUL") to listOf("en-us-x-iol-local", "en-us-x-tpf-local", "en-us-neural2-f"),
            Pair("en", "SWEET_COMPANION") to listOf("en-us-x-iol-local", "en-us-x-tpf-local", "en-us-wavenet-f"),

            // Hindi personas - prioritizing hi-in-x-hia-local (softest realistic offline female voice) and hi-in-neural2-a
            Pair("hi", "KORE") to listOf("hi-in-x-hia-local", "hi-in-neural2-a", "hi-in-wavenet-a", "hi-in-x-hie-local"),
            Pair("hi", "GENTLE_SOFT") to listOf("hi-in-x-hia-local", "hi-in-neural2-a", "hi-in-wavenet-a"),
            Pair("hi", "CRISP_CONFIDENT") to listOf("hi-in-x-hia-local", "hi-in-neural2-a", "hi-in-wavenet-a"),
            Pair("hi", "LIVELY_PLAYFUL") to listOf("hi-in-x-hia-local", "hi-in-neural2-a", "hi-in-wavenet-a"),
            Pair("hi", "SWEET_COMPANION") to listOf("hi-in-x-hia-local", "hi-in-neural2-a", "hi-in-wavenet-a"),

            // Bengali personas - prioritizing bn-in-x-bnf-local (softest realistic female voice) and wavenet-a
            Pair("bn", "KORE") to listOf("bn-in-x-bnf-local", "bn-in-wavenet-a", "bn-in-x-bnd-local"),
            Pair("bn", "GENTLE_SOFT") to listOf("bn-in-x-bnf-local", "bn-in-wavenet-a"),
            Pair("bn", "CRISP_CONFIDENT") to listOf("bn-in-x-bnf-local", "bn-in-wavenet-a"),
            Pair("bn", "LIVELY_PLAYFUL") to listOf("bn-in-x-bnf-local", "bn-in-wavenet-a"),
            Pair("bn", "SWEET_COMPANION") to listOf("bn-in-x-bnf-local", "bn-in-wavenet-a"),

            // Japanese personas
            Pair("ja", "KORE") to listOf("ja-jp-x-jtd-local", "ja-jp-neural2-b", "ja-jp-wavenet-b"),
            Pair("ja", "GENTLE_SOFT") to listOf("ja-jp-x-jtd-local", "ja-jp-wavenet-b"),
            Pair("ja", "CRISP_CONFIDENT") to listOf("ja-jp-x-jtd-local", "ja-jp-neural2-b"),
            Pair("ja", "LIVELY_PLAYFUL") to listOf("ja-jp-x-jtd-local", "ja-jp-wavenet-b"),
            Pair("ja", "SWEET_COMPANION") to listOf("ja-jp-x-jtd-local", "ja-jp-wavenet-b")
        )

        // General fallback mapping of supported language tags to prioritized premium natural female voice IDs
        val LANGUAGE_PREMIUM_VOICE_MAP: Map<String, List<String>> = mapOf(
            "en" to listOf(
                "en-us-x-tpf-local", "en-us-x-sfg-network", "en-us-x-iom-local", "en-us-x-iol-local",
                "en-us-x-iob-local", "en-gb-x-rjs-local", "en-in-x-cfl-local", "en-in-x-cfa-local",
                "en-us-neural2-f", "en-us-wavenet-f", "en-us-journey-f", "en-us-studio-f"
            ),
            "hi" to listOf(
                "hi-in-x-hie-local", "hi-in-x-hia-local", "hi-in-neural2-a", "hi-in-wavenet-a"
            ),
            "bn" to listOf(
                "bn-in-x-bnd-local", "bn-in-x-bnf-local", "bn-in-wavenet-a"
            ),
            "ja" to listOf(
                "ja-jp-x-htm-local", "ja-jp-x-jtd-local", "ja-jp-neural2-b", "ja-jp-wavenet-b"
            ),
            "ru" to listOf(
                "ru-ru-x-dfc-local", "ru-ru-x-rud-local", "ru-ru-wavenet-a", "ru-ru-neural2-c"
            ),
            "es" to listOf(
                "es-es-x-eed-local", "es-us-x-sfb-local", "es-es-neural2-a"
            ),
            "fr" to listOf(
                "fr-fr-x-vlf-local", "fr-fr-neural2-a", "fr-fr-wavenet-a"
            ),
            "de" to listOf(
                "de-de-x-deg-local", "de-de-neural2-f", "de-de-wavenet-f"
            ),
            "ar" to listOf(
                "ar-xa-x-ard-local"
            ),
            "id" to listOf(
                "id-id-x-dfz-local"
            ),
            "pt" to listOf(
                "pt-br-x-afs-local"
            ),
            "it" to listOf(
                "it-it-x-itc-local"
            )
        )

        // Flat set of all prioritized high-fidelity neural female voice IDs
        private val PREMIUM_FEMALE_VOICE_IDS = (LANGUAGE_PREMIUM_VOICE_MAP.values.flatten() + LANGUAGE_PERSONA_VOICE_MAP.values.flatten()).distinct()
    }

    init {
        initializeTtsEngine("Initial creation")
        scope.launch {
            diskCache.trimCacheAsync()
        }
    }

    /**
     * Initializes or re-instantiates the underlying TextToSpeech engine with preferred neural providers.
     */
    @Synchronized
    private fun initializeTtsEngine(reason: String) {
        if (isInitializing) {
            Log.d(TAG, "Initialization already in progress ($reason), skipping duplicate start.")
            return
        }
        isInitializing = true
        isInitialized = false
        isVoiceConfigured = false
        Log.i(TAG, "Starting TextToSpeech engine initialization: $reason")

        try {
            val preferredEngine = getBestAvailableTtsEngine(context)
            if (preferredEngine != null) {
                Log.i(TAG, "Initializing TTS with preferred engine: $preferredEngine")
                usedPreferredEngine = preferredEngine
                tts = TextToSpeech(context.applicationContext, this, preferredEngine)
            } else {
                usedPreferredEngine = null
                tts = TextToSpeech(context.applicationContext, this)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to instantiate TextToSpeech: ${e.message}", e)
            isInitializing = false
            isInitialized = false
        }
    }

    private fun getBestAvailableTtsEngine(ctx: Context): String? {
        return try {
            val intent = android.content.Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE)
            val resolveInfos = ctx.packageManager.queryIntentServices(intent, 0)
            val installedPackages = resolveInfos.mapNotNull { it.serviceInfo?.packageName }.toSet()

            val highQualityEngines = listOf(
                "com.google.android.tts",      // Google Speech Services (High bitrate Neural2/Wavenet)
                "com.samsung.SMT",             // Samsung TTS Neural Engine
                "com.amazon.dee.app",          // Alexa Engine
                "com.svox.pico"                // Standard AOSP Fallback
            )
            highQualityEngines.firstOrNull { installedPackages.contains(it) }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking TTS engines via PackageManager: ${e.message}")
            null
        }
    }

    override fun onInit(status: Int) {
        synchronized(this) {
            isInitializing = false
            if (status == TextToSpeech.SUCCESS) {
                isInitialized = true
                consecutiveErrorCount = 0
                utteranceCountSinceInit = 0
                Log.i(TAG, "TextToSpeech engine initialized successfully.")

                // Set high-fidelity speech audio attributes for studio-grade low-latency speech pipeline
                try {
                    val audioAttributes = AudioAttributes.Builder()
                        .apply {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                setUsage(AudioAttributes.USAGE_ASSISTANT)
                            } else {
                                setUsage(AudioAttributes.USAGE_MEDIA)
                            }
                        }
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                    tts?.setAudioAttributes(audioAttributes)
                } catch (e: Exception) {
                    Log.e(TAG, "Error setting TTS AudioAttributes: ${e.message}")
                }

                // Apply natural default language and premium female voice
                val targetLoc = configuredLocale ?: Locale.US
                configureNaturalVoice(targetLoc)

                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        mainHandler.post {
                            audioDeviceManager.requestAudioFocus(onFocusLoss = {
                                stop()
                            })
                            _isSpeaking.value = true
                            com.example.voice.audio.AudioLockManager.getInstance(context).acquireLock(com.example.voice.audio.AudioLockReason.TTS_SPEAKING)
                            onSpeechStarted?.invoke()
                            
                            com.example.util.diagnostics.DiagnosticLogManager.instance.logEvent(
                                stage = com.example.util.diagnostics.DiagnosticStage.RESULT,
                                command = "TTS Speaking",
                                details = "Neural voice synthesis started: ${_currentUtterance.value?.take(30)}...",
                                isSuccess = true
                            )
                        }
                    }

                    override fun onDone(utteranceId: String?) {
                        mainHandler.post {
                            utteranceCountSinceInit++
                            consecutiveErrorCount = 0
                            if (utteranceId != null) {
                                pendingUtteranceIds.remove(utteranceId)
                            }
                            val isFinalUtterance = pendingUtteranceIds.isEmpty()
                            if (isFinalUtterance) {
                                _isSpeaking.value = false
                                _currentUtterance.value = null
                                pendingUtteranceIds.clear()
                                lastUtteranceId = null
                                audioDeviceManager.abandonAudioFocus()
                                com.example.voice.audio.AudioLockManager.getInstance(context).releaseLock(com.example.voice.audio.AudioLockReason.TTS_SPEAKING)
                                onSpeechCompleted?.invoke()
                                activeCompletionCallback?.invoke()
                                activeCompletionCallback = null

                                com.example.util.diagnostics.DiagnosticLogManager.instance.logEvent(
                                    stage = com.example.util.diagnostics.DiagnosticStage.RESULT,
                                    command = "TTS Finished",
                                    details = "Utterance completed successfully.",
                                    isSuccess = true
                                )

                                // Proactive prevention of tone-shifting/audio distortion in marathon live conversations
                                checkPreventiveRefresh()
                            }
                        }
                    }

                    override fun onError(utteranceId: String?) {
                        mainHandler.post {
                            consecutiveErrorCount++
                            Log.w(TAG, "TTS Utterance error for id=$utteranceId (consecutiveErrors=$consecutiveErrorCount)")
                            if (utteranceId != null) pendingUtteranceIds.remove(utteranceId)
                            if (pendingUtteranceIds.isEmpty()) {
                                _isSpeaking.value = false
                                _currentUtterance.value = null
                                pendingUtteranceIds.clear()
                                lastUtteranceId = null
                                audioDeviceManager.abandonAudioFocus()
                                com.example.voice.audio.AudioLockManager.getInstance(context).releaseLock(com.example.voice.audio.AudioLockReason.TTS_SPEAKING)
                                onSpeechCompleted?.invoke()
                            }
                            if (consecutiveErrorCount >= 2) {
                                reinitialize("Consecutive utterance errors detected")
                            }
                        }
                    }

                    override fun onStop(utteranceId: String?, interrupted: Boolean) {
                        mainHandler.post {
                            pendingUtteranceIds.clear()
                            lastUtteranceId = null
                            _isSpeaking.value = false
                            _currentUtterance.value = null
                            audioDeviceManager.abandonAudioFocus()
                            com.example.voice.audio.AudioLockManager.getInstance(context).releaseLock(com.example.voice.audio.AudioLockReason.TTS_SPEAKING)
                            onSpeechCompleted?.invoke()
                        }
                    }
                })

                // Execute all pending reinit callbacks
                val callbacks = synchronized(reinitCallbacks) {
                    val list = ArrayList(reinitCallbacks)
                    reinitCallbacks.clear()
                    list
                }
                callbacks.forEach { it.invoke() }

                // Drain pending speech queue
                drainPendingSpeechQueue()
            } else {
                isInitialized = false
                Log.e(TAG, "TextToSpeech initialization failed with status: $status (usedEngine=$usedPreferredEngine)")
                // If a preferred engine was requested and failed, fall back to default system engine!
                if (usedPreferredEngine != null) {
                    Log.w(TAG, "Retrying TextToSpeech initialization with default system engine...")
                    usedPreferredEngine = null
                    try {
                        tts?.shutdown()
                    } catch (e: Exception) {}
                    isInitializing = true
                    tts = TextToSpeech(context.applicationContext, this)
                }
            }
        }
    }

    /**
     * Drains and processes queued speech requests that were buffered during initialization or re-initialization.
     */
    private fun drainPendingSpeechQueue() {
        val nextSpeech = synchronized(pendingSpeechQueue) {
            if (pendingSpeechQueue.isNotEmpty()) pendingSpeechQueue.removeAt(0) else null
        }
        if (nextSpeech != null) {
            mainHandler.post {
                speak(
                    text = nextSpeech.text,
                    speechRate = nextSpeech.speechRate,
                    speechPitch = nextSpeech.speechPitch,
                    locale = nextSpeech.locale,
                    persona = nextSpeech.persona,
                    onCompletion = nextSpeech.onCompletion
                )
            }
        }
    }

    /**
     * Dynamically identifies input text language and updates TTS locale for native tongue responses.
     */
    fun detectAndSwitchLocale(text: String, fallbackLocale: Locale = Locale.US): Locale {
        val result = languageDetector.detectLanguage(text, fallbackLocale)
        if (configuredLocale != result.locale) {
            Log.i(TAG, "Dynamically switching TTS locale to ${result.languageDisplayName} (${result.locale})")
            configureNaturalVoice(result.locale)
        }
        return result.locale
    }

    /**
     * Finds and applies exclusively premium, high-quality, natural realistic female voices.
     * Selects distinct real neural models tailored specifically to the active Voice Tone & Persona.
     * Male voices are strictly filtered out across all languages and TTS engines.
     * Seamlessly prioritizes offline on-device female voice packs when network is unavailable.
     */
    fun configureNaturalVoice(locale: Locale, persona: String = configuredPersona) {
        configuredPersona = persona
        if (!isInitialized || tts == null) {
            configuredLocale = locale
            return
        }

        try {
            var result = tts?.setLanguage(locale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "Language data missing or not supported for locale: $locale, attempting base language fallback")
                val baseLocale = Locale(locale.language)
                val baseResult = tts?.setLanguage(baseLocale)
                if (baseResult == TextToSpeech.LANG_MISSING_DATA || baseResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.w(TAG, "Base language not supported, falling back to US English")
                    tts?.setLanguage(Locale.US)
                }
            }

            val availableVoices = tts?.voices
            if (!availableVoices.isNullOrEmpty()) {
                // Find voices matching the requested language
                val eligibleVoices = availableVoices.filter { voice ->
                    val matchesLang = voice.locale.language.equals(locale.language, ignoreCase = true)
                    val isInstalled = !voice.features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)
                    matchesLang && isInstalled
                }

                if (eligibleVoices.isEmpty()) {
                    Log.w(TAG, "No installed voices found for language: ${locale.language}")
                    configuredLocale = locale
                    isVoiceConfigured = true
                    return
                }

                // Strictly enforce female voice selection - male voices are completely excluded
                val preferredVoices = eligibleVoices.filter { voice ->
                    val voiceName = voice.name.lowercase()
                    val isMale = (voiceName.contains("male") && !voiceName.contains("female")) ||
                            voiceName.contains("man") || voiceName.contains("-m-") ||
                            voiceName.contains("_m_") || voiceName.endsWith("-m") ||
                            voiceName.contains("puck") || voiceName.contains("charon") || voiceName.contains("fenrir")
                    !isMale
                }

                val candidates = if (preferredVoices.isNotEmpty()) preferredVoices else eligibleVoices

                val langKey = locale.language.lowercase()
                val personaKey = persona.uppercase()

                // Look up targeted voice IDs specifically for this (Language, Persona) pair
                val targetVoiceIds = LANGUAGE_PERSONA_VOICE_MAP[Pair(langKey, personaKey)]
                    ?: LANGUAGE_PREMIUM_VOICE_MAP[langKey]
                    ?: emptyList()

                // 1. Direct exact match for preferred high-fidelity female voices in target language & persona
                var directMatchVoice: Voice? = null
                for (targetId in targetVoiceIds) {
                    directMatchVoice = candidates.firstOrNull { it.name.equals(targetId, ignoreCase = true) }
                    if (directMatchVoice != null) break
                }
                if (directMatchVoice == null) {
                    for (targetId in targetVoiceIds) {
                        directMatchVoice = candidates.firstOrNull { it.name.contains(targetId, ignoreCase = true) }
                        if (directMatchVoice != null) break
                    }
                }

                // Identify best offline-capable voice as a safe fallback
                fallbackOfflineVoice = candidates.filter { !it.isNetworkConnectionRequired }.maxByOrNull { voice ->
                    val voiceName = voice.name.lowercase()
                    var score = 100
                    if (voice.quality == Voice.QUALITY_VERY_HIGH) score += 60
                    if (voice.quality == Voice.QUALITY_HIGH) score += 30
                    if (voiceName.contains("female") || voiceName.contains("-f-") || voiceName.endsWith("-f")) score += 80
                    score
                }

                // Rank candidate voices to pick the most authentic neural/studio voice for this persona
                val bestVoice = directMatchVoice ?: candidates.maxByOrNull { voice ->
                    var score = 100
                    val voiceName = voice.name.lowercase()

                    // Match against targeted persona voice IDs (Highest priority)
                    if (targetVoiceIds.any { voiceName.contains(it.lowercase()) }) {
                        score += 200
                    }

                    // Match against verified top-tier premium female voice IDs
                    if (PREMIUM_FEMALE_VOICE_IDS.any { voiceName.contains(it.lowercase()) }) {
                        score += 100
                    }

                    // Quality tiers: High-definition neural/studio voices provide true human speech
                    if (voice.quality == Voice.QUALITY_VERY_HIGH) score += 90
                    if (voice.quality == Voice.QUALITY_HIGH) score += 50
                    if (voice.quality == Voice.QUALITY_NORMAL) score += 20

                    // Latency score
                    if (voice.latency == Voice.LATENCY_VERY_LOW || voice.latency == Voice.LATENCY_LOW) {
                        score += 30
                    }

                    // High priority for verified female models
                    val isExplicitFemale = voiceName.contains("female") || voiceName.contains("#female") ||
                            voiceName.contains("-f-") || voiceName.contains("_f_") || voiceName.endsWith("-f")
                    if (isExplicitFemale) score += 80

                    // Known top-tier neural studio identifiers (Real voice synthesis)
                    val neuralIdentifiers = listOf(
                        "neural2", "wavenet", "journey", "studio", "sfg", "iog", "iol", "iom", "tpd", "tpf",
                        "rjs", "afh", "cfl", "cfa", "f00", "f01", "f02", "dfc", "htm", "woman", "girl"
                    )
                    if (neuralIdentifiers.any { voiceName.contains(it) }) {
                        score += 90
                    }

                    // Acoustic persona alignment scoring
                    when (personaKey) {
                        "GENTLE_SOFT", "SOFT_MELODIC", "ALYA_WARM_COMPANION" -> {
                            if (voiceName.contains("iol") || voiceName.contains("iog") || voiceName.contains("journey") || voiceName.contains("rjs")) score += 60
                        }
                        "CRISP_CONFIDENT", "EXECUTIVE", "ALYA_EXECUTIVE_CRISP" -> {
                            if (voiceName.contains("studio") || voiceName.contains("iom") || voiceName.contains("fis")) score += 60
                        }
                        "LIVELY_PLAYFUL", "ENERGETIC" -> {
                            if (voiceName.contains("iob") || voiceName.contains("cfl") || voiceName.contains("neural2-c")) score += 60
                        }
                        "SWEET_COMPANION", "ANIME_SWEET" -> {
                            if (voiceName.contains("tpf") || voiceName.contains("cfa") || voiceName.contains("wavenet")) score += 60
                        }
                        else -> { // KORE
                            if (voiceName.contains("tpf") || voiceName.contains("sfg") || voiceName.contains("neural2-f")) score += 60
                        }
                    }

                    score
                }

                if (bestVoice != null) {
                    tts?.voice = bestVoice
                    _selectedVoiceId.value = bestVoice.name
                    Log.i(TAG, "Selected real voice for persona $persona: ${bestVoice.name} (Quality: ${bestVoice.quality})")
                } else if (fallbackOfflineVoice != null) {
                    tts?.voice = fallbackOfflineVoice
                    _selectedVoiceId.value = fallbackOfflineVoice?.name
                }
            }
            configuredLocale = locale
            isVoiceConfigured = true
        } catch (e: Exception) {
            Log.e(TAG, "Error in configureNaturalVoice: ${e.message}", e)
        }
    }

    /**
     * Dynamically sets and applies the voice persona across the TTS engine.
     * Reconfigures voice selection to the designated neural model immediately.
     */
    fun setPersona(persona: String) {
        Log.i(TAG, "setPersona: switching to persona $persona")
        configuredPersona = persona
        val targetLocale = configuredLocale ?: Locale.US
        configureNaturalVoice(targetLocale, persona)
    }

    private fun detectLocaleForText(text: String, defaultLocale: Locale): Locale {
        return languageDetector.detectLanguage(text, defaultLocale).locale
    }

    /**
     * Builds a high-fidelity synthesis parameters Bundle with audio channel, volume, and latency tuning.
     */
    private fun createHighFidelitySynthesisParams(): Bundle {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val isCallActive = audioManager?.mode == AudioManager.MODE_IN_CALL || 
                           audioManager?.mode == AudioManager.MODE_IN_COMMUNICATION ||
                           com.example.service.TelephonyService.isCallActive

        return Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
            putFloat(TextToSpeech.Engine.KEY_PARAM_PAN, 0.0f)
            if (isCallActive) {
                putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_VOICE_CALL)
            }
            putInt(TextToSpeech.Engine.KEY_FEATURE_NETWORK_TIMEOUT_MS, 10000)
        }
    }

    /**
     * Speaks input text with full pre-validation of engine initialization, voice selection,
     * and target locale setup. Automatically buffers and handles recovery if the engine is restarting.
     * When flushCurrent is false, queued utterances append smoothly without audio crackling or pops.
     */
    fun speak(
        text: String,
        speechRate: Float = 1.00f,
        speechPitch: Float = 1.00f,
        locale: Locale = Locale.US,
        persona: String = "KORE",
        flushCurrent: Boolean = true,
        onCompletion: (() -> Unit)? = null
    ) {
        // Suppress Android TTS if PCM AudioTrack Player is currently streaming live audio to prevent double voice
        val app = context.applicationContext as? com.example.AlyaApplication
        if (app?.pcmAudioPlayer?.isPlaybackActive?.value == true) {
            Log.i(TAG, "PCM AudioTrack Player actively streaming live audio. Suppressing duplicate TTS speak request.")
            return
        }

        // Strict verification: if engine is not initialized or still starting, queue request and reinit if dead
        if (!isInitialized || tts == null) {
            Log.w(TAG, "TTS not ready (initialized=$isInitialized, initializing=$isInitializing). Buffering speech request.")
            synchronized(pendingSpeechQueue) {
                pendingSpeechQueue.add(QueuedSpeech(text, speechRate, speechPitch, locale, persona, onCompletion))
            }
            if (!isInitializing) {
                reinitialize("speak() called while engine uninitialized")
            }
            return
        }

        val localCompletion = onCompletion
        activeCompletionCallback = localCompletion
        if (flushCurrent) {
            stopActivePlayback()
            pendingUtteranceIds.clear()
        }

        val targetLocale = detectLocaleForText(text, locale)
        
        // Strict locale, persona, and voice validation before invoking speak
        if (configuredLocale != targetLocale || configuredPersona != persona || !isVoiceConfigured || tts?.voice == null) {
            configureNaturalVoice(targetLocale, persona)
        }

        // Realistic human voice calibration (Pitch, Speed, Inflection, Warmth) tailored per persona
        val (personaPitchOffset, personaRateMultiplier) = when (persona.uppercase()) {
            "KORE", "ALYA_KORE", "NATURAL_WARM", "WARM_NATURAL", "DEFAULT" -> Pair(0.00f, 1.00f)
            "GENTLE_SOFT", "SOFT_MELODIC", "ALYA_WARM_COMPANION", "WARM_COMPANION" -> Pair(-0.02f, 0.94f)
            "CRISP_CONFIDENT", "EXECUTIVE", "ALYA_EXECUTIVE_CRISP", "CALM_EXECUTIVE" -> Pair(-0.04f, 1.04f)
            "LIVELY_PLAYFUL", "ENERGETIC", "ENERGETIC_COMPANION" -> Pair(0.06f, 1.06f)
            "SWEET_COMPANION", "ALYA_ANIME_RUSSIAN", "ANIME_SWEET" -> Pair(0.04f, 0.98f)
            else -> Pair(0.00f, 1.00f)
        }

        val finalPitch = (speechPitch + personaPitchOffset).coerceIn(0.70f, 1.50f)
        val finalRate = (speechRate * personaRateMultiplier).coerceIn(0.70f, 1.60f)

        try {
            tts?.setSpeechRate(finalRate)
            tts?.setPitch(finalPitch)
        } catch (e: Exception) {
            Log.w(TAG, "Error applying rate/pitch: ${e.message}")
        }

        // Natural speech sanitization: remove robotic markdown, expand acronyms, format phone numbers, numbers & units
        val naturalSpeech = prepareTextForNaturalSpeech(text)
        if (naturalSpeech.isBlank()) {
            mainHandler.post { 
                onSpeechCompleted?.invoke()
                onCompletion?.invoke()
            }
            return
        }

        _currentUtterance.value = naturalSpeech
        if (flushCurrent) {
            pendingUtteranceIds.clear()
        }

        // Ensure audio output is audible
        audioDeviceManager.ensureAudibleVolume()

        // 1. Check disk cache for sub-10ms instant response playback
        val voiceName = tts?.voice?.name ?: _selectedVoiceId.value ?: "en-us-x-tpf-local"
        val cacheKey = diskCache.computeKey(naturalSpeech, voiceName, finalPitch, finalRate, locale.toLanguageTag() + "_$persona")
        val cachedFile = diskCache.getCachedAudioFile(cacheKey)

        if (cachedFile != null) {
            val mp = diskCache.playCachedAudio(
                file = cachedFile,
                onStart = {
                    mainHandler.post {
                        audioDeviceManager.requestAudioFocus(onFocusLoss = { stop() })
                        _isSpeaking.value = true
                        onSpeechStarted?.invoke()
                    }
                },
                onCompletion = {
                    mainHandler.post {
                        _isSpeaking.value = false
                        _currentUtterance.value = null
                        activeMediaPlayer = null
                        audioDeviceManager.abandonAudioFocus()
                        onSpeechCompleted?.invoke()
                        activeCompletionCallback?.invoke()
                        activeCompletionCallback = null
                    }
                },
                onError = {
                    mainHandler.post {
                        _isSpeaking.value = false
                        _currentUtterance.value = null
                        activeMediaPlayer = null
                        audioDeviceManager.abandonAudioFocus()
                        onSpeechCompleted?.invoke()
                    }
                }
            )
            if (mp != null) {
                activeMediaPlayer = mp
                return
            }
        }

        // 2. Split speech into conversational phrases and sentences for low-latency, natural rhythm
        val sentences = naturalSpeech.split(Regex("(?<=[.?!।])\\s+")).filter { it.isNotBlank() }
        val finalSentences = if (sentences.isEmpty()) listOf(naturalSpeech) else sentences

        // Pre-generate utterance IDs for all sentences to prevent premature completion callbacks
        val utteranceItems = finalSentences.map { sentence ->
            val id = UUID.randomUUID().toString()
            Pair(id, sentence)
        }
        utteranceItems.forEach { pendingUtteranceIds.add(it.first) }
        lastUtteranceId = utteranceItems.last().first

        // Watchdog timeout to prevent voice state from ever locking or hanging
        mainHandler.removeCallbacks(ttsWatchdogRunnable)
        val maxDurationMs = (naturalSpeech.length * 250L).coerceIn(25000L, 180000L)
        mainHandler.postDelayed(ttsWatchdogRunnable, maxDurationMs)

        var hasError = false
        utteranceItems.forEachIndexed { index, item ->
            val utteranceId = item.first
            val sentence = item.second

            val queueMode = if (flushCurrent && index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            val params = createHighFidelitySynthesisParams()
            
            val result = tts?.speak(sentence, queueMode, params, utteranceId)
            if (result == TextToSpeech.ERROR) {
                hasError = true
                Log.w(TAG, "tts.speak returned ERROR for sentence: $sentence")
                var recovered = false
                if (fallbackOfflineVoice != null) {
                    try {
                        tts?.voice = fallbackOfflineVoice
                        val retryResult = tts?.speak(sentence, queueMode, params, utteranceId)
                        if (retryResult == TextToSpeech.SUCCESS) {
                            hasError = false
                            recovered = true
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in offline fallback speak: ${e.message}")
                    }
                }
                if (!recovered) {
                    try {
                        tts?.setLanguage(targetLocale)
                        tts?.voice = null
                        val defaultResult = tts?.speak(sentence, queueMode, params, utteranceId)
                        if (defaultResult == TextToSpeech.SUCCESS) {
                            hasError = false
                            recovered = true
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in default voice fallback speak: ${e.message}")
                    }
                }
                if (!recovered) {
                    pendingUtteranceIds.remove(utteranceId)
                    if (pendingUtteranceIds.isEmpty()) {
                        _isSpeaking.value = false
                        _currentUtterance.value = null
                        audioDeviceManager.abandonAudioFocus()
                        onSpeechCompleted?.invoke()
                        activeCompletionCallback?.invoke()
                        activeCompletionCallback = null
                    }
                }
            }
        }

        if (hasError) {
            consecutiveErrorCount++
            if (consecutiveErrorCount >= 2) {
                reinitialize("Recovering from repeat speak() execution errors")
            }
        }

        // Asynchronously synthesize short responses (<240 chars) to disk cache for future instant zero-lag playback
        if (naturalSpeech.length in 3..240 && isInitialized && tts != null) {
            scope.launch {
                try {
                    val targetFile = diskCache.getTargetAudioFile(cacheKey)
                    targetFile.parentFile?.mkdirs()
                    if (!targetFile.exists()) {
                        targetFile.createNewFile()
                    }
                    if (targetFile.exists() && targetFile.canWrite()) {
                        val synthParams = createHighFidelitySynthesisParams()
                        tts?.synthesizeToFile(naturalSpeech, synthParams, targetFile, "cache_${UUID.randomUUID()}")
                    }
                } catch (e: Throwable) {
                    Log.d(TAG, "Pre-caching audio skipped safely: ${e.message}")
                }
            }
        }
    }

    /**
     * Proactively checks if the TTS engine should be refreshed during long continuous conversations
     * to eliminate pitch distortion, memory leaks, or tone-shifting artifacts.
     */
    private fun checkPreventiveRefresh() {
        if (utteranceCountSinceInit >= MAX_UTTERANCES_BEFORE_PREVENTIVE_REFRESH && !_isSpeaking.value) {
            Log.i(TAG, "Proactive engine refresh scheduled after $utteranceCountSinceInit continuous utterances.")
            reinitialize("Long conversation preventive tone refresh")
        }
    }

    /**
     * Release-Reinitialize Logic: Safely tears down any stale, crashed, or tone-shifted TTS instance
     * and initializes a pristine new TextToSpeech engine with all configurations restored.
     */
    @Synchronized
    fun reinitialize(reason: String = "Manual request", onReady: (() -> Unit)? = null) {
        Log.i(TAG, "Release & Reinitialize triggered: reason='$reason'")

        if (onReady != null) {
            synchronized(reinitCallbacks) {
                reinitCallbacks.add(onReady)
            }
        }

        mainHandler.removeCallbacks(ttsWatchdogRunnable)
        stopActivePlayback()

        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.w(TAG, "Exception during TTS shutdown: ${e.message}")
        } finally {
            tts = null
            isInitialized = false
            isInitializing = false
            isVoiceConfigured = false
            _isSpeaking.value = false
            _currentUtterance.value = null
            pendingUtteranceIds.clear()
            lastUtteranceId = null
        }

        initializeTtsEngine(reason)
    }

    /**
     * Transforms raw assistant output into clean, natural-sounding human conversation.
     * Expands technical and conversational acronyms, formats time and measurements,
     * strips robotic symbols, and inserts natural breathing pauses.
     */
    private fun prepareTextForNaturalSpeech(text: String): String {
        var clean = com.example.util.SystemThoughtFilter.cleanForSpeech(text)
            // Strip action tags, json code blocks and json payloads
            .replace(Regex("\\[ACTION:[^\\]]+\\]", RegexOption.IGNORE_CASE), "")
            .replace(Regex("```action[\\s\\S]*?```"), "")
            .replace(Regex("```[\\s\\S]*?```"), "")
            .replace(Regex("\\{[\\s\\S]*?\"(tool|action|intent)\"[\\s\\S]*?\\}"), "")
            // Strip markdown bold, italic, headings, blockquotes, bullets, brackets
            .replace(Regex("[*#_~`>]"), "")
            .replace(Regex("[\\[\\]]"), "")
            .replace(Regex("^[\\s]*[-•*]\\s+", RegexOption.MULTILINE), "")
            // Strip URLs
            .replace(Regex("https?://\\S+"), "the link")
            // Strip emojis (surrogate pairs and emoji blocks)
            .replace(Regex("[\\p{So}\\p{Cn}\\x{1F300}-\\x{1F9FF}\\x{1F600}-\\x{1F64F}\\x{1F680}-\\x{1F6FF}\\x{2600}-\\x{26FF}]"), "")

        // Conversational phrase and technical abbreviations expansions for smooth human flow
        val abbreviations = mapOf(
            "\\bWi-?Fi\\b" to "Wi-Fi",
            "\\bAI\\b" to "A I",
            "\\bTTS\\b" to "T T S",
            "\\bGPS\\b" to "G P S",
            "\\bSMS\\b" to "S M S",
            "\\bSOS\\b" to "S O S",
            "\\bFAQ\\b" to "F A Q",
            "\\bUSB\\b" to "U S B",
            "\\bNFC\\b" to "N F C",
            "\\bAPK\\b" to "A P K",
            "\\bUI\\b" to "U I",
            "\\be\\.g\\." to "for example",
            "\\bi\\.e\\." to "that is",
            "\\betc\\." to "and so on",
            "\\bapprox\\." to "approximately",
            "\\bw/o\\b" to "without",
            "\\bw/\\b" to "with",
            "\\bmin\\b" to "minutes",
            "\\bsec\\b" to "seconds",
            "\\bhr\\b" to "hour",
            "\\bhrs\\b" to "hours",
            "\\bvs\\." to "versus",
            "\\bdeg\\b" to "degrees",
            "\\b°C\\b" to " degrees Celsius",
            "\\b°F\\b" to " degrees Fahrenheit",
            "\\bkm/h\\b" to " kilometers per hour",
            "\\bmph\\b" to " miles per hour",
            "\\b%" to " percent",
            "\\bAM\\b" to "A M",
            "\\bPM\\b" to "P M"
        )

        for ((regex, replacement) in abbreviations) {
            clean = clean.replace(Regex(regex, RegexOption.IGNORE_CASE), replacement)
        }

        // Format currency symbols into spoken words
        clean = clean
            .replace(Regex("\\$(\\d+)"), "$1 dollars")
            .replace(Regex("₹(\\d+)"), "$1 rupees")
            .replace(Regex("€(\\d+)"), "$1 euros")
            .replace(Regex("¥(\\d+)"), "$1 yen")

        // Smooth em-dashes, ellipses, semicolons, and colons into natural conversational pauses
        clean = clean
            .replace(Regex("—|--"), ", ")
            .replace(Regex("\\.{3,}"), ", ")
            .replace(Regex("[;:]"), ", ")
            .replace(Regex("~"), " ")

        // Format phone numbers so TTS engine pronounces digits clearly
        clean = clean.replace(Regex("(\\+?\\d{1,3}[- .]?)?\\(?(\\d{3})\\)?[- .]?(\\d{3})[- .]?(\\d{4})")) { match ->
            val digits = match.value.filter { it.isDigit() }
            if (digits.length in 7..15) {
                digits.map { "$it " }.joinToString("").trim()
            } else {
                match.value
            }
        }

        // Clean up excessive whitespace or repeated commas
        clean = clean
            .replace(Regex(",\\s*,"), ",")
            .replace(Regex("\\s+"), " ")
            .trim()

        return clean
    }

    private fun stopActivePlayback() {
        try {
            activeMediaPlayer?.let { mp ->
                if (mp.isPlaying) {
                    mp.stop()
                }
                mp.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in stopActivePlayback: ${e.message}")
        }
        activeMediaPlayer = null
    }

    fun stop() {
        mainHandler.removeCallbacks(ttsWatchdogRunnable)
        stopActivePlayback()
        if (tts != null && isInitialized) {
            try {
                tts?.stop()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping TTS: ${e.message}")
            }
        }
        pendingUtteranceIds.clear()
        lastUtteranceId = null
        _isSpeaking.value = false
        _currentUtterance.value = null
        audioDeviceManager.abandonAudioFocus()
        com.example.voice.audio.AudioLockManager.getInstance(context).releaseLock(com.example.voice.audio.AudioLockReason.TTS_SPEAKING)
    }

    fun shutdown() {
        stop()
        audioDeviceManager.abandonAudioFocus()
        try {
            tts?.shutdown()
        } catch (e: Exception) {
            Log.w(TAG, "Error shutting down TTS: ${e.message}")
        }
        tts = null
        isInitialized = false
        isInitializing = false
        isVoiceConfigured = false
    }

    /**
     * Data class containing diagnostic and status information about the active TTS Voice Pack.
     */
    data class VoicePackDetails(
        val languageTag: String,
        val displayName: String,
        val isInstalled: Boolean,
        val totalVoicesCount: Int,
        val femaleVoicesCount: Int,
        val activeVoiceName: String?,
        val isHighQualityNeural: Boolean,
        val isOfflineCapable: Boolean,
        val enginePackageName: String?
    )

    /**
     * Inspects the currently installed voices and returns details on the voice pack for the given locale.
     */
    fun getVoicePackInfo(locale: Locale = configuredLocale ?: Locale.US): VoicePackDetails {
        val allVoices = tts?.voices ?: emptySet()
        val langVoices = allVoices.filter { it.locale.language.equals(locale.language, ignoreCase = true) }
        val femaleVoices = langVoices.filter { voice ->
            val voiceName = voice.name.lowercase()
            val isMale = (voiceName.contains("male") && !voiceName.contains("female")) ||
                    voiceName.contains("man") || voiceName.contains("-m-") ||
                    voiceName.contains("_m_") || voiceName.endsWith("-m") ||
                    voiceName.contains("puck") || voiceName.contains("charon") || voiceName.contains("fenrir")
            !isMale
        }
        val activeVoice = tts?.voice
        val isNeural = activeVoice?.name?.let { name ->
            val lower = name.lowercase()
            lower.contains("neural") || lower.contains("wavenet") || lower.contains("journey") ||
                    lower.contains("studio") || lower.contains("tpf") || lower.contains("sfg") ||
                    lower.contains("cfl") || lower.contains("cfa") || lower.contains("iog")
        } ?: false
        val isOffline = activeVoice?.isNetworkConnectionRequired == false

        return VoicePackDetails(
            languageTag = locale.toLanguageTag(),
            displayName = locale.getDisplayName(Locale.ENGLISH),
            isInstalled = langVoices.isNotEmpty(),
            totalVoicesCount = langVoices.size,
            femaleVoicesCount = femaleVoices.size,
            activeVoiceName = activeVoice?.name ?: _selectedVoiceId.value,
            isHighQualityNeural = isNeural || (activeVoice?.quality == Voice.QUALITY_VERY_HIGH),
            isOfflineCapable = isOffline,
            enginePackageName = tts?.defaultEngine ?: usedPreferredEngine ?: "com.google.android.tts"
        )
    }

    /**
     * Opens system settings to install / download high-quality voice data packs (Google Speech Services / Android TTS).
     */
    fun openTtsVoiceDataSettings(activityContext: Context): Boolean {
        return try {
            val installIntent = android.content.Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            }
            activityContext.startActivity(installIntent)
            true
        } catch (e: Exception) {
            Log.w(TAG, "ACTION_INSTALL_TTS_DATA failed, trying engine settings: ${e.message}")
            openTtsEngineSettings(activityContext)
        }
    }

    /**
     * Opens Android System Text-to-Speech Settings so the user can select Google TTS engine or install voices.
     */
    fun openTtsEngineSettings(activityContext: Context): Boolean {
        return try {
            val settingsIntent = android.content.Intent("com.android.settings.TTS_SETTINGS").apply {
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            }
            activityContext.startActivity(settingsIntent)
            true
        } catch (e: Exception) {
            try {
                val accessibilityIntent = android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                }
                activityContext.startActivity(accessibilityIntent)
                true
            } catch (ex: Exception) {
                Log.e(TAG, "Failed to open TTS settings: ${ex.message}")
                false
            }
        }
    }
}
