package com.example.domain.actions

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import com.example.data.local.AlyaDatabase
import com.example.data.local.entity.OfflineCommandEntity
import com.example.domain.tools.ActionResultStatus
import com.example.domain.tools.ToolExecutionResult
import com.example.util.diagnostics.DiagnosticLogManager
import com.example.util.diagnostics.DiagnosticStage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

data class OfflineIntentMapping(
    val triggerPhrases: List<String>,
    val actionType: String,
    val systemIntentAction: String?,
    val extraCategory: String? = null,
    val feedbackText: String
)

/**
 * CommandRegistry (Alya v2.3.0)
 * 
 * Room Database & local JSON command registry mapping offline voice commands to
 * direct device control actions (Volume adjustment, Wi-Fi toggling, Flashlight, Bluetooth).
 * Ensures instantaneous offline execution with zero internet connection required.
 */
class CommandRegistry(private val context: Context) {

    private val jsonFile = File(context.filesDir, "offline_command_registry.json")
    private val mappings = mutableListOf<OfflineIntentMapping>()
    private val diagLog = DiagnosticLogManager.instance
    private val offlineDao = AlyaDatabase.getDatabase(context).offlineCommandDao()
    private val localDeviceController = LocalDeviceController(context)
    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        loadOrCreateRegistry()
    }

    @Synchronized
    private fun loadOrCreateRegistry() {
        try {
            if (!jsonFile.exists()) {
                val defaultJson = createDefaultRegistryJson()
                jsonFile.writeText(defaultJson)
                Log.i(TAG, "[COMMAND_REGISTRY] Created initial offline_command_registry.json with default mappings.")
            }

            val jsonContent = jsonFile.readText()
            parseRegistryJson(jsonContent)
            Log.i(TAG, "[COMMAND_REGISTRY] Successfully loaded ${mappings.size} offline intent mappings.")

            // Seed & sync Room DB
            seedAndSyncRoomDatabase()
        } catch (e: Exception) {
            Log.e(TAG, "[COMMAND_REGISTRY] Error loading JSON registry: ${e.message}", e)
            populateHardcodedDefaults()
        }
    }

    private fun seedAndSyncRoomDatabase() {
        scope.launch {
            try {
                val roomEntities = mutableListOf<OfflineCommandEntity>()

                // 1. Device control entities for Room DB
                val deviceControlDefaults = listOf(
                    OfflineCommandEntity("volume up", "VOLUME_UP", extraCategory = "VOLUME", feedbackText = "Increasing media volume."),
                    OfflineCommandEntity("increase volume", "VOLUME_UP", extraCategory = "VOLUME", feedbackText = "Increasing media volume."),
                    OfflineCommandEntity("volume badhao", "VOLUME_UP", extraCategory = "VOLUME", feedbackText = "Media volume badha di hai."),
                    OfflineCommandEntity("awaj badhao", "VOLUME_UP", extraCategory = "VOLUME", feedbackText = "Awaj badha di hai."),
                    OfflineCommandEntity("sound up", "VOLUME_UP", extraCategory = "VOLUME", feedbackText = "Increasing volume."),

                    OfflineCommandEntity("volume down", "VOLUME_DOWN", extraCategory = "VOLUME", feedbackText = "Decreasing media volume."),
                    OfflineCommandEntity("decrease volume", "VOLUME_DOWN", extraCategory = "VOLUME", feedbackText = "Decreasing media volume."),
                    OfflineCommandEntity("volume kam karo", "VOLUME_DOWN", extraCategory = "VOLUME", feedbackText = "Media volume kam kar di hai."),
                    OfflineCommandEntity("awaj kam karo", "VOLUME_DOWN", extraCategory = "VOLUME", feedbackText = "Awaj kam kar di hai."),
                    OfflineCommandEntity("sound down", "VOLUME_DOWN", extraCategory = "VOLUME", feedbackText = "Decreasing volume."),

                    OfflineCommandEntity("mute", "MUTE", extraCategory = "VOLUME", feedbackText = "Muting device audio."),
                    OfflineCommandEntity("mute phone", "MUTE", extraCategory = "VOLUME", feedbackText = "Muting phone."),
                    OfflineCommandEntity("silent mode", "MUTE", extraCategory = "VOLUME", feedbackText = "Setting phone to silent mode."),
                    OfflineCommandEntity("unmute", "UNMUTE", extraCategory = "VOLUME", feedbackText = "Unmuting device audio."),

                    OfflineCommandEntity("turn on wifi", "WIFI_ON", extraCategory = "CONNECTIVITY", feedbackText = "Turning on Wi-Fi and connecting to available network."),
                    OfflineCommandEntity("turn on wifi and connect", "WIFI_ON", extraCategory = "CONNECTIVITY", feedbackText = "Turning on Wi-Fi and connecting to available network."),
                    OfflineCommandEntity("connect to wifi", "WIFI_ON", extraCategory = "CONNECTIVITY", feedbackText = "Connecting to available Wi-Fi network."),
                    OfflineCommandEntity("connect wifi", "WIFI_ON", extraCategory = "CONNECTIVITY", feedbackText = "Connecting to available Wi-Fi network."),
                    OfflineCommandEntity("turn off wifi", "WIFI_OFF", extraCategory = "CONNECTIVITY", feedbackText = "Turning off Wi-Fi."),
                    OfflineCommandEntity("wifi on", "WIFI_ON", extraCategory = "CONNECTIVITY", feedbackText = "Turning on Wi-Fi and connecting to available network."),
                    OfflineCommandEntity("wifi off", "WIFI_OFF", extraCategory = "CONNECTIVITY", feedbackText = "Turning off Wi-Fi."),
                    OfflineCommandEntity("wifi chalu karo", "WIFI_ON", extraCategory = "CONNECTIVITY", feedbackText = "Wi-Fi chalu karke connect kar diya hai."),
                    OfflineCommandEntity("wifi band karo", "WIFI_OFF", extraCategory = "CONNECTIVITY", feedbackText = "Wi-Fi band kar diya hai."),

                    OfflineCommandEntity("turn on flashlight", "FLASHLIGHT_ON", extraCategory = "HARDWARE", feedbackText = "Turning on flashlight."),
                    OfflineCommandEntity("turn off flashlight", "FLASHLIGHT_OFF", extraCategory = "HARDWARE", feedbackText = "Turning off flashlight."),
                    OfflineCommandEntity("torch on", "FLASHLIGHT_ON", extraCategory = "HARDWARE", feedbackText = "Torch enabled."),
                    OfflineCommandEntity("torch off", "FLASHLIGHT_OFF", extraCategory = "HARDWARE", feedbackText = "Torch disabled."),
                    OfflineCommandEntity("light chalu karo", "FLASHLIGHT_ON", extraCategory = "HARDWARE", feedbackText = "Light chalu kar di hai."),

                    OfflineCommandEntity("turn on bluetooth", "BLUETOOTH_ON", systemIntentAction = Settings.ACTION_BLUETOOTH_SETTINGS, extraCategory = "CONNECTIVITY", feedbackText = "Opening Bluetooth settings."),
                    OfflineCommandEntity("turn off bluetooth", "BLUETOOTH_OFF", systemIntentAction = Settings.ACTION_BLUETOOTH_SETTINGS, extraCategory = "CONNECTIVITY", feedbackText = "Opening Bluetooth settings."),
                    OfflineCommandEntity("bluetooth on", "BLUETOOTH_ON", systemIntentAction = Settings.ACTION_BLUETOOTH_SETTINGS, extraCategory = "CONNECTIVITY", feedbackText = "Opening Bluetooth settings."),
                    OfflineCommandEntity("bluetooth off", "BLUETOOTH_OFF", systemIntentAction = Settings.ACTION_BLUETOOTH_SETTINGS, extraCategory = "CONNECTIVITY", feedbackText = "Opening Bluetooth settings."),
                    OfflineCommandEntity("bluetooth chalu karo", "BLUETOOTH_ON", systemIntentAction = Settings.ACTION_BLUETOOTH_SETTINGS, extraCategory = "CONNECTIVITY", feedbackText = "Bluetooth chalu karne ki settings kholi hai."),
                    OfflineCommandEntity("bluetooth band karo", "BLUETOOTH_OFF", systemIntentAction = Settings.ACTION_BLUETOOTH_SETTINGS, extraCategory = "CONNECTIVITY", feedbackText = "Bluetooth band karne ki settings kholi hai."),
                    OfflineCommandEntity("connect bluetooth", "BLUETOOTH_ON", systemIntentAction = Settings.ACTION_BLUETOOTH_SETTINGS, extraCategory = "CONNECTIVITY", feedbackText = "Opening Bluetooth settings."),

                    OfflineCommandEntity("brightness up", "BRIGHTNESS_UP", extraCategory = "DISPLAY", feedbackText = "Increasing screen brightness."),
                    OfflineCommandEntity("increase brightness", "BRIGHTNESS_UP", extraCategory = "DISPLAY", feedbackText = "Increasing screen brightness."),
                    OfflineCommandEntity("brightness badhao", "BRIGHTNESS_UP", extraCategory = "DISPLAY", feedbackText = "Brightness badha di hai."),
                    OfflineCommandEntity("brightness high", "BRIGHTNESS_UP", extraCategory = "DISPLAY", feedbackText = "Setting screen brightness high."),
                    OfflineCommandEntity("max brightness", "BRIGHTNESS_UP", extraCategory = "DISPLAY", feedbackText = "Setting maximum screen brightness."),
                    OfflineCommandEntity("brightness down", "BRIGHTNESS_DOWN", extraCategory = "DISPLAY", feedbackText = "Decreasing screen brightness."),
                    OfflineCommandEntity("decrease brightness", "BRIGHTNESS_DOWN", extraCategory = "DISPLAY", feedbackText = "Decreasing screen brightness."),
                    OfflineCommandEntity("brightness kam karo", "BRIGHTNESS_DOWN", extraCategory = "DISPLAY", feedbackText = "Brightness kam kar di hai."),
                    OfflineCommandEntity("brightness low", "BRIGHTNESS_DOWN", extraCategory = "DISPLAY", feedbackText = "Setting screen brightness low."),
                    OfflineCommandEntity("brightness settings", "BRIGHTNESS_SETTINGS", systemIntentAction = Settings.ACTION_DISPLAY_SETTINGS, extraCategory = "DISPLAY", feedbackText = "Opening Display & Brightness settings."),
                    OfflineCommandEntity("display settings", "BRIGHTNESS_SETTINGS", systemIntentAction = Settings.ACTION_DISPLAY_SETTINGS, extraCategory = "DISPLAY", feedbackText = "Opening Display settings.")
                )
                roomEntities.addAll(deviceControlDefaults)

                // 2. Add mappings from JSON
                for (mapping in mappings) {
                    for (trigger in mapping.triggerPhrases) {
                        roomEntities.add(
                            OfflineCommandEntity(
                                triggerPhrase = trigger,
                                actionType = mapping.actionType,
                                systemIntentAction = mapping.systemIntentAction,
                                extraCategory = mapping.extraCategory ?: "SYSTEM_SETTING",
                                feedbackText = mapping.feedbackText,
                                isSystemDefault = true
                            )
                        )
                    }
                }

                offlineDao.insertCommands(roomEntities)
                Log.i(TAG, "[COMMAND_REGISTRY] Persisted ${roomEntities.size} offline command entities to Room DB.")
            } catch (e: Exception) {
                Log.e(TAG, "[COMMAND_REGISTRY] Error syncing Room DB offline commands: ${e.message}")
            }
        }
    }

    private fun parseRegistryJson(jsonString: String) {
        mappings.clear()
        val rootArray = JSONArray(jsonString)
        for (i in 0 until rootArray.length()) {
            val obj = rootArray.getJSONObject(i)
            val triggersArray = obj.getJSONArray("triggers")
            val triggersList = mutableListOf<String>()
            for (j in 0 until triggersArray.length()) {
                triggersList.add(triggersArray.getString(j).lowercase(Locale.ROOT))
            }

            mappings.add(
                OfflineIntentMapping(
                    triggerPhrases = triggersList,
                    actionType = obj.optString("actionType", "SYSTEM_SETTING"),
                    systemIntentAction = obj.optString("intentAction", null),
                    extraCategory = obj.optString("category", null),
                    feedbackText = obj.optString("feedback", "Executing action.")
                )
            )
        }
    }

    /**
     * Fast-resolves incoming user commands against Room database offline command mappings.
     * Executes local device controls (volume, Wi-Fi, flashlight) with zero network dependency.
     */
    fun tryFastResolve(command: String): ToolExecutionResult? {
        val lower = command.trim().lowercase(Locale.ROOT)
        if (lower.isBlank()) return null

        try {
            // Check Room Database first
            val roomMatch = runBlocking(Dispatchers.IO) {
                offlineDao.matchCommandInQuery(lower)
            }

            if (roomMatch != null) {
                diagLog.logEvent(
                    stage = DiagnosticStage.RESOLUTION,
                    command = command,
                    details = "Matched Room DB offline command trigger: '${roomMatch.triggerPhrase}' [Action: ${roomMatch.actionType}]"
                )

                return localDeviceController.executeOfflineCommand(roomMatch, command)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Room DB lookup error: ${e.message}")
        }

        // Fallback to JSON array matching
        for (mapping in mappings) {
            for (trigger in mapping.triggerPhrases) {
                if (lower == trigger || lower.contains(trigger)) {
                    diagLog.logEvent(
                        stage = DiagnosticStage.RESOLUTION,
                        command = command,
                        details = "Fast offline JSON CommandRegistry matched trigger: '$trigger'"
                    )

                    val entity = OfflineCommandEntity(
                        triggerPhrase = trigger,
                        actionType = mapping.actionType,
                        systemIntentAction = mapping.systemIntentAction,
                        feedbackText = mapping.feedbackText
                    )
                    return localDeviceController.executeOfflineCommand(entity, command)
                }
            }
        }
        return null
    }

    /**
     * Dynamically registers a new offline command mapping into Room DB and JSON file.
     */
    @Synchronized
    fun registerCommandMapping(triggers: List<String>, intentAction: String, feedback: String) {
        try {
            val newMapping = OfflineIntentMapping(
                triggerPhrases = triggers.map { it.lowercase(Locale.ROOT) },
                actionType = "SYSTEM_SETTING",
                systemIntentAction = intentAction,
                feedbackText = feedback
            )
            mappings.add(newMapping)

            // Save updated JSON back to file
            val array = JSONArray()
            for (m in mappings) {
                val obj = JSONObject().apply {
                    put("triggers", JSONArray(m.triggerPhrases))
                    put("actionType", m.actionType)
                    put("intentAction", m.systemIntentAction ?: "")
                    put("feedback", m.feedbackText)
                }
                array.put(obj)
            }
            jsonFile.writeText(array.toString(2))

            // Sync with Room DB
            seedAndSyncRoomDatabase()
            Log.i(TAG, "[COMMAND_REGISTRY] Successfully registered new custom offline mapping.")
        } catch (e: Exception) {
            Log.e(TAG, "[COMMAND_REGISTRY] Error registering custom mapping: ${e.message}")
        }
    }

    private fun createDefaultRegistryJson(): String {
        return """
        [
          {
            "triggers": ["open wifi", "wifi kholo", "wifi settings", "turn on wifi", "wifi connect"],
            "actionType": "WIFI_ON",
            "intentAction": "android.settings.WIFI_SETTINGS",
            "feedback": "Opening Wi-Fi settings."
          },
          {
            "triggers": ["open bluetooth", "bluetooth kholo", "bluetooth settings", "pair bluetooth"],
            "actionType": "BLUETOOTH_ON",
            "intentAction": "android.settings.BLUETOOTH_SETTINGS",
            "feedback": "Opening Bluetooth settings."
          },
          {
            "triggers": ["open display", "brightness settings", "display settings"],
            "actionType": "BRIGHTNESS_SETTINGS",
            "intentAction": "android.settings.DISPLAY_SETTINGS",
            "feedback": "Opening Display & Brightness settings."
          },
          {
            "triggers": ["open sound", "volume settings", "sound settings"],
            "actionType": "VOLUME_SET",
            "intentAction": "android.settings.SOUND_SETTINGS",
            "feedback": "Opening Sound & Volume settings."
          },
          {
            "triggers": ["open settings", "phone settings", "system settings"],
            "actionType": "SYSTEM_SETTING",
            "intentAction": "android.settings.SETTINGS",
            "feedback": "Opening Phone Settings."
          }
        ]
        """.trimIndent()
    }

    private fun populateHardcodedDefaults() {
        mappings.clear()
        mappings.add(
            OfflineIntentMapping(
                triggerPhrases = listOf("open wifi", "wifi kholo", "wifi settings"),
                actionType = "WIFI_ON",
                systemIntentAction = Settings.ACTION_WIFI_SETTINGS,
                feedbackText = "Opening Wi-Fi settings."
            )
        )
    }

    companion object {
        private const val TAG = "CommandRegistry"
    }
}

