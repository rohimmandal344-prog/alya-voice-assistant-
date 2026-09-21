package com.example.util.diagnostics

import android.content.Context
import android.util.Log
import com.example.data.local.AlyaDatabase
import com.example.data.local.entity.OfflineCommandLogEntity
import com.example.domain.tools.ActionResultStatus
import com.example.domain.tools.ToolExecutionResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * OfflineCommandLogger (Alya v2.2.0)
 * 
 * Asynchronously logs local intent and toggle executions to the Room database table.
 * Enables the Command History view for users to track actions taken on their device.
 */
class OfflineCommandLogger private constructor(context: Context) {

    private val db = AlyaDatabase.getDatabase(context)
    private val logDao = db.offlineCommandLogDao()
    private val scope = CoroutineScope(Dispatchers.IO)

    fun logExecution(
        command: String,
        actionType: String = "SYSTEM_TOGGLE",
        targetAppOrFeature: String?,
        result: ToolExecutionResult,
        isOffline: Boolean = true
    ) {
        scope.launch {
            try {
                val statusStr = when (result.status) {
                    ActionResultStatus.SUCCESS -> "SUCCESS"
                    ActionResultStatus.NOT_INSTALLED -> "NOT_INSTALLED"
                    else -> if (result.success) "SUCCESS" else "FAILED"
                }

                val entity = OfflineCommandLogEntity(
                    commandText = command,
                    actionType = actionType,
                    targetAppOrFeature = targetAppOrFeature ?: result.targetAppOrFeature,
                    executionOutput = result.message.ifBlank { result.output ?: "" },
                    status = statusStr,
                    timestamp = System.currentTimeMillis(),
                    isOfflineResolved = isOffline
                )
                logDao.insertLog(entity)
                Log.d(TAG, "[OFFLINE_LOGGER] Logged command execution: '$command' -> $statusStr")
            } catch (e: Exception) {
                Log.e(TAG, "[OFFLINE_LOGGER] Failed to record command log: ${e.message}")
            }
        }
    }

    fun getAllLogs(): Flow<List<OfflineCommandLogEntity>> {
        return logDao.getAllLogsFlow()
    }

    fun clearHistory(onCleared: (() -> Unit)? = null) {
        scope.launch {
            try {
                logDao.clearAllLogs()
                Log.i(TAG, "[OFFLINE_LOGGER] Cleared command log history.")
                onCleared?.invoke()
            } catch (e: Exception) {
                Log.e(TAG, "[OFFLINE_LOGGER] Error clearing history: ${e.message}")
            }
        }
    }

    companion object {
        private const val TAG = "OfflineCommandLogger"

        @Volatile
        private var instance: OfflineCommandLogger? = null

        fun getInstance(context: Context): OfflineCommandLogger {
            return instance ?: synchronized(this) {
                instance ?: OfflineCommandLogger(context.applicationContext).also { instance = it }
            }
        }
    }
}
