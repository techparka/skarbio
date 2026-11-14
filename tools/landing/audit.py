#!/usr/bin/env python3
"""Kontrola jakości strony lądowania Skarbio (Playwright + kontrola znaczników).

Uruchom:  python tools/landing/audit.py           # raport tekstowy
          python tools/landing/audit.py --shots  # + zrzuty do tools/landing/shots/

Progi:
  * kontrast WCAG 2.x AA: 4,5:1 tekst zwykły, 3,0:1 duży (>=24px lub >=18,66px bold)
    — kolor tekstu sprawdzany względem NAJGORSZEGO tła motywu (worst case),
      a komponenty na własnych tłach (papilot, kod, przyciski) osobnymi parami
  * pole trafienia >= 24x24 CSS px (WCAG 2.2 SC 2.5.8 Target SizeMinimum)
  * brak przewijania w poziomie przy 360 / 390 / 768 / 1440 px
  * ramka telefonu i zrzuty PIONOWE o proporcji 0,45 (1080x2400) — bez „kinowego” telefonu
  * jeden <h1>, hierarchia nagłówków, alt, JSON-LD, prefers-reduced-motion
  * zero żądań na zewnątrz (bez CDN / Google Fonts) i zero błędów konsoli
  * gęstość tekstu na sekcję — strona ma być do skanowania, nie do czytania
"""
from __future__ import annotations
import argparse, json, pathlib, re, sys
from urllib.parse import urlsplit

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from browser import launch  # noqa: E402
from playwright.sync_api import sync_playwright

ROOT = pathlib.Path(__file__).resolve().parents[2]
DOCS = ROOT / "docs"
OUT = pathlib.Path(__file__).resolve().parent / "shots"
PAGE = "http://127.0.0.1:8123/index.html"
GOŚĆ = "127.0.0.1"                       # nadpisuje ją --url (badanie wersji opublikowanej)
WIDTHS = (360, 390, 768, 820, 1024, 1440)
PHONE_RATIO = 1080 / 2400  # 0,45

fails: list[str] = []
oks: list[str] = []
notes: list[str] = []
ok, fail, note = oks.append, fails.append, notes.append


def chan(v: float) -> float:
    v /= 255
    return v / 12.92 if v <= 0.03928 else ((v + 0.055) / 1.055) ** 2.4


def lum(color: str) -> float | None:
    h = color.strip()
    m = re.match(r"rgba?\(([^)]+)\)", h)
    if m:
        p = [x.strip() for x in m.group(1).split(",")]
        if len(p) == 4 and float(p[3]) == 0:
            return None
        r, g, b = (float(p[i]) for i in range(3))
    elif h.startswith("#"):
        h = h[1:]
        if len(h) == 3:
            h = "".join(c * 2 for c in h)
        if len(h) != 6:
            return None
        r, g, b = (int(h[i:i + 2], 16) for i in (0, 2, 4))
    else:
        return None
    return 0.2126 * chan(r) + 0.7152 * chan(g) + 0.0722 * chan(b)


def contrast(fg: str, bg: str) -> float | None:
    a, b = lum(fg), lum(bg)
    if a is None or b is None:
        return None
    hi, lo = max(a, b), min(a, b)
    return (hi + 0.05) / (lo + 0.05)


def need(size: float, weight: str | int) -> float:
    big = size >= 24 or (size >= 18.66 and int(weight or 400) >= 700)
    return 3.0 if big else 4.5


# pary autorskie: tekst na własnym tle komponentu (nie na tle motywu)
PAPER = [("#101828", "#F8FAFC", "papilot: treść paragonu"),
         ("#4B5565", "#F8FAFC", "papilot: dane nadawcy"),
         ("#667085", "#F8FAFC", "papilot: stopka paragonu")]
CODE = [("#E6EDFA", "#0A1120", "kod: wynik"), ("#C6D2E6", "#0A1120", "kod: opis"),
        ("#7C8BA8", "#0A1120", "kod: komentarz"), ("#6EE7B7", "#0A1120", "kod: żródło"),
        ("#E6EDFA", "#0C1424", "kod (jasny): wynik"), ("#7C8BA8", "#0C1424", "kod (jasny): podpis")]
BUTTON = [("#170F3C", "#A5B4FC", "przycisk główny (ciemny)"),
          ("#FFFFFF", "#4338CA", "przycisk główny (jasny)"),
          ("#FFFFFF", "#4338CA", "zakładka aktywna (jasny)"),
          ("#170F3C", "#A5B4FC", "zakładka aktywna (ciemny)")]

SELF_BG = ".bg, .paper, .stack__code, .code, .ui, .btn, .tab, .ui__chip, .pop__bar, .bars"


def contrast_rows(page) -> list[dict]:
    """Elementy, których tekst leży bezpośrednio na tle wyznaczonym przez motyw."""
    return page.evaluate("""(SELF) => {
      const out = [];
      const sel = 'h1,h2,h3,h4,p,li,a,span,b,strong,i,dd,dt,summary,code,button,label';
      for (const el of document.querySelectorAll(sel)) {
        if (el.closest(SELF) || el.closest('[aria-hidden="true"]') || el.closest('.bg')) continue;
        const own = [...el.childNodes].some(n => n.nodeType === 3 && n.textContent.trim());
        if (!own) continue;
        const cs = getComputedStyle(el);
        if (cs.visibility === 'hidden' || +cs.opacity === 0) continue;
        const r = el.getBoundingClientRect();
        if (!r.width || !r.height) continue;
        out.push({ tag: el.tagName.toLowerCase(),
                   cls: String(el.className || '').split(' ')[0].slice(0, 30),
                   color: cs.color, size: parseFloat(cs.fontSize), weight: cs.fontWeight,
                   text: el.innerText.trim().slice(0, 40) });
      }
      return out;
    }""", SELF_BG)


def check_contrast(page, theme: str) -> None:
    bgs = page.evaluate("""() => {const cs = getComputedStyle(document.documentElement);
      return ['--bg','--surface-solid','--bg-2'].map(v => cs.getPropertyValue(v).trim()).filter(Boolean);}""")
    bad, checked = [], 0
    for r in contrast_rows(page):
        limit = need(r["size"], r["weight"])
        worst, worst_bg = None, None
        for bg in bgs:
            c = contrast(r["color"], bg)
            if c is not None and (worst is None or c < worst):
                worst, worst_bg = c, bg
        checked += 1
        if worst is not None and worst < limit:
            bad.append(f'{r["tag"]}.{r["cls"]} „{r["text"]}” {worst:.2f}:1 (trzeba {limit}:1) tło {worst_bg}')
    if bad:
        fail(f"[{theme}] kontrast AA — {len(bad)} z {checked}:\n      " + "\n      ".join(bad[:14]))
    else:
        ok(f"[{theme}] kontrast AA — {checked} elementów na tle motywu OK (tła: {', '.join(bgs)})")


def check_pair(pairs, label) -> None:
    bad = []
    for fg, bg, what in pairs:
        c = contrast(fg, bg)
        if c is None:
            continue
        if c < need(13, 400):
            bad.append(f"{what}: {fg} na {bg} = {c:.2f}:1")
    (ok if not bad else fail)(f"{label}: {len(pairs)} par dozwolone" if not bad else f"{label}: " + "; ".join(bad))


# "co na siebie nachodzi" — dwa sygnały: teksty leżące na sobie i treść ucięta przez własne overflow
LAYOUT_JS = """() => {
  const SKIP = '.bg, .rail, .orbit, .pop, .ui__chip, .lightbox, dialog, .toast, .paper__scan, script, noscript';
  const inFixed = e => { for (let a = e; a; a = a.parentElement) if (getComputedStyle(a).position === 'fixed') return true; return false; };
  const skip = e => e.closest(SKIP) || inFixed(e) || e.getAttribute('aria-hidden') === 'true';
  const sel = e => e.tagName.toLowerCase() + '.' + String(e.className || '').split(' ')[0].slice(0, 18);
  const txt = e => (e.innerText || '').trim().replace(/\\s+/g, ' ');
  const box = e => e.getBoundingClientRect();
  const inr = (a, b) => { const w = Math.min(a.right, b.right) - Math.max(a.left, b.left),
    h = Math.min(a.bottom, b.bottom) - Math.max(a.top, b.top); return w > 0 && h > 0 ? w * h : 0; };

  const nodes = [...document.querySelectorAll('h1,h2,h3,h4,p,li,dd,dt,b,span,a,button,summary,code')]
    .filter(e => !skip(e) && txt(e) && box(e).width > 2 && box(e).height > 2);

  const hits = [];
  for (let i = 0; i < nodes.length; i++) {
    const ra = box(nodes[i]);
    for (let j = i + 1; j < nodes.length; j++) {
      const rb = box(nodes[j]);
      if (nodes[i].contains(nodes[j]) || nodes[j].contains(nodes[i])) continue;
      const ar = Math.min(ra.width * ra.height, rb.width * rb.height);
      if (!ar || inr(ra, rb) / ar < 0.45) continue;
      hits.push(sel(nodes[i]) + ' „' + txt(nodes[i]).slice(0, 18) + '” ↔ ' + sel(nodes[j]) + ' „' + txt(nodes[j]).slice(0, 18) + '”');
    }
  }

  const CLIPPERS = '.card, .demo__panel, .wires, .paper__body, .legend, .field, .qa, .btn, .badge, .step, .tabs, .feed li, .footer';
  const cut = [];
  for (const c of document.querySelectorAll(CLIPPERS)) {
    if (skip(c) || !/hidden|clip/.test(getComputedStyle(c).overflowX + ' ' + getComputedStyle(c).overflowY)) continue;
    const rc = box(c);
    for (const el of c.querySelectorAll('p,li,h3,span,b,code')) {
      if (el.closest('.rail, .code, .paper__body') || skip(el) || !txt(el)) continue;
      const r = box(el);
      if (!r.width || !r.height) continue;
      if (r.bottom > rc.bottom + 3 || r.right > rc.right + 3) {
        cut.push(sel(c) + ' ucina ' + sel(el) + ' „' + txt(el).slice(0, 18) + '”');
        break;
      }
    }
  }

  // 3. tekst przykryty nieprzezroczystym elementem, który nie jest jego przodkiem
  const opaque = e => {
    const cs = getComputedStyle(e);
    if (+cs.opacity < 0.08) return false;
    const m = /rgba?\((\d+), (\d+), (\d+)(?:, ([\d.]+))?\)/.exec(cs.backgroundColor);
    if (m && (m[4] === undefined || +m[4] > 0.25)) return true;
    return /url/.test(cs.backgroundImage) || ['IMG', 'SVG'].includes(e.tagName);
  };
  const covered = [];
  for (const e of nodes) {
    const r = box(e);
    const cx = r.left + r.width / 2, cy = r.top + Math.min(r.height / 2, 12);
    if (cx < 0 || cy < 0 || cx > innerWidth - 1 || cy > innerHeight - 1) continue;
    const top = document.elementsFromPoint(cx, cy).filter(x => !skip(x));
    const hit = top.find(x => x !== e);
    if (!hit || hit.contains(e) || e.contains(hit) || !opaque(hit)) continue;
    covered.push(sel(e) + ' „' + txt(e).slice(0, 18) + '” pod ' + sel(hit));
    if (covered.length > 5) break;
  }
  return { hits: hits.slice(0, 8), cut: cut.slice(0, 8), covered: covered.slice(0, 6) };
}"""


def check_nav(page, label: str) -> None:
    """Osobno: pasek nawigacji (jest fixed, więc Layout go nie widzi)."""
    rows = page.evaluate("""() => {
      const inner = document.querySelector('.nav__inner');
      const ir = inner.getBoundingClientRect();
      const kids = [...inner.querySelectorAll('a, button')].filter(e => e.getBoundingClientRect().width && getComputedStyle(e).display !== 'none');
      const hits = [], out = [];
      for (let i = 0; i < kids.length; i++) {
        const r = kids[i].getBoundingClientRect();
        if (r.right > innerWidth + 2 || r.left < -2) out.push((kids[i].innerText || kids[i].getAttribute('aria-label') || '').trim().slice(0, 14));
        for (let j = i + 1; j < kids.length; j++) {
          const A = kids[i], B = kids[j];
          if (A.contains(B) || B.contains(A)) continue;
          const ra = A.getBoundingClientRect(), rb = B.getBoundingClientRect();
          if (Math.min(ra.right, rb.right) - Math.max(ra.left, rb.left) > 3 &&
              Math.min(ra.bottom, rb.bottom) - Math.max(ra.top, rb.top) > 3)
            hits.push((A.innerText || '').trim().slice(0, 12) + ' ↔ ' + (B.innerText || B.getAttribute('aria-label') || '').trim().slice(0, 12));
        }
      }
      return { hits, out };
    }""")
    msgs = [f"elemety nachodzą: {m}" for m in rows["hits"]] + [f"wyskakuje za wide: {m}" for m in rows["out"]]
    (ok if not msgs else fail)(f"{label}: pasek nawigacji w swoim miejscu" if not msgs
                               else f"{label}: pasek nawigacji rozjechany\n      " + "\n      ".join(msgs))


def check_layout(page, label: str) -> None:
    """Przejrzysta strona kawałek po kawałku — w którym miejscu tekst na siebie wpada."""
    height = page.evaluate("() => document.documentElement.scrollHeight")
    step = page.evaluate("() => innerHeight")
    msgs, seen, y = [], set(), 0
    while y < height:
        page.evaluate("(y) => scrollTo({ top: y, behavior: 'instant' })", y)
        page.wait_for_timeout(320)
        rows = page.evaluate(LAYOUT_JS)
        for key, pre in (("hits", "teksty na siebie"), ("cut", "przycięte"), ("covered", "przykryte")):
            for m in rows[key]:
                if m not in seen:
                    seen.add(m)
                    msgs.append(f"{pre}: {m}")
        y += int(step * 0.8)
    page.evaluate("() => scrollTo({ top: 0, behavior: 'instant' })")
    (ok if not msgs else fail)(
        f"{label}: nic na siebie nie nachodzi" if not msgs
        else f"{label}: rozjechany układ\n      " + "\n      ".join(msgs))


def check_policy(b) -> None:
    """docs/privacy-policy.html: generowany plik, więc sprawdzamy go razem z źródłem."""
    src_file, out_file = ROOT / "privacy-policy.html", DOCS / "privacy-policy.html"
    if not out_file.exists():
        fail("brak docs/privacy-policy.html — generuje go `python tools/landing/policy.py`")
        return

    def visible(text: str, pick) -> str:
        text = re.sub(r"<(script|style)\b.*?</\1>", " ", text, flags=re.S | re.I)
        body = re.search(r"<body[^>]*>(.*)</body>", text, re.S)
        if body:
            text = body.group(1)
        if pick:
            m = re.search(r'<article class="prose">(.*)</article>', text, re.S)
            text = m.group(1) if m else ""
        return re.sub(r"\s+", " ", re.sub(r"<[^>]+>", " ", text)).strip()

    src = src_file.read_text(encoding="utf-8")
    out = out_file.read_text(encoding="utf-8")
    a, doc = visible(src, False), visible(out, True)
    if not doc:
        fail("w docs/privacy-policy.html nie ma treść w .prose")
    elif a and a != doc:
        fail("docs/privacy-policy.html rozjechał się ze źródłem w korzeniu — uruchom `python tools/landing/policy.py`")
    else:
        ok("docs/privacy-policy.html = 1:1 z privacy-policy.html z korzenia")

    pol = b.new_context(viewport={"width": 1440, "height": 1000})
    p = pol.new_page()
    req = []
    p.on("request", lambda r: req.append(r.url))
    p.goto(f"{PAGE.rsplit('/', 1)[0]}/privacy-policy.html", wait_until="networkidle")
    p.wait_for_timeout(500)

    hs = p.evaluate("() => [...document.querySelectorAll('h1,h2,h3,h4,h5,h6')].map(h => +h.tagName[1])")
    skok = any((hs[i] or 0) - (hs[i - 1] or 0) > 1 for i in range(1, len(hs))) if hs else False
    (ok if hs and hs[0] == 1 and sum(1 for h in hs if h == 1) == 1 and not skok else fail)(
        f"polityka: jeden <h1>, {len(hs)} nagłówków bez skoków" if hs and hs.count(1) == 1 and not skok
        else f"polityka: zła hierarchia nagłówków {hs[:8]}…")

    for theme in (("dark", "ciemny"), ("light", "jasny")):
        p.evaluate("(t) => document.documentElement.dataset.theme = t", theme[0])
        p.wait_for_timeout(250)
        check_contrast(p, theme[1])

    linki = p.evaluate("""() => [...document.querySelectorAll('main a, .footer a')].map(a => a.getAttribute('href'))""")
    zle = []
    for href in linki:
        if not href or href.startswith(("http", "mailto", "#")):
            continue
        r = p.request.get(p.url.rsplit("/", 1)[0] + "/" + href)
        if r.status >= 400:
            zle.append(f"{href} → {r.status}")
    (ok if not zle else fail)(f"polityka: {len([h for h in linki if not str(h).startswith('http')])} lokalnych linków działa"
                              if not zle else f"polityka: martwe linki {zle}")

    obce = sorted({u.split("/")[2] for u in req if GOŚĆ not in u})
    (ok if not obce else fail)("polityka: zero żądań na zewnątrz" if not obce else f"polityka: żądania na {obce}")

    ov = p.evaluate("() => document.documentElement.scrollWidth - document.documentElement.clientWidth")
    p.set_viewport_size({"width": 390, "height": 844})
    p.wait_for_timeout(300)
    ov_m = p.evaluate("() => document.documentElement.scrollWidth - document.documentElement.clientWidth")
    (ok if ov <= 1 and ov_m <= 1 else fail)("polityka: bez poziomego przewijania (1440 i 390)"
                                             if ov <= 1 and ov_m <= 1 else f"polityka: overflow {ov}/{ov_m}px")
    pol.close()


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--shots", action="store_true")
    ap.add_argument("--url", help="badaj wersję opublikowaną, np. https://techparka.github.io/skarbio/")
    args = ap.parse_args()

    global PAGE, GOŚĆ
    if args.url:
        PAGE = args.url if args.url.endswith(".html") else args.url.rstrip("/") + "/index.html"
        GOŚĆ = urlsplit(PAGE).netloc or "127.0.0.1"
        print(f"badam: {PAGE}")

    html = (DOCS / "index.html").read_text(encoding="utf-8")
    css = (DOCS / "styles.css").read_text(encoding="utf-8")
    js = (DOCS / "app.js").read_text(encoding="utf-8")

    # ── 1. Znaczniki ────────────────────────────────────────────────────
    if len(re.findall(r"<h1[^>]*>", html)) == 1:
        ok("dokładnie jeden <h1>")
    else:
        fail("mało lub za dużo <h1>")

    lv = [int(n) for n in re.findall(r"<h([1-6])", html)]
    skips = {f"h{lv[i]}→h{lv[i + 1]}" for i in range(len(lv) - 1) if lv[i + 1] - lv[i] > 1}
    (ok if not skips else fail)(f"hierarchia nagłówków bez przeskoków" if not skips else f"przeskok: {', '.join(sorted(skips))}")

    imgs = re.findall(r"<img [^>]*>", html)
    noalt = [i for i in imgs if "alt=" not in i]
    (ok if not noalt else fail)(f"{len(imgs)} <img>, każdy z atrybutem alt" if not noalt else f"{len(noalt)} <img> bez alt")

    for probe, label in (('rel="canonical"', "canonical"), ('property="og:image"', "og:image"),
                         ('property="og:title"', "og:title"), ('name="description"', "meta description"),
                         ('name="theme-color"', "theme-color"), ('lang="en"', "język domyślny dokumentu")):
        (ok if probe in html else fail)(f"{label} w <head>")

    ld = re.search(r'<script type="application/ld\+json">(.*?)</script>', html, re.S)
    if not ld:
        fail("brak JSON-LD")
    else:
        try:
            data = json.loads(ld.group(1))
            assert data.get("@type") == "SoftwareApplication", "@type != SoftwareApplication"
            missing = [f for f in ("name", "operatingSystem", "codeRepository", "featureList") if f not in data]
            assert not missing, f"brak pól {missing}"
            ok("JSON-LD SoftwareApplication ma name/operatingSystem/codeRepository/featureList")
        except Exception as exc:
            fail(f"JSON-LD: {exc}")

    for probe in ("prefers-reduced-motion", "prefers-color-scheme", ":focus-visible", "clamp(", "color-mix(", "text-wrap: balance"):
        (ok if probe in css else fail)(f"w CSS jest {probe}")

    og = DOCS / "assets" / "og-cover.png"
    if not og.exists():
        fail("brak docs/assets/og-cover.png — generuje go `python tools/landing/og.py`")
    elif 'content="https://techparka.github.io/skarbio/assets/og-cover.png"' not in html:
        fail("og:image nie jest bezwzględnym URL-em do assets/og-cover.png")
    else:
        ok("og:image = bezwzględny URL do assets/og-cover.png")

    # gęstość liczymy z widocznego prozy w <main>: bez FAQ (zwinięte <details>),
    # bez bloków kodu/terminala i bez komentarzy — to miasto „ściany tekstu”, nie objętość pliku
    main = re.search(r"<main\b.*?</main>", html, re.S)
    body = main.group(0) if main else html
    body = re.sub(r"<!--.*?-->", " ", body, flags=re.S)
    body = re.sub(r"<details\b.*?</details>", " ", body, flags=re.S)
    body = re.sub(r"<(script|style|pre|code|svg)\b.*?</\1>", " ", body, flags=re.S | re.I)
    words = len(re.findall(r"[\w’'%.,/-]+", re.sub(r"<[^>]+>", " ", body)))
    secs = len(re.findall(r"<section\b", html)) or 1
    per = words / secs
    (ok if per <= 100 else fail)(f"gęstość tekstu {per:.0f} słów/sekcję ({words} słów, {secs} sekcji), limit 100")

    if "techparka.github.io" not in html:
        fail("w znacznikach nie ma docelowej domeny GitHub Pages")
    else:
        ok("kanoniczne URL-e i meta mówią techparka.github.io")

    if re.search(r"(https?:)?//(fonts\.googleapis|fonts\.gstatic|cdn\.|unpkg|jsdelivr)", html + css + js):
        fail("ktoś wciągnął zewnętrzny CDN/fonty")
    else:
        ok("bez zewnętrznych CDN i Google Fonts")

    # ── 2. Przeglądarka ─────────────────────────────────────────────────
    with sync_playwright() as p:
        b = launch(p)
        ctx = b.new_context(viewport={"width": 1440, "height": 1000})
        page = ctx.new_page()
        external, console = [], []
        page.on("console", lambda m: console.append(f"{m.type}: {m.text}") if m.type in ("error", "warning") else None)
        page.on("pageerror", lambda e: console.append(f"pageerror: {e}"))
        page.on("request", lambda r: external.append(r.url)
                if urlsplit(r.url).netloc and GOŚĆ not in urlsplit(r.url).netloc else None)

        page.goto(PAGE, wait_until="networkidle")
        page.wait_for_timeout(1000)

        (ok if not external else fail)("zero żądań na zewnątrz strony"
                                      if not external else f"{len(external)} żądań na zewnątrz: {sorted(set(external))[:4]}")
        (ok if not console else fail)("konsola bez błędów i ostrzeżeń"
                                     if not console else "konsola: " + "; ".join(sorted(set(console))[:5]))

        status = page.evaluate("() => document.fonts.status")
        (ok if status == "loaded" else fail)(f"stan fontów: {status}")
        fams = sorted(set(page.evaluate("() => [...document.fonts].map(f => f.family)")))
        ok("wczytane fonty: " + ", ".join(fams))

        check_pair(PAPER, "papilot (paragon)")
        check_pair(CODE, "blok kodu")
        check_pair(BUTTON, "przyciski i zakładki")

        check_contrast(page, "ciemny")
        page.evaluate("() => document.documentElement.dataset.theme = 'light'")
        page.wait_for_timeout(250)
        check_contrast(page, "jasny")
        page.evaluate("() => document.documentElement.dataset.theme = 'dark'")

        for w in WIDTHS:
            page.set_viewport_size({"width": w, "height": 900})
            page.wait_for_timeout(420)
            ov = page.evaluate("() => document.documentElement.scrollWidth - document.documentElement.clientWidth")
            wide = page.evaluate("""() => {
              const lim = document.documentElement.clientWidth;
              const clipped = e => { for (let a = e.parentElement; a; a = a.parentElement) {
                const cs = getComputedStyle(a);
                if (/(hidden|clip|auto|scroll)/.test(cs.overflowX + ' ' + cs.overflowY)) return true; }
                return false; };
              return [...document.querySelectorAll('body *')].filter(e => {
                if (e.closest('.bg') || e.closest('.rail') || getComputedStyle(e).position === 'fixed') return false;
                const r = e.getBoundingClientRect();
                if (!r.width || !r.height) return false;
                return (r.right > lim + 2 || r.left < -2) && !clipped(e);
              }).slice(0, 4).map(e => e.tagName.toLowerCase() + '.' + String(e.className || '').split(' ')[0].slice(0, 24));
            }""")
            (ok if ov <= 1 and not wide else fail)(
                f"@{w}px: brak poziomego przewijania" if ov <= 1 and not wide else f"@{w}px: overflow {ov}px — {wide}")

        page.set_viewport_size({"width": 1440, "height": 1000})
        page.wait_for_timeout(350)
        frames = page.evaluate("() => [...document.querySelectorAll('.device__body')].map(e => {"
                               "const r = e.getBoundingClientRect(); return +(r.width / r.height).toFixed(3);})")
        zle = [f for f in frames if not 0.42 <= f <= 0.50]
        (ok if frames and not zle else fail)(
            f"{len(frames)} ramek telefonu w pionie, proporcja {frames[0]:.3f}" if frames and not zle
            else f"ramki poza zakresem 0,42–0,50 (jest {frames})")

        shots = page.evaluate("""() => [...document.querySelectorAll('img.device__screen')].map(i => ({
            src: i.currentSrc.split('/').pop(), nat: i.naturalWidth + 'x' + i.naturalHeight,
            r: +(i.naturalWidth / i.naturalHeight).toFixed(3),
            stretched: Math.abs((i.getBoundingClientRect().width / i.getBoundingClientRect().height) -
                                 (i.naturalWidth / i.naturalHeight)) > 0.02,
            alt: !!i.alt}))""")
        zle2 = [s for s in shots if abs(s["r"] - PHONE_RATIO) > 0.01 or s["stretched"] or not s["alt"]]
        (ok if shots and not zle2 else fail)(
            f"{len(shots)} zrzutów 0,45:1 nierozciągniętych, z alt" if shots and not zle2 else f"źle zrzuty: {zle2}")

        page.set_viewport_size({"width": 1440, "height": 1000})
        page.wait_for_timeout(300)
        check_layout(page, "układ 1440 px")
        check_nav(page, "narazja 1440 px")
        page.evaluate("() => document.querySelector('#lang-btn').click()")
        page.wait_for_timeout(450)
        check_layout(page, "układ 1440 px EN")
        page.set_viewport_size({"width": 390, "height": 844})
        page.wait_for_timeout(450)
        check_layout(page, "układ 390 px EN")
        check_nav(page, "narazja 390 px")
        page.evaluate("() => document.querySelector('#menu-btn').click()")
        page.wait_for_timeout(450)
        check_nav(page, "narazja 390 px z otwartym menu")
        page.evaluate("() => document.querySelector('#menu-btn').click()")
        page.wait_for_timeout(350)
        page.evaluate("() => document.querySelector('#lang-btn').click()")
        page.wait_for_timeout(450)
        check_layout(page, "układ 390 px PL")

        # ── stany po interakcji na wąskim ekranie: zakładka faktury, skan, lightbox ──
        page.evaluate("() => document.querySelector('#tab-invoice').click()")
        page.wait_for_timeout(300)
        page.evaluate("() => { scrollTo({ top: document.querySelector('#demo').offsetTop, behavior: 'instant' }); document.querySelector('#scan-btn').click(); }")
        page.wait_for_timeout(900)
        check_layout(page, "układ 390 px w trakcie skanu")
        page.wait_for_timeout(2400)
        check_layout(page, "układ 390 px po zapisie")

        page.evaluate("() => document.querySelector('.rail .shot').click()")
        page.wait_for_timeout(1200)
        lb = page.evaluate("""() => { const d = document.querySelector('#lightbox'); if (!d.open) return null;
            const r = d.getBoundingClientRect(), im = document.querySelector('#lb-img').getBoundingClientRect();
            return { w: Math.round(r.width), h: Math.round(r.height), vw: innerWidth, vh: innerHeight,
                     iw: Math.round(im.width), ih: Math.round(im.height),
                     ov: document.documentElement.scrollWidth - document.documentElement.clientWidth }; }""")
        if not lb:
            fail("lightbox nie chce się otworzyć")
        else:
            prop = lb["iw"] / lb["ih"] if lb["ih"] else 0
            zle = []
            if lb["w"] > lb["vw"] + 1 or lb["h"] > lb["vh"] + 1:
                zle.append(f"okno {lb['w']}x{lb['h']} przy {lb['vw']}x{lb['vh']}")
            if not 0.43 <= prop <= 0.47:
                zle.append(f"obraz {prop:.3f} zamiast {PHONE_RATIO:.3f}")
            if lb["ov"] > 1:
                zle.append(f"poziomy overflow {lb['ov']}px")
            (ok if not zle else fail)(f"lightbox 390 px: w widoku, proporcja {prop:.3f}" if not zle
                                       else "lightbox: " + "; ".join(zle))
        page.keyboard.press("Escape")
        page.wait_for_timeout(300)

        # tablet: tu właśnie zapada się nawigacja (1040) i siatka bento (1041)
        page.set_viewport_size({"width": 1024, "height": 900})
        page.wait_for_timeout(450)
        check_layout(page, "układ 1024 px (tablet)")
        check_nav(page, "narazja 1024 px")
        page.set_viewport_size({"width": 820, "height": 1180})
        page.wait_for_timeout(400)
        check_nav(page, "narazja 820 px")

        page.set_viewport_size({"width": 1440, "height": 1000})
        page.evaluate("() => document.querySelectorAll('#faq details').forEach(d => { d.open = true; })")
        page.wait_for_timeout(350)
        check_layout(page, "układ 1440 px z otwartym FAQ")

        small = page.evaluate("""() => [...document.querySelectorAll('a[href], button')].map(e => {
            const r = e.getBoundingClientRect();
            return { sel: e.tagName.toLowerCase() + '.' + String(e.className || '').split(' ')[0].slice(0, 20),
                     w: Math.round(r.width), h: Math.round(r.height) };
          }).filter(x => x.w && x.h && (x.w < 24 || x.h < 24))""")
        (ok if not small else fail)(
            "wszystkie kontrolki mają pole trafienia >= 24 px" if not small else f"za małe pole trafienia: {small}")

        page.keyboard.press("Tab")
        f = page.evaluate("() => {const a = document.activeElement, cs = getComputedStyle(a);"
                          "return [a.tagName + '.' + String(a.className).split(' ')[0], cs.outlineStyle, cs.outlineWidth];}")
        (ok if f[1] != "none" else fail)(f"fokus po Tab widoczny: {f[0]} outline={f[1]} {f[2]}")

        counts = page.evaluate("""() => ({reveal: document.querySelectorAll('.reveal').length,
                                          inview: document.querySelectorAll('.reveal.is-in').length,
                                          alt: document.images.length,
                                          lang: document.documentElement.lang})""")
        ukryte = counts["reveal"] - counts["inview"]
        (ok if not ukryte else fail)(
            f"po przewinięciu odsłonięte wszystkie bloki ({counts['inview']}/{counts['reveal']}), język: {counts['lang']}"
            if not ukryte else f"{ukryte} bloków .reveal nigdy się nie odsłania — zostały niewidoczne")

        # o scroll 0: nic z hero nie może chować się pod paskiem nawigacji
        page.evaluate("() => scrollTo({ top: 0, behavior: 'instant' })")
        page.wait_for_timeout(350)
        pod = page.evaluate("""() => {
          const n = document.querySelector('.nav').getBoundingClientRect();
          return [...document.querySelectorAll('#hero h1, #hero .lead, #hero .btn, #hero dt, #hero .pop')]
            .filter(e => { const r = e.getBoundingClientRect();
              return r.width && Math.min(r.bottom, n.bottom) - Math.max(r.top, n.top) > 3 &&
                     Math.min(r.right, n.right) - Math.max(r.left, n.left) > 3; })
            .map(e => e.tagName.toLowerCase() + '.' + String(e.className || '').split(' ')[0]); }""")
        (ok if not pod else fail)("początek strony: treść hero nad paskiem nawigacji" if not pod
                                   else f"treść pod paskiem nawigacji: {pod}")

        p2 = ctx.new_page()
        p2.emulate_media(reduced_motion="reduce")
        p2.goto(PAGE, wait_until="networkidle")
        p2.wait_for_timeout(800)
        hidden = p2.evaluate("""() => [...document.querySelectorAll('.reveal')].filter(e => {
            const cs = getComputedStyle(e), r = e.getBoundingClientRect();
            return +cs.opacity < 0.9 && r.top < innerHeight * 1.5; }).length""")
        (ok if hidden == 0 else fail)(f"prefers-reduced-motion: nic nie znika ({hidden} ukrytych)")

        ctx3 = b.new_context(java_script_enabled=False, viewport={"width": 1440, "height": 1000})
        p3 = ctx3.new_page()
        p3.goto(PAGE, wait_until="load")
        vis = p3.evaluate("() => [...document.querySelectorAll('.reveal')].every(e => +getComputedStyle(e).opacity === 1)")
        (ok if vis else fail)("bez JavaScriptu cała treść jest widoczna")

        check_policy(b)

        if args.shots:
            OUT.mkdir(parents=True, exist_ok=True)
            for theme in ("dark", "light"):
                page.set_viewport_size({"width": 1440, "height": 1000})
                page.goto(PAGE, wait_until="networkidle")
                page.evaluate("(t) => document.documentElement.dataset.theme = t", theme)
                page.evaluate("() => document.querySelectorAll('.reveal').forEach(e => e.classList.add('is-in'))")
                page.wait_for_timeout(1400)
                page.screenshot(path=str(OUT / f"desktop-{theme}.png"), full_page=True)
                page.set_viewport_size({"width": 390, "height": 844})
                page.evaluate("(t) => document.documentElement.dataset.theme = t", theme)
                page.wait_for_timeout(900)
                page.screenshot(path=str(OUT / f"mobile-{theme}.png"))
            print(f"  zrzuty → {OUT}")

        b.close()

    # 13. Słownik PL kompletny + lista twierdzeń, których nie ma w kodzie aplikacji
    js = (DOCS / "app.js").read_text(encoding="utf-8")
    strona = (DOCS / "index.html").read_text(encoding="utf-8")
    klucze = set(re.findall(r'data-i18n="([^"]+)"', strona))
    braki = sorted(klucze - set(re.findall(r"'([^']+)':", js)))
    if braki:
        fail(f"brakuje {len(braki)} tłumaczeń PL: {', '.join(braki[:6])}")
    else:
        ok(f"słownik PL ma wszystkie {len(klucze)} kluczy z markupu")
    # 13b. Ogonki. Reguły zna tylko człowiek, więc: twardy zakaz form, które już raz
    #      zostały ucięte przez narzędzia, plus ostrzeżenie heurystyczne dla reszty.
    OGONKI = "ąćęłńóśźż"
    BEZ_OGONEK = str.maketrans(OGONKI, "acelnoszz")
    # homografy: forma bez ogonka jest poprawna (kategorie vs kategorię, date obok datę)
    DOZWOLONE = {"date", "data", "media", "menu", "hero", "kategorie", "kategoria", "kategorii",
                 "rekord", "pola", "dany", "idealny", "okno", "kopia", "baza", "stany", "panel"}
    REGRESJE = {  # forma błędna → jak powinno być (nazwa pliku albo None = każdy)
        "najedz": "najedź", "najedż": "najedź", "otdotyczy": "odnosi się do",
        "kazej": "każdej", "kazdej": "każdej", "odstpy": "odstępy", "wkleaj": "wklejaj",
        "reagowa": "reagować", "styluja": "stylują", "wartosc": "wartość", "obliczeniowyhc": "obliczeniowych",
    }
    publikacje = {"index.html": strona, "app.js": js,
                  "privacy-policy.html": (DOCS / "privacy-policy.html").read_text(encoding="utf-8"),
                  "AGENTS.md": (ROOT / "AGENTS.md").read_text(encoding="utf-8")}
    ugryzione = [f"{nazwa}: «{zle}» → «{dobre}»"
                 for nazwa, txt in publikacje.items()
                 for zle, dobre in REGRESJE.items()
                 if re.search(r"(?<![\w-])" + zle + r"(?![\w-])", txt.lower())]
    if ugryzione:
        for u in ugryzione[:6]:
            fail(f"zgubiony diakrytyk: {u}")
    else:
        ok(f"znane formy z uciętymi ogonkami nie wróciły ({len(REGRESJE)} sprawdzonych)")

    katalog = {}
    for txt in publikacje.values():
        for slowo in re.findall(r"[a-ząćęłńóśźż]{4,}", txt.lower()):
            if any(c in OGONKI for c in slowo):
                katalog.setdefault(slowo.translate(BEZ_OGONEK), slowo)
    heurystyka = []
    for nazwa, txt in publikacje.items():
        for forma in sorted(katalog):
            if forma in DOZWOLONE or forma in REGRESJE:
                continue
            if not re.search(r"(?<![\w-])" + re.escape(katalog[forma]) + r"(?![\w-])", txt.lower())                and re.search(r"(?<![\w-])" + forma + r"(?![\w-])", txt.lower()):
                heurystyka.append(f"{nazwa}: {forma} → «{katalog[forma]}»")
    if heurystyka:
        note(f"do przejrzenia ogonki (homografy bywają poprawne): {'; '.join(heurystyka[:4])}")
    else:
        ok(f"heurystyka ogonkowa czysta (katalog {len(katalog)} form)")

    trafione = [f"«{_slow}» — {powód}"
                for _slow, powód in (
                    ("Undo on delete", "R.string.undo jest nieużywany w kodzie"),
                    ("Cofnięcie usunięcia", "j.w."),
                    ("500+", "w pliku słów kluczowych jest 220 wpisów"),
                    ("make stats", "repo nie ma Makefile"),
                    ('"license":', "w repo nie ma pliku LICENSE"),
                )
                if _slow.lower() in (strona + js).lower()]
    if trafione:
        for t_ in trafione:
            fail(f"zabronione twierdzenie: {t_}")
    else:
        ok("żadne odrzucone twierdzenie nie wróciło do tekstów")

    print("\n".join("  OK   " + m for m in oks))
    if notes:
        print("\n".join("  ..   " + m for m in notes))
    if fails:
        print("\n".join("  !!   " + m for m in fails))
    print(f"\n{len(oks)} OK · {len(notes)} uwag · {len(fails)} błędów")
    return 1 if fails else 0


if __name__ == "__main__":
    sys.exit(main())
