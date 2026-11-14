"""Przygotowanie assetów landing page: webpy screenów, pomniejszone logo, podzbiory fontów.

uruchom:  python tools/landing/make-assets.py
wynik:    docs/screenshots/*.webp, docs/assets/{logo-96,logo-192,favicon}.png,
          docs/assets/fonts/*.woff2 (podzbiór: łacińskie + polskie znaki) + LICENSE-fonts.md

Fonty są tylko odczytywane z gotowych plików googlefonts w innym projekcie
(openoven-public) i podzbierane do realnie używanego zestawu znaków.
"""
from __future__ import annotations

import pathlib

from fontTools.subset import main as subset_main
from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parents[2]
DOCS = ROOT / "docs"
SHOTS = DOCS / "screenshots"
ASSETS = DOCS / "assets"
FONTS_SRC = pathlib.Path("W:/projekty/amica-home/openoven-public/docs/assets/fonts")

# Pliki googlefonts (podzbiory latin + latin-ext): łacińskie znaki są w pierwszym,
# polskie diakryty w drugim. Nie łączymy ich w jeden plik, bo źródła to fonty zmienne,
# a fontTools merge nie umie łączyć tablicy VarStore.
FONTS = [
    "Inter-latin.woff2", "Inter-latin-ext.woff2",
    "SpaceGrotesk-latin.woff2", "SpaceGrotesk-latin-ext.woff2",
    "JetBrainsMono-latin.woff2", "JetBrainsMono-latin-ext.woff2",
]
# Zestaw znaków, których strona naprawdę używa: ASCII + łacina zachodnia + polskie znaki + typografia.
CHARSET = (
    "U+0020-007E,U+00A0-00FF,U+0104-0107,U+0118-011B,U+0131,U+0139,U+0141-0144,"
    "U+015A-0161,U+0164-0167,U+0179-017E,"
    "U+2013-2014,U+2018-201E,U+2022,U+2026,U+2039-203A,U+20AC,U+2190-2193,U+2212,U+2713"
)
LICENSE_FONTS = """# Fonty

Strona używa trzech krojów na własnym hostingu (bez CDN), w podzbiorze obejmującym
znaki łacińskie i polskie oraz typografię cudzysłowów i strzałek.

| Krój | Autor | Licencja | Źródło |
|---|---|---|---|
| Inter | Rasmus Andersson | SIL Open Font License 1.1 | https://rsms.me/inter/ |
| Space Grotesk | Florian Karsten | SIL Open Font License 1.1 | https://fonts.google.com/specimen/Space+Grotesk |
| JetBrains Mono | Philipp Nurullin, Nikolay Avtonomov (JetBrains) | SIL Open Font License 1.1 | https://www.jetbrains.com/mono/ |

Wszystkie trzy kroje są publikowane na licencji SIL OFL 1.1, która zezwala na
wgrywanie, modyfikację (w tym podzbieranie znaków) i redystrybucję plików fontu
w dokumentach i stronach. Pełny tekst licencji: https://openfontlicense.org

Pliki `.woff2` w tym katalogu to podzbiory oryginalnych fontów Google Fonts
(wygenerowane przez `tools/landing/make-assets.py`); oryginalne, pełne pliki
pochodzą z https://fonts.google.com i nie były modyfikowane poza usunięciem
glifów spoza używanego zestawu znaków.
"""


def webp(src: pathlib.Path, dst: pathlib.Path, width: int, quality: int) -> None:
    im = Image.open(src).convert("RGB")
    if im.width > width:
        im = im.resize((width, round(im.height * width / im.width)), Image.LANCZOS)
    im.save(dst, "WEBP", quality=quality, method=6, exact=True)
    print(f"  {dst.relative_to(DOCS)}  {im.width}x{im.height}  {dst.stat().st_size/1024:.0f} kB")


def png(src: pathlib.Path, dst: pathlib.Path, size: int) -> None:
    im = Image.open(src).convert("RGBA").resize((size, size), Image.LANCZOS)
    im.save(dst, "PNG", optimize=True)
    print(f"  {dst.relative_to(DOCS)}  {size}x{size}  {dst.stat().st_size/1024:.1f} kB")


def main() -> None:
    SHOTS.mkdir(parents=True, exist_ok=True)
    ASSETS.mkdir(parents=True, exist_ok=True)
    (DOCS / ".nojekyll").touch()

    print("screenshots → webp (540 px), dashboard także w wersji hero (780 px):")
    for png_file in sorted(SHOTS.glob("*.png")):
        webp(png_file, SHOTS / f"{png_file.stem}.webp", 540, 80)
    dash = SHOTS / "dashboard.png"
    if dash.exists():
        webp(dash, SHOTS / "dashboard-hero.webp", 780, 82)

    print("logo:")
    logo = ASSETS / "logo.png"
    png(logo, ASSETS / "logo-96.png", 96)
    png(logo, ASSETS / "logo-192.png", 192)
    png(logo, ASSETS / "favicon.png", 48)

    print("fonts (podzbiór → woff2):")
    out_dir = ASSETS / "fonts"
    out_dir.mkdir(parents=True, exist_ok=True)
    before = after = 0
    for name in FONTS:
        src = FONTS_SRC / name
        if not src.exists():
            raise SystemExit(f"brak fontu: {src}")
        dst = out_dir / name
        subset_main([
            str(src), f"--output-file={dst}", "--flavor=woff2",
            f"--unicodes={CHARSET}", "--layout-features=*", "--ignore-missing-unicodes",
            "--desubroutinize", "--no-hinting", "--name-IDs=1,2,3,4,6", "--recalc-bounds",
        ])
        b, a = src.stat().st_size, dst.stat().st_size
        before, after = before + b, after + a
        if dst.read_bytes()[:3] != b"wOF":
            raise SystemExit(f"{dst} nie jest plikiem woff2")
        print(f"  {dst.relative_to(DOCS)}  {b/1024:.0f} kB → {a/1024:.1f} kB")
    print(f"  fonty razem: {before/1024:.0f} kB → {after/1024:.0f} kB")
    (out_dir / "LICENSE-fonts.md").write_text(LICENSE_FONTS, encoding="utf-8")

    total = sum(p.stat().st_size for p in DOCS.rglob("*") if p.is_file())
    print(f"\ndocs/ razem: {total/1024/1024:.2f} MB")


if __name__ == "__main__":
    main()
