# Saiyan Habits: the RPG design

This is the plan for turning Saiyan Habits into a full RPG without losing what
makes it work. It is the reference for milestones 10 to 14; the README
describes what is built.

## The rule everything follows

**Real habits are the only thing that makes you stronger. The RPG is where you
spend that strength, never where you earn it.**

- Experience (your level) comes only from your power level, which comes only
  from habits and real projects. Fights never give experience.
- Big rewards from fights (prizes, recruits, story) pay out once, on the first
  clear, so there is nothing to farm.
- Nothing in the RPG is required daily, nothing decays, and nothing is
  time-limited. A day of the RPG takes a few minutes, or none.
- Habit tools (habits, reminders, the time chamber, wishes, history, notes)
  work everywhere and are never behind travel or a fight.
- Bad days cost progress speed, never progress: beans, Master's Day Off and
  flexible habits stay, and a lost fight makes you stronger (Zenkai).

## The daily loop

- **Morning:** open the app, a mentor speaks, check off habits.
- **During the day:** steps move you along the world map in the background.
- **Evening:** with today's training done, everything in the RPG is open:
  fight the next round, shop, visit a mentor.
- **Every so often:** a new tournament unlocks as your power level climbs.

## Your fighter

| Real life | In the game |
|---|---|
| Power level | Experience. Level = 1 + the square root of (power level / 16), up to 99: about level 10 after two weeks, 50 after a year. |
| Bond with each mentor (grown by that mentor's habit category) | Stats. Goku: Strength. Roshi: Vitality. Piccolo: Ki and Spirit. Bulma: Luck. Vegeta: Speed. |
| Forms from the streak | Transformations in battle: a big boost that drains ki each turn. |
| Perfect days in the last 7 | Peak condition: up to +20% stats at a tournament. Only ever a bonus. |
| Throwing a real spirit bomb | A Spirit Bomb charge: one huge attack, saved for a hard fight. |
| A finished day of training | Retries: a lost tournament can be re-entered once today's training is done. |
| Losing a fight | Zenkai: the next 3 perfect days give +50% power. |

Stats: Strength, Speed, Vitality, Ki, Spirit, Luck (1 to 255), plus HP (up to
9,999) and ki (up to 999). Every number is in `GameConfig.kt`.

## Combat

Active-time battles from a first-person view: the opponent's art fills the top
of the screen; your party is name boxes with HP, ki, time gauge and surge
meter. The screen shakes when you're hit.

The mechanics follow the classic active-time battle system closely, in the
app's own names and look:

| System | Here |
|---|---|
| Time gauges for every fighter, filling at Speed; Active and Wait modes; battle speed setting | Same. Wait mode (time stops while choosing) is the default. |
| Party of 3, front and back rows (back row halves physical damage dealt and taken) | You plus two recruits. |
| Commands: Attack, Magic, Summon, Item, Defend, Change row | Attack, Ki, Call, Item, Defend, Change row, plus Transform. |
| Stats, level 99, damage cap 9,999 | Same caps; damage formula of the same shape, tuned in GameConfig. |
| Weapon, armour, accessory; weapons and armour with linked slots | Gloves, gi, accessory, with linked slots. |
| Slot-in orbs of five kinds (spells, support, command, independent, summon); linked support pairs; levels from AP; stat trade-offs; a mastered one copies itself | **Capsules**, the same five kinds and rules. AP comes from check-offs (double on perfect days and from time chamber sessions), not from fights. |
| A gauge that fills from damage taken, four levels, new finishers learned through use, mood states | **Surge**, the same. You and every recruit have your own finishers. |
| Learning enemy moves by being hit by them | **Mimic capsule**. |
| Status effects (Poison, Sleep, Silence, Confuse, Berserk, Haste, Slow, Stop, Barrier, Regen...) | The same set. |
| Summons with a cinematic | **Mentors as summons**: a Roshi capsule calls a full-power Kamehameha. |
| Steal, Sense, Cover, Counter, pre-emptive and back attacks | Same. |
| Random encounters, Escape | None. Battles are tournaments, sparring and the odd optional roadside challenger. In a tournament, Escape is Forfeit. |

Opponents are data files (stats, moves, behaviour by HP, lines, art), checked
by unit tests like the dialogue files.

## Team and mentors

- **Party:** you plus two recruits; more recruits wait on the bench. Recruits
  level with you, so there is never a separate grind. Each has a fixed role.
- **Recruiting:** after each tournament, pick one fighter you beat to join.
  The ones not picked may turn up later as optional roadside challengers.
- **Mentors** stop fighting and teach. Bonds still grow from check-offs, still
  unlock scenes, and now also give stats. A bond level unlocks a technique,
  learned by visiting that mentor on the map once. Mentors fight beside you as
  summons. Home lines, scenes and the evening nudge stay.

## Tournaments

- Not time-gated: each opens when your power level reaches its threshold, in
  order. Thresholds are in GameConfig.
- A tournament is a run of fights; HP and ki carry over between rounds. You
  can stop and come back.
- The first round opens after your first check-off of the day; the final opens
  once today's training is done.
- A loss can be retried once today's training is done.
- Prizes (zeni, a recruit, a story chapter) pay out on the first clear only.

### The Roshi Cup (threshold 0)

An unofficial tournament Roshi runs on the beach behind Kame House, refereed
(badly) by Oolong. Where you start, so no travel.

| Round | Opponent | As a recruit |
|---|---|---|
| 1 | Murasaki, the ninja | Fast multi-hitter and thief: Steal, smoke (Blind), shadow clones (evasion) |
| 2 | Nam, fighting for his village's water | Heavy hitter: huge Strength, slow, one massive finishing dive |
| Semifinal | Android 8, a gentle giant who'd rather not fight | Tank: highest HP, covers allies, counters |
| Final | A mysterious old master (Roshi in disguise) | Not recruitable. Beating him earns the Roshi summon capsule. |

Later tournaments climb through the original series and into the Z era.

## Zeni

**Earned from habits:** every point of power also pays a zeni (check-offs,
saga milestones and victories, spirit bombs, time chamber bonuses), plus 50
for a perfect day and 300 for a perfect week. Later: a daily step-goal bonus,
first-clear tournament prizes, a little from sparring once a day. Past
training counts, so you start with zeni.

**Spent on:**
1. Vehicles (hoverbike, capsule plane, jet): each cuts the steps every route costs.
2. Gear: gloves, gis, weighted clothing, accessories, with capsule slots.
3. Capsules.
4. Battle items: healing, ki drinks, revives.
5. Cosmetics: aura colours, scouter skins, portrait frames.
6. Beans at Korin Tower: expensive, one a week at most. Beans are never used
   in battle, so the streak's safety net stays out of the game's reach.

## Travel

- Daily steps come from Health Connect (Samsung Health, Google Fit).
- Pick a destination and steps fill the route in the background; a
  notification says when you arrive. Steps walked with no destination go into
  a bank (capped), so no walk is wasted.
- Travel only gates RPG extras: shops, learning techniques, tournament venues,
  story scenes.
- Routes are sized for a normal day: 3,000 to 5,000 steps between nearby
  places, about 15,000 to a new tournament venue.
- Travel gets easier: vehicles cut the cost; later a story reward (the flying
  cloud, then teleporting) jumps to visited places for a handful of steps.
- Small finds on the road (zeni, a capsule, a mentor message). No random battles.
- A step-goal habit ("Walk 8,000 steps") checks itself off from your steps.

| Place | What's there |
|---|---|
| Kame House | Start; Roshi teaches; the Roshi Cup |
| West City | The shop |
| Capsule Corp | Bulma (gear upgrades), Vegeta's gravity room |
| Mount Paozu | Goku teaches |
| Korin Tower | Beans |
| Kami's Lookout | Piccolo teaches; the time chamber story; scenes |
| Tournament venues | Each new tournament |

## What stays, changes and goes

| Stays as it is | Changes | Goes |
|---|---|---|
| Habits, gravity, flexible habits, reminders | Bonds also give stats and techniques | Mentors assisting in boss fights (recruits replace them) |
| Power level, streak, beans, forms | Forms also work as battle transformations | Story chapters unlocked by power level |
| Sagas and spirit bombs | Sagas pay zeni and capsules; spirit bombs give a battle charge | Perks that only made sense with assists |
| Time chamber, wish orbs and the dragon, the rival | Story: same reader, new chapters tied to tournaments and places | |
| History, recap, notes, achievements, widget, nudge, sound, art system | New achievements for tournaments and travel | |

## Milestones

10. **Foundations:** level from power, stats from bonds, the zeni wallet
    (with credit for past training), the fighter on the Team tab.
11. **Battle engine and battle screen**, tested by sparring with mentors.
12. **The Roshi Cup**, recruiting, the tournament ladder.
13. **Map, steps and travel** (Health Connect), the shop, Korin Tower, vehicles.
14. **Capsules and gear in depth**, the new story, the next tournaments.
