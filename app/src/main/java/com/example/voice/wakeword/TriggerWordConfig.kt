package com.example.voice.wakeword

import android.content.Context
import android.content.SharedPreferences

class TriggerWordConfig(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("trigger_word_prefs", Context.MODE_PRIVATE)

    var triggerWord: String
        get() = prefs.getString(KEY, DEFAULT_TRIGGER_WORD) ?: DEFAULT_TRIGGER_WORD
        set(value) {
            require(isValid(value)) { "Invalid trigger word: $value" }
            prefs.edit().putString(KEY, value).apply()
        }

    fun resetToDefault() {
        triggerWord = DEFAULT_TRIGGER_WORD
    }

    companion object {
        const val DEFAULT_TRIGGER_WORD = "alya"
        private const val KEY = "trigger_word"

        fun isValid(word: String): Boolean {
            if (word.length !in 2..20) return false
            // letters only (no spaces/special characters)
            return word.all { it.isLetter() }
        }
    }
}
