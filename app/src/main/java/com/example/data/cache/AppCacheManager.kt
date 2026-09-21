package com.example.data.cache

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Cache metrics and breakdown for user files, voice audio, documents, and data.
 */
data class CacheStats(
    val totalSizeBytes: Long = 0L,
    val formattedTotalSize: String = "0 KB",
    val audioCacheBytes: Long = 0L,
    val formattedAudioSize: String = "0 KB",
    val documentsCacheBytes: Long = 0L,
    val formattedDocsSize: String = "0 KB",
    val tempCacheBytes: Long = 0L,
    val formattedTempSize: String = "0 KB",
    val totalFilesCount: Int = 0,
    val isStorageOptimized: Boolean = true
)

/**
 * Local Caching System & Storage Buildup Manager for Alya Assistant (v1.4.3).
 *
 * Responsibilities:
 * 1. Tracks and measures storage across audio waveforms, document exports, AI responses, and temp logs.
 * 2. Manages cache buildup to prevent app bloat during extended conversations and OS updates.
 * 3. Provides intelligent LRU cache pruning when cache exceeds configurable quota (20 MB target).
 * 4. Enables 1-tap granular and full cache clearing with instant UI metrics update.
 */
class AppCacheManager(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val maxAllowedCacheBytes: Long = 20 * 1024 * 1024 // 20 MB threshold limit

    private val _cacheStats = MutableStateFlow(CacheStats())
    val cacheStats: StateFlow<CacheStats> = _cacheStats.asStateFlow()

    // Specific cache sub-directories
    val audioCacheDir: File by lazy {
        File(context.cacheDir, "audio_tts_lru_cache").apply { if (!exists()) mkdirs() }
    }

    val documentsCacheDir: File by lazy {
        File(context.cacheDir, "alya_docs_cache").apply { if (!exists()) mkdirs() }
    }

    val aiResponseCacheDir: File by lazy {
        File(context.cacheDir, "ai_response_cache").apply { if (!exists()) mkdirs() }
    }

    init {
        refreshCacheStats()
        autoManageStorageBuildup()
    }

    /**
     * Recalculates storage metrics across all cached files.
     */
    fun refreshCacheStats() {
        scope.launch {
            val stats = calculateStatsInternal()
            _cacheStats.value = stats
        }
    }

    private suspend fun calculateStatsInternal(): CacheStats = withContext(Dispatchers.IO) {
        try {
            val audioBytes = calculateDirSize(audioCacheDir)
            val docsBytes = calculateDirSize(documentsCacheDir)
            val aiBytes = calculateDirSize(aiResponseCacheDir)

            val totalCacheDirBytes = calculateDirSize(context.cacheDir)
            val codeCacheBytes = calculateDirSize(context.codeCacheDir)
            val totalBytes = totalCacheDirBytes + codeCacheBytes

            val totalFiles = countFiles(context.cacheDir) + countFiles(context.codeCacheDir)
            val isOptimized = totalBytes < maxAllowedCacheBytes

            CacheStats(
                totalSizeBytes = totalBytes,
                formattedTotalSize = formatBytes(totalBytes),
                audioCacheBytes = audioBytes,
                formattedAudioSize = formatBytes(audioBytes),
                documentsCacheBytes = docsBytes,
                formattedDocsSize = formatBytes(docsBytes),
                tempCacheBytes = (totalBytes - audioBytes - docsBytes).coerceAtLeast(0L),
                formattedTempSize = formatBytes((totalBytes - audioBytes - docsBytes).coerceAtLeast(0L)),
                totalFilesCount = totalFiles,
                isStorageOptimized = isOptimized
            )
        } catch (e: Exception) {
            Log.w(TAG, "Error calculating cache stats: ${e.message}")
            CacheStats()
        }
    }

    /**
     * Automatic LRU cleanup if cache exceeds 50MB during extended assistant usage.
     */
    fun autoManageStorageBuildup() {
        scope.launch {
            try {
                val totalBytes = calculateDirSize(context.cacheDir)
                if (totalBytes > maxAllowedCacheBytes) {
                    Log.i(TAG, "Cache size (${formatBytes(totalBytes)}) exceeds quota. Initiating LRU prune.")
                    pruneDirLru(audioCacheDir, 15 * 1024 * 1024)
                    pruneDirLru(documentsCacheDir, 10 * 1024 * 1024)
                    pruneDirLru(aiResponseCacheDir, 5 * 1024 * 1024)
                    refreshCacheStats()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Storage auto-management notice: ${e.message}")
            }
        }
    }

    /**
     * Clears all temporary cache files while preserving user auth and Room database.
     */
    suspend fun clearAllCache(): Boolean = withContext(Dispatchers.IO) {
        try {
            audioCacheDir.listFiles()?.forEach { it.delete() }
            documentsCacheDir.listFiles()?.forEach { it.delete() }
            aiResponseCacheDir.listFiles()?.forEach { it.delete() }

            // Clear generic cache dir files
            context.cacheDir.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    file.deleteRecursively()
                } else {
                    file.delete()
                }
            }
            refreshCacheStats()
            Log.i(TAG, "Successfully cleared all application cache files.")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing cache: ${e.message}")
            false
        }
    }

    /**
     * Clears exclusively audio TTS cache.
     */
    suspend fun clearAudioCache(): Boolean = withContext(Dispatchers.IO) {
        try {
            audioCacheDir.listFiles()?.forEach { it.delete() }
            refreshCacheStats()
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun calculateDirSize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        var size = 0L
        dir.listFiles()?.forEach { file ->
            size += if (file.isDirectory) calculateDirSize(file) else file.length()
        }
        return size
    }

    private fun countFiles(dir: File?): Int {
        if (dir == null || !dir.exists()) return 0
        var count = 0
        dir.listFiles()?.forEach { file ->
            count += if (file.isDirectory) countFiles(file) else 1
        }
        return count
    }

    private fun pruneDirLru(dir: File, targetSizeBytes: Long) {
        if (!dir.exists()) return
        val files = dir.listFiles()?.toList() ?: return
        var currentSize = files.sumOf { it.length() }
        if (currentSize <= targetSizeBytes) return

        // Sort oldest first
        val sorted = files.sortedBy { it.lastModified() }
        for (f in sorted) {
            if (currentSize <= targetSizeBytes) break
            val len = f.length()
            if (f.delete()) {
                currentSize -= len
            }
        }
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
            else -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }

    companion object {
        private const val TAG = "AppCacheManager"
    }
}
