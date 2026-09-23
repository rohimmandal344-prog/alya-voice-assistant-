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
 * 3. Uses byte-based capacity calculation instead of raw packet count to avoid
 *    premature shedding on small packet sizes (e.g., from network framing).
 */
class JitterBuffer(
    private val tag: String = "JitterBuffer",
    private val initialMinDepth: Int = 1,
    private val maxDepth: Int = 12
) {
    private val queue = ConcurrentLinkedQueue<ByteArray>()
    private val minDepth = AtomicInteger(1)
    private val lastPollTime = AtomicLong(0L)
    
    // Precise byte count tracker for 24kHz 16-bit Mono PCM (48,000 bytes/sec)
    private val totalBytes = AtomicLong(0L)
    
    // Maximum bytes to buffer (~300ms max buffer depth to prevent latency drift)
    private val maxBufferBytes = 14400L // 0.3s * 48000 bytes/sec
    
    // Adaptive jitter tracking
    private val lastArrivalJitter = AtomicLong(0L)
    private val arrivalIntervals = java.util.concurrent.CopyOnWriteArrayList<Long>()

    companion object {
        private const val FRAME_DURATION_MS = 20L
        private const val MAX_INTERVAL_HISTORY = 10
    }
    
    /**
     * Enqueues an audio packet. If the buffer is full (exceeds maxBufferBytes),
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
        totalBytes.addAndGet(packet.size.toLong())
        
        // Auto-shedding based on total buffered audio duration (bytes) instead of packet count.
        // This ensures small network MTU slices are never discarded prematurely.
        while (totalBytes.get() > maxBufferBytes && queue.isNotEmpty()) {
            val discarded = queue.poll()
            if (discarded != null) {
                val newBytes = totalBytes.addAndGet(-discarded.size.toLong())
                Log.w(tag, "[JITTER_SHEDDING] Shedding stale frame to maintain <300ms latency. Buffered bytes: $newBytes")
            } else {
                break
            }
        }
    }

    private fun updateAdaptiveDepth() {
        if (arrivalIntervals.isEmpty()) return
        val avgInterval = arrivalIntervals.average()
        if (avgInterval > 35.0) {
            setMinDepth(2)
        } else if (avgInterval < 25.0) {
            setMinDepth(1)
        }
    }
    
    /**
     * Polls the next audio packet for playback.
     * Enforces prebuffering threshold unless the server has finished sending the turn.
     */
    fun poll(isTurnComplete: Boolean = false): ByteArray? {
        if (!isTurnComplete && queue.size < minDepth.get()) {
            return null
        }
        val chunk = queue.poll()
        if (chunk != null) {
            totalBytes.addAndGet(-chunk.size.toLong())
            lastPollTime.set(System.currentTimeMillis())
        }
        return chunk
    }
    
    /**
     * Clears the entire buffer. Useful for barge-in or session resets.
     */
    fun clear() {
        queue.clear()
        totalBytes.set(0L)
        lastPollTime.set(0L)
        lastArrivalJitter.set(0L)
    }
    
    /**
     * Updates the minimum buffering depth dynamically.
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
     * Returns current buffer depth in milliseconds (assuming 24kHz 16-bit Mono PCM).
     */
    fun getDepthMs(): Int {
        // 48000 bytes per second = 48 bytes per millisecond
        return (totalBytes.get() / 48).toInt()
    }
}
