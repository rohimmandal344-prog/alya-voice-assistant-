package com.example.alya.voice

import kotlinx.coroutines.flow.Flow

/**
 * AlyaSTTProvider
 *
 * Pluggable Speech-To-Text interface.
 * Decouples speech recognition from Google STT / Vosk / Whisper / Whisper.cpp / Cloud STT.
 */
interface AlyaSTTProvider {
    val providerId: String
    val isOffline: Boolean

    fun startListening(
        language: String = "en",
        onPartialResult: (String) -> Unit,
        onFinalResult: (String) -> Unit,
        onError: (String) -> Unit
    )

    fun stopListening()
    fun cancel()
}

/**
 * AlyaTTSProvider
 *
 * Pluggable Text-To-Speech interface.
 * Decouples speech synthesis from Google TTS / Android System TTS / Piper / Coqui / ElevenLabs.
 */
interface AlyaTTSProvider {
    val providerId: String
    val isOffline: Boolean

    fun speak(
        text: String,
        language: String = "en",
        pitch: Float = 1.0f,
        speechRate: Float = 1.0f,
        onStart: (() -> Unit)? = null,
        onDone: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    )

    fun stop()
    fun shutdown()
}
