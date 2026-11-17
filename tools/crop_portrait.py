#!/usr/bin/env python3
"""crop_portrait.py <in> <out_512> <preview> <left> <top> <size>: square crop to 512, plus a circle preview."""
import sys
from PIL import Image, ImageDraw
src, out, preview, left, top, size = sys.argv[1], sys.argv[2], sys.argv[3], *map(int, sys.argv[4:7])
im = Image.open(src).convert('RGBA')
print('source', im.size)
crop = im.crop((left, top, left + size, top + size)).resize((512, 512), Image.LANCZOS)
crop.save(out)
prev = Image.new('RGBA', (512, 512), (19, 24, 39, 255))
mask = Image.new('L', (512, 512), 0)
ImageDraw.Draw(mask).ellipse([0, 0, 511, 511], fill=255)
from PIL import ImageChops
circle = crop.copy()
circle.putalpha(ImageChops.multiply(crop.getchannel('A'), mask))
prev.alpha_composite(circle)
prev.save(preview)
