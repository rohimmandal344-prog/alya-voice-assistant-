"""
ALYA ears + voice. 100% offline: Vosk (listen) + pyttsx3 (speak).
Any failure degrades gracefully, never crashes the main loop.
"""
import json, queue, sys, threading
import sounddevice as sd
from vosk import Model, KaldiRecognizer

import config


class Ears:
    """Offline speech-to-text using Vosk."""

    def __init__(self):
        self.q = queue.Queue()
        self.model = None
        self.rec = None
        self.ok = False
        try:
            self.model = Model(config.VOSK_MODEL)
            self.rec = KaldiRecognizer(self.model, config.SAMPLE_RATE)
            self.ok = True
        except Exception as e:
            print(f"[Ears] STT model not ready ({e}). "
                  f"Download: https://alphacephei.com/vosk/models")
            print(f"[Ears] You can still TYPE commands below.")

    def _callback(self, indata, frames, time, status):
        if status:
            print(status, file=sys.stderr)
        self.q.put(bytes(indata))

    def listen(self):
        """Return recognized text (lowercased) or ''. Falls back to typing."""
        if not self.ok:
            typed = input("[type command] > ").strip()
            return typed.lower()

        with sd.RawInputStream(samplerate=config.SAMPLE_RATE, blocksize=8000,
                               dtype="int16", channels=1, callback=self._callback):
            while True:
                data = self.q.get()
                if self.rec.AcceptWaveform(data):
                    try:
                        text = json.loads(self.rec.Result()).get("text", "")
                    except Exception:
                        text = ""
                    if text.strip():
                        return text.lower().strip()


class Voice:
    """Offline text-to-speech using pyttsx3. Thread-safe queue."""

    def __init__(self):
        self.ok = False
        try:
            import pyttsx3
            self.engine = pyttsx3.init()
            self.engine.setProperty("rate", 165)
            self.q = queue.Queue()
            threading.Thread(target=self._worker, daemon=True).start()
            self.ok = True
        except Exception as e:
            print(f"[Voice] TTS unavailable ({e}). Replies print on screen only.")

    def _worker(self):
        while True:
            text = self.q.get()
            try:
                self.engine.say(text)
                self.engine.runAndWait()
            except Exception:
                pass
            self.q.task_done()

    def say(self, text):
        print(f"Alya: {text}")
        if self.ok and text:
            self.q.put(text)
