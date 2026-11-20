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
  "pdfPages": "77-86",
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
| `pdfPages` | yes | Every page of the section in the manual PDF, such as "77-86". |
| `placeholder` | no | `true` makes the app warn that the content is a sample. |
| `subsections` | no | The subsections, in manual order. |
| `exam` | no | Questions about the whole section, not one subsection. |

The six tracks: `general_knowledge`, `air_brakes`, `combination`, `inspection`,
`basic_control`, `on_road`.

Only the eight sections Utah requires for a Class A are loaded: 1, 2, 3, 5, 6,
11, 12 and 13. A file for any other section is reported and left out.

## A subsection

```json
{
  "id": "5.1",
  "title": "The Parts of an Air Brake System",
  "manualPages": "5-1 to 5-3",
  "pdfPages": "77-79",
  "titleFixed": true,
  "facts": [ ... ],
  "cards": [ ... ],
  "exam": [ ... ]
}
```

| Field | Needed | What it is |
|---|---|---|
| `id` | yes | The manual's own numbering, such as `5.1`. |
| `title` | yes | The heading from the manual. |
| `pdfPages` | yes | The PDF pages to show on the Read step. |
| `manualPages` | no | The page labels the manual prints, such as "5-1 to 5-3". |
| `titleFixed` | no | `true` keeps a title you corrected by hand when the tool runs again. |

**Two sets of page numbers, and they differ.** `pdfPages` counts pages as the
PDF counts them, from 1, and the app needs it to show the page. `manualPages`
holds the labels the manual prints, such as 5-3, which start again in every
section. Page 5-1 of the manual is PDF page 77.

An `id` that ends in `.0`, such as `1.0`, is the section opening: the pages
before the first numbered heading. `tools/manual_map.py` makes it when those
pages exist, because they are reading too.

## A lesson

`reading` holds the lesson you read in the app. It is written by hand, in plain
words, and it is **never the manual's own text**: the handbook is AAMVA material
that only state license agencies may reproduce, and this repository is public.
Keep every number, limit and procedure exactly as the manual gives it.

```json
"reading": [
  { "h": "What it does" },
  { "p": "The governor decides when the compressor pumps air." },
  { "ul": ["Cut-out stops the pumping.", "Cut-in starts it again."] },
  { "numbers": [
    { "value": "125 psi", "what": "cut-out" },
    { "value": "100 psi", "what": "cut-in" }
  ] },
  { "note": "Learn both numbers." }
]
```

| Block | What it is for |
|---|---|
| `h` | A heading inside the lesson. |
| `p` | One short paragraph. Keep it under 600 characters; the build fails above that. |
| `ul` | A list, one point per line. |
| `numbers` | The numbers to memorize, each with what it is for. Drawn as a table. |
| `note` | A rule, a limit or a warning, drawn in a colored card. |

An unknown block is left out rather than shown wrongly.

Write short. Short blocks and short sentences read well on a phone, and they
suit the focus text, which bolds the first letters of each word.

Leave `reading` out and the app reads the fact list instead, and says so. That is
a usable lesson, not a broken screen, so facts first and prose later is fine.

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
- a file named `*sample*` does not set `placeholder`, or any other file does;
- a file holds a section outside the Class A plan, or one of the eight is missing;
- a section or a subsection has no `pdfPages`;
- a subsection's pages fall outside its section's pages;
- the subsections of a section are out of manual order.

## Making the files

`tools/manual_map.py` writes these files from the manual PDF. It records
headings and page numbers only, and copies no manual text, because the handbook
is AAMVA material that only state license agencies may reproduce.

```
python3 tools/manual_map.py /path/to/CDL-Manual.pdf
```

It needs `pdftotext` and `pdfinfo` (poppler-utils). A long heading wraps in the
two-column layout, so a few titles come out cut short. Correct those by hand and
add `"titleFixed": true`, and the next run keeps your version.
