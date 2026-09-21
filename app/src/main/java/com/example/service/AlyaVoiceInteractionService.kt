package com.example.service

import android.service.voice.VoiceInteractionService

/**
 * Alya VoiceInteractionService
 * 
 * Necessary to register Alya as a system-level assistant, allowing it to bypass 
 * certain background restrictions for microphone access on Android 14+ (targetSDK 36).
 */
class AlyaVoiceInteractionService : VoiceInteractionService() {
    // Basic implementation for manifest registration
}
