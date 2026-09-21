package com.example.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.AlyaApplication
import com.example.MainActivity
import com.example.voice.wakeword.WakeWordManager
import com.example.voice.wakeword.WakeWordKeyword

/**
 * Foreground service with:
 * 1. Persistent ongoing notification
 * 2. On-screen floating bubble overlay (SYSTEM_ALERT_WINDOW)
 * 3. Integrated TensorFlow Lite Wake-Word detection in the background
 */
class FloatingBubbleService : Service() {

    private var windowManager: WindowManager? = null
    private var bubbleView: View? = null
    private var wakeWordManager: WakeWordManager? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "FloatingBubbleService onCreate")
        wakeWordManager = (application as AlyaApplication).wakeWordManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopBubbleService()
                return START_NOT_STICKY
            }
            ACTION_TRIGGER_WAKE -> {
                openAppFromWakeWord()
            }
            else -> {
                startForegroundWithNotification()
                // Floating bubble overlay completely removed as requested
                startWakeWordDetection()
            }
        }
        return START_STICKY
    }

    private fun startForegroundWithNotification() {
        isRunning = true

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            201,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, FloatingBubbleService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            202,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, AlyaApplication.BUBBLE_SERVICE_CHANNEL_ID)
            .setContentTitle("Alisa Assistant Active")
            .setContentText("TensorFlow Lite wake-word active. Tap bubble or say the wake-up name.")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Dismiss Bubble", stopPendingIntent)
            .build()

        val hasMicPermission = androidx.core.content.ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (!hasMicPermission) {
            Log.i(TAG, "RECORD_AUDIO permission not granted yet. Operating FloatingBubbleService as standard background service.")
            return
        }

        val foregroundServiceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            0
        } else {
            0
        }

        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                foregroundServiceType
            )
            Log.i(TAG, "Successfully started foreground service with type: $foregroundServiceType")
        } catch (e: Exception) {
            Log.e(TAG, "ServiceCompat.startForeground failed: ${e.message}")
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceCompat.startForeground(
                        this,
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
                Log.i(TAG, "Successfully started foreground service with fallback")
            } catch (ex: Exception) {
                Log.e(TAG, "Fallback failed: ${ex.message}. Continuing in best-effort background mode.")
                isRunning = true 
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupFloatingBubble() {
        // Can only draw overlay if SYSTEM_ALERT_WINDOW is granted
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Cannot draw overlay: SYSTEM_ALERT_WINDOW not granted.")
            return
        }

        if (bubbleView != null) return // Already attached

        try {
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val density = resources.displayMetrics.density
            val bubbleSize = (64 * density).toInt()

            val params = WindowManager.LayoutParams(
                bubbleSize,
                bubbleSize,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = (20 * density).toInt()
                y = (120 * density).toInt()
            }

            // Create programmatically styled floating bubble with Alya branding
            val container = FrameLayout(this).apply {
                val shape = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    colors = intArrayOf(
                        Color.parseColor("#7C4DFF"), // Sleek Purple
                        Color.parseColor("#536DFE")  // Indigo accent
                    )
                    gradientType = GradientDrawable.LINEAR_GRADIENT
                    setStroke((2 * density).toInt(), Color.parseColor("#E0E7FF"))
                }
                background = shape
                elevation = 12f * density
            }

            val innerText = TextView(this).apply {
                text = "AK"
                setTextColor(Color.WHITE)
                textSize = 22f
                gravity = Gravity.CENTER
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            val textParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ).apply {
                gravity = Gravity.CENTER
            }
            container.addView(innerText, textParams)

            // Touch & Drag listener with click detection
            var initialX = 0
            var initialY = 0
            var initialTouchX = 0f
            var initialTouchY = 0f
            var isClick = false

            container.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isClick = true
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val diffX = event.rawX - initialTouchX
                        val diffY = event.rawY - initialTouchY
                        if (Math.hypot(diffX.toDouble(), diffY.toDouble()) > 10) {
                            isClick = false
                        }
                        params.x = initialX + diffX.toInt()
                        params.y = initialY + diffY.toInt()
                        windowManager?.updateViewLayout(container, params)
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (isClick) {
                            openAppFromWakeWord()
                        }
                        true
                    }
                    else -> false
                }
            }

            windowManager?.addView(container, params)
            bubbleView = container
            Log.i(TAG, "Floating bubble successfully added to WindowManager.")
        } catch (e: Exception) {
            Log.e(TAG, "Error creating floating bubble view: ${e.message}", e)
        }
    }

    private fun startWakeWordDetection() {
        val prefs = (application as? AlyaApplication)?.preferencesManager
        val isWakeEnabled = prefs?.isWakeWordEnabled() ?: true
        if (isWakeEnabled) {
            val sensitivity = prefs?.wakeWordSensitivity?.value ?: 0.5f
            val keywordName = prefs?.selectedWakeWord?.value ?: "ALYA"
            
            wakeWordManager?.start(
                keyword = keywordName,
                sensitivity = sensitivity
            )
        }
    }

    private fun openAppFromWakeWord() {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(EXTRA_FROM_WAKE_WORD, true)
        }
        startActivity(launchIntent)
    }

    private fun stopBubbleService() {
        isRunning = false
        wakeWordManager?.stop()
        if (bubbleView != null) {
            try {
                windowManager?.removeView(bubbleView)
            } catch (e: Exception) { android.util.Log.e("Alya", "Exception handled", e) }
            bubbleView = null
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopBubbleService()
    }

    companion object {
        const val NOTIFICATION_ID = 2002
        const val ACTION_START = "com.example.service.ACTION_START_BUBBLE"
        const val ACTION_STOP = "com.example.service.ACTION_STOP_BUBBLE"
        const val ACTION_TRIGGER_WAKE = "com.example.service.ACTION_TRIGGER_WAKE"
        const val EXTRA_FROM_WAKE_WORD = "extra_from_wake_word"
        private const val TAG = "FloatingBubbleService"

        var isRunning: Boolean = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, FloatingBubbleService::class.java).apply {
                action = ACTION_START
            }
            val hasMicPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.RECORD_AUDIO
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && hasMicPermission) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, FloatingBubbleService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
