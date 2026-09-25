package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import com.example.R
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import com.example.service.CallState
import com.example.data.sync.SyncStatus
import androidx.compose.material.icons.filled.Sync
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.AssistantSidebarDrawer
import com.example.ui.components.MessageItem
import com.example.ui.components.VoiceOrb
import com.example.ui.theme.SleekAvatarGradient
import com.example.ui.viewmodel.AlyaViewModel
import com.example.ui.viewmodel.AssistantScreen

enum class MainAppTab {
    CHAT,
    CALL_HISTORY
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainChatScreen(
    viewModel: AlyaViewModel,
    modifier: Modifier = Modifier
) {
    val messages by viewModel.messages.collectAsState()
    val isThinking by viewModel.isThinking.collectAsState()
    val isSpeaking by viewModel.ttsManager.isSpeaking.collectAsState()
    val conversations by viewModel.conversations.collectAsState()
    val activeConvId by viewModel.currentConversationId.collectAsState()
    val memories by viewModel.memories.collectAsState()
    val currentScreen by viewModel.currentScreen.collectAsState()
    val memoryEnabled by viewModel.repository.preferences.memoryEnabled.collectAsState()
    val isRateLimited by viewModel.isRateLimited.collectAsState()
    val rateLimitSecondsRemaining by viewModel.rateLimitSecondsRemaining.collectAsState()
    val callState by viewModel.callState.collectAsState()
    val isCallActive by viewModel.isCallActive.collectAsState()
    val isVoiceMode by viewModel.isVoiceMode.collectAsState()
    val isListening by viewModel.speechManager.isListening.collectAsStateWithLifecycle()
    val partialSpeech by viewModel.speechManager.partialResult.collectAsStateWithLifecycle()
    val isRecordingVoiceMessage by viewModel.isRecordingVoiceMessage.collectAsState()
    val voiceInputSentEvent by viewModel.voiceInputSentEvent.collectAsState()
    val callQuality by viewModel.callConnectionQuality.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    val sessionState by viewModel.sessionState.collectAsState()
    val isMicActiveReal by viewModel.isMicActive.collectAsState()
    val isAudioFocusHeld by viewModel.isAudioFocusHeld.collectAsState()
    val isNetworkAvailable by viewModel.isNetworkAvailable.collectAsState()
    val isBatterySaverActive by viewModel.isBatterySaverActive.collectAsState()
    val powerState by viewModel.powerState.collectAsState()
    val isHardwareReleased by viewModel.isHardwareReleased.collectAsState()

    val isListeningPopupVisible by viewModel.isListeningPopupVisible.collectAsStateWithLifecycle()
    val detectedWakeWord by viewModel.detectedWakeWord.collectAsStateWithLifecycle()
    val textInputPrefill by viewModel.textInputPrefill.collectAsStateWithLifecycle()
    val subtitles by viewModel.subtitles.collectAsStateWithLifecycle()

    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(textInputPrefill) {
        textInputPrefill?.let {
            inputText = it
            viewModel.clearTextInputPrefill()
        }
    }

    // Sync real-time transcription to text field during active voice recording
    LaunchedEffect(isListening, isRecordingVoiceMessage, partialSpeech) {
        if ((isListening || isRecordingVoiceMessage) && partialSpeech.isNotBlank()) {
            inputText = partialSpeech
        }
    }

    // Reset input text immediately when voice input has been dispatched
    LaunchedEffect(voiceInputSentEvent) {
        if (voiceInputSentEvent > 0L) {
            inputText = ""
        }
    }

    // Optimized state computations and remembered callbacks for smooth 60-120 FPS interactions
    val hasMessages = messages.isNotEmpty()
    val isSendActive = inputText.isNotBlank()
    val statusSubtitle = if (syncStatus is SyncStatus.Syncing) "Syncing offline data..." else if (isSpeaking) "Speaking..." else if (isThinking) "Thinking..." else "Alya • AI Assistant"
    val rateLimitNotice = "Cloud AI cooldown (${rateLimitSecondsRemaining}s). Local tools, phone actions & reminders active."

    val onSpeakMessageCallback = remember(viewModel) { { text: String -> viewModel.speakResponse(text) } }
    val onConfirmActionCallback = remember(viewModel) { { msg: com.example.data.local.entity.MessageEntity -> viewModel.confirmAction(msg) } }
    val onCancelActionCallback = remember(viewModel) { { msg: com.example.data.local.entity.MessageEntity -> viewModel.cancelAction(msg) } }

    // Bottom sheet states
    val context = androidx.compose.ui.platform.LocalContext.current
    var showOnboardingWalkthrough by remember {
        mutableStateOf(!com.example.ui.components.OnboardingPreferences.isCompleted(context))
    }
    var selectedMainTab by remember { mutableStateOf(MainAppTab.CHAT) }
    var activeBottomSheet by remember { mutableStateOf<AssistantScreen?>(null) }
    var showCommandGuideModal by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val offlineCommandLogs by viewModel.offlineCommandLogs.collectAsState()

    // Scroll to bottom on new message
    LaunchedEffect(messages.size, isThinking) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val isSidebarOpen by viewModel.isSidebarOpen.collectAsState()
    val doubleTapShortcutEnabled by viewModel.repository.preferences.doubleTapShortcutEnabled.collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(doubleTapShortcutEnabled) {
                if (doubleTapShortcutEnabled) {
                    detectTapGestures(
                        onDoubleTap = {
                            viewModel.navigateTo(AssistantScreen.VOICE_MODE)
                        }
                    )
                }
            }
    ) {
        Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        // Character Logo Avatar
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(Color(0xFF6750A4), CircleShape)
                                        .testTag("online_status_dot")
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = statusSubtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { viewModel.toggleSidebar(true) },
                            modifier = Modifier.testTag("open_sidebar_menu_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Sidebar Assistant Menu",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = { activeBottomSheet = AssistantScreen.HISTORY },
                            modifier = Modifier.testTag("open_history_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = "History",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    com.example.ui.components.ConnectionStatusPill(
                        quality = callQuality,
                        onClick = { viewModel.toggleCallQualityTest() },
                        modifier = Modifier.padding(end = 6.dp)
                    )
                    val isLiveCallActive = isVoiceMode || isCallActive || (callState != CallState.IDLE)
                    // Live Call Action: initiates live call seamlessly or ends call on tap with zero glitch
                    IconButton(
                        onClick = {
                            if (isLiveCallActive) {
                                if (isVoiceMode) viewModel.stopVoiceMode()
                                if (isCallActive || callState != CallState.IDLE) viewModel.endCall()
                            } else {
                                viewModel.startVoiceMode()
                            }
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("phone_call_top_bar_button")
                    ) {
                        Icon(
                            imageVector = if (isLiveCallActive) Icons.Default.CallEnd else Icons.Default.Call,
                            contentDescription = if (isLiveCallActive) "End Live Call" else "Initiate Live Call",
                            tint = if (isLiveCallActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = { activeBottomSheet = AssistantScreen.AI_STUDIO_LAB },
                        modifier = Modifier.testTag("open_ai_lab_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = "AI Studio & JARVIS Lab",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = { activeBottomSheet = AssistantScreen.MEMORY },
                        modifier = Modifier.testTag("open_memory_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = "Memories",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    /*IconButton(
                        onClick = { activeBottomSheet = AssistantScreen.TOOLS_CATALOG },
                        modifier = Modifier.testTag("open_tools_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = "Tools",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }*/

                    IconButton(
                        onClick = { activeBottomSheet = AssistantScreen.SETTINGS },
                        modifier = Modifier.testTag("open_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
        ) {
            // Offline Notification Banner
            AnimatedVisibility(visible = !isNetworkAvailable) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth(),
                    shadowElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier
                            .padding(vertical = 8.dp, horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudOff,
                            contentDescription = "Offline",
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Offline Mode — On-device voice, commands & local device controls active",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Sleek Segmented Navigation Tab Switcher (AI Assistant vs Call History)
            val callSessionsList by viewModel.callSessions.collectAsState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Tab 1: AI Assistant Chat
                Surface(
                    onClick = { selectedMainTab = MainAppTab.CHAT },
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .testTag("tab_assistant_chat"),
                    shape = RoundedCornerShape(11.dp),
                    color = if (selectedMainTab == MainAppTab.CHAT) MaterialTheme.colorScheme.surface else Color.Transparent,
                    shadowElevation = if (selectedMainTab == MainAppTab.CHAT) 2.dp else 0.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = null,
                            tint = if (selectedMainTab == MainAppTab.CHAT) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "AI Assistant",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selectedMainTab == MainAppTab.CHAT) FontWeight.Bold else FontWeight.Medium,
                            color = if (selectedMainTab == MainAppTab.CHAT) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Tab 2: Call History
                Surface(
                    onClick = { selectedMainTab = MainAppTab.CALL_HISTORY },
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .testTag("tab_call_history"),
                    shape = RoundedCornerShape(11.dp),
                    color = if (selectedMainTab == MainAppTab.CALL_HISTORY) MaterialTheme.colorScheme.surface else Color.Transparent,
                    shadowElevation = if (selectedMainTab == MainAppTab.CALL_HISTORY) 2.dp else 0.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhoneInTalk,
                            contentDescription = null,
                            tint = if (selectedMainTab == MainAppTab.CALL_HISTORY) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Call History",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selectedMainTab == MainAppTab.CALL_HISTORY) FontWeight.Bold else FontWeight.Medium,
                            color = if (selectedMainTab == MainAppTab.CALL_HISTORY) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (callSessionsList.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(5.dp))
                            Surface(
                                shape = CircleShape,
                                color = if (selectedMainTab == MainAppTab.CALL_HISTORY) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = "${callSessionsList.size}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selectedMainTab == MainAppTab.CALL_HISTORY) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (selectedMainTab == MainAppTab.CALL_HISTORY) {
                CallHistoryTab(
                    viewModel = viewModel,
                    onNavigateToChat = { selectedMainTab = MainAppTab.CHAT },
                    modifier = Modifier.weight(1f)
                )
            } else {
                // Active / Incoming Call Banner (Calling only on button tap, ending on tap)
            AnimatedVisibility(visible = isCallActive || callState != CallState.IDLE) {
                Surface(
                    color = if (callState == CallState.RINGING) Color(0xFF1B3D2F) else MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(16.dp),
                    shadowElevation = 3.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .testTag("active_call_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        if (callState == CallState.RINGING) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (callState == CallState.RINGING) Icons.Default.Call else Icons.Default.CallEnd,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = if (callState == CallState.RINGING) "Incoming Call..." else "Call Active",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (callState == CallState.RINGING) Color.White else MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = if (callState == CallState.RINGING) "Tap Answer or Decline" else "Tap End Call button to hang up",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 11.sp,
                                    color = if (callState == CallState.RINGING) Color(0xFFA5D6A7) else MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (callState == CallState.RINGING) {
                                Button(
                                    onClick = { viewModel.answerCall() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.testTag("banner_answer_call_button")
                                ) {
                                    Text("Answer", color = Color.White, fontSize = 12.sp)
                                }
                            }
                            Button(
                                onClick = { viewModel.endCall() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("banner_end_call_button")
                            ) {
                                Text(if (callState == CallState.RINGING) "Decline" else "End", color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Rate Limit Cooldown Banner
            AnimatedVisibility(visible = isRateLimited && isNetworkAvailable) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = rateLimitNotice,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Offline Chat Data Synchronization Indicator
            AnimatedVisibility(visible = syncStatus is SyncStatus.Syncing) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .testTag("offline_sync_indicator_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Synchronizing offline chat data with cloud...",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Real-time SessionManager State Dashboard (Mic activation, audio focus, network telemetry)
            com.example.ui.components.SessionStatusDashboard(
                sessionState = sessionState,
                isMicActive = isMicActiveReal,
                isAudioFocusHeld = isAudioFocusHeld,
                isNetworkAvailable = isNetworkAvailable,
                isBatterySaverActive = isBatterySaverActive,
                powerState = powerState,
                isHardwareReleased = isHardwareReleased,
                onToggleBatterySaver = { enabled -> viewModel.toggleBatterySaver(enabled) },
                onReleaseSession = { viewModel.sessionManager.releaseActiveSession() }
            )

            // Chat conversation list or Clean Empty State
            if (!hasMessages) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                    ) {
                        VoiceOrb(
                            isListening = false,
                            isSpeaking = false,
                            isThinking = false,
                            size = 120.dp
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = "Alya",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "How can I help you today?",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(messages, key = { it.id }) { message ->
                        MessageItem(
                            message = message,
                            onSpeakMessage = onSpeakMessageCallback,
                            onConfirmAction = onConfirmActionCallback,
                            onCancelAction = onCancelActionCallback
                        )
                    }

                    if (isThinking) {
                        item {
                            Row(
                                modifier = Modifier
                                    .padding(vertical = 8.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Alya is thinking...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            }



            // Sleek Interface Input Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .navigationBarsPadding()
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(28.dp),
                    shadowElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val isRecordingNow = isListening || isRecordingVoiceMessage

                        // Text Field inside sleek pill container
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = {
                                Text(
                                    if (isRecordingNow) "Listening... Speak now (tap stop to send)" else "Type or tap mic to speak...",
                                    color = if (isRecordingNow) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("chat_input_text_field"),
                            shape = RoundedCornerShape(20.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(
                                onSend = {
                                    if (inputText.isNotBlank()) {
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                        viewModel.sendMessage(inputText)
                                        inputText = ""
                                    }
                                }
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                                focusedBorderColor = if (isRecordingNow) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else Color.Transparent,
                                unfocusedBorderColor = if (isRecordingNow) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else Color.Transparent
                            )
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        // Unified Action Button: Glitch-free, stable 48dp sizing across all states
                        if (isRecordingNow) {
                            // Active audio recording button on tap for stopping and sending immediately
                            IconButton(
                                onClick = { viewModel.stopAndSendDirectVoiceRecording(inputText) },
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(MaterialTheme.colorScheme.error, CircleShape)
                                    .testTag("chat_mic_record_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Stop,
                                    contentDescription = "Stop & Send Recording",
                                    tint = Color.White
                                )
                            }
                        } else if (isSpeaking) {
                            IconButton(
                                onClick = { viewModel.stopSpeaking() },
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(MaterialTheme.colorScheme.errorContainer, CircleShape)
                                    .testTag("stop_speaking_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Stop,
                                    contentDescription = "Stop Speaking",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        } else if (inputText.isNotBlank()) {
                            IconButton(
                                onClick = {
                                    focusManager.clearFocus()
                                    keyboardController?.hide()
                                    viewModel.sendMessage(inputText)
                                    inputText = ""
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                                    .testTag("send_message_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Send,
                                    contentDescription = "Send",
                                    tint = Color.White
                                )
                            }
                        } else {
                            // Audio recording on tap for direct sending
                            IconButton(
                                onClick = { viewModel.startDirectVoiceRecording() },
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                                    .testTag("voice_mode_trigger_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "Record & Send Audio",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }

                // Sleek Home indicator bar
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .width(96.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.18f))
                        .align(Alignment.CenterHorizontally)
                )
            }
            }
        }

        // Small bottom listening pop-up (activated on wake words Alia, Alya, Seno)
        com.example.ui.components.ListeningPopup(
            viewModel = viewModel,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
        )
    }

    // Modal Bottom Sheets for History, Memory, Tools, Settings
    activeBottomSheet?.let { screen ->
        ModalBottomSheet(
            onDismissRequest = { activeBottomSheet = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            when (screen) {
                AssistantScreen.HISTORY -> {
                    HistorySheet(
                        conversations = conversations,
                        activeConversationId = activeConvId,
                        onSelectConversation = { viewModel.selectConversation(it) },
                        onNewConversation = { viewModel.createNewConversation() },
                        onRenameConversation = { id, title -> viewModel.renameConversation(id, title) },
                        onDeleteConversation = { viewModel.deleteConversation(it) },
                        onClearAll = { viewModel.clearAllConversations() },
                        onClose = { activeBottomSheet = null }
                    )
                }
                AssistantScreen.MEMORY -> {
                    MemorySheet(
                        memories = memories,
                        isMemoryEnabled = memoryEnabled,
                        onToggleMemory = { viewModel.repository.preferences.setMemoryEnabled(it) },
                        onAddMemory = { cat, k, c -> viewModel.addMemory(cat, k, c) },
                        onDeleteMemory = { viewModel.deleteMemory(it) },
                        onClearAll = { viewModel.clearAllMemories() },
                        onClose = { activeBottomSheet = null }
                    )
                }
                AssistantScreen.TOOLS_CATALOG -> {
                    ToolsCatalogSheet(
                        onTestTool = { action ->
                            activeBottomSheet = null
                            viewModel.sendMessage("Run test: ${action.toolName}")
                        },
                        onClose = { activeBottomSheet = null }
                    )
                }
                AssistantScreen.SETTINGS -> {
                    SettingsScreen(
                        preferencesManager = viewModel.repository.preferences,
                        appUpdateManager = viewModel.updateManager,
                        onOpenDeviceLink = { activeBottomSheet = AssistantScreen.DEVICE_LINK },
                        onOpenAccountSecurity = { activeBottomSheet = AssistantScreen.ACCOUNT_SECURITY },
                        onOpenPermissionsCapabilities = { activeBottomSheet = AssistantScreen.PERMISSIONS_CAPABILITIES },
                        onOpenWakeUpActivation = { viewModel.openWakeUpActivationDialog() },
                        onOpenCallTranscripts = { activeBottomSheet = AssistantScreen.CALL_TRANSCRIPTS },
                        onOpenAiStudioLab = { activeBottomSheet = AssistantScreen.AI_STUDIO_LAB },
                        onCheckForUpdates = { viewModel.checkForUpdatesManual() },
                        onTestVoice = { sampleText -> viewModel.speakResponse(sampleText) },
                        onClose = { activeBottomSheet = null }
                    )
                }
                AssistantScreen.AI_STUDIO_LAB -> {
                    AiStudioLabScreen(
                        viewModel = viewModel,
                        onNavigateBack = { activeBottomSheet = null }
                    )
                }
                AssistantScreen.PERMISSIONS_CAPABILITIES -> {
                    PermissionsDashboard(
                        viewModel = viewModel,
                        onClose = { activeBottomSheet = null },
                        onOpenWakeUpActivation = { viewModel.openWakeUpActivationDialog() }
                    )
                }
                AssistantScreen.ACCOUNT_SECURITY -> {
                    AccountSecuritySheet(
                        authManager = viewModel.authManager,
                        onClose = { activeBottomSheet = null }
                    )
                }
                AssistantScreen.TASKS -> {
                    TasksSheet(
                        viewModel = viewModel,
                        onClose = { activeBottomSheet = null }
                    )
                }
                AssistantScreen.DEVICE_LINK -> {
                    DeviceLinkSheet(
                        viewModel = viewModel,
                        onClose = { activeBottomSheet = null }
                    )
                }
                AssistantScreen.DIAGNOSTICS -> {
                    ModalBottomSheet(
                        onDismissRequest = { activeBottomSheet = null },
                        sheetState = sheetState
                    ) {
                        DiagnosticLogScreen(
                            onBack = { activeBottomSheet = null }
                        )
                    }
                }
                AssistantScreen.COMMAND_HISTORY -> {
                    ModalBottomSheet(
                        onDismissRequest = { activeBottomSheet = null },
                        sheetState = sheetState
                    ) {
                        CommandHistoryScreen(
                            onBack = { activeBottomSheet = null }
                        )
                    }
                }
                AssistantScreen.COMPATIBILITY -> {
                    ModalBottomSheet(
                        onDismissRequest = { activeBottomSheet = null },
                        sheetState = sheetState
                    ) {
                        CompatibilityScreen(
                            onBack = { activeBottomSheet = null }
                        )
                    }
                }
                AssistantScreen.CALL_TRANSCRIPTS, AssistantScreen.CALL_HISTORY -> {
                    ModalBottomSheet(
                        onDismissRequest = { activeBottomSheet = null },
                        sheetState = sheetState
                    ) {
                        CallTranscriptsScreen(
                            viewModel = viewModel,
                            onBack = { activeBottomSheet = null }
                        )
                    }
                }
                else -> {}
            }
        }
    }

    // Persistent / Expandable AI Assistant Sidebar Drawer
    AssistantSidebarDrawer(
        viewModel = viewModel,
        isOpen = isSidebarOpen,
        onClose = { viewModel.toggleSidebar(false) },
        onOpenVoiceMode = { viewModel.startVoiceMode() },
        onOpenSettings = { activeBottomSheet = AssistantScreen.SETTINGS },
        onOpenMemory = { activeBottomSheet = AssistantScreen.MEMORY },
        onOpenTasks = { activeBottomSheet = AssistantScreen.TASKS },
        onOpenTools = { activeBottomSheet = AssistantScreen.TOOLS_CATALOG },
        onOpenCommandHistory = { activeBottomSheet = AssistantScreen.COMMAND_HISTORY },
        onOpenCompatibility = { activeBottomSheet = AssistantScreen.COMPATIBILITY },
        onOpenDiagnostics = { activeBottomSheet = AssistantScreen.DIAGNOSTICS },
        onOpenCallTranscripts = { 
            selectedMainTab = MainAppTab.CALL_HISTORY
            viewModel.toggleSidebar(false)
        },
        onOpenWalkthrough = { showOnboardingWalkthrough = true },
        onSelectConversation = { convId -> viewModel.selectConversation(convId) },
        onNewChat = { viewModel.startNewConversation() }
    )

    if (showOnboardingWalkthrough) {
        com.example.ui.components.OnboardingWalkthroughModal(
            onDismiss = { showOnboardingWalkthrough = false },
            onRequestPermissions = {
                activeBottomSheet = AssistantScreen.PERMISSIONS_CAPABILITIES
            }
        )
    }

    if (showCommandGuideModal) {
        com.example.ui.components.CommandGuideModal(
            onDismiss = { showCommandGuideModal = false },
            onExecuteCommand = { cmd ->
                showCommandGuideModal = false
                viewModel.sendMessage(cmd)
            }
        )
    }
}
}

@Composable
private fun SleekActionChip(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.height(34.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
