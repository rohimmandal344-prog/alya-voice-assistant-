package com.example.data.auth

import android.util.Log
import com.example.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * SupabaseAuthManager
 * 
 * High-performance Supabase Authentication integration for Alya.
 * Replaces Firebase Auth with ultra-low latency cloud sync and secure identity management.
 */
class SupabaseAuthManager {

    private val _isSupabaseActive = MutableStateFlow<Boolean>(false)
    val isSupabaseActive: StateFlow<Boolean> = _isSupabaseActive.asStateFlow()

    private var client: SupabaseClient? = null

    init {
        try {
            val url = BuildConfig.SUPABASE_URL
            val key = BuildConfig.SUPABASE_ANON_KEY

            if (url.isNotBlank() && key.isNotBlank() && url != "YOUR_SUPABASE_URL") {
                client = createSupabaseClient(url, key) {
                    install(Auth) {
                        autoSaveToStorage = true
                    }
                }
                _isSupabaseActive.value = true
                Log.i(TAG, "Supabase Client initialized successfully.")
            } else {
                Log.w(TAG, "Supabase keys missing or placeholders. Cloud sync will be limited to local fallback.")
                _isSupabaseActive.value = false
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error initializing Supabase: ${t.message}")
            _isSupabaseActive.value = false
        }
    }

    suspend fun signUp(email: String, password: String): Result<String> {
        val auth = client?.auth ?: return Result.failure(Exception("Supabase not initialized"))
        return try {
            auth.signUpWith(Email) {
                this.email = email
                this.password = password
            }
            val userId = auth.currentUserOrNull()?.id ?: return Result.failure(Exception("Sign up failed - no user returned"))
            Result.success(userId)
        } catch (e: Exception) {
            Log.e(TAG, "Supabase Sign-Up Error: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun signIn(email: String, password: String): Result<String> {
        val auth = client?.auth ?: return Result.failure(Exception("Supabase not initialized"))
        return try {
            auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            val userId = auth.currentUserOrNull()?.id ?: return Result.failure(Exception("Sign in failed - no user returned"))
            Result.success(userId)
        } catch (e: Exception) {
            Log.e(TAG, "Supabase Sign-In Error: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun signOut() {
        try {
            client?.auth?.signOut()
        } catch (e: Exception) {
            Log.e(TAG, "Supabase Sign-Out Error: ${e.message}")
        }
    }

    fun getCurrentUserEmail(): String? = client?.auth?.currentUserOrNull()?.email

    fun getUserId(): String? = client?.auth?.currentUserOrNull()?.id

    companion object {
        private const val TAG = "SupabaseAuthManager"
    }
}
