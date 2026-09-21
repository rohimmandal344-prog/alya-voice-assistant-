package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * OverlayPermissionHandler (Alya v3.0.0)
 * 
 * Provides safe verification and intent dispatching for SYSTEM_ALERT_WINDOW (Floating Window Overlay).
 * Features graceful fallback to bottom-sheet or system notification alerts if overlay permission is denied or restricted.
 */
object OverlayPermissionHandler {

    private const val TAG = "OverlayPermissionHandler"

    /**
     * Checks if app has permission to draw system overlays over other apps.
     */
    fun canDrawOverlays(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    /**
     * Safely dispatches Intent to System Overlay Settings page.
     */
    fun requestOverlayPermission(context: Context): Boolean {
        if (canDrawOverlays(context)) return true

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                true
            } else {
                true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to open package overlay settings, falling back to general settings: ${e.message}")
            try {
                val intent = Intent(Settings.ACTION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                true
            } catch (err: Exception) {
                Log.e(TAG, "Could not open Settings activity: ${err.message}")
                false
            }
        }
    }
}

/**
 * OverlayPermissionFallback (Jetpack Compose UI)
 * 
 * Renders an interactive fallback card or dialog when floating overlay permission is restricted or denied.
 * Allows user to fallback gracefully to In-App Assistant Mode or Notification Controls without app crashes or locks.
 */
@Composable
fun OverlayPermissionFallbackCard(
    onOpenSettings: () -> Unit,
    onUseInAppFallback: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Layers,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = "Floating Assistant Window",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = "Draw over other apps (SYSTEM_ALERT_WINDOW) is currently disabled. Enable overlay permission for floating chat head access, or use in-app mode.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onUseInAppFallback,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("use_in_app_assistant_fallback")
                ) {
                    Icon(imageVector = Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("In-App Mode", maxLines = 1)
                }

                Button(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("enable_floating_overlay_permission")
                ) {
                    Icon(imageVector = Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Enable Overlay", maxLines = 1)
                }
            }
        }
    }
}
