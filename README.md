# Saiyan Habits

A personal-use Android habit tracker themed around Dragon Ball Z style training.
Fully offline: no accounts, no backend, no ads, no analytics.

## Status

| Milestone | What | State |
|---|---|---|
| 1 | Project setup, Room schema, habits CRUD, daily check-off, power level | Done |
| 2 | Streaks, transformations, healing beans, reminders | Done |
| 3 | Sagas, spirit bombs, time chamber | Done |
| 4 | Mentors, bonds, reactions, techniques | Done |
| 5 | Story chapters, wish orbs, rival | Done |
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

## Mentors and bonds

Every habit is trained by a mentor: its category's, or Vegeta for the one habit
flagged as hardest.

- Each check-off gives that mentor 10 bond XP. Bond levels run from 1 to 10.
- Each level from 2 up unlocks a short scene, readable on the Bonds tab.
- Levels 3, 6 and 9 unlock a technique:

  | Mentor | Level 3 | Level 6 | Level 9 |
  |---|---|---|---|
  | Roshi | +20% points on his habits | 4th healing bean slot | Master's Day Off: excuse one of today's habits (every 14 days) |
  | Goku | +20% points on his habits | Kaio-ken: double check-off points for the rest of the day (every 7 days) | Double spirit bomb throw bonus |
  | Piccolo | +20% points on his habits | Time chamber pays 2x instead of 1.5x | A healing bean every 5 streak days instead of 7 |
  | Bulma | +20% points on her habits | +25% points on saga milestones | +50% victory bonus |
  | Vegeta | +30% points on the hardest habit | 100x gravity pays 4x instead of 3x | Every assist deals double damage |

- From bond level 5 a mentor assists in boss fights, adding 1 bonus damage to
  every milestone hit. Bonus damage is worth points (10 per damage, and it
  counts towards the victory bonus) but does not shorten the fight: the boss
  still falls when the last milestone lands.
- One mentor speaks on the home screen each day. Mentors with habits take turns.
  What they talk about, in order of priority: a boss defeated today, a streak
  milestone reached today, a comeback after a missed day, a missed day, or
  nothing in particular.

### Editing the dialogue

All mentor lines are in `app/src/main/assets/dialogue/`, one JSON file per
mentor. Edit, rebuild, done.

- `reactions` holds the home-screen one-liners, a list per trigger:
  `missed_day`, `comeback`, `streak_milestone`, `boss_defeated`, `idle`.
  A mentor's lines for a trigger are used one per day in order, so a list of 8
  takes 8 days to repeat. `{streak}` is replaced by the streak length in
  `streak_milestone` lines, `{boss}` by the boss's name in `boss_defeated` lines.
- `scenes` holds the bond scenes, one per `level` from 2 to 10, each with a
  `title` and `lines`. A line's `speaker` is the mentor's name in capitals,
  `YOU` or `NARRATOR`.

`./gradlew testDebugUnitTest` checks every file: valid JSON, at least 8 lines
per trigger, a scene of 3 to 6 lines for every level, known speakers and
placeholders. A mistake is reported with its line number.

## Story, wish orbs and the rival

- **Story.** Eight chapters, reached from the book icon on the home screen.
  Chapter 1 is open from the start; chapters 2 to 7 unlock at power levels
  100, 300, 700, 1,500, 3,000 and 6,000; chapter 8 unlocks at form 3. A newly
  unlocked chapter is announced on the home screen. Opening a chapter counts
  as reading it.
- **Wish orbs.** Write real-life rewards as wishes under Settings. Each
  perfect week (Monday to Sunday, every scheduled habit done on every day, at
  least one day with something scheduled; a day saved by a bean does not count)
  earns an orb, up to seven. Weeks are judged once they have finished. With
  seven orbs, summon the dragon from the home screen and claim one wish; the
  orbs reset.
- **Rival.** The home screen shows last week's power gain as a rival figure.
  Mid-week it compares this week so far with where last week stood by the same
  weekday; once this week passes last week's whole total, the rival is beaten.

The story text is in `app/src/main/assets/dialogue/story.json`, in the same
line format as the bond scenes, and is checked by the unit tests (one chapter
per unlock, 120 to 400 words each, known speakers).

## Reminders

Each habit can have a reminder time. On its scheduled days, a notification
appears at that time if the habit isn't done yet. Reminders are run by Android's
WorkManager, which can deliver a few minutes late when the phone is saving
battery.

## Tuning

All points, multipliers, streak thresholds, bean limits, saga and spirit bomb payouts, timer lengths, bond levels, technique strengths, story unlock levels, the orb count and animation timings are in
[`app/src/main/java/com/saiyanhabits/GameConfig.kt`](app/src/main/java/com/saiyanhabits/GameConfig.kt).
Points are stored with each check-off, so a change only affects check-offs made afterwards.

## Layout

```
app/src/main/java/com/saiyanhabits/
  GameConfig.kt      tuning numbers
  domain/            rules with no Android code: points, schedules, streaks, beans, forms,
                     boss fights, spirit bombs, bonds, techniques, mentor reactions,
                     story unlocks, wish orbs, the rival
  data/              Room entities, DAOs, database, migrations, repositories
  reminders/         notifications and the WorkManager jobs behind them
  chamber/           the time chamber: session state and its foreground service
  ui/                Compose screens (home, habits, sagas, time chamber, bonds, story,
                     settings), navigation, theme, shared components
app/src/main/assets/dialogue/   mentor one-liners and bond scenes (one JSON file per
                                mentor) and the story chapters (story.json)
app/schemas/         Room schema per database version, for migrations
app/src/test/        unit tests
```
