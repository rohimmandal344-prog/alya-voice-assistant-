package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.OfflineCommandEntity

@Dao
interface OfflineCommandDao {
    @Query("SELECT * FROM offline_commands")
    suspend fun getAllCommands(): List<OfflineCommandEntity>

    @Query("SELECT * FROM offline_commands WHERE extraCategory = :category")
    suspend fun getCommandsByCategory(category: String): List<OfflineCommandEntity>

    @Query("SELECT * FROM offline_commands WHERE triggerPhrase = :trigger LIMIT 1")
    suspend fun getCommandByTrigger(trigger: String): OfflineCommandEntity?

    @Query("SELECT * FROM offline_commands WHERE LOWER(:query) LIKE '%' || triggerPhrase || '%' ORDER BY LENGTH(triggerPhrase) DESC LIMIT 1")
    suspend fun matchCommandInQuery(query: String): OfflineCommandEntity?

    @Query("SELECT COUNT(*) FROM offline_commands")
    suspend fun getCommandCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCommand(command: OfflineCommandEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCommands(commands: List<OfflineCommandEntity>)

    @Query("DELETE FROM offline_commands")
    suspend fun clearAll()
}

