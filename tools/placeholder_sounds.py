#!/usr/bin/env python3
"""Makes the placeholder sound effects and the sound list.

Writes a short synthesised WAV for every sound that has no file yet in
app/src/main/assets/sound/, then rewrites manifest.json (key -> file) and
SOUND_LIST.md. Your own sounds are never touched unless you pass --force.
Uses only the Python standard library.

    python3 tools/placeholder_sounds.py [--force]
"""
import json
import math
import random
import struct
import sys
import wave
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SOUND_DIR = ROOT / "app/src/main/assets/sound"
RATE = 22050

# key, label, when it plays, suggested length in seconds
SOUNDS = [
    ("check_off", "Check-off", "A habit is checked off.", 0.5),
    ("boss_hit", "Boss hit", "A saga milestone lands on the boss.", 0.8),
    ("victory", "Victory", "A boss is defeated.", 2.0),
    ("transformation", "Transformation", "A new form unlocks.", 2.0),
    ("bomb_throw", "Spirit bomb throw", "A full spirit bomb is thrown.", 1.5),
    ("level_up", "Level up", "A bond level or an achievement is gained.", 1.0),
    ("dragon_summon", "Dragon summon", "The dragon is summoned.", 2.5),
    ("card_place", "Card placed", "A card lands on the card battle board.", 0.25),
    ("card_flip", "Card captured", "A card flips to its new owner.", 0.3),
    ("card_combo", "Same, Plus or Combo", "A special capture rule triggers.", 0.8),
    ("card_hover", "Opponent choosing", "The challenger considers a card in their hand.", 0.08),
    ("card_win", "Match won", "You win a card battle.", 1.2),
    ("card_lose", "Match lost", "You lose a card battle.", 1.0),
    ("pack_open", "Pack opened", "A card pack is torn open.", 0.6),
    ("card_reveal_rare", "Rare reveal", "An epic, legendary or bond card is revealed.", 0.9),
]


def envelope(i, n, attack=0.01, release=0.2):
    t = i / RATE
    total = n / RATE
    a = min(1.0, t / attack) if attack > 0 else 1.0
    r = min(1.0, (total - t) / release) if release > 0 else 1.0
    return max(0.0, min(a, r))


def tone(freqs, seconds, volume=0.5, attack=0.01, release=0.15, wobble=0.0):
    """Notes one after another; each entry of freqs is (frequency, share of the length)."""
    out = []
    for freq, share in freqs:
        n = int(RATE * seconds * share)
        for i in range(n):
            f = freq * (1 + wobble * math.sin(2 * math.pi * 6 * i / RATE))
            s = math.sin(2 * math.pi * f * i / RATE) + 0.3 * math.sin(4 * math.pi * f * i / RATE)
            out.append(volume * envelope(i, n, attack, release) * s / 1.3)
    return out


def sweep(f0, f1, seconds, volume=0.5, noise=0.0, release=0.3):
    out = []
    n = int(RATE * seconds)
    phase = 0.0
    for i in range(n):
        f = f0 + (f1 - f0) * (i / n) ** 1.5
        phase += 2 * math.pi * f / RATE
        s = math.sin(phase) + noise * (random.random() * 2 - 1)
        out.append(volume * envelope(i, n, 0.05, release) * s / (1 + noise))
    return out


def burst(seconds, volume=0.6, low=60.0):
    out = []
    n = int(RATE * seconds)
    for i in range(n):
        decay = math.exp(-i / (RATE * seconds / 5))
        s = 0.6 * (random.random() * 2 - 1) + math.sin(2 * math.pi * low * i / RATE)
        out.append(volume * decay * s / 1.6)
    return out


def mix(*tracks):
    n = max(len(t) for t in tracks)
    return [sum(t[i] for t in tracks if i < len(t)) / len(tracks) * 1.4 for i in range(n)]


def make(key):
    random.seed(key)
    if key == "check_off":
        return tone([(880, 0.4), (1318.5, 0.6)], 0.28, release=0.1)
    if key == "boss_hit":
        return mix(burst(0.35, low=55), tone([(110, 1.0)], 0.35, volume=0.7, release=0.3))
    if key == "victory":
        return tone([(523.3, 0.18), (659.3, 0.18), (784.0, 0.18), (1046.5, 0.46)], 1.1, release=0.35)
    if key == "transformation":
        return mix(sweep(120, 900, 1.6, volume=0.6, noise=0.35), tone([(220, 1.0)], 1.6, volume=0.3, wobble=0.04))
    if key == "bomb_throw":
        return sweep(700, 90, 1.1, volume=0.6, noise=0.9, release=0.5)
    if key == "level_up":
        return tone([(659.3, 0.25), (880, 0.25), (1174.7, 0.5)], 0.6, release=0.25)
    if key == "card_place":
        return mix(burst(0.12, volume=0.7, low=90), tone([(196, 1.0)], 0.14, volume=0.6, release=0.1))
    if key == "card_flip":
        return mix(sweep(300, 1400, 0.18, volume=0.5, noise=0.6, release=0.1), tone([(784, 1.0)], 0.2, volume=0.3, release=0.15))
    if key == "card_combo":
        return tone([(587.3, 0.2), (784.0, 0.2), (1174.7, 0.6)], 0.55, volume=0.6, release=0.3)
    if key == "card_hover":
        return tone([(1046.5, 1.0)], 0.05, volume=0.25, attack=0.002, release=0.04)
    if key == "card_win":
        return tone([(392.0, 0.15), (523.3, 0.15), (659.3, 0.15), (784.0, 0.15), (1046.5, 0.4)], 1.0, release=0.35)
    if key == "card_lose":
        return tone([(392.0, 0.3), (349.2, 0.3), (293.7, 0.4)], 0.9, volume=0.45, release=0.35, wobble=0.02)
    if key == "pack_open":
        return sweep(200, 2400, 0.45, volume=0.5, noise=0.85, release=0.25)
    if key == "card_reveal_rare":
        return mix(tone([(1318.5, 0.3), (1568.0, 0.3), (2093.0, 0.4)], 0.7, volume=0.5, release=0.3), sweep(800, 3000, 0.7, volume=0.25, noise=0.3, release=0.4))
    if key == "dragon_summon":
        return mix(burst(1.8, volume=0.5, low=40), sweep(80, 400, 1.8, volume=0.5, noise=0.1, release=0.6))
    raise ValueError(key)


def write_wav(path, samples):
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(b"".join(struct.pack("<h", int(max(-1, min(1, s)) * 32000)) for s in samples))


def main():
    force = "--force" in sys.argv
    SOUND_DIR.mkdir(parents=True, exist_ok=True)
    manifest_path = SOUND_DIR / "manifest.json"
    manifest = json.loads(manifest_path.read_text()) if manifest_path.exists() else {}
    for key, *_ in SOUNDS:
        current = manifest.get(key)
        if current and (SOUND_DIR / current).exists() and not force:
            continue
        name = f"{key}.wav"
        write_wav(SOUND_DIR / name, make(key))
        manifest[key] = name
        print("made", name)
    manifest = {key: manifest[key] for key, *_ in SOUNDS}
    manifest_path.write_text(json.dumps(manifest, indent=2) + "\n")

    lines = [
        "# Sound list",
        "",
        "Every sound effect the app plays. Each key maps to a file in",
        "`app/src/main/assets/sound/` through `manifest.json` there.",
        "",
        "To use your own: put the file in that folder (WAV, MP3, OGG or M4A),",
        "point its key at it in `manifest.json`, and rebuild. Keep effects short;",
        "the suggested lengths are a guide, not a limit. The placeholders are",
        "synthesised by `tools/placeholder_sounds.py`, which never overwrites",
        "your files unless run with `--force`.",
        "",
        "| Key | Sound | Plays when | Suggested length | File |",
        "|---|---|---|---|---|",
    ]
    for key, label, when, seconds in SOUNDS:
        lines.append(f"| `{key}` | {label} | {when} | up to {seconds:g} s | `{manifest[key]}` |")
    (ROOT / "SOUND_LIST.md").write_text("\n".join(lines) + "\n")
    print("wrote manifest.json and SOUND_LIST.md")


if __name__ == "__main__":
    main()
