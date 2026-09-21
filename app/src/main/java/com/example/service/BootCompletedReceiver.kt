package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.AlyaApplication

/**
 * BootCompletedReceiver
 *
 * Dedicated BroadcastReceiver for handling 'android.intent.action.BOOT_COMPLETED' and related
 * system startup broadcasts (LOCKED_BOOT_COMPLETED, QUICKBOOT_POWERON).
 * 
 * Ensures the Alya Foreground Service and wake-word listener ("Alia", "Alya", "Seno")
 * initialize automatically and reliably upon device reboot for persistent background availability.
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        Log.i(TAG, "BootCompletedReceiver triggered with action: $action")

        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON" ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == Intent.ACTION_USER_UNLOCKED
        ) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val wakeLock = powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "AlyaAssistant:BootCompletedWakeLock"
            )?.apply {
                setReferenceCounted(false)
                acquire(20000L) // 20-second lease to allow full Foreground Service & mic initialization
            }

            try {
                // 1. Schedule AlarmManager exact keep-alive and JobScheduler backup
                VoiceKeepAliveAlarmScheduler.scheduleExactKeepAlive(context)
                VoiceKeepAliveJobService.scheduleKeepAliveJob(context)
                VoiceKeepAliveJobService.scheduleExpeditedWakeUp(context)

                // 2. Read user preferences
                val app = context.applicationContext as? AlyaApplication
                val isWakeWordEnabled = app?.preferencesManager?.wakeWordEnabled?.value ?: false
                val isBatterySaver = app?.preferencesManager?.wakeWordBatterySaver?.value ?: true

                if (isWakeWordEnabled) {
                    Log.i(TAG, "Device boot completed: Starting WakeWordService listener...")
                    val serviceIntent = Intent(context, WakeWordService::class.java).apply {
                        this.action = WakeWordService.ACTION_START
                        putExtra("battery_saver", isBatterySaver)
                    }

                    val hasMicPermission = ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.RECORD_AUDIO
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && hasMicPermission) {
                        ContextCompat.startForegroundService(context, serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                } else {
                    Log.i(TAG, "Wake-word is disabled in user preferences; skipping auto-start on boot.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception starting WakeWordService from BootCompletedReceiver: ${e.message}", e)
                // Guaranteed JobScheduler resurrection fallback
                VoiceKeepAliveJobService.scheduleExpeditedWakeUp(context)
            } finally {
                try {
                    if (wakeLock?.isHeld == true) {
                        wakeLock.release()
                    }
                } catch (e: Exception) { android.util.Log.e("Alya", "Exception handled", e) }
            }
        }
    }

    companion object {
        private const val TAG = "BootCompletedReceiver"
    }
}
