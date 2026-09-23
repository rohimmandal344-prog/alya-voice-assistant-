package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "command_sequences")
data class CommandSequenceEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val originalPrompt: String,
    val stepsJson: String, // Ordered JSON array of step actions
    val status: String = "PENDING", // PENDING, EXECUTING, COMPLETED, FAILED, OFFLINE_QUEUED, SYNCED
    val currentStepIndex: Int = 0,
    val totalSteps: Int = 1,
    val isOfflineExecutable: Boolean = true,
    val lastValidStateJson: String? = null, // Snapshot of state before/after execution
    val lastError: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val lastExecutedTimestamp: Long = System.currentTimeMillis()
)
