# CDL Study

An offline Android app for the Utah Class A CDL with air brakes. It runs the
study loop from `CDL_Class_A_Study_Handoff_1.pdf`: read, closed-book recall,
correction against the manual, flashcards, then an exam for each track.

No accounts, no backend, no ads, no network. Your study record stays on the phone.

## What it does, and what it does not do

| Job | Where it is done |
|---|---|
| Read the subsection | In your manual. The app shows the page numbers only. |
| Closed-book recall | In the app. No fact is shown until you send your recall. |
| Correction | In the app. You mark each manual fact against your own words. |
| Flashcards | The app makes them and exports TSV. **Anki** keeps the schedule. |
| Six exams | In the app. Each track is graded on its own. |
| Progress | In the app. It also copies a summary you can paste into any chat. |

The app does not replace Anki, and it does not replace practice behind the wheel.

## The study loop

1. **Read.** The app names the subsection and its manual pages. You read it in
   the manual, then close the manual.
2. **Recall.** You write everything you remember. The screen holds no facts.
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

`section01-sample.json` is a sample. It shows the format only. It sets
`"placeholder": true`, so the app shows a warning on every screen until it is
replaced.

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

- Content from the manual, section by section. This is the large job.
- AI grading of a recall, on demand, with your own API key.
- A daily reminder, and a streak for days with a recall.
- Export and import of the study record.
