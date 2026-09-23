package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.CommandSequenceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CommandSequenceDao {

    @Query("SELECT * FROM command_sequences ORDER BY createdAt DESC")
    fun getAllSequencesFlow(): Flow<List<CommandSequenceEntity>>

    @Query("SELECT * FROM command_sequences WHERE id = :id LIMIT 1")
    suspend fun getSequenceById(id: String): CommandSequenceEntity?

    @Query("SELECT * FROM command_sequences WHERE status IN ('PENDING', 'OFFLINE_QUEUED', 'EXECUTING') ORDER BY createdAt ASC")
    suspend fun getPendingOrOfflineSequences(): List<CommandSequenceEntity>

    @Query("SELECT * FROM command_sequences ORDER BY lastExecutedTimestamp DESC LIMIT 1")
    suspend fun getLatestSequence(): CommandSequenceEntity?

    @Query("SELECT * FROM command_sequences WHERE originalPrompt LIKE '%' || :query || '%' OR title LIKE '%' || :query || '%' ORDER BY lastExecutedTimestamp DESC LIMIT 1")
    suspend fun findMatchingSequence(query: String): CommandSequenceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSequence(sequence: CommandSequenceEntity)

    @Update
    suspend fun updateSequence(sequence: CommandSequenceEntity)

    @Query("UPDATE command_sequences SET status = :status, lastExecutedTimestamp = :timestamp, lastValidStateJson = :stateJson, lastError = :error WHERE id = :id")
    suspend fun updateSequenceStatus(id: String, status: String, timestamp: Long, stateJson: String?, error: String?)

    @Query("DELETE FROM command_sequences WHERE id = :id")
    suspend fun deleteSequenceById(id: String)

    @Query("DELETE FROM command_sequences WHERE status = 'COMPLETED' AND createdAt < :thresholdTimestamp")
    suspend fun purgeOldCompletedSequences(thresholdTimestamp: Long)
}
