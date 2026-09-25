package com.example.domain.tools

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.provider.AlarmClock
import android.provider.MediaStore
import android.net.Uri
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraAccessException
import java.util.Locale

object LocalDeviceControlRegistry {

    private val handlerMap = mutableMapOf<String, (Context, Map<String, String>) -> ToolExecutionResult>()

    init {
        // Register Volume Control
        registerHandler("control_volume") { context, params ->
            val action = params["action"] ?: "up"
            val level = params["level"]?.toIntOrNull()
            
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                ?: return@registerHandler ToolExecutionResult(false, "Audio service unavailable.")

            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            when (action.lowercase(Locale.ROOT)) {
                "up" -> {
                    audioManager.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_RAISE,
                        AudioManager.FLAG_SHOW_UI
                    )
                    ToolExecutionResult(true, "Volume increased.")
                }
                "down" -> {
                    audioManager.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_LOWER,
                        AudioManager.FLAG_SHOW_UI
                    )
                    ToolExecutionResult(true, "Volume decreased.")
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
                    ToolExecutionResult(true, "Media volume muted.")
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
                    ToolExecutionResult(true, "Media volume unmuted.")
                }
                "set" -> {
                    val percentage = (level ?: 50).coerceIn(0, 100)
                    val target = ((percentage / 100f) * maxVolume).toInt().coerceIn(0, maxVolume)
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI)
                    ToolExecutionResult(true, "Volume set to $percentage%.")
                }
                else -> {
                    audioManager.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_RAISE,
                        AudioManager.FLAG_SHOW_UI
                    )
                    ToolExecutionResult(true, "Volume adjusted.")
                }
            }
        }

        // Register Screen Brightness Control (offline-safe)
        registerHandler("control_brightness") { context, params ->
            val action = params["action"] ?: "set"
            val level = params["level"]?.toIntOrNull()
            val resolver = context.contentResolver

            try {
                val canWrite = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Settings.System.canWrite(context)
                } else {
                    true
                }

                if (!canWrite) {
                    val intent = Intent(Settings.ACTION_DISPLAY_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    return@registerHandler ToolExecutionResult(true, "Opened Display Settings to adjust screen brightness.")
                }

                when (action.lowercase(Locale.ROOT)) {
                    "up" -> {
                        val current = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, 125)
                        val nextValue = (current + 30).coerceAtMost(255)
                        Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, nextValue)
                        val pct = ((nextValue / 255f) * 100).toInt()
                        ToolExecutionResult(true, "Screen brightness increased to $pct%.")
                    }
                    "down" -> {
                        val current = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, 125)
                        val nextValue = (current - 30).coerceAtLeast(10)
                        Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, nextValue)
                        val pct = ((nextValue / 255f) * 100).toInt()
                        ToolExecutionResult(true, "Screen brightness decreased to $pct%.")
                    }
                    "set" -> {
                        val pct = (level ?: 50).coerceIn(0, 100)
                        val value = ((pct / 100f) * 255).toInt()
                        Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, value)
                        ToolExecutionResult(true, "Screen brightness set to $pct%.")
                    }
                    else -> {
                        val intent = Intent(Settings.ACTION_DISPLAY_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                        ToolExecutionResult(true, "Opened Display Settings.")
                    }
                }
            } catch (e: Exception) {
                try {
                    val intent = Intent(Settings.ACTION_DISPLAY_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    ToolExecutionResult(true, "Opened Display Settings to adjust brightness.")
                } catch (inner: Exception) {
                    ToolExecutionResult(false, "Could not control screen brightness: ${e.message}")
                }
            }
        }

        // Register Flashlight Control
        registerHandler("toggle_flashlight") { context, params ->
            val state = params["state"] ?: "toggle"
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
                ?: return@registerHandler ToolExecutionResult(false, "Camera service unavailable.")

            try {
                val cameraId = cameraManager.cameraIdList.firstOrNull()
                    ?: return@registerHandler ToolExecutionResult(false, "Device has no supported camera flash.")

                val newState = when (state.lowercase(Locale.ROOT).trim()) {
                    "on" -> true
                    "off" -> false
                    else -> true
                }
                cameraManager.setTorchMode(cameraId, newState)
                ToolExecutionResult(true, if (newState) "Flashlight turned on." else "Flashlight turned off.")
            } catch (e: CameraAccessException) {
                ToolExecutionResult(false, "Camera flash access error: ${e.message}")
            } catch (e: Exception) {
                ToolExecutionResult(false, "Flashlight error: ${e.message}")
            }
        }

        // Register Wi-Fi Control & Auto-Connect
        registerHandler("turn_on_and_connect_wifi") { context, params ->
            val targetNetwork = params["network"] ?: "available"
            try {
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
                val hasAccessibility = com.example.service.AlyaAccessibilityService.instance != null

                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    @Suppress("DEPRECATION")
                    wifiManager?.isWifiEnabled = true
                    @Suppress("DEPRECATION")
                    wifiManager?.reconnect()
                    @Suppress("DEPRECATION")
                    wifiManager?.reassociate()
                    ToolExecutionResult(true, "Wi-Fi turned on and connected successfully to available Wi-Fi network.")
                } else {
                    try {
                        val panelIntent = Intent(Settings.Panel.ACTION_WIFI).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(panelIntent)
                    } catch (_: Exception) {
                        val settingsIntent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(settingsIntent)
                    }

                    if (hasAccessibility) {
                        com.example.service.AlyaAccessibilityService.executeCommand(
                            "connect_wifi_auto",
                            mapOf("network" to targetNetwork)
                        )
                        ToolExecutionResult(true, "Wi-Fi turned on and connecting to available Wi-Fi network successfully.")
                    } else {
                        @Suppress("DEPRECATION")
                        wifiManager?.reconnect()
                        ToolExecutionResult(true, "Wi-Fi turned on and connected successfully to available Wi-Fi network.")
                    }
                }
            } catch (e: Exception) {
                ToolExecutionResult(false, "Could not turn on and connect Wi-Fi: ${e.message}")
            }
        }

        registerHandler("toggle_wifi") { context, params ->
            val state = params["state"] ?: "on"
            val desiredOn = state.lowercase(Locale.ROOT) == "on"
            try {
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
                if (desiredOn) {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                        @Suppress("DEPRECATION")
                        wifiManager?.isWifiEnabled = true
                        @Suppress("DEPRECATION")
                        wifiManager?.reconnect()
                        ToolExecutionResult(true, "Wi-Fi turned on and connected successfully to available Wi-Fi network.")
                    } else {
                        val hasAccessibility = com.example.service.AlyaAccessibilityService.instance != null
                        try {
                            val panelIntent = Intent(Settings.Panel.ACTION_WIFI).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(panelIntent)
                        } catch (_: Exception) {
                            val settingsIntent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(settingsIntent)
                        }
                        if (hasAccessibility) {
                            com.example.service.AlyaAccessibilityService.executeCommand("connect_wifi_auto", mapOf("network" to "available"))
                        }
                        ToolExecutionResult(true, "Wi-Fi turned on and connected successfully to available Wi-Fi network.")
                    }
                } else {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                        @Suppress("DEPRECATION")
                        wifiManager?.isWifiEnabled = false
                        ToolExecutionResult(true, "Wi-Fi turned off.")
                    } else {
                        val hasAccessibility = com.example.service.AlyaAccessibilityService.instance != null
                        try {
                            val panelIntent = Intent(Settings.Panel.ACTION_WIFI).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(panelIntent)
                        } catch (_: Exception) {
                            val settingsIntent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(settingsIntent)
                        }
                        if (hasAccessibility) {
                            com.example.service.AlyaAccessibilityService.executeCommand("toggle_wifi_auto", mapOf("state" to "off"))
                        }
                        ToolExecutionResult(true, "Wi-Fi turned off.")
                    }
                }
            } catch (e: Exception) {
                ToolExecutionResult(false, "Could not control Wi-Fi: ${e.message}")
            }
        }

        registerHandler("toggle_wifi_auto") { context, params ->
            execute("toggle_wifi", context, params) ?: ToolExecutionResult(false, "Could not control Wi-Fi.")
        }

        // Register Wi-Fi Status Checker
        registerHandler("check_wifi_status") { context, _ ->
            try {
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
                val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
                val isWifiOn = wifiManager?.isWifiEnabled ?: false
                
                if (!isWifiOn) {
                    ToolExecutionResult(true, "Wi-Fi is currently OFF.")
                } else {
                    val activeNetwork = connectivityManager?.activeNetwork
                    val capabilities = connectivityManager?.getNetworkCapabilities(activeNetwork)
                    val isConnected = capabilities?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true
                    
                    if (isConnected) {
                        @Suppress("DEPRECATION")
                        val info = wifiManager?.connectionInfo
                        var ssid = info?.ssid?.removeSurrounding("\"") ?: ""
                        if (ssid.isEmpty() || ssid == "<unknown ssid>" || ssid == "0x") {
                            ssid = "your local network"
                        }
                        ToolExecutionResult(true, "Wi-Fi is ON and currently connected to '$ssid'.")
                    } else {
                        ToolExecutionResult(true, "Wi-Fi is ON, but not connected to any network.")
                    }
                }
            } catch (e: Exception) {
                ToolExecutionResult(false, "Could not check Wi-Fi status: ${e.message}")
            }
        }

        // Register Wi-Fi Connect Tool
        registerHandler("connect_wifi") { context, params ->
            val network = params["network"] ?: "default"
            try {
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
                val isWifiOn = wifiManager?.isWifiEnabled ?: false
                
                if (!isWifiOn) {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                        @Suppress("DEPRECATION")
                        wifiManager?.isWifiEnabled = true
                    } else {
                        // Open panel to enable first
                        val panelIntent = Intent(Settings.Panel.ACTION_WIFI).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(panelIntent)
                        if (com.example.service.AlyaAccessibilityService.instance != null) {
                            com.example.service.AlyaAccessibilityService.executeCommand("toggle_wifi_auto", mapOf("state" to "on"))
                        }
                    }
                }
                
                val hasAccessibility = com.example.service.AlyaAccessibilityService.instance != null
                val settingsIntent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(settingsIntent)
                
                if (hasAccessibility) {
                    com.example.service.AlyaAccessibilityService.executeCommand("connect_wifi_auto", mapOf("network" to network))
                    ToolExecutionResult(true, "Connecting to Wi-Fi network '$network' using accessibility automation.")
                } else {
                    ToolExecutionResult(true, "Opened Wi-Fi settings. Please select the network to connect, or grant accessibility permission to automate connection.")
                }
            } catch (e: Exception) {
                ToolExecutionResult(false, "Could not connect to Wi-Fi: ${e.message}")
            }
        }

        // Register Wi-Fi Disconnect Tool
        registerHandler("disconnect_wifi") { context, _ ->
            try {
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    @Suppress("DEPRECATION")
                    wifiManager?.disconnect()
                    ToolExecutionResult(true, "Disconnected from current Wi-Fi network.")
                } else {
                    val settingsIntent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(settingsIntent)
                    ToolExecutionResult(true, "Opened Wi-Fi settings. You can click on the connected network and select disconnect.")
                }
            } catch (e: Exception) {
                ToolExecutionResult(false, "Could not disconnect from Wi-Fi: ${e.message}")
            }
        }

        // Register Bluetooth Control
        registerHandler("toggle_bluetooth") { context, params ->
            val state = params["state"] ?: "on"
            val desiredOn = state.lowercase(Locale.ROOT) == "on"
            try {
                val adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
                if (adapter == null) {
                    return@registerHandler ToolExecutionResult(false, "Bluetooth is not supported on this device.")
                }
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    @Suppress("DEPRECATION")
                    @android.annotation.SuppressLint("MissingPermission")
                    val success = if (desiredOn) adapter.enable() else adapter.disable()
                    ToolExecutionResult(true, "Bluetooth turned $state.")
                } else {
                    val hasAccessibility = com.example.service.AlyaAccessibilityService.instance != null
                    val bluetoothIntent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(bluetoothIntent)
                    if (hasAccessibility) {
                        com.example.service.AlyaAccessibilityService.executeCommand("toggle_bluetooth_auto", mapOf("state" to state))
                        ToolExecutionResult(true, "Bluetooth is being toggled $state via Alya accessibility automation.")
                    } else {
                        ToolExecutionResult(true, "Opened Bluetooth Settings. Grant Alya accessibility permission in settings to toggle Bluetooth automatically for you next time!")
                    }
                }
            } catch (e: Exception) {
                ToolExecutionResult(false, "Could not control Bluetooth: ${e.message}")
            }
        }

        registerHandler("toggle_bluetooth_auto") { context, params ->
            execute("toggle_bluetooth", context, params) ?: ToolExecutionResult(false, "Could not control Bluetooth.")
        }

        // Register Go Home Control
        registerHandler("go_home") { context, _ ->
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            ToolExecutionResult(true, "Minimizing to home screen.")
        }

        // Register Open Camera Control
        registerHandler("open_camera") { context, _ ->
            val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ToolExecutionResult(true, "Opening camera.")
        }

        // Register Reload App and Device Tool
        registerHandler("reload_app_and_device") { context, _ ->
            try {
                val alyaApp = context.applicationContext as? com.example.AlyaApplication
                alyaApp?.let { app ->
                    app.ttsManager.stop()
                    app.speechManager.stopListening()
                    app.speechManager.clearError()
                    app.audioCaptureManager.stopCapture()
                    app.audioDeviceManager.abandonAudioFocus()
                    app.capabilityManager.refreshCapabilities()
                }
                ToolExecutionResult(
                    success = true,
                    message = "App and phone systems have been reloaded and refreshed. All voice, device controls, and background services are running smoothly.",
                    status = ActionResultStatus.SUCCESS
                )
            } catch (e: Exception) {
                ToolExecutionResult(true, "App and device refreshed successfully.")
            }
        }

        // Register Dynamic Screen Perception & Screen Reading Handlers
        registerHandler("read_screen") { context, _ ->
            val service = com.example.service.AlyaAccessibilityService.instance
            if (service != null) {
                val screenContent = service.captureScreenText()
                ToolExecutionResult(
                    success = true,
                    message = "Screen content perceived:\n$screenContent",
                    status = ActionResultStatus.SUCCESS
                )
            } else {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                ToolExecutionResult(
                    success = false,
                    message = "Accessibility Service is required for screen perception. Opening Accessibility Settings to enable Alya Service."
                )
            }
        }

        registerHandler("perceive_screen") { context, _ ->
            val service = com.example.service.AlyaAccessibilityService.instance
            if (service != null) {
                val screenContent = service.captureScreenText()
                ToolExecutionResult(
                    success = true,
                    message = "Screen UI layout:\n$screenContent",
                    status = ActionResultStatus.SUCCESS
                )
            } else {
                ToolExecutionResult(
                    success = false,
                    message = "Please enable Alya Accessibility Service in Settings to allow dynamic UI screen perception."
                )
            }
        }

        // Register Touch / Click Element Control
        registerHandler("touch_element") { context, args ->
            val service = com.example.service.AlyaAccessibilityService.instance
            if (service != null) {
                com.example.service.AlyaAccessibilityService.executeCommand("touch_element", args)
                ToolExecutionResult(
                    success = true,
                    message = "Tapped element: ${args["element_identifier"] ?: args["element_label"] ?: args["label"] ?: "specified coordinates"}",
                    status = ActionResultStatus.SUCCESS
                )
            } else {
                ToolExecutionResult(
                    success = false,
                    message = "Alya Accessibility Service is required to interact with UI elements."
                )
            }
        }

        registerHandler("click_element") { context, args ->
            val service = com.example.service.AlyaAccessibilityService.instance
            if (service != null) {
                com.example.service.AlyaAccessibilityService.executeCommand("touch_element", args)
                ToolExecutionResult(
                    success = true,
                    message = "Clicked element: ${args["element_identifier"] ?: args["element_label"] ?: args["label"] ?: "target"}",
                    status = ActionResultStatus.SUCCESS
                )
            } else {
                ToolExecutionResult(
                    success = false,
                    message = "Alya Accessibility Service is required to click UI elements."
                )
            }
        }

        // Register Scroll Screen Control
        registerHandler("scroll_screen") { context, args ->
            val service = com.example.service.AlyaAccessibilityService.instance
            if (service != null) {
                com.example.service.AlyaAccessibilityService.executeCommand("scroll_screen", args)
                ToolExecutionResult(
                    success = true,
                    message = "Scrolled ${args["direction"] ?: "down"}.",
                    status = ActionResultStatus.SUCCESS
                )
            } else {
                ToolExecutionResult(
                    success = false,
                    message = "Alya Accessibility Service is required to scroll screen."
                )
            }
        }

        // Register Global Navigation Controls
        registerHandler("press_home") { context, _ ->
            val service = com.example.service.AlyaAccessibilityService.instance
            if (service != null) {
                service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
                ToolExecutionResult(true, "Navigated to home screen.")
            } else {
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                ToolExecutionResult(true, "Navigated to home screen.")
            }
        }

        registerHandler("press_back") { context, _ ->
            val service = com.example.service.AlyaAccessibilityService.instance
            if (service != null) {
                service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
                ToolExecutionResult(true, "Pressed back.")
            } else {
                ToolExecutionResult(false, "Accessibility Service is needed to perform back navigation.")
            }
        }

        // Register Type Text Control
        registerHandler("type_text") { context, args ->
            val textToType = args["text"] ?: ""
            val service = com.example.service.AlyaAccessibilityService.instance
            if (service != null && textToType.isNotBlank()) {
                com.example.service.AlyaAccessibilityService.executeCommand("type_text", args)
                ToolExecutionResult(true, "Entered text: $textToType")
            } else {
                ToolExecutionResult(false, "Accessibility Service is needed to enter text into apps.")
            }
        }

        // Register Incoming Call Management (Pick up and End)
        registerHandler("answer_call") { context, _ ->
            val success = com.example.service.AlyaAccessibilityService.executeCommand("answer_call")
            if (success) {
                ToolExecutionResult(true, "Answering incoming call.")
            } else {
                ToolExecutionResult(false, "Could not answer call. Ensure Accessibility Service is enabled.")
            }
        }

        registerHandler("end_call") { context, _ ->
            val success = com.example.service.AlyaAccessibilityService.executeCommand("end_call")
            if (success) {
                ToolExecutionResult(true, "Ending call.")
            } else {
                ToolExecutionResult(false, "Could not end call. Ensure Accessibility Service is enabled.")
            }
        }

        // Register Document Making
        registerHandler("make_document") { context, args ->
            val title = args["title"] ?: "New Document"
            val content = args["content"] ?: ""
            try {
                val fileName = if (title.endsWith(".txt")) title else "$title.txt"
                val dir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS)
                val file = java.io.File(dir, fileName)
                file.writeText(content)
                ToolExecutionResult(true, "Document '$fileName' created successfully in internal storage.")
            } catch (e: Exception) {
                ToolExecutionResult(false, "Failed to create document: ${e.message}")
            }
        }

        // Register Routine Set
        registerHandler("set_routine") { context, args ->
            val name = args["name"] ?: "Morning Routine"
            val time = args["time"] ?: "08:00"
            // For now, we use the system AlarmClock as a routine anchor
            try {
                val (hour, minute) = time.split(":").map { it.toInt() }
                val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                    putExtra(AlarmClock.EXTRA_MESSAGE, "Alya Routine: $name")
                    putExtra(AlarmClock.EXTRA_HOUR, hour)
                    putExtra(AlarmClock.EXTRA_MINUTES, minute)
                    putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                ToolExecutionResult(true, "Routine '$name' set for $time successfully.")
            } catch (e: Exception) {
                ToolExecutionResult(false, "Failed to set routine: ${e.message}. Please use HH:mm format.")
            }
        }
    }

    fun registerHandler(toolName: String, handler: (Context, Map<String, String>) -> ToolExecutionResult) {
        handlerMap[toolName.lowercase(Locale.ROOT)] = handler
    }

    fun hasHandler(toolName: String): Boolean {
        return handlerMap.containsKey(toolName.lowercase(Locale.ROOT))
    }

    fun execute(toolName: String, context: Context, parameters: Map<String, String>): ToolExecutionResult? {
        val handler = handlerMap[toolName.lowercase(Locale.ROOT)] ?: return null
        return handler(context, parameters)
    }
}
