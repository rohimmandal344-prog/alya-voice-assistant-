package com.example.alya.agent

import android.content.Context
import com.example.domain.tools.StructuredAction
import com.example.domain.tools.ToolExecutionResult
import com.example.domain.tools.ToolExecutor
import com.example.domain.tools.ToolRiskLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Definition of a JARVIS automated macro workflow.
 */
data class JarvisMacroProtocol(
    val id: String,
    val name: String,
    val description: String,
    val iconName: String,
    val actions: List<StructuredAction>
)

data class MacroExecutionReport(
    val protocol: JarvisMacroProtocol,
    val stepsCompleted: Int,
    val totalSteps: Int,
    val stepResults: List<ToolExecutionResult>,
    val summary: String,
    val isSuccess: Boolean
)

/**
 * JarvisMacroEngine
 *
 * Executes complex multi-step automated sequences with individual step verification.
 */
class JarvisMacroEngine(
    private val context: Context,
    private val toolExecutor: ToolExecutor
) {

    val predefinedProtocols: List<JarvisMacroProtocol> = listOf(
        JarvisMacroProtocol(
            id = "protocol_morning",
            name = "Morning Awakening Protocol",
            description = "Unmutes volume, verifies network connectivity, checks weather, and prepares daily tasks.",
            iconName = "wb_sunny",
            actions = listOf(
                StructuredAction(intent = "volume_up", toolName = "device_volume", parameters = mapOf("action" to "set", "level" to "70")),
                StructuredAction(intent = "check_weather", toolName = "weather_info", parameters = emptyMap()),
                StructuredAction(intent = "get_tasks", toolName = "task_list", parameters = emptyMap())
            )
        ),
        JarvisMacroProtocol(
            id = "protocol_bedtime",
            name = "Bedtime Quarantine Protocol",
            description = "Lowers media volume, turns off flashlight, checks morning alarms.",
            iconName = "bedtime",
            actions = listOf(
                StructuredAction(intent = "flashlight_off", toolName = "device_flashlight", parameters = mapOf("state" to "off")),
                StructuredAction(intent = "volume_down", toolName = "device_volume", parameters = mapOf("action" to "set", "level" to "15")),
                StructuredAction(intent = "check_alarms", toolName = "alarm_list", parameters = emptyMap())
            )
        ),
        JarvisMacroProtocol(
            id = "protocol_focus",
            name = "Focus Shield Protocol",
            description = "Silences non-essential audio and readies timer for a 25-minute deep work session.",
            iconName = "psychology",
            actions = listOf(
                StructuredAction(intent = "set_timer", toolName = "timer_create", parameters = mapOf("minutes" to "25", "label" to "Focus Session")),
                StructuredAction(intent = "volume_mute", toolName = "device_volume", parameters = mapOf("action" to "set", "level" to "0"))
            )
        ),
        JarvisMacroProtocol(
            id = "protocol_diagnostics",
            name = "Deep Hardware Audit",
            description = "Executes complete device security scan, storage check, and battery health analysis.",
            iconName = "health_and_safety",
            actions = listOf(
                StructuredAction(intent = "security_scan", toolName = "phone_security_scan", parameters = emptyMap()),
                StructuredAction(intent = "battery_status", toolName = "device_battery", parameters = emptyMap())
            )
        )
    )

    /**
     * Executes a macro protocol sequentially with deterministic verification.
     */
    suspend fun executeProtocol(protocol: JarvisMacroProtocol): MacroExecutionReport = withContext(Dispatchers.IO) {
        val results = mutableListOf<ToolExecutionResult>()
        var successCount = 0

        for (action in protocol.actions) {
            val res = toolExecutor.executeAction(action)
            results.add(res)
            if (res.success) {
                successCount++
            }
        }

        val allSuccess = successCount == protocol.actions.size
        val summary = if (allSuccess) {
            "Protocol '${protocol.name}' executed successfully (${successCount}/${protocol.actions.size} steps verified)."
        } else {
            "Protocol '${protocol.name}' completed with partial results (${successCount}/${protocol.actions.size} steps succeeded)."
        }

        MacroExecutionReport(
            protocol = protocol,
            stepsCompleted = successCount,
            totalSteps = protocol.actions.size,
            stepResults = results,
            summary = summary,
            isSuccess = allSuccess
        )
    }
}
