package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.OfflineCommandLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OfflineCommandLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: OfflineCommandLogEntity): Long

    @Query("SELECT * FROM offline_command_logs ORDER BY timestamp DESC")
    fun getAllLogsFlow(): Flow<List<OfflineCommandLogEntity>>

    @Query("SELECT * FROM offline_command_logs ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentLogs(limit: Int = 50): List<OfflineCommandLogEntity>

    @Query("DELETE FROM offline_command_logs")
    suspend fun clearAllLogs()

    @Query("DELETE FROM offline_command_logs WHERE id = :id")
    suspend fun deleteLogById(id: Long)
}
