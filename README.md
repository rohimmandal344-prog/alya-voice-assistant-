# Alya AI Voice Assistant (v2.3.0 Stable)

Alya (Alisa Mikhailovna Kujou) is an advanced, production-ready, ChatGPT-style real-time voice assistant built for Android 10 through Android 17+.

## Release Notes — Version 2.3.0 (Stable Build)

### 🚀 Key Capabilities & Refinements
- **Version Compatibility Layer (Android 10 → 17+)**:
  - Runtime API detection and compatibility wrappers (`CompatUtils`) ensuring smooth execution across Android 10 (API 29) to Android 17+ (API 37+).
  - Proper handling of Foreground Service types (`foregroundServiceType="microphone"`), `POST_NOTIFICATIONS` runtime permissions, System Alert Window overlays (`canDrawOverlays`), exact alarms, and lock-screen display flags.
- **Offline & Error Resilience**:
  - Offline network detection with `ConnectivityManager.NetworkCallback` to cleanly handle network drops without infinite microphone thrashing or polling loops.
  - Bounded retry backoff strategy for recognizer busy states (`ERROR_RECOGNIZER_BUSY`).
  - Automatic fallback mechanisms for speech recognizer errors and missing language engines.
- **On-Device Wake-Word Engine**:
  - Multi-wake-word activation for **"Alia"**, **"Alya"**, and **"Seno"** with high-priority audio buffers (`THREAD_PRIORITY_URGENT_AUDIO`).
  - Dual popup display support: Floating overlay window over other apps (`FloatingAssistantOverlay`) and responsive bottom listening popup (`ListeningBottomPopup`) in-app with direct "Send as Text" options.
- **Voice Session State Machine**:
  - Centralized single-owner microphone lifecycle (`IDLE` -> `WAKE_DETECTED` -> `LISTENING` -> `PROCESSING` -> `RESPONDING` -> `SPEAKING` -> `STOPPED`).
  - Strict resource disposal discipline to eliminate audio thread leaks and mic conflicts.
- **Permissions & Capabilities Engine**:
  - Transparent M3 Permissions Dashboard with real-time system status checks for microphone, notification, location, accessibility, and background service execution.
  - 3-step Voice Enrollment wizard with secure voice profile management.

## Project Structure
- `app/src/main/java/com/example/util/CompatUtils.kt` — System API version compatibility wrappers.
- `app/src/main/java/com/example/voice/SpeechRecognitionManager.kt` — Resilience speech recognition core.
- `app/src/main/java/com/example/voice/wakeword/TfLiteWakeWordManager.kt` — Wake-word detection engine.
- `app/src/main/java/com/example/capability/CapabilityManager.kt` — Live system capabilities checker.
- `app/src/main/java/com/example/ui/components/ListeningBottomPopup.kt` — Voice interaction bottom popup UI.
- `app/src/main/java/com/example/ui/screens/PermissionsCapabilitiesSheet.kt` — Capabilities and voice enrollment dashboard.

---
*Build Version:* `2.3.0` (Version Code `25`)
