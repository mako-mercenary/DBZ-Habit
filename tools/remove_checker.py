#!/usr/bin/env python3
"""Removes a painted-in transparency checkerboard from around a figure.

    python3 tools/remove_checker.py SOURCE OUT.png

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
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    img = Image.open(sys.argv[1]).convert("RGBA")
    rgb = np.array(img)[:, :, :3]
    background = flood_from_border(checker_mask(rgb))
    alpha = np.where(background, 0, 255).astype(np.uint8)
    # Soften the cut by a pixel so the edge isn't jagged.
    alpha_img = Image.fromarray(alpha).filter(ImageFilter.MinFilter(3)).filter(ImageFilter.GaussianBlur(0.7))
    img.putalpha(alpha_img)
    img.save(sys.argv[2])
    print(f"cleared {background.mean() * 100:.0f}% of the picture")


if __name__ == "__main__":
    main()
