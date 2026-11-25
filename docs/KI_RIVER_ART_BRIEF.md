# Art brief: Ki River (Goku's river shooter)

Personal-use artwork for a top-down scrolling shooter in the Saiyan Habits app.
17 images, one PNG each, named exactly as listed, returned in one folder.

## Style (applies to every image)

- **View:** straight top-down (bird's-eye), like a classic vertical-scrolling
  shooter. Everything that moves faces **up** the image (the direction of travel).
- **Style:** bright anime cel-shading, clean dark outlines, saturated colours,
  matching the card art. Light from the upper left on everything.
- **Sprites** (anything that moves or sits on the river): **transparent
  background**, subject centred, no shadow on the ground (the game draws one),
  no text, no border.
- **Tiles** (riverbank, water): **no transparency**, and they must repeat
  seamlessly from top to bottom so they can scroll forever.

## Master prompt

Paste this first, then each image's line below:

> Top-down, bird's-eye view game sprite for a vertical-scrolling shooter.
> Bright anime cel-shaded style with clean dark outlines and saturated colours,
> lit from the upper left. Subject centred and facing the top of the image.
> Transparent background, no ground shadow, no text, no border.

For the two tiles, replace the last line with: *"Fills the whole image, no
transparency, tiles seamlessly top to bottom."*

## The images

| # | File | Size (px) | What it is |
|---|---|---|---|
| 1 | `kiriver_goku.png` | 384 × 384 | Goku riding the golden flying cloud, seen from directly above, flying straight up the image. Arms forward as if about to fire. |
| 2 | `kiriver_goku_left.png` | 384 × 384 | Same as 1, banking left: body and cloud tilted, cloud trailing to the right. |
| 3 | `kiriver_goku_right.png` | 384 × 384 | Same as 1, banking right: the mirror of 2 (drawn, not flipped, so lighting stays correct). |
| 4 | `kiriver_blast.png` | 128 × 256 | A small blue-white ki blast flying up the image, bright core with a short glowing tail below it. |
| 5 | `kiriver_blast_charged.png` | 192 × 384 | A large charged ki beam blast, brighter and wider than 4, with crackling energy. |
| 6 | `kiriver_ki_orb.png` | 192 × 192 | A floating glowing golden-orange energy orb with a soft halo (the fuel pickup). |
| 7 | `kiriver_boat.png` | 256 × 384 | A small grey military patrol boat with a red-and-white army insignia (not a real-world flag), seen from above, bow pointing up. |
| 8 | `kiriver_helicopter.png` | 384 × 384 | A grey military helicopter seen from directly above, rotor blades motion-blurred, nose pointing up, same insignia. |
| 9 | `kiriver_drone.png` | 256 × 256 | A small round flying robot drone with a red eye, seen from above. |
| 10 | `kiriver_turret.png` | 256 × 256 | A gun turret on a concrete base, seen from above, barrel pointing up. Sits on the riverbank. |
| 11 | `kiriver_bridge.png` | 1536 × 320 | A stone-and-steel road bridge spanning the whole river, seen from above, running left to right across the image. Transparent above and below the bridge. |
| 12 | `kiriver_island.png` | 512 × 768 | A small rocky island with a palm tree and grass, seen from above, to sit in the middle of the river. Transparent around it. |
| 13 | `kiriver_bank.png` | 512 × 1024 | **Tile.** Canyon riverbank: grassy clifftop with rocks and trees, edge falling away to the water on the right side only (the game mirrors it for the other bank). Seamless top to bottom. |
| 14 | `kiriver_water.png` | 512 × 512 | **Tile.** Blue river water with gentle ripples and light sparkles. Seamless in every direction. |
| 15 | `kiriver_explosion_1.png` | 256 × 256 | Explosion, frame 1 of 3: a small bright flash with sparks. |
| 16 | `kiriver_explosion_2.png` | 256 × 256 | Explosion, frame 2 of 3: a big orange fireball. |
| 17 | `kiriver_explosion_3.png` | 256 × 256 | Explosion, frame 3 of 3: dissipating smoke puff with a few embers. |

## Checks before sending

- Images 1–12 and 15–17 have a **transparent** background (not a painted checkerboard).
- Images 13 and 14 **repeat seamlessly** when stacked top to bottom.
- Everything faces **up**; nothing has text, borders or ground shadows.
- File names match exactly.

Missing images are fine: the game uses plain placeholder shapes until each
picture arrives.
