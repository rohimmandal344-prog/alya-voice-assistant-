package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.ui.screens.MainChatScreen
import com.example.ui.screens.VoiceModeScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.SecurityLockScreen
import com.example.data.auth.AuthState
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.AlyaViewModel
import com.example.ui.viewmodel.AssistantScreen

import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@android.annotation.SuppressLint("InvalidFragmentVersionForActivityResult")
class MainActivity : ComponentActivity() {

    private val viewModel: AlyaViewModel by viewModels()
    private val fpsMonitor = com.example.util.FrameRateMonitor()
    private var offlineTtsManager: com.example.voice.OfflineTTSManager? = null

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val micGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        if (micGranted) {
            com.example.service.WakeWordService.start(this@MainActivity)
            if (viewModel.currentScreen.value == AssistantScreen.VOICE_MODE) {
                viewModel.startListening()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleIntentExtras(intent)
        fpsMonitor.start()
        offlineTtsManager = com.example.voice.OfflineTTSManager(this)

        // Ensure wake word model is ready in background
        lifecycleScope.launch(Dispatchers.IO + kotlinx.coroutines.SupervisorJob()) {
            Log.i("MainActivityStartup", "[STARTUP] Starting background startup sequence.")
            
            // Setup Offline Storage (Android/data and OBB)
            try {
                Log.i("MainActivityStartup", "[STARTUP] Setting up offline storage architecture...")
                com.example.util.AlyaStorageManager.setupOfflineStorage(this@MainActivity)
            } catch (e: Exception) {
                Log.e("MainActivityStartup", "[STARTUP_FALLBACK] Failure setting up offline storage: ${e.message}")
            }
            
            // Step 1: Initialize Wake Word Model with timeout & logging
            try {
                Log.i("MainActivityStartup", "[STARTUP] Initializing wake word model...")
                kotlinx.coroutines.withTimeout(4000L) {
                    // TFLite model initialization removed; OpenWakeWord handled internally
                }
                Log.i("MainActivityStartup", "[STARTUP] Wake word model initialized.")
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                Log.e("MainActivityStartup", "[STARTUP_FALLBACK] Timeout during wake word model initialization.")
            } catch (e: Exception) {
                Log.e("MainActivityStartup", "[STARTUP_FALLBACK] Failure during wake word model initialization: ${e.message}.", e)
            }

            // Step 2: Start persistent high-priority wake-word detection service if microphone permission is granted
            try {
                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    Log.i("MainActivityStartup", "[STARTUP] Launching persistent WakeWordService for wake-word...")
                    com.example.service.WakeWordService.start(this@MainActivity)
                }
            } catch (e: Exception) {
                Log.e("MainActivityStartup", "[STARTUP_FALLBACK] WakeWordService startup failed safely: ${e.message}")
            }

            // Step 3: Verify Version / What's New Dialog on startup
            try {
                Log.i("MainActivityStartup", "[STARTUP] Verifying version/changelog status...")
                kotlinx.coroutines.withTimeout(4000L) {
                    viewModel.versionCheckManager.verifyVersionOnStartup()
                }
                Log.i("MainActivityStartup", "[STARTUP] Version/changelog status verified successfully.")
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                Log.e("MainActivityStartup", "[STARTUP_FALLBACK] Timeout during 'What's New' version check.")
            } catch (e: Exception) {
                Log.e("MainActivityStartup", "[STARTUP_FALLBACK] Failure during version check: ${e.message}.", e)
            }
            
            Log.i("MainActivityStartup", "[STARTUP_COMPLETE] Background startup sequence completed safely.")
        }

        setContent {
            val themeMode by viewModel.repository.preferences.themeMode.collectAsState()
            val isDark = when (themeMode) {
                "DARK" -> true
                "LIGHT" -> false
                else -> isSystemInDarkTheme()
            }
            
            var showCustomPermissions by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

            androidx.compose.runtime.LaunchedEffect(Unit) {
                // Delay permission request to ensure UI is interactive first
                kotlinx.coroutines.delay(1000L)
                requestRequiredPermissions()
                
                // Note: showCustomPermissions is now only for Accessibility if needed
            }

            MyApplicationTheme(darkTheme = isDark) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AlyaApp(viewModel = viewModel)
                    
                    if (showCustomPermissions) {
                        com.example.ui.components.CustomPermissionDialog(
                            onGrantFiles = {
                                // No-op as MANAGE_EXTERNAL_STORAGE is removed
                            },
                            onGrantAccessibility = {
                                val intent = android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                startActivity(intent)
                            },
                            onDismiss = { showCustomPermissions = false }
                        )
                    }
                }
            }
        }
    }

    private fun requestRequiredPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.ANSWER_PHONE_CALLS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            requestPermissionsLauncher.launch(needed.toTypedArray())
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntentExtras(intent)
    }

    override fun onStart() {
        super.onStart()
        viewModel.onAppForegrounded()
    }

    override fun onResume() {
        super.onResume()
        handleIntentExtras(intent)

        // Seamlessly refresh capability & permission dashboard on app resume
        viewModel.capabilityManager.refreshCapabilities()
        com.example.audio.AlyaAudioManager.getInstance(this).onAppForegrounded()

        val hasMic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (!hasMic) {
            viewModel.handleMicrophonePermissionRevoked()
        } else if (viewModel.currentScreen.value == AssistantScreen.VOICE_MODE && !viewModel.speechManager.isListening.value && !viewModel.ttsManager.isSpeaking.value) {
            viewModel.startListening()
        }

        // Resume any pending update APK install after user grants install permission
        viewModel.repository.updateManager.resumePendingInstall()
    }

    override fun onPause() {
        super.onPause()
        val isWakeWordActive = (application as? AlyaApplication)?.wakeWordManager?.isListening?.value == true
        com.example.audio.AlyaAudioManager.getInstance(this).onAppBackgrounded(hasActiveForegroundService = isWakeWordActive)
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        viewModel.notifyUserActivity()
    }

    override fun onStop() {
        super.onStop()
        val isWakeWordActive = (application as? AlyaApplication)?.wakeWordManager?.isListening?.value == true
        com.example.audio.AlyaAudioManager.getInstance(this).onAppBackgrounded(hasActiveForegroundService = isWakeWordActive)
        viewModel.onAppBackgrounded()
    }

    override fun onDestroy() {
        val isWakeWordActive = (application as? AlyaApplication)?.wakeWordManager?.isListening?.value == true
        if (!isWakeWordActive) {
            com.example.audio.AlyaAudioManager.getInstance(this).forceReleaseMicrophone(
                com.example.audio.AudioInterruptionReason.APP_BACKGROUNDED,
                "MainActivity destroyed"
            )
            com.example.audio.AlyaAudioManager.getInstance(this).abandonAudioFocus()
        }
        offlineTtsManager?.shutdown()
        offlineTtsManager = null
        fpsMonitor.stop()
        super.onDestroy()
    }

    private fun handleIntentExtras(intent: Intent?) {
        if (intent == null) return

        if (intent.getBooleanExtra(com.example.domain.scheduler.AlyaWakeUpReceiver.EXTRA_WAKE_UP_TRIGGER, false)) {
            val title = intent.getStringExtra(com.example.domain.scheduler.AlyaWakeUpReceiver.EXTRA_WAKE_UP_TITLE) ?: "Good Morning!"
            val timeStr = intent.getStringExtra(com.example.domain.scheduler.AlyaWakeUpReceiver.EXTRA_WAKE_UP_TIME_STR) ?: ""
            intent.removeExtra(com.example.domain.scheduler.AlyaWakeUpReceiver.EXTRA_WAKE_UP_TRIGGER)
            setIntent(intent)
            viewModel.triggerWakeUpAlarm(title, timeStr)
        }

        if (intent.getBooleanExtra(com.example.service.WakeWordService.EXTRA_OPEN_VOICE_MODE, false)) {
            intent.removeExtra(com.example.service.WakeWordService.EXTRA_OPEN_VOICE_MODE)
            setIntent(intent)
            // Do not force full-screen voice mode on startup; user stays on primary Assistant chat interface
        }

        if (intent.hasExtra("USER_COMMAND")) {
            val command = intent.getStringExtra("USER_COMMAND")
            if (!command.isNullOrBlank()) {
                intent.removeExtra("USER_COMMAND")
                setIntent(intent)
                viewModel.sendVoiceMessage(command)
            }
        }
    }
}

@Composable
fun AlyaApp(viewModel: AlyaViewModel) {
    val authState by viewModel.authManager.authState.collectAsState()
    val currentScreen by viewModel.currentScreen.collectAsState()
    val showChangelog by viewModel.showChangelog.collectAsState()
    val activeWakeUp by viewModel.activeWakeUpAlarm.collectAsState()
    val currentVersionName = viewModel.versionCheckManager.getCurrentVersionName()

    androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = authState,
            transitionSpec = {
                (fadeIn(animationSpec = tween(300)) + slideInHorizontally(animationSpec = tween(300), initialOffsetX = { it / 3 }))
                    .togetherWith(fadeOut(animationSpec = tween(250)) + slideOutHorizontally(animationSpec = tween(250), targetOffsetX = { -it / 3 }))
            },
            label = "AuthFlowTransition"
        ) { state ->
            when (state) {
                is AuthState.Unauthenticated -> {
                    // Authentication flow on launch: hides upper bar options
                    AuthScreen(
                        authManager = viewModel.authManager,
                        onReloadApp = { viewModel.reloadApp() },
                        onAuthenticated = {
                            // Automatically switches state to Authenticated or GuestSession
                        }
                    )
                }
                is AuthState.Locked -> {
                    // Security PIN Lock Screen
                    SecurityLockScreen(
                        user = state.user,
                        authManager = viewModel.authManager,
                        onUnlocked = {
                            // Handled within AuthManager
                        }
                    )
                }
                is AuthState.Authenticated, is AuthState.GuestSession -> {
                    // Main application screen with top bar, assistant tools, and voice mode
                    AnimatedContent(
                        targetState = currentScreen,
                        transitionSpec = {
                            if (targetState == AssistantScreen.VOICE_MODE) {
                                (fadeIn(animationSpec = tween(300)) + androidx.compose.animation.slideInVertically(animationSpec = tween(300), initialOffsetY = { it / 2 }))
                                    .togetherWith(fadeOut(animationSpec = tween(250)) + androidx.compose.animation.slideOutVertically(animationSpec = tween(250), targetOffsetY = { -it / 2 }))
                            } else {
                                (fadeIn(animationSpec = tween(300)) + slideInHorizontally(animationSpec = tween(300), initialOffsetX = { it / 3 }))
                                    .togetherWith(fadeOut(animationSpec = tween(250)) + slideOutHorizontally(animationSpec = tween(250), targetOffsetX = { -it / 3 }))
                            }
                        },
                        label = "ScreenTransition"
                    ) { screen ->
                        when (screen) {
                            AssistantScreen.VOICE_MODE -> {
                                VoiceModeScreen(
                                    viewModel = viewModel,
                                    onClose = { viewModel.stopVoiceMode() }
                                )
                            }
                            else -> {
                                MainChatScreen(viewModel = viewModel)
                            }
                        }
                    }
                }
            }
        }

        if (activeWakeUp != null) {
            com.example.ui.screens.AlyaWakeUpScreen(
                title = activeWakeUp?.first ?: "",
                timeStr = activeWakeUp?.second ?: "",
                viewModel = viewModel,
                onDismiss = { viewModel.dismissWakeUpAlarm() }
            )
        }

        if (showChangelog && activeWakeUp == null && (authState is AuthState.Authenticated || authState is AuthState.GuestSession)) {
            com.example.ui.components.WhatsNewDialog(
                versionName = currentVersionName,
                onDismiss = { viewModel.versionCheckManager.markChangelogSeen() }
            )
        }

        val showWakeUpActivation by viewModel.showWakeUpActivationDialog.collectAsState()
        if (showWakeUpActivation) {
            com.example.ui.components.ImproveVoiceAccuracyDialog(
                viewModel = viewModel,
                onDismiss = { viewModel.dismissWakeUpActivationDialog() }
            )
        }

        val showUpdatePrompt by viewModel.showUpdatePrompt.collectAsState()
        val latestUpdateInfo by viewModel.latestUpdateInfo.collectAsState()
        val updateStatus by viewModel.updateStatus.collectAsState()

        if (showUpdatePrompt && latestUpdateInfo != null) {
            latestUpdateInfo?.let { info ->
                com.example.update.UpdatePromptDialog(
                    updateInfo = info,
                    updateStatus = updateStatus,
                    onDownloadStart = { viewModel.startNewUpdateDownload() },
                    onCancelDownload = { viewModel.cancelNewUpdateDownload() },
                    onDismiss = { viewModel.dismissUpdatePrompt() }
                )
            }
        }

        com.example.ui.components.ListeningPopup(viewModel = viewModel)
    }
}
