package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class CallConnectionQuality {
    CONNECTED,   // Fully connected (Subtle Green)
    UNSTABLE,    // Connection unstable / high latency / packet loss (Subtle Yellow)
    DISCONNECTED // Connection offline / dropped (Subtle Red)
}

/**
 * Small, non-intrusive status pill that turns subtle yellow when the call/network connection is unstable,
 * green when fully connected, providing immediate feedback on call quality.
 */
@Composable
fun ConnectionStatusPill(
    quality: CallConnectionQuality,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val targetBgColor = when (quality) {
        CallConnectionQuality.CONNECTED -> Color(0xFF1B382B)
        CallConnectionQuality.UNSTABLE -> Color(0xFF382E1B)
        CallConnectionQuality.DISCONNECTED -> Color(0xFF381B1B)
    }

    val targetDotColor = when (quality) {
        CallConnectionQuality.CONNECTED -> Color(0xFF4CAF50)
        CallConnectionQuality.UNSTABLE -> Color(0xFFFFC107)
        CallConnectionQuality.DISCONNECTED -> Color(0xFFE57373)
    }

    val targetTextColor = when (quality) {
        CallConnectionQuality.CONNECTED -> Color(0xFF81C784)
        CallConnectionQuality.UNSTABLE -> Color(0xFFFFD54F)
        CallConnectionQuality.DISCONNECTED -> Color(0xFFEF9A9A)
    }

    val labelText = when (quality) {
        CallConnectionQuality.CONNECTED -> "Connected"
        CallConnectionQuality.UNSTABLE -> "Unstable"
        CallConnectionQuality.DISCONNECTED -> "Offline"
    }

    val animatedBgColor by animateColorAsState(
        targetValue = targetBgColor,
        animationSpec = tween(durationMillis = 300),
        label = "pill_bg_color"
    )

    val animatedDotColor by animateColorAsState(
        targetValue = targetDotColor,
        animationSpec = tween(durationMillis = 300),
        label = "pill_dot_color"
    )

    val animatedTextColor by animateColorAsState(
        targetValue = targetTextColor,
        animationSpec = tween(durationMillis = 300),
        label = "pill_text_color"
    )

    // Gentle pulse animation for the status dot
    val infiniteTransition = rememberInfiniteTransition(label = "dot_pulse")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_alpha"
    )

    Surface(
        modifier = modifier
            .testTag("connection_status_pill")
            .clip(RoundedCornerShape(16.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        shape = RoundedCornerShape(16.dp),
        color = animatedBgColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .alpha(if (quality == CallConnectionQuality.CONNECTED) 1.0f else dotAlpha)
                    .background(animatedDotColor, CircleShape)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = labelText,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = animatedTextColor
            )
        }
    }
}
