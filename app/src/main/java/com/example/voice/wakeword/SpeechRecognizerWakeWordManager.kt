package com.example.voice.wakeword

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SpeechRecognizerWakeWordManager(private val context: Context) : WakeWordManager {
    private val detector = SpeechRecognizerHotwordDetector(context)
    private val _isListening = MutableStateFlow(false)
    private val listeners = mutableListOf<(String) -> Unit>()
    
    override val isListening: StateFlow<Boolean> = _isListening.asStateFlow()
    override val state: StateFlow<WakeState> = MutableStateFlow<WakeState>(WakeState.DISABLED).asStateFlow()
    override var isSuppressed: Boolean = false
    override var isTtsSpeaking: Boolean = false
    override var onBargeInDetected: (() -> Unit)? = null
    
    private var currentKeyword = TriggerWordConfig.DEFAULT_TRIGGER_WORD

    override fun start(keyword: String, sensitivity: Float): Boolean {
        if (isSuppressed || isTtsSpeaking) return false
        if (_isListening.value) return true
        
        currentKeyword = keyword
        _isListening.value = true
        detector.startDetection(keyword) {
            notifyListeners(keyword)
            _isListening.value = false // Auto stop when detected
        }
        return true
    }

    override fun stop() {
        _isListening.value = false
        detector.stopDetection()
    }

    override fun disposeNativeResources() {
        stop()
        detector.destroy()
    }

    override fun addWakeWordListener(listener: (String) -> Unit) {
        synchronized(listeners) {
            if (!listeners.contains(listener)) listeners.add(listener)
        }
    }

    override fun removeWakeWordListener(listener: (String) -> Unit) {
        synchronized(listeners) {
            listeners.remove(listener)
        }
    }

    private fun notifyListeners(keyword: String) {
        synchronized(listeners) {
            listeners.forEach { it.invoke(keyword) }
        }
    }

    override fun updateConfiguredKeyword(newKeyword: String) {
        currentKeyword = newKeyword
        if (_isListening.value) {
            stop()
            start(newKeyword, 0.5f)
        }
    }

    override fun clearAudioBuffer() {}
    override fun release() {
        disposeNativeResources()
    }
    override fun setBatterySaverEnabled(enabled: Boolean) {}
    override fun setDeviceIdlePowerMode(enabled: Boolean) {}
    override fun notifyUserActivity() {}
    override fun setBargeInEnabled(enabled: Boolean) {}
    override fun getRecentAudio(durationMs: Int): ShortArray = ShortArray(0)
    override fun onAppBackgrounded(allowBackground: Boolean) {}
    override fun onAppForegrounded(isEnabled: Boolean, kw: String, sens: Float) {
        if (isEnabled) start(kw, sens)
    }
}
