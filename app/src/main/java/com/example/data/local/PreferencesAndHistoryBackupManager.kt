package com.example.data.local

import android.content.Context
import android.util.Log
import com.example.data.local.entity.OfflineCommandLogEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class PreferencesAndHistoryBackupManager {

    companion object {
        private const val TAG = "BackupManager"
        private const val BACKUP_DIR_NAME = "secure_backups"
        private const val BACKUP_FILE_NAME = "alya_backup.json"

        /**
         * Exports all user preferences from 'alya_preferences' and command logs from the database
         * to a secure local JSON file in the app's internal storage.
         */
        suspend fun exportBackup(context: Context): File? = withContext(Dispatchers.IO) {
            try {
                val backupDir = File(context.filesDir, BACKUP_DIR_NAME)
                if (!backupDir.exists()) {
                    backupDir.mkdirs()
                }
                val backupFile = File(backupDir, BACKUP_FILE_NAME)

                val rootObject = JSONObject()
                rootObject.put("backup_version", 1)
                rootObject.put("timestamp", System.currentTimeMillis())

                // 1. Export Preferences
                val sharedPrefs = context.getSharedPreferences("alya_preferences", Context.MODE_PRIVATE)
                val allPrefs = sharedPrefs.all
                val prefsJson = JSONObject()
                for ((key, value) in allPrefs) {
                    if (value != null) {
                        prefsJson.put(key, value)
                    }
                }
                rootObject.put("preferences", prefsJson)

                // 2. Export Command History
                val database = AlyaDatabase.getDatabase(context)
                val logDao = database.offlineCommandLogDao()
                val logs = logDao.getRecentLogs(500) // export up to last 500 logs

                val logsArray = JSONArray()
                for (log in logs) {
                    val logJson = JSONObject()
                    logJson.put("commandText", log.commandText)
                    logJson.put("actionType", log.actionType)
                    logJson.put("targetAppOrFeature", log.targetAppOrFeature ?: JSONObject.NULL)
                    logJson.put("executionOutput", log.executionOutput ?: JSONObject.NULL)
                    logJson.put("status", log.status)
                    logJson.put("timestamp", log.timestamp)
                    logJson.put("isOfflineResolved", log.isOfflineResolved)
                    logsArray.put(logJson)
                }
                rootObject.put("command_history", logsArray)

                // Write to secure file
                backupFile.writeText(rootObject.toString(2))
                Log.i(TAG, "Successfully exported backup to ${backupFile.absolutePath}")
                backupFile
            } catch (e: Exception) {
                Log.e(TAG, "Error exporting backup: ${e.message}", e)
                null
            }
        }

        /**
         * Imports user preferences and command logs from the secure local JSON file back into the app,
         * merging/overwriting as appropriate.
         */
        suspend fun importBackup(context: Context): Boolean = withContext(Dispatchers.IO) {
            try {
                val backupDir = File(context.filesDir, BACKUP_DIR_NAME)
                val backupFile = File(backupDir, BACKUP_FILE_NAME)
                if (!backupFile.exists()) {
                    Log.e(TAG, "Backup file does not exist at ${backupFile.absolutePath}")
                    return@withContext false
                }

                val jsonContent = backupFile.readText()
                val rootObject = JSONObject(jsonContent)

                // 1. Import Preferences
                if (rootObject.has("preferences")) {
                    val prefsJson = rootObject.getJSONObject("preferences")
                    val sharedPrefs = context.getSharedPreferences("alya_preferences", Context.MODE_PRIVATE)
                    val editor = sharedPrefs.edit()

                    val keys = prefsJson.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        when (val value = prefsJson.get(key)) {
                            is Boolean -> editor.putBoolean(key, value)
                            is Int -> editor.putInt(key, value)
                            is Long -> editor.putLong(key, value)
                            is Float -> editor.putFloat(key, value)
                            is Double -> editor.putFloat(key, value.toFloat()) // SharedPreferences uses Float
                            is String -> editor.putString(key, value)
                            else -> editor.putString(key, value.toString())
                        }
                    }
                    editor.apply()
                }

                // 2. Import Command History
                if (rootObject.has("command_history")) {
                    val logsArray = rootObject.getJSONArray("command_history")
                    val database = AlyaDatabase.getDatabase(context)
                    val logDao = database.offlineCommandLogDao()

                    // Clear existing command logs before restoring
                    logDao.clearAllLogs()

                    for (i in 0 until logsArray.length()) {
                        val logJson = logsArray.getJSONObject(i)
                        val targetApp = if (logJson.isNull("targetAppOrFeature")) null else logJson.getString("targetAppOrFeature")
                        val execOutput = if (logJson.isNull("executionOutput")) null else logJson.getString("executionOutput")

                        val logEntity = OfflineCommandLogEntity(
                            commandText = logJson.getString("commandText"),
                            actionType = logJson.getString("actionType"),
                            targetAppOrFeature = targetApp,
                            executionOutput = execOutput,
                            status = logJson.getString("status"),
                            timestamp = logJson.getLong("timestamp"),
                            isOfflineResolved = logJson.optBoolean("isOfflineResolved", true)
                        )
                        logDao.insertLog(logEntity)
                    }
                }

                Log.i(TAG, "Successfully imported backup from ${backupFile.absolutePath}")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error importing backup: ${e.message}", e)
                false
            }
        }

        /**
         * Checks if a local backup file exists and is readable.
         */
        fun isBackupAvailable(context: Context): Boolean {
            val backupFile = File(File(context.filesDir, BACKUP_DIR_NAME), BACKUP_FILE_NAME)
            return backupFile.exists() && backupFile.length() > 0
        }

        /**
         * Returns details of the current backup file if available.
         */
        fun getBackupFileInfo(context: Context): String {
            val backupFile = File(File(context.filesDir, BACKUP_DIR_NAME), BACKUP_FILE_NAME)
            if (!backupFile.exists()) return "No backup found"
            val lastModified = backupFile.lastModified()
            val sizeKb = backupFile.length() / 1024.0
            return "Backup found: ${String.format("%.2f", sizeKb)} KB (Saved: ${android.text.format.DateFormat.format("yyyy-MM-dd HH:mm", lastModified)})"
        }
    }
}
