package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.AlyaViewModel
import com.example.voice.wakeword.PREDEFINED_WAKE_WORDS
import com.example.voice.wakeword.WakeWordOption

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WakeWordSettingsSheet(
    viewModel: AlyaViewModel,
    onDismiss: () -> Unit,
    onOpenEnrollmentDialog: () -> Unit
) {
    val selectedWakeWord by viewModel.selectedWakeWord.collectAsState()
    val sensitivity by viewModel.wakeWordSensitivity.collectAsState()
    val wakeWordAckSoundEnabled by viewModel.wakeWordAckSoundEnabled.collectAsState()
    val isVoiceProfileSet by viewModel.isVoiceProfileSet.collectAsState()
    val sampleCount by viewModel.voiceProfileSampleCount.collectAsState()
    val onlyOwnerVoiceWakes by viewModel.onlyOwnerVoiceWakes.collectAsState()
    val pauseDuringCalls by viewModel.pauseDuringCallsAndRecording.collectAsState()
    val neverWakePlayback by viewModel.neverWakeDuringPlayback.collectAsState()
    val voiceTrainingSummary by viewModel.voiceTrainingSummary.collectAsState()
    val isRecordingCustomWakeWord by viewModel.isRecordingCustomWakeWord.collectAsState()
    val customWakeWordRecordDuration by viewModel.customWakeWordRecordDuration.collectAsState()
    val allCustomWakeWords by viewModel.allCustomWakeWords.collectAsState()
    val activeCustomWakeWord by viewModel.activeCustomWakeWord.collectAsState()

    var playingAudioPath by remember { mutableStateOf<String?>(null) }
    var mediaPlayer by remember { mutableStateOf<android.media.MediaPlayer?>(null) }

    fun playAudio(path: String) {
        try {
            mediaPlayer?.release()
            val mp = android.media.MediaPlayer().apply {
                setDataSource(path)
                prepare()
                start()
                setOnCompletionListener {
                    playingAudioPath = null
                    mediaPlayer = null
                }
            }
            mediaPlayer = mp
            playingAudioPath = path
        } catch (e: Exception) {
            android.util.Log.e("WakeWordSettingsSheet", "Error playing wake word audio: ${e.message}")
        }
    }

    fun stopAudio() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) { android.util.Log.e("Alya", "Exception handled", e) }
        mediaPlayer = null
        playingAudioPath = null
    }

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.release()
        }
    }

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var deleteFeedbackMessage by remember { mutableStateOf<String?>(null) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val powerManager = remember(context) { context.getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager }
    var isIgnoringBatteryOptimizations by remember {
        mutableStateOf(
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
            } else true
        )
    }

    var customWakeWordInput by remember { mutableStateOf(selectedWakeWord) }
    var testResultText by remember { mutableStateOf<String?>(null) }
    var isTestingWakeWord by remember { mutableStateOf(false) }

    LaunchedEffect(selectedWakeWord) {
        customWakeWordInput = selectedWakeWord
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Column {
                        Text(
                            text = "Voice Wake-Up & Speaker ID",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Select assistant trigger name & dynamic recognition",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                }
            }

            Divider()

            // 1. Predefined & Custom Wake Word Names
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.RecordVoiceOver,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Assistant Wake Word (Trigger Name)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Text(
                        text = "Choose from predefined voice trigger profiles or enter a custom name. The recognition engine updates immediately in the background.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Predefined Options Grid / List
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PREDEFINED_WAKE_WORDS.forEach { option ->
                            val isSelected = selectedWakeWord.equals(option.displayName, ignoreCase = true) ||
                                    (option.displayName.equals("Alya", ignoreCase = true) && selectedWakeWord.isBlank())

                            Surface(
                                onClick = {
                                    customWakeWordInput = option.displayName
                                    viewModel.setSelectedWakeWord(option.displayName)
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("wake_word_option_${option.id}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = if (isSelected) Icons.Default.Check else Icons.Default.Mic,
                                                contentDescription = null,
                                                tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        Column {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = option.displayName,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                                )
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                                                ) {
                                                    Text(
                                                        text = option.pronunciation,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                            Text(
                                                text = option.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1
                                            )
                                        }
                                    }

                                    if (isSelected) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.primary
                                        ) {
                                            Text(
                                                text = "ACTIVE",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Or enter a custom assistant trigger name:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    var validationError by remember { mutableStateOf<String?>(null) }
                    OutlinedTextField(
                        value = customWakeWordInput,
                        onValueChange = {
                            customWakeWordInput = it
                            if (com.example.voice.wakeword.TriggerWordConfig.isValid(it)) {
                                validationError = null
                                try {
                                    viewModel.setSelectedWakeWord(it)
                                } catch (e: Exception) { android.util.Log.e("Alya", "Exception handled", e) }
                            } else {
                                validationError = "Must be 2-20 letters (no spaces/special chars)"
                            }
                        },
                        label = { Text("Custom Wake Name (e.g. jarvis, nova)") },
                        isError = validationError != null,
                        supportingText = {
                            if (validationError != null) Text(validationError ?: "")
                            else Text("Changes apply immediately without restart")
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_wake_word_input"),
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = {
                                customWakeWordInput = "alya"
                                validationError = null
                                try {
                                    viewModel.setSelectedWakeWord("alya")
                                } catch (e: Exception) { android.util.Log.e("Alya", "Exception handled", e) }
                            }) {
                                Icon(imageVector = Icons.Default.Restore, contentDescription = "Reset to default")
                            }
                        }
                    )

                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    Text(
                        text = "Record Reference Voice Signature",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "To improve local detection accuracy of your custom wake-word, record a 2-second reference file of you saying the name.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (isRecordingCustomWakeWord) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.error),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "Recording",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Recording audio...",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = "Speak: \"$customWakeWordInput\" ($customWakeWordRecordDuration s)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = { viewModel.cancelRecordingCustomWakeWord() }
                            ) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "Cancel Recording", tint = MaterialTheme.colorScheme.error)
                            }
                            Button(
                                onClick = { viewModel.stopRecordingCustomWakeWord(customWakeWordInput, makeActive = true) },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Save")
                            }
                        }
                    } else {
                        Button(
                            onClick = { viewModel.startRecordingCustomWakeWord() },
                            enabled = customWakeWordInput.isNotBlank(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("record_custom_wake_word_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        ) {
                            Icon(imageVector = Icons.Default.Mic, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Record \"${if (customWakeWordInput.isBlank()) "Custom Name" else customWakeWordInput}\" Voice Reference")
                        }
                    }

                    // Display saved custom wake words from Room database
                    if (allCustomWakeWords.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Recorded Custom Wake Words (Saved in Room DB):",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            allCustomWakeWords.forEach { customWord ->
                                val isCurrentActive = customWord.isActive
                                Surface(
                                    onClick = {
                                        viewModel.setCustomWakeWordActive(customWord.id, customWord.word)
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isCurrentActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isCurrentActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            IconButton(
                                                onClick = {
                                                    if (playingAudioPath == customWord.audioFilePath) {
                                                        stopAudio()
                                                    } else {
                                                        playAudio(customWord.audioFilePath)
                                                    }
                                                },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (playingAudioPath == customWord.audioFilePath) Icons.Default.Stop else Icons.Default.PlayArrow,
                                                    contentDescription = "Play/Stop Reference",
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }

                                            Column {
                                                Text(
                                                    text = customWord.word,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "Saved reference audio path exists",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            if (isCurrentActive) {
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = MaterialTheme.colorScheme.primary
                                                ) {
                                                    Text(
                                                        text = "ACTIVE",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onPrimary,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            IconButton(
                                                onClick = {
                                                    viewModel.deleteCustomWakeWord(customWord.id, customWord.audioFilePath)
                                                }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Delete Wake Word",
                                                    tint = MaterialTheme.colorScheme.error
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

            // 2. Enrolled Voice Status Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isVoiceProfileSet || voiceTrainingSummary.totalRecordings > 0) Color(0xFF1B4332).copy(alpha = 0.15f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (voiceTrainingSummary.totalRecordings > 0) "Voice Training Active (${voiceTrainingSummary.totalRecordings} recordings)" else "Voice Enrollment Needed",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (voiceTrainingSummary.totalRecordings > 0) Color(0xFF2D6A4F) else MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = if (voiceTrainingSummary.totalRecordings > 0)
                                    "Recorded in ${voiceTrainingSummary.environmentTotals.count { it.value > 0 }} environment(s). Recognizes YOUR voice and ignores strangers."
                                else
                                    "Record 3–5 samples of your voice across environments (quiet room, kitchen, car, outdoors).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = onOpenEnrollmentDialog,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(if (voiceTrainingSummary.totalRecordings > 0) "Train More" else "Enroll Now")
                        }
                    }

                    if (voiceTrainingSummary.totalRecordings > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = { showDeleteConfirmDialog = true },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Delete All Voice Recordings", fontSize = 12.sp)
                            }
                        }
                    }

                    deleteFeedbackMessage?.let { msg ->
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // 3. Sensitivity Slider
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Wake Sensitivity",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        val sensLabel = when {
                            sensitivity >= 0.8f -> "High (0.60 threshold)"
                            sensitivity <= 0.3f -> "Low (0.85 threshold)"
                            else -> "Medium (0.75 threshold)"
                        }
                        Text(
                            text = sensLabel,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Slider(
                        value = sensitivity,
                        onValueChange = { viewModel.setWakeWordSensitivity(it) },
                        valueRange = 0.0f..1.0f,
                        steps = 2,
                        modifier = Modifier.testTag("wake_sensitivity_slider")
                    )
                }
            }

            // 4. Verification & Feedback Toggles
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Verbal Acknowledgment Sound Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Verbal & Chime Acknowledgment",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Play audible chime or spoken confirmation when wake-word is detected. Disable for discreet silent mode.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = wakeWordAckSoundEnabled,
                        onCheckedChange = { viewModel.setWakeWordAckSoundEnabled(it) },
                        modifier = Modifier.testTag("wake_ack_sound_switch")
                    )
                }

                // Only my voice wakes it
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Only my voice wakes it",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Layer 2 verification: Cosine similarity >= 0.75 check",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = onlyOwnerVoiceWakes,
                        onCheckedChange = { viewModel.setOnlyOwnerVoiceWakes(it) },
                        modifier = Modifier.testTag("only_owner_voice_switch")
                    )
                }

                // Pause during calls & recording
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Pause during calls & recording",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Pause wake listener during phone/WhatsApp calls",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = pauseDuringCalls,
                        onCheckedChange = { viewModel.setPauseDuringCallsAndRecording(it) },
                        modifier = Modifier.testTag("pause_during_calls_switch")
                    )
                }

                // Never wake during audio playback
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Never wake during media playback",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Prevents TV or music speakers from triggering wake",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = neverWakePlayback,
                        onCheckedChange = { viewModel.setNeverWakeDuringPlayback(it) },
                        modifier = Modifier.testTag("never_wake_playback_switch")
                    )
                }
            }

            // 5. Battery Optimization Exemption Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isIgnoringBatteryOptimizations) Color(0xFF1B4332).copy(alpha = 0.15f) else MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.BatteryChargingFull,
                                contentDescription = null,
                                tint = if (isIgnoringBatteryOptimizations) Color(0xFF2D6A4F) else MaterialTheme.colorScheme.tertiary
                            )
                            Text(
                                text = "Battery Optimization Status",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isIgnoringBatteryOptimizations) Color(0xFF2D6A4F) else MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isIgnoringBatteryOptimizations) Color(0xFF2D6A4F) else MaterialTheme.colorScheme.tertiary
                        ) {
                            Text(
                                text = if (isIgnoringBatteryOptimizations) "EXEMPT / ACTIVE" else "RESTRICTED BY OS",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Text(
                        text = if (isIgnoringBatteryOptimizations)
                            "Alya is exempted from Android battery restrictions. The background wake-word listener will run reliably 24/7."
                        else
                            "Android battery optimizations may suspend background voice recognition after idle periods. Exempt Alya for 100% uninterrupted wake-word detection.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (!isIgnoringBatteryOptimizations) {
                        Button(
                            onClick = {
                                try {
                                    val intent = android.content.Intent().apply {
                                        action = android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                                        data = android.net.Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    try {
                                        val intent = android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                        context.startActivity(intent)
                                    } catch (ex: Exception) {
                                        // Ignore fallback
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(imageVector = Icons.Default.Shield, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Exempt Alya from Battery Restrictions")
                        }
                    }
                }
            }

            // 5. Test Button ("Try waking me")
            OutlinedButton(
                onClick = {
                    isTestingWakeWord = true
                    viewModel.testWakeWordSpeakerVerification { resultText ->
                        testResultText = resultText
                        isTestingWakeWord = false
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("test_wake_word_button"),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(imageVector = Icons.Default.Mic, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = if (isTestingWakeWord) "Listening..." else "Try Waking Me (Live Verification)")
            }

            testResultText?.let { resText ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (resText.contains("✅")) Color(0xFF1B4332) else Color(0xFF5C0011)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = resText,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text("Confirm Delete")
            },
            text = {
                Text("This will permanently delete all your voice recordings. I'll lose the ability to recognize your voice. Confirm delete?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        val result = viewModel.confirmDeleteAllVoiceRecordings()
                        deleteFeedbackMessage = result
                        showDeleteConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
