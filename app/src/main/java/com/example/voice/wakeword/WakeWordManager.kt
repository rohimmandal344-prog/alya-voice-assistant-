package com.example.voice.wakeword

import kotlinx.coroutines.flow.StateFlow

/**
 * Common interface for wake-word detection engines.
 */
interface WakeWordManager {
    val state: StateFlow<WakeState>
    var isSuppressed: Boolean
    val isListening: StateFlow<Boolean>
    var isTtsSpeaking: Boolean
    
    var onBargeInDetected: (() -> Unit)?

    fun start(keyword: String, sensitivity: Float): Boolean
    fun stop()
    fun disposeNativeResources()
    fun addWakeWordListener(listener: (String) -> Unit)
    fun removeWakeWordListener(listener: (String) -> Unit)
    fun updateConfiguredKeyword(newKeyword: String)
    fun clearAudioBuffer()
    fun release()
    fun setBatterySaverEnabled(enabled: Boolean)
    fun setDeviceIdlePowerMode(enabled: Boolean)
    fun notifyUserActivity()
    fun setBargeInEnabled(enabled: Boolean)
    fun getRecentAudio(durationMs: Int): ShortArray
    fun onAppBackgrounded(allowBackground: Boolean)
    fun onAppForegrounded(isEnabled: Boolean, kw: String, sens: Float)
}
