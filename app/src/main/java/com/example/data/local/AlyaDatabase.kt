package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.local.dao.CallSessionDao
import com.example.data.local.dao.CommandSequenceDao
import com.example.data.local.dao.ConversationDao
import com.example.data.local.dao.LinkedDeviceDao
import com.example.data.local.dao.MemoryDao
import com.example.data.local.dao.MessageDao
import com.example.data.local.dao.OfflineCommandDao
import com.example.data.local.dao.OfflineCommandLogDao
import com.example.data.local.dao.ScheduledTaskDao
import com.example.data.local.dao.CustomWakeWordDao
import com.example.data.local.dao.WeatherDao
import com.example.data.local.entity.CachedWeatherEntity
import com.example.data.local.entity.CallSessionEntity
import com.example.data.local.entity.CallTranscriptEntryEntity
import com.example.data.local.entity.CommandSequenceEntity
import com.example.data.local.entity.ConversationEntity
import com.example.data.local.entity.LinkedDeviceEntity
import com.example.data.local.entity.MemoryEntity
import com.example.data.local.entity.MessageEntity
import com.example.data.local.entity.OfflineCommandEntity
import com.example.data.local.entity.OfflineCommandLogEntity
import com.example.data.local.entity.ScheduledTaskEntity
import com.example.data.local.entity.CustomWakeWordEntity

@Database(
    entities = [
        MemoryEntity::class,
        ConversationEntity::class,
        MessageEntity::class,
        ScheduledTaskEntity::class,
        LinkedDeviceEntity::class,
        OfflineCommandEntity::class,
        OfflineCommandLogEntity::class,
        CustomWakeWordEntity::class,
        CallSessionEntity::class,
        CallTranscriptEntryEntity::class,
        CachedWeatherEntity::class,
        CommandSequenceEntity::class
    ],
    version = 11,
    exportSchema = false
)
abstract class AlyaDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun scheduledTaskDao(): ScheduledTaskDao
    abstract fun linkedDeviceDao(): LinkedDeviceDao
    abstract fun offlineCommandDao(): OfflineCommandDao
    abstract fun offlineCommandLogDao(): OfflineCommandLogDao
    abstract fun customWakeWordDao(): CustomWakeWordDao
    abstract fun callSessionDao(): CallSessionDao
    abstract fun weatherDao(): WeatherDao
    abstract fun commandSequenceDao(): CommandSequenceDao

    companion object {
        @Volatile
        private var INSTANCE: AlyaDatabase? = null

        fun getDatabase(context: Context): AlyaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AlyaDatabase::class.java,
                    "alya_assistant_db"
                ).fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
