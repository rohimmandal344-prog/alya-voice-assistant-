package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.CallSessionEntity
import com.example.ui.components.DateRangePreset
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Data item representing a single bar in the Recharts-styled analytics chart.
 */
data class ChartBarData(
    val label: String,
    val value: Int,
    val secondaryValue: Int = 0,
    val color: Color = Color(0xFF6750A4),
    val tooltipInfo: String = ""
)

/**
 * Data item representing a single date point on the 30-day frequency line chart.
 */
data class CallFrequencyPoint(
    val dayIndex: Int, // 0 to 29
    val label: String, // e.g. "Sep 14"
    val dayOfMonth: String, // e.g. "14"
    val count: Int,
    val dateMillis: Long,
    val fullDateLabel: String
)

enum class AnalyticsChartMode {
    TREND_30_DAYS,
    DAILY_VOLUME,
    LANGUAGES,
    DURATION
}

/**
 * Visual Top Card for Call History displaying aggregate KPIs:
 * - Total Calls in Selected Period / Today
 * - Most Used Language
 * - Average Conversation Duration
 *
 * Along with an interactive Recharts-styled Bar Chart (Daily Volume, Language Distribution, Duration Buckets).
 */
@Composable
fun CallAnalyticsCard(
    callSessions: List<CallSessionEntity>,
    dateRangeLabel: String = "All Time",
    isDateFiltered: Boolean = false,
    onOpenDateRangePicker: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedChartMode by remember { mutableStateOf(AnalyticsChartMode.TREND_30_DAYS) }
    var selectedBarIndex by remember { mutableStateOf<Int?>(null) }
    var selectedLinePointIndex by remember { mutableStateOf<Int?>(null) }

    // 1. Calculate Aggregate KPI: Total Calls in Period / Today
    val todayCallsCount = remember(callSessions) {
        val todayCal = Calendar.getInstance()
        val todayYear = todayCal.get(Calendar.YEAR)
        val todayDayOfYear = todayCal.get(Calendar.DAY_OF_YEAR)

        callSessions.count { session ->
            val cal = Calendar.getInstance().apply { timeInMillis = session.startTime }
            cal.get(Calendar.YEAR) == todayYear && cal.get(Calendar.DAY_OF_YEAR) == todayDayOfYear
        }
    }

    // 2. Calculate Aggregate KPI: Most Used Language
    val mostUsedLanguage = remember(callSessions) {
        if (callSessions.isEmpty()) {
            "None"
        } else {
            val languageCounts = callSessions.groupBy { session ->
                session.detectedLanguage.ifBlank { "English" }
            }.mapValues { it.value.size }

            val topLang = languageCounts.maxByOrNull { it.value }
            if (topLang != null) {
                val percentage = (topLang.value.toFloat() / callSessions.size * 100).toInt()
                "${topLang.key} ($percentage%)"
            } else {
                "Hindi (HI)"
            }
        }
    }

    // 3. Calculate Aggregate KPI: Average Conversation Duration
    val averageDurationFormatted = remember(callSessions) {
        if (callSessions.isEmpty()) {
            "0s"
        } else {
            val validDurations = callSessions.map { it.durationSeconds }.filter { it > 0 }
            if (validDurations.isEmpty()) {
                "0s"
            } else {
                val avgSec = validDurations.average().toLong()
                val minutes = avgSec / 60
                val seconds = avgSec % 60
                if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s"
            }
        }
    }

    // Prepare 30-Day Incoming Call Frequency Line Chart Data
    val monthlyTrendPoints = remember(callSessions) {
        val points = mutableListOf<CallFrequencyPoint>()
        val dayLabelFormat = SimpleDateFormat("MMM d", Locale.getDefault())
        val dayOfMonthFormat = SimpleDateFormat("d", Locale.getDefault())
        val fullDateFormat = SimpleDateFormat("EEEE, MMM d, yyyy", Locale.getDefault())

        val sessionsByDay = callSessions.groupBy { session ->
            val cal = Calendar.getInstance().apply { timeInMillis = session.startTime }
            cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR)
        }

        for (i in 29 downTo 0) {
            val cal = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -i)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val yearDayKey = cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR)
            val sessionCount = sessionsByDay[yearDayKey]?.size ?: 0
            val isToday = i == 0
            val label = if (isToday) "Today" else dayLabelFormat.format(cal.time)

            points.add(
                CallFrequencyPoint(
                    dayIndex = 29 - i,
                    label = label,
                    dayOfMonth = dayOfMonthFormat.format(cal.time),
                    count = sessionCount,
                    dateMillis = cal.timeInMillis,
                    fullDateLabel = fullDateFormat.format(cal.time)
                )
            )
        }
        points
    }

    // Prepare Daily 7-day volume data
    val dailyVolumeBars = remember(callSessions) {
        val days = mutableListOf<ChartBarData>()
        val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())
        val sessionsByDay = callSessions.groupBy { session ->
            val cal = Calendar.getInstance().apply { timeInMillis = session.startTime }
            cal.get(Calendar.DAY_OF_YEAR)
        }

        val primaryColor = Color(0xFF6750A4)

        for (i in 6 downTo 0) {
            val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -i) }
            val julianDay = cal.get(Calendar.DAY_OF_YEAR)
            val label = if (i == 0) "Today" else dayFormat.format(cal.time)

            val sessionCount = sessionsByDay[julianDay]?.size ?: 0
            val displayCount = sessionCount

            days.add(
                ChartBarData(
                    label = label,
                    value = displayCount,
                    color = primaryColor,
                    tooltipInfo = "$displayCount calls on $label"
                )
            )
        }
        days
    }

    // Prepare Language distribution bars
    val languageBars = remember(callSessions) {
        val palette = listOf(
            Color(0xFF6750A4), // Hindi / Primary
            Color(0xFF006C50), // English / Teal
            Color(0xFFB3261E), // Bengali / Amber-Red
            Color(0xFF7D5260), // Spanish / Rose
            Color(0xFF00658E)  // Other / Blue
        )

        if (callSessions.isEmpty()) {
            listOf(
                ChartBarData("None", 0, color = palette[0], tooltipInfo = "No calls in this date range")
            )
        } else {
            val grouped = callSessions.groupBy { it.detectedLanguage.ifBlank { "Other" } }
            grouped.entries.mapIndexed { index, entry ->
                val color = palette[index % palette.size]
                val pct = (entry.value.size.toFloat() / callSessions.size * 100).toInt()
                ChartBarData(
                    label = entry.key.take(7),
                    value = entry.value.size,
                    color = color,
                    tooltipInfo = "${entry.value.size} calls ($pct%) in ${entry.key}"
                )
            }.sortedByDescending { it.value }.take(5)
        }
    }

    // Prepare Duration distribution bars
    val durationBars = remember(callSessions) {
        val color1 = Color(0xFF006874)
        val color2 = Color(0xFF6750A4)
        val color3 = Color(0xFF984061)
        val color4 = Color(0xFF7C5800)

        if (callSessions.isEmpty()) {
            listOf(
                ChartBarData("<30s", 0, color = color1, tooltipInfo = "0 calls (<30s)"),
                ChartBarData("30s-1m", 0, color = color2, tooltipInfo = "0 calls (30s-1m)"),
                ChartBarData("1m-3m", 0, color = color3, tooltipInfo = "0 calls (1m-3m)"),
                ChartBarData(">3m", 0, color = color4, tooltipInfo = "0 calls (>3m)")
            )
        } else {
            val shortCount = callSessions.count { it.durationSeconds < 30 }
            val midCount = callSessions.count { it.durationSeconds in 30..60 }
            val longCount = callSessions.count { it.durationSeconds in 61..180 }
            val extendedCount = callSessions.count { it.durationSeconds > 180 }

            listOf(
                ChartBarData("<30s", shortCount, color = color1, tooltipInfo = "$shortCount calls (<30s)"),
                ChartBarData("30s-1m", midCount, color = color2, tooltipInfo = "$midCount calls (30s-1m)"),
                ChartBarData("1m-3m", longCount, color = color3, tooltipInfo = "$longCount calls (1m-3m)"),
                ChartBarData(">3m", extendedCount, color = color4, tooltipInfo = "$extendedCount calls (>3m)")
            )
        }
    }

    val activeBars = when (selectedChartMode) {
        AnalyticsChartMode.DAILY_VOLUME -> dailyVolumeBars
        AnalyticsChartMode.LANGUAGES -> languageBars
        AnalyticsChartMode.DURATION -> durationBars
        AnalyticsChartMode.TREND_30_DAYS -> emptyList()
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("call_analytics_visual_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Row with Date Range Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.BarChart,
                            contentDescription = "Analytics Chart",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "AI Call Analytics",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Volume & language breakdown ($dateRangeLabel)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Date Range Selector Button / Badge
                Surface(
                    onClick = onOpenDateRangePicker,
                    shape = RoundedCornerShape(10.dp),
                    color = if (isDateFiltered) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    border = if (isDateFiltered) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                    modifier = Modifier.testTag("open_date_picker_from_analytics")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = "Date Filter",
                            tint = if (isDateFiltered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = dateRangeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 11.sp,
                            fontWeight = if (isDateFiltered) FontWeight.Bold else FontWeight.Medium,
                            color = if (isDateFiltered) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Three Main Aggregate KPI Metric Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Metric 1: Total Calls in Period
                val callsKpiLabel = if (dateRangeLabel == "Today") "Calls Today" else "Total Calls"
                val callsKpiSubtext = if (dateRangeLabel == "All Time") "All recorded" else dateRangeLabel
                CallKpiPill(
                    icon = Icons.Default.Today,
                    label = callsKpiLabel,
                    value = callSessions.size.toString(),
                    subtext = callsKpiSubtext,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )

                // Metric 2: Most Used Language
                CallKpiPill(
                    icon = Icons.Default.Language,
                    label = "Top Language",
                    value = mostUsedLanguage,
                    subtext = "In this range",
                    color = Color(0xFF006C50), // Teal
                    modifier = Modifier.weight(1.15f)
                )

                // Metric 3: Average Conversation Duration
                CallKpiPill(
                    icon = Icons.Default.Schedule,
                    label = "Avg Duration",
                    value = averageDurationFormatted,
                    subtext = "Per session",
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(12.dp))

            // Chart Mode Selector Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when (selectedChartMode) {
                        AnalyticsChartMode.TREND_30_DAYS -> "Incoming Call Frequency (Last 30 Days)"
                        AnalyticsChartMode.DAILY_VOLUME -> "Daily Call Volume (Last 7 Days)"
                        AnalyticsChartMode.LANGUAGES -> "Detected Language Breakdown"
                        AnalyticsChartMode.DURATION -> "Call Duration Breakdown"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = selectedChartMode == AnalyticsChartMode.TREND_30_DAYS,
                        onClick = {
                            selectedChartMode = AnalyticsChartMode.TREND_30_DAYS
                            selectedLinePointIndex = null
                        },
                        label = { Text("30D Trend", fontSize = 11.sp, fontWeight = if (selectedChartMode == AnalyticsChartMode.TREND_30_DAYS) FontWeight.Bold else FontWeight.Normal) },
                        modifier = Modifier.height(28.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                    FilterChip(
                        selected = selectedChartMode == AnalyticsChartMode.DAILY_VOLUME,
                        onClick = {
                            selectedChartMode = AnalyticsChartMode.DAILY_VOLUME
                            selectedBarIndex = null
                        },
                        label = { Text("7D Daily", fontSize = 11.sp) },
                        modifier = Modifier.height(28.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                    FilterChip(
                        selected = selectedChartMode == AnalyticsChartMode.LANGUAGES,
                        onClick = {
                            selectedChartMode = AnalyticsChartMode.LANGUAGES
                            selectedBarIndex = null
                        },
                        label = { Text("Lang", fontSize = 11.sp) },
                        modifier = Modifier.height(28.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                    FilterChip(
                        selected = selectedChartMode == AnalyticsChartMode.DURATION,
                        onClick = {
                            selectedChartMode = AnalyticsChartMode.DURATION
                            selectedBarIndex = null
                        },
                        label = { Text("Duration", fontSize = 11.sp) },
                        modifier = Modifier.height(28.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Chart Canvas Rendering (30-Day Line Chart vs Bar Charts)
            if (selectedChartMode == AnalyticsChartMode.TREND_30_DAYS) {
                RechartsInteractiveLineChart(
                    points = monthlyTrendPoints,
                    selectedIndex = selectedLinePointIndex,
                    onSelectPoint = { index -> selectedLinePointIndex = index },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                )

                // Selected Line Point Tooltip Info Display
                selectedLinePointIndex?.let { index ->
                    if (index in monthlyTrendPoints.indices) {
                        val pt = monthlyTrendPoints[index]
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 3.dp,
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                                    )
                                    Column {
                                        Text(
                                            text = pt.fullDateLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = if (pt.count == 0) "No recorded calls" else "${pt.count} incoming call${if (pt.count > 1) "s" else ""} logged",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontSize = 11.sp,
                                            color = if (pt.count > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (pt.count > 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = "${pt.count} calls",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (pt.count > 0) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // Recharts-styled Interactive Bar Chart
                RechartsInteractiveBarChart(
                    bars = activeBars,
                    selectedBarIndex = selectedBarIndex,
                    onSelectBar = { index -> selectedBarIndex = index },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                )

                // Selected Bar Tooltip info display
                selectedBarIndex?.let { index ->
                    if (index in activeBars.indices) {
                        val bar = activeBars[index]
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 2.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(bar.color, CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = bar.tooltipInfo,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Text(
                                    text = "Count: ${bar.value}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = bar.color
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Metric Pill for high-impact visual representation of key statistics.
 */
@Composable
private fun CallKpiPill(
    icon: ImageVector,
    label: String,
    value: String,
    subtext: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtext,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1
            )
        }
    }
}

/**
 * Canvas-rendered Recharts-style Bar Chart with Cartesian gridlines,
 * rounded bar caps, interactive tap indicators, and bottom labels.
 */
@Composable
fun RechartsInteractiveBarChart(
    bars: List<ChartBarData>,
    selectedBarIndex: Int?,
    onSelectBar: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    if (bars.isEmpty()) return

    val animatedProgress = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(bars) {
        animatedProgress.snapTo(0f)
        animatedProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 600)
        )
    }

    val outlineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    val textPaintColor = MaterialTheme.colorScheme.onSurfaceVariant.hashCode()
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(bars) {
                    detectTapGestures { offset ->
                        val slotWidth = size.width / bars.size
                        val clickedIndex = (offset.x / slotWidth).toInt().coerceIn(0, bars.lastIndex)
                        if (selectedBarIndex == clickedIndex) {
                            onSelectBar(null)
                        } else {
                            onSelectBar(clickedIndex)
                        }
                    }
                }
        ) {
            val canvasWidth = size.width
            val bottomLabelHeight = 22.dp.toPx()
            val canvasHeight = size.height - bottomLabelHeight
            val maxValue = bars.maxOfOrNull { it.value }?.coerceAtLeast(4) ?: 4

            // 1. Draw Cartesian Grid Lines (Recharts style)
            val numGridLines = 3
            for (i in 0..numGridLines) {
                val y = canvasHeight * (1f - i.toFloat() / numGridLines)
                drawLine(
                    color = outlineColor,
                    start = Offset(0f, y),
                    end = Offset(canvasWidth, y),
                    strokeWidth = 1.dp.toPx()
                )
            }

            // 2. Draw Bars
            val slotWidth = canvasWidth / bars.size
            val maxBarWidth = 28.dp.toPx()
            val barWidth = (slotWidth * 0.45f).coerceAtMost(maxBarWidth)

            bars.forEachIndexed { index, bar ->
                val centerX = slotWidth * index + slotWidth / 2f
                val isSelected = selectedBarIndex == index

                // Bar height calculated with animation
                val targetHeight = (bar.value.toFloat() / maxValue * (canvasHeight - 12.dp.toPx())).coerceAtLeast(6.dp.toPx())
                val barHeight = targetHeight * animatedProgress.value
                val barLeft = centerX - barWidth / 2f
                val barTop = canvasHeight - barHeight

                val barColor = if (isSelected) {
                    bar.color
                } else if (selectedBarIndex != null) {
                    bar.color.copy(alpha = 0.35f)
                } else {
                    bar.color
                }

                // Draw Bar with subtle gradient & rounded top
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(barColor, barColor.copy(alpha = 0.75f)),
                        startY = barTop,
                        endY = canvasHeight
                    ),
                    topLeft = Offset(barLeft, barTop),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                )

                // If selected, draw accent border
                if (isSelected) {
                    drawRoundRect(
                        color = Color.White,
                        topLeft = Offset(barLeft - 1.dp.toPx(), barTop - 1.dp.toPx()),
                        size = Size(barWidth + 2.dp.toPx(), barHeight + 2.dp.toPx()),
                        cornerRadius = CornerRadius(7.dp.toPx(), 7.dp.toPx()),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
                    )
                }

                // Value above the bar
                drawContext.canvas.nativeCanvas.apply {
                    val paint = android.graphics.Paint().apply {
                        color = textPaintColor
                        textSize = 10.sp.toPx()
                        textAlign = android.graphics.Paint.Align.CENTER
                        isAntiAlias = true
                        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                    }
                    if (bar.value > 0) {
                        drawText(
                            bar.value.toString(),
                            centerX,
                            (barTop - 4.dp.toPx()).coerceAtLeast(10.dp.toPx()),
                            paint
                        )
                    }

                    // Label at the bottom (X-Axis)
                    val labelPaint = android.graphics.Paint().apply {
                        color = textPaintColor
                        textSize = 10.sp.toPx()
                        textAlign = android.graphics.Paint.Align.CENTER
                        isAntiAlias = true
                    }
                    drawText(
                        bar.label,
                        centerX,
                        size.height - 4.dp.toPx(),
                        labelPaint
                    )
                }
            }
        }
    }
}

/**
 * Canvas-rendered Interactive Smooth Line & Area Chart for 30-Day Call Frequency.
 * Features:
 * - Smooth cubic Bézier spline interpolation
 * - Shaded area gradient under curve
 * - Cartesian gridlines with frequency levels
 * - Interactive scrubbing / touch tracking with vertical dashed guide line
 * - X-Axis date timestamps
 * - Highlighted data points & animated entrance
 */
@Composable
fun RechartsInteractiveLineChart(
    points: List<CallFrequencyPoint>,
    selectedIndex: Int?,
    onSelectPoint: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    if (points.isEmpty()) return

    val animatedProgress = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(points) {
        animatedProgress.snapTo(0f)
        animatedProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 750)
        )
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val outlineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    val textPaintColor = MaterialTheme.colorScheme.onSurfaceVariant.hashCode()
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

    val cachedLinePath = remember { Path() }
    val cachedAreaPath = remember { Path() }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag("incoming_calls_30_day_line_chart")
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(points) {
                    detectTapGestures { offset ->
                        val paddingX = 12.dp.toPx()
                        val usableWidth = (size.width - paddingX * 2).coerceAtLeast(1f)
                        val touchX = (offset.x - paddingX).coerceIn(0f, usableWidth)
                        val nearestIndex = ((touchX / usableWidth) * (points.size - 1)).toInt().coerceIn(0, points.lastIndex)
                        if (selectedIndex == nearestIndex) {
                            onSelectPoint(null)
                        } else {
                            onSelectPoint(nearestIndex)
                        }
                    }
                }
                .pointerInput(points) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val paddingX = 12.dp.toPx()
                            val usableWidth = (size.width - paddingX * 2).coerceAtLeast(1f)
                            val touchX = (offset.x - paddingX).coerceIn(0f, usableWidth)
                            val nearestIndex = Math.round((touchX / usableWidth) * (points.size - 1)).coerceIn(0, points.lastIndex)
                            onSelectPoint(nearestIndex)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val paddingX = 12.dp.toPx()
                            val usableWidth = (size.width - paddingX * 2).coerceAtLeast(1f)
                            val touchX = (change.position.x - paddingX).coerceIn(0f, usableWidth)
                            val nearestIndex = Math.round((touchX / usableWidth) * (points.size - 1)).coerceIn(0, points.lastIndex)
                            onSelectPoint(nearestIndex)
                        }
                    )
                }
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val bottomLabelHeight = 22.dp.toPx()
            val topPadding = 18.dp.toPx()
            val sidePadding = 14.dp.toPx()
            val chartWidth = canvasWidth - (sidePadding * 2)
            val chartHeight = canvasHeight - bottomLabelHeight - topPadding
            val baselineY = topPadding + chartHeight

            val maxCount = points.maxOfOrNull { it.count }?.coerceAtLeast(3) ?: 3
            val stepX = if (points.size > 1) chartWidth / (points.size - 1) else chartWidth

            // 1. Draw Cartesian Horizontal Grid Lines & Y-Axis level indicators
            val numGridLines = 3
            for (i in 0..numGridLines) {
                val y = topPadding + chartHeight * (1f - i.toFloat() / numGridLines)
                drawLine(
                    color = outlineColor,
                    start = Offset(sidePadding, y),
                    end = Offset(canvasWidth - sidePadding, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()), 0f)
                )

                // Draw Y-Axis value label
                val levelValue = (maxCount * (i.toFloat() / numGridLines)).toInt()
                drawContext.canvas.nativeCanvas.apply {
                    val yLabelPaint = android.graphics.Paint().apply {
                        color = textPaintColor
                        textSize = 8.5.sp.toPx()
                        textAlign = android.graphics.Paint.Align.LEFT
                        isAntiAlias = true
                    }
                    drawText(
                        levelValue.toString(),
                        2.dp.toPx(),
                        y + 3.dp.toPx(),
                        yLabelPaint
                    )
                }
            }

            // Calculate screen coordinates for all 30 points
            val pointCoords = points.mapIndexed { index, pt ->
                val x = sidePadding + (index * stepX)
                val targetFraction = pt.count.toFloat() / maxCount
                val animatedFraction = targetFraction * animatedProgress.value
                val y = baselineY - (animatedFraction * chartHeight)
                Offset(x, y)
            }

            if (pointCoords.size >= 2) {
                // 2. Build Smooth Cubic Bézier Path
                cachedLinePath.reset()
                cachedLinePath.moveTo(pointCoords[0].x, pointCoords[0].y)
                for (i in 1 until pointCoords.size) {
                    val prev = pointCoords[i - 1]
                    val curr = pointCoords[i]
                    val controlDx = (curr.x - prev.x) * 0.45f
                    cachedLinePath.cubicTo(
                        prev.x + controlDx, prev.y,
                        curr.x - controlDx, curr.y,
                        curr.x, curr.y
                    )
                }

                // 3. Draw Gradient Filled Area under the curve
                cachedAreaPath.reset()
                cachedAreaPath.addPath(cachedLinePath)
                cachedAreaPath.lineTo(pointCoords.last().x, baselineY)
                cachedAreaPath.lineTo(pointCoords.first().x, baselineY)
                cachedAreaPath.close()

                drawPath(
                    path = cachedAreaPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = 0.38f),
                            primaryColor.copy(alpha = 0.02f)
                        ),
                        startY = topPadding,
                        endY = baselineY
                    )
                )

                // 4. Draw Main Line Stroke
                drawPath(
                    path = cachedLinePath,
                    color = primaryColor,
                    style = Stroke(
                        width = 2.5.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )

                // 5. Draw Baseline Axis Line
                drawLine(
                    color = outlineColor,
                    start = Offset(sidePadding, baselineY),
                    end = Offset(canvasWidth - sidePadding, baselineY),
                    strokeWidth = 1.2.dp.toPx()
                )

                // 6. Draw Small Point Dots for days with calls
                points.forEachIndexed { index, pt ->
                    val coord = pointCoords[index]
                    val isSelected = selectedIndex == index
                    if (pt.count > 0 && !isSelected) {
                        drawCircle(
                            color = primaryColor,
                            radius = 2.5.dp.toPx(),
                            center = coord
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 1.2.dp.toPx(),
                            center = coord
                        )
                    }
                }

                // 7. Draw Selected Point Indicator & Vertical Scrubbing Line
                selectedIndex?.let { index ->
                    if (index in pointCoords.indices) {
                        val coord = pointCoords[index]
                        val pt = points[index]

                        // Vertical dashed guide line
                        drawLine(
                            color = primaryColor.copy(alpha = 0.6f),
                            start = Offset(coord.x, topPadding),
                            end = Offset(coord.x, baselineY),
                            strokeWidth = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()), 0f)
                        )

                        // Outer pulsing halo
                        drawCircle(
                            color = primaryColor.copy(alpha = 0.22f),
                            radius = 11.dp.toPx(),
                            center = coord
                        )

                        // Middle solid circle
                        drawCircle(
                            color = primaryColor,
                            radius = 5.5.dp.toPx(),
                            center = coord
                        )

                        // Inner white core
                        drawCircle(
                            color = Color.White,
                            radius = 2.5.dp.toPx(),
                            center = coord
                        )

                        // Floating point count badge above the dot
                        val callCountText = "${pt.count}"
                        drawContext.canvas.nativeCanvas.apply {
                            val badgePaint = android.graphics.Paint().apply {
                                color = primaryColor.hashCode()
                                textSize = 11.sp.toPx()
                                textAlign = android.graphics.Paint.Align.CENTER
                                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                                isAntiAlias = true
                            }
                            val badgeY = (coord.y - 8.dp.toPx()).coerceAtLeast(topPadding - 2.dp.toPx())
                            drawText(
                                callCountText,
                                coord.x,
                                badgeY,
                                badgePaint
                            )
                        }
                    }
                }

                // 8. Draw Strategic Date Labels on X-Axis
                // Key intervals: Day 0 (-29d), Day 7 (-22d), Day 14 (-15d), Day 21 (-8d), Day 29 ("Today")
                val keyIndexes = listOf(0, 7, 14, 21, points.lastIndex)
                keyIndexes.forEach { idx ->
                    if (idx in points.indices) {
                        val pt = points[idx]
                        val coord = pointCoords[idx]
                        val isToday = idx == points.lastIndex
                        val isSelected = selectedIndex == idx

                        drawContext.canvas.nativeCanvas.apply {
                            val labelPaint = android.graphics.Paint().apply {
                                color = if (isSelected || isToday) primaryColor.hashCode() else textPaintColor
                                textSize = 9.5.sp.toPx()
                                textAlign = when (idx) {
                                    0 -> android.graphics.Paint.Align.LEFT
                                    points.lastIndex -> android.graphics.Paint.Align.RIGHT
                                    else -> android.graphics.Paint.Align.CENTER
                                }
                                typeface = if (isSelected || isToday) {
                                    android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                                } else {
                                    android.graphics.Typeface.DEFAULT
                                }
                                isAntiAlias = true
                            }
                            drawText(
                                pt.label,
                                coord.x,
                                canvasHeight - 3.dp.toPx(),
                                labelPaint
                            )
                        }
                    }
                }
            }
        }
    }
}
