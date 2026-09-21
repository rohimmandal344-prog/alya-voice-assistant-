package com.example.domain.tools

enum class ToolRiskLevel {
    LOW,      // Opening apps, settings, camera, calculator
    MEDIUM,   // Setting timer, alarm, toggling flashlight, navigating
    HIGH      // Initiating phone call, sending SMS, deleting data
}

data class ToolDefinition(
    val name: String,
    val description: String,
    val parameters: Map<String, String>,
    val riskLevel: ToolRiskLevel,
    val requiredPermission: String? = null,
    val defaultRequiresConfirmation: Boolean = false
)

data class StructuredAction(
    val intent: String,
    val toolName: String,
    val parameters: Map<String, String> = emptyMap(),
    val riskLevel: ToolRiskLevel = ToolRiskLevel.LOW,
    val requiresConfirmation: Boolean = false,
    val naturalConfirmationPrompt: String? = null
)

enum class ActionResultStatus {
    SUCCESS,
    FAILED,
    NOT_INSTALLED,
    PERMISSION_REQUIRED,
    NOT_SUPPORTED,
    AMBIGUOUS
}

data class ToolExecutionResult(
    val success: Boolean,
    val message: String,
    val status: ActionResultStatus = if (success) ActionResultStatus.SUCCESS else ActionResultStatus.FAILED,
    val output: String? = null,
    val missingPermission: String? = null,
    val requiresConfirmation: Boolean = false,
    val errorReason: String? = null,
    val candidates: List<String> = emptyList(),
    val targetAppOrFeature: String? = null
)
