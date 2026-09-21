package com.example.voice

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import java.util.Locale

/**
 * Dedicated Offline TTS Manager for lightweight local synthesis packs (~400MB / device packs).
 * Uses Google TTS ("com.google.android.tts") with strict local synthesis (KEY_FEATURE_NETWORK_SYNTHESIS = "false").
 * Strictly speaks Romanized script with clear Indian/Hinglish/Banglish phonetic pacing (0.95f rate, 1.0f pitch).
 */
class OfflineTTSManager(
    private val context: Context,
    private val onSpeechCompleted: (() -> Unit)? = null
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    var isReady: Boolean = false
        private set

    private var currentLocale: Locale = Locale("en", "IN")
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var focusRequest: AudioFocusRequest? = null

    companion object {
        private const val TAG = "OfflineTTSManager"
        const val UTTERANCE_ID = "AlyaUtteranceID"
    }

    init {
        try {
            // Direct Google TTS Engine use returns best offline quality
            tts = TextToSpeech(context, this, "com.google.android.tts")
        } catch (e: Exception) {
            Log.w(TAG, "Google TTS engine unavailable, falling back to default system engine.", e)
            tts = TextToSpeech(context, this)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            setVoicePack(currentLocale)

            // Speech Rate & Pitch Adjustment for clear phonetic audio:
            tts?.setSpeechRate(0.95f)
            tts?.setPitch(1.0f)

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    Log.d(TAG, "Offline TTS playback started: $utteranceId")
                    com.example.voice.audio.AudioLockManager.getInstance(context).acquireLock(com.example.voice.audio.AudioLockReason.TTS_SPEAKING)
                }

                override fun onDone(utteranceId: String?) {
                    Log.d(TAG, "Offline TTS playback completed: $utteranceId")
                    abandonAudioFocus()
                    com.example.voice.audio.AudioLockManager.getInstance(context).releaseLock(com.example.voice.audio.AudioLockReason.TTS_SPEAKING)
                    onSpeechCompleted?.invoke()
                }

                override fun onError(utteranceId: String?) {
                    Log.e(TAG, "Offline TTS playback error on: $utteranceId")
                    abandonAudioFocus()
                    com.example.voice.audio.AudioLockManager.getInstance(context).releaseLock(com.example.voice.audio.AudioLockReason.TTS_SPEAKING)
                    onSpeechCompleted?.invoke()
                }
            })

            isReady = true
            Log.i(TAG, "Offline TTS initialized successfully.")
        } else {
            Log.e(TAG, "TTS Initialization failed with status: $status")
            isReady = false
        }
    }

    /**
     * Requests dynamic AudioFocus for transient speech usage.
     */
    private fun requestAudioFocus(): Boolean {
        return try {
            val playbackAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(playbackAttributes)
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener { focusChange ->
                    when (focusChange) {
                        AudioManager.AUDIOFOCUS_LOSS,
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                            // Stop speaking if focus is lost to prevent overlap
                            stop()
                        }
                    }
                }
                .build()
            
            focusRequest = request
            val result = audioManager.requestAudioFocus(request)
            result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request AudioFocus: ${e.message}")
            false
        }
    }

    /**
     * Abandons transient AudioFocus cleanly.
     */
    private fun abandonAudioFocus() {
        try {
            focusRequest?.let {
                audioManager.abandonAudioFocusRequest(it)
            }
            focusRequest = null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to abandon AudioFocus: ${e.message}")
        }
    }

    /**
     * Strictly enforces Romanized script formatting for Hinglish, Banglish, and localized expressions.
     * Transliterates native Devanagari and Bengali characters into Latin/Roman equivalents, cleans up
     * irregular Unicode punctuation, and adds phonetic spacing/hyphenation to avoid robotic artifacts.
     */
    private fun enforceRomanizedScript(text: String): String {
        var clean = text
        
        // Transliterate Devanagari (Hindi) characters to Roman phonemes
        val devanagariMap = mapOf(
            "अ" to "a", "आ" to "aa", "इ" to "i", "ई" to "ee", "उ" to "u", "ऊ" to "oo", "ऋ" to "ri",
            "ए" to "e", "ऐ" to "ai", "ओ" to "o", "औ" to "au", "अं" to "an", "अः" to "ah",
            "क" to "ka", "ख" to "kha", "ग" to "ga", "घ" to "gha", "ङ" to "nga",
            "च" to "cha", "छ" to "chha", "ज" to "ja", "झ" to "jha", "ञ" to "nya",
            "ट" to "ta", "ठ" to "tha", "ड" to "da", "ढ" to "dha", "ण" to "na",
            "त" to "ta", "थ" to "tha", "द" to "da", "ध" to "dha", "न" to "na",
            "प" to "pa", "फ" to "pha", "ब" to "ba", "भ" to "bha", "म" to "ma",
            "य" to "ya", "र" to "ra", "ल" to "la", "व" to "va", "श" to "sha", "ष" to "sha", "स" to "sa", "ह" to "ha",
            "ा" to "a", "ि" to "i", "ी" to "ee", "ु" to "u", "ू" to "oo", "ृ" to "ri", "े" to "e", "ै" to "ai", "ो" to "o", "ौ" to "au", "ं" to "n", "ः" to "h"
        )
        
        // Transliterate Bengali characters to Roman phonemes
        val bengaliMap = mapOf(
            "অ" to "o", "আ" to "a", "ই" to "i", "ঈ" to "ee", "উ" to "u", "ঊ" to "oo", "ঋ" to "ree",
            "এ" to "e", "ঐ" to "oi", "ও" to "o", "ঔ" to "ou",
            "ক" to "ko", "খ" to "kho", "গ" to "go", "ঘ" to "gho", "ঙ" to "ngo",
            "চ" to "cho", "ছ" to "chho", "জ" to "jo", "ঝ" to "jho", "ঞ" to "nyo",
            "ট" to "to", "ঠ" to "tho", "ড" to "do", "ঢ" to "dho", "ণ" to "no",
            "ত" to "to", "থ" to "tho", "দ" to "do", "ধ" to "dho", "ন" to "no",
            "প" to "po", "ফ" to "pho", "ব" to "bo", "ভ" to "bho", "म" to "mo",
            "য" to "jo", "র" to "ro", "ল" to "lo", "ব" to "bo", "শ" to "sho", "ষ" to "sho", "স" to "so", "হ" to "ho", "ড়" to "ro", "ঢ়" to "rho", "য়" to "yo",
            "া" to "a", "ি" to "i", "ী" to "ee", "ু" to "u", "ূ" to "oo", "ৃ" to "ree", "ে" to "e", "ৈ" to "oi", "ো" to "o", "ৌ" to "ou", "ং" to "ng", "ঃ" to "h", "ঁ" to "n"
        )

        for ((nativeChar, roman) in devanagariMap) {
            clean = clean.replace(nativeChar, roman)
        }

        for ((nativeChar, roman) in bengaliMap) {
            clean = clean.replace(nativeChar, roman)
        }

        // Apply phonetic spacing & pacing rules to prevent robotic clumping
        clean = clean
            .replace(Regex("\\b(?i)aapka\\b"), "aapka")
            .replace(Regex("\\b(?i)kaise\\b"), "kai-se")
            .replace(Regex("\\b(?i)achha\\b"), "ach-ha")
            .replace(Regex("\\b(?i)achhe\\b"), "ach-he")
            .replace(Regex("\\b(?i)hoon\\b"), "huu")
            .replace(Regex("\\b(?i)ho\\b"), "ho")
            .replace(Regex("\\b(?i)koshish\\b"), "ko-shish")
            .replace(Regex("\\b(?i)bahut\\b"), "bohot")
            .replace(Regex("\\b(?i)shuru\\b"), "shu-ru")
            .replace(Regex("\\b(?i)kripya\\b"), "kri-pya")
            .replace(Regex("\\b(?i)theek\\b"), "theek")
            .replace(Regex("\\b(?i)thek\\b"), "theek")

        clean = clean.replace(Regex("\\s+"), " ")
                     .replace(Regex("-+"), "-")
                     .trim()

        Log.d(TAG, "Strictly enforced Romanized script output: $clean")
        return clean
    }

    /**
     * Switch between localized offline voice packs:
     * - "en-US" -> US English
     * - "en-IN" -> Indian English / Hinglish
     * - "hi-IN" -> Hindi
     * - "bn-IN" -> Bengali
     * - "rajbonshi" -> Rajbonshi (using Bengali locale with localized phonetic tuning)
     */
    fun setVoicePack(localeTag: String) {
        val targetLocale = when (localeTag.lowercase()) {
            "hi", "hi-in" -> Locale("hi", "IN")
            "bn", "bn-in" -> Locale("bn", "IN")
            "rajbonshi", "bn-in-rajbonshi" -> Locale("bn", "IN") // Rajbonshi mapped to Bengali pack with Romanized pacing
            "en-us", "us" -> Locale.US
            else -> Locale("en", "IN") // Default: Indian English / Hinglish
        }
        setVoicePack(targetLocale)
    }

    fun setVoicePack(locale: Locale) {
        currentLocale = locale
        val langResult = tts?.setLanguage(locale)

        if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w(TAG, "Language pack $locale missing! Falling back to Indian English locale...")
            tts?.setLanguage(Locale("en", "IN"))
        } else {
            setupOfflineVoice(locale)
        }
    }

    // Network requirement check karke strictly offline voice set karna
    private fun setupOfflineVoice(locale: Locale) {
        val availableVoices: Set<Voice>? = tts?.voices

        val offlineVoice = availableVoices?.firstOrNull { voice ->
            voice.locale.language == locale.language && !voice.isNetworkConnectionRequired
        } ?: availableVoices?.firstOrNull { voice ->
            !voice.isNetworkConnectionRequired
        }

        if (offlineVoice != null) {
            tts?.voice = offlineVoice
            isReady = true
            Log.d(TAG, "Offline Voice Configured: ${offlineVoice.name}")
        } else {
            Log.w(TAG, "No strictly offline voice found for $locale.")
        }
    }

    // User ko Offline Voice Pack download page par bhejna
    fun promptInstallLanguagePack() {
        val installIntent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            setPackage("com.google.android.tts")
        }
        try {
            context.startActivity(installIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Cannot open TTS installation settings", e)
        }
    }

    // Speak Function (Strictly Offline Force Parameters)
    fun speak(text: String, onComplete: (() -> Unit)? = null) {
        if (!isReady) {
            Log.e(TAG, "TTS Engine is not ready or offline pack is missing.")
            return
        }

        // Clean text for speech: strip internal thoughts, speaker prefix and action tags
        val rawSpeech = com.example.util.SystemThoughtFilter.cleanForSpeech(text)
            .replace(Regex("\\[ACTION:[^\\]]+\\]", RegexOption.IGNORE_CASE), "")
            .trim()

        if (rawSpeech.isBlank()) return

        // Strictly enforce Romanized script rendering to prevent pronunciation issues
        val cleanSpeech = enforceRomanizedScript(rawSpeech)

        // Request AudioFocus before playback starts to prevent sound overlays
        val focusGranted = requestAudioFocus()
        if (!focusGranted) {
            Log.w(TAG, "Could not acquire transient AudioFocus, continuing playback anyway.")
        }

        val params = Bundle().apply {
            // Force fully local synthesis (no internet required)
            putString(TextToSpeech.Engine.KEY_FEATURE_NETWORK_SYNTHESIS, "false")
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, UTTERANCE_ID)
        }

        tts?.speak(cleanSpeech, TextToSpeech.QUEUE_FLUSH, params, UTTERANCE_ID)
    }

    fun stop() {
        tts?.stop()
        abandonAudioFocus()
    }

    fun shutdown() {
        tts?.stop()
        abandonAudioFocus()
        tts?.shutdown()
        tts = null
        isReady = false
    }
}
