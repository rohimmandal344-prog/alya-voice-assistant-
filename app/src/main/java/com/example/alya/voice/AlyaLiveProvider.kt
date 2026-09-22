package com.example.alya.voice

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class LiveSessionState {
    IDLE,
    CONNECTING,
    CONNECTED,
    LISTENING,
    THINKING,
    SPEAKING,
    INTERRUPTED,
    RECONNECTING,
    ENDED,
    ERROR
}

/**
 * AlyaLiveProvider
 *
 * Real-time low-latency bidirectional voice stream contract (WebSocket / WebRTC).
 */
interface AlyaLiveProvider {
    val providerId: String
    val state: StateFlow<LiveSessionState>

    suspend fun connect(
        endpointUrl: String,
        headers: Map<String, String> = emptyMap(),
        onAudioChunkReceived: (ByteArray) -> Unit,
        onTranscriptReceived: (String, Boolean) -> Unit,
        onError: (String) -> Unit
    )

    suspend fun sendAudioChunk(pcmChunk: ByteArray)
    suspend fun interrupt()
    suspend fun disconnect()
}
