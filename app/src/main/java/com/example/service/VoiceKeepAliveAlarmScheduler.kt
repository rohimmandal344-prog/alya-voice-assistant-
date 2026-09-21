package com.example.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * VoiceKeepAliveAlarmScheduler
 *
 * Implements an AlarmManager-based 'exact' scheduling strategy (RTC_WAKEUP + setExactAndAllowWhileIdle)
 * ensuring the Alya Foreground Service and wake-word detection engine remain active and responsive
 * even after the application process is terminated or reclaimed by the OS.
 */
object VoiceKeepAliveAlarmScheduler {

    private const val TAG = "VoiceAlarmScheduler"
    private const val REQUEST_CODE_EXACT_KEEP_ALIVE = 7701
    private const val REQUEST_CODE_RESURRECTION = 7702

    const val ACTION_ALARM_KEEP_ALIVE = "com.example.service.ACTION_ALARM_KEEP_ALIVE"
    const val ACTION_ALARM_RESURRECT = "com.example.service.ACTION_ALARM_RESURRECT"

    // 5-minute heartbeat interval for exact keep-alive health checks
    const val DEFAULT_HEARTBEAT_INTERVAL_MS = 5 * 60 * 1000L

    /**
     * Schedules an exact keep-alive heartbeat alarm that fires even in deep Doze mode.
     */
    fun scheduleExactKeepAlive(context: Context, delayMs: Long = DEFAULT_HEARTBEAT_INTERVAL_MS) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val triggerTimeMs = System.currentTimeMillis() + delayMs

        val intent = Intent(context, VoiceAlarmKeepAliveReceiver::class.java).apply {
            action = ACTION_ALARM_KEEP_ALIVE
            putExtra("scheduled_time", triggerTimeMs)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_EXACT_KEEP_ALIVE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTimeMs,
                        pendingIntent
                    )
                    Log.d(TAG, "Scheduled exact keep-alive alarm in ${delayMs / 1000}s (API 31+ exact enabled).")
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTimeMs,
                        pendingIntent
                    )
                    Log.d(TAG, "Scheduled inexact keep-alive alarm in ${delayMs / 1000}s (fallback while idle).")
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTimeMs,
                    pendingIntent
                )
                Log.d(TAG, "Scheduled exact keep-alive alarm in ${delayMs / 1000}s (API 23-30).")
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerTimeMs,
                    pendingIntent
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule exact keep-alive alarm: ${e.message}", e)
        }
    }

    /**
     * Schedules an immediate high-priority exact alarm to resurrect the service after process kill.
     */
    fun scheduleImmediateResurrection(context: Context, delayMs: Long = 1000L) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val triggerTimeMs = System.currentTimeMillis() + delayMs

        val intent = Intent(context, VoiceAlarmKeepAliveReceiver::class.java).apply {
            action = ACTION_ALARM_RESURRECT
            putExtra("scheduled_time", triggerTimeMs)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_RESURRECTION,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTimeMs,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTimeMs,
                        pendingIntent
                    )
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTimeMs,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerTimeMs,
                    pendingIntent
                )
            }
            Log.i(TAG, "Scheduled immediate resurrection exact alarm in ${delayMs}ms.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule immediate resurrection alarm: ${e.message}", e)
        }
    }

    /**
     * Cancels any pending keep-alive and resurrection alarms (e.g. when assistant is explicitly stopped by user).
     */
    fun cancelAllAlarms(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        try {
            val keepAliveIntent = Intent(context, VoiceAlarmKeepAliveReceiver::class.java).apply {
                action = ACTION_ALARM_KEEP_ALIVE
            }
            val keepAlivePI = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_EXACT_KEEP_ALIVE,
                keepAliveIntent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (keepAlivePI != null) {
                alarmManager.cancel(keepAlivePI)
                keepAlivePI.cancel()
            }

            val resurrectIntent = Intent(context, VoiceAlarmKeepAliveReceiver::class.java).apply {
                action = ACTION_ALARM_RESURRECT
            }
            val resurrectPI = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_RESURRECTION,
                resurrectIntent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (resurrectPI != null) {
                alarmManager.cancel(resurrectPI)
                resurrectPI.cancel()
            }
            Log.i(TAG, "All keep-alive exact alarms successfully cancelled.")
        } catch (e: Exception) {
            Log.w(TAG, "Error cancelling keep-alive alarms: ${e.message}")
        }
    }
}
