package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun CustomPermissionDialog(
    onGrantFiles: () -> Unit,
    onGrantAccessibility: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Permissions Required") },
        text = {
            Column {
                Text("Alya requires the following permissions to function fully:")
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onGrantFiles, modifier = Modifier.fillMaxWidth()) {
                    Text("Grant All Files Access (App Inventory)")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onGrantAccessibility, modifier = Modifier.fillMaxWidth()) {
                    Text("Grant System Interaction (Accessibility)")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
