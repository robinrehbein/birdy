# Birdy – Visuelle Politur

Ziel: Jeder Bildschirm soll wie ein fertiges, professionell gestaltetes Mobile-Game aussehen.
Jeder Bereich der Scorecard soll laut einem unabhängigen Art-Director-Review mindestens 8/10
erreichen. Die Reviews sehen nur Screenshots, keinen Code.

**Werkzeuge:**
- `scripts/catalog.mjs` erzeugt den Screenshot-Katalog: 34 Zustände je Sprache (DE/EN) und
  Größe (360×640, 390×844). Der Zufall ist gesät, und die Spielzeit läuft ohne Zeichnen vor.
  Dadurch zeigen zwei Builds dieselben Szenen.
- `scripts/render-assets.mjs` rendert Icon, Splash und Feature-Grafiken aus dem Spielvogel.
- `SEED=7 node scripts/playtest.mjs 50` lässt den Bot 50 Runs je Stufe spielen, mit gesätem
  Zufall.
- `scripts/perf.mjs` misst Draw Calls, Dreiecke und die Zeit bis zum ersten Frame (headless).

**Messbasis vor Iteration 1:**
- Build: `main` plus Test-Hook. Median aus 3 Messungen, headless SwiftShader.
- Erster Frame 652 ms. Budget: höchstens +20 %, also ≤ 782 ms.
- 138 Draw Calls (Budget 170), 41,5 k Dreiecke (Budget 90 k).
- Playtest mit `SEED=7`, 50 Runs je Stufe:

  | Stufe | Median Zeit | Median Punkte | Münzen/Run | Zonen 2/3/4 |
  |---|---|---|---|---|
  | Anfänger | 17,4 s | 9 | 8 | 46 / 2 / 0 % |
  | Geübt | 40,2 s | 23 | 23 | 100 / 64 / 22 % |
  | Profi | 180 s (Zeitlimit) | 144 | 138 | 100 / 100 / 100 % |

## Visual-Scorecard

| # | Bereich | Review 0 (Start) |
|---|---|---|
| 1 | Vogel & Kosmetik | 6 |
| 2 | Welten & Szenerie | 6 |
| 3 | Hindernisse & Pickups | 5 |
| 4 | Licht, Farbe & Atmosphäre | 6 |
| 5 | UI-System | 6 |
| 6 | Typografie & Icons | 4 |
| 7 | Effekte & Partikel | 4 |
| 8 | Animation & Übergänge | 5 |
| 9 | Lesbarkeit im Spiel | 5 |
| 10 | Store-Assets | 3 |
| | **Schnitt** | **5,0** |

Begründungen aus Review 0 (Art-Director-Subagent, nur Screenshots):

1. **Vogel & Kosmetik (6):** In der Seitenansicht im Shop wirkt der Vogel charmant. Von hinten
   und von vorn ist er eine gelbe Facettenkugel mit blassen, stäbchenartigen Flügeln. Die
   Vorschaubilder sind klein, und gesperrte Artikel lassen sich schlecht unterscheiden.
2. **Welten & Szenerie (6):** Jede Zone hat eine eigene Palette und eigene Props. Weg, Röhren
   und Randstreifen sind aber überall gleich. Die Stadt besteht aus grauen Quadern.
3. **Hindernisse & Pickups (5):** Die Röhren blenden nach oben zu „Lichtsäulen“ aus. Die Münzen
   sind flache Scheiben. Power-up-Blasen sind in der Ferne winzig, der Kaktus ist schwer zu
   erkennen.
4. **Licht, Farbe & Atmosphäre (6):** Die Himmel je Zone sind stimmig. Es gibt nur
   Blob-Schatten, der Horizont ist flach. In der Candy-Welt beißen sich grüne Röhren mit Rosa.
5. **UI-System (6):** Die Panels sind konsistent: Creme, Plum-Outline, 3D-Kante. Listen werden
   abgeschnitten, das Unlock-Banner ragt über den Rand, und Game Over ist in EN 390 verrutscht.
6. **Typografie & Icons (4):** Zwei Schriften stehen nebeneinander (Lilita One und
   System-Grotesk). Als Icons dient ein Emoji-Mischmasch.
7. **Effekte & Partikel (4):** Die Trails sind einfache Punkte. Das Regenbogen-Power-up färbt den
   Vogel nur. Der Magnet ist unsichtbar, und beim Crash gibt es keinen Treffer-Effekt.
8. **Animation & Übergänge (5):** Aus Standbildern abgeleitet: Hinter Pause und Game Over wird
   nicht abgedunkelt. Der Crash-Frame sieht aus wie das normale Spiel.
9. **Lesbarkeit im Spiel (5):** Nahe, schon passierte Röhren und Münzen erscheinen als große
   halbtransparente Geister vor der Kamera. Toasts und Banner liegen auf den Hindernissen.
10. **Store-Assets (3):** Der Splash zeigt noch „Flapsy“. Die Store-Screenshots sind rohe
    Spielbilder ohne Rahmen und Claim.

Katalog-Übersichten (Start): `docs/visual/base/*.jpg`

![Menü und Tutorial](visual/base/01-tutorial-menu.jpg)
![Zonen](visual/base/06-zones.jpg)
![Icon, Splash, Feature-Grafik](visual/base/20-icon-splash-feature.jpg)

## Mängelliste (priorisiert, Stand Review 0)

Status: offen / ✅ erledigt (Iteration) / ⏸ wartet auf Entscheidung.

| # | Bereich | Schwere | Mangel | Plan | Status |
|---|---|---|---|---|---|
| 1 | 10 | Blocker | Der Splash zeigt „Flapsy“ statt „Birdy“. | Splash neu rendern mit dem Birdy-Schriftzug | offen |
| 2 | 5 | Blocker | Game Over in EN 390×844 ist nach oben verrutscht, unten erscheint ein türkiser Streifen. Ursache: Der App-Container scrollt (`scrollIntoView`). | Container gegen Scrollen sperren, nur die Liste scrollen lassen | offen |
| 3 | 10 | ~~Blocker~~ | ~~Schwarze Ecken am runden Launcher-Icon~~ | Geprüft: Die Icons sind transparent. Das Schwarz kam vom Übersichtsbild-Werkzeug. Die gezackte Kante wird beim Neurendern mit geglättet. | – |
| 4 | 9 | hoch | Passierte Röhren und Münzen erscheinen als riesige halbtransparente Geister vor der Kamera und verdecken den Vogel. | Schneller und vollständig ausblenden, sobald sie hinter dem Vogel sind | offen |
| 5 | 10 | hoch | Store-Screenshots ohne Rahmen, Hintergrund und Claim. | Gestaltete Screens DE/EN mit Claim | offen |
| 6 | 6 | hoch | Emoji als Icons: Shop-Reiter, Schlösser, Toasts, Hand, Ton, Geschenk. | Eigenes SVG-Icon-Set | ⏸ Varianten zur Wahl |
| 7 | 6 | hoch | Fließtexte in System-Schrift statt Hausschrift. | Einheitliche Schrift | ⏸ Varianten zur Wahl |
| 8 | 7/8 | hoch | Beim Crash ist keine Rückmeldung sichtbar. | Treffer-Stern, Federn, Flash, Squash | offen |
| 9 | 3 | hoch | Die Röhren blenden nach oben zu „grünen Lichtsäulen“ aus. | Röhren oben sauber enden lassen oder in den Himmel ausblenden | offen |
| 10 | 2/3 | hoch | Alle Kaufwelten haben denselben beigen Weg und denselben Rand. | Weg-Palette je Welt | ⏸ Farbwelt (Rückfrage) |
| 11 | 3 | hoch | Der Kaktus ist nicht erkennbar, Power-up-Blasen sind in der Ferne winzig. | Silhouette, Kontrast, Halo | offen |
| 12 | 1 | mittel | Die Flügel sind blass und stäbchenartig. | Flügel in Körpernähe kräftiger | ⏸ Vogel (Rückfrage) |
| 13 | 1 | mittel | Von hinten ist der Vogel eine Kugel. | Scheitelbüschel und Schwanz lesbarer | ⏸ Vogel (Rückfrage) |
| 14 | 1/5 | mittel | Shop-Vorschaubilder sind klein; gesperrte Artikel sind schlecht erkennbar. | Größere Kacheln, Preisleiste, gesperrte Artikel entsättigen | offen |
| 15 | 5 | mittel | Listen enden mitten in einer Zeile, ohne Scroll-Hinweis. | Fade-Masken, ganze Zeilen | offen |
| 16 | 5 | mittel | Das Unlock-Banner ragt über den Panelrand (360 px). | In Panelbreite halten, Text kürzen | offen |
| 17 | 9 | mittel | Die gestrichelten Spurlinien laufen durch Himmel und HUD. | Linien nur auf dem Boden | offen |
| 18 | 8 | mittel | Get-Ready-Pfeile bleiben halbtransparent stehen; „hierhin“ sagt wenig. | Sauber ausblenden, Text „Spur wechseln“ | offen |
| 19 | 5/8 | mittel | Pause und Game Over dunkeln das Spiel nicht ab. | Scrim und Scale-In | offen |
| 20 | 9 | mittel | Toasts und Zonenbanner liegen auf den Hindernissen. | Toast kompakt unter dem HUD, Banner kürzer und höher | offen |
| 21 | 2 | mittel | Die Stadt besteht aus grauen Quadern, die im Shop riesig wirken. | Fenster, Dächer, Pastelltöne | offen |
| 22 | 7 | mittel | Das Regenbogen-Power-up zeigt nur Farbe, der Magnet ist unsichtbar, die Speed-Lines sind schwach. | Regenbogen-Band, Magnet-Ring, kräftigere Linien | offen |
| 23 | 3 | niedrig | Münzen ohne Rand und Prägung. | Rand und Prägung, Glanz | offen |
| 24 | 10 | niedrig | Im Icon ist der Vogel angeschnitten, der Schnabel wirkt wie Lippen. | Vogel vollständig, mit Outline | offen |
| 25 | 10 | niedrig | Die Feature-Grafik ist leer, und das Logo ist anders gefärbt als im Menü. | Logo wie im Menü, Vogel vollständig | offen |
| 26 | 4 | niedrig | Nur Blob-Schatten; der Horizont ist flach. | Kontaktschatten unter Röhren, Horizont staffeln | offen |
| 27 | 5 | niedrig | Die Spurpunkte unten wirken wie ein Karussell. | Spuranzeige neu | offen |
| 28 | 6 | niedrig | Das Logo wirkt unausgewogen. | – (Logo bleibt, Markenzeichen) | offen |

## Styleguide

Alle Iterationen richten sich danach. Werte sind CSS-Pixel bei 390 px Breite.

### Farben

| Rolle | Token | Hex |
|---|---|---|
| Outline, Textschatten, dunkle Schrift | `--ink` | `#543847` (Plum) |
| Panel-Fläche | `--panel` | `#f4ebc4` (wärmer als bisher `#ded895`) |
| Karte im Panel (Missionen, Kacheln) | `--card` | `#e6d9a2` |
| Panel-Unterkante (Innenlippe) | `--panel-lip` | `#d9c98a` |
| Primär-Aktion (Los geht's, Nochmal) | `--primary` | `#f26b1d` |
| Kaufen, Geschenk, Highlight | `--gold` | `#fcb800` |
| Sekundär (Zurück, Menü) | `--green` | `#73bf2e` |
| Erledigt / Erfolg | `--done` | `#b5d98a` |
| Akzent Überraschung / Zufall | `--violet` | `#7b6bd6` |
| HUD-Pille (Münzen, Power-ups) | `--pill` | `rgba(84, 56, 71, 0.55)` |
| Scrim hinter Overlays | `--scrim` | `rgba(43, 30, 46, 0.45)` |
| Himmel/Logo | – | Gelb `#fcb800`, Grün `#73bf2e` wie im Menü-Titel |

Spielobjekte behalten ihre Signalfarben: Röhre grün `#73bf2e`, Münze gold `#ffcf33`, Kaktus
türkis `#2fa58f` mit cremefarbenen Stacheln.

### Formen

- **Radien:** 6 px (Tabs, kleine Chips), 10 px (Buttons, Karten, Toast), 16 px (Panels),
  voll rund (Pillen, Punkte).
- **Outline:** 3 px für Panels und alles Klickbare, 2 px für Kacheln, Karten und Icons.
- **3D-Kante:** Nur klickbare Elemente haben eine Unterkante in `--ink`: 5 px bei großen
  Buttons, 3 px bei kleinen. Toasts, Banner und Pillen sind flach.
- **Raster:** Abstände in Vielfachen von 4 px (4 / 8 / 12 / 16 / 24). Panel-Innenabstand 16 px.

### Schrift

- **Display:** Lilita One für Titel, Zahlen, Buttons und Banner, mit Plum-Outline über
  Textschatten.
- **Größen:**
  - Logo 56
  - Titel 44
  - Zonenbanner 40 / 20
  - Score 48–80
  - Button L 26, Button M 20
  - Body 14
  - Caption 11–12
- **Body-Schrift:** Wird in einer Iteration vereinheitlicht (siehe Rückfrage). Bis dahin gilt:
  keine neuen Stellen mit System-Schrift.

### Icons

- Eigene SVG-Icons statt Emoji. Stilvarianten werden zur Wahl gestellt.
- Vorgabe:
  - 24er-Raster, 2 px Plum-Outline
  - flache Füllung aus der Palette, höchstens 2 Töne plus Glanzpunkt
  - runde Linienenden
- Größen:
  - 20 px in Reitern und Buttons
  - 24 px in Listen
  - 56 px für die Tutorial-Hand
- Münze: überall dieselbe Münze wie im HUD, also Gold mit Plum-Rand.

### 3D

- Low-Poly mit Flat Shading, helle Töne.
- Neue Objekte:
  - backen, damit sie einen Draw Call pro Gruppe brauchen
  - keine Texturen außer generierten
  - Vertex-Farben aus der Palette der Zone
- Durchsichtigkeit nur für kurze Ausblendungen, nie als dauerhafter Zustand im Bild.

## Iterationen

Jede Iteration behebt die größte sichtbare Schwäche und wird so belegt:
- Vorher/Nachher-Bild derselben Szene
- Playtest mit 50 Runs je Stufe
- Perf-Messung
- APK-Build
