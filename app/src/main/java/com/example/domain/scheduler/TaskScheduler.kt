package com.example.domain.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.data.local.entity.ScheduledTaskEntity

class TaskScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    fun scheduleTask(task: ScheduledTaskEntity): Boolean {
        if (alarmManager == null || task.scheduledTimeMillis <= System.currentTimeMillis()) {
            return false
        }

        val intent = Intent(context, TaskAlarmReceiver::class.java).apply {
            putExtra(TaskAlarmReceiver.EXTRA_TASK_ID, task.id)
            putExtra(TaskAlarmReceiver.EXTRA_TASK_TITLE, task.title)
            putExtra(TaskAlarmReceiver.EXTRA_TASK_DESC, task.description)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            task.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return try {
            val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                alarmManager.canScheduleExactAlarms()
            } else {
                true
            }

            if (canExact) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        task.scheduledTimeMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        task.scheduledTimeMillis,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    task.scheduledTimeMillis,
                    pendingIntent
                )
            }
            Log.i(TAG, "Scheduled task ${task.id} (${task.title}) at ${task.scheduledTimeMillis}")
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException: exact alarm permission missing: ${e.message}, falling back")
            try {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    task.scheduledTimeMillis,
                    pendingIntent
                )
                true
            } catch (ex: Exception) {
                Log.e(TAG, "Failed to schedule alarm: ${ex.message}")
                false
            }
        } catch (e: Exception) {
            Log.w(TAG, "Exact alarm failed: ${e.message}, falling back to standard alarm")
            try {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    task.scheduledTimeMillis,
                    pendingIntent
                )
                true
            } catch (ex: Exception) {
                Log.e(TAG, "Failed to schedule alarm: ${ex.message}")
                false
            }
        }
    }

    fun cancelTask(taskId: Long) {
        if (alarmManager == null) return

        val intent = Intent(context, TaskAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            taskId.toInt(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )

        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.i(TAG, "Cancelled alarm for task $taskId")
        }
    }

    companion object {
        private const val TAG = "TaskScheduler"
    }
}
