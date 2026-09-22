package com.example.alya.provider.impl

import com.example.alya.provider.AlyaChatMessage
import com.example.alya.provider.AlyaModelProvider
import com.example.alya.provider.GenerationOptions
import com.example.alya.provider.GenerationResult
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
 * SelfHostedOpenAICompatibleProvider
 *
 * Pluggable provider for self-hosted LLMs running on:
 * - Ollama (e.g. http://localhost:11434/v1)
 * - vLLM / LocalAI / LM Studio (e.g. http://your-alya-server:8000/v1)
 *
 * Implements real OpenAI-compatible /v1/chat/completions endpoint calls.
 */
class SelfHostedOpenAICompatibleProvider(
    private val baseUrl: String = "http://10.0.2.2:11434/v1",
    private val modelName: String = "llama3:8b",
    private val apiKey: String? = null
) : AlyaModelProvider {

    override val providerId: String = "self_hosted_v1"
    override val providerName: String = "Self-Hosted ($modelName)"
    override val isLocal: Boolean = false

    override suspend fun generate(
        prompt: String,
        history: List<AlyaChatMessage>,
        options: GenerationOptions
    ): GenerationResult = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val url = URL("$baseUrl/chat/completions")
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                if (!apiKey.isNullOrBlank()) {
                    setRequestProperty("Authorization", "Bearer $apiKey")
                }
                connectTimeout = 15000
                readTimeout = 60000
                doOutput = true
            }

            val rootObj = JSONObject()
            rootObj.put("model", modelName)
            rootObj.put("temperature", options.temperature)
            rootObj.put("max_tokens", options.maxTokens)

            val messagesArray = JSONArray()

            // Optional System Prompt
            options.systemInstruction?.let { sys ->
                val sysObj = JSONObject()
                sysObj.put("role", "system")
                sysObj.put("content", sys)
                messagesArray.put(sysObj)
            }

            // History
            for (msg in history) {
                val msgObj = JSONObject()
                msgObj.put("role", msg.role.name.lowercase())
                msgObj.put("content", msg.content)
                messagesArray.put(msgObj)
            }

            // Current user prompt
            val userObj = JSONObject()
            userObj.put("role", "user")
            userObj.put("content", prompt)
            messagesArray.put(userObj)

            rootObj.put("messages", messagesArray)

            OutputStreamWriter(conn.outputStream).use { it.write(rootObj.toString()) }

            val responseCode = conn.responseCode
            if (responseCode in 200..299) {
                val responseText = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                val respJson = JSONObject(responseText)
                val choices = respJson.optJSONArray("choices")
                if (choices != null && choices.length() > 0) {
                    val choice = choices.getJSONObject(0)
                    val content = choice.optJSONObject("message")?.optString("content") ?: ""
                    GenerationResult.Success(content)
                } else {
                    GenerationResult.Error("No choices returned from self-hosted endpoint")
                }
            } else {
                val errText = BufferedReader(InputStreamReader(conn.errorStream ?: conn.inputStream)).use { it.readText() }
                GenerationResult.Error("HTTP $responseCode: $errText", responseCode)
            }
        } catch (e: Exception) {
            GenerationResult.Error("Self-Hosted Provider unreachable: ${e.localizedMessage}")
        } finally {
            conn?.disconnect()
        }
    }

    override fun streamGenerate(
        prompt: String,
        history: List<AlyaChatMessage>,
        options: GenerationOptions
    ): Flow<String> = flow {
        val result = generate(prompt, history, options)
        if (result is GenerationResult.Success) {
            emit(result.text)
        } else if (result is GenerationResult.Error) {
            emit("Error: ${result.message}")
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun healthCheck(): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl/models")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            val code = conn.responseCode
            conn.disconnect()
            code in 200..299
        } catch (e: Exception) {
            false
        }
    }
}
