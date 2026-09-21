package com.example.service

import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

class AlyaVoiceInteractionSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        return AlyaVoiceInteractionSession(this)
    }
}

class AlyaVoiceInteractionSession(context: android.content.Context) : VoiceInteractionSession(context) {
    override fun onCreate() {
        super.onCreate()
        // Assistant session created
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        // Triggered when user long-presses home or says wake word (if OS handled)
        try {
            WakeUpPopup.show(context)
            finish() // We use our own floating UI
        } catch (e: Exception) {
            finish()
        }
    }
}
