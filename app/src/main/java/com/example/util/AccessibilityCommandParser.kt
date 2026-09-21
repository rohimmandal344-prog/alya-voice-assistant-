package com.example.util

import com.example.service.AlyaAccessibilityService

object AccessibilityCommandParser {

    /**
     * Attempts to parse a spoken command into a direct accessibility action.
     * Returns true if a command was handled, false otherwise.
     */
    fun tryHandleDirectCommand(spokenText: String): Boolean {
        val lower = spokenText.lowercase().trim()
        
        // 1. Navigation & Basic Actions
        if (lower == "go back" || lower == "back" || lower == "piche jao" || lower == "piche" || lower == "wapas" || lower == "wapas jao") {
            return AlyaAccessibilityService.executeCommand("go_back")
        }
        if (lower == "go home" || lower == "home" || lower == "home screen" || lower == "ghar" || lower == "home screen pe jao") {
            return AlyaAccessibilityService.executeCommand("go_home")
        }
        if (lower == "open recents" || lower == "recents" || lower == "recent apps" || lower == "abhi ke apps dikhao" || lower == "recents kholo") {
            return AlyaAccessibilityService.executeCommand("open_recents")
        }
        if (lower == "open notifications" || lower == "notifications" || lower == "notifications dikhao" || lower == "notifications kholo") {
            return AlyaAccessibilityService.executeCommand("open_notifications")
        }
        if (lower == "open quick settings" || lower == "quick settings" || lower == "settings kholo") {
            return AlyaAccessibilityService.executeCommand("open_quick_settings")
        }
        if (lower == "open power menu" || lower == "power menu" || lower == "switch off menu") {
            return AlyaAccessibilityService.executeCommand("open_power_menu")
        }
        
        // 2. Scrolling
        if (lower.contains("scroll down") || lower.contains("niche scroll karo") || lower.contains("niche jao")) {
            return AlyaAccessibilityService.executeCommand("scroll", mapOf("direction" to "down"))
        }
        if (lower.contains("scroll up") || lower.contains("upar scroll karo") || lower.contains("upar jao")) {
            return AlyaAccessibilityService.executeCommand("scroll", mapOf("direction" to "up"))
        }
        
        // 3. Magnification (Zoom)
        if (lower == "zoom in" || lower == "bada karo" || lower == "zoom karo" || lower == "magnify") {
            return AlyaAccessibilityService.executeCommand("zoom_in")
        }
        if (lower == "zoom out" || lower == "chota karo" || lower == "zoom out karo") {
            return AlyaAccessibilityService.executeCommand("zoom_out")
        }
        if (lower.startsWith("pan ")) {
            val direction = lower.removePrefix("pan ").trim()
            return AlyaAccessibilityService.executeCommand("pan_magnification", mapOf("direction" to direction))
        }

        // 4. Grid Selection
        if (lower == "show grid" || lower == "grid dikhao" || lower == "show numbers" || lower == "numbers dikhao") {
            return AlyaAccessibilityService.executeCommand("show_grid")
        }
        if (lower == "hide grid" || lower == "grid hatao" || lower == "hide numbers" || lower == "numbers hatao") {
            return AlyaAccessibilityService.executeCommand("hide_grid")
        }
        if (lower.startsWith("tap ") || lower.startsWith("select ") || lower.startsWith("number ")) {
            val words = lower.split(" ")
            val numberStr = words.lastOrNull()
            val index = numberStr?.toIntOrNull()
            if (index != null && index in 1..12) {
                return AlyaAccessibilityService.executeCommand("tap_grid", mapOf("index" to index.toString()))
            }
        }

        // 5. Gestures
        if (lower.startsWith("click ") || lower.startsWith("tap ") || lower.startsWith("kholo ") || lower.startsWith("open ")) {
            var target = lower.removePrefix("click ").removePrefix("tap ").removePrefix("kholo ").removePrefix("open ").trim()
            if (target.isNotBlank()) {
                return AlyaAccessibilityService.executeCommand("click_text", mapOf("text" to target))
            }
        }
        if (lower.startsWith("long press on ")) {
            val target = lower.removePrefix("long press on ").trim()
            if (target.isNotBlank()) {
                return AlyaAccessibilityService.executeCommand("long_press", mapOf("label" to target))
            }
        }

        // 6. Text Editing
        if (lower == "clear text" || lower == "sab saaf karo") {
            return AlyaAccessibilityService.executeCommand("edit_text_action", mapOf("action" to "clear"))
        }
        if (lower == "delete last word" || lower == "piche ka delete karo") {
            return AlyaAccessibilityService.executeCommand("edit_text_action", mapOf("action" to "delete_last"))
        }
        if (lower.startsWith("type ")) {
            val content = lower.removePrefix("type ").trim()
            return AlyaAccessibilityService.executeCommand("edit_text_action", mapOf("action" to "type", "content" to content))
        }

        // 7. Connectivity
        if (lower.contains("wifi") && (lower.contains("connect") || lower.contains("available"))) {
            val target = if (lower.startsWith("connect to wifi ")) lower.removePrefix("connect to wifi ").trim() else "available"
            return AlyaAccessibilityService.executeCommand("connect_wifi_auto", mapOf("network" to target))
        }
        if (lower == "turn on wifi" || lower == "wifi chalu karo" || lower == "wifi on karo" || lower == "wifi on") {
            return AlyaAccessibilityService.executeCommand("connect_wifi_auto", mapOf("network" to "available"))
        }
        if (lower == "turn off wifi" || lower == "wifi band karo" || lower == "wifi off karo" || lower == "wifi off") {
            return AlyaAccessibilityService.executeCommand("toggle_wifi_auto", mapOf("state" to "off"))
        }
        if (lower.startsWith("connect to wifi ")) {
            val network = lower.removePrefix("connect to wifi ").trim()
            return AlyaAccessibilityService.executeCommand("connect_wifi_auto", mapOf("network" to network))
        }
        if (lower == "turn on bluetooth" || lower == "bluetooth chalu karo") {
            return AlyaAccessibilityService.executeCommand("toggle_bluetooth_auto", mapOf("state" to "on"))
        }
        if (lower == "turn off bluetooth" || lower == "bluetooth band karo") {
            return AlyaAccessibilityService.executeCommand("toggle_bluetooth_auto", mapOf("state" to "off"))
        }

        // 8. Phone Calls
        if (lower == "answer call" || lower == "pick up" || lower == "accept call" || lower == "phone uthao" || lower == "call answer karo" || lower == "pick up call") {
            return AlyaAccessibilityService.executeCommand("answer_call")
        }
        if (lower == "decline call" || lower == "end call" || lower == "cut call" || lower == "hang up" || lower == "call kato" || lower == "reject call" || lower == "call cut karo") {
            return AlyaAccessibilityService.executeCommand("end_call")
        }

        return false
    }
}
