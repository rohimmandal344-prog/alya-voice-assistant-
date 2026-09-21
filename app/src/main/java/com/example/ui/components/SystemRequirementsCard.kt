package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

/**
 * SystemRequirementsCard
 * 
 * Clean, production-grade System Requirements view detailing supported Android versions,
 * necessary runtime permissions, and hardware baselines.
 */
@Composable
fun SystemRequirementsCard(
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false
) {
    var isExpanded by remember { mutableStateOf(initiallyExpanded) }
    var selectedTab by remember { mutableStateOf(0) } // 0: Permissions, 1: Android Versions, 2: Hardware

    val context = LocalContext.current
    val currentSdk = Build.VERSION.SDK_INT
    val currentOsRelease = Build.VERSION.RELEASE
    val isRecommended = currentSdk >= Build.VERSION_CODES.Q // API 29+ (Android 10+)

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .testTag("system_requirements_section")
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            // Header Row: Device info & interactive toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhoneAndroid,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "System Requirements & Permissions",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Android $currentOsRelease (API $currentSdk) • ${if (isRecommended) "Fully Compatible" else "Legacy Compatible"}",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = if (isRecommended) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary
                        )
                    }
                }

                IconButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse Requirements" else "Expand Requirements",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Quick Badges
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RequirementBadge(
                    icon = Icons.Default.Android,
                    title = "Android 10–17+",
                    subtitle = "Target API 34+",
                    modifier = Modifier.weight(1f)
                )
                RequirementBadge(
                    icon = Icons.Default.Mic,
                    title = "16kHz Audio",
                    subtitle = "Low Latency HAL",
                    modifier = Modifier.weight(1f)
                )
                RequirementBadge(
                    icon = Icons.Default.Security,
                    title = "10+ Permissions",
                    subtitle = "Granular Access",
                    modifier = Modifier.weight(1f)
                )
            }

            // Expanded Breakdown Tabs & Details
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp)
                ) {
                    Divider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        thickness = 1.dp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // Segmented Filter Controls
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.primary,
                        divider = {}
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("Permissions", fontSize = 12.sp, fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal) }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text("Android Versions", fontSize = 12.sp, fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal) }
                        )
                        Tab(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            text = { Text("Hardware", fontSize = 12.sp, fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal) }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    when (selectedTab) {
                        0 -> PermissionsRequirementsList(context = context)
                        1 -> AndroidVersionsRequirementsList(currentSdk = currentSdk)
                        2 -> HardwareRequirementsList()
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Developer & Studio Identity Card
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Code,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Developer & Studio Info",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Developer:",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Rohim Mandal",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Studio (Short):",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "SBSM35G studio",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "Studio (Full Name):",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "SUPER BIND SAMSTAR MOBILE 35 GEN-Z Studio",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionsRequirementsList(context: android.content.Context) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val permissions = listOf(
            PermissionItemData(
                name = "Microphone Access",
                manifestName = "android.permission.RECORD_AUDIO",
                description = "Enables real-time voice conversations, multi-lingual recognition, and on-device wake-word detection.",
                minVersion = "Android 6.0+ (API 23)",
                isRequired = true,
                icon = Icons.Default.Mic,
                isGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            ),
            PermissionItemData(
                name = "Microphone Foreground Service",
                manifestName = "android.permission.FOREGROUND_SERVICE_MICROPHONE",
                description = "Maintains continuous background listening and hands-free calling without process termination.",
                minVersion = "Android 14+ (API 34)",
                isRequired = true,
                icon = Icons.Default.GraphicEq,
                isGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ContextCompat.checkSelfPermission(context, "android.permission.FOREGROUND_SERVICE_MICROPHONE") == PackageManager.PERMISSION_GRANTED
                } else true
            ),
            PermissionItemData(
                name = "Post Notifications",
                manifestName = "android.permission.POST_NOTIFICATIONS",
                description = "Shows live assistant status, quick mic toggle actions, and background voice session indicators.",
                minVersion = "Android 13+ (API 33)",
                isRequired = false,
                icon = Icons.Default.Notifications,
                isGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                } else true
            ),
            PermissionItemData(
                name = "Display Over Other Apps (Overlay)",
                manifestName = "android.permission.SYSTEM_ALERT_WINDOW",
                description = "Displays the floating Siri-style animated listening popup and glowing voice orb over any screen.",
                minVersion = "Android 6.0+ (API 23)",
                isRequired = false,
                icon = Icons.Default.Layers,
                isGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    android.provider.Settings.canDrawOverlays(context)
                } else true
            ),
            PermissionItemData(
                name = "Phone Calls & State",
                manifestName = "android.permission.CALL_PHONE, READ_PHONE_STATE",
                description = "Allows voice-commanded dialing, incoming call pickup, and hands-free call termination.",
                minVersion = "Android 6.0+ (API 23)",
                isRequired = false,
                icon = Icons.Default.Phone,
                isGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
            ),
            PermissionItemData(
                name = "Contacts Access",
                manifestName = "android.permission.READ_CONTACTS",
                description = "Allows voice search for stored contacts (e.g., 'Call Mom', 'Find John').",
                minVersion = "Android 6.0+ (API 23)",
                isRequired = false,
                icon = Icons.Default.AccountBox,
                isGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
            ),
            PermissionItemData(
                name = "Precise Location",
                manifestName = "android.permission.ACCESS_FINE_LOCATION",
                description = "Provides real-time hyperlocal weather reports and navigation assistance.",
                minVersion = "Android 6.0+ (API 23)",
                isRequired = false,
                icon = Icons.Default.LocationOn,
                isGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            ),
            PermissionItemData(
                name = "Accessibility Service",
                manifestName = "android.permission.BIND_ACCESSIBILITY_SERVICE",
                description = "Enables automated UI scrolling, app launching, clicking controls, and hands-free device automation.",
                minVersion = "Android 8.0+ (API 26)",
                isRequired = false,
                icon = Icons.Default.TouchApp,
                isGranted = com.example.service.AlyaAccessibilityService.checkAccessibilityPermission(context) == com.example.service.AccessibilityPermissionStatus.GRANTED
            ),
            PermissionItemData(
                name = "Ignore Battery Optimizations",
                manifestName = "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS",
                description = "Prevents aggressive OEM battery killers from pausing 24/7 low-power wake-word standby.",
                minVersion = "Android 6.0+ (API 23)",
                isRequired = false,
                icon = Icons.Default.BatteryChargingFull,
                isGranted = false
            ),
            PermissionItemData(
                name = "Camera (Torch)",
                manifestName = "android.permission.CAMERA",
                description = "Controls phone flashlight via voice commands ('turn on flashlight').",
                minVersion = "Android 6.0+ (API 23)",
                isRequired = false,
                icon = Icons.Default.FlashlightOn,
                isGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            ),
            PermissionItemData(
                name = "Special All Files & Document Access",
                manifestName = "android.permission.MANAGE_EXTERNAL_STORAGE",
                description = "Allows Alya to organize, create, and read documents and files via voice commands.",
                minVersion = "Android 11.0+ (API 30)",
                isRequired = false,
                icon = Icons.Default.Layers,
                isGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) android.os.Environment.isExternalStorageManager() else true
            ),
            PermissionItemData(
                name = "Media & Photos Access",
                manifestName = "android.permission.READ_MEDIA_IMAGES",
                description = "Accesses photos and media library for voice searching and sharing.",
                minVersion = "Android 13.0+ (API 33)",
                isRequired = false,
                icon = Icons.Default.Info,
                isGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
                } else {
                    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                }
            )
        )

        permissions.forEach { item ->
            PermissionItemRow(item)
        }
    }
}

private data class PermissionItemData(
    val name: String,
    val manifestName: String,
    val description: String,
    val minVersion: String,
    val isRequired: Boolean,
    val icon: ImageVector,
    val isGranted: Boolean
)

@Composable
private fun PermissionItemRow(item: PermissionItemData) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        if (item.isGranted) Color(0xFF10B981).copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.primaryContainer,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = if (item.isGranted) Color(0xFF10B981) else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (item.isGranted) Color(0xFFE8F5E9) else if (item.isRequired) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = if (item.isGranted) "Granted" else if (item.isRequired) "Required" else "Optional",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (item.isGranted) Color(0xFF1B5E20) else if (item.isRequired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Requires: ${item.minVersion}",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
private fun AndroidVersionsRequirementsList(currentSdk: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val versions = listOf(
            VersionItemData(
                versionName = "Android 15, 16 & 17+ (API 35–36+)",
                supportLevel = "Next-Gen Optimized",
                badgeColor = Color(0xFF10B981),
                features = listOf(
                    "Predictive back animations & edge-to-edge system insets",
                    "16KB page-size alignment for high performance audio engines",
                    "Strict background activity start safeguards & isolated microphone channels"
                ),
                isCurrent = currentSdk >= 35
            ),
            VersionItemData(
                versionName = "Android 14 (API 34) Upside Down Cake",
                supportLevel = "Fully Supported & Recommended",
                badgeColor = Color(0xFF10B981),
                features = listOf(
                    "Mandatory foregroundServiceType='microphone' integration",
                    "Granular photo picker & media permissions",
                    "Enhanced battery optimization compliance"
                ),
                isCurrent = currentSdk == 34
            ),
            VersionItemData(
                versionName = "Android 13 (API 33) Tiramisu",
                supportLevel = "Fully Supported",
                badgeColor = Color(0xFF10B981),
                features = listOf(
                    "POST_NOTIFICATIONS runtime permission model",
                    "Per-app multilingual speech & TTS preferences",
                    "Modern spatial audio focus & routing"
                ),
                isCurrent = currentSdk == 33
            ),
            VersionItemData(
                versionName = "Android 10, 11 & 12 (API 29–32)",
                supportLevel = "Supported (Baseline Target)",
                badgeColor = Color(0xFF3B82F6),
                features = listOf(
                    "Scoped storage and SAF document creation",
                    "Package visibility (<queries>) for installed app launching",
                    "Bluetooth LE & fast audio HAL connectivity"
                ),
                isCurrent = currentSdk in 29..32
            ),
            VersionItemData(
                versionName = "Android 8.0 & 9.0 (API 26–28) Oreo / Pie",
                supportLevel = "Legacy Compatibility",
                badgeColor = Color(0xFFF59E0B),
                features = listOf(
                    "Minimum supported operating system threshold",
                    "Notification channels & basic background services",
                    "Local Room offline database persistence"
                ),
                isCurrent = currentSdk in 26..28
            )
        )

        versions.forEach { v ->
            VersionItemRow(v)
        }
    }
}

private data class VersionItemData(
    val versionName: String,
    val supportLevel: String,
    val badgeColor: Color,
    val features: List<String>,
    val isCurrent: Boolean
)

@Composable
private fun VersionItemRow(item: VersionItemData) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            if (item.isCurrent) 1.5.dp else 1.dp,
            if (item.isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.versionName,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = item.badgeColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = if (item.isCurrent) "Your Device" else item.supportLevel,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (item.isCurrent) MaterialTheme.colorScheme.primary else item.badgeColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            item.features.forEach { feat ->
                Row(
                    modifier = Modifier.padding(vertical = 1.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = feat,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun HardwareRequirementsList() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        RequirementDetailItem(
            icon = Icons.Default.Speed,
            title = "Processor & NPU",
            minimum = "64-bit ARMv8-A or x86_64 Dual-Core @ 1.5 GHz",
            recommended = "Octa-Core processor with NEON SIMD vector extensions for instant TF Lite wake-word vectorization"
        )
        RequirementDetailItem(
            icon = Icons.Default.Memory,
            title = "RAM & Storage",
            minimum = "2.0 GB Total RAM • 60 MB Free Storage",
            recommended = "3.0 GB+ RAM for zero-swapping multi-lingual neural speech synthesis and live background buffering"
        )
        RequirementDetailItem(
            icon = Icons.Default.Mic,
            title = "Acoustic Subsystem",
            minimum = "Standard 16-bit Mono 16,000 Hz PCM hardware input",
            recommended = "Hardware Noise Suppressor (NS) & Acoustic Echo Canceler (AEC) for barge-in speech interruption"
        )
        RequirementDetailItem(
            icon = Icons.Default.Wifi,
            title = "Connectivity Architecture",
            minimum = "Offline-first Room database for apps, calls, contacts, volume & Wi-Fi toggling",
            recommended = "Active LTE / 5G / Wi-Fi connection for Google Gemini generative AI conversations & live search"
        )
    }
}

@Composable
private fun RequirementBadge(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RequirementDetailItem(
    icon: ImageVector,
    title: String,
    minimum: String,
    recommended: String
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Minimum: $minimum",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Recommended: $recommended",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
