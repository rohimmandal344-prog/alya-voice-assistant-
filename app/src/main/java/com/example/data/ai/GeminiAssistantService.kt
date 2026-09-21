package com.example.data.ai

import android.content.Context
import android.util.Log
import com.example.domain.actions.ActionResolver
import com.example.domain.tools.ToolExecutionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * GeminiAssistantService (Alya v1.8.0)
 * 
 * High-level orchestration service integrating Gemini Live models (gemini-3.8-flash, gemini-3.7-flash, etc.)
 * for natural language processing, voice turn management, and device intent routing.
 */
class GeminiAssistantService(
    private val context: Context,
    private val geminiApiClient: GeminiApiClient = GeminiApiClient(),
    private val actionResolver: ActionResolver = ActionResolver(context)
) {

    private val _currentModel = MutableStateFlow("gemini-1.5-flash")
    val currentModel: StateFlow<String> = _currentModel.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _lastResult = MutableStateFlow<String?>(null)
    val lastResult: StateFlow<String?> = _lastResult.asStateFlow()

    /**
     * Supported Gemini models prioritized for real-time natural language and voice interaction.
     */
    val supportedLiveModels = listOf(
        "gemini-2.0-flash-exp",
        "gemini-1.5-flash",
        "gemini-1.5-pro"
    )

    /**
     * Process user query (voice or text input) with Gemini Live NLP engine.
     * Extracts device intents or returns conversational responses.
     */
    suspend fun processQuery(
        userInput: String,
        isVoiceMode: Boolean = false,
        conversationHistory: List<Pair<String, String>> = emptyList()
    ): GeminiAssistantResponse = withContext(Dispatchers.IO) {
        _isProcessing.value = true
        Log.i(TAG, "[GEMINI_ASSISTANT] Processing user input: '$userInput' with model '${_currentModel.value}'")

        try {
            // First check if user query can be directly resolved as a hardware/app device action
            val directActionResult = actionResolver.resolveAndExecute(userInput)
            if (directActionResult.success) {
                Log.i(TAG, "[GEMINI_ASSISTANT] Direct device action succeeded: ${directActionResult.message}")
                _isProcessing.value = false
                val responseText = directActionResult.message
                _lastResult.value = responseText
                return@withContext GeminiAssistantResponse(
                    text = responseText,
                    isDeviceAction = true,
                    actionResult = directActionResult,
                    modelUsed = "local-action-resolver"
                )
            }

            // Build system prompt using AiPersonality with active persona
            val prefPersona = (context.applicationContext as? com.example.AlyaApplication)?.preferencesManager?.voicePersona?.value ?: "KORE"
            val systemPrompt = AiPersonality.buildSystemPrompt(isVoiceMode = isVoiceMode, persona = prefPersona)

            // Prepare messages payload (history + current user input)
            val messages = conversationHistory.toMutableList().apply {
                add("user" to userInput)
            }

            // Fallback to Gemini NLU generation
            val result = geminiApiClient.generateResponse(
                messages = messages,
                systemInstruction = systemPrompt,
                preferredModel = _currentModel.value
            )

            _isProcessing.value = false

            if (result.isSuccess) {
                val responseText = result.getOrDefault("I'm here to help!")
                _lastResult.value = responseText
                Log.i(TAG, "[GEMINI_ASSISTANT] Response generated successfully.")
                GeminiAssistantResponse(
                    text = responseText,
                    isDeviceAction = false,
                    modelUsed = _currentModel.value
                )
            } else {
                val error = result.exceptionOrNull()?.message ?: "Unknown error"
                Log.w(TAG, "[GEMINI_ASSISTANT] Gemini query failed: $error. Operating in local fallback mode.")
                val fallbackText = "I heard '$userInput'. Network response is currently unavailable, but your device commands are fully functional offline."
                _lastResult.value = fallbackText
                GeminiAssistantResponse(
                    text = fallbackText,
                    isDeviceAction = false,
                    isFallback = true,
                    error = error,
                    modelUsed = "offline-fallback"
                )
            }
        } catch (e: Exception) {
            _isProcessing.value = false
            Log.e(TAG, "[GEMINI_ASSISTANT] Exception in processQuery: ${e.message}", e)
            
            // Distinguish connectivity errors vs server/service errors
            val isNetworkIssue = e is java.net.UnknownHostException || 
                                 e is java.net.SocketTimeoutException || 
                                 e is java.net.ConnectException ||
                                 (e is retrofit2.HttpException && e.code() == 0)

            val errorText = if (isNetworkIssue) {
                "I'm currently offline, but I can help you with your phone settings. Let me know what you need."
            } else if (e is retrofit2.HttpException) {
                 "I'm having trouble connecting to my service (Error ${e.code()}). I can still help you with local device commands."
            } else {
                "I'm having trouble processing that right now, but I can still manage your device."
            }
            
            _lastResult.value = errorText
            GeminiAssistantResponse(
                text = errorText,
                isDeviceAction = false,
                isFallback = true,
                error = e.message,
                isConnectivityError = isNetworkIssue,
                modelUsed = "offline-fallback"
            )
        }
    }

    /**
     * Switch active Gemini Live model dynamically.
     */
    fun selectModel(modelName: String) {
        if (supportedLiveModels.contains(modelName)) {
            _currentModel.value = modelName
            Log.i(TAG, "[GEMINI_ASSISTANT] Switched active Gemini Live model to '$modelName'")
        } else {
            Log.w(TAG, "[GEMINI_ASSISTANT] Model '$modelName' not in supported live list. Defaulting to 'gemini-1.5-flash'")
            _currentModel.value = "gemini-1.5-flash"
        }
    }

    companion object {
        private const val TAG = "GeminiAssistantService"
    }
}

/**
 * Data class representing structured response from GeminiAssistantService.
 */
data class GeminiAssistantResponse(
    val text: String,
    val isDeviceAction: Boolean = false,
    val actionResult: ToolExecutionResult? = null,
    val isFallback: Boolean = false,
    val error: String? = null,
    val isConnectivityError: Boolean = false,
    val modelUsed: String = "gemini-1.5-flash"
)
