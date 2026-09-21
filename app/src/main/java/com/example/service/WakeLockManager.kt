package com.example.service

import android.content.Context
import android.os.PowerManager
import android.util.Log

/**
 * WakeLockManager
 *
 * Dedicated manager for acquiring and safely releasing high-priority partial wake locks,
 * keeping the CPU active for wake-word audio processing, TFLite inference, and Foreground Service execution
 * without unconstrained battery drain.
 */
class WakeLockManager(private val context: Context) {

    private val powerManager: PowerManager? by lazy {
        context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    }

    private var persistentWakeLock: PowerManager.WakeLock? = null
    private var temporaryWakeLock: PowerManager.WakeLock? = null

    companion object {
        private const val TAG = "WakeLockManager"
        private const val PERSISTENT_LOCK_TAG = "AlyaAssistant::PersistentAudioWakeLock"
        private const val TEMPORARY_LOCK_TAG = "AlyaAssistant::TemporaryAudioWakeLock"
        const val DEFAULT_PERSISTENT_TIMEOUT_MS = 30 * 60 * 1000L // 30-minute safety lease
    }

    /**
     * Acquires a high-priority partial wake lock with safety timeout to prevent battery drain.
     */
    @Synchronized
    fun acquireHighPriorityLock(timeoutMs: Long = DEFAULT_PERSISTENT_TIMEOUT_MS) {
        try {
            if (persistentWakeLock == null) {
                persistentWakeLock = powerManager?.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    PERSISTENT_LOCK_TAG
                )?.apply {
                    setReferenceCounted(false)
                }
            }

            persistentWakeLock?.let { lock ->
                if (!lock.isHeld) {
                    lock.acquire(timeoutMs)
                    Log.i(TAG, "High-priority Partial WakeLock acquired (lease: ${timeoutMs / 1000}s).")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to acquire high-priority partial wake lock: ${e.message}")
        }
    }

    /**
     * Acquires a short-term temporary wake lock lease (e.g. for audio transition or resurrection).
     */
    @Synchronized
    fun acquireTemporaryLease(timeoutMs: Long = 15000L) {
        try {
            if (temporaryWakeLock == null) {
                temporaryWakeLock = powerManager?.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    TEMPORARY_LOCK_TAG
                )?.apply {
                    setReferenceCounted(false)
                }
            }

            temporaryWakeLock?.acquire(timeoutMs)
            Log.d(TAG, "Temporary WakeLock lease acquired for ${timeoutMs}ms.")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to acquire temporary wake lock: ${e.message}")
        }
    }

    /**
     * Releases the persistent wake lock safely.
     */
    @Synchronized
    fun releaseHighPriorityLock() {
        try {
            persistentWakeLock?.let { lock ->
                if (lock.isHeld) {
                    lock.release()
                    Log.i(TAG, "High-priority Partial WakeLock released.")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing high-priority wake lock: ${e.message}")
        }
    }

    /**
     * Releases all held wake locks.
     */
    @Synchronized
    fun releaseAll() {
        releaseHighPriorityLock()
        try {
            temporaryWakeLock?.let { lock ->
                if (lock.isHeld) {
                    lock.release()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing temporary wake lock: ${e.message}")
        }
    }

    fun isHeld(): Boolean {
        return persistentWakeLock?.isHeld == true || temporaryWakeLock?.isHeld == true
    }
}
