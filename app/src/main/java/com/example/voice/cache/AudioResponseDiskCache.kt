package com.example.voice.cache

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * Lightweight, LRU-bounded disk cache for synthesized audio responses.
 *
 * Benefits:
 * 1. Near-zero (<10ms) playback latency for cached responses and system phrases.
 * 2. Completely bypasses TTS neural synthesis overhead and network queries for repeated utterances.
 * 3. Automatic LRU space management (capped at 25MB).
 */
class AudioResponseDiskCache(private val context: Context) {

    private val cacheDir: File
        get() {
            val dir = File(context.cacheDir, "audio_tts_lru_cache")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return dir
        }

    private val maxCacheSizeBytes: Long = 25 * 1024 * 1024 // 25 MB
    private val maxCachedFiles: Int = 180

    /**
     * Computes a deterministic SHA-256 hash for a specific text and voice synthesis configuration.
     */
    fun computeKey(text: String, voiceName: String, pitch: Float, rate: Float, localeTag: String): String {
        val input = "$text|$voiceName|$pitch|$rate|$localeTag"
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Returns the target File where new synthesized audio should be written.
     */
    fun getTargetAudioFile(key: String): File {
        val dir = cacheDir
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val file = File(dir, "$key.wav")
        try {
            file.parentFile?.mkdirs()
            if (!file.exists()) {
                file.createNewFile()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not pre-create target audio file: ${e.message}")
        }
        return file
    }

    /**
     * Checks if a valid cached audio response file exists on disk.
     */
    fun getCachedAudioFile(key: String): File? {
        val file = File(cacheDir, "$key.wav")
        return if (file.exists() && file.length() > 256) {
            // Update last modified for LRU eviction tracking
            file.setLastModified(System.currentTimeMillis())
            file
        } else {
            null
        }
    }

    /**
     * Plays a cached audio file instantly via Android MediaPlayer.
     */
    fun playCachedAudio(
        file: File,
        onStart: () -> Unit,
        onCompletion: () -> Unit,
        onError: () -> Unit
    ): MediaPlayer? {
        return try {
            val mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(context, Uri.fromFile(file))
                setOnPreparedListener { mp ->
                    onStart()
                    mp.start()
                }
                setOnCompletionListener { mp ->
                    try {
                        mp.release()
                    } catch (e: Exception) { android.util.Log.e("Alya", "Exception handled", e) }
                    onCompletion()
                }
                setOnErrorListener { mp, _, _ ->
                    try {
                        mp.release()
                    } catch (e: Exception) { android.util.Log.e("Alya", "Exception handled", e) }
                    onError()
                    true
                }
                prepareAsync()
            }
            mediaPlayer
        } catch (e: Exception) {
            Log.w(TAG, "Failed to play cached audio file: ${e.message}")
            onError()
            null
        }
    }

    /**
     * Trims cache to stay within maximum disk bounds and file limits.
     */
    suspend fun trimCacheAsync() = withContext(Dispatchers.IO) {
        try {
            val files = cacheDir.listFiles() ?: return@withContext
            var currentSize = files.sumOf { it.length() }

            if (currentSize > maxCacheSizeBytes || files.size > maxCachedFiles) {
                // Sort by lastModified ascending (oldest first)
                val sortedFiles = files.sortedBy { it.lastModified() }
                for (file in sortedFiles) {
                    if (currentSize <= (maxCacheSizeBytes * 0.75) && files.size <= (maxCachedFiles * 0.75)) {
                        break
                    }
                    val len = file.length()
                    if (file.delete()) {
                        currentSize -= len
                    }
                }
                Log.d(TAG, "Audio disk cache trimmed. Current size: ${currentSize / 1024} KB")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error trimming audio disk cache: ${e.message}")
        }
    }

    /**
     * Clear all cached audio files
     */
    fun clearCache() {
        try {
            cacheDir.listFiles()?.forEach { it.delete() }
        } catch (e: Exception) { android.util.Log.e("Alya", "Exception handled", e) }
    }

    companion object {
        private const val TAG = "AudioResponseDiskCache"
    }
}
