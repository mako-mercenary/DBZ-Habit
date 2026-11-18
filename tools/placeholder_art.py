#!/usr/bin/env python3
"""
Generates the placeholder art, the art manifest and ART_LIST.md.

All of it is original: coloured silhouettes for portraits, simple shapes for
bosses, a gradient for the background. Run from the repository root:

    python3 tools/placeholder_art.py

It only creates images that are missing, so your own art is never touched.
Pass --force to overwrite every image with a fresh placeholder. The manifest
and ART_LIST.md are always rewritten.
"""
import json
import math
import os
import random
import sys

from PIL import Image, ImageChops, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ART = os.path.join(ROOT, "app", "src", "main", "assets", "art")
SS = 2  # supersampling for smooth edges

NIGHT = (10, 13, 22)
INK = (12, 15, 26)
ORANGE = (255, 138, 31)
ORANGE_DEEP = (232, 93, 4)
BLUE = (46, 155, 255)
GOLD = (255, 210, 63)
TEXT_DIM = (154, 166, 196)

MENTORS = {
    "roshi": (255, 200, 87),
    "goku": (255, 138, 31),
    "piccolo": (95, 211, 141),
    "bulma": (79, 209, 224),
    "vegeta": (108, 140, 255),
}
BOSSES = {
    # name: (colour, star points, pinch)
    "raditz": ((217, 105, 74), 3, 0.55),
    "nappa": ((217, 177, 59), 4, 1.0),
    "frieza": ((182, 120, 240), 6, 1.0),
    "cell": ((116, 208, 95), 5, 0.72),
    "buu": ((255, 130, 184), 28, 1.0),
    "broly": ((63, 224, 168), 8, 0.72),
}

# key, used for, width, height, shown as
KEYS = [
    *[(f"mentor_{m}_portrait", f"{m.capitalize()}'s portrait: the home-screen line, the Bonds tab, scenes and the story, assists in boss fights.", 512, 512, "circle") for m in MENTORS],
    *[(f"boss_{b}", f"{b.capitalize() if b != 'buu' else 'Majin Buu'} in the boss picker, on saga cards, in the fight and on the victory screen.", 1024, 1024, "free") for b in BOSSES],
    ("rival_figure", "The rival (last week's you) on the home screen.", 512, 512, "circle"),
    ("dragon", "The whole summoning scene: the dragon rising above a ring of the seven orbs, on a transparent background. Shown full-width at the top of the screen; put the ring about 85% of the way down, where the app adds a golden glow.", 1024, 1536, "free"),
    ("player_portrait", "You, in base form: inside the scouter on the home screen, and your lines in scenes and the story.", 512, 512, "circle"),
    ("player_form_1", "You as Super Saiyan (form 1), inside the scouter once the form is unlocked.", 512, 512, "circle"),
    ("player_form_2", "You as Super Saiyan 2 (form 2).", 512, 512, "circle"),
    ("player_form_3", "You as Super Saiyan 3 (form 3).", 512, 512, "circle"),
    ("background_home", "Behind the home screen. A dark scrim is drawn over it so the cards stay readable.", 1080, 2340, "full"),
    ("scouter_device", "The scouter's earpiece, on the right end of the power level readout. Upright, on a transparent background.", 280, 560, "free"),
    ("cloud_ornament", "A flying cloud, used as a decoration on the new story chapter banner and the corners of the bottom bar. Transparent background; drawn mirrored on the right.", 600, 360, "free"),
    ("emblem_capsule", "A faint emblem in the corner of the healing beans card and of Bulma's cards. Transparent background; shown at low opacity.", 512, 512, "free"),
    ("bike_rider", "The hover-bike run: the rider on the bike, seen from behind, on a transparent background. Drawn over the 3D road, following the steering and tilting into turns; put the bottom of the bike on the bottom edge and keep it centred. The placeholder is a plain stand-in rider.", 768, 768, "free"),
    ("bike_skyline", "The hover-bike run: the city far away, behind the 3D street. A wide strip (4:1) with the horizon on the bottom edge; it is shown about 1.3 screens wide and slides a little as you steer, so keep the middle the busiest part. The sky above it is filled with a gradient matched to its top edge.", 2048, 512, "free"),
    ("app_icon", "The launcher icon's foreground. Android shows the middle 288 x 288 and crops the edges. Swapped by file and rebuild only.", 432, 432, "icon"),
]


def canvas(w, h, bg=(0, 0, 0, 0)):
    return Image.new("RGBA", (w * SS, h * SS), bg)


FORCE = "--force" in sys.argv


def finish(img, path, supersampled=True):
    if os.path.exists(path) and not FORCE:
        print(f"  {os.path.relpath(path, ROOT)}  kept (already there)")
        return
    if supersampled:
        w, h = img.size
        img = img.resize((w // SS, h // SS), Image.LANCZOS)
    img.save(path, "PNG", optimize=True)
    print(f"  {os.path.relpath(path, ROOT)}  {img.size[0]}x{img.size[1]}")


def rgba(color, alpha=255):
    return (*color, alpha)


def glow(img, center, radius, color, alpha=120, steps=40):
    """A soft radial glow, drawn as fading concentric circles on its own layer."""
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    for i in range(steps, 0, -1):
        r = radius * i / steps
        a = int(alpha * (1 - i / steps) ** 2)
        d.ellipse([center[0] - r, center[1] - r, center[0] + r, center[1] + r], fill=rgba(color, a))
    return Image.alpha_composite(img, layer)


def circle_mask(size, center, radius):
    mask = Image.new("L", size, 0)
    ImageDraw.Draw(mask).ellipse([center[0] - radius, center[1] - radius, center[0] + radius, center[1] + radius], fill=255)
    return mask


def silhouette(color, hair=None, extras=None, aura=None, size=512):
    """Head and shoulders in one colour, clipped to a circle. Hair and extras are drawn on top."""
    s = size * SS
    img = canvas(size, size)
    cx = cy = s // 2
    r = s // 2
    img = glow(img, (cx, cy), r, color, alpha=70)
    if aura:
        ring = Image.new("RGBA", img.size, (0, 0, 0, 0))
        ImageDraw.Draw(ring).ellipse([cx - r + 8 * SS, cy - r + 8 * SS, cx + r - 8 * SS, cy + r - 8 * SS], outline=rgba(aura, 200), width=10 * SS)
        img = Image.alpha_composite(img, ring)

    figure = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(figure)
    d.ellipse([cx - 200 * SS, cy + 80 * SS, cx + 200 * SS, cy + 470 * SS], fill=rgba(color))  # shoulders
    head_r = 88 * SS
    head_c = (cx, cy - 45 * SS)
    d.ellipse([head_c[0] - head_r, head_c[1] - head_r, head_c[0] + head_r, head_c[1] + head_r], fill=rgba(color))
    if hair:
        hair(d, head_c, head_r, color)
    if extras:
        extras(d, head_c, head_r)
    figure.putalpha(ImageChops.multiply(figure.getchannel("A"), circle_mask(img.size, (cx, cy), r)))
    return Image.alpha_composite(img, figure)


def spikes(count, height, width_factor=1.0, lean=0.0):
    """Hair as a row of triangles on top of the head."""
    def draw(d, head_c, head_r, color):
        x0 = head_c[0] - head_r * 0.95 * width_factor
        x1 = head_c[0] + head_r * 0.95 * width_factor
        base_y = head_c[1] - head_r * 0.35
        step = (x1 - x0) / count
        pts = [(x0, base_y + head_r * 0.3)]
        for i in range(count):
            left = x0 + i * step
            tip_x = left + step / 2 + lean * head_r
            tip_y = base_y - height * head_r * (0.75 + 0.25 * math.sin(i * 1.7))
            pts += [(tip_x, tip_y), (left + step, base_y + head_r * 0.1 if i % 2 else base_y)]
        pts.append((x1, base_y + head_r * 0.3))
        d.polygon(pts, fill=rgba(color))
    return draw


def sunglasses(d, head_c, head_r):
    y = head_c[1] - head_r * 0.15
    d.rounded_rectangle([head_c[0] - head_r * 0.8, y - head_r * 0.16, head_c[0] + head_r * 0.8, y + head_r * 0.16], radius=head_r * 0.12, fill=rgba(INK))


def piccolo_ears(d, head_c, head_r, color):
    for side in (-1, 1):
        d.polygon([
            (head_c[0] + side * head_r * 0.85, head_c[1] - head_r * 0.2),
            (head_c[0] + side * head_r * 1.6, head_c[1] - head_r * 0.55),
            (head_c[0] + side * head_r * 0.85, head_c[1] + head_r * 0.35),
        ], fill=rgba(color))
        # antennae
        d.line([(head_c[0] + side * head_r * 0.3, head_c[1] - head_r * 0.9), (head_c[0] + side * head_r * 0.55, head_c[1] - head_r * 1.5)], fill=rgba(color), width=int(head_r * 0.12))
        d.ellipse([head_c[0] + side * head_r * 0.55 - head_r * 0.1, head_c[1] - head_r * 1.6, head_c[0] + side * head_r * 0.55 + head_r * 0.1, head_c[1] - head_r * 1.4], fill=rgba(color))


def bob(d, head_c, head_r, color):
    d.ellipse([head_c[0] - head_r * 1.25, head_c[1] - head_r * 1.35, head_c[0] + head_r * 1.25, head_c[1] + head_r * 0.55], fill=rgba(color))
    # the face shows as a lighter inner oval
    d.ellipse([head_c[0] - head_r * 0.8, head_c[1] - head_r * 0.75, head_c[0] + head_r * 0.8, head_c[1] + head_r * 0.95], fill=rgba(tuple(min(255, c + 40) for c in color)))


def long_hair(d, head_c, head_r, color):
    d.polygon([
        (head_c[0] - head_r * 1.1, head_c[1] + head_r * 2.4),
        (head_c[0] - head_r * 1.0, head_c[1] - head_r * 0.6),
        (head_c[0] - head_r * 0.5, head_c[1] - head_r * 1.5),
        (head_c[0], head_c[1] - head_r * 1.1),
        (head_c[0] + head_r * 0.5, head_c[1] - head_r * 1.5),
        (head_c[0] + head_r * 1.0, head_c[1] - head_r * 0.6),
        (head_c[0] + head_r * 1.1, head_c[1] + head_r * 2.4),
    ], fill=rgba(color))
    d.ellipse([head_c[0] - head_r * 0.7, head_c[1] - head_r * 0.6, head_c[0] + head_r * 0.7, head_c[1] + head_r], fill=rgba(tuple(min(255, c + 40) for c in color)))


def lightning(d, head_c, head_r):
    for side in (-1, 1):
        x = head_c[0] + side * head_r * 1.9
        y = head_c[1] - head_r * 0.4
        pts = [(x, y - head_r), (x - side * head_r * 0.25, y - head_r * 0.3), (x + side * head_r * 0.1, y - head_r * 0.25), (x - side * head_r * 0.2, y + head_r * 0.6)]
        d.line(pts, fill=rgba((191, 224, 255)), width=int(head_r * 0.1), joint="curve")


def star_points(center, radius, points, pinch):
    pts = []
    for i in range(points * 2):
        r = radius if i % 2 == 0 else radius * pinch * math.cos(math.pi / points)
        a = -math.pi / 2 + i * math.pi / points
        pts.append((center[0] + r * math.cos(a), center[1] + r * math.sin(a)))
    return pts


def boss(color, points, pinch, size=1024):
    s = size * SS
    img = canvas(size, size)
    c = (s // 2, s // 2)
    img = glow(img, c, s // 2, color, alpha=120)
    d = ImageDraw.Draw(img)
    body = s * 0.31
    pts = star_points(c, body, points, pinch)
    d.polygon(pts, fill=rgba(INK), outline=rgba(color), width=int(s * 0.035))
    # eyes: two slanted slits
    eye_y = c[1] - body * 0.08
    reach = body * 0.34
    for side in (-1, 1):
        d.line([(c[0] + side * reach * 0.35, eye_y + reach * 0.22), (c[0] + side * reach, eye_y - reach * 0.12)], fill=rgba((255, 255, 255)), width=int(s * 0.03))
    return img


def rival(size=512):
    s = size * SS
    img = canvas(size, size)
    c = (s // 2, s // 2)
    img = glow(img, c, s // 2, (255, 90, 95), alpha=60)
    d = ImageDraw.Draw(img)
    shadow = (42, 47, 66)
    d.ellipse([c[0] - 200 * SS, c[1] + 80 * SS, c[0] + 200 * SS, c[1] + 470 * SS], fill=rgba(shadow))
    d.ellipse([c[0] - 88 * SS, c[1] - 133 * SS, c[0] + 88 * SS, c[1] + 43 * SS], fill=rgba(shadow))
    spikes(6, 1.1, 1.05)(d, (c[0], c[1] - 45 * SS), 88 * SS, shadow)
    for side in (-1, 1):
        d.ellipse([c[0] + side * 32 * SS - 9 * SS, c[1] - 62 * SS, c[0] + side * 32 * SS + 9 * SS, c[1] - 44 * SS], fill=rgba((255, 59, 48)))
    img.putalpha(ImageChops.multiply(img.getchannel("A"), circle_mask(img.size, c, s // 2)))
    return img


def dragon(w=1024, h=1536):
    img = canvas(w, h)
    W, H = img.size
    d = ImageDraw.Draw(img)
    green = (63, 191, 106)
    belly = (217, 226, 122)
    pts = []
    steps = 60
    for i in range(steps + 1):
        f = i / steps
        x = W / 2 + math.sin(f * 3.2 * math.pi) * W * 0.3 * (1 - 0.45 * f)
        y = H * 0.85 - f * (H * 0.85 - H * 0.1)
        pts.append((x, y))
    thick = int(W * 0.085)
    d.line(pts, fill=rgba(green), width=thick, joint="curve")
    d.line(pts, fill=rgba(belly, 210), width=int(thick * 0.28), joint="curve")
    head = pts[-1]
    ring_c = (W / 2, H * 0.85)
    for i in range(7):
        a = -math.pi / 2 + i * 2 * math.pi / 7
        cx, cy = ring_c[0] + math.cos(a) * W * 0.3, ring_c[1] + math.sin(a) * W * 0.12
        orb = W * 0.06
        d.ellipse([cx - orb, cy - orb, cx + orb, cy + orb], fill=rgba(ORANGE), outline=rgba(ORANGE_DEEP), width=int(orb * 0.12))
    hr = thick * 0.85
    for side in (-1, 1):
        d.line([(head[0] + side * hr * 0.5, head[1] - hr * 0.5), (head[0] + side * hr * 1.3, head[1] - hr * 1.9)], fill=rgba(belly), width=int(thick * 0.16))
        d.line([(head[0] + side * hr * 0.8, head[1] + hr * 0.3), (head[0] + side * hr * 2.2, head[1] + hr * 1.1)], fill=rgba(green), width=int(thick * 0.08))
    d.ellipse([head[0] - hr, head[1] - hr, head[0] + hr, head[1] + hr], fill=rgba(green))
    for side in (-1, 1):
        er = hr * 0.17
        d.ellipse([head[0] + side * hr * 0.42 - er, head[1] - hr * 0.15 - er, head[0] + side * hr * 0.42 + er, head[1] - hr * 0.15 + er], fill=rgba((255, 59, 48)))
    return img


def background(w=1080, h=2340):
    # Vertical gradient: deep blue at the top down to night at the bottom.
    top, bottom = (18, 26, 51), NIGHT
    column = Image.new("RGB", (1, h))
    for y in range(h):
        f = y / (h - 1)
        column.putpixel((0, y), tuple(int(top[i] + (bottom[i] - top[i]) * f) for i in range(3)))
    img = column.resize((w, h)).convert("RGBA")
    img = glow(img, (int(w * 0.2), int(h * 0.22)), int(w * 0.9), ORANGE, alpha=38, steps=60)
    img = glow(img, (int(w * 0.85), int(h * 0.7)), int(w * 0.8), BLUE, alpha=34, steps=60)
    d = ImageDraw.Draw(img)
    rng = random.Random(7)
    for _ in range(160):
        x, y = rng.randrange(w), rng.randrange(h)
        r = rng.choice([1, 1, 1, 2])
        a = rng.randrange(60, 170)
        d.ellipse([x - r, y - r, x + r, y + r], fill=(220, 230, 255, a))
    return img


def app_icon(size=432):
    img = canvas(size, size)
    s = size * SS
    d = ImageDraw.Draw(img)
    c = s / 2
    # Blue ring
    r = s * 0.23
    d.ellipse([c - r, c - r, c + r, c + r], outline=rgba(BLUE), width=int(s * 0.035))
    # Flame
    def flame(scale, color, dy=0):
        pts = []
        for ang in range(0, 360, 6):
            a = math.radians(ang)
            # a teardrop: narrow at the top, round at the bottom
            rr = scale * (0.55 + 0.45 * math.sin(a))
            if ang in range(60, 120):
                rr *= 1.25
            pts.append((c + rr * math.cos(a - math.pi / 2) * 0.75, c + dy + rr * math.sin(a - math.pi / 2) * 1.0))
        d.polygon(pts, fill=rgba(color))
    flame(s * 0.2, ORANGE, s * 0.02)
    flame(s * 0.1, (255, 217, 168), s * 0.07)
    return img


def scouter_device(w=280, h=560):
    img = canvas(w, h)
    W, H = img.size
    d = ImageDraw.Draw(img)
    # A white earpiece housing with a dark band and a red lens light.
    d.rounded_rectangle([W * 0.2, H * 0.06, W * 0.95, H * 0.94], radius=W * 0.3, fill=rgba((236, 240, 246)), outline=rgba((40, 44, 56)), width=int(W * 0.04))
    d.rounded_rectangle([W * 0.05, H * 0.3, W * 0.32, H * 0.7], radius=W * 0.1, fill=rgba((40, 44, 56)))
    d.rounded_rectangle([W * 0.5, H * 0.38, W * 0.8, H * 0.62], radius=W * 0.06, fill=rgba((214, 38, 38)), outline=rgba((40, 44, 56)), width=int(W * 0.03))
    d.ellipse([W * 0.56, H * 0.41, W * 0.66, H * 0.47], fill=rgba((255, 160, 160)))
    return img


def cloud_ornament(w=600, h=360):
    img = canvas(w, h)
    W, H = img.size
    d = ImageDraw.Draw(img)
    gold, edge = (255, 214, 64), (170, 110, 0)
    puffs = [(0.2, 0.62, 0.17), (0.38, 0.45, 0.22), (0.6, 0.48, 0.2), (0.78, 0.6, 0.15), (0.5, 0.68, 0.18), (0.3, 0.72, 0.13)]
    for cx, cy, r in puffs:
        R = r * W
        d.ellipse([cx * W - R - 6 * SS, cy * H - R - 6 * SS, cx * W + R + 6 * SS, cy * H + R + 6 * SS], fill=rgba(edge))
    for cx, cy, r in puffs:
        R = r * W
        d.ellipse([cx * W - R, cy * H - R, cx * W + R, cy * H + R], fill=rgba(gold))
    for cx, cy, r in puffs[:4]:
        R = r * W * 0.45
        d.arc([cx * W - R, cy * H - R, cx * W + R, cy * H + R], start=200, end=340, fill=rgba(edge), width=int(5 * SS))
    return img


def emblem_capsule(size=512):
    img = canvas(size, size)
    S = size * SS
    d = ImageDraw.Draw(img)
    # An original emblem: a ring around a capsule pill.
    d.ellipse([S * 0.08, S * 0.08, S * 0.92, S * 0.92], outline=rgba((255, 255, 255)), width=int(S * 0.06))
    d.rounded_rectangle([S * 0.28, S * 0.4, S * 0.72, S * 0.6], radius=S * 0.1, outline=rgba((255, 255, 255)), width=int(S * 0.05))
    d.line([S * 0.5, S * 0.4, S * 0.5, S * 0.6], fill=rgba((255, 255, 255)), width=int(S * 0.04))
    return img


def bike_rider(size=768):
    """A plain stand-in rider on a hover bike, seen from behind. Not any particular character."""
    img = canvas(size, size)
    S = size * SS
    img = glow(img, (S * 0.5, S * 0.86), S * 0.2, (90, 220, 255), alpha=200)
    d = ImageDraw.Draw(img)
    ink = rgba((30, 34, 44))
    w = int(S * 0.012)
    # Rider: a grey jacket, dark trousers, a round white helmet.
    d.rounded_rectangle([S * 0.36, S * 0.5, S * 0.64, S * 0.7], radius=S * 0.06, fill=rgba((60, 66, 84)), outline=ink, width=w)
    d.rounded_rectangle([S * 0.38, S * 0.28, S * 0.62, S * 0.54], radius=S * 0.08, fill=rgba((120, 130, 150)), outline=ink, width=w)
    for side in (-1, 1):
        d.line([S * (0.5 + side * 0.11), S * 0.34, S * (0.5 + side * 0.2), S * 0.47], fill=rgba((120, 130, 150)), width=int(S * 0.05))
    d.ellipse([S * 0.42, S * 0.12, S * 0.58, S * 0.3], fill=rgba((240, 242, 246)), outline=ink, width=w)
    d.rectangle([S * 0.44, S * 0.2, S * 0.56, S * 0.23], fill=rgba((80, 170, 230)))
    # Bike: a white body with two side pods and a thruster.
    d.rounded_rectangle([S * 0.3, S * 0.6, S * 0.7, S * 0.84], radius=S * 0.07, fill=rgba((236, 238, 242)), outline=ink, width=w)
    for side in (-1, 1):
        x0 = 0.5 + side * 0.2
        d.rounded_rectangle([S * min(x0, x0 + side * 0.12), S * 0.62, S * max(x0, x0 + side * 0.12), S * 0.82], radius=S * 0.04, fill=rgba((214, 96, 150)), outline=ink, width=w)
        d.line([S * (0.5 + side * 0.17), S * 0.47, S * (0.5 + side * 0.24), S * 0.45], fill=ink, width=int(S * 0.02))
    d.ellipse([S * 0.41, S * 0.8, S * 0.59, S * 0.95], fill=rgba((40, 44, 56)), outline=ink, width=w)
    d.ellipse([S * 0.44, S * 0.83, S * 0.56, S * 0.92], fill=rgba((150, 235, 255)))
    return img


def bike_skyline(w=2048, h=512):
    """A far-off city: towers and domes against the sky, hazy at the bottom."""
    top, bottom = (58, 132, 230), (196, 226, 248)
    column = Image.new("RGB", (1, h))
    for y in range(h):
        f = y / (h - 1)
        column.putpixel((0, y), tuple(int(top[i] + (bottom[i] - top[i]) * f) for i in range(3)))
    img = column.resize((w, h)).convert("RGBA")
    d = ImageDraw.Draw(img)
    rng = random.Random(11)
    for _ in range(9):  # clouds
        cx, cy = rng.randrange(w), rng.randrange(int(h * 0.05), int(h * 0.4))
        for _ in range(6):
            r = rng.randrange(30, 70)
            ox, oy = rng.randrange(-90, 90), rng.randrange(-20, 20)
            d.ellipse([cx + ox - r * 1.6, cy + oy - r, cx + ox + r * 1.6, cy + oy + r], fill=(255, 255, 255, 200))
    for layer, (shade, lo, hi) in enumerate([((150, 180, 215), 0.35, 0.75), ((175, 200, 228), 0.25, 0.55)]):
        x = -40
        while x < w:
            bw = rng.randrange(50, 130)
            top_y = h * (1 - rng.uniform(lo, hi) * (0.6 + 0.8 * math.exp(-((x - w / 2) / (w * 0.3)) ** 2)) * 0.6)
            if rng.random() < 0.25:
                d.ellipse([x, top_y, x + bw * 1.6, h + (h - top_y)], fill=shade + (255,))
                x += int(bw * 1.6)
            else:
                d.rectangle([x, top_y, x + bw, h], fill=shade + (255,))
                for wy in range(int(top_y) + 14, h, 26):
                    d.line([x + 8, wy, x + bw - 8, wy], fill=tuple(c + 18 for c in shade) + (255,), width=6)
                x += bw + rng.randrange(4, 30)
    haze = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    hd = ImageDraw.Draw(haze)
    for y in range(int(h * 0.7), h):
        hd.line([0, y, w, y], fill=bottom + (int(230 * max(0.0, (y - h * 0.7) / (h * 0.3)) ** 1.5),))
    return Image.alpha_composite(img, haze)


def main():
    os.makedirs(ART, exist_ok=True)
    print("Placeholders:")
    manifest = {}
    for key, _, w, h, _ in KEYS:
        manifest[key] = f"{key}.png"
    for name, color in MENTORS.items():
        hair = {"roshi": None, "goku": spikes(5, 1.5, 1.05), "piccolo": piccolo_ears, "bulma": bob, "vegeta": spikes(4, 2.1, 0.8)}[name]
        extras = sunglasses if name == "roshi" else None
        finish(silhouette(color, hair, extras), os.path.join(ART, f"mentor_{name}_portrait.png"))
    for name, (color, points, pinch) in BOSSES.items():
        finish(boss(color, points, pinch), os.path.join(ART, f"boss_{name}.png"))
    finish(rival(), os.path.join(ART, "rival_figure.png"))
    finish(dragon(), os.path.join(ART, "dragon.png"))
    finish(silhouette(BLUE, spikes(5, 1.0, 0.95)), os.path.join(ART, "player_portrait.png"))
    finish(silhouette(GOLD, spikes(5, 1.6, 1.0), aura=GOLD), os.path.join(ART, "player_form_1.png"))
    finish(silhouette((255, 224, 102), spikes(5, 1.9, 1.0), lightning, aura=(127, 212, 255)), os.path.join(ART, "player_form_2.png"))
    finish(silhouette((255, 241, 168), long_hair, aura=(255, 255, 255)), os.path.join(ART, "player_form_3.png"))
    finish(background(), os.path.join(ART, "background_home.png"), supersampled=False)
    finish(app_icon(), os.path.join(ART, "app_icon.png"))
    finish(scouter_device(), os.path.join(ART, "scouter_device.png"))
    finish(cloud_ornament(), os.path.join(ART, "cloud_ornament.png"))
    finish(emblem_capsule(), os.path.join(ART, "emblem_capsule.png"))
    finish(bike_rider(), os.path.join(ART, "bike_rider.png"))
    finish(bike_skyline(), os.path.join(ART, "bike_skyline.png"), supersampled=False)

    with open(os.path.join(ART, "manifest.json"), "w") as f:
        json.dump(manifest, f, indent=2)
        f.write("\n")
    print("  manifest.json")

    lines = [
        "# Art list",
        "",
        "Every image the app uses, by key. To replace one, put a PNG with the same",
        "filename in `app/src/main/assets/art/` and rebuild, or pick an image from your",
        "phone under Settings > Art (the launcher icon is file-and-rebuild only).",
        "",
        "Sizes are the recommended size in pixels. Larger images are scaled down",
        "when picked in the app; files in the assets folder are used as they are.",
        "\"Circle\" means the image is shown cropped to a circle, so keep the subject",
        "centred. \"Free\" images are shown whole on a transparent background.",
        "",
        "| Key | File | Used for | Size | Shown as |",
        "|---|---|---|---|---|",
    ]
    for key, used, w, h, shape in KEYS:
        lines.append(f"| `{key}` | `{manifest[key]}` | {used} | {w} × {h} | {shape} |")
    lines += [
        "",
        "The placeholders are generated by `tools/placeholder_art.py`; rerunning it",
        "overwrites every file above, so keep your own art elsewhere too.",
        "",
    ]
    # Keep any sections written by hand after the table (such as the 3D models).
    old = os.path.join(ROOT, "ART_LIST.md")
    if os.path.exists(old):
        text = open(old).read()
        at = text.find("\n## ")
        if at >= 0:
            lines.append(text[at + 1:].rstrip("\n"))
            lines.append("")
    with open(os.path.join(ROOT, "ART_LIST.md"), "w") as f:
        f.write("\n".join(lines))
    print("  ART_LIST.md")


if __name__ == "__main__":
    main()
