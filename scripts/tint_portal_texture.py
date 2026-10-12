#!/usr/bin/env python3
"""Recolour a texture to one tint, keeping its light/dark pattern and its alpha.

Usage: python3 -I scripts/tint_portal_texture.py <in.png> <out.png> <hex-tint>
  e.g. python3 -I scripts/tint_portal_texture.py nether_portal.png portal_material.png '#0a4646'

For each pixel (R, G, B, A):
  L   = (0.2126 R + 0.7152 G + 0.0722 B) / 255    relative luminance (Rec. 709 weights)
  n   = L / max(L over every pixel of the image)   so the brightest pixel gets n = 1
  out = (round(tint_r * n), round(tint_g * n), round(tint_b * n), A)
The brightest pixel comes out exactly as the tint and alpha is copied through untouched. An
animation strip needs nothing special: the maximum is taken over all of its frames at once.
"""

import sys

from PIL import Image


def parse_tint(text):
    digits = text.removeprefix("#")
    if len(digits) != 6:
        sys.exit(f"tint must be six hex digits, got {text!r}")
    return tuple(int(digits[i:i + 2], 16) for i in (0, 2, 4))


def luminance(r, g, b):
    return (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255


def main():
    if len(sys.argv) != 4:
        sys.exit("usage: tint_portal_texture.py <in.png> <out.png> <hex-tint>")
    src, dst, tint = sys.argv[1], sys.argv[2], parse_tint(sys.argv[3])
    image = Image.open(src).convert("RGBA")
    data = bytearray(image.tobytes())
    lums = [luminance(*data[i:i + 3]) for i in range(0, len(data), 4)]
    peak = max(lums)
    if peak == 0:
        sys.exit("input is entirely black; nothing to normalise against")
    for p, lum in enumerate(lums):
        n = lum / peak
        data[4 * p:4 * p + 3] = bytes(round(c * n) for c in tint)
    Image.frombytes("RGBA", image.size, bytes(data)).save(dst)


if __name__ == "__main__":
    main()
