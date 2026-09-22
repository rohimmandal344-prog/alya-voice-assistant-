package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*

enum class AccessibilityPermissionStatus {
    GRANTED,
    DISABLED,
    RESTRICTED_BY_ANDROID_13_PLUS
}

data class AccessibilityDiagnosticResult(
    val status: AccessibilityPermissionStatus,
    val isAndroid10Plus: Boolean,
    val isAccessibilityGloballyEnabled: Boolean,
    val instructionGuideTitle: String,
    val stepByStepInstructions: List<String>
)

class AlyaAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())

    companion object {
        private const val TAG = "AlyaAccessibility"
        var instance: AlyaAccessibilityService? = null

        fun checkAccessibilityPermission(context: android.content.Context): AccessibilityPermissionStatus {
            if (instance != null) {
                return AccessibilityPermissionStatus.GRANTED
            }

            val am = context.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE) as? android.view.accessibility.AccessibilityManager
            val enabledList = am?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK) ?: emptyList()
            val serviceName = "${context.packageName}/${AlyaAccessibilityService::class.java.canonicalName}"
            
            val isEnabledViaManager = enabledList.any { serviceInfo ->
                val resolveInfo = serviceInfo.resolveInfo
                val pName = resolveInfo.serviceInfo.packageName
                val name = resolveInfo.serviceInfo.name
                pName == context.packageName && (name == AlyaAccessibilityService::class.java.canonicalName || name.contains("AlyaAccessibilityService"))
            }

            val enabledServicesSetting = try {
                android.provider.Settings.Secure.getString(
                    context.contentResolver,
                    android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                ) ?: ""
            } catch (_: Exception) {
                ""
            }
            val isEnabledInSettings = enabledServicesSetting.contains(serviceName) || enabledServicesSetting.contains(context.packageName)

            if (isEnabledViaManager || isEnabledInSettings) {
                return AccessibilityPermissionStatus.GRANTED
            }

            return AccessibilityPermissionStatus.DISABLED
        }

        fun getDiagnosticResult(context: android.content.Context): AccessibilityDiagnosticResult {
            val status = checkAccessibilityPermission(context)
            val isAndroid10Plus = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q
            val am = context.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE) as? android.view.accessibility.AccessibilityManager
            val isGloballyEnabled = am?.isEnabled ?: false

            val title: String
            val instructions: List<String>

            when (status) {
                AccessibilityPermissionStatus.GRANTED -> {
                    title = "Accessibility Active"
                    instructions = listOf("Alya Accessibility Automation Service is fully active and ready.")
                }
                AccessibilityPermissionStatus.RESTRICTED_BY_ANDROID_13_PLUS -> {
                    title = "Android 13+ Restricted Setting Notice"
                    instructions = listOf(
                        "1. Open App Info for Alya.",
                        "2. Tap 3-dots (⋮) in the top-right corner.",
                        "3. Select 'Allow Restricted Settings'.",
                        "4. Open Accessibility Settings and toggle on Alya Service."
                    )
                }
                AccessibilityPermissionStatus.DISABLED -> {
                    title = "Enable Accessibility Automation"
                    instructions = listOf(
                        "1. Open Accessibility Settings below.",
                        "2. Locate 'Alya Automation Service' in downloaded apps.",
                        "3. Toggle the switch to Enable."
                    )
                }
            }

            return AccessibilityDiagnosticResult(
                status = status,
                isAndroid10Plus = isAndroid10Plus,
                isAccessibilityGloballyEnabled = isGloballyEnabled,
                instructionGuideTitle = title,
                stepByStepInstructions = instructions
            )
        }
        
        fun isCommandSupported(command: String): Boolean {
            val cmd = command.lowercase().trim()
            return when (cmd) {
                "open_settings", "open_setting", "settings", "system_settings",
                "open_app", "open_application",
                "skip_reel", "next_reel", "scroll_forward", "scroll_down", "scroll_up", "scroll_backward", "previous_reel",
                "like_post", "like_reel", "toggle_play_reel", "pause_reel", "play_reel", "ensure_shorts_playing",
                "click_target_delayed", "click_text", "send_whatsapp", "toggle_wifi_auto", "toggle_bluetooth_auto", "connect_wifi_auto",
                "search_and_play_youtube", "click_toggle_switch", "toggle_setting_switch", "close_application", "scroll_screen", "scroll",
                "touch_element", "type_text", "press_home", "press_home_button", "press_back", "press_back_button",
                "open_notifications", "open_quick_settings", "open_recents", "recent_apps", "open_power_menu", "power_menu", "power_dialog",
                "split_screen", "toggle_split_screen", "take_screenshot", "screenshot", "lock_screen", "lock_phone",
                "double_tap", "long_press", "show_grid", "hide_grid", "tap_grid", "zoom_in", "zoom_out", "pan_magnification", "edit_text_action",
                "go_back", "go_home", "answer_call", "accept_call", "pick_up_call", "end_call", "decline_call", "reject_call", "hang_up_call" -> true
                else -> false
            }
        }

        fun executeCommand(command: String, args: Map<String, String>? = null): Boolean {
            val s = instance ?: run {
                Log.w(TAG, "Accessibility service instance is inactive. Action '$command' cannot execute via Accessibility.")
                return false
            }
            val normalizedCmd = command.lowercase().trim()
            if (!isCommandSupported(normalizedCmd)) {
                Log.w(TAG, "Accessibility command '$command' is not supported by Accessibility Service dispatcher. Returning false to trigger native fallback.")
                return false
            }
            return try {
                s.handleAssistantCommand(normalizedCmd, args)
                true
            } catch (e: Exception) {
                Log.e(TAG, "Accessibility action failed or restricted: ${e.message}")
                false
            }
        }
    }

    private var magnificationController: MagnificationController? = null
    private var windowManager: android.view.WindowManager? = null
    private var gridOverlayView: ComposeView? = null
    private var hudView: ComposeView? = null
    private var overlayLifecycleOwner: OverlayLifecycleOwner? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isGridVisible = mutableStateOf(false)
    private var hudMessage = mutableStateOf("")
    private var isHudVisible = mutableStateOf(false)

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        magnificationController = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) getMagnificationController() else null
        windowManager = getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager
        
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_CLICKED or
                    AccessibilityEvent.TYPE_VIEW_SCROLLED or
                    AccessibilityEvent.TYPE_VIEW_FOCUSED or
                    AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            notificationTimeout = 100
        }
        this.serviceInfo = info
        Log.i(TAG, "Alya Accessibility Service Connected and Configured.")
        setupHUD()
    }

    private fun setupHUD() {
        mainHandler.post {
            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            val owner = overlayLifecycleOwner ?: OverlayLifecycleOwner().also { overlayLifecycleOwner = it }
            val view = ComposeView(this).apply {
                owner.attachToComposeView(this)
                setContent {
                    AnimatedVisibility(
                        visible = isHudVisible.value,
                        enter = fadeIn() + slideInVertically { -it },
                        exit = fadeOut() + slideOutVertically { -it }
                    ) {
                        Surface(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth()
                                .height(56.dp)
                                .shadow(8.dp, RoundedCornerShape(28.dp)),
                            color = Color(0xF012121D),
                            shape = RoundedCornerShape(28.dp),
                            border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 20.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = Color(0xFF00E5FF),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = hudMessage.value,
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP
            }
            try {
                wm.addView(view, params)
                hudView = view
            } catch (e: Exception) { android.util.Log.e("Alya", "Exception handled", e) }
        }
    }

    private fun showHUD(message: String) {
        hudMessage.value = message
        isHudVisible.value = true
        mainHandler.removeCallbacksAndMessages("HUD_HIDE")
        mainHandler.postAtTime({ isHudVisible.value = false }, "HUD_HIDE", android.os.SystemClock.uptimeMillis() + 3000)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        
        // Auto-detect secure applications (payment / banking) and sync foreground app with DeviceContextManager
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: ""
            if (packageName.isNotBlank()) {
                com.example.domain.actions.DeviceContextManager.getInstance(applicationContext).updateForegroundApp(packageName)
            }
            handleForegroundPackageChanged(packageName)
        }
    }

    private fun handleForegroundPackageChanged(packageName: String) {
        if (isPaymentOrBankingApp(packageName)) {
            // Instantly hide active overlays to prevent Android alert overlay blocks in banking / payment apps
            if (isHudVisible.value) {
                isHudVisible.value = false
            }
            if (isGridVisible.value) {
                isGridVisible.value = false
            }
            try {
                com.example.service.WakeUpPopup.hide(this)
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to hide WakeUpPopup automatically: ${e.message}")
            }
            Log.w(TAG, "Secured Banking/Payment Window: Automatically dismissed active Alya overlays for $packageName to prevent security blocks.")
        }
    }

    private fun isPaymentOrBankingApp(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        val lower = packageName.lowercase()
        val exactMatches = setOf(
            "com.google.android.apps.walletnfcrel",
            "com.google.android.apps.nfc.payment",
            "com.phonepe.app",
            "net.one97.paytm",
            "in.org.npci.upiapp",
            "com.paypal.android.p2pmobile",
            "com.squareup.cash",
            "com.revolut.business",
            "com.revolut.revolut",
            "com.binance.dev",
            "com.coinbase.android"
        )
        if (exactMatches.contains(lower)) return true
        
        val keywords = listOf(
            "bank", "wallet", "pay", "finance", "card", "crypto", "trading", 
            "invest", "gpay", "phonepe", "paytm", "bhim", "paypal"
        )
        // Ensure standard harmless apps with similar keywords are not falsely matched
        return keywords.any { lower.contains(it) } && 
               !lower.contains("google.android.apps.maps") && 
               !lower.contains("google.android.youtube") &&
               !lower.contains("com.android.chrome")
    }

    override fun onInterrupt() {
        Log.w(TAG, "Alya Accessibility Service Interrupted.")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        serviceScope.cancel()
        mainHandler.removeCallbacksAndMessages(null)
        hudView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) { android.util.Log.e("Alya", "Exception handled", e) }
            hudView = null
        }
        gridOverlayView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) { android.util.Log.e("Alya", "Exception handled", e) }
            gridOverlayView = null
        }
        overlayLifecycleOwner?.destroy()
        overlayLifecycleOwner = null
    }

    private fun handleAssistantCommand(command: String, args: Map<String, String>?) {
        val normalizedCmd = command.lowercase().trim()
        Log.i(TAG, "Executing accessibility command: $normalizedCmd")
        showHUD("Executing $normalizedCmd...")
        serviceScope.launch {
            when (normalizedCmd) {
                "open_settings", "open_setting", "settings", "system_settings" -> {
                    val target = args?.get("target") ?: args?.get("setting") ?: "general"
                    autoOpenSettingsSection(target)
                }
                "open_app", "open_application" -> {
                    val appName = args?.get("app_name") ?: args?.get("appName") ?: args?.get("app") ?: ""
                    if (appName.isNotBlank()) {
                        val manager = com.example.domain.apps.AppLauncherManager(this@AlyaAccessibilityService)
                        manager.launchApplication(appName)
                    } else {
                        performGlobalAction(GLOBAL_ACTION_HOME)
                    }
                }
                "skip_reel", "next_reel", "scroll_forward", "scroll_down" -> {
                    swipeUpGesture()
                    delay(150)
                    performGlobalScroll(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                }
                "scroll_backward", "scroll_up", "previous_reel" -> {
                    swipeDownGesture()
                    delay(150)
                    performGlobalScroll(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
                }
                "like_post", "like_reel" -> {
                    clickNodeByContentDescriptionOrText("Like", "like")
                    delay(100)
                    doubleTapCenterGesture()
                }
                "toggle_play_reel", "pause_reel", "play_reel" -> {
                    tapCenterGesture()
                }
                "ensure_shorts_playing" -> {
                    autoOpenShortsTab()
                }
                "click_target_delayed" -> {
                    val target = args?.get("target") ?: ""
                    val waitMs = args?.get("delay")?.toLongOrNull() ?: 2000L
                    serviceScope.launch {
                        delay(waitMs)
                        clickNodeByText(target)
                        delay(500)
                        clickNodeByContentDescriptionOrText(target, target)
                    }
                }
                "click_text" -> args?.get("text")?.let { clickNodeByText(it) }
                "send_whatsapp" -> {
                    clickNodeByContentDescriptionOrText("Send", "send")
                }
                "toggle_wifi_auto" -> {
                    val desiredState = args?.get("state") == "on"
                    autoToggleWifi(desiredState)
                }
                "toggle_bluetooth_auto" -> {
                    val desiredState = args?.get("state") == "on"
                    autoToggleBluetooth(desiredState)
                }
                "connect_wifi_auto" -> {
                    val network = args?.get("network")
                    autoConnectWifi(network)
                }
                "search_and_play_youtube" -> {
                    val query = args?.get("query")
                    autoSearchAndPlayYoutube(query)
                }
                "click_toggle_switch", "toggle_setting_switch" -> {
                    val targetName = args?.get("target") ?: ""
                    autoClickToggleSwitch(targetName)
                }
                "close_application" -> {
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    delay(150)
                    performGlobalAction(GLOBAL_ACTION_HOME)
                }
                "scroll_screen" -> {
                    val direction = (args?.get("direction") ?: "DOWN").uppercase()
                    val distance = (args?.get("distance") ?: "SHORT").uppercase()
                    val repeatCount = if (distance == "LONG") 2 else 1
                    for (i in 1..repeatCount) {
                        when (direction) {
                            "UP" -> {
                                swipeDownGesture()
                                delay(150)
                                performGlobalScroll(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
                            }
                            "DOWN" -> {
                                swipeUpGesture()
                                delay(150)
                                performGlobalScroll(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                            }
                            "LEFT" -> swipeHorizontalGesture(toLeft = true)
                            "RIGHT" -> swipeHorizontalGesture(toLeft = false)
                        }
                        if (i < repeatCount) delay(200)
                    }
                }
                "touch_element" -> {
                    val x = (args?.get("coordinate_x") ?: args?.get("x"))?.toFloatOrNull()
                    val y = (args?.get("coordinate_y") ?: args?.get("y"))?.toFloatOrNull()
                    val label = args?.get("element_identifier") ?: args?.get("element_label") ?: args?.get("label")
                    if (!label.isNullOrBlank()) {
                        clickNodeByContentDescriptionOrText(label, label)
                    } else if (x != null && y != null) {
                        dispatchTapGesture(x, y)
                    } else {
                        tapCenterGesture()
                    }
                }
                "type_text" -> {
                    val targetField = args?.get("target_field")
                    val textContent = args?.get("text_content") ?: args?.get("text") ?: ""
                    typeTextInActiveField(targetField, textContent)
                }
                "press_home", "press_home_button" -> performGlobalAction(GLOBAL_ACTION_HOME)
                "press_back", "press_back_button" -> performGlobalAction(GLOBAL_ACTION_BACK)
                "open_notifications" -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
                "open_quick_settings" -> performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
                "open_recents", "recent_apps" -> performGlobalAction(GLOBAL_ACTION_RECENTS)
                "open_power_menu", "power_menu", "open_power_dialog", "power_dialog" -> {
                    performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
                }
                "split_screen", "toggle_split_screen" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        performGlobalAction(GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)
                    }
                }
                "take_screenshot", "screenshot" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
                    }
                }
                "lock_screen", "lock_phone" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
                    }
                }
                "double_tap" -> {
                    val x = args?.get("x")?.toFloatOrNull()
                    val y = args?.get("y")?.toFloatOrNull()
                    if (x != null && y != null) {
                        dispatchDoubleTapGesture(x, y)
                    } else {
                        doubleTapCenterGesture()
                    }
                }
                "long_press" -> {
                    val x = args?.get("x")?.toFloatOrNull()
                    val y = args?.get("y")?.toFloatOrNull()
                    val label = args?.get("label") ?: args?.get("text")
                    if (!label.isNullOrBlank()) {
                        longPressNodeByText(label)
                    } else if (x != null && y != null) {
                        dispatchLongPressGesture(x, y)
                    }
                }
                "show_grid" -> showGridOverlay()
                "hide_grid" -> hideGridOverlay()
                "tap_grid" -> {
                    val index = args?.get("index")?.toIntOrNull() ?: 1
                    tapGridLocation(index)
                }
                "zoom_in" -> zoomIn()
                "zoom_out" -> zoomOut()
                "pan_magnification" -> {
                    val direction = args?.get("direction") ?: "center"
                    panMagnification(direction)
                }
                "edit_text_action" -> {
                    val action = args?.get("action") ?: "type"
                    val content = args?.get("content") ?: ""
                    handleTextEditingAction(action, content)
                }
                "go_back" -> performGlobalAction(GLOBAL_ACTION_BACK)
                "go_home" -> performGlobalAction(GLOBAL_ACTION_HOME)
                "open_recents" -> performGlobalAction(GLOBAL_ACTION_RECENTS)
                "scroll" -> {
                    val direction = args?.get("direction") ?: "down"
                    performScroll(direction)
                }
                "answer_call", "accept_call", "pick_up_call" -> {
                    performAnswerCallAccessibilityAction()
                }
                "end_call", "decline_call", "reject_call", "hang_up_call" -> {
                    performEndCallAccessibilityAction()
                }
            }
        }
    }

    private fun showGridOverlay() {
        if (gridOverlayView != null) {
            isGridVisible.value = true
            return
        }

        mainHandler.post {
            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            val owner = overlayLifecycleOwner ?: OverlayLifecycleOwner().also { overlayLifecycleOwner = it }
            val view = ComposeView(this).apply {
                owner.attachToComposeView(this)
                setContent {
                    AnimatedVisibility(
                        visible = isGridVisible.value,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            // 3x4 Grid for 12 items
                            Column(modifier = Modifier.fillMaxSize()) {
                                for (row in 0..3) {
                                    Row(modifier = Modifier.weight(1f)) {
                                        for (col in 0..2) {
                                            val index = row * 3 + col + 1
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .fillMaxHeight()
                                                    .border(0.5.dp, Color(0x3300E5FF)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Surface(
                                                    modifier = Modifier.size(32.dp),
                                                    color = Color(0x99000000),
                                                    shape = CircleShape,
                                                    border = BorderStroke(1.dp, Color(0xFF00E5FF))
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Text(
                                                            text = index.toString(),
                                                            color = Color.White,
                                                            fontSize = 16.sp,
                                                            fontWeight = FontWeight.Bold
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
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            )
            try {
                wm.addView(view, params)
                gridOverlayView = view
                isGridVisible.value = true
            } catch (e: Exception) { android.util.Log.e("Alya", "Exception handled", e) }
        }
    }

    private fun hideGridOverlay() {
        isGridVisible.value = false
    }

    private fun tapGridLocation(index: Int) {
        if (index !in 1..12) return
        
        val metrics = resources.displayMetrics
        val row = (index - 1) / 3
        val col = (index - 1) % 3
        
        val x = (col + 0.5f) * (metrics.widthPixels / 3f)
        val y = (row + 0.5f) * (metrics.heightPixels / 4f)
        
        showHUD("Selecting Item $index")
        hideGridOverlay()
        dispatchTapGesture(x, y)
    }

    private fun zoomIn() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val controller = magnificationController ?: return
            val currentScale = controller.scale
            controller.setScale(currentScale + 1.0f, true)
        }
    }

    private fun zoomOut() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val controller = magnificationController ?: return
            val currentScale = controller.scale
            if (currentScale > 1.0f) {
                controller.setScale(currentScale - 1.0f, true)
            } else {
                controller.reset(true)
            }
        }
    }

    private fun panMagnification(direction: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val controller = magnificationController ?: return
            val currentCenterX = controller.centerX
            val currentCenterY = controller.centerY
            val step = 200f
            
            when (direction.lowercase()) {
                "up" -> controller.setCenter(currentCenterX, currentCenterY - step, true)
                "down" -> controller.setCenter(currentCenterX, currentCenterY + step, true)
                "left" -> controller.setCenter(currentCenterX - step, currentCenterY, true)
                "right" -> controller.setCenter(currentCenterX + step, currentCenterY, true)
                "center" -> controller.reset(true)
            }
        }
    }

    private fun handleTextEditingAction(action: String, content: String) {
        val rootNode = rootInActiveWindow ?: return
        val focusedNode = findFocusedEditableNode(rootNode) ?: return
        
        when (action.lowercase()) {
            "type" -> {
                val currentText = focusedNode.text?.toString() ?: ""
                val newText = currentText + content
                val arguments = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newText)
                }
                focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            }
            "clear" -> {
                val arguments = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
                }
                focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            }
            "delete_last" -> {
                val currentText = focusedNode.text?.toString() ?: ""
                if (currentText.isNotEmpty()) {
                    val lastSpace = currentText.trimEnd().lastIndexOf(' ')
                    val newText = if (lastSpace != -1) currentText.substring(0, lastSpace) else ""
                    val arguments = Bundle().apply {
                        putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newText)
                    }
                    focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                }
            }
        }
    }

    private fun findFocusedEditableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isFocused && (node.isEditable || node.className.contains("EditText"))) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findFocusedEditableNode(child)
            if (found != null) return found
        }
        return null
    }

    private fun performScroll(direction: String) {
        val rootNode = rootInActiveWindow ?: return
        val scrollableNode = findScrollableNode(rootNode) ?: return
        when (direction.lowercase()) {
            "up" -> scrollableNode.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
            "down" -> scrollableNode.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        }
    }

    private fun swipeUpGesture() {
        try {
            val metrics = resources.displayMetrics
            val width = metrics.widthPixels.toFloat()
            val height = metrics.heightPixels.toFloat()
            val startX = width / 2f
            val startY = height * 0.78f
            val endY = height * 0.22f

            val path = Path().apply {
                moveTo(startX, startY)
                lineTo(startX, endY)
            }
            val stroke = GestureDescription.StrokeDescription(path, 0, 260)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            dispatchGesture(gesture, null, null)
            Log.i(TAG, "Dispatched smooth swipe-up gesture for next reel/short.")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to dispatch swipeUpGesture: ${e.message}")
        }
    }

    private fun swipeDownGesture() {
        try {
            val metrics = resources.displayMetrics
            val width = metrics.widthPixels.toFloat()
            val height = metrics.heightPixels.toFloat()
            val startX = width / 2f
            val startY = height * 0.22f
            val endY = height * 0.78f

            val path = Path().apply {
                moveTo(startX, startY)
                lineTo(startX, endY)
            }
            val stroke = GestureDescription.StrokeDescription(path, 0, 260)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            dispatchGesture(gesture, null, null)
            Log.i(TAG, "Dispatched smooth swipe-down gesture for previous reel/short.")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to dispatch swipeDownGesture: ${e.message}")
        }
    }

    private fun tapCenterGesture() {
        try {
            val metrics = resources.displayMetrics
            val width = metrics.widthPixels.toFloat()
            val height = metrics.heightPixels.toFloat()
            val path = Path().apply {
                moveTo(width / 2f, height / 2f)
            }
            val stroke = GestureDescription.StrokeDescription(path, 0, 80)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            dispatchGesture(gesture, null, null)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to dispatch tapCenterGesture: ${e.message}")
        }
    }

    private fun doubleTapCenterGesture() {
        try {
            val metrics = resources.displayMetrics
            val width = metrics.widthPixels.toFloat()
            val height = metrics.heightPixels.toFloat()
            val path1 = Path().apply { moveTo(width / 2f, height / 2f) }
            val path2 = Path().apply { moveTo(width / 2f, height / 2f) }
            val stroke1 = GestureDescription.StrokeDescription(path1, 0, 60)
            val stroke2 = GestureDescription.StrokeDescription(path2, 120, 60)
            val gesture = GestureDescription.Builder().addStroke(stroke1).addStroke(stroke2).build()
            dispatchGesture(gesture, null, null)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to dispatch doubleTapCenterGesture: ${e.message}")
        }
    }

    private fun dispatchDoubleTapGesture(x: Float, y: Float) {
        try {
            val path1 = Path().apply { moveTo(x, y) }
            val path2 = Path().apply { moveTo(x, y) }
            val stroke1 = GestureDescription.StrokeDescription(path1, 0, 50)
            val stroke2 = GestureDescription.StrokeDescription(path2, 100, 50)
            val gesture = GestureDescription.Builder().addStroke(stroke1).addStroke(stroke2).build()
            dispatchGesture(gesture, null, null)
            Log.i(TAG, "Dispatched double tap gesture at ($x, $y)")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to dispatch double tap at ($x, $y): ${e.message}")
        }
    }

    private fun dispatchLongPressGesture(x: Float, y: Float) {
        try {
            val path = Path().apply { moveTo(x, y) }
            val stroke = GestureDescription.StrokeDescription(path, 0, 650)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            dispatchGesture(gesture, null, null)
            Log.i(TAG, "Dispatched long press gesture at ($x, $y)")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to dispatch long press at ($x, $y): ${e.message}")
        }
    }

    private fun longPressNodeByText(targetText: String) {
        try {
            val root = rootInActiveWindow ?: return
            val nodes = root.findAccessibilityNodeInfosByText(targetText)
            val target = nodes?.firstOrNull()
            if (target != null) {
                val rect = android.graphics.Rect()
                target.getBoundsInScreen(rect)
                dispatchLongPressGesture(rect.centerX().toFloat(), rect.centerY().toFloat())
                Log.i(TAG, "Long pressed node matching text '$targetText'")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to long press node: ${e.message}")
        }
    }

    private fun dispatchTapGesture(x: Float, y: Float) {
        try {
            val path = Path().apply { moveTo(x, y) }
            val stroke = GestureDescription.StrokeDescription(path, 0, 80)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            dispatchGesture(gesture, null, null)
            Log.i(TAG, "Dispatched tap gesture at coordinates ($x, $y)")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to dispatch tap gesture at ($x, $y): ${e.message}")
        }
    }

    private fun swipeHorizontalGesture(toLeft: Boolean) {
        try {
            val metrics = resources.displayMetrics
            val width = metrics.widthPixels.toFloat()
            val height = metrics.heightPixels.toFloat()
            val y = height / 2f
            val startX = if (toLeft) width * 0.85f else width * 0.15f
            val endX = if (toLeft) width * 0.15f else width * 0.85f

            val path = Path().apply {
                moveTo(startX, y)
                lineTo(endX, y)
            }
            val stroke = GestureDescription.StrokeDescription(path, 0, 250)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            dispatchGesture(gesture, null, null)
            Log.i(TAG, "Dispatched horizontal swipe gesture (toLeft=$toLeft)")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to dispatch swipeHorizontalGesture: ${e.message}")
        }
    }

    private fun autoOpenShortsTab() {
        serviceScope.launch {
            delay(1200)
            for (attempt in 1..8) {
                val rootNode = rootInActiveWindow ?: continue
                val shortsTab = findShortsTabInTree(rootNode)
                if (shortsTab != null) {
                    clickNode(shortsTab)
                    Log.i(TAG, "Clicked Shorts tab in YouTube navigation.")
                    break
                }
                delay(300)
            }
        }
    }

    /**
     * Captures and aggregates all readable text elements from the active screen hierarchy
     * to enable dynamic UI perception via voice commands.
     */
    fun captureScreenText(): String {
        val root = rootInActiveWindow ?: return "Screen content unavailable (Accessibility Service is not enabled or lock screen is active)."
        val textList = mutableListOf<String>()
        collectTextNodes(root, textList)
        if (textList.isEmpty()) {
            return "No readable text elements visible on the screen right now."
        }
        return textList.distinct().take(35).joinToString("\n")
    }

    private fun collectTextNodes(node: AccessibilityNodeInfo?, list: MutableList<String>) {
        if (node == null) return
        val text = node.safeText.trim()
        val desc = node.safeContentDescription.trim()
        if (text.isNotBlank() && text.length > 1) {
            list.add(text)
        } else if (desc.isNotBlank() && desc.length > 1) {
            list.add("[$desc]")
        }
        for (i in 0 until node.childCount) {
            val child = try { node.getChild(i) } catch (_: Throwable) { null }
            collectTextNodes(child, list)
        }
    }

    private val AccessibilityNodeInfo.safeText: String
        get() = try { text?.toString() ?: "" } catch (_: Throwable) { "" }

    private val AccessibilityNodeInfo.safeContentDescription: String
        get() = try { contentDescription?.toString() ?: "" } catch (_: Throwable) { "" }

    private val AccessibilityNodeInfo.safeClassName: String
        get() = try { className?.toString() ?: "" } catch (_: Throwable) { "" }

    private val AccessibilityNodeInfo.safeViewId: String
        get() = try { viewIdResourceName ?: "" } catch (_: Throwable) { "" }

    private fun typeTextInActiveField(targetField: String?, textContent: String) {
        try {
            val rootNode = rootInActiveWindow ?: return
            var targetNode: AccessibilityNodeInfo? = null
            if (!targetField.isNullOrBlank()) {
                val nodes = try { rootNode.findAccessibilityNodeInfosByText(targetField) } catch (_: Throwable) { null }
                targetNode = nodes?.firstOrNull { it.isEditable || it.isFocusable }
            }
            if (targetNode == null) {
                targetNode = findEditableNode(rootNode)
            }

            if (targetNode != null) {
                val arguments = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, textContent)
                }
                val performed = try {
                    targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                } catch (_: Throwable) { false }

                if (!performed) {
                    // Fallback: clipboard paste action for custom input fields
                    try {
                        val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("alya_text", textContent)
                        clipboard?.setPrimaryClip(clip)
                        targetNode.performAction(AccessibilityNodeInfo.ACTION_PASTE)
                    } catch (e: Throwable) { android.util.Log.e("Alya", "Throwable handled", e) }
                }
                Log.i(TAG, "Typed text '$textContent' into active field.")
            } else {
                Log.w(TAG, "No editable field found to type text.")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to type text: ${e.message}")
        }
    }

    private fun findNodeByText(root: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? {
        if (root == null) return null
        val nodes = try { root.findAccessibilityNodeInfosByText(text) } catch (_: Throwable) { null }
        val exactMatch = nodes?.firstOrNull { 
            it.safeText.equals(text, ignoreCase = true) || it.safeContentDescription.equals(text, ignoreCase = true)
        }
        if (exactMatch != null) return exactMatch
        
        return nodes?.firstOrNull { 
            it.safeText.contains(text, ignoreCase = true) || it.safeContentDescription.contains(text, ignoreCase = true)
        }
    }

    private fun clickNodeByContentDescriptionOrText(desc: String, text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val nodes = root.findAccessibilityNodeInfosByText(text)
        val target = nodes?.firstOrNull() ?: root.findAccessibilityNodeInfosByViewId(desc)?.firstOrNull()
        
        return if (target != null) {
            clickNode(target)
        } else {
            // Try searching recursively as fallback
            val targetRecursive = findNodeByText(root, text) ?: findNodeByContentDescription(root, desc)
            if (targetRecursive != null) {
                clickNode(targetRecursive)
            } else {
                Log.w(TAG, "No node found matching description '$desc' or text '$text'")
                false
            }
        }
    }

    private fun findNodeByContentDescription(root: AccessibilityNodeInfo, desc: String): AccessibilityNodeInfo? {
        if (root.safeContentDescription.contains(desc, ignoreCase = true)) return root
        for (i in 0 until root.childCount) {
            val child = try { root.getChild(i) } catch (_: Throwable) { null } ?: continue
            val found = findNodeByContentDescription(child, desc)
            if (found != null) return found
        }
        return null
    }

    private fun performGlobalScroll(action: Int): Boolean {
        val root = rootInActiveWindow ?: return false
        val scrollable = findScrollableNode(root)
        return scrollable?.performAction(action) ?: false
    }

    private fun findAnySwitch(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val className = node.safeClassName
        if (className.contains("Switch") || className.contains("ToggleButton") || node.isCheckable) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = try { node.getChild(i) } catch (_: Throwable) { null } ?: continue
            val found = findAnySwitch(child)
            if (found != null) return found
        }
        return null
    }

    private fun clickNodeByText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val target = findNodeByText(root, text)
        return if (target != null) {
            clickNode(target)
        } else {
            Log.w(TAG, "No node found matching text: $text")
            false
        }
    }

    private fun clickNode(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable) {
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        val parent = node.parent
        return if (parent != null) {
            clickNode(parent)
        } else {
            // If not clickable, try to tap by coordinates
            val rect = android.graphics.Rect()
            node.getBoundsInScreen(rect)
            dispatchTapGesture(rect.centerX().toFloat(), rect.centerY().toFloat())
            true
        }
    }

    private fun findScrollableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            val child = try { node.getChild(i) } catch (_: Throwable) { null } ?: continue
            val found = findScrollableNode(child)
            if (found != null) return found
        }
        return null
    }

    private fun findSwitchInTree(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val className = node.safeClassName
        if (className.contains("Switch") || className.contains("ToggleButton") || node.isCheckable) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = try { node.getChild(i) } catch (_: Throwable) { null } ?: continue
            val found = findSwitchInTree(child)
            if (found != null) return found
        }
        return null
    }

    private fun findEditableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable || node.isFocused || node.className.contains("EditText")) return node
        for (i in 0 until node.childCount) {
            val child = try { node.getChild(i) } catch (_: Throwable) { null } ?: continue
            val found = findEditableNode(child)
            if (found != null) return found
        }
        return null
    }

    private fun findShortsTabInTree(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        try {
            val text = node.safeText.lowercase()
            val desc = node.safeContentDescription.lowercase()
            if ((text.contains("shorts") || desc.contains("shorts")) && node.isClickable) {
                return node
            }
            for (i in 0 until node.childCount) {
                val child = try { node.getChild(i) } catch (_: Throwable) { null } ?: continue
                val found = findShortsTabInTree(child)
                if (found != null) return found
            }
        } catch (e: Throwable) {
            Log.d(TAG, "Safely handled node inspection exception: ${e.message}")
        }
        return null
    }

    private fun autoSearchAndPlayYoutube(query: String?) {
        serviceScope.launch {
            Log.i(TAG, "Starting YouTube play automation for: $query")
            delay(1200) // Wait for YouTube activity/results to load
            for (attempt in 1..10) {
                val rootNode = rootInActiveWindow ?: continue
                val videoItem = findFirstVideoResult(rootNode)
                if (videoItem != null) {
                    val clicked = clickNode(videoItem)
                    Log.i(TAG, "Clicked first YouTube video result: $clicked")
                    if (clicked) break
                }
                delay(300)
            }
        }
    }

    private fun autoClickToggleSwitch(targetSetting: String) {
        serviceScope.launch {
            Log.i(TAG, "Starting automated toggle switch click for: $targetSetting")
            for (attempt in 1..10) {
                val rootNode = rootInActiveWindow ?: continue
                val switchNode = findSwitchInTree(rootNode)
                if (switchNode != null) {
                    val clicked = clickNode(switchNode)
                    Log.i(TAG, "Clicked setting toggle switch: $clicked")
                    if (clicked) break
                }
                delay(250)
            }
        }
    }

    private fun findFirstVideoResult(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        try {
            val desc = node.safeContentDescription.lowercase()
            val viewId = node.safeViewId.lowercase()
            if (node.isClickable && (desc.contains("views") || desc.contains("video") || viewId.contains("thumbnail") || viewId.contains("item"))) {
                return node
            }
            for (i in 0 until node.childCount) {
                val child = try { node.getChild(i) } catch (_: Throwable) { null } ?: continue
                val found = findFirstVideoResult(child)
                if (found != null) return found
            }
        } catch (e: Throwable) {
            Log.d(TAG, "Safely handled node inspection exception: ${e.message}")
        }
        return null
    }

    private fun autoToggleWifi(desiredState: Boolean) {
        serviceScope.launch {
            Log.i(TAG, "Starting Wi-Fi toggle automation to state: $desiredState")
            var success = false
            for (attempt in 1..15) {
                val rootNode = rootInActiveWindow
                if (rootNode != null) {
                    var switchNode = findWifiSwitch(rootNode)
                    if (switchNode == null) {
                        switchNode = findAnySwitch(rootNode)
                    }
                    if (switchNode != null) {
                        val isChecked = switchNode.isChecked
                        Log.i(TAG, "Found Wi-Fi switch (attempt $attempt): current checked status is $isChecked, desired is $desiredState")
                        if (isChecked != desiredState) {
                            val clicked = clickNode(switchNode) || switchNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            Log.i(TAG, "Click switch returned: $clicked")
                            if (clicked) {
                                success = true
                                delay(600)
                                break
                            }
                        } else {
                            success = true
                            break
                        }
                    }
                }
                delay(300)
            }
            if (success) {
                Log.i(TAG, "Wi-Fi toggle automation completed successfully. Backing out...")
                delay(600)
                performGlobalAction(GLOBAL_ACTION_BACK)
            } else {
                Log.w(TAG, "Wi-Fi toggle automation timed out or failed.")
            }
        }
    }

    private fun autoToggleBluetooth(desiredState: Boolean) {
        serviceScope.launch {
            Log.i(TAG, "Starting Bluetooth toggle automation to state: $desiredState")
            var success = false
            for (attempt in 1..12) {
                val rootNode = rootInActiveWindow
                if (rootNode != null) {
                    val switchNode = findBluetoothSwitch(rootNode)
                    if (switchNode != null) {
                        val isChecked = switchNode.isChecked
                        Log.i(TAG, "Found Bluetooth switch: current checked status is $isChecked, desired is $desiredState")
                        if (isChecked != desiredState) {
                            val clicked = clickNode(switchNode)
                            Log.i(TAG, "Click switch returned: $clicked")
                            if (clicked) {
                                success = true
                                delay(500)
                                break
                            }
                        } else {
                            success = true
                            break
                        }
                    }
                }
                delay(250)
            }
            if (success) {
                Log.i(TAG, "Bluetooth toggle automation completed successfully. Backing out...")
                delay(500)
                performGlobalAction(GLOBAL_ACTION_BACK)
            } else {
                Log.w(TAG, "Bluetooth toggle automation timed out or failed.")
            }
        }
    }

    private fun autoConnectWifi(networkName: String?) {
        serviceScope.launch {
            Log.i(TAG, "Starting intelligent Wi-Fi connect automation to: $networkName")
            showHUD("Connecting to Wi-Fi: ${networkName ?: "Available"}")
            
            com.example.util.diagnostics.DiagnosticLogManager.instance.logEvent(
                stage = com.example.util.diagnostics.DiagnosticStage.EXECUTION,
                command = "Wi-Fi Automation",
                details = "Attempting to connect to ${networkName ?: "best available"} network via Accessibility.",
                isSuccess = true
            )

            delay(500)
            
            // Step 1: Ensure Wi-Fi is toggled ON first if found OFF
            for (turnOnAttempt in 1..4) {
                val rootNode = rootInActiveWindow ?: continue
                val wifiSwitch = findWifiSwitch(rootNode)
                if (wifiSwitch != null && !wifiSwitch.isChecked) {
                    Log.i(TAG, "Wi-Fi switch is off, turning it on...")
                    clickNode(wifiSwitch)
                    delay(1200) // Give it time to initialize radio
                    break
                }
            }

            // Step 2: Scan for available or saved Wi-Fi networks in list
            var success = false
            for (attempt in 1..20) {
                val rootNode = rootInActiveWindow
                if (rootNode != null) {
                    if (networkName.isNullOrBlank() || networkName == "default") {
                        val item = findFirstAvailableWifiNetwork(rootNode)
                        if (item != null) {
                            val clicked = clickNode(item)
                            Log.i(TAG, "Clicked first available Wi-Fi network: $clicked")
                            if (clicked) {
                                success = true
                                delay(800)
                                checkAndClickConnectButton(rootNode)
                                break
                            }
                        }
                    } else {
                        val item = findWifiNetworkByName(rootNode, networkName)
                        if (item != null) {
                            val clicked = clickNode(item)
                            Log.i(TAG, "Clicked specific Wi-Fi network '$networkName': $clicked")
                            if (clicked) {
                                success = true
                                delay(800)
                                checkAndClickConnectButton(rootNode)
                                break
                            }
                        }
                    }
                }
                delay(400)
            }

            if (success) {
                Log.i(TAG, "Wi-Fi connect automation completed successfully. Backing out...")
                showHUD("Wi-Fi Connection Triggered Successfully")
                com.example.util.diagnostics.DiagnosticLogManager.instance.logEvent(
                    stage = com.example.util.diagnostics.DiagnosticStage.RESULT,
                    command = "Wi-Fi Connected",
                    details = "Successfully navigated and triggered connection for $networkName",
                    isSuccess = true
                )
                delay(1500)
                performGlobalAction(GLOBAL_ACTION_BACK)
                delay(500)
                performGlobalAction(GLOBAL_ACTION_HOME)
            } else {
                Log.w(TAG, "Wi-Fi connect automation timed out or failed to locate network.")
                showHUD("Failed to find network: $networkName")
                com.example.util.diagnostics.DiagnosticLogManager.instance.logEvent(
                    stage = com.example.util.diagnostics.DiagnosticStage.RESULT,
                    command = "Wi-Fi Failed",
                    details = "Automation failed to locate or click the specified Wi-Fi network.",
                    isSuccess = false
                )
            }
        }
    }

    private fun checkAndClickConnectButton(rootNode: AccessibilityNodeInfo) {
        val connectNodes = rootNode.findAccessibilityNodeInfosByText("Connect")
        if (connectNodes != null) {
            for (btn in connectNodes) {
                if (btn.isClickable) {
                    clickNode(btn)
                    Log.i(TAG, "Clicked 'Connect' confirmation button.")
                    return
                }
            }
        }
    }

    private fun findWifiSwitch(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val className = node.className?.toString() ?: ""
        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""

        if ((className.contains("Switch") || className.contains("ToggleButton") || node.isCheckable) && 
            (text.contains("wi-fi") || text.contains("wifi") || text.contains("wlan") || text.contains("use") || text.contains("internet") ||
             desc.contains("wi-fi") || desc.contains("wifi") || desc.contains("wlan") || desc.contains("use") || desc.contains("internet") ||
             node.viewIdResourceName?.lowercase()?.contains("switch") == true)) {
            return node
        }
        
        if (text.contains("wi-fi") || text.contains("wlan") || text.contains("wifi") || text.contains("use wi-fi") ||
            desc.contains("wi-fi") || desc.contains("wlan") || desc.contains("wifi")) {
            val parent = node.parent
            if (parent != null) {
                val switchNode = findSwitchInTree(parent)
                if (switchNode != null) return switchNode
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findWifiSwitch(child)
            if (result != null) return result
        }
        return null
    }

    private fun findBluetoothSwitch(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val className = node.className?.toString() ?: ""
        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""

        if ((className.contains("Switch") || className.contains("ToggleButton") || node.isCheckable) && 
            (text.contains("bluetooth") || desc.contains("bluetooth") || text.contains("bt") || desc.contains("bt") ||
             node.viewIdResourceName?.lowercase()?.contains("switch") == true)) {
            return node
        }

        if (text.contains("bluetooth") || desc.contains("bluetooth")) {
            val parent = node.parent
            if (parent != null) {
                val switchNode = findSwitchInTree(parent)
                if (switchNode != null) return switchNode
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findBluetoothSwitch(child)
            if (result != null) return result
        }
        return null
    }

    private fun findFirstAvailableWifiNetwork(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val className = node.safeClassName
        val text = node.safeText.lowercase()
        
        if (node.isClickable && !className.contains("Switch") && !className.contains("Button") && 
            text.isNotBlank() && !text.contains("wi-fi") && !text.contains("wlan") && !text.contains("internet")) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = try { node.getChild(i) } catch (_: Throwable) { null } ?: continue
            val found = findFirstAvailableWifiNetwork(child)
            if (found != null) return found
        }
        return null
    }

    private fun findWifiNetworkByName(node: AccessibilityNodeInfo, name: String): AccessibilityNodeInfo? {
        val text = node.safeText.lowercase()
        val desc = node.safeContentDescription.lowercase()
        val target = name.lowercase()

        if (text.contains(target) || desc.contains(target)) {
            var temp: AccessibilityNodeInfo? = node
            while (temp != null) {
                if (temp.isClickable) return temp
                temp = temp.parent
            }
            return node
        }
        for (i in 0 until node.childCount) {
            val child = try { node.getChild(i) } catch (_: Throwable) { null } ?: continue
            val found = findWifiNetworkByName(child, name)
            if (found != null) return found
        }
        return null
    }

    private fun performAnswerCallAccessibilityAction(): Boolean {
        val root = rootInActiveWindow ?: return false
        val targets = listOf("Answer", "Accept", "Pick up", "Uthao", "Uthalo", "Uthale", "Receive", "Swipe up to answer", "Swipe right to answer", "Hello", "Yes")
        for (target in targets) {
            if (clickNodeByText(target) || clickNodeByContentDescriptionOrText(target, target)) {
                Log.i(TAG, "Successfully clicked answer target: '$target'")
                return true
            }
        }

        // Search for view IDs containing answer / accept
        val answerNode = findNodeByViewIdSubstring(root, listOf("answer", "accept", "btn_accept", "call_answer", "incoming_call"))
        if (answerNode != null && clickNode(answerNode)) {
            Log.i(TAG, "Successfully clicked answer node by View ID")
            return true
        }

        // Swipe up gesture as fallback to answer
        swipeUpGesture()
        return true
    }

    private fun performEndCallAccessibilityAction(): Boolean {
        val root = rootInActiveWindow ?: return false
        val targets = listOf("Decline", "Reject", "End", "Hang up", "Cut", "End call", "Dismiss", "Disconnect", "Kato", "Kat do", "Kat de", "Cancel")
        for (target in targets) {
            if (clickNodeByText(target) || clickNodeByContentDescriptionOrText(target, target)) {
                Log.i(TAG, "Successfully clicked decline/end target: '$target'")
                return true
            }
        }

        // Search for view IDs containing decline / reject / end
        val endNode = findNodeByViewIdSubstring(root, listOf("decline", "reject", "end_call", "hangup", "btn_decline", "call_end"))
        if (endNode != null && clickNode(endNode)) {
            Log.i(TAG, "Successfully clicked end call node by View ID")
            return true
        }

        return false
    }

    private fun findNodeByViewIdSubstring(node: AccessibilityNodeInfo, substrings: List<String>): AccessibilityNodeInfo? {
        val viewId = node.safeViewId.lowercase()
        if (substrings.any { viewId.contains(it) }) {
            var temp: AccessibilityNodeInfo? = node
            while (temp != null) {
                if (temp.isClickable) return temp
                temp = temp.parent
            }
            return node
        }
        for (i in 0 until node.childCount) {
            val child = try { node.getChild(i) } catch (_: Throwable) { null } ?: continue
            val found = findNodeByViewIdSubstring(child, substrings)
            if (found != null) return found
        }
        return null
    }

    private fun autoOpenSettingsSection(target: String) {
        val targetLower = target.lowercase().trim()
        val action = when (targetLower) {
            "wifi", "wi-fi", "internet", "network", "wlan" -> android.provider.Settings.ACTION_WIFI_SETTINGS
            "bluetooth", "bt" -> android.provider.Settings.ACTION_BLUETOOTH_SETTINGS
            "display", "screen", "brightness" -> android.provider.Settings.ACTION_DISPLAY_SETTINGS
            "sound", "volume", "audio" -> android.provider.Settings.ACTION_SOUND_SETTINGS
            "notifications", "notification" -> android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS
            "location", "gps" -> android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS
            "battery", "power" -> android.provider.Settings.ACTION_BATTERY_SAVER_SETTINGS
            "datetime", "date", "time" -> android.provider.Settings.ACTION_DATE_SETTINGS
            "app_info", "apps" -> android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            "accessibility" -> android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS
            else -> android.provider.Settings.ACTION_SETTINGS
        }

        val intent = android.content.Intent(action).apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            if (action == android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS) {
                data = android.net.Uri.parse("package:$packageName")
            } else if (action == android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS) {
                putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, packageName)
            }
        }

        try {
            startActivity(intent)
            Log.i(TAG, "Successfully launched settings section intent for $targetLower")
        } catch (e: Exception) {
            try {
                startActivity(android.content.Intent(android.provider.Settings.ACTION_SETTINGS).apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } catch (ex: Exception) {
                Log.e(TAG, "Failed to launch settings activity: ${ex.message}")
            }
        }
    }
}
