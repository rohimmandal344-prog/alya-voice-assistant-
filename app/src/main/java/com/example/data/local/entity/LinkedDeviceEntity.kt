package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "linked_devices")
data class LinkedDeviceEntity(
    @PrimaryKey
    val deviceId: String,
    val name: String,
    val deviceType: String, // "PHONE", "COMPUTER", "TABLET"
    val model: String,
    val osVersion: String,
    val batteryPercentage: Int, // 0..100, or -1 if not applicable
    val isCharging: Boolean,
    val isOnline: Boolean,
    val lastActiveTimeMillis: Long,
    val totalStorageBytes: Long,
    val freeStorageBytes: Long,
    val totalRamBytes: Long,
    val freeRamBytes: Long,
    val networkType: String, // "Wi-Fi", "Cellular (5G)", "Ethernet", "Disconnected"
    val ipAddress: String = "",
    val runningAppsSummary: String = "",
    val backgroundProcessCount: Int = 0,
    val isCurrentDevice: Boolean = false,
    val linkedAccountEmail: String = "",
    val securityFingerprint: String = "",
    val pairingApproved: Boolean = true,
    val createdAtMillis: Long = System.currentTimeMillis()
)
