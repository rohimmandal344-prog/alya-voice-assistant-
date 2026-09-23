package com.example.data.sync

import android.content.Context
import android.util.Log
import com.example.data.local.AlyaDatabase
import com.example.data.local.entity.CachedWeatherEntity
import com.example.data.local.entity.CommandSequenceEntity
import com.example.domain.weather.WeatherReport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * RoomDataSyncManager
 * 
 * Centralized Room database synchronization layer that caches multi-step command sequences
 * and weather data, ensuring the assistant can retrieve the last known valid state when connectivity is lost.
 */
class RoomDataSyncManager private constructor(private val context: Context) {

    private val db = AlyaDatabase.getDatabase(context)
    private val weatherDao = db.weatherDao()
    private val commandSequenceDao = db.commandSequenceDao()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private const val TAG = "RoomDataSyncManager"

        @Volatile
        private var INSTANCE: RoomDataSyncManager? = null

        fun getInstance(context: Context): RoomDataSyncManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: RoomDataSyncManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    // =========================================================================
    // WEATHER DATA SYNCHRONIZATION & OFFLINE CACHE
    // =========================================================================

    suspend fun syncWeatherReport(query: String, report: WeatherReport, lat: Double = 0.0, lon: Double = 0.0) = withContext(Dispatchers.IO) {
        try {
            val key = if (query.isBlank() || query.equals("local", ignoreCase = true) || query.equals("here", ignoreCase = true)) {
                "default"
            } else {
                query.lowercase().trim().replace(" ", "_")
            }

            val entity = CachedWeatherEntity.fromWeatherReport(key, report, lat, lon)
            weatherDao.insertOrUpdateWeather(entity)
            Log.i(TAG, "[ROOM_SYNC] Weather report successfully cached in Room DB for key='$key', location='${report.locationName}'")
        } catch (e: Exception) {
            Log.e(TAG, "[ROOM_SYNC] Failed to cache weather report to Room DB: ${e.message}", e)
        }
    }

    suspend fun getLastKnownWeather(query: String): WeatherReport? = withContext(Dispatchers.IO) {
        try {
            val key = if (query.isBlank() || query.equals("local", ignoreCase = true) || query.equals("here", ignoreCase = true)) {
                "default"
            } else {
                query.lowercase().trim().replace(" ", "_")
            }

            val cached = weatherDao.getWeatherByKey(key) ?: weatherDao.getLatestWeather() ?: return@withContext null

            val diffMs = System.currentTimeMillis() - cached.timestamp
            val minutesAgo = TimeUnit.MILLISECONDS.toMinutes(diffMs)
            val timeAgoStr = when {
                minutesAgo < 1 -> "just now"
                minutesAgo < 60 -> "$minutesAgo minutes ago"
                minutesAgo < 1440 -> "${minutesAgo / 60} hours ago"
                else -> "${minutesAgo / 1440} days ago"
            }

            val formattedText = "[Offline Cache - Valid state from $timeAgoStr]\n${cached.formattedText}"
            val speechText = "You are currently offline. Here is the last known weather for ${cached.locationName} recorded $timeAgoStr: It was ${Math.round(cached.temperature)} degrees Celsius with ${cached.condition}."

            cached.toWeatherReport().copy(
                formattedText = formattedText,
                speechText = speechText
            )
        } catch (e: Exception) {
            Log.e(TAG, "[ROOM_SYNC] Error retrieving cached weather from Room: ${e.message}", e)
            null
        }
    }

    fun observeAllCachedWeather(): Flow<List<CachedWeatherEntity>> = weatherDao.getAllCachedWeatherFlow()

    // =========================================================================
    // MULTI-STEP COMMAND SEQUENCE CACHING & OFFLINE RESUMPTION
    // =========================================================================

    suspend fun saveCommandSequence(
        id: String = UUID.randomUUID().toString(),
        title: String,
        originalPrompt: String,
        steps: List<Map<String, Any?>>,
        isOfflineExecutable: Boolean = true,
        initialState: Map<String, Any?>? = null
    ): CommandSequenceEntity = withContext(Dispatchers.IO) {
        val stepsArray = JSONArray()
        steps.forEach { step ->
            stepsArray.put(JSONObject(step))
        }

        val initialStateJson = initialState?.let { JSONObject(it).toString() }

        val entity = CommandSequenceEntity(
            id = id,
            title = title,
            originalPrompt = originalPrompt,
            stepsJson = stepsArray.toString(),
            status = "PENDING",
            currentStepIndex = 0,
            totalSteps = steps.size.coerceAtLeast(1),
            isOfflineExecutable = isOfflineExecutable,
            lastValidStateJson = initialStateJson,
            createdAt = System.currentTimeMillis(),
            lastExecutedTimestamp = System.currentTimeMillis()
        )

        commandSequenceDao.insertSequence(entity)
        Log.i(TAG, "[ROOM_SYNC] Saved multi-step command sequence '${entity.title}' (${steps.size} steps) in Room DB.")
        entity
    }

    suspend fun updateCommandSequenceStep(
        sequenceId: String,
        stepIndex: Int,
        status: String,
        stateSnapshot: Map<String, Any?>? = null,
        errorMessage: String? = null
    ) = withContext(Dispatchers.IO) {
        val existing = commandSequenceDao.getSequenceById(sequenceId) ?: return@withContext
        val stateJson = stateSnapshot?.let { JSONObject(it).toString() } ?: existing.lastValidStateJson

        val updated = existing.copy(
            currentStepIndex = stepIndex,
            status = status,
            lastValidStateJson = stateJson,
            lastError = errorMessage,
            lastExecutedTimestamp = System.currentTimeMillis()
        )
        commandSequenceDao.updateSequence(updated)
        Log.i(TAG, "[ROOM_SYNC] Updated sequence '$sequenceId' -> Step $stepIndex/$status")
    }

    suspend fun getLastKnownSequenceState(promptOrQuery: String): CommandSequenceEntity? = withContext(Dispatchers.IO) {
        commandSequenceDao.findMatchingSequence(promptOrQuery) ?: commandSequenceDao.getLatestSequence()
    }

    suspend fun getPendingOrOfflineSequences(): List<CommandSequenceEntity> = withContext(Dispatchers.IO) {
        commandSequenceDao.getPendingOrOfflineSequences()
    }

    fun observeAllSequences(): Flow<List<CommandSequenceEntity>> = commandSequenceDao.getAllSequencesFlow()

    fun purgeOldData() {
        scope.launch {
            val oneDayAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1)
            val sevenDaysAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
            weatherDao.purgeOldWeather(oneDayAgo)
            commandSequenceDao.purgeOldCompletedSequences(sevenDaysAgo)
            Log.d(TAG, "[ROOM_SYNC] Purged expired weather cache and completed command sequences.")
        }
    }
}
