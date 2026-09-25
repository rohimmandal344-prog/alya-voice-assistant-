package com.example.alya.provider.device

import android.content.Context
import android.util.Log
import com.example.capability.CapabilityManager
import com.example.capability.CapabilityStatus
import com.example.domain.actions.DeviceActionPlanner
import com.example.domain.tools.StructuredAction
import com.example.domain.tools.ToolExecutionResult
import com.example.domain.tools.ToolExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * DeviceControlManager
 *
 * Centralized, authoritative device controller for Alya Assistant.
 * Obeys the Source-of-Truth Principle:
 * ACTION -> SYSTEM/API RESULT -> ACTUAL STATE QUERY -> VERIFY -> TRUTHFUL RESPONSE
 */
class DeviceControlManager(
    private val context: Context,
    private val toolExecutor: ToolExecutor = ToolExecutor(context)
) {
    companion object {
        private const val TAG = "DeviceControlManager"
    }

    private val capabilityManager = CapabilityManager.getInstance(context)

    /**
     * Executes a planned structured device action and verifies the actual outcome.
     */
    suspend fun executeAction(action: StructuredAction): ToolExecutionResult = withContext(Dispatchers.IO) {
        Log.i(TAG, "Executing device action: ${action.toolName} (Intent: ${action.intent})")

        val capId = mapToolToCapabilityId(action.toolName)
        if (capId != null) {
            val status = capabilityManager.getCapabilityStatus(capId)
            if (status == CapabilityStatus.NOT_SUPPORTED || status == CapabilityStatus.RESTRICTED_BY_DEVICE) {
                return@withContext ToolExecutionResult(
                    success = false,
                    message = "This capability is not supported on your current device hardware."
                )
            }
            if (status == CapabilityStatus.DENIED || status == CapabilityStatus.PERMANENTLY_DENIED) {
                return@withContext ToolExecutionResult(
                    success = false,
                    message = "Permission required: Please grant the necessary permission in Alya Settings."
                )
            }
        }

        // Execute through ToolExecutor (which queries actual Android OS state)
        val result = toolExecutor.executeAction(action)
        Log.i(TAG, "Device action result [Success=${result.success}]: ${result.message}")
        result
    }

    /**
     * Parse natural language command and execute directly if it maps to a device action.
     */
    suspend fun parseAndExecute(userQuery: String): ToolExecutionResult? = withContext(Dispatchers.IO) {
        val planned = DeviceActionPlanner.planAction(userQuery) ?: return@withContext null
        executeAction(planned)
    }

    private fun mapToolToCapabilityId(toolName: String): String? {
        return when (toolName.lowercase()) {
            "set_wifi" -> "wifi_control"
            "set_bluetooth" -> "bluetooth_control"
            "set_flashlight" -> "flashlight_control"
            "set_volume" -> "volume_control"
            "open_app" -> "app_control"
            "media_control" -> "media_control"
            "make_call" -> "call_control"
            "send_sms" -> "sms_control"
            "get_location" -> "location_access"
            "set_alarm" -> "exact_alarms"
            "set_timer" -> "exact_alarms"
            "set_reminder" -> "exact_alarms"
            else -> null
        }
    }
}
