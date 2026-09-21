package com.example.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.AlyaApplication
import com.example.MainActivity
import com.example.ui.components.AudioRecognitionState
import com.example.ui.components.PulsingFluidOrb
import com.example.util.SessionState

/**
 * High-level wake-word session state observed by the floating window.
 */
enum class WakeWordSessionState {
    IDLE,
    WAKE_DETECTED,
    LISTENING,
    PROCESSING,
    SPEAKING
}

/**
 * `WakeUpPopup`
 * 
 * Floating overlay window constructed using Android `WindowManager` and `TYPE_APPLICATION_OVERLAY`.
 * Observes `WakeWordService` / `SessionManager` session states:
 * - IDLE: Dormant, awaiting wake-words ("Alia", "Alya", "Seno")
 * - LISTENING: User speaking, active audio recording & RMS energy waveform pulses
 * - PROCESSING: Analyzing voice command / executing actions / querying AI
 * - SPEAKING: Text-to-speech feedback synthesized to speaker
 *
 * Dynamically updates fluid canvas animation state and offers inline "Send as Text" input.
 */
class WakeUpPopup private constructor(private val context: Context) {

    private val windowManager: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var composeOverlayView: ComposeView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var overlayLifecycleOwner: OverlayLifecycleOwner? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private var isExpandedState = mutableStateOf(false)

    private val autoDismissRunnable = Runnable {
        hide()
    }

    companion object {
        private const val TAG = "WakeUpPopup"

        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: WakeUpPopup? = null

        fun getInstance(context: Context): WakeUpPopup {
            return instance ?: synchronized(this) {
                instance ?: WakeUpPopup(context.applicationContext).also { instance = it }
            }
        }

        fun show(context: Context) {
            getInstance(context).show()
        }

        fun hide(context: Context) {
            getInstance(context).hide()
        }

        val isShowing: Boolean
            get() = instance?.composeOverlayView != null
    }

    /**
     * Checks whether the app has SYSTEM_ALERT_WINDOW permission.
     */
    fun canDrawOverlays(): Boolean {
        return com.example.util.OverlayPermissionHandler.canDrawOverlays(context)
    }

    private fun fallbackToInAppVoiceMode() {
        Log.w(TAG, "SYSTEM_ALERT_WINDOW permission denied or restricted. Executing graceful in-app fallback.")
        try {
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(WakeWordService.EXTRA_OPEN_VOICE_MODE, true)
            }
            context.startActivity(launchIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch in-app voice mode fallback: ${e.message}")
        }
    }

    /**
     * Attaches and displays the floating popup window via WindowManager using TYPE_APPLICATION_OVERLAY.
     * Safely handles SYSTEM_ALERT_WINDOW permission denials and window token exceptions.
     */
    fun show() {
        if (!canDrawOverlays()) {
            fallbackToInAppVoiceMode()
            return
        }

        val app = context.applicationContext as? AlyaApplication
        if (app?.sessionManager?.sessionState?.value == SessionState.IDLE || app?.sessionManager?.sessionState?.value == SessionState.STOPPED) {
            app.sessionManager.onWakeDetected()
        }

        mainHandler.post {
            try {
                if (composeOverlayView != null && composeOverlayView?.isAttachedToWindow == true) {
                    resetDismissTimer(10000L)
                    return@post
                }

                overlayLifecycleOwner = OverlayLifecycleOwner()
                val composeView = ComposeView(context).apply {
                    overlayLifecycleOwner?.attachToComposeView(this)
                    setContent {
                        WakeUpPopupContent(
                            onClose = { hide() },
                            onToggleExpand = { toggleExpansion() },
                            isExpanded = isExpandedState.value,
                            onSendCommand = { cmd -> processCommand(cmd) },
                            onOpenApp = { cmd -> openAppWithCommand(cmd) },
                            onUserInteraction = { resetDismissTimer(12000L) },
                            onDrag = { dx: Float, dy: Float -> 
                                (layoutParams as? WindowManager.LayoutParams)?.let { lp ->
                                    lp.x += dx.toInt()
                                    lp.y -= dy.toInt()
                                    try {
                                        if (composeOverlayView != null && composeOverlayView?.isAttachedToWindow == true) {
                                            windowManager.updateViewLayout(this, lp)
                                        }
                                    } catch (e: Exception) {
                                        Log.w(TAG, "Failed to update view layout on drag: ${e.message}")
                                    }
                                }
                            }
                        )
                    }
                }

                val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                }

                layoutParams = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    windowType,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                    y = 20
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    }
                }

                windowManager.addView(composeView, layoutParams)
                composeOverlayView = composeView
                Log.i(TAG, "WakeUpPopup overlay attached successfully via TYPE_APPLICATION_OVERLAY.")
                resetDismissTimer(10000L)

            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException: SYSTEM_ALERT_WINDOW permission denied by system: ${e.message}")
                composeOverlayView = null
                fallbackToInAppVoiceMode()
            } catch (e: WindowManager.BadTokenException) {
                Log.e(TAG, "BadTokenException attaching overlay view to WindowManager: ${e.message}")
                composeOverlayView = null
                fallbackToInAppVoiceMode()
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error displaying WakeUpPopup WindowManager overlay: ${e.message}", e)
                composeOverlayView = null
            }
        }
    }

    /**
     * Detaches and hides the floating popup window.
     */
    fun hide() {
        mainHandler.post {
            mainHandler.removeCallbacks(autoDismissRunnable)
            try {
                if (composeOverlayView != null && composeOverlayView?.isAttachedToWindow == true) {
                    windowManager.removeView(composeOverlayView)
                    Log.i(TAG, "WakeUpPopup overlay removed from WindowManager.")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error removing WakeUpPopup view: ${e.message}")
            } finally {
                overlayLifecycleOwner?.destroy()
                overlayLifecycleOwner = null
                composeOverlayView = null
                isExpandedState.value = false
            }
        }
    }

    private fun resetDismissTimer(durationMs: Long) {
        mainHandler.removeCallbacks(autoDismissRunnable)
        mainHandler.postDelayed(autoDismissRunnable, durationMs)
    }

    private fun toggleExpansion() {
        if (!isExpandedState.value) {
            expandTextInput()
        } else {
            collapseTextInput()
        }
    }

    private fun expandTextInput() {
        isExpandedState.value = true
        resetDismissTimer(30000L)
        layoutParams?.let { lp ->
            lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            try {
                if (composeOverlayView != null && composeOverlayView?.isAttachedToWindow == true) {
                    windowManager.updateViewLayout(composeOverlayView, lp)
                }
            } catch (e: Exception) { android.util.Log.e("Alya", "Exception handled", e) }
        }
    }

    private fun collapseTextInput() {
        isExpandedState.value = false
        resetDismissTimer(8000L)
        layoutParams?.let { lp ->
            lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            try {
                if (composeOverlayView != null && composeOverlayView?.isAttachedToWindow == true) {
                    windowManager.updateViewLayout(composeOverlayView, lp)
                }
            } catch (e: Exception) { android.util.Log.e("Alya", "Exception handled", e) }
        }
    }

    private fun processCommand(command: String) {
        if (command.isBlank()) return
        Log.i(TAG, "Processing voice command from WakeUpPopup: $command")
        val app = context.applicationContext as? AlyaApplication
        if (WakeWordService.isRunning) {
            val intent = Intent(context, WakeWordService::class.java).apply {
                action = WakeWordService.ACTION_PROCESS_VOICE_COMMAND
                putExtra("COMMAND", command)
            }
            context.startService(intent)
        } else {
            openAppWithCommand(command)
        }
        hide()
    }

    private fun openAppWithCommand(command: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("VOICE_COMMAND", command)
            putExtra("OPEN_FROM_OVERLAY", true)
        }
        context.startActivity(intent)
        hide()
    }

    @Composable
    private fun WakeUpPopupContent(
        onClose: () -> Unit,
        onToggleExpand: () -> Unit,
        isExpanded: Boolean,
        onSendCommand: (String) -> Unit,
        onOpenApp: (String) -> Unit,
        onUserInteraction: () -> Unit,
        onDrag: (Float, Float) -> Unit
    ) {
        val app = context.applicationContext as AlyaApplication
        
        // Live reactive session streams
        val sessionState by app.sessionManager.sessionState.collectAsState()
        val isListening by app.speechManager.isListening.collectAsState()
        val rmsDb by app.speechManager.rmsDb.collectAsState()
        val isSpeaking by app.ttsManager.isSpeaking.collectAsState()
        val partialResult by app.speechManager.partialResult.collectAsState()

        // Map SessionState to high-level WakeWordSessionState (IDLE, WAKE_DETECTED, LISTENING, PROCESSING, SPEAKING)
        val wakeSessionState: WakeWordSessionState = remember(sessionState, isListening, isSpeaking) {
            when {
                isSpeaking || sessionState == SessionState.SPEAKING -> WakeWordSessionState.SPEAKING
                sessionState == SessionState.PROCESSING || sessionState == SessionState.RESPONDING -> WakeWordSessionState.PROCESSING
                sessionState == SessionState.WAKE_DETECTED -> WakeWordSessionState.WAKE_DETECTED
                isListening || sessionState == SessionState.LISTENING -> WakeWordSessionState.LISTENING
                else -> WakeWordSessionState.IDLE
            }
        }

        // Map to AudioRecognitionState for fluid orb rendering
        val recognitionState: AudioRecognitionState = remember(wakeSessionState, rmsDb) {
            when (wakeSessionState) {
                WakeWordSessionState.SPEAKING -> AudioRecognitionState.SPEAKING
                WakeWordSessionState.PROCESSING -> AudioRecognitionState.PROCESSING
                WakeWordSessionState.WAKE_DETECTED -> AudioRecognitionState.RECOGNIZING
                WakeWordSessionState.LISTENING -> if (rmsDb > 8f) AudioRecognitionState.RECOGNIZING else AudioRecognitionState.LISTENING
                WakeWordSessionState.IDLE -> AudioRecognitionState.IDLE
            }
        }

        var textInput by remember { mutableStateOf("") }
        var previousState by remember { mutableStateOf<WakeWordSessionState?>(null) }

        // Final result handling
        LaunchedEffect(Unit) {
            app.speechManager.finalResultFlow.collect { result ->
                if (result.isNotBlank()) {
                    onSendCommand(result)
                }
            }
        }

        // Reset auto dismiss timer on user speech updates
        LaunchedEffect(partialResult) {
            if (partialResult.isNotBlank()) {
                onUserInteraction()
            }
        }

        // Re-bind overlay popup strictly to actual session state machine:
        // Popup ONLY appears during WAKE_DETECTED, LISTENING, PROCESSING, or SPEAKING states.
        // Auto-dismisses when session transitions back to IDLE or STOPPED.
        LaunchedEffect(wakeSessionState, isExpanded) {
            if (previousState != null && previousState != WakeWordSessionState.IDLE && wakeSessionState == WakeWordSessionState.IDLE && !isExpanded) {
                Log.i(TAG, "Session state returned to IDLE/STOPPED from $previousState. Auto-dismissing WakeUpPopup overlay.")
                onClose()
            } else {
                if (wakeSessionState != WakeWordSessionState.IDLE) {
                    previousState = wakeSessionState
                }
                onUserInteraction()
            }
        }

        // Dynamic State Colors
        val statePrimaryColor by animateColorAsState(
            targetValue = when (wakeSessionState) {
                WakeWordSessionState.WAKE_DETECTED -> Color(0xFFF59E0B) // Radiant Amber Gold
                WakeWordSessionState.LISTENING -> Color(0xFF00E5FF) // Electric Cyan
                WakeWordSessionState.PROCESSING -> Color(0xFFA855F7) // Radiant Purple
                WakeWordSessionState.SPEAKING -> Color(0xFF10B981) // Emerald Green
                WakeWordSessionState.IDLE -> Color(0xFF38BDF8) // Soft Sky Blue
            },
            animationSpec = tween(350, easing = FastOutSlowInEasing),
            label = "state_color"
        )

        // Pulsing glow border transition
        val infiniteTransition = rememberInfiniteTransition(label = "border_glow")
        val glowAlpha by infiniteTransition.animateFloat(
            initialValue = 0.4f,
            targetValue = 0.85f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "glow_alpha"
        )

        // Render floating overlay ONLY during active states: WAKE_DETECTED, LISTENING, PROCESSING, or SPEAKING (or expanded)
        if (wakeSessionState != WakeWordSessionState.IDLE || isExpanded) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .testTag("wake_up_popup_overlay")
                    .pointerInput(Unit) {
                        detectDragGestures { change: androidx.compose.ui.input.pointer.PointerInputChange, dragAmount: Offset ->
                            change.consume()
                            onDrag(dragAmount.x, dragAmount.y)
                        }
                    },
                contentAlignment = Alignment.BottomCenter
            ) {
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = Color(0xF20F172A), // Premium Dark Slate
                border = BorderStroke(1.5.dp, statePrimaryColor.copy(alpha = glowAlpha)),
                shadowElevation = 18.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    // Header Bar: Pulsing Orb, Status, Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Left: Fluid Animated Orb & Text Status
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onToggleExpand() }
                        ) {
                            // Fluid Orb Indicator
                            PulsingFluidOrb(
                                orbSize = 42.dp,
                                audioLevel = if (wakeSessionState == WakeWordSessionState.LISTENING) (rmsDb / 35f).coerceIn(0f, 1f) else 0.15f,
                                isListening = wakeSessionState == WakeWordSessionState.LISTENING,
                                isProcessing = wakeSessionState == WakeWordSessionState.PROCESSING,
                                recognitionState = recognitionState,
                                primaryColor = statePrimaryColor,
                                secondaryColor = Color(0xFF6366F1),
                                accentColor = Color.White,
                                deepShadowColor = Color(0xFF020617),
                                onClick = { onToggleExpand() }
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = when (wakeSessionState) {
                                            WakeWordSessionState.WAKE_DETECTED -> "Wake word detected!"
                                            WakeWordSessionState.LISTENING -> "Alya is listening..."
                                            WakeWordSessionState.PROCESSING -> "Thinking & processing..."
                                            WakeWordSessionState.SPEAKING -> "Alya is speaking..."
                                            WakeWordSessionState.IDLE -> "Alya Ready"
                                        },
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    // Status Badge Pill
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = statePrimaryColor.copy(alpha = 0.2f),
                                        border = BorderStroke(1.dp, statePrimaryColor.copy(alpha = 0.4f))
                                    ) {
                                        Text(
                                            text = wakeSessionState.name,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = statePrimaryColor,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Black,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(2.dp))

                                Text(
                                    text = when {
                                        partialResult.isNotBlank() -> "\"$partialResult\""
                                        wakeSessionState == WakeWordSessionState.LISTENING -> "Speak command or tap 'Send as Text'"
                                        wakeSessionState == WakeWordSessionState.PROCESSING -> "Analyzing voice action on device..."
                                        else -> "Wake words: 'Alia', 'Alya', 'Seno'"
                                    },
                                    color = if (partialResult.isNotBlank()) statePrimaryColor else Color(0xFF94A3B8),
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Right: Quick Action Controls
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // "Send as Text" Action Button
                            Surface(
                                onClick = {
                                    if (partialResult.isNotBlank()) {
                                        onOpenApp(partialResult)
                                    } else {
                                        onToggleExpand()
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = statePrimaryColor.copy(alpha = 0.2f),
                                border = BorderStroke(1.dp, statePrimaryColor.copy(alpha = 0.35f)),
                                modifier = Modifier.testTag("wake_up_send_as_text_btn")
                            ) {
                                Text(
                                    text = "Send as Text",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                            }

                            // Dismiss / Close Button
                            IconButton(
                                onClick = onClose,
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x33EF4444))
                                    .testTag("wake_up_popup_close_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = Color(0xFFF87171),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    val activeVoiceError by com.example.voice.error.VoiceErrorRegistry.instance.activeError.collectAsState()

                    activeVoiceError?.let { err ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (err.severity == com.example.voice.error.VoiceErrorSeverity.FATAL) Color(0x33EF4444) else Color(0x33F59E0B),
                            border = BorderStroke(1.dp, if (err.severity == com.example.voice.error.VoiceErrorSeverity.FATAL) Color(0xFFF87171) else Color(0xFFFBBF24)),
                            modifier = Modifier.fillMaxWidth().clickable { com.example.voice.error.VoiceErrorRegistry.instance.clearActiveError() }
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Error",
                                    tint = if (err.severity == com.example.voice.error.VoiceErrorSeverity.FATAL) Color(0xFFF87171) else Color(0xFFFBBF24),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${err.type.name.replace("_", " ")}: ${err.message}",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Suggestion: ${err.suggestedAction}",
                                        color = Color(0xFFCBD5E1),
                                        fontSize = 10.sp
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(14.dp).clickable { com.example.voice.error.VoiceErrorRegistry.instance.clearActiveError() }
                                )
                            }
                        }
                    }

                    // Expandable Text Input Section
                    AnimatedVisibility(
                        visible = isExpanded,
                        enter = fadeIn() + slideInVertically { it },
                        exit = fadeOut() + slideOutVertically { it }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF1E293B), RoundedCornerShape(14.dp))
                                    .border(1.dp, statePrimaryColor.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                BasicTextField(
                                    value = textInput,
                                    onValueChange = {
                                        textInput = it
                                        onUserInteraction()
                                    },
                                    textStyle = TextStyle(
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Normal
                                    ),
                                    cursorBrush = SolidColor(statePrimaryColor),
                                    singleLine = true,
                                    decorationBox = { innerTextField ->
                                        if (textInput.isEmpty()) {
                                            Text(
                                                text = if (partialResult.isNotBlank()) partialResult else "Type your command for Alya...",
                                                color = Color(0xFF64748B),
                                                fontSize = 13.sp
                                            )
                                        }
                                        innerTextField()
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("wake_up_popup_text_input")
                                )

                                IconButton(
                                    onClick = {
                                        val cmd = textInput.ifBlank { partialResult }
                                        if (cmd.isNotBlank()) {
                                            onSendCommand(cmd)
                                            textInput = ""
                                        }
                                    },
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(statePrimaryColor)
                                        .testTag("wake_up_popup_send_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Send,
                                        contentDescription = "Send",
                                        tint = Color(0xFF0F172A),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
}
