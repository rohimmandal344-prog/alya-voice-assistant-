package com.example.alya.provider.impl

import android.content.Context
import android.util.Log
import com.example.alya.provider.AlyaChatMessage
import com.example.alya.provider.GenerationOptions
import com.example.alya.provider.GenerationResult
import com.example.alya.provider.MessageRole
import com.example.alya.provider.ModelCapabilities
import com.example.alya.provider.ModelProvider
import com.example.alya.provider.ModelProviderTelemetry
import com.example.alya.provider.ModelRuntimeType
import com.example.data.ai.GeminiApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * CloudModelProvider
 *
 * Implements the ModelProvider abstraction layer for cloud-hosted AI APIs (Gemini).
 * Ensures the conversation engine remains decoupled and can treat cloud endpoints
 * identically to local on-device and self-hosted inference backends.
 */
class CloudModelProvider(
    private val context: Context,
    private val geminiClient: GeminiApiClient = GeminiApiClient()
) : ModelProvider {

    companion object {
        private const val TAG = "CloudModelProvider"
    }

    override val providerId: String = "cloud_gemini"
    override val providerName: String = "Cloud AI (Gemini 2.5/Flash)"
    override val isLocal: Boolean = false

    override val capabilities: ModelCapabilities = ModelCapabilities(
        supportsStreaming = true,
        supportsToolCalling = true,
        supportsStructuredOutput = true,
        supportsContextMemory = true,
        isLocalOnDevice = false,
        isSelfHosted = false,
        contextWindowTokens = 32768,
        runtimeType = ModelRuntimeType.CLOUD_GEMINI,
        quantizationType = "Cloud-FP16",
        estimatedMemoryMb = 0
    )

    private var totalRequests: Long = 0
    private var successfulRequests: Long = 0
    private var failedRequests: Long = 0
    private var totalLatencyMs: Long = 0
    private var lastLatencyMs: Long = 0

    override suspend fun generate(
        prompt: String,
        history: List<AlyaChatMessage>,
        options: GenerationOptions
    ): GenerationResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        totalRequests++

        val apiMessages = history.map { msg ->
            val roleStr = when (msg.role) {
                MessageRole.USER -> "user"
                MessageRole.ASSISTANT -> "model"
                MessageRole.SYSTEM -> "system"
                MessageRole.TOOL -> "tool"
            }
            Pair(roleStr, msg.content)
        }.toMutableList().apply {
            add(Pair("user", prompt))
        }

        val result = geminiClient.generateResponse(
            messages = apiMessages,
            systemInstruction = options.systemInstruction ?: "You are Alya, a friendly, intelligent assistant. Respond in ${options.targetLanguage}."
        )

        val latency = System.currentTimeMillis() - startTime
        lastLatencyMs = latency
        totalLatencyMs += latency

        if (result.isSuccess) {
            successfulRequests++
            val responseText = result.getOrDefault("I'm here to assist you.")
            GenerationResult.Success(
                text = responseText,
                latencyMs = latency,
                tokensUsed = (responseText.length / 4)
            )
        } else {
            failedRequests++
            val error = result.exceptionOrNull()
            Log.w(TAG, "Cloud generation error: ${error?.message}")
            GenerationResult.Error(
                message = error?.message ?: "Unknown cloud error",
                isRecoverable = true,
                cause = error
            )
        }
    }

    override fun streamGenerate(
        prompt: String,
        history: List<AlyaChatMessage>,
        options: GenerationOptions
    ): Flow<String> = flow {
        val genResult = generate(prompt, history, options)
        if (genResult is GenerationResult.Success) {
            val tokens = genResult.text.split(Regex("(?<=\\s)|(?<=[.,!?;])"))
            for (t in tokens) {
                if (t.isNotEmpty()) {
                    emit(t)
                    kotlinx.coroutines.delay(15)
                }
            }
        } else if (genResult is GenerationResult.Error) {
            emit("Error: ${genResult.message}")
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun healthCheck(): Boolean = withContext(Dispatchers.IO) {
        val connManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
        val activeNet = connManager?.activeNetwork
        val caps = connManager?.getNetworkCapabilities(activeNet)
        caps?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    override fun getTelemetry(): ModelProviderTelemetry {
        val avgLatency = if (totalRequests > 0) totalLatencyMs.toDouble() / totalRequests else 0.0
        return ModelProviderTelemetry(
            providerId = providerId,
            providerName = providerName,
            runtimeType = capabilities.runtimeType,
            totalRequests = totalRequests,
            successfulRequests = successfulRequests,
            failedRequests = failedRequests,
            lastLatencyMs = lastLatencyMs,
            averageLatencyMs = avgLatency,
            isHealthy = true
        )
    }
}
