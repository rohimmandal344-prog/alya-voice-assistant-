package com.example.util

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat

/**
 * CompatUtils
 * 
 * Centralized runtime version-detection and compatibility wrapper for Android 10 (API 29) 
 * through Android 17+ (API 37+).
 * 
 * Handles permission flows, service behaviors, and system restrictions per OS version.
 */
object CompatUtils {

    // --- OS Version Checks ---
    val isAndroid10OrAbove: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    val isAndroid11OrAbove: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    val isAndroid12OrAbove: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val isAndroid13OrAbove: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val isAndroid14OrAbove: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
    val isAndroid15OrAbove: Boolean get() = Build.VERSION.SDK_INT >= 35 // Vanilla Ice Cream
    val isAndroid16OrAbove: Boolean get() = Build.VERSION.SDK_INT >= 36 
    val isAndroid17OrAbove: Boolean get() = Build.VERSION.SDK_INT >= 37

    // --- Permission & Security Flows ---

    /**
     * Checks for microphone permission, considering background access restrictions.
     */
    fun hasMicrophonePermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Checks for notification permission (Android 13+).
     */
    fun hasNotificationPermission(context: Context): Boolean {
        return if (isAndroid13OrAbove) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Checks if the app is exempt from battery optimizations.
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
        } else {
            true
        }
    }

    /**
     * Checks if the app can schedule exact alarms (Android 12+).
     */
    fun canScheduleExactAlarms(context: Context): Boolean {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? android.app.AlarmManager
        return if (isAndroid12OrAbove) {
            alarmManager?.canScheduleExactAlarms() == true
        } else {
            true
        }
    }

    // --- Service Behavior ---

    /**
     * Returns the appropriate foreground service type for microphone usage (Android 14+).
     */
    fun getMicrophoneFgsType(): Int {
        return if (isAndroid14OrAbove) {
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        } else {
            0
        }
    }

    /**
     * Returns the appropriate foreground service type for data sync (Android 14+).
     */
    fun getDataSyncFgsType(): Int {
        return if (isAndroid14OrAbove) {
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
    }

    /**
     * Returns the appropriate foreground service type for media playback (Android 14+).
     */
    fun getMediaPlaybackFgsType(): Int {
        return if (isAndroid14OrAbove) {
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        } else {
            0
        }
    }

    // --- Audio System ---

    /**
     * Returns the appropriate audio stream for assistant voice.
     */
    fun getAssistantAudioStream(): Int {
        return android.media.AudioManager.STREAM_MUSIC
    }

    // --- Notification Model ---

    /**
     * Creates a notification channel with appropriate importance.
     */
    fun createNotificationChannel(context: Context, channelId: String, name: String, description: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = android.app.NotificationChannel(channelId, name, importance).apply {
                this.description = description
                setShowBadge(false)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    // --- Overlay & Accessibility & System Settings ---

    /**
     * Checks if System Alert Window (Overlay) permission is granted.
     */
    fun canDrawOverlays(context: Context): Boolean {
        return android.provider.Settings.canDrawOverlays(context)
    }

    /**
     * Checks if Bluetooth Connect permission is granted (Android 12+).
     */
    fun hasBluetoothConnectPermission(context: Context): Boolean {
        return if (isAndroid12OrAbove) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Checks if Location permission is granted (Coarse or Fine).
     */
    fun hasLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    /**
     * Configures Activity for lock-screen display and turn-screen-on across Android 10-17+.
     */
    fun configureShowOnLockScreen(activity: android.app.Activity, showOnLock: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            activity.setShowWhenLocked(showOnLock)
            activity.setTurnScreenOn(showOnLock)
        } else {
            @Suppress("DEPRECATION")
            val flags = android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            if (showOnLock) {
                activity.window.addFlags(flags)
            } else {
                activity.window.clearFlags(flags)
            }
        }
    }

    // --- Background Execution ---

    /**
     * Wraps intent with flags to avoid background activity start restrictions (Android 10-17+).
     */
    fun wrapBackgroundActivityIntent(intent: android.content.Intent): android.content.Intent {
        return intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
