#!/usr/bin/env python3
"""Builds app/src/main/assets/cards/cards.json: every card, its numbers, and the challengers.

    python3 tools/build_cards.py

Card numbers are generated from the card's rarity (and a hash of its name, so
they're stable between runs): each card's four sides add up to a budget for
its rarity, no side is above 10 (shown as A), and every card has one weak side.
Change a card by editing the lists below, or pin its numbers in FIXED.
"""
import hashlib
import json
import re
from pathlib import Path

OUT = Path(__file__).resolve().parent.parent / "app/src/main/assets/cards/cards.json"

# K = Ki, S = Strength, P = Spirit, T = Tech
PACK = {
    "legendary": """Super Saiyan Goku (Namek)|K
Super Saiyan 2 Gohan (Cell Games)|K
Gogeta (Super Saiyan)|K
Broly (Legendary Super Saiyan)|S
Perfect Cell|K
Kid Buu|S
Frieza (Final Form)|K
Shenron|P""",
    "epic": """Ultimate Gohan|K
Super Vegeta|S
Gotenks (Super Saiyan 3)|K
Super Buu|S
Majin Buu|S
Cooler (Final Form)|K
Janemba|P
Bojack|S
Android 17|T
Android 16|T
Semi-Perfect Cell|K
King Piccolo|P
Captain Ginyu|T
Baby Vegeta|T
Super 17|T
Omega Shenron|P
Porunga|P
Dabura|P
Turles|S
Lord Slug|S
Super Saiyan Future Trunks|K
Grandpa Gohan|P""",
    "rare": """Goku (Saiyan Saga)|K
Vegeta (Saiyan Saga)|S
Piccolo (Saiyan Saga)|P
Kid Gohan (Namek)|K
Teen Gohan|K
Super Saiyan Goten|K
Super Saiyan Kid Trunks|K
Raditz|S
Nappa|S
Zarbon (Monster Form)|S
Recoome|S
Burter|T
Jeice|K
Guldo|P
Frieza (Second Form)|S
Frieza (Third Form)|T
Mecha Frieza|T
King Cold|S
Android 19|T
Android 20 (Dr. Gero)|T
Cell Jr.|K
Babidi|P
Pui Pui|S
Yakon|S
Spopovich|S
Garlic Jr.|P
Zangya|K
Cooler (Base Form)|K
Pan|K
Uub|K
Majuub|K
Kibito|P
Supreme Kai|P
Kibito Kai|P
King Kai|P
Kami|P
Mr. Popo|P
Korin|P
Bardock|S
Fasha|S
King Vegeta|S
Nail|P
Dende|P
Grand Elder Guru|P
Hirudegarn|S""",
    "common": """Kid Goku|S
Turtle|P
Oolong|T
Puar|T
Launch|K
Chi-Chi|S
Ox-King|S
Bulma (Capsule Corp)|T
Dr. Brief|T
Mrs. Brief|P
Goten|K
Kid Trunks|K
Marron|P
Gohan (Saiyan Saga)|K
Saibaman|S
Appule|S
Cui|K
Dodoria|S
Zarbon|K
Frieza Soldier|T
Red Ribbon Soldier|T
General Blue|S
Mercenary Tao|S
Android 8|S
Commander Red|T
Pilaf|T
Mai|T
Shu|S
Arale|S
Giran|S
Nam|P
Bacterian|S
Tournament Announcer|P
King Furry|P
Bee (Mr. Satan's dog)|P
Upa|P
Bora|S
Suno|P
Colonel Silver|S
General White|T
Ninja Murasaki|T
Major Metallitron|T
Staff Officer Black|T
Tambourine|S
Cymbal|S
Drum|S
Fortuneteller Baba|P
Bubbles|P
Gregory|P
Princess Snake|P
Goz|S
Mez|S
King Yemma|P
Sharpner|S
Erasa|P
Bulla|K
Paragus|K
Salza|K
Doore|S
Neiz|T""",
}

# (challenger, difficulty, signature card, element, mentor or None, intro lines, win lines, lose lines)
# "win" is what they say when you beat them; "lose" when they beat you.
CHALLENGERS = [
    ("Yamcha", "easy", "Yamcha: Wolf Fang Fist", "S", None,
     ["Okay, cards. I'm great at cards. Probably.", "Don't go easy on me. Actually, a little easy is fine."],
     ["Aw, man. Again? Fine, take one. Not the good one. Okay, the good one."],
     ["I won? I WON! Somebody write this down!"]),
    ("Chiaotzu", "easy", "Chiaotzu: Telekinesis", "P", None,
     ["Tien said I should practise. Can we play?", "I'm not allowed to move the cards with my mind. I asked."],
     ["Good game! I'll tell Tien you're really strong."],
     ["Oh! I won! Sorry. Not sorry. A little sorry."]),
    ("Yajirobe", "easy", "Yajirobe: Senzu Courier", "S", None,
     ["I'll play, but if I win you're buying lunch.", "Quick game. Korin's waiting and I'm starving."],
     ["Tch. I let you win. Obviously. Go on, pick one."],
     ["Ha! Pay up. I'm thinking noodles."]),
    ("Videl", "easy", "Videl: Satan City Hero", "S", None,
     ["Let's keep it fair. No flying over the board.", "Gohan says you're good. Prove it."],
     ["Not bad. I want a rematch tomorrow."],
     ["That's how it's done. Don't sulk, it's not a good look."]),
    ("Mr. Satan", "medium", "Mr. Satan: The Champ", "S", None,
     ["The World Champion accepts your challenge! Ha ha ha!", "Cameras? Is anyone filming this? They should be."],
     ["I... was testing you! Yes! You passed! Take a card, my student!"],
     ["Victory! As always! Sign here for an autograph."]),
    ("Krillin", "medium", "Krillin: Destructo Disc", "K", None,
     ["Hey! Up for a game? I've been practising with 18. She always wins.", "Best of luck. You'll need it. Kidding! Mostly."],
     ["Ha, okay, you got me. Good game, really."],
     ["Whoa, I actually won! Don't tell Goku, he'll want a go."]),
    ("Gohan", "medium", "Great Saiyaman", "K", None,
     ["I worked out the probabilities for this. Let's see if they hold.", "Dad taught me cards. Mum taught me to count them."],
     ["Well played. I miscalculated the corners."],
     ["Sorry! I'll go easier next time. Probably not, actually."]),
    ("Tien", "hard", "Tien: Tri-Beam", "K", None,
     ["Discipline wins card games too. Sit.", "I don't lose focus. Let's see if you do."],
     ["You earned that. Take your card."],
     ["Your focus slipped in the middle. Train it."]),
    ("Android 18", "hard", "Android 18: Infinite Energy", "T", None,
     ["Make it quick. I'm meeting Krillin for dinner.", "I calculate every move. Do you?"],
     ["Hm. Not bad. Krillin will be thrilled someone beat me."],
     ["Predictable. Try harder next time."]),
    ("Future Trunks", "hard", "Future Trunks: Sword of Hope", "T", None,
     ["Where I'm from, we didn't have time for games. Let's make this count.", "Ready when you are."],
     ["Well fought. I'll remember that move."],
     ["Don't give up. That's the one thing I've learned."]),
    ("Master Roshi", "hard", "Master Roshi: Max Power", "K", "ROSHI",
     ["Ho ho! I've been playing cards since before your grandparents were born.", "Shuffle well. I'll know if you don't."],
     ["Ho ho! The student surpasses the master. At cards. Don't get cocky."],
     ["Experience, my young friend. Three hundred years of it."]),
    ("Goku", "very_hard", "Goku: Super Saiyan 2", "K", "GOKU",
     ["Cards! I love cards! Well, I love winning cards!", "No holding back, okay? I won't!"],
     ["Aw, you got me! That was awesome! Again!"],
     ["Ha! That was so fun! Wanna go again? Let's go again!"]),
    ("Piccolo", "very_hard", "Piccolo: Special Beam Cannon", "K", "PICCOLO",
     ["Sit. Focus. Play.", "Every move matters. Act like it."],
     ["...Well played. I won't say it twice."],
     ["You rushed. Again."]),
    ("Bulma", "hard", "Bulma: Capsule Corp President", "T", "BULMA",
     ["I designed a card-playing robot once. I beat it. Let's go.", "Fair warning: I'm a genius."],
     ["Huh. You actually beat me. I'm recalculating everything."],
     ["Told you. Genius. Take notes."]),
    ("Vegeta", "very_hard", "Vegeta: Final Flash", "K", "VEGETA",
     ["Hmph. Let's see if you're worthy of a Saiyan.", "I don't play games. I win them."],
     ["...Impossible. Again. Tomorrow."],
     ["As expected. A prince doesn't lose at anything."]),
]

# Bond reward cards: (mentor, bond level, name, element, rarity budget)
BOND = [
    ("ROSHI", 5, "Jackie Chun", "S"),
    ("GOKU", 5, "Goku (Frieza Saga)", "K"),
    ("PICCOLO", 5, "Piccolo (Cell Saga)", "P"),
    ("BULMA", 5, "Bulma (Dragon Ball)", "T"),
    ("VEGETA", 5, "Vegeta (Namek Saga)", "S"),
    ("ROSHI", 10, "Goku Super Saiyan 4", "K"),
    ("GOKU", 10, "Goku Super Saiyan 3", "K"),
    ("PICCOLO", 10, "Piccolo Fused with Kami", "P"),
    ("BULMA", 10, "Vegeta Super Saiyan 4", "S"),
    ("VEGETA", 10, "Majin Vegeta", "S"),
    ("ALL", 10, "Vegito", "K"),
]

# Total of the four sides, by rarity, as (lowest, highest).
BUDGET = {
    "common": (12, 16),
    "rare": (18, 21),
    "epic": (22, 25),
    "legendary": (27, 30),
    "signature_easy": (18, 20),
    "signature_medium": (21, 23),
    "signature_hard": (24, 26),
    "signature_very_hard": (28, 30),
    "bond5": (24, 26),
    "bond10": (30, 32),
    "vegito": (35, 35),
}

FIXED = {"Vegito": [10, 10, 9, 6]}


def slug(name):
    return re.sub(r"[^a-z0-9]+", "_", name.lower()).strip("_")


def rng(seed):
    h = hashlib.sha256(seed.encode()).digest()
    i = 0
    while True:
        yield h[i % len(h)] + 256 * (i // len(h))
        i += 1


def numbers(name, budget_key):
    if name in FIXED:
        return FIXED[name]
    lo, hi = BUDGET[budget_key]
    r = rng(name)
    total = lo + next(r) % (hi - lo + 1)
    # One weak side, the rest share the remainder as evenly as the dice allow.
    weak = max(1, min(4, total // 6 - 1 + next(r) % 2))
    rest = total - weak
    sides = [rest // 3] * 3
    for k in range(rest - sum(sides)):
        sides[k % 3] += 1
    # Shuffle a little strength between the strong sides.
    for _ in range(3):
        a, b = next(r) % 3, next(r) % 3
        if a != b and sides[a] > 2 and sides[b] < 10:
            sides[a] -= 1
            sides[b] += 1
    sides = [min(10, s) for s in sides]
    spill = total - weak - sum(sides)
    while spill > 0:
        k = min(range(3), key=lambda j: sides[j])
        sides[k] += 1
        spill -= 1
    all4 = sides + [weak]
    rot = next(r) % 4
    all4 = all4[rot:] + all4[:rot]
    return all4  # top, right, bottom, left


def main():
    cards = []
    for rarity, block in PACK.items():
        for line in block.splitlines():
            name, el = line.split("|")
            t, rgt, b, lft = numbers(name, rarity)
            cards.append({"id": slug(name), "name": name, "rarity": rarity, "element": el, "source": "pack",
                          "top": t, "right": rgt, "bottom": b, "left": lft})
    challengers = []
    for name, diff, sig, el, mentor, intro, win, lose in CHALLENGERS:
        t, rgt, b, lft = numbers(sig, "signature_" + diff)
        cards.append({"id": slug(sig), "name": sig, "rarity": "signature", "element": el, "source": "signature",
                      "top": t, "right": rgt, "bottom": b, "left": lft})
        challengers.append({"id": slug(name), "name": name, "difficulty": diff, "signature": slug(sig),
                            "mentor": mentor, "intro": intro, "win": win, "lose": lose})
    for mentor, level, name, el in BOND:
        key = "vegito" if mentor == "ALL" else f"bond{level}"
        t, rgt, b, lft = numbers(name, key)
        cards.append({"id": slug(name), "name": name, "rarity": "legendary" if level == 10 else "epic", "element": el,
                      "source": "bond", "mentor": mentor, "bondLevel": level,
                      "top": t, "right": rgt, "bottom": b, "left": lft})
    ids = [c["id"] for c in cards]
    assert len(ids) == len(set(ids)), "duplicate card ids"
    for c in cards:
        assert all(1 <= c[k] <= 10 for k in ("top", "right", "bottom", "left")), c
    OUT.write_text(json.dumps({"cards": cards, "challengers": challengers}, indent=1, ensure_ascii=False) + "\n")
    by = {}
    for c in cards:
        by[c["source"]] = by.get(c["source"], 0) + 1
    print(len(cards), "cards", by, len(challengers), "challengers")
    for c in cards[:3] + cards[-3:]:
        print(c["name"], c["top"], c["right"], c["bottom"], c["left"])


if __name__ == "__main__":
    main()
