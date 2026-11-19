#!/usr/bin/env python3
"""Builds app/src/main/assets/dialogue/calls_<mentor>.json from tools/dialogue/calls_<mentor>.py.

    python3 tools/build_calls.py

Each source file sets its mentor, then defines RING (notification lines) and
CALLS (a list made with call(...)). See tools/dialogue/script_dsl.py.
"""
import importlib.util
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "tools" / "dialogue"
OUT = ROOT / "app" / "src" / "main" / "assets" / "dialogue"


def load(path):
    spec = importlib.util.spec_from_file_location(path.stem, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def main():
    import sys
    sys.path.insert(0, str(SRC))
    for path in sorted(SRC.glob("calls_*.py")):
        m = load(path)
        mentor = path.stem.split("_", 1)[1].upper()
        book = {"mentor": mentor, "ring": m.RING, "calls": m.CALLS}
        ids = [c["id"] for c in m.CALLS]
        assert len(ids) == len(set(ids)), f"{path.name}: duplicate ids"
        (OUT / f"calls_{mentor.lower()}.json").write_text(json.dumps(book, indent=1, ensure_ascii=False) + "\n")
        kinds = {}
        for c in m.CALLS:
            kinds[c["kind"]] = kinds.get(c["kind"], 0) + 1
        print(f"{mentor:8s} {len(m.CALLS)} calls {kinds}")


if __name__ == "__main__":
    main()
