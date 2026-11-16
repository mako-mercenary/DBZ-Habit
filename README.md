# Saiyan Habits

A personal-use Android habit tracker themed around Dragon Ball Z style training.
Fully offline: no accounts, no backend, no ads, no analytics.

## Status

| Milestone | What | State |
|---|---|---|
| 1 | Project setup, Room schema, habits CRUD, daily check-off, power level | Done |
| 2 | Streaks, transformations, healing beans, reminders | Not started |
| 3 | Sagas, spirit bombs, time chamber | Not started |
| 4 | Mentors, bonds, reactions, techniques | Not started |
| 5 | Story chapters, wish orbs, rival | Not started |
| 6 | Art manifest, gallery swap, `ART_LIST.md`, polish and animations | Not started |
| 7 | Data export/import, final debug APK | Not started |

## Build

Needs a JDK (17 or newer) and the Android SDK (platform 37, build-tools 37.0.0).
Point the build at the SDK with the `ANDROID_HOME` environment variable or a
`local.properties` file containing `sdk.dir=/path/to/sdk`.

```
./gradlew assembleDebug        # APK lands in app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # unit tests
```

Every build is signed with the debug key in `app/debug.keystore`, so a newer APK
installs over an older one and keeps your data, whichever machine built it.

## Install on a phone

1. Copy `app-debug.apk` to the phone (download it, or send it to yourself).
2. Open it from the Files app. Android asks once to allow installs from that
   app: allow it, then tap Install.
3. For later versions, install the new APK the same way. It updates in place.

With a USB cable and developer mode on: `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

## Tuning

All points, multipliers and animation timings are in
[`app/src/main/java/com/saiyanhabits/GameConfig.kt`](app/src/main/java/com/saiyanhabits/GameConfig.kt).
Points are stored with each check-off, so a change only affects check-offs made afterwards.

## Layout

```
app/src/main/java/com/saiyanhabits/
  GameConfig.kt      tuning numbers
  domain/            rules with no Android code: points, schedules, mentors
  data/              Room entities, DAOs, database, repository
  ui/                Compose screens (home, habits), theme, shared components
app/schemas/         Room schema per database version, for migrations
app/src/test/        unit tests
```
