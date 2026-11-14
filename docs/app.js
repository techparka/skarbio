/* Skarbio — landing page. Bez frameworków, bez zewnętrznych żądań. */
(() => {
  'use strict';

  const root = document.documentElement;
  const $ = (s, c = document) => c.querySelector(s);
  const $$ = (s, c = document) => Array.from(c.querySelectorAll(s));
  const reduce = () => window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  const LS = {
    get(k) { try { return localStorage.getItem(k); } catch (e) { return null; } },
    set(k, v) { try { localStorage.setItem(k, v); } catch (e) { /* tryb prywatny */ } }
  };

  /* ── 1. Język PL/EN ─────────────────────────────────────────────────
     Wersja EN to znacznik w HTML — jej kopią jest `EN` zrzutowana z DOM.
     Słownik PL nadpisuje te same klucze (dozwolony prosty HTML).      */
  const EN = Object.create(null);
  const nodes = $$('[data-i18n]');
  nodes.forEach(n => { EN[n.dataset.i18n] = n.innerHTML; });

  const PL = {
    'a.skip': 'Przejdź do treści',
    'nav.demo': 'Demo', 'nav.features': 'Możliwości', 'nav.screens': 'Ekrany',
    'nav.privacy': 'Prywatność', 'nav.get': 'Pobierz', 'nav.github': 'GitHub',

    'meta.title': 'Skarbio — polski budżet w telefonie, działa bez internetu',
    'meta.desc': 'Skarbio skanuje paragony i faktury OCR-em na telefonie. Konta, budżety, rachunki — wszystko zostaje w urządzeniu. Bez konta, chmury i analityki.',
    'hero.badge': 'Android 7.0+ · v1.3.1 · bez rejestracji',
    'hero.h1a': 'Zrób zdjęcie paragonu.',
    'hero.h1b': 'Dane zostają u Ciebie.',
    'hero.lead': 'Skarbio czyta paragony i faktury PDF OCR-em na telefonie, sam podciąga kategorię i pilnuje budżetu. Bez chmury i konta.',
    'hero.cta1': 'Kod na GitHubie',
    'hero.cta2': 'Zobacz skanowanie →',
    'hero.chip1': 'Bez konta', 'hero.chip2': 'Bez reklam i analityki', 'hero.chip3': 'Działa w trybie samolotowym',
    'hero.stat1': 'na urządzeniu', 'hero.stat2': 'haseł sklepów', 'hero.stat3': 'kategorii z bazy', 'hero.stat4': 'serwerów w chmurze',
    'hero.scroll': 'scroll',

    'demo.eyebrow': 'Demo interaktywne',
    'demo.h2': 'Papier wchodzi, wpis wychodzi',
    'demo.lead': 'Kliknij „Skanuj” i patrz, jak pola się zapełniają. Tylko OCR jest tu udawany.',
    'demo.tab1': 'Paragon', 'demo.tab2': 'Faktura',
    'demo.hint': 'Przykładowe dane — nic nie wychodzi z tej strony. Prawdziwy OCR działa w Skarbio.',
    'demo.s1t': 'Zdjęcie', 'demo.s1d': 'Aparat, galeria albo PDF udostępniony z innej aplikacji',
    'demo.s2t': 'Rozpoznanie', 'demo.s2d': 'Rozpoznawanie tekstu ML Kit, liczone na telefonie',
    'demo.s3t': 'Zapis', 'demo.s3d': 'Jeden wiersz w lokalnej bazie Room, kategoria już wybrana',
    'demo.f.store': 'Sklep', 'demo.f.amount': 'Kwota', 'demo.f.date': 'Data', 'demo.f.cat': 'Kategoria',
    'demo.recur': 'Rachunek cykliczny — zakładaj kolejne 12 miesięcy',
    'demo.scan': 'Skanuj dokument', 'demo.reset': 'Wyczyść',
    'demo.feed.empty': 'Tu pojawiają się wpisy zapisane w tym demo.',

    'feat.eyebrow': 'Co jest w środku',
    'feat.h2': 'Aplikacja do budżetu, nie platforma fintech',
    'feat.lead': 'Wszystko liczy się lokalnie. Nic nie pyta o twojego maila.',
    'feat.1t': 'Paragony i faktury',
    'feat.1d': 'Zdjęcie z aparatu, galerii albo PDF z innej aplikacji. OCR na urządzeniu wyciąga kontrahenta, kwotę i datę, a przy powtórce od razu ostrzega.',
    'feat.2t': 'Kategorie same',
    'feat.2d': '220 haseł sklepów i dopasowanie nieostre, więc „Biedronka” z literówką trafi w Zakupy spożywcze.',
    'feat.3t': 'Budżet, który się upomina',
    'feat.3d': 'Miesięczny limit na kategorię, pasek postępu i sygnał przy 80%.',
    'feat.4t': 'Rachunki i przypomnienia',
    'feat.4d': 'Czynsz, internet, prąd: terminy, zaległości i powiadomienie przed płatnością.',
    'feat.5t': 'Na co poszło',
    'feat.5d': 'Tapnij wycinek, zobaczysz kategorię. Tu najedź na legendę.',

    'feat.6t': 'Szyfrowane kopie',
    'feat.6d': 'Jeden plik <code>.skb</code> ze wszystkim, opcjonalnie zamknięty AES-256-GCM. CSV wchodzi i wychodzi.',

    'more.1': 'Konta i przelewanie', 'more.2': 'Wspólny stan kont', 'more.3': 'Trend 6 miesięcy, największe sklepy',
    'more.4': 'Blokada odciskiem lub PIN', 'more.5': 'Widget skanowania na pulpicie',
    'more.6': 'Udostępnianie PDF prosto do Skarbio', 'more.7': 'PLN · EUR · USD',
    'more.8': 'Motyw jasny i ciemny', 'more.9': 'Interfejs polski i angielski', 'more.10': 'Własne kategorie',

    'sc.eyebrow': 'Ekrany', 'sc.h2': 'Sześć ekranów, na których się żyje',
    'sc.lead': 'Domyślnie ciemny, jasny na żądanie. Tapnij telefon, żeby powiększyć.',
    'sc.1': 'Pulpit', 'sc.2': 'Historia', 'sc.3': 'Konta', 'sc.4': 'Budżety', 'sc.5': 'Raporty', 'sc.6': 'Kalendarz',

    'pr.eyebrow': 'Prywatność', 'pr.h2': 'Nie ma serwera, który można by złamać',
    'pr.lead': 'To nie obietnica w regulaminie — aplikacja po prostu nie ma gdzie wysłać twoich danych.',
    'pr.1': 'Bez rejestracji. Działa w momencie instalacji.',
    'pr.2': 'Bez analityki, SDK reklam i crash reportów — build nie niesie żadnego.',
    'pr.3': 'Bez synchronizacji. Wiersze leżą w bazie Room (SQLite) w telefonie.',
    'pr.4': 'Bez internetu dla OCR, parsowania, wykresów i kopii.',
    'pr.note': 'Jedno opcjonalne zapytanie: przy włączonym <i>pobieraniu nazw sklepów</i> numer NIP z paragonu idzie do publicznego rejestru VAT po oficjalną nazwę firmy. Domyślnie wyłączone. <a href="privacy-policy.html">Cała polityka</a>.',
    'pr.device': 'ten telefon', 'pr.out': 'bajtów wyszło z telefonu',

    'st.eyebrow': 'Pod maską', 'st.h2': 'Natywny Kotlin. Bez nakładki, bez backendu.',
    'st.lead': 'Jeden APK, ekrany w XML-u, baza Room i ML Kit. Na jedno wieczorne czytanie.',
    'st.src': 'zmierzone w publicznym repo, 2026-11-14',

    'get.eyebrow': 'Skąd wziąć', 'get.h2': 'Trzy komendy do własnej kopii',
    'get.lead': 'Twój build, twój telefon, twoje dane. Publicznego APK brak — kanałem wydania jest repozytorium.',
    'get.1t': 'Sklonuj repozytorium',
    'get.2t': 'Otwórz katalog w Android Studio', 'get.2d': 'JDK 17 i jedna synchronizacja Gradle.',
    'get.3t': 'Zbuduj APK debug i zainstaluj',
    'get.cta': 'Otwórz repozytorium', 'get.cta2': 'README i uwagi o budowie',
    'faq.h3': 'Pytania, które naprawdę padają',
    'faq.1q': 'To jest open source?',
    'faq.1a': 'Kod jest publiczny i czytelny, ale plik licencji jeszcze nie powstał, więc na razie zastrzegam sobie wszystkie prawa. Chcesz coś wykorzystać — otwórz issue i pogadajmy.',
    'faq.2q': 'Czy aplikacja potrzebuje internetu?',
    'faq.2a': 'Nie. OCR, parsowanie faktur, wykresy, kopie i przypomnienia działają offline. Jedyna funkcja sieciowa to opcjonalne sprawdzanie NIP w rejestrze VAT, domyślnie wyłączone.',
    'faq.3q': 'Na jakich telefonach?',
    'faq.3a': 'Android 7.0 (API 24) i nowsze, w pierwszej kolejności telefony w pionie. Aparat pomaga, ale można też wczytać zdjęcie lub PDF z galerii i innych aplikacji.',
    'faq.4q': 'Jak wyniosę swoje dane?',
    'faq.4a': 'Eksport CSV do arkusza, import CSV, żeby wejść, plus pełna kopia <code>.skb</code>, którą zaszyfrujesz AES-256-GCM i hasłem.',
    'faq.5q': 'Czy jest w Google Play?',
    'faq.5a': 'Dziś nie ma. To repozytorium jest jedynym kanałem wydania, a konfiguracja podpisu wydania pozostaje prywatna.',

    'ft.desc': 'Aplikacja budżetowa na Androida, bez chmury. Paragony, konta, budżety i rachunki — wszystko na urządzeniu.',
    'ft.h1': 'Projekt', 'ft.readme': 'README', 'ft.issues': 'Issues', 'ft.policy': 'Polityka prywatności',
    'ft.h2': 'Status', 'ft.license': 'Kod publiczny, licencja jeszcze nieogłoszona.',
    'ft.made': 'Pisana przez jednego człowieka, kodzona z Claude Code.',
    'ft.noCookie': 'Żadnych ciasteczek, żadnej analityki, żadnego żądania z tej strony na zewnątrz.'
  };

  const ARIA = {
    'lang-btn': ['Switch language', 'Zmień język'],
    'theme-btn': ['Switch colour theme', 'Zmień motyw kolorów'],
    'menu-btn': ['Open menu', 'Otwórz menu'],
    'rail-prev': ['Previous screen', 'Poprzedni ekran'],
    'rail-next': ['Next screen', 'Następny ekran'],
    'lb-close': ['Close', 'Zamknij'],
    'lb-prev': ['Previous screen', 'Poprzedni ekran'],
    'lb-next': ['Next screen', 'Następny ekran']
  };

  let lang = LS.get('skarbio.lang') === 'pl' || (!LS.get('skarbio.lang') && (navigator.language || 'en').toLowerCase().startsWith('pl')) ? 'pl' : 'en';

  function applyLang(next) {
    lang = next;
    const dict = lang === 'pl' ? PL : EN;
    nodes.forEach(n => {
      const key = n.dataset.i18n;
      if (key in dict) n.innerHTML = dict[key];
    });
    root.lang = lang === 'pl' ? 'pl' : 'en';
    const title = PL['meta.title'], desc = PL['meta.desc'];
    document.title = lang === 'pl' ? title : EN_TITLE;
    const md = $('meta[name="description"]');
    if (md && desc) md.setAttribute('content', lang === 'pl' ? desc : EN_DESC);
    Object.keys(ARIA).forEach(id => {
      const el = document.getElementById(id);
      if (el) el.setAttribute('aria-label', ARIA[id][lang === 'pl' ? 1 : 0]);
    });
    $$('.code__copy').forEach(b => b.setAttribute('aria-label', lang === 'pl' ? 'Kopiuj komendę' : 'Copy command'));
    $('#lang-label').textContent = lang === 'pl' ? 'EN' : 'PL';
    LS.set('skarbio.lang', lang);
    document.dispatchEvent(new CustomEvent('lang', { detail: lang }));
  }
  const EN_TITLE = document.title;
  const EN_DESC = ($('meta[name="description"]') || {}).content || '';
  const t = key => (lang === 'pl' ? PL[key] : EN[key]) || EN[key] || '';

  /* ── 2. Motyw ────────────────────────────────────────────────────── */
  const themeBtn = $('#theme-btn');
  themeBtn.setAttribute('aria-pressed', root.dataset.theme === 'light' ? 'true' : 'false');
  themeBtn.addEventListener('click', () => {
    const next = root.dataset.theme === 'light' ? 'dark' : 'light';
    root.dataset.theme = next;
    LS.set('skarbio.theme', next);
    themeBtn.setAttribute('aria-pressed', next === 'light' ? 'true' : 'false');
  });
  $('#lang-btn').addEventListener('click', () => applyLang(lang === 'pl' ? 'en' : 'pl'));

  /* ── 3. Nawigacja: postęp, napinanie, podświetlanie sekcji ────────── */
  const nav = $('#nav'), bar = $('#progress');
  const links = $$('.nav__links a');
  const spy = links.map(a => ({ a, sec: $(a.getAttribute('href')) })).filter(x => x.sec);

  function onScroll() {
    const y = window.scrollY;
    nav.classList.toggle('is-stuck', y > 8);
    const h = document.documentElement.scrollHeight - window.innerHeight;
    bar.style.width = (h > 0 ? Math.min(1, y / h) * 100 : 0) + '%';
    let best = spy[0];
    spy.forEach(o => { if (o.sec.offsetTop - 140 <= y) best = o; });
    links.forEach(a => a.classList.remove('is-active'));
    if (best && y > 260) best.a.classList.add('is-active');
  }
  addEventListener('scroll', () => requestAnimationFrame(onScroll), { passive: true });

  const menuBtn = $('#menu-btn'), menu = $('#nav-links');
  menuBtn.addEventListener('click', () => {
    const open = menuBtn.getAttribute('aria-expanded') === 'true';
    menuBtn.setAttribute('aria-expanded', String(!open));
    menu.classList.toggle('is-open', !open);
  });
  menu.addEventListener('click', e => {
    if (e.target.tagName === 'A') { menu.classList.remove('is-open'); menuBtn.setAttribute('aria-expanded', 'false'); }
  });
  addEventListener('keydown', e => {
    if (e.key === 'Escape' && menu.classList.contains('is-open')) {
      menu.classList.remove('is-open'); menuBtn.setAttribute('aria-expanded', 'false'); menuBtn.focus();
    }
  });

  /* ── 4. Odsłanianie, liczniki, paski ─────────────────────────────── */
  const io = new IntersectionObserver(es => {
    es.forEach(e => {
      if (!e.isIntersecting) return;
      e.target.classList.add('is-in');
      $$('[data-count]', e.target).forEach(count);
      io.unobserve(e.target);
    });
  }, { rootMargin: '0px 0px -12% 0px', threshold: .12 });
  $$('.reveal, .bars').forEach(el => io.observe(el));

  function count(el) {
    const to = +el.dataset.count;
    if (reduce() || to === 0) { el.textContent = to; return; }
    const t0 = performance.now(), dur = 900;
    (function step(now) {
      const p = Math.min(1, (now - t0) / dur);
      el.textContent = Math.round(to * (1 - Math.pow(1 - p, 3)));
      if (p < 1) requestAnimationFrame(step);
    })(performance.now());
  }

  /* Poświata pod kursorem na kartach */
  $$('.card').forEach(c => c.addEventListener('pointermove', e => {
    const r = c.getBoundingClientRect();
    c.style.setProperty('--mx', ((e.clientX - r.left) / r.width * 100).toFixed(1) + '%');
    c.style.setProperty('--my', ((e.clientY - r.top) / r.height * 100).toFixed(1) + '%');
  }));

  /* Delikatny przechył telefonu w hero */
  const art = $('.hero__art'), phone = $('#hero-device');
  if (art && phone && !reduce() && matchMedia('(hover: hover)').matches) {
    art.addEventListener('pointermove', e => {
      const r = art.getBoundingClientRect();
      const x = (e.clientX - r.left) / r.width - .5, y = (e.clientY - r.top) / r.height - .5;
      phone.style.transform = `rotateY(${x * 13}deg) rotateX(${-y * 9}deg) translateZ(14px)`;
    });
    art.addEventListener('pointerleave', () => { phone.style.transform = ''; });
  }

  /* ── 5. Demo skanowania ──────────────────────────────────────────── */
  const paper = $('#paper'), scanBtn = $('#scan-btn'), scanLabel = $('#scan-label'), resetBtn = $('#reset-btn');
  const stepEls = $$('#steps .step'), feed = $('#feed'), recurRow = $('#recur-row'), recur = $('#recur');
  const DOC = {
    receipt: {
      store: 'Sklep Spożywczy', amount: '58,40 zł',
      date: ['12 cze 2026', '12 cze 2026'], cat: 'Zakupy spożywcze', color: '#4ADE80',
      sub: ['Rachunek', 'Paragon']
    },
    invoice: {
      store: 'Enea Operator', amount: '312,42 zł',
      date: ['20 cze 2026', '20 Jun 2026'], cat: 'Energia', color: '#FB923C',
      sub: ['Rachunek · termin', 'Bill · due date']
    }
  };
  const DYN = {
    scan: ['Scan document', 'Skanuj dokument'],
    scanNext: ['Scan the other one', 'Przeskanuj drugi'],
    save: ['Save entry', 'Zapisz wpis'],
    reading: ['Reading text…', 'Czytam tekst…'],
    saved: ['Saved on this device', 'Zapisane na tym urządzeniu'],
    demoNote: ['demo — nothing leaves this page', 'demo — nic nie opuszcza tej strony'],
    cleared: ['Cleared', 'Wyczyszczone'],
    copied: ['Copied to clipboard', 'Skopiowane do schowka'],
    bills: ['12 rat założonych z góry', '12 rat z góry w kalendarzu']
  };
  const d = k => DYN[k] ? DYN[k][lang === 'pl' ? 1 : 0] : '';
  let docKind = 'receipt', phase = 'idle', timers = [];

  const fieldEls = {
    store: $('.field[data-field="store"]'), amount: $('.field[data-field="amount"]'),
    date: $('.field[data-field="date"]'), cat: $('.field[data-field="cat"]')
  };
  const set = (name, text) => {
    const box = fieldEls[name]; if (!box) return;
    const val = $('.val', box);
    val.innerHTML = text;
    box.classList.remove('filled'); void box.offsetWidth; box.classList.add('filled');
  };
  const clearFields = () => {
    Object.values(fieldEls).forEach(b => b.classList.remove('filled'));
    $('.val', fieldEls.store).textContent = '—';
    $('.val', fieldEls.amount).textContent = '—';
    $('.val', fieldEls.date).textContent = '—';
    $('.cat__txt', fieldEls.cat).textContent = '—';
    $('.cat', fieldEls.cat).classList.remove('is-set');
  };
  const hasEntries = () => !!feed.querySelector('li:not(.feed__empty)');
  const setPhase = p => {
    phase = p;
    // nie `disabled` — wyłuczone pole gubi koncentracje, a tego nie lubia klawiatura
    scanBtn.classList.toggle('is-waiting', p === 'reading');
    scanBtn.setAttribute('aria-disabled', p === 'reading' ? 'true' : 'false');
    scanLabel.textContent = p === 'reading' ? d('reading')
      : p === 'ready' ? d('save')
        : hasEntries() ? d('scanNext') : d('scan');
    resetBtn.hidden = p === 'idle';
  };

  function setDoc(kind) {
    if (phase === 'reading') return;
    docKind = kind;
    $$('.tab').forEach(b => {
      const on = b.dataset.doc === kind;
      b.classList.toggle('is-on', on); b.setAttribute('aria-pressed', String(on));
    });
    $('.tabs__ink').classList.toggle('is-2', kind === 'invoice');
    paper.dataset.doc = kind;
    $$('.doc', paper).forEach(el => el.classList.toggle('is-on', el.classList.contains('doc--' + kind)));
    recurRow.hidden = kind !== 'invoice';
    clearFields();
    stepEls.forEach(s => s.classList.remove('is-run', 'is-done'));
    setPhase('idle');
  }

  function scan() {
    if (phase === 'reading') return;
    if (phase === 'ready') return save();
    timers.forEach(clearTimeout); timers = [];
    clearFields();
    stepEls.forEach(s => s.classList.remove('is-run', 'is-done'));
    setPhase('reading');
    paper.classList.remove('is-scan'); void paper.offsetWidth;
    if (!reduce()) paper.classList.add('is-scan');
    const fast = reduce() ? .3 : 1;
    const hit = (ms, fn) => timers.push(setTimeout(fn, ms * fast));

    stepEls[0].classList.add('is-run');
    hit(420, () => { stepEls[0].classList.replace('is-run', 'is-done'); stepEls[1].classList.add('is-run'); });
    const D = DOC[docKind];
    hit(900, () => set('store', D.store));
    hit(1250, () => set('amount', D.amount));
    hit(1600, () => set('date', D.date[lang === 'pl' ? 0 : 1]));
    hit(1950, () => {
      const chip = $('.cat', fieldEls.cat);
      $('.cat__txt', chip).textContent = D.cat;
      chip.style.setProperty('--c', D.color);
      chip.classList.add('is-set');
      set('cat', chip.outerHTML);
    });
    hit(2350, () => {
      stepEls[1].classList.replace('is-run', 'is-done');
      stepEls[2].classList.add('is-done');
      paper.classList.remove('is-scan');
      setPhase('ready');
    });
  }

  function save() {
    const D = DOC[docKind];
    const bills = docKind === 'invoice' && recur.checked;
    $$('.feed__empty', feed).forEach(n => n.remove());
    const li = document.createElement('li');
    li.innerHTML = `<i style="--c:${D.color}"></i><span><b style="font-weight:600">${D.store}</b>` +
      `<small>${D.sub[lang === 'pl' ? 0 : 1]} · ${D.date[lang === 'pl' ? 0 : 1]}${bills ? ' · ' + d('bills') : ''}</small></span>` +
      `<b>${D.amount}</b>`;
    feed.prepend(li);
    while (feed.children.length > 3) feed.lastElementChild.remove();
    setPhase('idle');
    toast(d('saved') + ' · ' + d('demoNote'));
  }

  function reset() {
    clearFields();
    stepEls.forEach(s => s.classList.remove('is-run', 'is-done'));
    setPhase('idle');
    toast(d('cleared'));
  }

  $$('.tab').forEach(b => b.addEventListener('click', () => setDoc(b.dataset.doc)));
  scanBtn.addEventListener('click', () => (phase === 'ready' ? save() : scan()));
  resetBtn.addEventListener('click', reset);

  /* toast */
  const toastEl = $('#toast');
  let toastTimer;
  function toast(msg) {
    toastEl.innerHTML = `<svg class="i" viewBox="0 0 24 24" aria-hidden="true"><path d="M4.5 12.5 9.5 17.5 19.5 6.5" /></svg><span></span>`;
    $('span', toastEl).textContent = msg;
    toastEl.classList.add('is-on');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => toastEl.classList.remove('is-on'), 2600);
  }

  document.addEventListener('lang', () => {
    setPhase(phase);
    if (phase !== 'reading') {
      const D = DOC[docKind];
      if (fieldEls.date.classList.contains('filled')) set('date', D.date[lang === 'pl' ? 0 : 1]);
    }
  });

  /* ── 6. Koło kategorii ───────────────────────────────────────────── */
  const donut = $('#donut');
  if (donut) {
    const slices = $$('.donut__slice', donut), btns = $$('.legend button', donut);
    const focus = k => {
      donut.classList.toggle('is-focus', !!k);
      slices.forEach(s => s.classList.toggle('is-on', s.dataset.k === k));
      btns.forEach(b => b.classList.toggle('is-on', b.dataset.k === k));
    };
    btns.forEach(b => {
      b.addEventListener('pointerenter', () => focus(b.dataset.k));
      b.addEventListener('focus', () => focus(b.dataset.k));
      b.addEventListener('click', () => focus(b.dataset.k));
      b.addEventListener('pointerleave', () => focus(null));
      b.addEventListener('blur', () => focus(null));
    });
  }

  /* ── 7. Galeria ekranów + lightbox ───────────────────────────────── */
  const rail = $('#rail'), shots = $$('.shot', rail), dotsBox = $('#rail-dots');
  shots.forEach((s, i) => {
    const b = document.createElement('button');
    b.type = 'button';
    b.setAttribute('aria-label', (s.querySelector('.shot__cap') || {}).textContent || ('screen ' + (i + 1)));
    b.setAttribute('aria-current', i === 0 ? 'true' : 'false');
    b.addEventListener('click', () => goTo(i));
    dotsBox.appendChild(b);
  });
  const dots = $$('button', dotsBox);
  const center = () => rail.scrollLeft + rail.clientWidth / 2;

  function goTo(i) {
    const el = shots[Math.max(0, Math.min(shots.length - 1, i))];
    const rr = rail.getBoundingClientRect(), er = el.getBoundingClientRect();
    rail.scrollTo({
      left: rail.scrollLeft + (er.left - rr.left) - (rail.clientWidth - er.width) / 2,
      behavior: reduce() ? 'auto' : 'smooth'
    });
  }
  let idx = 0;
  function railScroll() {
    const mid = center(), rr = rail.getBoundingClientRect();
    let best = 0, bd = 1e9;
    shots.forEach((s, i) => {
      const er = s.getBoundingClientRect();
      const dist = Math.abs(er.left - rr.left + er.width / 2 - rail.clientWidth / 2);
      if (dist < bd) { bd = dist; best = i; }
    });
    if (best !== idx) {
      idx = best;
      dots.forEach((b, i) => b.setAttribute('aria-current', String(i === idx)));
      shots.forEach((s, i) => s.classList.toggle('is-active', i === idx));
    }
  }
  rail.addEventListener('scroll', () => requestAnimationFrame(railScroll), { passive: true });
  $('#rail-prev').addEventListener('click', () => goTo(idx - 1));
  $('#rail-next').addEventListener('click', () => goTo(idx + 1));
  rail.addEventListener('keydown', e => {
    if (e.key === 'ArrowRight') { e.preventDefault(); goTo(idx + 1); }
    if (e.key === 'ArrowLeft') { e.preventDefault(); goTo(idx - 1); }
  });

  /* przeciąganie taśmy palcem/myszą */
  let drag = null, moved = 0;
  rail.addEventListener('pointerdown', e => {
    if (e.pointerType === 'mouse' && e.button !== 0) return;
    drag = { x: e.clientX, left: rail.scrollLeft }; moved = 0;
    rail.classList.add('is-drag');
  });
  rail.addEventListener('pointermove', e => {
    if (!drag) return;
    const dx = e.clientX - drag.x;
    moved = Math.max(moved, Math.abs(dx));
    rail.scrollLeft = drag.left - dx;
  });
  ['pointerup', 'pointercancel', 'pointerleave'].forEach(ev => rail.addEventListener(ev, () => {
    drag = null; rail.classList.remove('is-drag');
  }));

  /* podświetlenie podążające za kursorem na kaflach i krokach */
  if (matchMedia('(hover: hover) and (pointer: fine)').matches) {
    let klatka = null, cel = null, x = 0, y = 0;
    document.addEventListener('pointermove', (e) => {
      cel = e.target.closest ? e.target.closest('.card, .step') : null;
      if (!cel || klatka) return;
      x = e.clientX; y = e.clientY;
      klatka = requestAnimationFrame(() => {
        klatka = null;
        if (!cel) return;
        const r = cel.getBoundingClientRect();
        cel.style.setProperty('--mx', ((x - r.left) / r.width * 100).toFixed(1) + '%');
        cel.style.setProperty('--my', ((y - r.top) / r.height * 100).toFixed(1) + '%');
      });
    }, { passive: true });
  }

  const lb = $('#lightbox'), lbImg = $('#lb-img'), lbCap = $('#lb-cap');
  let lbIdx = 0;
  function openLb(i) {
    lbIdx = (i + shots.length) % shots.length;
    const s = shots[lbIdx];
    const cap = s.querySelector('.shot__cap').textContent;
    lbImg.src = s.dataset.full;
    lbImg.alt = s.querySelector('img').alt;
    lbCap.textContent = cap;
    if (!lb.open) lb.showModal();
  }
  shots.forEach((s, i) => s.addEventListener('click', () => {
    if (moved > 6) { moved = 0; return; }
    openLb(i);
  }));
  $('#lb-close').addEventListener('click', () => lb.close());
  $('#lb-prev').addEventListener('click', () => openLb(lbIdx - 1));
  $('#lb-next').addEventListener('click', () => openLb(lbIdx + 1));
  lb.addEventListener('click', e => { if (e.target === lb) lb.close(); });
  lb.addEventListener('keydown', e => {
    if (e.key === 'ArrowRight') openLb(lbIdx + 1);
    if (e.key === 'ArrowLeft') openLb(lbIdx - 1);
  });
  lb.addEventListener('close', () => { const s = shots[lbIdx]; s.focus(); });

  /* ── 8. Kopiowanie komend ────────────────────────────────────────── */
  $$('.code__copy').forEach(btn => btn.addEventListener('click', async () => {
    const txt = btn.dataset.copy;
    try {
      await navigator.clipboard.writeText(txt);
    } catch (e) {
      const ta = document.createElement('textarea');
      ta.value = txt; ta.style.position = 'fixed'; ta.style.opacity = '0';
      document.body.appendChild(ta); ta.select();
      try { document.execCommand('copy'); } catch (_) { /* stara przeglądarka */ }
      ta.remove();
    }
    btn.classList.add('is-done');
    btn.innerHTML = '<svg class="i" viewBox="0 0 24 24" aria-hidden="true"><path d="M4.5 12.5 9.5 17.5 19.5 6.5" /></svg>';
    toast(d('copied') + ' · ' + txt.split(' ')[0]);
    setTimeout(() => {
      btn.classList.remove('is-done');
      btn.innerHTML = '<svg class="i" viewBox="0 0 24 24" aria-hidden="true"><rect x="9" y="9" width="11" height="11" rx="2" /><path d="M15 6.5V5a1.5 1.5 0 0 0-1.5-1.5h-7A1.5 1.5 0 0 0 5 5v7A1.5 1.5 0 0 0 6.5 13H8" /></svg>';
    }, 1600);
  }));

  /* ── 9. Start ────────────────────────────────────────────────────── */
  $('#year').textContent = new Date().getFullYear();
  setDoc('receipt');
  applyLang(lang);
  onScroll();
  railScroll();

  /* Jedno automatyczne, nieproszone skanowanie — gdy demo wjedzie w kadr. */
  if (!reduce()) {
    const demoIO = new IntersectionObserver(es => {
      es.forEach(e => {
        if (!e.isIntersecting) return;
        demoIO.disconnect();
        setTimeout(() => { if (phase === 'idle' && !feed.querySelector('li:not(.feed__empty)')) scan(); }, 1100);
      });
    }, { threshold: .45 });
    demoIO.observe($('#demo'));
  }
})();
