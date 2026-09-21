package com.example.audio

import android.util.Log
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * JitterBuffer
 * 
 * Specialized class for normalizing incoming audio frames and smoothing playback.
 * 1. Buffers variable-sized network audio packets.
 * 2. Applies adaptive timing normalization to reduce artifacts and smooth jitter.
 * 3. Supports dynamic depth adjustment and latency-aware frame shedding.
 */
class JitterBuffer(
    private val tag: String = "JitterBuffer",
    private val initialMinDepth: Int = 1, // Ultra-low latency: start immediately on first chunk
    private val maxDepth: Int = 12  // Max ~240ms buffer
) {
    private val queue = ConcurrentLinkedQueue<ByteArray>()
    private val minDepth = AtomicInteger(1) // Ultra-low latency: start immediately on first chunk
    private val lastPollTime = AtomicLong(0L)
    
    // Adaptive jitter tracking
    private val lastArrivalJitter = AtomicLong(0L)
    private val arrivalIntervals = java.util.concurrent.CopyOnWriteArrayList<Long>()

    companion object {
        private const val FRAME_DURATION_MS = 20L
        private const val MAX_INTERVAL_HISTORY = 10
    }
    
    /**
     * Enqueues an audio packet. If the buffer is full (exceeds maxDepth),
     * it sheds the oldest frame to maintain low latency.
     */
    fun enqueue(packet: ByteArray) {
        if (packet.isEmpty()) return
        
        val now = System.currentTimeMillis()
        if (lastArrivalJitter.get() != 0L) {
            val interval = now - lastArrivalJitter.get()
            arrivalIntervals.add(interval)
            if (arrivalIntervals.size > MAX_INTERVAL_HISTORY) {
                arrivalIntervals.removeAt(0)
            }
            updateAdaptiveDepth()
        }
        lastArrivalJitter.set(now)

        queue.offer(packet)
        
        // Auto-shedding to prevent latency drift (Stay under 200ms target)
        // Aggressive shedding if queue grows too fast to maintain < 20ms-40ms apparent latency
        while (queue.size > maxDepth) {
            queue.poll()
            Log.w(tag, "[JITTER_SHEDDING] Shedding stale frame. Size: ${queue.size}")
        }
    }

    private fun updateAdaptiveDepth() {
        if (arrivalIntervals.isEmpty()) return
        val avgInterval = arrivalIntervals.average()
        // If jitter is high (> 30ms for 20ms frames), increase minDepth to prevent clicks
        if (avgInterval > 35.0) {
            setMinDepth(2)
        } else if (avgInterval < 25.0) {
            setMinDepth(1)
        }
    }
    
    /**
     * Polls the next audio packet for playback.
     * AudioTrack handles its own hardware clocking via WRITE_BLOCKING, so chunks are delivered immediately.
     */
    fun poll(isTurnComplete: Boolean = false): ByteArray? {
        val chunk = queue.poll()
        if (chunk != null) {
            lastPollTime.set(System.currentTimeMillis())
        }
        return chunk
    }
    
    /**
     * Clears the entire buffer. Useful for barge-in or session resets.
     */
    fun clear() {
        queue.clear()
        lastPollTime.set(0L)
        lastArrivalJitter.set(0L)
    }
    
    /**
     * Updates the minimum buffering depth dynamically.
     * Smaller values reduce latency; larger values increase stability against network jitter.
     */
    fun setMinDepth(depth: Int) {
        val normalizedDepth = depth.coerceIn(1, maxDepth)
        minDepth.set(normalizedDepth)
    }
    
    /**
     * Returns the current number of chunks in the buffer.
     */
    fun size(): Int = queue.size
    
    /**
     * Returns current buffer depth in milliseconds (assuming 20ms chunks).
     */
    fun getDepthMs(): Int = queue.size * 20
}
