#!/usr/bin/env python3
"""Robi docs/assets/og-cover.png (1200×630) na podstawie inline HTML-a.

Wymaga lokalnego podglądu: python -m http.server 8123 --directory docs
Uruchom:                  python tools/landing/og.py
"""
from __future__ import annotations
import pathlib, sys
sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from browser import launch  # noqa: E402
from playwright.sync_api import sync_playwright

ROOT = pathlib.Path(__file__).resolve().parents[2]
OUT = ROOT / "docs" / "assets" / "og-cover.png"
BASE = "http://127.0.0.1:8123/"

HTML = """<!DOCTYPE html><html><head><meta charset="utf-8"><style>
@import url('{base}assets/fonts/fonts.css');
*{margin:0;box-sizing:border-box}
html,body{width:1200px;height:630px;overflow:hidden}
body{position:relative;background:#080D1A;color:#F1F5FB;
  font-family:'Inter',system-ui,sans-serif;-webkit-font-smoothing:antialiased}
.aur{position:absolute;inset:-25% -10%;filter:blur(8px);
  background:radial-gradient(40% 46% at 12% 8%,rgba(79,70,229,.62),transparent 68%),
             radial-gradient(34% 40% at 82% 4%,rgba(5,150,105,.34),transparent 70%),
             radial-gradient(46% 44% at 66% 104%,rgba(34,211,238,.18),transparent 70%)}
.grid{position:absolute;inset:0;opacity:.5;
  background-image:linear-gradient(rgba(148,163,184,.07) 1px,transparent 1px),
                   linear-gradient(90deg,rgba(148,163,184,.07) 1px,transparent 1px);
  background-size:60px 60px;mask-image:radial-gradient(105% 80% at 46% 6%,#000 10%,transparent 74%)}
.main{position:absolute;inset:0;display:grid;grid-template-columns:1fr 470px;align-items:center;
  padding:0 62px;gap:26px}
h1{font-family:'Space Grotesk',sans-serif;font-size:104px;font-weight:700;letter-spacing:-.045em;line-height:.94}
h1 em{font-style:normal;background:linear-gradient(96deg,#C7D2FE,#6EE7B7 92%);
  -webkit-background-clip:text;background-clip:text;color:transparent}
.p{font-size:26px;line-height:1.42;color:#A9B7CC;margin:24px 0 0;max-width:21ch}
.chips{display:flex;flex-wrap:wrap;gap:11px;margin:30px 0 0;list-style:none;padding:0}
.chips li{font-family:'JetBrains Mono',monospace;font-size:16px;color:#C3CEE0;
  border:1px solid rgba(148,163,184,.3);border-radius:999px;padding:9px 15px;background:rgba(19,27,45,.7)}
.brand{position:absolute;top:44px;left:62px;display:flex;align-items:center;gap:15px}
.brand img{width:50px;height:50px;border-radius:13px}
.brand b{font-family:'Space Grotesk',sans-serif;font-size:31px;font-weight:600;letter-spacing:-.03em}
.gh{position:absolute;bottom:42px;left:62px;font-family:'JetBrains Mono',monospace;
  font-size:19px;color:#818CF8;letter-spacing:.01em}
.phones{position:relative;height:560px}
.ph{position:absolute;width:196px;filter:drop-shadow(0 30px 46px rgba(0,0,0,.6))}
.ph .body{padding:6px;border-radius:30px;background:linear-gradient(160deg,#2A3548,#101623 44%,#232D40 78%,#0B1120);
  box-shadow:0 0 0 1px rgba(255,255,255,.09),0 26px 54px -30% rgba(0,0,0,.85)}
.ph img{width:100%;display:block;border-radius:25px}
.p1{left:64px;top:60px;transform:rotate(-8deg)}
.p2{left:150px;top:6px;width:224px;z-index:3}
.p3{left:252px;top:78px;transform:rotate(8deg)}
.badge{position:absolute;right:6px;bottom:34px;z-index:4;background:#34D399;color:#080D1A;
  font-family:'JetBrains Mono',monospace;font-size:17px;font-weight:700;padding:9px 15px;border-radius:999px}
</style></head><body>
<div class="aur"></div><div class="grid"></div>
<div class="brand"><img src="{base}assets/logo-192.png" alt=""><b>Skarbio</b></div>
<div class="main">
  <div>
    <h1>Snap the<br><em>receipt.</em></h1>
    <p class="p">Offline-first expense tracker for Android. On-device OCR, budgets, bills — all on the phone.</p>
    <ul class="chips"><li>no account</li><li>no cloud</li><li>no analytics</li></ul>
  </div>
  <div class="phones">
    <div class="ph p1"><div class="body"><img src="{base}screenshots/budgets.webp"></div></div>
    <div class="ph p3"><div class="body"><img src="{base}screenshots/reports.webp"></div></div>
    <div class="ph p2"><div class="body"><img src="{base}screenshots/dashboard.webp"></div></div>
    <span class="badge">v1.3.1 · Kotlin</span>
  </div>
</div>
<div class="gh">github.com/techparka/skarbio</div>
</body></html>"""


def main() -> int:
    OUT.parent.mkdir(parents=True, exist_ok=True)
    with sync_playwright() as p:
        b = launch(p)
        page = b.new_page(viewport={"width": 1200, "height": 630}, device_scale_factor=1)
        page.set_content(HTML.replace("{base}", BASE))
        page.wait_for_timeout(1400)
        page.screenshot(path=str(OUT))
        b.close()
    print(f"{OUT}  {OUT.stat().st_size / 1024:.0f} kB")
    return 0


if __name__ == "__main__":
    sys.exit(main())
