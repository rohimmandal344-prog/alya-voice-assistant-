package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.CallSessionEntity
import com.example.data.local.entity.CallTranscriptEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CallSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCallSession(session: CallSessionEntity)

    @Update
    suspend fun updateCallSession(session: CallSessionEntity)

    @Query("SELECT * FROM call_sessions WHERE id = :id LIMIT 1")
    suspend fun getCallSessionById(id: String): CallSessionEntity?

    @Query("SELECT * FROM call_sessions ORDER BY startTime DESC")
    fun getAllCallSessions(): Flow<List<CallSessionEntity>>

    @Query("SELECT * FROM call_sessions ORDER BY startTime DESC")
    suspend fun getAllCallSessionsSync(): List<CallSessionEntity>

    @Query("DELETE FROM call_sessions WHERE id = :id")
    suspend fun deleteCallSession(id: String)

    @Query("DELETE FROM call_sessions")
    suspend fun clearAllCallSessions()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTranscriptEntry(entry: CallTranscriptEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTranscriptEntries(entries: List<CallTranscriptEntryEntity>)

    @Query("SELECT * FROM call_transcript_entries WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getTranscriptsForSession(sessionId: String): Flow<List<CallTranscriptEntryEntity>>

    @Query("SELECT * FROM call_transcript_entries WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getTranscriptsForSessionSync(sessionId: String): List<CallTranscriptEntryEntity>

    @Query("DELETE FROM call_transcript_entries WHERE sessionId = :sessionId")
    suspend fun deleteTranscriptsForSession(sessionId: String)

    @Query("DELETE FROM call_transcript_entries")
    suspend fun clearAllTranscripts()
}
