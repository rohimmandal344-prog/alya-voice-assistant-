package com.example.voice.vad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.sin

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RealtimeVoiceActivityDetectorTest {

    private lateinit var vad: RealtimeVoiceActivityDetector
    private var currentTime = 1000000L

    @Before
    fun setUp() {
        currentTime = 1000000L
        vad = RealtimeVoiceActivityDetector(
            sampleRate = 16000,
            minVoiceEnergyDb = 34.0f,
            minSnrDb = 7.0f,
            speechOnsetFramesRequired = 2,
            silenceHangoverMs = 80L, // 80ms for fast test execution
            bargeInThresholdDb = 38.0f,
            timeProvider = { currentTime }
        )
    }

    private fun generateSineFrame(frequency: Double, amplitude: Double, frameSize: Int = 320): ShortArray {
        return ShortArray(frameSize) { i ->
            (sin(2.0 * Math.PI * frequency * i / 16000.0) * amplitude).toInt().toShort()
        }
    }

    private fun generateSilenceFrame(frameSize: Int = 320): ShortArray {
        return ShortArray(frameSize) { 0 }
    }

    private fun generateLowNoiseFrame(frameSize: Int = 320): ShortArray {
        return ShortArray(frameSize) { i ->
            (if (i % 2 == 0) 10 else -10).toShort()
        }
    }

    @Test
    fun testSilenceDoesNotTriggerSpeech() {
        var speechStartedCount = 0
        vad.onSpeechStarted = { speechStartedCount++ }

        val silence = generateSilenceFrame()
        for (i in 0 until 10) {
            val isSpeech = vad.processFrame(silence, silence.size)
            assertFalse("Silence frame $i should not trigger speech", isSpeech)
        }

        assertEquals(0, speechStartedCount)
        assertEquals(VadState.SILENCE, vad.vadState.value)
    }

    @Test
    fun testAmbientNoiseDoesNotTriggerSpeech() {
        var speechStartedCount = 0
        vad.onSpeechStarted = { speechStartedCount++ }

        val noise = generateLowNoiseFrame()
        for (i in 0 until 20) {
            val isSpeech = vad.processFrame(noise, noise.size)
            assertFalse("Low ambient noise should not trigger speech", isSpeech)
        }

        assertEquals(0, speechStartedCount)
        assertEquals(VadState.SILENCE, vad.vadState.value)
    }

    @Test
    fun testVocalFrameTriggersSpeechAndOnsetHysteresis() {
        var speechStartedCalled = false
        vad.onSpeechStarted = { speechStartedCalled = true }

        val speechFrame = generateSineFrame(300.0, 4000.0) // 300Hz vocal formant

        // First frame: POTENTIAL_SPEECH
        vad.processFrame(speechFrame, speechFrame.size)
        // Second frame: reaches onsetHangoverFrames (2) -> transitions to SPEECH
        val isSpeech = vad.processFrame(speechFrame, speechFrame.size)

        assertTrue("Speech should be detected after onset hangover", isSpeech)
        assertTrue("onSpeechStarted callback should have fired", speechStartedCalled)
        assertEquals(VadState.SPEECH_ACTIVE, vad.vadState.value)
    }

    @Test
    fun testTurnTakingAndSilenceHangoverTriggerCompletion() {
        var turnCompleteTriggered = false
        var recordedSpeechDuration = 0L
        vad.onTurnComplete = { speechDuration, _ ->
            turnCompleteTriggered = true
            recordedSpeechDuration = speechDuration
        }

        val speechFrame = generateSineFrame(400.0, 5000.0)
        // Trigger speech (3 frames = 60ms)
        repeat(3) {
            currentTime += 20L
            vad.processFrame(speechFrame, speechFrame.size)
        }
        assertTrue("VAD should be in speech", vad.isSpeechActive.value)

        // Now follow with silence frames to exceed silenceHangoverMs (80ms: 5 frames = 100ms)
        val silence = generateSilenceFrame()
        repeat(5) {
            currentTime += 20L
            vad.processFrame(silence, silence.size)
        }

        assertFalse("VAD should have transitioned out of speech", vad.isSpeechActive.value)
        assertTrue("onTurnComplete callback should have been triggered", turnCompleteTriggered)
        assertTrue("Speech duration should be > 0", recordedSpeechDuration > 0)
        assertEquals(VadState.SILENCE, vad.vadState.value)
    }

    @Test
    fun testBargeInDetectionDuringAssistantPlayback() {
        var bargeInTriggered = false
        var bargeInRms = 0f
        vad.onBargeInTriggered = { rms ->
            bargeInTriggered = true
            bargeInRms = rms
        }

        vad.setAssistantSpeaking(true)

        // Loud vocal speech frame (e.g. 5000 amplitude)
        val loudSpeech = generateSineFrame(350.0, 6000.0)

        // Process 2 frames to satisfy bargeInHangoverFrames (2)
        vad.processFrame(loudSpeech, loudSpeech.size)
        vad.processFrame(loudSpeech, loudSpeech.size)

        assertTrue("Barge-in should have been triggered during assistant speaking", bargeInTriggered)
        assertTrue("Barge-in RMS should exceed barge-in threshold", bargeInRms >= 38.0f)
    }

    @Test
    fun testResetClearsAllInternalState() {
        val speechFrame = generateSineFrame(400.0, 5000.0)
        repeat(3) {
            vad.processFrame(speechFrame, speechFrame.size)
        }
        assertTrue(vad.isSpeechActive.value)

        vad.reset()

        assertFalse(vad.isSpeechActive.value)
        assertEquals(VadState.SILENCE, vad.vadState.value)
    }
}
