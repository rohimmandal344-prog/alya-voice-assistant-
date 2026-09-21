package com.example.ui.components

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.with
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

data class OnboardingStep(
    val title: String,
    val subtitle: String,
    val description: String,
    val icon: ImageVector,
    val accentColor: Color,
    val highlights: List<Pair<ImageVector, String>>
)

object OnboardingPreferences {
    private const val PREF_NAME = "alya_onboarding_prefs"
    private const val KEY_COMPLETED = "has_completed_onboarding"

    fun isCompleted(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_COMPLETED, false)
    }

    fun setCompleted(context: Context, completed: Boolean = true) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_COMPLETED, completed).apply()
    }
}

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun OnboardingWalkthroughModal(
    onDismiss: () -> Unit,
    onRequestPermissions: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var currentStepIndex by remember { mutableStateOf(0) }

    val steps = remember {
        listOf(
            OnboardingStep(
                title = "Welcome to Alya",
                subtitle = "Your Real-Time Android Voice Assistant",
                description = "Alya understands natural spoken and written commands to control your device effortlessly both online and offline.",
                icon = Icons.Default.RecordVoiceOver,
                accentColor = Color(0xFF6366F1),
                highlights = listOf(
                    Icons.Default.GraphicEq to "Wake Words: \"Alia\", \"Alya\", or \"Seno\"",
                    Icons.Default.WifiOff to "100% Offline Resilience & Local Fallbacks",
                    Icons.Default.Hearing to "Continuous Real-Time Voice Conversation"
                )
            ),
            OnboardingStep(
                title = "Full Device Control",
                subtitle = "Hands-Free Natural Language Execution",
                description = "Speak standard phrases to perform system navigation, take screenshots, adjust hardware, or execute routines.",
                icon = Icons.Default.Smartphone,
                accentColor = Color(0xFF10B981),
                highlights = listOf(
                    Icons.Default.CameraAlt to "\"Take a screenshot\" or \"Lock screen\"",
                    Icons.Default.Wifi to "\"Turn on Wi-Fi\" or \"Set volume to 80%\"",
                    Icons.Default.Call to "\"Answer call\" or \"Call John\""
                )
            ),
            OnboardingStep(
                title = "Permissions & Trust",
                subtitle = "Transparent & Privacy-First Security",
                description = "Alya strictly requests Android permissions to carry out your explicit voice commands — never for background spying.",
                icon = Icons.Default.Security,
                accentColor = Color(0xFF8B5CF6),
                highlights = listOf(
                    Icons.Default.Mic to "Microphone — Active only during voice listening",
                    Icons.Default.AccessibilityNew to "Accessibility — Enables tap, scroll & screen capture",
                    Icons.Default.Notifications to "Notifications — Displays floating orb & active service"
                )
            ),
            OnboardingStep(
                title = "Ready for Action",
                subtitle = "Start Controlling Your Device",
                description = "You're all set! Tap below to finish setup, grant essential permissions, and start speaking with Alya.",
                icon = Icons.Default.CheckCircle,
                accentColor = Color(0xFFEC4899),
                highlights = listOf(
                    Icons.Default.FlashOn to "Instant Wake Word Detection Ready",
                    Icons.Filled.HelpOutline to "Tap Help anytime to see command ideas",
                    Icons.Default.Lock to "Local Encrypted Database Protection"
                )
            )
        )
    }

    val currentStep = steps[currentStepIndex]

    Dialog(
        onDismissRequest = {
            OnboardingPreferences.setCompleted(context, true)
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .testTag("onboarding_walkthrough_modal"),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 12.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // Header Bar (Step counter & Skip button)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Step ${currentStepIndex + 1} of ${steps.size}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = currentStep.accentColor
                    )

                    TextButton(
                        onClick = {
                            OnboardingPreferences.setCompleted(context, true)
                            onDismiss()
                        },
                        modifier = Modifier.testTag("skip_onboarding_button")
                    ) {
                        Text("Skip", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // Step Visual Icon Badge
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(currentStep.accentColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = currentStep.icon,
                        contentDescription = null,
                        tint = currentStep.accentColor,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Step Title & Subtitle with Animated Transition
                AnimatedContent(
                    targetState = currentStep,
                    transitionSpec = { fadeIn() with fadeOut() },
                    label = "step_content"
                ) { step ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = step.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = step.subtitle,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = step.accentColor,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = step.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )
                    }
                }

                // Highlight Card Items
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        currentStep.highlights.forEach { (icon, text) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(currentStep.accentColor.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = currentStep.accentColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Text(
                                    text = text,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                // Step Dots Indicator
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    steps.indices.forEach { index ->
                        val isSelected = index == currentStepIndex
                        Box(
                            modifier = Modifier
                                .height(8.dp)
                                .width(if (isSelected) 24.dp else 8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) currentStep.accentColor
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                                )
                        )
                    }
                }

                // Navigation Buttons Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentStepIndex > 0) {
                        OutlinedButton(
                            onClick = { currentStepIndex-- },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Back")
                        }
                    } else {
                        Spacer(modifier = Modifier.width(80.dp))
                    }

                    if (currentStepIndex < steps.size - 1) {
                        Button(
                            onClick = { currentStepIndex++ },
                            colors = ButtonDefaults.buttonColors(containerColor = currentStep.accentColor),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("next_onboarding_step_button")
                        ) {
                            Text("Next")
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    } else {
                        Button(
                            onClick = {
                                OnboardingPreferences.setCompleted(context, true)
                                onRequestPermissions?.invoke()
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = currentStep.accentColor),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("finish_onboarding_button")
                        ) {
                            Text("Get Started")
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}
