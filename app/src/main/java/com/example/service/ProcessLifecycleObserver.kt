package com.example.service

import android.app.Activity
import android.app.Application
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import com.example.AlyaApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * ProcessLifecycleObserver
 *
 * Observes application and process lifecycle transitions.
 * When the Android OS recovers or restarts the application process (e.g. after low-memory kills or system restart),
 * this observer immediately ensures the Foreground Service and TFLite wake-word engine ("Alia", "Alya", "Seno")
 * are re-triggered, maintaining 100% responsiveness without user intervention.
 */
class ProcessLifecycleObserver(
    private val application: AlyaApplication
) : Application.ActivityLifecycleCallbacks, ComponentCallbacks2 {

    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var runningActivityCount = 0
    private var isAppInForeground = false

    companion object {
        private const val TAG = "ProcessLifecycleObs"
    }

    /**
     * Initializes lifecycle observation and verifies background voice engine status.
     */
    fun initialize() {
        application.registerActivityLifecycleCallbacks(this)
        application.registerComponentCallbacks(this)
        Log.i(TAG, "ProcessLifecycleObserver registered. Triggering process recovery check...")
        verifyAndTriggerWakeWordEngine("Process Startup / System Recovery")
    }

    /**
     * Verifies user preferences and re-triggers the wake-word engine and Foreground Service.
     */
    fun verifyAndTriggerWakeWordEngine(triggerReason: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val isWakeWordEnabled = application.preferencesManager.wakeWordEnabled.value
                val isBatterySaver = application.preferencesManager.wakeWordBatterySaver.value

                Log.i(TAG, "Verifying wake-word engine on [$triggerReason]. Enabled: $isWakeWordEnabled")

                if (isWakeWordEnabled) {
                    // 1. Ensure Foreground Service is active
                    WakeWordService.start(application, batterySaver = isBatterySaver)

                    // 2. Schedule exact keep-alive alarms and background jobs
                    VoiceKeepAliveAlarmScheduler.scheduleExactKeepAlive(application)
                    VoiceKeepAliveJobService.scheduleKeepAliveJob(application)

                    // 3. Notify wake-word manager of process activity
                    application.wakeWordManager.notifyUserActivity()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in verifyAndTriggerWakeWordEngine ($triggerReason): ${e.message}", e)
            }
        }
    }

    override fun onActivityStarted(activity: Activity) {
        runningActivityCount++
        if (runningActivityCount == 1 && !isAppInForeground) {
            isAppInForeground = true
            Log.d(TAG, "App entered FOREGROUND.")
            application.sessionManager.unlockSession("App entered foreground")
            verifyAndTriggerWakeWordEngine("App Entered Foreground")
        }
    }

    override fun onActivityStopped(activity: Activity) {
        runningActivityCount = maxOf(0, runningActivityCount - 1)
        if (runningActivityCount == 0 && isAppInForeground) {
            isAppInForeground = false
            Log.d(TAG, "App entered BACKGROUND — locking session to prevent background recognition loops.")
            application.sessionManager.lockSession("App entered background")
            verifyAndTriggerWakeWordEngine("App Entered Background")
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}

    override fun onTrimMemory(level: Int) {
        Log.d(TAG, "ComponentCallbacks onTrimMemory level: $level")
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            // Release inactive ASR / temporary audio records to prevent hardware locks and battery drain
            com.example.audio.AudioLifecycleManager.releaseAudioRecord()
            verifyAndTriggerWakeWordEngine("TrimMemory Level $level")
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {}
    override fun onLowMemory() {
        Log.w(TAG, "System Low Memory event — scheduling expedited wake-up heartbeat")
        VoiceKeepAliveAlarmScheduler.scheduleExactKeepAlive(application, 60000L)
    }
}
