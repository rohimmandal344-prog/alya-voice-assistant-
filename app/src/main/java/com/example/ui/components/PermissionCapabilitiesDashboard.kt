package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.service.AlyaAccessibilityService

/**
 * System Capability / Permission definition with live checking and intents.
 */
data class SystemPermissionCapability(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val isGranted: Boolean,
    val isRequired: Boolean,
    val tag: String,
    val description: String,
    val explanation: String,
    val impactWhenDenied: String,
    val actionLabel: String,
    val onAction: (Context, () -> Unit) -> Unit
)

/**
 * `PermissionCapabilitiesDashboard`
 * Maps real-time Android system status of required permissions:
 * - Microphone (Audio recording / Voice wake & recognition)
 * - Accessibility (Hands-free phone automation, screen reading, call pickup/hangup)
 * - Notifications (Background persistent service, active listening alerts, timers)
 * - Overlay / System Alert Window (Floating assistant bubble, listening pop-up over other apps)
 *
 * Includes capability cards with live status indicators, clear user explanations,
 * and direct intent triggers for Android system settings.
 */
@Composable
fun PermissionCapabilitiesDashboard(
    modifier: Modifier = Modifier,
    onShowAccessibilityGuidance: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Refresh trigger on lifecycle resume
    var refreshKey by remember { mutableStateOf(0) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshKey++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Permission launchers for standard runtime requests
    var requestedPermission by remember { mutableStateOf<String?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        refreshKey++
    }

    // Check live real-time status across system
    val micGranted = remember(refreshKey) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    val notificationGranted = remember(refreshKey) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Granted at install time prior to Android 13
        }
    }

    val overlayGranted = remember(refreshKey) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    val accessibilityGranted: Boolean = remember(refreshKey) {
        AlyaAccessibilityService.checkAccessibilityPermission(context) == com.example.service.AccessibilityPermissionStatus.GRANTED
    }

    // Build the core system capabilities list
    val capabilities = remember(micGranted, notificationGranted, overlayGranted, accessibilityGranted) {
        listOf(
            SystemPermissionCapability(
                id = "microphone",
                title = "Microphone & Voice Engine",
                icon = Icons.Default.Mic,
                isGranted = micGranted,
                isRequired = true,
                tag = if (micGranted) "Operational" else "Required",
                description = "Enables continuous offline wake-word detection (Alia, Alya, Seno) and live voice recognition.",
                explanation = "Alya processes audio entirely on-device for ultra-low latency and privacy. Voice data is never transmitted without explicit user action.",
                impactWhenDenied = "Voice mode and wake-word hands-free triggering will be completely disabled. You can only use typed text.",
                actionLabel = if (micGranted) "Manage Permission" else "Grant Microphone Access",
                onAction = { ctx, triggerReq ->
                    if (!micGranted) {
                        requestedPermission = Manifest.permission.RECORD_AUDIO
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    } else {
                        openAppSettings(ctx)
                    }
                }
            ),
            SystemPermissionCapability(
                id = "accessibility",
                title = "Accessibility Automation",
                icon = Icons.Default.AccessibilityNew,
                isGranted = accessibilityGranted,
                isRequired = true,
                tag = if (accessibilityGranted) "Active" else "Action Needed",
                description = "Allows Alya to answer/end incoming calls, control Wi-Fi, open apps, and execute on-screen automation commands.",
                explanation = "Android's Accessibility API provides secure OS automation hooks. Alya uses this exclusively to perform user-instructed actions when you speak commands.",
                impactWhenDenied = "Voice-activated call answering, Wi-Fi toggling in offline mode, and UI document actions will not execute automatically.",
                actionLabel = if (accessibilityGranted) "Accessibility Configured" else "Enable in Settings",
                onAction = { ctx, _ ->
                    onShowAccessibilityGuidance()
                    openAccessibilitySettings(ctx)
                }
            ),
            SystemPermissionCapability(
                id = "notifications",
                title = "Notifications & Live Status",
                icon = Icons.Default.Notifications,
                isGranted = notificationGranted,
                isRequired = true,
                tag = if (notificationGranted) "Enabled" else "Restricted",
                description = "Keeps Alya's low-power background service active and alerts you to alarms, reminders, and call statuses.",
                explanation = "Required on Android 13+ to maintain foreground service continuity so the assistant stays responsive even when your screen is locked or another app is active.",
                impactWhenDenied = "Background wake-word service may be killed by Android OS battery managers, causing missed voice triggers.",
                actionLabel = if (notificationGranted) "Notification Settings" else "Allow Notifications",
                onAction = { ctx, _ ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationGranted) {
                        requestedPermission = Manifest.permission.POST_NOTIFICATIONS
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        openNotificationSettings(ctx)
                    }
                }
            ),
            SystemPermissionCapability(
                id = "overlay",
                title = "Display Over Other Apps",
                icon = Icons.Default.Layers,
                isGranted = overlayGranted,
                isRequired = false,
                tag = if (overlayGranted) "Active" else "Recommended",
                description = "Shows the bottom listening pop-up and quick assistant overlay above any running application or game.",
                explanation = "Overlay permission lets Alya pop up seamlessly on top of navigation, YouTube, or browser windows whenever wake words are spoken.",
                impactWhenDenied = "Listening pop-up will only display inside the Alya app window rather than globally over third-party apps.",
                actionLabel = if (overlayGranted) "Overlay Allowed" else "Enable Floating Overlay",
                onAction = { ctx, _ ->
                    openOverlaySettings(ctx)
                }
            )
        )
    }

    val grantedCount = capabilities.count { it.isGranted }
    val totalCount = capabilities.size
    val progress = grantedCount.toFloat() / totalCount.toFloat()
    val allRequiredGranted = capabilities.filter { it.isRequired }.all { it.isGranted }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("permission_capabilities_dashboard")
    ) {
        // Summary Header Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (allRequiredGranted) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                } else {
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                }
            ),
            border = BorderStroke(
                width = 1.dp,
                color = if (allRequiredGranted) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                } else {
                    MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
                }
            )
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    if (allRequiredGranted) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                    else MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (allRequiredGranted) Icons.Default.CheckCircle else Icons.Default.Security,
                                contentDescription = null,
                                tint = if (allRequiredGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "System Permissions & Capabilities",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "$grantedCount of $totalCount system capabilities granted",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = { refreshKey++ },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("refresh_capabilities_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Status",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Progress Bar
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = if (allRequiredGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                if (!allRequiredGranted) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Grant required permissions below to unlock full hands-free voice automation.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Capability Cards List
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            capabilities.forEach { capability ->
                CapabilityCard(
                    capability = capability,
                    onTriggerAction = {
                        capability.onAction(context) { refreshKey++ }
                    }
                )
            }
        }
    }
}

/**
 * Detailed individual capability card with expandable explanation and direct intent button.
 */
@Composable
fun CapabilityCard(
    capability: SystemPermissionCapability,
    onTriggerAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(!capability.isGranted && capability.isRequired) }

    val statusColor by animateColorAsState(
        targetValue = if (capability.isGranted) Color(0xFF10B981) else if (capability.isRequired) Color(0xFFEF4444) else Color(0xFFF59E0B),
        label = "status_color"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .testTag("capability_card_${capability.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (capability.isGranted) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            else statusColor.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Row: Icon, Title, Status Badge, Expand/Collapse
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Capability Icon
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(statusColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = capability.icon,
                        contentDescription = capability.title,
                        tint = statusColor,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                // Title & Brief Description
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = capability.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (capability.isRequired) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                            ) {
                                Text(
                                    text = "REQUIRED",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = capability.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (isExpanded) 4 else 1
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Status Pill
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statusColor.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = capability.tag,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = statusColor,
                            fontSize = 11.sp
                        )
                    }
                }

                IconButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Expandable details section
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp)
                ) {
                    // Explanation Box
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Why this is needed:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = capability.explanation,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (!capability.isGranted) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "If denied:",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = capability.impactWhenDenied,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.9f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Action Button triggering Intent
                    if (!capability.isGranted) {
                        Button(
                            onClick = onTriggerAction,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("action_button_${capability.id}"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (capability.isRequired) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                            )
                        ) {
                            Text(
                                text = capability.actionLabel,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else {
                        OutlinedButton(
                            onClick = onTriggerAction,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .testTag("settings_button_${capability.id}"),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "Configured (Tap to open System Settings)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------------------
// Helper System Intent Launchers
// -------------------------------------------------------------------------

private fun openAppSettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        openGeneralSettings(context)
    }
}

private fun openAccessibilitySettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        openAppSettings(context)
    }
}

private fun openOverlaySettings(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            openAppSettings(context)
        }
    } else {
        openAppSettings(context)
    }
}

private fun openNotificationSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        try {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            openAppSettings(context)
        }
    } else {
        openAppSettings(context)
    }
}

private fun openGeneralSettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) { android.util.Log.e("Alya", "Exception handled", e) }
}
