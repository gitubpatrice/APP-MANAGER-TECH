"""Builds `drawable-nodpi/splash_logo.png`, the logo the splash screen shows, from the launcher artwork.

Why a separate asset: the launcher foreground is a square whose corners are barely rounded, because
the launcher mask rounds it on the home screen. The splash screen applies no such mask — it draws the
icon inside a circle of 2/3 of the icon — so the logo came out clipped round ("too rounded"). This
script rounds the square's corners at 13 % of its side, the rounding of the SMS Tech and Agenda Tech
logos, and `drawable/splash_icon.xml` insets it enough to sit whole inside that circle.

Ported from Agenda Tech. Deterministic: same input, same parameters, same bytes. From the repository
root:

    python tools/make-splash-logo.py
"""
from pathlib import Path

from PIL import Image, ImageDraw

SOURCE = Path("app/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.png")
TARGET = Path("app/src/main/res/drawable-nodpi/splash_logo.png")
CORNER_RADIUS = 0.13  # of the side — the SMS Tech logo measures 13.1 %
SUPERSAMPLE = 4  # the mask is drawn larger then reduced, for smooth (antialiased) corners


def main() -> None:
    artwork = Image.open(SOURCE).convert("RGBA")
    square = artwork.crop(artwork.split()[3].point(lambda v: 255 if v > 128 else 0).getbbox())
    side = square.width
    if square.height != side:
        raise SystemExit(f"expected a square logo, got {square.size}")

    big = side * SUPERSAMPLE
    mask = Image.new("L", (big, big), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, big - 1, big - 1), radius=round(big * CORNER_RADIUS), fill=255)
    mask = mask.resize((side, side), Image.LANCZOS)

    alpha = Image.composite(square.split()[3], Image.new("L", (side, side), 0), mask)
    square.putalpha(alpha)
    TARGET.parent.mkdir(parents=True, exist_ok=True)
    square.save(TARGET, optimize=True)
    print(f"{TARGET} {square.size} radius {CORNER_RADIUS:.0%} of the side")


if __name__ == "__main__":
    main()
