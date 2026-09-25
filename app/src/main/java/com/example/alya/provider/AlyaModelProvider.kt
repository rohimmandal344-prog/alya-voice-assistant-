package com.example.alya.provider

import android.content.Context
import kotlinx.coroutines.flow.Flow

/**
 * Representation of a generic chat message for provider consumption.
 */
data class AlyaChatMessage(
    val role: MessageRole,
    val content: String,
    val name: String? = null,
    val toolCallId: String? = null,
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
    val topP: Float = 0.95f,
    val systemInstruction: String? = null,
    val toolsJson: String? = null,
    val targetLanguage: String = "en",
    val timeoutMillis: Long = 30000L,
    val stopSequences: List<String> = emptyList()
)

/**
 * Result of a model generation call.
 */
sealed class GenerationResult {
    data class Success(
        val text: String,
        val toolCallJson: String? = null,
        val finishReason: String = "stop",
        val latencyMs: Long = 0L,
        val tokensUsed: Int = 0
    ) : GenerationResult()

    data class Error(
        val message: String,
        val code: Int? = null,
        val isRecoverable: Boolean = true,
        val cause: Throwable? = null
    ) : GenerationResult()
}

/**
 * Streaming chunk emitted during incremental generation.
 */
data class GenerationChunk(
    val textDelta: String,
    val isLast: Boolean = false,
    val toolCallDelta: String? = null
)

/**
 * Categorization of runtime execution environments.
 */
enum class ModelRuntimeType {
    ON_DEVICE_NLU,
    ON_DEVICE_QUANTIZED,
    SELF_HOSTED_OLLAMA,
    SELF_HOSTED_VLLM,
    SELF_HOSTED_LLAMACPP,
    CLOUD_GEMINI,
    CLOUD_CUSTOM
}

/**
 * Real-time metadata on model capabilities and execution constraints.
 */
data class ModelCapabilities(
    val supportsStreaming: Boolean = true,
    val supportsToolCalling: Boolean = true,
    val supportsStructuredOutput: Boolean = true,
    val supportsContextMemory: Boolean = true,
    val isLocalOnDevice: Boolean = true,
    val isSelfHosted: Boolean = false,
    val contextWindowTokens: Int = 4096,
    val runtimeType: ModelRuntimeType = ModelRuntimeType.ON_DEVICE_NLU,
    val quantizationType: String? = null,
    val estimatedMemoryMb: Int = 2
)

/**
 * Live execution telemetry from a model provider.
 */
data class ModelProviderTelemetry(
    val providerId: String,
    val providerName: String,
    val runtimeType: ModelRuntimeType,
    val totalRequests: Long = 0L,
    val successfulRequests: Long = 0L,
    val failedRequests: Long = 0L,
    val lastLatencyMs: Long = 0L,
    val averageLatencyMs: Double = 0.0,
    val isHealthy: Boolean = true
)

/**
 * ModelProvider
 *
 * Core architectural abstraction layer decoupling the Alya conversation engine
 * and agent reasoning systems from any specific AI runtime or external API backend.
 *
 * Enables pluggable support for:
 * - Local, 100% on-device inference runtimes (Rule-based NLU, LiteRT, ONNX, GGUF)
 * - Self-hosted local network endpoints (Ollama, llama.cpp, vLLM)
 * - Cloud providers (Gemini, Custom API endpoints)
 */
interface ModelProvider {
    val providerId: String
    val providerName: String
    val isLocal: Boolean
    val capabilities: ModelCapabilities

    suspend fun initialize(context: Context): Boolean = true

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

    suspend fun loadModel(): Boolean = true
    suspend fun unloadModel(): Boolean = true
    suspend fun healthCheck(): Boolean = true
    fun getTelemetry(): ModelProviderTelemetry = ModelProviderTelemetry(
        providerId = providerId,
        providerName = providerName,
        runtimeType = capabilities.runtimeType,
        isHealthy = true
    )
}

// Type alias for backward compatibility across the codebase
typealias AlyaModelProvider = ModelProvider
