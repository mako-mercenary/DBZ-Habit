#!/usr/bin/env python3
"""fit_figure.py <in> <out> <preview> <width> <height>: trims transparent edges, then fits the figure
centred inside width x height with a small margin, on transparency. The preview shows it on the
app's dark panel at full colour and greyed out (as on the victory screen)."""
import sys
from PIL import Image, ImageOps
src, out, preview = sys.argv[1:4]
W, H = int(sys.argv[4]), int(sys.argv[5])
im = Image.open(src).convert('RGBA')
alpha = im.getchannel('A').point(lambda v: 255 if v > 24 else 0)
box = alpha.getbbox()
fig = im.crop(box)
margin = 0.04
scale = min(W * (1 - 2 * margin) / fig.width, H * (1 - 2 * margin) / fig.height)
fig = fig.resize((max(1, round(fig.width * scale)), max(1, round(fig.height * scale))), Image.LANCZOS)
canvas = Image.new('RGBA', (W, H), (0, 0, 0, 0))
canvas.alpha_composite(fig, ((W - fig.width) // 2, (H - fig.height) // 2))
canvas.save(out)
print('trimmed to', box, '-> placed', fig.size)
small = canvas.resize((W // 2, H // 2), Image.LANCZOS)
grey = ImageOps.grayscale(small.convert('RGB')).convert('RGBA')
grey.putalpha(small.getchannel('A').point(lambda v: int(v * 0.6)))
sheet = Image.new('RGBA', (W + 20, H // 2), (19, 24, 39, 255))
sheet.alpha_composite(small, (0, 0))
sheet.alpha_composite(grey, (W // 2 + 20, 0))
sheet.convert('RGB').save(preview)
