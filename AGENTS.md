# AGENTS.md — `skarbio-public`

Publiczne repozytorium aplikacji **Skarbio** (Android, Kotlin) — prywatny tracker wydatków
z OCR paragonów i faktur. Repo jest **kanonem wydania**: nie ma tu wydania APK, więc kod jest
dystrybucją (patrz FAQ na stronie startowej).

## Fakty zweryfikowane 2026-11-14 (nie przepisuj z pamięci sesji)

| Fakt | Skąd |
| --- | --- |
| wersja `1.3.1`, `versionCode 2026040720`, `minSdk 24`, `targetSdk 35`, `compileSdk 35` | `app/build.gradle` |
| **7** tabel Room (nie 8 — `database/Expense.kt` ma `@Entity`, ale jest poza bazą) | `data/local/AppDatabase.kt`, lista `entities = [...]` |
| **26** kategorii (enum) | `data/model/CategoryKey.kt` |
| **220** polskich słów kluczowych sklepów | `app/src/main/assets/data/category_keywords/pl.json` |
| doładowanie nieostre (Levenshtein) | `data/local/CategoryManager.kt` (`fuzzyMatchesAnyWord`) |
| waluty `PLN`, `EUR`, `USD` | `ui/settings/SettingsFragment.kt`, `utils/CurrencyFormatter.kt` |
| kopia `.skb`: AES-256-GCM, PBKDF2-HmacSHA1 100 000 iteracji | `backup/BackupManager.kt` (`KEY_SIZE = 256`) |
| OCR i skaner działają offline; PDF-a przyjmuje `SharePdfReceiptActivity` | `MainActivity.kt:549`, `SharePdfReceiptActivity`, `processReceiptImage` |
| lookup NIP → rejestr VAT jest **opcjonalny i domyślnie wyłączony** | `data/repository/SettingsRepository.kt` (`API_STORE_LOOKUP_ENABLED`, domyślnie `false`) |
| widget ekranu startowego | `ScanReceiptWidgetProvider`, `res/xml/widget_scan_receipt_info.xml` |
| **brak** Firebase, analityki, SDK reklam i crash-reportów | `app/build.gradle` (brak zależności), manifest (brak uprawnień sieciowych poza lookupem) |
| 100 plików `.kt`, 16 652 linie, 47 layoutów XML | `find app/src -name "*.kt" \| xargs wc -l \| tail -1` |
| **licencji brak** — „all rights reserved” | brak `LICENSE`; `README.md:212` |

Zmieniając którąkolwiek z tych liczb, zmień też tekst na stronie startowej
(`docs/index.html` + słownik `PL` w `docs/app.js`) i przesuń datę pomiaru w sekcji
„Pod maską”.

## Strona startowa (`docs/`)

`docs/` = katalog publikowany na GitHub Pages (`main` + `/docs`). Statyczne pliki, zero
zbieranego kodu i zero zewnętrznych żądań (fonty własne).

| Plik | Rola |
| --- | --- |
| `docs/index.html` | cała strona; markup **po angielsku** (`lang="en"`), polski dokłada `app.js` słownikiem `data-i18n` |
| `docs/app.js` | tłumaczenia PL, demo skanu, lightbox, liczniki, motyw |
| `docs/styles.css` | tokeny wydobyte z motywów aplikacji (`values` i `values-night`) |
| `docs/privacy-policy.html` | **generowany** — nie edytuj |
| `docs/assets/fonts/` | subsety `woff2` (bez żadnego CDN) |

Naprawę polityki wnosi się w `privacy-policy.html` w **korzeniu** repo, potem:

```bash
python tools/landing/policy.py     # kopiuje <body> do szkieletu, obniża drugi język pod jeden <h1>
```

### Warsztat (`tools/landing/`)

| Skrypt | Co robi |
| --- | --- |
| `make-assets.py` | PNG → WebP (`dashboard-hero.jpg` **przycinany od góry**, bo UI dashboardu zaczyna się pod statusem), miniatury, logo/favicony, `?full=1`, subsety fontów (`--flavor=woff2`) i `.nojekyll` |
| `browser.py` | Playwright przez `msedge` → `chrome` → goły Chromium (nie pobieraj silnika) |
| `audit.py` | bramka: kontrast w obu motywach i na polityce, hierarchia `<h1>`, brak przewijania przy 360/390/768/820/1024/1440, kolizje układu na telefonie i **tablecie** (1024/820 — tu zapada się menu i siatka bento), proporcje telefonów, zero żądań zewnętrznych, lokalne fonty, `prefers-reduced-motion`, stan bez JS, pole trafienia, **nakładanie i zasłanianie tekstu**, gęstość słowa w `<main>`, polityka 1:1 ze źródłem, test mutacyjny. `--shots` dorzuca zrzuty QA. Cel: `66 OK · 0 uwag · 0 błędów` |
| `beauty.py` | **miara smaku, nie ocena**: zdejmuje próbki stylów obliczeniowych z każdej widocznej klasy i liczy drabinę pisma (9 stopni, skok ≥1.07), rytm odstępów (wielokrotne 4 px), liczby zaokrągleń / czasów / easingów / barw, wspólną lewą krawędź bloków, airaż sekcji (po sumie pakietów treści, nie po sumie dzieci), długość wiersza ≤75 znaków, proporcję `h2` do nadzywki. `--hover` dodatkowo sprawdza, że każda kontrolka **po najechaniu coś zmienia** (patrzy też na `::before`/`::after` i dzieci). Cel: zero `źle` |
| `shoot.py dark light` | zrzuty sekcji (desktop 1440 + mobile 390) do przeglądu przez człowieka; `pl en` dorzuca wariant językowy z przyrostkiem `-en` |
| `og.py` | okładka 1200×630 |

```bash
python -m http.server 8123 --bind 127.0.0.1 --directory docs &
PYTHONIOENCODING=utf-8 python tools/landing/audit.py            # cel: 0 błędów, 0 uwag
PYTHONIOENCODING=utf-8 python tools/landing/beauty.py --hover    # cel: zero «źle»
PYTHONIOENCODING=utf-8 python tools/landing/shoot.py dark light  # do ludzkiego oka
PYTHONIOENCODING=utf-8 python tools/landing/policy.py --write    # strona polityki = korzeń
```

`tools/landing/shots/` i `__pycache__/` są w `.gitignore` — nie publikuj ich w `docs/`.

Sześć klas nie ma własnej reguły w CSS i **tak ma być** — są po to, żeby opisywać, czym co
jest, a nie jak się renderuje: `features`, `screens` (sekcje stylowane przez `.section` i
`id`), `hero__copy`, `privacy__copy` (potomków stylują rodzice), `doc--receipt`
(domyślny wariant `.doc`, kontrastuje z `.doc--invoice`) oraz `paper__edge--bottom`
domyślna orientacja przerysowanej krawędzi, której `--top` przeciga przez `rotate(180deg)`.
Nie usuwaj ich jako „martwych” — bramka układu i przyszłe motywy czepiają właśnie tych nazw.

## Zasady, których trzymaj przy tej stronie

- **Każde zdanie o aplikacji musi mieć adres w kodzie.** Nie ma `R.string.undo` w użyciu →
  nie ma „cofnij usunięcie”. `DuplicateDetector` **ostrzega** → nie „usuwa duplikatów”.
  Nie wymyślaj komend (`make stats` nie istnieje) ani liczb („500+ sklepów” — jest 220).
- Ekrany to **1080×2400** (`0,45:1`). Ramka makiety `aspect-ratio: 9 / 20`, `<img>` z
  `width`/`height` równymi realnym wymiarom. Kadry-hero tnij `crop_top` (startuje pod
  paskiem stanu) i opisuj je ogólnie.
- Nazwane obszary siatki (`grid-template-areas`) tylko w MediaQuery `min-width: 1041px`;
  poza nim jedna kolumna — inaczej dostajesz kolumny `0px` i tekst na siebie.
- Demo w przeglądarce jest **symulacją** i musi to mówić (próbka danych, „nic stąd nie
  wychodzi”). Stan „trwam w pętli” to błąd: `scanTimer` ma `finally`.
- **Liczyć progi z kodu, nie z głowy.** Alert na pulpicie pojawia się od `percentage
  >= 80f` (`DashboardViewModel.kt:204`), a `isOverBudget` to `spentAmount > limitAmount`
  (czyli powyżej 100%) — stąd „sygnał przy 80%” i „80% i 100%” w JSON-LD. Kolor paska
  zmienia się wcześniej (`>= 75` w `BudgetAdapter`, `>= 90` w `BudgetAlertAdapter`), więc
  nie pisz o kolorze dopiero od osiemdziesiąciu procent.

- Zmiany widoku sprawdzaj bramką **po kliknięciu** (druga zakładka, stan w trakcie skanu,
  stan po zapisie, otwarty lightbox), nie tylko na martwej stronie.
- **Wszystkie liczby stylistyczne bior z tokenów `docs/styles.css`**: `--t-*` (9 stopni
  pisma), `--s-*` (odstępy, wszystkie wielokrotne 4 px), `--r-*` (zaokrąglenia), `--d-*` i
  `--ease*` (ruch). Nowa wartoś spoza skali to cofnięcie: `beauty.py` liczy wtedy kolejne
  „źle”. Reguła jest w obie strony — jeśli skali brakuje, uzupełnij token, nie wklejaj
  literału.
- **Pomiar w przeglądarce musi przewijać natychmiast**: `scrollTo({behavior:'instant'})`,
  `scrollIntoView({behavior:'instant'})`. Strona ma `scroll-behavior: smooth`, więc zwykłe
  `scrollTo(0, y)` animuje, a próbka mierzy element, który *dopiero podleci* na swoje
  miejsce — tak powstają fałszywe kolizje i „martwe” `:hover`. Po przewinięciu odczekaj
  ~350 ms, aż dojdą `reveal`.
- **Klawiatura: pierścień widoczny, fokus nie ucieka.** `beauty.py` przechodzi stronę klawiszem `Tab` (53 zatrzymania) i sprawdza trzy rzeczy: każdy punkt ma nazwę dostępną (również gdy wejście jest wewnątrz `<label>`, nie tylko przy `label[for]`), pierścień nie chowa się pod przyklejonym paskiem i żdnce przejście nie wpada na `<body>`. Realna pułapka: `scanBtn.disabled = true` w fazie `reading` wyrzucało fokus z przycisku, który właśnie obsługuje klawiatura — teraz to `.is-waiting` + `aria-disabled`, a logika i tak ma `if (phase === "reading") return`. „Przejdź do treści” wyskakuje POD paskiem (`top: 88px`), bo nad nim nachodził na logo. Po każdym `Tab` odczekaj i potwierdź, że `scrollY` oraz `top` elementu stanęły — przeglądarka przewija do fokusu z opóźnieniem.
- Stan czynny (`aria-current`, `.is-on`) nie musi reagować na `:hover`; odnośnik w tekście
  musi być widoczny (kolor + podkreślenie) — globalne `a { color: inherit }` ich nie widać
  na stronie polityki, stąd `.prose a`.

## Status publikacji (2026-11-14)

Strona gotowa lokalnie (`66 OK · 0 uwag · 0 błędów`, do tego `beauty.py` `12 OK · 0 · 0`
z pełnym pokryciem `:hover`), ale **GitHub Pages dla `techparka/skarbio` nie został jeszcze
włączony**, a zmiany nie są commitnięte. Przed włączeniem Pages trzeba zdecydować o licencji
(dziś brak pliku `LICENSE` → nie piszemy „open source” ani nie dodajemy pola `license` w
JSON-LD).

Runbook procedury: [`knowledge/runbooks/landing-page-github-pages.md`](../knowledge/runbooks/landing-page-github-pages.md).
