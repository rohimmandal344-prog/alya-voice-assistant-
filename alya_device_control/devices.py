"""
ALYA body — device execution + the LOCAL alarm clock.
Alarms are stored in alarms.json and fire on a background thread,
calling the user's NAME via TTS. Works 100% offline.
If Home Assistant is configured, real devices are controlled instead of
the simulator.
"""
import json, os, threading, time, datetime
import requests
import config


class AlarmClock:
    """Local scheduler: rings even with zero internet."""

    def __init__(self, voice):
        self.voice = voice
        self.store = os.path.join(os.path.dirname(__file__), "alarms.json")
        self.pending = self._load()
        self._confirm = None          # ('all_off', reply) waiting for yes/no
        threading.Thread(target=self._loop, daemon=True).start()

    def _load(self):
        try:
            with open(self.store) as f:
                return json.load(f)
        except Exception:
            return []

    def _save(self):
        try:
            with open(self.store, "w") as f:
                json.dump(self.pending, f)
        except Exception:
            pass

    def add(self, hour, minute):
        self.pending.append({"hour": hour, "minute": minute,
                             "created": time.time()})
        self._save()

    def _loop(self):
        while True:
            now = datetime.datetime.now()
            fired = False
            for a in list(self.pending):
                if now.hour == a["hour"] and now.minute == a["minute"]:
                    self.pending.remove(a)
                    fired = True
                    self.voice.say(
                        f"Alya bol rahi hoon... uth jao {config.USER_NAME}! "
                        f"Subah ke {a['hour']} baj gaye! Din shuru karo!")
            if fired:
                self._save()
            time.sleep(5)


class DeviceManager:
    """Executes commands. Simulator by default; real via Home Assistant."""

    def __init__(self):
        self.state = {}   # simulator memory: {"light": "on 80%"}

    # ---------------- confirmation flow ----------------
    def needs_confirm(self, cmd):
        return bool(cmd) and (cmd.get("requires_confirmation")
                              or cmd.get("action") in config.NEEDS_CONFIRM)

    # ---------------- main entry ----------------
    def execute(self, cmd, confirmed=False):
        if not cmd:
            return
        if self.needs_confirm(cmd) and not confirmed:
            return "WAITING_CONFIRM"
        action = cmd.get("action")
        params = cmd.get("parameters") or {}
        device = cmd.get("device", "?")

        if action == "alarm":
            self._ha("script", "alya_alarm")  # optional HA mirror
            return  # AlarmClock handles the real ring locally

        if action == "scene":
            return self._scene(params.get("scene"))
        if action == "chat":
            return
        if action == "read":
            print(f"[sensor] simulated reading -> {self.state}")
            return

        # numeric safety clamp
        val = params.get("value")
        if isinstance(val, (int, float)):
            val = max(0, min(config.MAX_VOLUME_PERCENT, val))

        # try Home Assistant first, else simulate
        ok = self._ha(device, action, val)
        if not ok:
            label = f"{action}" + (f" {val}{params.get('unit','')}" if val is not None else "")
            self.state[device] = label
            print(f"[SIMULATOR] {device} -> {label}   (no Home Assistant set — simulated)")

    # ---------------- scenes ----------------
    def _scene(self, name):
        scenes = {
            "good night": [("light", "off", None), ("ac", "set", 26),
                           ("fan", "set", 30), ("curtain", "close", None)],
            "movie mode": [("tv", "on", None), ("light", "set", 20),
                           ("curtain", "close", None)],
        }
        for dev, act, val in scenes.get(name, []):
            self.execute({"device": dev, "action": act,
                          "parameters": {"value": val}})

    # ---------------- Home Assistant hook ----------------
    def _ha(self, device, action, val=None):
        if not config.HA_URL:
            return False
        try:
            headers = {"Authorization": f"Bearer {config.HA_TOKEN}",
                       "Content-Type": "application/json"}
            service = {"on": "turn_on", "off": "turn_off",
                       "set": "turn_on", "set_temp": "turn_on"}.get(action, "turn_on")
            data = {"entity_id": f"{device}.{device}"}
            if action == "set_temp" and val:
                data["temperature"] = val
            elif action in ("set",) and val is not None:
                data["brightness_pct"] = val
            requests.post(f"{config.HA_URL}/api/services/{device.split('_')[0]}/{service}",
                          headers=headers, json=data, timeout=4)
            return True
        except Exception:
            return False
