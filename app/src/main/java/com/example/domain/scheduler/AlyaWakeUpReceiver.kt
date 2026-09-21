package com.example.domain.scheduler

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R

/**
 * Handles exact wake-up alarm triggers set by Alya's voice commands.
 * Acquires a high-priority CPU wake-lock and launches the full-screen Alya Wake-Up Greeting screen.
 */
class AlyaWakeUpReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val alarmTitle = intent.getStringExtra(EXTRA_WAKE_UP_TITLE) ?: "Good Morning! Time to Wake Up"
        val alarmTimeStr = intent.getStringExtra(EXTRA_WAKE_UP_TIME_STR) ?: ""

        Log.i(TAG, "Alya Wake-Up Alarm Triggered: $alarmTitle ($alarmTimeStr)")

        // Acquire temporary wake lock to ensure device screen wakes up from sleep
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or
                    PowerManager.ACQUIRE_CAUSES_WAKEUP or
                    PowerManager.ON_AFTER_RELEASE,
            "AlyaAssistant:WakeUpAlarmLock"
        )
        try {
            wakeLock?.acquire(15000L) // 15 seconds wake lock
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire wake lock: ${e.message}")
        }

        // Show High Priority Wake-Up Notification
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createWakeUpNotificationChannel(notificationManager)

        val fullScreenIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_WAKE_UP_TRIGGER, true)
            putExtra(EXTRA_WAKE_UP_TITLE, alarmTitle)
            putExtra(EXTRA_WAKE_UP_TIME_STR, alarmTimeStr)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            1088,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, WAKE_UP_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("☀️ $alarmTitle")
            .setContentText("Alya is waiting to wake you up!")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(pendingIntent, true)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOngoing(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)

        // Launch MainActivity directly
        try {
            context.startActivity(fullScreenIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch wake-up activity directly: ${e.message}")
        }
    }

    private fun createWakeUpNotificationChannel(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                WAKE_UP_CHANNEL_ID,
                "Alya Voice Wake-Up Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High-priority wake up alerts with voice greeting from Alya"
                enableVibration(true)
                setBypassDnd(true)
            }
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val TAG = "AlyaWakeUpReceiver"
        const val WAKE_UP_CHANNEL_ID = "alya_wakeup_channel"
        const val NOTIFICATION_ID = 8801

        const val EXTRA_WAKE_UP_TRIGGER = "extra_wake_up_trigger"
        const val EXTRA_WAKE_UP_TITLE = "extra_wake_up_title"
        const val EXTRA_WAKE_UP_TIME_STR = "extra_wake_up_time_str"

        fun scheduleWakeUp(context: Context, timeMillis: Long, title: String, timeStr: String): Boolean {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
            if (timeMillis <= System.currentTimeMillis()) return false

            val intent = Intent(context, AlyaWakeUpReceiver::class.java).apply {
                putExtra(EXTRA_WAKE_UP_TITLE, title)
                putExtra(EXTRA_WAKE_UP_TIME_STR, timeStr)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                1088,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val showIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_WAKE_UP_TRIGGER, true)
                putExtra(EXTRA_WAKE_UP_TITLE, title)
                putExtra(EXTRA_WAKE_UP_TIME_STR, timeStr)
            }
            val showPendingIntent = PendingIntent.getActivity(
                context,
                1089,
                showIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            return try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    val alarmClockInfo = AlarmManager.AlarmClockInfo(timeMillis, showPendingIntent)
                    alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
                    Log.i(TAG, "Scheduled AlarmClockInfo Alya wake-up call for $timeStr ($timeMillis)")
                    true
                } else {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, timeMillis, pendingIntent)
                    true
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed setAlarmClock, falling back to setExactAndAllowWhileIdle: ${e.message}")
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMillis, pendingIntent)
                    } else {
                        alarmManager.set(AlarmManager.RTC_WAKEUP, timeMillis, pendingIntent)
                    }
                    true
                } catch (fallbackEx: Exception) {
                    Log.e(TAG, "Failed to schedule wake-up alarm: ${fallbackEx.message}")
                    false
                }
            }
        }

        fun cancelWakeUp(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, AlyaWakeUpReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                1088,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }
    }
}
