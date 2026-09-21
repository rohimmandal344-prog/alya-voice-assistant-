package com.example.util

import android.util.Log

/**
 * SystemThoughtFilter
 * 
 * Strict text parsing & cleaning protocol to remove internal AI reasoning, system thoughts,
 * markdown headers, action tags, emojis, and speaker prefixes before passing responses
 * to UI rendering or Text-To-Speech (TTS).
 */
object SystemThoughtFilter {

    private const val TAG = "SystemThoughtFilter"

    /**
     * Cleans raw model output for UI rendering.
     * Removes internal thoughts, reasoning blocks, action tags, and markdown clutter while retaining human text.
     */
    fun cleanForDisplay(text: String?): String {
        if (text.isNullOrBlank()) return ""
        var clean = text

        // 1. Remove XML/HTML style thought tags
        clean = clean.replace(Regex("(?i)<thought>[\\s\\S]*?</thought>"), "")
        clean = clean.replace(Regex("(?i)<reasoning>[\\s\\S]*?</reasoning>"), "")

        // 2. Remove markdown reasoning headers and thought blocks
        clean = clean.replace(Regex("(?i)\\*\\*Crafting[^*]*\\*\\*"), "")
        clean = clean.replace(Regex("(?i)\\*\\*Checking[^*]*\\*\\*"), "")
        clean = clean.replace(Regex("(?i)\\*\\*Processing[^*]*\\*\\*"), "")
        clean = clean.replace(Regex("(?i)\\*\\*Analyzing[^*]*\\*\\*"), "")
        clean = clean.replace(Regex("(?i)\\*\\*Thinking[^*]*\\*\\*"), "")
        clean = clean.replace(Regex("(?i)\\*\\*Generating[^*]*\\*\\*"), "")
        clean = clean.replace(Regex("(?i)\\*\\*Interpreting[^*]*\\*\\*"), "")
        clean = clean.replace(Regex("(?i)\\*\\*Acknowledge[^*]*\\*\\*"), "")
        clean = clean.replace(Regex("(?i)\\*\\*Initiating[^*]*\\*\\*"), "")
        clean = clean.replace(Regex("(?i)\\*\\*[^*]+\\*\\*"), "") // Any double asterisk header
        clean = clean.replace(Regex("(?i)^thought:.*$", RegexOption.MULTILINE), "")
        clean = clean.replace(Regex("(?i)^reasoning:.*$", RegexOption.MULTILINE), "")
        clean = clean.replace(Regex("(?i)I've registered the user's[^\n]*"), "")
        clean = clean.replace(Regex("(?i)System message:[^\n]*"), "")
        
        // Remove meta-reflections / chain-of-thought reasoning sentences
        clean = clean.replace(Regex("(?i)I've (?:formulated|crafted|processed|determined|revised|been iterating|decided|settled|zeroed in)[^\n.]*\\.?"), "")
        clean = clean.replace(Regex("(?i)My thought process[^\n.]*\\.?"), "")
        clean = clean.replace(Regex("(?i)The next step is[^\n.]*\\.?"), "")
        clean = clean.replace(Regex("(?i)Then,?\\s*I'll provide[^\n.]*\\.?"), "")
        clean = clean.replace(Regex("(?i)followed by (?:the|a) JSON (?:object|block)?[^\n.]*\\.?"), "")
        clean = clean.replace(Regex("(?i)To (?:fulfill|achieve|execute) this (?:request|objective|command)[^\n.]*\\.?"), "")
        clean = clean.replace(Regex("(?i)The user (?:wants|requested|asked) to[^\n.]*\\.?"), "")
        clean = clean.replace(Regex("(?i)I (?:need to|should|must|will) (?:call|use|execute|launch|open|identify) the[^\n.]*\\.?"), "")
        clean = clean.replace(Regex("(?i)Okay, I've got a [^\n.]*\\.?"), "")
        clean = clean.replace(Regex("(?i)The response is intended for voice output[^\n.]*\\.?"), "")
        clean = clean.replace(Regex("(?i)This aligns with the specified length[^\n.]*\\.?"), "")
        clean = clean.replace(Regex("(?i)This adheres to the specified structure[^\n.]*\\.?"), "")
        clean = clean.replace(Regex("(?i)This balances the prompt's requirements[^\n.]*\\.?"), "")

        // 3. Remove embedded Action tags like [ACTION: ...] or ```action ... ```
        clean = clean.replace(Regex("\\[ACTION:[^\\]]+\\]", RegexOption.IGNORE_CASE), "")
        clean = clean.replace(Regex("```action[\\s\\S]*?```", RegexOption.IGNORE_CASE), "")
        clean = clean.replace(Regex("```json[\\s\\S]*?```", RegexOption.IGNORE_CASE), "")
        clean = clean.replace(Regex("(?s)\\{[^{}]*\"(?:command_id|action|intent|device_domain|tool)\"[^{}]*\\}"), "")

        // 4. Strip speaker prefixes
        clean = clean.replace(Regex("^(?:Alya|Alia|Assistant|System|Model):\\s*", RegexOption.IGNORE_CASE), "")

        // 5. Clean excessive whitespace / extra empty lines
        clean = clean.replace(Regex("\n{3,}"), "\n\n").trim()

        return clean
    }

    /**
     * Cleans raw model output for Text-To-Speech (TTS) voice generation.
     * Strips all markdown syntax, headers, bullets, URLs, emojis, and robotic symbols.
     */
    fun cleanForSpeech(text: String?): String {
        val displayClean = cleanForDisplay(text)
        if (displayClean.isBlank()) return ""

        var speechClean = displayClean
            // Remove code blocks
            .replace(Regex("```[\\s\\S]*?```"), "")
            // Remove markdown syntax
            .replace(Regex("[*#_~`>]"), "")
            .replace(Regex("[\\[\\]]"), "")
            .replace(Regex("^[\\s]*[-•*]\\s+", RegexOption.MULTILINE), "")
            // Strip URLs
            .replace(Regex("https?://\\S+"), "")
            // Strip Emojis
            .replace(Regex("[\\p{So}\\p{Cn}\\x{1F300}-\\x{1F9FF}\\x{1F600}-\\x{1F64F}\\x{1F680}-\\x{1F6FF}\\x{2600}-\\x{26FF}]"), "")

        // Normalize whitespace and punctuation
        speechClean = speechClean
            .replace(Regex(",\\s*,"), ",")
            .replace(Regex("\\s+"), " ")
            .trim()

        return speechClean
    }
}
