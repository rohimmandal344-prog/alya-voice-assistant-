package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.util.SessionState

/**
 * SessionStatusDashboard
 * 
 * Displays real-time status indicators for current SessionManager state,
 * ensuring the UI accurately reflects microphone activation, hardware audio focus,
 * and network availability without using fake states.
 */
@Composable
fun SessionStatusDashboard(
    sessionState: SessionState,
    isMicActive: Boolean,
    isAudioFocusHeld: Boolean,
    isNetworkAvailable: Boolean,
    isBatterySaverActive: Boolean,
    powerState: com.example.power.DevicePowerState = com.example.power.DevicePowerState.ACTIVE,
    isHardwareReleased: Boolean = false,
    onToggleBatterySaver: (Boolean) -> Unit,
    onReleaseSession: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .testTag("session_manager_status_dashboard")
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Session lifecycle badge
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val stateColor by animateColorAsState(
                        targetValue = when (sessionState) {
                            SessionState.IDLE -> MaterialTheme.colorScheme.outline
                            SessionState.WAKE_DETECTED -> Color(0xFFFFB300)
                            SessionState.LISTENING -> Color(0xFF4CAF50)
                            SessionState.PROCESSING -> Color(0xFF2196F3)
                            SessionState.RESPONDING -> Color(0xFF9C27B0)
                            SessionState.SPEAKING -> Color(0xFF00BCD4)
                            SessionState.STOPPED -> Color(0xFFF44336)
                        },
                        label = "sessionStateColor"
                    )

                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(stateColor, CircleShape)
                            .testTag("session_state_dot")
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when (sessionState) {
                            SessionState.IDLE -> "Session: Idle (Ready)"
                            SessionState.WAKE_DETECTED -> "Session: Wake Detected"
                            SessionState.LISTENING -> "Session: Listening"
                            SessionState.PROCESSING -> "Session: Processing"
                            SessionState.RESPONDING -> "Session: Responding"
                            SessionState.SPEAKING -> "Session: Speaking"
                            SessionState.STOPPED -> "Session: Stopped"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Battery-Saver Toggle Chip
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isBatterySaverActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onToggleBatterySaver(!isBatterySaverActive) }
                        .testTag("battery_saver_toggle_chip")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isBatterySaverActive) Icons.Default.BatteryChargingFull else Icons.Default.BatteryStd,
                            contentDescription = "Battery Saver",
                            tint = if (isBatterySaverActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isBatterySaverActive) "Battery Saver ON" else "Standard Power",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isBatterySaverActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Status indicator pills row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Microphone Real State
                StatusPill(
                    icon = if (isMicActive) Icons.Default.Mic else Icons.Default.MicOff,
                    label = if (isMicActive) "Mic Active" else "Mic Released",
                    isActive = isMicActive,
                    activeColor = Color(0xFFE91E63),
                    testTag = "mic_status_pill"
                )

                // Audio Focus State
                StatusPill(
                    icon = if (isAudioFocusHeld) Icons.Filled.VolumeUp else Icons.Default.VolumeMute,
                    label = if (isAudioFocusHeld) "Audio Priority" else "Audio Idle",
                    isActive = isAudioFocusHeld,
                    activeColor = Color(0xFF2196F3),
                    testTag = "audio_focus_pill"
                )

                // Network Availability
                StatusPill(
                    icon = if (isNetworkAvailable) Icons.Default.Wifi else Icons.Default.WifiOff,
                    label = if (isNetworkAvailable) "Online" else "Offline",
                    isActive = isNetworkAvailable,
                    activeColor = Color(0xFF4CAF50),
                    testTag = "network_status_pill"
                )

                // Power & Hardware State
                StatusPill(
                    icon = if (isHardwareReleased) Icons.Default.PowerSettingsNew else Icons.Default.BatteryChargingFull,
                    label = when (powerState) {
                        com.example.power.DevicePowerState.ACTIVE -> "Power: Active"
                        com.example.power.DevicePowerState.INACTIVE_IDLE -> "Hardware Idle (Saved)"
                        com.example.power.DevicePowerState.SCREEN_OFF_DOZE -> "Doze (Released)"
                        com.example.power.DevicePowerState.BATTERY_SAVER -> "Battery Saver"
                    },
                    isActive = !isHardwareReleased,
                    activeColor = Color(0xFFFF9800),
                    testTag = "power_status_pill"
                )

                // Quick release button if active
                if (sessionState != SessionState.IDLE && sessionState != SessionState.STOPPED) {
                    IconButton(
                        onClick = onReleaseSession,
                        modifier = Modifier
                            .size(24.dp)
                            .testTag("quick_release_session_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close session",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.StatusPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean,
    activeColor: Color,
    testTag: String
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Surface(
        color = if (isActive) activeColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .weight(1f)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isActive) activeColor.copy(alpha = if (isActive) alpha else 1f) else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isActive) activeColor else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}
