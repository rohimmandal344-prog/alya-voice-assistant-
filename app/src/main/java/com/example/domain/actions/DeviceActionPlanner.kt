package com.example.domain.actions

import com.example.domain.tools.StructuredAction
import com.example.domain.tools.ToolRiskLevel
import java.util.Locale
import java.util.regex.Pattern

/**
 * Decoupled Intent Detection and Action Planner.
 * Separates user natural language understanding from Android execution.
 */
object DeviceActionPlanner {

    private val commonStandaloneApps = setOf(
        "free fire", "freefire", "free fire max", "garena free fire", "ff",
        "youtube", "yt", "you tube",
        "whatsapp", "wa", "whatsap", "watsapp", "whats app",
        "instagram", "insta", "ig",
        "facebook", "fb",
        "chrome", "google chrome", "browser",
        "camera", "cam",
        "calculator", "calc",
        "gallery", "photos", "photo", "google photos",
        "maps", "map", "google maps",
        "spotify", "music",
        "twitter", "x",
        "snapchat", "snap",
        "telegram", "tg",
        "netflix", "gmail", "drive",
        "play store", "google play", "store",
        "dialer", "phone", "clock", "messages", "sms"
    )

    /**
     * Analyzes raw user command and produces a planned StructuredAction if a device control intent is detected.
     */
    fun planAction(rawInput: String): StructuredAction? {
        val trimmed = rawInput.trim()
        val lower = trimmed.lowercase(Locale.ROOT)
        if (lower.isBlank()) return null

        // 1. Settings Intents (Higher precedence than generic app or connectivity toggle)
        // e.g. "open wifi settings", "wi-fi settings", "open bluetooth settings", "open notification settings"
        if (isSettingsIntent(lower)) {
            val target = detectSettingsTarget(lower)
            return StructuredAction(
                intent = "open_settings",
                toolName = "open_settings",
                parameters = mapOf("target" to target),
                riskLevel = ToolRiskLevel.LOW
            )
        }

        // 1.5 Indirect Intents: Natural casual speech mapping to device controls
        // "Bohot andhera hai" -> Flashlight ON
        if (lower.contains("andhera") || lower.contains("dark") || lower.contains("dikh nahi raha") || lower.contains("dekh nahi pa raha")) {
            return StructuredAction(
                intent = "toggle_flashlight",
                toolName = "toggle_flashlight",
                parameters = mapOf("state" to "on"),
                riskLevel = ToolRiskLevel.LOW
            )
        }

        // "Photo kheenchani hai" -> Open Camera App
        if ((lower.contains("photo") || lower.contains("tasveer") || lower.contains("pic") || lower.contains("selfie")) &&
            (lower.contains("kheench") || lower.contains("khich") || lower.contains("lena") || lower.contains("leni") || lower.contains("click") || lower.contains("take") || lower.contains("shoot") || lower.contains("capture"))) {
            return StructuredAction(
                intent = "launch_app",
                toolName = "launch_app",
                parameters = mapOf("app_name" to "Camera", "appName" to "Camera"),
                riskLevel = ToolRiskLevel.LOW
            )
        }

        // "Net nahi chal raha" -> Toggle Wi-Fi ON
        if ((lower.contains("net") || lower.contains("internet") || lower.contains("data") || lower.contains("wifi") || lower.contains("wi-fi")) &&
            (lower.contains("nahi chal") || lower.contains("nahi chal raha") || lower.contains("nahi aa raha") || lower.contains("not working") || lower.contains("no internet") || lower.contains("chalu nahi hai"))) {
            return StructuredAction(
                intent = "turn_on_and_connect_wifi",
                toolName = "turn_on_and_connect_wifi",
                parameters = mapOf("network" to "available", "autoConnect" to "true", "state" to "on"),
                riskLevel = ToolRiskLevel.LOW
            )
        }

        // 2. Volume Control (English, Hindi, Hinglish)
        if (lower.contains("volume") || lower.contains("awaz") || lower.contains("awaaz") || lower.contains("aawaz") || 
            lower == "mute" || lower == "unmute" || lower.contains("silent karo") || 
            (lower.contains("sound") && (lower.contains("badhao") || lower.contains("kam") || lower.contains("up") || lower.contains("down")))) {
            val action = when {
                lower.contains("up") || lower.contains("increase") || lower.contains("raise") || 
                lower.contains("louder") || lower.contains("badhao") || lower.contains("jyada") || lower.contains("badao") || lower.contains("tez") -> "up"
                lower.contains("down") || lower.contains("decrease") || lower.contains("lower") || 
                lower.contains("softer") || lower.contains("kam") || lower.contains("ghatao") || lower.contains("dheere") -> "down"
                (lower.contains("mute") || lower.contains("silent")) && !lower.contains("unmute") -> "mute"
                lower.contains("unmute") -> "unmute"
                else -> "set"
            }
            val levelMatcher = Pattern.compile("(\\d{1,3})\\s*%?").matcher(lower)
            val level = if (levelMatcher.find()) levelMatcher.group(1) else null
            val value = when {
                level != null -> level
                action == "up" -> "+20"
                action == "down" -> "-20"
                else -> "50"
            }
            return StructuredAction(
                intent = "control_volume",
                toolName = "control_volume",
                parameters = buildMap {
                    put("action", action)
                    put("value", value)
                    if (level != null) put("level", level)
                },
                riskLevel = ToolRiskLevel.LOW
            )
        }

        // 3. Screen Brightness Control
        if (lower.contains("brightness") || lower.contains("dim screen") || lower.contains("screen dimmer") || lower.contains("brighten screen")) {
            val action = when {
                lower.contains("up") || lower.contains("increase") || lower.contains("raise") || lower.contains("brighten") || lower.contains("badhao") || lower.contains("tez") -> "up"
                lower.contains("down") || lower.contains("decrease") || lower.contains("dim") || lower.contains("lower") || lower.contains("kam") || lower.contains("ghatao") -> "down"
                else -> "set"
            }
            val levelMatcher = Pattern.compile("(\\d{1,3})\\s*%?").matcher(lower)
            val level = if (levelMatcher.find()) levelMatcher.group(1) else null
            val value = when {
                level != null -> level
                action == "up" -> "+20"
                action == "down" -> "-20"
                else -> "50"
            }
            return StructuredAction(
                intent = "control_brightness",
                toolName = "control_brightness",
                parameters = buildMap {
                    put("action", action)
                    put("value", value)
                    if (level != null) put("level", level)
                },
                riskLevel = ToolRiskLevel.LOW
            )
        }

        // 4. Flashlight / Torch Intent
        // e.g. "turn on flashlight", "turn off torch", "flashlight", "torch on", "torch jalao", "torch band karo"
        if (lower.contains("flashlight") || lower.contains("torch")) {
            val state = when {
                lower.contains("turn on") || lower.contains("on") || lower.contains("enable") || lower.contains("activate") || lower.contains("chalao") || lower.contains("jalao") || lower.contains("chalu") -> "on"
                lower.contains("turn off") || lower.contains("off") || lower.contains("disable") || lower.contains("deactivate") || lower.contains("band") || lower.contains("bujhao") -> "off"
                else -> "toggle"
            }
            return StructuredAction(
                intent = "toggle_flashlight",
                toolName = "toggle_flashlight",
                parameters = mapOf("state" to state),
                riskLevel = ToolRiskLevel.LOW
            )
        }

        // 5. Wi-Fi Control & Auto-Connect Intents
        if (isWifiIntent(lower)) {
            if (lower.contains("status") || lower.contains("is connected") || lower.contains("check")) {
                return StructuredAction(
                    intent = "check_wifi_status",
                    toolName = "check_wifi_status",
                    riskLevel = ToolRiskLevel.LOW
                )
            }
            if (lower.contains("disconnect") || lower.contains("hatao")) {
                return StructuredAction(
                    intent = "disconnect_wifi",
                    toolName = "disconnect_wifi",
                    riskLevel = ToolRiskLevel.LOW
                )
            }
            val isOff = lower.contains("off") || lower.contains("disable") || lower.contains("band")
            if (isOff) {
                return StructuredAction(
                    intent = "toggle_wifi",
                    toolName = "toggle_wifi",
                    parameters = mapOf("state" to "off"),
                    riskLevel = ToolRiskLevel.LOW
                )
            }
            // User saying: "Turn on wifi", "connect available wifi", "wifi chalu karo aur connect karo"
            return StructuredAction(
                intent = "turn_on_and_connect_wifi",
                toolName = "turn_on_and_connect_wifi",
                parameters = mapOf("network" to "available", "autoConnect" to "true", "state" to "on"),
                riskLevel = ToolRiskLevel.LOW
            )
        }

        // 6. Bluetooth Control Intents
        if (lower.contains("bluetooth") && !lower.contains("setting")) {
            val state = if (lower.contains("off") || lower.contains("disable") || lower.contains("band")) "off" else "on"
            return StructuredAction(
                intent = "toggle_bluetooth",
                toolName = "toggle_bluetooth",
                parameters = mapOf("state" to state),
                riskLevel = ToolRiskLevel.LOW
            )
        }

        // 7. Mobile Data Control Intents
        if (lower.contains("mobile data") || lower.contains("cellular data")) {
            val state = if (lower.contains("off") || lower.contains("disable") || lower.contains("band")) "off" else "on"
            return StructuredAction(
                intent = "toggle_mobile_data",
                toolName = "toggle_mobile_data",
                parameters = mapOf("state" to state),
                riskLevel = ToolRiskLevel.LOW
            )
        }

        // 6. Natural App Launch Patterns:
        // "open Free Fire", "start Free Fire", "launch Free Fire", "go to WhatsApp", "kholo youtube"
        val openPrefixPattern = Pattern.compile(
            "^(?:please\\s+)?(?:can\\s+you\\s+)?(?:open|launch|start|run|go\\s+to|switch\\s+to|kholo|chalao)\\s+([a-zA-Z0-9\\s\\.\\-_]+?)(?:\\s+(?:app|application|now|please))?$",
            Pattern.CASE_INSENSITIVE
        )
        val openPrefixMatcher = openPrefixPattern.matcher(trimmed)
        if (openPrefixMatcher.find()) {
            val appCandidate = openPrefixMatcher.group(1)?.trim() ?: ""
            if (appCandidate.isNotBlank() && !isNonAppKeyword(appCandidate.lowercase(Locale.ROOT))) {
                return StructuredAction(
                    intent = "open_app",
                    toolName = "open_app",
                    parameters = mapOf("appName" to appCandidate),
                    riskLevel = ToolRiskLevel.LOW
                )
            }
        }

        // 7. Standalone App Command Matching
        // When user simply utters the app name: e.g. "Free Fire", "YouTube", "WhatsApp", "Chrome", etc.
        val cleanDirect = lower.replace(Regex("[?!.,]"), "").trim()
        if (commonStandaloneApps.contains(cleanDirect)) {
            return StructuredAction(
                intent = "open_app",
                toolName = "open_app",
                parameters = mapOf("appName" to trimmed),
                riskLevel = ToolRiskLevel.LOW
            )
        }

        // 8. Call Management: Answer, End, or Make Call
        if (cleanDirect in setOf("answer", "answer call", "pick up", "accept call", "take call", "phone uthao")) {
            return StructuredAction(
                intent = "answer_call",
                toolName = "answer_call",
                riskLevel = ToolRiskLevel.MEDIUM
            )
        }
        if (cleanDirect in setOf("end call", "hang up", "decline call", "reject call", "disconnect call", "cut call")) {
            return StructuredAction(
                intent = "end_call",
                toolName = "end_call",
                riskLevel = ToolRiskLevel.MEDIUM
            )
        }
        val callPattern = Pattern.compile("^(?:call|phone|dial)\\s+(?:to\\s+)?([a-zA-Z0-9\\+\\s]+)$", Pattern.CASE_INSENSITIVE)
        val callMatcher = callPattern.matcher(trimmed)
        if (callMatcher.find()) {
            val recipient = callMatcher.group(1)?.trim() ?: ""
            if (recipient.isNotBlank() && !recipient.equals("settings", ignoreCase = true)) {
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

        // 9. Camera Shortcut
        if (cleanDirect in setOf("open camera", "launch camera", "camera", "take photo", "take a picture")) {
            return StructuredAction(
                intent = "open_camera",
                toolName = "open_camera",
                riskLevel = ToolRiskLevel.LOW
            )
        }

        // 10. Alarms & Timers
        val timerPattern = Pattern.compile("^(?:set\\s+)?(?:a\\s+)?timer(?:\\s+for)?\\s+(\\d+)\\s*(minute|min|second|sec|hour|hr)s?$", Pattern.CASE_INSENSITIVE)
        val timerMatcher = timerPattern.matcher(lower)
        if (timerMatcher.find()) {
            val amount = timerMatcher.group(1)?.toIntOrNull() ?: 5
            val unit = timerMatcher.group(2)?.lowercase(Locale.ROOT) ?: "minute"
            val seconds = when {
                unit.startsWith("sec") -> amount
                unit.startsWith("hr") || unit.startsWith("hour") -> amount * 3600
                else -> amount * 60
            }
            return StructuredAction(
                intent = "create_timer",
                toolName = "create_timer",
                parameters = mapOf("seconds" to seconds.toString(), "message" to "Alya Timer"),
                riskLevel = ToolRiskLevel.MEDIUM
            )
        }

        val alarmPattern = Pattern.compile("^(?:set\\s+)?(?:an?\\s+)?alarm(?:\\s+for)?\\s+(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?$", Pattern.CASE_INSENSITIVE)
        val alarmMatcher = alarmPattern.matcher(lower)
        if (alarmMatcher.find()) {
            var hour = alarmMatcher.group(1)?.toIntOrNull() ?: 8
            val minute = alarmMatcher.group(2)?.toIntOrNull() ?: 0
            val ampm = alarmMatcher.group(3)?.lowercase(Locale.ROOT)
            if (ampm == "pm" && hour < 12) hour += 12
            if (ampm == "am" && hour == 12) hour = 0
            return StructuredAction(
                intent = "create_wakeup_alarm",
                toolName = "create_wakeup_alarm",
                parameters = mapOf("hour" to hour.toString(), "minute" to minute.toString(), "title" to "Alya Alarm"),
                riskLevel = ToolRiskLevel.MEDIUM
            )
        }

        // 11. Navigate Home / Minimize
        if (cleanDirect in setOf("go home", "go to home", "home screen", "minimize", "exit", "close alya", "close app")) {
            return StructuredAction(
                intent = "go_home",
                toolName = "go_home",
                riskLevel = ToolRiskLevel.LOW
            )
        }

        // 12. Accessibility System Controls (Screenshot, Lock, Recents, Notifications, Quick Settings, Power Menu, Split Screen)
        if (cleanDirect in setOf("take screenshot", "take a screenshot", "screenshot", "capture screen", "screen capture")) {
            return StructuredAction(
                intent = "take_screenshot",
                toolName = "take_screenshot",
                riskLevel = ToolRiskLevel.LOW
            )
        }
        if (cleanDirect in setOf("lock phone", "lock screen", "screen lock", "lock the phone")) {
            return StructuredAction(
                intent = "lock_screen",
                toolName = "lock_screen",
                riskLevel = ToolRiskLevel.LOW
            )
        }
        if (cleanDirect in setOf("open recents", "recent apps", "show recents", "switch apps", "app switcher")) {
            return StructuredAction(
                intent = "open_recents",
                toolName = "open_recents",
                riskLevel = ToolRiskLevel.LOW
            )
        }
        if (cleanDirect in setOf("open notifications", "show notifications", "notification shade", "pull down notifications", "notification panel")) {
            return StructuredAction(
                intent = "open_notifications",
                toolName = "open_notifications",
                riskLevel = ToolRiskLevel.LOW
            )
        }
        if (cleanDirect in setOf("open quick settings", "quick settings", "control center", "toggle panel")) {
            return StructuredAction(
                intent = "open_quick_settings",
                toolName = "open_quick_settings",
                riskLevel = ToolRiskLevel.LOW
            )
        }
        if (cleanDirect in setOf("open power menu", "power menu", "power dialog", "power options")) {
            return StructuredAction(
                intent = "open_power_menu",
                toolName = "open_power_menu",
                riskLevel = ToolRiskLevel.LOW
            )
        }
        if (cleanDirect in setOf("split screen", "toggle split screen", "multi window")) {
            return StructuredAction(
                intent = "split_screen",
                toolName = "split_screen",
                riskLevel = ToolRiskLevel.LOW
            )
        }

        // 13. Screen Navigation (Scroll & Touch)
        if (lower.contains("scroll down") || lower.contains("swipe down") || lower.contains("page down")) {
            return StructuredAction(
                intent = "scroll_screen",
                toolName = "scroll_screen",
                parameters = mapOf("direction" to "DOWN", "distance" to "SHORT"),
                riskLevel = ToolRiskLevel.LOW
            )
        }
        if (lower.contains("scroll up") || lower.contains("swipe up") || lower.contains("page up")) {
            return StructuredAction(
                intent = "scroll_screen",
                toolName = "scroll_screen",
                parameters = mapOf("direction" to "UP", "distance" to "SHORT"),
                riskLevel = ToolRiskLevel.LOW
            )
        }

        val tapPattern = Pattern.compile("^(?:click|tap|select|press)\\s+(?:on\\s+)?(.+)$", Pattern.CASE_INSENSITIVE)
        val tapMatcher = tapPattern.matcher(cleanDirect)
        if (tapMatcher.find()) {
            val target = tapMatcher.group(1)?.trim() ?: ""
            if (target.isNotBlank() && target !in setOf("back", "home", "recents", "camera", "settings")) {
                return StructuredAction(
                    intent = "touch_element",
                    toolName = "touch_element",
                    parameters = mapOf("element_identifier" to target),
                    riskLevel = ToolRiskLevel.LOW
                )
            }
        }

        val typePattern = Pattern.compile("^(?:type|write|enter)\\s+(?:text\\s+)?(.+)$", Pattern.CASE_INSENSITIVE)
        val typeMatcher = typePattern.matcher(cleanDirect)
        if (typeMatcher.find()) {
            val textContent = typeMatcher.group(1)?.trim() ?: ""
            if (textContent.isNotBlank()) {
                return StructuredAction(
                    intent = "type_text",
                    toolName = "type_text",
                    parameters = mapOf("text_content" to textContent),
                    riskLevel = ToolRiskLevel.LOW
                )
            }
        }

        // 14. Document Creation: "create document called Notes with content meeting notes", "make document Notes", "document making"
        val docPattern = Pattern.compile("^(?:create|make|write)\\s+(?:a\\s+)?(?:document|doc|note)(?:\\s+(?:called|named|titled)\\s+([a-zA-Z0-9_\\-\\s]+?))?(?:\\s+(?:with|saying|containing)\\s+(.+))?$", Pattern.CASE_INSENSITIVE)
        val docMatcher = docPattern.matcher(cleanDirect)
        if (docMatcher.find()) {
            val docTitle = docMatcher.group(1)?.trim() ?: "My Document"
            val docContent = docMatcher.group(2)?.trim() ?: ""
            return StructuredAction(
                intent = "create_document",
                toolName = "create_document",
                parameters = mapOf("title" to docTitle, "content" to docContent),
                riskLevel = ToolRiskLevel.LOW
            )
        }
        if (lower.contains("document making") || lower.contains("make document") || lower.contains("create document")) {
            return StructuredAction(
                intent = "create_document",
                toolName = "create_document",
                parameters = mapOf("title" to "New Document", "content" to ""),
                riskLevel = ToolRiskLevel.LOW
            )
        }

        // 15. Routine Scheduling: "set routine morning workout at 7 am", "set morning routine at 8 am", "routine set"
        val routinePattern = Pattern.compile("^(?:set|create|schedule)\\s+(?:a\\s+)?(?:daily\\s+)?routine\\s+([a-zA-Z0-9\\s]+?)(?:\\s+(?:at|for)\\s+(\\d{1,2}(?::\\d{2})?\\s*(?:am|pm)?))?$", Pattern.CASE_INSENSITIVE)
        val routineMatcher = routinePattern.matcher(cleanDirect)
        if (routineMatcher.find()) {
            val rName = routineMatcher.group(1)?.trim() ?: "Daily Routine"
            val rTime = routineMatcher.group(2)?.trim() ?: "08:00 AM"
            return StructuredAction(
                intent = "set_routine",
                toolName = "set_routine",
                parameters = mapOf("name" to rName, "time" to rTime),
                riskLevel = ToolRiskLevel.LOW
            )
        }
        if (lower.contains("routine set") || lower.contains("set routine")) {
            return StructuredAction(
                intent = "set_routine",
                toolName = "set_routine",
                parameters = mapOf("name" to "Daily Routine", "time" to "08:00 AM"),
                riskLevel = ToolRiskLevel.LOW
            )
        }

        // 16. Weather
        if (lower.contains("weather") || lower.contains("forecast") || lower.contains("mausam")) {
            val loc = if (lower.contains(" in ")) lower.substringAfter(" in ").trim() else "local"
            return StructuredAction(
                intent = "check_weather",
                toolName = "check_weather",
                parameters = mapOf("location" to loc),
                riskLevel = ToolRiskLevel.LOW
            )
        }

        // 17. Reload App and Phone / Reset System / Clear Lag (Multilingual: English, Hindi, Bengali, Hinglish, Banglish)
        // e.g. "reload app", "reload phone", "app reload karo", "phone reload karo", "app refresh karo", "phone refresh karo", "reset state", "fix lag", "clear lag", "system reload", "restart assistant"
        if (lower in setOf("reload", "reload app", "reload phone", "refresh", "refresh app", "phone reload", "app reload",
                "app ko reload karo", "phone ko reload karo", "app reload karo", "phone reload karo",
                "app refresh karo", "phone refresh karo", "reset state", "fix lag", "clear lag",
                "system reload", "restart assistant", "restart app", "lag thik karo", "reload device", "refresh device",
                "app ar phone reload koro", "reload koro", "refresh koro") ||
            (lower.contains("reload") && (lower.contains("app") || lower.contains("phone") || lower.contains("device") || lower.contains("system"))) ||
            (lower.contains("refresh") && (lower.contains("app") || lower.contains("phone") || lower.contains("device") || lower.contains("system"))) ||
            (lower.contains("atke hue") || lower.contains("lag ho raha") || lower.contains("freeze ho gaya"))
        ) {
            return StructuredAction(
                intent = "reload_app_and_device",
                toolName = "reload_app_and_device",
                parameters = emptyMap(),
                riskLevel = ToolRiskLevel.LOW
            )
        }

        // 18. Dynamic UI & Screen Perception (Multilingual: Hindi, Bengali, Hinglish, Banglish, English)
        // e.g. "screen dekho", "read screen", "look at screen", "screen pe kya hai", "screen pora", "read the screen", "perceive screen"
        if (lower.contains("screen dekho") || lower.contains("read screen") || lower.contains("look at screen") ||
            lower.contains("what's on my screen") || lower.contains("screen pe kya hai") || lower.contains("screen pora") ||
            lower.contains("read the screen") || lower.contains("screen text") || lower.contains("perceive screen") || lower.contains("see screen")
        ) {
            return StructuredAction(
                intent = "read_screen",
                toolName = "read_screen",
                parameters = emptyMap(),
                riskLevel = ToolRiskLevel.LOW
            )
        }

        if ((lower.contains("click") || lower.contains("tap") || lower.contains("press")) &&
            (lower.contains("button") || lower.contains("option") || lower.contains("link") || lower.contains("on screen") || lower.contains("screen pe"))
        ) {
            val targetLabel = lower.replace(Regex("(click|tap|press|button|option|link|on screen|screen pe)"), "").trim()
            return StructuredAction(
                intent = "touch_element",
                toolName = "touch_element",
                parameters = mapOf("element_identifier" to targetLabel),
                riskLevel = ToolRiskLevel.LOW
            )
        }

        if (lower.contains("scroll down") || lower.contains("niche scroll") || lower.contains("scroll up") || lower.contains("upar scroll")) {
            val dir = if (lower.contains("up") || lower.contains("upar")) "UP" else "DOWN"
            return StructuredAction(
                intent = "scroll_screen",
                toolName = "scroll_screen",
                parameters = mapOf("direction" to dir),
                riskLevel = ToolRiskLevel.LOW
            )
        }

        return null
    }

    private fun isSettingsIntent(lower: String): Boolean {
        if (lower.contains("volume") || lower.contains("awaz") || lower.contains("awaaz") || 
            lower.contains("brightness") || lower.contains("torch") || lower.contains("flashlight")) {
            if (!lower.contains("setting")) return false
        }
        return lower.contains("setting") ||
               (lower.contains("open") && (lower.contains("wifi") || lower.contains("wi-fi") || lower.contains("bluetooth") || lower.contains("notification") || lower.contains("display") || lower.contains("battery")) && lower.contains("setting")) ||
               lower.startsWith("wifi setting") || lower.startsWith("bluetooth setting") || lower.startsWith("notification setting")
    }

    private fun detectSettingsTarget(lower: String): String {
        return when {
            lower.contains("wifi") || lower.contains("wi-fi") || lower.contains("internet") || lower.contains("network") -> "wifi"
            lower.contains("bluetooth") || lower.contains("bt") -> "bluetooth"
            lower.contains("notification") || lower.contains("notif") -> "notifications"
            lower.contains("display") || lower.contains("screen") || lower.contains("brightness") -> "display"
            lower.contains("sound") || lower.contains("volume") || lower.contains("audio") -> "sound"
            lower.contains("location") || lower.contains("gps") -> "location"
            lower.contains("battery") || lower.contains("power") -> "battery"
            lower.contains("datetime") || lower.contains("date") || lower.contains("time") -> "datetime"
            lower.contains("app info") || lower.contains("application details") -> "app_info"
            else -> "general"
        }
    }

    private fun isWifiIntent(lower: String): Boolean {
        return (lower.contains("wifi") || lower.contains("wi-fi") || lower.contains("waifai")) &&
               !lower.contains("setting")
    }

    private fun isNonAppKeyword(keyword: String): Boolean {
        return keyword in setOf(
            "settings", "setting", "wifi", "wi-fi", "bluetooth", "torch", "flashlight",
            "alarm", "timer", "home", "home screen", "website", "url"
        )
    }
}
