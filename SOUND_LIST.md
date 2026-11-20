# Sound list

Every sound effect the app plays. Each key maps to a file in
`app/src/main/assets/sound/` through `manifest.json` there.

To use your own: put the file in that folder (WAV, MP3, OGG or M4A),
point its key at it in `manifest.json`, and rebuild. Keep effects short;
the suggested lengths are a guide, not a limit. The placeholders are
synthesised by `tools/placeholder_sounds.py`, which never overwrites
your files unless run with `--force`.

| Key | Sound | Plays when | Suggested length | File |
|---|---|---|---|---|
| `check_off` | Check-off | A habit is checked off. | up to 0.5 s | `check_off.wav` |
| `boss_hit` | Boss hit | A saga milestone lands on the boss. | up to 0.8 s | `boss_hit.wav` |
| `victory` | Victory | A boss is defeated. | up to 2 s | `victory.wav` |
| `transformation` | Transformation | A new form unlocks. | up to 2 s | `transformation.wav` |
| `bomb_throw` | Spirit bomb throw | A full spirit bomb is thrown. | up to 1.5 s | `bomb_throw.wav` |
| `level_up` | Level up | A bond level or an achievement is gained. | up to 1 s | `level_up.wav` |
| `dragon_summon` | Dragon summon | The dragon is summoned. | up to 2.5 s | `dragon_summon.wav` |
| `card_place` | Card placed | A card lands on the card battle board. | up to 0.25 s | `card_place.wav` |
| `card_flip` | Card captured | A card flips to its new owner. | up to 0.3 s | `card_flip.wav` |
| `card_combo` | Same, Plus or Combo | A special capture rule triggers. | up to 0.8 s | `card_combo.wav` |
| `card_hover` | Opponent choosing | The challenger considers a card in their hand. | up to 0.08 s | `card_hover.wav` |
| `card_win` | Match won | You win a card battle. | up to 1.2 s | `card_win.wav` |
| `card_lose` | Match lost | You lose a card battle. | up to 1 s | `card_lose.wav` |
| `pack_open` | Pack opened | A card pack is torn open. | up to 0.6 s | `pack_open.wav` |
| `card_reveal_rare` | Rare reveal | An epic, legendary or bond card is revealed. | up to 0.9 s | `card_reveal_rare.wav` |
