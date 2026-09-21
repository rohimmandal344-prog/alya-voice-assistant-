package com.example.voice.error

import android.content.Context
import android.media.AudioRecord
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.SpeechRecognizer
import android.util.Log
import com.example.util.diagnostics.DiagnosticLogManager
import com.example.util.diagnostics.DiagnosticStage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Centralized Error Recovery Handler for Alya.
 * 
 * Maps SpeechRecognizer and AudioRecord hardware errors (e.g. ERROR_RECOGNIZER_BUSY,
 * ERROR_INSUFFICIENT_PERMISSIONS, STATE_UNINITIALIZED) to friendly conversational messages,
 * and handles seamless auto-resume via ConnectivityManager network callbacks.
 */
class ErrorRecoveryHandler private constructor(private val context: Context) {

    companion object {
        private const val TAG = "ErrorRecoveryHandler"

        @Volatile
        private var INSTANCE: ErrorRecoveryHandler? = null

        fun getInstance(context: Context): ErrorRecoveryHandler {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ErrorRecoveryHandler(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _lastErrorMessage = MutableStateFlow<String?>(null)
    val lastErrorMessage: StateFlow<String?> = _lastErrorMessage.asStateFlow()

    private val _isAutoResuming = MutableStateFlow(false)
    val isAutoResuming: StateFlow<Boolean> = _isAutoResuming.asStateFlow()

    // Registered auto-resume callbacks keyed by identifier (e.g., "speech_manager", "live_session")
    private val autoResumeCallbacks = ConcurrentHashMap<String, () -> Unit>()

    private var wasOfflineWaitingToResume = false
    private var isNetworkCallbackRegistered = false

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            mainHandler.post {
                _isOnline.value = true
                Log.i(TAG, "[NETWORK_CALLBACK] Internet connection available.")
                if (wasOfflineWaitingToResume) {
                    triggerAutoResume()
                }
            }
        }

        override fun onLost(network: Network) {
            mainHandler.post {
                _isOnline.value = false
                wasOfflineWaitingToResume = true
                Log.w(TAG, "[NETWORK_CALLBACK] Internet connection lost. Queuing auto-resume on reconnect.")
            }
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            mainHandler.post {
                _isOnline.value = hasInternet
                if (hasInternet && wasOfflineWaitingToResume) {
                    triggerAutoResume()
                }
            }
        }
    }

    init {
        registerNetworkCallback()
    }

    private fun registerNetworkCallback() {
        if (isNetworkCallbackRegistered) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                connectivityManager?.registerDefaultNetworkCallback(networkCallback)
            } else {
                val request = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
                connectivityManager?.registerNetworkCallback(request, networkCallback)
            }
            isNetworkCallbackRegistered = true
            Log.i(TAG, "ConnectivityManager callback registered successfully for auto-resume.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register network callback: ${e.message}", e)
        }
    }

    fun unregisterNetworkCallback() {
        if (!isNetworkCallbackRegistered) return
        try {
            connectivityManager?.unregisterNetworkCallback(networkCallback)
            isNetworkCallbackRegistered = false
            Log.i(TAG, "ConnectivityManager callback unregistered.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister network callback: ${e.message}", e)
        }
    }

    /**
     * Registers a callback to be invoked automatically when connectivity is restored.
     */
    fun registerAutoResumeCallback(key: String, callback: () -> Unit) {
        autoResumeCallbacks[key] = callback
        Log.d(TAG, "Registered auto-resume callback for: $key")
    }

    /**
     * Unregisters an auto-resume callback.
     */
    fun unregisterAutoResumeCallback(key: String) {
        autoResumeCallbacks.remove(key)
        Log.d(TAG, "Unregistered auto-resume callback for: $key")
    }

    /**
     * Manually triggers auto-resume for all registered listeners.
     */
    fun triggerAutoResume() {
        wasOfflineWaitingToResume = false
        _isAutoResuming.value = true
        Log.i(TAG, "Auto-resuming voice tasks after network recovery (${autoResumeCallbacks.size} listeners)...")
        DiagnosticLogManager.instance.logEvent(
            stage = DiagnosticStage.NETWORK,
            command = "Network Auto-Resume",
            details = "Network re-established. Auto-resuming voice services.",
            isSuccess = true
        )

        mainHandler.postDelayed({
            autoResumeCallbacks.values.forEach { cb ->
                try {
                    cb.invoke()
                } catch (e: Exception) {
                    Log.e(TAG, "Error executing auto-resume callback: ${e.message}", e)
                }
            }
            _isAutoResuming.value = false
        }, 300L) // Small delay to let network stack settle
    }

    /**
     * Maps SpeechRecognizer error codes to user-friendly messages and determines recovery strategy.
     */
    fun mapSpeechRecognizerError(
        errorCode: Int,
        onRetry: (() -> Unit)? = null,
        onFallbackOffline: (() -> Unit)? = null
    ): String {
        val userFriendlyMessage: String
        val recoveryAction: String
        var autoRetryDelayMs = 0L

        when (errorCode) {
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                userFriendlyMessage = "Voice recognizer is busy. Resetting and resuming in a moment..."
                recoveryAction = "AUTO_RETRY"
                autoRetryDelayMs = 600L
            }
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                userFriendlyMessage = "Microphone permission is required to talk with Alya. Please grant access in settings."
                recoveryAction = "REQUEST_PERMISSION"
            }
            SpeechRecognizer.ERROR_AUDIO -> {
                userFriendlyMessage = "Microphone recording failed. Check if another app is using the mic."
                recoveryAction = "CHECK_HARDWARE"
            }
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> {
                wasOfflineWaitingToResume = true
                userFriendlyMessage = "Network connection lost. Seamlessly running offline. Will auto-resume when online."
                recoveryAction = "FALLBACK_OFFLINE_AND_WAIT"
                onFallbackOffline?.invoke()
            }
            SpeechRecognizer.ERROR_NO_MATCH -> {
                userFriendlyMessage = "I didn't quite catch that. Please speak again."
                recoveryAction = "RETRY_SPEAKING"
            }
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                userFriendlyMessage = "Listening timed out. Tap the microphone when you're ready."
                recoveryAction = "PROMPT_USER"
            }
            SpeechRecognizer.ERROR_CLIENT -> {
                userFriendlyMessage = "Speech service encountered a client glitch. Re-aligning voice stream..."
                recoveryAction = "AUTO_RETRY"
                autoRetryDelayMs = 400L
            }
            SpeechRecognizer.ERROR_SERVER -> {
                userFriendlyMessage = "Speech server error. Retrying with local speech engine..."
                recoveryAction = "FALLBACK_OFFLINE"
                onFallbackOffline?.invoke()
            }
            11 -> { // Server disconnected or offline recognizer fallback
                userFriendlyMessage = "Connecting speech service in the background. Please continue speaking."
                recoveryAction = "FALLBACK_OFFLINE"
                onFallbackOffline?.invoke()
            }
            13 -> { // Language pack missing
                userFriendlyMessage = "Selected language pack is not available offline. Using English fallback."
                recoveryAction = "DOWNLOAD_LANGUAGE_PACK"
            }
            else -> {
                userFriendlyMessage = "Speech service error ($errorCode). Tap to try again."
                recoveryAction = "PROMPT_USER"
            }
        }

        _lastErrorMessage.value = userFriendlyMessage

        // Log to diagnostics engine
        DiagnosticLogManager.instance.logEvent(
            stage = DiagnosticStage.RESULT,
            command = "SpeechRecognizer Error $errorCode",
            details = "$userFriendlyMessage (Action: $recoveryAction)",
            isSuccess = false,
            failureCode = "SPEECH_ERR_$errorCode"
        )

        // Handle auto-retry if requested
        if (autoRetryDelayMs > 0 && onRetry != null) {
            mainHandler.postDelayed({
                Log.i(TAG, "Executing auto-recovery retry for SpeechRecognizer error $errorCode...")
                onRetry.invoke()
            }, autoRetryDelayMs)
        }

        return userFriendlyMessage
    }

    /**
     * Maps AudioRecord errors to user-friendly messages.
     */
    fun mapAudioRecordError(
        status: Int,
        onReinitialize: (() -> Unit)? = null
    ): String {
        val message = when (status) {
            AudioRecord.STATE_UNINITIALIZED -> {
                "Microphone buffer failed to initialize. Re-allocating audio stream..."
            }
            AudioRecord.ERROR_BAD_VALUE -> {
                "Invalid audio configuration. Resetting to 16kHz standard audio..."
            }
            AudioRecord.ERROR_INVALID_OPERATION -> {
                "Microphone hardware is busy. Resetting audio session..."
            }
            AudioRecord.ERROR -> {
                "Generic microphone capture error occurred. Attempting restart..."
            }
            else -> {
                "Microphone capture status: $status. Checking hardware..."
            }
        }

        _lastErrorMessage.value = message

        DiagnosticLogManager.instance.logEvent(
            stage = DiagnosticStage.RESULT,
            command = "AudioRecord Error $status",
            details = message,
            isSuccess = false,
            failureCode = "AUDIO_REC_ERR_$status"
        )

        if (onReinitialize != null) {
            mainHandler.postDelayed({
                try {
                    onReinitialize.invoke()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to reinitialize AudioRecord: ${e.message}", e)
                }
            }, 500L)
        }

        return message
    }

    fun clearError() {
        _lastErrorMessage.value = null
    }
}
