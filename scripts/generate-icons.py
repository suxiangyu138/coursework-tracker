from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "packaging" / "icon-source.jpg"
CROP = (161, 144, 1281, 1264)
MASTER = 1024
RADIUS_RATIO = 0.22
SIZES = (16, 20, 24, 32, 40, 48, 64, 96, 128, 256)
ICO_SIZES = ((16, 16), (20, 20), (24, 24), (32, 32), (40, 40), (48, 48), (64, 64), (128, 128), (256, 256))


def rounded_mask(size, radius):
    scale = 4
    box = size * scale
    mask = Image.new("L", (box, box), 0)
    draw = ImageDraw.Draw(mask)
    draw.rounded_rectangle((0, 0, box - 1, box - 1), radius=radius * scale, fill=255)
    return mask.resize((size, size), Image.LANCZOS)


def main():
    source = Image.open(SOURCE).convert("RGB")
    art = source.crop(CROP).resize((MASTER, MASTER), Image.LANCZOS)
    art = art.convert("RGBA")
    art.putalpha(rounded_mask(MASTER, int(MASTER * RADIUS_RATIO)))

    resource_dir = ROOT / "src" / "main" / "resources" / "icons"
    resource_dir.mkdir(parents=True, exist_ok=True)
    rendered = {}
    for size in SIZES:
        icon = art.resize((size, size), Image.LANCZOS)
        rendered[size] = icon
        icon.save(resource_dir / f"app-icon-{size}.png", optimize=True)

    rendered[256].save(ROOT / "packaging" / "coursework-tracker.ico",
                       format="ICO", sizes=ICO_SIZES, bitmap_format="png")
    rendered[256].save(ROOT / "docs" / "assets" / "coursework-tracker.ico",
                       format="ICO", sizes=ICO_SIZES, bitmap_format="png")
    rendered[256].save(ROOT / "docs" / "assets" / "icon-256.png", optimize=True)
    rendered[64].save(ROOT / "docs" / "assets" / "icon-64.png", optimize=True)


if __name__ == "__main__":
    main()
