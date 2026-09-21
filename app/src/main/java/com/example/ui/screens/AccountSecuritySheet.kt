package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoDelete
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EnhancedEncryption
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.auth.AuthManager
import com.example.data.auth.UserProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSecuritySheet(
    authManager: AuthManager,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentUser by authManager.currentUser.collectAsState()
    val user = currentUser ?: UserProfile(displayName = "Guest", isGuest = true)

    var appLockEnabled by remember { mutableStateOf(user.isAppLockEnabled) }
    var localEncryptionEnabled by remember { mutableStateOf(user.isLocalEncryptionEnabled) }
    var autoDeleteDays by remember { mutableStateOf(user.autoDeleteHistoryDays) }
    var isChangingPin by remember { mutableStateOf(false) }
    var newPinInput by remember { mutableStateOf("") }
    var saveFeedback by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Account & Privacy Vault",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Manage Security, Privacy & App Lock",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            IconButton(
                onClick = onClose,
                modifier = Modifier.testTag("close_account_security_button")
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Profile Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (user.isGuest) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    } else {
                        Image(
                            painter = painterResource(id = R.drawable.alya_anime_avatar_1788907978096),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = user.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (user.isGuest) "Guest Session (Local Privacy Sandbox)" else user.email.ifBlank { "Personal AI Account" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        color = if (user.isCloudLinked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = if (user.isCloudLinked) "✓ Cloud Sync Authenticated" else "🔒 Local Vault Protected",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (user.isCloudLinked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Security & App Lock Section
        Text(
            text = "SECURITY & ACCESS LOCK",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // App Lock Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("App Lock Protection", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text("Require 4-digit PIN when opening Alya", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                    Switch(
                        checked = appLockEnabled,
                        onCheckedChange = {
                            appLockEnabled = it
                            if (it && user.pinHash.isBlank()) {
                                isChangingPin = true
                            } else {
                                authManager.updateSecuritySettings(
                                    enableAppLock = it,
                                    enableLocalEncryption = localEncryptionEnabled,
                                    autoDeleteDays = autoDeleteDays
                                )
                                saveFeedback = if (it) "App Lock enabled!" else "App Lock disabled."
                            }
                        },
                        modifier = Modifier.testTag("app_lock_switch")
                    )
                }

                // Change PIN Action
                AnimatedVisibility(visible = appLockEnabled || isChangingPin) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Divider()
                        if (!isChangingPin) {
                            TextButton(
                                onClick = { isChangingPin = true },
                                modifier = Modifier.testTag("change_pin_button")
                            ) {
                                Text("Change Security PIN")
                            }
                        } else {
                            OutlinedTextField(
                                value = newPinInput,
                                onValueChange = { if (it.length <= 6) newPinInput = it },
                                label = { Text("Enter New 4-Digit PIN") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        if (newPinInput.length >= 4) {
                                            authManager.updateSecuritySettings(
                                                enableAppLock = true,
                                                newPin = newPinInput,
                                                enableLocalEncryption = localEncryptionEnabled,
                                                autoDeleteDays = autoDeleteDays
                                            )
                                            isChangingPin = false
                                            appLockEnabled = true
                                            newPinInput = ""
                                            saveFeedback = "New PIN saved successfully!"
                                        }
                                    },
                                    enabled = newPinInput.length >= 4
                                ) {
                                    Text("Save PIN")
                                }
                                TextButton(onClick = { isChangingPin = false }) {
                                    Text("Cancel")
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Privacy Vault Section
        Text(
            text = "PRIVACY & LOCAL ENCRYPTION",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Local On-Device Encryption Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.EnhancedEncryption, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("On-Device Local Encryption", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text("Encrypt memories & chat transcripts locally", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                    Switch(
                        checked = localEncryptionEnabled,
                        onCheckedChange = {
                            localEncryptionEnabled = it
                            authManager.updateSecuritySettings(
                                enableAppLock = appLockEnabled,
                                enableLocalEncryption = it,
                                autoDeleteDays = autoDeleteDays
                            )
                            saveFeedback = if (it) "Local encryption active" else "Encryption relaxed"
                        },
                        modifier = Modifier.testTag("encryption_switch")
                    )
                }

                Divider()

                // Auto Delete History
                var autoDeleteExpanded by remember { mutableStateOf(false) }
                val options = listOf(0 to "Never (Keep History)", 1 to "Auto-Delete After 24 Hours", 7 to "Auto-Delete After 7 Days", 30 to "Auto-Delete After 30 Days")
                ExposedDropdownMenuBox(
                    expanded = autoDeleteExpanded,
                    onExpandedChange = { autoDeleteExpanded = !autoDeleteExpanded }
                ) {
                    OutlinedTextField(
                        value = options.firstOrNull { it.first == autoDeleteDays }?.second ?: "Never",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Conversation Retention & Auto-Clean") },
                        leadingIcon = { Icon(Icons.Default.AutoDelete, contentDescription = null) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = autoDeleteExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = autoDeleteExpanded,
                        onDismissRequest = { autoDeleteExpanded = false }
                    ) {
                        options.forEach { (days, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    autoDeleteDays = days
                                    authManager.updateSecuritySettings(
                                        enableAppLock = appLockEnabled,
                                        enableLocalEncryption = localEncryptionEnabled,
                                        autoDeleteDays = days
                                    )
                                    autoDeleteExpanded = false
                                    saveFeedback = "Retention policy updated: $label"
                                }
                            )
                        }
                    }
                }
            }
        }

        if (saveFeedback != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "✓ $saveFeedback",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Sign Out / Switch Profile
        OutlinedButton(
            onClick = {
                authManager.signOut()
                onClose()
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("auth_sign_out_button"),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) {
            Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (user.isGuest) "Exit Guest Session" else "Sign Out / Switch Account")
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}
