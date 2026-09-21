package com.example.voice.wakeword

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

/**
 * Finite State Machine for Alya's Local-First On-Device Wake-Word Engine.
 *
 * Enforces strict transition boundaries:
 * - WAKE_DISABLED   -> Engine off (user disabled or no mic permission)
 * - WAKE_INITIALIZING -> Model loading, AudioRecord initialization, buffer warming
 * - WAKE_READY      -> Actively listening on low-power background classifier
 * - WAKE_DETECTED   -> Wake word trigger validated ("alia", "alya", "seno")
 * - WAKE_ACTIVE     -> App awake, full pipeline running
 * - WAKE_ERROR      -> Recoverable hardware or engine failure
 */
sealed class WakeState {
    data object DISABLED : WakeState()
    data object INITIALIZING : WakeState()
    data class READY(val keyword: String = "alya") : WakeState()
    data class LISTENING(val keyword: String) : WakeState()
    data class DETECTED(val keyword: String, val confidence: Float, val timestamp: Long = System.currentTimeMillis()) : WakeState()
    data class ACTIVE(val keyword: String, val activatedAt: Long = System.currentTimeMillis()) : WakeState()
    data class ERROR(val message: String, val retryCount: Int = 0) : WakeState()
}

/**
 * Observable State Machine controlling wake-word life cycles, timeouts, and error self-healing.
 */
class WakeWordStateMachine(
    private val config: WakeWordConfig = WakeWordConfig(),
    private val onMaxRetriesExceeded: ((String) -> Unit)? = null
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val _currentState = MutableStateFlow<WakeState>(WakeState.DISABLED)
    val currentState: StateFlow<WakeState> = _currentState.asStateFlow()

    private val retryCounter = AtomicInteger(0)
    private var activeTimeoutJob: Job? = null
    private var retryJob: Job? = null

    private val stateListeners = mutableListOf<(WakeState) -> Unit>()

    fun addStateListener(listener: (WakeState) -> Unit) {
        synchronized(stateListeners) {
            if (!stateListeners.contains(listener)) {
                stateListeners.add(listener)
            }
        }
    }

    fun removeStateListener(listener: (WakeState) -> Unit) {
        synchronized(stateListeners) {
            stateListeners.remove(listener)
        }
    }

    @Synchronized
    fun transitionTo(newState: WakeState): Boolean {
        val oldState = _currentState.value
        if (!isValidTransition(oldState, newState)) {
            Log.w(
                TAG,
                "Invalid state transition rejected: ${oldState::class.simpleName} -> ${newState::class.simpleName}"
            )
            return false
        }

        activeTimeoutJob?.cancel()
        activeTimeoutJob = null

        // Reset retries if transitioning to READY or DISABLED
        if (newState is WakeState.READY || newState is WakeState.DISABLED) {
            retryCounter.set(0)
            retryJob?.cancel()
            retryJob = null
        }

        _currentState.value = newState
        Log.i(TAG, "WakeState Transition: ${oldState::class.simpleName} -> ${newState::class.simpleName}")

        // Handle side-effects for specific target states
        when (newState) {
            is WakeState.ACTIVE -> {
                scheduleActiveAutoReturn(newState.keyword)
            }
            is WakeState.ERROR -> {
                handleErrorSelfHealing(newState.message, newState.retryCount)
            }
            else -> {}
        }

        notifyListeners(newState)
        return true
    }

    private fun isValidTransition(from: WakeState, to: WakeState): Boolean {
        // Any state can transition to ERROR or DISABLED
        if (to is WakeState.ERROR || to is WakeState.DISABLED) {
            return true
        }

        return when (from) {
            is WakeState.DISABLED -> to is WakeState.INITIALIZING || to is WakeState.READY || to is WakeState.LISTENING
            is WakeState.INITIALIZING -> to is WakeState.READY || to is WakeState.INITIALIZING || to is WakeState.LISTENING
            is WakeState.READY -> to is WakeState.DETECTED || to is WakeState.LISTENING || to is WakeState.INITIALIZING || to is WakeState.DISABLED
            is WakeState.LISTENING -> to is WakeState.DETECTED || to is WakeState.READY || to is WakeState.INITIALIZING || to is WakeState.DISABLED
            is WakeState.DETECTED -> to is WakeState.ACTIVE || to is WakeState.READY || to is WakeState.LISTENING || to is WakeState.DISABLED
            is WakeState.ACTIVE -> to is WakeState.READY || to is WakeState.LISTENING || to is WakeState.INITIALIZING || to is WakeState.DISABLED
            is WakeState.ERROR -> to is WakeState.INITIALIZING || to is WakeState.READY || to is WakeState.DISABLED
        }
    }

    private fun scheduleActiveAutoReturn(keyword: String) {
        activeTimeoutJob = scope.launch {
            delay(config.activeTimeoutMs)
            if (_currentState.value is WakeState.ACTIVE) {
                Log.i(TAG, "Active mode auto-timeout (${config.activeTimeoutMs}ms). Returning to WAKE_READY.")
                transitionTo(WakeState.READY(keyword))
            }
        }
    }

    private fun handleErrorSelfHealing(errorMessage: String, count: Int) {
        val currentRetries = retryCounter.incrementAndGet()
        if (currentRetries <= config.maxErrorRetries) {
            val backoffMs = (1000L * currentRetries).coerceAtMost(5000L)
            Log.w(TAG, "Self-healing WAKE_ERROR (attempt $currentRetries/${config.maxErrorRetries}) in ${backoffMs}ms: $errorMessage")
            retryJob = scope.launch {
                delay(backoffMs)
                if (_currentState.value is WakeState.ERROR) {
                    transitionTo(WakeState.INITIALIZING)
                }
            }
        } else {
            Log.e(TAG, "Max retries (${config.maxErrorRetries}) exceeded. Transitioning to WAKE_DISABLED.")
            transitionTo(WakeState.DISABLED)
            onMaxRetriesExceeded?.invoke("Wake-word engine suspended after multiple retries: $errorMessage")
        }
    }

    fun resetRetries() {
        retryCounter.set(0)
    }

    private fun notifyListeners(state: WakeState) {
        synchronized(stateListeners) {
            for (listener in stateListeners) {
                try {
                    listener(state)
                } catch (e: Exception) {
                    Log.e(TAG, "Error notifying WakeState listener", e)
                }
            }
        }
    }

    companion object {
        private const val TAG = "WakeWordStateMachine"
    }
}
