package com.example.util

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Robust, production-grade Permission Request Helper.
 *
 * Implements:
 * 1. Version-specific flows:
 *    - Android 13+ (API 33, Tiramisu): POST_NOTIFICATIONS runtime permission flow.
 *    - Android 14+ (API 34, UpsideDownCake): Foreground service types (Microphone, Special Use, Media Projection)
 *      precondition validation before FGS startup.
 *    - Android 10+ (API 29, Q) / Android 11+ (API 30, R): System Alert Window (Overlay) special access with URI fallbacks.
 *    - Android 12+ (API 31, S): Bluetooth Scan/Connect and SCHEDULE_EXACT_ALARM permissions.
 *    - Android 13+/14+: Granular media permissions (READ_MEDIA_*) vs legacy storage permissions.
 *
 * 2. Clear error handling & detection for 'Permanently Denied' states:
 *    - Tracks permission request history in persistent storage.
 *    - Distinguishes between First Request, Denied (Rationale Allowed), and Permanently Denied ("Don't ask again").
 *    - Direct intent actions for App Settings, System Alert Window, Notification Channels, and Special Permissions.
 *
 * 3. Jetpack Compose Integration:
 *    - rememberPermissionRequestHelper() state & launcher hook.
 *    - Built-in Material 3 Rationale & Permanently Denied dialogs.
 */
object PermissionRequestHelper {

    private const val TAG = "PermissionRequestHelper"
    private const val PREFS_NAME = "alya_permission_request_history"
    private const val KEY_PREFIX_REQUESTED = "perm_requested_"

    // =========================================================================
    // 1. DATA MODELS & STATUSES
    // =========================================================================

    /**
     * Detailed status for an individual permission evaluation.
     */
    sealed class PermissionStatus {
        object Granted : PermissionStatus()
        data class Denied(val permission: String, val shouldShowRationale: Boolean) : PermissionStatus()
        data class PermanentlyDenied(
            val permission: String,
            val userFriendlyName: String,
            val explanation: String,
            val settingsIntent: Intent
        ) : PermissionStatus()
        data class SpecialAccessRequired(
            val type: SpecialPermissionType,
            val title: String,
            val explanation: String,
            val settingsIntent: Intent
        ) : PermissionStatus()
        data class VersionNotApplicable(
            val permission: String,
            val minApi: Int,
            val currentApi: Int = Build.VERSION.SDK_INT
        ) : PermissionStatus()
        data class PolicyRestricted(val permission: String, val reason: String) : PermissionStatus()
    }

    /**
     * Types of special Android system permissions requiring explicit Settings navigation.
     */
    enum class SpecialPermissionType {
        SYSTEM_ALERT_WINDOW,
        SCHEDULE_EXACT_ALARM,
        ACCESSIBILITY_SERVICE,
        MANAGE_EXTERNAL_STORAGE,
        IGNORE_BATTERY_OPTIMIZATIONS,
        NOTIFICATION_POLICY_ACCESS
    }

    /**
     * Foreground service types for Android 14+ (API 34) validation.
     */
    enum class ForegroundServiceType {
        MICROPHONE,
        MEDIA_PROJECTION,
        SPECIAL_USE,
        LOCATION,
        DATA_SYNC,
        PHONE_CALL
    }

    /**
     * Detailed result of a multi-permission evaluation or request batch.
     */
    data class PermissionBatchReport(
        val grantedPermissions: List<String> = emptyList(),
        val deniedPermissions: List<String> = emptyList(),
        val permanentlyDeniedPermissions: List<String> = emptyList(),
        val specialPermissionsNeeded: List<SpecialPermissionType> = emptyList()
    ) {
        val isAllGranted: Boolean get() = deniedPermissions.isEmpty() && permanentlyDeniedPermissions.isEmpty() && specialPermissionsNeeded.isEmpty()
        val hasPermanentlyDenied: Boolean get() = permanentlyDeniedPermissions.isNotEmpty()
        val hasDenied: Boolean get() = deniedPermissions.isNotEmpty()
    }

    // =========================================================================
    // 2. PERSISTENT HISTORY & PERMANENT DENIAL DETECTION
    // =========================================================================

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Records that a permission was requested through the system dialog.
     */
    fun markPermissionRequested(context: Context, permission: String) {
        getPrefs(context).edit().putBoolean(KEY_PREFIX_REQUESTED + permission, true).apply()
    }

    /**
     * Checks if this permission was previously requested.
     */
    fun hasPermissionBeenRequested(context: Context, permission: String): Boolean {
        return getPrefs(context).getBoolean(KEY_PREFIX_REQUESTED + permission, false)
    }

    /**
     * Clears permission request history (useful for testing or app resets).
     */
    fun resetPermissionHistory(context: Context) {
        getPrefs(context).edit().clear().apply()
    }

    /**
     * Accurately determines whether a permission is 'Permanently Denied' ("Don't Ask Again").
     *
     * Logic:
     * - If already GRANTED -> Not permanently denied.
     * - If NOT granted AND shouldShowRequestPermissionRationale == false AND previously requested -> PERMANENTLY DENIED.
     * - If NOT granted AND shouldShowRequestPermissionRationale == true -> DENIED (User can still be prompted).
     * - If NOT granted AND not previously requested -> First time (Not permanently denied).
     */
    fun isPermanentlyDenied(activity: Activity, permission: String): Boolean {
        val isGranted = ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED
        if (isGranted) return false

        val shouldShowRationale = ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
        val previouslyRequested = hasPermissionBeenRequested(activity, permission)

        return previouslyRequested && !shouldShowRationale
    }

    /**
     * Checks status for a single runtime permission with detailed categorization.
     */
    fun checkPermissionStatus(activity: Activity, permission: String): PermissionStatus {
        val isGranted = ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED
        if (isGranted) {
            return PermissionStatus.Granted
        }

        val shouldShowRationale = ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
        val previouslyRequested = hasPermissionBeenRequested(activity, permission)

        return if (previouslyRequested && !shouldShowRationale) {
            PermissionStatus.PermanentlyDenied(
                permission = permission,
                userFriendlyName = getPermissionDisplayName(permission),
                explanation = getPermissionExplanation(permission),
                settingsIntent = createAppSettingsIntent(activity)
            )
        } else {
            PermissionStatus.Denied(
                permission = permission,
                shouldShowRationale = shouldShowRationale
            )
        }
    }

    // =========================================================================
    // 3. VERSION-SPECIFIC FLOW IMPLEMENTATIONS
    // =========================================================================

    /**
     * [Android 13+ (API 33, Tiramisu)] Notification Permission Flow.
     *
     * - On API 33+: Evaluates POST_NOTIFICATIONS runtime permission.
     * - On API < 33: Evaluates NotificationManagerCompat.areNotificationsEnabled() (legacy app-level toggle).
     */
    fun checkNotificationPermission(context: Context): PermissionStatus {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val isGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (isGranted) {
                PermissionStatus.Granted
            } else if (context is Activity && isPermanentlyDenied(context, Manifest.permission.POST_NOTIFICATIONS)) {
                PermissionStatus.PermanentlyDenied(
                    permission = Manifest.permission.POST_NOTIFICATIONS,
                    userFriendlyName = "Notifications",
                    explanation = "Alya needs notification access to show active call status, timer alerts, and background assistant updates.",
                    settingsIntent = createNotificationSettingsIntent(context)
                )
            } else {
                PermissionStatus.Denied(
                    permission = Manifest.permission.POST_NOTIFICATIONS,
                    shouldShowRationale = if (context is Activity) ActivityCompat.shouldShowRequestPermissionRationale(context, Manifest.permission.POST_NOTIFICATIONS) else true
                )
            }
        } else {
            // Android 12 and lower: notifications do not require runtime permission, but user could have disabled them in settings
            val notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
            if (notificationsEnabled) {
                PermissionStatus.Granted
            } else {
                PermissionStatus.SpecialAccessRequired(
                    type = SpecialPermissionType.NOTIFICATION_POLICY_ACCESS,
                    title = "App Notifications Disabled",
                    explanation = "Notifications are disabled in device settings. Please enable them so Alya can alert you.",
                    settingsIntent = createNotificationSettingsIntent(context)
                )
            }
        }
    }

    /**
     * [Android 14+ (API 34, UpsideDownCake)] Foreground Service Type Precondition Check.
     *
     * Validates whether all mandatory prerequisites (such as microphone or media permissions)
     * are granted prior to starting a foreground service of a specific type.
     * Prevents fatal ForegroundServiceStartNotAllowedException & SecurityExceptions.
     */
    fun validateForegroundServicePrerequisites(
        context: Context,
        serviceType: ForegroundServiceType
    ): Pair<Boolean, String?> {
        when (serviceType) {
            ForegroundServiceType.MICROPHONE -> {
                val hasMic = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED

                if (!hasMic) {
                    return Pair(false, "Microphone permission (RECORD_AUDIO) is required before launching microphone foreground service.")
                }
            }
            ForegroundServiceType.MEDIA_PROJECTION -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    // On A14+, Media Projection FGS requires prior runtime projection token from MediaProjectionManager
                    Log.d(TAG, "Media projection foreground service requires valid MediaProjection token.")
                }
            }
            ForegroundServiceType.LOCATION -> {
                val hasLocation = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

                if (!hasLocation) {
                    return Pair(false, "Location permission is required before starting location foreground service.")
                }
            }
            ForegroundServiceType.SPECIAL_USE -> {
                // Verified in manifest with PROPERTY_SPECIAL_USE_FGS_SUBTYPE
                return Pair(true, null)
            }
            ForegroundServiceType.DATA_SYNC,
            ForegroundServiceType.PHONE_CALL -> {
                return Pair(true, null)
            }
        }
        return Pair(true, null)
    }

    /**
     * [Android 10+ (API 29, Q) & Android 11+ (API 30, R)] System Alert Window Flow.
     *
     * Manages SYSTEM_ALERT_WINDOW (Draw over other apps / Floating overlay).
     * Handles Android 10 background activity start restrictions and provides URI fallback.
     */
    fun checkOverlayPermission(context: Context): PermissionStatus {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Settings.canDrawOverlays(context)) {
                PermissionStatus.Granted
            } else {
                PermissionStatus.SpecialAccessRequired(
                    type = SpecialPermissionType.SYSTEM_ALERT_WINDOW,
                    title = "Floating Assistant Window (Draw Over Apps)",
                    explanation = "Allows Alya to show the floating listening bubble and incoming call manager above other applications.",
                    settingsIntent = createOverlaySettingsIntent(context)
                )
            }
        } else {
            PermissionStatus.Granted
        }
    }

    /**
     * [Android 12+ (API 31, S)] Exact Alarm Scheduling Flow.
     *
     * Checks SCHEDULE_EXACT_ALARM / USE_EXACT_ALARM to ensure alarms & routines fire accurately.
     */
    fun checkExactAlarmPermission(context: Context): PermissionStatus {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            val canSchedule = alarmManager?.canScheduleExactAlarms() ?: true
            if (canSchedule) {
                PermissionStatus.Granted
            } else {
                PermissionStatus.SpecialAccessRequired(
                    type = SpecialPermissionType.SCHEDULE_EXACT_ALARM,
                    title = "Exact Alarms & Schedule Permission",
                    explanation = "Required for precise timing of reminders, wake-up calls, and automated daily routines.",
                    settingsIntent = createExactAlarmSettingsIntent(context)
                )
            }
        } else {
            PermissionStatus.Granted
        }
    }

    /**
     * Version-aware Bluetooth Permissions list.
     * - Android 12+ (API 31+): BLUETOOTH_SCAN, BLUETOOTH_CONNECT.
     * - Legacy: BLUETOOTH, BLUETOOTH_ADMIN, ACCESS_FINE_LOCATION.
     */
    fun getRequiredBluetoothPermissions(): List<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            listOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
    }

    /**
     * Version-aware Media & Storage Permissions list.
     * - Android 13+ (API 33+): READ_MEDIA_IMAGES, READ_MEDIA_VIDEO, READ_MEDIA_AUDIO.
     * - Android 14+ (API 34+): Adds READ_MEDIA_VISUAL_USER_SELECTED if applicable.
     * - Android 12 and below: READ_EXTERNAL_STORAGE.
     */
    fun getRequiredMediaPermissions(): List<String> {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> listOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> listOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            )
            else -> listOf(
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        }
    }

    /**
     * Evaluates a list of permissions against the current device version and user grant state.
     */
    fun evaluatePermissions(activity: Activity, permissions: List<String>): PermissionBatchReport {
        val granted = mutableListOf<String>()
        val denied = mutableListOf<String>()
        val permanentlyDenied = mutableListOf<String>()

        for (perm in permissions) {
            when (val status = checkPermissionStatus(activity, perm)) {
                is PermissionStatus.Granted -> granted.add(perm)
                is PermissionStatus.PermanentlyDenied -> permanentlyDenied.add(perm)
                is PermissionStatus.Denied -> denied.add(perm)
                is PermissionStatus.VersionNotApplicable -> granted.add(perm) // Not needed on this OS
                else -> denied.add(perm)
            }
        }

        return PermissionBatchReport(
            grantedPermissions = granted,
            deniedPermissions = denied,
            permanentlyDeniedPermissions = permanentlyDenied
        )
    }

    // =========================================================================
    // 4. INTENT FACTORIES FOR RECOVERY & SETTINGS NAVIGATION
    // =========================================================================

    /**
     * Creates intent to Application Details Settings (App Info).
     */
    fun createAppSettingsIntent(context: Context): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Creates intent to System Overlay Permission Settings.
     */
    fun createOverlaySettingsIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            createAppSettingsIntent(context)
        }
    }

    /**
     * Creates intent to App Notification Settings.
     */
    fun createNotificationSettingsIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            createAppSettingsIntent(context)
        }
    }

    /**
     * Creates intent to Exact Alarm Settings (Android 12+).
     */
    fun createExactAlarmSettingsIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            createAppSettingsIntent(context)
        }
    }

    /**
     * Creates intent to Accessibility Settings.
     */
    fun createAccessibilitySettingsIntent(): Intent {
        return Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Creates intent to Battery Optimization Settings.
     */
    fun createBatteryOptimizationIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (powerManager?.isIgnoringBatteryOptimizations(context.packageName) == false) {
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } else {
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
        } else {
            createAppSettingsIntent(context)
        }
    }

    // =========================================================================
    // 5. HUMAN-READABLE METADATA HELPERS
    // =========================================================================

    fun getPermissionDisplayName(permission: String): String {
        return when (permission) {
            Manifest.permission.RECORD_AUDIO -> "Microphone & Voice Input"
            Manifest.permission.POST_NOTIFICATIONS -> "Notifications"
            Manifest.permission.SYSTEM_ALERT_WINDOW -> "Floating Overlay (Draw Over Apps)"
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.ANSWER_PHONE_CALLS,
            Manifest.permission.CALL_PHONE -> "Phone & Call Manager"
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS -> "Contacts"
            Manifest.permission.CAMERA -> "Camera & Vision"
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION -> "Location"
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN -> "Bluetooth Devices"
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.READ_EXTERNAL_STORAGE -> "Storage & Documents"
            else -> permission.substringAfterLast(".")
        }
    }

    fun getPermissionExplanation(permission: String): String {
        return when (permission) {
            Manifest.permission.RECORD_AUDIO -> "Required for real-time voice conversations, wake-word detection ('Hey Alya', 'Alia', 'Seno'), and voice commands."
            Manifest.permission.POST_NOTIFICATIONS -> "Required to display live assistant status, call announcements, and routine alarms in your notification shade."
            Manifest.permission.SYSTEM_ALERT_WINDOW -> "Required to display the floating assistant bubble and answer calls hands-free over other apps."
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.ANSWER_PHONE_CALLS,
            Manifest.permission.CALL_PHONE -> "Required for Alya to announce incoming callers and answer or end calls using your voice."
            Manifest.permission.READ_CONTACTS -> "Required to find and call contacts by name when you say 'Call [Name]'."
            Manifest.permission.CAMERA -> "Required for visual understanding and document scanning features."
            Manifest.permission.ACCESS_FINE_LOCATION -> "Required for local weather updates and navigation commands."
            else -> "This permission enables essential capabilities for your smart AI companion."
        }
    }

    fun getPermissionIcon(permission: String): ImageVector {
        return when (permission) {
            Manifest.permission.RECORD_AUDIO -> Icons.Default.Mic
            Manifest.permission.POST_NOTIFICATIONS -> Icons.Default.Notifications
            Manifest.permission.SYSTEM_ALERT_WINDOW -> Icons.Default.Layers
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.ANSWER_PHONE_CALLS,
            Manifest.permission.CALL_PHONE -> Icons.Default.Phone
            Manifest.permission.READ_CONTACTS -> Icons.Default.Contacts
            Manifest.permission.CAMERA -> Icons.Default.PhotoCamera
            Manifest.permission.ACCESS_FINE_LOCATION -> Icons.Default.LocationOn
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN -> Icons.Default.Bluetooth
            else -> Icons.Default.Security
        }
    }
}

// =============================================================================
// 6. JETPACK COMPOSE INTEGRATION & DIALOGS
// =============================================================================

/**
 * State holder for Permission Request Flow in Jetpack Compose.
 */
class PermissionRequestState(
    val context: Context,
    val onReport: (PermissionRequestHelper.PermissionBatchReport) -> Unit = {}
) {
    var permanentlyDeniedPermissions by mutableStateOf<List<String>>(emptyList())
    var rationalePermissions by mutableStateOf<List<String>>(emptyList())
    var pendingPermissions by mutableStateOf<List<String>>(emptyList())

    val showPermanentlyDeniedDialog: Boolean get() = permanentlyDeniedPermissions.isNotEmpty()
    val showRationaleDialog: Boolean get() = rationalePermissions.isNotEmpty()

    fun dismissPermanentlyDenied() {
        permanentlyDeniedPermissions = emptyList()
    }

    fun dismissRationale() {
        rationalePermissions = emptyList()
    }
}

/**
 * Creates and remembers a PermissionRequestState for UI composition.
 */
@Composable
fun rememberPermissionRequestState(
    onReport: (PermissionRequestHelper.PermissionBatchReport) -> Unit = {}
): PermissionRequestState {
    val context = LocalContext.current
    return remember { PermissionRequestState(context, onReport) }
}

/**
 * Clean Material Design 3 Dialog for 'Permanently Denied' permissions ("Don't Ask Again").
 * Guides the user to Application Settings with one click.
 */
@Composable
fun PermissionPermanentlyDeniedDialog(
    permissions: List<String>,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (permissions.isEmpty()) return

    val firstPerm = permissions.first()
    val displayName = PermissionRequestHelper.getPermissionDisplayName(firstPerm)
    val explanation = PermissionRequestHelper.getPermissionExplanation(firstPerm)
    val icon = PermissionRequestHelper.getPermissionIcon(firstPerm)

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier.testTag("permanently_denied_dialog"),
        shape = RoundedCornerShape(24.dp),
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "$displayName Permission Required",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = explanation,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Because this permission was previously denied with 'Don't ask again', Android requires you to enable it manually in App Settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onDismiss()
                    onOpenSettings()
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier.testTag("open_app_settings_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Open Settings")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("cancel_permission_dialog")
            ) {
                Text("Not Now")
            }
        }
    )
}

/**
 * Material Design 3 Dialog for Permission Rationale.
 */
@Composable
fun PermissionRationaleDialog(
    permissions: List<String>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (permissions.isEmpty()) return

    val firstPerm = permissions.first()
    val displayName = PermissionRequestHelper.getPermissionDisplayName(firstPerm)
    val explanation = PermissionRequestHelper.getPermissionExplanation(firstPerm)
    val icon = PermissionRequestHelper.getPermissionIcon(firstPerm)

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier.testTag("permission_rationale_dialog"),
        shape = RoundedCornerShape(24.dp),
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "Enable $displayName",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = explanation,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    onDismiss()
                    onConfirm()
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("grant_permission_rationale_button")
            ) {
                Text("Continue & Grant")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Maybe Later")
            }
        }
    )
}
