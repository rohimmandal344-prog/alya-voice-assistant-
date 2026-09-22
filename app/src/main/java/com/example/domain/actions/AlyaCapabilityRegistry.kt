package com.example.domain.actions

import android.content.Context
import android.os.Build
import com.example.domain.tools.ActionResultStatus
import com.example.domain.tools.ToolExecutionResult
import com.example.util.PermissionManager

/**
 * Capability state for granular status reporting.
 */
enum class CapabilityAvailability {
    SUPPORTED_AND_WORKING,
    SUPPORTED_BUT_PERMISSION_REQUIRED,
    SUPPORTED_BUT_SPECIAL_ACCESS_REQUIRED,
    PARTIALLY_SUPPORTED,
    PLATFORM_RESTRICTED,
    DEVICE_RESTRICTED,
    ONLINE_REQUIRED,
    OFFLINE_SUPPORTED,
    UNAVAILABLE
}

/**
 * Metadata definition for every assistant capability in the registry.
 */
data class AlyaCapability(
    val id: String,
    val name: String,
    val description: String,
    val offlineSupported: Boolean,
    val onlineRequired: Boolean,
    val requiredPermissions: List<String> = emptyList(),
    val requiresAccessibility: Boolean = false,
    val requiresOverlay: Boolean = false,
    val minSdk: Int = Build.VERSION_CODES.LOLLIPOP
)

/**
 * AlyaCapabilityRegistry (Alya v3.0 Production)
 *
 * Central Source-of-Truth for all system, device, local, and online capabilities.
 * Checks real OS permissions, accessibility status, and platform restrictions
 * before reporting availability.
 */
class AlyaCapabilityRegistry private constructor(private val context: Context) {

    private val capabilities = mutableMapOf<String, AlyaCapability>()

    init {
        registerDefaultCapabilities()
    }

    private fun registerDefaultCapabilities() {
        register(AlyaCapability("open_app", "App Launcher", "Open installed applications", offlineSupported = true, onlineRequired = false))
        register(AlyaCapability("touch_element", "UI Click/Touch", "Interact with UI controls via Accessibility", offlineSupported = true, onlineRequired = false, requiresAccessibility = true))
        register(AlyaCapability("scroll_screen", "Screen Scroll", "Scroll UI pages up/down", offlineSupported = true, onlineRequired = false, requiresAccessibility = true))
        register(AlyaCapability("type_text", "Text Input", "Type text into focused UI elements", offlineSupported = true, onlineRequired = false, requiresAccessibility = true))
        register(AlyaCapability("read_screen", "Screen Perception", "Inspect current UI elements and text", offlineSupported = true, onlineRequired = false, requiresAccessibility = true))
        register(AlyaCapability("control_volume", "Volume Control", "Adjust system audio and ringer volumes", offlineSupported = true, onlineRequired = false))
        register(AlyaCapability("control_brightness", "Brightness Control", "Adjust screen brightness or open display settings", offlineSupported = true, onlineRequired = false))
        register(AlyaCapability("toggle_flashlight", "Flashlight/Torch", "Toggle camera LED torch", offlineSupported = true, onlineRequired = false))
        register(AlyaCapability("toggle_wifi", "Wi-Fi Control", "Toggle Wi-Fi state or open connectivity panel", offlineSupported = true, onlineRequired = false))
        register(AlyaCapability("toggle_bluetooth", "Bluetooth Control", "Toggle Bluetooth or open Bluetooth settings", offlineSupported = true, onlineRequired = false))
        register(AlyaCapability("make_call", "Phone Call", "Initiate outgoing voice call to contact", offlineSupported = true, onlineRequired = false, requiredPermissions = listOf(android.Manifest.permission.CALL_PHONE)))
        register(AlyaCapability("answer_call", "Answer Call", "Answer incoming phone call via accessibility", offlineSupported = true, onlineRequired = false, requiresAccessibility = true))
        register(AlyaCapability("end_call", "End Call", "Reject or hang up active call", offlineSupported = true, onlineRequired = false, requiresAccessibility = true))
        register(AlyaCapability("create_timer", "Timer", "Set countdown timers with system clock", offlineSupported = true, onlineRequired = false))
        register(AlyaCapability("create_wakeup_alarm", "Wake-Up Alarm", "Schedule exact wake alarms with persistent scheduler", offlineSupported = true, onlineRequired = false))
        register(AlyaCapability("check_weather", "Weather", "Fetch live meteorological report and forecast", offlineSupported = false, onlineRequired = true))
        register(AlyaCapability("web_search", "Online Search", "Search the web for up-to-date information", offlineSupported = false, onlineRequired = true))
        register(AlyaCapability("live_conversation", "Live Voice Conversation", "Full-duplex low latency live audio dialogue", offlineSupported = false, onlineRequired = true, requiredPermissions = listOf(android.Manifest.permission.RECORD_AUDIO)))
    }

    fun register(capability: AlyaCapability) {
        capabilities[capability.id] = capability
    }

    fun getCapability(id: String): AlyaCapability? = capabilities[id]

    fun getAllCapabilities(): List<AlyaCapability> = capabilities.values.toList()

    fun checkAvailability(capabilityId: String): CapabilityAvailability {
        val cap = capabilities[capabilityId] ?: return CapabilityAvailability.UNAVAILABLE

        if (Build.VERSION.SDK_INT < cap.minSdk) {
            return CapabilityAvailability.DEVICE_RESTRICTED
        }

        // Check required dangerous permissions
        for (perm in cap.requiredPermissions) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(context, perm) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                return CapabilityAvailability.SUPPORTED_BUT_PERMISSION_REQUIRED
            }
        }

        // Check accessibility
        if (cap.requiresAccessibility) {
            if (com.example.service.AlyaAccessibilityService.instance == null) {
                return CapabilityAvailability.SUPPORTED_BUT_SPECIAL_ACCESS_REQUIRED
            }
        }

        // Check overlay
        if (cap.requiresOverlay) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !android.provider.Settings.canDrawOverlays(context)) {
                return CapabilityAvailability.SUPPORTED_BUT_SPECIAL_ACCESS_REQUIRED
            }
        }

        if (cap.onlineRequired) {
            return CapabilityAvailability.ONLINE_REQUIRED
        }

        return CapabilityAvailability.SUPPORTED_AND_WORKING
    }

    companion object {
        @Volatile
        private var INSTANCE: AlyaCapabilityRegistry? = null

        fun getInstance(context: Context): AlyaCapabilityRegistry {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AlyaCapabilityRegistry(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
