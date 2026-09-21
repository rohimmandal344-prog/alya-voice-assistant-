package com.example.data.auth

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/**
 * GoogleCredentialManagerHandler (Alya v2.3.0)
 *
 * Implements Android Credential Manager API for secure, modern 'Sign in with Google' flows.
 * Handles credential retrieval, GoogleIdToken parsing, and graceful fallbacks.
 */
class GoogleCredentialManagerHandler(private val context: Context) {

    private val credentialManager = CredentialManager.create(context)

    suspend fun launchGoogleSignIn(
        serverClientId: String = "887422690979-dummy.apps.googleusercontent.com"
    ): Result<GoogleSignInAccountResult> {
        return try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(serverClientId)
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(
                context = context,
                request = request
            )

            val credential = result.credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val email = googleCredential.id
                val displayName = googleCredential.displayName ?: googleCredential.givenName ?: email.substringBefore("@")
                val idToken = googleCredential.idToken
                val profilePictureUrl = googleCredential.profilePictureUri?.toString()

                Log.i(TAG, "Successfully retrieved Google Credentials for $email via Credential Manager.")
                Result.success(
                    GoogleSignInAccountResult(
                        email = email,
                        displayName = displayName,
                        idToken = idToken,
                        profilePictureUrl = profilePictureUrl
                    )
                )
            } else {
                Log.w(TAG, "Received unknown credential type: ${credential.type}")
                Result.failure(IllegalArgumentException("Unsupported credential response type: ${credential.type}"))
            }
        } catch (e: GetCredentialException) {
            Log.w(TAG, "Credential Manager API returned error: ${e.message} (Type: ${e.type})")
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Error invoking Credential Manager Google Sign-In: ${e.message}", e)
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "GoogleCredManager"
    }
}

data class GoogleSignInAccountResult(
    val email: String,
    val displayName: String,
    val idToken: String? = null,
    val profilePictureUrl: String? = null
)
