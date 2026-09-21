package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.capability.CapabilityCategory
import com.example.capability.CapabilityItem
import com.example.capability.CapabilityStatus
import com.example.capability.SystemHealthOverview
import com.example.ui.components.AccessibilityGuidanceDialog
import com.example.ui.viewmodel.AlyaViewModel

/**
 * CapabilityDashboardScreen
 *
 * Material3 dashboard displaying real-time statuses of system permissions and core features
 * (Wake-Up Engine, Microphone Capture, Foreground Service FGS, Notifications, Overlay, Accessibility).
 *
 * All status badges are directly bound to genuine system API checks via CapabilityManager.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CapabilityDashboardScreen(
    viewModel: AlyaViewModel,
    onClose: () -> Unit = {},
    onOpenWakeUpActivation: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val capabilities by viewModel.capabilities.collectAsState()
    val healthOverview by viewModel.healthOverview.collectAsState()
    val isChecking by viewModel.isCapabilitiesChecking.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<CapabilityCategory?>(null) }
    var expandedItems by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showAccessibilityGuidance by remember { mutableStateOf(false) }

    // Auto-refresh on resume (when returning from System Settings or permission dialogs)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshCapabilities()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Permission launcher for runtime permissions
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.refreshCapabilities()
    }

    if (showAccessibilityGuidance) {
        AccessibilityGuidanceDialog(
            onDismiss = { showAccessibilityGuidance = false }
        )
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("capability_dashboard_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Capabilities & Permissions",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Real-time system API inspection across Android ${Build.VERSION.RELEASE}+",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.testTag("capability_dashboard_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.refreshCapabilities() },
                        enabled = !isChecking,
                        modifier = Modifier.testTag("capability_dashboard_refresh_button")
                    ) {
                        if (isChecking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh System Statuses"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // 1. System Health Overview Card
            item {
                SystemReadinessCard(
                    overview = healthOverview,
                    isChecking = isChecking,
                    onRequestPending = {
                        requestCorePermissions(context, permissionLauncher)
                    }
                )
            }

            // 2. Core Feature Pillars Section (Wake-Up, Mic, FGS, Notifications, Overlay, Accessibility)
            item {
                CoreFeaturePillarsSection(
                    context = context,
                    capabilities = capabilities,
                    permissionLauncher = permissionLauncher,
                    onOpenWakeUpActivation = onOpenWakeUpActivation,
                    onShowAccessibilityGuidance = { showAccessibilityGuidance = true }
                )
            }

            // 3. Category Filter Chips & Search Bar
            item {
                FilterAndSearchSection(
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    selectedCategory = selectedCategory,
                    onCategorySelect = { selectedCategory = it }
                )
            }

            // 4. System Settings Shortcut & Details Expand/Collapse Header Row
            val filteredCapabilities = capabilities.filter { item ->
                val matchesCategory = selectedCategory == null || item.category == selectedCategory
                val matchesSearch = searchQuery.isBlank() ||
                        item.title.contains(searchQuery, ignoreCase = true) ||
                        item.description.contains(searchQuery, ignoreCase = true) ||
                        item.whyNeeded.contains(searchQuery, ignoreCase = true)
                matchesCategory && matchesSearch
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            val intent = android.content.Intent(
                                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                android.net.Uri.fromParts("package", context.packageName, null)
                            ).apply {
                                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("open_system_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open System Permissions Settings", fontWeight = FontWeight.Bold)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Capability Details",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        
                        val allExpanded = filteredCapabilities.isNotEmpty() && filteredCapabilities.all { expandedItems.contains(it.id) }
                        TextButton(
                            onClick = {
                                if (allExpanded) {
                                    expandedItems = emptySet()
                                } else {
                                    expandedItems = filteredCapabilities.map { it.id }.toSet()
                                }
                            },
                            modifier = Modifier.testTag("expand_collapse_all_button")
                        ) {
                            Icon(
                                imageVector = if (allExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (allExpanded) "Collapse All" else "Expand All")
                        }
                    }
                }
            }

            if (filteredCapabilities.isEmpty()) {
                item {
                    EmptyCapabilitiesCard(
                        searchQuery = searchQuery,
                        onClearSearch = {
                            searchQuery = ""
                            selectedCategory = null
                        }
                    )
                }
            } else {
                items(
                    items = filteredCapabilities,
                    key = { it.id }
                ) { capability ->
                    CapabilityItemCard(
                        item = capability,
                        isExpanded = expandedItems.contains(capability.id),
                        onToggleExpand = {
                            expandedItems = if (expandedItems.contains(capability.id)) {
                                expandedItems - capability.id
                            } else {
                                expandedItems + capability.id
                            }
                        },
                        onAction = {
                            handleCapabilityAction(
                                context = context,
                                item = capability,
                                permissionLauncher = permissionLauncher,
                                onShowAccessibilityGuidance = { showAccessibilityGuidance = true },
                                onOpenWakeUpActivation = onOpenWakeUpActivation
                            )
                        }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 1. SYSTEM READINESS HERO CARD
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SystemReadinessCard(
    overview: SystemHealthOverview,
    isChecking: Boolean,
    onRequestPending: () -> Unit
) {
    ElevatedCard(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = "System Readiness",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "${overview.healthPercentage}% Ready",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            // Readiness Linear Progress Bar
            LinearProgressIndicator(
                progress = { overview.healthPercentage / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
            )

            // Status Counts (Granted, Warnings, Missing)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatusMetricBadge(
                    count = overview.grantedCount,
                    label = "Granted",
                    color = Color(0xFF10B981)
                )
                StatusMetricBadge(
                    count = overview.warningCount,
                    label = "Warnings",
                    color = Color(0xFFF59E0B)
                )
                StatusMetricBadge(
                    count = overview.criticalMissingCount,
                    label = "Missing Required",
                    color = if (overview.criticalMissingCount > 0) Color(0xFFEF4444) else Color(0xFF10B981)
                )
            }

            if (overview.criticalMissingCount > 0) {
                Button(
                    onClick = onRequestPending,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Request Pending Permissions")
                }
            }
        }
    }
}

@Composable
private fun StatusMetricBadge(
    count: Int,
    label: String,
    color: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, CircleShape)
        )
        Text(
            text = "$count $label",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 2. CORE FEATURE PILLARS SECTION (Wake-Up, Mic, FGS, Notifications, Overlay, Accessibility)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CoreFeaturePillarsSection(
    context: Context,
    capabilities: List<CapabilityItem>,
    permissionLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>,
    onOpenWakeUpActivation: (() -> Unit)?,
    onShowAccessibilityGuidance: () -> Unit
) {
    val wakeItem = capabilities.find { it.id == "wake_word_engine" }
    val micItem = capabilities.find { it.id == "mic" }
    val fgsItem = capabilities.find { it.id == "foreground_service" }
    val notificationItem = capabilities.find { it.id == "notifications" }
    val overlayItem = capabilities.find { it.id == "system_overlay" }
    val accessibilityItem = capabilities.find { it.id == "accessibility_service" }

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "Core Feature Real-Time Statuses",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Pillar 1: Wake-Up Detection Engine
            CoreFeaturePillarCard(
                title = "Wake-Up Engine (Alia / Alya / Seno)",
                subtitle = "Always-listening trigger for instant voice pop-up",
                icon = Icons.Default.RecordVoiceOver,
                statusBadge = wakeItem?.statusBadge ?: "Standby",
                isGranted = wakeItem?.status == CapabilityStatus.GRANTED,
                onAction = {
                    onOpenWakeUpActivation?.invoke()
                }
            )

            // Pillar 2: Microphone & Audio Capture
            CoreFeaturePillarCard(
                title = "Microphone Access",
                subtitle = "Captures live audio & voice commands",
                icon = Icons.Default.Mic,
                statusBadge = micItem?.statusBadge ?: "Required",
                isGranted = micItem?.status == CapabilityStatus.GRANTED,
                onAction = {
                    permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                }
            )

            // Pillar 3: Foreground Service (FGS)
            CoreFeaturePillarCard(
                title = "Foreground Service (FGS)",
                subtitle = "Keeps background listening active during multitasking",
                icon = Icons.Default.GraphicEq,
                statusBadge = fgsItem?.statusBadge ?: "Stopped",
                isGranted = fgsItem?.status == CapabilityStatus.GRANTED,
                onAction = {
                    onOpenWakeUpActivation?.invoke()
                }
            )

            // Pillar 4: Notifications & Live Alerts
            CoreFeaturePillarCard(
                title = "Notifications (Android 13+)",
                subtitle = "Displays persistent status notification",
                icon = Icons.Default.Notifications,
                statusBadge = notificationItem?.statusBadge ?: "Permission Needed",
                isGranted = notificationItem?.status == CapabilityStatus.GRANTED,
                onAction = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                    } else {
                        openAppSettings(context)
                    }
                }
            )

            // Pillar 5: Floating Popup Overlay
            CoreFeaturePillarCard(
                title = "System Alert Overlay",
                subtitle = "Displays floating voice pop-up over other apps",
                icon = Icons.Default.Layers,
                statusBadge = overlayItem?.statusBadge ?: "Recommended",
                isGranted = overlayItem?.status == CapabilityStatus.GRANTED,
                onAction = {
                    openOverlaySettings(context)
                }
            )

            // Pillar 6: Accessibility Service
            CoreFeaturePillarCard(
                title = "Accessibility Automation",
                subtitle = "Enables device control, scrolling & screen reading",
                icon = Icons.Default.AccessibilityNew,
                statusBadge = accessibilityItem?.statusBadge ?: "Disabled",
                isGranted = accessibilityItem?.status == CapabilityStatus.GRANTED,
                onAction = {
                    onShowAccessibilityGuidance()
                    openAccessibilitySettings(context)
                }
            )
        }
    }
}

@Composable
private fun CoreFeaturePillarCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    statusBadge: String,
    isGranted: Boolean,
    onAction: () -> Unit
) {
    val activeColor = Color(0xFF10B981)
    val inactiveColor = Color(0xFFF59E0B)

    OutlinedCard(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier.fillMaxWidth()
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
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isGranted) activeColor.copy(alpha = 0.15f) else inactiveColor.copy(alpha = 0.15f),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (isGranted) activeColor else inactiveColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isGranted) activeColor.copy(alpha = 0.15f) else inactiveColor.copy(alpha = 0.15f),
                modifier = Modifier.clickable { onAction() }
            ) {
                Text(
                    text = statusBadge,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isGranted) activeColor else inactiveColor,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 3. FILTER & SEARCH SECTION
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FilterAndSearchSection(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedCategory: CapabilityCategory?,
    onCategorySelect: (CapabilityCategory?) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("Search capabilities or permissions...") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear search"
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            ),
            modifier = Modifier.fillMaxWidth()
        )

        // Horizontal Category Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedCategory == null,
                onClick = { onCategorySelect(null) },
                label = { Text("All Capabilities") },
                shape = RoundedCornerShape(12.dp)
            )

            CapabilityCategory.values().forEach { category ->
                FilterChip(
                    selected = selectedCategory == category,
                    onClick = {
                        onCategorySelect(if (selectedCategory == category) null else category)
                    },
                    label = { Text(category.title) },
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 4. CAPABILITY ITEM CARD
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CapabilityItemCard(
    item: CapabilityItem,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onAction: () -> Unit
) {
    val statusColor = when (item.status) {
        CapabilityStatus.GRANTED -> Color(0xFF10B981)
        CapabilityStatus.DENIED -> Color(0xFFEF4444)
        CapabilityStatus.RESTRICTED_BY_DEVICE,
        CapabilityStatus.RESTRICTED_BY_ANDROID -> Color(0xFFF59E0B)
        CapabilityStatus.NOT_CONFIGURED -> Color(0xFF3B82F6)
        CapabilityStatus.NOT_SUPPORTED,
        CapabilityStatus.PERMANENTLY_DENIED -> Color(0xFF6B7280)
    }

    val categoryIcon = when (item.category) {
        CapabilityCategory.AUDIO_AND_VOICE -> Icons.Default.Mic
        CapabilityCategory.BACKGROUND_AND_WAKEUP -> Icons.Default.GraphicEq
        CapabilityCategory.DEVICE_CONTROL -> Icons.Default.Settings
        CapabilityCategory.STORAGE_AND_FILES -> Icons.Default.Folder
        CapabilityCategory.CONNECTIVITY_AND_OFFLINE -> Icons.Default.Wifi
        CapabilityCategory.PHONE_AND_CONTACTS -> Icons.Default.Phone
    }

    OutlinedCard(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .testTag("capability_card_${item.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = statusColor.copy(alpha = 0.12f),
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = categoryIcon,
                                contentDescription = null,
                                tint = statusColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (item.isRequired) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.errorContainer
                                ) {
                                    Text(
                                        text = "Required",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Text(
                            text = item.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = if (isExpanded) Int.MAX_VALUE else 2
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = statusColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = item.statusBadge,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = statusColor,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }

                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Expanded Details Section
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    // Why Needed
                    InfoBlock(
                        title = "Why Needed",
                        text = item.whyNeeded,
                        icon = Icons.Default.Info,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Fallback Behavior
                    InfoBlock(
                        title = "Fallback Behavior",
                        text = item.fallbackBehavior,
                        icon = Icons.Default.HelpOutline,
                        color = MaterialTheme.colorScheme.secondary
                    )

                    // Action Button
                    if (item.actionType != CapabilityItem.ActionType.NONE && item.status != CapabilityStatus.GRANTED) {
                        Button(
                            onClick = onAction,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(getActionTitle(item))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoBlock(
    title: String,
    text: String,
    icon: ImageVector,
    color: Color
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.08f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(18.dp)
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun EmptyCapabilitiesCard(
    searchQuery: String,
    onClearSearch: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "No Capabilities Found",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "No matching items found for \"$searchQuery\".",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = onClearSearch) {
                Text("Clear Filters")
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// HELPER INTENT LAUNCHERS & ACTIONS
// ─────────────────────────────────────────────────────────────────────────────

private fun getActionTitle(item: CapabilityItem): String {
    return when (item.actionType) {
        CapabilityItem.ActionType.REQUEST_PERMISSION -> "Grant Permission"
        CapabilityItem.ActionType.OPEN_APP_SETTINGS -> "Open App Settings"
        CapabilityItem.ActionType.OPEN_OVERLAY_SETTINGS -> "Enable Floating Overlay"
        CapabilityItem.ActionType.OPEN_BATTERY_SETTINGS -> "Disable Battery Limits"
        CapabilityItem.ActionType.OPEN_ALARM_SETTINGS -> "Allow Exact Alarms"
        CapabilityItem.ActionType.OPEN_ACCESSIBILITY_SETTINGS -> "Enable Accessibility"
        CapabilityItem.ActionType.OPEN_FILE_ACCESS_SETTINGS -> "Grant Special File Access"
        CapabilityItem.ActionType.OPEN_VOICE_TRAINING -> "Train Voice Profile"
        CapabilityItem.ActionType.TOGGLE_STATE -> "Activate Feature"
        CapabilityItem.ActionType.NONE -> "View Details"
    }
}

private fun handleCapabilityAction(
    context: Context,
    item: CapabilityItem,
    permissionLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>,
    onShowAccessibilityGuidance: () -> Unit,
    onOpenWakeUpActivation: (() -> Unit)?
) {
    when (item.actionType) {
        CapabilityItem.ActionType.REQUEST_PERMISSION -> {
            item.permissionKey?.let { perm ->
                permissionLauncher.launch(arrayOf(perm))
            } ?: openAppSettings(context)
        }
        CapabilityItem.ActionType.OPEN_APP_SETTINGS -> openAppSettings(context)
        CapabilityItem.ActionType.OPEN_OVERLAY_SETTINGS -> openOverlaySettings(context)
        CapabilityItem.ActionType.OPEN_BATTERY_SETTINGS -> openBatterySettings(context)
        CapabilityItem.ActionType.OPEN_ALARM_SETTINGS -> openAlarmSettings(context)
        CapabilityItem.ActionType.OPEN_ACCESSIBILITY_SETTINGS -> {
            onShowAccessibilityGuidance()
            openAccessibilitySettings(context)
        }
        CapabilityItem.ActionType.OPEN_FILE_ACCESS_SETTINGS -> openFileAccessSettings(context)
        CapabilityItem.ActionType.OPEN_VOICE_TRAINING -> onOpenWakeUpActivation?.invoke()
        CapabilityItem.ActionType.TOGGLE_STATE -> onOpenWakeUpActivation?.invoke()
        CapabilityItem.ActionType.NONE -> {}
    }
}

private fun requestCorePermissions(
    context: Context,
    permissionLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>
) {
    val needed = mutableListOf<String>()
    needed.add(Manifest.permission.RECORD_AUDIO)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        needed.add(Manifest.permission.POST_NOTIFICATIONS)
    }
    if (needed.isNotEmpty()) {
        permissionLauncher.launch(needed.toTypedArray())
    } else {
        openAppSettings(context)
    }
}

private fun openAppSettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun openOverlaySettings(context: Context) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } else {
            openAppSettings(context)
        }
    } catch (e: Exception) {
        openAppSettings(context)
    }
}

private fun openBatterySettings(context: Context) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } else {
            openAppSettings(context)
        }
    } catch (e: Exception) {
        openAppSettings(context)
    }
}

private fun openAlarmSettings(context: Context) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } else {
            openAppSettings(context)
        }
    } catch (e: Exception) {
        openAppSettings(context)
    }
}

private fun openAccessibilitySettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        openAppSettings(context)
    }
}

private fun openFileAccessSettings(context: Context) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } else {
            openAppSettings(context)
        }
    } catch (e: Exception) {
        openAppSettings(context)
    }
}
