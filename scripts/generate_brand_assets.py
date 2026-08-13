#!/usr/bin/env python3
"""Gera os ícones oficiais do SyncDeck a partir da geometria da marca."""

from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont


ROOT = Path(__file__).resolve().parents[1]
BACKGROUND = "#070A09"
LIGHT_GREEN = "#72F5AD"
DARK_GREEN = "#50AA78"
TEXT = "#F5F8F6"


def rounded_mask(size: int) -> Image.Image:
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).rounded_rectangle(
        (0, 0, size - 1, size - 1), radius=round(size * 0.22), fill=255
    )
    return mask


def mark_layer(size: int, transparent: bool = False) -> Image.Image:
    scale = size / 1024
    layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)
    tile = round(210 * scale)
    radius = round(60 * scale)
    positions = (
        (273, 273, LIGHT_GREEN),
        (541, 273, DARK_GREEN),
        (273, 541, DARK_GREEN),
        (541, 541, LIGHT_GREEN),
    )
    for x, y, color in positions:
        left = round(x * scale)
        top = round(y * scale)
        draw.rounded_rectangle(
            (left, top, left + tile, top + tile), radius=radius, fill=color
        )
    return layer.rotate(8, resample=Image.Resampling.BICUBIC, center=(size / 2, size / 2))


def app_icon(size: int = 1024) -> Image.Image:
    image = Image.new("RGB", (size, size), BACKGROUND)

    glow = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    glow_draw = ImageDraw.Draw(glow)
    glow_draw.ellipse(
        (round(size * 0.15), round(size * 0.12), round(size * 0.85), round(size * 0.88)),
        fill=(72, 223, 145, 54),
    )
    glow = glow.filter(ImageFilter.GaussianBlur(round(size * 0.12)))
    image = Image.alpha_composite(image.convert("RGBA"), glow)

    mark = mark_layer(size)
    mark_glow = mark.filter(ImageFilter.GaussianBlur(max(2, round(size * 0.02))))
    mark_glow.putalpha(mark_glow.getchannel("A").point(lambda value: round(value * 0.25)))
    image = Image.alpha_composite(image, mark_glow)
    image = Image.alpha_composite(image, mark)

    mask = rounded_mask(size)
    clipped = Image.new("RGB", (size, size), BACKGROUND)
    clipped.paste(image.convert("RGB"), mask=mask)
    return clipped


def brand_font(size: int) -> ImageFont.FreeTypeFont:
    candidates = (
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
        "/usr/share/fonts/truetype/liberation2/LiberationSans-Bold.ttf",
    )
    for candidate in candidates:
        if Path(candidate).exists():
            return ImageFont.truetype(candidate, size=size)
    return ImageFont.load_default(size=size)


def horizontal_logo() -> Image.Image:
    width, height = 1440, 480
    image = Image.new("RGB", (width, height), BACKGROUND)
    glow = Image.new("RGBA", image.size, (0, 0, 0, 0))
    ImageDraw.Draw(glow).ellipse((-80, -120, 560, 600), fill=(72, 223, 145, 44))
    image = Image.alpha_composite(image.convert("RGBA"), glow.filter(ImageFilter.GaussianBlur(110)))

    mark = mark_layer(360)
    image.alpha_composite(mark, (40, 60))
    draw = ImageDraw.Draw(image)
    font = brand_font(148)
    draw.text((420, 150), "SyncDeck", fill=TEXT, font=font, anchor="lm", stroke_width=0)
    return image.convert("RGB")


def social_preview() -> Image.Image:
    width, height = 1200, 630
    image = Image.new("RGB", (width, height), BACKGROUND)
    glow = Image.new("RGBA", image.size, (0, 0, 0, 0))
    ImageDraw.Draw(glow).ellipse((-160, -140, 720, 760), fill=(72, 223, 145, 52))
    image = Image.alpha_composite(image.convert("RGBA"), glow.filter(ImageFilter.GaussianBlur(145)))

    mark = mark_layer(310)
    image.alpha_composite(mark, (60, 160))
    draw = ImageDraw.Draw(image)
    draw.text((405, 260), "SyncDeck", fill=TEXT, font=brand_font(112), anchor="lm")
    draw.text(
        (410, 360),
        "Seu PC, a um toque de distância.",
        fill="#AAB5AE",
        font=brand_font(38),
        anchor="lm",
    )
    return image.convert("RGB")


def save_png(image: Image.Image, path: Path, size: int) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    resized = image.resize((size, size), Image.Resampling.LANCZOS).convert("RGB")
    resized.save(path, format="PNG", optimize=True)


def main() -> None:
    master = app_icon()
    brand_dir = ROOT / "brand"
    brand_dir.mkdir(parents=True, exist_ok=True)
    master.save(brand_dir / "syncdeck-mark-1024.png", format="PNG", optimize=True)
    horizontal_logo().save(brand_dir / "syncdeck-logo.png", format="PNG", optimize=True)
    social_preview().save(brand_dir / "syncdeck-social-preview.png", format="PNG", optimize=True)

    save_png(master, ROOT / "store-assets" / "play-icon-512.png", 512)

    ios_icons = ROOT / "ios-app" / "SyncDeck" / "Assets.xcassets" / "AppIcon.appiconset"
    for size in (40, 58, 60, 80, 87, 120, 180, 1024):
        save_png(master, ios_icons / f"icon-{size}.png", size)

    windows_icon = ROOT / "windows-agent" / "assets" / "syncdeck.ico"
    windows_icon.parent.mkdir(parents=True, exist_ok=True)
    master.save(
        windows_icon,
        format="ICO",
        sizes=[(16, 16), (20, 20), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)],
    )

    print("Ícones oficiais do SyncDeck gerados com sucesso.")


if __name__ == "__main__":
    main()
