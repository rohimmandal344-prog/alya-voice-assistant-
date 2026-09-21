package com.example.voice.wakeword

interface HotwordDetector {
    fun startDetection(keyword: String, onDetect: () -> Unit)
    fun stopDetection()
    fun destroy()
}
