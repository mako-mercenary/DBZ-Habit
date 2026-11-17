#!/usr/bin/env python3
"""remove_bg.py <in> <out>: makes the near-black background connected to the image edges transparent."""
import sys
from collections import deque
from PIL import Image, ImageFilter
src, out = sys.argv[1], sys.argv[2]
im = Image.open(src).convert('RGBA')
w, h = im.size
px = im.load()
LIMIT = 28  # how dark counts as background
def dark(x, y):
    r, g, b, a = px[x, y]
    return a < 10 or max(r, g, b) <= LIMIT
seen = bytearray(w * h)
queue = deque()
for x in range(w):
    for y in (0, h - 1):
        queue.append((x, y))
for y in range(h):
    for x in (0, w - 1):
        queue.append((x, y))
bg = Image.new('L', (w, h), 0)
bgp = bg.load()
while queue:
    x, y = queue.popleft()
    i = y * w + x
    if seen[i]:
        continue
    seen[i] = 1
    if not dark(x, y):
        continue
    bgp[x, y] = 255
    for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
        if 0 <= nx < w and 0 <= ny < h and not seen[ny * w + nx]:
            queue.append((nx, ny))
# Black pockets enclosed by other shapes (say, inside a loop of lightning) are
# background too when they are large; small ones are pupils and lettering.
POCKET = 1500
for sy in range(h):
    for sx in range(w):
        i = sy * w + sx
        if seen[i] or not dark(sx, sy):
            continue
        component = []
        stack = [(sx, sy)]
        seen[i] = 1
        while stack:
            x, y = stack.pop()
            component.append((x, y))
            for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
                if 0 <= nx < w and 0 <= ny < h:
                    j = ny * w + nx
                    if not seen[j] and dark(nx, ny):
                        seen[j] = 1
                        stack.append((nx, ny))
        if len(component) >= POCKET:
            for x, y in component:
                bgp[x, y] = 255
    # pixels that are not dark were already marked seen above or are skipped
# Keep the black outline that hugs the figure: only clear background more than 2 px from it.
figure = Image.eval(bg, lambda v: 255 - v)
grown = figure.filter(ImageFilter.MaxFilter(5))
alpha = grown.filter(ImageFilter.GaussianBlur(0.8))
orig_a = im.getchannel('A')
from PIL import ImageChops
im.putalpha(ImageChops.multiply(alpha, orig_a))
im.save(out)
pass
