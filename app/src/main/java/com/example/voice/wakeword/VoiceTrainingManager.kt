package com.example.voice.wakeword

import android.content.Context
import android.content.SharedPreferences
import com.example.data.local.PreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * Approved trigger words for Alya Voice Assistant.
 * Trigger words can ONLY be chosen from this approved list.
 */
val APPROVED_TRIGGER_WORDS = listOf(
    "Alya", "Alia", "Seno", "Jarvis", "Luna", "Nova", "Echo", "Iris", "Friday", "AlyaPro"
)

/**
 * Environments for voice training under different acoustic conditions.
 */
val TRAINING_ENVIRONMENTS = listOf(
    "Quiet Room", "Kitchen", "Car", "Outdoors"
)

data class EnvironmentCount(
    val environment: String,
    val count: Int
)

data class TriggerWordProgress(
    val triggerWord: String,
    val totalCount: Int,
    val environmentCounts: Map<String, Int>
)

data class VoiceTrainingSummary(
    val totalRecordings: Int,
    val triggerProgress: List<TriggerWordProgress>,
    val environmentTotals: Map<String, Int>,
    val isEnrolled: Boolean
)

/**
 * Manager for User Voice Training / Voice Enrollment.
 * Handles recording progress across environments (Quiet room, Kitchen, Car, Outdoors),
 * generates natural feedback, maintains progress summaries, and handles deletion workflows.
 */
class VoiceTrainingManager(
    private val context: Context,
    private val preferencesManager: PreferencesManager
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("alya_voice_training", Context.MODE_PRIVATE)

    private val _summaryState = MutableStateFlow(loadSummary())
    val summaryState: StateFlow<VoiceTrainingSummary> = _summaryState.asStateFlow()

    private val _pendingDeleteConfirmation = MutableStateFlow(false)
    val pendingDeleteConfirmation: StateFlow<Boolean> = _pendingDeleteConfirmation.asStateFlow()

    fun isApprovedTriggerWord(word: String): Boolean {
        val trimmed = word.trim()
        return APPROVED_TRIGGER_WORDS.any { it.equals(trimmed, ignoreCase = true) }
    }

    fun getApprovedTriggerWordMatch(word: String): String? {
        val trimmed = word.trim()
        return APPROVED_TRIGGER_WORDS.find { it.equals(trimmed, ignoreCase = true) }
    }

    /**
     * Records a voice sample for a specific trigger word in a given acoustic environment.
     * Returns spoken feedback for the user.
     */
    fun recordVoiceSample(triggerWord: String, environment: String): String {
        val approvedWord = getApprovedTriggerWordMatch(triggerWord) ?: "Alya"
        val validEnv = if (environment in TRAINING_ENVIRONMENTS) environment else "Quiet Room"

        val jsonStr = prefs.getString("recordings_data", "{}") ?: "{}"
        val root = try { JSONObject(jsonStr) } catch (_: Exception) { JSONObject() }

        val wordObj = if (root.has(approvedWord)) root.getJSONObject(approvedWord) else JSONObject()
        val currentCount = wordObj.optInt(validEnv, 0)
        val newCount = currentCount + 1
        wordObj.put(validEnv, newCount)
        root.put(approvedWord, wordObj)

        prefs.edit().putString("recordings_data", root.toString()).apply()

        // Also update the primary preferences manager profile state
        preferencesManager.setVoiceProfileCompleted(sampleCount = newCount)

        val updatedSummary = loadSummary()
        _summaryState.value = updatedSummary

        // Calculate feedback
        val wordProgress = updatedSummary.triggerProgress.find { it.triggerWord.equals(approvedWord, ignoreCase = true) }
        val wordTotal = wordProgress?.totalCount ?: newCount

        val feedback = when {
            wordTotal < 3 -> {
                val needed = 3 - wordTotal
                "Got it! $needed more recording${if (needed > 1) "s" else ""} of '$approvedWord' in a noisy place would help."
            }
            wordTotal in 3..5 -> {
                val needed = 5 - wordTotal
                if (needed > 0) {
                    "Got it! $needed more recording${if (needed > 1) "s" else ""} of '$approvedWord' in a car or outdoors would give maximum accuracy."
                } else {
                    "Awesome! '$approvedWord' is fully trained across environments with $wordTotal recordings!"
                }
            }
            else -> {
                "Sample saved for '$approvedWord' in $validEnv! Voice model updated."
            }
        }

        return feedback
    }

    fun loadSummary(): VoiceTrainingSummary {
        val jsonStr = prefs.getString("recordings_data", "{}") ?: "{}"
        val root = try { JSONObject(jsonStr) } catch (_: Exception) { JSONObject() }

        val triggerProgressList = mutableListOf<TriggerWordProgress>()
        val envTotals = mutableMapOf<String, Int>()
        TRAINING_ENVIRONMENTS.forEach { envTotals[it] = 0 }

        var grandTotal = 0

        APPROVED_TRIGGER_WORDS.forEach { word ->
            if (root.has(word)) {
                val obj = root.getJSONObject(word)
                val counts = mutableMapOf<String, Int>()
                var wordTotal = 0
                TRAINING_ENVIRONMENTS.forEach { env ->
                    val c = obj.optInt(env, 0)
                    counts[env] = c
                    wordTotal += c
                    envTotals[env] = (envTotals[env] ?: 0) + c
                }
                if (wordTotal > 0) {
                    triggerProgressList.add(
                        TriggerWordProgress(
                            triggerWord = word,
                            totalCount = wordTotal,
                            environmentCounts = counts
                        )
                    )
                    grandTotal += wordTotal
                }
            }
        }

        val isEnrolled = grandTotal >= 3 || preferencesManager.isVoiceProfileSet.value

        return VoiceTrainingSummary(
            totalRecordings = grandTotal,
            triggerProgress = triggerProgressList,
            environmentTotals = envTotals,
            isEnrolled = isEnrolled
        )
    }

    fun getProgressSummarySpoken(): String {
        val summary = loadSummary()
        if (summary.totalRecordings == 0) {
            return "You haven't recorded any voice samples yet. You can start voice training anytime by saying 'Record my voice'."
        }

        val triggerParts = summary.triggerProgress.joinToString(", ") {
            "${it.triggerWord} (${it.totalCount} recording${if (it.totalCount > 1) "s" else ""})"
        }

        val envParts = summary.environmentTotals.entries.joinToString(", ") {
            "${it.key}: ${it.value}"
        }

        return "Here is your voice training progress: $triggerParts across environments ($envParts). Total of ${summary.totalRecordings} recordings."
    }

    fun requestDeleteAllRecordings(): String {
        _pendingDeleteConfirmation.value = true
        return "This will permanently delete all your voice recordings. I'll lose the ability to recognize your voice. Confirm delete?"
    }

    fun confirmDeleteAllRecordings(): String {
        _pendingDeleteConfirmation.value = false
        prefs.edit().clear().apply()
        preferencesManager.deleteUserVoiceProfile()
        _summaryState.value = loadSummary()
        return "All voice recordings deleted. You can re-enroll your voice anytime in Settings."
    }

    fun cancelDelete(): String {
        _pendingDeleteConfirmation.value = false
        return "Delete cancelled. Your voice recordings are safe."
    }
}
