# Content format

One JSON file per manual section, in `app/src/main/assets/content`. The file
name does not matter. The `section` number inside it sets the order.

The **fact** is the centre of the format. One fact does three jobs:

- it is a checklist item you grade your recall against;
- it is the source of a flashcard;
- it is the tag on an exam question, which is how the app finds a weak area.

## A section file

```json
{
  "section": 5,
  "title": "Air Brakes",
  "track": "air_brakes",
  "readingPages": 10,
  "placeholder": false,
  "subsections": [ ... ],
  "exam": [ ... ]
}
```

| Field | Needed | What it is |
|---|---|---|
| `section` | yes | The manual section number. |
| `title` | yes | The section name from the manual. |
| `track` | yes | One of the six exam tracks. The list is below. |
| `readingPages` | no | Pages to read, for the home screen. |
| `placeholder` | no | `true` makes the app warn that the content is a sample. |
| `subsections` | no | The subsections, in manual order. |
| `exam` | no | Questions about the whole section, not one subsection. |

The six tracks: `general_knowledge`, `air_brakes`, `combination`, `inspection`,
`basic_control`, `on_road`.

## A subsection

```json
{
  "id": "5.1",
  "title": "The Parts of an Air Brake System",
  "manualPages": "5-1 to 5-3",
  "facts": [ ... ],
  "cards": [ ... ],
  "exam": [ ... ]
}
```

Use the manual's own numbering for `id`, such as `5.1`. The app shows
`manualPages` on the Read step, so write it the way the manual labels its pages.

## A fact

```json
{
  "id": "5.1.3",
  "text": "The governor stops the compressor from pumping air at about 125 psi.",
  "critical": true,
  "page": "5-2",
  "keywords": ["governor", "125"]
}
```

| Field | Needed | What it is |
|---|---|---|
| `text` | yes | One fact, in a full sentence. Keep it to one fact. |
| `id` | no | A stable id. Without one, the app numbers it by position. |
| `critical` | no | `true` for a must-memorize rule, number or procedure. |
| `page` | no | The manual page, for a correction you want to check. |
| `keywords` | no | Words that show the fact is in your recall. |

Rules that keep the content useful:

- **Write a stable `id`.** Your score history points at it. If an id moves to
  another fact, your history becomes wrong.
- **One fact per record.** Two facts in one sentence cannot be marked.
- **Mark the numbers critical.** A number, a limit and a procedure step are
  must-memorize facts. The score you trust is the must-memorize score.
- **Keywords are a number or a short phrase.** Write a number with no comma:
  `26001`, not `26,001`. The app takes the comma out of your recall, so both
  forms match. Matching is on whole words, so `air` does not match `airbrakes`.
- **Every subsection needs one critical fact.** The build fails without one.

## A card

```json
{ "q": "At what pressure does the governor cut out?", "a": "About 125 psi.", "factId": "5.1.3" }
```

One fact per card. A short, exact answer. Put the unit in the answer. A card
with no `factId` is always added after a recall, so use it only for material
that stands by itself.

## An exam question

```json
{
  "id": "5.1.e1",
  "q": "What does the governor do?",
  "choices": ["Stops the compressor pumping", "Opens the spring brakes", "Drains the tanks", "Warns of low air"],
  "answer": 0,
  "why": "The governor controls when the compressor pumps air.",
  "factId": "5.1.3",
  "page": "5-2"
}
```

`answer` is the position in `choices`, counted from 0. The build refuses a
question with fewer than two choices, or an `answer` outside the choices.

Write original questions from the manual. Do not write a purported copy of the
state test. `why` is shown after the exam, with the manual page.

## Checks on every build

`ContentFilesTest` fails the build if:

- a file will not parse;
- a fact id is used twice;
- a card or a question points at a fact that is not there;
- a subsection has facts but no must-memorize fact;
- a file named `*sample*` does not set `placeholder`, or any other file does.
