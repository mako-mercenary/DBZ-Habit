#!/usr/bin/env python3
"""Prepares the 3D models for Bulma's hover-bike run.

Two kinds of model end up in app/src/main/assets/models/:

* Picked from Kenney's CC0 packs (car kit, city kit commercial, city kit roads,
  nature kit; https://kenney.nl). Each is copied with its texture packed into
  the file, then resized and turned so that it stands on the ground (y = 0),
  is centred, and faces the camera the way the game expects: vehicles show
  their rear, buildings their front.
* Made here from simple shapes: the hover bike, a stand-in rider, dragon
  balls, boost orbs, a Capsule Corp dome, the road strip and the ground.

    python3 tools/prepare_models.py KENNEY_DIR

KENNEY_DIR holds the unzipped packs (car-kit/, city-kit-commercial/,
city-kit-roads/, nature-kit/). Needs numpy and Pillow.
"""
import io
import json
import math
import struct
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "app/src/main/assets/models"

# ---- GLB reading and writing ----


def read_glb(path):
    data = Path(path).read_bytes()
    magic, version, length = struct.unpack("<III", data[:12])
    assert magic == 0x46546C67, f"{path} is not a GLB"
    offset = 12
    gltf, binary = None, b""
    while offset < length:
        chunk_len, chunk_type = struct.unpack("<II", data[offset:offset + 8])
        chunk = data[offset + 8:offset + 8 + chunk_len]
        if chunk_type == 0x4E4F534A:
            gltf = json.loads(chunk)
        elif chunk_type == 0x004E4942:
            binary = chunk
        offset += 8 + chunk_len
    return gltf, bytearray(binary)


def write_glb(path, gltf, binary):
    while len(binary) % 4:
        binary.append(0)
    gltf["buffers"] = [{"byteLength": len(binary)}]
    js = json.dumps(gltf, separators=(",", ":")).encode()
    js += b" " * ((4 - len(js) % 4) % 4)
    total = 12 + 8 + len(js) + 8 + len(binary)
    out = struct.pack("<III", 0x46546C67, 2, total)
    out += struct.pack("<II", len(js), 0x4E4F534A) + js
    out += struct.pack("<II", len(binary), 0x004E4942) + bytes(binary)
    Path(path).write_bytes(out)


def append_view(gltf, binary, data, target=None):
    while len(binary) % 4:
        binary.append(0)
    view = {"buffer": 0, "byteOffset": len(binary), "byteLength": len(data)}
    if target:
        view["target"] = target
    binary.extend(data)
    gltf.setdefault("bufferViews", []).append(view)
    return len(gltf["bufferViews"]) - 1


def embed_images(gltf, binary, folder):
    """Packs images the model points to (like Textures/colormap.png) into the file itself."""
    for image in gltf.get("images", []):
        uri = image.get("uri")
        if not uri or uri.startswith("data:"):
            continue
        data = (Path(folder) / uri).read_bytes()
        image.pop("uri")
        image["bufferView"] = append_view(gltf, binary, data)
        image["mimeType"] = "image/png" if uri.lower().endswith(".png") else "image/jpeg"


# ---- Bounds and normalising ----


def node_matrix(node):
    if "matrix" in node:
        return np.array(node["matrix"], dtype=float).reshape(4, 4).T
    t = node.get("translation", [0, 0, 0])
    r = node.get("rotation", [0, 0, 0, 1])
    s = node.get("scale", [1, 1, 1])
    x, y, z, w = r
    rot = np.array([
        [1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
        [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
        [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)],
    ])
    m = np.eye(4)
    m[:3, :3] = rot * np.array(s)
    m[:3, 3] = t
    return m


def bounds(gltf):
    """The model's box in its own space, from every mesh's position bounds through the node tree."""
    lo = np.full(3, np.inf)
    hi = np.full(3, -np.inf)

    def visit(index, parent):
        node = gltf["nodes"][index]
        m = parent @ node_matrix(node)
        if "mesh" in node:
            for prim in gltf["meshes"][node["mesh"]]["primitives"]:
                acc = gltf["accessors"][prim["attributes"]["POSITION"]]
                mn, mx = acc["min"], acc["max"]
                for cx in (mn[0], mx[0]):
                    for cy in (mn[1], mx[1]):
                        for cz in (mn[2], mx[2]):
                            p = m @ np.array([cx, cy, cz, 1.0])
                            lo[:] = np.minimum(lo, p[:3])
                            hi[:] = np.maximum(hi, p[:3])
        for child in node.get("children", []):
            visit(child, m)

    scene = gltf["scenes"][gltf.get("scene", 0)]
    for root in scene["nodes"]:
        visit(root, np.eye(4))
    return lo, hi


def normalise(gltf, size, axis, turn_degrees):
    """
    Wraps the scene in one node that turns it [turn_degrees] about the vertical,
    scales it so its extent along [axis] (0 x, 1 y, 2 z, after turning) is
    [size] metres, and moves it to stand on y = 0, centred in x and z.
    """
    lo, hi = bounds(gltf)
    a = math.radians(turn_degrees)
    c, s = math.cos(a), math.sin(a)
    turn = np.array([[c, 0, s, 0], [0, 1, 0, 0], [-s, 0, c, 0], [0, 0, 0, 1]])
    corners = np.array([[x, y, z, 1] for x in (lo[0], hi[0]) for y in (lo[1], hi[1]) for z in (lo[2], hi[2])])
    turned = (turn @ corners.T).T[:, :3]
    tlo, thi = turned.min(axis=0), turned.max(axis=0)
    k = size / max(thi[axis] - tlo[axis], 1e-6)
    centre = (tlo + thi) / 2
    shift = np.array([-centre[0] * k, -tlo[1] * k, -centre[2] * k])
    m = np.eye(4)
    m[:3, :3] = turn[:3, :3] * k
    m[:3, 3] = shift
    scene = gltf["scenes"][gltf.get("scene", 0)]
    gltf["nodes"].append({"name": "normalised", "matrix": list(m.T.flatten()), "children": scene["nodes"]})
    scene["nodes"] = [len(gltf["nodes"]) - 1]
    lo2 = np.array([-(thi[0] - tlo[0]) * k / 2, 0, -(thi[2] - tlo[2]) * k / 2])
    return lo2, -lo2 + np.array([0, (thi[1] - tlo[1]) * k, 0])


# What to take from Kenney: (pack, file, output name, size, axis, turn). Vehicles in the
# car kit face +z; the game wants their back to the camera (+z), so they turn 180.
PICKS = [
    ("car-kit", "sedan", "car_sedan", 4.4, 2, 180),
    ("car-kit", "taxi", "car_taxi", 4.4, 2, 180),
    ("car-kit", "police", "car_police", 4.6, 2, 180),
    ("car-kit", "suv", "car_suv", 4.6, 2, 180),
    ("car-kit", "hatchback-sports", "car_sports", 4.2, 2, 180),
    ("car-kit", "van", "van", 5.2, 2, 180),
    ("car-kit", "delivery", "truck_delivery", 6.5, 2, 180),
    ("car-kit", "truck", "truck", 7.0, 2, 180),
    ("city-kit-commercial", "building-a", "building_a", 11, 0, 0),
    ("city-kit-commercial", "building-b", "building_b", 11, 0, 0),
    ("city-kit-commercial", "building-c", "building_c", 11, 0, 0),
    ("city-kit-commercial", "building-d", "building_d", 11, 0, 0),
    ("city-kit-commercial", "building-e", "building_e", 12, 0, 0),
    ("city-kit-commercial", "building-f", "building_f", 12, 0, 0),
    ("city-kit-commercial", "building-skyscraper-a", "tower_a", 11, 0, 0),
    ("city-kit-commercial", "building-skyscraper-b", "tower_b", 11, 0, 0),
    ("city-kit-commercial", "building-skyscraper-c", "tower_c", 11, 0, 0),
    ("city-kit-roads", "construction-barrier", "barrier", 2.4, 0, 90),
    ("city-kit-roads", "construction-cone", "cone", 0.7, 1, 0),
    ("city-kit-roads", "light-square", "street_light", 6.5, 1, 0),
    ("nature-kit", "tree_palmTall", "palm_tall", 8.0, 1, 0),
    ("nature-kit", "tree_palmDetailedTall", "palm_detailed", 8.5, 1, 0),
    ("nature-kit", "tree_palmBend", "palm_bend", 7.0, 1, 0),
]


def take_kenney(kenney):
    report = {}
    for pack, name, out, size, axis, turn in PICKS:
        matches = list(Path(kenney, pack).rglob(f"GLB format/{name}.glb")) or list(Path(kenney, pack).rglob(f"{name}.glb"))
        if not matches:
            print("missing", pack, name)
            continue
        src = matches[0]
        gltf, binary = read_glb(src)
        embed_images(gltf, binary, src.parent)
        lo, hi = normalise(gltf, size, axis, turn)
        write_glb(OUT / f"{out}.glb", gltf, binary)
        report[out] = [round(float(v), 2) for v in (hi - lo)]
    return report


# ---- Made from shapes ----


class Builder:
    """Collects coloured shapes into one model, a mesh per material."""

    def __init__(self):
        self.parts = {}  # (r, g, b, emissive, metal, rough) -> (positions, normals, indices)

    def add(self, positions, normals, indices, color, emissive=0.0, metallic=0.0, roughness=0.6):
        key = (tuple(round(c, 3) for c in color), emissive, metallic, roughness)
        pos, nor, idx = self.parts.setdefault(key, ([], [], []))
        base = len(pos)
        pos.extend(positions)
        nor.extend(normals)
        idx.extend(i + base for i in indices)

    def box(self, centre, size, color, **kw):
        cx, cy, cz = centre
        sx, sy, sz = (s / 2 for s in size)
        faces = [
            ((1, 0, 0), [(sx, -sy, -sz), (sx, sy, -sz), (sx, sy, sz), (sx, -sy, sz)]),
            ((-1, 0, 0), [(-sx, -sy, sz), (-sx, sy, sz), (-sx, sy, -sz), (-sx, -sy, -sz)]),
            ((0, 1, 0), [(-sx, sy, -sz), (-sx, sy, sz), (sx, sy, sz), (sx, sy, -sz)]),
            ((0, -1, 0), [(-sx, -sy, sz), (-sx, -sy, -sz), (sx, -sy, -sz), (sx, -sy, sz)]),
            ((0, 0, 1), [(sx, -sy, sz), (sx, sy, sz), (-sx, sy, sz), (-sx, -sy, sz)]),
            ((0, 0, -1), [(-sx, -sy, -sz), (-sx, sy, -sz), (sx, sy, -sz), (sx, -sy, -sz)]),
        ]
        for normal, quad in faces:
            pts = [(cx + x, cy + y, cz + z) for x, y, z in quad]
            self.add(pts, [normal] * 4, [0, 1, 2, 0, 2, 3], color, **kw)

    def sphere(self, centre, radius, color, scale=(1, 1, 1), rings=14, segments=20, top_only=False, **kw):
        cx, cy, cz = centre
        pos, nor, idx = [], [], []
        last_ring = rings // 2 if top_only else rings
        for i in range(last_ring + 1):
            v = i / rings
            phi = v * math.pi
            for j in range(segments + 1):
                theta = j / segments * 2 * math.pi
                n = (math.sin(phi) * math.cos(theta), math.cos(phi), math.sin(phi) * math.sin(theta))
                pos.append((cx + n[0] * radius * scale[0], cy + n[1] * radius * scale[1], cz + n[2] * radius * scale[2]))
                nor.append(n)
        for i in range(last_ring):
            for j in range(segments):
                a = i * (segments + 1) + j
                b = a + segments + 1
                idx += [a, a + 1, b, a + 1, b + 1, b]
        self.add(pos, nor, idx, color, **kw)

    def cylinder(self, centre, radius, height, color, axis="y", segments=20, **kw):
        cx, cy, cz = centre
        pos, nor, idx = [], [], []

        def place(a, b, h):
            # a, b across the cylinder, h along its axis
            if axis == "y":
                return (cx + a, cy + h, cz + b)
            if axis == "z":
                return (cx + a, cy + b, cz + h)
            return (cx + h, cy + a, cz + b)

        def normal(a, b):
            if axis == "y":
                return (a, 0, b)
            if axis == "z":
                return (a, b, 0)
            return (0, a, b)

        for j in range(segments + 1):
            t = j / segments * 2 * math.pi
            a, b = math.cos(t), math.sin(t)
            pos += [place(a * radius, b * radius, -height / 2), place(a * radius, b * radius, height / 2)]
            nor += [normal(a, b), normal(a, b)]
        for j in range(segments):
            k = j * 2
            idx += [k, k + 1, k + 2, k + 1, k + 3, k + 2]
        self.add(pos, nor, idx, color, **kw)
        # Caps.
        for h, sign in ((-height / 2, -1), (height / 2, 1)):
            cap_pos = [place(0, 0, h)]
            for j in range(segments + 1):
                t = j / segments * 2 * math.pi
                cap_pos.append(place(math.cos(t) * radius, math.sin(t) * radius, h))
            axis_n = {"y": (0, sign, 0), "z": (0, 0, sign), "x": (sign, 0, 0)}[axis]
            cap_idx = []
            for j in range(segments):
                cap_idx += [0, j + 1, j + 2] if sign > 0 else [0, j + 2, j + 1]
            self.add(cap_pos, [axis_n] * len(cap_pos), cap_idx, color, **kw)

    def save(self, path, texture=None, uv_quads=None):
        gltf = {"asset": {"version": "2.0", "generator": "saiyan-habits prepare_models.py"},
                "scene": 0, "scenes": [{"nodes": []}], "nodes": [], "meshes": [], "materials": [], "accessors": []}
        binary = bytearray()
        for (color, emissive, metallic, roughness), (pos, nor, idx) in self.parts.items():
            p = np.array(pos, dtype=np.float32)
            n = np.array(nor, dtype=np.float32)
            n /= np.maximum(np.linalg.norm(n, axis=1, keepdims=True), 1e-6)
            i = np.array(idx, dtype=np.uint32)
            pv = append_view(gltf, binary, p.tobytes(), 34962)
            nv = append_view(gltf, binary, n.tobytes(), 34962)
            iv = append_view(gltf, binary, i.tobytes(), 34963)
            acc = gltf["accessors"]
            acc.append({"bufferView": pv, "componentType": 5126, "count": len(p), "type": "VEC3",
                        "min": p.min(axis=0).tolist(), "max": p.max(axis=0).tolist()})
            acc.append({"bufferView": nv, "componentType": 5126, "count": len(n), "type": "VEC3"})
            acc.append({"bufferView": iv, "componentType": 5125, "count": len(i), "type": "SCALAR"})
            material = {"pbrMetallicRoughness": {"baseColorFactor": [*color, 1.0], "metallicFactor": metallic, "roughnessFactor": roughness}}
            if emissive:
                material["emissiveFactor"] = [c * emissive for c in color]
            gltf["materials"].append(material)
            gltf["meshes"].append({"primitives": [{"attributes": {"POSITION": len(acc) - 3, "NORMAL": len(acc) - 2},
                                                   "indices": len(acc) - 1, "material": len(gltf["materials"]) - 1}]})
            gltf["nodes"].append({"mesh": len(gltf["meshes"]) - 1})
            gltf["scenes"][0]["nodes"].append(len(gltf["nodes"]) - 1)
        if texture is not None:
            add_textured_quads(gltf, binary, texture, uv_quads)
        write_glb(path, gltf, binary)


def add_textured_quads(gltf, binary, image, quads):
    """Adds flat ground quads carrying [image]: each quad is (x0, x1, z0, z1, u0, u1, v0, v1, y)."""
    buf = io.BytesIO()
    image.save(buf, "PNG")
    img_view = append_view(gltf, binary, buf.getvalue())
    gltf.setdefault("images", []).append({"bufferView": img_view, "mimeType": "image/png"})
    gltf.setdefault("samplers", []).append({"magFilter": 9729, "minFilter": 9987, "wrapS": 10497, "wrapT": 10497})
    gltf.setdefault("textures", []).append({"source": len(gltf["images"]) - 1, "sampler": len(gltf["samplers"]) - 1})
    pos, nor, uv, idx = [], [], [], []
    for x0, x1, z0, z1, u0, u1, v0, v1, y in quads:
        b = len(pos)
        pos += [(x0, y, z0), (x1, y, z0), (x1, y, z1), (x0, y, z1)]
        nor += [(0, 1, 0)] * 4
        uv += [(u0, v0), (u1, v0), (u1, v1), (u0, v1)]
        idx += [b, b + 2, b + 1, b, b + 3, b + 2]
    p = np.array(pos, dtype=np.float32)
    acc = gltf["accessors"]
    acc.append({"bufferView": append_view(gltf, binary, p.tobytes(), 34962), "componentType": 5126, "count": len(p),
                "type": "VEC3", "min": p.min(axis=0).tolist(), "max": p.max(axis=0).tolist()})
    acc.append({"bufferView": append_view(gltf, binary, np.array(nor, dtype=np.float32).tobytes(), 34962),
                "componentType": 5126, "count": len(p), "type": "VEC3"})
    acc.append({"bufferView": append_view(gltf, binary, np.array(uv, dtype=np.float32).tobytes(), 34962),
                "componentType": 5126, "count": len(p), "type": "VEC2"})
    acc.append({"bufferView": append_view(gltf, binary, np.array(idx, dtype=np.uint32).tobytes(), 34963),
                "componentType": 5125, "count": len(idx), "type": "SCALAR"})
    gltf["materials"].append({"pbrMetallicRoughness": {"baseColorTexture": {"index": len(gltf["textures"]) - 1},
                                                       "metallicFactor": 0.0, "roughnessFactor": 0.9}})
    gltf["meshes"].append({"primitives": [{"attributes": {"POSITION": len(acc) - 4, "NORMAL": len(acc) - 3, "TEXCOORD_0": len(acc) - 2},
                                           "indices": len(acc) - 1, "material": len(gltf["materials"]) - 1}]})
    gltf["nodes"].append({"mesh": len(gltf["meshes"]) - 1})
    gltf["scenes"][0]["nodes"].append(len(gltf["nodes"]) - 1)


WHITE = (0.95, 0.95, 0.95)
PINK = (0.91, 0.12, 0.39)
BLUE = (0.05, 0.28, 0.63)
CYAN = (0.31, 0.76, 0.97)
DARK = (0.12, 0.13, 0.15)


def make_bike():
    """The hover bike from behind: a white body, pink side pods, a glowing blue thruster. Faces -z."""
    b = Builder()
    b.sphere((0, 0.75, 0), 0.5, WHITE, scale=(0.9, 0.55, 1.9), roughness=0.35)
    b.box((0, 0.95, -0.55), (0.55, 0.18, 0.9), WHITE, roughness=0.35)
    for side in (-1, 1):
        b.cylinder((side * 0.62, 0.62, 0.35), 0.2, 1.1, PINK, axis="z", roughness=0.4)
        b.box((side * 0.62, 0.62, 0.92), (0.3, 0.3, 0.05), DARK)
    b.cylinder((0, 0.62, 0.95), 0.22, 0.12, CYAN, axis="z", emissive=3.0)
    b.cylinder((0, 0.62, 0.9), 0.3, 0.06, DARK, axis="z")
    b.cylinder((0, 1.2, -0.85), 0.04, 0.9, DARK, axis="x")
    b.box((0, 0.75, 0.97), (0.35, 0.25, 0.02), BLUE, emissive=0.3)
    return b


def make_rider():
    """A stand-in rider leaning over the bike, until a real model is added. Faces -z."""
    b = Builder()
    skin = (1.0, 0.8, 0.6)
    b.box((0, 1.25, 0.1), (0.42, 0.3, 0.5), WHITE)  # shorts
    b.box((0, 1.55, -0.05), (0.4, 0.42, 0.3), (1.0, 0.92, 0.23))  # top
    for side in (-1, 1):
        b.box((side * 0.27, 1.38, -0.45), (0.1, 0.1, 0.6), skin)  # arms to the bars
        b.box((side * 0.2, 1.05, 0.15), (0.14, 0.14, 0.55), skin)  # legs
    b.sphere((0, 1.92, -0.05), 0.2, (0.15, 0.65, 0.6))  # hair
    b.sphere((0, 1.85, 0.25), 0.11, (0.15, 0.65, 0.6), scale=(1, 2.2, 1))  # ponytail
    b.sphere((0, 2.08, 0.05), 0.07, (0.9, 0.15, 0.15), scale=(1.6, 1, 1))  # bow
    return b


def make_dragon_ball():
    b = Builder()
    b.sphere((0, 0, 0), 0.32, (1.0, 0.6, 0.05), emissive=0.5, roughness=0.15)
    for k, (x, y) in enumerate([(-0.1, 0.05), (0.08, 0.1), (0.0, -0.08), (0.12, -0.05)]):
        b.sphere((x, y, 0.3), 0.045, (0.85, 0.05, 0.05), scale=(1, 1, 0.4))
    return b


def make_boost():
    b = Builder()
    b.sphere((0, 0, 0), 0.38, CYAN, emissive=2.5, roughness=0.1)
    b.sphere((0, 0, 0), 0.2, (1, 1, 1), emissive=3.0)
    return b


def make_robot():
    """A squat patrol robot with a red eye. Faces +z (towards the camera)."""
    b = Builder()
    grey = (0.38, 0.49, 0.55)
    b.box((0, 0.75, 0), (0.9, 0.9, 0.7), grey, metallic=0.6, roughness=0.4)
    b.sphere((0, 1.45, 0), 0.38, (0.47, 0.56, 0.61), metallic=0.6, roughness=0.3)
    b.sphere((0, 1.48, 0.34), 0.12, (1.0, 0.1, 0.2), emissive=3.0)
    for side in (-1, 1):
        b.box((side * 0.3, 0.15, 0), (0.25, 0.3, 0.5), DARK)
        b.box((side * 0.58, 0.8, 0), (0.18, 0.6, 0.2), grey, metallic=0.6)
    return b


def make_ground():
    """A wide grass plane the road sits on; it follows the bike."""
    b = Builder()
    b.box((0, -0.06, -100), (240, 0.1, 400), (0.42, 0.68, 0.29), roughness=1.0)
    return b


def make_dome():
    """A Capsule Corp-style dome: white, a blue window band, a dark ring logo. Front faces +z."""
    b = Builder()
    b.sphere((0, 0, 0), 9, (0.98, 0.97, 0.93), scale=(1, 0.95, 1), top_only=True, rings=24, segments=40, roughness=0.5)
    b.cylinder((0, 1.6, 0), 9.05, 1.4, (0.55, 0.8, 1.0), segments=40, roughness=0.2)
    b.cylinder((0, 5.2, 7.4), 1.3, 0.2, DARK, axis="z", segments=24)
    b.cylinder((0, 5.2, 7.5), 1.0, 0.2, (0.98, 0.97, 0.93), axis="z", segments=24)
    b.box((0, 1.3, 8.8), (3.0, 2.6, 0.4), (0.3, 0.35, 0.45))  # door
    return b


def road_texture():
    """One 20 m stretch of road: asphalt, dashed lanes, red-and-white kerbs at the edges."""
    w, h = 512, 1024
    img = Image.new("RGB", (w, h), (88, 94, 106))
    d = ImageDraw.Draw(img)
    rng = np.random.default_rng(3)
    noise = rng.integers(-10, 10, (h, w, 1))
    arr = np.clip(np.array(img, dtype=np.int16) + noise, 0, 255).astype(np.uint8)
    img = Image.fromarray(arr)
    d = ImageDraw.Draw(img)
    kerb = 22
    for k in range(0, h, 64):
        color = (220, 40, 40) if (k // 64) % 2 == 0 else (240, 240, 240)
        d.rectangle([0, k, kerb, k + 64], fill=color)
        d.rectangle([w - kerb, k, w, k + 64], fill=color)
    for lane in (1, 2):
        x = kerb + (w - 2 * kerb) * lane // 3
        d.rectangle([x - 5, 100, x + 5, 360], fill=(245, 245, 245))
        d.rectangle([x - 5, 612, x + 5, 872], fill=(245, 245, 245))
    return img


def pavement_texture():
    img = Image.new("RGB", (256, 256), (205, 196, 188))
    d = ImageDraw.Draw(img)
    for k in range(0, 256, 64):
        d.line([(0, k), (256, k)], fill=(180, 170, 162), width=3)
        d.line([(k, 0), (k, 256)], fill=(180, 170, 162), width=3)
    return img


def save_road():
    # The road (10 m) plus a kerb's width, 20 m long, from z = 0 back to z = -20.
    b = Builder()
    b.save(OUT / "road.glb", road_texture(), [(-5.4, 5.4, 0, -20, 0, 1, 1, 0, 0.0)])
    pav = Builder()
    pav.save(OUT / "pavement.glb", pavement_texture(), [(-2, 2, 0, -20, 0, 1, 5, 0, 0.02)])


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    OUT.mkdir(parents=True, exist_ok=True)
    report = take_kenney(sys.argv[1])
    for name, builder in [("bike", make_bike()), ("rider", make_rider()), ("dragon_ball", make_dragon_ball()),
                          ("boost", make_boost()), ("capsule_dome", make_dome()), ("robot", make_robot()),
                          ("ground", make_ground())]:
        builder.save(OUT / f"{name}.glb")
    save_road()
    for name, size in sorted(report.items()):
        print(f"{name:16s} {size}")
    total = sum(p.stat().st_size for p in OUT.glob("*.glb"))
    print(f"{len(list(OUT.glob('*.glb')))} models, {total / 1e6:.1f} MB")


if __name__ == "__main__":
    main()
