"""Draw the app icons (a barbell over a rising line) with Pillow. Run: python tools/make_icons.py"""
from pathlib import Path
from PIL import Image, ImageDraw

OUT = Path(__file__).resolve().parent.parent / "icons"
BG, LIME, WHITE = (14, 15, 18), (198, 255, 61), (242, 243, 245)


def icon(size, safe=1.0):
    s = 4  # supersample
    W = size * s
    im = Image.new("RGB", (W, W), BG)
    d = ImageDraw.Draw(im)
    c, k = W / 2, W * 0.5 * safe / 256          # design on a 512 grid, scaled into the safe zone
    P = lambda x, y: (c + (x - 256) * k, c + (y - 256) * k)
    # rising PR line
    d.line([P(96, 380), P(196, 300), P(276, 336), P(416, 176)], fill=LIME, width=int(30 * k), joint="curve")
    d.ellipse([*P(400, 160), *P(432, 192)], fill=LIME)
    # barbell
    d.rounded_rectangle([*P(80, 236), *P(432, 252)], radius=int(8 * k), fill=WHITE)
    for x0, h in ((112, 120), (148, 86), (332, 86), (364, 120)):
        d.rounded_rectangle([*P(x0, 244 - h / 2), *P(x0 + 30, 244 + h / 2)], radius=int(8 * k), fill=WHITE)
    return im.resize((size, size), Image.LANCZOS)


OUT.mkdir(exist_ok=True)
icon(192).save(OUT / "icon-192.png")
icon(512).save(OUT / "icon-512.png")
icon(512, safe=0.78).save(OUT / "icon-maskable-512.png")
print("icons written to", OUT)
