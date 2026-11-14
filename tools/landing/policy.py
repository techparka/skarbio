#!/usr/bin/env python3
"""Przenosi treść prawną z privacy-policy.html (korzeń repo) do docs/.

GitHub Pages serwuje tylko katalog docs/, więc polityka musi się tam znaleźć.
Źródłem tekstu pozostaje plik w korzeniu — skrypt bierze z niego <body> 1:1
i osadza w szacie graficznej strony głównej (styles.css + te same tokeny).

Uruchom:  python tools/landing/policy.py
"""
from __future__ import annotations
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
SRC = ROOT / "privacy-policy.html"
OUT = ROOT / "docs" / "privacy-policy.html"

TPL = """<!DOCTYPE html>
<html lang="en" data-theme="dark">
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width, initial-scale=1" />
<title>Skarbio — Privacy policy</title>
<meta name="description" content="Read how Skarbio stores records on your device and how it uses Google ML Kit and the optional Polish VAT registry lookup." />
<link rel="canonical" href="https://techparka.github.io/skarbio/privacy-policy.html" />
<link rel="icon" href="assets/favicon.png" sizes="48x48" />
<meta name="theme-color" content="#080D1A" media="(prefers-color-scheme: dark)" />
<meta name="theme-color" content="#F8FAFF" media="(prefers-color-scheme: light)" />
<link rel="stylesheet" href="assets/fonts/fonts.css" />
<link rel="stylesheet" href="styles.css" />
<script>
  /* Ten sam motyw co na stronie głównej; bez przycisku — strona prawna jest statyczna. */
  try {{
    var t = localStorage.getItem('skarbio.theme');
    if (!t) t = matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'dark';
    document.documentElement.setAttribute('data-theme', t);
  }} catch (e) {{}}
</script>
</head>
<body>
<header class="nav">
  <div class="nav__inner wrap">
    <a class="brand" href="./"><img src="assets/logo-96.png" alt="" width="96" height="96" /><span>Skarbio</span></a>
    <nav class="nav__links" aria-label="Sections">
      <a href="./#demo">Live demo</a>
      <a href="./#features">Features</a>
      <a href="./#privacy">Privacy</a>
      <a href="https://github.com/techparka/skarbio" rel="noopener">GitHub</a>
    </nav>
  </div>
</header>
<main class="section" id="main">
  <div class="wrap">
    <p class="prose__tldr"><b>W skrócie po polsku:</b> historia wydatków jest zapisana na urządzeniu. Skanowanie używa Google ML Kit. Sprawdzanie NIP w rejestrze VAT jest opcjonalne i domyślnie wyłączone. Poniżej znajdziesz wersję angielską i polską.</p>
    <article class="prose">{body}</article>
  </div>
</main>
<footer class="footer">
  <div class="wrap footer__bottom">
    <p class="mono">© 2026 techparka</p>
    <p class="footnote"><a href="./">← Back to Skarbio</a> · <a href="https://github.com/techparka/skarbio" rel="noopener">github.com/techparka/skarbio</a></p>
  </div>
</footer>
</body>
</html>
"""


def demote_second_language(html: str) -> str:
    """W dokumencie ma być dokładnie jeden <h1>.

    Źródło to dwa pełne teksty (EN + PL) obok siebie, każdy ze swoim <h1>.
    Drugi blok zsuwamy o poziom w dół, żeby hierarchia nagłówków została spójna.
    """
    idx = [m.start() for m in re.finditer(r"<h1\b", html)]
    if len(idx) < 2:
        return html
    head, tail = html[:idx[1]], html[idx[1]:]
    tail = (tail.replace("<h3", "<h4").replace("</h3>", "</h4>")
                .replace("<h2", "<h3").replace("</h2>", "</h3>")
                .replace("<h1", '<h2 class="prose__lang"', 1)
                .replace("</h1>", "</h2>", 1))
    return head + tail


def main() -> int:
    src = SRC.read_text(encoding="utf-8")
    body = re.search(r"<body[^>]*>(.*)</body>", src, re.S)
    if not body:
        print("w źródle nie ma <body>", file=sys.stderr)
        return 1
    inner = re.sub(r"<script.*?</script>", "", body.group(1), flags=re.S).strip()
    inner = demote_second_language(inner)
    if "Privacy Policy" not in inner:
        print("treść polityki wygląda na niepełną", file=sys.stderr)
        return 1
    OUT.write_text(TPL.format(body=inner), encoding="utf-8")
    print(f"{OUT}  {OUT.stat().st_size / 1024:.1f} kB (tekst 1:1 z {SRC.name})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
