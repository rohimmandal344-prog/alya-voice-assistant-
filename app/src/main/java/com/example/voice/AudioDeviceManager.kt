package com.example.voice

import android.content.Context
import com.example.voice.routing.AudioRoute
import com.example.voice.routing.AudioRoutingManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * AudioDeviceManager wrapper maintaining backward compatibility and providing direct access
 * to the underlying [AudioRoutingManager].
 */
class AudioDeviceManager(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    val routingManager = AudioRoutingManager(context)

    val currentAudioRoute: StateFlow<AudioRoute> = routingManager.currentRoute
    val availableAudioRoutes: StateFlow<List<AudioRoute>> = routingManager.availableRoutes
    val isBluetoothConnectedFlow: StateFlow<Boolean> = routingManager.isBluetoothConnected
    val isWiredHeadsetConnectedFlow: StateFlow<Boolean> = routingManager.isWiredHeadsetConnected

    val isSpeakerOn: StateFlow<Boolean> = routingManager.currentRoute
        .map { it == AudioRoute.SPEAKER }
        .stateIn(scope, SharingStarted.Eagerly, true)

    fun requestAudioFocus(onFocusLoss: (() -> Unit)? = null): Boolean {
        return routingManager.requestAudioFocus(onFocusLoss)
    }

    fun abandonAudioFocus() {
        routingManager.abandonAudioFocus()
    }

    fun isHoldingAudioFocus(): Boolean = routingManager.isHoldingAudioFocus()

    fun setAudioRoute(route: AudioRoute) {
        routingManager.setAudioRoute(route)
    }

    fun setSpeakerphone(enable: Boolean) {
        if (enable) {
            routingManager.setAudioRoute(AudioRoute.SPEAKER)
        } else {
            routingManager.setAudioRoute(AudioRoute.EARPIECE)
        }
    }

    fun toggleSpeakerphone(): Boolean {
        val current = routingManager.currentRoute.value
        val next = if (current == AudioRoute.SPEAKER) AudioRoute.EARPIECE else AudioRoute.SPEAKER
        routingManager.setAudioRoute(next)
        return next == AudioRoute.SPEAKER
    }

    fun isBluetoothConnected(): Boolean {
        return routingManager.isBluetoothConnected.value
    }

    fun ensureAudibleVolume() {
        routingManager.ensureAudibleVolume()
    }

    fun onDestroy() {
        routingManager.unregister()
    }
}
