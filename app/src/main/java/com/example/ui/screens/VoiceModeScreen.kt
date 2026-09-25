package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import com.example.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import com.example.ui.viewmodel.SubtitleLine
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.VoiceOrb
import com.example.ui.theme.SleekAvatarGradient
import com.example.ui.viewmodel.AlyaViewModel

@Composable
fun VoiceModeScreen(
    viewModel: AlyaViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isHardwareRecording by viewModel.isHardwareRecording.collectAsState()
    val isSpeechDetected by viewModel.isSpeechDetected.collectAsState()
    val isListeningRecognizer by viewModel.speechManager.isListening.collectAsState()
    val isSpeakingPcm by viewModel.pcmAudioPlayer.isPlaybackActive.collectAsState()
    val isSpeakingTts by viewModel.ttsManager.isSpeaking.collectAsState()
    val isSpeaking = isSpeakingPcm || isSpeakingTts

    val geminiLiveState by viewModel.geminiLiveClient.sessionState.collectAsState()
    val liveTranscript by viewModel.liveAssistantTranscript.collectAsState()
    val playbackProgressMs by viewModel.pcmAudioPlayer.playbackProgressMs.collectAsState()

    val isThinking by viewModel.isThinking.collectAsState()
    val isMuted by viewModel.isMuted.collectAsState()
    val isNetworkAvailable by viewModel.isNetworkAvailable.collectAsState()
    val isSpeakerOn by viewModel.audioDeviceManager.isSpeakerOn.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val callQuality by viewModel.callConnectionQuality.collectAsState()

    val isLiveStreaming = geminiLiveState is com.example.data.ai.GeminiLiveSessionState.Streaming ||
                          geminiLiveState is com.example.data.ai.GeminiLiveSessionState.Connected
    
    val isLiveContinuousMode by viewModel.isLiveContinuousMode.collectAsState()

    // Direct binding to hardware / recognizer capture state
    val isMicActive = !isMuted && (isListeningRecognizer || isHardwareRecording || isLiveStreaming)

    val partialText by viewModel.speechManager.partialResult.collectAsState()
    val speechError by viewModel.speechManager.speechError.collectAsState()
    val currentUtterance by viewModel.ttsManager.currentUtterance.collectAsState()

    val statusText by remember {
        derivedStateOf {
            when {
                isSpeaking && isMuted -> "ALYA IS SPEAKING (MIC MUTED)"
                isSpeaking -> "ALYA IS SPEAKING..."
                isThinking -> "ALYA IS PROCESSING..."
                isMuted -> "MICROPHONE IS MUTED"
                isMicActive && isSpeechDetected -> "ALYA IS LISTENING (VOICE DETECTED)..."
                isMicActive -> if (isLiveContinuousMode) "LIVE REAL-TIME LISTENING..." else "TAP ORB TO SPEAK..."
                else -> if (isLiveContinuousMode) "LIVE CONVERSATION ACTIVE..." else "TAP TO SPEAK..."
            }
        }
    }

    val statusBadgeText by remember {
        derivedStateOf {
            when {
                isSpeaking -> "Speaking (HD Voice)"
                isThinking -> "Processing..."
                isMuted -> "Mic Muted"
                isMicActive && isSpeechDetected -> "Listening (Active)"
                isMicActive -> if (isLiveContinuousMode) "Live Listening" else "Tap to Speak"
                else -> if (isLiveContinuousMode) "Live Mode" else "Standard Mode"
            }
        }
    }

    val subtitles by viewModel.subtitles.collectAsState()
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when subtitles update
    LaunchedEffect(subtitles) {
        if (subtitles.isNotEmpty()) {
            listState.animateScrollToItem(subtitles.size - 1)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val context = androidx.compose.ui.platform.LocalContext.current
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Offline Notification Banner (Informative, showing on-device local capability)
            AnimatedVisibility(visible = !isNetworkAvailable) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            com.example.OfflineLiveConversationActivity.start(context)
                        },
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier
                            .padding(vertical = 10.dp, horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudOff,
                            contentDescription = "Offline Mode",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "OFFLINE MODE — Tap to open Offline Live Voice Lounge",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 16.dp)
            ) {
                // Soft ambient background glowing circles
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(320.dp)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    Color(0xFF6750A4).copy(alpha = 0.15f),
                                    Color(0xFFD0BCFF).copy(alpha = 0.08f),
                                    Color.Transparent
                                )
                            ),
                            CircleShape
                        )
                )

                // Top Bar
                Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = R.drawable.alya_anime_avatar_1788907978096),
                    contentDescription = "Alya Avatar",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Alya",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Real-time Voice Assistant",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Interactive Live Mode / Standard Mode Section
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isLiveContinuousMode)
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
                    else
                        MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            viewModel.setLiveContinuousMode(!isLiveContinuousMode)
                        }
                        .testTag("live_mode_toggle_section")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        // Pulsing status dot
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isMuted -> MaterialTheme.colorScheme.error
                                        isSpeaking -> Color(0xFF64B5F6)
                                        isThinking -> Color(0xFFFFB74D)
                                        isMicActive && isSpeechDetected -> Color(0xFF00E676)
                                        isLiveContinuousMode -> Color(0xFF4CAF50)
                                        else -> MaterialTheme.colorScheme.outline
                                    }
                                )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = if (isLiveContinuousMode) "LIVE MODE" else "STANDARD MODE",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (isLiveContinuousMode)
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp,
                                maxLines = 1
                            )
                            Text(
                                text = when {
                                    isMuted -> "Mic Muted"
                                    isSpeaking -> "Alya Speaking"
                                    isThinking -> "Instant NLU"
                                    isMicActive && isSpeechDetected -> "User Speaking"
                                    isMicActive -> if (isLiveContinuousMode) "Listening (Active)" else "Tap to Speak"
                                    else -> "Ready"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isLiveContinuousMode)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.outline,
                                fontSize = 9.sp,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(6.dp))
                IconButton(
                    onClick = {
                        viewModel.stopVoiceMode()
                        onClose()
                    },
                    modifier = Modifier.size(36.dp).testTag("voice_mode_top_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Live Voice Mode",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Center: Decorative Animated Voice Orb & Live Visualizer
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable {
                        if (isSpeaking) {
                            viewModel.stopSpeaking()
                        } else {
                            viewModel.toggleMute()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                VoiceOrb(
                    isListening = isMicActive && !isSpeaking && !isThinking,
                    isSpeaking = isSpeaking,
                    isThinking = isThinking,
                    rmsProvider = {
                        if (isMuted) 0f
                        else if (isLiveStreaming) viewModel.liveMicRmsDb.value
                        else viewModel.speechManager.rmsDb.value
                    },
                    size = 200.dp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Speech Transcript Bubbles & Live Gemini Subtitles Container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .padding(horizontal = 8.dp)
            ) {
                if (subtitles.isEmpty() && liveTranscript.isBlank() && partialText.isBlank()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "\"Hey Alya, turn on the flashlight.\"",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Speak naturally or give commands in any language",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(subtitles, key = { it.id }) { subtitle ->
                            val isUser = subtitle.speaker == "user"
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
                            ) {
                                Surface(
                                    shape = if (isUser) {
                                        RoundedCornerShape(topStart = 18.dp, topEnd = 4.dp, bottomStart = 18.dp, bottomEnd = 18.dp)
                                    } else {
                                        RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp)
                                    },
                                    color = if (isUser) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
                                    },
                                    tonalElevation = if (subtitle.isInterim) 0.dp else 3.dp,
                                    modifier = Modifier.widthIn(max = 300.dp)
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = if (isUser) "You" else "Alya",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isUser) {
                                                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                                } else {
                                                    MaterialTheme.colorScheme.primary
                                                }
                                            )
                                            if (subtitle.isInterim) {
                                                Text(
                                                    text = "● Live",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 10.sp
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        val displayText = if (!isUser && subtitle.isInterim) {
                                            getRevealedText(subtitle.text, playbackProgressMs, isLiveStreaming)
                                        } else {
                                            subtitle.text
                                        }
                                        Text(
                                            text = displayText,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isUser) FontWeight.Normal else FontWeight.Medium,
                                            color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = if (isUser) TextAlign.End else TextAlign.Start,
                                            lineHeight = 22.sp,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = statusText,
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 1.5.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            val activeVoiceError by com.example.voice.error.VoiceErrorRegistry.instance.activeError.collectAsState()

            if (activeVoiceError != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (activeVoiceError?.severity == com.example.voice.error.VoiceErrorSeverity.FATAL) {
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f)
                    } else {
                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.9f)
                    },
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .padding(horizontal = 16.dp)
                        .testTag("voice_error_registry_card")
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Error icon",
                                    tint = if (activeVoiceError?.severity == com.example.voice.error.VoiceErrorSeverity.FATAL) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.tertiary
                                    },
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = activeVoiceError?.type?.name?.replace("_", " ") ?: "VOICE ERROR",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (activeVoiceError?.severity == com.example.voice.error.VoiceErrorSeverity.FATAL) {
                                        MaterialTheme.colorScheme.onErrorContainer
                                    } else {
                                        MaterialTheme.colorScheme.onTertiaryContainer
                                    }
                                )
                            }
                            IconButton(
                                onClick = { com.example.voice.error.VoiceErrorRegistry.instance.clearActiveError() },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = if (activeVoiceError?.severity == com.example.voice.error.VoiceErrorSeverity.FATAL) {
                                        MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                                    } else {
                                        MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                                    },
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = activeVoiceError?.message ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (activeVoiceError?.severity == com.example.voice.error.VoiceErrorSeverity.FATAL) {
                                MaterialTheme.colorScheme.onErrorContainer
                            } else {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Suggestion: ${activeVoiceError?.suggestedAction}",
                            style = MaterialTheme.typography.bodySmall,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            color = if (activeVoiceError?.severity == com.example.voice.error.VoiceErrorSeverity.FATAL) {
                                MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                            } else {
                                MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                            }
                        )
                    }
                }
            } else if (speechError != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = speechError ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

    // Bottom Controls Dock & Home Indicator
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mute / Unmute Button
                IconButton(
                    onClick = { viewModel.toggleMute() },
                    modifier = Modifier
                        .size(54.dp)
                        .background(
                            if (isMuted) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
                            CircleShape
                        )
                        .testTag("voice_mode_mute_button")
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = if (isMuted) "Unmute" else "Mute",
                        tint = if (isMuted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // End Voice Session (Large Sleek Call End Button)
                FloatingActionButton(
                    onClick = {
                        viewModel.stopVoiceMode()
                        onClose()
                    },
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = Color.White,
                    shape = CircleShape,
                    elevation = FloatingActionButtonDefaults.elevation(4.dp),
                    modifier = Modifier
                        .size(68.dp)
                        .testTag("voice_mode_end_call_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.CallEnd,
                        contentDescription = "End Call",
                        modifier = Modifier.size(30.dp)
                    )
                }

                // Interrupt or Speaker toggle
                if (isSpeaking) {
                    IconButton(
                        onClick = { viewModel.stopSpeaking() },
                        modifier = Modifier
                            .size(54.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                            .testTag("voice_mode_interrupt_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Interrupt",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    IconButton(
                        onClick = { viewModel.toggleSpeaker() },
                        modifier = Modifier
                            .size(54.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                            .testTag("voice_mode_speaker_button")
                    ) {
                        Icon(
                            imageVector = if (isSpeakerOn) Icons.Filled.VolumeUp else Icons.Filled.VolumeDown,
                            contentDescription = "Toggle Speaker",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Sleek Home indicator bar
            Box(
                modifier = Modifier
                    .width(96.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.18f))
            )
        }
    }
}

private fun getRevealedText(fullText: String, progressMs: Long, isLiveStreaming: Boolean): String {
    if (!isLiveStreaming || progressMs <= 0L) return fullText
    
    // Check for CJK (Chinese, Japanese, Korean) characters
    val hasCjk = fullText.any { it.code in 0x3000..0x9FFF }
    if (hasCjk) {
        // Average Japanese spoken speed: ~6-7 chars per second
        val charsToShow = (progressMs / 150L).toInt() + 1
        return fullText.take(charsToShow)
    } else {
        // Space-separated languages (English, Hindi, Bengali, Spanish, etc.)
        val words = fullText.split("\\s+".toRegex())
        if (words.size <= 1) return fullText
        // Average speaking rate: ~150 words/min => ~1 word per 400ms
        val wordsToShow = (progressMs / 380L).toInt() + 1
        return words.take(wordsToShow).joinToString(" ")
    }
}
