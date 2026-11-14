"""Wspólne uruchamianie przeglądarki dla narzędzi strony.

Preferuje silnik zainstalowanego Edge/Chrome, żeby nie ciągnąć
~150 MB `playwright install`; gdy nic nie ma — spada w pakowany Chromium.
"""
from __future__ import annotations

CHANNELS = ("msedge", "chrome", "")


def launch(p, **kwargs):
    last = None
    for ch in CHANNELS:
        try:
            return p.chromium.launch(channel=ch, **kwargs) if ch else p.chromium.launch(**kwargs)
        except Exception as exc:  # brak danego silnika — próbuj następnego
            last = exc
    raise SystemExit(f"brak przeglądarki dla Playwright: {last}")
