package com.example.alya.api

import com.example.alya.brain.AlyaBrain
import com.example.alya.brain.BrainContext
import com.example.alya.brain.BrainDecision
import com.example.alya.provider.AlyaChatMessage
import com.example.data.local.entity.MemoryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Standard Alya API Gateway Response.
 */
data class AlyaApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val error: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * AlyaApiGateway
 *
 * Single application-level gateway implementing standard endpoints:
 * - /api/v1/chat
 * - /api/v1/memory
 * - /api/v1/capabilities
 * - /api/v1/health
 * - /api/v1/tools
 *
 * Keeps client layers strictly insulated from underlying reasoning engines or cloud endpoints.
 */
class AlyaApiGateway(
    private val brain: AlyaBrain,
    private val memoryDao: com.example.data.local.dao.MemoryDao,
    private val capabilityManager: com.example.capability.CapabilityManager
) {

    /**
     * Endpoint: /api/v1/chat
     */
    suspend fun handleChatRequest(
        prompt: String,
        history: List<AlyaChatMessage> = emptyList(),
        targetLanguage: String = "en",
        isOnline: Boolean = true
    ): AlyaApiResponse<BrainDecision> = withContext(Dispatchers.IO) {
        try {
            // Retrieve relevant user memories
            val memories = memoryDao.getAllMemories()

            val context = BrainContext(
                userPrompt = prompt,
                conversationHistory = history,
                relevantMemories = memories,
                targetLanguage = targetLanguage,
                isOnline = isOnline
            )

            val decision = brain.process(context)
            AlyaApiResponse(success = true, data = decision)
        } catch (e: Exception) {
            AlyaApiResponse(success = false, error = e.localizedMessage ?: "API Chat Processing Error")
        }
    }

    /**
     * Endpoint: /api/v1/memory
     */
    suspend fun handleGetMemories(): AlyaApiResponse<List<MemoryEntity>> = withContext(Dispatchers.IO) {
        try {
            val memories = memoryDao.getAllMemories()
            AlyaApiResponse(success = true, data = memories)
        } catch (e: Exception) {
            AlyaApiResponse(success = false, error = e.localizedMessage)
        }
    }

    /**
     * Endpoint: /api/v1/health
     */
    suspend fun handleHealthCheck(): AlyaApiResponse<Map<String, Any>> = withContext(Dispatchers.IO) {
        val healthMap = mapOf(
            "status" to "healthy",
            "active_brain_provider" to brain.getActiveProviderInfo(),
            "uptime_ms" to System.currentTimeMillis()
        )
        AlyaApiResponse(success = true, data = healthMap)
    }
}
