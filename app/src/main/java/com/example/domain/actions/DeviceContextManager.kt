package com.example.domain.actions

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * DeviceContextState represents real-time runtime state of the device interaction.
 */
data class DeviceContextState(
    val foregroundPackageName: String? = null,
    val foregroundAppLabel: String? = null,
    val lastActiveTask: String? = null,
    val lastTargetElement: String? = null,
    val lastCommand: String? = null,
    val lastExecutionSuccess: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * DeviceContextManager (Alya v3.0 Production)
 *
 * Maintains conversational & operational device context:
 * - Current foreground application package & label
 * - Active UI task context (e.g. YouTube open, Settings open)
 * - Last interacted elements & command continuity
 */
class DeviceContextManager private constructor(private val context: Context) {

    private val _state = MutableStateFlow(DeviceContextState())
    val state: StateFlow<DeviceContextState> = _state.asStateFlow()

    fun updateForegroundApp(packageName: String?, label: String? = null) {
        val appLabel = label ?: resolveAppLabel(packageName)
        _state.value = _state.value.copy(
            foregroundPackageName = packageName,
            foregroundAppLabel = appLabel,
            timestamp = System.currentTimeMillis()
        )
        Log.d(TAG, "Foreground app updated: $appLabel ($packageName)")
    }

    fun recordAction(command: String, target: String? = null, success: Boolean = true, task: String? = null) {
        _state.value = _state.value.copy(
            lastCommand = command,
            lastTargetElement = target,
            lastExecutionSuccess = success,
            lastActiveTask = task ?: _state.value.lastActiveTask,
            timestamp = System.currentTimeMillis()
        )
        Log.d(TAG, "Action recorded: cmd='$command', target='$target', success=$success, task='$task'")
    }

    fun isAppInForeground(packageNameOrPrefix: String): Boolean {
        val currentPkg = _state.value.foregroundPackageName ?: return false
        return currentPkg.contains(packageNameOrPrefix, ignoreCase = true)
    }

    fun getCurrentForegroundPackage(): String? = _state.value.foregroundPackageName
    fun getCurrentForegroundLabel(): String? = _state.value.foregroundAppLabel

    private fun resolveAppLabel(packageName: String?): String? {
        if (packageName == null) return null
        return try {
            val pm = context.packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (_: Exception) {
            packageName.substringAfterLast('.')
        }
    }

    companion object {
        private const val TAG = "DeviceContextManager"

        @Volatile
        private var INSTANCE: DeviceContextManager? = null

        fun getInstance(context: Context): DeviceContextManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DeviceContextManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
