package com.example.alya.brain

import com.example.alya.provider.AlyaModelProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ModelRoutePriority {
    LOCAL_FIRST,
    SELF_HOSTED_FIRST,
    CLOUD_FIRST
}

/**
 * ModelRouter
 *
 * Directs conversational & tool prompts to the best available provider
 * (Local Vosk/NLU -> Self-Hosted Ollama/vLLM -> Rotational Multi-Instance Endpoints)
 * based on network availability and user configuration.
 */
class ModelRouter(
    private var localProvider: AlyaModelProvider?,
    private var selfHostedProvider: AlyaModelProvider?,
    private var cloudProvider: AlyaModelProvider,
    private var priority: ModelRoutePriority = ModelRoutePriority.CLOUD_FIRST
) {
    private val _activeProvider = MutableStateFlow(cloudProvider)
    val activeProvider: StateFlow<AlyaModelProvider> = _activeProvider.asStateFlow()

    fun setPriority(newPriority: ModelRoutePriority) {
        this.priority = newPriority
    }

    fun setSelfHostedProvider(provider: AlyaModelProvider?) {
        this.selfHostedProvider = provider
    }

    fun setLocalProvider(provider: AlyaModelProvider?) {
        this.localProvider = provider
    }

    suspend fun resolveBestProvider(isOnline: Boolean): AlyaModelProvider {
        val selected = when (priority) {
            ModelRoutePriority.LOCAL_FIRST -> {
                if (localProvider?.healthCheck() == true) localProvider!!
                else if (isOnline && selfHostedProvider?.healthCheck() == true) selfHostedProvider!!
                else cloudProvider
            }
            ModelRoutePriority.SELF_HOSTED_FIRST -> {
                if (isOnline && selfHostedProvider?.healthCheck() == true) selfHostedProvider!!
                else if (localProvider?.healthCheck() == true) localProvider!!
                else cloudProvider
            }
            ModelRoutePriority.CLOUD_FIRST -> {
                if (isOnline && cloudProvider.healthCheck()) cloudProvider
                else if (selfHostedProvider?.healthCheck() == true) selfHostedProvider!!
                else localProvider ?: cloudProvider
            }
        }
        _activeProvider.value = selected
        return selected
    }
}
