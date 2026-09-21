package com.example.voice.wakeword

/**
 * Predefined customizable wake-word option with metadata and pronunciation guidance.
 */
data class WakeWordOption(
    val id: String,
    val displayName: String,
    val pronunciation: String,
    val description: String,
    val category: String = "Classic",
    val isPrimaryDefault: Boolean = false
)

/**
 * Predefined list of custom assistant names available in settings.
 */
val PREDEFINED_WAKE_WORDS: List<WakeWordOption> = listOf(
    WakeWordOption(
        id = "Alya",
        displayName = "Alya",
        pronunciation = "Ah-ly-uh",
        description = "Primary acoustic formant model with ultra-fast recognition",
        category = "Core Assistant",
        isPrimaryDefault = true
    ),
    WakeWordOption(
        id = "Alia",
        displayName = "Alia",
        pronunciation = "Ah-lee-uh",
        description = "Soft vocal harmonic model optimized for effortless activation",
        category = "Core Assistant"
    ),
    WakeWordOption(
        id = "Seno",
        displayName = "Seno",
        pronunciation = "Seh-noh",
        description = "High-frequency sibilant onset detector with high noise resistance",
        category = "Core Assistant"
    ),
    WakeWordOption(
        id = "Jarvis",
        displayName = "Jarvis",
        pronunciation = "Jhar-vis",
        description = "Classic AI assistant profile with distinct fricative pattern",
        category = "AI Companion"
    ),
    WakeWordOption(
        id = "Luna",
        displayName = "Luna",
        pronunciation = "Loo-nuh",
        description = "Smooth nasal-liquid acoustic contour for ambient environments",
        category = "AI Companion"
    ),
    WakeWordOption(
        id = "Nova",
        displayName = "Nova",
        pronunciation = "Noh-vuh",
        description = "Punchy open vowel onset with high clarity in noisy rooms",
        category = "Futuristic"
    ),
    WakeWordOption(
        id = "Echo",
        displayName = "Echo",
        pronunciation = "Eh-koh",
        description = "Sharp acoustic onset with minimal recognition latency",
        category = "Acoustic"
    ),
    WakeWordOption(
        id = "Iris",
        displayName = "Iris",
        pronunciation = "Eye-ris",
        description = "High-frequency sibilant diphthong formant profile",
        category = "Acoustic"
    ),
    WakeWordOption(
        id = "Friday",
        displayName = "Friday",
        pronunciation = "Fry-day",
        description = "Broadband fricative plosive sequence for reliable trigger",
        category = "AI Companion"
    ),
    WakeWordOption(
        id = "AlyaPro",
        displayName = "Alya Pro",
        pronunciation = "Ah-ly-uh-pro",
        description = "Advanced neural acoustic formant model tuned for continuous ambient speech",
        category = "Melodic"
    )
)

/**
 * Built-in wake words supported by Alya's TensorFlow Lite Wake-Word engine.
 */
enum class WakeWordKeyword(val keywordName: String) {
    ALIA("alia"),
    ALYA("alya"),
    SENO("seno"),
    JARVIS("jarvis"),
    LUNA("luna"),
    NOVA("nova"),
    ECHO("echo"),
    IRIS("iris"),
    FRIDAY("friday"),
    ALYA_PRO("alyapro");

    companion object {
        fun fromString(value: String): WakeWordKeyword {
            return entries.firstOrNull { 
                it.name.equals(value, ignoreCase = true) || it.keywordName.equals(value, ignoreCase = true) 
            } ?: ALYA
        }
    }
}

// Backward compatibility alias during transition
typealias PorcupineBuiltinKeyword = WakeWordKeyword

/**
 * Status and state of the TensorFlow Lite Wake-Word engine.
 */
sealed class WakeWordState {
    data object Idle : WakeWordState()
    data class Listening(val keyword: String) : WakeWordState()
    data class Detected(val keyword: String, val timestamp: Long = System.currentTimeMillis()) : WakeWordState()
    data class Interrupted(val keyword: String, val timestamp: Long = System.currentTimeMillis()) : WakeWordState()
    data class Error(val message: String) : WakeWordState()

    companion object {
        fun fromWakeState(wakeState: WakeState): WakeWordState {
            return when (wakeState) {
                is WakeState.DISABLED -> Idle
                is WakeState.INITIALIZING -> Idle
                is WakeState.READY -> Listening(wakeState.keyword)
                is WakeState.LISTENING -> Listening(wakeState.keyword)
                is WakeState.DETECTED -> Detected(wakeState.keyword, wakeState.timestamp)
                is WakeState.ACTIVE -> Listening(wakeState.keyword)
                is WakeState.ERROR -> Error(wakeState.message)
            }
        }
    }
}

/**
 * Interface for Wake Word detection callback
 */
fun interface WakeWordCallback {
    fun onWakeWordDetected(keywordIndex: Int, keywordName: String)
}
