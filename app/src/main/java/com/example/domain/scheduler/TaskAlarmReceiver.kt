package com.example.domain.scheduler

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.AlyaApplication
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TaskAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
        val taskTitle = intent.getStringExtra(EXTRA_TASK_TITLE) ?: "Task Reminder"
        val taskDescription = intent.getStringExtra(EXTRA_TASK_DESC) ?: ""

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel(notificationManager)

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_NAVIGATE_TO", "TASKS")
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            taskId.toInt(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(taskTitle)
            .setContentText(if (taskDescription.isNotBlank()) taskDescription else "Scheduled reminder from Alya Assistant")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        notificationManager.notify(taskId.toInt(), notification)

        // Update task state in Room DB or reschedule recurring
        if (taskId > 0L) {
            val app = context.applicationContext as? AlyaApplication ?: return
            val scope = CoroutineScope(Dispatchers.IO)
            scope.launch {
                try {
                    val taskDao = app.database.scheduledTaskDao()
                    val task = taskDao.getTaskById(taskId)
                    if (task != null) {
                        when (task.repeatInterval) {
                            "DAILY" -> {
                                val nextTime = task.scheduledTimeMillis + (24 * 3600 * 1000L)
                                val updated = task.copy(scheduledTimeMillis = nextTime)
                                taskDao.updateTask(updated)
                                TaskScheduler(context).scheduleTask(updated)
                            }
                            "WEEKLY" -> {
                                val nextTime = task.scheduledTimeMillis + (7 * 24 * 3600 * 1000L)
                                val updated = task.copy(scheduledTimeMillis = nextTime)
                                taskDao.updateTask(updated)
                                TaskScheduler(context).scheduleTask(updated)
                            }
                            else -> {
                                taskDao.updateTask(task.copy(isCompleted = true))
                            }
                        }
                    }
                } catch (e: Exception) { android.util.Log.e("Alya", "Exception handled", e) }
            }
        }
    }

    private fun createNotificationChannel(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Alya Reminders & Schedules",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifies you of scheduled assistant reminders, tasks, and calendar alerts."
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "alya_scheduled_tasks_channel"
        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_TASK_TITLE = "extra_task_title"
        const val EXTRA_TASK_DESC = "extra_task_desc"
    }
}
