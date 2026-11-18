# Art sources

The full-size images supplied for the app, before cropping. The app uses the
cropped versions in `app/src/main/assets/art/`; these are kept so a crop can be
redone later. Nothing here is packaged into the APK.

To redo a crop: `tools/crop_portrait.py <source> <out.png> <preview.png> <left> <top> <size>`
takes a square of `size` pixels from (`left`, `top`) and scales it to 512 x 512
(negative values add transparent margin). For a source with a solid black
background, first run `tools/remove_bg.py <source> <cut.png>`.

For full figures (bosses, the dragon): `tools/fit_figure.py <source> <out.png> <preview.png> <width> <height>`
trims the empty edges and centres the figure in the target size.

For pictures that arrive with their background painted in:
`tools/remove_checker.py <source> <cut.png>` clears a grey-and-white
checkerboard (used for `bike_building_1`), and
`tools/remove_checker.py <source> <cut.png> --dark <limit>` clears a black
backdrop with a glow (`bike_car_1` at 100, `bike_car_2` at 150; `bike_car_3`
at 95 with `--any-tint`, for its blue glow). `bike_palm` used `--smooth`, which
floods the backdrop in small colour steps and stops at the figure's sharp edge. Then trim to
the figure and fit it inside the key's size, keeping its proportions.
