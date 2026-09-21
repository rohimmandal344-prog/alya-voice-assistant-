package com.example.domain.actions

import com.example.domain.tools.StructuredAction
import com.example.domain.tools.ToolRiskLevel
import java.util.regex.Pattern

/**
 * Parses explicit [ACTION: COMMAND ...] protocol tags from model or engine outputs.
 * Example tags:
 * - [ACTION: OPEN_APP name="YouTube"]
 * - [ACTION: SET_WIFI state="ON"]
 * - [ACTION: SET_BLUETOOTH state="ON"]
 * - [ACTION: SET_VOLUME level="80"]
 * - [ACTION: SET_BRIGHTNESS level="70"]
 * - [ACTION: SET_FLASHLIGHT state="ON"]
 * - [ACTION: MEDIA_CONTROL state="PLAY"]
 */
object ActionTagParser {

    private val actionPattern = Pattern.compile("\\[ACTION:\\s*([A-Z_]+)(?:\\s+([^\\]]+))?\\]", Pattern.CASE_INSENSITIVE)
    private val paramPattern = Pattern.compile("(\\w+)=\"([^\"]*)\"")

    data class ParsedOutput(
        val fullText: String,
        val spokenText: String,
        val action: StructuredAction?
    )

    fun parse(rawResponse: String): ParsedOutput {
        val trimmed = rawResponse.trim()
        val matcher = actionPattern.matcher(trimmed)

        var action: StructuredAction? = null
        if (matcher.find()) {
            val command = matcher.group(1)?.uppercase() ?: ""
            val paramsString = matcher.group(2) ?: ""
            val params = mutableMapOf<String, String>()

            val paramMatcher = paramPattern.matcher(paramsString)
            while (paramMatcher.find()) {
                val key = paramMatcher.group(1)?.lowercase() ?: ""
                val value = paramMatcher.group(2) ?: ""
                params[key] = value
            }

            action = mapCommandToAction(command, params)
        }

        // Clean spoken text: Remove "Alya:" prefix and remove the [ACTION: ...] tag
        val spokenText = trimmed
            .replace(Regex("^(?:Alya|Alia|Assistant|System):\\s*", RegexOption.IGNORE_CASE), "")
            .replace(actionPattern.toRegex(), "")
            .replace(Regex("[*#_~`>]"), "")
            .trim()

        return ParsedOutput(
            fullText = trimmed,
            spokenText = spokenText,
            action = action
        )
    }

    private fun mapCommandToAction(command: String, params: Map<String, String>): StructuredAction? {
        return when (command) {
            "OPEN_APP" -> {
                val appName = params["name"] ?: params["app"] ?: params["appname"] ?: ""
                if (appName.isNotBlank()) {
                    StructuredAction(
                        intent = "launch_app",
                        toolName = "launch_app",
                        parameters = mapOf("app_name" to appName, "appName" to appName),
                        riskLevel = ToolRiskLevel.LOW
                    )
                } else null
            }

            "SET_WIFI" -> {
                val state = params["state"]?.lowercase() ?: "on"
                StructuredAction(
                    intent = "toggle_wifi",
                    toolName = "toggle_wifi",
                    parameters = mapOf("state" to state),
                    riskLevel = ToolRiskLevel.LOW
                )
            }

            "SET_BLUETOOTH" -> {
                val state = params["state"]?.lowercase() ?: "on"
                StructuredAction(
                    intent = "toggle_bluetooth",
                    toolName = "toggle_bluetooth",
                    parameters = mapOf("state" to state),
                    riskLevel = ToolRiskLevel.LOW
                )
            }

            "SET_VOLUME" -> {
                val value = params["value"] ?: params["level"] ?: "50"
                val actionType = when {
                    value.startsWith("+") -> "up"
                    value.startsWith("-") -> "down"
                    else -> "set"
                }
                StructuredAction(
                    intent = "control_volume",
                    toolName = "control_volume",
                    parameters = mapOf("action" to actionType, "level" to value, "value" to value),
                    riskLevel = ToolRiskLevel.LOW
                )
            }

            "SET_BRIGHTNESS" -> {
                val value = params["value"] ?: params["level"] ?: "50"
                val actionType = when {
                    value.startsWith("+") -> "up"
                    value.startsWith("-") -> "down"
                    else -> "set"
                }
                StructuredAction(
                    intent = "control_brightness",
                    toolName = "control_brightness",
                    parameters = mapOf("action" to actionType, "level" to value, "value" to value),
                    riskLevel = ToolRiskLevel.LOW
                )
            }

            "SET_FLASHLIGHT" -> {
                val state = params["state"]?.lowercase() ?: "on"
                StructuredAction(
                    intent = "toggle_flashlight",
                    toolName = "toggle_flashlight",
                    parameters = mapOf("state" to state),
                    riskLevel = ToolRiskLevel.LOW
                )
            }

            "MEDIA_CONTROL" -> {
                val state = params["state"]?.lowercase() ?: "play"
                StructuredAction(
                    intent = "media_control",
                    toolName = "media_control",
                    parameters = mapOf("action" to state),
                    riskLevel = ToolRiskLevel.LOW
                )
            }

            else -> null
        }
    }

    /**
     * Constructs standard action tag string for offline response templates
     */
    fun createActionTag(action: StructuredAction): String {
        return when (action.toolName) {
            "launch_app", "open_app" -> {
                val appName = action.parameters["app_name"] ?: action.parameters["appName"] ?: ""
                if (appName.isNotBlank()) "[ACTION: OPEN_APP name=\"$appName\"]" else ""
            }
            "toggle_wifi" -> {
                val state = action.parameters["state"]?.uppercase() ?: "ON"
                "[ACTION: SET_WIFI state=\"$state\"]"
            }
            "toggle_bluetooth" -> {
                val state = action.parameters["state"]?.uppercase() ?: "ON"
                "[ACTION: SET_BLUETOOTH state=\"$state\"]"
            }
            "control_volume", "adjust_volume" -> {
                val v = action.parameters["value"] ?: action.parameters["level"] ?: (if (action.parameters["action"] == "down") "-20" else "+20")
                "[ACTION: SET_VOLUME value=\"$v\"]"
            }
            "control_brightness" -> {
                val v = action.parameters["value"] ?: action.parameters["level"] ?: (if (action.parameters["action"] == "down") "-20" else "+20")
                "[ACTION: SET_BRIGHTNESS value=\"$v\"]"
            }
            "toggle_flashlight" -> {
                val state = action.parameters["state"]?.uppercase() ?: "ON"
                "[ACTION: SET_FLASHLIGHT state=\"$state\"]"
            }
            "media_control" -> {
                val state = action.parameters["action"]?.uppercase() ?: "PLAY"
                "[ACTION: MEDIA_CONTROL state=\"$state\"]"
            }
            else -> ""
        }
    }
}
