package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import com.example.voice.wakeword.PREDEFINED_WAKE_WORDS
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.local.PreferencesManager
import com.example.data.local.PreferencesAndHistoryBackupManager
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.SettingsBackupRestore
import com.example.ui.components.PermissionCapabilitiesDashboard
import com.example.ui.components.AppUpdateCard
import com.example.update.AppUpdateManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    preferencesManager: PreferencesManager,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    appUpdateManager: AppUpdateManager? = null,
    onOpenDeviceLink: (() -> Unit)? = null,
    onOpenAccountSecurity: (() -> Unit)? = null,
    onOpenPermissionsCapabilities: (() -> Unit)? = null,
    onOpenWakeUpActivation: (() -> Unit)? = null,
    onOpenCallTranscripts: (() -> Unit)? = null,
    onShowAccessibilityGuidance: (() -> Unit)? = null,
    onCheckForUpdates: (() -> Unit)? = null,
    onTestVoice: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val updateManagerInstance = appUpdateManager ?: remember { AppUpdateManager(context) }

    val memoryEnabled by preferencesManager.memoryEnabled.collectAsState()
    val bgVoiceMode by preferencesManager.backgroundVoiceMode.collectAsState()
    val confirmationLevel by preferencesManager.confirmationLevel.collectAsState()
    val themeMode by preferencesManager.themeMode.collectAsState()
    val speechRate by preferencesManager.speechRate.collectAsState()
    val speechPitch by preferencesManager.speechPitch.collectAsState()
    val voiceLanguage by preferencesManager.voiceLanguage.collectAsState()
    val voicePersona by preferencesManager.voicePersona.collectAsState()
    val responseStyle by preferencesManager.responseStyle.collectAsState()
    val updateCheckUrl by preferencesManager.updateCheckUrl.collectAsState()
    val fpsBoostEnabled by preferencesManager.fpsBoostEnabled.collectAsState()

    var backupStatus by remember { mutableStateOf(PreferencesAndHistoryBackupManager.getBackupFileInfo(context)) }
    var backupInProgress by remember { mutableStateOf(false) }

    val micGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    val cameraGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    val phoneGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Alya Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Configure AI, Voice & Security Vault",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            TextButton(
                onClick = onClose,
                modifier = Modifier.testTag("close_settings_button")
            ) {
                Text("Done")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Section 1: AI Configuration
        SettingsSectionHeader(icon = Icons.Default.SmartToy, title = "Alya Assistant Intelligence")
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                val currentVersionName = updateManagerInstance.getCurrentVersionName()
                Text(
                    text = "Alya Assistant Core v$currentVersionName (Optimized)",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )

                // Response Style Dropdown
                var styleExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = styleExpanded,
                    onExpandedChange = { styleExpanded = !styleExpanded }
                ) {
                    OutlinedTextField(
                        value = responseStyle,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Response Style") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = styleExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = styleExpanded,
                        onDismissRequest = { styleExpanded = false }
                    ) {
                        listOf("Concise", "Balanced", "Detailed").forEach { style ->
                            DropdownMenuItem(
                                text = { Text(style) },
                                onClick = {
                                    preferencesManager.setResponseStyle(style)
                                    styleExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Section 2: Voice & Speech Engine
        SettingsSectionHeader(icon = Icons.Default.RecordVoiceOver, title = "Voice & Speech Engine")
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Voice Tone & Persona Selector
                var toneExpanded by remember { mutableStateOf(false) }
                val currentPersonaOption = remember(voicePersona) {
                    PreferencesManager.AVAILABLE_VOICE_PERSONAS.firstOrNull { it.id.equals(voicePersona, ignoreCase = true) }
                        ?: PreferencesManager.AVAILABLE_VOICE_PERSONAS.first()
                }

                fun getAuditionPhrase(personaId: String, lang: String): String {
                    val langPrefix = lang.lowercase().take(2)
                    return when (personaId.uppercase()) {
                        "GENTLE_SOFT", "SOFT_MELODIC", "ALYA_WARM_COMPANION" -> when (langPrefix) {
                            "hi" -> "नमस्ते। मैं आलिया हूँ। शांत और कोमल स्वर में, मैं हमेशा आपकी सहायता के लिए यहाँ हूँ।"
                            "bn" -> "নমস্কার। আমি আলিয়া। শান্ত ও মিষ্টি কণ্ঠে, আমি সর্বদা আপনার সেবায় প্রস্তুত।"
                            "ja" -> "こんにちは。アーリャです。穏やかで優しい声で、いつでもあなたをサポートします。"
                            else -> "Hello. I am Alya. With a calm and gentle voice, I am always here to assist you."
                        }
                        "CRISP_CONFIDENT", "EXECUTIVE", "ALYA_EXECUTIVE_CRISP" -> when (langPrefix) {
                            "hi" -> "नमस्ते! आलिया तैयार है। सभी कमांड्स और टास्क पूरी स्पष्टता और गति के साथ निष्पादित होंगे।"
                            "bn" -> "নমস্কার! আলিয়া প্রস্তুত। সমস্ত নির্দেশাবলী দ্রুত ও নির্ভুলভাবে সম্পন্ন করা হবে।"
                            "ja" -> "こんにちは！アーリャです。すべてのタスクを迅速かつ的確に遂行いたします。"
                            else -> "Hello! Alya is ready. All commands and tasks will be executed with crisp, executive precision."
                        }
                        "LIVELY_PLAYFUL", "ENERGETIC" -> when (langPrefix) {
                            "hi" -> "अरे वाह! नमस्ते! मैं आलिया हूँ, फुल एनर्जी के साथ! आज हम क्या नया करने वाले हैं?"
                            "bn" -> "আরে দারুণ! হ্যালো! আমি আলিয়া, একরাশ উচ্ছ্বাস নিয়ে! আজ আমরা কী করতে পারি?"
                            "ja" -> "やっほー！アーリャだよ！元気いっぱいにサポートするね！今日は何をする？"
                            else -> "Hey there! I am Alya, full of energy and ready to roll! What are we doing today?"
                        }
                        "SWEET_COMPANION", "ANIME_SWEET" -> when (langPrefix) {
                            "hi" -> "नमस्ते प्यारे दोस्त! मैं आलिया हूँ। आपके साथ बात करना मुझे बहुत अच्छा लगता है।"
                            "bn" -> "হ্যালো বন্ধু! আমি আলিয়া। আপনার পাশে থাকতে পেরে আমি ভীষণ খুশি।"
                            "ja" -> "こんにちは！アーリャです。あなたと話せてとても嬉しいな！何でも言ってね。"
                            else -> "Hello there! I am Alya. Having you as a companion brings me so much joy!"
                        }
                        else -> when (langPrefix) {
                            "hi" -> "नमस्ते! मैं आलिया हूँ, आपकी पर्सनल एआई असिस्टेंट। मैं आपकी क्या मदद कर सकती हूँ?"
                            "bn" -> "হ্যালো! আমি আলিয়া, আপনার পার্সোনাল এআই অ্যাসিস্ট্যান্ট। কীভাবে আপনাকে সাহায্য করতে পারি?"
                            "ja" -> "こんにちは！私はアーリャです。何かお手伝いしましょうか？"
                            else -> "Hello! I am Alya, your personal AI voice companion. How can I help you today?"
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ExposedDropdownMenuBox(
                        expanded = toneExpanded,
                        onExpandedChange = { toneExpanded = !toneExpanded }
                    ) {
                        OutlinedTextField(
                            value = currentPersonaOption.displayName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Voice Tone & Persona") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.RecordVoiceOver,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = toneExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = toneExpanded,
                            onDismissRequest = { toneExpanded = false }
                        ) {
                            PreferencesManager.AVAILABLE_VOICE_PERSONAS.forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(option.displayName, fontWeight = FontWeight.SemiBold)
                                            Text(
                                                option.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                        }
                                    },
                                    trailingIcon = {
                                        IconButton(
                                            onClick = {
                                                preferencesManager.setVoicePersona(option.id)
                                                (context.applicationContext as? com.example.AlyaApplication)?.ttsManager?.setPersona(option.id)
                                                val phrase = getAuditionPhrase(option.id, voiceLanguage)
                                                onTestVoice?.invoke(phrase)
                                            }
                                        ) {
                                            Icon(
                                                Icons.Default.PlayArrow,
                                                contentDescription = "Preview ${option.displayName}",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    },
                                    onClick = {
                                        preferencesManager.setVoicePersona(option.id)
                                        (context.applicationContext as? com.example.AlyaApplication)?.ttsManager?.setPersona(option.id)
                                        toneExpanded = false
                                        val phrase = getAuditionPhrase(option.id, voiceLanguage)
                                        onTestVoice?.invoke(phrase)
                                    }
                                )
                            }
                        }
                    }
                    Text(
                        text = currentPersonaOption.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                    )
                }

                // Test Voice Preview Button
                OutlinedButton(
                    onClick = {
                        val testPhrase = getAuditionPhrase(currentPersonaOption.id, voiceLanguage)
                        (context.applicationContext as? com.example.AlyaApplication)?.ttsManager?.setPersona(currentPersonaOption.id)
                        onTestVoice?.invoke(testPhrase)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Test Voice", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Audition Voice (${currentPersonaOption.displayName})")
                }

                // Speech Rate
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Speech Rate", style = MaterialTheme.typography.bodyMedium)
                        Text(String.format("%.2fx", speechRate), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = speechRate,
                        onValueChange = { preferencesManager.setSpeechRate(it) },
                        valueRange = 0.70f..1.50f,
                        steps = 7,
                        modifier = Modifier.testTag("speech_rate_slider")
                    )
                }

                // Speech Pitch
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Voice Pitch", style = MaterialTheme.typography.bodyMedium)
                        Text(String.format("%.2fx", speechPitch), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = speechPitch,
                        onValueChange = { preferencesManager.setSpeechPitch(it) },
                        valueRange = 0.70f..1.40f,
                        steps = 6,
                        modifier = Modifier.testTag("speech_pitch_slider")
                    )
                }

                // Reset Pitch & Speed button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = {
                            preferencesManager.setSpeechRate(1.00f)
                            preferencesManager.setSpeechPitch(1.00f)
                        }
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reset Pitch & Speed", style = MaterialTheme.typography.bodySmall)
                    }
                }

                // Hybrid Voice Bridge (Python Server + EdgeTTS) for warm, soft human voice
                val isBridgeModeEnabled by preferencesManager.bridgeModeEnabled.collectAsState()
                val bridgeServerUrl by preferencesManager.bridgeServerUrl.collectAsState()
                var bridgeUrlText by remember { mutableStateOf(bridgeServerUrl) }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.RecordVoiceOver, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                    Text("Alya Hybrid Voice Bridge", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                }
                                Text("Uses local Python server + EdgeTTS for warm, soft human-like Hindi/English voice.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            }
                            Switch(
                                checked = isBridgeModeEnabled,
                                onCheckedChange = { preferencesManager.setBridgeModeEnabled(it) },
                                modifier = Modifier.testTag("bridge_mode_switch")
                            )
                        }
                        
                        if (isBridgeModeEnabled) {
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = bridgeUrlText,
                                onValueChange = { bridgeUrlText = it },
                                label = { Text("Python Bridge Server URL") },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("ws://10.0.2.2:8000/ws/chat") },
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                textStyle = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { preferencesManager.setBridgeServerUrl(bridgeUrlText) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Apply Server Configuration")
                            }
                        }
                    }
                }

                // Wake-up Assistant Name Input Field (Custom Wake Word)
                val selectedWakeWord by preferencesManager.selectedWakeWord.collectAsState()
                OutlinedTextField(
                    value = selectedWakeWord,
                    onValueChange = { preferencesManager.setSelectedWakeWord(it) },
                    label = { Text("Wake-up Assistant Name") },
                    placeholder = { Text("e.g. Alya, Alisa, Jarvis, Hey computer") },
                    leadingIcon = { Icon(Icons.Default.RecordVoiceOver, contentDescription = "Wake Word Icon", tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.fillMaxWidth()
                )

                // Voice Language Selection component with Primary & Secondary learning languages
                com.example.ui.components.LanguageSelection(preferencesManager = preferencesManager)


                // Background Voice Mode
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Background Voice Mode", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Runs foreground service with ongoing notification for hands-free queries",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    Switch(
                        checked = bgVoiceMode,
                        onCheckedChange = { preferencesManager.setBackgroundVoiceMode(it) },
                        modifier = Modifier.testTag("background_voice_switch")
                    )
                }

                // AI Inbound Call Management & Transcripts
                val autoAnswerCallsWithAi by preferencesManager.autoAnswerCallsWithAi.collectAsState()
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Autonomous AI Call Answering", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Alya conducts unscripted multilingual phone dialogue and logs transcripts.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            Switch(
                                checked = autoAnswerCallsWithAi,
                                onCheckedChange = { preferencesManager.setAutoAnswerCallsWithAi(it) },
                                modifier = Modifier.testTag("settings_auto_answer_calls_switch")
                            )
                        }

                        if (onOpenCallTranscripts != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = onOpenCallTranscripts,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("View Call Transcripts & Summaries")
                            }
                        }
                    }
                }

                // Verbal Acknowledgment Sound
                val wakeWordAckSoundEnabled by preferencesManager.wakeWordAckSoundEnabled.collectAsState()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Verbal Acknowledgment Sound", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Play chime or spoken confirmation on wake-word detection. Turn off for discreet mode.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    Switch(
                        checked = wakeWordAckSoundEnabled,
                        onCheckedChange = { preferencesManager.setWakeWordAckSoundEnabled(it) },
                        modifier = Modifier.testTag("settings_ack_sound_switch")
                    )
                }

                // Battery Optimization Exemption Check
                val powerManager = remember(context) { context.getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager }
                val isIgnoringBatteryOptimizations = remember(context) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
                    } else true
                }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PowerSettingsNew,
                                    contentDescription = null,
                                    tint = if (isIgnoringBatteryOptimizations) MaterialTheme.colorScheme.tertiary else Color(0xFF2D6A4F),
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = if (isIgnoringBatteryOptimizations) "Battery Mode: Unrestricted" else "Battery Mode: Optimised (Recommended)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isIgnoringBatteryOptimizations) MaterialTheme.colorScheme.onSurface else Color(0xFF2D6A4F)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isIgnoringBatteryOptimizations) MaterialTheme.colorScheme.tertiaryContainer else Color(0xFFD8F3DC)
                            ) {
                                Text(
                                    text = if (isIgnoringBatteryOptimizations) "24/7 Background" else "Battery Friendly",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isIgnoringBatteryOptimizations) MaterialTheme.colorScheme.onTertiaryContainer else Color(0xFF1B4332),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Text(
                            text = if (isIgnoringBatteryOptimizations) {
                                "Unrestricted allows Alya to listen for wake words in the background without sleep restrictions, but may consume more battery."
                            } else {
                                "Optimised saves battery by adapting to your app usage. Voice mode, live conversation, and commands work whenever you use Alya."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedButton(
                            onClick = {
                                try {
                                    val intent = android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    try {
                                        val intent = android.content.Intent().apply {
                                            action = android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                                            data = android.net.Uri.parse("package:${context.packageName}")
                                        }
                                        context.startActivity(intent)
                                    } catch (ex: Exception) {
                                        android.util.Log.e("Alya", "Exception opening battery settings", ex)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Manage Battery Mode in Phone Settings", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                // Voice Gender Indicator
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.RecordVoiceOver,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Natural High-Fidelity Female Voice (Exclusive)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Text-to-Speech Voice Pack & Neural Engine Management
                val ttsManager = (context.applicationContext as? com.example.AlyaApplication)?.ttsManager
                val voicePackInfo = remember(voiceLanguage) {
                    ttsManager?.getVoicePackInfo(java.util.Locale.forLanguageTag(voiceLanguage))
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.RecordVoiceOver,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Text-to-Speech Voice Pack",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (voicePackInfo?.isInstalled == true) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = if (voicePackInfo?.isHighQualityNeural == true) "Neural HD" else if (voicePackInfo?.isInstalled == true) "Installed" else "Download Needed",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (voicePackInfo?.isInstalled == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Engine: ${voicePackInfo?.enginePackageName ?: "Google Speech Services"} • Language: ${voicePackInfo?.displayName ?: voiceLanguage}\nActive Voice: ${voicePackInfo?.activeVoiceName ?: "Natural Neural Female Voice"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    ttsManager?.openTtsVoiceDataSettings(context)
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Voice Packs", style = MaterialTheme.typography.labelSmall)
                            }

                            OutlinedButton(
                                onClick = {
                                    ttsManager?.openTtsEngineSettings(context)
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("TTS Settings", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                // User Voice Profile & Wake-Up Activation
                val isWakeUpActivated by preferencesManager.isWakeUpActivated.collectAsState()
                val isVoiceProfileSet by preferencesManager.isVoiceProfileSet.collectAsState()
                var showDeleteVoiceConfirmation by remember { mutableStateOf(false) }

                if (showDeleteVoiceConfirmation) {
                    AlertDialog(
                        onDismissRequest = { showDeleteVoiceConfirmation = false },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        title = { Text("Delete User Voice Profile?") },
                        text = {
                            Text("This will remove your recorded acoustic voice calibration and deactivate automatic wake-up detection. You can re-enroll your voice profile anytime.")
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showDeleteVoiceConfirmation = false
                                    preferencesManager.deleteUserVoiceProfile()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Delete Voice")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDeleteVoiceConfirmation = false }) {
                                Text("Cancel")
                            }
                        }
                    )
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isWakeUpActivated) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                        .background(
                                            if (isWakeUpActivated) MaterialTheme.colorScheme.primaryContainer
                                            else MaterialTheme.colorScheme.surfaceVariant
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Mic,
                                        contentDescription = null,
                                        tint = if (isWakeUpActivated) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "User Voice & Wake-Up",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = when {
                                            isVoiceProfileSet && isWakeUpActivated -> "Profile Active • Wake-Up Listening"
                                            isVoiceProfileSet && !isWakeUpActivated -> "Profile Saved • Wake-Up Paused"
                                            !isVoiceProfileSet && isWakeUpActivated -> "Wake-Up Active • Profile Not Set"
                                            else -> "Wake-Up Deactivated • Profile Not Set"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isWakeUpActivated) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // Wake-Up Active / Deactivate Toggle
                            Switch(
                                checked = isWakeUpActivated,
                                onCheckedChange = { active ->
                                    val app = context.applicationContext as? com.example.AlyaApplication
                                    if (active) {
                                        preferencesManager.activateWakeUp()
                                        val kw = preferencesManager.selectedWakeWord.value
                                        val sens = preferencesManager.wakeWordSensitivity.value
                                        app?.wakeWordManager?.start(kw, sens)
                                        com.example.service.WakeWordService.start(context)
                                    } else {
                                        preferencesManager.deactivateWakeUp()
                                        app?.wakeWordManager?.stop()
                                        com.example.service.WakeWordService.stop(context)
                                    }
                                },
                                modifier = Modifier.testTag("wake_up_active_toggle")
                            )
                        }

                        if (isVoiceProfileSet) {
                            Text(
                                text = "Calibrated for: \"Hey Alya, what is the temperature outside\", \"Hey Alya, add milk in my bucket list\", and \"Hey Alya, set a alarm 7 am\".",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { showDeleteVoiceConfirmation = true },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Delete Voice", style = MaterialTheme.typography.labelMedium)
                                }

                                Button(
                                    onClick = { onOpenWakeUpActivation?.invoke() },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Re-train", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        } else {
                            Text(
                                text = "Set up your voice profile so Alya responds to you with high accuracy when saying \"Hey Alya\", \"Alia\", or \"Seno\".",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Button(
                                onClick = { onOpenWakeUpActivation?.invoke() },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RecordVoiceOver,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isWakeUpActivated) "Calibrate User Voice Profile" else "Activate Wake-Up & Voice Profile",
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                // Wake-up Name (Wake Word)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Wake-up Keyword (Predefined & Custom)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Tap a predefined name to switch recognition immediately, or enter a custom name:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Horizontal list of predefined wake words
                    val predefinedScrollState = rememberScrollState()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(predefinedScrollState),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PREDEFINED_WAKE_WORDS.forEach { option ->
                            val isSelected = selectedWakeWord.equals(option.displayName, ignoreCase = true) ||
                                    (option.displayName.equals("Alya", ignoreCase = true) && selectedWakeWord.isBlank())

                            Surface(
                                onClick = {
                                    preferencesManager.setSelectedWakeWord(option.displayName)
                                    com.example.service.WakeWordService.notifyWakeWordConfigChanged(context, option.displayName)
                                },
                                shape = RoundedCornerShape(20.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.testTag("settings_wake_word_${option.id}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Text(
                                        text = option.displayName,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = selectedWakeWord,
                        onValueChange = {
                            preferencesManager.setSelectedWakeWord(it)
                            if (it.isNotBlank()) {
                                com.example.service.WakeWordService.notifyWakeWordConfigChanged(context, it)
                            }
                        },
                        label = { Text("Custom Trigger Name") },
                        placeholder = { Text("e.g. Alya, Alia, Seno, Jarvis, Luna") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_custom_wake_word_input"),
                        singleLine = true
                    )

                    // Custom Wake Word Recording Component
                    if (selectedWakeWord.isNotBlank() && !PREDEFINED_WAKE_WORDS.any { it.displayName.equals(selectedWakeWord, ignoreCase = true) }) {
                        com.example.ui.components.CustomWakeWordRecorder(
                            preferencesManager = preferencesManager,
                            onRecordingComplete = {
                                scope.launch {
                                    // In a real implementation, we would save the audio reference to the database here.
                                    // For now, we update the voice profile status.
                                    preferencesManager.setVoiceProfileCompleted()
                                }
                            }
                        )
                    }
                }

                // Unified "Improve Voice Recognition Accuracy" Settings Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Text(
                                text = "Improve Voice Recognition Accuracy",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Calibrate speaker recognition with custom voice samples and adjust wake-up sensitivity threshold filters for your specific environment.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = { onOpenWakeUpActivation?.invoke() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.RecordVoiceOver,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                              )
                              Spacer(modifier = Modifier.width(8.dp))
                              Text("Open Voice Accuracy Dashboard", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                // Double-Tap Screen Shortcut to Launch Voice Recognition
                val doubleTapShortcutEnabled by preferencesManager.doubleTapShortcutEnabled.collectAsState()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Double-Tap Screen Shortcut", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Double-tap anywhere on the main screen background to trigger live voice recognition immediately",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    Switch(
                        checked = doubleTapShortcutEnabled,
                        onCheckedChange = { preferencesManager.setDoubleTapShortcutEnabled(it) },
                        modifier = Modifier.testTag("settings_double_tap_shortcut_switch")
                    )
                }

                // Wake-Word Battery Saver Mode
                val wakeWordBatterySaver by preferencesManager.wakeWordBatterySaver.collectAsState()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Wake-Word Battery Saver", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Lowers sampling rate during device inactivity to conserve power while staying responsive",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    Switch(
                        checked = wakeWordBatterySaver,
                        onCheckedChange = { preferencesManager.setWakeWordBatterySaver(it) },
                        modifier = Modifier.testTag("wake_word_battery_saver_switch")
                    )
                }

                // Ultra Low-Latency Voice Mode
                val ultraLowLatencyMode by preferencesManager.ultraLowLatencyMode.collectAsState()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Ultra Low-Latency Mode", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Optimizes speech recognition silence thresholds, audio chunking, and instant on-device stream synthesis for zero-lag conversations",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    Switch(
                        checked = ultraLowLatencyMode,
                        onCheckedChange = { preferencesManager.setUltraLowLatencyMode(it) },
                        modifier = Modifier.testTag("ultra_low_latency_switch")
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Dedicated Offline Voice Lounge Action Card
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            com.example.OfflineLiveConversationActivity.start(context)
                        }
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Offline Voice Lounge",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 2.dp)
                            ) {
                                Text(
                                    text = "100% LOCAL",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            "Launch real-time hands-free voice sessions powered entirely by native SpeechRecognizer and local text-to-speech engine.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Launch Offline Lounge",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Sound Effects
                val soundEffectsEnabled by preferencesManager.soundEffectsEnabled.collectAsState()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Interactive Sound Effects", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Harmonic acoustic chimes when initiating live voice calls, sending commands, and completing tasks",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    Switch(
                        checked = soundEffectsEnabled,
                        onCheckedChange = { preferencesManager.setSoundEffectsEnabled(it) },
                        modifier = Modifier.testTag("sound_effects_switch")
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Section 3: Device Control & Permissions
        SettingsSectionHeader(icon = Icons.Default.Security, title = "Device Control & Security")
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Confirmation Level
                var confExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = confExpanded,
                    onExpandedChange = { confExpanded = !confExpanded }
                ) {
                    OutlinedTextField(
                        value = when (confirmationLevel) {
                            "ALWAYS" -> "Always Confirm Every Action"
                            "IMPORTANT" -> "Confirm High-Risk Actions (Calls/SMS)"
                            else -> "Never Confirm (Auto-Execute)"
                        },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Action Confirmation Policy") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = confExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = confExpanded,
                        onDismissRequest = { confExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Confirm High-Risk Actions (Recommended)") },
                            onClick = {
                                preferencesManager.setConfirmationLevel("IMPORTANT")
                                confExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Always Confirm Every Action") },
                            onClick = {
                                preferencesManager.setConfirmationLevel("ALWAYS")
                                confExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Never Confirm (Auto-Execute)") },
                            onClick = {
                                preferencesManager.setConfirmationLevel("NEVER")
                                confExpanded = false
                            }
                        )
                    }
                }

                // Real-time Permission Capabilities Dashboard (Microphone, Accessibility, Notifications, Overlay)
                PermissionCapabilitiesDashboard(
                    modifier = Modifier.fillMaxWidth(),
                    onShowAccessibilityGuidance = { onShowAccessibilityGuidance?.invoke() }
                )

                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = { onOpenPermissionsCapabilities?.invoke() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Manage All Permissions & Capabilities")
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Section 4: Appearance & Theming
        SettingsSectionHeader(icon = Icons.Default.Palette, title = "Appearance")
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                var themeExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = themeExpanded,
                    onExpandedChange = { themeExpanded = !themeExpanded }
                ) {
                    OutlinedTextField(
                        value = when (themeMode) {
                            "DARK" -> "Dark Mode"
                            "LIGHT" -> "Light Mode"
                            else -> "System Default"
                        },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Theme") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = themeExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = themeExpanded,
                        onDismissRequest = { themeExpanded = false }
                    ) {
                        listOf("SYSTEM" to "System Default", "LIGHT" to "Light Mode", "DARK" to "Dark Mode").forEach { (key, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    preferencesManager.setThemeMode(key)
                                    themeExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("FPS Boost & Fluid Rendering", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Unlocks hardware-accelerated 120 FPS animations and 30ms ultra-low latency visual updates (battery-optimized 75ms standard mode when disabled).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Switch(
                        checked = fpsBoostEnabled,
                        onCheckedChange = { preferencesManager.setFpsBoostEnabled(it) },
                        modifier = Modifier.testTag("fps_boost_switch")
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Section 4.5: Backup & Restore (Secure Local Files)
        SettingsSectionHeader(icon = Icons.Default.Backup, title = "Secure Local Backup")
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Export or import your user preferences and local command execution history to/from secure internal application storage.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp)
                ) {
                    Text(
                        text = backupStatus,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            if (!backupInProgress) {
                                backupInProgress = true
                                scope.launch {
                                    PreferencesAndHistoryBackupManager.exportBackup(context)
                                    backupStatus = PreferencesAndHistoryBackupManager.getBackupFileInfo(context)
                                    backupInProgress = false
                                }
                            }
                        },
                        enabled = !backupInProgress,
                        modifier = Modifier.weight(1f).testTag("export_backup_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(imageVector = Icons.Default.Backup, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export Backup", style = MaterialTheme.typography.labelLarge)
                    }

                    OutlinedButton(
                        onClick = {
                            if (!backupInProgress) {
                                backupInProgress = true
                                scope.launch {
                                    val success = PreferencesAndHistoryBackupManager.importBackup(context)
                                    if (success) {
                                        preferencesManager.reloadAll()
                                    }
                                    backupStatus = PreferencesAndHistoryBackupManager.getBackupFileInfo(context)
                                    backupInProgress = false
                                }
                            }
                        },
                        enabled = !backupInProgress && PreferencesAndHistoryBackupManager.isBackupAvailable(context),
                        modifier = Modifier.weight(1f).testTag("import_backup_button")
                    ) {
                        Icon(imageVector = Icons.Default.SettingsBackupRestore, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Import Backup", style = MaterialTheme.typography.labelLarge)
                    }
                }

                if (backupInProgress) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Section 5: App Version & In-App Updates
        SettingsSectionHeader(icon = Icons.Default.Info, title = "About & System Requirements")
        com.example.ui.components.SystemRequirementsCard()
        Spacer(modifier = Modifier.height(10.dp))
        AppUpdateCard(
            updateManager = updateManagerInstance,
            initialUrl = updateCheckUrl,
            onUrlChanged = { preferencesManager.setUpdateCheckUrl(it) },
            onCheckForUpdates = onCheckForUpdates
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun SettingsSectionHeader(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 8.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun PermissionStatusRow(label: String, isGranted: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall)
        Text(
            text = if (isGranted) "Granted" else "Not Granted",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (isGranted) Color(0xFF10B981) else Color(0xFFEF4444)
        )
    }
}
