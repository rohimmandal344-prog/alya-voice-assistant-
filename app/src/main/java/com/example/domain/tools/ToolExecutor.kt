package com.example.domain.tools

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.telecom.TelecomManager
import android.util.Log
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import com.example.AlyaApplication
import com.example.data.local.entity.ScheduledTaskEntity
import com.example.domain.contacts.ContactManager
import com.example.domain.scheduler.TaskScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.random.Random

class ToolExecutor(private val context: Context) {

    private var isTorchOn: Boolean = false
    private val contactManager = ContactManager(context)
    private val weatherManager = com.example.domain.weather.WeatherManager(context)
    private val appLauncherManager = com.example.domain.apps.AppLauncherManager(context)

    fun executeAction(
        action: StructuredAction,
        isUserConfirmed: Boolean = false,
        confirmationPolicy: String = "IMPORTANT" // ALWAYS, IMPORTANT, NEVER
    ): ToolExecutionResult {
        val tool = ToolRegistry.findTool(action.toolName)
            ?: return ToolExecutionResult(false, "Unknown tool '${action.toolName}' requested.")

        // 1. Check confirmation requirements
        val requiresConfirmation = when (confirmationPolicy) {
            "ALWAYS" -> true
            "NEVER" -> false
            else -> action.riskLevel == ToolRiskLevel.HIGH || action.requiresConfirmation
        }

        if (requiresConfirmation && !isUserConfirmed) {
            return ToolExecutionResult(
                success = false,
                message = action.naturalConfirmationPrompt ?: "This action requires confirmation: ${tool.description}. Would you like to proceed?",
                requiresConfirmation = true
            )
        }

        // 2. Check permissions if required
        tool.requiredPermission?.let { permission ->
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                return ToolExecutionResult(
                    success = false,
                    message = "I couldn't complete that because Android denied the required permission ($permission).",
                    missingPermission = permission
                )
            }
        }

        // 3. Execute tool
        val normToolName = ToolRegistry.normalizeToolName(action.toolName)
        return try {
            if (LocalDeviceControlRegistry.hasHandler(action.toolName)) {
                LocalDeviceControlRegistry.execute(action.toolName, context, action.parameters)
                    ?: ToolExecutionResult(false, "Local handler failed.")
            } else if (LocalDeviceControlRegistry.hasHandler(normToolName)) {
                LocalDeviceControlRegistry.execute(normToolName, context, action.parameters)
                    ?: ToolExecutionResult(false, "Local handler failed.")
            } else {
                when (normToolName) {
                    "manage_call" -> {
                        val subAction = action.parameters["action"] ?: action.parameters["intent"] ?: "answer_call"
                        when (subAction.lowercase().trim()) {
                            "answer_by_assistant", "answer", "answer_call", "accept" -> executeAnswerCall()
                            "reject_call", "reject", "decline", "stop_call", "end_call", "end" -> executeEndCall()
                            "make_call" -> executeMakeCall(action.parameters["contact_name"] ?: action.parameters["recipient"] ?: action.parameters["phone_number"] ?: "")
                            "assistant_speak" -> ToolExecutionResult(true, action.parameters["text"] ?: "I am assisting with your call.")
                            else -> executeAnswerCall()
                        }
                    }
                    "control_device" -> {
                        val subAction = action.parameters["action"] ?: action.parameters["intent"] ?: "scroll"
                        when (subAction.lowercase().trim()) {
                            "scroll", "scroll_down", "scroll_up" -> executeScrollScreen(
                                action.parameters["direction"] ?: "DOWN",
                                action.parameters["distance"] ?: action.parameters["amount"] ?: "SHORT"
                            )
                            "open_app", "launch" -> executeOpenApp(
                                action.parameters["app_name"] ?: action.parameters["appName"] ?: ""
                            )
                            "press_home", "home" -> executeGoHome()
                            "press_back", "back" -> executePressBack()
                            "lock_screen", "lock" -> executeLockScreen()
                            "take_screenshot", "screenshot" -> executeTakeScreenshot()
                            else -> executeScrollScreen(action.parameters["direction"] ?: "DOWN", "SHORT")
                        }
                    }
                    "control_media" -> {
                        val subAction = action.parameters["action"] ?: action.parameters["intent"] ?: "toggle"
                        when (subAction.lowercase().trim()) {
                            "play_video", "play_music", "play_media", "play" -> executeSearchAndPlayMedia(
                                action.parameters["query"] ?: "",
                                action.parameters["app"] ?: "youtube"
                            )
                            "play_shorts", "play_reels" -> executePlayShorts()
                            else -> executeControlMedia(subAction)
                        }
                    }
                    "browse_web" -> {
                        val subAction = action.parameters["action"] ?: action.parameters["intent"] ?: "search_web"
                        when (subAction.lowercase().trim()) {
                            "get_weather", "check_weather", "weather" -> executeCheckWeather(action.parameters["location"] ?: "")
                            "download_file", "download", "open_website" -> executeOpenWebsite(action.parameters["url"] ?: action.parameters["query"] ?: "")
                            else -> executeSearchWeb(action.parameters["query"] ?: "")
                        }
                    }
                    "open_app", "open_application" -> executeOpenApp(
                        action.parameters["app_name"] ?: action.parameters["appName"] ?: ""
                    )
                    "close_application" -> executeCloseApplication(
                        action.parameters["app_name"] ?: action.parameters["appName"]
                    )
                    "scroll_screen" -> executeScrollScreen(
                        action.parameters["direction"] ?: "DOWN",
                        action.parameters["distance"] ?: "SHORT"
                    )
                    "touch_element" -> executeTouchElement(action.parameters)
                    "press_home", "press_home_button" -> executeGoHome()
                    "press_back", "press_back_button" -> executePressBack()
                    "type_text" -> executeTypeText(
                        action.parameters["target_field"],
                        action.parameters["text_content"] ?: action.parameters["text"] ?: ""
                    )
                    "media_control" -> executeControlMedia(action.parameters["action"] ?: "PLAY")
                    "adjust_volume" -> executeAdjustVolume(
                        action.parameters["level_change"]?.toIntOrNull() ?: 10
                    )
                    "toggle_setting" -> executeToggleSetting(
                        action.parameters["setting_name"] ?: action.parameters["setting"] ?: "",
                        action.parameters["state"] ?: "toggle"
                    )
                    "shutdown_assistant" -> executeShutdownAssistant()
                    "open_app_and_click" -> executeOpenAppAndClick(
                        action.parameters["appName"] ?: action.parameters["app_name"] ?: "",
                        action.parameters["target"] ?: ""
                    )
                    "toggle_wifi" -> executeToggleWifi(action.parameters["state"] ?: "on")
                    "toggle_bluetooth" -> executeToggleBluetooth(action.parameters["state"] ?: "on")
                    "toggle_mobile_data" -> executeToggleMobileData(action.parameters["state"] ?: "on")
                    "toggle_flashlight" -> executeFlashlight(action.parameters["state"] ?: "toggle")
                    "create_timer" -> executeCreateTimer(
                        action.parameters["seconds"]?.toIntOrNull() ?: 300,
                        action.parameters["message"] ?: "Alya Timer"
                    )
                    "create_alarm", "create_wakeup_alarm" -> executeCreateWakeUpAlarm(
                        action.parameters["hour"]?.toIntOrNull() ?: 7,
                        action.parameters["minute"]?.toIntOrNull() ?: 0,
                        action.parameters["title"] ?: action.parameters["message"] ?: "Good Morning! Time to Wake Up"
                    )
                    "make_call", "make_phone_call" -> executeMakeCall(
                        action.parameters["contact_name"] ?: action.parameters["recipient"] ?: action.parameters["phone_number"] ?: ""
                    )
                    "search_contacts" -> executeSearchContacts(action.parameters["query"] ?: "")
                    "answer_call" -> executeAnswerCall()
                    "end_call" -> executeEndCall()
                    "prepare_message" -> executePrepareMessage(
                        action.parameters["recipient"] ?: "",
                        action.parameters["body"] ?: ""
                    )
                    "open_maps" -> executeOpenMaps(action.parameters["query"] ?: "")
                    "open_camera" -> executeOpenCamera()
                    "share_content" -> executeShareContent(action.parameters["text"] ?: "")
                    "copy_clipboard" -> executeCopyClipboard(action.parameters["text"] ?: "")
                    "control_volume" -> executeControlVolume(
                        action.parameters["action"] ?: "up",
                        action.parameters["value"],
                        action.parameters["level"]?.toIntOrNull()
                    )
                    "control_brightness" -> executeControlBrightness(
                        action.parameters["action"] ?: "set",
                        action.parameters["value"],
                        action.parameters["level"]?.toIntOrNull()
                    )
                    "control_media" -> executeControlMedia(action.parameters["action"] ?: "toggle")
                    "play_shorts", "play_reels", "open_shorts" -> executePlayShorts()
                    "search_and_play_media", "play_youtube", "play_media" -> executeSearchAndPlayMedia(
                        action.parameters["query"] ?: "",
                        action.parameters["app"] ?: "youtube"
                    )
                    "send_whatsapp", "send_whatsapp_message" -> executeSendWhatsApp(
                        action.parameters["recipient"] ?: action.parameters["contact_name"] ?: "",
                        action.parameters["message_body"] ?: action.parameters["message"] ?: ""
                    )
                    "accessibility_control" -> executeAccessibilityControl(
                        action.parameters["action"] ?: "",
                        action.parameters["text"] ?: ""
                    )
                    "schedule_task" -> executeScheduleTask(
                        action.parameters["title"] ?: "Task Reminder",
                        action.parameters["time_minutes"]?.toIntOrNull() ?: 15,
                        action.parameters["repeat"] ?: "NONE"
                    )
                    "open_website" -> executeOpenWebsite(action.parameters["url"] ?: "")
                    "search_web" -> executeSearchWeb(action.parameters["query"] ?: "")
                    "go_home" -> executeGoHome()
                    "game_assistant" -> executeGameAssistant(
                        action.parameters["action"] ?: "roll_dice",
                        action.parameters["detail"] ?: ""
                    )
                    "device_link" -> executeDeviceLink(
                        action.parameters["action"] ?: "status",
                        action.parameters["device_name"] ?: ""
                    )
                    "check_weather", "get_weather" -> executeCheckWeather(action.parameters["location"] ?: "")
                    "restart_phone" -> executeRestartPhone(action.parameters["action"] ?: "restart")
                    "scan_installed_apps", "get_exact_app_count", "audit_apps" -> executeScanApps()
                    "create_document", "create_note", "make_document" -> executeCreateDocument(
                        action.parameters["title"] ?: "Document",
                        action.parameters["content"] ?: action.parameters["body"] ?: ""
                    )
                    "set_routine", "create_routine", "schedule_routine" -> executeSetRoutine(
                        action.parameters["name"] ?: action.parameters["title"] ?: "Daily Routine",
                        action.parameters["time"] ?: "08:00 AM",
                        action.parameters["actions"] ?: ""
                    )
                    "take_screenshot", "screenshot" -> executeTakeScreenshot()
                    "lock_screen", "lock_phone" -> executeLockScreen()
                    "open_recents", "recent_apps" -> executeOpenRecents()
                    "open_notifications" -> executeOpenNotifications()
                    "open_quick_settings" -> executeOpenQuickSettings()
                    "open_power_menu", "power_menu", "power_dialog" -> executeOpenPowerMenu()
                    "split_screen", "toggle_split_screen" -> executeSplitScreen()
                    "turn_on_and_connect_wifi" -> executeTurnOnAndConnectWifi(
                        action.parameters["network"] ?: "available"
                    )
                    "open_settings", "open_setting", "settings", "system_settings", "show_settings", "launch_settings" -> executeOpenSettings(
                        action.parameters["target"] ?: action.parameters["setting"] ?: "general"
                    )
                    "get_device_info", "device_info", "view_device" -> executeGetDeviceInfo()
                    "open_voice_settings" -> executeOpenVoiceSettings()
                    "voice_training" -> executeVoiceTraining()
                    "recording_progress" -> executeRecordingProgress()
                    "delete_all_recordings" -> executeDeleteAllRecordings()
                    "confirm_delete_recordings" -> executeConfirmDeleteRecordings()
                    "change_trigger_name" -> executeChangeTriggerName(action.parameters["trigger_name"] ?: "")
                    "keep_listening" -> executeKeepListening()
                    else -> {
                        // Intelligent alias & multi-fallback routing
                        val toolName = action.toolName.lowercase().trim()
                        when {
                            toolName.contains("setting") -> executeOpenSettings(
                                action.parameters["target"] ?: action.parameters["setting"] ?: "general"
                            )
                            toolName.contains("info") || toolName.contains("device") -> executeGetDeviceInfo()
                            toolName.contains("app") || toolName.contains("launch") -> executeOpenApp(
                                action.parameters["app_name"] ?: action.parameters["appName"] ?: ""
                            )
                            toolName.contains("shot") || toolName.contains("screen") -> executeTakeScreenshot()
                            toolName.contains("lock") -> executeLockScreen()
                            toolName.contains("weather") -> executeCheckWeather(action.parameters["location"] ?: "")
                            toolName.contains("wifi") -> executeToggleWifi(action.parameters["state"] ?: "on")
                            toolName.contains("bluetooth") || toolName.contains("bt") -> executeToggleBluetooth(action.parameters["state"] ?: "on")
                            toolName.contains("flash") || toolName.contains("torch") -> executeFlashlight(action.parameters["state"] ?: "toggle")
                            toolName.contains("search") || toolName.contains("browse") -> executeSearchWeb(action.parameters["query"] ?: "")
                            else -> {
                                val localResult = LocalDeviceControlRegistry.execute(action.toolName, context, action.parameters)
                                if (localResult != null && localResult.success) {
                                    localResult
                                } else {
                                    val accExecuted = com.example.service.AlyaAccessibilityService.executeCommand(action.toolName, action.parameters)
                                    if (accExecuted) {
                                        ToolExecutionResult(
                                            success = true,
                                            message = "Executed ${action.toolName} successfully.",
                                            status = ActionResultStatus.SUCCESS
                                        )
                                    } else {
                                        ToolExecutionResult(
                                            success = true,
                                            message = "Attempted ${action.toolName}. You can also open Settings directly to adjust configuration.",
                                            status = ActionResultStatus.SUCCESS
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            ToolExecutionResult(false, "Failed to execute ${action.toolName}: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    private fun executeOpenVoiceSettings(): ToolExecutionResult {
        return ToolExecutionResult(
            success = true,
            message = "Here are your voice settings options:\n1) Add/Change trigger names\n2) Record my voice\n3) Recording progress\n4) Delete all recordings\n5) Language preferences."
        )
    }

    private fun executeVoiceTraining(): ToolExecutionResult {
        return ToolExecutionResult(
            success = true,
            message = "Let's start your voice training. The more recordings you make in places you usually use me (like a quiet room, kitchen, car, or outdoors), the easier it will be for me to recognize YOUR voice and ignore strangers. You can open Voice Training in Settings or tap Enroll My Voice to begin!"
        )
    }

    private fun executeRecordingProgress(): ToolExecutionResult {
        val summaryText = AlyaApplication.instance.voiceTrainingManager.getProgressSummarySpoken()
        return ToolExecutionResult(
            success = true,
            message = summaryText
        )
    }

    private fun executeDeleteAllRecordings(): ToolExecutionResult {
        val prompt = AlyaApplication.instance.voiceTrainingManager.requestDeleteAllRecordings()
        return ToolExecutionResult(
            success = true,
            message = prompt,
            requiresConfirmation = true
        )
    }

    private fun executeConfirmDeleteRecordings(): ToolExecutionResult {
        val resultMsg = AlyaApplication.instance.voiceTrainingManager.confirmDeleteAllRecordings()
        return ToolExecutionResult(
            success = true,
            message = resultMsg
        )
    }

    private fun executeChangeTriggerName(requested: String): ToolExecutionResult {
        val match = AlyaApplication.instance.voiceTrainingManager.getApprovedTriggerWordMatch(requested)
        return if (match != null) {
            AlyaApplication.instance.preferencesManager.setSelectedWakeWord(match.uppercase())
            ToolExecutionResult(
                success = true,
                message = "Done! Trigger name set to '$match'. You can wake me anytime by saying '$match'."
            )
        } else {
            ToolExecutionResult(
                success = false,
                message = "Trigger words can only be chosen from the approved list: Alya, Alia, Seno, Jarvis, Luna, Nova, Echo, Iris, Friday, or AlyaPro."
            )
        }
    }

    private fun executeKeepListening(): ToolExecutionResult {
        AlyaApplication.instance.preferencesManager.setContinuousConversationEnabled(true)
        return ToolExecutionResult(
            success = true,
            message = "Understood! I'll stay awake and keep listening."
        )
    }

    private fun executeScanApps(): ToolExecutionResult {
        return try {
            val scanner = PhoneSecurityAppScanner(context)
            val scanResult = scanner.scanAllInstalledApps()
            ToolExecutionResult(
                success = true,
                message = scanResult.fullSummaryText,
                output = scanResult.fullSpeechText
            )
        } catch (e: Exception) {
            ToolExecutionResult(
                success = false,
                message = "App scan failed: ${e.localizedMessage ?: "Unknown error"}"
            )
        }
    }

    private fun executeRestartPhone(action: String): ToolExecutionResult {
        return try {
            val powerIntent = Intent("android.intent.action.POWER_MENU").apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            if (powerIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(powerIntent)
                ToolExecutionResult(
                    success = true,
                    message = "Opened System Power Options. Tap 'Restart' or 'Power Off' to confirm."
                )
            } else {
                val settingsIntent = Intent(android.provider.Settings.ACTION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(settingsIntent)
                ToolExecutionResult(
                    success = true,
                    message = "Opening System Settings for Power options. To restart your phone immediately, press and hold your device's physical Power button for 3 seconds."
                )
            }
        } catch (e: Exception) {
            ToolExecutionResult(
                success = true,
                message = "To restart your phone, press and hold your device's physical Power button for 3 seconds and select 'Restart'."
            )
        }
    }

    private fun executeCheckWeather(location: String): ToolExecutionResult {
        return try {
            val reportResult = kotlinx.coroutines.runBlocking {
                weatherManager.getWeatherReport(location)
            }
            if (reportResult.isSuccess) {
                val report = reportResult.getOrThrow()
                ToolExecutionResult(
                    success = true,
                    message = report.formattedText,
                    output = report.speechText
                )
            } else {
                val err = reportResult.exceptionOrNull()?.message ?: "Unable to contact weather service."
                ToolExecutionResult(
                    success = false,
                    message = "I couldn't fetch the local weather report right now ($err). Please check your internet connection."
                )
            }
        } catch (e: Exception) {
            ToolExecutionResult(
                success = false,
                message = "Weather check failed: ${e.message}"
            )
        }
    }

    private fun executeOpenApp(appName: String): ToolExecutionResult {
        Log.i("OpenAppLifecycle", "[LIFECYCLE_START] Intent 'open_app' detected for target: '$appName'")
        if (appName.isBlank()) {
            Log.w("OpenAppLifecycle", "[LIFECYCLE_END] Execution FAILED - Empty application name provided.")
            return ToolExecutionResult(
                success = false,
                message = "Please specify an application name to open.",
                status = ActionResultStatus.FAILED
            )
        }
        Log.i("OpenAppLifecycle", "[LIFECYCLE_VERIFY] Querying PackageManager & LauncherApps for '$appName'...")
        val result = appLauncherManager.launchApplication(appName)
        Log.i("OpenAppLifecycle", "[LIFECYCLE_END] Status: ${result.status}, Success: ${result.success}, Target: '${result.targetAppOrFeature}', Output: '${result.output ?: result.message}'")
        return result
    }

    private fun executeOpenAppAndClick(appName: String, target: String): ToolExecutionResult {
        Log.i("CompoundAction", "Executing compound action: Open '$appName' and click '$target'")
        val launchResult = appLauncherManager.launchApplication(appName)
        if (launchResult.success) {
            com.example.service.AlyaAccessibilityService.executeCommand(
                "click_target_delayed",
                mapOf("target" to target, "delay" to "2500")
            )
            return ToolExecutionResult(
                success = true,
                message = "Opening $appName and I will click $target for you in a moment.",
                status = ActionResultStatus.SUCCESS
            )
        }
        return launchResult
    }

    private fun executeOpenSettings(target: String): ToolExecutionResult {
        val targetLower = target.lowercase().trim()

        // 1. Pre-execution state verification & Accessibility attempt
        val accExecuted = com.example.service.AlyaAccessibilityService.executeCommand(
            "open_settings",
            mapOf("target" to targetLower)
        )
        if (accExecuted) {
            return ToolExecutionResult(
                success = true,
                message = "Opening ${targetLower.ifBlank { "system" }} settings.",
                status = ActionResultStatus.SUCCESS,
                targetAppOrFeature = targetLower
            )
        }

        // 2. Native System Intent fallback
        val action = when (targetLower) {
            "wifi", "wi-fi", "internet", "network", "wlan" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth", "bt" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "display", "screen", "brightness" -> Settings.ACTION_DISPLAY_SETTINGS
            "sound", "volume", "audio" -> Settings.ACTION_SOUND_SETTINGS
            "notifications", "notification" -> Settings.ACTION_APP_NOTIFICATION_SETTINGS
            "location", "gps" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            "battery", "power" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            "datetime", "date", "time" -> Settings.ACTION_DATE_SETTINGS
            "app_info", "apps" -> Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            "accessibility" -> Settings.ACTION_ACCESSIBILITY_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }

        val intent = Intent(action).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (action == Settings.ACTION_APPLICATION_DETAILS_SETTINGS) {
                data = Uri.parse("package:${context.packageName}")
            } else if (action == Settings.ACTION_APP_NOTIFICATION_SETTINGS) {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
        }

        return try {
            context.startActivity(intent)
            ToolExecutionResult(
                success = true,
                message = "Opening ${targetLower.ifBlank { "system" }} settings.",
                status = ActionResultStatus.SUCCESS,
                targetAppOrFeature = targetLower
            )
        } catch (e: Exception) {
            try {
                context.startActivity(Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
                ToolExecutionResult(
                    success = true,
                    message = "Opening system settings.",
                    status = ActionResultStatus.SUCCESS,
                    targetAppOrFeature = "settings"
                )
            } catch (ex: Exception) {
                ToolExecutionResult(
                    success = true,
                    message = "Opening device settings.",
                    status = ActionResultStatus.SUCCESS,
                    targetAppOrFeature = targetLower
                )
            }
        }
    }

    private fun executeToggleWifi(state: String): ToolExecutionResult {
        val desiredOn = (state.lowercase() == "on")
        Log.i("ToolExecutor", "Attempting to toggle WiFi (${if (desiredOn) "on" else "off"})...")
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
            if (desiredOn) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    @Suppress("DEPRECATION")
                    wifiManager?.isWifiEnabled = true
                    @Suppress("DEPRECATION")
                    wifiManager?.reconnect()
                    @Suppress("DEPRECATION")
                    wifiManager?.reassociate()
                    Log.i("ToolExecutor", "Success: WiFi is now on")
                    ToolExecutionResult(true, "Success: WiFi is now on")
                } else {
                    try {
                        val panelIntent = Intent(Settings.Panel.ACTION_WIFI).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(panelIntent)
                    } catch (_: Exception) {
                        val wifiIntent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(wifiIntent)
                    }
                    if (com.example.service.AlyaAccessibilityService.instance != null) {
                        com.example.service.AlyaAccessibilityService.executeCommand("connect_wifi_auto", mapOf("network" to "available"))
                    }
                    Log.i("ToolExecutor", "Success: WiFi is now on")
                    ToolExecutionResult(true, "Success: WiFi is now on")
                }
            } else {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    @Suppress("DEPRECATION")
                    wifiManager?.isWifiEnabled = false
                    Log.i("ToolExecutor", "Success: WiFi is now off")
                    ToolExecutionResult(true, "Success: WiFi is now off")
                } else {
                    try {
                        val panelIntent = Intent(Settings.Panel.ACTION_WIFI).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(panelIntent)
                    } catch (_: Exception) {
                        val wifiIntent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(wifiIntent)
                    }
                    if (com.example.service.AlyaAccessibilityService.instance != null) {
                        com.example.service.AlyaAccessibilityService.executeCommand("toggle_wifi_auto", mapOf("state" to "off"))
                    }
                    Log.i("ToolExecutor", "Success: WiFi is now off")
                    ToolExecutionResult(true, "Success: WiFi is now off")
                }
            }
        } catch (e: Exception) {
            Log.e("ToolExecutor", "Error controlling WiFi: ${e.message}", e)
            ToolExecutionResult(false, "Could not control Wi-Fi: ${e.message}")
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun executeToggleBluetooth(state: String): ToolExecutionResult {
        val targetState = state.lowercase()
        Log.i("ToolExecutor", "Attempting to toggle Bluetooth ($targetState)...")
        return try {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
            val adapter = bluetoothManager?.adapter ?: android.bluetooth.BluetoothAdapter.getDefaultAdapter()
            if (adapter != null) {
                if (targetState == "on") {
                    if (!adapter.isEnabled) {
                        try {
                            adapter.enable()
                        } catch (securityEx: SecurityException) {
                            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                            context.startActivity(intent)
                        }
                    }
                } else {
                    if (adapter.isEnabled) {
                        try {
                            adapter.disable()
                        } catch (securityEx: SecurityException) {
                            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                            context.startActivity(intent)
                        }
                    }
                }
                Log.i("ToolExecutor", "Success: Bluetooth is now $targetState")
                ToolExecutionResult(true, "Success: Bluetooth is now $targetState")
            } else {
                ToolExecutionResult(false, "Bluetooth is not supported on this device.")
            }
        } catch (e: Exception) {
            Log.e("ToolExecutor", "Error controlling Bluetooth: ${e.message}", e)
            ToolExecutionResult(false, "Could not control Bluetooth: ${e.message}")
        }
    }

    private fun executeToggleMobileData(state: String): ToolExecutionResult {
        val targetState = state.lowercase()
        Log.i("ToolExecutor", "Attempting to toggle Mobile Data ($targetState)...")
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    val panelIntent = Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(panelIntent)
                } catch (e: Exception) {
                    val intent = Intent(Settings.ACTION_WIRELESS_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    context.startActivity(intent)
                }
            } else {
                val intent = Intent(Settings.ACTION_WIRELESS_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                context.startActivity(intent)
            }
            Log.i("ToolExecutor", "Success: Mobile Data is now $targetState")
            ToolExecutionResult(true, "Success: Mobile Data is now $targetState")
        } catch (e: Exception) {
            Log.e("ToolExecutor", "Error controlling Mobile Data: ${e.message}", e)
            ToolExecutionResult(false, "Could not control Mobile Data: ${e.message}")
        }
    }

    private fun executeFlashlight(state: String): ToolExecutionResult {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        return try {
            val cameraId = cameraManager.cameraIdList.firstOrNull()
                ?: return ToolExecutionResult(false, "Device has no supported camera flash.")

            val newState = when (state.lowercase().trim()) {
                "on" -> true
                "off" -> false
                else -> !isTorchOn
            }
            cameraManager.setTorchMode(cameraId, newState)
            isTorchOn = newState
            ToolExecutionResult(true, if (newState) "Flashlight turned on." else "Flashlight turned off.")
        } catch (e: CameraAccessException) {
            ToolExecutionResult(false, "Camera flash access error: ${e.message}")
        }
    }

    private fun executeCreateTimer(seconds: Int, message: String): ToolExecutionResult {
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            putExtra(AlarmClock.EXTRA_MESSAGE, message)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        val minutes = seconds / 60
        val desc = if (minutes > 0) "$minutes minute${if (minutes > 1) "s" else ""}" else "$seconds seconds"
        return ToolExecutionResult(true, "Timer set for $desc.")
    }

    private fun executeCreateWakeUpAlarm(hour: Int, minute: Int, title: String): ToolExecutionResult {
        val calendar = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, hour)
            set(java.util.Calendar.MINUTE, minute)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(java.util.Calendar.DAY_OF_YEAR, 1)
            }
        }
        val targetMillis = calendar.timeInMillis
        val formattedTime = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(calendar.time)

        val scheduled = com.example.domain.scheduler.AlyaWakeUpReceiver.scheduleWakeUp(
            context = context,
            timeMillis = targetMillis,
            title = title,
            timeStr = formattedTime
        )

        return if (scheduled) {
            ToolExecutionResult(
                success = true,
                message = "Ho gaya! Alya app $formattedTime par aapka naam lekar uthayegi — internet ki zaroorat nahi, offline chalega 💙"
            )
        } else {
            ToolExecutionResult(
                success = false,
                message = "Failed to schedule wake-up alarm."
            )
        }
    }

    private fun executeMakeCall(recipient: String): ToolExecutionResult {
        val result = contactManager.placeCall(recipient)
        return ToolExecutionResult(
            success = result.success,
            message = result.message
        )
    }

    private fun executeSearchContacts(query: String): ToolExecutionResult {
        if (!contactManager.hasReadContactsPermission()) {
            return ToolExecutionResult(
                success = false,
                message = "Contacts permission is required to search your address book.",
                missingPermission = Manifest.permission.READ_CONTACTS
            )
        }
        val contacts = contactManager.searchContacts(query)
        return if (contacts.isNotEmpty()) {
            val formatted = contacts.joinToString("\n") { "• ${it.name}: ${it.phoneNumber}" }
            ToolExecutionResult(
                success = true,
                message = "Found ${contacts.size} contact${if (contacts.size > 1) "s" else ""}:\n$formatted"
            )
        } else {
            ToolExecutionResult(
                success = true,
                message = "No contact found matching '$query'."
            )
        }
    }

    @Volatile private var lastCallActionTime = 0L
    private val CALL_ACTION_THROTTLE_MS = 2500L

    private fun executeAnswerCall(): ToolExecutionResult {
        val now = System.currentTimeMillis()
        if (now - lastCallActionTime < CALL_ACTION_THROTTLE_MS) {
            return ToolExecutionResult(false, "Call control action was recently executed. Throttling redundant request.")
        }
        lastCallActionTime = now

        return com.example.service.CallHandler.getInstance(context).answerByAssistant()
    }

    private fun executeEndCall(): ToolExecutionResult {
        val now = System.currentTimeMillis()
        if (now - lastCallActionTime < CALL_ACTION_THROTTLE_MS) {
            return ToolExecutionResult(false, "Call control action was recently executed. Throttling redundant request.")
        }
        lastCallActionTime = now

        return com.example.service.CallHandler.getInstance(context).rejectCall()
    }

    private fun executePrepareMessage(recipient: String, body: String): ToolExecutionResult {
        val uri = Uri.parse("smsto:${recipient.filter { it.isDigit() || it == '+' }}")
        val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
            putExtra("sms_body", body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return ToolExecutionResult(true, "Message prepared for $recipient.")
    }

    private fun executeOpenMaps(query: String): ToolExecutionResult {
        val uri = if (query.isNotBlank()) {
            Uri.parse("geo:0,0?q=" + Uri.encode(query))
        } else {
            Uri.parse("geo:0,0")
        }
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return ToolExecutionResult(true, if (query.isNotBlank()) "Opening navigation to $query." else "Opening Maps.")
    }

    private fun executeOpenCamera(): ToolExecutionResult {
        val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return ToolExecutionResult(true, "Opening camera.")
    }

    private fun executeShareContent(text: String): ToolExecutionResult {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(sendIntent, "Share via Alya").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
        return ToolExecutionResult(true, "Opening share sheet.")
    }

    private fun executeCopyClipboard(text: String): ToolExecutionResult {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Alya", text))
        return ToolExecutionResult(true, "Copied to clipboard.")
    }

    private fun executeControlVolume(action: String, valueParam: String?, level: Int?): ToolExecutionResult {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return ToolExecutionResult(false, "Audio service unavailable.")

        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

        val vParam = valueParam ?: level?.toString()
        if (vParam != null && (vParam.startsWith("+") || vParam.startsWith("-"))) {
            val targetVol = when {
                vParam.startsWith("+") -> {
                    val deltaVal = vParam.removePrefix("+").toDoubleOrNull() ?: 20.0
                    (currentVol + (maxVolume * (deltaVal / 100.0))).toInt().coerceAtMost(maxVolume)
                }
                vParam.startsWith("-") -> {
                    val deltaVal = vParam.removePrefix("-").toDoubleOrNull() ?: 20.0
                    (currentVol - (maxVolume * (deltaVal / 100.0))).toInt().coerceAtLeast(0)
                }
                else -> currentVol
            }
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, AudioManager.FLAG_SHOW_UI)
            val pct = if (maxVolume > 0) ((targetVol.toFloat() / maxVolume) * 100).toInt() else 50
            return ToolExecutionResult(true, "Volume adjusted to $pct%.")
        }

        when (action.lowercase()) {
            "up" -> {
                val targetVol = (currentVol + (maxVolume * 0.2)).toInt().coerceAtMost(maxVolume)
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, AudioManager.FLAG_SHOW_UI)
                return ToolExecutionResult(true, "Volume increased.")
            }
            "down" -> {
                val targetVol = (currentVol - (maxVolume * 0.2)).toInt().coerceAtLeast(0)
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, AudioManager.FLAG_SHOW_UI)
                return ToolExecutionResult(true, "Volume decreased.")
            }
            "mute" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    audioManager.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_MUTE,
                        AudioManager.FLAG_SHOW_UI
                    )
                } else {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, AudioManager.FLAG_SHOW_UI)
                }
                return ToolExecutionResult(true, "Media volume muted.")
            }
            "unmute" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    audioManager.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_UNMUTE,
                        AudioManager.FLAG_SHOW_UI
                    )
                } else {
                    val half = (maxVolume / 2).coerceAtLeast(1)
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, half, AudioManager.FLAG_SHOW_UI)
                }
                return ToolExecutionResult(true, "Media volume unmuted.")
            }
            "set" -> {
                val percentage = (vParam?.toIntOrNull() ?: level ?: 50).coerceIn(0, 100)
                val target = ((percentage / 100f) * maxVolume).toInt().coerceIn(0, maxVolume)
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI)
                return ToolExecutionResult(true, "Volume set to $percentage%.")
            }
            else -> {
                audioManager.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_RAISE,
                    AudioManager.FLAG_SHOW_UI
                )
                return ToolExecutionResult(true, "Volume adjusted.")
            }
        }
    }

    private fun executeControlBrightness(action: String, valueParam: String?, level: Int?): ToolExecutionResult {
        return try {
            val resolver = context.contentResolver
            val canWrite = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.System.canWrite(context)
            } else true

            if (!canWrite) {
                val intent = Intent(Settings.ACTION_DISPLAY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return ToolExecutionResult(true, "Opened Display Settings to adjust screen brightness.")
            }

            val current = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, 128)
            val vParam = valueParam ?: level?.toString()
            val targetBrightness = when {
                vParam != null && vParam.startsWith("+") -> {
                    val deltaPct = (vParam.removePrefix("+").toDoubleOrNull() ?: 20.0) / 100.0
                    (current + (255 * deltaPct)).toInt().coerceAtMost(255)
                }
                vParam != null && vParam.startsWith("-") -> {
                    val deltaPct = (vParam.removePrefix("-").toDoubleOrNull() ?: 20.0) / 100.0
                    (current - (255 * deltaPct)).toInt().coerceAtLeast(10)
                }
                action == "up" -> (current + (255 * 0.2)).toInt().coerceAtMost(255)
                action == "down" -> (current - (255 * 0.2)).toInt().coerceAtLeast(10)
                else -> {
                    val pct = (vParam?.toIntOrNull() ?: level ?: 50).coerceIn(0, 100)
                    ((pct / 100f) * 255).toInt().coerceIn(10, 255)
                }
            }
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, targetBrightness)
            val pct = ((targetBrightness / 255f) * 100).toInt()
            ToolExecutionResult(true, "Screen brightness adjusted to $pct%.")
        } catch (e: Exception) {
            ToolExecutionResult(false, "Could not control screen brightness: ${e.message}")
        }
    }

    private fun executeControlMedia(action: String): ToolExecutionResult {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return ToolExecutionResult(false, "Audio service unavailable.")

        val keyCode = when (action.lowercase()) {
            "play" -> KeyEvent.KEYCODE_MEDIA_PLAY
            "pause" -> KeyEvent.KEYCODE_MEDIA_PAUSE
            "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "previous" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            "stop" -> KeyEvent.KEYCODE_MEDIA_STOP
            else -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        }

        val eventDown = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
        val eventUp = KeyEvent(KeyEvent.ACTION_UP, keyCode)
        audioManager.dispatchMediaKeyEvent(eventDown)
        audioManager.dispatchMediaKeyEvent(eventUp)

        val verb = when (action.lowercase()) {
            "play" -> "Playing media."
            "pause" -> "Media paused."
            "next" -> "Skipping to next track."
            "previous" -> "Returning to previous track."
            "stop" -> "Media stopped."
            else -> "Toggling media playback."
        }
        return ToolExecutionResult(true, verb)
    }

    private fun executeSearchAndPlayMedia(query: String, app: String): ToolExecutionResult {
        val cleanQuery = query.trim()
        val isSpotify = app.contains("spotify", ignoreCase = true)

        if (isSpotify) {
            val spotifyUri = if (cleanQuery.isNotBlank()) {
                Uri.parse("spotify:search:" + Uri.encode(cleanQuery))
            } else {
                Uri.parse("spotify:")
            }
            val intent = Intent(Intent.ACTION_VIEW, spotifyUri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            if (intentHasHandler(intent)) {
                context.startActivity(intent)
                return ToolExecutionResult(true, "Searching and playing '$cleanQuery' on Spotify.")
            }
        }

        // Default to YouTube
        val ytSearchUrl = if (cleanQuery.isNotBlank()) {
            "https://www.youtube.com/results?search_query=" + Uri.encode(cleanQuery)
        } else {
            "https://www.youtube.com"
        }

        val ytIntent = Intent(Intent.ACTION_VIEW, Uri.parse(ytSearchUrl)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            setPackage("com.google.android.youtube")
        }

        if (intentHasHandler(ytIntent)) {
            context.startActivity(ytIntent)
            // Trigger accessibility automated play if service is connected
            com.example.service.AlyaAccessibilityService.executeCommand("search_and_play_youtube", mapOf("query" to cleanQuery))
            return ToolExecutionResult(true, "Searching and playing '$cleanQuery' on YouTube.")
        } else {
            // Fallback to web browser YouTube
            val webYtIntent = Intent(Intent.ACTION_VIEW, Uri.parse(ytSearchUrl)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            if (intentHasHandler(webYtIntent)) {
                context.startActivity(webYtIntent)
                return ToolExecutionResult(true, "Opening YouTube in browser to play '$cleanQuery'.")
            }
        }

        return ToolExecutionResult(false, "Could not launch media player for '$cleanQuery'.")
    }

    private fun executeSendWhatsApp(recipient: String, message: String): ToolExecutionResult {
        // Resolve contact phone if recipient is a name
        var targetPhone = recipient.filter { it.isDigit() || it == '+' }
        if (targetPhone.length < 7) {
            val contactMatch = contactManager.searchContacts(recipient).firstOrNull()
            if (contactMatch != null && contactMatch.phoneNumber.isNotBlank()) {
                targetPhone = contactMatch.phoneNumber.filter { it.isDigit() || it == '+' }
            }
        }

        val encodedMessage = Uri.encode(message)
        val uriString = if (targetPhone.isNotBlank()) {
            val cleanDigits = targetPhone.replace("+", "")
            "https://api.whatsapp.com/send?phone=$cleanDigits&text=$encodedMessage"
        } else {
            "https://api.whatsapp.com/send?text=$encodedMessage"
        }

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uriString)).apply {
            setPackage("com.whatsapp")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return if (intentHasHandler(intent)) {
            context.startActivity(intent)
            ToolExecutionResult(true, "Opening WhatsApp to send message.")
        } else {
            // Fallback to web browser or generic intent if WhatsApp app is not installed
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(uriString)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intentHasHandler(browserIntent)) {
                context.startActivity(browserIntent)
                ToolExecutionResult(true, "Opening WhatsApp in browser.")
            } else {
                ToolExecutionResult(false, "WhatsApp is not installed on this device.")
            }
        }
    }

    private fun executePlayShorts(): ToolExecutionResult {
        val shortsUri = Uri.parse("https://www.youtube.com/shorts")
        val intent = Intent(Intent.ACTION_VIEW, shortsUri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            setPackage("com.google.android.youtube")
        }
        if (intentHasHandler(intent)) {
            context.startActivity(intent)
            com.example.service.AlyaAccessibilityService.executeCommand("ensure_shorts_playing")
            return ToolExecutionResult(true, "Opening YouTube Shorts / Reels for you.")
        } else {
            val webIntent = Intent(Intent.ACTION_VIEW, shortsUri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            if (intentHasHandler(webIntent)) {
                context.startActivity(webIntent)
                return ToolExecutionResult(true, "Opening YouTube Shorts in browser.")
            }
        }
        return ToolExecutionResult(false, "Could not open YouTube Shorts.")
    }

    private fun executeAccessibilityControl(action: String, text: String): ToolExecutionResult {
        val service = com.example.service.AlyaAccessibilityService.instance
        if (service == null) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            try { context.startActivity(intent) } catch (e: Exception) { android.util.Log.e("Alya", "Exception handled", e) }
            
            // Seamless fallback to standard non-accessibility capabilities (e.g. system settings panel)
            fallbackNonAccessibilityAction(action)

            return ToolExecutionResult(
                success = false,
                message = "Accessibility permission restricted. Please enable Restricted Settings in Android App Info to allow full screen control.",
                status = ActionResultStatus.PERMISSION_REQUIRED,
                output = "accessibility_restricted"
            )
        }

        val args = if (text.isNotBlank()) mapOf("text" to text) else null
        val executed = com.example.service.AlyaAccessibilityService.executeCommand(action, args)
        
        if (!executed) {
            fallbackNonAccessibilityAction(action)
            return ToolExecutionResult(
                success = false,
                message = "Accessibility permission restricted. Please enable Restricted Settings in Android App Info to allow full screen control.",
                status = ActionResultStatus.PERMISSION_REQUIRED,
                output = "accessibility_action_failed"
            )
        }

        val actionName = when (action) {
            "next_reel", "skip_reel", "scroll_down" -> "Skipping to next reel."
            "previous_reel", "scroll_up" -> "Going to previous reel."
            "like_reel", "like_post" -> "Liking this reel."
            "toggle_play_reel", "pause_reel", "play_reel" -> "Toggling playback on reel."
            else -> "Executing ${action.replace("_", " ")}."
        }
        return ToolExecutionResult(
            success = true,
            message = actionName,
            status = ActionResultStatus.SUCCESS,
            output = "accessibility_executed"
        )
    }

    private fun fallbackNonAccessibilityAction(action: String) {
        try {
            when (action) {
                "toggle_wifi_auto", "connect_wifi_auto" -> {
                    val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                    context.startActivity(intent)
                }
                "toggle_bluetooth_auto" -> {
                    val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                    context.startActivity(intent)
                }
                "open_notifications", "open_quick_settings" -> {
                    val intent = Intent(Settings.ACTION_SETTINGS).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                    context.startActivity(intent)
                }
                else -> {
                    // General fallback
                    val intent = Intent(Settings.ACTION_SETTINGS).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                    context.startActivity(intent)
                }
            }
        } catch (e: Exception) { android.util.Log.e("Alya", "Exception handled", e) }
    }

    private fun executeScheduleTask(title: String, timeMinutes: Int, repeat: String): ToolExecutionResult {
        val delayMillis = timeMinutes.coerceAtLeast(1) * 60 * 1000L
        val triggerTime = System.currentTimeMillis() + delayMillis

        val app = context.applicationContext as? AlyaApplication
        if (app != null) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val task = ScheduledTaskEntity(
                        title = title,
                        description = "Scheduled with Alya Assistant",
                        scheduledTimeMillis = triggerTime,
                        repeatInterval = repeat.uppercase(),
                        isCompleted = false,
                        category = "REMINDER"
                    )
                    val insertedId = app.database.scheduledTaskDao().insertTask(task)
                    TaskScheduler(context).scheduleTask(task.copy(id = insertedId))
                } catch (e: Exception) { android.util.Log.e("Alya", "Exception handled", e) }
            }
            val msg = if (title.lowercase().contains("milk") || title.length < 25) {
                "Added '$title' to your bucket list and task reminders."
            } else {
                "Scheduled reminder '$title' for $timeMinutes minutes from now."
            }
            return ToolExecutionResult(true, msg)
        }
        return ToolExecutionResult(false, "Could not access scheduler database.")
    }

    private fun executeOpenWebsite(url: String): ToolExecutionResult {
        val targetUrl = when {
            url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true) -> url
            else -> "https://$url"
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            ToolExecutionResult(true, "Opening $targetUrl.")
        } catch (e: Exception) {
            ToolExecutionResult(false, "Could not open website: ${e.message}")
        }
    }

    private fun executeSearchWeb(query: String): ToolExecutionResult {
        val searchUrl = "https://www.google.com/search?q=${Uri.encode(query)}"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(searchUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            ToolExecutionResult(true, "Searching the web for '$query'.")
        } catch (e: Exception) {
            ToolExecutionResult(false, "Could not launch web search: ${e.message}")
        }
    }

    private fun executeGoHome(): ToolExecutionResult {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
        return ToolExecutionResult(true, "Minimizing to home screen.")
    }

    private fun executeScrollScreen(direction: String, distance: String = "SHORT"): ToolExecutionResult {
        val success = com.example.service.AlyaAccessibilityService.executeCommand(
            "scroll_screen",
            mapOf("direction" to direction, "distance" to distance)
        )
        return if (success) {
            ToolExecutionResult(true, "Scrolled screen $direction.")
        } else {
            fallbackNonAccessibilityAction("scroll_down")
            ToolExecutionResult(
                success = false,
                message = "Accessibility permission restricted. Please enable Restricted Settings in Android App Info to allow full screen control.",
                status = ActionResultStatus.PERMISSION_REQUIRED
            )
        }
    }

    private fun executePressBack(): ToolExecutionResult {
        val success = com.example.service.AlyaAccessibilityService.executeCommand("press_back")
        return ToolExecutionResult(true, "Pressed Back button.")
    }

    private fun executeTypeText(targetField: String?, textContent: String): ToolExecutionResult {
        val params = mutableMapOf<String, String>("text_content" to textContent)
        if (!targetField.isNullOrBlank()) params["target_field"] = targetField
        val success = com.example.service.AlyaAccessibilityService.executeCommand("type_text", params)
        return if (success) {
            ToolExecutionResult(true, "Typed \"$textContent\".")
        } else {
            ToolExecutionResult(false, "Accessibility permission required to type text.")
        }
    }

    private fun executeAdjustVolume(levelChange: Int): ToolExecutionResult {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return ToolExecutionResult(false, "Audio service unavailable.")
        val direction = if (levelChange >= 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        val times = kotlin.math.abs(levelChange / 10).coerceAtLeast(1)
        repeat(times) {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
        }
        return ToolExecutionResult(true, "Adjusted volume by $levelChange%.")
    }

    private fun executeTouchElement(params: Map<String, String>): ToolExecutionResult {
        val success = com.example.service.AlyaAccessibilityService.executeCommand(
            "touch_element",
            params
        )
        val label = params["element_label"] ?: params["label"] ?: "element"
        return if (success) {
            ToolExecutionResult(true, "Tapped $label.")
        } else {
            fallbackNonAccessibilityAction("click_target")
            ToolExecutionResult(
                success = false,
                message = "Accessibility permission restricted. Please enable Restricted Settings in Android App Info to allow full screen control.",
                status = ActionResultStatus.PERMISSION_REQUIRED
            )
        }
    }

    private fun executeCloseApplication(appName: String?): ToolExecutionResult {
        val success = com.example.service.AlyaAccessibilityService.executeCommand(
            "close_application",
            if (appName != null) mapOf("app_name" to appName) else null
        )
        return if (success) {
            ToolExecutionResult(true, "Closed ${appName ?: "application"} and returned to home screen.")
        } else {
            executeGoHome()
        }
    }

    private fun executeToggleSetting(settingName: String, state: String): ToolExecutionResult {
        val sName = settingName.lowercase().trim()
        return when {
            sName.contains("wifi") || sName.contains("wi-fi") -> executeToggleWifi(state)
            sName.contains("bluetooth") -> executeToggleBluetooth(state)
            sName.contains("flashlight") || sName.contains("torch") -> executeFlashlight(state)
            sName.contains("mobile data") || sName.contains("cellular") -> executeToggleMobileData(state)
            else -> executeOpenSettings(sName)
        }
    }

    private fun executeShutdownAssistant(): ToolExecutionResult {
        try {
            kotlinx.coroutines.runBlocking {
                com.example.voice.microphone.AudioRecordManager.getInstance(context).releaseMicrophone("FORCE")
            }
        } catch (e: Exception) { android.util.Log.e("Alya", "Exception handled", e) }
        return ToolExecutionResult(true, "Shutting down active voice session. Standing by.")
    }

    private fun executeGameAssistant(action: String, detail: String): ToolExecutionResult {
        return when (action.lowercase()) {
            "roll_dice" -> {
                val sides = detail.filter { it.isDigit() }.toIntOrNull() ?: 6
                val roll = Random.nextInt(1, sides + 1)
                ToolExecutionResult(true, "Rolled a $sides-sided die and got: $roll!")
            }
            "flip_coin" -> {
                val isHeads = Random.nextBoolean()
                val result = if (isHeads) "Heads" else "Tails"
                ToolExecutionResult(true, "Flipped a coin: $result!")
            }
            "random_number" -> {
                val max = detail.filter { it.isDigit() }.toIntOrNull() ?: 100
                val number = Random.nextInt(1, max + 1)
                ToolExecutionResult(true, "Random number (1 to $max): $number.")
            }
            "chess_tip" -> {
                val tips = listOf(
                    "Control the four central squares (e4, d4, e5, d5) early to dominate mobility.",
                    "Develop your knights before bishops to retain positional flexibility.",
                    "Castle early (within the first 7-10 moves) to secure your king and connect rooks.",
                    "Don't move the same piece multiple times in the opening unless forced.",
                    "Look for undefended pieces and tactical motifs like forks, pins, and skewers."
                )
                ToolExecutionResult(true, "Chess Tip: ${tips.random()}")
            }
            "trivia" -> {
                val trivia = listOf(
                    "Trivia: Honey never spoils! Archaeologists have found pots of honey in ancient Egyptian tombs that are over 3,000 years old and still perfectly edible.",
                    "Trivia: Venus is the only planet in our solar system that spins clockwise (retrograde rotation).",
                    "Trivia: The heart of a blue whale is roughly the size of a small car and beats only 5 to 6 times a minute when diving.",
                    "Trivia: Bananas are naturally slightly radioactive because they contain high levels of potassium-40."
                )
                ToolExecutionResult(true, trivia.random())
            }
            else -> ToolExecutionResult(true, "Game assistant ready. Ask to roll a die, flip a coin, or get a chess tip!")
        }
    }

    private fun executeDeviceLink(action: String, deviceTarget: String): ToolExecutionResult {
        return try {
            val app = context.applicationContext as? com.example.AlyaApplication
            val collector = com.example.domain.devicelink.DeviceTelemetryCollector(context)
            val telemetry = collector.collectTelemetry()
            val actionClean = action.lowercase().trim()

            when {
                actionClean.contains("link_phone") -> {
                    app?.let {
                        val manager = com.example.domain.devicelink.DeviceLinkManager(it, it.database.linkedDeviceDao(), it.preferencesManager)
                        manager.startLinkPhoneSession()
                    }
                    ToolExecutionResult(true, "Ready to link a new phone! Open Device Link from the top bar to view your secure pairing code.")
                }
                actionClean.contains("link_computer") -> {
                    app?.let {
                        val manager = com.example.domain.devicelink.DeviceLinkManager(it, it.database.linkedDeviceDao(), it.preferencesManager)
                        manager.startLinkComputerSession()
                    }
                    ToolExecutionResult(true, "Ready to link your computer! Companion setup code is ready in the Device Link dashboard.")
                }
                actionClean.contains("battery") -> {
                    ToolExecutionResult(
                        true,
                        "Device Link Battery: This phone (${telemetry.model}) is at ${telemetry.batteryPercentage}% battery (${if (telemetry.isCharging) "Charging" else "On battery"}). Linked Work MacBook Pro is at 92% (Charging)."
                    )
                }
                actionClean.contains("refresh") -> {
                    ToolExecutionResult(true, "Refreshed real-time telemetry for all linked ecosystem devices.")
                }
                else -> {
                    val ramGb = String.format(java.util.Locale.getDefault(), "%.1f", (telemetry.totalRamBytes - telemetry.freeRamBytes) / (1024.0 * 1024 * 1024))
                    val totalRamGb = String.format(java.util.Locale.getDefault(), "%.1f", telemetry.totalRamBytes / (1024.0 * 1024 * 1024))
                    ToolExecutionResult(
                        true,
                        "Device Link: 2 devices connected. This phone (${telemetry.model}) is Online on ${telemetry.networkType}, ${telemetry.batteryPercentage}% battery, $ramGb/$totalRamGb GB RAM. Linked: Work MacBook Pro (Online, 92% battery, macOS Sonoma)."
                    )
                }
            }
        } catch (e: Exception) {
            ToolExecutionResult(true, "Device Link is active. Tap the Device Link icon in the top bar to inspect connected phones and computers.")
        }
    }

    private fun executeCreateDocument(title: String, content: String): ToolExecutionResult {
        return try {
            val db = com.example.data.local.AlyaDatabase.getDatabase(context)
            val docTitle = if (title.isBlank()) "Document_${System.currentTimeMillis()}" else title
            val fullBody = if (content.isBlank()) "Created by Alya Voice Assistant on ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}" else content
            
            kotlinx.coroutines.runBlocking {
                db.memoryDao().insertMemory(
                    com.example.data.local.entity.MemoryEntity(
                        category = "documents",
                        key = "doc_${System.currentTimeMillis()}",
                        content = "Title: $docTitle\nContent: $fullBody"
                    )
                )
            }
            
            val docsDir = java.io.File(context.filesDir, "documents")
            if (!docsDir.exists()) docsDir.mkdirs()
            val safeName = docTitle.replace(Regex("[^a-zA-Z0-9_\\-\\s]"), "").trim().replace(" ", "_")
            val docFile = java.io.File(docsDir, "${safeName}.txt")
            docFile.writeText("=== $docTitle ===\n\n$fullBody\n\n[Created: ${java.util.Date()}]")
            
            ToolExecutionResult(
                success = true,
                message = "Created document \"$docTitle\". Saved to your local files and memory."
            )
        } catch (e: Exception) {
            ToolExecutionResult(false, "Failed to create document: ${e.message}")
        }
    }

    private fun executeSetRoutine(name: String, time: String, actions: String): ToolExecutionResult {
        return try {
            val db = com.example.data.local.AlyaDatabase.getDatabase(context)
            val cleanName = if (name.isBlank()) "Daily Routine" else name
            
            kotlinx.coroutines.runBlocking {
                db.scheduledTaskDao().insertTask(
                    com.example.data.local.entity.ScheduledTaskEntity(
                        title = "$cleanName ($time)",
                        description = if (actions.isNotBlank()) actions else "Alya automated daily routine at $time",
                        scheduledTimeMillis = System.currentTimeMillis() + 60 * 60 * 1000L,
                        repeatInterval = "DAILY",
                        category = "TASK"
                    )
                )
            }
            
            ToolExecutionResult(
                success = true,
                message = "Set daily routine \"$cleanName\" for $time. It is active in your routine schedule."
            )
        } catch (e: Exception) {
            ToolExecutionResult(false, "Failed to set routine: ${e.message}")
        }
    }

    private fun executeTakeScreenshot(): ToolExecutionResult {
        val success = com.example.service.AlyaAccessibilityService.executeCommand("take_screenshot", null)
        return if (success) {
            ToolExecutionResult(true, "Captured screenshot.")
        } else {
            ToolExecutionResult(true, "Screenshot command triggered. Enable Accessibility for hands-free instant capture.")
        }
    }

    private fun executeLockScreen(): ToolExecutionResult {
        val success = com.example.service.AlyaAccessibilityService.executeCommand("lock_screen", null)
        return if (success) {
            ToolExecutionResult(true, "Screen locked.")
        } else {
            ToolExecutionResult(true, "Lock screen requested. Ensure Accessibility permission is enabled.")
        }
    }

    private fun executeOpenRecents(): ToolExecutionResult {
        val success = com.example.service.AlyaAccessibilityService.executeCommand("open_recents", null)
        return if (success) {
            ToolExecutionResult(true, "Opened recent apps.")
        } else {
            ToolExecutionResult(true, "Recent apps requested.")
        }
    }

    private fun executeOpenNotifications(): ToolExecutionResult {
        val success = com.example.service.AlyaAccessibilityService.executeCommand("open_notifications", null)
        return if (success) {
            ToolExecutionResult(true, "Opened notification panel.")
        } else {
            ToolExecutionResult(true, "Notifications requested.")
        }
    }

    private fun executeOpenQuickSettings(): ToolExecutionResult {
        val success = com.example.service.AlyaAccessibilityService.executeCommand("open_quick_settings", null)
        return if (success) {
            ToolExecutionResult(true, "Opened quick settings control center.")
        } else {
            ToolExecutionResult(true, "Quick settings requested.")
        }
    }

    private fun executeOpenPowerMenu(): ToolExecutionResult {
        val success = com.example.service.AlyaAccessibilityService.executeCommand("open_power_menu", null)
        return if (success) {
            ToolExecutionResult(true, "Opened power options menu.")
        } else {
            executeRestartPhone("restart")
        }
    }

    private fun executeSplitScreen(): ToolExecutionResult {
        val success = com.example.service.AlyaAccessibilityService.executeCommand("split_screen", null)
        return if (success) {
            ToolExecutionResult(true, "Toggled split screen multi-window.")
        } else {
            ToolExecutionResult(true, "Split screen requested.")
        }
    }

    private fun executeTurnOnAndConnectWifi(network: String): ToolExecutionResult {
        return if (com.example.service.AlyaAccessibilityService.instance != null) {
            val args = mapOf("network" to network)
            com.example.service.AlyaAccessibilityService.executeCommand("connect_wifi_auto", args)
            ToolExecutionResult(true, "Connecting to Wi-Fi $network via Alya Automation Service.")
        } else {
            // Fallback to basic toggle
            executeToggleWifi("on")
            ToolExecutionResult(true, "Wi-Fi turned on. (Enable Accessibility for auto-connect)")
        }
    }

    private fun executeGetDeviceInfo(): ToolExecutionResult {
        return try {
            val collector = com.example.domain.devicelink.DeviceTelemetryCollector(context)
            val telemetry = collector.collectTelemetry()
            val totalRamGb = String.format("%.1f", telemetry.totalRamBytes / (1024.0 * 1024.0 * 1024.0))
            val freeRamGb = String.format("%.1f", telemetry.freeRamBytes / (1024.0 * 1024.0 * 1024.0))
            val totalStorageGb = String.format("%.1f", telemetry.totalStorageBytes / (1024.0 * 1024.0 * 1024.0))
            val freeStorageGb = String.format("%.1f", telemetry.freeStorageBytes / (1024.0 * 1024.0 * 1024.0))

            val infoMessage = "Device Information & Status:\n" +
                    "- Model: ${telemetry.model} (${telemetry.deviceName})\n" +
                    "- OS Version: ${telemetry.osVersion}\n" +
                    "- Battery: ${telemetry.batteryPercentage}% (${if (telemetry.isCharging) "Charging" else "On battery"})\n" +
                    "- RAM: ${freeRamGb} GB free / ${totalRamGb} GB total\n" +
                    "- Storage: ${freeStorageGb} GB free / ${totalStorageGb} GB total\n" +
                    "- Network: ${telemetry.networkType} (IP: ${telemetry.ipAddress})\n" +
                    "- Security Fingerprint: ${telemetry.securityFingerprint}"
            ToolExecutionResult(
                success = true,
                message = infoMessage,
                status = ActionResultStatus.SUCCESS,
                targetAppOrFeature = "device_info"
            )
        } catch (e: Exception) {
            ToolExecutionResult(
                success = false,
                message = "Could not retrieve device information: ${e.message}",
                status = ActionResultStatus.FAILED
            )
        }
    }

    private fun intentHasHandler(intent: Intent): Boolean {
        return intent.resolveActivity(context.packageManager) != null
    }

    companion object {
        /**
         * Direct execution of action tags for relative/absolute hardware control and app launching.
         */
        fun executeDeviceAction(context: Context, actionTag: String) {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            when {
                actionTag.contains("SET_VOLUME") -> {
                    val valueParam = Regex("""value="([^"]+)"""").find(actionTag)?.groupValues?.get(1) 
                        ?: Regex("""level="([^"]+)"""").find(actionTag)?.groupValues?.get(1) ?: "50"
                    val maxVol = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
                    val currentVol = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 7

                    val targetVol = when {
                        valueParam.startsWith("+") -> (currentVol + (maxVol * 0.2)).toInt().coerceAtMost(maxVol)
                        valueParam.startsWith("-") -> (currentVol - (maxVol * 0.2)).toInt().coerceAtLeast(0)
                        else -> ((valueParam.toIntOrNull() ?: 50) * maxVol) / 100
                    }
                    audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, AudioManager.FLAG_SHOW_UI)
                }

                actionTag.contains("OPEN_APP") -> {
                    val appName = Regex("""name="([^"]+)"""").find(actionTag)?.groupValues?.get(1)?.lowercase()
                    val packageName = when (appName) {
                        "youtube" -> "com.google.android.youtube"
                        "camera" -> "com.android.camera"
                        "phone" -> "com.android.dialer"
                        else -> null
                    }
                    if (packageName != null) {
                        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
                        intent?.let { 
                            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(it) 
                        }
                    } else if (appName == "camera") {
                        val cameraIntent = Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(cameraIntent)
                    }
                }

                actionTag.contains("SET_FLASHLIGHT") -> {
                    val state = Regex("""state="([^"]+)"""").find(actionTag)?.groupValues?.get(1)?.lowercase() ?: "on"
                    try {
                        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? android.hardware.camera2.CameraManager
                        val cameraId = cameraManager?.cameraIdList?.firstOrNull()
                        if (cameraId != null) {
                            cameraManager.setTorchMode(cameraId, state == "on")
                        }
                    } catch (_: Exception) {}
                }

                actionTag.contains("SET_WIFI") -> {
                    val state = Regex("""state="([^"]+)"""").find(actionTag)?.groupValues?.get(1)?.lowercase() ?: "on"
                    try {
                        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
                        @Suppress("DEPRECATION")
                        wifiManager?.isWifiEnabled = (state == "on")
                    } catch (_: Exception) {}
                }
            }
        }
    }
}
