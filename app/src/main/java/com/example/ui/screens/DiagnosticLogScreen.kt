package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.util.diagnostics.DiagnosticLogEntry
import com.example.util.diagnostics.DiagnosticLogManager
import com.example.util.diagnostics.DiagnosticStage

/**
 * Developer Diagnostic & Performance Inspector (Alya v2.3.0)
 *
 * Developer interface to monitor real-time execution performance, battery drain metrics,
 * network request latencies, and runtime glitches.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticLogScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val diagManager = DiagnosticLogManager.instance
    val logEntries by diagManager.logEntries.collectAsState()
    val systemSnapshot by diagManager.systemSnapshot.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedStageFilter by remember { mutableStateOf<DiagnosticStage?>(null) }
    var showOnlyErrors by remember { mutableStateOf(false) }

    val filteredEntries = remember(logEntries, selectedStageFilter, searchQuery, showOnlyErrors) {
        logEntries.filter { entry ->
            val matchesStage = selectedStageFilter == null || entry.stage == selectedStageFilter
            val matchesQuery = searchQuery.isBlank() ||
                    entry.command.contains(searchQuery, ignoreCase = true) ||
                    entry.details.contains(searchQuery, ignoreCase = true) ||
                    (entry.failureCode != null && entry.failureCode.contains(searchQuery, ignoreCase = true))
            val matchesErrors = !showOnlyErrors || entry.isSuccess == false || entry.failureCode != null

            matchesStage && matchesQuery && matchesErrors
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.BugReport,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Developer Inspector & Logs",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Performance • Battery • Network • Glitches",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            diagManager.logPerformance("Developer Manual Refresh", 14, 148)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Metrics",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { diagManager.clearLogs() }) {
                        Icon(
                            imageVector = Icons.Default.ClearAll,
                            contentDescription = "Clear Logs",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 14.dp)
        ) {
            // Real-time System Metrics Header
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // RAM Usage
                    MetricBox(
                        icon = Icons.Default.Memory,
                        label = "RAM Usage",
                        value = "${systemSnapshot.memoryUsageMb}MB",
                        statusColor = Color(0xFF10B981)
                    )

                    Divider(
                        modifier = Modifier
                            .height(28.dp)
                            .width(1.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    // Battery Health
                    MetricBox(
                        icon = Icons.Default.BatteryChargingFull,
                        label = "Battery",
                        value = "${systemSnapshot.batteryPercent}%",
                        statusColor = if (systemSnapshot.batteryPercent > 20) Color(0xFF3B82F6) else Color(0xFFEF4444)
                    )

                    Divider(
                        modifier = Modifier
                            .height(28.dp)
                            .width(1.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    // Network Latency
                    MetricBox(
                        icon = Icons.Default.Speed,
                        label = "Avg Latency",
                        value = "${systemSnapshot.avgLatencyMs}ms",
                        statusColor = Color(0xFF8B5CF6)
                    )
                }
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .testTag("diagnostic_search_input"),
                placeholder = { Text("Search logs, endpoints or glitches...", style = MaterialTheme.typography.bodySmall) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search", modifier = Modifier.size(18.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            // Stage Category Filter Chips
            val stageCounts = remember(logEntries) {
                logEntries.groupBy { it.stage }.mapValues { it.value.size }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = selectedStageFilter == null && !showOnlyErrors,
                    onClick = {
                        selectedStageFilter = null
                        showOnlyErrors = false
                    },
                    label = { Text("All (${logEntries.size})", fontSize = 12.sp) }
                )

                FilterChip(
                    selected = showOnlyErrors,
                    onClick = { showOnlyErrors = !showOnlyErrors },
                    label = { Text("Glitches/Errors", fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                )

                DiagnosticStage.values().forEach { stage ->
                    val count = stageCounts[stage] ?: 0
                    FilterChip(
                        selected = selectedStageFilter == stage && !showOnlyErrors,
                        onClick = {
                            selectedStageFilter = if (selectedStageFilter == stage) null else stage
                            showOnlyErrors = false
                        },
                        label = { Text("${stage.name} ($count)", fontSize = 12.sp) }
                    )
                }
            }

            // Log List
            if (filteredEntries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = Color(0xFF10B981).copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "No log events found for selected filters.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp, top = 4.dp)
                ) {
                    items(filteredEntries, key = { it.id }) { entry ->
                        DeveloperDiagnosticCard(entry = entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricBox(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    statusColor: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = statusColor,
            modifier = Modifier.size(18.dp)
        )
        Column {
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
fun DeveloperDiagnosticCard(entry: DiagnosticLogEntry) {
    val stageColor = when (entry.stage) {
        DiagnosticStage.PERFORMANCE -> Color(0xFF10B981)
        DiagnosticStage.BATTERY -> Color(0xFF3B82F6)
        DiagnosticStage.NETWORK -> Color(0xFF8B5CF6)
        DiagnosticStage.DETECTION -> Color(0xFF06B6D4)
        DiagnosticStage.RESOLUTION -> Color(0xFFEC4899)
        DiagnosticStage.EXECUTION -> Color(0xFFF59E0B)
        DiagnosticStage.RESULT -> if (entry.isSuccess == true) Color(0xFF10B981) else Color(0xFFEF4444)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (entry.isSuccess == false || entry.failureCode != null) Color(0xFFEF4444).copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Category Badge
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = stageColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = entry.stage.name,
                            color = stageColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    if (entry.latencyMs != null) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (entry.latencyMs < 100) Color(0xFF10B981).copy(alpha = 0.12f)
                            else if (entry.latencyMs < 300) Color(0xFFF59E0B).copy(alpha = 0.12f)
                            else Color(0xFFEF4444).copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = "${entry.latencyMs}ms",
                                color = if (entry.latencyMs < 100) Color(0xFF059669)
                                else if (entry.latencyMs < 300) Color(0xFFD97706)
                                else Color(0xFFDC2626),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Text(
                    text = entry.formattedTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (entry.command.isNotBlank()) {
                Text(
                    text = entry.command,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Text(
                text = entry.details,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (entry.failureCode != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFFEF4444).copy(alpha = 0.12f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            tint = Color(0xFFDC2626),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Error Code: ${entry.failureCode}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFDC2626),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
