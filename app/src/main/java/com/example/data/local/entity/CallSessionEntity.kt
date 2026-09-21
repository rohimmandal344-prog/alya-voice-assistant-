package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "call_sessions")
data class CallSessionEntity(
    @PrimaryKey
    val id: String,
    val callerName: String,
    val callerNumber: String,
    val startTime: Long,
    val endTime: Long,
    val durationSeconds: Long,
    val detectedLanguage: String,
    val detectedLocaleTag: String,
    val callSummary: String,
    val disconnectReason: String,
    val errorLog: String = "",
    val status: String = "COMPLETED" // COMPLETED, IN_PROGRESS, MISSED, DECLINED
)
