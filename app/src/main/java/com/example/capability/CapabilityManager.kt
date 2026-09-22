package com.example.capability

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.AlyaApplication
import com.example.service.AlyaAccessibilityService
import com.example.service.WakeWordService
import com.example.util.CompatUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Status of a capability or permission on the device.
 */
enum class CapabilityStatus {
    GRANTED,
    DENIED,
    PERMANENTLY_DENIED,
    RESTRICTED_BY_ANDROID,
    RESTRICTED_BY_DEVICE,
    NOT_CONFIGURED,
    NOT_SUPPORTED
}

/**
 * Category grouping for capabilities.
 */
enum class CapabilityCategory(val title: String) {
    AUDIO_AND_VOICE("Audio & Speech Engine"),
    BACKGROUND_AND_WAKEUP("Wake-Up & Background Continuity"),
    DEVICE_CONTROL("Device Control & Automation"),
    STORAGE_AND_FILES("Storage & File Access"),
    CONNECTIVITY_AND_OFFLINE("Network & Offline Intelligence"),
    PHONE_AND_CONTACTS("Phone & Contacts")
}

/**
 * Rich model representing a single capability, its real system state,
 * and user-facing explanation & fallback.
 */
data class CapabilityItem(
    val id: String,
    val title: String,
    val category: CapabilityCategory,
    val isRequired: Boolean,
    val status: CapabilityStatus,
    val statusBadge: String,
    val description: String,
    val whyNeeded: String,
    val fallbackBehavior: String,
    val actionType: ActionType,
    val permissionKey: String? = null
) {
    enum class ActionType {
        REQUEST_PERMISSION,
        OPEN_APP_SETTINGS,
        OPEN_OVERLAY_SETTINGS,
        OPEN_ACCESSIBILITY_SETTINGS,
        OPEN_BATTERY_SETTINGS,
        OPEN_ALARM_SETTINGS,
        OPEN_FILE_ACCESS_SETTINGS,
        OPEN_VOICE_TRAINING,
        TOGGLE_STATE,
        NONE
    }
}

/**
 * System health overview summarizing capability statuses.
 */
data class SystemHealthOverview(
    val totalCount: Int,
    val grantedCount: Int,
    val warningCount: Int,
    val criticalMissingCount: Int,
    val healthPercentage: Int,
    val healthLabel: String
)

/**
 * Real-time Capability & Permission Manager for Alya Assistant.
 * Auto-checks real hardware, OS services, and Android permission states.
 * Never reports fake or hardcoded 'true' values.
 */
class CapabilityManager(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _capabilities = MutableStateFlow<List<CapabilityItem>>(emptyList())
    val capabilities: StateFlow<List<CapabilityItem>> = _capabilities.asStateFlow()

    private val _healthOverview = MutableStateFlow(
        SystemHealthOverview(0, 0, 0, 0, 0, "Checking...")
    )
    val healthOverview: StateFlow<SystemHealthOverview> = _healthOverview.asStateFlow()

    private val _isChecking = MutableStateFlow(false)
    val isChecking: StateFlow<Boolean> = _isChecking.asStateFlow()

    private var ttsEngineAvailable = false

    init {
        checkTtsAvailability()
        refreshCapabilities()
    }

    private fun checkTtsAvailability() {
        try {
            var tempTts: TextToSpeech? = null
            tempTts = TextToSpeech(context.applicationContext) { status ->
                ttsEngineAvailable = (status == TextToSpeech.SUCCESS)
                try {
                    tempTts?.shutdown()
                } catch (e: Exception) { android.util.Log.e("Alya", "Exception handled", e) }
                refreshCapabilities()
            }
        } catch (e: Exception) {
            Log.w(TAG, "TTS availability probe failed: ${e.message}")
        }
    }

    /**
     * Inspects the real hardware and OS status across Android 10 through Android 17+.
     */
    fun refreshCapabilities() {
        scope.launch {
            _isChecking.value = true
            try {
                val list = mutableListOf<CapabilityItem>()
                val app = context.applicationContext as? AlyaApplication
                val prefs = app?.preferencesManager

                // 1. Microphone Hardware & Permission
                val hasMicPerm = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                val micStatus = if (hasMicPerm) {
                    CapabilityStatus.GRANTED
                } else {
                    CapabilityStatus.DENIED
                }
                list.add(
                    CapabilityItem(
                        id = "mic",
                        title = "Microphone & Speech Capture",
                        category = CapabilityCategory.AUDIO_AND_VOICE,
                        isRequired = true,
                        status = micStatus,
                        statusBadge = if (hasMicPerm) "Granted" else "Required",
                        description = "Enables voice recognition, live speech commands, and wake-word audio streaming.",
                        whyNeeded = "Required to capture your spoken words and allow Alya to listen to voice requests.",
                        fallbackBehavior = "If denied, voice input is disabled. You can still interact with Alya via typed text.",
                        actionType = if (hasMicPerm) CapabilityItem.ActionType.NONE else CapabilityItem.ActionType.REQUEST_PERMISSION,
                        permissionKey = Manifest.permission.RECORD_AUDIO
                    )
                )

                // 2. Speech Recognizer System Engine
                val isSpeechRecognitionAvailable = SpeechRecognizer.isRecognitionAvailable(context)
                list.add(
                    CapabilityItem(
                        id = "speech_engine",
                        title = "On-Device Speech Recognizer",
                        category = CapabilityCategory.AUDIO_AND_VOICE,
                        isRequired = true,
                        status = if (isSpeechRecognitionAvailable) CapabilityStatus.GRANTED else CapabilityStatus.NOT_SUPPORTED,
                        statusBadge = if (isSpeechRecognitionAvailable) "Engine Active" else "Unavailable",
                        description = "Android system speech recognition engine converting audio into real-time text transcription.",
                        whyNeeded = "Used for continuous speech recognition, partial transcription, and conversational turn-taking.",
                        fallbackBehavior = "Falls back to Google Speech Services or offline TFLite acoustic matcher if available.",
                        actionType = CapabilityItem.ActionType.NONE
                    )
                )

                // 3. Text-To-Speech (TTS) Voice Engine
                val ttsStatus = if (ttsEngineAvailable) CapabilityStatus.GRANTED else CapabilityStatus.NOT_CONFIGURED
                list.add(
                    CapabilityItem(
                        id = "tts_engine",
                        title = "Text-To-Speech (TTS) Voice Engine",
                        category = CapabilityCategory.AUDIO_AND_VOICE,
                        isRequired = true,
                        status = ttsStatus,
                        statusBadge = if (ttsEngineAvailable) "Voice Active" else "Checking Engine",
                        description = "High-fidelity female voice engine audibly speaking every reply through device speaker.",
                        whyNeeded = "Required for hands-free audio conversation and natural voice feedback.",
                        fallbackBehavior = "If unavailable, replies are displayed as text messages in the chat interface.",
                        actionType = CapabilityItem.ActionType.NONE
                    )
                )

                // 4. User Voice Profile Enrollment
                val isVoiceProfileSet = prefs?.isVoiceProfileSet?.value ?: false
                val isWakeUpActivated = prefs?.isWakeUpActivated?.value ?: true
                val voiceProfileStatus = when {
                    isVoiceProfileSet && isWakeUpActivated -> CapabilityStatus.GRANTED
                    isVoiceProfileSet && !isWakeUpActivated -> CapabilityStatus.NOT_CONFIGURED
                    else -> CapabilityStatus.NOT_CONFIGURED
                }
                list.add(
                    CapabilityItem(
                        id = "voice_profile",
                        title = "User Voice Profile & Calibration",
                        category = CapabilityCategory.AUDIO_AND_VOICE,
                        isRequired = false,
                        status = voiceProfileStatus,
                        statusBadge = when {
                            isVoiceProfileSet && isWakeUpActivated -> "Profile Active"
                            isVoiceProfileSet && !isWakeUpActivated -> "Wake-Up Paused"
                            else -> "Train 3 Samples"
                        },
                        description = "Calibrated acoustic voice profile recognizing your voice on 'Hey Alya', 'Alia', and 'Seno'.",
                        whyNeeded = "Enhances wake-word accuracy and ensures Alya responds reliably to your unique voice timbre.",
                        fallbackBehavior = "General acoustic detection is used if voice profile training is not completed.",
                        actionType = CapabilityItem.ActionType.OPEN_VOICE_TRAINING
                    )
                )

                // 5. Wake-Up Detection Engine
                val wakeManager = app?.wakeWordManager
                val isWakeReady = wakeManager?.isListening?.value ?: false
                val wakeStatus = when {
                    !hasMicPerm -> CapabilityStatus.DENIED
                    !isWakeUpActivated -> CapabilityStatus.NOT_CONFIGURED
                    isWakeReady -> CapabilityStatus.GRANTED
                    else -> CapabilityStatus.NOT_CONFIGURED
                }
                list.add(
                    CapabilityItem(
                        id = "wake_word_engine",
                        title = "Wake-Up Detection Engine",
                        category = CapabilityCategory.BACKGROUND_AND_WAKEUP,
                        isRequired = false,
                        status = wakeStatus,
                        statusBadge = when {
                            !hasMicPerm -> "Mic Denied"
                            !isWakeUpActivated -> "Deactivated"
                            isWakeReady -> "Active & Listening"
                            else -> "Standby"
                        },
                        description = "Dedicated ultra-low-power acoustic classifier monitoring for 'Alia', 'Alya', and 'Seno'.",
                        whyNeeded = "Instantly brings up the floating listening pop-up hands-free without touching the screen.",
                        fallbackBehavior = "You can tap the microphone button or the wake activation button at any time.",
                        actionType = if (!isWakeUpActivated) CapabilityItem.ActionType.TOGGLE_STATE else CapabilityItem.ActionType.NONE
                    )
                )

                // 6. Notifications (Android 13+ / API 33+)
                val hasNotificationPerm = CompatUtils.hasNotificationPermission(context)
                val notifStatus = if (hasNotificationPerm) {
                    CapabilityStatus.GRANTED
                } else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        CapabilityStatus.DENIED
                    } else {
                        CapabilityStatus.GRANTED
                    }
                }
                list.add(
                    CapabilityItem(
                        id = "notifications",
                        title = "Notifications & Live Alerts",
                        category = CapabilityCategory.BACKGROUND_AND_WAKEUP,
                        isRequired = true,
                        status = notifStatus,
                        statusBadge = if (hasNotificationPerm) "Granted" else "Permission Needed",
                        description = "Displays persistent foreground assistant notification and background status updates.",
                        whyNeeded = "Required on Android 13+ to maintain background services and inform you when Alya is active.",
                        fallbackBehavior = "If denied, background service may be restricted or terminated by Android OS.",
                        actionType = if (hasNotificationPerm) CapabilityItem.ActionType.NONE else CapabilityItem.ActionType.REQUEST_PERMISSION,
                        permissionKey = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            Manifest.permission.POST_NOTIFICATIONS
                        } else null
                    )
                )

                // 7. Foreground Service & Background Continuity
                val isServiceRunning = WakeWordService.isRunning
                val fgsStatus = if (isServiceRunning) CapabilityStatus.GRANTED else CapabilityStatus.NOT_CONFIGURED
                list.add(
                    CapabilityItem(
                        id = "foreground_service",
                        title = "Background Continuity Service",
                        category = CapabilityCategory.BACKGROUND_AND_WAKEUP,
                        isRequired = true,
                        status = fgsStatus,
                        statusBadge = if (isServiceRunning) "Running" else "Stopped",
                        description = "Foreground Service with microphone type keeping wake-up active when app is minimized.",
                        whyNeeded = "Ensures Alya stays alive in the background without being killed during multitasking.",
                        fallbackBehavior = "Wake-up will only operate while the app is actively visible on screen.",
                        actionType = CapabilityItem.ActionType.TOGGLE_STATE
                    )
                )

                // 8. Battery Optimization Exemption
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                val isIgnoringBattery = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
                } else true
                
                val batteryStatus = if (isIgnoringBattery) CapabilityStatus.GRANTED else CapabilityStatus.DENIED
                list.add(
                    CapabilityItem(
                        id = "battery_opt",
                        title = "Unrestricted Battery Background Access",
                        category = CapabilityCategory.BACKGROUND_AND_WAKEUP,
                        isRequired = false,
                        status = batteryStatus,
                        statusBadge = if (isIgnoringBattery) "Unrestricted" else "Optimizing (May Kill)",
                        description = "Exempts Alya from aggressive Android Doze mode and OEM background killing.",
                        whyNeeded = "Allows wake-word monitoring to continue seamlessly while screen is locked or idle.",
                        fallbackBehavior = "Android may freeze or suspend the background listener when device enters deep sleep.",
                        actionType = if (isIgnoringBattery) CapabilityItem.ActionType.NONE else CapabilityItem.ActionType.OPEN_BATTERY_SETTINGS
                    )
                )

                // 9. Floating Popup Permissions / System Alert Window
                val canDrawOverlay = Settings.canDrawOverlays(context)
                val overlayStatus = if (canDrawOverlay) CapabilityStatus.GRANTED else CapabilityStatus.DENIED
                list.add(
                    CapabilityItem(
                        id = "system_overlay",
                        title = "Floating Popup Permissions",
                        category = CapabilityCategory.BACKGROUND_AND_WAKEUP,
                        isRequired = false,
                        status = overlayStatus,
                        statusBadge = if (canDrawOverlay) "Active" else "Recommended",
                        description = "Special access to display the floating orb and listening pop-up over other apps.",
                        whyNeeded = "The assistant's visual popup cannot appear over other apps without this special permission.",
                        fallbackBehavior = "Alya will still work, but you'll only see the visual pop-up while inside the main Alya app.",
                        actionType = if (canDrawOverlay) CapabilityItem.ActionType.NONE else CapabilityItem.ActionType.OPEN_OVERLAY_SETTINGS
                    )
                )

                // 10. Exact Alarms & Routines (Android 12+ / API 31+)
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                val canScheduleExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    alarmManager?.canScheduleExactAlarms() ?: false
                } else {
                    true
                }
                val alarmStatus = if (canScheduleExact) CapabilityStatus.GRANTED else CapabilityStatus.RESTRICTED_BY_ANDROID
                list.add(
                    CapabilityItem(
                        id = "exact_alarms",
                        title = "Exact Alarms & Voice Timers",
                        category = CapabilityCategory.DEVICE_CONTROL,
                        isRequired = false,
                        status = alarmStatus,
                        statusBadge = if (canScheduleExact) "Active" else "Permission Needed",
                        description = "Allows scheduling precise voice alarms (e.g. 'Hey Alya, set an alarm for 7 AM') and reminders.",
                        whyNeeded = "Required by Android 12+ to deliver alarms down to the exact second.",
                        fallbackBehavior = "Alarms may be delayed up to 10 minutes by Android battery management if denied.",
                        actionType = if (canScheduleExact) CapabilityItem.ActionType.NONE else CapabilityItem.ActionType.OPEN_ALARM_SETTINGS
                    )
                )

                // 11. Real-Time Location for Weather
                val hasFineLocation = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                val hasCoarseLocation = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                val locationStatus = when {
                    hasFineLocation || hasCoarseLocation -> CapabilityStatus.GRANTED
                    else -> CapabilityStatus.DENIED
                }
                list.add(
                    CapabilityItem(
                        id = "location_weather",
                        title = "Location for Accurate Weather",
                        category = CapabilityCategory.DEVICE_CONTROL,
                        isRequired = false,
                        status = locationStatus,
                        statusBadge = if (hasFineLocation || hasCoarseLocation) "Granted" else "Setup Needed",
                        description = "Resolves your actual locality for commands like 'Hey Alya, what is the temperature outside?'.",
                        whyNeeded = "Required to fetch genuine, local meteorological data without guessing a random city.",
                        fallbackBehavior = "Weather queries will require you to explicitly state a city name (e.g. 'weather in Tokyo').",
                        actionType = if (hasFineLocation || hasCoarseLocation) CapabilityItem.ActionType.NONE else CapabilityItem.ActionType.REQUEST_PERMISSION,
                        permissionKey = Manifest.permission.ACCESS_FINE_LOCATION
                    )
                )

                // 12. Phone Calls & Contacts
                val hasCallPhone = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CALL_PHONE
                ) == PackageManager.PERMISSION_GRANTED
                val hasReadContacts = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_CONTACTS
                ) == PackageManager.PERMISSION_GRANTED
                val phoneStatus = when {
                    hasCallPhone && hasReadContacts -> CapabilityStatus.GRANTED
                    hasCallPhone || hasReadContacts -> CapabilityStatus.RESTRICTED_BY_ANDROID
                    else -> CapabilityStatus.DENIED
                }
                list.add(
                    CapabilityItem(
                        id = "phone_contacts",
                        title = "Phone Calling & Contacts Access",
                        category = CapabilityCategory.PHONE_AND_CONTACTS,
                        isRequired = false,
                        status = phoneStatus,
                        statusBadge = when {
                            hasCallPhone && hasReadContacts -> "Full Access"
                            hasCallPhone || hasReadContacts -> "Partial Access"
                            else -> "Setup Needed"
                        },
                        description = "Enables hands-free calling, call answering, and dialing contacts by name.",
                        whyNeeded = "Allows Alya to resolve contact names and place voice calls on your behalf.",
                        fallbackBehavior = "Alya will open the system dialer app with the number filled for manual confirmation.",
                        actionType = if (hasCallPhone && hasReadContacts) CapabilityItem.ActionType.NONE else CapabilityItem.ActionType.REQUEST_PERMISSION,
                        permissionKey = Manifest.permission.CALL_PHONE
                    )
                )

                // 13. Bluetooth Audio & Device Connect (Android 12+ / API 31+)
                val hasBtConnect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED
                } else {
                    true
                }
                val btStatus = if (hasBtConnect) CapabilityStatus.GRANTED else CapabilityStatus.DENIED
                list.add(
                    CapabilityItem(
                        id = "bluetooth_connect",
                        title = "Bluetooth Headset & Car Audio",
                        category = CapabilityCategory.DEVICE_CONTROL,
                        isRequired = false,
                        status = btStatus,
                        statusBadge = if (hasBtConnect) "Connected" else "Optional",
                        description = "Routes voice capture and TTS playback to Bluetooth earphones, earbuds, or car speakers.",
                        whyNeeded = "Required on Android 12+ to detect connected Bluetooth headsets for hands-free audio.",
                        fallbackBehavior = "Audio automatically routes through the device internal microphone and speaker.",
                        actionType = if (hasBtConnect) CapabilityItem.ActionType.NONE else CapabilityItem.ActionType.REQUEST_PERMISSION,
                        permissionKey = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            Manifest.permission.BLUETOOTH_CONNECT
                        } else null
                    )
                )

                // 14. Accessibility Service & Deep Phone Control
                val isAccessibilityGranted = AlyaAccessibilityService.checkAccessibilityPermission(context) == com.example.service.AccessibilityPermissionStatus.GRANTED
                val isAccessibilityActive = isAccessibilityGranted || AlyaAccessibilityService.instance != null
                val accessStatus = if (isAccessibilityActive) CapabilityStatus.GRANTED else CapabilityStatus.NOT_CONFIGURED
                list.add(
                    CapabilityItem(
                        id = "accessibility_service",
                        title = "Accessibility Automation Service",
                        category = CapabilityCategory.DEVICE_CONTROL,
                        isRequired = false,
                        status = accessStatus,
                        statusBadge = if (isAccessibilityActive) "Active" else "Setup Needed",
                        description = "Enables deep app control: opening apps, tapping buttons, scrolling, and multi-step tasks.",
                        whyNeeded = "Empowers Alya to execute complex multi-step user actions across installed applications.",
                        fallbackBehavior = "Alya falls back to standard Android Intents (e.g. launching apps without deep UI tapping).",
                        actionType = if (isAccessibilityActive) CapabilityItem.ActionType.NONE else CapabilityItem.ActionType.OPEN_ACCESSIBILITY_SETTINGS
                    )
                )

                // 15. Special File Access & Storage Management
                val hasAllFilesAccess = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    android.os.Environment.isExternalStorageManager()
                } else {
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    ) == PackageManager.PERMISSION_GRANTED
                }
                val fileStatus = if (hasAllFilesAccess) CapabilityStatus.GRANTED else CapabilityStatus.RESTRICTED_BY_ANDROID
                list.add(
                    CapabilityItem(
                        id = "special_file_access",
                        title = "Special All Files & Document Access",
                        category = CapabilityCategory.STORAGE_AND_FILES,
                        isRequired = false,
                        status = fileStatus,
                        statusBadge = if (hasAllFilesAccess) "Full Storage Access" else "Permission Needed",
                        description = "Enables Alya to access, organize, read, and create documents and files via voice commands.",
                        whyNeeded = "Required on Android 11+ to manage device storage, documents, and backups directly with voice conversation.",
                        fallbackBehavior = "File access will be restricted to private app sandbox storage only.",
                        actionType = if (hasAllFilesAccess) CapabilityItem.ActionType.NONE else CapabilityItem.ActionType.OPEN_FILE_ACCESS_SETTINGS
                    )
                )

                // 16. Media & Audio Files
                val hasMediaImages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
                } else {
                    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                }
                val mediaStatus = if (hasMediaImages) CapabilityStatus.GRANTED else CapabilityStatus.DENIED
                list.add(
                    CapabilityItem(
                        id = "media_access",
                        title = "Media & Photo Library Access",
                        category = CapabilityCategory.STORAGE_AND_FILES,
                        isRequired = false,
                        status = mediaStatus,
                        statusBadge = if (hasMediaImages) "Active" else "Optional",
                        description = "Allows finding, sharing, and viewing photos, videos, and music files via assistant voice conversation.",
                        whyNeeded = "Used when asking Alya to show recent photos or access audio recordings.",
                        fallbackBehavior = "Media file search falls back to system photo picker.",
                        actionType = if (hasMediaImages) CapabilityItem.ActionType.NONE else CapabilityItem.ActionType.REQUEST_PERMISSION,
                        permissionKey = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
                    )
                )

                // 16b. Media & Audio Files Permission
                val hasMediaAudio = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
                } else {
                    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                }
                val mediaAudioStatus = if (hasMediaAudio) CapabilityStatus.GRANTED else CapabilityStatus.DENIED
                list.add(
                    CapabilityItem(
                        id = "media_audio_access",
                        title = "Media & Audio Permission",
                        category = CapabilityCategory.STORAGE_AND_FILES,
                        isRequired = false,
                        status = mediaAudioStatus,
                        statusBadge = if (hasMediaAudio) "Active" else "Optional",
                        description = "Allows playing back voice clips, indexing custom audio commands, and accessing music files.",
                        whyNeeded = "Used when requesting Alya to open, play, or process local audio and media clips directly.",
                        fallbackBehavior = "Audio access falls back to in-app assets or streaming models.",
                        actionType = if (hasMediaAudio) CapabilityItem.ActionType.NONE else CapabilityItem.ActionType.REQUEST_PERMISSION,
                        permissionKey = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
                    )
                )

                // 17. Internet & Offline Intelligence
                val connMgr = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                val activeNet = connMgr?.activeNetwork
                val netCaps = activeNet?.let { connMgr.getNetworkCapabilities(it) }
                val hasInternet = netCaps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
                        netCaps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                val netStatus = if (hasInternet) CapabilityStatus.GRANTED else CapabilityStatus.NOT_CONFIGURED
                list.add(
                    CapabilityItem(
                        id = "network_intelligence",
                        title = "Internet & Offline Intelligence",
                        category = CapabilityCategory.CONNECTIVITY_AND_OFFLINE,
                        isRequired = true,
                        status = netStatus,
                        statusBadge = if (hasInternet) "Online & Offline Ready" else "Offline Mode",
                        description = "Monitors network state. Automatically routes to offline acoustic matcher and cached tools when offline.",
                        whyNeeded = "Ensures graceful recovery and honest communication during connectivity drops.",
                        fallbackBehavior = "Local offline wake-word and system device toggles remain fully functional without internet.",
                        actionType = CapabilityItem.ActionType.NONE
                    )
                )

                _capabilities.value = list

                // Compute System Health
                val total = list.size
                val granted = list.count { it.status == CapabilityStatus.GRANTED }
                val warnings = list.count { it.status == CapabilityStatus.RESTRICTED_BY_DEVICE || it.status == CapabilityStatus.RESTRICTED_BY_ANDROID }
                val criticalMissing = list.count { it.isRequired && it.status != CapabilityStatus.GRANTED }
                val healthPct = if (total > 0) (granted * 100) / total else 0
                val healthLabel = when {
                    criticalMissing > 0 -> "Action Required"
                    healthPct >= 80 -> "Optimal Setup"
                    healthPct >= 50 -> "Good • Minor Enhancements"
                    else -> "Setup Recommended"
                }

                _healthOverview.value = SystemHealthOverview(
                    totalCount = total,
                    grantedCount = granted,
                    warningCount = warnings,
                    criticalMissingCount = criticalMissing,
                    healthPercentage = healthPct,
                    healthLabel = healthLabel
                )

            } catch (e: Exception) {
                Log.e(TAG, "Error evaluating system capabilities: ${e.message}", e)
            } finally {
                _isChecking.value = false
            }
        }
    }

    /**
     * Storage & Media Runtime Permission Helper.
     * Manages backward-compatible migration from Android 10 legacy storage
     * to Android 13/14+ granular media and photo picker permissions.
     */
    class StoragePermissionHelper(private val context: Context) {

        enum class StorageAccessLevel {
            FULL_DEVICE_STORAGE,   // MANAGE_EXTERNAL_STORAGE (Android 11+)
            LEGACY_READ_WRITE,     // READ/WRITE_EXTERNAL_STORAGE (Android 10 & below)
            GRANULAR_MEDIA_FULL,   // READ_MEDIA_IMAGES, VIDEO, AUDIO (Android 13+)
            GRANULAR_MEDIA_PARTIAL,// READ_MEDIA_VISUAL_USER_SELECTED (Android 14+)
            SANDBOX_ONLY           // App-specific internal & external files directory only
        }

        data class StorageState(
            val accessLevel: StorageAccessLevel,
            val canReadImages: Boolean,
            val canReadVideos: Boolean,
            val canReadAudio: Boolean,
            val isPartialVisualAccess: Boolean,
            val hasAllFilesAccess: Boolean,
            val requiredPermissionsToRequest: List<String>
        )

        /**
         * Resolves the current real storage and media permission state for the running Android OS version.
         */
        fun getStorageState(): StorageState {
            val sdk = Build.VERSION.SDK_INT
            val hasAllFiles = if (sdk >= Build.VERSION_CODES.R) {
                android.os.Environment.isExternalStorageManager()
            } else {
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            }

            var canReadImages = false
            var canReadVideos = false
            var canReadAudio = false
            var isPartialVisual = false

            when {
                // Android 14+ (API 34+): Granular Photo/Video/Audio + Visual User Selected (Partial Access)
                sdk >= 34 -> {
                    val fullImages = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
                    val fullVideos = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
                    val partialVisual = ContextCompat.checkSelfPermission(context, "android.permission.READ_MEDIA_VISUAL_USER_SELECTED") == PackageManager.PERMISSION_GRANTED
                    canReadAudio = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED

                    canReadImages = fullImages || partialVisual
                    canReadVideos = fullVideos || partialVisual
                    isPartialVisual = partialVisual && (!fullImages || !fullVideos)
                }
                // Android 13 (API 33, Tiramisu): Granular Media Permissions
                sdk >= Build.VERSION_CODES.TIRAMISU -> {
                    canReadImages = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
                    canReadVideos = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
                    canReadAudio = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
                }
                // Android 10 - 12 (API 29 - 32): Legacy READ_EXTERNAL_STORAGE
                else -> {
                    val legacyRead = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                    canReadImages = legacyRead
                    canReadVideos = legacyRead
                    canReadAudio = legacyRead
                }
            }

            val accessLevel = when {
                hasAllFiles -> StorageAccessLevel.FULL_DEVICE_STORAGE
                sdk >= 34 && isPartialVisual -> StorageAccessLevel.GRANULAR_MEDIA_PARTIAL
                sdk >= Build.VERSION_CODES.TIRAMISU && canReadImages && canReadAudio -> StorageAccessLevel.GRANULAR_MEDIA_FULL
                sdk < Build.VERSION_CODES.TIRAMISU && canReadImages -> StorageAccessLevel.LEGACY_READ_WRITE
                else -> StorageAccessLevel.SANDBOX_ONLY
            }

            return StorageState(
                accessLevel = accessLevel,
                canReadImages = canReadImages,
                canReadVideos = canReadVideos,
                canReadAudio = canReadAudio,
                isPartialVisualAccess = isPartialVisual,
                hasAllFilesAccess = hasAllFiles,
                requiredPermissionsToRequest = getPermissionsForMigration()
            )
        }

        /**
         * Computes the exact array of permission strings to request based on Android version.
         */
        fun getPermissionsForMigration(): List<String> {
            val sdk = Build.VERSION.SDK_INT
            return when {
                // Android 14+: Request granular media + visual user selected
                sdk >= 34 -> listOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_AUDIO,
                    "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"
                )
                // Android 13: Request granular media permissions
                sdk >= Build.VERSION_CODES.TIRAMISU -> listOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_AUDIO
                )
                // Android 10 - 12: Request classic read external storage
                else -> listOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE
                )
            }
        }
    }

    val storagePermissionHelper = StoragePermissionHelper(context)

    companion object {
        private const val TAG = "CapabilityManager"

        @Volatile
        private var instance: CapabilityManager? = null

        fun getInstance(context: Context): CapabilityManager {
            return instance ?: synchronized(this) {
                instance ?: CapabilityManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
