package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.sin

/**
 * Ultra-smooth, 120fps hardware-accelerated Voice Visualizer Orb.
 * Renders glows, pulsing core, and dynamic audio equalizer bars in a single Canvas pass.
 * Uses draw-phase reading for zero recomposition overhead.
 */
@Composable
fun VoiceOrb(
    isListening: Boolean,
    isSpeaking: Boolean,
    isThinking: Boolean,
    rmsDb: Float = 0f,
    rmsProvider: (() -> Float)? = null,
    size: Dp = 180.dp,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "VoiceOrbTransition")

    // Single unified phase timer for silky smooth continuous animation
    val animPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.283185f, // 2 * PI
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "animPhase"
    )

    val breathingPulse by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathingPulse"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val surfaceColor = MaterialTheme.colorScheme.surface

    val rimGradientColors = androidx.compose.runtime.remember(primaryColor, secondaryColor, tertiaryColor) {
        listOf(primaryColor, secondaryColor, tertiaryColor, primaryColor)
    }

    val ambientGlowColors = androidx.compose.runtime.remember(primaryColor, tertiaryColor, isListening) {
        listOf(
            primaryColor.copy(alpha = if (isListening) 0.28f else 0.16f),
            tertiaryColor.copy(alpha = 0.08f),
            Color.Transparent
        )
    }

    val rippleStroke = androidx.compose.runtime.remember { Stroke(width = 2.5f) }
    val effectiveRmsProvider = rmsProvider ?: { rmsDb }

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                val currentDb = effectiveRmsProvider()
                val rmsFactor = if (isListening) ((currentDb.coerceIn(-2f, 10f) + 2f) / 12f) else 0f
                val scale = if (isListening) 1f + (rmsFactor * 0.2f) else breathingPulse
                scaleX = scale
                scaleY = scale
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val minDim = this.size.minDimension
            val outerRadius = minDim / 2f

            val currentDb = effectiveRmsProvider()
            val rmsFactor = if (isListening) ((currentDb.coerceIn(-2f, 10f) + 2f) / 12f) else 0f

            // 1. Soft Ambient Radial Glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = ambientGlowColors,
                    center = center,
                    radius = outerRadius
                ),
                radius = outerRadius,
                center = center
            )

            // 2. Dynamic Expanding Ripple Ring
            if (isListening || isSpeaking) {
                val rippleRadius = (outerRadius * 0.78f) + (rmsFactor * 14f)
                drawCircle(
                    color = tertiaryColor.copy(alpha = 0.35f),
                    radius = rippleRadius,
                    center = center,
                    style = rippleStroke
                )
            }

            // 3. Glowing Outer Gradient Rim
            val rimRadius = outerRadius * 0.72f
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = rimGradientColors,
                    center = center
                ),
                radius = rimRadius,
                center = center
            )

            // 4. Inner Core Circle
            val coreRadius = rimRadius - 4.dp.toPx()
            drawCircle(
                color = surfaceColor,
                radius = coreRadius,
                center = center
            )

            // 5. Hardware-rendered Sound Equalizer Bars
            val barCount = 5
            val barWidth = 5.dp.toPx()
            val barSpacing = 6.dp.toPx()
            val totalBarsWidth = (barCount * barWidth) + ((barCount - 1) * barSpacing)
            val startX = center.x - (totalBarsWidth / 2f)

            val activeMultiplier = if (isListening) {
                1f + (rmsFactor * 1.0f)
            } else if (isSpeaking || isThinking) {
                1f
            } else {
                0.35f
            }

            for (i in 0 until barCount) {
                // Harmonic sine phase variation for natural fluid waves
                val phaseOffset = i * 1.15f
                val sineWave = (sin(animPhase * (1.2f + (i * 0.15f)) + phaseOffset) + 1f) / 2f
                val minBarH = 8.dp.toPx()
                val maxBarH = (22.dp.toPx() + (i % 3 * 10.dp.toPx())) * activeMultiplier
                val barHeight = (minBarH + (sineWave * (maxBarH - minBarH))).coerceIn(minBarH, 44.dp.toPx())

                val barLeft = startX + i * (barWidth + barSpacing)
                val barTop = center.y - (barHeight / 2f)

                drawRoundRect(
                    color = primaryColor,
                    topLeft = Offset(barLeft, barTop),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                )
            }
        }
    }
}
