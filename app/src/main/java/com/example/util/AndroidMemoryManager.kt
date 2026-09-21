package com.example.util

import android.content.Context
import android.hardware.HardwareBuffer
import android.os.Build
import android.os.SharedMemory
import android.system.OsConstants
import android.util.Log
import androidx.annotation.RequiresApi
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * Modern Android 10+ (API 29+) compliant memory allocation manager.
 * Replaces legacy ashmem pinning with android.os.SharedMemory, HardwareBuffer (AHardwareBuffer NDK equivalent),
 * and direct ByteBuffer allocations.
 */
object AndroidMemoryManager {
    private const val TAG = "AndroidMemoryManager"

    /**
     * Allocates a memory buffer compliant with Android Q+ (API 29+).
     * Uses android.os.SharedMemory on API 27+ / API 29+ to prevent ashmem pinning deprecation logs.
     */
    fun allocateCompliantBuffer(size: Int, debugName: String = "alya_buffer"): ByteBuffer {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val sharedMemory = SharedMemory.create(debugName, size)
                val buffer = sharedMemory.mapReadWrite()
                buffer.order(ByteOrder.nativeOrder())
                Log.d(TAG, "Allocated $size bytes using API 29+ android.os.SharedMemory for '$debugName'")
                buffer
            } catch (e: Exception) {
                Log.w(TAG, "SharedMemory allocation fallback to direct ByteBuffer for '$debugName': ${e.message}")
                allocateDirectByteBuffer(size)
            }
        } else {
            allocateDirectByteBuffer(size)
        }
    }

    /**
     * Creates an Android 10+ (API 29+) compliant SharedMemory object for native C++/NDK interop.
     * Replaces legacy ashmem file descriptors with ASharedMemory native descriptors.
     */
    fun createSharedMemoryForNative(name: String, size: Int, readOnly: Boolean = false): SharedMemory? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return null
        return try {
            val sharedMemory = SharedMemory.create(name, size)
            if (readOnly) {
                sharedMemory.setProtect(OsConstants.PROT_READ)
            } else {
                sharedMemory.setProtect(OsConstants.PROT_READ or OsConstants.PROT_WRITE)
            }
            Log.d(TAG, "Created API 29+ compliant SharedMemory '$name' ($size bytes)")
            sharedMemory
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create SharedMemory '$name': ${e.message}")
            null
        }
    }

    /**
     * Allocates a HardwareBuffer compliant with AHardwareBuffer NDK layer on Android 8.0+ (API 26+) / 10+ (API 29+).
     * Prevents ashmem pinning by using native graphic/DSP hardware backing memory.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun allocateHardwareBuffer(
        width: Int,
        height: Int = 1,
        format: Int = HardwareBuffer.BLOB,
        layers: Int = 1,
        usage: Long = HardwareBuffer.USAGE_CPU_READ_OFTEN or HardwareBuffer.USAGE_CPU_WRITE_OFTEN
    ): HardwareBuffer? {
        return try {
            val buffer = HardwareBuffer.create(width, height, format, layers, usage)
            Log.d(TAG, "Allocated HardwareBuffer (AHardwareBuffer compliant) width=$width, height=$height")
            buffer
        } catch (e: Exception) {
            Log.e(TAG, "Failed to allocate HardwareBuffer: ${e.message}")
            null
        }
    }

    /**
     * Reads asset content directly into a TFLite-compatible direct ByteBuffer.
     */
    fun loadAssetBuffer(context: Context, assetName: String): ByteBuffer? {
        return try {
            context.assets.openFd(assetName).use { assetFd ->
                FileInputStream(assetFd.fileDescriptor).use { inputStream ->
                    val fileChannel = inputStream.channel
                    val startOffset = assetFd.startOffset
                    val declaredLength = assetFd.declaredLength
                    val buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
                    buffer.order(ByteOrder.nativeOrder())
                    buffer
                }
            }
        } catch (e: Exception) {
            try {
                context.assets.open(assetName).use { inputStream ->
                    val bytes = inputStream.readBytes()
                    val buffer = allocateDirectByteBuffer(bytes.size)
                    buffer.put(bytes)
                    buffer.rewind()
                    buffer
                }
            } catch (ex: Exception) {
                Log.w(TAG, "Asset buffer load failed for '$assetName': ${ex.message}")
                null
            }
        }
    }

    /**
     * Reads file content directly into a TFLite-compatible direct ByteBuffer for offline storage.
     */
    fun loadFileBuffer(file: File): ByteBuffer? {
        return try {
            FileInputStream(file).use { inputStream ->
                val fileChannel = inputStream.channel
                val buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileChannel.size())
                buffer.order(ByteOrder.nativeOrder())
                buffer
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load offline file buffer: ${e.message}")
            null
        }
    }

    /**
     * Safely unmaps a mapped SharedMemory buffer on API 27+.
     */
    fun unmapBuffer(buffer: ByteBuffer) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            try {
                SharedMemory.unmap(buffer)
            } catch (e: Exception) {
                Log.w(TAG, "Error unmapping SharedMemory buffer: ${e.message}")
            }
        }
    }

    fun allocateDirectByteBuffer(size: Int): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(size)
        buffer.order(ByteOrder.nativeOrder())
        return buffer
    }
}

