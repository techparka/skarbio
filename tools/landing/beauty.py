#!/usr/bin/env python3
"""Metryki «ładności» policzone z geometrii i stylów wyrenderowanej strony.

Model bez wzroku nie oceni smaku, ale oceni rzeczy, które ze smakiem korelują:
skala pisma, rytm odstępów, wspólna siatka krawędzi, długość wersu, kompletność
stanów :hover, dyscyplina palety i czasu animacji, powietrze w sekcjach.

Uruchom najpierw podgląd:  python -m http.server 8123 --bind 127.0.0.1 --directory docs
Potem:                    PYTHONIOENCODING=utf-8 python tools/landing/beauty.py
"""
from __future__ import annotations

import json
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import browser  # noqa: E402

URL = "http://127.0.0.1:8123/index.html"

# ---------------------------------------------------------------- JS do pomiaru
PROBE = r"""
() => {
  const vis = (e) => {
    const r = e.getBoundingClientRect();
    if (!r.width || !r.height) return false;
    const s = getComputedStyle(e);
    if (s.visibility === 'hidden' || s.display === 'none' || +s.opacity === 0) return false;
    if (e.closest('[aria-hidden="true"]')) return false;
    return true;
  };
  const txt = (e) => (e.innerText || '').replace(/\s+/g, ' ').trim();
  const px = (v) => Math.round(parseFloat(v) || 0);

  // 1. drabina pisma — rozmiary realnie widzianego tekstu
  const sizes = new Map();
  document.querySelectorAll('h1,h2,h3,h4,p,li,a,button,dt,dd,span,code,figcaption,label')
    .forEach(e => {
      if (!vis(e) || !txt(e)) return;
      const s = getComputedStyle(e);
      const k = px(s.fontSize);
      const w = +s.fontWeight, lh = parseFloat(s.lineHeight) || k * 1.4;
      if (!sizes.has(k)) sizes.set(k, { n: 0, w, lh: +(lh / k).toFixed(2), weight: w });
      sizes.get(k).n++;
    });

  // 2. odstęp i promień
  const odstepy = new Set(), promienie = new Set(), liniowe = new Set(),
        czasy = new Set(), easingi = new Set();
  document.querySelectorAll('*').forEach(e => {
    if (!vis(e)) return;
    const s = getComputedStyle(e);
    ['paddingTop', 'paddingBottom', 'marginTop', 'marginBottom', 'rowGap', 'columnGap']
      .forEach(p => { const v = px(s[p]); if (v > 0) odstepy.add(v); });
    ['borderTopLeftRadius', 'borderTopRightRadius'].forEach(p => {
      const v = px(s[p]); if (v > 0) promienie.add(v);
    });
    const lh = parseFloat(s.lineHeight);
    if (lh) liniowe.add(+(lh / parseFloat(s.fontSize)).toFixed(2));
    (s.transitionDuration || '').split(',').forEach(v => {
      const ms = Math.round(parseFloat(v) * (v.includes('ms') ? 1 : 1000));
      if (ms > 0) czasy.add(ms);
    });
    (s.transitionTimingFunction || '').split(',').forEach(v => easingi.add(v.trim()));
  });

  // 3. paleta — odcienie użyte w tekście i w tłach
  const hue = (c) => {
    const m = c.match(/rgba?\(([\d.]+),\s*([\d.]+),\s*([\d.]+)(?:,\s*([\d.]+))?/);
    if (!m) return null;
    if (m[4] !== undefined && +m[4] < .14) return null;
    const [r, g, b] = [+m[1] / 255, +m[2] / 255, +m[3] / 255];
    const mx = Math.max(r, g, b), mn = Math.min(r, g, b), d = mx - mn;
    if (!d) return null;
    const h = mx === r ? 60 * (((g - b) / d) % 6) : mx === g ? 60 * ((b - r) / d + 2) : 60 * ((r - g) / d + 4);
    return Math.round((h + 360) % 360);
  };
  const barwy = new Map();
  document.querySelectorAll('body *').forEach(e => {
    if (!vis(e)) return;
    const s = getComputedStyle(e);
    [s.color, s.backgroundColor, s.borderTopColor].forEach(c => {
      if (!c || c === 'transparent') return;
      const h = hue(c);
      if (h === null) return;
      const bucket = h < 15 || h >= 345 ? 'roz' : h < 45 ? 'bursztyn' : h < 70 ? 'zolc'
        : h < 165 ? 'ziel' : h < 200 ? 'cyjan' : h < 260 ? 'indygo' : h < 320 ? 'fiolet' : 'roz2';
      barwy.set(bucket, (barwy.get(bucket) || 0) + 1);
    });
  });

  // 4. krawędzie sekcji — czy wszystko siedzi na jednej siatce
  const lewe = new Map();
  document.querySelectorAll('section .wrap > *').forEach(e => {
    if (!vis(e)) return;
    const l = Math.round(e.getBoundingClientRect().left);
    lewe.set(l, (lewe.get(l) || 0) + 1);
  });

  // 5. powietrze — ile wysokości sekcji zajmuje realna treść (unia boxów)
  const sekcje = [];
  document.querySelectorAll('section').forEach(s => {
    const sr = s.getBoundingClientRect();
    if (!sr.height) return;
    let gora = Infinity, dol = -Infinity, lewo = Infinity, prawo = -Infinity;
    [...s.querySelectorAll('h1,h2,h3,h4,p,li,a,button,img,svg,dl,ul,.card,.rail,.terminal,.code,.stack__grid,.privacy__grid,.demo__grid')].forEach(e => {
      const r = e.getBoundingClientRect();
      if (!r.width || !r.height) return;
      const s2 = getComputedStyle(e);
      if (s2.visibility === 'hidden' || +s2.opacity === 0 || e.closest('[aria-hidden="true"]')) return;
      gora = Math.min(gora, r.top - sr.top);
      dol = Math.max(dol, r.bottom - sr.top);
      lewo = Math.min(lewo, r.left - sr.left);
      prawo = Math.max(prawo, r.right - sr.left);
    });
    if (gora === Infinity) return;
    sekcje.push({
      id: s.id || '(bez id)',
      h: Math.round(sr.height),
      od_gory: Math.round(gora),
      pod_spacja: Math.round(sr.height - dol),
      szerokosc: Math.round(prawo - lewo),
      wypelnienie: +(((dol - gora) / sr.height) * 100).toFixed(0),
    });
  });

  // 6. długość wersu w znakach (nagłówek i akapity)
  const wers = [];
  document.querySelectorAll('h1,h2,h3,p,li,dd').forEach(e => {
    if (!vis(e)) return;
    const t = txt(e);
    if (t.length < 45) return;
    const r = e.getBoundingClientRect();
    const s = getComputedStyle(e);
    const znaki = Math.round(t.length / Math.max(1, r.height / (parseFloat(s.lineHeight) || 1.5 * parseFloat(s.fontSize))));
    wers.push({ e: e.tagName.toLowerCase() + '.' + (e.className || '').split(' ')[0], znaki, w: Math.round(r.width) });
  });

  // 7. hierarchia nagłówków sekcji — czy wszyscy trzymają tę samą proporcję
  const glowy = [...document.querySelectorAll('section .section__head')].map(h => {
    const we = h.querySelector('.eyebrow'), h2 = h.querySelector('h2'), p = h.querySelector('p');
    const f = (el) => el ? px(getComputedStyle(el).fontSize) : 0;
    return { id: h.parentElement.id || '?', eb: f(we), h2: f(h2), p: f(p), h2h: h2 ? +(f(h2) / f(we)).toFixed(2) : 0 };
  });

  return {
    rozmiary: [...sizes.entries()].sort((a, b) => b[0] - a[0]).map(([k, v]) => ({ px: +k, ...v })),
    odstepy: [...odstepy].sort((a, b) => a - b),
    promienie: [...promienie].sort((a, b) => a - b),
    liniowe: [...liniowe].sort((a, b) => a - b),
    czasy: [...czasy].sort((a, b) => a - b),
    easingi: [...easingi].length,
    barwy: Object.fromEntries(barwy),
    lewe: [...lewe.entries()].sort((a, b) => b[1] - a[1]),
    sekcje,
    wers: wers.sort((a, b) => b.znaki - a.znaki).slice(0, 6),
    glowy,
    window: { w: innerWidth, h: innerHeight },
  };
}
"""

# ------------------------------------------------------------------ porównania
ETYKIETA = """(el) => el.tagName.toLowerCase() + (el.id ? '#' + el.id : '')
  + (typeof el.className === 'string' && el.className.trim()
     ? '.' + el.className.trim().split(/\s+/)[0] : '')"""

SNAP = """(el) => {
  const wez = (e, pseud) => { const c = getComputedStyle(e, pseud);
    return [c.color, c.backgroundColor, c.boxShadow, c.borderColor, c.transform, c.opacity,
            c.width, c.borderRadius, c.textDecorationColor, c.textUnderlineOffset].join('|'); };
  const r = el.getBoundingClientRect(), s = getComputedStyle(el);
  if (!r.width || !r.height || s.opacity === '0' || s.visibility === 'hidden') return null;
  if (el.tagName === 'INPUT' && s.position === 'absolute') return null;      // pole pod rysowaną kontrolką
  if (el.classList.contains('skip')) return null;                            // poza ekranem do czasu fokusu
  if (el.classList.contains('is-on') || el.getAttribute('aria-pressed') === 'true'
      || el.getAttribute('aria-current') === 'true') return null;            // stan czynny nie musi się zmieniać
  return [wez(el), wez(el, '::before'), wez(el, '::after'),
    ...[...el.querySelectorAll('*')].slice(0, 12).map((x) => wez(x))].join('#');
}"""


def stan_hover(p) -> tuple[list[str], list[str]]:
    """Które kontrolki nie reagują na :hover — nawet pośrednio (liczą się dzieci)."""
    martwe = []
    for el in p.query_selector_all('a,button,.card,.shot,.pill,summary,[role=button]'):
        if el.evaluate(SNAP) is None:
            continue
        label = el.evaluate(ETYKIETA)
        try:
            # wyśrodkuj, odczekaj aż reveal-dojdzie do równowagi, dopiero mierz punkt
            tr = None
            for _ in range(2):
                el.evaluate("e => e.scrollIntoView({ block: 'center', behavior: 'instant' })")
                p.wait_for_timeout(360)
                tr = el.bounding_box()
                if not tr:
                    break
                trafiony = el.evaluate(
                    "(e) => { const r = e.getBoundingClientRect();"
                    " return e.contains(document.elementFromPoint(r.left + r.width / 2, r.top + r.height / 2)) }")
                if trafiony:
                    break
            if not tr:
                continue
            przed = el.evaluate(SNAP)
            p.mouse.move(tr["x"] + tr["width"] / 2, tr["y"] + tr["height"] / 2)
            p.wait_for_timeout(240)
            po = el.evaluate(SNAP)
        except Exception:
            continue
        p.mouse.move(4, 4)
        p.wait_for_timeout(90)
        if przed == po:
            martwe.append(label)
    return martwe, []


def stan_fokusu(p) -> tuple[int, list[str], list[str], list[str]]:
    """Tab po kontrolkach: ile ich jest, czy kazda ma pieścień i czy nie chowa jej pasek."""
    p.evaluate("() => { scrollTo({ top: 0, behavior: 'instant' });"
               " if (document.activeElement) document.activeElement.blur(); }")
    p.wait_for_timeout(250)
    zatrzymane: list[dict] = []
    puste = 0
    for _ in range(130):                 # strona ma ok. 46 punków koncentracji
        p.keyboard.press("Tab")
        p.wait_for_timeout(300)          # przeglądarka jeszcze doczytuje fokus i przewija
        # fokus przewija dokument — dokument jeszcze podlewa — czekamy, aż obie wartości staną
        yt, et = p.evaluate("() => [scrollY, document.activeElement ? Math.round(document.activeElement.getBoundingClientRect().top) : 0]")
        p.wait_for_timeout(180)
        yt2, et2 = p.evaluate("() => [scrollY, document.activeElement ? Math.round(document.activeElement.getBoundingClientRect().top) : 0]")
        if abs(yt2 - yt) > 1 or abs(et2 - et) > 2:
            p.wait_for_timeout(420)      # element jeszcze się przesuwał — pomiar od nowa
        info = p.evaluate("""() => {
          const e = document.activeElement;
          if (!e || e === document.body || e === document.documentElement) return null;
          const pier = (x, ps) => { const c = getComputedStyle(x, ps);
            return [c.outlineStyle, c.outlineWidth, c.boxShadow].join(' '); };
          const odczyty = [pier(e), pier(e, '::before'), pier(e, '::after')];
          const kreska = odczyty.some(o => { const cz = o.split(' ');
            return !['none', 'hidden'].includes(cz[0]) && parseFloat(cz[1]) >= 1; });
          const cien = odczyty.some(o => /shadow/.test(o) === false && !/(^|\s)none$/.test(o)
            && o.includes('rgba(') && !o.includes('rgba(0, 0, 0, 0)'));
          const r = e.getBoundingClientRect();
          const pasek = document.querySelector('.nav');
          const pod = pasek && !pasek.contains(e) && r.top < pasek.getBoundingClientRect().bottom - 1;
          const label = e.tagName.toLowerCase() + (e.id ? '#' + e.id : '')
            + (typeof e.className === 'string' && e.className.trim()
              ? '.' + e.className.trim().split(/\\s+/)[0] : '');
          return { label: label + '|' + (e.innerText || e.getAttribute('aria-label')
            || e.value || '').trim().replace(/\s+/g, ' ').slice(0, 20), sygnal: kreska || cien, pod,
                   poza: r.bottom < 4 || r.top > innerHeight + 40 || r.width < 1 || r.height < 1 };
        }""")
        if info is None:                 # fokus wyszedł poza dokument — daj mu szansę wrócić
            puste += 1
            if puste > 3:
                break
            continue
        puste = 0
        if info not in zatrzymane:
            zatrzymane.append(info)
    bez = sorted({i["label"] for i in zatrzymane if not i["sygnal"]})
    zasloniety = sorted({i["label"] for i in zatrzymane if i["pod"]})
    poza = sorted({i["label"] for i in zatrzymane if i["poza"]})
    return len(zatrzymane), bez, zasloniety, poza



def nazwy_dostepne(p) -> tuple[int, list[str]]:
    """Każdy punkt fokusu musi mieć nazwę dla czytnika ekranu."""
    return p.evaluate("""() => {
      const SEL = 'a[href], button, input, select, textarea, summary, [tabindex]:not([tabindex="-1"])';
      const pole = [...document.querySelectorAll(SEL)].filter(e => {
        const r = e.getBoundingClientRect();
        return r.width > 0 && r.height > 0; });
      const bez = pole.filter(e => {
        const atrybut = e.getAttribute('aria-label') || e.getAttribute('title')
          || e.getAttribute('aria-labelledby') || e.getAttribute('alt');
        return !((e.innerText || e.value || '').trim() || atrybut
          || e.closest('label') || document.querySelector('label[for="' + e.id + '"]')
          || e.querySelector('img[alt]'));
      });
      return [pole.length, bez.map(e => e.tagName.toLowerCase() + (e.id ? '#' + e.id : '')
        + '.' + String(e.className || '').trim().split(/\s+/)[0].slice(0, 18))];
    }""")
def ocena(m: dict) -> list[tuple[str, str, str]]:
    """Zwraca (nazwa, ocena, liczba) — 'OK' znaczy, że metryka jest w porządku."""
    out = []

    # drabina pisma
    d = sorted({r["px"] for r in m["rozmiary"]}, reverse=True)
    skoki = [round(d[i] / d[i + 1], 2) for i in range(len(d) - 1)]
    mialkie = [s for s in skoki if 1 < s < 1.068]
    out.append(("drabina pisma", "OK" if len(d) <= 13 and not mialkie else "uważaj",
                f"{len(d)} stopni, skoki {min(skoki) if skoki else 0}–{max(skoki) if skoki else 0}"
                + (f", rozmyte {mialkie}" if mialkie else "")))

    # odstęp na siatce 4 px
    poza = [v for v in m["odstepy"] if v % 4]
    out.append(("rytm odstępów", "OK" if len(poza) <= 8 else "źle",
                f"{len(m['odstepy'])} wartości, {len(poza)} poza siatką 4 px"
                + (f" (np. {poza[:6]})" if poza else "")))

    out.append(("promienie", "OK" if len(m["promienie"]) <= 9 else "uważaj",
                f"{len(m['promienie'])} wartości: {m['promienie'][:9]}"))

    out.append(("wiersze", "OK" if len(m["liniowe"]) <= 8 else "uważaj",
                f"{len(m['liniowe'])} współczynników line-height"))

    out.append(("czas animacji", "OK" if len(m["czasy"]) <= 8 else "uważaj",
                f"{len(m['czasy'])} wartości: {m['czasy'][:9]}"))
    out.append(("easingi", "OK" if m["easingi"] <= 8 else "uważaj",
                f"{m['easingi']} rodzin (w tym steps/linear dla skanu i paska)"))

    # paleta
    barw = {k: v for k, v in m["barwy"].items() if v > 3}
    out.append(("paleta", "OK" if len(barw) <= 6 else "uważaj",
                ", ".join(f"{k} {v}" for k, v in sorted(barw.items(), key=lambda x: -x[1]))))

    # wspólna siatka
    sumy = sorted((v for _, v in m["lewe"]), reverse=True)
    drugie = sumy[1] if len(sumy) > 1 else 0
    out.append(("wspólna krawędź", "OK" if drugie <= max(2, sumy[0] * 0.12) else "uważaj",
                f"lewe krawędzie: {m['lewe'][:4]}"))

    # powietrze w sekcjach
    ciaesne = [s["id"] for s in m["sekcje"] if s["wypelnienie"] > 94]
    puste = [s["id"] for s in m["sekcje"] if s["wypelnienie"] < 55]
    luki = [f'{s["id"]} {s["od_gory"]}/{s["pod_spacja"]}' for s in m["sekcje"]
            if s["od_gory"] > 130 or s["pod_spacja"] > 150]
    out.append(("powietrze", "OK" if not ciaesne and not puste else "uważaj",
                " ".join(f"{s['id']}:{s['wypelnienie']}%" for s in m["sekcje"])
                + (f" | ciasne {ciaesne}" if ciaesne else "")
                + (f" | puste {puste}" if puste else "")
                + (f" | luki {luki}" if luki else "")))

    # wers
    dlugie = [w for w in m["wers"] if w["znaki"] > 78]
    out.append(("długość wersu", "OK" if not dlugie else "źle",
                "najdłuższe: " + ", ".join(f"{w['e']} {w['znaki']}" for w in m["wers"][:3])))

    # spoistość nagłówków sekcji
    prop = [g["h2h"] for g in m["glowy"] if g["h2h"]]
    rozrzut = (round(max(prop) - min(prop), 2) if prop else 0)
    out.append(("nagłówki sekcji", "OK" if rozrzut <= 0.5 else "uważaj",
                f"h2/eyebrow {min(prop) if prop else 0}–{max(prop) if prop else 0} (rozrzut {rozrzut})"))

    # hero — co widać na pierwszym ekranie
    hero = next((s for s in m["sekcje"] if s["id"] == "hero"), None)
    if hero:
        out.append(("hero na starcie", "OK" if m["window"]["h"] * 0.9 > hero["h"] * 0.82 else "uważaj",
                    f"hero {hero['h']} px przy oknie {m['window']['h']} px"))
    return out


def main() -> int:
    from playwright.sync_api import sync_playwright

    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass

    z_hoverem = "--hover" in sys.argv
    with sync_playwright() as p:
        b = browser.launch(p)
        page = b.new_page(viewport={"width": 1440, "height": 900})
        page.goto(URL, wait_until="networkidle")
        page.wait_for_timeout(600)
        w = page.evaluate("() => document.body.scrollHeight")
        for y in range(0, w, 700):
            page.evaluate("(y) => scrollTo(0, y)", y)
            page.wait_for_timeout(90)
        page.evaluate("() => scrollTo(0, 0)")
        page.wait_for_timeout(700)

        m = page.evaluate(PROBE)
        wyniki = ocena(m)

        liczba_poli, bez_nazwy = nazwy_dostepne(page)
        wyniki.append(("nazwa dostępna", "OK" if not bez_nazwy else "źle",
                       f"{liczba_poli} punktów fokusu"
                       + (", bez nazwy: " + str(bez_nazwy[:5]) if bez_nazwy else ", każdy ma nazwę")))

        kroki, bez_pierscienia, pod_paskiem, poza_oknem = stan_fokusu(page)
        wyniki.append(("fokus z klawiatury",
                       "OK" if not bez_pierscienia and not pod_paskiem and not poza_oknem else "uważaj",
                       f"{kroki} zatrzymane"
                       + (f", bez pieścienia {bez_pierscienia[:6]}" if bez_pierscienia else "")
                       + (f", pod paskiem {pod_paskiem[:4]}" if pod_paskiem else "")
                       + (f", poza oknem {poza_oknem[:4]}" if poza_oknem else "")))

        if z_hoverem:
            martwe, _ = stan_hover(page)
            m["martwe_hover"] = martwe
            wyniki.append(("reakcja :hover", "OK" if len(martwe) <= 2 else "uważaj",
                           f"bez zmian: {martwe[:8]}" if martwe else "wszystkie kontrolki reagują"))

        b.close()

    zle = sum(1 for _, o, _ in wyniki if o == "źle")
    uw = sum(1 for _, o, _ in wyniki if o == "uważaj")
    for nazwa, oc, liczba in wyniki:
        znak = {"OK": " ok ", "uważaj": " ..  ", "źle": " !!  "}[oc]
        print(f"{znak} {nazwa:<18} {liczba}")
    print("\n" + f"{len([1 for _, o, _ in wyniki if o == 'OK'])} ok · {uw} uwagi · {zle} źle")

    (pathlib.Path(__file__).resolve().parent / "beauty.json").write_text(
        json.dumps(m, ensure_ascii=False, indent=1), encoding="utf-8")
    print("szczegóły → tools/landing/beauty.json")
    return 1 if zle else 0


if __name__ == "__main__":
    sys.exit(main())
