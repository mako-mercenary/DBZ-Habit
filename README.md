# Saiyan Habits

A personal-use Android habit tracker themed around Dragon Ball Z style training.
Fully offline: no accounts, no backend, no ads, no analytics.

## Status

| Milestone | What | State |
|---|---|---|
| 1 | Project setup, Room schema, habits CRUD, daily check-off, power level | Done |
| 2 | Streaks, transformations, healing beans, reminders | Done |
| 3 | Sagas, spirit bombs, time chamber | Done |
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

## How streaks work

- A day counts once it is over. The app records each finished day as perfect
  (everything scheduled was done), a rest day (nothing was scheduled), or missed.
  The record uses your habits as they were that day, so editing a habit later
  never changes past days.
- The overall streak is the run of perfect days. Rest days don't add to it and
  don't break it. Today adds one as soon as everything scheduled is done.
- Each habit also has its own streak: its check-offs since the last scheduled
  day it was missed.
- Forms unlock when the overall streak reaches 7, 30 and 100 days, and stay
  unlocked if the streak later ends.
- A healing bean is earned at every 7th day of a streak (3 at most). After a
  missed day the app offers to spend one, in the app and in a notification at
  8 in the morning. A covered day keeps the streak going but doesn't add to it.

## Long tasks

- **Sagas** are long goals fought as a boss. You list milestones and give each
  a damage value from 1 to 5; the boss's HP is the total. Finishing a milestone
  deals its damage and pays 10 points per damage. At 0 HP the saga is won and
  pays a victory bonus of 100 plus 20 per HP.
- **Spirit bombs** are long tasks measured by amount (minutes, pages,
  sessions, or anything you type). A bomb has a size (100, 250 or 500 points)
  that is paid out in proportion as it fills. Throwing a full bomb finishes the
  task and pays half its size again.
- **The time chamber** is a 25 or 50 minute focus timer spent on one thing. When
  it ends, the progress it logs is worth 1.5x points:
  - on a habit, it checks the habit off for today and adds half its points again
    (once per habit per day);
  - on a saga milestone, it charges the milestone, which then pays 1.5x when it
    lands, and asks whether it is finished;
  - on a spirit bomb counted in minutes or sessions, it logs the session by
    itself; for any other unit it asks how much you got through.

  The timer runs as a foreground service with a countdown notification, so it
  keeps going with the screen off or the app closed.

## Reminders

Each habit can have a reminder time. On its scheduled days, a notification
appears at that time if the habit isn't done yet. Reminders are run by Android's
WorkManager, which can deliver a few minutes late when the phone is saving
battery.

## Tuning

All points, multipliers, streak thresholds, bean limits, saga and spirit bomb payouts, timer lengths and animation timings are in
[`app/src/main/java/com/saiyanhabits/GameConfig.kt`](app/src/main/java/com/saiyanhabits/GameConfig.kt).
Points are stored with each check-off, so a change only affects check-offs made afterwards.

## Layout

```
app/src/main/java/com/saiyanhabits/
  GameConfig.kt      tuning numbers
  domain/            rules with no Android code: points, schedules, streaks, beans, forms,
                     boss fights, spirit bombs
  data/              Room entities, DAOs, database, migrations, repositories
  reminders/         notifications and the WorkManager jobs behind them
  chamber/           the time chamber: session state and its foreground service
  ui/                Compose screens (home, habits, sagas, time chamber), navigation,
                     theme, shared components
app/schemas/         Room schema per database version, for migrations
app/src/test/        unit tests
```
