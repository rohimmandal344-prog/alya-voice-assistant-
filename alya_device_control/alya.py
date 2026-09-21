"""
ALYA — main loop.
Online + Offline voice device control. Fail-safe by design:
  * internet check with timeout -> offline brain if anything fails
  * every exception caught -> Alya apologizes and keeps listening
  * nothing here can permanently crash the loop
"""
import time, requests
import config
from speech_io import Ears, Voice
from brains import OnlineBrain, OfflineBrain
from devices import DeviceManager, AlarmClock


def internet_ok():
    try:
        requests.head("https://www.google.com", timeout=config.NET_TIMEOUT)
        return True
    except Exception:
        return False


def main():
    voice = Voice()
    ears = Ears()
    online = OnlineBrain()
    offline = OfflineBrain()
    devices = DeviceManager()
    clock = AlarmClock(voice)

    net = internet_ok() and online.ok
    mode = "ONLINE (Gemini brain)" if net else "OFFLINE (local brain)"
    voice.say(f"Namaste! Main Alya hoon — {mode} se chal rahi hoon. Boliye kya karna hai?")
    if not net and config.GEMINI_API_KEY:
        voice.say("Internet nahi mila, isliye offline mode me hoon. Sab kaam chalega.")

    waiting_confirm = None   # (cmd, reply) pending yes/no

    while True:
        try:
            text = ears.listen()
            if not text:
                continue

            # ---- quit words ----
            if any(w in text for w in ["exit", "stop alya", "quit",
                                       "band karo alya", "বন্ধ করো"]):
                voice.say(f"Bye {config.USER_NAME}! Alya yahin hai, bula lena.")
                break

            # ---- confirmation answers ----
            if waiting_confirm:
                if any(w in text for w in ["yes", "haan", "ha", "হ্যাঁ", "ok", "theek"]):
                    cmd, _ = waiting_confirm
                    devices.execute(cmd, confirmed=True)
                    voice.say("Ho gaya! Sab kuch band kar diya.")
                    waiting_confirm = None
                    continue
                else:
                    voice.say("Theek hai, cancel kar diya.")
                    waiting_confirm = None
                    continue

            # ---- think ----
            result = online.think(text) if (net and online.ok) else None
            used = "online"
            if result is None:
                result = offline.think(text)
                used = "offline"
                if net and online.ok:
                    voice.say("Online brain me dikkat aayi, offline se kar rahi hoon.")

            reply, cmd = result

            # ---- confirmations for dangerous commands ----
            if devices.needs_confirm(cmd):
                waiting_confirm = (cmd, reply)
                voice.say(reply or "Ye pakka karna hai? Haan ya na?")
                continue

            # ---- speak ----
            if reply:
                voice.say(reply)

            # ---- execute ----
            devices.execute(cmd)
            if cmd and cmd.get("action") == "alarm":
                p = cmd.get("parameters", {})
                clock.add(int(p.get("hour", 7)), int(p.get("minute", 0)))

        except KeyboardInterrupt:
            voice.say(f"Bye {config.USER_NAME}!")
            break
        except Exception as e:
            # the fail-safe: never die
            print(f"[error recovered] {e}")
            voice.say("Oops, ek second ki dikkat thi. Phir se bolo?")
            time.sleep(0.5)


if __name__ == "__main__":
    main()
