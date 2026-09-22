package com.example.domain.actions

import android.content.Context
import android.util.Log
import com.example.domain.tools.ActionResultStatus
import com.example.domain.tools.ToolExecutionResult
import java.util.Locale

/**
 * Route classification categories.
 */
enum class IntelligenceRouteType {
    LOCAL_DEVICE_COMMAND,
    LOCAL_CONTEXT_ACTION,
    ONLINE_AI_REQUEST,
    ONLINE_INFORMATION_SEARCH,
    AMBIGUOUS_ACTION
}

/**
 * Result of routing evaluation.
 */
data class RoutingDecision(
    val routeType: IntelligenceRouteType,
    val localExecutionResult: ToolExecutionResult? = null,
    val reason: String
)

/**
 * IntelligenceRouter (Alya v3.0 Production)
 *
 * Evaluates user input against local capabilities, active device context,
 * and deterministic command patterns before routing to cloud AI or local execution.
 */
class IntelligenceRouter(private val context: Context) {

    private val localCommandRouter = LocalCommandRouter(context)
    private val deviceContextManager = DeviceContextManager.getInstance(context)

    fun evaluateAndRoute(command: String): RoutingDecision {
        val trimmed = command.trim()
        val lower = trimmed.lowercase(Locale.ROOT)

        if (lower.isBlank()) {
            return RoutingDecision(
                routeType = IntelligenceRouteType.LOCAL_DEVICE_COMMAND,
                localExecutionResult = ToolExecutionResult(false, "Please provide a valid command."),
                reason = "Empty input"
            )
        }

        // 1. Check if it's a local contextual or device command
        val localResult = localCommandRouter.routeAndExecute(trimmed)
        if (localResult != null && (localResult.success || localResult.status != ActionResultStatus.FAILED)) {
            Log.i(TAG, "Command handled by LocalCommandRouter: '$trimmed'")
            return RoutingDecision(
                routeType = IntelligenceRouteType.LOCAL_DEVICE_COMMAND,
                localExecutionResult = localResult,
                reason = "Matched deterministic local capability or device command"
            )
        }

        // 2. Check for online search keywords
        if (lower.startsWith("search ") || lower.startsWith("google ") || lower.contains("who is") || lower.contains("what is")) {
            return RoutingDecision(
                routeType = IntelligenceRouteType.ONLINE_INFORMATION_SEARCH,
                reason = "Informational query requiring web search or online AI"
            )
        }

        // 3. Delegate to Online AI reasoning
        return RoutingDecision(
            routeType = IntelligenceRouteType.ONLINE_AI_REQUEST,
            reason = "General conversation or complex reasoning"
        )
    }

    companion object {
        private const val TAG = "IntelligenceRouter"
    }
}
