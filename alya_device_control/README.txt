ALYA — Voice Device Controller (Online + Offline)
==================================================

WHAT THIS IS
------------
A complete voice assistant loop:
  1. LISTEN  : offline speech-to-text (Vosk — works with NO internet)
  2. THINK   : ONLINE  -> Gemini API brain (your AI Studio prompt)
             : OFFLINE -> built-in intent brain (English/Hindi/Bangla keywords)
  3. SPEAK   : offline text-to-speech (pyttsx3 — works with NO internet)
  4. EXECUTE : device simulator built-in + OPTIONAL real smart-home hook
               (Home Assistant REST API) — the alarm at 7 AM speaking your
               name is handled 100% locally, internet NOT required.

ONLINE vs OFFLINE (automatic)
-----------------------------
At startup the app pings Google. Internet + valid API key -> Gemini brain.
Anything missing -> local brain. Same voice, same devices, no crash ever.

SETUP
-----
1.  Python 3.10+ installed.
2.  pip install -r requirements.txt
3.  Offline speech model (English, ~40 MB):
       https://alphacephei.com/vosk/models  -> download vosk-model-small-en-us-0.15
       unzip it, place folder next to alya.py (or set VOSK_MODEL path in config.py)
    Hindi model: vosk-model-small-hi-0.22 | Bangla: vosk-model-small-bn-in (if listed)
4.  (Optional, for online brain) get a free API key:
       https://aistudio.google.com/app/apikey
       set it as an environment variable:
       Linux/Mac: export GEMINI_API_KEY="your_key"
       Windows  : setx GEMINI_API_KEY "your_key"
5.  Edit config.py -> put YOUR name in USER_NAME (this is the name Alya
    speaks during the 7 AM wake-up).
6.  Run:  python alya.py

USAGE EXAMPLES (say any of these)
---------------------------------
  "Alya, light on"              / "light jalao"          / "alo jalao"
  "fan off"                     / "fan band karo"        / "paka bondho koro"
  "AC 24 degrees"               / "AC chowbees degree"   / "এসি ২৪ ডিগ্রি"
  "set alarm 7 AM"              / "subah 7 baje uthana"  / "সকাল ৭টায় দেবে"
  "turn everything off"         / "sab band karo"        / "sob bondho koro"
  "what is the temperature"     / "kitna temperature"    / "tapmatra koto"
  "good night"  -> sleep scene  |  "movie mode" -> scene
  "exit" / "stop" / "band karo alya"  -> quit

ALARM FEATURE (the one you asked about)
---------------------------------------
"set alarm 7 AM"  -> stored LOCALLY in alarms.json.
At 7:00 your laptop/phone speaks, even with Wi-Fi off:
   "Alya bol rahi hoon... uth jao <YOUR NAME>! Subah ke 7 baj gaye!"
A scheduler thread checks every second. Zero internet needed.

REAL DEVICE CONTROL (optional)
------------------------------
Out of the box devices are SIMULATED (printed on screen).
To control REAL devices, install Home Assistant (free), then fill in
HA_URL and HA_TOKEN in config.py. Every command is then sent to HA.
Without HA, simulation mode lets you test everything safely.

TROUBLESHOOTING
---------------
- Mic not found -> check Windows/Mac microphone permission for terminal.
- Vosk error "model not found" -> download the model (step 3).
- Online mode not engaging -> key missing or no internet; app auto-switches
  to offline brain and tells you. This is normal, not an error.
- If anything crashes -> the app restarts its own listening loop and says so.
