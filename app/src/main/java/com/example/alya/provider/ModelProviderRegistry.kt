package com.example.alya.provider

import android.content.Context
import com.example.alya.provider.impl.CloudModelProvider
import com.example.alya.provider.impl.LocalModelProvider
import com.example.alya.provider.impl.SelfHostedModelProvider
import com.example.data.local.PreferencesManager
import com.example.domain.tools.ToolExecutor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ModelProviderRegistry
 *
 * Central registry, discovery, and lifecycle coordinator for all AI model providers in Alya.
 * Decouples the conversation engine, agent orchestration, and voice systems from any concrete model backend:
 * - 100% On-Device Local Inference Runtime (LocalModelProvider)
 * - Self-Hosted Local Network Endpoints (SelfHostedModelProvider - Ollama / vLLM / llama.cpp)
 * - Cloud APIs (CloudModelProvider - Gemini)
 * - Custom pluggable runtimes
 */
class ModelProviderRegistry(
    private val context: Context,
    private val preferences: PreferencesManager,
    private val toolExecutor: ToolExecutor? = null
) {
    val localModelProvider = LocalModelProvider(context, toolExecutor)
    val selfHostedModelProvider = SelfHostedModelProvider(context, localModelProvider, preferences)
    val cloudModelProvider = CloudModelProvider(context)

    private val customProviders = mutableMapOf<String, ModelProvider>()

    private val _activeProvider = MutableStateFlow<ModelProvider>(
        resolveInitialProvider()
    )
    val activeProvider: StateFlow<ModelProvider> = _activeProvider.asStateFlow()

    private fun resolveInitialProvider(): ModelProvider {
        return when (preferences.openSourceProviderType.value.uppercase()) {
            "SELF_HOSTED", "OLLAMA", "VLLM" -> selfHostedModelProvider
            "CLOUD", "GEMINI" -> cloudModelProvider
            else -> localModelProvider
        }
    }

    fun getAllProviders(): List<ModelProvider> {
        val list = mutableListOf<ModelProvider>(
            localModelProvider,
            selfHostedModelProvider,
            cloudModelProvider
        )
        list.addAll(customProviders.values)
        return list
    }

    fun registerProvider(provider: ModelProvider) {
        customProviders[provider.providerId] = provider
    }

    fun selectProvider(providerTypeOrId: String) {
        val selected = when (providerTypeOrId.uppercase()) {
            "SELF_HOSTED", "OLLAMA", "VLLM", "SELF_HOSTED_SERVER" -> selfHostedModelProvider
            "CLOUD", "GEMINI", "CLOUD_GEMINI" -> cloudModelProvider
            "LOCAL", "LOCAL_ON_DEVICE", "ON_DEVICE" -> localModelProvider
            else -> customProviders[providerTypeOrId] ?: localModelProvider
        }
        _activeProvider.value = selected

        val prefValue = when (selected) {
            selfHostedModelProvider -> "SELF_HOSTED"
            cloudModelProvider -> "CLOUD"
            else -> "LOCAL_ON_DEVICE"
        }
        preferences.setOpenSourceProviderType(prefValue)
    }

    fun getActiveProvider(): ModelProvider = _activeProvider.value

    fun getProviderById(providerId: String): ModelProvider? {
        return getAllProviders().find { it.providerId.equals(providerId, ignoreCase = true) }
    }

    fun getAllTelemetries(): List<ModelProviderTelemetry> {
        return getAllProviders().map { it.getTelemetry() }
    }
}
