# Art list

Every image the app uses, by key. To replace one, put a PNG with the same
filename in `app/src/main/assets/art/` and rebuild, or pick an image from your
phone under Settings > Art (the launcher icon is file-and-rebuild only).

Sizes are the recommended size in pixels. Larger images are scaled down
when picked in the app; files in the assets folder are used as they are.
"Circle" means the image is shown cropped to a circle, so keep the subject
centred. "Free" images are shown whole on a transparent background.

| Key | File | Used for | Size | Shown as |
|---|---|---|---|---|
| `mentor_roshi_portrait` | `mentor_roshi_portrait.png` | Roshi's portrait: the home-screen line, the Bonds tab, scenes and the story, assists in boss fights. | 512 × 512 | circle |
| `mentor_goku_portrait` | `mentor_goku_portrait.png` | Goku's portrait: the home-screen line, the Bonds tab, scenes and the story, assists in boss fights. | 512 × 512 | circle |
| `mentor_piccolo_portrait` | `mentor_piccolo_portrait.png` | Piccolo's portrait: the home-screen line, the Bonds tab, scenes and the story, assists in boss fights. | 512 × 512 | circle |
| `mentor_bulma_portrait` | `mentor_bulma_portrait.png` | Bulma's portrait: the home-screen line, the Bonds tab, scenes and the story, assists in boss fights. | 512 × 512 | circle |
| `mentor_vegeta_portrait` | `mentor_vegeta_portrait.png` | Vegeta's portrait: the home-screen line, the Bonds tab, scenes and the story, assists in boss fights. | 512 × 512 | circle |
| `boss_raditz` | `boss_raditz.png` | Raditz in the boss picker, on saga cards, in the fight and on the victory screen. | 1024 × 1024 | free |
| `boss_nappa` | `boss_nappa.png` | Nappa in the boss picker, on saga cards, in the fight and on the victory screen. | 1024 × 1024 | free |
| `boss_frieza` | `boss_frieza.png` | Frieza in the boss picker, on saga cards, in the fight and on the victory screen. | 1024 × 1024 | free |
| `boss_cell` | `boss_cell.png` | Cell in the boss picker, on saga cards, in the fight and on the victory screen. | 1024 × 1024 | free |
| `boss_buu` | `boss_buu.png` | Majin Buu in the boss picker, on saga cards, in the fight and on the victory screen. | 1024 × 1024 | free |
| `boss_broly` | `boss_broly.png` | Broly in the boss picker, on saga cards, in the fight and on the victory screen. | 1024 × 1024 | free |
| `rival_figure` | `rival_figure.png` | The rival (last week's you) on the home screen. | 512 × 512 | circle |
| `dragon` | `dragon.png` | The whole summoning scene: the dragon rising above a ring of the seven orbs, on a transparent background. Shown full-width at the top of the screen; put the ring about 85% of the way down, where the app adds a golden glow. | 1024 × 1536 | free |
| `player_portrait` | `player_portrait.png` | You, in base form: inside the scouter on the home screen, and your lines in scenes and the story. | 512 × 512 | circle |
| `player_form_1` | `player_form_1.png` | You as Super Saiyan (form 1), inside the scouter once the form is unlocked. | 512 × 512 | circle |
| `player_form_2` | `player_form_2.png` | You as Super Saiyan 2 (form 2). | 512 × 512 | circle |
| `player_form_3` | `player_form_3.png` | You as Super Saiyan 3 (form 3). | 512 × 512 | circle |
| `background_home` | `background_home.png` | Behind the home screen. A dark scrim is drawn over it so the cards stay readable. | 1080 × 2340 | full |
| `scouter_device` | `scouter_device.png` | The scouter's earpiece, on the right end of the power level readout. Upright, on a transparent background. | 280 × 560 | free |
| `cloud_ornament` | `cloud_ornament.png` | A flying cloud, used as a decoration on the new story chapter banner and the corners of the bottom bar. Transparent background; drawn mirrored on the right. | 600 × 360 | free |
| `emblem_capsule` | `emblem_capsule.png` | A faint emblem in the corner of the healing beans card and of Bulma's cards. Transparent background; shown at low opacity. | 512 × 512 | free |
| `bike_rider` | `bike_rider.png` | The hover-bike run: the rider on the bike, seen from behind, on a transparent background. Drawn over the 3D road, following the steering and tilting into turns; put the bottom of the bike on the bottom edge and keep it centred. The placeholder is a plain stand-in rider. | 768 × 768 | free |
| `bike_skyline` | `bike_skyline.png` | The hover-bike run: the city far away, behind the 3D street. A wide strip (4:1) with the horizon on the bottom edge; it is shown about 1.3 screens wide and slides a little as you steer, so keep the middle the busiest part. The sky above it is filled with a gradient matched to its top edge. | 2048 × 512 | free |
| `app_icon` | `app_icon.png` | The launcher icon's foreground. Android shows the middle 288 x 288 and crops the edges. Swapped by file and rebuild only. | 432 × 432 | icon |
| `bike_building_1` | `bike_building_1.png` | The hover-bike run: a roadside building, front or three-quarter view, standing on the bottom edge, transparent around it. Stood beside the road angled towards you; how wide it stands is set per slot in GameConfig (dome 18 m, shop 14 m, apartments 17 m, office tower 22 m) and its height follows the picture. Trimmed to the building, keeping its proportions. Until set, the 3D buildings are used. | 1024 × 1024 | free, optional |
| `bike_building_2` | `bike_building_2.png` | The hover-bike run: a roadside building, front or three-quarter view, standing on the bottom edge, transparent around it. Stood beside the road angled towards you; how wide it stands is set per slot in GameConfig (dome 18 m, shop 14 m, apartments 17 m, office tower 22 m) and its height follows the picture. Trimmed to the building, keeping its proportions. Until set, the 3D buildings are used. | 1024 × 1024 | free, optional |
| `bike_building_3` | `bike_building_3.png` | The hover-bike run: a roadside building, front or three-quarter view, standing on the bottom edge, transparent around it. Stood beside the road angled towards you; how wide it stands is set per slot in GameConfig (dome 18 m, shop 14 m, apartments 17 m, office tower 22 m) and its height follows the picture. Trimmed to the building, keeping its proportions. Until set, the 3D buildings are used. | 1024 × 1024 | free, optional |
| `bike_building_4` | `bike_building_4.png` | The hover-bike run: a roadside building, front or three-quarter view, standing on the bottom edge, transparent around it. Stood beside the road angled towards you; how wide it stands is set per slot in GameConfig (dome 18 m, shop 14 m, apartments 17 m, office tower 22 m) and its height follows the picture. Trimmed to the building, keeping its proportions. Until set, the 3D buildings are used. | 1024 × 1024 | free, optional |
| `bike_car_1` | `bike_car_1.png` | The hover-bike run: a hover car seen from behind, on a transparent background, its underside on the bottom edge. Floats over the road about 2.3 m wide; the game adds the shadow. Until set, the 3D cars are used. | 512 × 512 | free, optional |
| `bike_car_2` | `bike_car_2.png` | The hover-bike run: a hover car seen from behind, on a transparent background, its underside on the bottom edge. Floats over the road about 2.3 m wide; the game adds the shadow. Until set, the 3D cars are used. | 512 × 512 | free, optional |
| `bike_car_3` | `bike_car_3.png` | The hover-bike run: a hover car seen from behind, on a transparent background, its underside on the bottom edge. Floats over the road about 2.3 m wide; the game adds the shadow. Until set, the 3D cars are used. | 512 × 512 | free, optional |
| `bike_palm` | `bike_palm.png` | The hover-bike run: a palm tree, trunk foot on the bottom edge, transparent around it. About 8 m tall beside the road. Until set, the 3D palms are used. | 512 × 1024 | free, optional |
| `bike_person_1` | `bike_person_1.png` *(none yet)* | The hover-bike run: someone standing or walking on the pavement, feet on the bottom edge, transparent around them. About 1.75 m tall; just scenery, you can't hit them. Until set, there are no pedestrians. | 256 × 512 | free, optional |
| `bike_person_2` | `bike_person_2.png` *(none yet)* | The hover-bike run: someone standing or walking on the pavement, feet on the bottom edge, transparent around them. About 1.75 m tall; just scenery, you can't hit them. Until set, there are no pedestrians. | 256 × 512 | free, optional |
| `bike_sign` | `bike_sign.png` *(none yet)* | The hover-bike run: a city sign on a gantry that spans the road, posts on the bottom edge, transparent around it. Shown about 19 m wide every so often. Until set, there is no sign. | 1024 × 512 | free, optional |

The placeholders are generated by `tools/placeholder_art.py`; rerunning it
overwrites every file above, so keep your own art elsewhere too.

## Hover-bike run: 3D models

Bulma's game is drawn in 3D from the models in `app/src/main/assets/models/`
(glTF binary, `.glb`). The cars, buildings and street lights come from KayKit
City Builder Bits by Kay Lousberg, and the trucks, barriers, cones and palms
from Kenney's packs; both are free CC0 packs. The bike, the stand-in rider,
dragon balls, boosts, the dome, the hover pads, the robot, the road and the
ground are made from simple shapes by `tools/prepare_models.py`, which also
sizes and turns the downloaded models and takes the wheels off the vehicles.
To change one, replace its `.glb` (standing on y = 0, centred, facing +z;
vehicles show their back to +z).

The rider is normally the `bike_rider` image above; `rider.glb` and `bike.glb`
only show when that image can't be loaded.
