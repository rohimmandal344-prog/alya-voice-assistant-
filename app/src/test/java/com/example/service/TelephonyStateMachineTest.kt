package com.example.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TelephonyStateMachineTest {

    private lateinit var stateMachine: TelephonyStateMachine

    @Before
    fun setUp() {
        stateMachine = TelephonyStateMachine(mandatoryCooldownMs = 5000L)
    }

    @Test
    fun testRingingGeneratesCallSessionId() {
        assertEquals(CallState.IDLE, stateMachine.currentState)
        assertNull(stateMachine.callSessionId)

        val result = stateMachine.transition(CallState.RINGING)
        assertTrue(result is TransitionResult.Success)
        val success = result as TransitionResult.Success
        assertEquals(CallState.IDLE, success.previousState)
        assertEquals(CallState.RINGING, success.newState)
        assertNotNull(success.sessionId)
        assertEquals(success.sessionId, stateMachine.callSessionId)
        assertTrue(stateMachine.isCallActiveFlow.value)
    }

    @Test
    fun testMandatory5SecondCooldownBlocksRapidTransitions() {
        val ringResult = stateMachine.transition(CallState.RINGING)
        assertTrue(ringResult is TransitionResult.Success)

        // Attempting immediate state change to OFFHOOK within 5 seconds must be throttled
        val rapidResult = stateMachine.transition(CallState.OFFHOOK)
        assertTrue(rapidResult is TransitionResult.Throttled)
        val throttled = rapidResult as TransitionResult.Throttled
        assertEquals(CallState.RINGING, throttled.currentState)
        assertEquals(CallState.OFFHOOK, throttled.attemptedState)
        assertTrue(throttled.remainingCooldownMs > 0)
        assertEquals(CallState.RINGING, stateMachine.currentState)
    }

    @Test
    fun testCallTerminationClearsSessionId() {
        // Use a test state machine with 0ms cooldown to test sequential transitions
        val fastStateMachine = TelephonyStateMachine(mandatoryCooldownMs = 0L)
        fastStateMachine.transition(CallState.RINGING)
        val activeSessionId = fastStateMachine.callSessionId
        assertNotNull(activeSessionId)

        fastStateMachine.transition(CallState.OFFHOOK)
        assertEquals(activeSessionId, fastStateMachine.callSessionId)

        fastStateMachine.transition(CallState.IDLE)
        assertNull(fastStateMachine.callSessionId)
        assertEquals(CallState.IDLE, fastStateMachine.currentState)
        assertEquals(false, fastStateMachine.isCallActiveFlow.value)
    }
}
