package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.CustomWakeWordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomWakeWordDao {
    @Query("SELECT * FROM custom_wake_words ORDER BY createdAt DESC")
    fun getAllCustomWakeWordsFlow(): Flow<List<CustomWakeWordEntity>>

    @Query("SELECT * FROM custom_wake_words ORDER BY createdAt DESC")
    suspend fun getAllCustomWakeWords(): List<CustomWakeWordEntity>

    @Query("SELECT * FROM custom_wake_words WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveCustomWakeWord(): CustomWakeWordEntity?

    @Query("SELECT * FROM custom_wake_words WHERE isActive = 1 LIMIT 1")
    fun getActiveCustomWakeWordFlow(): Flow<CustomWakeWordEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomWakeWord(customWakeWord: CustomWakeWordEntity): Long

    @Update
    suspend fun updateCustomWakeWord(customWakeWord: CustomWakeWordEntity)

    @Query("UPDATE custom_wake_words SET isActive = 0")
    suspend fun deactivateAllCustomWakeWords()

    @Query("UPDATE custom_wake_words SET isActive = 1 WHERE id = :id")
    suspend fun activateCustomWakeWord(id: Long)

    @Delete
    suspend fun deleteCustomWakeWord(customWakeWord: CustomWakeWordEntity)

    @Query("DELETE FROM custom_wake_words WHERE id = :id")
    suspend fun deleteCustomWakeWordById(id: Long)
}
