package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class MemoryCategory(val displayName: String) {
    CONVERSATION_CONTEXT("Conversation Context"),
    USER_PREFERENCES("User Preferences"),
    LANGUAGE_PREFERENCES("Language Preferences"),
    ASSISTANT_PREFERENCES("Assistant Preferences"),
    IMPORTANT_FACTS("Important Facts"),
    TASKS("Tasks"),
    REMINDERS("Reminders"),
    RECENT_INTERACTIONS("Recent Interactions")
}

@Entity(
    tableName = "memories",
    indices = [
        Index(value = ["category"])
    ]
)
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val category: String,
    val key: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
