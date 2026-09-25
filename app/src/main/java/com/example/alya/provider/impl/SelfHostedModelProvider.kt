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
import com.example.data.local.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * SelfHostedModelProvider
 *
 * Pluggable provider for self-hosted LLMs running on:
 * - Local / Home Server Ollama (e.g. http://10.0.2.2:11434/v1 or http://localhost:11434/v1)
 * - llama.cpp server / vLLM / LocalAI
 *
 * Features:
 * - Completely optional: Does NOT require any environment variables or mandatory setup.
 * - Automatic seamless fallback to LocalModelProvider if server is unreachable.
 * - Real OpenAI-compatible /v1/chat/completions endpoint calls with SSE streaming.
 */
class SelfHostedModelProvider(
    private val context: Context,
    private val localFallbackProvider: LocalModelProvider,
    private val preferences: PreferencesManager
) : ModelProvider {

    companion object {
        private const val TAG = "SelfHostedModelProvider"
    }

    override val providerId: String = "self_hosted_server"
    override val providerName: String
        get() = "Self-Hosted Model (${preferences.openSourceModelName.value})"

    override val isLocal: Boolean
        get() {
            val url = preferences.openSourceBaseUrl.value
            return url.contains("localhost") || url.contains("127.0.0.1") || url.contains("10.0.2.2")
        }

    override val capabilities: ModelCapabilities = ModelCapabilities(
        supportsStreaming = true,
        supportsToolCalling = true,
        supportsStructuredOutput = true,
        supportsContextMemory = true,
        isLocalOnDevice = false,
        isSelfHosted = true,
        contextWindowTokens = 8192,
        runtimeType = ModelRuntimeType.SELF_HOSTED_OLLAMA,
        quantizationType = "GGUF/Ollama",
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

        val baseUrl = preferences.openSourceBaseUrl.value.trim().trimEnd('/')
        val modelName = preferences.openSourceModelName.value.trim().ifBlank { "llama3.2" }

        if (baseUrl.isBlank()) {
            Log.d(TAG, "No self-hosted endpoint configured. Delegating to local on-device provider.")
            return@withContext localFallbackProvider.generate(prompt, history, options)
        }

        try {
            val url = URL("$baseUrl/chat/completions")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                connectTimeout = 8000
                readTimeout = 30000
                doOutput = true
            }

            val rootObj = JSONObject().apply {
                put("model", modelName)
                put("temperature", options.temperature)
                put("max_tokens", options.maxTokens)
                put("stream", false)

                val messagesArray = JSONArray()

                // System Instruction
                val systemPrompt = options.systemInstruction ?: "You are Alya, an open-source truthful assistant. Respond naturally in ${options.targetLanguage}."
                messagesArray.put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })

                // History
                for (msg in history) {
                    messagesArray.put(JSONObject().apply {
                        put("role", when (msg.role) {
                            MessageRole.USER -> "user"
                            MessageRole.SYSTEM -> "system"
                            MessageRole.ASSISTANT -> "assistant"
                            MessageRole.TOOL -> "system"
                        })
                        put("content", msg.content)
                    })
                }

                // Current Prompt
                messagesArray.put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })

                put("messages", messagesArray)
            }

            OutputStreamWriter(conn.outputStream).use { it.write(rootObj.toString()) }

            val responseCode = conn.responseCode
            val latency = System.currentTimeMillis() - startTime
            lastLatencyMs = latency
            totalLatencyMs += latency

            if (responseCode in 200..299) {
                successfulRequests++
                val responseStr = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseStr)
                val choices = json.optJSONArray("choices")
                if (choices != null && choices.length() > 0) {
                    val message = choices.getJSONObject(0).optJSONObject("message")
                    val content = message?.optString("content", "") ?: ""
                    GenerationResult.Success(
                        text = content,
                        latencyMs = latency,
                        tokensUsed = content.length / 4
                    )
                } else {
                    GenerationResult.Success(
                        text = "I received an empty response from the self-hosted model.",
                        latencyMs = latency
                    )
                }
            } else {
                failedRequests++
                Log.w(TAG, "Self-hosted model endpoint returned code: $responseCode. Falling back to local on-device AI.")
                localFallbackProvider.generate(prompt, history, options)
            }
        } catch (e: Exception) {
            failedRequests++
            Log.i(TAG, "Self-hosted model connection failed (${e.message}). Falling back to local on-device AI.")
            localFallbackProvider.generate(prompt, history, options)
        }
    }

    override fun streamGenerate(
        prompt: String,
        history: List<AlyaChatMessage>,
        options: GenerationOptions
    ): Flow<String> = flow {
        val baseUrl = preferences.openSourceBaseUrl.value.trim().trimEnd('/')
        val modelName = preferences.openSourceModelName.value.trim().ifBlank { "llama3.2" }

        if (baseUrl.isBlank()) {
            localFallbackProvider.streamGenerate(prompt, history, options).collect { emit(it) }
            return@flow
        }

        try {
            val url = URL("$baseUrl/chat/completions")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "text/event-stream")
                connectTimeout = 8000
                readTimeout = 30000
                doOutput = true
            }

            val rootObj = JSONObject().apply {
                put("model", modelName)
                put("temperature", options.temperature)
                put("max_tokens", options.maxTokens)
                put("stream", true)

                val messagesArray = JSONArray()

                val systemPrompt = options.systemInstruction ?: "You are Alya, an open-source assistant. Respond in ${options.targetLanguage}."
                messagesArray.put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })

                for (msg in history) {
                    messagesArray.put(JSONObject().apply {
                        put("role", when (msg.role) {
                            MessageRole.USER -> "user"
                            MessageRole.SYSTEM -> "system"
                            MessageRole.ASSISTANT -> "assistant"
                            MessageRole.TOOL -> "system"
                        })
                        put("content", msg.content)
                    })
                }

                messagesArray.put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })

                put("messages", messagesArray)
            }

            OutputStreamWriter(conn.outputStream).use { it.write(rootObj.toString()) }

            val responseCode = conn.responseCode
            if (responseCode in 200..299) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val currentLine = line?.trim().orEmpty()
                    if (currentLine.startsWith("data:")) {
                        val data = currentLine.removePrefix("data:").trim()
                        if (data == "[DONE]") break
                        try {
                            val json = JSONObject(data)
                            val choices = json.optJSONArray("choices")
                            if (choices != null && choices.length() > 0) {
                                val delta = choices.getJSONObject(0).optJSONObject("delta")
                                val chunk = delta?.optString("content", "") ?: ""
                                if (chunk.isNotEmpty()) {
                                    emit(chunk)
                                }
                            }
                        } catch (e: Exception) {
                            // ignore format error in SSE chunk
                        }
                    }
                }
            } else {
                localFallbackProvider.streamGenerate(prompt, history, options).collect { emit(it) }
            }
        } catch (e: Exception) {
            Log.i(TAG, "Streaming to self-hosted server failed (${e.message}). Falling back to local on-device AI.")
            localFallbackProvider.streamGenerate(prompt, history, options).collect { emit(it) }
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun healthCheck(): Boolean = withContext(Dispatchers.IO) {
        val baseUrl = preferences.openSourceBaseUrl.value.trim().trimEnd('/')
        if (baseUrl.isBlank()) return@withContext false
        try {
            val url = URL("$baseUrl/models")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 3000
                readTimeout = 3000
            }
            val code = conn.responseCode
            conn.disconnect()
            code in 200..399
        } catch (e: Exception) {
            false
        }
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
