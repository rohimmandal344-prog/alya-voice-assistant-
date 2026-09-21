package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "offline_commands",
    indices = [
        Index(value = ["triggerPhrase"]),
        Index(value = ["actionType"])
    ]
)
data class OfflineCommandEntity(
    @PrimaryKey
    val triggerPhrase: String,
    val actionType: String, // e.g. "VOLUME_UP", "VOLUME_DOWN", "VOLUME_SET", "WIFI_ON", "WIFI_OFF", "FLASHLIGHT_ON", "FLASHLIGHT_OFF", "BLUETOOTH_ON", "BLUETOOTH_OFF", "SYSTEM_SETTING"
    val systemIntentAction: String? = null,
    val targetValue: Int? = null, // e.g., volume level %, brightness level %
    val extraCategory: String? = "DEVICE_CONTROL", // "VOLUME", "CONNECTIVITY", "HARDWARE", "SETTINGS"
    val feedbackText: String,
    val isSystemDefault: Boolean = true,
    val lastUsedTimestamp: Long = System.currentTimeMillis()
)

