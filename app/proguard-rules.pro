# ProGuard rules for Alya Voice Assistant

# Preserve TensorFlow Lite classes and native methods
-keep class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.lite.**

# Preserve Room Database DAOs and Entities
-keep class androidx.room.** { *; }
-keep class com.example.data.local.** { *; }
-dontwarn androidx.room.**

# Preserve Android Services, Receivers, and Application
-keep class com.example.AlyaApplication { *; }
-keep class com.example.service.** { *; }
-keep class com.example.domain.scheduler.** { *; }
-keep class com.example.update.** { *; }
-keep class com.example.voice.** { *; }

# Preserve Kotlin Coroutines and Serialization
-keep class kotlinx.coroutines.** { *; }
-keep class kotlinx.serialization.** { *; }
-dontwarn kotlinx.coroutines.**

# Preserve Vosk STT engine and Kaldi JNI
-keep class org.kaldi.** { *; }
-keep class org.vosk.** { *; }
-dontwarn org.kaldi.**
-dontwarn org.vosk.**

# Preserve OpenWakeWord engine
-keep class xyz.rementia.openwakeword.** { *; }
-dontwarn xyz.rementia.openwakeword.**

# Preserve Moshi models and adapters
-keep class com.squareup.moshi.** { *; }
-keepclassmembers class * {
    @com.squareup.moshi.Json *;
}

# Preserve Retrofit interfaces and response types
-keep class retrofit2.** { *; }
-dontwarn retrofit2.**

# Preserve OkHttp
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**

# Preserve Google Play / Firebase components
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
