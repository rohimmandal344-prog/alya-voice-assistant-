package com.example.alya.provider.impl

import com.example.alya.provider.AlyaChatMessage
import com.example.alya.provider.AlyaModelProvider
import com.example.alya.provider.GenerationOptions
import com.example.alya.provider.GenerationResult
import com.example.alya.provider.MessageRole
import com.example.data.ai.GeminiApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * GeminiModelProvider
 *
 * Implements AlyaModelProvider using GeminiApiClient cloud intelligence.
 * Kept strictly as a pluggable driver layer.
 */
class GeminiModelProvider(
    private val client: GeminiApiClient = GeminiApiClient()
) : AlyaModelProvider {

    override val providerId: String = "gemini_cloud"
    override val providerName: String = "Gemini AI"
    override val isLocal: Boolean = false

    override suspend fun generate(
        prompt: String,
        history: List<AlyaChatMessage>,
        options: GenerationOptions
    ): GenerationResult = withContext(Dispatchers.IO) {
        try {
            val messageList = mutableListOf<Pair<String, String>>()
            for (msg in history) {
                val roleStr = if (msg.role == MessageRole.USER) "user" else "model"
                messageList.add(roleStr to msg.content)
            }
            messageList.add("user" to prompt)

            val sysPrompt = options.systemInstruction ?: "You are Alya, a helpful personal assistant."
            val result = client.generateResponse(
                messages = messageList,
                systemInstruction = sysPrompt,
                temperature = options.temperature,
                maxTokens = options.maxTokens
            )

            result.fold(
                onSuccess = { GenerationResult.Success(it) },
                onFailure = { GenerationResult.Error(it.localizedMessage ?: "Gemini Generation Error") }
            )
        } catch (e: Exception) {
            GenerationResult.Error(e.localizedMessage ?: "Unknown Gemini Generation Error")
        }
    }

    override fun streamGenerate(
        prompt: String,
        history: List<AlyaChatMessage>,
        options: GenerationOptions
    ): Flow<String> = flow {
        val res = generate(prompt, history, options)
        if (res is GenerationResult.Success) {
            emit(res.text)
        } else if (res is GenerationResult.Error) {
            emit("Error: ${res.message}")
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun healthCheck(): Boolean = true
}
