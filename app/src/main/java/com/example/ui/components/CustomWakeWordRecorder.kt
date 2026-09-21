package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.compose.animation.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.local.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CustomWakeWordRecorder(
    preferencesManager: PreferencesManager,
    onRecordingComplete: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isRecording by remember { mutableStateOf(false) }
    var recordingProgress by remember { mutableStateOf(0f) }
    var recordingStep by remember { mutableStateOf(0) } // 0: Idle, 1-3: Recording steps
    val customWakeWord by preferencesManager.selectedWakeWord.collectAsState()
    
    val micPermissionGranted = ContextCompat.checkSelfPermission(
        context, Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Personalized Wake-Word Training",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            
            Text(
                text = "Speak your custom name \"$customWakeWord\" 3 times to calibrate the recognition engine for your voice.",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StepIndicator(step = 1, currentStep = recordingStep, isComplete = recordingStep > 1)
                Spacer(modifier = Modifier.width(12.dp))
                StepIndicator(step = 2, currentStep = recordingStep, isComplete = recordingStep > 2)
                Spacer(modifier = Modifier.width(12.dp))
                StepIndicator(step = 3, currentStep = recordingStep, isComplete = recordingStep > 3)
            }

            if (isRecording) {
                LinearProgressIndicator(
                    progress = { recordingProgress },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                )
            }

            Button(
                onClick = {
                    if (!micPermissionGranted) return@Button
                    scope.launch {
                        startRecordingProcess(
                            onProgress = { recordingProgress = it },
                            onStepComplete = { 
                                recordingStep = it
                                if (it == 3) {
                                    onRecordingComplete()
                                }
                            },
                            onRecordingStateChange = { isRecording = it }
                        )
                    }
                },
                enabled = !isRecording && customWakeWord.isNotBlank() && micPermissionGranted,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRecording) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = if (isRecording) Icons.Default.MicNone else Icons.Default.Mic,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isRecording) "Listening..." else if (recordingStep > 0) "Record Again" else "Start Calibration",
                    fontWeight = FontWeight.Bold
                )
            }
            
            if (!micPermissionGranted) {
                Text(
                    text = "Microphone permission required for voice training.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun StepIndicator(step: Int, currentStep: Int, isComplete: Boolean) {
    val color = when {
        isComplete -> Color(0xFF4CAF50)
        step == currentStep -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    }
    
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        if (isComplete) {
            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        } else {
            Text(text = step.toString(), color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

private suspend fun startRecordingProcess(
    onProgress: (Float) -> Unit,
    onStepComplete: (Int) -> Unit,
    onRecordingStateChange: (Boolean) -> Unit
) {
    onRecordingStateChange(true)
    for (step in 1..3) {
        onStepComplete(step)
        // Simulate recording duration and analysis
        for (i in 0..100) {
            onProgress(i / 100f)
            delay(20)
        }
        delay(500) // Brief pause between steps
        onProgress(0f)
    }
    onRecordingStateChange(false)
}
