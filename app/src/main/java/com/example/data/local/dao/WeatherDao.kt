package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.CachedWeatherEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WeatherDao {

    @Query("SELECT * FROM cached_weather WHERE locationKey = :key LIMIT 1")
    suspend fun getWeatherByKey(key: String): CachedWeatherEntity?

    @Query("SELECT * FROM cached_weather ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestWeather(): CachedWeatherEntity?

    @Query("SELECT * FROM cached_weather ORDER BY timestamp DESC")
    fun getAllCachedWeatherFlow(): Flow<List<CachedWeatherEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateWeather(weather: CachedWeatherEntity)

    @Query("DELETE FROM cached_weather WHERE timestamp < :thresholdTimestamp")
    suspend fun purgeOldWeather(thresholdTimestamp: Long)

    @Query("DELETE FROM cached_weather WHERE locationKey = :key")
    suspend fun deleteByKey(key: String)
}
