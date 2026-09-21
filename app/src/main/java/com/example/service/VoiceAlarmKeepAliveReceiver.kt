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
 * VoiceAlarmKeepAliveReceiver
 *
 * BroadcastReceiver triggered by AlarmManager exact alarms (RTC_WAKEUP + setExactAndAllowWhileIdle).
 * Ensures that if the app process or service is terminated by Android OS low-memory killer (LMK),
 * the Foreground Service and wake-word listener ("Alia", "Alya", "Seno") are immediately resurrected
 * and kept in a persistent active state.
 */
class VoiceAlarmKeepAliveReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        Log.i(TAG, "VoiceAlarmKeepAliveReceiver received exact alarm trigger: $action")

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "AlyaAssistant:AlarmKeepAliveWakeLock"
        )?.apply {
            setReferenceCounted(false)
            acquire(15000L) // 15-second CPU wake lease
        }

        try {
            val app = context.applicationContext as? AlyaApplication
            val isWakeWordEnabled = app?.preferencesManager?.wakeWordEnabled?.value ?: false
            val isBatterySaver = app?.preferencesManager?.wakeWordBatterySaver?.value ?: true

            if (isWakeWordEnabled) {
                // If service is not running or process was recreated, restart service immediately
                if (!WakeWordService.isRunning) {
                    Log.i(TAG, "WakeWordService not running during alarm check; starting service now...")
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
                    Log.d(TAG, "WakeWordService is active; ensuring audio session is running.")
                    val reloadIntent = Intent(context, WakeWordService::class.java).apply {
                        this.action = WakeWordService.ACTION_RELOAD_AUDIO_SESSION
                    }
                    context.startService(reloadIntent)
                }

                // Reschedule the next exact keep-alive heartbeat in the continuous chain
                VoiceKeepAliveAlarmScheduler.scheduleExactKeepAlive(context)
            } else {
                Log.i(TAG, "Wake-word is disabled in user preferences. Halting alarm keep-alive chain.")
                VoiceKeepAliveAlarmScheduler.cancelAllAlarms(context)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in VoiceAlarmKeepAliveReceiver: ${e.message}", e)
            // Reschedule next heartbeat as fail-safe
            VoiceKeepAliveAlarmScheduler.scheduleExactKeepAlive(context)
        } finally {
            try {
                if (wakeLock?.isHeld == true) {
                    wakeLock.release()
                }
            } catch (e: Exception) { android.util.Log.e("Alya", "Exception handled", e) }
        }
    }

    companion object {
        private const val TAG = "VoiceAlarmReceiver"
    }
}
