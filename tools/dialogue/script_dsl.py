"""A small notation for writing branching dialogue, turned into the app's JSON.

    M("...")              the mentor this file belongs to
    Y("...")              you
    N("...")              the narrator
    S("GOKU", "...")      anyone else, by name
    C(("reply", [...]),   a reply for you to pick, each with what follows it
      ("reply", [...]))
    K("streak",           lines picked by one of your numbers: the first branch
      (7, [...]),         whose minimum it reaches (give a 0 branch last)
      (0, [...]))

Numbers for K and {placeholders}: streak, power, week_checkoffs, week_missed,
journal_streak, bond_level, habits, days_using. Words: top_habit, weak_habit.
"""

_MENTOR = None


def set_mentor(name):
    global _MENTOR
    _MENTOR = name


def M(text):
    return {"speaker": _MENTOR, "text": text}


def Y(text):
    return {"speaker": "YOU", "text": text}


def N(text):
    return {"speaker": "NARRATOR", "text": text}


def S(speaker, text):
    return {"speaker": speaker, "text": text}


def C(*options):
    assert len(options) >= 2, "a choice needs two or more options"
    return {"choice": [{"text": text, "then": list(then)} for text, then in options]}


def K(stat, *branches):
    assert branches[-1][0] == 0, f"check on {stat} needs a 0 branch last"
    return {"check": stat, "branches": [{"min": m, "then": list(then)} for m, then in branches]}


def call(cid, kind, title, *lines):
    assert kind in ("wisdom", "about", "subject", "checkin", "gossip"), kind
    return {"id": cid, "kind": kind, "title": title, "lines": list(lines)}


# Shorthand for scenes with several mentors.
def R(text):
    return S("ROSHI", text)


def G(text):
    return S("GOKU", text)


def P(text):
    return S("PICCOLO", text)


def B(text):
    return S("BULMA", text)


def V(text):
    return S("VEGETA", text)


def chapter(number, title, *lines):
    return {"number": number, "title": title, "lines": list(lines)}


def scene(level, title, *lines):
    return {"level": level, "title": title, "lines": list(lines)}
