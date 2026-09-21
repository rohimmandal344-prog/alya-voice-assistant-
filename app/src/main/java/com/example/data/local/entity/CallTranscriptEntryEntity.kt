package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "call_transcript_entries",
    indices = [Index(value = ["sessionId"])]
)
data class CallTranscriptEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: String,
    val timestamp: Long,
    val speaker: String, // "caller" or "assistant"
    val text: String,
    val detectedLanguage: String = "en-US",
    val confidence: Float = 1.0f
)
