#!/usr/bin/env python3
"""Makes a placeholder song for Vegeta's rhythm game and adds it to the manifest.

Not needed once real songs are in; kept for testing the game without them.

Writes a 60-second synthesised drum-and-bass beat (120 BPM) to
app/src/main/assets/music/training_beat.wav, then makes sure manifest.json
lists it. Songs you add to the manifest are kept. Standard library only.

    python3 tools/placeholder_music.py
"""
import json
import math
import random
import struct
import wave
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MUSIC = ROOT / "app/src/main/assets/music"
RATE = 22050
BPM = 120
SECONDS = 60


def main():
    random.seed(7)
    n = RATE * SECONDS
    out = [0.0] * n
    beat = 60 / BPM

    def add(t, seconds, fn):
        start = int(t * RATE)
        for k in range(int(seconds * RATE)):
            if start + k < n:
                out[start + k] += fn(k / RATE)

    kick = lambda t: math.sin(2 * math.pi * (60 + 80 * math.exp(-t * 40)) * t) * math.exp(-t * 18) * 0.9
    snare = lambda t: (random.random() * 2 - 1) * math.exp(-t * 22) * 0.5 + math.sin(2 * math.pi * 190 * t) * math.exp(-t * 30) * 0.3
    hat = lambda t: (random.random() * 2 - 1) * math.exp(-t * 90) * 0.25
    bass_notes = [55, 55, 65.4, 49]
    lead_notes = [440, 523.3, 587.3, 659.3, 784]

    t = 0.5
    i = 0
    while t < SECONDS - 1:
        step = i % 8
        if step in (0, 4) or (step == 6 and i % 32 >= 16):
            add(t, 0.4, kick)
        if step in (2, 6):
            add(t, 0.3, snare)
        add(t, 0.08, hat)
        if step == 0:
            f = bass_notes[(i // 8) % 4]
            add(t, beat * 1.9, lambda x, f=f: math.sin(2 * math.pi * f * x) * 0.25 * min(1, x * 50) * math.exp(-x * 1.2))
        if i % 16 in (3, 11, 13) and i > 32:
            f = lead_notes[(i // 3) % len(lead_notes)]
            add(t, 0.18, lambda x, f=f: math.sin(2 * math.pi * f * x) * 0.22 * math.exp(-x * 12))
        t += beat / 2
        i += 1

    peak = max(abs(s) for s in out) or 1
    MUSIC.mkdir(parents=True, exist_ok=True)
    with wave.open(str(MUSIC / "training_beat.wav"), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(b"".join(struct.pack("<h", int(s / peak * 30000)) for s in out))

    manifest_path = MUSIC / "manifest.json"
    songs = json.loads(manifest_path.read_text()) if manifest_path.exists() else []
    if not any(s["file"] == "training_beat.wav" for s in songs):
        songs.insert(0, {"title": "Training beat", "file": "training_beat.wav"})
    manifest_path.write_text(json.dumps(songs, indent=2) + "\n")
    print("wrote training_beat.wav and manifest.json")


if __name__ == "__main__":
    main()
