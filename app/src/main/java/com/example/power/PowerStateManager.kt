package com.example.power

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import com.example.voice.AudioDeviceManager
import com.example.voice.SpeechRecognitionManager
import com.example.voice.wakeword.WakeWordManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Device Power and Activity States
 */
enum class DevicePowerState {
    /** Screen is on, recent user interaction, hardware ready and responsive */
    ACTIVE,
    /** Screen is on, but no interaction for >45s: audio focus & mic released, wake-word duty-cycled */
    INACTIVE_IDLE,
    /** Screen is off or device entered Android Doze mode: full non-active hardware release, wake-word duty-cycled */
    SCREEN_OFF_DOZE,
    /** System Battery Saver is enabled: maximum energy conservation profile */
    BATTERY_SAVER
}

/**
 * PowerStateManager
 *
 * Monitors device idle states (Screen On/Off, Android Doze mode, System Battery Saver,
 * and user inactivity). Explicitly releases audio focus and camera/microphone hardware
 * resources during non-active periods, preventing battery drain while keeping the
 * wake-word listener efficient and ready for voice commands.
 */
class PowerStateManager(
    private val context: Context,
    private val audioDeviceManager: AudioDeviceManager,
    private val speechManager: SpeechRecognitionManager,
    private val wakeWordManager: WakeWordManager
) {
    companion object {
        private const val TAG = "PowerStateManager"
        /** Inactivity threshold before dropping from ACTIVE to INACTIVE_IDLE (45 seconds) */
        const val DEFAULT_INACTIVITY_TIMEOUT_MS = 45_000L
    }

    private val powerScope = CoroutineScope(Dispatchers.Default)
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager

    // Observability StateFlows
    private val _powerState = MutableStateFlow(DevicePowerState.ACTIVE)
    val powerState: StateFlow<DevicePowerState> = _powerState.asStateFlow()

    private val _isHardwareReleased = MutableStateFlow(false)
    val isHardwareReleased: StateFlow<Boolean> = _isHardwareReleased.asStateFlow()

    private val _isScreenInteractive = MutableStateFlow(true)
    val isScreenInteractive: StateFlow<Boolean> = _isScreenInteractive.asStateFlow()

    private val _isDeviceIdleMode = MutableStateFlow(false)
    val isDeviceIdleMode: StateFlow<Boolean> = _isDeviceIdleMode.asStateFlow()

    private val _isSystemBatterySaver = MutableStateFlow(false)
    val isSystemBatterySaver: StateFlow<Boolean> = _isSystemBatterySaver.asStateFlow()

    private val _hardwareReleaseCount = MutableStateFlow(0)
    val hardwareReleaseCount: StateFlow<Int> = _hardwareReleaseCount.asStateFlow()

    private val _lastUserActivityTime = MutableStateFlow(System.currentTimeMillis())
    val lastUserActivityTime: StateFlow<Long> = _lastUserActivityTime.asStateFlow()

    private var inactivityWatchdogJob: Job? = null
    private var isReceiverRegistered = AtomicBoolean(false)

    private val powerBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            Log.d(TAG, "Power broadcast received: $action")

            when (action) {
                Intent.ACTION_SCREEN_OFF -> {
                    _isScreenInteractive.value = false
                    handleScreenOff()
                }
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                    _isScreenInteractive.value = true
                    handleScreenOn()
                }
                PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED -> {
                    checkDeviceIdleMode()
                }
                PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> {
                    checkPowerSaveMode()
                }
                Intent.ACTION_BATTERY_LOW -> {
                    Log.w(TAG, "Device battery low: transitioning to optimized power state")
                    _isSystemBatterySaver.value = true
                    wakeWordManager.setBatterySaverEnabled(true)
                }
                Intent.ACTION_BATTERY_OKAY -> {
                    Log.i(TAG, "Device battery okay")
                    checkPowerSaveMode()
                }
            }
        }
    }

    init {
        initializePowerTelemetry()
        startMonitoring()
    }

    /**
     * Reads initial hardware and system power flags.
     */
    private fun initializePowerTelemetry() {
        try {
            val isInteractive = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
                powerManager?.isInteractive ?: true
            } else {
                @Suppress("DEPRECATION")
                powerManager?.isScreenOn ?: true
            }
            _isScreenInteractive.value = isInteractive

            val isDeviceIdle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                powerManager?.isDeviceIdleMode ?: false
            } else {
                false
            }
            _isDeviceIdleMode.value = isDeviceIdle

            val isPowerSave = powerManager?.isPowerSaveMode ?: false
            _isSystemBatterySaver.value = isPowerSave

            if (!isInteractive || isDeviceIdle) {
                _powerState.value = DevicePowerState.SCREEN_OFF_DOZE
                releaseNonActiveHardware("Initial screen off or device idle state")
            } else if (isPowerSave) {
                _powerState.value = DevicePowerState.BATTERY_SAVER
                wakeWordManager.setBatterySaverEnabled(true)
            } else {
                _powerState.value = DevicePowerState.ACTIVE
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing power telemetry: ${e.message}", e)
        }
    }

    /**
     * Starts listening for system power broadcasts and launches the inactivity watchdog.
     */
    fun startMonitoring() {
        if (isReceiverRegistered.compareAndSet(false, true)) {
            try {
                val filter = IntentFilter().apply {
                    addAction(Intent.ACTION_SCREEN_OFF)
                    addAction(Intent.ACTION_SCREEN_ON)
                    addAction(Intent.ACTION_USER_PRESENT)
                    addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)
                    }
                    addAction(Intent.ACTION_BATTERY_LOW)
                    addAction(Intent.ACTION_BATTERY_OKAY)
                }
                androidx.core.content.ContextCompat.registerReceiver(
                    context,
                    powerBroadcastReceiver,
                    filter,
                    androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
                )
                Log.i(TAG, "Power state broadcast receiver registered.")
            } catch (e: Exception) {
                Log.e(TAG, "Error registering power broadcast receiver: ${e.message}", e)
            }
        }

        startInactivityWatchdog()
    }

    /**
     * Coroutine watchdog that monitors user inactivity during screen-on periods.
     * Drops to INACTIVE_IDLE if user has not interacted for > DEFAULT_INACTIVITY_TIMEOUT_MS.
     */
    private fun startInactivityWatchdog() {
        inactivityWatchdogJob?.cancel()
        inactivityWatchdogJob = powerScope.launch {
            while (isActive) {
                delay(10_000L) // Check every 10 seconds
                val now = System.currentTimeMillis()
                val elapsedSinceActivity = now - _lastUserActivityTime.value

                if (_isScreenInteractive.value &&
                    !_isDeviceIdleMode.value &&
                    elapsedSinceActivity > DEFAULT_INACTIVITY_TIMEOUT_MS &&
                    _powerState.value == DevicePowerState.ACTIVE
                ) {
                    Log.i(TAG, "Inactivity threshold reached (${elapsedSinceActivity}ms): transitioning to INACTIVE_IDLE")
                    _powerState.value = DevicePowerState.INACTIVE_IDLE
                    releaseNonActiveHardware("User inactivity timeout ($DEFAULT_INACTIVITY_TIMEOUT_MS ms)")
                }
            }
        }
    }

    /**
     * Notifies the manager that user activity has occurred (touch, speech, tool execution).
     * Instantly brings assistant to ACTIVE state and restores wake-word responsiveness.
     */
    fun notifyUserActivity() {
        _lastUserActivityTime.value = System.currentTimeMillis()
        wakeWordManager.notifyUserActivity()
        wakeWordManager.setDeviceIdlePowerMode(false)

        if (_powerState.value == DevicePowerState.INACTIVE_IDLE || _isHardwareReleased.value) {
            Log.i(TAG, "User activity detected: Restoring ACTIVE state")
            _powerState.value = DevicePowerState.ACTIVE
            _isHardwareReleased.value = false
        }
    }

    /**
     * Handles transition when the screen turns off.
     */
    private fun handleScreenOff() {
        Log.i(TAG, "Screen turned off: entering SCREEN_OFF_DOZE power state")
        _powerState.value = DevicePowerState.SCREEN_OFF_DOZE
        releaseNonActiveHardware("Screen turned off")
    }

    /**
     * Handles transition when the screen turns on.
     */
    private fun handleScreenOn() {
        Log.i(TAG, "Screen turned on: restoring ACTIVE state")
        _lastUserActivityTime.value = System.currentTimeMillis()
        wakeWordManager.notifyUserActivity()
        wakeWordManager.setDeviceIdlePowerMode(false)

        val isPowerSave = powerManager?.isPowerSaveMode ?: false
        if (isPowerSave) {
            _powerState.value = DevicePowerState.BATTERY_SAVER
        } else {
            _powerState.value = DevicePowerState.ACTIVE
        }
        _isHardwareReleased.value = false
    }

    /**
     * Checks if Android has entered or exited OS Doze mode.
     */
    private fun checkDeviceIdleMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val isIdle = powerManager?.isDeviceIdleMode ?: false
            _isDeviceIdleMode.value = isIdle
            Log.i(TAG, "Device idle mode changed: isDeviceIdleMode=$isIdle")
            if (isIdle) {
                _powerState.value = DevicePowerState.SCREEN_OFF_DOZE
                releaseNonActiveHardware("Android OS Doze mode active")
            } else if (_isScreenInteractive.value) {
                _powerState.value = DevicePowerState.ACTIVE
                wakeWordManager.setDeviceIdlePowerMode(false)
                _isHardwareReleased.value = false
            }
        }
    }

    /**
     * Checks if System Battery Saver is enabled.
     */
    private fun checkPowerSaveMode() {
        val isPowerSave = powerManager?.isPowerSaveMode ?: false
        _isSystemBatterySaver.value = isPowerSave
        Log.i(TAG, "Power save mode changed: isPowerSaveMode=$isPowerSave")
        if (isPowerSave) {
            _powerState.value = DevicePowerState.BATTERY_SAVER
            wakeWordManager.setBatterySaverEnabled(true)
            releaseNonActiveHardware("System Battery Saver enabled")
        } else {
            wakeWordManager.setBatterySaverEnabled(false)
            if (_isScreenInteractive.value) {
                _powerState.value = DevicePowerState.ACTIVE
                wakeWordManager.setDeviceIdlePowerMode(false)
                _isHardwareReleased.value = false
            }
        }
    }

    /**
     * Explicitly releases audio focus and camera/microphone hardware resources
     * during non-active periods, preventing battery drain while keeping the
     * wake-word listener efficient.
     */
    fun releaseNonActiveHardware(reason: String) {
        powerScope.launch {
            try {
                Log.i(TAG, "Releasing hardware resources during non-active period. Reason: $reason")

                // 1. Explicitly Release Audio Focus
                releaseAudioFocus()

                // 2. Explicitly Release Microphone & Active Speech Recognizer handles
                releaseMicrophoneHardware()

                // 3. Explicitly Release Camera Torch / Flashlight hardware if active
                releaseCameraHardware()

                // 4. Optimize Wake-Word Listener for low-power duty cycle
                optimizeWakeWordListener()

                _isHardwareReleased.value = true
                _hardwareReleaseCount.value += 1
                Log.i(TAG, "Non-active hardware resources released successfully. Release count: ${_hardwareReleaseCount.value}")
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing non-active hardware resources: ${e.message}", e)
            }
        }
    }

    /**
     * Explicitly abandons Android audio focus so DACs and amplifiers can power down.
     */
    private fun releaseAudioFocus() {
        try {
            audioDeviceManager.abandonAudioFocus()
            Log.d(TAG, "Audio focus abandoned successfully.")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to abandon audio focus: ${e.message}")
        }
    }

    /**
     * Explicitly stops continuous speech recognition so the microphone
     * hardware pipeline and high-sampling buffers are released.
     */
    private fun releaseMicrophoneHardware() {
        try {
            speechManager.isContinuousMode = false
            speechManager.stopListening()
            speechManager.clearError()
            Log.d(TAG, "Microphone continuous speech recognition released.")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to stop continuous speech recognition: ${e.message}")
        }
    }

    /**
     * Ensures any camera flashlight or torch mode is turned off.
     */
    private fun releaseCameraHardware() {
        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            val cameraIds = cameraManager?.cameraIdList ?: emptyArray()
            for (id in cameraIds) {
                try {
                    cameraManager?.setTorchMode(id, false)
                } catch (_: Exception) {
                    // Safe ignore if torch wasn't active
                }
            }
            Log.d(TAG, "Camera hardware checked and released.")
        } catch (e: Exception) {
            Log.w(TAG, "Error checking camera hardware: ${e.message}")
        }
    }

    /**
     * Places the wake-word listener into low-power duty cycle mode.
     * The listener remains operational to detect 'Hey Alya', but sleeps longer
     * during silence frames to conserve battery.
     */
    private fun optimizeWakeWordListener() {
        try {
            wakeWordManager.setDeviceIdlePowerMode(true)
            Log.d(TAG, "Wake-word listener optimized for efficient idle duty cycle.")
        } catch (e: Exception) {
            Log.w(TAG, "Error optimizing wake-word listener: ${e.message}")
        }
    }

    /**
     * Stops monitoring and releases broadcast receivers and watchdog jobs.
     */
    fun stopMonitoring() {
        inactivityWatchdogJob?.cancel()
        inactivityWatchdogJob = null

        if (isReceiverRegistered.compareAndSet(true, false)) {
            try {
                context.unregisterReceiver(powerBroadcastReceiver)
                Log.i(TAG, "Power state broadcast receiver unregistered.")
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering power broadcast receiver: ${e.message}")
            }
        }
    }
}
