package com.example.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlarmOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.viewmodel.AlyaViewModel

@Composable
fun AlyaWakeUpScreen(
    title: String,
    timeStr: String,
    viewModel: AlyaViewModel,
    onDismiss: () -> Unit
) {
    val authState = viewModel.authManager.authState.value
    val userName = when (authState) {
        is com.example.data.auth.AuthState.Authenticated -> authState.user.displayName.ifBlank { "User" }
        is com.example.data.auth.AuthState.GuestSession -> authState.user.displayName.ifBlank { "User" }
        else -> "User"
    }

    // Speak wake up greeting with user's name
    LaunchedEffect(Unit) {
        val greeting = if (timeStr.isNotBlank()) {
            "Alya bol rahi hoon... uth jao $userName! Subah ke $timeStr baj gaye hain, time to wake up!"
        } else {
            "Alya bol rahi hoon... uth jao $userName! Subah ho gayi hai, start your day!"
        }
        viewModel.ttsManager.speak(greeting)
    }

    // Pulse animation
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0F0E17)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            // Ambient glowing pulse behind character
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size((260 * pulseScale).dp)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                Color(0xFFFFB703).copy(alpha = 0.35f),
                                Color(0xFFFB8500).copy(alpha = 0.15f),
                                Color.Transparent
                            )
                        ),
                        CircleShape
                    )
            )

            // Center Content
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFFFB703).copy(alpha = 0.2f),
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AlarmOn,
                        contentDescription = "Alarm",
                        tint = Color(0xFFFFB703),
                        modifier = Modifier
                            .padding(12.dp)
                            .size(32.dp)
                    )
                }

                Text(
                    text = if (timeStr.isNotBlank()) timeStr else "Morning Alarm",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "☀️ GOOD MORNING!",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFFFFB703),
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Alya Avatar
                Image(
                    painter = painterResource(id = R.drawable.alya_anime_avatar_1788907978096),
                    contentDescription = "Alya Avatar",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(160.dp)
                        .clip(CircleShape)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "\"Alya bol rahi hoon... uth jao $userName! Subah ho gayi hai!\"",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Alya Assistant Wake-Up Call",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }

            // Bottom Actions (Snooze + I'm Awake + Daily Briefing)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        viewModel.ttsManager.stop()
                        onDismiss()
                        viewModel.sendMessage("Tell me today's morning briefing with weather and my schedule")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("morning_briefing_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFFB703),
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = "I'M AWAKE • MORNING BRIEFING ☀️",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    androidx.compose.material3.OutlinedButton(
                        onClick = {
                            viewModel.ttsManager.stop()
                            // Schedule snooze in 5 minutes
                            val snoozeMillis = System.currentTimeMillis() + (5 * 60 * 1000L)
                            com.example.domain.scheduler.AlyaWakeUpReceiver.scheduleWakeUp(
                                context = viewModel.context,
                                timeMillis = snoozeMillis,
                                title = "Snoozed Wake-Up Alarm",
                                timeStr = "5 Mins"
                            )
                            onDismiss()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("snooze_alarm_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Text("Snooze 5 Mins ⏰", fontWeight = FontWeight.SemiBold)
                    }

                    androidx.compose.material3.OutlinedButton(
                        onClick = {
                            viewModel.ttsManager.stop()
                            onDismiss()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("dismiss_wakeup_alarm_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF8585))
                    ) {
                        Text("Dismiss ✕", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
