"""
ALYA configuration — edit this file only.
"""
import os

# ---------- USER ----------
USER_NAME = "Boss"          # <-- YOUR NAME: Alya speaks this at wake-up alarms
USER_LANG = "auto"          # auto | en | hi | bn   (reply language preference)

# ---------- OFFLINE SPEECH ----------
VOSK_MODEL = "vosk-model-small-en-us-0.15"   # folder name of downloaded model
SAMPLE_RATE = 16000

# ---------- ONLINE BRAIN (Gemini) ----------
GEMINI_API_KEY = os.environ.get("GEMINI_API_KEY", "")
GEMINI_MODEL = "gemini-2.5-flash"            # fast + cheap, works great
NET_TIMEOUT = 4                              # seconds to detect internet

# The system prompt given to Gemini (condensed version of your AI Studio packs).
# If you built longer packs there, paste them here — same effect.
SYSTEM_PROMPT = """You are ALYA, a warm multilingual voice device-control assistant.
Reply in the SAME language the user uses (English, Hindi/Hinglish, Bangla/Banglish).
Rules:
1) Keep spoken replies under 15 words.
2) After the short reply, ALWAYS output ONE fenced ```json block:
{"device_domain":"smart_home|mobile_phone|general_assistant",
 "device":"name","action":"on|off|set|read|scene|alarm|schedule|chat",
 "parameters":{"value":0,"unit":"%|°C|level","text":"..."},
 "requires_confirmation":false,"safety_level":"safe",
 "reply_to_user":"<your short spoken reply>"}
3) "chat" action = normal conversation, reply_to_user holds the full answer, no device.
4) Ambiguous command -> ask ONE short question, no JSON.
5) Dangerous (unlock door, oven on, msg others) -> requires_confirmation true.
6) NEVER claim you executed anything; app executes locally. Be honest about limits.
Scenes: "good night"=lights off,AC 26,fan low,alarm 7am | "movie mode"=TV on,lights 20%,curtains close."""

# ---------- REAL DEVICES (optional) ----------
# Fill these to control REAL devices through Home Assistant.
# Leave HA_URL empty to run in safe SIMULATION mode.
HA_URL = ""        # e.g. "http://192.168.1.10:8123"
HA_TOKEN = ""      # long-lived access token from HA profile page

# ---------- SAFETY ----------
MAX_VOLUME_PERCENT = 100
NEEDS_CONFIRM = {"door_unlock", "vehicle_unlock", "oven_on", "geyser_on",
                 "send_message", "all_off"}
