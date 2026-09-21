package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random

data class NaturalCommandItem(
    val id: String,
    val phrase: String,
    val category: String,
    val description: String,
    val apiRequired: String,
    val icon: ImageVector,
    val accentColor: Color,
    val isOfflineSupported: Boolean = true
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NaturalLanguageHelpComponent(
    onExecuteCommand: (String) -> Unit,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null
) {
    val clipboardManager = LocalClipboardManager.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var copiedToastText by remember { mutableStateOf<String?>(null) }
    var shuffleSeed by remember { mutableStateOf(0) }

    val allCommands = remember {
        listOf(
            NaturalCommandItem(
                id = "sys_screenshot",
                phrase = "Take a screenshot",
                category = "System & Screen",
                description = "Captures current device screen instantly via Accessibility Service",
                apiRequired = "Accessibility",
                icon = Icons.Default.CameraAlt,
                accentColor = Color(0xFF3B82F6)
            ),
            NaturalCommandItem(
                id = "sys_lock",
                phrase = "Lock screen",
                category = "System & Screen",
                description = "Puts screen to sleep and locks device safely",
                apiRequired = "Accessibility",
                icon = Icons.Default.Lock,
                accentColor = Color(0xFF3B82F6)
            ),
            NaturalCommandItem(
                id = "sys_recents",
                phrase = "Open recents",
                category = "System & Screen",
                description = "Displays the app switcher / recent tasks window",
                apiRequired = "Accessibility",
                icon = Icons.Default.FlipToFront,
                accentColor = Color(0xFF3B82F6)
            ),
            NaturalCommandItem(
                id = "sys_notif",
                phrase = "Open notifications",
                category = "System & Screen",
                description = "Pulls down system notification shade hands-free",
                apiRequired = "Accessibility",
                icon = Icons.Default.Notifications,
                accentColor = Color(0xFF3B82F6)
            ),
            NaturalCommandItem(
                id = "sys_home",
                phrase = "Go home",
                category = "System & Screen",
                description = "Navigates directly back to device home screen",
                apiRequired = "Accessibility",
                icon = Icons.Default.Home,
                accentColor = Color(0xFF3B82F6)
            ),
            NaturalCommandItem(
                id = "conn_wifi_on",
                phrase = "Turn on WiFi and connect",
                category = "Connectivity",
                description = "Enables Wi-Fi adapter and auto-connects to known access points",
                apiRequired = "Wi-Fi & Settings",
                icon = Icons.Default.Wifi,
                accentColor = Color(0xFF10B981)
            ),
            NaturalCommandItem(
                id = "conn_wifi_off",
                phrase = "Turn off WiFi",
                category = "Connectivity",
                description = "Disables device Wi-Fi radio to save battery",
                apiRequired = "Wi-Fi API",
                icon = Icons.Default.WifiOff,
                accentColor = Color(0xFF10B981)
            ),
            NaturalCommandItem(
                id = "conn_vol",
                phrase = "Set volume to 80%",
                category = "Connectivity",
                description = "Adjusts media and notification volume levels dynamically",
                apiRequired = "Audio Manager",
                icon = Icons.Filled.VolumeUp,
                accentColor = Color(0xFF10B981)
            ),
            NaturalCommandItem(
                id = "conn_torch",
                phrase = "Turn on flashlight",
                category = "Connectivity",
                description = "Toggles camera hardware torch light",
                apiRequired = "Camera Manager",
                icon = Icons.Default.FlashOn,
                accentColor = Color(0xFF10B981)
            ),
            NaturalCommandItem(
                id = "call_answer",
                phrase = "Answer call",
                category = "Calls & Phone",
                description = "Picks up incoming phone call hands-free via Telecom API",
                apiRequired = "Telecom Service",
                icon = Icons.Default.Call,
                accentColor = Color(0xFF8B5CF6)
            ),
            NaturalCommandItem(
                id = "call_end",
                phrase = "End call",
                category = "Calls & Phone",
                description = "Hangs up active phone call immediately",
                apiRequired = "Telecom Service",
                icon = Icons.Default.CallEnd,
                accentColor = Color(0xFF8B5CF6)
            ),
            NaturalCommandItem(
                id = "call_john",
                phrase = "Call John",
                category = "Calls & Phone",
                description = "Dials contact 'John' directly using Contacts permission",
                apiRequired = "Telephony & Contacts",
                icon = Icons.Default.Phone,
                accentColor = Color(0xFF8B5CF6)
            ),
            NaturalCommandItem(
                id = "doc_notes",
                phrase = "Create document Notes with content meeting summary",
                category = "Documents & Routines",
                description = "Generates a local markdown/text document",
                apiRequired = "File Storage",
                icon = Icons.Default.Description,
                accentColor = Color(0xFFEC4899)
            ),
            NaturalCommandItem(
                id = "doc_routine",
                phrase = "Set routine Morning Gym at 7:00 AM",
                category = "Documents & Routines",
                description = "Schedules an automated alarm & daily task sequence",
                apiRequired = "Alarm Manager",
                icon = Icons.Default.Schedule,
                accentColor = Color(0xFFEC4899)
            ),
            NaturalCommandItem(
                id = "touch_click",
                phrase = "Click on Send",
                category = "Touch & Gestures",
                description = "Finds on-screen button with text 'Send' and performs a physical tap",
                apiRequired = "Accessibility Gesture",
                icon = Icons.Default.TouchApp,
                accentColor = Color(0xFFF59E0B)
            ),
            NaturalCommandItem(
                id = "touch_type",
                phrase = "Type Hello World",
                category = "Touch & Gestures",
                description = "Injects text string into active focused text field",
                apiRequired = "Accessibility Input",
                icon = Icons.Default.Keyboard,
                accentColor = Color(0xFFF59E0B)
            ),
            NaturalCommandItem(
                id = "touch_scroll",
                phrase = "Scroll down",
                category = "Touch & Gestures",
                description = "Performs a vertical swipe gesture on current view",
                apiRequired = "Accessibility Scroll",
                icon = Icons.Default.SwapVert,
                accentColor = Color(0xFFF59E0B)
            ),
            NaturalCommandItem(
                id = "info_weather",
                phrase = "Check weather in Tokyo",
                category = "Weather & Info",
                description = "Retrieves live weather report with temperature and forecast",
                apiRequired = "Weather API / Offline Cache",
                icon = Icons.Default.WbSunny,
                accentColor = Color(0xFF06B6D4)
            ),
            NaturalCommandItem(
                id = "info_battery",
                phrase = "What is the battery level?",
                category = "Weather & Info",
                description = "Queries device battery state, percentage, and charging status",
                apiRequired = "Battery Manager",
                icon = Icons.Default.BatteryChargingFull,
                accentColor = Color(0xFF06B6D4)
            )
        )
    }

    val categories = remember {
        listOf("All", "System & Screen", "Connectivity", "Calls & Phone", "Documents & Routines", "Touch & Gestures", "Weather & Info")
    }

    // Dynamic shuffle of featured suggestion
    val featuredCommand = remember(shuffleSeed) {
        allCommands.random(Random(shuffleSeed + System.currentTimeMillis()))
    }

    // Filter logic
    val filteredCommands = remember(searchQuery, selectedCategory, shuffleSeed) {
        allCommands.filter { item ->
            val matchesCategory = (selectedCategory == "All" || item.category == selectedCategory)
            val matchesSearch = searchQuery.isBlank() ||
                    item.phrase.contains(searchQuery, ignoreCase = true) ||
                    item.description.contains(searchQuery, ignoreCase = true) ||
                    item.category.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesSearch
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("natural_language_help_component"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Bar
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
                            imageVector = Icons.Default.RecordVoiceOver,
                            contentDescription = "Help Guide",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Natural Command Help",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Examples of spoken & text natural language controls",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = { shuffleSeed++ },
                        modifier = Modifier.size(36.dp).testTag("shuffle_help_commands_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Shuffle Examples",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (onDismiss != null) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Help",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Interactive Video Container with Sample Caption Cycle & Progress Bar Overlay
            VoiceDemoVideoContainer(
                onExecuteCommand = onExecuteCommand,
                modifier = Modifier.fillMaxWidth()
            )

            // Featured Random Example Highlight Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = featuredCommand.accentColor.copy(alpha = 0.12f),
                border = androidx.compose.foundation.BorderStroke(1.dp, featuredCommand.accentColor.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = featuredCommand.icon,
                            contentDescription = null,
                            tint = featuredCommand.accentColor,
                            modifier = Modifier.size(22.dp)
                        )
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Try Saying:",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = featuredCommand.accentColor
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = featuredCommand.accentColor.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = featuredCommand.apiRequired,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                        color = featuredCommand.accentColor
                                    )
                                }
                            }
                            Text(
                                text = "\"${featuredCommand.phrase}\"",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Button(
                        onClick = { onExecuteCommand(featuredCommand.phrase) },
                        colors = ButtonDefaults.buttonColors(containerColor = featuredCommand.accentColor),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("try_featured_command_button")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Try", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Search Filter Text Field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search natural commands (e.g. wifi, screenshot, call)...", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search", modifier = Modifier.size(18.dp))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                )
            )

            // Category Filter Scrollable Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                categories.forEach { cat ->
                    val isSelected = cat == selectedCategory
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            // Copied Toast Notification
            AnimatedVisibility(visible = copiedToastText != null, enter = fadeIn(), exit = fadeOut()) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Text(
                            text = "Copied command: \"$copiedToastText\"",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // Commands List Column
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (filteredCommands.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No natural commands matching \"$searchQuery\"",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    filteredCommands.take(6).forEach { cmd ->
                        Surface(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(cmd.phrase))
                                copiedToastText = cmd.phrase
                            },
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(cmd.accentColor.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = cmd.icon,
                                            contentDescription = null,
                                            tint = cmd.accentColor,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = "\"${cmd.phrase}\"",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = cmd.description,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = { onExecuteCommand(cmd.phrase) },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Execute",
                                            tint = cmd.accentColor,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            clipboardManager.setText(AnnotatedString(cmd.phrase))
                                            copiedToastText = cmd.phrase
                                        },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Copy",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
