package com.example.data.local

import android.content.Context
import android.util.Log
import com.example.util.diagnostics.DiagnosticLogManager
import com.example.util.diagnostics.DiagnosticStage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

data class StorageOptimizationReport(
    val initialDbSizeBytes: Long = 0,
    val finalDbSizeBytes: Long = 0,
    val freedBytes: Long = 0,
    val prunedMessagesCount: Int = 0,
    val prunedConversationsCount: Int = 0,
    val prunedVoiceSamplesCount: Int = 0,
    val executionTimeMs: Long = 0
)

/**
 * DataCacheManager (Alya v2.3.0)
 *
 * Implements local data retention and stale cache pruning strategy using Room DB
 * and file system inspection. Maintains a minimal app storage footprint (<50MB target)
 * while preserving essential user voice profiles and relevant conversation history.
 */
class DataCacheManager(
    private val context: Context,
    private val database: AlyaDatabase
) {
    private val diagLog = DiagnosticLogManager.instance

    /**
     * Executes the complete storage footprint optimization policy.
     * 
     * @param maxDaysOld Keep conversation history for this many days (default: 14 days)
     * @param maxMessagesPerConversation Cap messages per conversation (default: 100)
     */
    suspend fun optimizeStorageFootprint(
        maxDaysOld: Int = 14,
        maxMessagesPerConversation: Int = 100
    ): StorageOptimizationReport = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val dbFile = context.getDatabasePath("alya_assistant_db")
        val initialDbSize = if (dbFile.exists()) dbFile.length() else 0L

        Log.i(TAG, "[DATA_CACHE_MANAGER] Starting storage footprint optimization. Initial DB size: ${initialDbSize / 1024} KB")

        var prunedMsgs = 0
        var prunedConvs = 0
        var prunedVoiceSamples = 0

        try {
            // 1. Prune old synced messages beyond retention window
            val cutoffTimestamp = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(maxDaysOld.toLong())
            val initialMsgCount = database.messageDao().getTotalMessageCount()

            database.messageDao().pruneOldSyncedMessages(cutoffTimestamp)

            // 2. Cap messages per conversation to recent limit
            val conversations = database.conversationDao().getAllConversationsList()
            for (conv in conversations) {
                database.messageDao().trimExcessMessagesForConversation(conv.id, maxMessagesPerConversation)
            }

            val finalMsgCount = database.messageDao().getTotalMessageCount()
            prunedMsgs = (initialMsgCount - finalMsgCount).coerceAtLeast(0)

            // 3. Prune old unpinned conversations & empty conversations
            val initialConvCount = conversations.size
            database.conversationDao().pruneOldUnpinnedConversations(cutoffTimestamp)
            database.conversationDao().pruneEmptyConversations()
            val finalConvCount = database.conversationDao().getAllConversationsList().size
            prunedConvs = (initialConvCount - finalConvCount).coerceAtLeast(0)

            // 4. Prune stale voice pattern files and temporary audio buffers
            prunedVoiceSamples = cleanStaleVoicePatternFiles()

            // 5. Run WAL checkpoint to reclaim disk space safely
            try {
                database.openHelper.writableDatabase.query(androidx.sqlite.db.SimpleSQLiteQuery("PRAGMA wal_checkpoint(TRUNCATE);")).use { cursor ->
                    cursor.moveToFirst()
                }
            } catch (e: Exception) {
                Log.w(TAG, "PRAGMA wal_checkpoint warning: ${e.message}")
            }

        } catch (e: Exception) {
            Log.e(TAG, "[DATA_CACHE_MANAGER] Error during cache optimization: ${e.message}", e)
        }

        val finalDbSize = if (dbFile.exists()) dbFile.length() else 0L
        val freedBytes = (initialDbSize - finalDbSize).coerceAtLeast(0L)
        val durationMs = System.currentTimeMillis() - startTime

        val report = StorageOptimizationReport(
            initialDbSizeBytes = initialDbSize,
            finalDbSizeBytes = finalDbSize,
            freedBytes = freedBytes,
            prunedMessagesCount = prunedMsgs,
            prunedConversationsCount = prunedConvs,
            prunedVoiceSamplesCount = prunedVoiceSamples,
            executionTimeMs = durationMs
        )

        diagLog.logPerformance(
            operation = "Room Cache Storage Optimization",
            durationMs = durationMs,
            memoryMb = (finalDbSize / (1024 * 1024)).coerceAtLeast(1)
        )

        Log.i(TAG, "[DATA_CACHE_MANAGER] Optimization completed in ${durationMs}ms. Pruned $prunedMsgs msgs, $prunedConvs convs, $prunedVoiceSamples audio files. Final DB size: ${finalDbSize / 1024} KB")

        return@withContext report
    }

    /**
     * Cleans temporary voice recording files and stale audio samples while strictly
     * preserving the authorized user's voice profile.
     */
    private fun cleanStaleVoicePatternFiles(): Int {
        var deletedCount = 0
        try {
            // Clean temp files in cacheDir (.pcm, .wav, .tmp)
            context.cacheDir.listFiles()?.forEach { file ->
                if (file.isFile && (file.name.endsWith(".pcm") || file.name.endsWith(".wav") || file.name.endsWith(".tmp"))) {
                    if (file.delete()) deletedCount++
                }
            }

            // Inspect voice_profiles folder
            val profileDir = File(context.filesDir, "voice_profiles")
            if (profileDir.exists() && profileDir.isDirectory) {
                val validFiles = setOf(
                    "user_voice_profile.dat",
                    "user_voice_sample_1.dat",
                    "user_voice_sample_2.dat",
                    "user_voice_sample_3.dat"
                )

                profileDir.listFiles()?.forEach { file ->
                    // Delete any unrecognized or temporary files older than 1 day
                    if (file.isFile && file.name !in validFiles) {
                        val oneDayAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1)
                        if (file.lastModified() < oneDayAgo) {
                            if (file.delete()) deletedCount++
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error cleaning voice files: ${e.message}")
        }
        return deletedCount
    }

    companion object {
        private const val TAG = "DataCacheManager"
    }
}
