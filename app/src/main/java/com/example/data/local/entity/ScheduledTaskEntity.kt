package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scheduled_tasks")
data class ScheduledTaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val title: String,
    val description: String = "",
    val scheduledTimeMillis: Long,
    val repeatInterval: String = "NONE", // "NONE", "DAILY", "WEEKLY"
    val isCompleted: Boolean = false,
    val category: String = "REMINDER", // "REMINDER", "TASK", "ALARM"
    val createdAt: Long = System.currentTimeMillis()
)
