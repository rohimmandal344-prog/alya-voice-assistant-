package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.tools.StructuredAction
import com.example.domain.tools.ToolDefinition
import com.example.domain.tools.ToolRegistry
import com.example.domain.tools.ToolRiskLevel

@Composable
fun ToolsCatalogSheet(
    onTestTool: (StructuredAction) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Build,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Device Tools & Actions",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Unified Android Command Registry",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            TextButton(
                onClick = onClose,
                modifier = Modifier.testTag("close_tools_sheet_button")
            ) {
                Text("Done")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(ToolRegistry.tools, key = { it.name }) { tool ->
                ToolCard(tool = tool, onTest = onTestTool)
            }
        }
    }
}

@Composable
private fun ToolCard(
    tool: ToolDefinition,
    onTest: (StructuredAction) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = tool.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                val (riskColor, riskBg) = when (tool.riskLevel) {
                    ToolRiskLevel.LOW -> Color(0xFF10B981) to Color(0xFFD1FAE5)
                    ToolRiskLevel.MEDIUM -> Color(0xFFF59E0B) to Color(0xFFFEF3C7)
                    ToolRiskLevel.HIGH -> Color(0xFFEF4444) to Color(0xFFFEE2E2)
                }

                Surface(
                    color = riskBg,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "${tool.riskLevel.name} RISK",
                        color = riskColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = tool.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (tool.requiredPermission != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "Permission",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Requires: ${tool.requiredPermission.substringAfterLast('.')}",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            FilledTonalButton(
                onClick = {
                    val defaultAction = when (tool.name) {
                        "open_app" -> StructuredAction("open_app", "open_app", mapOf("appName" to "youtube"), ToolRiskLevel.LOW)
                        "open_settings" -> StructuredAction("open_settings", "open_settings", mapOf("target" to "wifi"), ToolRiskLevel.LOW)
                        "toggle_flashlight" -> StructuredAction("toggle_flashlight", "toggle_flashlight", mapOf("state" to "toggle"), ToolRiskLevel.MEDIUM)
                        "create_timer" -> StructuredAction("create_timer", "create_timer", mapOf("seconds" to "300", "message" to "Quick 5m Timer"), ToolRiskLevel.MEDIUM)
                        "open_camera" -> StructuredAction("open_camera", "open_camera", emptyMap(), ToolRiskLevel.LOW)
                        "open_maps" -> StructuredAction("open_maps", "open_maps", mapOf("query" to "Coffee shops nearby"), ToolRiskLevel.LOW)
                        else -> StructuredAction(tool.name, tool.name, emptyMap(), tool.riskLevel)
                    }
                    onTest(defaultAction)
                },
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.align(Alignment.End)
            ) {
                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Test Action")
            }
        }
    }
}
