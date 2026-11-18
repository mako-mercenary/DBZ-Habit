#!/usr/bin/env python3
"""Removes a painted-in background from around a figure.

    python3 tools/remove_checker.py SOURCE OUT.png            # a grey-and-white checkerboard
    python3 tools/remove_checker.py SOURCE OUT.png --dark 100 # a dark backdrop with a glow
    python3 tools/remove_checker.py SOURCE OUT.png --dark 95 --any-tint  # ...a blue glow too
    python3 tools/remove_checker.py SOURCE OUT.png --dark 95 --greys     # ...fading to white haze
    python3 tools/remove_checker.py SOURCE OUT.png --smooth              # any smoothly fading backdrop

With --dark, the background is every pixel no brighter than the limit (0-255)
that is connected to the border: a black backdrop and the dim glow around the
figure, as long as it isn't tinted blue (dark navy parts of the figure stay);
--any-tint drops that exception, for a blue glow around a brighter figure;
--greys also clears washed-out, nearly grey pixels of any brightness, for a
backdrop that fades from black to a white haze behind a colourful figure.

With --smooth, the backdrop is whatever can be reached from the border in small
colour steps: haze, glows of any tint and black all fade gradually, while the
figure's edge is a sharp step, so it stops there. Used when a glow is too close
to the figure's own colours for the other modes.
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


def dark_mask(rgb, limit, any_tint=False):
    """
    Near-black pixels, and pixels no brighter than [limit] in any channel that aren't
    tinted blue: a black or warm backdrop and glow, but not the navy of a car's underside.
    """
    rgb = rgb.astype(np.int16)
    bright = rgb.max(axis=2)
    if any_tint:
        return bright <= limit
    return (bright <= 35) | ((bright <= limit) & (rgb[:, :, 2] - rgb[:, :, 0] <= 8))


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


def smooth_background(rgb, step=4):
    """
    Everything reachable from the border by small steps: each pixel joins if it is
    within [step] (per channel) of a neighbour that has already joined. A backdrop
    of haze and glows changes slowly; the figure's edge (an outline, or a jump to a
    bright colour) is a big step, so the flood stops there.
    """
    rgb = rgb.astype(np.int16)
    def close(a, b):
        return np.abs(a - b).max(axis=2) <= step
    down = close(rgb[1:, :], rgb[:-1, :])   # pixel (y+1, x) close to (y, x)
    right = close(rgb[:, 1:], rgb[:, :-1])  # pixel (y, x+1) close to (y, x)
    reached = np.zeros(rgb.shape[:2], dtype=bool)
    reached[0, :] = reached[-1, :] = True
    reached[:, 0] = reached[:, -1] = True
    while True:
        grown = reached.copy()
        grown[1:, :] |= reached[:-1, :] & down
        grown[:-1, :] |= reached[1:, :] & down
        grown[:, 1:] |= reached[:, :-1] & right
        grown[:, :-1] |= reached[:, 1:] & right
        if (grown == reached).all():
            return reached
        reached = grown


def main():
    args = sys.argv[1:]
    if "--smooth" in args:
        img = Image.open(args[0]).convert("RGBA")
        rgb = np.array(img)[:, :, :3]
        background = smooth_background(rgb)
        # Where a black backdrop met the outline, the outline went with it: put a ring back.
        dark = rgb.max(axis=2) <= 60
        background &= ~(dark & grow(~background, 3))
        alpha = Image.fromarray(np.where(background, 0, 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(0.6))
        img.putalpha(alpha)
        img.save(args[1])
        print(f"cleared {background.mean() * 100:.0f}% of the picture")
        return
    any_tint = "--any-tint" in args
    greys = "--greys" in args
    args = [a for a in args if a not in ("--any-tint", "--greys")]
    if len(args) not in (2, 4) or (len(args) == 4 and args[2] != "--dark"):
        sys.exit(__doc__)
    sys.argv = [sys.argv[0]] + args
    img = Image.open(sys.argv[1]).convert("RGBA")
    rgb = np.array(img)[:, :, :3]
    if len(sys.argv) == 5:
        candidates = dark_mask(rgb, int(sys.argv[4]), any_tint)
        if greys:
            spread = rgb.astype(np.int16).max(axis=2) - rgb.astype(np.int16).min(axis=2)
            candidates |= spread <= 30
        background = flood_from_border(candidates)
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
