package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.ScheduledTaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduledTaskDao {

    @Query("SELECT * FROM scheduled_tasks ORDER BY scheduledTimeMillis ASC")
    fun getAllTasksFlow(): Flow<List<ScheduledTaskEntity>>

    @Query("SELECT * FROM scheduled_tasks ORDER BY scheduledTimeMillis ASC")
    suspend fun getAllTasks(): List<ScheduledTaskEntity>

    @Query("SELECT * FROM scheduled_tasks WHERE isCompleted = 0 ORDER BY scheduledTimeMillis ASC")
    fun getPendingTasksFlow(): Flow<List<ScheduledTaskEntity>>

    @Query("SELECT * FROM scheduled_tasks WHERE id = :id")
    suspend fun getTaskById(id: Long): ScheduledTaskEntity?

    @Query("SELECT * FROM scheduled_tasks WHERE isCompleted = 0 AND scheduledTimeMillis >= :currentTimeMillis ORDER BY scheduledTimeMillis ASC")
    suspend fun getUpcomingTasks(currentTimeMillis: Long): List<ScheduledTaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: ScheduledTaskEntity): Long

    @Update
    suspend fun updateTask(task: ScheduledTaskEntity)

    @Delete
    suspend fun deleteTask(task: ScheduledTaskEntity)

    @Query("DELETE FROM scheduled_tasks WHERE id = :id")
    suspend fun deleteTaskById(id: Long)

    @Query("DELETE FROM scheduled_tasks")
    suspend fun clearAllTasks()
}
