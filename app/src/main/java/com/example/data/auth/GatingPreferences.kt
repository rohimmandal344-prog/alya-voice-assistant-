package com.example.data.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "gating_preferences")

class GatingPreferences(private val context: Context) {
    companion object {
        private val KEY_TERMS_ACCEPTED = booleanPreferencesKey("terms_accepted")
    }

    val isTermsAccepted: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_TERMS_ACCEPTED] ?: false
    }

    suspend fun acceptTerms() {
        context.dataStore.edit { preferences ->
            preferences[KEY_TERMS_ACCEPTED] = true
        }
    }
}
