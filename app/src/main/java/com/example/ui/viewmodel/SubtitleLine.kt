package com.example.ui.viewmodel

data class SubtitleLine(
    val id: String = java.util.UUID.randomUUID().toString(),
    val speaker: String, // "user" or "alya"
    val text: String,
    val isInterim: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
