package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.LinkedDeviceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LinkedDeviceDao {

    @Query("SELECT * FROM linked_devices ORDER BY isCurrentDevice DESC, lastActiveTimeMillis DESC")
    fun getAllDevicesFlow(): Flow<List<LinkedDeviceEntity>>

    @Query("SELECT * FROM linked_devices")
    suspend fun getAllDevices(): List<LinkedDeviceEntity>

    @Query("SELECT * FROM linked_devices WHERE deviceId = :deviceId LIMIT 1")
    suspend fun getDeviceById(deviceId: String): LinkedDeviceEntity?

    @Query("SELECT * FROM linked_devices WHERE isCurrentDevice = 1 LIMIT 1")
    suspend fun getCurrentDevice(): LinkedDeviceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateDevice(device: LinkedDeviceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateDevices(devices: List<LinkedDeviceEntity>)

    @Update
    suspend fun updateDevice(device: LinkedDeviceEntity)

    @Delete
    suspend fun deleteDevice(device: LinkedDeviceEntity)

    @Query("DELETE FROM linked_devices WHERE deviceId = :deviceId")
    suspend fun deleteDeviceById(deviceId: String)

    @Query("UPDATE linked_devices SET name = :newName WHERE deviceId = :deviceId")
    suspend fun renameDevice(deviceId: String, newName: String)

    @Query("UPDATE linked_devices SET isOnline = :isOnline, lastActiveTimeMillis = :lastActive WHERE deviceId = :deviceId")
    suspend fun updateDeviceStatus(deviceId: String, isOnline: Boolean, lastActive: Long)

    @Query("SELECT COUNT(*) FROM linked_devices")
    suspend fun getDeviceCount(): Int
}
