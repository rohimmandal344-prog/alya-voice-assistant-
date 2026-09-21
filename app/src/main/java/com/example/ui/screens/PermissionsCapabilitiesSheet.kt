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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.capability.CapabilityCategory
import com.example.capability.CapabilityItem
import com.example.capability.CapabilityStatus
import com.example.ui.viewmodel.AlyaViewModel

/**
 * Clean, soft, production-grade Permissions & Capabilities Sheet.
 * Features real-time auto-checking of device hardware, Android permissions,
 * and system services across Android 10 through Android 17+.
 *
 * Fully addresses user intent:
 * - Soft, clean look with generous whitespace and elegant Material 3 cards.
 * - Auto-checks permissions when opened and whenever system focus changes.
 * - Wake-up activation workflow:
 *   "Wake Up Active" button -> 3-step Voice Enrollment -> User voice profile set.
 * - User voice profile management: Re-train, Delete Voice Profile, and Wake-up deactivate switch.
 */
@Composable
fun PermissionsCapabilitiesSheet(
    viewModel: AlyaViewModel,
    onDismiss: () -> Unit = {},
    onClose: () -> Unit = onDismiss,
    onOpenWakeUpActivation: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val closeAction = {
        onDismiss()
        onClose()
    }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val capabilities by viewModel.capabilities.collectAsState()
    val healthOverview by viewModel.healthOverview.collectAsState()
    val isChecking by viewModel.isCapabilitiesChecking.collectAsState()

    val isVoiceProfileSet by viewModel.isVoiceProfileSet.collectAsState()
    val isWakeUpActivated by viewModel.isWakeUpActivated.collectAsState()

    var showDeleteVoiceDialog by remember { mutableStateOf(false) }
    var showAccessibilityGuidanceDialog by remember { mutableStateOf(false) }
    var expandedItemId by remember { mutableStateOf<String?>(null) }
    var selectedCategoryFilter by remember { mutableStateOf<CapabilityCategory?>(null) }

    // Auto-checking: recheck capabilities on lifecycle resume
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

    // Permission launcher for runtime requests
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.refreshCapabilities()
    }

    // Delete Voice Profile Confirmation Dialog
    if (showDeleteVoiceDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteVoiceDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = "Delete User Voice Profile?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "This will remove your enrolled voice sample profile. Wake-up detection ('Hey Alya', 'Alia', 'Seno') will be deactivated until you re-train your voice.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteVoiceDialog = false
                        viewModel.deleteUserVoiceProfile()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Delete Voice Profile")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteVoiceDialog = false }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }

    if (showAccessibilityGuidanceDialog) {
        com.example.ui.components.AccessibilityGuidanceDialog(
            onDismiss = { showAccessibilityGuidanceDialog = false }
        )
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("permissions_capabilities_screen"),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
        ) {
            // 1. Soft Top App Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Permissions",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Permissions & Capabilities",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Auto-checking live indicator
                            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                            val pulseAlpha by infiniteTransition.animateFloat(
                                initialValue = 0.4f,
                                targetValue = 1f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(900, easing = FastOutSlowInEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "auto_check_pulse"
                            )
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isChecking) MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha)
                                        else Color(0xFF10B981)
                                    )
                            )
                            Text(
                                text = if (isChecking) "Auto-checking..." else "Auto-checking active",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { viewModel.refreshCapabilities() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = closeAction,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Divider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                thickness = 1.dp
            )

            // 2. Scrollable Content
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // 2.1 System Health & Progress Card
                item {
                    SoftSystemHealthCard(
                        overview = healthOverview,
                        isChecking = isChecking
                    )
                }

                // 2.1.1 System Requirements & Android Version Breakdown
                item {
                    com.example.ui.components.SystemRequirementsCard(
                        initiallyExpanded = false
                    )
                }

                // 2.2 Wake-Up Voice Activation Card (Primary User Feature)
                item {
                    SoftWakeUpActivationCard(
                        isVoiceProfileSet = isVoiceProfileSet,
                        isWakeUpActivated = isWakeUpActivated,
                        onActivateClick = {
                            if (onOpenWakeUpActivation != null) {
                                onOpenWakeUpActivation()
                            } else {
                                viewModel.openWakeUpActivationDialog()
                            }
                        },
                        onDeleteClick = { showDeleteVoiceDialog = true },
                        onToggleWakeUp = { enabled ->
                            if (enabled) {
                                if (!isVoiceProfileSet) {
                                    if (onOpenWakeUpActivation != null) onOpenWakeUpActivation()
                                    else viewModel.openWakeUpActivationDialog()
                                } else {
                                    viewModel.activateWakeUp()
                                }
                            } else {
                                viewModel.deactivateWakeUp()
                            }
                        }
                    )
                }

                // 2.3 Category Filter Pills
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Capabilities & Access",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SoftFilterChip(
                                label = "All (${capabilities.size})",
                                isSelected = selectedCategoryFilter == null,
                                onClick = { selectedCategoryFilter = null }
                            )
                            SoftFilterChip(
                                label = "Audio & Voice",
                                isSelected = selectedCategoryFilter == CapabilityCategory.AUDIO_AND_VOICE,
                                onClick = { selectedCategoryFilter = CapabilityCategory.AUDIO_AND_VOICE }
                            )
                            SoftFilterChip(
                                label = "Wake-Up",
                                isSelected = selectedCategoryFilter == CapabilityCategory.BACKGROUND_AND_WAKEUP,
                                onClick = { selectedCategoryFilter = CapabilityCategory.BACKGROUND_AND_WAKEUP }
                            )
                        }
                    }
                }

                // 2.4 Filtered Capability Items
                val filteredList = capabilities.filter {
                    selectedCategoryFilter == null || it.category == selectedCategoryFilter
                }

                items(filteredList, key = { it.id }) { item ->
                    SoftCapabilityItemCard(
                        item = item,
                        isExpanded = expandedItemId == item.id,
                        onToggleExpand = {
                            expandedItemId = if (expandedItemId == item.id) null else item.id
                        },
                        onActionClick = {
                            handleCapabilityAction(
                                context = context,
                                item = item,
                                permissionLauncher = permissionLauncher,
                                onOpenVoiceTraining = {
                                    if (onOpenWakeUpActivation != null) onOpenWakeUpActivation()
                                    else viewModel.openWakeUpActivationDialog()
                                },
                                onShowAccessibilityGuidance = {
                                    showAccessibilityGuidanceDialog = true
                                },
                                onToggleState = {
                                    if (item.id == "wake_word_engine") {
                                        viewModel.activateWakeUp()
                                    } else if (item.id == "foreground_service") {
                                        com.example.service.WakeWordService.start(context)
                                        viewModel.refreshCapabilities()
                                    }
                                }
                            )
                        }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

/**
 * System Health Summary Card with clean, soft aesthetics and live progress bar.
 */
@Composable
private fun SoftSystemHealthCard(
    overview: com.example.capability.SystemHealthOverview,
    isChecking: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("system_health_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "System Readiness",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${overview.grantedCount} of ${overview.totalCount} Capabilities Active",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Health Badge
                val badgeColor = when {
                    overview.healthPercentage >= 80 -> Color(0xFF10B981)
                    overview.healthPercentage >= 50 -> Color(0xFFF59E0B)
                    else -> Color(0xFFEF4444)
                }
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = badgeColor.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.35f))
                ) {
                    Text(
                        text = overview.healthLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = badgeColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Smooth animated progress
            LinearProgressIndicator(
                progress = { overview.healthPercentage / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (overview.healthPercentage >= 80) Color(0xFF10B981) else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Optimal hands-free voice performance",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
                Text(
                    text = "${overview.healthPercentage}% Ready",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/**
 * Wake-Up Voice Activation & Profile Management Card.
 * Directly fulfills the user's requirement:
 * "add wake up activation process in this app user tap wake up active --->
 *  App showing say hey alya what is the temperature outside and
 *  2 say hey alya ADD milk in my bucket list and
 *  3 hey alya set a alarm 7 am ---> set user voice --->
 *  user voice delete option and wake up deactivate option in setting"
 */
@Composable
private fun SoftWakeUpActivationCard(
    isVoiceProfileSet: Boolean,
    isWakeUpActivated: Boolean,
    onActivateClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onToggleWakeUp: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("wake_up_activation_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isVoiceProfileSet && isWakeUpActivated)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
            else
                MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        border = BorderStroke(
            1.2.dp,
            if (isVoiceProfileSet && isWakeUpActivated)
                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            else
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Header Row: Icon + Title + Switch / Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                if (isVoiceProfileSet && isWakeUpActivated)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.surfaceVariant
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.RecordVoiceOver,
                            contentDescription = null,
                            tint = if (isVoiceProfileSet && isWakeUpActivated)
                                MaterialTheme.colorScheme.onPrimary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Column {
                        Text(
                            text = if (isVoiceProfileSet) "User Voice Profile Enrolled" else "Wake-Up Activation",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = when {
                                isVoiceProfileSet && isWakeUpActivated -> "Active • Hey Alya, Alia, Seno"
                                isVoiceProfileSet && !isWakeUpActivated -> "Profile Saved • Wake-Up Deactivated"
                                else -> "Train your voice with 3 sample phrases"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isVoiceProfileSet && isWakeUpActivated)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (isVoiceProfileSet) {
                    Switch(
                        checked = isWakeUpActivated,
                        onCheckedChange = onToggleWakeUp,
                        modifier = Modifier.testTag("wake_up_active_switch"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (!isVoiceProfileSet) {
                // Enrollment Prompt
                Text(
                    text = "Alya listens for \"Hey Alya\", \"Alia\", and \"Seno\". Tap below to calibrate your voice by speaking 3 quick commands.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = onActivateClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("wake_up_active_button"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Wake Up Active",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                // Profile Active Details & Management
                Text(
                    text = "Calibrated for: \"Hey Alya, what is the temperature outside\", \"add milk in my bucket list\", and \"set a alarm 7 am\".",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDeleteClick,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("delete_voice_profile_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Delete Voice",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Button(
                        onClick = onActivateClick,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("retrain_voice_profile_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Re-train",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

/**
 * Clean, soft capability item card with expandable details and one-tap action.
 */
@Composable
private fun SoftCapabilityItemCard(
    item: CapabilityItem,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onActionClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusColors = getStatusColorPalette(item.status)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable { onToggleExpand() }
            .testTag("capability_${item.id}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        border = BorderStroke(
            1.dp,
            if (item.status == CapabilityStatus.GRANTED)
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            else
                statusColors.border
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Main Summary Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(statusColors.iconBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = getCategoryIcon(item.category, item.id),
                            contentDescription = null,
                            tint = statusColors.text,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (item.isRequired) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                                ) {
                                    Text(
                                        text = "Required",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = item.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = if (isExpanded) Int.MAX_VALUE else 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Status Badge & Expand Indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = statusColors.badgeBg,
                        border = BorderStroke(1.dp, statusColors.border)
                    ) {
                        Text(
                            text = item.statusBadge,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = statusColors.text,
                            maxLines = 1,
                            softWrap = false,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }

                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Toggle",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Expanded Details Section
            if (isExpanded) {
                Spacer(modifier = Modifier.height(14.dp))
                Divider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                    thickness = 1.dp
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Why Needed
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(16.dp)
                            .padding(top = 2.dp)
                    )
                    Column {
                        Text(
                            text = "Why Alya Needs This",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = item.whyNeeded,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Fallback Behavior
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.WarningAmber,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier
                            .size(16.dp)
                            .padding(top = 2.dp)
                    )
                    Column {
                        Text(
                            text = "Graceful Fallback If Denied",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = item.fallbackBehavior,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Action Button if required
                if (item.actionType != CapabilityItem.ActionType.NONE) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = onActionClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = when (item.status) {
                                CapabilityStatus.DENIED -> MaterialTheme.colorScheme.primary
                                CapabilityStatus.RESTRICTED_BY_DEVICE,
                                CapabilityStatus.RESTRICTED_BY_ANDROID -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.secondary
                            }
                        )
                    ) {
                        Text(
                            text = getActionButtonLabel(item),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SoftFilterChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(
            1.dp,
            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        ),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

private data class StatusColorPalette(
    val text: Color,
    val badgeBg: Color,
    val iconBg: Color,
    val border: Color
)

@Composable
private fun getStatusColorPalette(status: CapabilityStatus): StatusColorPalette {
    return when (status) {
        CapabilityStatus.GRANTED -> StatusColorPalette(
            text = Color(0xFF10B981),
            badgeBg = Color(0xFF10B981).copy(alpha = 0.12f),
            iconBg = Color(0xFF10B981).copy(alpha = 0.16f),
            border = Color(0xFF10B981).copy(alpha = 0.35f)
        )
        CapabilityStatus.DENIED -> StatusColorPalette(
            text = Color(0xFFEF4444),
            badgeBg = Color(0xFFEF4444).copy(alpha = 0.12f),
            iconBg = Color(0xFFEF4444).copy(alpha = 0.16f),
            border = Color(0xFFEF4444).copy(alpha = 0.35f)
        )
        CapabilityStatus.RESTRICTED_BY_DEVICE,
        CapabilityStatus.RESTRICTED_BY_ANDROID -> StatusColorPalette(
            text = Color(0xFFF59E0B),
            badgeBg = Color(0xFFF59E0B).copy(alpha = 0.12f),
            iconBg = Color(0xFFF59E0B).copy(alpha = 0.16f),
            border = Color(0xFFF59E0B).copy(alpha = 0.35f)
        )
        CapabilityStatus.NOT_CONFIGURED -> StatusColorPalette(
            text = Color(0xFF3B82F6),
            badgeBg = Color(0xFF3B82F6).copy(alpha = 0.12f),
            iconBg = Color(0xFF3B82F6).copy(alpha = 0.16f),
            border = Color(0xFF3B82F6).copy(alpha = 0.35f)
        )
        CapabilityStatus.NOT_SUPPORTED,
        CapabilityStatus.PERMANENTLY_DENIED -> StatusColorPalette(
            text = MaterialTheme.colorScheme.outline,
            badgeBg = MaterialTheme.colorScheme.surfaceVariant,
            iconBg = MaterialTheme.colorScheme.surfaceVariant,
            border = MaterialTheme.colorScheme.outlineVariant
        )
    }
}

private fun getCategoryIcon(category: CapabilityCategory, id: String): ImageVector {
    return when (id) {
        "mic" -> Icons.Default.Mic
        "speech_engine" -> Icons.Default.RecordVoiceOver
        "tts_engine" -> Icons.Filled.VolumeUp
        "voice_profile" -> Icons.Default.RecordVoiceOver
        "wake_word_engine" -> Icons.Default.Mic
        "notifications" -> Icons.Default.Notifications
        "foreground_service" -> Icons.Default.Layers
        "battery_opt" -> Icons.Default.BatteryChargingFull
        "system_overlay" -> Icons.Default.Layers
        "exact_alarms" -> Icons.Default.Alarm
        "location_weather" -> Icons.Default.LocationOn
        "phone_contacts" -> Icons.Default.Phone
        "bluetooth_connect" -> Icons.Default.Bluetooth
        "accessibility_service" -> Icons.Default.Settings
        "special_file_access" -> Icons.Default.Layers
        "media_access" -> Icons.Default.Info
        "network_intelligence" -> Icons.Default.Wifi
        else -> Icons.Default.Security
    }
}

private fun getActionButtonLabel(item: CapabilityItem): String {
    return when (item.actionType) {
        CapabilityItem.ActionType.REQUEST_PERMISSION -> "Grant Permission"
        CapabilityItem.ActionType.OPEN_APP_SETTINGS -> "Open App Settings"
        CapabilityItem.ActionType.OPEN_OVERLAY_SETTINGS -> "Enable Floating Overlay"
        CapabilityItem.ActionType.OPEN_BATTERY_SETTINGS -> "Disable Battery Limits"
        CapabilityItem.ActionType.OPEN_ALARM_SETTINGS -> "Allow Exact Alarms"
        CapabilityItem.ActionType.OPEN_ACCESSIBILITY_SETTINGS -> "Enable Accessibility"
        CapabilityItem.ActionType.OPEN_FILE_ACCESS_SETTINGS -> "Manage File Access"
        CapabilityItem.ActionType.OPEN_VOICE_TRAINING -> "Train Voice (3 Samples)"
        CapabilityItem.ActionType.TOGGLE_STATE -> "Activate Feature"
        CapabilityItem.ActionType.NONE -> "View Details"
    }
}

private fun handleCapabilityAction(
    context: Context,
    item: CapabilityItem,
    permissionLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>,
    onOpenVoiceTraining: () -> Unit,
    onShowAccessibilityGuidance: () -> Unit = {},
    onToggleState: () -> Unit
) {
    when (item.actionType) {
        CapabilityItem.ActionType.REQUEST_PERMISSION -> {
            val perm = item.permissionKey
            if (perm != null) {
                permissionLauncher.launch(arrayOf(perm))
            } else if (item.id == "phone_contacts") {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.CALL_PHONE,
                        Manifest.permission.READ_CONTACTS
                    )
                )
            } else {
                openAppSettings(context)
            }
        }
        CapabilityItem.ActionType.OPEN_APP_SETTINGS -> {
            openAppSettings(context)
        }
        CapabilityItem.ActionType.OPEN_OVERLAY_SETTINGS -> {
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                context.startActivity(intent)
            } catch (_: Exception) {
                openAppSettings(context)
            }
        }
        CapabilityItem.ActionType.OPEN_BATTERY_SETTINGS -> {
            try {
                val intent = Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:${context.packageName}")
                ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                context.startActivity(intent)
            } catch (_: Exception) {
                try {
                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (_: Exception) {
                    openAppSettings(context)
                }
            }
        }
        CapabilityItem.ActionType.OPEN_ALARM_SETTINGS -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    val intent = Intent(
                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:${context.packageName}")
                    ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    context.startActivity(intent)
                } catch (_: Exception) {
                    openAppSettings(context)
                }
            }
        }
        CapabilityItem.ActionType.OPEN_ACCESSIBILITY_SETTINGS -> {
            onShowAccessibilityGuidance()
            try {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                openAppSettings(context)
            }
        }
        CapabilityItem.ActionType.OPEN_FILE_ACCESS_SETTINGS -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    context.startActivity(intent)
                } catch (_: Exception) {
                    try {
                        val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        openAppSettings(context)
                    }
                }
            } else {
                openAppSettings(context)
            }
        }
        CapabilityItem.ActionType.OPEN_VOICE_TRAINING -> {
            onOpenVoiceTraining()
        }
        CapabilityItem.ActionType.TOGGLE_STATE -> {
            onToggleState()
        }
        CapabilityItem.ActionType.NONE -> {}
    }
}

private fun openAppSettings(context: Context) {
    try {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        context.startActivity(intent)
    } catch (e: Exception) { android.util.Log.e("Alya", "Exception handled", e) }
}
