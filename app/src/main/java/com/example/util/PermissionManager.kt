package com.example.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * PermissionState model for real-time UI state binding.
 */
data class PermissionState(
    val isMicrophoneGranted: Boolean = false,
    val isAccessibilityGranted: Boolean = false,
    val isOverlayGranted: Boolean = false,
    val isNotificationGranted: Boolean = false,
    val isPhoneStateGranted: Boolean = false,
    val isAllRequiredGranted: Boolean = false
)

/**
 * PermissionManager (Alya Assistant)
 *
 * Handles runtime version-aware checks for required system permissions:
 * - Microphone (RECORD_AUDIO)
 * - Accessibility Service (AlyaAccessibilityService)
 * - System Overlay (SYSTEM_ALERT_WINDOW)
 * - Notifications (POST_NOTIFICATIONS on Android 13+)
 * - Phone Call State (READ_PHONE_STATE, ANSWER_PHONE_CALLS)
 *
 * Links permission statuses directly to a UI-bound StateFlow<PermissionState>.
 */
class PermissionManager(private val context: Context) {

    companion object {
        private const val TAG = "PermissionManager"
    }

    private val _permissionState = MutableStateFlow(PermissionState())
    val permissionState: StateFlow<PermissionState> = _permissionState.asStateFlow()

    init {
        refreshPermissions()
    }

    /**
     * Executes version-aware checks for all system permissions and updates the StateFlow.
     */
    fun refreshPermissions(): PermissionState {
        val mic = checkMicrophonePermission()
        val accessibility = checkAccessibilityPermission()
        val overlay = checkOverlayPermission()
        val notification = checkNotificationPermission()
        val phone = checkPhonePermission()

        val allGranted = mic && accessibility && overlay && notification

        val state = PermissionState(
            isMicrophoneGranted = mic,
            isAccessibilityGranted = accessibility,
            isOverlayGranted = overlay,
            isNotificationGranted = notification,
            isPhoneStateGranted = phone,
            isAllRequiredGranted = allGranted
        )

        _permissionState.value = state
        Log.d(TAG, "Refreshed permission state: $state")
        return state
    }

    fun checkMicrophonePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun checkAccessibilityPermission(): Boolean {
        val expectedComponentName = android.content.ComponentName(context, "com.example.service.AlyaAccessibilityService")
        val enabledServicesSetting = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val colonSplitter = android.text.TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServicesSetting)
        while (colonSplitter.hasNext()) {
            val componentNameString = colonSplitter.next()
            val enabledComponent = android.content.ComponentName.unflattenFromString(componentNameString)
            if (enabledComponent != null && (enabledComponent == expectedComponentName || enabledComponent.packageName == context.packageName)) {
                return true
            }
        }
        return false
    }

    fun checkOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    fun checkNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun checkPhonePermission(): Boolean {
        val readState = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        val answerCall = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ANSWER_PHONE_CALLS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        return readState && answerCall
    }

    fun openAppSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening app settings: ${e.message}")
        }
    }

    fun openAccessibilitySettings() {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening accessibility settings: ${e.message}")
        }
    }

    fun openOverlaySettings() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening overlay settings: ${e.message}")
        }
    }
}
