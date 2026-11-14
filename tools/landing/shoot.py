#!/usr/bin/env python3
"""Zrzuty sekcji do oceny wizualnej.

  python tools/landing/shoot.py                    # ciemny + jasny, po polsku
  python tools/landing/shoot.py dark               # tylko ciemny
  python tools/landing/shoot.py pl en              # oba języki, oba motywy
  python tools/landing/shoot.py dark light en      # jasny i ciemny, EN
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from browser import launch  # noqa: E402
from playwright.sync_api import sync_playwright  # noqa: E402

PAGE = "http://127.0.0.1:8123/index.html?full=1"
OUT = Path(__file__).resolve().parent / "shots"
SECTIONS = ["hero", "demo", "features", "screens", "privacy", "stack", "get", "footer"]


def main() -> int:
    argi = sys.argv[1:]
    motywy = [a for a in argi if a in ("dark", "light")] or ["dark", "light"]
    języki = [a for a in argi if a in ("pl", "en")] or ["pl"]
    OUT.mkdir(parents=True, exist_ok=True)
    zróbcie = 0
    with sync_playwright() as p:
        b = launch(p)
        for w, tag in ((1440, "desktop"), (390, "mobile")):
            for theme in motywy:
                for jezyk in języki:
                    przyr = "" if jezyk == "pl" and len(języki) == 1 else f"-{jezyk}"
                    pg = b.new_page(viewport={"width": w, "height": 950})
                    pg.goto(PAGE, wait_until="load")
                    pg.wait_for_timeout(900)
                    pg.evaluate("(t) => document.documentElement.dataset.theme = t", theme)
                    if jezyk == "en":
                        pg.evaluate("() => document.querySelector('#lang-btn').click()")
                        pg.wait_for_timeout(500)
                    pg.evaluate("() => document.querySelectorAll('.reveal').forEach(e => e.classList.add('is-in'))")
                    pg.evaluate("() => document.querySelector('#scan-btn')?.click()")
                    pg.wait_for_timeout(2600)
                    for sel in SECTIONS:
                        el = pg.query_selector(f"section#{sel}, .footer, #{sel}")
                        if not el:
                            continue
                        el.scroll_into_view_if_needed()
                        pg.wait_for_timeout(180)
                        el.screenshot(path=str(OUT / f"{tag}-{theme}-{sel}{przyr}.png"))
                        zróbcie += 1
                    pg.close()
        b.close()
    print(f"→ {OUT}  ({zróbcie} plików)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
