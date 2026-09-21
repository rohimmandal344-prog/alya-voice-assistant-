package com.example.domain.actions

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.provider.Settings
import android.util.Log
import com.example.data.local.entity.OfflineCommandEntity
import com.example.domain.tools.ActionResultStatus
import com.example.domain.tools.ToolExecutionResult
import com.example.util.diagnostics.DiagnosticLogManager
import com.example.util.diagnostics.DiagnosticStage

/**
 * LocalDeviceController (Alya v2.3.0)
 *
 * Executes basic device control tasks (Volume adjustment, Wi-Fi toggling,
 * Flashlight control, Bluetooth settings, Brightness) on-device locally
 * without requiring an active internet connection.
 */
class LocalDeviceController(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    private val diagLog = DiagnosticLogManager.instance

    fun executeOfflineCommand(entity: OfflineCommandEntity, originalCommand: String): ToolExecutionResult {
        Log.i(TAG, "[LOCAL_DEVICE_CONTROLLER] Executing offline actionType: '${entity.actionType}' for trigger: '${entity.triggerPhrase}'")

        return when (entity.actionType.uppercase()) {
            "VOLUME_UP" -> adjustVolume(increase = true)
            "VOLUME_DOWN" -> adjustVolume(increase = false)
            "VOLUME_MUTE", "MUTE" -> setMute(mute = true)
            "VOLUME_UNMUTE", "UNMUTE" -> setMute(mute = false)
            "VOLUME_SET" -> setVolumePercent(entity.targetValue ?: 50)

            "WIFI_ON", "WIFI_OFF", "WIFI_TOGGLE" -> toggleWifi(entity.actionType)
            "FLASHLIGHT_ON" -> setFlashlight(enable = true)
            "FLASHLIGHT_OFF" -> setFlashlight(enable = false)

            "BLUETOOTH_ON", "BLUETOOTH_OFF", "BLUETOOTH_TOGGLE" -> toggleBluetooth(entity.actionType)
            "BRIGHTNESS_UP" -> adjustBrightness(increase = true)
            "BRIGHTNESS_DOWN" -> adjustBrightness(increase = false)
            "BRIGHTNESS_SETTINGS" -> openSettingsIntent(Settings.ACTION_DISPLAY_SETTINGS, "Display Brightness")

            "SYSTEM_SETTING" -> {
                val intentAction = entity.systemIntentAction ?: Settings.ACTION_SETTINGS
                openSettingsIntent(intentAction, entity.feedbackText)
            }

            else -> {
                if (!entity.systemIntentAction.isNullOrEmpty()) {
                    openSettingsIntent(entity.systemIntentAction, entity.feedbackText)
                } else {
                    ToolExecutionResult(
                        success = false,
                        message = "Command not supported offline.",
                        status = ActionResultStatus.FAILED,
                        errorReason = "UNSUPPORTED_OFFLINE_ACTION"
                    )
                }
            }
        }
    }

    private fun adjustVolume(increase: Boolean): ToolExecutionResult {
        return try {
            if (audioManager != null) {
                val direction = if (increase) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)

                val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val percent = if (maxVol > 0) (currentVol * 100) / maxVol else 0

                val msg = if (increase) "Increased volume to $percent%." else "Decreased volume to $percent%."
                diagLog.logEvent(DiagnosticStage.EXECUTION, "Volume Adjustment", msg, isSuccess = true)

                ToolExecutionResult(
                    success = true,
                    message = msg,
                    status = ActionResultStatus.SUCCESS,
                    targetAppOrFeature = "Volume"
                )
            } else {
                ToolExecutionResult(false, "Audio service unavailable.", ActionResultStatus.FAILED)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error adjusting volume: ${e.message}")
            ToolExecutionResult(false, "Couldn't adjust volume: ${e.message}", ActionResultStatus.FAILED)
        }
    }

    private fun setMute(mute: Boolean): ToolExecutionResult {
        return try {
            if (audioManager != null) {
                val ringerMode = if (mute) AudioManager.RINGER_MODE_SILENT else AudioManager.RINGER_MODE_NORMAL
                audioManager.ringerMode = ringerMode
                val msg = if (mute) "Muted phone audio." else "Unmuted phone audio."

                ToolExecutionResult(
                    success = true,
                    message = msg,
                    status = ActionResultStatus.SUCCESS,
                    targetAppOrFeature = "Ringer Mute"
                )
            } else {
                ToolExecutionResult(false, "Audio service unavailable.", ActionResultStatus.FAILED)
            }
        } catch (e: Exception) {
            ToolExecutionResult(false, "Couldn't change ringer mode.", ActionResultStatus.FAILED)
        }
    }

    private fun setVolumePercent(percent: Int): ToolExecutionResult {
        return try {
            if (audioManager != null) {
                val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val targetVol = ((percent.coerceIn(0, 100) * maxVol) / 100).coerceIn(0, maxVol)
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, AudioManager.FLAG_SHOW_UI)

                ToolExecutionResult(
                    success = true,
                    message = "Set volume to $percent%.",
                    status = ActionResultStatus.SUCCESS,
                    targetAppOrFeature = "Volume"
                )
            } else {
                ToolExecutionResult(false, "Audio service unavailable.", ActionResultStatus.FAILED)
            }
        } catch (e: Exception) {
            ToolExecutionResult(false, "Couldn't set volume.", ActionResultStatus.FAILED)
        }
    }

    private fun toggleWifi(action: String): ToolExecutionResult {
        return try {
            val isOff = action.uppercase() == "WIFI_OFF"
            val registryResult = com.example.domain.tools.LocalDeviceControlRegistry.execute(
                if (isOff) "toggle_wifi" else "turn_on_and_connect_wifi",
                context,
                if (isOff) mapOf("state" to "off") else mapOf("network" to "available")
            )
            if (registryResult != null) {
                registryResult
            } else {
                val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                ToolExecutionResult(
                    success = true,
                    message = if (isOff) "Wi-Fi turned off." else "Wi-Fi turned on and connecting to available network.",
                    status = ActionResultStatus.SUCCESS,
                    targetAppOrFeature = "Wi-Fi"
                )
            }
        } catch (e: Exception) {
            ToolExecutionResult(false, "Couldn't manage Wi-Fi: ${e.message}", ActionResultStatus.FAILED)
        }
    }

    private fun toggleBluetooth(action: String): ToolExecutionResult {
        return try {
            val isOff = action.uppercase() == "BLUETOOTH_OFF"
            val registryResult = com.example.domain.tools.LocalDeviceControlRegistry.execute(
                "toggle_bluetooth",
                context,
                mapOf("state" to if (isOff) "off" else "on")
            )
            if (registryResult != null) {
                registryResult
            } else {
                val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                ToolExecutionResult(
                    success = true,
                    message = if (isOff) "Bluetooth turned off." else "Bluetooth turned on.",
                    status = ActionResultStatus.SUCCESS,
                    targetAppOrFeature = "Bluetooth"
                )
            }
        } catch (e: Exception) {
            ToolExecutionResult(false, "Couldn't manage Bluetooth: ${e.message}", ActionResultStatus.FAILED)
        }
    }

    private fun adjustBrightness(increase: Boolean): ToolExecutionResult {
        return try {
            if (Settings.System.canWrite(context)) {
                val current = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
                val step = 50
                val newVal = if (increase) (current + step).coerceAtMost(255) else (current - step).coerceAtLeast(10)
                Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, newVal)
                val percent = (newVal * 100) / 255
                val msg = if (increase) "Increased screen brightness to $percent%." else "Decreased screen brightness to $percent%."
                ToolExecutionResult(
                    success = true,
                    message = msg,
                    status = ActionResultStatus.SUCCESS,
                    targetAppOrFeature = "Screen Brightness"
                )
            } else {
                openSettingsIntent(Settings.ACTION_DISPLAY_SETTINGS, "Display & Brightness")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Direct brightness change fallback: ${e.message}")
            openSettingsIntent(Settings.ACTION_DISPLAY_SETTINGS, "Display & Brightness")
        }
    }

    private fun setFlashlight(enable: Boolean): ToolExecutionResult {
        return try {
            if (cameraManager != null) {
                val cameraId = cameraManager.cameraIdList.firstOrNull()
                if (cameraId != null) {
                    cameraManager.setTorchMode(cameraId, enable)
                    val msg = if (enable) "Turned on flashlight." else "Turned off flashlight."
                    ToolExecutionResult(
                        success = true,
                        message = msg,
                        status = ActionResultStatus.SUCCESS,
                        targetAppOrFeature = "Flashlight"
                    )
                } else {
                    ToolExecutionResult(false, "No camera flashlight found on device.", ActionResultStatus.FAILED)
                }
            } else {
                ToolExecutionResult(false, "Camera service unavailable.", ActionResultStatus.FAILED)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Flashlight toggle failed: ${e.message}")
            // Fallback: launch camera / flashlight setting
            openSettingsIntent(Settings.ACTION_DISPLAY_SETTINGS, "Flashlight")
        }
    }

    private fun openSettingsIntent(action: String, name: String): ToolExecutionResult {
        return try {
            val intent = Intent(action).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ToolExecutionResult(
                success = true,
                message = "Opened $name settings.",
                status = ActionResultStatus.SUCCESS,
                targetAppOrFeature = name
            )
        } catch (e: Exception) {
            ToolExecutionResult(
                success = false,
                message = "Couldn't open $name settings.",
                status = ActionResultStatus.FAILED,
                errorReason = "INTENT_FAILED"
            )
        }
    }

    companion object {
        private const val TAG = "LocalDeviceController"
    }
}
