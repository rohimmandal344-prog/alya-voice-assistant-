package com.example.alya.provider

import kotlinx.coroutines.flow.Flow
import org.json.JSONObject

/**
 * Representation of a generic chat message for provider consumption.
 */
data class AlyaChatMessage(
    val role: MessageRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

enum class MessageRole {
    SYSTEM,
    USER,
    ASSISTANT,
    TOOL
}

/**
 * Configuration for generation requests across any model backend.
 */
data class GenerationOptions(
    val temperature: Float = 0.7f,
    val maxTokens: Int = 1024,
    val systemInstruction: String? = null,
    val toolsJson: String? = null,
    val targetLanguage: String = "en"
)

/**
 * Result of a model generation call.
 */
sealed class GenerationResult {
    data class Success(val text: String, val toolCallJson: String? = null) : GenerationResult()
    data class Error(val message: String, val code: Int? = null) : GenerationResult()
}

/**
 * AlyaModelProvider
 *
 * Pluggable contract for all reasoning/generation backends.
 * Allows effortless interchange between:
 * - Gemini Provider (Google cloud inference)
 * - OpenAI / Anthropic Compatible Provider
 * - Self-Hosted Ollama / vLLM Provider (Local or private cloud)
 * - On-Device Small Language Model (Gemma / llama.cpp)
 */
interface AlyaModelProvider {
    val providerId: String
    val providerName: String
    val isLocal: Boolean

    suspend fun generate(
        prompt: String,
        history: List<AlyaChatMessage> = emptyList(),
        options: GenerationOptions = GenerationOptions()
    ): GenerationResult

    fun streamGenerate(
        prompt: String,
        history: List<AlyaChatMessage> = emptyList(),
        options: GenerationOptions = GenerationOptions()
    ): Flow<String>

    suspend fun healthCheck(): Boolean
}
