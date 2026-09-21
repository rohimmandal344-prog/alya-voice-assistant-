package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Standard Presets for Call History Date Range Filtering.
 */
enum class DateRangePreset(val title: String, val shortLabel: String) {
    ALL_TIME("All Time", "All"),
    TODAY("Today", "Today"),
    YESTERDAY("Yesterday", "Yesterday"),
    LAST_7_DAYS("Last 7 Days (Last Week)", "Last 7 Days"),
    LAST_30_DAYS("Last 30 Days (Last Month)", "Last 30 Days"),
    THIS_MONTH("This Month", "This Month"),
    LAST_MONTH("Previous Month", "Last Month"),
    CUSTOM("Custom Range", "Custom")
}

/**
 * Date Range State Model.
 */
data class DateRangeFilterState(
    val preset: DateRangePreset = DateRangePreset.ALL_TIME,
    val startMillis: Long? = null,
    val endMillis: Long? = null,
    val customDisplayLabel: String = ""
) {
    val isFiltered: Boolean get() = preset != DateRangePreset.ALL_TIME

    fun getDisplayLabel(): String {
        return when (preset) {
            DateRangePreset.ALL_TIME -> "All Time"
            DateRangePreset.TODAY -> "Today"
            DateRangePreset.YESTERDAY -> "Yesterday"
            DateRangePreset.LAST_7_DAYS -> "Last 7 Days"
            DateRangePreset.LAST_30_DAYS -> "Last 30 Days"
            DateRangePreset.THIS_MONTH -> "This Month"
            DateRangePreset.LAST_MONTH -> "Last Month"
            DateRangePreset.CUSTOM -> {
                if (startMillis != null && endMillis != null) {
                    val df = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                    "${df.format(Date(startMillis))} – ${df.format(Date(endMillis))}"
                } else if (customDisplayLabel.isNotBlank()) {
                    customDisplayLabel
                } else {
                    "Custom Range"
                }
            }
        }
    }

    /**
     * Checks if a given timestamp falls within this date range filter.
     */
    fun matches(timestamp: Long): Boolean {
        if (preset == DateRangePreset.ALL_TIME) return true

        val now = Calendar.getInstance()
        val sessionCal = Calendar.getInstance().apply { timeInMillis = timestamp }

        return when (preset) {
            DateRangePreset.TODAY -> {
                sessionCal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                        sessionCal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
            }
            DateRangePreset.YESTERDAY -> {
                val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
                sessionCal.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) &&
                        sessionCal.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR)
            }
            DateRangePreset.LAST_7_DAYS -> {
                val sevenDaysAgo = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, -7)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                }.timeInMillis
                timestamp >= sevenDaysAgo
            }
            DateRangePreset.LAST_30_DAYS -> {
                val thirtyDaysAgo = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, -30)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                }.timeInMillis
                timestamp >= thirtyDaysAgo
            }
            DateRangePreset.THIS_MONTH -> {
                sessionCal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                        sessionCal.get(Calendar.MONTH) == now.get(Calendar.MONTH)
            }
            DateRangePreset.LAST_MONTH -> {
                val lastMonthCal = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }
                sessionCal.get(Calendar.YEAR) == lastMonthCal.get(Calendar.YEAR) &&
                        sessionCal.get(Calendar.MONTH) == lastMonthCal.get(Calendar.MONTH)
            }
            DateRangePreset.CUSTOM -> {
                val start = startMillis ?: 0L
                val end = endMillis ?: Long.MAX_VALUE
                timestamp in start..end
            }
            DateRangePreset.ALL_TIME -> true
        }
    }
}

/**
 * Modal BottomSheet allowing the user to select predefined date range presets
 * (e.g. Last Week, Last Month, Today) or a custom calendar DateRangePicker.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallDateRangePickerSheet(
    currentState: DateRangeFilterState,
    onSelectRange: (DateRangeFilterState) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedPreset by remember { mutableStateOf(currentState.preset) }
    var isCustomCalendarOpen by remember { mutableStateOf(false) }

    val dateRangePickerState = rememberDateRangePickerState(
        initialSelectedStartDateMillis = currentState.startMillis ?: (System.currentTimeMillis() - 7 * 86400000L),
        initialSelectedEndDateMillis = currentState.endMillis ?: System.currentTimeMillis()
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Sheet Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
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
                            imageVector = Icons.Default.DateRange,
                            contentDescription = "Date Range",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Filter Call History by Date",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Select period for analytics & past transcripts",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Clear, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Presets Grid / List
            Text(
                text = "TIME PERIOD PRESETS",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.outline
            )

            Spacer(modifier = Modifier.height(8.dp))

            val presets = listOf(
                DateRangePreset.ALL_TIME to "Complete call archive across all time",
                DateRangePreset.TODAY to "Calls recorded since midnight today",
                DateRangePreset.YESTERDAY to "Calls recorded during yesterday",
                DateRangePreset.LAST_7_DAYS to "Last 7 days (Past week volume & transcripts)",
                DateRangePreset.LAST_30_DAYS to "Last 30 days (Past month aggregate analytics)",
                DateRangePreset.THIS_MONTH to "Calls recorded during the current calendar month",
                DateRangePreset.LAST_MONTH to "Calls recorded during the previous calendar month"
            )

            presets.forEach { (preset, desc) ->
                DatePresetCard(
                    title = preset.title,
                    description = desc,
                    isSelected = selectedPreset == preset && !isCustomCalendarOpen,
                    onClick = {
                        selectedPreset = preset
                        isCustomCalendarOpen = false
                        onSelectRange(DateRangeFilterState(preset = preset))
                        onDismiss()
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Custom Range Option
            DatePresetCard(
                title = "📅 Custom Date Range...",
                description = "Choose specific start and end calendar dates",
                isSelected = isCustomCalendarOpen || selectedPreset == DateRangePreset.CUSTOM,
                onClick = {
                    selectedPreset = DateRangePreset.CUSTOM
                    isCustomCalendarOpen = true
                }
            )

            // Custom DateRangePicker View when expanded
            AnimatedVisibility(visible = isCustomCalendarOpen) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            RoundedCornerShape(16.dp)
                        )
                        .padding(12.dp)
                ) {
                    Text(
                        text = "SELECT START & END DATES",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // DateRangePicker composable from Material3
                    DateRangePicker(
                        state = dateRangePickerState,
                        title = null,
                        headline = null,
                        showModeToggle = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(340.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    val start = dateRangePickerState.selectedStartDateMillis
                    val end = dateRangePickerState.selectedEndDateMillis

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (start != null && end != null) {
                            val df = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                            Text(
                                text = "${df.format(Date(start))} – ${df.format(Date(end))}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Text(
                                text = "Tap start & end dates above",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }

                        Button(
                            onClick = {
                                if (start != null && end != null) {
                                    // Set end of day timestamp for end date
                                    val endCal = Calendar.getInstance().apply {
                                        timeInMillis = end
                                        set(Calendar.HOUR_OF_DAY, 23)
                                        set(Calendar.MINUTE, 59)
                                        set(Calendar.SECOND, 59)
                                    }
                                    onSelectRange(
                                        DateRangeFilterState(
                                            preset = DateRangePreset.CUSTOM,
                                            startMillis = start,
                                            endMillis = endCal.timeInMillis
                                        )
                                    )
                                    onDismiss()
                                }
                            },
                            enabled = start != null && end != null,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Apply Range")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun DatePresetCard(
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("date_preset_${title.take(8).replace(" ", "_").lowercase()}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}
