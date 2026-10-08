"""Renders design/logo/aegida-notification-icon.svg's shapes into the white-on-transparent PNGs Android
uses for a notification's small icon (24 dp at each density). The SVG is a shield with eight petal
cut-outs and a centre disc put back, made with an SVG mask that a vector drawable cannot express.

    python scripts/make-notification-icon.py

Writes platform/src/main/res/drawable-{m,h,xh,xxh,xxxh}dpi/ic_notification.png. The shapes below are copied from
the SVG; if the design changes, update both. Run with -I if the working folder is not trusted.
"""
import math
import pathlib
from PIL import Image, ImageDraw

SCALE = 8  # supersampling for smooth edges
VIEW = 96.0
DENSITIES = {"mdpi": 24, "hdpi": 36, "xhdpi": 48, "xxhdpi": 72, "xxxhdpi": 96}

# Shield path from the SVG: M48 6 L84 19 V46 C84 67 68 83 48 92 C28 83 12 67 12 46 V19 Z
def cubic(p0, p1, p2, p3, steps=40):
    return [
        (
            (1 - t) ** 3 * p0[0] + 3 * (1 - t) ** 2 * t * p1[0] + 3 * (1 - t) * t ** 2 * p2[0] + t ** 3 * p3[0],
            (1 - t) ** 3 * p0[1] + 3 * (1 - t) ** 2 * t * p1[1] + 3 * (1 - t) * t ** 2 * p2[1] + t ** 3 * p3[1],
        )
        for t in (i / steps for i in range(steps + 1))
    ]


SHIELD = (
    [(48, 6), (84, 19), (84, 46)]
    + cubic((84, 46), (84, 67), (68, 83), (48, 92))[1:]
    + cubic((48, 92), (28, 83), (12, 67), (12, 46))[1:]
    + [(12, 19)]
)
PETALS = [(64.0, 47.0), (59.3, 58.3), (48.0, 63.0), (36.7, 58.3), (32.0, 47.0), (36.7, 35.7), (48.0, 31.0), (59.3, 35.7)]
PETAL_RADIUS = 8
CENTRE = (48, 47, 11)


def render(size_px: int) -> Image.Image:
    big = size_px * SCALE
    k = big / VIEW
    mask = Image.new("L", (big, big), 0)
    draw = ImageDraw.Draw(mask)
    draw.polygon([(x * k, y * k) for x, y in SHIELD], fill=255)
    for cx, cy in PETALS:
        draw.ellipse([(cx - PETAL_RADIUS) * k, (cy - PETAL_RADIUS) * k, (cx + PETAL_RADIUS) * k, (cy + PETAL_RADIUS) * k], fill=0)
    cx, cy, r = CENTRE
    draw.ellipse([(cx - r) * k, (cy - r) * k, (cx + r) * k, (cy + r) * k], fill=255)
    alpha = mask.resize((size_px, size_px), Image.LANCZOS)
    icon = Image.new("RGBA", (size_px, size_px), (255, 255, 255, 0))
    icon.putalpha(alpha)
    return icon


if __name__ == "__main__":
    root = pathlib.Path(__file__).resolve().parent.parent / "platform" / "src" / "main" / "res"
    for density, px in DENSITIES.items():
        folder = root / f"drawable-{density}"
        folder.mkdir(parents=True, exist_ok=True)
        render(px).save(folder / "ic_notification.png", optimize=True)
        print(f"wrote {folder.name}/ic_notification.png ({px}x{px})")
