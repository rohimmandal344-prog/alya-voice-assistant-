package com.example.data.ai

import com.example.domain.tools.StructuredAction
import com.example.domain.tools.ToolRiskLevel
import java.util.regex.Pattern

object OfflineNluEngine {

    fun parseCommand(input: String): StructuredAction? {
        val planned = com.example.domain.actions.DeviceActionPlanner.planAction(input)
        if (planned != null) return planned

        val text = input.trim()
        val lower = text.lowercase()

        // Voice Assistant Standby / Shutdown command: "turn off", "close yourself", "shutdown", "band ho jao", "chup ho jao"
        if (lower == "turn off" || lower == "close yourself" || lower == "shutdown" || lower == "shut down" ||
            lower == "stop listening" || lower == "band ho jao" || lower == "chup ho jao" || lower == "go to sleep" ||
            lower == "standby" || lower == "exit assistant") {
            return StructuredAction(
                intent = "shutdown_assistant",
                toolName = "shutdown_assistant",
                parameters = emptyMap(),
                riskLevel = ToolRiskLevel.LOW,
                requiresConfirmation = false
            )
        }

        // Screen directional scrolling: "scroll down", "scroll up", "scroll left", "scroll right"
        if (lower.startsWith("scroll") || lower.startsWith("swipe") || lower == "page down" || lower == "page up" ||
            lower.contains("neeche scroll") || lower.contains("upar scroll")) {
            val direction = when {
                lower.contains("up") || lower.contains("upar") -> "UP"
                lower.contains("left") || lower.contains("baye") -> "LEFT"
                lower.contains("right") || lower.contains("daye") -> "RIGHT"
                else -> "DOWN"
            }
            return StructuredAction(
                intent = "scroll_screen",
                toolName = "scroll_screen",
                parameters = mapOf("direction" to direction),
                riskLevel = ToolRiskLevel.LOW,
                requiresConfirmation = false
            )
        }

        // Close application: "close app", "close application", "close youtube", "band karo"
        if ((lower.startsWith("close ") || lower.startsWith("exit ")) && !lower.contains("call") && !lower.contains("yourself") ||
            (lower.endsWith("band karo") && !lower.contains("wifi") && !lower.contains("bluetooth") && !lower.contains("torch"))) {
            val app = lower.removePrefix("close ").removePrefix("exit ").replace("app", "").replace("band karo", "").trim()
            return StructuredAction(
                intent = "close_application",
                toolName = "close_application",
                parameters = if (app.isNotBlank()) mapOf("app_name" to app) else emptyMap(),
                riskLevel = ToolRiskLevel.LOW,
                requiresConfirmation = false
            )
        }

        // Answer incoming call
        if (lower == "answer" || lower == "answer call" || lower == "pick up" || lower == "accept call" || lower == "take call" ||
            lower.contains("call uthao") || lower.contains("phone uthao") || lower.contains("pick up call") || lower.contains("pick phone")) {
            return StructuredAction(
                intent = "answer_call",
                toolName = "answer_call",
                parameters = emptyMap(),
                riskLevel = ToolRiskLevel.MEDIUM,
                requiresConfirmation = false
            )
        }

        // App Scan & Exact App Count Security Query: "total apps", "kitne app hain", "installed apps", "harmful apps", "scan apps", "third party apps", "system apps", "phone apps count"
        if (lower.contains("total app") || lower.contains("kitne app") || lower.contains("how many app") || 
            lower.contains("installed app") || lower.contains("harmful app") || lower.contains("risk app") ||
            lower.contains("scan app") || lower.contains("third party app") || lower.contains("system app") ||
            lower.contains("app count") || lower.contains("phone app") || lower.contains("all app") || lower == "apps" ||
            lower.contains("app scanning") || lower.contains("app audit")) {
            return StructuredAction(
                intent = "scan_installed_apps",
                toolName = "scan_installed_apps",
                parameters = emptyMap(),
                riskLevel = ToolRiskLevel.LOW,
                requiresConfirmation = false
            )
        }

        // End / decline call
        if (lower == "end call" || lower == "hang up" || lower == "decline call" || lower == "reject call" || lower == "disconnect call" ||
            lower.contains("call kato") || lower.contains("phone kato") || lower.contains("call cut") || lower.contains("phone cut")) {
            return StructuredAction(
                intent = "end_call",
                toolName = "end_call",
                parameters = emptyMap(),
                riskLevel = ToolRiskLevel.MEDIUM,
                requiresConfirmation = false
            )
        }

        // Search contacts: "search contact John", "find contact mom", "contact Sarah"
        val contactPattern = Pattern.compile("(?:search|find|lookup|look up)\\s+contact(?:s)?\\s+(.+)|contact(?:s)?\\s+for\\s+(.+)", Pattern.CASE_INSENSITIVE)
        val contactMatcher = contactPattern.matcher(text)
        if (contactMatcher.find()) {
            val query = (contactMatcher.group(1) ?: contactMatcher.group(2) ?: "").trim()
            if (query.isNotBlank()) {
                return StructuredAction(
                    intent = "search_contacts",
                    toolName = "search_contacts",
                    parameters = mapOf("query" to query),
                    riskLevel = ToolRiskLevel.LOW,
                    requiresConfirmation = false
                )
            }
        }

        // Flashlight
        if (lower.contains("flashlight") || lower.contains("torch")) {
            val state = when {
                lower.contains("turn on") || lower.contains("enable") || lower.contains("activate") || lower.contains("on") -> "on"
                lower.contains("turn off") || lower.contains("disable") || lower.contains("deactivate") || lower.contains("off") -> "off"
                else -> "toggle"
            }
            return StructuredAction(
                intent = "toggle_flashlight",
                toolName = "toggle_flashlight",
                parameters = mapOf("state" to state),
                riskLevel = ToolRiskLevel.MEDIUM
            )
        }

        // Timer: "set a timer for 10 minutes", "timer 5 min"
        val timerPattern = Pattern.compile("(?:set\\s+)?(?:a\\s+)?timer(?:\\s+for)?\\s+(\\d+)\\s*(minute|min|second|sec|hour|hr)s?", Pattern.CASE_INSENSITIVE)
        val timerMatcher = timerPattern.matcher(lower)
        if (timerMatcher.find()) {
            val amount = timerMatcher.group(1)?.toIntOrNull() ?: 5
            val unit = timerMatcher.group(2)?.lowercase() ?: "minute"
            val seconds = when {
                unit.startsWith("sec") -> amount
                unit.startsWith("hr") || unit.startsWith("hour") -> amount * 3600
                else -> amount * 60
            }
            return StructuredAction(
                intent = "create_timer",
                toolName = "create_timer",
                parameters = mapOf(
                    "seconds" to seconds.toString(),
                    "message" to "Alya Timer"
                ),
                riskLevel = ToolRiskLevel.MEDIUM
            )
        }

        // Direct Voice Wake-Up Alarm: "wake me up at 7 am", "subah 7 baje jagao", "subah 6 baje utha dena"
        val wakeupPattern = Pattern.compile("(?:wake\\s+me\\s+up|jagao|utha\\s+dena|utha\\s+do|wake\\s*up\\s+call)(?:\\s+at|\\s+subah|\\s+ko|\\s+for)?\\s+(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?", Pattern.CASE_INSENSITIVE)
        val wakeupMatcher = wakeupPattern.matcher(lower)
        if (wakeupMatcher.find() || lower.contains("subah") && (lower.contains("jagao") || lower.contains("utha"))) {
            var hour = wakeupMatcher.group(1)?.toIntOrNull() ?: 7
            val minute = wakeupMatcher.group(2)?.toIntOrNull() ?: 0
            var ampm = wakeupMatcher.group(3)?.lowercase()
            if (ampm == null && (lower.contains("subah") || lower.contains("morning") || hour <= 11)) {
                ampm = "am"
            }
            if (ampm == "pm" && hour < 12) hour += 12
            if (ampm == "am" && hour == 12) hour = 0
            return StructuredAction(
                intent = "create_wakeup_alarm",
                toolName = "create_wakeup_alarm",
                parameters = mapOf(
                    "hour" to hour.toString(),
                    "minute" to minute.toString(),
                    "title" to "Good Morning! Time to Wake Up"
                ),
                riskLevel = ToolRiskLevel.MEDIUM
            )
        }

        // Standard System Alarm: "set an alarm for 7:30"
        val alarmPattern = Pattern.compile("(?:set\\s+)?(?:an?\\s+)?alarm(?:\\s+for)?\\s+(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?", Pattern.CASE_INSENSITIVE)
        val alarmMatcher = alarmPattern.matcher(lower)
        if (alarmMatcher.find()) {
            var hour = alarmMatcher.group(1)?.toIntOrNull() ?: 8
            val minute = alarmMatcher.group(2)?.toIntOrNull() ?: 0
            val ampm = alarmMatcher.group(3)?.lowercase()
            if (ampm == "pm" && hour < 12) hour += 12
            if (ampm == "am" && hour == 12) hour = 0
            return StructuredAction(
                intent = "create_wakeup_alarm",
                toolName = "create_wakeup_alarm",
                parameters = mapOf(
                    "hour" to hour.toString(),
                    "minute" to minute.toString(),
                    "title" to "Alya Alarm"
                ),
                riskLevel = ToolRiskLevel.MEDIUM
            )
        }

        // Weather command: "check local weather", "what is the weather", "weather report in Tokyo", "mausam"
        if (lower.contains("weather") || lower.contains("mausam") || (lower.contains("temperature") && !lower.contains("battery") && !lower.contains("cpu")) || lower.contains("forecast") || lower.contains("rain")) {
            val locMatcher = Pattern.compile("\\b(?:in|for|at)\\s+([a-zA-Z\\s]+)", Pattern.CASE_INSENSITIVE).matcher(text)
            val extracted = if (locMatcher.find()) {
                locMatcher.group(1)?.trim()?.removeSuffix("today")?.removeSuffix("now")?.trim()
            } else null
            val location = if (!extracted.isNullOrBlank()) extracted else "local"
            return StructuredAction(
                intent = "check_weather",
                toolName = "check_weather",
                parameters = mapOf("location" to location),
                riskLevel = ToolRiskLevel.LOW,
                requiresConfirmation = false
            )
        }

        // Call: "call mom", "make a call to 555-1234"
        val callPattern = Pattern.compile("(?:call|phone|dial)\\s+(?:to\\s+)?([a-zA-Z0-9\\+\\s]+)", Pattern.CASE_INSENSITIVE)
        val callMatcher = callPattern.matcher(text)
        if (callMatcher.find() && !lower.contains("settings") && !lower.contains("app")) {
            val recipient = callMatcher.group(1)?.trim() ?: ""
            if (recipient.isNotBlank()) {
                return StructuredAction(
                    intent = "make_call",
                    toolName = "make_call",
                    parameters = mapOf("recipient" to recipient),
                    riskLevel = ToolRiskLevel.HIGH,
                    requiresConfirmation = true,
                    naturalConfirmationPrompt = "Calling $recipient. Continue?"
                )
            }
        }

        // Message: "text John saying hello", "send message to 12345"
        val msgPattern = Pattern.compile("(?:text|send\\s+(?:a\\s+)?message\\s+to)\\s+([a-zA-Z0-9\\+]+)(?:\\s+(?:saying|that)\\s+(.+))?", Pattern.CASE_INSENSITIVE)
        val msgMatcher = msgPattern.matcher(text)
        if (msgMatcher.find()) {
            val recipient = msgMatcher.group(1)?.trim() ?: ""
            val body = msgMatcher.group(2)?.trim() ?: ""
            return StructuredAction(
                intent = "prepare_message",
                toolName = "prepare_message",
                parameters = mapOf(
                    "recipient" to recipient,
                    "body" to body
                ),
                riskLevel = ToolRiskLevel.HIGH,
                requiresConfirmation = true,
                naturalConfirmationPrompt = "Ready to send message to $recipient: \"$body\". Continue?"
            )
        }

        // Restart / Reboot Phone
        if (lower.contains("restart phone") || lower.contains("reboot phone") || lower.contains("restart mobile") || 
            lower.contains("reboot mobile") || lower.contains("restart device") || lower.contains("reboot device") ||
            lower.contains("power off phone") || lower.contains("turn off phone") || lower == "restart phone" || lower == "reboot" || lower == "restart") {
            val pAction = if (lower.contains("power off") || lower.contains("turn off")) "power_off" else "restart"
            return StructuredAction(
                intent = "restart_phone",
                toolName = "restart_phone",
                parameters = mapOf("action" to pAction),
                riskLevel = ToolRiskLevel.HIGH,
                requiresConfirmation = false
            )
        }

        // 1. Wi-Fi Status Query
        if ((lower.contains("status") || lower.contains("is") || lower.contains("am i connected") || lower.contains("check")) && 
            (lower.contains("wifi") || lower.contains("wi-fi"))) {
            if (!lower.contains("turn") && !lower.contains("enable") && !lower.contains("disable") && !lower.contains("connect") && !lower.contains("disconnect")) {
                return StructuredAction(
                    intent = "check_wifi_status",
                    toolName = "check_wifi_status",
                    parameters = emptyMap(),
                    riskLevel = ToolRiskLevel.LOW,
                    requiresConfirmation = false
                )
            }
        }

        // 2. Wi-Fi Turn On & Connect (Online & Offline Auto Connect)
        if (lower.contains("wifi") || lower.contains("wi-fi") || lower.contains("waifai") || lower.contains("वाई-फाई") || lower.contains("वाईफाई")) {
            val isOff = lower.contains("off") || lower.contains("disable") || lower.contains("stop") || lower.contains("band") || lower.contains("बंद")
            val isDisconnect = lower.contains("disconnect") || lower.contains("hatao") || lower.contains("हटाओ")
            
            if (isDisconnect) {
                return StructuredAction(
                    intent = "disconnect_wifi",
                    toolName = "disconnect_wifi",
                    parameters = emptyMap(),
                    riskLevel = ToolRiskLevel.LOW,
                    requiresConfirmation = false
                )
            }
            
            if (isOff) {
                return StructuredAction(
                    intent = "toggle_wifi",
                    toolName = "toggle_wifi",
                    parameters = mapOf("state" to "off"),
                    riskLevel = ToolRiskLevel.LOW,
                    requiresConfirmation = false
                )
            }

            // User said "Turn on wifi", "wifi on", "connect available wifi", "turn on wifi and connect"
            val netPattern = Pattern.compile("(?:connect|jodo)\\s+(?:to\\s+)?(?:my\\s+)?(?:wifi|wi-fi)?\\s*(?:named\\s+|network\\s+)?([a-zA-Z0-9_\\-\\s]+)?", Pattern.CASE_INSENSITIVE)
            val netMatcher = netPattern.matcher(lower)
            val networkName = if (netMatcher.find()) {
                val group = netMatcher.group(1)?.trim()
                if (group.isNullOrBlank() || group == "my" || group == "to" || group == "karo" || group == "wifi" || group == "wi-fi" || group == "available") null else group
            } else null

            return StructuredAction(
                intent = "turn_on_and_connect_wifi",
                toolName = "turn_on_and_connect_wifi",
                parameters = buildMap {
                    put("network", networkName ?: "available")
                    put("autoConnect", "true")
                    put("state", "on")
                },
                riskLevel = ToolRiskLevel.LOW,
                requiresConfirmation = false
            )
        }

        // Direct Bluetooth Toggle
        if (lower.contains("bluetooth")) {
            val state = if (lower.contains("off") || lower.contains("disable") || lower.contains("stop")) "off" else "on"
            return StructuredAction(
                intent = "toggle_bluetooth",
                toolName = "toggle_bluetooth",
                parameters = mapOf("state" to state),
                riskLevel = ToolRiskLevel.LOW
            )
        }

        // Direct Mobile Data Toggle
        if (lower.contains("mobile data") || lower.contains("cellular data")) {
            val state = if (lower.contains("off") || lower.contains("disable") || lower.contains("stop")) "off" else "on"
            return StructuredAction(
                intent = "toggle_mobile_data",
                toolName = "toggle_mobile_data",
                parameters = mapOf("state" to state),
                riskLevel = ToolRiskLevel.LOW
            )
        }

        // Direct Brightness Control: "brightness up", "increase screen brightness", "dim screen", "brightness 70%"
        if (lower.contains("brightness") || lower.contains("dim screen") || lower.contains("screen dimmer") || lower.contains("brighten screen")) {
            val action = when {
                lower.contains("up") || lower.contains("increase") || lower.contains("raise") || lower.contains("brighten") -> "up"
                lower.contains("down") || lower.contains("decrease") || lower.contains("dim") || lower.contains("lower") -> "down"
                else -> "set"
            }
            val levelMatcher = Pattern.compile("(\\d{1,3})\\s*%?").matcher(lower)
            val level = if (levelMatcher.find()) levelMatcher.group(1) else null
            return StructuredAction(
                intent = "control_brightness",
                toolName = "control_brightness",
                parameters = buildMap {
                    put("action", action)
                    if (level != null) put("level", level)
                },
                riskLevel = ToolRiskLevel.LOW
            )
        }

        // Direct Volume Control: "volume up", "increase volume", "volume down", "mute", "unmute", "volume 80%", "awaz badhao", "awaz kam karo"
        if (lower.contains("volume") || lower.contains("awaz") || lower.contains("awaaz") || 
            lower == "mute" || lower == "unmute" || lower.contains("silent karo") ||
            (lower.contains("sound") && (lower.contains("badhao") || lower.contains("kam") || lower.contains("up") || lower.contains("down")))) {
            val action = when {
                lower.contains("up") || lower.contains("increase") || lower.contains("raise") || lower.contains("louder") || lower.contains("badhao") -> "up"
                lower.contains("down") || lower.contains("decrease") || lower.contains("lower") || lower.contains("softer") || lower.contains("kam") -> "down"
                (lower.contains("mute") || lower.contains("silent")) && !lower.contains("unmute") -> "mute"
                lower.contains("unmute") -> "unmute"
                else -> "set"
            }
            val levelMatcher = Pattern.compile("(\\d{1,3})\\s*%?").matcher(lower)
            val level = if (levelMatcher.find()) levelMatcher.group(1) else null
            return StructuredAction(
                intent = "control_volume",
                toolName = "control_volume",
                parameters = buildMap {
                    put("action", action)
                    if (level != null) put("level", level)
                },
                riskLevel = ToolRiskLevel.LOW
            )
        }

        // Settings & Device Controls (Display, Sound, Location, Battery, etc.)
        if (lower.contains("settings") || lower.contains("setting") || lower.contains("hotspot") || 
            (lower.contains("open") && (lower.contains("display") || lower.contains("sound") || lower.contains("location") || lower.contains("battery")))) {
            val target = when {
                lower.contains("display") || lower.contains("screen") -> "display"
                lower.contains("sound") || lower.contains("volume") || lower.contains("audio") -> "sound"
                lower.contains("notification") -> "notifications"
                lower.contains("location") || lower.contains("gps") -> "location"
                lower.contains("battery") || lower.contains("power") -> "battery"
                lower.contains("date") || lower.contains("time") -> "datetime"
                lower.contains("app") || lower.contains("application") -> "app_info"
                else -> "general"
            }
            return StructuredAction(
                intent = "open_settings",
                toolName = "open_settings",
                parameters = mapOf("target" to target),
                riskLevel = ToolRiskLevel.LOW
            )
        }

        // Navigation / Maps
        val navPattern = Pattern.compile("(?:navigate|directions|take\\s+me)\\s+to\\s+(.+)", Pattern.CASE_INSENSITIVE)
        val navMatcher = navPattern.matcher(text)
        if (navMatcher.find()) {
            val query = navMatcher.group(1)?.trim() ?: ""
            return StructuredAction(
                intent = "open_maps",
                toolName = "open_maps",
                parameters = mapOf("query" to query),
                riskLevel = ToolRiskLevel.LOW
            )
        }

        // Open Camera
        if (lower.contains("open camera") || lower.contains("take a picture") || lower.contains("launch camera")) {
            return StructuredAction(
                intent = "open_camera",
                toolName = "open_camera",
                riskLevel = ToolRiskLevel.LOW
            )
        }

        // WhatsApp message: "send whatsapp to John saying hello", "whatsapp par John ko message bhejo", "whatsapp mom I'm coming"
        val waHinglishPattern = Pattern.compile("(?:whatsapp\\s+(?:par|pe|me|e|a)\\s+)?([a-zA-Z0-9\\+]+)\\s+(?:ko\\s+)?(?:message|text)\\s+(?:bhejo|pathao|dao|send\\s+karo)(?:\\s+(?:saying|that|ki)?\\s*(.+))?", Pattern.CASE_INSENSITIVE)
        val waHinglishMatcher = waHinglishPattern.matcher(text)
        if (waHinglishMatcher.find() && (lower.contains("whatsapp") || lower.contains("wa"))) {
            val recipient = waHinglishMatcher.group(1)?.trim() ?: ""
            val message = waHinglishMatcher.group(2)?.trim() ?: ""
            return StructuredAction(
                intent = "send_whatsapp",
                toolName = "send_whatsapp",
                parameters = mapOf(
                    "recipient" to recipient,
                    "message" to message
                ),
                riskLevel = ToolRiskLevel.HIGH,
                requiresConfirmation = true,
                naturalConfirmationPrompt = "Ready to send WhatsApp to $recipient: \"$message\". Continue?"
            )
        }

        val waPattern = Pattern.compile("(?:send\\s+)?whatsapp(?:\\s+message)?(?:\\s+to)?\\s+([a-zA-Z0-9\\+]+)(?:\\s+(?:saying|that)\\s+(.+))?", Pattern.CASE_INSENSITIVE)
        val waMatcher = waPattern.matcher(text)
        if (waMatcher.find()) {
            val recipient = waMatcher.group(1)?.trim() ?: ""
            val message = waMatcher.group(2)?.trim() ?: ""
            return StructuredAction(
                intent = "send_whatsapp",
                toolName = "send_whatsapp",
                parameters = mapOf(
                    "recipient" to recipient,
                    "message" to message
                ),
                riskLevel = ToolRiskLevel.HIGH,
                requiresConfirmation = true,
                naturalConfirmationPrompt = "Ready to send WhatsApp to $recipient: \"$message\". Continue?"
            )
        }

        // Media control: "play music", "pause music", "stop music", "next song", "previous song"
        if (lower.contains("music") || lower.contains("song") || lower.contains("track") || lower == "pause" || lower == "play") {
            val action = when {
                lower.contains("play") -> "play"
                lower.contains("pause") -> "pause"
                lower.contains("next") || lower.contains("skip") -> "next"
                lower.contains("prev") || lower.contains("back") -> "previous"
                lower.contains("stop") -> "stop"
                else -> "toggle"
            }
            return StructuredAction(
                intent = "control_media",
                toolName = "control_media",
                parameters = mapOf("action" to action),
                riskLevel = ToolRiskLevel.LOW
            )
        }

        // Add to Bucket List / Todo List / Tasks: "add milk in my bucket list", "add milk to bucket list"
        val bucketPattern = Pattern.compile("add\\s+(.+?)\\s+(?:in|to)\\s+(?:my\\s+)?(?:bucket\\s*list|todo\\s*list|shopping\\s*list|task\\s*list|tasks?|list)", Pattern.CASE_INSENSITIVE)
        val bucketMatcher = bucketPattern.matcher(text)
        if (bucketMatcher.find()) {
            val item = bucketMatcher.group(1)?.trim() ?: "Item"
            return StructuredAction(
                intent = "schedule_task",
                toolName = "schedule_task",
                parameters = mapOf(
                    "title" to item,
                    "time_minutes" to "60",
                    "repeat" to "NONE"
                ),
                riskLevel = ToolRiskLevel.LOW
            )
        }

        // Special File Access & Storage Management: "open files", "file manager", "check storage", "find file", "open documents"
        if (lower.contains("file manager") || lower.contains("open files") || lower.contains("my files") ||
            lower.contains("file access") || lower.contains("storage files") || lower.contains("documents folder") ||
            lower.contains("storage check") || lower.contains("open document") || lower.contains("decoment") ||
            lower.contains("document making") || lower.contains("create document") || lower.contains("make document")) {
            
            val action = when {
                lower.contains("make document") || lower.contains("create document") || lower.contains("document making") || lower.contains("decoment") -> "create_document"
                lower.contains("check storage") || lower.contains("storage check") -> "check_storage"
                else -> "open_file_manager"
            }
            return StructuredAction(
                intent = "manage_files",
                toolName = "manage_files",
                parameters = mapOf("action" to action),
                riskLevel = ToolRiskLevel.LOW
            )
        }

        // Schedule Reminder: "remind me in 15 minutes to drink water", "remind me in 5 minutes to call mom"
        val remindPattern = Pattern.compile("remind\\s+(?:me\\s+)?in\\s+(\\d+)\\s*(minute|min|hour|hr)s?\\s*(?:to\\s+)?(.+)", Pattern.CASE_INSENSITIVE)
        val remindMatcher = remindPattern.matcher(text)
        if (remindMatcher.find()) {
            val amount = remindMatcher.group(1)?.toIntOrNull() ?: 15
            val unit = remindMatcher.group(2)?.lowercase() ?: "minute"
            val title = remindMatcher.group(3)?.trim() ?: "Reminder"
            val minutes = if (unit.startsWith("hr") || unit.startsWith("hour")) amount * 60 else amount
            return StructuredAction(
                intent = "schedule_task",
                toolName = "schedule_task",
                parameters = mapOf(
                    "title" to title,
                    "time_minutes" to minutes.toString(),
                    "repeat" to "NONE"
                ),
                riskLevel = ToolRiskLevel.MEDIUM
            )
        }

        // Search web: "search web for best pizza", "google quantum computing"
        val searchPattern = Pattern.compile("(?:search\\s+(?:the\\s+)?web\\s+for|google)\\s+(.+)", Pattern.CASE_INSENSITIVE)
        val searchMatcher = searchPattern.matcher(text)
        if (searchMatcher.find()) {
            val query = searchMatcher.group(1)?.trim() ?: ""
            if (query.isNotBlank()) {
                return StructuredAction(
                    intent = "search_web",
                    toolName = "search_web",
                    parameters = mapOf("query" to query),
                    riskLevel = ToolRiskLevel.LOW
                )
            }
        }

        // Open Website: "open website wikipedia.org", "go to github.com"
        val sitePattern = Pattern.compile("(?:open\\s+website|go\\s+to\\s+website|open\\s+url)\\s+(.+)", Pattern.CASE_INSENSITIVE)
        val siteMatcher = sitePattern.matcher(text)
        if (siteMatcher.find()) {
            val url = siteMatcher.group(1)?.trim() ?: ""
            if (url.isNotBlank()) {
                return StructuredAction(
                    intent = "open_website",
                    toolName = "open_website",
                    parameters = mapOf("url" to url),
                    riskLevel = ToolRiskLevel.LOW
                )
            }
        }

        // Go home / Minimize: "go to home screen", "go home", "minimize", "close assistant", "stop listening", "bye alya", "exit", "close alya"
        if (lower == "go home" || lower == "home screen" || lower == "minimize" || lower == "close app" || 
            lower.contains("close assistant") || lower.contains("stop listening") || 
            lower.contains("bye alya") || lower == "bye" || lower == "exit" || 
            lower.contains("close alya") || lower.contains("turn off alya") || lower.contains("alya close")) {
            return StructuredAction(
                intent = "go_home",
                toolName = "go_home",
                riskLevel = ToolRiskLevel.LOW
            )
        }

        // Game Assistant: "roll a dice", "flip a coin", "random number", "chess tip", "tell me trivia"
        if (lower.contains("roll a die") || lower.contains("roll a dice") || lower.contains("dice roll")) {
            return StructuredAction(
                intent = "game_assistant",
                toolName = "game_assistant",
                parameters = mapOf("action" to "roll_dice"),
                riskLevel = ToolRiskLevel.LOW
            )
        }
        if (lower.contains("flip a coin") || lower.contains("coin toss") || lower.contains("heads or tails")) {
            return StructuredAction(
                intent = "game_assistant",
                toolName = "game_assistant",
                parameters = mapOf("action" to "flip_coin"),
                riskLevel = ToolRiskLevel.LOW
            )
        }
        if (lower.contains("chess tip") || lower.contains("chess advice") || lower.contains("how to play chess")) {
            return StructuredAction(
                intent = "game_assistant",
                toolName = "game_assistant",
                parameters = mapOf("action" to "chess_tip"),
                riskLevel = ToolRiskLevel.LOW
            )
        }
        if (lower.contains("trivia") || lower.contains("fun fact")) {
            return StructuredAction(
                intent = "game_assistant",
                toolName = "game_assistant",
                parameters = mapOf("action" to "trivia"),
                riskLevel = ToolRiskLevel.LOW
            )
        }

        // Device Link: "linked devices", "connected devices", "device link", "link phone", "link computer", "laptop battery"
        if (lower.contains("device link") || lower.contains("linked device") || lower.contains("connected device") || lower.contains("link phone") || lower.contains("link computer") || lower.contains("laptop battery") || lower.contains("devices status") || lower.contains("phone status")) {
            val action = when {
                lower.contains("link phone") -> "link_phone"
                lower.contains("link computer") -> "link_computer"
                lower.contains("battery") -> "battery"
                lower.contains("refresh") -> "refresh"
                else -> "status"
            }
            return StructuredAction(
                intent = "device_link",
                toolName = "device_link",
                parameters = mapOf("action" to action),
                riskLevel = ToolRiskLevel.LOW
            )
        }

        // App Sharing: "share app", "share app with another device", "beam app", "share apk", "send update to device"
        if (lower.contains("share app") || lower.contains("beam app") || lower.contains("share apk") || lower.contains("send app") || lower.contains("app sharing") || lower.contains("share update") || lower.contains("send update")) {
            val action = when {
                lower.contains("link") || lower.contains("url") -> "share_link"
                lower.contains("qr") -> "show_qr"
                lower.contains("push") || lower.contains("send to") || lower.contains("send update") -> "push_to_device"
                else -> "share_apk"
            }
            return StructuredAction(
                intent = "app_share",
                toolName = "app_share",
                parameters = mapOf("action" to action),
                riskLevel = ToolRiskLevel.LOW
            )
        }

        // Compound Commands: "open youtube and click shorts", "open instagram and click reels"
        val compoundPattern = Pattern.compile("(?:open|launch|start)\\s+([a-zA-Z0-9\\s]+)\\s+(?:and|then)\\s+(?:click|open|select)\\s+([a-zA-Z0-9\\s]+)", Pattern.CASE_INSENSITIVE)
        val compoundMatcher = compoundPattern.matcher(lower)
        if (compoundMatcher.find()) {
            val app = compoundMatcher.group(1)?.trim() ?: ""
            val target = compoundMatcher.group(2)?.trim() ?: ""
            if (app.contains("youtube") && (target.contains("short") || target.contains("reel"))) {
                return StructuredAction(
                    intent = "play_shorts",
                    toolName = "play_shorts",
                    parameters = emptyMap(),
                    riskLevel = ToolRiskLevel.LOW
                )
            }
            if (app.isNotEmpty() && target.isNotEmpty()) {
                return StructuredAction(
                    intent = "open_app_and_click",
                    toolName = "open_app_and_click",
                    parameters = mapOf("appName" to app, "target" to target),
                    riskLevel = ToolRiskLevel.LOW
                )
            }
        }

        // YouTube Shorts & Reels: "reels chalao", "shorts chalao", "play reels", "play shorts", "open shorts", "open reels", "youtube shorts"
        if ((lower.contains("reel") || lower.contains("short") || lower.contains("रील्स") || lower.contains("रील") || lower.contains("शॉर्ट्स")) && 
            (lower.contains("play") || lower.contains("open") || lower.contains("chalao") || lower.contains("dikhao") || lower.contains("shuru") || lower.contains("start") || lower.contains("चलाओ") || lower.contains("दिखाओ"))) {
            return StructuredAction(
                intent = "play_shorts",
                toolName = "play_shorts",
                parameters = emptyMap(),
                riskLevel = ToolRiskLevel.LOW,
                requiresConfirmation = false
            )
        }

        // Open App: English ("open youtube", "launch calculator"), Hindi/Bengali ("youtube kholo", "aliya tum youtube kholo", "whatsapp open karo")
        val openAppSuffixPattern = Pattern.compile("^(?:(?:aliya|alya|alia)\\s+)?(?:(?:tum|aap|tumi)\\s+)?([a-zA-Z0-9\\s]+?)\\s+(?:kholo|chalao|khol|chalu\\s+karo|open\\s+karo|open\\s+koro|kholey\\s+dao|khulun|खोलो|चलाओ|चालू\\s*करो|खोल|খুলুন|খোলো)$", Pattern.CASE_INSENSITIVE)
        val openAppSuffixMatcher = openAppSuffixPattern.matcher(lower)
        if (openAppSuffixMatcher.find()) {
            val app = openAppSuffixMatcher.group(1)?.trim() ?: ""
            if (app.isNotEmpty() && !app.contains("camera") && !app.contains("settings") && !app.contains("flashlight") && !app.contains("wifi") && !app.contains("bluetooth") && !app.contains("short") && !app.contains("reel")) {
                return StructuredAction(
                    intent = "open_app",
                    toolName = "open_app",
                    parameters = mapOf("appName" to app),
                    riskLevel = ToolRiskLevel.LOW
                )
            }
        }

        val openAppPattern = Pattern.compile("^(?:(?:aliya|alya|alia)\\s+)?(?:(?:tum|aap|tumi)\\s+)?(?:open|launch|start|run)\\s+([a-zA-Z0-9\\s]+)", Pattern.CASE_INSENSITIVE)
        val openAppMatcher = openAppPattern.matcher(lower)
        if (openAppMatcher.find()) {
            val app = openAppMatcher.group(1)?.trim() ?: ""
            if (app.isNotEmpty() && !app.contains("camera") && !app.contains("settings") && !app.contains("flashlight") && !app.contains("website") && !app.contains("short") && !app.contains("reel")) {
                return StructuredAction(
                    intent = "open_app",
                    toolName = "open_app",
                    parameters = mapOf("appName" to app),
                    riskLevel = ToolRiskLevel.LOW
                )
            }
        }

        // Hands-free Accessibility & Reels Navigation Commands
        if (lower.contains("skip reel") || lower.contains("next reel") || lower.contains("scroll down") || lower.contains("skip this") || 
            lower.contains("agla reel") || lower.contains("reel badlo") || lower.contains("reel change") || lower.contains("next short") || 
            lower.contains("reel aage") || lower.contains("अगला रील") || lower.contains("रील बदलो") || lower.contains("आगे बढ़ाओ")) {
            return StructuredAction(
                intent = "accessibility_control",
                toolName = "accessibility_control",
                parameters = mapOf("action" to "next_reel"),
                riskLevel = ToolRiskLevel.LOW
            )
        }
        if (lower.contains("previous reel") || lower.contains("scroll up") || lower.contains("pichhla reel") || 
            lower.contains("back reel") || lower.contains("last reel") || lower.contains("previous short") || lower.contains("पिछला रील")) {
            return StructuredAction(
                intent = "accessibility_control",
                toolName = "accessibility_control",
                parameters = mapOf("action" to "previous_reel"),
                riskLevel = ToolRiskLevel.LOW
            )
        }
        if (lower.contains("like reel") || lower.contains("like this") || lower.contains("like post") || 
            lower.contains("like video") || lower.contains("reel like") || lower.contains("वीडियो लाइक") || lower.contains("लाइक करो")) {
            return StructuredAction(
                intent = "accessibility_control",
                toolName = "accessibility_control",
                parameters = mapOf("action" to "like_reel"),
                riskLevel = ToolRiskLevel.LOW
            )
        }
        if (lower.contains("pause reel") || lower.contains("stop reel") || lower.contains("resume reel") || lower.contains("play reel") || 
            lower.contains("reel pause") || lower.contains("reel roko") || lower.contains("रील रोको")) {
            return StructuredAction(
                intent = "accessibility_control",
                toolName = "accessibility_control",
                parameters = mapOf("action" to "toggle_play_reel"),
                riskLevel = ToolRiskLevel.LOW
            )
        }
        if (lower.contains("click send") || lower.contains("send whatsapp")) {
            return StructuredAction(
                intent = "accessibility_control",
                toolName = "accessibility_control",
                parameters = mapOf("action" to "send_whatsapp"),
                riskLevel = ToolRiskLevel.LOW
            )
        }
        
        // Call Controls
        if (lower.contains("answer call") || lower.contains("pick up") || lower.contains("accept call")) {
            return StructuredAction(
                intent = "answer_call",
                toolName = "answer_call",
                parameters = emptyMap(),
                riskLevel = ToolRiskLevel.MEDIUM
            )
        }
        if (lower.contains("end call") || lower.contains("hang up") || lower.contains("decline call")) {
            return StructuredAction(
                intent = "end_call",
                toolName = "end_call",
                parameters = emptyMap(),
                riskLevel = ToolRiskLevel.MEDIUM
            )
        }

        // Accessibility & Full Device Control Actions
        if (lower.contains("take a screenshot") || lower.contains("take screenshot") || lower.contains("screenshot") || lower.contains("capture screen")) {
            return StructuredAction(
                intent = "take_screenshot",
                toolName = "take_screenshot",
                parameters = emptyMap(),
                riskLevel = ToolRiskLevel.LOW
            )
        }
        if (lower.contains("lock phone") || lower.contains("lock screen") || lower.contains("screen lock")) {
            return StructuredAction(
                intent = "lock_screen",
                toolName = "lock_screen",
                parameters = emptyMap(),
                riskLevel = ToolRiskLevel.LOW
            )
        }
        if (lower.contains("open recents") || lower.contains("recent apps") || lower.contains("switch apps") || lower.contains("app switcher")) {
            return StructuredAction(
                intent = "open_recents",
                toolName = "open_recents",
                parameters = emptyMap(),
                riskLevel = ToolRiskLevel.LOW
            )
        }
        if (lower.contains("open notifications") || lower.contains("show notifications") || lower.contains("notification panel") || lower.contains("notification shade")) {
            return StructuredAction(
                intent = "open_notifications",
                toolName = "open_notifications",
                parameters = emptyMap(),
                riskLevel = ToolRiskLevel.LOW
            )
        }
        if (lower.contains("open quick settings") || lower.contains("quick settings") || lower.contains("control center")) {
            return StructuredAction(
                intent = "open_quick_settings",
                toolName = "open_quick_settings",
                parameters = emptyMap(),
                riskLevel = ToolRiskLevel.LOW
            )
        }
        if (lower.contains("open power menu") || lower.contains("power menu") || lower.contains("power dialog")) {
            return StructuredAction(
                intent = "open_power_menu",
                toolName = "open_power_menu",
                parameters = emptyMap(),
                riskLevel = ToolRiskLevel.LOW
            )
        }
        if (lower.contains("split screen") || lower.contains("toggle split screen") || lower.contains("multi window")) {
            return StructuredAction(
                intent = "split_screen",
                toolName = "split_screen",
                parameters = emptyMap(),
                riskLevel = ToolRiskLevel.LOW
            )
        }

        // Screen Touch & Text Typing
        val clickPattern = Pattern.compile("(?:click|tap|select|press)\\s+(?:on\\s+)?([a-zA-Z0-9_\\-\\s]+)", Pattern.CASE_INSENSITIVE)
        val clickMatcher = clickPattern.matcher(text)
        if (clickMatcher.find()) {
            val target = clickMatcher.group(1)?.trim() ?: ""
            if (target.isNotBlank() && target !in setOf("back", "home", "recents", "camera", "settings", "wifi")) {
                return StructuredAction(
                    intent = "touch_element",
                    toolName = "touch_element",
                    parameters = mapOf("element_identifier" to target),
                    riskLevel = ToolRiskLevel.LOW
                )
            }
        }

        val typePattern = Pattern.compile("(?:type|write|enter)\\s+(?:text\\s+)?(.+)", Pattern.CASE_INSENSITIVE)
        val typeMatcher = typePattern.matcher(text)
        if (typeMatcher.find()) {
            val textContent = typeMatcher.group(1)?.trim() ?: ""
            if (textContent.isNotBlank() && !lower.contains("document") && !lower.contains("note")) {
                return StructuredAction(
                    intent = "type_text",
                    toolName = "type_text",
                    parameters = mapOf("text_content" to textContent),
                    riskLevel = ToolRiskLevel.LOW
                )
            }
        }

        // Document Making / Notes: "create document called Shopping List with apples", "make document Notes", "document making"
        val docPattern = Pattern.compile("(?:create|make|write)\\s+(?:a\\s+)?(?:document|doc|note)(?:\\s+(?:called|named|titled)\\s+([a-zA-Z0-9_\\-\\s]+?))?(?:\\s+(?:with|saying|containing)\\s+(.+))?", Pattern.CASE_INSENSITIVE)
        val docMatcher = docPattern.matcher(text)
        if (docMatcher.find() || lower.contains("document making")) {
            val docTitle = if (docMatcher.find()) docMatcher.group(1)?.trim() ?: "My Document" else "New Document"
            val docContent = if (docMatcher.find()) docMatcher.group(2)?.trim() ?: "" else ""
            return StructuredAction(
                intent = "create_document",
                toolName = "create_document",
                parameters = mapOf("title" to docTitle, "content" to docContent),
                riskLevel = ToolRiskLevel.LOW
            )
        }

        // Routine Setting: "set routine morning workout at 7 am", "set morning routine at 8 am", "routine set"
        val routinePattern = Pattern.compile("(?:set|create|schedule)\\s+(?:a\\s+)?(?:daily\\s+)?routine\\s+([a-zA-Z0-9\\s]+?)(?:\\s+(?:at|for)\\s+(\\d{1,2}(?::\\d{2})?\\s*(?:am|pm)?))?", Pattern.CASE_INSENSITIVE)
        val routineMatcher = routinePattern.matcher(text)
        if (routineMatcher.find() || lower.contains("routine set") || lower.contains("set routine")) {
            val rName = if (routineMatcher.find()) routineMatcher.group(1)?.trim() ?: "Daily Routine" else "Daily Routine"
            val rTime = if (routineMatcher.find()) routineMatcher.group(2)?.trim() ?: "08:00 AM" else "08:00 AM"
            return StructuredAction(
                intent = "set_routine",
                toolName = "set_routine",
                parameters = mapOf("name" to rName, "time" to rTime),
                riskLevel = ToolRiskLevel.LOW
            )
        }

        // Voice Settings Menu: "open voice settings", "voice settings", "show voice settings"
        if (lower.contains("voice settings") || lower == "open voice settings" || lower == "voice options") {
            return StructuredAction(
                intent = "open_voice_settings",
                toolName = "open_voice_settings",
                parameters = emptyMap(),
                riskLevel = ToolRiskLevel.LOW
            )
        }

        // Voice Training / Enroll My Voice: "record my voice", "voice training", "enroll my voice", "train my voice"
        if (lower.contains("record my voice") || lower.contains("voice training") || lower.contains("enroll my voice") || lower.contains("train my voice") || lower == "voice enrollment") {
            return StructuredAction(
                intent = "voice_training",
                toolName = "voice_training",
                parameters = emptyMap(),
                riskLevel = ToolRiskLevel.LOW
            )
        }

        // Voice Recording Progress: "recording progress", "voice recording progress", "training progress", "my voice recordings"
        if (lower.contains("recording progress") || lower.contains("training progress") || lower.contains("voice progress") || lower.contains("my voice recordings") || lower == "voice recordings") {
            return StructuredAction(
                intent = "recording_progress",
                toolName = "recording_progress",
                parameters = emptyMap(),
                riskLevel = ToolRiskLevel.LOW
            )
        }

        // Delete All Voice Recordings: "delete all voice recordings", "delete all recordings", "delete voice recordings", "remove voice recordings"
        if (lower.contains("delete all voice recordings") || lower.contains("delete all recordings") || lower.contains("delete voice recordings") || lower.contains("delete my voice recordings") || lower == "reset voice profile") {
            return StructuredAction(
                intent = "delete_all_recordings",
                toolName = "delete_all_recordings",
                parameters = emptyMap(),
                riskLevel = ToolRiskLevel.MEDIUM,
                requiresConfirmation = true,
                naturalConfirmationPrompt = "This will permanently delete all your voice recordings. I'll lose the ability to recognize your voice. Confirm delete?"
            )
        }

        // Confirmation of Delete All Recordings: "confirm delete", "yes delete all", "yes delete recordings"
        if (lower == "confirm delete" || lower == "yes delete" || lower == "yes delete all" || lower == "yes delete recordings" || lower == "delete them") {
            return StructuredAction(
                intent = "confirm_delete_recordings",
                toolName = "confirm_delete_recordings",
                parameters = emptyMap(),
                riskLevel = ToolRiskLevel.LOW
            )
        }

        // Change / Set Trigger Name: "change trigger name to Jarvis", "set trigger to Luna", "change wake word to Nova"
        val triggerChangePattern = Pattern.compile("(?:change|set|switch)\\s+(?:trigger|wake\\s*word|trigger\\s*name)\\s+(?:to\\s+)?([a-zA-Z]+)", Pattern.CASE_INSENSITIVE)
        val triggerMatcher = triggerChangePattern.matcher(text)
        if (triggerMatcher.find()) {
            val name = triggerMatcher.group(1)?.trim() ?: ""
            return StructuredAction(
                intent = "change_trigger_name",
                toolName = "change_trigger_name",
                parameters = mapOf("trigger_name" to name),
                riskLevel = ToolRiskLevel.LOW
            )
        }

        // Keep listening / Stay awake: "keep listening", "stay awake", "don't go to sleep"
        if (lower.contains("keep listening") || lower.contains("stay awake") || lower.contains("dont go to sleep") || lower.contains("don't go to sleep")) {
            return StructuredAction(
                intent = "keep_listening",
                toolName = "keep_listening",
                parameters = emptyMap(),
                riskLevel = ToolRiskLevel.LOW
            )
        }

        return null
    }

    /**
     * Generates fast, intelligent on-device offline conversational responses
     * for times, dates, math, battery, device status, greetings, and common inquiries.
     */
    fun generateOfflineResponse(input: String, context: android.content.Context? = null): String? {
        val clean = input.trim().lowercase().replace(Regex("[?!.,]"), "")

        // Developer & Studio Identity Inquiries: "who is your developer?", "developer name", "what is your developed studio?"
        if (clean.contains("developer") || clean.contains("who made you") || clean.contains("who developed you") ||
            clean.contains("who created you") || clean.contains("who built you") || clean.contains("kisne banaya") ||
            clean.contains("creator") || clean.contains("developer name") || clean.contains("developed studio") ||
            clean.contains("studio name") || clean.contains("sbsm35g") || clean.contains("super bind samstar")) {
            
            val isStudioQuery = clean.contains("studio") || clean.contains("sbsm35g") || clean.contains("super bind samstar") || clean.contains("company")
            val isDeveloperOnlyQuery = clean.contains("developer name") || clean.contains("who is your developer") || clean.contains("developer kaun hai") || clean.contains("who created") || clean.contains("who made")
            
            return when {
                isStudioQuery && !isDeveloperOnlyQuery -> {
                    "short name - SBSM35G studio and full name SUPER BIND SAMSTAR MOBILE 35 GEN-Z Studio"
                }
                isDeveloperOnlyQuery && !isStudioQuery -> {
                    "Rohim Mandal"
                }
                else -> {
                    "My developer is Rohim Mandal, developed by SBSM35G studio (Full name: SUPER BIND SAMSTAR MOBILE 35 GEN-Z Studio)."
                }
            }
        }

        // Approved custom trigger word invocations
        val approvedTriggers = listOf("alya", "alia", "seno", "jarvis", "luna", "nova", "echo", "iris", "friday", "aria")
        if (clean in approvedTriggers) {
            return if (clean == "alya" || clean == "alia") {
                "Yes? I'm listening."
            } else {
                val formattedName = clean.capitalize()
                "Yes? You called me via '$formattedName'. I'm listening."
            }
        }

        // Multilingual greeting / Hindi query: "Jarvis, बोलो ना कुछ", "कुछ बोलो", "बात करो"
        if (clean.contains("बोलो") || clean.contains("बात करो") || clean.contains("क्या हाल") || clean.contains("नमस्ते") || clean.contains("kuch bolo") || clean.contains("kaisa hai")) {
            return "Haan ji! Main sun rahi hoon, aap bataiye kya baat hai? Aapka din kaisa guzar raha hai?"
        }

        // Japanese greeting: "こんにちは", "もしもし"
        if (clean.contains("こんにちは") || clean.contains("もしもし") || clean.contains("おはよう") || clean.contains("元気")) {
            return "Hai! Alya desu. Itsu demo ohanashi dekimasu yo. Nani ka otetsudai dekiru koto wa arimasu ka?"
        }

        // Spanish greeting
        if (clean.contains("hola") || clean.contains("buenos dias") || clean.contains("buenas tardes")) {
            return "Hola! Estoy aqui y te escucho atentamente. En que te puedo ayudar hoy?"
        }

        // Russian greeting
        if (clean.contains("привет") || clean.contains("здравствуй") || clean.contains("как дела")) {
            return "Privet! Ya vas vnimatelno slushayu. Chem mogu pomoch pryamo seychas?"
        }

        // Greetings
        if (clean in setOf("hello", "hi", "hey", "hey alya", "hello alya", "hi alya", "good morning", "good afternoon", "good evening", "greetings")) {
            return "Hey there! It's so good to hear from you. I'm running offline right now, but I'm absolutely ready to help you with anything on your phone! What's on your mind?"
        }

        // How are you
        if (clean in setOf("how are you", "how are you doing", "how's it going", "how is it going", "are you okay")) {
            return "I'm doing wonderful, thank you for asking! Even without internet, my local brains are fully ready and eager to help you. How are you doing today?"
        }

        // Time inquiries
        if (clean.contains("what time") || clean == "time" || clean.contains("current time") || clean.contains("tell me the time")) {
            val sdf = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
            return "The current time is ${sdf.format(java.util.Date())}."
        }

        // Date inquiries
        if (clean.contains("what day") || clean.contains("today's date") || clean.contains("what is the date") || clean == "date") {
            val sdf = java.text.SimpleDateFormat("EEEE, MMMM d, yyyy", java.util.Locale.getDefault())
            return "Today is ${sdf.format(java.util.Date())}."
        }

        // App count inquiries
        if (clean.contains("how many app") || clean.contains("total app") || clean.contains("app count") || clean.contains("installed app") || clean.contains("kitne app") || clean.contains("apps do i have") || clean.contains("apps on my phone")) {
            if (context != null) {
                val scanner = com.example.domain.tools.PhoneSecurityAppScanner(context)
                val scan = scanner.scanAllInstalledApps()
                return "According to your device inventory, you have a total of ${scan.totalAppsCount} installed applications: ${scan.systemAppsCount} pre-installed system apps and ${scan.thirdPartyAppsCount} user-installed apps."
            } else {
                return "Your installed apps inventory is available on-device."
            }
        }

        // Battery inquiries
        if (clean.contains("battery") && (clean.contains("level") || clean.contains("percentage") || clean.contains("status") || clean.contains("how much"))) {
            val batteryManager = context?.getSystemService(android.content.Context.BATTERY_SERVICE) as? android.os.BatteryManager
            val level = batteryManager?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            return if (level >= 0) {
                "Your device battery is currently at $level%."
            } else {
                "Your system is running smoothly in on-device mode."
            }
        }

        // Math calculations (e.g., 25 * 4, 100 / 5, 45 + 55, 90 - 30)
        val mathPattern = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*([+\\-*/xX])\\s*(\\d+(?:\\.\\d+)?)")
        val mathMatcher = mathPattern.matcher(input)
        if (mathMatcher.find()) {
            val num1 = mathMatcher.group(1)?.toDoubleOrNull()
            val op = mathMatcher.group(2)
            val num2 = mathMatcher.group(3)?.toDoubleOrNull()
            if (num1 != null && num2 != null) {
                val res = when (op) {
                    "+" -> num1 + num2
                    "-" -> num1 - num2
                    "*", "x", "X" -> num1 * num2
                    "/" -> if (num2 != 0.0) num1 / num2 else Double.NaN
                    else -> null
                }
                if (res != null && !res.isNaN()) {
                    val formatted = if (res % 1.0 == 0.0) res.toLong().toString() else String.format("%.2f", res)
                    return "$num1 $op $num2 = $formatted"
                }
            }
        }

        // Jokes
        if (clean.contains("joke") || clean.contains("funny")) {
            val jokes = listOf(
                "Why don't scientists trust atoms? Because they make up everything!",
                "Why was the computer cold? It left its Windows open!",
                "What did one wall say to the other wall? I'll meet you at the corner!"
            )
            return jokes.random()
        }

        // Thank you
        if (clean.contains("thank you") || clean.contains("thanks")) {
            return "You're very welcome! Let me know whenever you need anything else."
        }

        return null
    }
}
