package com.example.voice.routing

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

enum class AudioRoute(val id: String, val displayName: String) {
    AUTO("auto", "Auto (Smart Route)"),
    SPEAKER("speaker", "Device Speaker"),
    EARPIECE("earpiece", "Earpiece (Receiver)"),
    BLUETOOTH("bluetooth", "Bluetooth Headset"),
    WIRED_HEADSET("wired", "Wired Headset")
}

/**
 * AudioRoutingManager (Alya v2.0.0)
 *
 * Production-ready audio routing manager providing:
 * 1. Seamless transitions between Device Speakers, Bluetooth Headsets (SCO/A2DP/BLE),
 *    Earpieces, and Wired Headsets.
 * 2. Android 12+ (API 31+) CommunicationDevice API support with safe backward compatibility
 *    for Android 10 & 11 (SCO / Speakerphone).
 * 3. Complete Audio Focus lifecycle management tailored for Alya's TTS and Live PCM outputs,
 *    handling transient ducking, gain, and loss.
 * 4. Reactive StateFlows for UI and engine observability.
 */
class AudioRoutingManager(private val context: Context) {

    companion object {
        private const val TAG = "AudioRoutingManager"
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    private val _currentRoute = MutableStateFlow(AudioRoute.SPEAKER)
    val currentRoute: StateFlow<AudioRoute> = _currentRoute.asStateFlow()

    private val _availableRoutes = MutableStateFlow<List<AudioRoute>>(listOf(AudioRoute.SPEAKER, AudioRoute.EARPIECE))
    val availableRoutes: StateFlow<List<AudioRoute>> = _availableRoutes.asStateFlow()

    private val _isBluetoothConnected = MutableStateFlow(false)
    val isBluetoothConnected: StateFlow<Boolean> = _isBluetoothConnected.asStateFlow()

    private val _isWiredHeadsetConnected = MutableStateFlow(false)
    val isWiredHeadsetConnected: StateFlow<Boolean> = _isWiredHeadsetConnected.asStateFlow()

    private val isFocusHeld = AtomicBoolean(false)
    private var focusRequest: AudioFocusRequest? = null
    private var onFocusLossListener: (() -> Unit)? = null

    private var preferredRoute: AudioRoute = AudioRoute.AUTO

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            Log.d(TAG, "Audio devices connected.")
            refreshAvailableDevices()
            applyCurrentRoute()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            Log.d(TAG, "Audio devices removed.")
            refreshAvailableDevices()
            applyCurrentRoute()
        }
    }

    private val hardwareBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_HEADSET_PLUG -> {
                    val state = intent.getIntExtra("state", 0)
                    val isPlugged = state == 1
                    _isWiredHeadsetConnected.value = isPlugged
                    Log.i(TAG, "Headset plug changed: isPlugged=$isPlugged")
                    refreshAvailableDevices()
                    applyCurrentRoute()
                }
                AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED -> {
                    val state = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, AudioManager.SCO_AUDIO_STATE_ERROR)
                    Log.d(TAG, "Bluetooth SCO state changed: $state")
                    if (state == AudioManager.SCO_AUDIO_STATE_CONNECTED) {
                        _isBluetoothConnected.value = true
                    } else if (state == AudioManager.SCO_AUDIO_STATE_DISCONNECTED) {
                        _isBluetoothConnected.value = isBluetoothHeadsetAvailable()
                    }
                    refreshAvailableDevices()
                }
                BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_DISCONNECTED)
                    _isBluetoothConnected.value = state == BluetoothProfile.STATE_CONNECTED
                    Log.i(TAG, "Bluetooth connection state changed: $state")
                    refreshAvailableDevices()
                    applyCurrentRoute()
                }
            }
        }
    }

    init {
        registerListeners()
        refreshAvailableDevices()
        applyCurrentRoute()
    }

    private fun registerListeners() {
        try {
            audioManager.registerAudioDeviceCallback(audioDeviceCallback, null)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register AudioDeviceCallback: ${e.message}")
        }

        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_HEADSET_PLUG)
                addAction(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
                addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
            }
            context.registerReceiver(hardwareBroadcastReceiver, filter)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register hardware BroadcastReceiver: ${e.message}")
        }
    }

    fun unregister() {
        try {
            audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
            context.unregisterReceiver(hardwareBroadcastReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to unregister audio routing listeners: ${e.message}")
        }
        abandonAudioFocus()
    }

    /**
     * Refreshes the list of currently connected and available audio output devices.
     */
    fun refreshAvailableDevices() {
        val routes = mutableListOf(AudioRoute.AUTO, AudioRoute.SPEAKER)

        // Check Earpiece
        if (hasEarpiece()) {
            routes.add(AudioRoute.EARPIECE)
        }

        // Check Wired Headset
        if (isWiredHeadsetAvailable()) {
            routes.add(AudioRoute.WIRED_HEADSET)
            _isWiredHeadsetConnected.value = true
        } else {
            _isWiredHeadsetConnected.value = false
        }

        // Check Bluetooth
        if (isBluetoothHeadsetAvailable()) {
            routes.add(AudioRoute.BLUETOOTH)
            _isBluetoothConnected.value = true
        } else {
            _isBluetoothConnected.value = false
        }

        _availableRoutes.value = routes
        Log.d(TAG, "Available audio routes: ${routes.map { it.id }}")
    }

    private fun hasEarpiece(): Boolean {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        return devices.any { it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
    }

    private fun isWiredHeadsetAvailable(): Boolean {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        return devices.any {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
            it.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }
    }

    private fun isBluetoothHeadsetAvailable(): Boolean {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        return devices.any {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && (it.type == AudioDeviceInfo.TYPE_BLE_HEADSET || it.type == AudioDeviceInfo.TYPE_BLE_SPEAKER))
        }
    }

    /**
     * Sets user desired audio route and transitions immediately.
     */
    fun setAudioRoute(route: AudioRoute) {
        preferredRoute = route
        Log.i(TAG, "Setting audio route to: ${route.displayName}")
        applyCurrentRoute()
    }

    /**
     * Applies routing transition using version-appropriate APIs.
     */
    @Synchronized
    fun applyCurrentRoute() {
        val targetRoute = if (preferredRoute == AudioRoute.AUTO) {
            when {
                isBluetoothHeadsetAvailable() -> AudioRoute.BLUETOOTH
                isWiredHeadsetAvailable() -> AudioRoute.WIRED_HEADSET
                else -> AudioRoute.SPEAKER
            }
        } else {
            preferredRoute
        }

        _currentRoute.value = targetRoute

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Modern Android 12+ CommunicationDevice API
                val devices = audioManager.availableCommunicationDevices
                val matchingDevice = when (targetRoute) {
                    AudioRoute.SPEAKER -> devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                    AudioRoute.EARPIECE -> devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
                    AudioRoute.BLUETOOTH -> devices.firstOrNull {
                        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                        it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                        it.type == AudioDeviceInfo.TYPE_BLE_SPEAKER
                    }
                    AudioRoute.WIRED_HEADSET -> devices.firstOrNull {
                        it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                        it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                        it.type == AudioDeviceInfo.TYPE_USB_HEADSET
                    }
                    else -> null
                }

                if (matchingDevice != null) {
                    val result = audioManager.setCommunicationDevice(matchingDevice)
                    Log.i(TAG, "setCommunicationDevice(${matchingDevice.productName}, type=${matchingDevice.type}) result: $result")
                } else {
                    audioManager.clearCommunicationDevice()
                    Log.d(TAG, "clearCommunicationDevice applied for route: $targetRoute")
                }
            } else {
                // Legacy Android 10 & 11 Routing
                when (targetRoute) {
                    AudioRoute.SPEAKER -> {
                        stopBluetoothScoLegacy()
                        audioManager.isSpeakerphoneOn = true
                    }
                    AudioRoute.EARPIECE -> {
                        stopBluetoothScoLegacy()
                        audioManager.isSpeakerphoneOn = false
                    }
                    AudioRoute.BLUETOOTH -> {
                        audioManager.isSpeakerphoneOn = false
                        startBluetoothScoLegacy()
                    }
                    AudioRoute.WIRED_HEADSET -> {
                        stopBluetoothScoLegacy()
                        audioManager.isSpeakerphoneOn = false
                    }
                    else -> {
                        audioManager.isSpeakerphoneOn = true
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error applying audio route $targetRoute: ${e.message}")
        }
    }

    private fun startBluetoothScoLegacy() {
        try {
            if (!audioManager.isBluetoothScoOn) {
                audioManager.startBluetoothSco()
                audioManager.isBluetoothScoOn = true
                Log.d(TAG, "Bluetooth SCO started (legacy).")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not start Bluetooth SCO: ${e.message}")
        }
    }

    private fun stopBluetoothScoLegacy() {
        try {
            if (audioManager.isBluetoothScoOn) {
                audioManager.isBluetoothScoOn = false
                audioManager.stopBluetoothSco()
                Log.d(TAG, "Bluetooth SCO stopped (legacy).")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not stop Bluetooth SCO: ${e.message}")
        }
    }

    // =========================================================================
    // AUDIO FOCUS MANAGEMENT (TTS & LIVE STREAMING)
    // =========================================================================

    /**
     * Requests Audio Focus for Alya TTS / Live Voice Output.
     * Guarantees background audio is paused or ducked cleanly.
     */
    @Synchronized
    fun requestAudioFocus(onFocusLoss: (() -> Unit)? = null): Boolean {
        onFocusLossListener = onFocusLoss
        if (isFocusHeld.get()) {
            return true
        }

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val playbackAttributes = AudioAttributes.Builder()
                    .apply {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            setUsage(AudioAttributes.USAGE_ASSISTANT)
                        } else {
                            setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        }
                    }
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()

                val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(playbackAttributes)
                    .setAcceptsDelayedFocusGain(true)
                    .setWillPauseWhenDucked(false)
                    .setOnAudioFocusChangeListener { focusChange ->
                        handleFocusChange(focusChange)
                    }
                    .build()

                focusRequest = request
                val res = audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
                isFocusHeld.set(res)
                Log.i(TAG, "Audio focus requested (O+): granted=$res")
                res
            } else {
                @Suppress("DEPRECATION")
                val res = audioManager.requestAudioFocus(
                    { focusChange -> handleFocusChange(focusChange) },
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
                isFocusHeld.set(res)
                Log.i(TAG, "Audio focus requested (legacy): granted=$res")
                res
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire audio focus: ${e.message}")
            false
        }
    }

    private fun handleFocusChange(focusChange: Int) {
        Log.i(TAG, "Audio focus change event: $focusChange")
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                Log.w(TAG, "Audio focus lost ($focusChange).")
                isFocusHeld.set(false)
                try {
                    onFocusLossListener?.invoke()
                } catch (e: Exception) {
                    Log.e(TAG, "Error in onFocusLossListener: ${e.message}")
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                Log.i(TAG, "Audio focus ducking requested.")
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.i(TAG, "Audio focus regained.")
                isFocusHeld.set(true)
            }
        }
    }

    /**
     * Abandons Audio Focus when Alya finishes speaking.
     */
    @Synchronized
    fun abandonAudioFocus() {
        if (!isFocusHeld.getAndSet(false) && focusRequest == null) {
            return
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
                focusRequest = null
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }
            onFocusLossListener = null
            Log.d(TAG, "Audio focus abandoned cleanly.")
        } catch (e: Exception) {
            Log.w(TAG, "Error abandoning audio focus: ${e.message}")
        }
    }

    fun isHoldingAudioFocus(): Boolean = isFocusHeld.get()

    fun ensureAudibleVolume() {
        try {
            val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            if (currentVol == 0 && maxVol > 0) {
                val target = (maxVol * 0.6f).toInt().coerceAtLeast(1)
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
                Log.i(TAG, "Volume unmuted and set to audible baseline: $target/$maxVol")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not adjust stream volume: ${e.message}")
        }
    }
}
