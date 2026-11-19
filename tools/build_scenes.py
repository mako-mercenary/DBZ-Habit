#!/usr/bin/env python3
"""Builds the story chapters and bond scenes from tools/dialogue/.

    python3 tools/build_scenes.py

story.py (CHAPTERS) becomes assets/dialogue/story.json; scenes_<mentor>.py
(SCENES) replaces the "scenes" list in assets/dialogue/<mentor>.json, leaving
that file's other lines alone. See tools/dialogue/script_dsl.py.
"""
import importlib.util
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "tools" / "dialogue"
OUT = ROOT / "app" / "src" / "main" / "assets" / "dialogue"


def load(path):
    spec = importlib.util.spec_from_file_location(path.stem, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def count_lines(nodes):
    """Lines on the longest and shortest path."""
    lo = hi = 0
    for n in nodes:
        if "speaker" in n:
            lo += 1
            hi += 1
        elif "choice" in n:
            sub = [count_lines(o["then"]) for o in n["choice"]]
            lo += 1 + min(s[0] for s in sub)
            hi += 1 + max(s[1] for s in sub)
    return lo, hi


def main():
    sys.path.insert(0, str(SRC))
    story = SRC / "story.py"
    if story.exists():
        chapters = load(story).CHAPTERS
        (OUT / "story.json").write_text(json.dumps({"chapters": chapters}, indent=1, ensure_ascii=False) + "\n")
        for c in chapters:
            print(f"chapter {c['number']} {c['title']!r}: {count_lines(c['lines'])} lines")
    for path in sorted(SRC.glob("scenes_*.py")):
        mentor = path.stem.split("_", 1)[1]
        scenes = load(path).SCENES
        target = OUT / f"{mentor}.json"
        data = json.loads(target.read_text())
        data["scenes"] = scenes
        target.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n")
        print(mentor, [count_lines(s["lines"]) for s in scenes])


if __name__ == "__main__":
    main()
