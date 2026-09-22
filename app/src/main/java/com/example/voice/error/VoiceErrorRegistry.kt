package com.example.voice.error

import android.speech.SpeechRecognizer
import android.util.Log
import com.example.util.diagnostics.DiagnosticLogManager
import com.example.util.diagnostics.DiagnosticStage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Classifies speech, audio recording, and voice synthesis errors in Alya.
 */
enum class VoiceErrorType {
    AUDIO_RECORD_INIT_FAILED,
    SECURITY_PERMISSION_DENIED,
    RECOGNIZER_BUSY,
    RECOGNIZER_NO_MATCH,
    RECOGNIZER_TIMEOUT,
    RECOGNIZER_NETWORK_ERROR,
    RECOGNIZER_GENERIC_FAILURE,
    TTS_INITIALIZATION_ERROR,
    AUDIO_TRACK_PLAYBACK_ERROR
}

/**
 * Severity levels for active voice errors.
 */
enum class VoiceErrorSeverity {
    FATAL,      // Blocks operational execution completely until resolved
    WARNING     // Intermittent, transient, or auto-recoverable issue
}

/**
 * Rich domain model representing a concrete voice error event.
 * Integrates both old and new properties to maintain backward-compatibility
 * while satisfying the specified error contract.
 */
data class VoiceError(
    val type: VoiceErrorType,
    val message: String,
    val suggestedAction: String,
    val severity: VoiceErrorSeverity,
    val timestamp: Long = System.currentTimeMillis(),
    val code: Int = 0,
    val userMessage: String = message,
    val isNetworkRelated: Boolean = (type == VoiceErrorType.RECOGNIZER_NETWORK_ERROR),
    val recoverable: Boolean = (severity == VoiceErrorSeverity.WARNING)
)

/**
 * VoiceErrorRegistry (Alya v3.2.0)
 * 
 * Central registry that monitors, classifies, and manages all speech recognition,
 * microphone hardware, and audio playback errors in Alya.
 * Dynamically tracks offline/online state and integrates with system callbacks.
 */
class VoiceErrorRegistry private constructor() {

    companion object {
        private const val TAG = "VoiceErrorRegistry"
        const val OFFLINE_FALLBACK_STRING = "I'm right here with you! I'm fully ready to help you with phone controls, apps, calls, notes, or chat. What would you like to do?"
        val instance: VoiceErrorRegistry by lazy { VoiceErrorRegistry() }
    }

    private var connectivityManager: android.net.ConnectivityManager? = null
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    fun updateNetworkStatus(online: Boolean) {
        _isOnline.value = online
        Log.i(TAG, "Network status updated: online = $online")
    }

    private val _activeError = MutableStateFlow<VoiceError?>(null)
    val activeError: StateFlow<VoiceError?> = _activeError.asStateFlow()

    private val _errorHistory = MutableStateFlow<List<VoiceError>>(emptyList())
    val errorHistory: StateFlow<List<VoiceError>> = _errorHistory.asStateFlow()

    private fun isDeviceOnline(): Boolean {
        return try {
            val active = connectivityManager?.activeNetwork ?: return false
            val caps = connectivityManager?.getNetworkCapabilities(active) ?: return false
            caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            true // safe fallback
        }
    }

    private val networkCallback = object : android.net.ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: android.net.Network) {
            updateNetworkStatus(true)
            Log.i(TAG, "Network is ONLINE.")
        }

        override fun onLost(network: android.net.Network) {
            val online = isDeviceOnline()
            updateNetworkStatus(online)
            Log.i(TAG, "Network callback onLost. Device online state: $online")
        }

        override fun onCapabilitiesChanged(
            network: android.net.Network,
            networkCapabilities: android.net.NetworkCapabilities
        ) {
            val hasInternet = networkCapabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
            updateNetworkStatus(hasInternet)
        }
    }

    private var isNetworkCallbackRegistered = false

    /**
     * Registers ConnectivityManager network callback to track active internet connection.
     */
    fun registerNetworkCallback(context: android.content.Context) {
        if (isNetworkCallbackRegistered) return
        try {
            connectivityManager = context.applicationContext.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            
            // Immediate check of current active network
            val currentlyOnline = isDeviceOnline()
            updateNetworkStatus(currentlyOnline)

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                connectivityManager?.registerDefaultNetworkCallback(networkCallback)
            } else {
                val request = android.net.NetworkRequest.Builder()
                    .addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
                connectivityManager?.registerNetworkCallback(request, networkCallback)
            }
            isNetworkCallbackRegistered = true
            Log.i(TAG, "Network Callback registered successfully. Initial online state: $currentlyOnline")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register network callback: ${e.message}", e)
        }
    }

    /**
     * Unregisters ConnectivityManager network callback to prevent leaks.
     */
    fun unregisterNetworkCallback() {
        if (!isNetworkCallbackRegistered) return
        try {
            connectivityManager?.unregisterNetworkCallback(networkCallback)
            isNetworkCallbackRegistered = false
            Log.i(TAG, "Network Callback unregistered successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister network callback: ${e.message}", e)
        }
    }

    /**
     * Publishes a new classified error to the registry and UI listeners.
     */
    fun publishError(error: VoiceError) {
        // Suppress transient conversational/network events from showing as active, intrusive errors on the UI
        if (error.type != VoiceErrorType.RECOGNIZER_NO_MATCH && 
            error.type != VoiceErrorType.RECOGNIZER_TIMEOUT &&
            error.type != VoiceErrorType.RECOGNIZER_NETWORK_ERROR) {
            _activeError.value = error
        }
        
        val currentHistory = _errorHistory.value.toMutableList()
        currentHistory.add(0, error)
        if (currentHistory.size > 50) {
            currentHistory.removeAt(currentHistory.lastIndex)
        }
        _errorHistory.value = currentHistory

        // Seamlessly route to Developer Diagnostics Engine
        DiagnosticLogManager.instance.logEvent(
            stage = DiagnosticStage.DETECTION,
            command = "VoiceError: ${error.type.name}",
            details = "${error.message} | Suggestion: ${error.suggestedAction}",
            isSuccess = false,
            failureCode = error.type.name
        )
        
        if (error.severity == VoiceErrorSeverity.FATAL) {
            Log.e(TAG, "Voice Error Registered [${error.severity.name}]: ${error.type} - ${error.message}")
        } else {
            Log.w(TAG, "Voice Error Registered [${error.severity.name}]: ${error.type} - ${error.message}")
        }
    }

    /**
     * Clears the current active error.
     */
    fun clearActiveError() {
        _activeError.value = null
    }

    /**
     * Resolves error code using mapSpeechRecognizerError.
     */
    fun resolve(errorCode: Int): VoiceError {
        return mapSpeechRecognizerError(errorCode)
    }

    /**
     * Posts the resolved error result to the Main Looper immediately.
     */
    fun resolveOnMain(errorCode: Int, onResult: (VoiceError) -> Unit) {
        mainHandler.post { onResult(resolve(errorCode)) }
    }

    /**
     * Translates a standard SpeechRecognizer error code into a rich VoiceError.
     */
    fun mapSpeechRecognizerError(errorCode: Int): VoiceError {
        return when (errorCode) {
            11 -> VoiceError(
                type = VoiceErrorType.RECOGNIZER_NETWORK_ERROR,
                message = OFFLINE_FALLBACK_STRING,
                suggestedAction = "Connecting automatically in the background. Please continue speaking.",
                severity = VoiceErrorSeverity.WARNING,
                code = errorCode,
                userMessage = OFFLINE_FALLBACK_STRING,
                isNetworkRelated = true,
                recoverable = true
            )
            SpeechRecognizer.ERROR_AUDIO -> VoiceError(
                type = VoiceErrorType.AUDIO_RECORD_INIT_FAILED,
                message = "Microphone recording failed. Check hardware/permissions.",
                suggestedAction = "Check if other applications are using the microphone, or restart the device.",
                severity = VoiceErrorSeverity.FATAL,
                code = errorCode,
                userMessage = "Microphone recording failed. Check hardware/permissions.",
                isNetworkRelated = false,
                recoverable = false
            )
            SpeechRecognizer.ERROR_CLIENT -> VoiceError(
                type = VoiceErrorType.RECOGNIZER_GENERIC_FAILURE,
                message = "A client-side error occurred. Let's try again.",
                suggestedAction = "Please tap to try again or restart Alya voice services.",
                severity = VoiceErrorSeverity.WARNING,
                code = errorCode,
                userMessage = "A client-side error occurred. Let's try again.",
                isNetworkRelated = false,
                recoverable = true
            )
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> VoiceError(
                type = VoiceErrorType.SECURITY_PERMISSION_DENIED,
                message = "Permission denied. Grant microphone access in settings.",
                suggestedAction = "Grant Microphone permission in Alya Settings or System Settings.",
                severity = VoiceErrorSeverity.FATAL,
                code = errorCode,
                userMessage = "Permission denied. Grant microphone access in settings.",
                isNetworkRelated = false,
                recoverable = false
            )
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> VoiceError(
                type = VoiceErrorType.RECOGNIZER_BUSY,
                message = "Voice service is busy. Wait a moment.",
                suggestedAction = "Please wait a moment and try speaking again.",
                severity = VoiceErrorSeverity.WARNING,
                code = errorCode,
                userMessage = "Voice service is busy. Wait a moment.",
                isNetworkRelated = false,
                recoverable = true
            )
            SpeechRecognizer.ERROR_NO_MATCH -> VoiceError(
                type = VoiceErrorType.RECOGNIZER_NO_MATCH,
                message = "No clear match. Speak clearly in a quiet environment.",
                suggestedAction = "Ensure you are speaking clearly in a quiet environment and try again.",
                severity = VoiceErrorSeverity.WARNING,
                code = errorCode,
                userMessage = "No clear match. Speak clearly in a quiet environment.",
                isNetworkRelated = false,
                recoverable = true
            )
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> VoiceError(
                type = VoiceErrorType.RECOGNIZER_TIMEOUT,
                message = "Listening timed out. Tap mic to speak.",
                suggestedAction = "Please tap the microphone button or speak your wake-word to try again.",
                severity = VoiceErrorSeverity.WARNING,
                code = errorCode,
                userMessage = "Listening timed out. Tap mic to speak.",
                isNetworkRelated = false,
                recoverable = true
            )
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> VoiceError(
                type = VoiceErrorType.RECOGNIZER_NETWORK_ERROR,
                message = OFFLINE_FALLBACK_STRING,
                suggestedAction = "Alya will attempt to switch to offline mode. Ensure Wi-Fi/data is active.",
                severity = VoiceErrorSeverity.WARNING,
                code = errorCode,
                userMessage = OFFLINE_FALLBACK_STRING,
                isNetworkRelated = true,
                recoverable = true
            )
            13 -> VoiceError(
                type = VoiceErrorType.RECOGNIZER_GENERIC_FAILURE,
                message = "The selected language is not currently available for offline use.",
                suggestedAction = "Please connect to the internet or download the language pack in Google App settings.",
                severity = VoiceErrorSeverity.WARNING,
                code = errorCode,
                userMessage = "Language unavailable. Please connect to internet or check language settings.",
                isNetworkRelated = false,
                recoverable = true
            )
            else -> VoiceError(
                type = VoiceErrorType.RECOGNIZER_GENERIC_FAILURE,
                message = "Speech service encountered an unexpected error (code $errorCode).",
                suggestedAction = "Please tap to try again or restart Alya voice services.",
                severity = VoiceErrorSeverity.WARNING,
                code = errorCode,
                userMessage = "Speech service encountered an unexpected error (code $errorCode).",
                isNetworkRelated = false,
                recoverable = true
            )
        }
    }

    /**
     * Global Error Handling Wrapper
     * Executes the given block of code safely, capturing any runtime exception or hardware error,
     * and elegantly mapping it to the Alya Voice Error Registry.
     * Prevents system crashes and updates UI state dynamically.
     */
    inline fun <T> runSafely(
        ownerTag: String,
        fallbackValue: T,
        crossinline block: () -> T
    ): T {
        return try {
            block()
        } catch (e: SecurityException) {
            val error = VoiceError(
                type = VoiceErrorType.SECURITY_PERMISSION_DENIED,
                message = "Security exception triggered by $ownerTag: ${e.localizedMessage ?: "Permission Denied"}",
                suggestedAction = "Ensure system permissions for Microphone, Overlay, or Background notifications are enabled.",
                severity = VoiceErrorSeverity.FATAL
            )
            publishError(error)
            fallbackValue
        } catch (e: IllegalStateException) {
            val error = VoiceError(
                type = VoiceErrorType.AUDIO_RECORD_INIT_FAILED,
                message = "Hardware resource contention or invalid state in $ownerTag: ${e.localizedMessage ?: "Invalid State"}",
                suggestedAction = "Close other applications that might be holding the microphone.",
                severity = VoiceErrorSeverity.FATAL
            )
            publishError(error)
            fallbackValue
        } catch (e: Throwable) {
            val error = VoiceError(
                type = VoiceErrorType.RECOGNIZER_GENERIC_FAILURE,
                message = "Unexpected crash prevented in $ownerTag: ${e.localizedMessage ?: "Generic runtime failure"}",
                suggestedAction = "Please restart voice services or check active background processes.",
                severity = VoiceErrorSeverity.WARNING
            )
            publishError(error)
            fallbackValue
        }
    }
}
