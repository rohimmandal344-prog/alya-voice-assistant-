package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.FilterList
import com.example.ui.components.CallAnalyticsCard
import com.example.ui.components.CallDateRangePickerSheet
import com.example.ui.components.DateRangeFilterState
import com.example.ui.components.DateRangePreset
import com.example.data.local.entity.CallSessionEntity
import com.example.data.local.entity.CallTranscriptEntryEntity
import com.example.domain.call.AiCallManager
import com.example.ui.viewmodel.AlyaViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallHistoryTab(
    viewModel: AlyaViewModel,
    modifier: Modifier = Modifier,
    onNavigateToChat: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val aiCallManager = viewModel.aiCallManager
    val liveCallState by aiCallManager.sessionState.collectAsState()
    val savedSessions by viewModel.callSessions.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedLanguageFilter by remember { mutableStateOf("ALL") }
    var dateRangeFilter by remember { mutableStateOf(DateRangeFilterState(preset = DateRangePreset.ALL_TIME)) }
    var showDateRangePickerSheet by remember { mutableStateOf(false) }
    var showSimulatorModal by remember { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    // First filter by Date Range for both Analytics and List
    val dateFilteredSessions = remember(savedSessions, dateRangeFilter) {
        savedSessions.filter { dateRangeFilter.matches(it.startTime) }
    }

    // Then filter by Search Query and Language
    val filteredSessions = remember(dateFilteredSessions, searchQuery, selectedLanguageFilter) {
        dateFilteredSessions.filter { session ->
            val matchesSearch = searchQuery.isBlank() ||
                    session.callerName.contains(searchQuery, ignoreCase = true) ||
                    session.callerNumber.contains(searchQuery, ignoreCase = true) ||
                    session.callSummary.contains(searchQuery, ignoreCase = true) ||
                    session.detectedLanguage.contains(searchQuery, ignoreCase = true) ||
                    session.disconnectReason.contains(searchQuery, ignoreCase = true)

            val matchesLang = when (selectedLanguageFilter) {
                "ALL" -> true
                "HINDI" -> session.detectedLanguage.contains("Hindi", ignoreCase = true) || session.detectedLocaleTag.startsWith("hi")
                "ENGLISH" -> session.detectedLanguage.contains("English", ignoreCase = true) || session.detectedLocaleTag.startsWith("en")
                "BENGALI" -> session.detectedLanguage.contains("Bengali", ignoreCase = true) || session.detectedLocaleTag.startsWith("bn")
                "SPANISH" -> session.detectedLanguage.contains("Spanish", ignoreCase = true) || session.detectedLocaleTag.startsWith("es")
                "OTHER" -> !session.detectedLocaleTag.startsWith("hi") && !session.detectedLocaleTag.startsWith("en") && !session.detectedLocaleTag.startsWith("bn") && !session.detectedLocaleTag.startsWith("es")
                else -> true
            }

            matchesSearch && matchesLang
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Header Controls & Search
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Title Row with Session Counter & Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.PhoneInTalk,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Call History & Transcripts",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Text(
                        text = if (dateRangeFilter.isFiltered) {
                            "${filteredSessions.size} calls (${dateRangeFilter.getDisplayLabel()})"
                        } else {
                            "${savedSessions.size} AI call logs recorded"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (dateRangeFilter.isFiltered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = { showSimulatorModal = true },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp).testTag("simulate_call_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SupportAgent,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Simulate Call", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    if (savedSessions.isNotEmpty()) {
                        IconButton(
                            onClick = { showClearConfirmDialog = true },
                            modifier = Modifier.size(34.dp).testTag("clear_all_calls_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Clear All Call Logs",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search by caller, summary, language, or notes...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.outline
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear Search",
                                tint = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("call_history_search_input"),
                shape = RoundedCornerShape(14.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Date Range Presets & Selector Filter Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Main Calendar Action Button to open Sheet
                Surface(
                    onClick = { showDateRangePickerSheet = true },
                    shape = RoundedCornerShape(10.dp),
                    color = if (dateRangeFilter.isFiltered) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    border = if (dateRangeFilter.isFiltered) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                    modifier = Modifier.testTag("date_range_picker_trigger_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = "Pick Date Range",
                            tint = if (dateRangeFilter.isFiltered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = if (dateRangeFilter.isFiltered) dateRangeFilter.getDisplayLabel() else "Date Range",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (dateRangeFilter.isFiltered) FontWeight.Bold else FontWeight.SemiBold,
                            color = if (dateRangeFilter.isFiltered) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                }

                // Quick Preset Filter Chips
                DateFilterChipItem("All Time", DateRangePreset.ALL_TIME, dateRangeFilter.preset) {
                    dateRangeFilter = DateRangeFilterState(preset = DateRangePreset.ALL_TIME)
                }
                DateFilterChipItem("Today", DateRangePreset.TODAY, dateRangeFilter.preset) {
                    dateRangeFilter = DateRangeFilterState(preset = DateRangePreset.TODAY)
                }
                DateFilterChipItem("Last 7 Days", DateRangePreset.LAST_7_DAYS, dateRangeFilter.preset) {
                    dateRangeFilter = DateRangeFilterState(preset = DateRangePreset.LAST_7_DAYS)
                }
                DateFilterChipItem("Last 30 Days", DateRangePreset.LAST_30_DAYS, dateRangeFilter.preset) {
                    dateRangeFilter = DateRangeFilterState(preset = DateRangePreset.LAST_30_DAYS)
                }
                DateFilterChipItem("This Month", DateRangePreset.THIS_MONTH, dateRangeFilter.preset) {
                    dateRangeFilter = DateRangeFilterState(preset = DateRangePreset.THIS_MONTH)
                }
                DateFilterChipItem("Last Month", DateRangePreset.LAST_MONTH, dateRangeFilter.preset) {
                    dateRangeFilter = DateRangeFilterState(preset = DateRangePreset.LAST_MONTH)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Language Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                LanguageFilterChipItem("All Languages", "ALL", selectedLanguageFilter) { selectedLanguageFilter = it }
                LanguageFilterChipItem("🇮🇳 Hindi", "HINDI", selectedLanguageFilter) { selectedLanguageFilter = it }
                LanguageFilterChipItem("🇬🇧 English", "ENGLISH", selectedLanguageFilter) { selectedLanguageFilter = it }
                LanguageFilterChipItem("🇧🇩 Bengali", "BENGALI", selectedLanguageFilter) { selectedLanguageFilter = it }
                LanguageFilterChipItem("🇪🇸 Spanish", "SPANISH", selectedLanguageFilter) { selectedLanguageFilter = it }
                LanguageFilterChipItem("🌍 Other", "OTHER", selectedLanguageFilter) { selectedLanguageFilter = it }
            }
        }

        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        // Main List Content
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Visual Aggregate Data & Bar Chart Card at the top of Call History
            item(key = "call_analytics_visual_card") {
                CallAnalyticsCard(
                    callSessions = dateFilteredSessions,
                    dateRangeLabel = dateRangeFilter.getDisplayLabel(),
                    isDateFiltered = dateRangeFilter.isFiltered,
                    onOpenDateRangePicker = { showDateRangePickerSheet = true },
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            // Active Call Banner if call in progress
            if (liveCallState.isCallActive) {
                item(key = "active_call_banner") {
                    ActiveLiveCallBanner(
                        state = liveCallState,
                        onEndCall = { aiCallManager.endCallSession(reason = "User manually ended live call") }
                    )
                }
            }

            if (filteredSessions.isEmpty()) {
                item(key = "empty_state") {
                    CallHistoryEmptyView(
                        searchQuery = searchQuery,
                        onSimulateTestCall = { showSimulatorModal = true }
                    )
                }
            } else {
                items(filteredSessions, key = { it.id }) { session ->
                    CallHistoryCard(
                        session = session,
                        viewModel = viewModel,
                        onCallBack = { number ->
                            viewModel.initiateCall(number)
                            Toast.makeText(context, "Dialing $number...", Toast.LENGTH_SHORT).show()
                        },
                        onDelete = {
                            viewModel.deleteCallSession(session.id)
                            Toast.makeText(context, "Call log removed", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }

    // Clear Confirmation Dialog
    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("Clear All Call History?") },
            text = { Text("This will permanently delete all logged AI call sessions, transcripts, and generated summaries.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllCallSessions()
                        showClearConfirmDialog = false
                        Toast.makeText(context, "Call history cleared", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Interactive Live Call Agent Simulator Modal
    if (showSimulatorModal) {
        CallSimulatorDialog(
            viewModel = viewModel,
            onDismiss = { showSimulatorModal = false }
        )
    }

    // Modal Date Range Picker Sheet
    if (showDateRangePickerSheet) {
        CallDateRangePickerSheet(
            currentState = dateRangeFilter,
            onSelectRange = { newFilter ->
                dateRangeFilter = newFilter
            },
            onDismiss = { showDateRangePickerSheet = false }
        )
    }
}

@Composable
private fun DateFilterChipItem(
    label: String,
    preset: DateRangePreset,
    selectedPreset: DateRangePreset,
    onSelect: () -> Unit
) {
    FilterChip(
        selected = selectedPreset == preset,
        onClick = onSelect,
        label = { Text(label, fontSize = 12.sp, fontWeight = if (selectedPreset == preset) FontWeight.Bold else FontWeight.Normal) },
        shape = RoundedCornerShape(10.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
}

@Composable
private fun LanguageFilterChipItem(
    label: String,
    tag: String,
    selectedTag: String,
    onSelect: (String) -> Unit
) {
    FilterChip(
        selected = selectedTag == tag,
        onClick = { onSelect(tag) },
        label = { Text(label, fontSize = 12.sp, fontWeight = if (selectedTag == tag) FontWeight.Bold else FontWeight.Normal) },
        shape = RoundedCornerShape(10.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
}

@Composable
fun CallHistoryCard(
    session: CallSessionEntity,
    viewModel: AlyaViewModel,
    onCallBack: (String) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isExpanded by remember { mutableStateOf(false) }
    var transcripts by remember { mutableStateOf<List<CallTranscriptEntryEntity>>(emptyList()) }
    var isLoadingTranscripts by remember { mutableStateOf(false) }

    // Load full transcript on expand
    LaunchedEffect(isExpanded) {
        if (isExpanded && transcripts.isEmpty()) {
            isLoadingTranscripts = true
            transcripts = viewModel.getTranscriptsForSession(session.id)
            isLoadingTranscripts = false
        }
    }

    val dateFormatter = remember { SimpleDateFormat("EEE, MMM d, yyyy • h:mm a", Locale.getDefault()) }
    val timeFormatted = remember(session.startTime) {
        if (session.startTime > 0) dateFormatter.format(Date(session.startTime)) else "Recent Call"
    }

    val durationText = remember(session.durationSeconds) {
        val mins = session.durationSeconds / 60
        val secs = session.durationSeconds % 60
        if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
    }

    val languageFlag = remember(session.detectedLocaleTag, session.detectedLanguage) {
        when {
            session.detectedLocaleTag.startsWith("hi") || session.detectedLanguage.contains("Hindi", true) -> "🇮🇳 Hindi"
            session.detectedLocaleTag.startsWith("bn") || session.detectedLanguage.contains("Bengali", true) -> "🇧🇩 Bengali"
            session.detectedLocaleTag.startsWith("es") || session.detectedLanguage.contains("Spanish", true) -> "🇪🇸 Spanish"
            session.detectedLocaleTag.startsWith("fr") || session.detectedLanguage.contains("French", true) -> "🇫🇷 French"
            session.detectedLocaleTag.startsWith("de") || session.detectedLanguage.contains("German", true) -> "🇩🇪 German"
            session.detectedLocaleTag.startsWith("ja") || session.detectedLanguage.contains("Japanese", true) -> "🇯🇵 Japanese"
            session.detectedLocaleTag.startsWith("ar") || session.detectedLanguage.contains("Arabic", true) -> "🇸🇦 Arabic"
            else -> "🇬🇧 English (${session.detectedLocaleTag})"
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .testTag("call_history_card_${session.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Avatar, Caller Details, Duration & Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(
                                Brush.linearGradient(
                                    listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)
                                ),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallReceived,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = session.callerName.ifBlank { "Unknown Caller" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = session.callerNumber.ifBlank { "+1-800-ALYA-AI" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "• $durationText",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Status & Timestamp Chip
                Column(horizontalAlignment = Alignment.End) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF2E7D32).copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, Color(0xFF2E7D32).copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = "AI Answered",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E7D32),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = timeFormatted,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Detected Language & Intent Badge Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = languageFlag,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                if (session.disconnectReason.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "End: ${session.disconnectReason}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // AI Call Summary Box
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.SupportAgent,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "AI Call Summary",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = session.callSummary.ifBlank { "Caller engaged in voice dialogue with Alya AI Assistant." },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 18.sp
                    )
                }
            }

            // Transcript Snippet Preview (When collapsed)
            if (!isExpanded) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    onClick = { isExpanded = true },
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.RecordVoiceOver,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "View Full Dialogue Transcript",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ExpandMore,
                            contentDescription = "Expand",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Expanded Full Turn-by-Turn Transcript
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                ) {
                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Full Verbatim Transcript (${transcripts.size} turns)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(
                            onClick = { isExpanded = false },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ExpandLess,
                                contentDescription = "Collapse",
                                tint = MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    if (isLoadingTranscripts) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Loading transcript turns...", style = MaterialTheme.typography.bodySmall)
                        }
                    } else if (transcripts.isEmpty()) {
                        Text(
                            text = "No turn-by-turn dialogue logged for this call.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            transcripts.forEach { entry ->
                                val isCaller = entry.speaker.equals("caller", ignoreCase = true)
                                val speakerTime = SimpleDateFormat("h:mm:ss a", Locale.getDefault()).format(Date(entry.timestamp))

                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(
                                        topStart = 12.dp,
                                        topEnd = 12.dp,
                                        bottomStart = if (isCaller) 2.dp else 12.dp,
                                        bottomEnd = if (isCaller) 12.dp else 2.dp
                                    ),
                                    color = if (isCaller) {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                    },
                                    border = BorderStroke(
                                        1.dp,
                                        if (isCaller) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = if (isCaller) Icons.Default.Phone else Icons.Default.RecordVoiceOver,
                                                    contentDescription = null,
                                                    tint = if (isCaller) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = if (isCaller) "Caller (${session.callerName.ifBlank { "User" }})" else "Alya AI",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isCaller) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            Text(
                                                text = "$speakerTime • ${entry.detectedLanguage}",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))

                                        Text(
                                            text = entry.text,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            lineHeight = 18.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons Row: Call Back, Copy All, Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (session.callerNumber.isNotBlank()) {
                        OutlinedButton(
                            onClick = { onCallBack(session.callerNumber) },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.CallMade, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Call Back", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            val fullText = buildString {
                                appendLine("--- AI CALL LOG ---")
                                appendLine("Caller: ${session.callerName} (${session.callerNumber})")
                                appendLine("Date: $timeFormatted")
                                appendLine("Duration: $durationText")
                                appendLine("Language: $languageFlag")
                                appendLine("Summary: ${session.callSummary}")
                                if (transcripts.isNotEmpty()) {
                                    appendLine("\n--- TRANSCRIPT ---")
                                    transcripts.forEach {
                                        val role = if (it.speaker == "caller") "Caller" else "Alya"
                                        appendLine("[$role]: ${it.text}")
                                    }
                                }
                            }
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("AI Call Transcript", fullText))
                            Toast.makeText(context, "Call summary and transcript copied to clipboard!", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy Log", fontSize = 11.sp)
                    }
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete call log",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ActiveLiveCallBanner(
    state: com.example.domain.call.AiCallSessionState,
    onEndCall: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(Color(0xFF2E7D32), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PhoneInTalk,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Live AI Call in Progress",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "${state.callerName} (${state.detectedLanguageName})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }

            Button(
                onClick = onEndCall,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.CallEnd, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Hang Up", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun CallHistoryEmptyView(
    searchQuery: String,
    onSimulateTestCall: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PhoneInTalk,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (searchQuery.isNotBlank()) "No Matching Calls Found" else "No AI Call Logs Yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = if (searchQuery.isNotBlank()) {
                    "Try refining your search keyword or clearing the language filter."
                } else {
                    "When incoming calls are received, Alya will autonomously converse in the caller's language, answer inquiries, and generate structured summaries and transcripts here."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(18.dp))

            Button(
                onClick = onSimulateTestCall,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.SupportAgent, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Simulate Live Inbound Call")
            }
        }
    }
}

@Composable
private fun CallSimulatorDialog(
    viewModel: AlyaViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val aiCallManager = viewModel.aiCallManager
    val liveCallState by aiCallManager.sessionState.collectAsState()

    var simulatedCallerName by remember { mutableStateOf("Rahul Sharma") }
    var simulatedCallerNumber by remember { mutableStateOf("+91 98765 43210") }
    var simulatedLanguage by remember { mutableStateOf("hi-IN") }
    var userMessageInput by remember { mutableStateOf("") }
    var isLiveActive by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.SupportAgent,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("AI Call Agent Simulator", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = "Test autonomous unscripted call answering and multilingual dialogue. The full session will be summarized and saved to Call History.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (!liveCallState.isCallActive) {
                    OutlinedTextField(
                        value = simulatedCallerName,
                        onValueChange = { simulatedCallerName = it },
                        label = { Text("Caller Name") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = simulatedCallerNumber,
                        onValueChange = { simulatedCallerNumber = it },
                        label = { Text("Phone Number") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text("Initial Language:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "hi-IN" to "🇮🇳 Hindi",
                            "en-US" to "🇬🇧 English",
                            "bn-IN" to "🇧🇩 Bengali",
                            "es-ES" to "🇪🇸 Spanish"
                        ).forEach { (code, label) ->
                            FilterChip(
                                selected = simulatedLanguage == code,
                                onClick = { simulatedLanguage = code },
                                label = { Text(label, fontSize = 11.sp) },
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            aiCallManager.startCallSession(
                                callerName = simulatedCallerName,
                                callerNumber = simulatedCallerNumber
                            )
                            isLiveActive = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.PhoneInTalk, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Connect Live Call")
                    }
                } else {
                    // Active call chat interface
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "Call with ${liveCallState.callerName} (${liveCallState.detectedLanguageName})",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            val lastAiSpeech = liveCallState.transcripts.lastOrNull { it.speaker == "assistant" }?.text ?: "Speaking initial greeting..."
                            Text(
                                text = "Alya: \"$lastAiSpeech\"",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = userMessageInput,
                        onValueChange = { userMessageInput = it },
                        placeholder = { Text("Caller speech (Hindi, English, etc.)...") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    if (userMessageInput.isNotBlank()) {
                                        aiCallManager.processCallerUtterance(userMessageInput)
                                        userMessageInput = ""
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Send, contentDescription = "Send", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Preset Quick Utterances
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(
                            "नमस्ते, क्या आप मेरी मदद कर सकते हैं?" to "Hindi Greeting",
                            "Hello, I need to leave a message for tomorrow" to "English Task",
                            "আমি একটু পরে ফোন করবো, ধন্যবাদ" to "Bengali End",
                            "Muchas gracias, hasta luego" to "Spanish Goodbye"
                        ).forEach { (text, label) ->
                            Surface(
                                onClick = {
                                    aiCallManager.processCallerUtterance(text)
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            aiCallManager.endCallSession(reason = "Caller completed dialogue")
                            Toast.makeText(context, "Call completed and saved to Call History!", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.CallEnd, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("End Call & Save Transcript")
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = {
                if (liveCallState.isCallActive) {
                    aiCallManager.endCallSession(reason = "Simulation closed")
                }
                onDismiss()
            }) {
                Text("Close")
            }
        }
    )
}
