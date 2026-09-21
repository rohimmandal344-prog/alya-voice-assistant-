package com.example.voice.wakeword

/**
 * Configuration parameters for Alya's local on-device wake-word detection engine.
 *
 * All parameters are tunable and local-first to guarantee zero network latency,
 * optimal power consumption, and zero false positives.
 */
data class WakeWordConfig(
    val keywords: List<String> = listOf("alia", "alya", "seno"),
    val confidenceThreshold: Float = 0.65f,
    val minWordDurationMs: Long = 200L,
    val cooldownWindowMs: Long = 1500L,
    val activeTimeoutMs: Long = 8000L,
    val maxErrorRetries: Int = 3,
    val noiseFloorThresholdMultiplier: Float = 1.35f,
    val sampleRate: Int = 16000,
    val frameLength: Int = 512
)
