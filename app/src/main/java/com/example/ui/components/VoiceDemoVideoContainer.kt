package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Terminal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import com.example.AlyaApplication
import com.example.R
import com.example.util.SessionManager
import com.example.util.SessionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Data model for interaction log entries recorded during voice assistant usage.
 */
data class InteractionLogEntry(
    val id: String,
    val timestamp: String,
    val tag: String,
    val level: String,
    val message: String,
    val details: String? = null,
    val accentColor: Color = Color(0xFF3B82F6)
)

val INITIAL_INTERACTION_LOGS = listOf(
    InteractionLogEntry(
        id = "log_001",
        timestamp = "14:22:01.042",
        tag = "SystemInit",
        level = "INFO",
        message = "SessionManager initialized with state IDLE",
        details = "AudioRecord initialized @ 16kHz Mono • VoiceSessionStateManager Ready",
        accentColor = Color(0xFF3B82F6)
    ),
    InteractionLogEntry(
        id = "log_002",
        timestamp = "14:22:05.120",
        tag = "WakeWord",
        level = "SUCCESS",
        message = "Wake word detected: 'Alia' / 'Alya'",
        details = "Acoustic Score: 0.984 | Local Engine: On-Device | Transition -> WAKE_DETECTED",
        accentColor = Color(0xFFF59E0B)
    ),
    InteractionLogEntry(
        id = "log_003",
        timestamp = "14:22:06.310",
        tag = "Connectivity",
        level = "EXECUTE",
        message = "Voice Command Received: 'Hey Alya, turn on Wi-Fi and connect to office network'",
        details = "ASR Provider: Streaming WebRTC | Intent: NETWORK_ENABLE_WIFI | Latency: 22ms",
        accentColor = Color(0xFF3B82F6)
    ),
    InteractionLogEntry(
        id = "log_004",
        timestamp = "14:22:07.890",
        tag = "Calls & Telecom",
        level = "EXECUTE",
        message = "Voice Command Received: 'Hey Alya, answer incoming call and switch to speakerphone'",
        details = "TelephonyManager Intent: ANSWER_CALL | Speakerphone: TRUE | AudioFocus: Granted",
        accentColor = Color(0xFF10B981)
    ),
    InteractionLogEntry(
        id = "log_005",
        timestamp = "14:22:11.450",
        tag = "Live Weather",
        level = "SUCCESS",
        message = "Weather Query Executed: 24°C Sunny, 48% Humidity",
        details = "LocationProvider: GPS fine location | Locality: Current City | Response Time: 140ms",
        accentColor = Color(0xFF06B6D4)
    )
)

/**
 * Data model for sample captions cycled in the demo video container.
 */
data class SampleCaptionItem(
    val id: String,
    val title: String,
    val spokenPhrase: String,
    val actionOutput: String,
    val category: String,
    val icon: ImageVector,
    val accentColor: Color
)

/**
 * Live connection status data structure for video container indicator badge.
 */
data class LiveStatusData(
    val text: String,
    val color: Color,
    val isActive: Boolean
)

/**
 * Comprehensive list of realistic hands-free voice command sample captions.
 */
val SAMPLE_DEMO_CAPTIONS = listOf(
    SampleCaptionItem(
        id = "wifi_connect",
        title = "Wi-Fi & Connectivity",
        spokenPhrase = "Hey Alya, turn on Wi-Fi and connect to office network",
        actionOutput = "Wi-Fi Manager • Enabled 5GHz Wi-Fi and successfully linked to network",
        category = "Connectivity",
        icon = Icons.Default.Wifi,
        accentColor = Color(0xFF3B82F6)
    ),
    SampleCaptionItem(
        id = "call_answer",
        title = "Autonomous Call Answering",
        spokenPhrase = "Hey Alya, answer incoming call and switch to speakerphone",
        actionOutput = "Telephony Service • Picked up caller Rahul Sharma on speakerphone",
        category = "Calls & Telecom",
        icon = Icons.Default.PhoneInTalk,
        accentColor = Color(0xFF10B981)
    ),
    SampleCaptionItem(
        id = "screenshot_doc",
        title = "Screen Capture & Documents",
        spokenPhrase = "Hey Alya, take a screenshot and create document Notes",
        actionOutput = "Screen Capture • Saved full-resolution screenshot to markdown document",
        category = "Screen & Docs",
        icon = Icons.Default.CameraAlt,
        accentColor = Color(0xFFEC4899)
    ),
    SampleCaptionItem(
        id = "routine_gym",
        title = "Scheduled Routines & Alarms",
        spokenPhrase = "Hey Alya, set routine Morning Gym at 6:30 AM",
        actionOutput = "Alarm Scheduler • Configured daily recurring 6:30 AM wake-up routine",
        category = "Routines & Alarms",
        icon = Icons.Default.Schedule,
        accentColor = Color(0xFFF59E0B)
    ),
    SampleCaptionItem(
        id = "live_weather",
        title = "Real-Time Weather Report",
        spokenPhrase = "Hey Alya, what is the temperature and weather outside?",
        actionOutput = "Weather Engine • 24°C Sunny, 48% Humidity with light breeze",
        category = "Live Weather",
        icon = Icons.Default.WbSunny,
        accentColor = Color(0xFF06B6D4)
    ),
    SampleCaptionItem(
        id = "accessibility_touch",
        title = "Hands-Free Screen Gestures",
        spokenPhrase = "Hey Alya, scroll down and click on Send button",
        actionOutput = "Accessibility Gesture • Swiped viewport and dispatched touch click",
        category = "Gestures & UI",
        icon = Icons.Default.TouchApp,
        accentColor = Color(0xFF8B5CF6)
    ),
    SampleCaptionItem(
        id = "system_volume",
        title = "Hardware & System Controls",
        spokenPhrase = "Hey Alya, lock screen and set media volume to 80%",
        actionOutput = "System Controller • Screen locked and volume calibrated to 80%",
        category = "System Controls",
        icon = Icons.Filled.VolumeUp,
        accentColor = Color(0xFF6366F1)
    )
)

/**
 * High-performance Video Container (.video-container) featuring:
 * 1. Real-time playback progress scrubber slider with timestamp readout
 * 2. Mute/Unmute audio toggle button
 * 3. Full-screen mode view toggle
 * 4. Scale-up animation and glowing gradient border on hover/touch
 * 5. Live Badge & Connection Status indicator bound to voice session state
 */
@Composable
fun VoiceDemoVideoContainer(
    onExecuteCommand: (String) -> Unit,
    modifier: Modifier = Modifier,
    cycleDurationSeconds: Float = 4.0f
) {
    val context = LocalContext.current
    val app = context.applicationContext as? AlyaApplication
    val sessionState = app?.sessionManager?.sessionState?.collectAsState(initial = SessionState.IDLE)?.value ?: SessionState.IDLE

    var currentCaptionIndex by remember { mutableIntStateOf(0) }
    var isPlaying by remember { mutableStateOf(true) }
    var isMuted by remember { mutableStateOf(false) }
    var isFullscreen by remember { mutableStateOf(false) }
    var isHovered by remember { mutableStateOf(false) }
    var configuredDurationSec by remember { mutableFloatStateOf(cycleDurationSeconds) }
    var elapsedMillis by remember { mutableLongStateOf(0L) }
    var isUserHolding by remember { mutableStateOf(false) }
    var interactionLogs by remember { mutableStateOf(INITIAL_INTERACTION_LOGS) }
    var selectedFilter by remember { mutableStateOf("ALL") }

    // Auto-append real-time interaction log entries as captions cycle
    LaunchedEffect(currentCaptionIndex) {
        val timeStr = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        val caption = SAMPLE_DEMO_CAPTIONS[currentCaptionIndex]
        val newEntry = InteractionLogEntry(
            id = UUID.randomUUID().toString(),
            timestamp = timeStr,
            tag = caption.category,
            level = "EXECUTE",
            message = "Voice Command Received: \"${caption.spokenPhrase}\"",
            details = "Action Output: ${caption.actionOutput} | Cycle Pace: ${configuredDurationSec}s",
            accentColor = caption.accentColor
        )
        interactionLogs = (listOf(newEntry) + interactionLogs).take(60)
    }

    val totalCaptions = SAMPLE_DEMO_CAPTIONS.size
    val activeCaption = SAMPLE_DEMO_CAPTIONS[currentCaptionIndex]
    val totalDurationMillis = (configuredDurationSec * 1000f).toLong()

    // Smooth ~30fps progress update loop optimized for frame-rate stability
    LaunchedEffect(currentCaptionIndex, isPlaying, isUserHolding, configuredDurationSec) {
        if (!isPlaying || isUserHolding) return@LaunchedEffect
        val tickInterval = 33L
        while (isActive) {
            delay(tickInterval)
            elapsedMillis += tickInterval
            if (elapsedMillis >= totalDurationMillis) {
                elapsedMillis = 0L
                currentCaptionIndex = (currentCaptionIndex + 1) % totalCaptions
            }
        }
    }

    // Derived values for performance
    val currentProgress by remember(elapsedMillis, totalDurationMillis) {
        derivedStateOf { (elapsedMillis.toFloat() / totalDurationMillis.toFloat()).coerceIn(0.0f, 1.0f) }
    }
    val elapsedSeconds by remember(elapsedMillis) {
        derivedStateOf { elapsedMillis / 1000f }
    }
    val remainingSeconds by remember(elapsedMillis, totalDurationMillis) {
        derivedStateOf { (totalDurationMillis - elapsedMillis) / 1000f }
    }

    // Dynamic Live Status Indicator bound to Voice Session State
    val liveStatusInfo = remember(sessionState) {
        when (sessionState) {
            SessionState.LISTENING -> LiveStatusData("LIVE • LISTENING", Color(0xFF10B981), true)
            SessionState.PROCESSING -> LiveStatusData("LIVE • PROCESSING", Color(0xFF8B5CF6), true)
            SessionState.RESPONDING, SessionState.SPEAKING -> LiveStatusData("LIVE • SPEAKING", Color(0xFFEC4899), true)
            SessionState.WAKE_DETECTED -> LiveStatusData("LIVE • WAKE DETECTED", Color(0xFFF59E0B), true)
            SessionState.IDLE -> LiveStatusData("LIVE • READY", Color(0xFF3B82F6), false)
            SessionState.STOPPED -> LiveStatusData("OFFLINE", Color(0xFF64748B), false)
        }
    }

    // Scale-Up transition animation on Hover (1.05x)
    val animatedScale by animateFloatAsState(
        targetValue = if (isHovered) 1.05f else 1.000f,
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        label = "container_scale_anim"
    )

    // Glowing blue border animation
    val infiniteTransition = rememberInfiniteTransition(label = "border_glow_transition")
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1.00f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "border_glow_pulse"
    )

    val glowingBlueBorderBrush = Brush.horizontalGradient(
        colors = if (isHovered) {
            listOf(
                Color(0xFF3B82F6).copy(alpha = glowPulse),
                Color(0xFF60A5FA).copy(alpha = glowPulse),
                Color(0xFF00E5FF).copy(alpha = glowPulse),
                Color(0xFF3B82F6).copy(alpha = glowPulse)
            )
        } else {
            listOf(
                liveStatusInfo.color.copy(alpha = if (liveStatusInfo.isActive) glowPulse else 0.40f),
                activeCaption.accentColor.copy(alpha = if (liveStatusInfo.isActive) glowPulse else 0.40f),
                Color(0xFF38BDF8).copy(alpha = if (liveStatusInfo.isActive) glowPulse else 0.40f)
            )
        }
    )

    @Composable
    fun VideoContainerCard(isFullWindow: Boolean) {
        Card(
            modifier = (if (isFullWindow) Modifier.fillMaxSize() else modifier.fillMaxWidth())
                .graphicsLayer {
                    if (!isFullWindow) {
                        scaleX = animatedScale
                        scaleY = animatedScale
                    }
                }
                .shadow(
                    elevation = if (isHovered) 20.dp else if (isFullWindow) 0.dp else 4.dp,
                    shape = RoundedCornerShape(22.dp),
                    spotColor = if (isHovered) Color(0xFF3B82F6) else Color.Transparent,
                    ambientColor = if (isHovered) Color(0xFF60A5FA) else Color.Transparent
                )
                .border(
                    width = if (isHovered) 3.dp else if (liveStatusInfo.isActive || isFullWindow) 2.dp else 1.dp,
                    brush = glowingBlueBorderBrush,
                    shape = RoundedCornerShape(22.dp)
                )
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            when (event.type) {
                                PointerEventType.Enter -> isHovered = true
                                PointerEventType.Exit -> isHovered = false
                            }
                        }
                    }
                }
                .testTag("video_container")
                .testTag("video-container")
                .testTag("video_container_demo_card"),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            elevation = CardDefaults.cardElevation(defaultElevation = if (isHovered) 14.dp else 6.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Main Video Container Frame
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (isFullWindow) Modifier.weight(1f) else Modifier.aspectRatio(16f / 9.5f))
                        .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                        .background(Color(0xFF020617))
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    isHovered = true
                                    isUserHolding = true
                                    tryAwaitRelease()
                                    isUserHolding = false
                                    isHovered = false
                                },
                                onTap = {
                                    isPlaying = !isPlaying
                                }
                            )
                        }
                        .testTag("sample_caption_video_container")
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.voice_demo_preview_1789312949749),
                        contentDescription = "Voice Assistant Demo Video Frame",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xFF020617).copy(alpha = 0.82f),
                                        Color(0xFF090D16).copy(alpha = 0.55f),
                                        Color(0xFF020617).copy(alpha = 0.94f)
                                    )
                                )
                            )
                    )

                    // TOP OVERLAY: Segmented Story Bars & Badges
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp)
                                .testTag("segmented_caption_progress_bars"),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            for (i in 0 until totalCaptions) {
                                val segmentProgress = when {
                                    i < currentCaptionIndex -> 1.0f
                                    i == currentCaptionIndex -> currentProgress
                                    else -> 0.0f
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(3.5.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(Color.White.copy(alpha = 0.22f))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(segmentProgress)
                                            .fillMaxSize()
                                            .background(
                                                if (i == currentCaptionIndex) activeCaption.accentColor else Color.White
                                            )
                                    )
                                }
                            }
                        }

                        // Top Row Controls: LIVE Badge + Timing Readout + Mute + Fullscreen
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Live Status Badge directly integrated into .video-container
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.Black.copy(alpha = 0.65f),
                                border = androidx.compose.foundation.BorderStroke(
                                    width = 1.dp,
                                    color = liveStatusInfo.color.copy(alpha = 0.6f)
                                ),
                                modifier = Modifier.testTag("video_container_live_badge")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(
                                                liveStatusInfo.color.copy(
                                                    alpha = if (liveStatusInfo.isActive) glowPulse else 0.8f
                                                )
                                            )
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = liveStatusInfo.text,
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    )
                                    if (liveStatusInfo.isActive) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Default.GraphicEq,
                                            contentDescription = null,
                                            tint = liveStatusInfo.color,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = activeCaption.accentColor.copy(alpha = 0.25f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, activeCaption.accentColor.copy(alpha = 0.6f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Schedule,
                                            contentDescription = null,
                                            tint = activeCaption.accentColor,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Text(
                                            text = String.format("%.1fs / %.1fs", elapsedSeconds, configuredDurationSec),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            color = Color.White
                                        )
                                    }
                                }

                                // Mute / Unmute Toggle Button
                                Surface(
                                    shape = CircleShape,
                                    color = Color.Black.copy(alpha = 0.60f),
                                    onClick = { isMuted = !isMuted },
                                    modifier = Modifier
                                        .testTag("video_mute_toggle_button")
                                        .testTag("video-mute-toggle")
                                        .testTag("video_mute_button")
                                        .testTag("mute_unmute_toggle")
                                ) {
                                    Box(
                                        modifier = Modifier.padding(5.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                            contentDescription = if (isMuted) "Unmute Audio" else "Mute Audio",
                                            tint = if (isMuted) Color(0xFFEF4444) else Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                // Full-Screen Mode Toggle Button
                                Surface(
                                    shape = CircleShape,
                                    color = Color.Black.copy(alpha = 0.60f),
                                    onClick = { isFullscreen = !isFullscreen },
                                    modifier = Modifier
                                        .testTag("video_fullscreen_button")
                                        .testTag("video-fullscreen-button")
                                        .testTag("video_fullscreen_toggle_button")
                                        .testTag("video_expand_collapse_button")
                                ) {
                                    Box(
                                        modifier = Modifier.padding(5.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isFullWindow) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                            contentDescription = if (isFullWindow) "Exit Fullscreen" else "Fullscreen Mode",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // CENTER: Sample Caption Output
                    AnimatedContent(
                        targetState = activeCaption,
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(220)) + slideInVertically(
                                animationSpec = tween(220),
                                initialOffsetY = { 20 }
                            )).togetherWith(
                                fadeOut(animationSpec = tween(180)) + slideOutVertically(
                                    animationSpec = tween(180),
                                    targetOffsetY = { -20 }
                                )
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.Center)
                            .padding(horizontal = 14.dp, vertical = 16.dp),
                        label = "caption_text_animation"
                    ) { caption ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(caption.accentColor.copy(alpha = 0.22f))
                                    .padding(horizontal = 9.dp, vertical = 3.dp)
                            ) {
                                Icon(
                                    imageVector = caption.icon,
                                    contentDescription = null,
                                    tint = caption.accentColor,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = caption.category,
                                    color = caption.accentColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "\"${caption.spokenPhrase}\"",
                                color = Color.White,
                                fontSize = if (isFullWindow) 20.sp else 15.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp)
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = if (isMuted) "[Audio Muted] ${caption.actionOutput}" else caption.actionOutput,
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // BOTTOM OVERLAY: Playback Controls & Real-Time Scrubber Slider
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        elapsedMillis = 0L
                                        currentCaptionIndex = if (currentCaptionIndex > 0) currentCaptionIndex - 1 else totalCaptions - 1
                                    },
                                    modifier = Modifier
                                        .size(28.dp)
                                        .testTag("video_prev_caption_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FastRewind,
                                        contentDescription = "Previous Sample Caption",
                                        tint = Color.White.copy(alpha = 0.85f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { isPlaying = !isPlaying },
                                    modifier = Modifier
                                        .size(28.dp)
                                        .testTag("video_play_pause_button")
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (isPlaying) "Pause Cycle" else "Play Cycle",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        elapsedMillis = 0L
                                        currentCaptionIndex = (currentCaptionIndex + 1) % totalCaptions
                                    },
                                    modifier = Modifier
                                        .size(28.dp)
                                        .testTag("video_next_caption_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FastForward,
                                        contentDescription = "Next Sample Caption",
                                        tint = Color.White.copy(alpha = 0.85f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Button(
                                onClick = { onExecuteCommand(activeCaption.spokenPhrase) },
                                colors = ButtonDefaults.buttonColors(containerColor = activeCaption.accentColor),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .height(28.dp)
                                    .testTag("try_sample_caption_command_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(13.dp),
                                    tint = Color.White
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Try This",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Real-Time Playback Progress Scrubber Slider
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(16.dp)
                                .testTag("video_progress_slider_container")
                        ) {
                            Slider(
                                value = currentProgress,
                                onValueChange = { newProgress ->
                                    elapsedMillis = (newProgress * totalDurationMillis).toLong()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(16.dp)
                                    .testTag("video_progress_slider")
                                    .testTag("video-progress-bar")
                                    .testTag("video_progress_bar")
                                    .testTag("video_playback_progress_bar")
                                    .testTag("caption_timing_progress_bar_overlay"),
                                colors = SliderDefaults.colors(
                                    thumbColor = activeCaption.accentColor,
                                    activeTrackColor = activeCaption.accentColor,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                                )
                            )
                        }
                    }
                }

                // SUB-PANEL: Pace Controls & Remaining Time Readout
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0F172A))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "Speed:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF94A3B8)
                        )

                        listOf(3.0f to "3s", 4.0f to "4s", 6.0f to "6s").forEach { (duration, label) ->
                            val isSelected = configuredDurationSec == duration
                            Surface(
                                onClick = {
                                    configuredDurationSec = duration
                                    elapsedMillis = 0L
                                },
                                shape = RoundedCornerShape(6.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF1E293B),
                                modifier = Modifier.testTag("speed_chip_$label")
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = String.format("%.1fs left", remainingSeconds),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        color = activeCaption.accentColor
                    )
                }
            }
        }
    }

    // Default inline container view
    VideoContainerCard(isFullWindow = false)

    // Expanded Full-Screen Overlay View with Detailed Interaction Log
    if (isFullscreen) {
        Dialog(
            onDismissRequest = { isFullscreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF020617))
                    .testTag("video_container_expanded")
                    .testTag("video_container_fullscreen"),
                color = Color(0xFF020617)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Header Bar with Title, Subtitle, and Close Button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Terminal,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "ALYA Voice Interaction Log & Live Preview",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Expanded Telemetry & Real-Time System Event Log",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 12.sp
                                )
                            }
                        }

                        IconButton(
                            onClick = { isFullscreen = false },
                            modifier = Modifier
                                .background(Color(0xFF1E293B), CircleShape)
                                .testTag("video_fullscreen_close_button")
                                .testTag("video_container_close_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Expanded View",
                                tint = Color.White
                            )
                        }
                    }

                    // Compact Video Player Preview Box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        VideoContainerCard(isFullWindow = false)
                    }

                    // Detailed Interaction Log Section
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .testTag("video_container_interaction_log")
                            .testTag("interaction_log"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp)
                        ) {
                            // Header & Clear Button
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.List,
                                        contentDescription = null,
                                        tint = Color(0xFF38BDF8),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Interaction Log Stream (${interactionLogs.size} Events)",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Surface(
                                    onClick = { interactionLogs = emptyList() },
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF1E293B),
                                    modifier = Modifier.testTag("clear_interaction_logs_button")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Clear Logs",
                                            tint = Color(0xFF94A3B8),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "Clear",
                                            color = Color(0xFF94A3B8),
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }

                            // Filter Chips Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf("ALL", "EXECUTE", "SUCCESS", "INFO").forEach { filter ->
                                    val isSelected = selectedFilter == filter
                                    Surface(
                                        onClick = { selectedFilter = filter },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF1E293B),
                                        modifier = Modifier.testTag("interaction_log_filter_$filter")
                                    ) {
                                        Text(
                                            text = filter,
                                            color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }

                            val filteredLogs = remember(interactionLogs, selectedFilter) {
                                if (selectedFilter == "ALL") interactionLogs
                                else interactionLogs.filter { it.level.equals(selectedFilter, ignoreCase = true) }
                            }

                            // Scrollable Detailed Interaction Log Entries List
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("interaction_log_list"),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(filteredLogs, key = { it.id }) { log ->
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color(0xFF020617),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, log.accentColor.copy(alpha = 0.3f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(10.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = log.accentColor.copy(alpha = 0.2f)
                                                    ) {
                                                        Text(
                                                            text = log.level,
                                                            color = log.accentColor,
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }

                                                    Text(
                                                        text = log.tag,
                                                        color = Color.White,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }

                                                Text(
                                                    text = log.timestamp,
                                                    color = Color(0xFF64748B),
                                                    fontSize = 10.sp,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(4.dp))

                                            Text(
                                                text = log.message,
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium
                                            )

                                            if (!log.details.isNullOrEmpty()) {
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = log.details,
                                                    color = Color(0xFF94A3B8),
                                                    fontSize = 11.sp,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
