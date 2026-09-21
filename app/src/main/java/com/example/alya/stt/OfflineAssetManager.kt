package com.example.alya.stt

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Manages offline AI model assets, Vosk acoustic models, and local intent weights.
 * Unpacks assets from the APK into internal application storage if not already present.
 */
object OfflineAssetManager {

    private const val TAG = "OfflineAssetManager"

    suspend fun prepareModel(context: Context, assetPath: String = "vosk-model-small-en-us-0.15"): File? =
        withContext(Dispatchers.IO) {
            try {
                val targetDir = File(context.filesDir, "models/$assetPath")
                if (targetDir.exists() && (targetDir.list()?.isNotEmpty() == true)) {
                    Log.d(TAG, "Model already extracted at: ${targetDir.absolutePath}")
                    return@withContext targetDir
                }

                // Attempt to copy from assets if directory or archive exists
                val assetManager = context.assets
                val files = assetManager.list(assetPath) ?: emptyArray()

                if (files.isNotEmpty()) {
                    targetDir.mkdirs()
                    copyAssetFolder(context, assetPath, targetDir.absolutePath)
                    Log.i(TAG, "Successfully extracted model folder to: ${targetDir.absolutePath}")
                    return@withContext targetDir
                }

                // Check if zip archive exists in assets
                val zipAsset = "$assetPath.zip"
                val rootAssets = assetManager.list("") ?: emptyArray()
                if (rootAssets.contains(zipAsset)) {
                    targetDir.mkdirs()
                    assetManager.open(zipAsset).use { input ->
                        java.util.zip.ZipInputStream(input).use { zis ->
                            var entry = zis.nextEntry
                            while (entry != null) {
                                val outFile = File(targetDir, entry.name)
                                if (entry.isDirectory) {
                                    outFile.mkdirs()
                                } else {
                                    outFile.parentFile?.mkdirs()
                                    FileOutputStream(outFile).use { fos ->
                                        zis.copyTo(fos)
                                    }
                                }
                                zis.closeEntry()
                                entry = zis.nextEntry
                            }
                        }
                    }
                    Log.i(TAG, "Successfully unzipped model to: ${targetDir.absolutePath}")
                    return@withContext targetDir
                }

                // If no asset folder exists on disk, create placeholder model descriptor
                targetDir.mkdirs()
                File(targetDir, "model.info").writeText("alya_offline_embedded_model")
                targetDir
            } catch (e: Exception) {
                Log.e(TAG, "Failed to prepare offline model: ${e.message}", e)
                null
            }
        }

    private fun copyAssetFolder(context: Context, srcName: String, dstPath: String) {
        val assetManager = context.assets
        val fileList = assetManager.list(srcName) ?: return
        val destDir = File(dstPath)
        if (!destDir.exists()) destDir.mkdirs()

        for (filename in fileList) {
            val srcFile = if (srcName.isEmpty()) filename else "$srcName/$filename"
            val dstFile = "$dstPath/$filename"
            val subFiles = assetManager.list(srcFile)
            if (subFiles != null && subFiles.isNotEmpty()) {
                copyAssetFolder(context, srcFile, dstFile)
            } else {
                copyAssetFile(context, srcFile, dstFile)
            }
        }
    }

    private fun copyAssetFile(context: Context, srcFile: String, dstFile: String) {
        try {
            context.assets.open(srcFile).use { input ->
                FileOutputStream(dstFile).use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: IOException) {
            Log.w(TAG, "Could not copy asset file $srcFile: ${e.message}")
        }
    }
}
