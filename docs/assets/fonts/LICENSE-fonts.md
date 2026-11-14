# Fonty

Strona używa trzech krojów na własnym hostingu (bez CDN), w podzbiorze obejmującym
znaki łacińskie i polskie oraz typografię cudzysłowów i strzałek.

| Krój | Autor | Licencja | Źródło |
|---|---|---|---|
| Inter | Rasmus Andersson | SIL Open Font License 1.1 | https://rsms.me/inter/ |
| Space Grotesk | Florian Karsten | SIL Open Font License 1.1 | https://fonts.google.com/specimen/Space+Grotesk |
| JetBrains Mono | Philipp Nurullin, Nikolay Avtonomov (JetBrains) | SIL Open Font License 1.1 | https://www.jetbrains.com/mono/ |

Wszystkie trzy kroje są publikowane na licencji SIL OFL 1.1, która zezwala na
wgrywanie, modyfikację (w tym podzbieranie znaków) i redystrybucję plików fontu
w dokumentach i stronach. Pełne teksty licencji leżą obok fontów, pobrane
z repozytoriów samych projektów:

- `LICENSE-Inter.txt` — github.com/rsms/inter
- `LICENSE-Space-Grotesk.txt` — github.com/floriankarsten/space-grotesk
- `LICENSE-JetBrains-Mono.txt` — github.com/JetBrains/JetBrainsMono

Pliki `.woff2` w tym katalogu to podzbiory oryginalnych fontów Google Fonts
(wygenerowane przez `tools/landing/make-assets.py`); oryginalne, pełne pliki
pochodzą z https://fonts.google.com i nie były modyfikowane poza usunięciem
glifów spoza używanego zestawu znaków.
