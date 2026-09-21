package com.example.voice

import android.speech.RecognitionService
import android.content.Intent
import android.os.Bundle
import android.speech.SpeechRecognizer
import android.util.Log

/**
 * Custom RecognitionService required for VoiceInteractionService integration.
 */
class AlyaRecognitionService : RecognitionService() {
    override fun onStartListening(intent: Intent?, listener: Callback?) {
        Log.i("AlyaRecognitionService", "onStartListening")
        // Implementation can be added if we want to support system-wide speech recognition
    }

    override fun onCancel(listener: Callback?) {
        Log.i("AlyaRecognitionService", "onCancel")
    }

    override fun onStopListening(listener: Callback?) {
        Log.i("AlyaRecognitionService", "onStopListening")
    }
}
