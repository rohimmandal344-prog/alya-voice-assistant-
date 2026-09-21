package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import com.example.AlyaApplication

/**
 * VoiceServiceBootReceiver
 * 
 * Robust system-wide BroadcastReceiver leveraging JobScheduler and Partial WakeLocks
 * to handle device boot, package update, power events, and wake-up signals,
 * ensuring the AI wake-word engine ("Alia", "Alya", "Seno") re-initializes properly
 * even after the app process has been killed.
 */
class VoiceServiceBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        Log.i(TAG, "VoiceServiceBootReceiver received broadcast action: $action")

        val validActions = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_USER_UNLOCKED,
            Intent.ACTION_REBOOT,
            Intent.ACTION_POWER_CONNECTED,
            Intent.ACTION_POWER_DISCONNECTED,
            Intent.ACTION_BATTERY_OKAY,
            Intent.ACTION_USER_PRESENT,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON"
        )

        if (action in validActions) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val wakeLock = powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "AlyaAssistant:SystemWakeUpReceiverWakeLock"
            )?.apply {
                setReferenceCounted(false)
                acquire(15000L) // 15 seconds lease while initializing service
            }

            try {
                // 1. Dispatch through AlarmManager exact scheduler and JobScheduler
                VoiceKeepAliveAlarmScheduler.scheduleExactKeepAlive(context)
                VoiceKeepAliveJobService.scheduleExpeditedWakeUp(context)
                VoiceKeepAliveJobService.scheduleKeepAliveJob(context)

                // 2. Direct Foreground Service start attempt with user preferences check
                val app = context.applicationContext as? AlyaApplication
                val isWakeWordEnabled = app?.preferencesManager?.wakeWordEnabled?.value ?: false
                val isBatterySaver = app?.preferencesManager?.wakeWordBatterySaver?.value ?: true

                if (isWakeWordEnabled) {
                    Log.i(TAG, "Starting WakeWordService directly from system broadcast ($action)...")
                    WakeWordService.start(context, batterySaver = isBatterySaver)
                } else {
                    Log.i(TAG, "Wake-word is disabled in user preferences; skipping service launch.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed during broadcast receiver service launch: ${e.message}", e)
                // JobScheduler scheduled above acts as fail-safe
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
        private const val TAG = "VoiceServiceBootReceiver"
    }
}
