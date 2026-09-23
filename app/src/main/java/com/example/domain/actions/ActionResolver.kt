package com.example.domain.actions

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import com.example.domain.apps.AppLauncherManager
import com.example.domain.apps.AppResolutionResult
import com.example.domain.tools.ActionResultStatus
import com.example.domain.tools.ToolExecutionResult
import com.example.util.diagnostics.DiagnosticLogManager
import com.example.util.diagnostics.DiagnosticStage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Locale

enum class SystemFailureCode {
    APP_NOT_INSTALLED,
    PACKAGE_NOT_FOUND,
    PERMISSION_DENIED,
    ACCESSIBILITY_DISABLED,
    RESOLVE_ACTIVITY_NULL,
    FEATURE_UNAVAILABLE,
    NETWORK_TIMEOUT,
    TRANSIENT_SYSTEM_BUSY,
    EXECUTION_FAILED,
    UNKNOWN_FAILURE
}

/**
 * EmpatheticErrorHandler (Alya v2.2.0)
 * 
 * Translates low-level system failure codes and exceptions into warm, human, conversational feedback.
 * Implements exponential backoff retry logic for transient network or system state errors.
 */
class EmpatheticErrorHandler {

    fun formatEmpatheticMessage(
        code: SystemFailureCode,
        target: String,
        customMessage: String? = null
    ): String {
        return customMessage ?: when (code) {
            SystemFailureCode.APP_NOT_INSTALLED, SystemFailureCode.PACKAGE_NOT_FOUND ->
                "I couldn't open '$target' because it isn't installed on your phone. Would you like me to look for it on Google Play?"

            SystemFailureCode.PERMISSION_DENIED ->
                "I don't have permission to perform that action right now. Please check your phone settings to grant the required permission."

            SystemFailureCode.ACCESSIBILITY_DISABLED ->
                "I need Accessibility Service enabled to perform screen actions for you. Please turn on Alya in your Accessibility settings."

            SystemFailureCode.RESOLVE_ACTIVITY_NULL ->
                "I found '$target', but Android couldn't launch its main activity. You might need to update or reinstall the app."

            SystemFailureCode.FEATURE_UNAVAILABLE ->
                "That setting or system feature isn't available on your device."

            SystemFailureCode.NETWORK_TIMEOUT ->
                "I had trouble connecting to the network to complete that request. Let me try again for you."

            SystemFailureCode.TRANSIENT_SYSTEM_BUSY ->
                "Your system was briefly busy. I'm retrying the command now."

            SystemFailureCode.EXECUTION_FAILED ->
                "I ran into an issue while trying to open '$target'. Let me try another way."

            SystemFailureCode.UNKNOWN_FAILURE ->
                "I couldn't complete that action right now. Please try repeating your request."
        }
    }

    /**
     * Executes an action block with exponential backoff retries for transient system/network errors.
     */
    fun executeWithRetry(
        maxRetries: Int = 3,
        initialDelayMs: Long = 150L,
        action: () -> ToolExecutionResult
    ): ToolExecutionResult {
        var currentDelay = initialDelayMs
        var lastResult: ToolExecutionResult? = null

        for (attempt in 1..maxRetries) {
            try {
                val result = action()
                if (result.success || !isRetryableError(result.errorReason)) {
                    return result
                }
                lastResult = result
                Log.w("EmpatheticErrorHandler", "Attempt $attempt failed with retryable error: ${result.errorReason}. Retrying in ${currentDelay}ms...")
            } catch (e: Exception) {
                Log.w("EmpatheticErrorHandler", "Attempt $attempt threw exception: ${e.message}. Retrying in ${currentDelay}ms...")
            }

            if (attempt < maxRetries) {
                try {
                    Thread.sleep(currentDelay)
                } catch (_: InterruptedException) {}
                currentDelay *= 2
            }
        }

        return lastResult ?: ToolExecutionResult(
            success = false,
            message = formatEmpatheticMessage(SystemFailureCode.EXECUTION_FAILED, "requested action"),
            status = ActionResultStatus.FAILED,
            errorReason = SystemFailureCode.EXECUTION_FAILED.name
        )
    }

    private fun isRetryableError(errorReason: String?): Boolean {
        if (errorReason == null) return false
        return errorReason == SystemFailureCode.NETWORK_TIMEOUT.name ||
                errorReason == SystemFailureCode.TRANSIENT_SYSTEM_BUSY.name ||
                errorReason == SystemFailureCode.EXECUTION_FAILED.name
    }
}

/**
 * ActionResolver (Alya v2.2.0)
 * 
 * Maps natural language user commands to Android Intents and system actions.
 * Translates low-level system errors into empathetic, actionable feedback
 * and streams real-time diagnostic entries across command execution stages.
 */
class ActionResolver(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager
    private val appLauncherManager = AppLauncherManager(context)
    private val commandRegistry = CommandRegistry(context)
    private val diagLog = DiagnosticLogManager.instance
    private val offlineLogger = com.example.util.diagnostics.OfflineCommandLogger.getInstance(context)
    private val roomSyncManager = com.example.data.sync.RoomDataSyncManager.getInstance(context)
    private val resolverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun resolveAndExecute(command: String): ToolExecutionResult {
        val trimmed = command.trim()
        
        // Support for multiple commands separated by conjunctions (English, Hindi, Bengali, Hinglish, Banglish)
        // Example: "open youtube and click shorts", "bhai YouTube khol kar do reels aage badha de"
        val splitRegex = Regex("\\b(?:and|then|fir|phir|aur|ar|tarpor|ebong|khol\\s+kar|karke)\\b|,\\s*", RegexOption.IGNORE_CASE)
        if (splitRegex.containsMatchIn(trimmed)) {
            val segments = trimmed.split(splitRegex).map { it.trim() }.filter { it.isNotEmpty() }
            if (segments.size > 1) {
                Log.i(TAG, "[ACTION_RESOLVER] Multi-command detected. Splitting into ${segments.size} segments.")
                
                val sequenceId = java.util.UUID.randomUUID().toString()
                val stepMaps = segments.mapIndexed { idx, seg ->
                    mapOf<String, Any?>(
                        "stepIndex" to idx,
                        "command" to seg,
                        "status" to "PENDING"
                    )
                }

                resolverScope.launch {
                    roomSyncManager.saveCommandSequence(
                        id = sequenceId,
                        title = "Multi-step command: ${segments.firstOrNull() ?: "Routine"}",
                        originalPrompt = trimmed,
                        steps = stepMaps,
                        isOfflineExecutable = true
                    )
                }

                var lastResult: ToolExecutionResult? = null
                val combinedOutput = StringBuilder()
                var currentStep = 0
                
                for (segment in segments) {
                    val result = internalResolveAndExecute(segment)
                    lastResult = result
                    combinedOutput.append(result.message).append(" ")
                    
                    val stepStatus = if (result.success) "STEP_COMPLETED" else "STEP_FAILED"
                    val stepIdx = currentStep
                    val lastMsg = result.message
                    
                    resolverScope.launch {
                        roomSyncManager.updateCommandSequenceStep(
                            sequenceId = sequenceId,
                            stepIndex = stepIdx,
                            status = stepStatus,
                            stateSnapshot = mapOf("lastExecutedSegment" to segment, "stepResult" to result.success),
                            errorMessage = if (!result.success) lastMsg else null
                        )
                    }

                    if (!result.success) {
                        Log.w(TAG, "[ACTION_RESOLVER] Segment '$segment' failed: ${result.message}")
                    }
                    
                    currentStep++
                    try { Thread.sleep(350L) } catch (e: Exception) { android.util.Log.e("Alya", "Exception handled", e) }
                }
                
                val finalResult = lastResult ?: ToolExecutionResult(false, "No commands found.")
                val overallSuccess = segments.isNotEmpty() && (lastResult?.success == true)
                val finalStatus = if (overallSuccess) "COMPLETED" else "PARTIALLY_COMPLETED"

                resolverScope.launch {
                    roomSyncManager.updateCommandSequenceStep(
                        sequenceId = sequenceId,
                        stepIndex = segments.size - 1,
                        status = finalStatus,
                        stateSnapshot = mapOf("finalCombinedMessage" to combinedOutput.toString().trim(), "isComplete" to true)
                    )
                }

                val resultWithCombinedMessage = finalResult.copy(
                    message = combinedOutput.toString().trim()
                )
                
                offlineLogger.logExecution(
                    command = command,
                    actionType = "MULTI_ACTION_RESOLVED",
                    targetAppOrFeature = "MultiCommand",
                    result = resultWithCombinedMessage,
                    isOffline = true
                )
                return resultWithCombinedMessage
            }
        }

        val result = internalResolveAndExecute(trimmed)
        offlineLogger.logExecution(
            command = command,
            actionType = if (result.success) "ACTION_RESOLVED" else "ACTION_FAILED",
            targetAppOrFeature = result.targetAppOrFeature,
            result = result,
            isOffline = true
        )
        return result
    }

    private fun internalResolveAndExecute(command: String): ToolExecutionResult {
        val trimmed = command.trim()
        val lower = trimmed.lowercase(Locale.ROOT)

        diagLog.logEvent(
            stage = DiagnosticStage.DETECTION,
            command = trimmed,
            details = "Received user command for action resolution."
        )

        if (lower.isBlank()) {
            val result = mapFailureToResult(
                code = SystemFailureCode.UNKNOWN_FAILURE,
                target = trimmed,
                customMessage = "Please provide a valid command."
            )
            diagLog.logEvent(
                stage = DiagnosticStage.RESULT,
                command = trimmed,
                details = result.message,
                isSuccess = false,
                failureCode = SystemFailureCode.UNKNOWN_FAILURE.name
            )
            return result
        }

        // Direct battery status query interception (English, Hindi, Hinglish, Bengali, etc.)
        if ((lower.contains("battery") || lower.contains("charge") || lower.contains("power") || lower.contains("batery") || lower.contains("batteri")) &&
            !lower.contains("setting") && !lower.contains("saver") && !lower.contains("open")) {
            try {
                val collector = com.example.domain.devicelink.DeviceTelemetryCollector(context)
                val telemetry = collector.collectTelemetry()
                val msg = "Your device battery is at ${telemetry.batteryPercentage}% (${if (telemetry.isCharging) "Charging" else "On battery"})."
                diagLog.logEvent(
                    stage = DiagnosticStage.RESULT,
                    command = trimmed,
                    details = msg,
                    isSuccess = true
                )
                return ToolExecutionResult(
                    success = true,
                    message = msg,
                    status = ActionResultStatus.SUCCESS,
                    targetAppOrFeature = "battery"
                )
            } catch (e: Exception) {
                // fall through
            }
        }

        // Fast offline JSON registry lookup
        val fastMatch = commandRegistry.tryFastResolve(trimmed)
        if (fastMatch != null && fastMatch.success) {
            Log.i(TAG, "[ACTION_RESOLVER] Fast offline registry match executed for '$trimmed'")
            return fastMatch
        }

        diagLog.logEvent(
            stage = DiagnosticStage.RESOLUTION,
            command = trimmed,
            details = "Analyzing PackageManager and AppLauncherManager queries."
        )

        // 1. Check if it's a Click command
        if (lower.startsWith("click ")) {
            val target = trimmed.substringAfter("click ", "").trim()
            if (target.isNotEmpty()) {
                diagLog.logEvent(
                    stage = DiagnosticStage.RESOLUTION,
                    command = trimmed,
                    details = "Resolved as click action for text: $target"
                )
                com.example.service.AlyaAccessibilityService.executeCommand("click_text", mapOf("text" to target))
                return ToolExecutionResult(
                    success = true,
                    message = "Clicking on $target.",
                    status = ActionResultStatus.SUCCESS,
                    targetAppOrFeature = target
                )
            }
        }

        // 2. Check if it's an App Launch command
        val cleanedAppQuery = appLauncherManager.normalizeCommand(trimmed)
        val appResolution = appLauncherManager.resolveApp(cleanedAppQuery)

        when (appResolution) {
            is AppResolutionResult.Success -> {
                val intent = appResolution.launchIntent
                diagLog.logEvent(
                    stage = DiagnosticStage.EXECUTION,
                    command = trimmed,
                    details = "Attempting to launch resolved target: ${appResolution.app.label} (${appResolution.app.packageName})"
                )

                try {
                    val resolvedActivity = intent.resolveActivity(packageManager)
                    if (resolvedActivity == null) {
                        Log.w(TAG, "Intent activity null for app: ${appResolution.app.label}")
                        val failResult = mapFailureToResult(
                            code = SystemFailureCode.RESOLVE_ACTIVITY_NULL,
                            target = appResolution.app.label
                        )
                        diagLog.logEvent(
                            stage = DiagnosticStage.RESULT,
                            command = trimmed,
                            details = failResult.message,
                            isSuccess = false,
                            failureCode = SystemFailureCode.RESOLVE_ACTIVITY_NULL.name
                        )
                        return failResult
                    }

                    // Execute launch
                    context.startActivity(intent)
                    Log.i(TAG, "Successfully opened app: ${appResolution.app.label}")
                    
                    com.example.domain.actions.DeviceContextManager.getInstance(context).updateForegroundApp(
                        appResolution.app.packageName,
                        appResolution.app.label
                    )

                    val successResult = ToolExecutionResult(
                        success = true,
                        message = "Opening ${appResolution.app.label}.",
                        status = ActionResultStatus.SUCCESS,
                        output = "Successfully launched ${appResolution.app.label}.",
                        targetAppOrFeature = appResolution.app.label
                    )
                    diagLog.logEvent(
                        stage = DiagnosticStage.RESULT,
                        command = trimmed,
                        details = "Opening ${appResolution.app.label}.",
                        isSuccess = true
                    )
                    return successResult

                } catch (e: SecurityException) {
                    Log.e(TAG, "SecurityException launching app ${appResolution.app.label}: ${e.message}")
                    val failResult = mapFailureToResult(
                        code = SystemFailureCode.PERMISSION_DENIED,
                        target = appResolution.app.label
                    )
                    diagLog.logEvent(
                        stage = DiagnosticStage.RESULT,
                        command = trimmed,
                        details = failResult.message,
                        isSuccess = false,
                        failureCode = SystemFailureCode.PERMISSION_DENIED.name
                    )
                    return failResult

                } catch (e: Exception) {
                    Log.e(TAG, "Failed launching app ${appResolution.app.label}: ${e.message}")
                    val failResult = mapFailureToResult(
                        code = SystemFailureCode.EXECUTION_FAILED,
                        target = appResolution.app.label,
                        customMessage = "I couldn't open ${appResolution.app.label}: ${e.message}"
                    )
                    diagLog.logEvent(
                        stage = DiagnosticStage.RESULT,
                        command = trimmed,
                        details = failResult.message,
                        isSuccess = false,
                        failureCode = SystemFailureCode.EXECUTION_FAILED.name
                    )
                    return failResult
                }
            }

            is AppResolutionResult.Ambiguous -> {
                val names = appResolution.candidates.map { it.label }.joinToString(", ")
                val message = "I found multiple matching apps: $names. Please specify which one you'd like to open."
                diagLog.logEvent(
                    stage = DiagnosticStage.RESULT,
                    command = trimmed,
                    details = message,
                    isSuccess = false,
                    failureCode = "AMBIGUOUS_CANDIDATES"
                )
                return ToolExecutionResult(
                    success = false,
                    message = message,
                    status = ActionResultStatus.AMBIGUOUS,
                    candidates = appResolution.candidates.map { it.label },
                    targetAppOrFeature = trimmed
                )
            }

            is AppResolutionResult.NotInstalled -> {
                // Try system settings fallback before reporting not installed
                val settingIntent = resolveSystemSettingsIntent(lower)
                if (settingIntent != null) {
                    diagLog.logEvent(
                        stage = DiagnosticStage.EXECUTION,
                        command = trimmed,
                        details = "Launching settings fallback for '$trimmed'"
                    )
                    return executeSystemIntent(settingIntent, lower, trimmed)
                }

                val failResult = mapFailureToResult(
                    code = SystemFailureCode.APP_NOT_INSTALLED,
                    target = trimmed
                )
                diagLog.logEvent(
                    stage = DiagnosticStage.RESULT,
                    command = trimmed,
                    details = failResult.message,
                    isSuccess = false,
                    failureCode = SystemFailureCode.APP_NOT_INSTALLED.name
                )
                return failResult
            }

            is AppResolutionResult.ExecutionFailed -> {
                val failResult = mapFailureToResult(
                    code = SystemFailureCode.EXECUTION_FAILED,
                    target = appResolution.app.label,
                    customMessage = "I couldn't start ${appResolution.app.label} because ${appResolution.reason}."
                )
                diagLog.logEvent(
                    stage = DiagnosticStage.RESULT,
                    command = trimmed,
                    details = failResult.message,
                    isSuccess = false,
                    failureCode = SystemFailureCode.EXECUTION_FAILED.name
                )
                return failResult
            }
        }
    }

    private fun resolveSystemSettingsIntent(query: String): Intent? {
        val action = when {
            query.contains("wifi") || query.contains("wi-fi") -> Settings.ACTION_WIFI_SETTINGS
            query.contains("bluetooth") -> Settings.ACTION_BLUETOOTH_SETTINGS
            query.contains("display") || query.contains("brightness") -> Settings.ACTION_DISPLAY_SETTINGS
            query.contains("sound") || query.contains("volume") -> Settings.ACTION_SOUND_SETTINGS
            query.contains("notification") -> Settings.ACTION_APP_NOTIFICATION_SETTINGS
            query.contains("battery") || query.contains("power") -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            query.contains("settings") -> Settings.ACTION_SETTINGS
            else -> null
        } ?: return null

        return Intent(action).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (action == Settings.ACTION_APP_NOTIFICATION_SETTINGS) {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
        }
    }

    private fun executeSystemIntent(intent: Intent, targetName: String, originalCommand: String): ToolExecutionResult {
        return try {
            val resolved = intent.resolveActivity(packageManager)
            if (resolved == null) {
                val failResult = mapFailureToResult(
                    code = SystemFailureCode.FEATURE_UNAVAILABLE,
                    target = targetName
                )
                diagLog.logEvent(
                    stage = DiagnosticStage.RESULT,
                    command = originalCommand,
                    details = failResult.message,
                    isSuccess = false,
                    failureCode = SystemFailureCode.FEATURE_UNAVAILABLE.name
                )
                failResult
            } else {
                context.startActivity(intent)
                val successResult = ToolExecutionResult(
                    success = true,
                    message = "Opening $targetName settings.",
                    status = ActionResultStatus.SUCCESS,
                    targetAppOrFeature = targetName
                )
                diagLog.logEvent(
                    stage = DiagnosticStage.RESULT,
                    command = originalCommand,
                    details = "Opened $targetName settings.",
                    isSuccess = true
                )
                successResult
            }
        } catch (e: SecurityException) {
            val failResult = mapFailureToResult(
                code = SystemFailureCode.PERMISSION_DENIED,
                target = targetName
            )
            diagLog.logEvent(
                stage = DiagnosticStage.RESULT,
                command = originalCommand,
                details = failResult.message,
                isSuccess = false,
                failureCode = SystemFailureCode.PERMISSION_DENIED.name
            )
            failResult
        } catch (e: Exception) {
            val failResult = mapFailureToResult(
                code = SystemFailureCode.EXECUTION_FAILED,
                target = targetName,
                customMessage = "I couldn't open $targetName settings: ${e.message}"
            )
            diagLog.logEvent(
                stage = DiagnosticStage.RESULT,
                command = originalCommand,
                details = failResult.message,
                isSuccess = false,
                failureCode = SystemFailureCode.EXECUTION_FAILED.name
            )
            failResult
        }
    }

    private val errorHandler = EmpatheticErrorHandler()

    private fun mapFailureToResult(
        code: SystemFailureCode,
        target: String,
        customMessage: String? = null
    ): ToolExecutionResult {
        val empatheticMessage = errorHandler.formatEmpatheticMessage(code, target, customMessage)

        return ToolExecutionResult(
            success = false,
            message = empatheticMessage,
            status = if (code == SystemFailureCode.APP_NOT_INSTALLED || code == SystemFailureCode.PACKAGE_NOT_FOUND) ActionResultStatus.NOT_INSTALLED else ActionResultStatus.FAILED,
            errorReason = code.name,
            targetAppOrFeature = target
        )
    }

    companion object {
        private const val TAG = "ActionResolver"
    }
}
