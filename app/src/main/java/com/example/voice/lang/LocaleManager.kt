package com.example.voice.lang

import android.util.Log
import java.util.Locale

/**
 * LocaleManager
 * 
 * Dynamically switches and adapts the speech recognition and synthesis locale
 * across all supported world languages (Hindi, Bengali, Japanese, Russian, Spanish,
 * French, German, Arabic, Italian, Portuguese, Korean, Chinese, Turkish, Indonesian,
 * Urdu, Tamil, Telugu, English, Hinglish, Banglish, etc.) based on the identified user dialect.
 */
object LocaleManager {
    private const val TAG = "LocaleManager"
    private val languageDetector = OnDeviceLanguageDetector()

    /**
     * Identifies the dialect of spoken text and returns the corresponding BCP-47 locale tag
     * (e.g. "en-US", "hi-IN", "bn-IN", "ja-JP", "ru-RU", "es-ES", "fr-FR", "de-DE", "ar-SA", etc.).
     */
    fun detectUserDialect(text: String, defaultLocaleTag: String = "en-US"): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return defaultLocaleTag

        val defaultLocale = try {
            Locale.forLanguageTag(defaultLocaleTag)
        } catch (e: Exception) {
            Locale.US
        }

        val result = languageDetector.detectLanguage(trimmed, defaultLocale)
        val detectedTag = result.locale.toLanguageTag()

        Log.i(TAG, "Detected dialect for '$trimmed': language=${result.languageCode}, tag=$detectedTag, confidence=${result.confidence}")
        return detectedTag
    }
}

