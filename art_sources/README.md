# Art sources

The full-size images supplied for the app, before cropping. The app uses the
cropped versions in `app/src/main/assets/art/`; these are kept so a crop can be
redone later. Nothing here is packaged into the APK.

To redo a crop: `tools/crop_portrait.py <source> <out.png> <preview.png> <left> <top> <size>`
takes a square of `size` pixels from (`left`, `top`) and scales it to 512 x 512
(negative values add transparent margin). For a source with a solid black
background, first run `tools/remove_bg.py <source> <cut.png>`.
