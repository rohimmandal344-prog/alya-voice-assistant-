package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun getMessagesForConversationFlow(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    suspend fun getMessagesForConversation(conversationId: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE id = :id LIMIT 1")
    suspend fun getMessageById(id: String): MessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Update
    suspend fun updateMessage(message: MessageEntity)

    @Delete
    suspend fun deleteMessage(message: MessageEntity)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteMessagesForConversation(conversationId: String)

    @Query("SELECT COUNT(*) FROM messages WHERE conversationId = :conversationId")
    suspend fun getMessageCount(conversationId: String): Int

    @Query("SELECT * FROM messages WHERE isSynced = 0 ORDER BY timestamp ASC")
    suspend fun getUnsyncedMessages(): List<MessageEntity>

    @Query("UPDATE messages SET isSynced = 1 WHERE isSynced = 0")
    suspend fun markAllMessagesSynced()

    @Query("DELETE FROM messages WHERE conversationId = :conversationId AND id NOT IN (SELECT id FROM messages WHERE conversationId = :conversationId ORDER BY timestamp DESC LIMIT :keepLimit)")
    suspend fun trimExcessMessagesForConversation(conversationId: String, keepLimit: Int = 150)

    @Query("DELETE FROM messages WHERE timestamp < :cutoffTimestamp AND isSynced = 1")
    suspend fun pruneOldSyncedMessages(cutoffTimestamp: Long)

    @Query("SELECT COUNT(*) FROM messages")
    suspend fun getTotalMessageCount(): Int
}
