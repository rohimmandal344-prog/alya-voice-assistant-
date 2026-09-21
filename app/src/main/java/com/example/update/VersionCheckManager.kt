package com.example.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.example.BuildConfig
import com.example.data.local.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Service responsible for:
 * 1. Verifying installed application version dynamically via PackageManager & BuildConfig.
 * 2. Comparing current version against remote configuration file on startup to verify installation success.
 * 3. Detecting version upgrades to trigger the "What's New" / Changelog UI once per update.
 */
class VersionCheckManager(
    private val context: Context,
    private val preferencesManager: PreferencesManager
) {
    private val _isVersionUpgraded = MutableStateFlow(false)
    val isVersionUpgraded: StateFlow<Boolean> = _isVersionUpgraded.asStateFlow()

    private val _showChangelog = MutableStateFlow(false)
    val showChangelog: StateFlow<Boolean> = _showChangelog.asStateFlow()

    /**
     * Gets the active build version name directly from BuildConfig or PackageManager.
     * Never cached or hardcoded.
     */
    fun getCurrentVersionName(): String {
        return try {
            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            pInfo.versionName ?: BuildConfig.VERSION_NAME
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching package info, using BuildConfig.VERSION_NAME", e)
            BuildConfig.VERSION_NAME
        }
    }

    /**
     * Gets active build version code dynamically.
     */
    fun getCurrentVersionCode(): Long {
        return try {
            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode.toLong()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching package code, using BuildConfig.VERSION_CODE", e)
            BuildConfig.VERSION_CODE.toLong()
        }
    }

    /**
     * Performs startup verification:
     * - Compares current runtime version against last seen version.
     * - Triggers "What's New" changelog if current version > last seen version.
     * - Clears obsolete cache if a version bump occurred.
     */
    suspend fun verifyVersionOnStartup() = withContext(Dispatchers.IO) {
        val currentVersion = getCurrentVersionName()
        val lastSeenVersion = preferencesManager.getLastSeenVersion()

        Log.i(TAG, "[DEBUG_LOG] Startup Version Check — Active Version: $currentVersion (Build ${getCurrentVersionCode()}), Last Seen: '$lastSeenVersion' on Thread: ${Thread.currentThread().name}")

        if (lastSeenVersion.isBlank() || lastSeenVersion == "1.0.0") {
            // First install or uninitialized: persist current version without blocking popup
            Log.i(TAG, "[DEBUG_LOG] Initial setup detected. Storing $currentVersion in preferences without triggering changelog popup.")
            preferencesManager.setLastSeenVersion(currentVersion)
            _isVersionUpgraded.value = false
            _showChangelog.value = false
            return@withContext
        }

        if (isVersionNewer(currentVersion, lastSeenVersion)) {
            Log.i(TAG, "[DEBUG_LOG] New version detected ($currentVersion > $lastSeenVersion). Triggering upgrade state & changelog dialog.")
            _isVersionUpgraded.value = true
            _showChangelog.value = true

            // Automatically purge outdated cache directory on upgrade
            try {
                context.cacheDir?.deleteRecursively()
                context.codeCacheDir?.deleteRecursively()
                Log.i(TAG, "[DEBUG_LOG] Successfully purged old cache directory for newly installed version $currentVersion")
            } catch (e: Exception) {
                Log.w(TAG, "[DEBUG_LOG] Cache purge notice: ${e.message}")
            }
        } else {
            Log.i(TAG, "[DEBUG_LOG] Current version ($currentVersion) matches last seen version ($lastSeenVersion). No upgrade dialog needed.")
            _isVersionUpgraded.value = false
            _showChangelog.value = false
        }
    }

    /**
     * Dismisses changelog and persists the current version as last seen.
     */
    fun markChangelogSeen() {
        val currentVersion = getCurrentVersionName()
        preferencesManager.setLastSeenVersion(currentVersion)
        _showChangelog.value = false
        Log.i(TAG, "Changelog acknowledged. Saved lastSeenVersion = $currentVersion")
    }

    private fun isVersionNewer(current: String, lastSeen: String): Boolean {
        if (lastSeen.isBlank() || lastSeen == "1.0.0") return false
        val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }
        val lastSeenParts = lastSeen.split(".").mapNotNull { it.toIntOrNull() }

        val maxLength = maxOf(currentParts.size, lastSeenParts.size)
        for (i in 0 until maxLength) {
            val c = currentParts.getOrElse(i) { 0 }
            val l = lastSeenParts.getOrElse(i) { 0 }
            if (c > l) return true
            if (c < l) return false
        }
        return false
    }

    companion object {
        private const val TAG = "VersionCheckManager"
    }
}
