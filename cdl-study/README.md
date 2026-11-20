# CDL Study

An offline Android app for the Utah Class A CDL with air brakes. You read the
manual in the app, close it, say what you remember, mark your recall against the
manual's facts, and export the cards to Anki. Then an exam for each track.

It covers the eight sections Utah requires for a Class A with air brakes and no
endorsements, and nothing else.

No accounts, no backend, no ads, no network. Your study record stays on the phone.

## What it does, and what it does not do

| Job | Where it is done |
|---|---|
| Read the subsection | In the app, on the pages of the manual you import. |
| Closed-book recall | In the app. No fact is shown until you send your recall. |
| Correction | In the app. You mark each manual fact against your own words. |
| Flashcards | The app makes them and exports TSV. **Anki** keeps the schedule. |
| Six exams | In the app. Each track is graded on its own. |
| Progress | In the app. It also copies a summary you can paste into any chat. |

The app does not replace Anki, and it does not replace practice behind the wheel.

## What you must study, and why

Three knowledge tests and a skills test in three parts:

| Section | Why it is required |
|---|---|
| 1 Introduction | General Knowledge test |
| 2 Driving Safely | General Knowledge test, and the largest section at 45 pages |
| 3 Transporting Cargo Safely | General Knowledge test |
| 5 Air Brakes | The Air Brakes test. Skip it and your licence bars air brakes. |
| 6 Combination Vehicles | The Combination Vehicles test, for Class A only |
| 11 Vehicle Inspection | Skills test, part one |
| 12 Basic Vehicle Control Skills Test | Skills test, part two |
| 13 On-Road Driving | Skills test, part three |

Sections 4, 7, 8, 9 and 10 are endorsement material (passenger, doubles and
triples, tank, hazmat, school bus). The app leaves them out and says so on the
"What you must study" screen. The manual's own Figure 1.2 gives the same list
for Class A. The app will not load a section outside this plan.

**Check this against the state.** The list comes from the 2025 Utah handbook and
the Utah Driver License Division testing page, read in November 2026. The
Division is the authority, not this app.

## Your copy of the manual

The app ships **no manual text and no manual pages**. The handbook is AAMVA
material, and the copyright page gives permission to reproduce it to state
driver licence agencies only. So:

- you download the handbook from the Utah Driver License Division (dld.utah.gov);
- you import that PDF once, on the "Your manual" screen;
- the app keeps it in its own private folder and shows you the right pages;
- nothing is uploaded, and nothing is shared.

`tools/manual_map.py` records only the headings and the page numbers from the
PDF. It copies no manual text into the repository.

## The study loop

1. **Read.** The app opens your manual at that subsection's pages, inside the
   app. Pinch is not needed: a button holds the page at the screen width or at
   2x. Then you press "Close the book", and the pages are gone.
2. **Recall.** You write everything you remember. The screen holds no facts and
   no manual.
3. **Correct.** The app shows every fact for that subsection. You mark each one
   *said it*, *part of it*, or *missed it*. The app suggests a mark from the
   keywords in your text, but a keyword in a wrong sentence is still wrong, so
   you confirm every mark.
4. **Score.** A *said it* counts 1 and a *part of it* counts 0.5. You get two
   numbers: all facts, and the must-memorize facts alone. The second number is
   the one that matters. 80% is the pass level and 90% is mastery.
5. **Cards.** The app queues a card for each fact you did not say, and a card
   for every must-memorize fact. The same question is never queued twice.
6. **Export.** The Cards screen gives you the TSV block to copy, or a `.tsv`
   file to save. The format is one Basic card per line: question, tab, answer,
   no header row.

## Exams

Six tracks, each graded on its own, as the study plan requires. Vehicle
Inspection is never mixed with Basic Vehicle Control or On-Road Driving.

- No answer is shown until the exam is finished.
- A question about a fact you have missed before is twice as likely to appear.
- A missed question becomes a card.
- The pass level is 80% and mastery is 90%.

**Check the official numbers.** The default exam lengths (50 General Knowledge,
25 Air Brakes, 20 Combination Vehicles) come from the study plan as common
practice targets. They are not verified against the Utah Driver License
Division. The questions are original, written from the manual. They are not
copies of the confidential state test.

## Content

The manual is not in this repository, and the app holds no manual text. The app
reads section files from `app/src/main/assets/content`. Each file turns one
manual section into facts, cards and exam questions. `docs/CONTENT_FORMAT.md`
gives the format.

The content is built by `tools/manual_map.py`, which maps the manual PDF to the
eight required sections: 249 subsections with their titles and page ranges.

```
python3 tools/manual_map.py ~/Downloads/CDL-Manual.pdf
python3 tools/manual_map.py ~/Downloads/CDL-Manual.pdf --sections 5,6
```

Run it again for a new edition of the manual: the facts, cards and questions you
have written are kept, and only the titles and page numbers are refreshed.

**Fact checklists are written by hand**, section by section, because the facts
must be accurate and in plain words.

| Section | Subsections | With a checklist | Facts | Cards | Questions |
|---|---|---|---|---|---|
| 1 Introduction | 23 | 20 | 101 | 77 | 21 |
| 2 Driving Safely | 116 | 0 | 0 | 0 | 0 |
| 3 Transporting Cargo Safely | 18 | 15 | 44 | 31 | 13 |
| 5 Air Brakes | 31 | 30 | 105 | 80 | 27 |
| 6 Combination Vehicles | 32 | 0 | 0 | 0 | 0 |
| 11 Vehicle Inspection | 14 | 0 | 0 | 0 | 0 |
| 12 Basic Vehicle Control | 2 | 0 | 0 | 0 | 0 |
| 13 On-Road Driving | 15 | 0 | 0 | 0 | 0 |
| **Total** | **251** | **65** | **250** | **188** | **61** |

A subsection with no checklist can still be read in the app, and it says plainly
that a recall cannot be graded there yet. A parent heading with no text of its
own, such as 5.4, needs no checklist.

The Air Brakes exam can run at its full 25 questions now.

`ContentFilesTest` reads every content file on each build. A file that will not
parse, a repeated fact id, a card that points at a fact that is not there, or a
subsection with no must-memorize fact all fail the build. Bad content must be
visible, because a wrong fact in a study app is worse than a missing one.

## Build

Needs a JDK (17 or newer) and the Android SDK (platform 37, build-tools 37.0.0).
Point the build at the SDK with `ANDROID_HOME`, or with a `local.properties`
file that holds `sdk.dir=/path/to/sdk`.

```
./gradlew assembleDebug       # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest   # unit tests
```

The build is signed with the debug key in `app/debug.keystore`, so a newer APK
installs over an older one and keeps your study data.

## Install on a phone

1. Copy `app-debug.apk` to the phone.
2. Open it from the Files app. Android asks once to allow installs from that
   app. Allow it, then tap Install.
3. Install a later version the same way. It updates in place.

With a USB cable and developer mode on:
`adb install -r app/build/outputs/apk/debug/app-debug.apk`.

## Still to do

- Fact checklists for sections 2, 6, 11, 12 and 13. Section 2 is the large job,
  at 45 pages and 116 subsections.
- AI grading of a recall, on demand, with your own API key.
- A daily reminder, and a streak for days with a recall.
- Export and import of the study record.
