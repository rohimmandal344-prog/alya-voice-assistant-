package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "offline_command_logs",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["actionType"])
    ]
)
data class OfflineCommandLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val commandText: String,
    val actionType: String,
    val targetAppOrFeature: String?,
    val executionOutput: String?,
    val status: String, // SUCCESS, FAILED, NOT_INSTALLED
    val timestamp: Long = System.currentTimeMillis(),
    val isOfflineResolved: Boolean = true
)
