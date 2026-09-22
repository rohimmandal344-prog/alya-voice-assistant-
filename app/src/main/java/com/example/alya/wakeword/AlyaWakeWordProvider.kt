package com.example.alya.wakeword

import kotlinx.coroutines.flow.StateFlow

/**
 * AlyaWakeWordProvider
 *
 * Decouples wake-word detection (Porcupine / Vosk / OpenWakeWord / TFLite)
 * from direct implementation dependencies.
 */
interface AlyaWakeWordProvider {
    val providerId: String
    val supportedKeywords: List<String>
    val isListening: StateFlow<Boolean>

    fun startListening(
        keywords: List<String> = listOf("alya", "alia", "seno"),
        threshold: Float = 0.65f,
        onWakeWordDetected: (String, Float) -> Unit,
        onError: (String) -> Unit
    )

    fun stopListening()
    fun release()
}
