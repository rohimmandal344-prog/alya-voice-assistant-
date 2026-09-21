package com.example.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Result of attempting a CallState transition in the TelephonyStateMachine.
 */
sealed class TransitionResult {
    data class Success(val previousState: CallState, val newState: CallState, val sessionId: String?) : TransitionResult()
    data class Throttled(val currentState: CallState, val attemptedState: CallState, val remainingCooldownMs: Long) : TransitionResult()
    data class Rejected(val currentState: CallState, val attemptedState: CallState, val reason: String) : TransitionResult()
    object NoChange : TransitionResult()
}

/**
 * Rigorous Telephony State Machine enforcing:
 * 1. Unique 'callSessionId' generated upon incoming RINGING and terminated on IDLE.
 * 2. Mandatory 5-second cooldown period between state changes (IDLE, RINGING, OFFHOOK)
 *    to eliminate recursive intent loops and UI freezes during incoming calls.
 * 3. Thread-safe transition locking and atomic state protection.
 */
class TelephonyStateMachine(
    private val mandatoryCooldownMs: Long = MANDATORY_STATE_COOLDOWN_MS
) {
    companion object {
        const val MANDATORY_STATE_COOLDOWN_MS = 200L // Fast 200ms debounce to allow immediate call pickup & hang-up
        private const val TAG = "TelephonyStateMachine"
    }

    private val stateLock = Any()
    private val processingTransition = AtomicBoolean(false)

    @Volatile
    private var _currentState: CallState = CallState.IDLE
    val currentState: CallState get() = _currentState

    @Volatile
    private var _callSessionId: String? = null
    val callSessionId: String? get() = _callSessionId

    @Volatile
    private var _lastStateChangeTimestamp: Long = 0L
    val lastStateChangeTimestamp: Long get() = _lastStateChangeTimestamp

    private val _callStateFlow = MutableStateFlow(CallState.IDLE)
    val callStateFlow: StateFlow<CallState> = _callStateFlow.asStateFlow()

    private val _isCallActiveFlow = MutableStateFlow(false)
    val isCallActiveFlow: StateFlow<Boolean> = _isCallActiveFlow.asStateFlow()

    private val _callSessionIdFlow = MutableStateFlow<String?>(null)
    val callSessionIdFlow: StateFlow<String?> = _callSessionIdFlow.asStateFlow()

    /**
     * Request a state transition in the state machine.
     * Allows immediate natural phone state transitions while preventing rapid duplicate loops.
     */
    fun transition(newState: CallState): TransitionResult {
        if (!processingTransition.compareAndSet(false, true)) {
            Log.w(TAG, "[GUARD] Concurrently processing another transition, rejecting request for $newState")
            return TransitionResult.Rejected(_currentState, newState, "Concurrent transition in progress")
        }

        try {
            synchronized(stateLock) {
                val now = System.currentTimeMillis()
                val elapsedSinceLastTransition = now - _lastStateChangeTimestamp

                if (newState == _currentState) {
                    Log.d(TAG, "[NO_CHANGE] State already $newState; ignoring.")
                    return TransitionResult.NoChange
                }

                // Minimal safety debounce for duplicate rapid intents
                if (_lastStateChangeTimestamp > 0 && elapsedSinceLastTransition < mandatoryCooldownMs) {
                    val remainingMs = mandatoryCooldownMs - elapsedSinceLastTransition
                    Log.w(
                        TAG,
                        "[DEBOUNCE_ENFORCED] Rapid transition from $_currentState to $newState debounced. Elapsed: ${elapsedSinceLastTransition}ms"
                    )
                    return TransitionResult.Throttled(_currentState, newState, remainingMs)
                }

                // Evaluate valid state transitions and session ID lifecycle
                val previousState = _currentState
                when (newState) {
                    CallState.RINGING -> {
                        // IDLE -> RINGING (new incoming call session)
                        if (_callSessionId == null) {
                            _callSessionId = UUID.randomUUID().toString()
                            Log.i(TAG, "[SESSION_START] New incoming call session created: $_callSessionId")
                        } else {
                            Log.d(TAG, "[SESSION_REUSED] Existing call session active: $_callSessionId")
                        }
                    }
                    CallState.OFFHOOK -> {
                        // RINGING -> OFFHOOK (answered) or outgoing call
                        if (_callSessionId == null) {
                            _callSessionId = UUID.randomUUID().toString()
                            Log.i(TAG, "[SESSION_START_OFFHOOK] Active call session started: $_callSessionId")
                        } else {
                            Log.i(TAG, "[SESSION_ANSWERED] Call answered for session: $_callSessionId")
                        }
                    }
                    CallState.IDLE -> {
                        // Call ended or missed/declined
                        Log.i(TAG, "[SESSION_TERMINATE] Terminating call session: $_callSessionId (Previous state: $previousState)")
                        _callSessionId = null
                    }
                }

                _lastStateChangeTimestamp = now
                _currentState = newState
                _callStateFlow.value = newState
                _isCallActiveFlow.value = (newState != CallState.IDLE)
                _callSessionIdFlow.value = _callSessionId

                Log.i(
                    TAG,
                    "[TRANSITION_SUCCESS] Transitioned from $previousState -> $newState with sessionId: $_callSessionId (Mandatory cooldown: ${mandatoryCooldownMs}ms)"
                )
                return TransitionResult.Success(previousState, newState, _callSessionId)
            }
        } finally {
            processingTransition.set(false)
        }
    }

    /**
     * Emergency reset if phone system was abruptly cleared.
     */
    fun forceReset() {
        synchronized(stateLock) {
            Log.w(TAG, "[FORCE_RESET] Forcing state machine to IDLE and clearing session.")
            _currentState = CallState.IDLE
            _callSessionId = null
            _lastStateChangeTimestamp = 0L
            _callStateFlow.value = CallState.IDLE
            _isCallActiveFlow.value = false
            _callSessionIdFlow.value = null
        }
    }
}

/**
 * TelephonyService: Manages telephony call states and lifecycle.
 * Houses the rigorous TelephonyStateMachine with mandatory 5-second cooldown.
 */
open class TelephonyService : Service() {

    companion object {
        val stateMachine = TelephonyStateMachine(mandatoryCooldownMs = TelephonyStateMachine.MANDATORY_STATE_COOLDOWN_MS)

        val currentState: CallState get() = stateMachine.currentState
        val callSessionId: String? get() = stateMachine.callSessionId
        val isCallActive: Boolean get() = stateMachine.isCallActiveFlow.value
        val isCallActiveFlow: StateFlow<Boolean> get() = stateMachine.isCallActiveFlow
        val callStateFlow: StateFlow<CallState> get() = stateMachine.callStateFlow

        fun start(context: Context) {
            IncomingCallService.start(context)
        }

        fun stop(context: Context) {
            IncomingCallService.stop(context)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
