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
| 6 | Art manifest, gallery swap, `ART_LIST.md`, polish and animations | Done |
| 7 | Data export/import, final debug APK | Done |
| 8 | History screen, weekly recap | Done |
| 9 | Flexible habits, day notes, achievements, widget, evening nudge, more mentor lines, sound | Done |
| 10 | Mini games with the mentors | Done |

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

## Flexible habits

In the habit editor:

- **How often:** on the days you pick, or a number of **times a week** on any
  days (1 to 6). A weekly goal is only *due* on the day the rest of the week
  has exactly enough days left for it: 3 times a week with 1 done by Saturday
  is due on Saturday. Any other day it can still be done, and a day it is done
  counts like any check-off. A goal that slips counts as one missed day (a
  bean can save it, as usual), and after that it stops being due that week.
  In the week a weekly habit is made, its target is cut to the days left.
- **Each time:** once, or **a count** (2 to 50) with a name for what you count,
  such as 8 glasses. On the home screen each tap adds one; the last tap is the
  check-off and asks for the gravity as usual. The minus button takes one off.
  Undoing the check-off puts the count back to one short.

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

- `evening` holds the evening nudges (no placeholders).
- `reactions` holds the home-screen one-liners, a list per trigger:
  `missed_day`, `comeback`, `streak_milestone`, `boss_defeated`, `idle`.
  A mentor's lines for a trigger are used one per day in order, so a list of 16
  takes 16 days to repeat. `{streak}` is replaced by the streak length in
  `streak_milestone` lines, `{boss}` by the boss's name in `boss_defeated` lines.
- `scenes` holds the bond scenes, one per `level` from 2 to 10, each with a
  `title` and `lines`. A line's `speaker` is the mentor's name in capitals,
  `YOU` or `NARRATOR`.

`./gradlew testDebugUnitTest` checks every file: valid JSON, at least 8 lines
per trigger and 8 evening lines, a scene of 3 to 6 lines for every level, known speakers and
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

## History and the weekly recap

- **History** (the calendar icon on the home screen) shows your best and
  current streak, perfect days, and the share of scheduled habits you've done.
  Below that is a calendar: gold for a perfect day, green for a day saved by a
  bean, red for a missed day, faint for a rest day. Tap a day to see what you
  checked off and the power it earned. Then a chart of your power level over
  the last 30 or 90 days, or since you started.
- **Weekly recap.** The first time you open the app in a new week, last week's
  recap opens by itself: each day's colour, power gained against your rival
  (the week before), whether you earned a wish orb, your most-trained habit,
  the mentor who trained you most, and any saga milestones, bosses and spirit
  bombs. It only appears if something happened that week, and only once. You
  can open it again from History.

The "strong week" threshold for the recap headline is in `GameConfig.kt`.

## Day notes

Write a line about today from the card on the home screen, or about any past
day from that day in the History calendar. Days with a note get a dot in the
calendar.

## Achievements and titles

41 achievements, for check-offs, perfect days, streaks, power level, forms,
bosses, spirit bombs, the time chamber, bonds, the story, wishes and notes.
They are worked out from what's already in the app, so past training counts.
The number unlocked earns a title, shown under the power level; tap it to see
every achievement and how close you are. The titles and how many achievements
each needs are in `GameConfig.kt`; the last one needs all of them. An
achievement that has been announced stays unlocked even if you undo the
check-off that earned it.

## Home-screen widget

Long-press the home screen, choose Widgets, and drag out **Today's training**.
It shows your power level, streak and today's habits; tap a habit to check it
off at normal gravity (or add one to a count), or tap anything else to open the
app. Make it taller to see more habits. It updates as soon as anything changes
in the app, and every 30 minutes so the day rolls over.

## Evening nudge and sound

Settings > Extras:

- **Evening nudge:** at 9 pm (change the time there), if anything that counts
  today is still open, the mentor who trains most of it says so in a
  notification. Nothing comes if you're done.
- **Sound effects:** check-offs, boss hits, victories, transformations, spirit
  bomb throws, level-ups and the dragon. The sounds are listed in
  [`SOUND_LIST.md`](SOUND_LIST.md); to use your own, put the file in
  `app/src/main/assets/sound/`, point its key at it in `manifest.json` there,
  and rebuild.

## Mini games

A game with each mentor, from the controller icon on the home screen or the
Train button on a mentor's page. Each finished game gives that mentor 10 bond
XP, up to 20 a day per mentor (in `GameConfig.kt`), so games add to the bond
without replacing check-offs.

- **Roshi: chess.** You play white against four levels, from napping to
  serious. Hints, take-backs, promotion, resigning; the game saves itself. The
  engine is in `domain/games/Chess.kt` and is checked against the standard
  perft move counts.
- **Piccolo: meditation.** Guided breathing (calm 4-6, box, 4-7-8) for 1 to 10
  minutes, with an aura that swells and settles with each breath and a soft
  vibration at each change. Afterwards you can tick off one of today's habits.
- **Goku: fishing.** Charge a cast, strike when the bobber dips, then reel
  while keeping the line between slack and snapping. Seven fish, a fish log.
- **Vegeta: gravity-room rhythm.** Tap four lanes in time with a song. Notes
  are made from the music itself (beat detection), more of them on harder
  levels; combos raise the gravity up to 100x. Songs are listed in
  `app/src/main/assets/music/manifest.json` (title and file; MP3, WAV, OGG or
  M4A). A song is analysed once, the first time it's played.
- **Bulma: hover-bike run.** Tilt the phone (or hold a side) to weave through
  West City traffic, grabbing capsules and boosts.

The mentors' lines during their games are in the dialogue files under `game`,
`game_done` and `game_lost`.

## Look

Royal-blue panels in bevelled gold frames, slanted gold label tabs, and chunky
outlined lettering in the Bangers display font (see [CREDITS.md](CREDITS.md)).
The frames, tabs, watermarks and the green scouter screen are drawn in code in
`ui/components/DbzFrames.kt`, `ScouterReadout.kt` and `DbzNavBar.kt`, so they fit
any size; colours are in `ui/theme/Theme.kt`. The home background image shows
behind every screen.

## Art

Every image is listed in `app/src/main/assets/art/manifest.json`, which maps a
key to a file in the same folder. [`ART_LIST.md`](ART_LIST.md) lists each key,
where it is used and the size to make it.

- **Swap by file:** put a PNG with the same name in `app/src/main/assets/art/`
  and rebuild. No code changes. This includes the launcher icon (`app_icon.png`).
- **Swap on the phone:** Settings > Art lists every image; pick one from your
  gallery and it is copied into the app and used instead of the bundled file.
  "Reset" goes back to the bundled one. (The launcher icon can't be changed this
  way: Android bakes it in at build time.)
- The bundled placeholders are original, generated by
  `tools/placeholder_art.py`. Rerunning it overwrites every image in the folder.

## Your data

Settings > Your data:

- **Export data** saves everything (habits, check-offs, day records, streaks,
  beans, sagas, spirit bombs, bonds, techniques, story progress, wish orbs,
  wishes, counts and day notes) to one JSON file, wherever you choose: the phone, a cloud drive, an
  email to yourself.
- **Import data** replaces everything in the app with an export, after a
  confirmation that shows what the file holds. It happens in one step: if
  anything in the file is wrong, nothing changes. Reminders are rescheduled for
  the imported habits.
- Pictures picked under Settings > Art aren't included; keep the originals.
- An export can be imported into the same version of the app or a later one
  (from 1.0.0 on). One made by a newer version is refused with an explanation
  rather than half-imported.
- Settings in Settings > Extras and which achievements have been announced
  stay on the phone and aren't exported.

## Reminders

Each habit can have a reminder time. On its scheduled days, a notification
appears at that time if the habit isn't done yet. A weekly goal reminds you
each day until that week's target is reached. Reminders are run by Android's
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
