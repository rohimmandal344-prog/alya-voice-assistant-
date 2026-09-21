package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "custom_wake_words")
data class CustomWakeWordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val word: String,
    val audioFilePath: String,
    val isActive: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
