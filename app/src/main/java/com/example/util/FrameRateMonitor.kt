package com.example.util

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Choreographer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State

/**
 * Real-time FrameRateMonitor to ensure UI stays within 60-120 FPS.
 */
class FrameRateMonitor : Choreographer.FrameCallback {
    private val choreographer = Choreographer.getInstance()
    private var lastFrameTimeNanos: Long = 0
    
    private val _fps = mutableStateOf(0f)
    val fps: State<Float> = _fps

    private var frameCount = 0
    private var startTimeNanos: Long = 0

    fun start() {
        choreographer.postFrameCallback(this)
    }

    fun stop() {
        choreographer.removeFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (lastFrameTimeNanos != 0L) {
            val frameTimeDiff = frameTimeNanos - lastFrameTimeNanos
            val currentFps = 1_000_000_000f / frameTimeDiff
            
            // Smoothed average over 1 second
            if (startTimeNanos == 0L) startTimeNanos = frameTimeNanos
            frameCount++
            
            val elapsed = frameTimeNanos - startTimeNanos
            if (elapsed >= 1_000_000_000L) {
                _fps.value = (frameCount * 1_000_000_000f) / elapsed
                if (_fps.value < 55f) {
                    Log.w("Performance", "Dropped frame detected! Current FPS: ${_fps.value}")
                }
                frameCount = 0
                startTimeNanos = frameTimeNanos
            }
        }
        lastFrameTimeNanos = frameTimeNanos
        choreographer.postFrameCallback(this)
    }
}
