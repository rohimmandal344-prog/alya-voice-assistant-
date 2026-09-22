package com.example.domain.actions

import android.content.Context
import android.util.Log
import com.example.domain.apps.AppLauncherManager
import com.example.domain.tools.ActionResultStatus
import com.example.domain.tools.StructuredAction
import com.example.domain.tools.ToolExecutionResult
import com.example.domain.tools.ToolExecutor
import java.util.Locale

/**
 * LocalCommandRouter (Alya v3.0 Production)
 *
 * Deterministic local execution router for device actions and offline command intents.
 * Directs planned device actions to ToolExecutor, ActionResolver, or LocalDeviceControlRegistry
 * with real system state verification and contextual recording in DeviceContextManager.
 */
class LocalCommandRouter(private val context: Context) {

    private val toolExecutor = ToolExecutor(context)
    private val actionResolver = ActionResolver(context)
    private val deviceContextManager = DeviceContextManager.getInstance(context)
    private val capabilityRegistry = AlyaCapabilityRegistry.getInstance(context)

    /**
     * Attempts to route and execute a local device command deterministically.
     * Returns ToolExecutionResult if handled, or null if it should be delegated to Cloud/Online AI.
     */
    fun routeAndExecute(command: String): ToolExecutionResult? {
        val trimmed = command.trim()
        val lower = trimmed.lowercase(Locale.ROOT)
        if (lower.isBlank()) return null

        Log.i(TAG, "Routing command locally: '$trimmed'")

        // 1. Contextual UI follow-up commands (e.g., user is in YouTube and says "shorts", "tap shorts", "click shorts")
        val foregroundPkg = deviceContextManager.getCurrentForegroundPackage()
        if (isShortsOrTabFollowUp(lower, foregroundPkg)) {
            Log.i(TAG, "Contextual YouTube Shorts follow-up detected with foreground app: $foregroundPkg")
            val structuredTouch = StructuredAction(
                intent = "touch_element",
                toolName = "touch_element",
                parameters = mapOf("element_identifier" to "Shorts", "label" to "Shorts")
            )
            val touchResult = toolExecutor.executeAction(structuredTouch, isUserConfirmed = true)
            deviceContextManager.recordAction(trimmed, target = "Shorts", success = touchResult.success, task = "YouTube Shorts")
            return touchResult
        }

        // 2. Structured action planning (Deterministic Natural Language to Device Control)
        val plannedAction = DeviceActionPlanner.planAction(trimmed)
        if (plannedAction != null) {
            Log.i(TAG, "Structured action planned: tool='${plannedAction.toolName}', intent='${plannedAction.intent}'")
            val execResult = toolExecutor.executeAction(plannedAction, isUserConfirmed = true)
            deviceContextManager.recordAction(
                command = trimmed,
                target = plannedAction.parameters["appName"] ?: plannedAction.parameters["element_identifier"] ?: plannedAction.toolName,
                success = execResult.success,
                task = plannedAction.intent
            )
            return execResult
        }

        // 3. Fallback to ActionResolver for installed app launch, click text, or system settings
        val resolverResult = actionResolver.resolveAndExecute(trimmed)
        if (resolverResult.success || resolverResult.status != ActionResultStatus.NOT_INSTALLED) {
            deviceContextManager.recordAction(
                command = trimmed,
                target = resolverResult.targetAppOrFeature,
                success = resolverResult.success,
                task = "ActionResolver"
            )
            return resolverResult
        }

        return null
    }

    private fun isShortsOrTabFollowUp(lower: String, foregroundPkg: String?): Boolean {
        val isShortsIntent = lower == "shorts" || lower == "tap shorts" || lower == "click shorts" || 
                             lower == "open shorts" || lower.contains("shorts kholo") || lower.contains("shorts chalao")
        val isYouTubeActive = foregroundPkg?.contains("youtube", ignoreCase = true) == true ||
                              deviceContextManager.state.value.lastActiveTask?.contains("youtube", ignoreCase = true) == true
        return isShortsIntent && isYouTubeActive
    }

    companion object {
        private const val TAG = "LocalCommandRouter"
    }
}
