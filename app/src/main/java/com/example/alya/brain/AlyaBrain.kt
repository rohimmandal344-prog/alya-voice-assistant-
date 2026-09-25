package com.example.alya.brain

import com.example.alya.provider.AlyaChatMessage
import com.example.alya.provider.AlyaModelProvider
import com.example.alya.provider.GenerationOptions
import com.example.alya.provider.GenerationResult
import com.example.alya.provider.MessageRole
import com.example.alya.provider.ModelCapabilities
import com.example.alya.provider.ModelProviderTelemetry
import com.example.data.ai.OfflineNluEngine
import com.example.data.local.entity.MemoryEntity
import com.example.domain.tools.StructuredAction
import com.example.domain.tools.ToolExecutionResult
import com.example.domain.tools.ToolExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * Context payload packaged before delegating to the reasoning engine.
 */
data class BrainContext(
    val userPrompt: String,
    val conversationHistory: List<AlyaChatMessage> = emptyList(),
    val relevantMemories: List<MemoryEntity> = emptyList(),
    val knowledgeSnippets: List<String> = emptyList(),
    val targetLanguage: String = "en",
    val isVoiceMode: Boolean = false,
    val isOnline: Boolean = true
)

/**
 * Structured output decision from Alya Brain.
 */
data class BrainDecision(
    val replyText: String,
    val action: StructuredAction? = null,
    val toolResult: ToolExecutionResult? = null,
    val latencyMs: Long = 0L,
    val modelUsed: String = "ModelProvider",
    val updatedContext: Map<String, Any> = emptyMap()
)

/**
 * AlyaBrain
 *
 * Central intelligence orchestration layer.
 * Completely decoupled from specific external AI APIs via the ModelProvider abstraction.
 *
 * Responsibilities:
 * - Intent understanding & contextual resolution
 * - Retrieval of relevant long-term memory & knowledge chunks
 * - Decision making (Tool execution vs Direct verbal response)
 * - Safe delegation to the active ModelProvider (On-device local, self-hosted, or cloud)
 * - Verification of executed tool operations before formulating natural replies
 */
class AlyaBrain(
    private var activeModelProvider: AlyaModelProvider,
    private val toolExecutor: ToolExecutor
) {

    fun setModelProvider(provider: AlyaModelProvider) {
        this.activeModelProvider = provider
    }

    fun getActiveModelProvider(): AlyaModelProvider = activeModelProvider

    fun getActiveProviderInfo(): String = "${activeModelProvider.providerName} (${activeModelProvider.providerId})"

    fun getActiveProviderCapabilities(): ModelCapabilities = activeModelProvider.capabilities

    fun getActiveProviderTelemetry(): ModelProviderTelemetry = activeModelProvider.getTelemetry()

    /**
     * Orchestrates the complete reasoning loop.
     */
    suspend fun process(context: BrainContext): BrainDecision = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        // 1. Direct Local Action Resolver (Always checked first for zero-latency device operations)
        val offlineIntent = OfflineNluEngine.parseCommand(context.userPrompt)
        if (offlineIntent != null) {
            val actionResult = toolExecutor.executeAction(offlineIntent)
            val latency = System.currentTimeMillis() - startTime
            return@withContext BrainDecision(
                replyText = actionResult.message,
                action = offlineIntent,
                toolResult = actionResult,
                latencyMs = latency,
                modelUsed = "On-Device Action Engine"
            )
        }

        // 2. Offline Mode Check
        if (!context.isOnline && !activeModelProvider.isLocal) {
            val localResponse = OfflineNluEngine.generateOfflineResponse(context.userPrompt)
                ?: "I am operating in offline mode. Local device controls and tools are ready."
            val latency = System.currentTimeMillis() - startTime
            return@withContext BrainDecision(
                replyText = localResponse,
                latencyMs = latency,
                modelUsed = "Offline NLU Fallback"
            )
        }

        // 3. Build Rich Augmented Context Prompt (Memory + Knowledge + System Directives)
        val memoryBlock = if (context.relevantMemories.isNotEmpty()) {
            "RELEVANT USER MEMORIES:\n" + context.relevantMemories.joinToString("\n") { "- ${it.key}: ${it.content}" }
        } else ""

        val knowledgeBlock = if (context.knowledgeSnippets.isNotEmpty()) {
            "RETRIEVED KNOWLEDGE (RAG):\n" + context.knowledgeSnippets.joinToString("\n") { "- $it" }
        } else ""

        val enrichedSystemInstruction = buildString {
            append("You are Alya, an intelligent, empathetic, and helpful personal assistant.\n")
            if (memoryBlock.isNotBlank()) append("\n$memoryBlock\n")
            if (knowledgeBlock.isNotBlank()) append("\n$knowledgeBlock\n")
            append("\nAlways verify before reporting device state and respond naturally in the user's language (${context.targetLanguage}).")
        }

        // 4. Delegate to Active Model Provider (Decoupled abstraction)
        val generationOptions = GenerationOptions(
            systemInstruction = enrichedSystemInstruction,
            targetLanguage = context.targetLanguage
        )

        when (val result = activeModelProvider.generate(context.userPrompt, context.conversationHistory, generationOptions)) {
            is GenerationResult.Success -> {
                val latency = System.currentTimeMillis() - startTime
                BrainDecision(
                    replyText = result.text,
                    latencyMs = latency,
                    modelUsed = activeModelProvider.providerName
                )
            }
            is GenerationResult.Error -> {
                // Graceful fallback to offline heuristic analysis
                val fallbackIntent = OfflineNluEngine.parseCommand(context.userPrompt)
                val latency = System.currentTimeMillis() - startTime
                if (fallbackIntent != null) {
                    val actionResult = toolExecutor.executeAction(fallbackIntent)
                    BrainDecision(
                        replyText = actionResult.message,
                        action = fallbackIntent,
                        toolResult = actionResult,
                        latencyMs = latency,
                        modelUsed = "Local Fallback Action"
                    )
                } else {
                    val localResponse = OfflineNluEngine.generateOfflineResponse(context.userPrompt)
                    val reply = localResponse ?: "I am experiencing difficulty reaching my reasoning backend (${result.message}), but local tools remain operational."
                    BrainDecision(
                        replyText = reply,
                        latencyMs = latency,
                        modelUsed = "Local Fallback"
                    )
                }
            }
        }
    }

    /**
     * Streams responses from the active decoupled model provider.
     */
    fun streamProcess(context: BrainContext): Flow<String> = flow {
        val memoryBlock = if (context.relevantMemories.isNotEmpty()) {
            "RELEVANT MEMORIES:\n" + context.relevantMemories.joinToString("\n") { "- ${it.key}: ${it.content}" }
        } else ""

        val systemInstruction = "You are Alya, an intelligent assistant. ${if (memoryBlock.isNotBlank()) memoryBlock else ""}"
        val options = GenerationOptions(
            systemInstruction = systemInstruction,
            targetLanguage = context.targetLanguage
        )

        activeModelProvider.streamGenerate(context.userPrompt, context.conversationHistory, options).collect { token ->
            emit(token)
        }
    }.flowOn(Dispatchers.IO)
}
