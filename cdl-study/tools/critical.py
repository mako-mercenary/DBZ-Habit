"""
Which facts are marked "must memorize".

THE RULE
A fact is critical only if it is one of these:
  1. a number: a weight, pressure, distance, time, percentage, age, fee or count
     you must be able to state;
  2. an exact limit or threshold: an out-of-service condition, a pass or fail
     boundary, a legal cut-off;
  3. a named definition: a term the manual names and the test can ask you to
     define (off-tracking, encroachment, black ice, rearward amplification,
     glad hands, bobtail, stab braking);
  4. a step in an ordered procedure, where the order or completeness is scored:
     the seven-step inspection, the air brake check, coupling, uncoupling,
     shifting, a skid correction;
  5. an enumerated list you must reproduce: the four causes of a skid, the
     three steps at an accident scene, the six endorsements.

A fact is NOT critical if it is a behavior you learn once and then simply do
(keep both hands on the wheel, keep the doors shut on a cargo fire, signal
early), the reason behind a rule, an example, background, a statistic quoted
for effect, or a restatement of another fact.

A non-critical fact is still a real fact. It appears in the lesson, it counts
in the overall recall score, and it can be an exam question. It just does not
drag the must-memorize score, and it does not force a card when you got it
right.

WHY IT MATTERS
The flag drives two things: the must-memorize score, which your study plan
treats as the number that decides mastery, and the card queue, where every
critical fact makes a card even when you recalled it. Flag everything and both
stop working: the score becomes the overall score again, and the deck becomes
the whole manual.

Each section gives CRITICAL (the ids to flag) or NOT (the ids to leave), never
both. Sections 1 and 11 use NOT because nearly every fact in them is a legal
threshold or a scored inspection item.
"""

SECTIONS = {
    "section01": {"not": """
        1.1.1.1 1.1.1.5 1.1.2.3 1.1.2.4 1.1.2.6 1.1.2.7 1.1.3.3 1.2.2.2
        1.2.4.7 1.2.7.4 1.3.1.1 1.3.5.5 1.4.10 1.4.11 1.4.12 1.5.2 1.5.3
    """},
    "section11": {"not": """
        11.0.2 11.0.3 11.0.7 11.0.8 11.0.9 11.0.10 11.0.11
    """},
    "section02": {"critical": """
        2.1.2.2
        2.1.3.1 2.1.3.2 2.1.3.3 2.1.3.4 2.1.3.6 2.1.3.7 2.1.3.8 2.1.3.10 2.1.3.11
        2.1.4.1
        2.1.5.2 2.1.5.3 2.1.5.4 2.1.5.5 2.1.5.6 2.1.5.7 2.1.5.8 2.1.5.9
        2.1.5.10 2.1.5.11 2.1.5.12 2.1.5.13 2.1.5.14
        2.1.6.2 2.1.7.2
        2.2.4.2
        2.3.1.1 2.3.1.2 2.3.1.3 2.3.1.4 2.3.1.5 2.3.1.6 2.3.4.1 2.3.4.2
        2.4.1.1 2.4.2.4 2.4.2.6
        2.5.1.1 2.5.2.4 2.5.2.5 2.5.2.6 2.5.2.7
        2.6.1.1 2.6.1.2 2.6.1.3 2.6.1.4 2.6.1.5 2.6.1.6 2.6.1.7 2.6.1.8 2.6.1.9
        2.6.2.1 2.6.2.2 2.6.2.3 2.6.2.5 2.6.2.7 2.6.2.10 2.6.2.11
        2.6.3.2 2.6.5.2
        2.7.1.2 2.7.1.3 2.7.1.4 2.7.4.2
        2.7.6.2 2.7.6.3 2.7.6.5 2.7.6.6
        2.8.1.1 2.8.1.2 2.8.2.1 2.8.3.11 2.8.3.14
        2.9.1.1 2.9.1.3 2.9.3.1 2.9.3.2
        2.9.4.1 2.9.4.2 2.9.4.3 2.9.4.5
        2.9.5.1 2.9.5.2 2.9.5.3 2.9.5.4
        2.10.1.1 2.10.1.2 2.10.3.3
        2.11.2.3 2.11.2.6 2.11.2.7 2.11.2.8 2.11.2.9 2.11.2.10 2.11.2.11
        2.11.4.1 2.11.4.3 2.11.5.1 2.11.5.2
        2.13.2.4 2.13.2.8
        2.14.1.1 2.14.1.2 2.14.1.7
        2.15.1.1 2.15.1.2
        2.15.2.1 2.15.2.2 2.15.2.3 2.15.2.4 2.15.2.5 2.15.2.6
        2.15.5.3 2.15.5.4 2.15.6.1 2.15.6.2
        2.16.0.3 2.16.2.3 2.16.4.1 2.16.4.2
        2.17.1.6 2.17.1.7 2.17.1.8 2.17.1.9 2.17.1.11 2.17.1.12
        2.17.2.1 2.17.2.2 2.17.2.3 2.17.3.2 2.17.4.2 2.17.4.5
        2.18.2.1 2.18.2.2 2.18.2.3 2.18.3.1 2.18.3.2 2.18.6.2 2.18.8.2 2.18.8.3
        2.19.0.1 2.19.1.2 2.19.2.1 2.19.2.2 2.19.3.1 2.19.3.3
        2.20.0.1
        2.21.1.1 2.21.1.2 2.21.1.3 2.21.1.4 2.21.1.5 2.21.3.6 2.21.3.7
        2.22.1.3 2.22.1.4 2.22.1.5 2.22.1.6 2.22.2.3
        2.23.1.1 2.23.2.1 2.23.2.4
        2.23.3.1 2.23.3.2 2.23.3.3 2.23.3.4 2.23.3.5
    """},
    "section03": {"critical": """
        3.0.1 3.0.2 3.0.3 3.0.4 3.0.6
        3.1.1 3.1.2 3.1.3
        3.2.1.1 3.2.1.2 3.2.1.3 3.2.1.4 3.2.2.1
        3.3.1.1 3.3.1.2 3.3.2.1 3.3.2.2 3.3.2.3 3.3.3.1 3.3.4.1
    """},
    "section05": {"critical": """
        5.1.a
        5.1.2.1 5.1.2.2 5.1.4.2 5.1.5.1 5.1.6.1 5.1.8.1
        5.1.11.1 5.1.11.3 5.1.11.4
        5.1.13.1 5.1.13.2 5.1.13.3 5.1.14.3 5.1.15.1 5.1.15.3
        5.1.16.1 5.1.16.2 5.1.16.4 5.1.16.5
        5.2.1 5.2.2 5.2.3 5.2.4
        5.3.2.1 5.3.2.2 5.3.2.4 5.3.2.5
        5.3.3.1 5.3.3.2 5.3.3.3 5.3.3.4 5.3.3.5 5.3.3.6 5.3.3.7 5.3.3.8
        5.3.3.9 5.3.3.10
        5.4.2.6 5.4.3.1 5.4.3.2 5.4.3.3 5.4.3.4
        5.4.4.1 5.4.4.2 5.4.4.3 5.4.4.4
        5.4.6.2 5.4.7.2 5.4.8.4
    """},
    "section06": {"critical": """
        6.1.1.3 6.1.2.1 6.1.2.2 6.1.2.3 6.1.2.4 6.1.3.1 6.1.3.3
        6.1.4.1 6.1.4.2 6.1.4.4 6.1.5.1 6.1.5.3 6.1.5.4
        6.1.6.1 6.1.6.4 6.1.7.1 6.1.7.6
        6.2.1.1 6.2.1.2 6.2.1.4 6.2.2.1 6.2.2.3
        6.2.3.1 6.2.3.2 6.2.3.3
        6.2.4.1 6.2.4.2 6.2.4.4 6.2.4.6
        6.2.5.1 6.2.5.3 6.2.5.4 6.2.5.5 6.2.5.7
        6.2.6.3 6.2.7.1 6.2.7.2 6.2.8.1 6.2.8.2 6.2.8.3
        6.3.1.1 6.3.1.2
        6.4.1.1 6.4.1.3 6.4.1.5 6.4.1.6 6.4.1.7 6.4.1.8 6.4.1.9 6.4.1.10
        6.4.1.11 6.4.1.12 6.4.1.13 6.4.1.14
        6.4.2.1 6.4.2.2 6.4.2.3 6.4.2.4 6.4.2.5 6.4.2.6 6.4.2.7
        6.4.3.2 6.4.4.1 6.4.5.1 6.4.5.2 6.4.7.1 6.4.8.1
        6.5.1.1 6.5.1.2 6.5.1.3 6.5.1.4 6.5.1.5 6.5.1.6 6.5.1.7
        6.5.2.1 6.5.2.2 6.5.2.3 6.5.2.4 6.5.2.5 6.5.2.6 6.5.2.7
    """},
    "section12": {"critical": """
        12.0.1 12.0.3 12.0.4
        12.1.1 12.1.3 12.1.5 12.1.8
        12.2.1.1 12.2.1.2 12.2.2.1 12.2.2.2
        12.2.3.1 12.2.3.2 12.2.4.1 12.2.4.4
    """},
    "section13": {"critical": """
        13.1.1.3 13.1.1.5 13.1.6.3 13.1.6.4
        13.1.8.1 13.1.8.2 13.1.8.3 13.1.8.4 13.1.8.5
        13.1.9.1 13.1.11.1 13.1.14.2
    """},
}


def ids(text):
    return set(text.split())
