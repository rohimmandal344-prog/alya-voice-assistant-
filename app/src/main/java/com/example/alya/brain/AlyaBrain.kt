package com.example.alya.brain

import com.example.alya.provider.AlyaChatMessage
import com.example.alya.provider.AlyaModelProvider
import com.example.alya.provider.GenerationOptions
import com.example.alya.provider.GenerationResult
import com.example.alya.provider.MessageRole
import com.example.data.ai.OfflineNluEngine
import com.example.data.local.entity.MemoryEntity
import com.example.domain.tools.StructuredAction
import com.example.domain.tools.ToolExecutionResult
import com.example.domain.tools.ToolExecutor
import kotlinx.coroutines.Dispatchers
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
    val updatedContext: Map<String, Any> = emptyMap()
)

/**
 * AlyaBrain
 *
 * Central intelligence orchestration layer.
 * Responsibilities:
 * - Intent understanding & contextual resolution
 * - Retrieval of relevant long-term memory & knowledge chunks
 * - Decision making (Tool execution vs Direct verbal response)
 * - Safe delegation to the active AlyaModelProvider or Offline NLU fallback
 * - Verification of executed tool operations before formulating natural replies
 */
class AlyaBrain(
    private var activeModelProvider: AlyaModelProvider,
    private val toolExecutor: ToolExecutor
) {

    fun setModelProvider(provider: AlyaModelProvider) {
        this.activeModelProvider = provider
    }

    fun getActiveProviderInfo(): String = "${activeModelProvider.providerName} (${activeModelProvider.providerId})"

    /**
     * Orchestrates the complete reasoning loop.
     */
    suspend fun process(context: BrainContext): BrainDecision = withContext(Dispatchers.IO) {
        // 1. Offline or Local Fallback Check
        if (!context.isOnline) {
            val offlineIntent = OfflineNluEngine.parseCommand(context.userPrompt)
            if (offlineIntent != null) {
                val actionResult = toolExecutor.executeAction(offlineIntent)
                return@withContext BrainDecision(
                    replyText = actionResult.message,
                    action = offlineIntent,
                    toolResult = actionResult
                )
            }
            return@withContext BrainDecision(
                replyText = "I am currently offline. I can still control hardware like Wi-Fi, Bluetooth, Alarms, and launch local apps."
            )
        }

        // 2. Build Rich Augmented Context Prompt (Memory + Knowledge + System Directives)
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

        // 3. Delegate to Active Model Provider
        val generationOptions = GenerationOptions(
            systemInstruction = enrichedSystemInstruction,
            targetLanguage = context.targetLanguage
        )

        when (val result = activeModelProvider.generate(context.userPrompt, context.conversationHistory, generationOptions)) {
            is GenerationResult.Success -> {
                BrainDecision(replyText = result.text)
            }
            is GenerationResult.Error -> {
                // Graceful fallback to offline heuristic analysis
                val offlineIntent = OfflineNluEngine.parseCommand(context.userPrompt)
                if (offlineIntent != null) {
                    val actionResult = toolExecutor.executeAction(offlineIntent)
                    BrainDecision(
                        replyText = actionResult.message,
                        action = offlineIntent,
                        toolResult = actionResult
                    )
                } else {
                    BrainDecision(replyText = "I encountered an issue connecting to my reasoning service: ${result.message}")
                }
            }
        }
    }
}
