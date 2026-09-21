"""
ALYA brains.
- OnlineBrain : Gemini API (needs internet + key). Any error -> returns None,
                main loop then automatically uses OfflineBrain. NEVER crashes.
- OfflineBrain: fully local intent parser. Understands English, Hindi/Hinglish,
                Bangla/Banglish keywords for device control. Always available.
Both return the SAME format: (spoken_reply, command_dict_or_None)
"""
import re, json
import config

try:
    from google import genai
except Exception:
    genai = None


# ---------------------------------------------------------------- ONLINE
class OnlineBrain:
    def __init__(self):
        self.ok = False
        if genai and config.GEMINI_API_KEY:
            try:
                self.client = genai.Client(api_key=config.GEMINI_API_KEY)
                self.ok = True
            except Exception:
                self.ok = False

    def think(self, user_text):
        """Return (reply, command). None,(err) on failure -> caller falls back."""
        if not self.ok:
            return None
        try:
            resp = self.client.models.generate_content(
                model=config.GEMINI_MODEL,
                contents=user_text,
                config={"system_instruction": config.SYSTEM_PROMPT,
                        "temperature": 0.2})
            text = resp.text
            cmd = self._extract_json(text)
            reply = cmd.get("reply_to_user") if cmd else text.strip()[:200]
            return reply, cmd
        except Exception:
            return None  # silent -> offline brain takes over

    @staticmethod
    def _extract_json(text):
        m = re.search(r"```json\s*(\{.*?\})\s*```", text, re.DOTALL)
        if not m:
            m = re.search(r"(\{.*\})", text, re.DOTALL)
        if not m:
            return None
        try:
            return json.loads(m.group(1))
        except Exception:
            return None


# ---------------------------------------------------------------- OFFLINE
class OfflineBrain:
    """
    Zero-dependency intent parser. Not an AI — but it understands the core
    commands in EN/HI/BN and ALWAYS works. Gemini (online) handles everything
    more complex when available.
    """

    DEVICES = {
        "light":   ["light", "batti", "bati", "লাইট", "alo", "áló", "lalten"],
        "fan":     ["fan", "pankha", "পাখা", "paka"],
        "ac":      ["ac", "a c", "এসি", "air conditioner", "kooler", "cooler"],
        "tv":      ["tv", "t v", "টিভি", "television"],
        "geyser":  ["geyser", "geejer", "গিজার", "heater", "bojler"],
        "curtain": ["curtain", "parda", "পর্দা", "blind"],
        "music":   ["music", "gaana", "গান", "song", "gana"],
    }

    ON_WORDS  = ["on", "jalao", "jala", "chalao", "চালু", "jalao", "kholo",
                 "start", "চালু করো", "chalu"]
    OFF_WORDS = ["off", "band", "বন্ধ", "bujhao", "bondho", "bondo", "stop",
                 "বন্ধ করো", "bandh"]
    ALL_WORDS = ["sab", "sob", "সব", "everything", "all", "sabhi"]
    DIM_WORDS = ["dim", "kam", "কম", "dheere", "low"]
    BRIGHT_WORDS = ["bright", "tez", "তেজ", "zada", "full", "high"]
    SET_PAT = re.compile(r"(\d{2})\s*(degree|deg|°c|°|%)")

    SCENES = {
        "good night":  "Shubh ratri! Lights off, AC 26, alarm subah 7 baje.",
        "movie mode":  "Movie mode — lights dim, TV on. Popcorn banao!",
        "sleep":       "Sleep scene on. Sweet dreams!",
    }

    def think(self, text):
        t = " " + text.lower().strip() + " "

        # --- scenes ---
        for k, v in self.SCENES.items():
            if k in t:
                return v, {"action": "scene", "parameters": {"scene": k},
                           "reply_to_user": v}

        # --- alarms (stored + spoken locally) ---
        m = re.search(r"(\d{1,2})\s*(am|pm|baje)?", t) if "alarm" in t or "uth" in t or "wake" in t or "উঠ" in t else None
        if m and ("alarm" in t or "uth" in t or "wake" in t or "উঠ" in t):
            hh = int(m.group(1))
            if m.group(2) == "pm" and hh < 12:
                hh += 12
            when = "AM" if hh < 12 else "PM"
            h12 = hh if hh <= 12 else hh - 12
            reply = (f"Alarm set ho gaya — {h12} {when}. "
                     f"Main khud aapka naam lekar uthaungi, internet ke bina bhi.")
            return reply, {"action": "alarm",
                           "parameters": {"hour": hh, "minute": 0},
                           "reply_to_user": reply}

        # --- status queries ---
        if any(w in t for w in ["temperature", "tapman", "তাপমাত্রা", "tapmatra", "status", "kya chal"]):
            reply = "Abhi sab normal chal raha hai. Details app panel me hain."
            return reply, {"action": "read", "device": "sensors",
                           "reply_to_user": reply}

        # --- all off (needs confirm) ---
        if any(w in t for w in self.ALL_WORDS) and any(w in t for w in self.OFF_WORDS):
            reply = "Sab kuch band karun? Haan ya na bolo — confirm karo."
            return reply, {"action": "all_off", "requires_confirmation": True,
                           "safety_level": "caution",
                           "reply_to_user": reply}

        # --- find device ---
        device = None
        for name, words in self.DEVICES.items():
            if any(w in t for w in words):
                device = name
                break
        if not device:
            return ("Hmm, ye samajh nahi aaya. Device ka naam bolo — "
                    "jaise light, fan, AC, TV."), None

        # --- find action ---
        if any(w in t for w in self.OFF_WORDS):
            action, val = "off", None
        elif any(w in t for w in self.ON_WORDS):
            action, val = "on", None
        elif any(w in t for w in self.DIM_WORDS):
            action, val = "set", 30
        elif any(w in t for w in self.BRIGHT_WORDS):
            action, val = "set", 100
        else:
            m = self.SET_PAT.search(t)
            if m:
                action = "set"
                val = int(m.group(1))
                if device == "ac":
                    action = "set_temp"
            else:
                return (f"{device} ke saath kya karun — on, off, ya level batao?"), None

        unit = "°C" if action == "set_temp" else "%"
        verb = {"on": "on kar diya", "off": "band kar diya",
                "set": f"{val}% par set kar diya",
                "set_temp": f"{val} degree par set kar diya"}[action]
        reply = f"{device.capitalize()} {verb}."
        return reply, {"device_domain": "smart_home", "device": device,
                       "action": action, "parameters": {"value": val, "unit": unit},
                       "reply_to_user": reply,
                       "requires_confirmation": False, "safety_level": "safe"}
