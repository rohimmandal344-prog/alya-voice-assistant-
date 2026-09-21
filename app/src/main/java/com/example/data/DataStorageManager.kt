package com.example.data

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File

/**
 * DataStorageManager
 *
 * A data management module that checks for external storage availability to handle
 * OBB file downloads and persistent offline storage paths within the app's
 * internal Android data folder.
 */
class DataStorageManager(private val context: Context) {

    companion object {
        private const val TAG = "DataStorageManager"
    }

    /**
     * Checks if external storage is available for read and write.
     */
    fun isExternalStorageWritable(): Boolean {
        return Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
    }

    /**
     * Checks if external storage is available to at least read.
     */
    fun isExternalStorageReadable(): Boolean {
        val state = Environment.getExternalStorageState()
        return state == Environment.MEDIA_MOUNTED || state == Environment.MEDIA_MOUNTED_READ_ONLY
    }

    /**
     * Gets the directory for the application's OBB files.
     * This path is used to store large assets like expansion files.
     *
     * @return The OBB directory file, or null if not available.
     */
    fun getObbStorageDirectory(): File? {
        if (!isExternalStorageWritable()) {
            Log.w(TAG, "External storage not writable, cannot access OBB directory.")
            return null
        }
        val obbDir = context.obbDir
        if (obbDir != null && !obbDir.exists()) {
            obbDir.mkdirs()
        }
        return obbDir
    }

    /**
     * Gets the persistent offline storage path within the app's internal Android data folder
     * on the external storage (e.g., Android/data/com.example/files).
     *
     * @param type The type of files directory to return. May be null for the root of the files directory.
     * @return The files directory file, or null if not available.
     */
    fun getPersistentOfflineStoragePath(type: String? = null): File? {
        if (!isExternalStorageWritable()) {
            Log.w(TAG, "External storage not writable, falling back to internal storage.")
            return getInternalPersistentStoragePath()
        }
        val externalFilesDir = context.getExternalFilesDir(type)
        if (externalFilesDir != null && !externalFilesDir.exists()) {
            externalFilesDir.mkdirs()
        }
        return externalFilesDir ?: getInternalPersistentStoragePath()
    }

    /**
     * Gets the internal persistent storage path (data/data/com.example/files).
     * This is guaranteed to be available and private to the app.
     *
     * @return The internal files directory file.
     */
    fun getInternalPersistentStoragePath(): File {
        val internalFilesDir = context.filesDir
        if (!internalFilesDir.exists()) {
            internalFilesDir.mkdirs()
        }
        return internalFilesDir
    }
    
    /**
     * Utility to create a specific folder for downloading OBB files.
     */
    fun prepareObbDownloadPath(fileName: String): File? {
        val obbDir = getObbStorageDirectory() ?: return null
        val downloadFile = File(obbDir, fileName)
        Log.i(TAG, "Prepared OBB download path: \${downloadFile.absolutePath}")
        return downloadFile
    }
}
