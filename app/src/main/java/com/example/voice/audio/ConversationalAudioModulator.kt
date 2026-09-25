package com.example.voice.audio

import android.util.Log
import java.util.Locale

/**
 * ConversationalAudioModulator
 *
 * Provides Expressive Conversational Audio with Natural Warmth and Adaptive Modulation
 * for Alya's realistic human female voice synthesis engine.
 *
 * Key Characteristics:
 * 1. Conversational Warmth: Friendly, natural, and approachable tone rather than stiff or distant.
 * 2. Expressive Modulation: Dynamic pitch and dynamic range adjustments for natural emphasis, questioning tones, and emotional nuance.
 * 3. Acoustic Clarity & Balance: Clean balance between mid-range warmth and clear high frequencies, eliminating harsh or metallic distortion.
 * 4. Human-like Rhythm & Cadence: Sentence complexity-aware speech pacing, adjusting speed dynamically based on clause length.
 * 5. Realistic Micro-Pauses: Breath-like micro-pause punctuation inserted between clauses, conjunctions, and sentence boundaries.
 * 6. Adaptive Delivery: Contextual tone classification (Informational, Conversational, Empathetic, Inquisitive, Automation Confirmation).
 */
object ConversationalAudioModulator {

    private const val TAG = "AudioModulator"

    /**
     * Delivery context for adaptive tone delivery.
     */
    enum class DeliveryContext {
        INFORMATIONAL,             // System status, settings, battery, weather data, facts -> Clear, structured, articulate
        CONVERSATIONAL,            // Greetings, chit-chat, personal queries -> Casual, warm, engaging
        EMPATHETIC,                // Reassurance, apology, listening -> Sympathetic, supportive undertones
        TECHNICAL,                 // Code, technical commands, deep configurations -> Precise articulation, slower pace
        INQUISITIVE,               // Questions, clarifications -> Expressive rising inflection
        AUTOMATION_CONFIRMATION    // Device toggles, app launches -> Crisp, decisive, responsive
    }

    /**
     * Modulated speech properties computed for a specific sentence chunk.
     */
    data class ModulatedSpeechProps(
        val cleanText: String,
        val pitchMultiplier: Float,
        val rateMultiplier: Float,
        val deliveryContext: DeliveryContext,
        val pauseAfterMs: Long
    )

    /**
     * Analyzes and classifies the input text into a contextual delivery profile.
     */
    fun classifyDeliveryContext(text: String): DeliveryContext {
        val lower = text.lowercase(Locale.ROOT)

        // 1. Inquisitive check
        if (text.trim().endsWith("?") || lower.contains("right?") || lower.contains("would you like") || lower.contains("kya aap")) {
            return DeliveryContext.INQUISITIVE
        }

        // 2. Automation confirmation check
        if (lower.startsWith("done") || lower.startsWith("opening") || lower.startsWith("switched") ||
            lower.contains("turned on") || lower.contains("turned off") || lower.contains("set to") || lower.contains("chalu kar diya")) {
            return DeliveryContext.AUTOMATION_CONFIRMATION
        }

        // 3. Empathetic check
        if (lower.contains("sorry") || lower.contains("don't worry") || lower.contains("take your time") ||
            lower.contains("i understand") || lower.contains("koi baat nahi") || lower.contains("shant ho jao")) {
            return DeliveryContext.EMPATHETIC
        }

        // 4. Technical check (code, API, configuration, parameters)
        if (lower.contains("code") || lower.contains("function") || lower.contains("script") ||
            lower.contains("ip address") || lower.contains("setting") || lower.contains("config") || lower.contains("version")) {
            return DeliveryContext.TECHNICAL
        }

        // 5. Informational check (data, terms, lists, numbers)
        if (lower.contains("percent") || lower.contains("battery") || lower.contains("celsius") ||
            lower.contains("status") || lower.contains("storage") || lower.contains("ram") || lower.contains("schedule")) {
            return DeliveryContext.INFORMATIONAL
        }

        // Default: Conversational warmth
        return DeliveryContext.CONVERSATIONAL
    }

    /**
     * Modulates raw text into a sequence of natural, micro-paused speech chunks
     * with expressive pitch, cadence, and human breath pacing.
     */
    fun modulateForHumanSpeech(
        text: String,
        basePitch: Float = 1.02f,
        baseRate: Float = 0.98f,
        persona: String = "ALYA_EXPRESSIVE_WARM"
    ): List<ModulatedSpeechProps> {
        if (text.isBlank()) return emptyList()

        val context = classifyDeliveryContext(text)

        // Sanitize and expand text into clear phonetics
        val sanitized = prepareAcousticText(text)

        // Split text into natural breath clauses based on punctuation and conjunctions
        val rawClauses = sanitized.split(Regex("(?<=[,.?!।;\n])\\s+")).filter { it.isNotBlank() }
        val clauses = if (rawClauses.isEmpty()) listOf(sanitized) else rawClauses

        val result = mutableListOf<ModulatedSpeechProps>()

        for ((index, clause) in clauses.withIndex()) {
            val isLastClause = index == clauses.size - 1
            val isQuestion = clause.trim().endsWith("?")

            // Persona & Context specific modulation parameters
            var (pitchOffset, rateFactor) = when (context) {
                DeliveryContext.CONVERSATIONAL -> Pair(0.02f, 0.96f)  // Warm, casual, friendly pacing
                DeliveryContext.INFORMATIONAL -> Pair(0.00f, 1.02f)   // Clear, structured, articulate
                DeliveryContext.EMPATHETIC -> Pair(-0.02f, 0.92f)    // Sympathetic, supportive, calming
                DeliveryContext.TECHNICAL -> Pair(-0.01f, 0.90f)     // Precise articulation, measured pace
                DeliveryContext.INQUISITIVE -> Pair(0.05f, 0.98f)    // Expressive rising tone
                DeliveryContext.AUTOMATION_CONFIRMATION -> Pair(0.00f, 1.04f) // Crisp, decisive
            }

            // Adjust pitch for question terminals (expressive questioning tone)
            if (isQuestion) {
                pitchOffset += 0.04f
            }

            // Sentence complexity pacing: longer complex clauses spoken with measured cadence
            val clauseWordCount = clause.split("\\s+".toRegex()).size
            if (clauseWordCount > 12) {
                rateFactor *= 0.95f // Slow down slightly for long complex thoughts
            } else if (clauseWordCount in 1..3 && !isLastClause) {
                rateFactor *= 1.03f // Short conversational openers ("Hey!", "Sure thing,")
            }

            // Calculate final pitch and rate clamped to natural limits
            val finalPitch = (basePitch + pitchOffset).coerceIn(0.85f, 1.35f)
            val finalRate = (baseRate * rateFactor).coerceIn(0.80f, 1.40f)

            // Realistic micro-pause calculation between phrases to mimic human breath
            val pauseMs = when {
                clause.endsWith("?") -> 320L
                clause.endsWith("!") -> 280L
                clause.endsWith(".") || clause.endsWith("।") -> 250L
                clause.endsWith(",") || clause.endsWith(";") -> 140L
                else -> 100L
            }

            result.add(
                ModulatedSpeechProps(
                    cleanText = clause.trim(),
                    pitchMultiplier = finalPitch,
                    rateMultiplier = finalRate,
                    deliveryContext = context,
                    pauseAfterMs = pauseMs
                )
            )
        }

        return result
    }

    /**
     * Sanitizes raw assistant text for clean acoustic balance & clarity.
     * Expands technical terms, removes robotic symbols, and formats phonetics.
     */
    private fun prepareAcousticText(text: String): String {
        var clean = text
            // Strip markdown, json tags, emojis
            .replace(Regex("\\[ACTION:[^\\]]+\\]", RegexOption.IGNORE_CASE), "")
            .replace(Regex("```[\\s\\S]*?```"), "")
            .replace(Regex("[*#_~`>]"), "")
            .replace(Regex("[\\[\\]]"), "")
            .replace(Regex("^[\\s]*[-•*]\\s+", RegexOption.MULTILINE), "")
            .replace(Regex("https?://\\S+"), "the link")
            .replace(Regex("[\\p{So}\\p{Cn}\\x{1F300}-\\x{1F9FF}\\x{1F600}-\\x{1F64F}\\x{1F680}-\\x{1F6FF}]"), "")

        // Multilingual Phonetic Normalization (Hinglish / Banglish / English)
        val phoneticMap = mapOf(
            "\\bAI\\b" to "A I",
            "\\bWi-?Fi\\b" to "Wi-Fi",
            "\\bTTS\\b" to "Text To Speech",
            "\\bGPS\\b" to "G P S",
            "\\bSMS\\b" to "S M S",
            "\\bUSB\\b" to "U S B",
            "\\bUI\\b" to "U I",
            "\\bchalo\\b" to "chah-lo",
            "\\bkaro\\b" to "kah-ro",
            "\\bkijiye\\b" to "key-jee-yay",
            "\\bkardo\\b" to "kar-doe",
            "\\bshuru\\b" to "shoo-roo",
            "\\bband\\b" to "bund",
            "\\bpankha\\b" to "pun-kha",
            "\\bbatti\\b" to "but-tee",
            "\\bkore\\b" to "ko-ray",
            "\\balya\\b" to "ahl-yah",
            "\\bsen-o\\b" to "say-no",
            "\\be\\.g\\." to "for example",
            "\\bi\\.e\\." to "that is",
            "\\betc\\." to "and so on",
            "\\bmin\\b" to "minutes",
            "\\bsec\\b" to "seconds",
            "\\bhr\\b" to "hour",
            "\\bhrs\\b" to "hours",
            "\\b°C\\b" to " degrees Celsius",
            "\\b°F\\b" to " degrees Fahrenheit",
            "\\bkm/h\\b" to " kilometers per hour",
            "\\b%" to " percent",
            "\\bAM\\b" to "A M",
            "\\bPM\\b" to "P M"
        )

        for ((regex, replacement) in phoneticMap) {
            clean = clean.replace(Regex(regex, RegexOption.IGNORE_CASE), replacement)
        }

        // Smooth em-dashes and ellipses into natural breath pauses
        clean = clean
            .replace(Regex("—|--"), ", ")
            .replace(Regex("\\.{3,}"), "... ")
            .replace(Regex("[;:]"), ", ")
            .replace(Regex("\\s+"), " ")
            .trim()

        return clean
    }
}
