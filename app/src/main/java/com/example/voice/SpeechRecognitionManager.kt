package com.example.voice

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.ArrayList

class SpeechRecognitionManager(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Main)

    companion object {
        private const val TAG = "SpeechRecognitionMgr"
        private const val ERROR_SERVER_DISCONNECTED = 11
        private const val ERROR_LANGUAGE_NOT_SUPPORTED = 12
        private const val ERROR_LANGUAGE_UNAVAILABLE = 13
        private const val ERROR_CANNOT_CHECK_SUPPORT = 14
        private const val ERROR_CANNOT_LISTEN_TO_DOWNLOAD_EVENTS = 15

        // Supported fallback language locales for multi-lingual recognition intent
        val DEFAULT_SUPPORTED_LOCALES = arrayListOf(
            "en-US", "en-GB", "en-IN",
            "hi-IN", "bn-IN", "ta-IN", "te-IN", "ml-IN", "gu-IN", "pa-IN", "ur-PK",
            "es-ES", "es-US", "es-MX",
            "ja-JP", "ru-RU", "fr-FR", "de-DE",
            "ar-SA", "ar-EG", "id-ID", "pt-BR", "it-IT",
            "zh-CN", "zh-TW", "ko-KR", "tr-TR", "th-TH", "vi-VN", "ne-NP"
        )
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _rmsDb = MutableStateFlow(0f)
    val rmsDb: StateFlow<Float> = _rmsDb.asStateFlow()

    private val _partialResult = MutableStateFlow("")
    val partialResult: StateFlow<String> = _partialResult.asStateFlow()

    private val _speechError = MutableStateFlow<String?>(null)
    val speechError: StateFlow<String?> = _speechError.asStateFlow()

    private val _activeLanguageLocale = MutableStateFlow("en-US")
    val activeLanguageLocale: StateFlow<String> = _activeLanguageLocale.asStateFlow()

    private val _finalResultFlow = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 1)
    val finalResultFlow = _finalResultFlow.asSharedFlow()

    private val _userBeganSpeakingFlow = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val userBeganSpeakingFlow = _userBeganSpeakingFlow.asSharedFlow()

    var onFinalSpeechResult: ((String) -> Unit)? = null
    var onUserBeganSpeaking: (() -> Unit)? = null
    var onSilenceTimeout: (() -> Unit)? = null

    // Controls whether speech recognizer automatically re-arms during pauses in live conversation
    var isContinuousMode: Boolean = false
    private var currentLanguage: String? = "en-US"
    private var isReinitializing = false
    private var languageFallbackAttempts = 0
    private var fallbackToSystemDefault = false

    private var lastRmsUpdateTime = 0L
    private var smoothedRms = 0f
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager

    private fun suppressSystemSounds(mute: Boolean) {
        // No-op: Modifying STREAM_SYSTEM or STREAM_NOTIFICATION causes volume pops/clicks in Android
    }

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private var isNetworkAvailable = true
    private var busyRetryCount = 0
    private val maxBusyRetries = 3

    init {
        checkNetworkState()
        registerNetworkCallback()
        
        // Register with centralized ErrorRecoveryHandler for auto-resume
        com.example.voice.error.ErrorRecoveryHandler.getInstance(context).registerAutoResumeCallback("SpeechRecognitionManager") {
            if (isContinuousMode && !_isListening.value) {
                Log.i(TAG, "ErrorRecoveryHandler requested auto-resume after network reconnection.")
                startListening(currentLanguage)
            }
        }

        // Register as SPEECH_RECOGNITION owner to handle external deactivation requests (e.g. incoming calls)
        com.example.audio.AudioSessionManager.registerSessionOwner(com.example.audio.AudioSessionType.SPEECH_RECOGNITION) {
            Log.i(TAG, "AudioSessionManager requested mic release for SPEECH_RECOGNITION. Stopping listener.")
            stopListening()
        }

        // Register AudioLockManager callback to auto-resume speech listening when audio lock is released or barge-in occurs
        com.example.voice.audio.AudioLockManager.getInstance(context).onResumeListeningRequested = {
            if (isContinuousMode && !_isListening.value) {
                Log.i(TAG, "AudioLockManager requested listening resumption post TTS playback completion or barge-in.")
                startListening()
            }
        }
    }

    private fun checkNetworkState() {
        try {
            val activeNetwork = connectivityManager?.activeNetwork
            val capabilities = connectivityManager?.getNetworkCapabilities(activeNetwork)
            isNetworkAvailable = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            com.example.voice.error.VoiceErrorRegistry.instance.updateNetworkStatus(isNetworkAvailable)
        } catch (_: Exception) {
            isNetworkAvailable = true // Fallback
        }
    }

    private fun registerNetworkCallback() {
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager?.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                private var networkLossJob: kotlinx.coroutines.Job? = null

                override fun onAvailable(network: Network) {
                    Log.i(TAG, "Network available - speech recognition ready.")
                    networkLossJob?.cancel()
                    val wasOffline = !isNetworkAvailable
                    isNetworkAvailable = true
                    com.example.voice.error.VoiceErrorRegistry.instance.updateNetworkStatus(true)
                    if (wasOffline && isContinuousMode && !_isListening.value) {
                        mainHandler.post { startListening(currentLanguage) }
                    }
                }

                override fun onLost(network: Network) {
                    Log.w(TAG, "Network lost, waiting to confirm offline state...")
                    networkLossJob?.cancel()
                    networkLossJob = scope.launch {
                        kotlinx.coroutines.delay(2000)
                        isNetworkAvailable = false
                        com.example.voice.error.VoiceErrorRegistry.instance.updateNetworkStatus(false)
                    }
                }

                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    isNetworkAvailable = hasInternet
                    com.example.voice.error.VoiceErrorRegistry.instance.updateNetworkStatus(hasInternet)
                }
            })
        } catch (e: Exception) { Log.e(TAG, "Failed to register network callback", e) }
    }

    fun isRecognitionAvailable(): Boolean {
        return try {
            SpeechRecognizer.isRecognitionAvailable(context)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking SpeechRecognizer availability", e)
            false
        }
    }

    /**
     * Dynamically updates the active language locale.
     * If speech recognition is actively listening, reconfigures parameters seamlessly.
     */
    fun updateLanguageLocale(localeTag: String, rebindIfListening: Boolean = true) {
        val cleanTag = localeTag.trim()
        if (cleanTag.isBlank()) return
        Log.i(TAG, "Updating speech recognition language locale to: $cleanTag")
        _activeLanguageLocale.value = cleanTag
        currentLanguage = cleanTag
        fallbackToSystemDefault = false
        languageFallbackAttempts = 0

        if (_isListening.value && rebindIfListening) {
            mainHandler.post {
                stopListening()
                mainHandler.postDelayed({
                    startListening(cleanTag)
                }, 100L)
            }
        }
    }

    /**
     * Starts listening for user speech with multi-language recognition intent parameters.
     * Guaranteed to execute on Android's Main UI thread.
     */
    fun startListening(languageLocale: String? = null) {
        val isTtsSpeaking = try {
            TextToSpeechManager.getInstance(context).isSpeaking.value
        } catch (_: Exception) { false }
        val isPcmPlaying = try {
            (context.applicationContext as? com.example.AlyaApplication)?.pcmAudioPlayer?.isPlaybackActive?.value == true
        } catch (_: Exception) { false }

        if (isTtsSpeaking || isPcmPlaying) {
            Log.w(TAG, "Assistant is currently speaking/playing audio. Delaying startListening to prevent audio loop.")
            return
        }

        if (!isNetworkAvailable) {
            Log.i(TAG, "Offline mode - starting local speech recognition.")
        }

        val targetLocale = languageLocale ?: _activeLanguageLocale.value
        currentLanguage = targetLocale
        _activeLanguageLocale.value = targetLocale

        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { startListening(targetLocale) }
            return
        }

        if (_isListening.value) {
            Log.d(TAG, "Already listening, ignoring startListening call.")
            return
        }

        _speechError.value = null

        val sessionGranted = com.example.audio.AudioSessionManager.requestSession(com.example.audio.AudioSessionType.SPEECH_RECOGNITION)
        if (!sessionGranted) {
            Log.e(TAG, "AudioSessionManager denied SPEECH_RECOGNITION audio session.")
            _speechError.value = "Microphone session is currently in use."
            return
        }

        if (!isRecognitionAvailable()) {
            _speechError.value = "Speech recognition is not supported on this device."
            com.example.audio.AudioSessionManager.releaseSession(com.example.audio.AudioSessionType.SPEECH_RECOGNITION)
            return
        }

        try {
            suppressSystemSounds(true)
            cleanupRecognizer()

            val recognizerInstance = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
                    Log.i(TAG, "Creating On-Device SpeechRecognizer for offline and low-latency operation.")
                    SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                } else {
                    SpeechRecognizer.createSpeechRecognizer(context)
                }
            } catch (e: Exception) {
                try {
                    SpeechRecognizer.createSpeechRecognizer(context)
                } catch (e2: Exception) {
                    Log.e(TAG, "Failed to create SpeechRecognizer instance", e2)
                    val mappedErr = com.example.voice.error.VoiceError(
                        type = com.example.voice.error.VoiceErrorType.RECOGNIZER_GENERIC_FAILURE,
                        message = "Could not initialize Speech Recognizer service on this device.",
                        suggestedAction = "Check microphone permissions or ensure Google Speech Services are available.",
                        severity = com.example.voice.error.VoiceErrorSeverity.FATAL
                    )
                    com.example.voice.error.VoiceErrorRegistry.instance.publishError(mappedErr)
                    _speechError.value = mappedErr.message
                    _isListening.value = false
                    com.example.audio.AudioSessionManager.releaseSession(com.example.audio.AudioSessionType.SPEECH_RECOGNITION)
                    return
                }
            }

            speechRecognizer = recognizerInstance.apply {
                com.example.audio.AlyaAudioManager.getInstance(context).registerMicrophoneHolder("SpeechRecognitionManager") {
                    stopListening()
                }
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _isListening.value = true
                        _speechError.value = null
                        languageFallbackAttempts = 0
                        armWatchdog(8000L) // Longer watchdog for initial readiness
                    }

                    override fun onBeginningOfSpeech() {
                        _isListening.value = true
                        _speechError.value = null
                        onUserBeganSpeaking?.invoke()
                        scope.launch { _userBeganSpeakingFlow.emit(Unit) }
                        armWatchdog(15000L) // User is talking, give them time
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        val now = System.currentTimeMillis()
                        smoothedRms = (0.7f * smoothedRms) + (0.3f * rmsdB)
                        if (now - lastRmsUpdateTime > 80L) {
                            lastRmsUpdateTime = now
                            _rmsDb.value = smoothedRms
                        }
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        disarmWatchdog()
                        _isListening.value = false
                        _rmsDb.value = 0f
                    }

                    override fun onError(error: Int) {
                        disarmWatchdog()
                        _isListening.value = false
                        _rmsDb.value = 0f

                        Log.d(TAG, "SpeechRecognizer onError: $error")

                        // If offline and error is network-related, gracefully handle without publishing noisy speech TTS alerts
                        val isOnline = com.example.voice.error.VoiceErrorRegistry.instance.isOnline.value
                        if (!isOnline && (error == SpeechRecognizer.ERROR_NETWORK || error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT || error == 11)) {
                            Log.w(TAG, "Speech recognition network error while offline (error $error). Staying in local offline mode.")
                            cleanupRecognizer()
                            if (isContinuousMode) {
                                mainHandler.postDelayed({
                                    if (isContinuousMode && !_isListening.value) {
                                        this@SpeechRecognitionManager.startListening(currentLanguage)
                                    }
                                }, 1000L)
                            } else {
                                onSilenceTimeout?.invoke()
                            }
                            return
                        }

                        // 0. Classify and register the error in the central registry
                        val mappedError = com.example.voice.error.VoiceErrorRegistry.instance.mapSpeechRecognizerError(error)
                        com.example.voice.error.VoiceErrorRegistry.instance.publishError(mappedError)

                        val recoveryAction = com.example.voice.error.ErrorRecoveryHandler.getInstance(context)
                            .mapSpeechRecognizerError(error) {
                                if (isContinuousMode && !_isListening.value) {
                                    this@SpeechRecognitionManager.startListening(currentLanguage)
                                }
                            }
                        if (error != SpeechRecognizer.ERROR_RECOGNIZER_BUSY && error != SpeechRecognizer.ERROR_NO_MATCH && error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                            _speechError.value = recoveryAction
                        }

                        // Handle Recognizer Busy with bounded backoff retry
                        if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                            if (busyRetryCount < maxBusyRetries) {
                                busyRetryCount++
                                val delay = busyRetryCount * 500L
                                Log.w(TAG, "Recognizer busy. Retrying in ${delay}ms (Attempt $busyRetryCount)")
                                mainHandler.postDelayed({
                                    this@SpeechRecognitionManager.startListening(currentLanguage)
                                }, delay)
                                return
                            } else {
                                Log.e(TAG, "Recognizer busy after maximum retries. Cleaning up.")
                                busyRetryCount = 0
                                _speechError.value = "Microphone is being used by another app."
                                cleanupRecognizer()
                                return
                            }
                        }
                        busyRetryCount = 0

                        // 1. Language unavailable / not supported (Error 13 or 12)
                        // Automatically fall back to system default recognizer mode seamlessly.
                        if (error == ERROR_LANGUAGE_UNAVAILABLE || error == ERROR_LANGUAGE_NOT_SUPPORTED) {
                            cleanupRecognizer()
                            if (languageFallbackAttempts < 2) {
                                languageFallbackAttempts++
                                fallbackToSystemDefault = true
                                Log.w(TAG, "Speech language unavailable (error $error). Switching to system default recognizer mode.")
                                _speechError.value = null
                                mainHandler.postDelayed({
                                    this@SpeechRecognitionManager.startListening(null)
                                }, 250L)
                                return
                            }
                        }

                        val isSilenceOrTimeout = (error == SpeechRecognizer.ERROR_NO_MATCH ||
                                error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)

                        // 2. Clean up recognizer resources on error
                        cleanupRecognizer()

                        if (isSilenceOrTimeout) {
                            if (isContinuousMode) {
                                // Continuous live mode: smoothly re-arm listening after brief debounce without thrashing
                                mainHandler.postDelayed({
                                    if (isContinuousMode && !_isListening.value) {
                                        this@SpeechRecognitionManager.startListening(currentLanguage)
                                    }
                                }, 300L)
                            } else {
                                onSilenceTimeout?.invoke()
                            }
                        } else {
                            if (!isContinuousMode) {
                                _speechError.value = mappedError.message
                            }
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        disarmWatchdog()
                        _isListening.value = false
                        _rmsDb.value = 0f
                        _speechError.value = null
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull()?.trim() ?: ""
                        _partialResult.value = ""

                        Log.d(TAG, "Final Result: $text")

                        if (text.isNotBlank()) {
                            scope.launch { _finalResultFlow.emit(text) }
                            onFinalSpeechResult?.invoke(text)
                        } else if (isContinuousMode) {
                            // Blank result in continuous conversation: smoothly re-arm listener
                            mainHandler.postDelayed({
                                if (isContinuousMode && !_isListening.value) {
                                    this@SpeechRecognitionManager.startListening(currentLanguage)
                                }
                            }, 300L)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        matches?.firstOrNull()?.let { text ->
                            _partialResult.value = text
                            if (text.isNotBlank()) {
                                _speechError.value = null
                                onUserBeganSpeaking?.invoke()
                                disarmWatchdog()
                                armWatchdog(10000L) // Reset watchdog on speech
                            }
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            // Build rich dynamic speech recognition intent parameters for high-accuracy multilingual recognition
            val intent = buildRecognitionIntent(targetLocale, fallbackToSystemDefault)
            com.example.voice.error.VoiceErrorRegistry.instance.runSafely("SpeechRecognizer", Unit) {
                speechRecognizer?.startListening(intent)
            }
            _isListening.value = true
            armWatchdog(5000L)
        } catch (e: Exception) {
            val mappedErr = com.example.voice.error.VoiceError(
                type = com.example.voice.error.VoiceErrorType.RECOGNIZER_GENERIC_FAILURE,
                message = "Failed to start speech recognizer: ${e.localizedMessage}",
                suggestedAction = "Check microphone access and settings.",
                severity = com.example.voice.error.VoiceErrorSeverity.FATAL
            )
            com.example.voice.error.VoiceErrorRegistry.instance.publishError(mappedErr)
            _speechError.value = mappedErr.message
            _isListening.value = false
        }
    }

    /**
     * Constructs a comprehensive SpeechRecognizer intent configured for seamless multilingual support.
     */
    private fun buildRecognitionIntent(languageLocale: String?, forceSystemDefault: Boolean): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            // Free Form language model for natural, unconstrained speech recognition
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            
            // Explicitly enable streaming partial results for fast, real-time transcription feedback
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            
            if (!forceSystemDefault && !languageLocale.isNullOrBlank()) {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageLocale)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageLocale)
            }
            
            // Multilingual locales with precise hints for English, Hindi, and Bengali command interpretation
            val langHints = arrayListOf("en-US", "en-IN", "hi-IN", "bn-IN")
            putExtra(RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES, langHints)
            putStringArrayListExtra("android.speech.extra.ADDITIONAL_LANGUAGES", DEFAULT_SUPPORTED_LOCALES)
            
            // Modern Android (13+) real-time language switching parameters
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH, true)
            }
            // Older versions or specific OEM recognizer hints for switching
            putExtra("android.speech.extra.LANGUAGE_SWITCH_ALLOWED", true)
            putExtra("android.speech.extra.LANGUAGE_DETECTION_CONFIDENCE_LEVEL", 1)

            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
            
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            
            // Anti-cutoff & natural conversation parameters
            putExtra("android.speech.extra.DICTATION_MODE", true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 3000L) // Longer minimum to capture full phrases
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L) // More time for user to finish
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1800L)
            
            // Enable offline / hybrid recognition preference if supported
            val isOnline = com.example.voice.error.VoiceErrorRegistry.instance.isOnline.value
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, !isOnline)
        }
    }

    private val watchdogRunnable = Runnable {
        if (_isListening.value) {
            _isListening.value = false
            _rmsDb.value = 0f
            cleanupRecognizer()
            if (false) { // Disabled auto wake-up
                startListening(if (fallbackToSystemDefault) null else currentLanguage)
            }
        }
    }

    private fun armWatchdog(timeoutMs: Long) {
        mainHandler.removeCallbacks(watchdogRunnable)
        mainHandler.postDelayed(watchdogRunnable, timeoutMs)
    }

    private fun disarmWatchdog() {
        mainHandler.removeCallbacks(watchdogRunnable)
    }

    fun stopListening() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { stopListening() }
            return
        }

        cleanupRecognizer()
        suppressSystemSounds(false)
        _isListening.value = false
        _rmsDb.value = 0f
        _partialResult.value = ""
    }

    private fun cleanupRecognizer() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) { android.util.Log.e("Alya", "Exception handled", e) }
        speechRecognizer = null
        com.example.audio.AlyaAudioManager.getInstance(context).unregisterMicrophoneHolder("SpeechRecognitionManager")
        com.example.audio.AudioSessionManager.releaseSession(com.example.audio.AudioSessionType.SPEECH_RECOGNITION)
        com.example.service.SessionArbitrator.onAsrEngineReleased()
    }

    fun clearError() {
        _speechError.value = null
    }
}
