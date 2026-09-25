package com.example.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.data.ai.AiPersonality
import com.example.data.ai.EmotionDetector
import com.example.data.ai.GeminiApiClient
import com.example.data.ai.GeminiRateLimitException
import com.example.data.ai.OfflineNluEngine
import com.example.data.local.AlyaDatabase
import com.example.data.local.PreferencesManager
import com.example.data.local.entity.ConversationEntity
import com.example.data.local.entity.MemoryEntity
import com.example.data.local.entity.MessageEntity
import com.example.data.local.entity.ScheduledTaskEntity
import com.example.data.local.entity.LinkedDeviceEntity
import com.example.data.local.entity.CustomWakeWordEntity
import com.example.domain.appshare.AppSharingManager
import com.example.domain.devicelink.DeviceLinkManager
import com.example.domain.scheduler.TaskScheduler
import com.example.domain.tools.StructuredAction
import com.example.domain.tools.ToolExecutionResult
import com.example.domain.tools.ToolExecutor
import com.example.domain.tools.ToolRiskLevel
import com.example.update.AppUpdateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

class AlyaRepository(
    private val context: Context,
    val database: AlyaDatabase,
    val preferences: PreferencesManager
) {
    private val memoryDao = database.memoryDao()
    private val conversationDao = database.conversationDao()
    private val messageDao = database.messageDao()
    val scheduledTaskDao = database.scheduledTaskDao()
    val linkedDeviceDao = database.linkedDeviceDao()

    val geminiClient = GeminiApiClient()
    val toolExecutor = ToolExecutor(context)
    val deviceControlManager = com.example.alya.provider.device.DeviceControlManager(context, toolExecutor)
    
    // Open-Source Model Architecture (Local On-Device AI by default & Self-Hosted Option)
    val modelProviderRegistry = com.example.alya.provider.ModelProviderRegistry(context, preferences, toolExecutor)
    val defaultModelProvider = modelProviderRegistry.getActiveProvider()
    val openSourceLiveClient = com.example.alya.voice.OpenSourceLiveWebSocketClient()
    val alyaBrain = com.example.alya.brain.AlyaBrain(defaultModelProvider, toolExecutor)
    val alyaApiGateway = com.example.alya.api.AlyaApiGateway(
        brain = alyaBrain,
        memoryDao = memoryDao,
        capabilityManager = com.example.capability.CapabilityManager.getInstance(context)
    )

    // Advanced Reasoning, Extreme Long-Term Memory & JARVIS Automation Stack
    val extremeMemoryEngine = com.example.alya.memory.ExtremeMemoryEngine(context, memoryDao)
    val jarvisTelemetryManager = com.example.alya.agent.JarvisTelemetryManager(context, memoryDao, scheduledTaskDao)
    val jarvisMacroEngine = com.example.alya.agent.JarvisMacroEngine(context, toolExecutor)
    val aiBenchmarkEngine = com.example.alya.agent.AiBenchmarkEngine(context, extremeMemoryEngine, toolExecutor)
    val agentOrchestrator = com.example.alya.agent.AgentOrchestrator(
        modelProvider = defaultModelProvider,
        toolExecutor = toolExecutor,
        memoryEngine = extremeMemoryEngine,
        telemetryManager = jarvisTelemetryManager
    )

    val updateManager = AppUpdateManager(context)
    val versionCheckManager = com.example.update.VersionCheckManager(context, preferences)
    val taskScheduler = TaskScheduler(context)
    val deviceLinkManager = DeviceLinkManager(context, linkedDeviceDao, preferences)
    val appSharingManager = AppSharingManager(context, updateManager)
    val dataCacheManager = com.example.data.local.DataCacheManager(context, database)

    val allConversations: Flow<List<ConversationEntity>> = conversationDao.getAllConversationsFlow()
    val allMemories: Flow<List<MemoryEntity>> = memoryDao.getAllMemoriesFlow()
    val allTasks: Flow<List<ScheduledTaskEntity>> = scheduledTaskDao.getAllTasksFlow()
    val allLinkedDevices: Flow<List<LinkedDeviceEntity>> = deviceLinkManager.allLinkedDevices

    val customWakeWordDao = database.customWakeWordDao()
    val allCustomWakeWords: Flow<List<CustomWakeWordEntity>> = customWakeWordDao.getAllCustomWakeWordsFlow()
    val activeCustomWakeWordFlow: Flow<CustomWakeWordEntity?> = customWakeWordDao.getActiveCustomWakeWordFlow()

    suspend fun getActiveCustomWakeWord(): CustomWakeWordEntity? = withContext(Dispatchers.IO) {
        customWakeWordDao.getActiveCustomWakeWord()
    }

    suspend fun saveCustomWakeWord(word: String, audioFilePath: String, makeActive: Boolean) = withContext(Dispatchers.IO) {
        if (makeActive) {
            customWakeWordDao.deactivateAllCustomWakeWords()
        }
        val entity = CustomWakeWordEntity(
            word = word,
            audioFilePath = audioFilePath,
            isActive = makeActive
        )
        customWakeWordDao.insertCustomWakeWord(entity)
    }

    suspend fun setCustomWakeWordActive(id: Long) = withContext(Dispatchers.IO) {
        customWakeWordDao.deactivateAllCustomWakeWords()
        customWakeWordDao.activateCustomWakeWord(id)
    }

    suspend fun deleteCustomWakeWord(id: Long) = withContext(Dispatchers.IO) {
        customWakeWordDao.deleteCustomWakeWordById(id)
    }

    suspend fun optimizeStorageFootprint(maxDaysOld: Int = 14, maxMessagesPerConv: Int = 100) =
        dataCacheManager.optimizeStorageFootprint(maxDaysOld = maxDaysOld, maxMessagesPerConversation = maxMessagesPerConv)

    // Task Management
    suspend fun createScheduledTask(
        title: String,
        description: String = "",
        delayMinutes: Int,
        repeat: String = "NONE"
    ): Long = withContext(Dispatchers.IO) {
        val triggerTime = System.currentTimeMillis() + (delayMinutes.coerceAtLeast(1) * 60 * 1000L)
        val task = ScheduledTaskEntity(
            title = title,
            description = description.ifBlank { "Scheduled reminder from Alya" },
            scheduledTimeMillis = triggerTime,
            repeatInterval = repeat.uppercase(),
            isCompleted = false,
            category = "REMINDER"
        )
        val id = scheduledTaskDao.insertTask(task)
        taskScheduler.scheduleTask(task.copy(id = id))
        id
    }

    suspend fun toggleTaskCompleted(task: ScheduledTaskEntity) = withContext(Dispatchers.IO) {
        val updated = task.copy(isCompleted = !task.isCompleted)
        scheduledTaskDao.updateTask(updated)
        if (updated.isCompleted) {
            taskScheduler.cancelTask(task.id)
        } else if (updated.scheduledTimeMillis > System.currentTimeMillis()) {
            taskScheduler.scheduleTask(updated)
        }
    }

    suspend fun deleteScheduledTask(task: ScheduledTaskEntity) = withContext(Dispatchers.IO) {
        taskScheduler.cancelTask(task.id)
        scheduledTaskDao.deleteTask(task)
    }

    fun getMessagesForConversation(convId: String): Flow<List<MessageEntity>> =
        messageDao.getMessagesForConversationFlow(convId)

    suspend fun getOrCreateActiveConversation(): ConversationEntity = withContext(Dispatchers.IO) {
        val list = conversationDao.getAllConversationsFlow()
        // Check if there is already a conversation, otherwise create one
        val existing = database.runInTransaction<ConversationEntity?> {
            // We can query SQLite directly or insert
            null
        }
        val newConv = ConversationEntity(
            title = "New Conversation",
            category = "General"
        )
        conversationDao.insertConversation(newConv)
        newConv
    }

    suspend fun createNewConversation(title: String = "Chat with Alya"): ConversationEntity = withContext(Dispatchers.IO) {
        val conv = ConversationEntity(
            title = title,
            category = "General"
        )
        conversationDao.insertConversation(conv)
        val welcomeMsg = MessageEntity(
            conversationId = conv.id,
            role = "assistant",
            content = "Hello? Heyyy, it's me, Alya! Hehe, I picked up — so tell me, what's going on?"
        )
        messageDao.insertMessage(welcomeMsg)
        conv
    }

    suspend fun renameConversation(id: String, newTitle: String) = withContext(Dispatchers.IO) {
        val conv = conversationDao.getConversationById(id) ?: return@withContext
        conversationDao.updateConversation(conv.copy(title = newTitle, updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteConversation(id: String) = withContext(Dispatchers.IO) {
        conversationDao.deleteConversationById(id)
    }

    suspend fun clearAllConversations() = withContext(Dispatchers.IO) {
        conversationDao.clearAllConversations()
    }

    // Memory operations
    suspend fun addMemory(category: String, key: String, content: String) = withContext(Dispatchers.IO) {
        memoryDao.insertMemory(
            MemoryEntity(
                category = category,
                key = key,
                content = content
            )
        )
    }

    suspend fun updateMemory(memory: MemoryEntity) = withContext(Dispatchers.IO) {
        memoryDao.updateMemory(memory.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteMemory(memory: MemoryEntity) = withContext(Dispatchers.IO) {
        memoryDao.deleteMemory(memory)
    }

    suspend fun clearAllMemories() = withContext(Dispatchers.IO) {
        memoryDao.clearAllMemories()
    }

    fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    // Message processing
    suspend fun processUserMessage(
        conversationId: String,
        userText: String,
        isVoiceMode: Boolean = false,
        languageHint: String? = null
    ): MessageEntity = withContext(Dispatchers.IO) {
        val emotion = EmotionDetector.detectEmotion(userText)
        val isOnline = isNetworkAvailable()
        val lang = languageHint ?: preferences.voiceLanguage.value

        // 1. Save user message in DB
        val userMessage = MessageEntity(
            conversationId = conversationId,
            role = "user",
            content = userText,
            emotionDetected = emotion.label,
            isSynced = isOnline
        )
        messageDao.insertMessage(userMessage)

        // Auto-update conversation title if it's the first message
        val messageCount = messageDao.getMessageCount(conversationId)
        if (messageCount <= 2) {
            val shortTitle = if (userText.length > 28) userText.take(28) + "..." else userText
            renameConversation(conversationId, shortTitle)
        }

        // 2. Check for AGI-level reasoning and extreme automation
        val historyMessages = messageDao.getMessagesForConversation(conversationId).takeLast(10)
        val providerHistory = historyMessages.map {
            com.example.alya.provider.AlyaChatMessage(
                role = if (it.role == "user") com.example.alya.provider.MessageRole.USER else com.example.alya.provider.MessageRole.ASSISTANT,
                content = it.content
            )
        }

        // Use AgentOrchestrator for EXTREME reasoning
        val agentResult = agentOrchestrator.execute(
            prompt = userText,
            history = providerHistory,
            targetLanguage = lang,
            isOnline = isOnline
        )

        // 3. Save assistant message in DB
        val executedAction = agentResult.allActionsExecuted.firstOrNull()
        val assistantMessage = MessageEntity(
            conversationId = conversationId,
            role = "assistant",
            content = agentResult.replyText,
            toolName = executedAction?.toolName,
            toolActionJson = executedAction?.parameters?.toString(),
            toolStatus = if (executedAction != null) {
                if (executedAction.requiresConfirmation) "pending_confirmation" else "executed"
            } else "none",
            toolResult = agentResult.trace?.steps?.lastOrNull()?.observation,
            isSynced = isOnline
        )
        messageDao.insertMessage(assistantMessage)
        messageDao.trimExcessMessagesForConversation(conversationId, 150)

        assistantMessage
    }

    private suspend fun handleActionExecution(
        conversationId: String,
        action: StructuredAction
    ): MessageEntity {
        val confirmationPolicy = preferences.getConfirmationLevel()
        val result = toolExecutor.executeAction(
            action = action,
            isUserConfirmed = false,
            confirmationPolicy = confirmationPolicy
        )

        val assistantMessage = MessageEntity(
            conversationId = conversationId,
            role = "assistant",
            content = result.message,
            toolName = action.toolName,
            toolActionJson = action.parameters.toString(),
            toolStatus = if (result.requiresConfirmation) "pending_confirmation" else if (result.success) "executed" else "failed",
            toolResult = result.output ?: result.message
        )
        messageDao.insertMessage(assistantMessage)
        return assistantMessage
    }

    suspend fun executeConfirmedAction(message: MessageEntity): MessageEntity = withContext(Dispatchers.IO) {
        val toolName = message.toolName ?: return@withContext message
        val action = StructuredAction(
            intent = toolName,
            toolName = toolName,
            parameters = emptyMap(),
            requiresConfirmation = false
        )
        val result = toolExecutor.executeAction(action, isUserConfirmed = true, confirmationPolicy = "NEVER")

        val updated = message.copy(
            toolStatus = if (result.success) "executed" else "failed",
            toolResult = result.message,
            content = result.message
        )
        messageDao.updateMessage(updated)
        updated
    }

    suspend fun cancelPendingAction(message: MessageEntity): MessageEntity = withContext(Dispatchers.IO) {
        val updated = message.copy(
            toolStatus = "cancelled",
            toolResult = "Action was cancelled by user.",
            content = "Cancelled."
        )
        messageDao.updateMessage(updated)
        updated
    }

    private fun parseActionJson(jsonStr: String): StructuredAction? {
        return try {
            val trimmed = jsonStr.trim()
            val obj = if (trimmed.startsWith("[")) {
                val array = org.json.JSONArray(trimmed)
                if (array.length() > 0) array.getJSONObject(0) else return null
            } else {
                JSONObject(trimmed)
            }

            // Check if this is the Master Alya JSON format
            if (obj.has("device_domain") || obj.has("command_id")) {
                val deviceDomain = obj.optString("device_domain", "general_assistant").lowercase().trim()
                val device = obj.optString("device", "").lowercase().trim()
                val action = obj.optString("action", "").lowercase().trim()
                val requiresConfirmation = obj.optBoolean("requires_confirmation", false) ||
                        obj.optString("safety_level").lowercase().trim() == "dangerous"

                val paramsMap = mutableMapOf<String, String>()
                val paramsObj = obj.optJSONObject("parameters")
                if (paramsObj != null) {
                    val keys = paramsObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val v = paramsObj.opt(k)?.toString() ?: ""
                        if (v != "null") paramsMap[k] = v
                    }
                }
                val outputObj = obj.optJSONObject("output")
                if (outputObj != null) {
                    val outText = outputObj.optString("text", "")
                    if (outText.isNotBlank()) paramsMap["content"] = outText
                }

                val (toolName, mappedParams) = mapMasterAlyaToTool(deviceDomain, device, action, paramsMap)
                val riskLevel = if (requiresConfirmation) ToolRiskLevel.HIGH else ToolRiskLevel.LOW

                return StructuredAction(
                    intent = "$deviceDomain:$action",
                    toolName = toolName,
                    parameters = mappedParams,
                    riskLevel = riskLevel,
                    requiresConfirmation = requiresConfirmation,
                    naturalConfirmationPrompt = obj.optString("reply_to_user", null)
                )
            }

            // Fallback: Legacy tool format {"tool": "...", "parameters": { ... }}
            val toolName = obj.optString("tool", "")
            if (toolName.isBlank()) return null

            val paramsMap = mutableMapOf<String, String>()
            val paramsObj = obj.optJSONObject("parameters")
            if (paramsObj != null) {
                val keys = paramsObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    paramsMap[k] = paramsObj.optString(k, "")
                }
            }
            StructuredAction(
                intent = toolName,
                toolName = toolName,
                parameters = paramsMap
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun mapMasterAlyaToTool(
        domain: String,
        device: String,
        action: String,
        params: Map<String, String>
    ): Pair<String, Map<String, String>> {
        val value = params["value"] ?: ""
        val content = params["content"] ?: ""
        val merged = params.toMutableMap()
        val lowerDomain = domain.lowercase().trim()
        val lowerDevice = device.lowercase().trim()
        val lowerAction = action.lowercase().trim()

        return when {
            lowerDomain == "emergency" || lowerDevice.contains("emergency") || lowerAction.contains("emergency") -> {
                if (lowerAction.contains("call") || lowerDevice.contains("call")) {
                    Pair("manage_call", mapOf("action" to "make_call", "contact_name" to (content.ifBlank { "Emergency" })))
                } else {
                    Pair("toggle_flashlight", mapOf("state" to "on"))
                }
            }
            lowerDomain in listOf("modes", "mode", "profile") || lowerDevice.contains("mode") || lowerAction.contains("mode") -> {
                val modeName = if (lowerDevice.contains("mode")) device else "$action Mode"
                Pair("set_routine", mapOf("name" to modeName, "actions" to (content.ifBlank { "Activate $modeName" })))
            }
            lowerDomain == "mobile_phone" || lowerDomain == "communication" -> {
                when {
                    lowerAction == "send" || lowerDevice.contains("whatsapp") || content.contains("whatsapp", ignoreCase = true) -> {
                        if (lowerDevice.contains("whatsapp") || content.contains("whatsapp", ignoreCase = true)) {
                            Pair("send_whatsapp", mapOf("recipient" to (params["recipient"] ?: device), "message_body" to content))
                        } else {
                            Pair("prepare_message", mapOf("recipient" to (params["recipient"] ?: device), "body" to content))
                        }
                    }
                    lowerAction == "read" && (lowerDevice.contains("notification") || lowerDevice.contains("message")) -> {
                        Pair("open_notifications", emptyMap())
                    }
                    lowerAction in listOf("reminder", "schedule") || lowerDevice.contains("reminder") -> {
                        Pair("schedule_task", mapOf("title" to (content.ifBlank { "Reminder" }), "time_minutes" to (value.ifBlank { "60" })))
                    }
                    lowerDevice.contains("wifi") || lowerDevice.contains("wi-fi") -> {
                        if (lowerAction == "on") {
                            Pair("turn_on_and_connect_wifi", mapOf("network" to (content.ifBlank { "available" })))
                        } else {
                            Pair("toggle_wifi", mapOf("state" to action))
                        }
                    }
                    lowerDevice.contains("bluetooth") -> {
                        Pair("toggle_bluetooth", mapOf("state" to action))
                    }
                    lowerDevice.contains("torch") || lowerDevice.contains("flashlight") -> {
                        Pair("toggle_flashlight", mapOf("state" to action))
                    }
                    lowerDevice.contains("volume") || lowerAction in listOf("louder", "quieter") -> {
                        val act = if (lowerAction in listOf("quieter", "down", "decrease")) "down" else if (lowerAction in listOf("louder", "up", "increase")) "up" else action
                        Pair("control_volume", mapOf("action" to act, "level" to value.ifBlank { "50" }))
                    }
                    lowerDevice.contains("brightness") -> {
                        val act = if (lowerAction in listOf("dim", "down", "decrease")) "down" else if (lowerAction in listOf("brighten", "up", "increase")) "up" else action
                        Pair("control_brightness", mapOf("action" to act, "level" to value.ifBlank { "50" }))
                    }
                    lowerDevice == "alya_app" || lowerDevice.contains("alya") -> {
                        val task = params["task"] ?: ""
                        val time = params["time"] ?: ""
                        if (lowerAction == "schedule" || task.contains("wake_up") || task.contains("alarm")) {
                            val hourMinute = time.split(":")
                            val hour = hourMinute.getOrNull(0)?.toIntOrNull() ?: 7
                            val minute = hourMinute.getOrNull(1)?.toIntOrNull() ?: 0
                            val title = content.ifBlank { "Good Morning! Time to Wake Up" }
                            Pair("create_wakeup_alarm", mapOf("hour" to hour.toString(), "minute" to minute.toString(), "title" to title))
                        } else {
                            Pair("schedule_task", mapOf("title" to (content.ifBlank { task.ifBlank { "Alya Routine" } }), "time_minutes" to (value.ifBlank { "60" })))
                        }
                    }
                    lowerDevice.contains("alarm") || params["task"]?.contains("alarm") == true -> {
                        val time = params["time"] ?: ""
                        if (time.isNotBlank()) {
                            val hourMinute = time.split(":")
                            val hour = hourMinute.getOrNull(0)?.toIntOrNull() ?: 7
                            val minute = hourMinute.getOrNull(1)?.toIntOrNull() ?: 0
                            val title = content.ifBlank { "Alya Alarm" }
                            Pair("create_wakeup_alarm", mapOf("hour" to hour.toString(), "minute" to minute.toString(), "title" to title))
                        } else {
                            Pair("create_alarm", mapOf("message" to (content.ifBlank { "Alarm" })))
                        }
                    }
                    lowerDevice.contains("timer") -> {
                        Pair("create_timer", mapOf("seconds" to value.ifBlank { "60" }, "message" to content))
                    }
                    lowerDevice.contains("camera") -> {
                        Pair("open_camera", emptyMap())
                    }
                    lowerDevice.contains("screenshot") -> {
                        Pair("take_screenshot", emptyMap())
                    }
                    lowerDevice.contains("lock") -> {
                        Pair("lock_screen", emptyMap())
                    }
                    lowerDevice.contains("call") || lowerAction == "call" -> {
                        Pair("manage_call", mapOf("action" to "make_call", "contact_name" to (content.ifBlank { device })))
                    }
                    lowerAction == "open" || lowerAction == "start" || lowerAction == "launch" -> {
                        Pair("open_app", mapOf("appName" to device))
                    }
                    else -> {
                        Pair("control_device", merged.apply { put("device", device); put("action", action) })
                    }
                }
            }
            lowerDomain == "media_entertainment" -> {
                if (lowerAction in listOf("open", "start", "launch")) {
                    Pair("open_app", mapOf("appName" to (device.ifBlank { content })))
                } else if (lowerAction in listOf("play", "pause", "resume", "stop", "next", "previous", "shuffle", "repeat")) {
                    if (lowerAction == "play" && content.isNotBlank()) {
                        Pair("search_and_play_media", mapOf("query" to content, "app" to (if (device.isNotBlank()) device else "youtube")))
                    } else {
                        Pair("control_media", mapOf("action" to action, "query" to content))
                    }
                } else if (device.isNotBlank() && (device.contains("youtube") || device.contains("spotify") || device.contains("netflix"))) {
                    Pair("open_app", mapOf("appName" to device))
                } else {
                    Pair("control_media", mapOf("action" to action, "query" to content))
                }
            }
            lowerDomain == "general_assistant" -> {
                if (lowerDevice.contains("weather") || lowerAction == "weather") {
                    Pair("check_weather", mapOf("location" to content.ifBlank { "current" }))
                } else if (lowerAction in listOf("reminder", "schedule") || lowerDevice.contains("reminder")) {
                    Pair("schedule_task", mapOf("title" to (content.ifBlank { "Reminder" }), "time_minutes" to (value.ifBlank { "60" })))
                } else {
                    Pair("answer", emptyMap())
                }
            }
            lowerDomain == "smart_home" -> {
                Pair("control_smart_home", merged.apply { put("device", device); put("action", action) })
            }
            else -> {
                Pair("control_device", merged.apply { put("device", device); put("action", action) })
            }
        }
    }
}
