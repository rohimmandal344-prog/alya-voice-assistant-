# Alya Assistant — 100% Open-Source & Free Architecture

Alya Assistant is architected from the ground up to be **100% free, open-source, private, and on-device first**, with zero dependence on proprietary API services or mandatory external environment variables.

---

## 1. Core Architecture Overview

```
                          ┌───────────────────────────┐
                          │    User Speech / Audio    │
                          └─────────────┬─────────────┘
                                        │
                         ┌──────────────▼──────────────┐
                         │   Vosk / On-Device STT      │  (100% On-Device / Offline)
                         └──────────────┬──────────────┘
                                        │
                         ┌──────────────▼──────────────┐
                         │   ModelProvider Registry    │
                         └──────┬───────────────┬──────┘
                                │               │
        ┌───────────────────────▼───┐       ┌───▼────────────────────────┐
        │   LocalModelProvider      │       │   SelfHostedModelProvider  │
        │   (100% On-Device NLU)    │       │   (Optional Local Server)  │
        │   • Zero API keys         │       │   • Ollama / llama.cpp     │
        │   • Zero external servers │       │   • Graceful local fallback│
        │   • Zero env variables    │       │   • Real-time SSE stream   │
        └───────────────┬───────────┘       └───┬────────────────────────┘
                        │                       │
                        └───────────────┬───────┘
                                        │
                         ┌──────────────▼──────────────┐
                         │   Alya Decision Engine      │
                         │   & Real Android Tools      │
                         │   • Wi-Fi, Bluetooth, Apps  │
                         │   • Alarms, Reminders, Media│
                         │   • State Verification      │
                         └──────────────┬──────────────┘
                                        │
                         ┌──────────────▼──────────────┐
                         │  On-Device Neural TTS Engine│  (Streaming Synthesis)
                         └──────────────┬──────────────┘
                                        │
                         ┌──────────────▼──────────────┐
                         │    Speaker / Audio Output   │
                         └─────────────────────────────┘
```

---

## 2. ModelProvider Abstraction

The conversational intelligence uses a clean `ModelProvider` hierarchy:
- `ModelProvider`: Generic contract with streaming text generation, structured actions, system instructions, coroutine cancellation, interruption handling, and capability detection.
- `LocalModelProvider` (Default): 100% On-device natural language understanding engine with entity extraction, rule-based reasoning, memory grounding, and structured device action execution. Requires zero network and zero configuration.
- `SelfHostedModelProvider` (Optional): Connects to an optional local server (Ollama, llama.cpp, vLLM) if configured in settings. Gracefully and automatically delegates to `LocalModelProvider` if the server is offline or unreachable.

---

## 3. Live Voice Pipeline

```
SPEECH INPUT -> VAD -> ON-DEVICE/OPEN-SOURCE STT -> LOCAL/SELF-HOSTED MODEL -> LIVE SUBTITLES -> STREAMING NATURAL VOICE -> INTERRUPTION HANDLING -> CONTINUOUS LISTENING
```

- **Speech Recognition**: Vosk Acoustic Models & Android Speech Engine for low-latency on-device transcription.
- **Voice Activity Detection & Barge-In**: Real-time voice onset detection, acoustic echo cancellation, and immediate playback interruption when the user speaks.
- **Neural Text-to-Speech**: Streaming synthesis with pitch, speed, and multilingual prosody controls.

---

## 4. Real Device Control & Verification

Alya enforces the **Source-of-Truth Principle**:
`ACTION -> ANDROID API EXECUTION -> SYSTEM STATE QUERY -> VERIFY -> TRUTHFUL SPOKEN FEEDBACK`

All device controls (Wi-Fi, Bluetooth, Flashlight, Volume, App Launching, Alarms, Timers, Contacts, Calls, Media Session) execute directly against real Android OS APIs with permission checking and capability reporting.
