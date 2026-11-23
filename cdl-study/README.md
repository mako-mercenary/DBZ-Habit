# Gateway 2 100k

An offline Android app for the Utah Class A CDL with air brakes. The name is
the point of it: the license is the gateway. You read the
manual in the app, close it, say what you remember, mark your recall against the
manual's facts, and export the cards to Anki. Then an exam for each track.

It covers the eight sections Utah requires for a Class A with air brakes and no
endorsements, and nothing else.

No accounts, no backend, no ads. Your study record stays on the phone. One
feature uses the network: AI grading, which is off until you put in your own
API key, and which only sends anything when you press its button.

## What it does, and what it does not do

| Job | Where it is done |
|---|---|
| Read the subsection | In the app, as a lesson written in plain words. |
| Closed-book recall | In the app. No fact is shown until you send your recall. |
| Correction | In the app. You mark each manual fact against your own words. |
| Flashcards | The app makes them and exports TSV. **Anki** keeps the schedule. |
| Six exams | In the app. Each track is graded on its own. |
| Progress | In the app. It also copies a summary you can paste into any chat. |
| Daily reminder | In the app, at a time you choose, with your streak. |
| A second opinion on a recall | Claude, with your own API key. Off by default. |

The app does not replace Anki, and it does not replace practice behind the wheel.

## What you must study, and why

Three knowledge tests and a skills test in three parts:

| Section | Why it is required |
|---|---|
| 1 Introduction | General Knowledge test |
| 2 Driving Safely | General Knowledge test, and the largest section at 45 pages |
| 3 Transporting Cargo Safely | General Knowledge test |
| 5 Air Brakes | The Air Brakes test. Skip it and your license bars air brakes. |
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

## Reading in the app

You read a lesson, not the handbook. Each lesson is short blocks: headings,
short paragraphs, lists, a table of the numbers to memorize, and a note for a
rule you must not miss. That reads far better on a phone than two columns of
small print.

**Focus text** puts the first letters of each word in bold, so the eye lands on
the start of the word. It is the bionic-style effect, and the reader can switch
it off or set it to light, medium or strong. Research is mixed on whether it
makes anybody read faster, so it is a preference, not a default truth.

A number is never half bold. "26,001" and "0.04" go bold whole, because in this
material the number is the fact. A bold run never ends on a hyphen either, so
"out-of-service" bolds "out-of".

The reader also has text size, airy line spacing, and a **Book pages** switch,
which shows the real pages of your own manual instead of the lesson. Use it to
check a correction against the book.

The controls sit at the top of every lesson, and on the **Reading** screen from
the home screen, which shows a sample that changes as you set it.
`docs/focus-text-preview.png` shows the three strengths beside focus switched
off. One setting serves the whole app, so a change shows at once in a lesson
that is already open.

## Where the lesson text comes from

The lessons are **written by hand for this app**, in plain words, with every
number, limit and procedure kept exactly as the manual gives it.

They are not the handbook's text, and the app ships none of it. The handbook is
AAMVA material, and its copyright page gives permission to reproduce it to state
driver license agencies only. This repository is public, so the manual's wording
belongs in neither the repo nor the APK.

For the real pages, you import your own copy:

- download the handbook from the Utah Driver License Division (dld.utah.gov);
- import that PDF once, on the "Your manual" screen;
- the app keeps it in its own private folder and shows you the right pages;
- nothing is uploaded, and nothing is shared.

`tools/manual_map.py` records only the headings and the page numbers from the
PDF. It copies no manual text into the repository.

## The study loop

1. **Read.** The app shows the lesson for that subsection, with focus text and
   your own text size. Then you press "Close the book", and the lesson is gone.
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

### The second opinion

The keyword suggestion has one blind spot it cannot fix: it cannot tell you
that a number was backwards. "Cut-in is 125" holds the keyword 125 and is
wrong.

So the correct step has one more button, which appears once you put an API key
in Settings. It sends the words you typed and this app's own fact list to
Claude, and asks for a mark on every fact, a reason for each, and a list of
anything you said that is wrong. The last part is the useful part: a checklist
cannot catch a wrong statement, and a person grading their own recall at 10 at
night will not catch it either.

It advises. Your own marks are what gets saved and what makes the cards. A
"Take its marks" button is there if you agree with it.

What is sent: your recall, and the fact text this repository wrote. What is not
sent: the manual (the app does not hold it), your name, your scores, your
history. Each request is billed to your own API account. The key is kept in the
app's private storage, where no other app can read it.

## The daily reminder and the streak

Settings has a daily reminder at a time you choose. It names the next thing to
do, so the notification is useful on its own. A day counts toward the streak
when you record a recall. Today never breaks the streak until it is over.

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

| Section | Subsections | Lessons | Facts | Cards | Questions |
|---|---|---|---|---|---|
| 1 Introduction | 23 | 21 | 101 | 77 | 21 |
| 2 Driving Safely | 116 | 116 | 427 | 234 | 90 |
| 3 Transporting Cargo Safely | 18 | 18 | 44 | 31 | 13 |
| 5 Air Brakes | 31 | 30 | 105 | 80 | 27 |
| 6 Combination Vehicles | 32 | 32 | 122 | 65 | 25 |
| 11 Vehicle Inspection | 15 | 15 | 81 | 39 | 16 |
| 12 Basic Vehicle Control | 7 | 7 | 27 | 17 | 7 |
| 13 On-Road Driving | 19 | 19 | 50 | 28 | 12 |
| **Total** | **261** | **258** | **957** | **571** | **211** |

**All eight sections are written.** Every subsection that holds anything has a
lesson. The three that do not are headings: 1.1, 1.3 and 5.4, which the manual
gives no text of their own.

Exam pools against their default lengths:

| Track | Questions | Default exam | |
|---|---|---|---|
| General Knowledge | 124 | 50 | full length |
| Air Brakes | 27 | 25 | full length |
| Combination Vehicles | 25 | 20 | full length |
| Vehicle Inspection | 16 | 25 | runs at 16 |
| Basic Vehicle Control | 7 | 20 | runs at 7 |
| On-Road Driving | 12 | 20 | runs at 12 |

An exam never asks for more questions than its pool holds, so the three skills
tracks simply run shorter until more are written.

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

## The app, screen by screen

Dark, with gold for anything that asks you to act. Gold is the only accent, so
it means something: a gold thing is a thing to press, or a number to remember.

Four tabs along the bottom:

| Tab | What is on it |
|---|---|
| **Today** | Where you are, one big button for the next thing to do, then the Reading, Testing and Cards rows. |
| **Progress** | Weak facts, best scores, and the progress summary to copy. |
| **Cards** | The TSV block and the .tsv file for Anki. |
| **Settings** | Reading, your manual, what you must study, the daily reminder, AI grading, and the backup. |

The button on Today is worked out by `NextStepRules`, not by a guess: the first
part of the first section you have not recalled; then, once the reading is
done, any cards waiting for Anki; then the exam of each track that is not at
90% yet. `docs/today-design.png` is the design it was built from.

## Name and icon

The app is **Gateway 2 100k**, with a Class A combination in white on deep blue.
The icon is an adaptive icon drawn as a vector, so every launcher shape works
and nothing is stored as a bitmap. `app/src/main/res/drawable/ic_launcher_foreground.xml`
holds the drawing.

The application id stays `com.cdlstudy`. A rename there would make Android treat
this as a different app, install it beside the old one, and leave your study
record stranded in the old one.

## Install on a phone

1. Copy `app-debug.apk` to the phone.
2. Open it from the Files app. Android asks once to allow installs from that
   app. Allow it, then tap Install.
3. Install a later version the same way. It updates in place.

With a USB cable and developer mode on:
`adb install -r app/build/outputs/apk/debug/app-debug.apk`.

## Backing up your record

Your recalls, the mark you gave every fact, your cards, exam results and
progress live only on this phone. **Settings, then Back up your record** writes
them to a JSON file you can keep anywhere, and reads one back.

Putting a record back REPLACES what is on the phone. It does not merge, and the
screen says so before it does it: merging two records would double every card
and every recall, with no honest way to tell a repeat recall from a copy of one.

The file holds your work only. The lessons come with the app, and your manual
stays where you imported it.

## Still to do

- Try every screen on a real phone. There is no emulator in the build
  environment, so the screens are compile-checked and the logic is unit-tested,
  but nothing here has been seen running except in screenshots you send back.
- A written lesson for the subsections that are still a page reference only.
- The AI grading request has not been sent against the live API from this
  repository: there is no API key here to send it with. The request is built
  with the official SDK, the schema hand-off is unit-tested, and every failure
  turns into a sentence on the screen instead of a crash. The first real call
  will be yours.
