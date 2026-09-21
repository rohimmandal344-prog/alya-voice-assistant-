package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * AudioRecognitionState represents the discrete lifecycle states of speech recognition and voice synthesis.
 */
enum class AudioRecognitionState {
    IDLE,
    WAKING_UP,
    LISTENING,
    RECOGNIZING,
    PROCESSING,
    SPEAKING
}

/**
 * PulsingFluidOrb (Alya Assistant AI Indicator)
 *
 * A custom Jetpack Compose animation component featuring:
 * - Fluid, morphing circular wave contours powered by multi-harmonic sinusoidal mathematics.
 * - Dynamic circular expansion sequence synchronized with audio recognition state transitions.
 * - Multi-tiered pulsing energy aura with infinite transitions and Animatable physics.
 * - Reactive acoustic audio level expansion with spring physics.
 * - Multi-stop glowing radial gradients (Vibrant Cyan, Luminous Indigo, Radiant Pearl).
 * - Renderable as a floating overlay above other applications or within app layouts.
 */
@Composable
fun PulsingFluidOrb(
    modifier: Modifier = Modifier,
    orbSize: Dp = 140.dp,
    audioLevel: Float = 0f,
    isListening: Boolean = true,
    isProcessing: Boolean = false,
    recognitionState: AudioRecognitionState? = null,
    primaryColor: Color = Color(0xFF00E5FF),     // Electric Cyan
    secondaryColor: Color = Color(0xFF7C4DFF),   // Luminous Purple/Indigo
    accentColor: Color = Color(0xFFFFFFFF),      // Pure Highlight Pearl
    deepShadowColor: Color = Color(0xFF0D1B2A),  // Deep Abyssal Core
    onClick: (() -> Unit)? = null
) {
    // Resolve computed recognition state
    val activeState = recognitionState ?: when {
        isProcessing -> AudioRecognitionState.PROCESSING
        isListening && audioLevel > 0.08f -> AudioRecognitionState.RECOGNIZING
        isListening -> AudioRecognitionState.LISTENING
        else -> AudioRecognitionState.IDLE
    }

    val infiniteTransition = rememberInfiniteTransition(label = "PulsingFluidTransition")

    // Dynamic wave phases for fluid surface contouring
    val waveDuration = when (activeState) {
        AudioRecognitionState.PROCESSING -> 1600
        AudioRecognitionState.RECOGNIZING -> 2000
        AudioRecognitionState.LISTENING -> 2800
        AudioRecognitionState.SPEAKING -> 2200
        else -> 3600
    }

    val phase1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = waveDuration, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "WavePhase1"
    )

    val phase2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = (waveDuration * 0.75f).toInt(), easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "WavePhase2"
    )

    // Breathing pulse expansion
    val breathScale by infiniteTransition.animateFloat(
        initialValue = if (activeState == AudioRecognitionState.PROCESSING) 0.90f else 0.94f,
        targetValue = if (activeState == AudioRecognitionState.PROCESSING) 1.10f else 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (activeState == AudioRecognitionState.PROCESSING) 1200 else 1800,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "BreathingPulse"
    )

    // Concentric acoustic ripples
    val rippleProgress1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RippleProgress1"
    )

    val rippleProgress2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 2000,
                delayMillis = 1000,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "RippleProgress2"
    )

    // Dynamic circular expansion sequence triggered upon state changes
    val stateExpansionSurge = remember { Animatable(1f) }
    val sequenceBloomProgress = remember { Animatable(0f) }

    LaunchedEffect(activeState) {
        sequenceBloomProgress.snapTo(0f)
        coroutineScope {
            launch {
                sequenceBloomProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing)
                )
            }
            launch {
                stateExpansionSurge.animateTo(
                    targetValue = 1.22f,
                    animationSpec = tween(durationMillis = 180, easing = FastOutLinearInEasing)
                )
                stateExpansionSurge.animateTo(
                    targetValue = 1.0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                )
            }
        }
    }

    // Reactive audio expansion
    val audioScale = remember { Animatable(1f) }
    LaunchedEffect(audioLevel, activeState) {
        val multiplier = when (activeState) {
            AudioRecognitionState.RECOGNIZING -> 0.45f
            AudioRecognitionState.LISTENING -> 0.35f
            AudioRecognitionState.SPEAKING -> 0.30f
            else -> 0.10f
        }
        val target = 1f + (audioLevel.coerceIn(0f, 1f) * multiplier)
        audioScale.animateTo(
            targetValue = target,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        )
    }

    // Dynamic state theme color resolution
    val resolvedPrimary = when (activeState) {
        AudioRecognitionState.PROCESSING -> Color(0xFFFF9100) // Vibrant Amber
        AudioRecognitionState.RECOGNIZING -> Color(0xFF00E676) // Emerald Active
        AudioRecognitionState.SPEAKING -> Color(0xFF00E5FF)    // Cyan Voice
        else -> primaryColor
    }

    val resolvedSecondary = when (activeState) {
        AudioRecognitionState.PROCESSING -> Color(0xFFFF3D00) // Deep Coral
        AudioRecognitionState.RECOGNIZING -> Color(0xFF00B0FF) // Azure Cyan
        AudioRecognitionState.SPEAKING -> Color(0xFF7C4DFF)    // Luminous Purple
        else -> secondaryColor
    }

    val cachedFluidPath = remember { Path() }
    val radialColors = remember(accentColor, resolvedPrimary, resolvedSecondary, deepShadowColor) {
        listOf(accentColor, resolvedPrimary, resolvedSecondary, deepShadowColor)
    }
    val ambientColors = remember(resolvedPrimary, resolvedSecondary) {
        listOf(resolvedPrimary.copy(alpha = 0.38f), resolvedSecondary.copy(alpha = 0.20f), Color.Transparent)
    }

    Box(
        modifier = modifier
            .size(orbSize)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick
                    )
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
    // Performance-optimized Drawing
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                // Offload scaling and rotation to GPU layer for 60-120 FPS smoothness
                val scale = breathScale * audioScale.value * stateExpansionSurge.value
                scaleX = scale
                scaleY = scale
            }
    ) {
        val canvasW = size.width
        val canvasH = size.height
        val center = Offset(canvasW / 2f, canvasH / 2f)
        val baseRadius = (minOf(canvasW, canvasH) / 2.6f)

        // 1. Dynamic Circular Expansion Sequence (Bloom shockwave rings on state transitions)
        if (sequenceBloomProgress.value in 0.01f..0.99f) {
            drawExpansionSequenceBloom(
                center = center,
                baseRadius = baseRadius,
                progress = sequenceBloomProgress.value,
                primaryColor = resolvedPrimary,
                secondaryColor = resolvedSecondary
            )
        }

        // 2. Propagating acoustic ripples
        if (activeState != AudioRecognitionState.IDLE) {
            drawAcousticRipple(
                center = center,
                baseRadius = baseRadius,
                progress = rippleProgress1,
                color = resolvedPrimary
            )
            drawAcousticRipple(
                center = center,
                baseRadius = baseRadius,
                progress = rippleProgress2,
                color = resolvedSecondary
            )
        }

        // 3. Multi-layer ambient radial aura
        drawCircle(
            brush = Brush.radialGradient(
                colors = ambientColors,
                center = center,
                radius = baseRadius * 1.75f
            ),
            radius = baseRadius * 1.75f,
            center = center
        )

        // 4. Fluid morphing circular contour
        cachedFluidPath.reset()
        val pathPoints = 80
        val angleStep = ((2.0 * PI) / pathPoints).toFloat()
        val amplitude = when (activeState) {
            AudioRecognitionState.PROCESSING -> 16f
            AudioRecognitionState.RECOGNIZING -> (10f + (audioLevel * 22f))
            AudioRecognitionState.LISTENING -> (6f + (audioLevel * 14f))
            AudioRecognitionState.SPEAKING -> 8f
            else -> 4f
        }

        for (i in 0..pathPoints) {
            val angle = (i * angleStep).toDouble()
            // Multi-harmonic sine/cosine radius modulation
            val harmonicOffset = (sin(angle * 3.0 + phase1) * amplitude).toFloat() +
                    (cos(angle * 2.0 - phase2) * (amplitude * 0.65f)).toFloat()
            val r = baseRadius + harmonicOffset
            val x = center.x + (r * cos(angle).toFloat())
            val y = center.y + (r * sin(angle).toFloat())

            if (i == 0) {
                cachedFluidPath.moveTo(x, y)
            } else {
                cachedFluidPath.lineTo(x, y)
            }
        }
        cachedFluidPath.close()

        // Fill fluid morphing core with multi-stop radial gradient
        val highlightOffset = Offset(
            center.x - (baseRadius * 0.30f),
            center.y - (baseRadius * 0.35f)
        )

        drawPath(
            path = cachedFluidPath,
            brush = Brush.radialGradient(
                colors = radialColors,
                center = highlightOffset,
                radius = baseRadius * 1.40f
            ),
            style = Fill
        )

        // 5. Luminous specular rim sheen
        drawPath(
            path = cachedFluidPath,
            brush = Brush.sweepGradient(
                colors = listOf(
                    resolvedPrimary.copy(alpha = 0.95f),
                    resolvedSecondary.copy(alpha = 0.45f),
                    accentColor.copy(alpha = 0.98f),
                    resolvedPrimary.copy(alpha = 0.35f),
                    resolvedPrimary.copy(alpha = 0.95f)
                ),
                center = center
            ),
            style = Stroke(width = 3.5f)
        )

        // 6. Specular highlight glow at top-left
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.88f),
                    Color.White.copy(alpha = 0.28f),
                    Color.Transparent
                ),
                center = highlightOffset,
                radius = baseRadius * 0.45f
            ),
            radius = baseRadius * 0.45f,
            center = highlightOffset
        )
    }
    }
}

/**
 * Draws the dynamic circular expansion sequence bloom wave when recognition state updates.
 */
private fun DrawScope.drawExpansionSequenceBloom(
    center: Offset,
    baseRadius: Float,
    progress: Float,
    primaryColor: Color,
    secondaryColor: Color
) {
    val shockwaveRadius = baseRadius + (progress * baseRadius * 1.15f)
    val alpha = ((1f - progress) * 0.75f).coerceIn(0f, 1f)

    // Inner bright ionized ring
    drawCircle(
        color = Color.White.copy(alpha = alpha * 0.9f),
        radius = shockwaveRadius * 0.88f,
        center = center,
        style = Stroke(width = 3.0f * (1f - (progress * 0.4f)))
    )

    // Outer chromatic expansion ring
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(primaryColor.copy(alpha = alpha), secondaryColor.copy(alpha = alpha * 0.5f), Color.Transparent),
            center = center,
            radius = shockwaveRadius
        ),
        radius = shockwaveRadius,
        center = center,
        style = Stroke(width = 4.5f * (1f - (progress * 0.5f)))
    )
}

private fun DrawScope.drawAcousticRipple(
    center: Offset,
    baseRadius: Float,
    progress: Float,
    color: Color
) {
    val rippleRadius = baseRadius + (progress * baseRadius * 0.80f)
    val alpha = ((1f - progress) * 0.55f).coerceIn(0f, 1f)
    drawCircle(
        color = color.copy(alpha = alpha),
        radius = rippleRadius,
        center = center,
        style = Stroke(width = 2.5f * (1f - (progress * 0.5f)))
    )
}
