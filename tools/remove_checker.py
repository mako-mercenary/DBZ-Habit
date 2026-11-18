#!/usr/bin/env python3
"""Removes a painted-in background from around a figure.

    python3 tools/remove_checker.py SOURCE OUT.png            # a grey-and-white checkerboard
    python3 tools/remove_checker.py SOURCE OUT.png --dark 100 # a dark backdrop with a glow

With --dark, the background is every pixel no brighter than the limit (0-255)
that is connected to the border: a black backdrop and the dim glow around the
figure, as long as it isn't tinted blue (dark navy parts of the figure stay).
That also eats the figure's dark outline, so a ring of a few pixels
around what is kept is put back, outline and all.

Some images arrive with the grey-and-white "transparent" checkerboard drawn
into the picture rather than real transparency. This clears every light, grey
pixel that is connected to the image's border, stopping at the figure's
outline, so light areas inside the figure are kept. The cut edge is softened
by a pixel. Needs numpy and Pillow.
"""
import sys

import numpy as np
from PIL import Image, ImageFilter


def checker_mask(rgb):
    """Pixels that look like the checkerboard: light and nearly grey."""
    rgb = rgb.astype(np.int16)
    light = rgb.min(axis=2) >= 200
    grey = (rgb.max(axis=2) - rgb.min(axis=2)) <= 14
    return light & grey


def dark_mask(rgb, limit):
    """
    Pixels no brighter than [limit] in any channel that aren't tinted blue: a black
    or warm backdrop and glow, but not the dark navy of a car's underside.
    """
    rgb = rgb.astype(np.int16)
    return (rgb.max(axis=2) <= limit) & (rgb[:, :, 2] - rgb[:, :, 0] <= 8)


def grow(mask, pixels):
    """[mask] widened by [pixels] in every direction (8-connected)."""
    out = mask.copy()
    for _ in range(pixels):
        g = out.copy()
        g[1:, :] |= out[:-1, :]
        g[:-1, :] |= out[1:, :]
        g[:, 1:] |= out[:, :-1]
        g[:, :-1] |= out[:, 1:]
        g[1:, 1:] |= out[:-1, :-1]
        g[:-1, :-1] |= out[1:, 1:]
        g[1:, :-1] |= out[:-1, 1:]
        g[:-1, 1:] |= out[1:, :-1]
        out = g
    return out


def flood_from_border(candidates):
    """The candidates reachable from the border through other candidates (4-connected)."""
    reached = np.zeros_like(candidates)
    reached[0, :] = candidates[0, :]
    reached[-1, :] = candidates[-1, :]
    reached[:, 0] = candidates[:, 0]
    reached[:, -1] = candidates[:, -1]
    while True:
        grown = reached.copy()
        grown[1:, :] |= reached[:-1, :]
        grown[:-1, :] |= reached[1:, :]
        grown[:, 1:] |= reached[:, :-1]
        grown[:, :-1] |= reached[:, 1:]
        grown &= candidates
        if (grown == reached).all():
            return reached
        reached = grown


def main():
    if len(sys.argv) not in (3, 5) or (len(sys.argv) == 5 and sys.argv[3] != "--dark"):
        sys.exit(__doc__)
    img = Image.open(sys.argv[1]).convert("RGBA")
    rgb = np.array(img)[:, :, :3]
    if len(sys.argv) == 5:
        background = flood_from_border(dark_mask(rgb, int(sys.argv[4])))
        background &= ~grow(~background, 4)  # keep the outline ring
    else:
        background = flood_from_border(checker_mask(rgb))
    alpha = np.where(background, 0, 255).astype(np.uint8)
    # Soften the cut by a pixel so the edge isn't jagged.
    alpha_img = Image.fromarray(alpha).filter(ImageFilter.MinFilter(3)).filter(ImageFilter.GaussianBlur(0.7))
    img.putalpha(alpha_img)
    img.save(sys.argv[2])
    print(f"cleared {background.mean() * 100:.0f}% of the picture")


if __name__ == "__main__":
    main()
