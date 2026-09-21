package com.example.service

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.util.Log
import com.example.AlyaApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * VoiceKeepAliveJobService
 *
 * System JobScheduler service ensuring the Alya AI wake-word engine and Foreground Service
 * are reliably re-initialized even after the app process is terminated or killed by OS memory pressure.
 */
class VoiceKeepAliveJobService : JobService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onStartJob(params: JobParameters?): Boolean {
        Log.i(TAG, "VoiceKeepAliveJobService triggered with jobId: ${params?.jobId}")

        serviceScope.launch {
            try {
                val app = applicationContext as? AlyaApplication
                val isWakeWordEnabled = app?.preferencesManager?.wakeWordEnabled?.value ?: false
                val isBatterySaver = app?.preferencesManager?.wakeWordBatterySaver?.value ?: true

                if (isWakeWordEnabled) {
                    Log.i(TAG, "Re-initializing WakeWordService & AI audio pipeline from JobScheduler...")
                    WakeWordService.start(applicationContext, batterySaver = isBatterySaver)
                } else {
                    Log.i(TAG, "Wake-word is disabled in user preferences. Keep-alive job complete.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in VoiceKeepAliveJobService execution: ${e.message}", e)
            } finally {
                jobFinished(params, false)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        Log.i(TAG, "VoiceKeepAliveJobService onStopJob called. Requesting reschedule.")
        serviceScope.cancel()
        return true
    }

    companion object {
        private const val TAG = "VoiceKeepAliveJob"
        const val PERIODIC_JOB_ID = 2001
        const val IMMEDIATE_JOB_ID = 2002

        /**
         * Schedules a persistent periodic keep-alive job that survives device reboots.
         */
        fun scheduleKeepAliveJob(context: Context) {
            try {
                val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
                val componentName = ComponentName(context, VoiceKeepAliveJobService::class.java)

                // 15 minutes standard interval for JobScheduler periodic tasks
                val periodicIntervalMs = 15 * 60 * 1000L
                val jobBuilder = JobInfo.Builder(PERIODIC_JOB_ID, componentName)
                    .setPeriodic(periodicIntervalMs)
                    .setPersisted(true) // Survives device reboots
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE)

                val result = jobScheduler.schedule(jobBuilder.build())
                if (result == JobScheduler.RESULT_SUCCESS) {
                    Log.i(TAG, "Persistent VoiceKeepAlive periodic Job scheduled successfully.")
                } else {
                    Log.w(TAG, "Failed to schedule persistent VoiceKeepAlive periodic Job.")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error scheduling periodic Job: ${e.message}")
            }
        }

        /**
         * Dispatches an immediate/expedited wake-up job to re-initialize the AI engine after system events.
         */
        fun scheduleExpeditedWakeUp(context: Context) {
            try {
                val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
                val componentName = ComponentName(context, VoiceKeepAliveJobService::class.java)

                val jobBuilder = JobInfo.Builder(IMMEDIATE_JOB_ID, componentName)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    jobBuilder.setExpedited(true)
                } else {
                    jobBuilder.setOverrideDeadline(0L) // Execute immediately
                }

                val result = jobScheduler.schedule(jobBuilder.build())
                if (result == JobScheduler.RESULT_SUCCESS) {
                    Log.i(TAG, "Expedited system wake-up Job scheduled successfully.")
                } else {
                    Log.w(TAG, "Failed to schedule expedited wake-up Job.")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error scheduling expedited wake-up Job: ${e.message}")
                // Fallback to direct foreground service start if JobScheduler throws
                try {
                    WakeWordService.start(context)
                } catch (e: Exception) { android.util.Log.e("Alya", "Exception handled", e) }
            }
        }

        /**
         * Cancels all scheduled keep-alive jobs when the user explicitly stops the assistant.
         */
        fun cancelAllJobs(context: Context) {
            try {
                val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler
                jobScheduler?.cancel(PERIODIC_JOB_ID)
                jobScheduler?.cancel(IMMEDIATE_JOB_ID)
                Log.i(TAG, "All VoiceKeepAlive jobs cancelled.")
            } catch (e: Exception) {
                Log.w(TAG, "Error cancelling jobs: ${e.message}")
            }
        }
    }
}
