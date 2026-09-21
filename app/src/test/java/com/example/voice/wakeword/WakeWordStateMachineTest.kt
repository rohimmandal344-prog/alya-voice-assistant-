package com.example.voice.wakeword

import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@OptIn(ExperimentalCoroutinesApi::class)
class WakeWordStateMachineTest {

    private lateinit var config: WakeWordConfig
    private lateinit var stateMachine: WakeWordStateMachine
    private var maxRetriesExceededMessage: String? = null

    @Before
    fun setUp() {
        config = WakeWordConfig(
            maxErrorRetries = 3,
            activeTimeoutMs = 1000L,
            cooldownWindowMs = 1500L
        )
        maxRetriesExceededMessage = null
        stateMachine = WakeWordStateMachine(config) { msg ->
            maxRetriesExceededMessage = msg
        }
    }

    @Test
    fun testInitialStateIsDisabled() {
        assertEquals(WakeState.DISABLED, stateMachine.currentState.value)
    }

    @Test
    fun testValidLifecycleTransitions() {
        assertTrue(stateMachine.transitionTo(WakeState.INITIALIZING))
        assertEquals(WakeState.INITIALIZING, stateMachine.currentState.value)

        assertTrue(stateMachine.transitionTo(WakeState.READY("alya")))
        assertEquals(WakeState.READY("alya"), stateMachine.currentState.value)

        assertTrue(stateMachine.transitionTo(WakeState.DETECTED("alya", 0.95f)))
        assertTrue(stateMachine.currentState.value is WakeState.DETECTED)

        assertTrue(stateMachine.transitionTo(WakeState.ACTIVE("alya")))
        assertTrue(stateMachine.currentState.value is WakeState.ACTIVE)

        assertTrue(stateMachine.transitionTo(WakeState.READY("alya")))
        assertEquals(WakeState.READY("alya"), stateMachine.currentState.value)

        assertTrue(stateMachine.transitionTo(WakeState.DISABLED))
        assertEquals(WakeState.DISABLED, stateMachine.currentState.value)
    }

    @Test
    fun testInvalidTransitionRejected() {
        // Cannot transition directly from DISABLED to ACTIVE
        assertFalse(stateMachine.transitionTo(WakeState.ACTIVE("alya")))
        assertEquals(WakeState.DISABLED, stateMachine.currentState.value)

        // Cannot transition directly from DISABLED to DETECTED
        assertFalse(stateMachine.transitionTo(WakeState.DETECTED("alya", 0.9f)))
        assertEquals(WakeState.DISABLED, stateMachine.currentState.value)
    }

    @Test
    fun testErrorTransitionAndRecovery() {
        assertTrue(stateMachine.transitionTo(WakeState.INITIALIZING))
        assertTrue(stateMachine.transitionTo(WakeState.ERROR("Mic busy", 1)))
        assertTrue(stateMachine.currentState.value is WakeState.ERROR)

        // Error can transition back to INITIALIZING to retry
        assertTrue(stateMachine.transitionTo(WakeState.INITIALIZING))
        assertEquals(WakeState.INITIALIZING, stateMachine.currentState.value)
    }

    @Test
    fun testWakeWordConfigThresholds() {
        assertEquals("alia", config.keywords[0])
        assertEquals("alya", config.keywords[1])
        assertEquals("seno", config.keywords[2])
        assertEquals(0.65f, config.confidenceThreshold, 0.001f)
        assertEquals(200L, config.minWordDurationMs)
        assertEquals(1500L, config.cooldownWindowMs)
    }
}
