package com.example.data.ai.linguistics

import android.util.Log
import com.example.voice.lang.OnDeviceLanguageDetector
import java.util.Locale

enum class DialectType {
    ENGLISH,
    HINDI_HINGLISH,
    BENGALI_BANGLISH,
    JAPANESE,
    RUSSIAN,
    SPANISH,
    FRENCH,
    GERMAN,
    ARABIC,
    KOREAN,
    CHINESE,
    PORTUGUESE,
    ITALIAN,
    TURKISH,
    INDONESIAN,
    URDU,
    TAMIL,
    TELUGU,
    REGIONAL_MIX
}

data class LinguisticProfile(
    val dialect: DialectType,
    val languageCode: String,
    val confidence: Float,
    val promptModifier: String
)

/**
 * LinguisticContextManager (Alya v2.3.0)
 * 
 * Identifies the user's dialect and language (Hindi, Bengali, Japanese, Russian, Spanish,
 * French, German, Arabic, Korean, Chinese, Hinglish, Banglish, etc.) and configures
 * the Gemini model prompt dynamically to accurately interpret colloquial phrasing
 * and respond fluently in the EXACT user language.
 */
class LinguisticContextManager {

    private val detector = OnDeviceLanguageDetector()

    fun analyzeCommandDialect(command: String): LinguisticProfile {
        val trimmed = command.trim()
        val detection = detector.detectLanguage(trimmed, Locale.US)

        val dialect = when (detection.languageCode) {
            "hi" -> DialectType.HINDI_HINGLISH
            "bn" -> DialectType.BENGALI_BANGLISH
            "ja" -> DialectType.JAPANESE
            "ru" -> DialectType.RUSSIAN
            "es" -> DialectType.SPANISH
            "fr" -> DialectType.FRENCH
            "de" -> DialectType.GERMAN
            "ar" -> DialectType.ARABIC
            "ko" -> DialectType.KOREAN
            "zh" -> DialectType.CHINESE
            "pt" -> DialectType.PORTUGUESE
            "it" -> DialectType.ITALIAN
            "tr" -> DialectType.TURKISH
            "id" -> DialectType.INDONESIAN
            "ur" -> DialectType.URDU
            "ta" -> DialectType.TAMIL
            "te" -> DialectType.TELUGU
            else -> DialectType.ENGLISH
        }

        Log.i(TAG, "[LINGUISTIC] Detected dialect '$dialect' (${detection.languageCode}) for command: '$command' with confidence ${detection.confidence}")

        return LinguisticProfile(
            dialect = dialect,
            languageCode = detection.languageCode,
            confidence = detection.confidence,
            promptModifier = buildPromptModifierForDialect(dialect, detection.languageDisplayName)
        )
    }

    private fun buildPromptModifierForDialect(dialect: DialectType, displayName: String): String {
        return when (dialect) {
            DialectType.HINDI_HINGLISH -> """
                [LINGUISTIC CONTEXT: HINDI / HINGLISH DIALECT DETECTED]
                - The user is speaking in Hindi or Hinglish (code-switched Hindi-English).
                - Interpret "kholo" / "open karo" as opening apps or settings.
                - Interpret "chalao" / "baja" as playing media or YouTube videos.
                - Respond fluently in warm, conversational Hindi or natural Hinglish matching the user's phrasing.
            """.trimIndent()

            DialectType.BENGALI_BANGLISH -> """
                [LINGUISTIC CONTEXT: BENGALI / BANGLISH DIALECT DETECTED]
                - The user is speaking in Bengali or Banglish (code-switched Bengali-English).
                - Interpret "kholo" / "open koron" as opening apps or options.
                - Interpret "gan" / "chalao" as playing songs or video reels.
                - Respond fluently in natural, polite Banglish or conversational Bengali matching the user's phrasing.
            """.trimIndent()

            DialectType.JAPANESE -> """
                [LINGUISTIC CONTEXT: JAPANESE DIALECT DETECTED]
                - The user is speaking in Japanese.
                - Respond naturally, politely, and fluently in Japanese (日本語).
            """.trimIndent()

            DialectType.RUSSIAN -> """
                [LINGUISTIC CONTEXT: RUSSIAN DIALECT DETECTED]
                - The user is speaking in Russian.
                - Respond naturally, warmly, and fluently in Russian (Русский язык).
            """.trimIndent()

            DialectType.SPANISH -> """
                [LINGUISTIC CONTEXT: SPANISH DIALECT DETECTED]
                - The user is speaking in Spanish.
                - Respond warmly, conversationally, and fluently in Spanish (Español).
            """.trimIndent()

            DialectType.FRENCH -> """
                [LINGUISTIC CONTEXT: FRENCH DIALECT DETECTED]
                - The user is speaking in French.
                - Respond politely, naturally, and fluently in French (Français).
            """.trimIndent()

            DialectType.GERMAN -> """
                [LINGUISTIC CONTEXT: GERMAN DIALECT DETECTED]
                - The user is speaking in German.
                - Respond clearly, politely, and fluently in German (Deutsch).
            """.trimIndent()

            DialectType.ARABIC -> """
                [LINGUISTIC CONTEXT: ARABIC DIALECT DETECTED]
                - The user is speaking in Arabic.
                - Respond respectfully, warmly, and fluently in Arabic (العربية).
            """.trimIndent()

            DialectType.KOREAN -> """
                [LINGUISTIC CONTEXT: KOREAN DIALECT DETECTED]
                - The user is speaking in Korean.
                - Respond warmly, politely, and fluently in Korean (한국어).
            """.trimIndent()

            DialectType.CHINESE -> """
                [LINGUISTIC CONTEXT: CHINESE DIALECT DETECTED]
                - The user is speaking in Chinese.
                - Respond fluently, naturally, and warmly in Chinese (中文).
            """.trimIndent()

            DialectType.PORTUGUESE -> """
                [LINGUISTIC CONTEXT: PORTUGUESE DIALECT DETECTED]
                - The user is speaking in Portuguese.
                - Respond warmly and fluently in Portuguese (Português).
            """.trimIndent()

            DialectType.ITALIAN -> """
                [LINGUISTIC CONTEXT: ITALIAN DIALECT DETECTED]
                - The user is speaking in Italian.
                - Respond warmly and fluently in Italian (Italiano).
            """.trimIndent()

            DialectType.TURKISH -> """
                [LINGUISTIC CONTEXT: TURKISH DIALECT DETECTED]
                - The user is speaking in Turkish.
                - Respond politely and fluently in Turkish (Türkçe).
            """.trimIndent()

            DialectType.INDONESIAN -> """
                [LINGUISTIC CONTEXT: INDONESIAN DIALECT DETECTED]
                - The user is speaking in Indonesian.
                - Respond warmly and fluently in Indonesian (Bahasa Indonesia).
            """.trimIndent()

            DialectType.URDU -> """
                [LINGUISTIC CONTEXT: URDU DIALECT DETECTED]
                - The user is speaking in Urdu.
                - Respond politely, warmly, and fluently in Urdu (اردو).
            """.trimIndent()

            DialectType.TAMIL -> """
                [LINGUISTIC CONTEXT: TAMIL DIALECT DETECTED]
                - The user is speaking in Tamil.
                - Respond fluently and warmly in Tamil (தமிழ்).
            """.trimIndent()

            DialectType.TELUGU -> """
                [LINGUISTIC CONTEXT: TELUGU DIALECT DETECTED]
                - The user is speaking in Telugu.
                - Respond fluently and warmly in Telugu (తెలుగు).
            """.trimIndent()

            DialectType.REGIONAL_MIX -> """
                [LINGUISTIC CONTEXT: REGIONAL MULTILINGUAL MIX DETECTED]
                - The user is mixing languages and colloquial phrases.
                - Keep responses clear, direct, and empathetic in the user's primary conversational tone.
            """.trimIndent()

            DialectType.ENGLISH -> """
                [LINGUISTIC CONTEXT: STANDARD ENGLISH DIALECT]
                - Process standard English voice commands with direct action resolution.
            """.trimIndent()
        }
    }

    companion object {
        private const val TAG = "LinguisticContextManager"
    }
}

