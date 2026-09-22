package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.ai.OfflineNluEngine
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import kotlin.math.sin

/**
 * High-performance, offline-first voice interaction activity.
 * Operates strictly with native components: native [SpeechRecognizer] and native [TextToSpeech].
 * Bypasses all server connections by passing [RecognizerIntent.EXTRA_PREFER_OFFLINE]
 * to ensure execution is entirely local and offline-resilient.
 */
class OfflineLiveConversationActivity : ComponentActivity() {

    companion object {
        private const val TAG = "OfflineLiveConversation"
        private const val UTTERANCE_ID_RESPONSE = "ALYA_OFFLINE_RESPONSE"
        
        fun start(context: Context) {
            val intent = Intent(context, OfflineLiveConversationActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    // Voice & Audio states
    enum class ConversationState {
        IDLE,
        LISTENING,
        PROCESSING,
        SPEAKING,
        ERROR
    }

    data class ChatMessage(
        val text: String,
        val isUser: Boolean,
        val timestamp: Long = System.currentTimeMillis()
    )

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private val toolExecutor by lazy { com.example.domain.tools.ToolExecutor(this) }
    
    // Reactive States
    private val conversationState = mutableStateOf(ConversationState.IDLE)
    private val isListeningActive = mutableStateOf(false)
    private val chatMessages = mutableStateListOf<ChatMessage>()
    private val audioAmplitude = mutableStateOf(0.0f)
    private val currentLanguagePack = mutableStateOf("English (Device Local)")
    private val errorMessage = mutableStateOf("")

    // Continuous listening loop flag
    private var isLoopEnabled = true

    // Permission launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startOfflineConversationFlow()
        } else {
            errorMessage.value = "Microphone permission is required for live voice conversation."
            conversationState.value = ConversationState.ERROR
            Toast.makeText(this, "Permission denied. Offline voice conversation unavailable.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Welcome introduction message
        chatMessages.add(
            ChatMessage(
                text = "Welcome to Alya Offline Mode. I am fully loaded and ready to talk without any internet connection!",
                isUser = false
            )
        )

        // Initialize SpeechRecognizer & TextToSpeech safely
        initializeNativeTts()
        checkPermissionsAndStart()

        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = { OfflineTopAppBar() }
                ) { paddingValues ->
                    OfflineConversationScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        state = conversationState.value,
                        messages = chatMessages,
                        amplitude = audioAmplitude.value,
                        lang = currentLanguagePack.value,
                        err = errorMessage.value,
                        onMicClick = { handleManualMicAction() },
                        onStopClick = { finish() }
                    )
                }
            }
        }
    }

    private fun checkPermissionsAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startOfflineConversationFlow()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startOfflineConversationFlow() {
        initializeSpeechRecognizer()
        startListeningLoop()
    }

    private fun initializeNativeTts() {
        Log.i(TAG, "Initializing Native TextToSpeech engine...")
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val localeResult = textToSpeech?.setLanguage(Locale.getDefault())
                if (localeResult == TextToSpeech.LANG_MISSING_DATA || localeResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.w(TAG, "Default locale not supported, falling back to US English.")
                    textToSpeech?.setLanguage(Locale.US)
                    currentLanguagePack.value = "US English (Fallback)"
                } else {
                    currentLanguagePack.value = "${Locale.getDefault().displayName} (Local)"
                }

                // Configure premium female voice locally to match user intent
                try {
                    val availableVoices = textToSpeech?.voices
                    if (!availableVoices.isNullOrEmpty()) {
                        val currentLocale = textToSpeech?.language ?: Locale.getDefault()
                        val eligibleVoices = availableVoices.filter { voice ->
                            voice.locale.language.equals(currentLocale.language, ignoreCase = true)
                        }
                        val preferredFemaleVoices = eligibleVoices.filter { voice ->
                            val voiceName = voice.name.lowercase()
                            val isMale = (voiceName.contains("male") && !voiceName.contains("female")) ||
                                    voiceName.contains("man") || voiceName.contains("-m-") ||
                                    voiceName.contains("_m_") || voiceName.endsWith("-m") ||
                                    voiceName.contains("puck") || voiceName.contains("charon") || voiceName.contains("fenrir")
                            !isMale
                        }
                        
                        val bestVoice = preferredFemaleVoices.maxByOrNull { voice ->
                            val voiceName = voice.name.lowercase()
                            var score = 0
                            if (voice.quality == Voice.QUALITY_VERY_HIGH) score += 90
                            if (voice.quality == Voice.QUALITY_HIGH) score += 50
                            if (voiceName.contains("female") || voiceName.contains("-f-") || voiceName.contains("_f_") || voiceName.endsWith("-f")) score += 80
                            if (voiceName.contains("wavenet") || voiceName.contains("neural2") || voiceName.contains("studio")) score += 60
                            score
                        } ?: eligibleVoices.firstOrNull()

                        if (bestVoice != null) {
                            textToSpeech?.voice = bestVoice
                            Log.i(TAG, "Successfully configured premium female voice: ${bestVoice.name}")
                        }
                    }
                    textToSpeech?.setPitch(1.06f)
                    textToSpeech?.setSpeechRate(1.0f)
                } catch (e: Exception) {
                    Log.e(TAG, "Error configuring female voice fallback: ${e.message}")
                }

                // Add progress listener to trigger continuous conversation loop automatically when she finishes speaking
                textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        Log.i(TAG, "TTS started speaking: $utteranceId")
                        conversationState.value = ConversationState.SPEAKING
                    }

                    override fun onDone(utteranceId: String?) {
                        Log.i(TAG, "TTS finished speaking: $utteranceId")
                        if (isLoopEnabled && utteranceId == UTTERANCE_ID_RESPONSE) {
                            runOnUiThread {
                                lifecycleScope.launch {
                                    delay(400L) // Echo buffer: prevents microphone from hearing assistant's voice tail
                                    if (isLoopEnabled && conversationState.value != ConversationState.SPEAKING) {
                                        startListeningLoop()
                                    }
                                }
                            }
                        } else if (!isLoopEnabled) {
                            conversationState.value = ConversationState.IDLE
                        }
                    }

                    override fun onError(utteranceId: String?) {
                        Log.e(TAG, "TTS speaking error on: $utteranceId")
                        runOnUiThread {
                            conversationState.value = ConversationState.IDLE
                        }
                    }
                })
            } else {
                Log.e(TAG, "Failed to initialize native TTS engine.")
                errorMessage.value = "TTS Initialization Failed."
                conversationState.value = ConversationState.ERROR
            }
        }
    }

    private fun initializeSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.e(TAG, "SpeechRecognizer is not available on this device.")
            errorMessage.value = "Speech recognition is unsupported."
            conversationState.value = ConversationState.ERROR
            return
        }

        try {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(createRecognitionListener())
            }
            Log.i(TAG, "Native SpeechRecognizer initialized successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing SpeechRecognizer: ${e.message}", e)
            errorMessage.value = "Recognizer init failed: ${e.message}"
            conversationState.value = ConversationState.ERROR
        }
    }

    private fun startListeningLoop() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        // Cancel previous speech synthesis to handle barge-in seamlessly
        textToSpeech?.stop()

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().language)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            
            // STRICTLY FORCE OFFLINE ONLY
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            
            // Additional parameters to help clean up offline engine detection
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
        }

        runOnUiThread {
            try {
                speechRecognizer?.startListening(intent)
                isListeningActive.value = true
                conversationState.value = ConversationState.LISTENING
                errorMessage.value = ""
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start offline SpeechRecognizer: ${e.message}")
                errorMessage.value = "Failed to listen: ${e.message}"
                conversationState.value = ConversationState.ERROR
            }
        }
    }

    private fun createRecognitionListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(TAG, "Speech Recognizer: Ready")
            audioAmplitude.value = 0.1f
        }

        override fun onBeginningOfSpeech() {
            Log.d(TAG, "Speech Recognizer: Beginning of speech")
        }

        override fun onRmsChanged(rmsdB: Float) {
            // Map RMS to beautiful UI voice amplitude (from 0 to 1)
            val normalized = ((rmsdB + 2.0f) / 12.0f).coerceIn(0.0f, 1.0f)
            audioAmplitude.value = normalized
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            Log.d(TAG, "Speech Recognizer: End of speech")
            conversationState.value = ConversationState.PROCESSING
            isListeningActive.value = false
            audioAmplitude.value = 0.0f
        }

        override fun onError(error: Int) {
            val description = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
                SpeechRecognizer.ERROR_CLIENT -> "Client-side error."
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions."
                SpeechRecognizer.ERROR_NETWORK -> "Network issue (requires offline speech packs)."
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout."
                SpeechRecognizer.ERROR_NO_MATCH -> "No speech matched."
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer is busy."
                SpeechRecognizer.ERROR_SERVER -> "Server exception."
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input detected."
                else -> "Unknown speech error."
            }
            Log.w(TAG, "SpeechRecognizer Error: $error ($description)")

            // Recovery strategy: If no match or timeout, wait briefly and start listening again to keep conversation alive
            if (isLoopEnabled && (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)) {
                runOnUiThread {
                    conversationState.value = ConversationState.IDLE
                    lifecycleScope.launch {
                        delay(1200L) // prevent thrashing
                        if (isLoopEnabled && conversationState.value == ConversationState.IDLE) {
                            startListeningLoop()
                        }
                    }
                }
            } else {
                runOnUiThread {
                    errorMessage.value = description
                    conversationState.value = ConversationState.ERROR
                    isListeningActive.value = false
                }
            }
        }

        override fun onResults(results: Bundle?) {
            val candidates = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val matchedText = candidates?.firstOrNull()
            
            if (!matchedText.isNullOrBlank()) {
                handleRecognizedSpeech(matchedText)
            } else {
                // Restart listening
                if (isLoopEnabled) {
                    startListeningLoop()
                } else {
                    conversationState.value = ConversationState.IDLE
                }
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (!partial.isNullOrBlank()) {
                Log.d(TAG, "Speech partial result: $partial")
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun handleRecognizedSpeech(text: String) {
        Log.i(TAG, "User transcribed input: $text")
        
        // Update list on UI thread
        runOnUiThread {
            chatMessages.add(ChatMessage(text = text, isUser = true))
            conversationState.value = ConversationState.PROCESSING
        }

        // Perform offline planning and NLU parsing
        val plannedAction = com.example.domain.actions.DeviceActionPlanner.planAction(text)
            ?: OfflineNluEngine.parseCommand(text)

        if (plannedAction != null) {
            lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val result = toolExecutor.executeAction(plannedAction)
                val actionTag = com.example.domain.actions.ActionTagParser.createActionTag(plannedAction)
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    val baseMsg = if (result.message.startsWith("Alya:", ignoreCase = true)) result.message else "Alya: ${result.message}"
                    val formattedMsg = if (actionTag.isNotBlank()) "$baseMsg $actionTag" else baseMsg
                    chatMessages.add(ChatMessage(text = formattedMsg, isUser = false))
                    speakOfflineResponse(result.message)
                }
            }
        } else {
            val replyText = OfflineNluEngine.generateOfflineResponse(text, this)
                ?: getGeneralOfflineReply(text)
            val parsed = com.example.domain.actions.ActionTagParser.parse(replyText)
            if (parsed.action != null) {
                lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    toolExecutor.executeAction(parsed.action)
                }
            }
            val formattedReply = if (parsed.fullText.startsWith("Alya:", ignoreCase = true)) parsed.fullText else "Alya: ${parsed.fullText}"
            runOnUiThread {
                chatMessages.add(ChatMessage(text = formattedReply, isUser = false))
                speakOfflineResponse(parsed.spokenText)
            }
        }
    }

    private fun getGeneralOfflineReply(query: String): String {
        val lower = query.lowercase()
        return when {
            lower.contains("hello") || lower.contains("hi") -> "Hello! I am running entirely offline on your device right now. How can I help you locally?"
            lower.contains("how are you") -> "I am operating optimally on low battery and local system resources. Thanks for asking!"
            lower.contains("name") -> "I am Alya, your offline assistant. Operating with wake words Alia, Alya, and Seno."
            lower.contains("wifi") || lower.contains("internet") -> "I am running locally. If you say turn on wifi, I can guide you or use local scripts to configure connections."
            else -> "I processed your voice locally: \"$query\". I can help you toggle the flashlight, scroll, control phone calls, set timers, and search contacts offline!"
        }
    }

    private fun speakOfflineResponse(response: String) {
        if (textToSpeech == null) return
        
        val cleanSpeech = response
            .replace(Regex("^(?:Alya|Alia|Assistant|System):\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\[ACTION:[^\\]]+\\]", RegexOption.IGNORE_CASE), "")
            .trim()
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_FEATURE_NETWORK_SYNTHESIS, "false")
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, UTTERANCE_ID_RESPONSE)
        }

        conversationState.value = ConversationState.SPEAKING
        textToSpeech?.speak(cleanSpeech, TextToSpeech.QUEUE_FLUSH, params, UTTERANCE_ID_RESPONSE)
    }

    private fun handleManualMicAction() {
        // Interruption / Barge-In Logic
        if (conversationState.value == ConversationState.SPEAKING) {
            Log.i(TAG, "User manual barge-in. Interrupting speech and starting capture.")
            textToSpeech?.stop()
            startListeningLoop()
            return
        }

        if (isListeningActive.value) {
            Log.i(TAG, "Pausing offline listening loop.")
            speechRecognizer?.stopListening()
            isListeningActive.value = false
            conversationState.value = ConversationState.IDLE
        } else {
            Log.i(TAG, "Starting offline listening loop.")
            startListeningLoop()
        }
    }

    override fun onDestroy() {
        isLoopEnabled = false
        try {
            speechRecognizer?.destroy()
            textToSpeech?.stop()
            textToSpeech?.shutdown()
        } catch (e: Exception) {
            Log.w(TAG, "Resource clean-up error: ${e.message}")
        }
        super.onDestroy()
    }

    // --- COMPOSE UI COMPONENTS ---

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun OfflineTopAppBar() {
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = "Alya",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Offline Live Conversation",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = { finish() }) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            actions = {
                // Elegant Green Offline Status Tag
                Row(
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .background(
                            color = Color(0xFF1B5E20),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(Color(0xFF4CAF50), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "SECURE OFFLINE",
                        fontSize = 10.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
            )
        )
    }

    @Composable
    private fun OfflineConversationScreen(
        modifier: Modifier = Modifier,
        state: ConversationState,
        messages: List<ChatMessage>,
        amplitude: Float,
        lang: String,
        err: String,
        onMicClick: () -> Unit,
        onStopClick: () -> Unit
    ) {
        val listState = rememberLazyListState()
        val coroutineScope = rememberCoroutineScope()

        // Auto-scroll to the newest message
        LaunchedEffect(messages.size) {
            if (messages.isNotEmpty()) {
                coroutineScope.launch {
                    listState.animateScrollToItem(messages.size - 1)
                }
            }
        }

        Box(
            modifier = modifier
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Lang pack indicator
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = "Lang pack",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Engine Language Pack:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = lang,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Chat transcripts column
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages) { msg ->
                        ChatBubble(msg)
                    }
                }

                // Central high-fidelity visualizer and active status descriptor
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .padding(bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Status text description
                    Text(
                        text = when (state) {
                            ConversationState.IDLE -> "TAP TO START TALKING"
                            ConversationState.LISTENING -> "LISTENING TO YOUR VOICE..."
                            ConversationState.PROCESSING -> "ANALYZING COMMAND LOCALLY..."
                            ConversationState.SPEAKING -> "ALYA IS SPEAKING"
                            ConversationState.ERROR -> "ERROR OCCURRED"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (state) {
                            ConversationState.LISTENING -> MaterialTheme.colorScheme.primary
                            ConversationState.PROCESSING -> MaterialTheme.colorScheme.tertiary
                            ConversationState.SPEAKING -> Color(0xFF4CAF50)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        letterSpacing = 1.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // Sinusoidal Dynamic Audio Waveform Canvas
                    VoiceWaveformVisualizer(
                        state = state,
                        amplitude = amplitude,
                        modifier = Modifier
                            .size(160.dp)
                            .shadow(2.dp, CircleShape)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp))
                            .clickable { onMicClick() }
                    )

                    AnimatedVisibility(
                        visible = err.isNotEmpty(),
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Text(
                            text = err,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Settings / Pack info
                        FilledTonalIconButton(
                            onClick = {
                                Toast.makeText(
                                    this@OfflineLiveConversationActivity,
                                    "Alya uses Android's native Offline Speech Packs for ultra-low latency.",
                                    Toast.LENGTH_LONG
                                ).show()
                            },
                            modifier = Modifier.size(54.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Info",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Floating Microphone Action
                        FloatingActionButton(
                            onClick = onMicClick,
                            containerColor = when (state) {
                                ConversationState.LISTENING -> MaterialTheme.colorScheme.primary
                                ConversationState.SPEAKING -> Color(0xFF2E7D32)
                                else -> MaterialTheme.colorScheme.primaryContainer
                            },
                            contentColor = when (state) {
                                ConversationState.LISTENING -> MaterialTheme.colorScheme.onPrimary
                                ConversationState.SPEAKING -> Color.White
                                else -> MaterialTheme.colorScheme.onPrimaryContainer
                            },
                            shape = CircleShape,
                            modifier = Modifier.size(68.dp)
                        ) {
                            Icon(
                                imageVector = when (state) {
                                    ConversationState.LISTENING -> Icons.Default.Mic
                                    ConversationState.SPEAKING -> Icons.Default.VolumeUp
                                    else -> Icons.Default.MicNone
                                },
                                contentDescription = "Microphone Toggle",
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        // Exit button
                        FilledTonalIconButton(
                            onClick = onStopClick,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f)
                            ),
                            modifier = Modifier.size(54.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Exit Offline",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }

    @Composable
    private fun ChatBubble(message: ChatMessage) {
        val alignment = if (message.isUser) Alignment.End else Alignment.Start
        val containerColor = if (message.isUser) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
        val contentColor = if (message.isUser) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }

        val shape = if (message.isUser) {
            RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
        } else {
            RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalAlignment = alignment
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .background(containerColor, shape)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    text = message.text,
                    color = contentColor,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }
        }
    }

    @Composable
    private fun VoiceWaveformVisualizer(
        state: ConversationState,
        amplitude: Float,
        modifier: Modifier = Modifier
    ) {
        val transition = rememberInfiniteTransition(label = "wave")
        
        // Continuous wave phase shift based on time
        val phaseShift by transition.animateFloat(
            initialValue = 0.0f,
            targetValue = 2.0f * Math.PI.toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "phase"
        )

        // Pulsing scale factor matching the active speech state
        val targetScale = when (state) {
            ConversationState.LISTENING -> 1.05f + amplitude * 0.4f
            ConversationState.PROCESSING -> 1.05f
            ConversationState.SPEAKING -> 1.10f
            else -> 1.0f
        }

        val scale by animateFloatAsState(
            targetValue = targetScale,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessLow
            ),
            label = "scale"
        )

        val waveColor = when (state) {
            ConversationState.LISTENING -> MaterialTheme.colorScheme.primary
            ConversationState.PROCESSING -> MaterialTheme.colorScheme.tertiary
            ConversationState.SPEAKING -> Color(0xFF4CAF50)
            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        }

        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                val width = size.width
                val height = size.height
                val centerH = height / 2f

                if (state == ConversationState.LISTENING) {
                    // Draw continuous flowing sinusoidal live mic waveforms
                    val numWaves = 3
                    for (i in 0 until numWaves) {
                        val amplitudeModifier = (1.0f - (i.toFloat() / numWaves)) * (amplitude * centerH * 0.9f)
                        val frequencyModifier = 1.5f + (i * 0.5f)
                        
                        val path = androidx.compose.ui.graphics.Path()
                        path.moveTo(0f, centerH)

                        for (x in 0..width.toInt()) {
                            val relativeX = x.toFloat() / width
                            val y = centerH + amplitudeModifier * sin(frequencyModifier * (relativeX * 2f * Math.PI.toFloat() + phaseShift))
                            path.lineTo(x.toFloat(), y)
                        }

                        drawPath(
                            path = path,
                            color = waveColor.copy(alpha = 1.0f - (i * 0.25f)),
                            style = Stroke(width = (4 - i).dp.toPx())
                        )
                    }
                } else if (state == ConversationState.SPEAKING) {
                    // Draw beautiful expanding rings matching synthesizer amplitude
                    drawCircle(
                        color = waveColor.copy(alpha = 0.25f),
                        radius = (centerH * scale * 0.8f).coerceAtMost(centerH),
                        style = Stroke(width = 4.dp.toPx())
                    )
                    drawCircle(
                        color = waveColor.copy(alpha = 0.60f),
                        radius = centerH * 0.55f,
                        style = Stroke(width = 3.dp.toPx())
                    )
                    drawCircle(
                        color = waveColor,
                        radius = centerH * 0.35f
                    )
                } else if (state == ConversationState.PROCESSING) {
                    // Circular rotating dots showing computing sequence
                    val dotsCount = 8
                    val radius = centerH * 0.6f
                    for (i in 0 until dotsCount) {
                        val angle = (phaseShift + (i * (2 * Math.PI / dotsCount))).toFloat()
                        val dotX = (width / 2) + radius * kotlin.math.cos(angle)
                        val dotY = (height / 2) + radius * sin(angle)
                        drawCircle(
                            color = waveColor.copy(alpha = 1.0f - (i.toFloat() / dotsCount)),
                            radius = 6.dp.toPx(),
                            center = androidx.compose.ui.geometry.Offset(dotX, dotY)
                        )
                    }
                } else {
                    // Standard soft static pulsing icon
                    drawCircle(
                        color = waveColor.copy(alpha = 0.15f),
                        radius = centerH * 0.7f,
                        style = Stroke(width = 2.dp.toPx())
                    )
                    drawCircle(
                        color = waveColor.copy(alpha = 0.40f),
                        radius = centerH * 0.45f
                    )
                }
            }

            // Central icon
            Icon(
                imageVector = when (state) {
                    ConversationState.LISTENING -> Icons.Default.Mic
                    ConversationState.SPEAKING -> Icons.Default.GraphicEq
                    ConversationState.PROCESSING -> Icons.Default.Memory
                    else -> Icons.Default.VolumeMute
                },
                contentDescription = null,
                tint = when (state) {
                    ConversationState.LISTENING -> MaterialTheme.colorScheme.primary
                    ConversationState.SPEAKING -> Color.White
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(36.dp)
            )
        }
    }
}
