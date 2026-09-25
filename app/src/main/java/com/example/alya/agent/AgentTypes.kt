package com.example.alya.agent

import com.example.domain.tools.StructuredAction
import com.example.domain.tools.ToolExecutionResult
import com.example.domain.tools.ToolRiskLevel

/**
 * Agent execution mode defining reasoning depth and autonomy.
 */
enum class AgentMode(val displayName: String, val description: String) {
    DIRECT("Fast Response", "Direct single-pass response with instant execution"),
    CHAIN_OF_THOUGHT("Chain-of-Thought", "Step-by-step reasoning breakdown before answering"),
    REACT_AGENT("ReAct (Reason + Act)", "Interactive tool execution loop with observation feedback"),
    AUTONOMOUS_PLANNER("Autonomous Planner", "Full multi-step goal decomposition, macro execution & verification"),
    JARVIS_EXECUTIVE("JARVIS Executive", "Proactive system telemetry, macro protocols & voice status briefings"),
    SUPER_REASONING("SUPER AGI Reasoning", "Extreme multi-agent reasoning, self-reflection & long-term memory retrieval")
}

/**
 * Single step within a multi-step agent reasoning plan.
 */
data class AgentStep(
    val stepNumber: Int,
    val description: String,
    val thought: String,
    val proposedAction: StructuredAction? = null,
    val status: AgentStepStatus = AgentStepStatus.PENDING,
    val observation: String? = null,
    val executionResult: ToolExecutionResult? = null,
    val timestamp: Long = System.currentTimeMillis()
)

enum class AgentStepStatus {
    PENDING,
    REASONING,
    EXECUTING_ACTION,
    VERIFYING,
    COMPLETED,
    FAILED,
    SKIPPED
}

/**
 * Trace of the agent's thought process for transparency and real-world debugging.
 */
data class ReasoningTrace(
    val agentMode: AgentMode,
    val goal: String,
    val steps: List<AgentStep> = emptyList(),
    val totalTimeMs: Long = 0,
    val tokensEvaluated: Int = 0,
    val toolsInvoked: Int = 0,
    val reflectionNotes: String? = null,
    val isSuccess: Boolean = true
)

/**
 * Final synthesized result from the Agent Orchestrator.
 */
data class AgentResult(
    val replyText: String,
    val finalThought: String = "",
    val trace: ReasoningTrace,
    val allActionsExecuted: List<StructuredAction> = emptyList(),
    val isSuccess: Boolean = true
)

/**
 * Hardware & System Telemetry for JARVIS Assistant HUD.
 */
data class JarvisTelemetry(
    val batteryPercentage: Int,
    val isCharging: Boolean,
    val batteryTemperatureC: Float,
    val availableStorageMb: Long,
    val totalStorageMb: Long,
    val estimatedAvailableRamMb: Long,
    val wifiSsid: String?,
    val isWifiConnected: Boolean,
    val isBluetoothEnabled: Boolean,
    val networkLatencyMs: Long,
    val activeTasksCount: Int,
    val memoryEntitiesCount: Int,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Benchmark test score card.
 */
data class BenchmarkResult(
    val testName: String,
    val category: String,
    val scoreOutOf100: Int,
    val latencyMs: Long,
    val details: String,
    val passed: Boolean
)

data class BenchmarkSuiteReport(
    val overallScore: Int,
    val results: List<BenchmarkResult>,
    val timestamp: Long = System.currentTimeMillis()
)
