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
  |---|---|---|---|---|---|
  | Anfänger | 17,4 s | 9 | 8 | 46 / 2 / 0 % |
  | Geübt | 40,2 s | 23 | 23 | 100 / 64 / 22 % |
  | Profi | 180 s (Zeitlimit) | 144 | 138 | 100 / 100 / 100 % |

## Visual-Scorecard

| # | Bereich | Review 0 (Start) | Review 1 (nach It. 3) |
|---|---|---|
| 1 | Vogel & Kosmetik | 6 | 5 |
| 2 | Welten & Szenerie | 6 | 6 |
| 3 | Hindernisse & Pickups | 5 | 4 |
| 4 | Licht, Farbe & Atmosphäre | 6 | 6 |
| 5 | UI-System | 6 | 6 |
| 6 | Typografie & Icons | 4 | 5 |
| 7 | Effekte & Partikel | 4 | 4 |
| 8 | Animation & Übergänge | 5 | 5 |
| 9 | Lesbarkeit im Spiel | 5 | 5 |
| 10 | Store-Assets | 3 | 4 |
| | **Schnitt** | **5,0** | **5,0** |

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
| 1 | 10 | Blocker | Der Splash zeigt „Flapsy“ statt „Birdy“. | Splash neu rendern mit dem Birdy-Schriftzug | ✅ It. 1 |
| 2 | 5 | Blocker | Game Over in EN 390×844 ist nach oben verrutscht, unten erscheint ein türkiser Streifen. Ursache: Der App-Container scrollt (`scrollIntoView`). | Container gegen Scrollen sperren, nur die Liste scrollen lassen | ✅ It. 3 |
| 3 | 10 | ~~Blocker~~ | ~~Schwarze Ecken am runden Launcher-Icon~~ | Geprüft: Die Icons sind transparent. Das Schwarz kam vom Übersichtsbild-Werkzeug. Die gezackte Kante wird beim Neurendern mit geglättet. | – |
| 4 | 9 | hoch | Passierte Röhren und Münzen erscheinen als riesige halbtransparente Geister vor der Kamera und verdecken den Vogel. | Schneller und vollständig ausblenden, sobald sie hinter dem Vogel sind | ✅ It. 2 |
| 5 | 10 | hoch | Store-Screenshots ohne Rahmen, Hintergrund und Claim. | Gestaltete Screens DE/EN mit Claim | offen |
| 6 | 6 | hoch | Emoji als Icons: Shop-Reiter, Schlösser, Toasts, Hand, Ton, Geschenk. | Eigenes SVG-Icon-Set | ⏸ Varianten zur Wahl |
| 7 | 6 | hoch | Fließtexte in System-Schrift statt Hausschrift. | Einheitliche Schrift | ⏸ Varianten zur Wahl |
| 8 | 7/8 | hoch | Beim Crash ist keine Rückmeldung sichtbar. | Treffer-Stern, Federn, Flash, Squash | ✅ It. 4 |
| 9 | 3 | hoch | Die Röhren blenden nach oben zu „grünen Lichtsäulen“ aus. | Röhren oben sauber enden lassen oder in den Himmel ausblenden | ✅ It. 5 (Wolkenbank) |
| 10 | 2/3 | hoch | Alle Kaufwelten haben denselben beigen Weg und denselben Rand. | Weg-Palette je Welt | ⏸ Farbwelt (Rückfrage) |
| 11 | 3 | hoch | Der Kaktus ist nicht erkennbar, Power-up-Blasen sind in der Ferne winzig. | Silhouette, Kontrast, Halo | offen |
| 12 | 1 | mittel | Die Flügel sind blass und stäbchenartig. | Flügel in Körpernähe kräftiger | ⏸ Vogel (Rückfrage) |
| 13 | 1 | mittel | Von hinten ist der Vogel eine Kugel. | Scheitelbüschel und Schwanz lesbarer | ⏸ Vogel (Rückfrage) |
| 14 | 1/5 | mittel | Shop-Vorschaubilder sind klein; gesperrte Artikel sind schlecht erkennbar. | Größere Kacheln, Preisleiste, gesperrte Artikel entsättigen | offen |
| 15 | 5 | mittel | Listen enden mitten in einer Zeile, ohne Scroll-Hinweis. | Fade-Masken, ganze Zeilen | ✅ It. 3 (Fade) |
| 16 | 5 | mittel | Das Unlock-Banner ragt über den Panelrand (360 px). | In Panelbreite halten, Text kürzen | ✅ It. 3 |
| 17 | 9 | mittel | Die gestrichelten Spurlinien laufen durch Himmel und HUD. | Linien nur auf dem Boden | offen |
| 18 | 8 | mittel | Get-Ready-Pfeile bleiben halbtransparent stehen; „hierhin“ sagt wenig. | Sauber ausblenden, Text „Spur wechseln“ | offen |
| 19 | 5/8 | mittel | Pause und Game Over dunkeln das Spiel nicht ab. | Scrim und Scale-In | ✅ It. 3 (Scrim) |
| 20 | 9 | mittel | Toasts und Zonenbanner liegen auf den Hindernissen. | Toast kompakt unter dem HUD, Banner kürzer und höher | offen |
| 21 | 2 | mittel | Die Stadt besteht aus grauen Quadern, die im Shop riesig wirken. | Fenster, Dächer, Pastelltöne | offen |
| 22 | 7 | mittel | Das Regenbogen-Power-up zeigt nur Farbe, der Magnet ist unsichtbar, die Speed-Lines sind schwach. | Regenbogen-Band, Magnet-Ring, kräftigere Linien | offen |
| 23 | 3 | niedrig | Münzen ohne Rand und Prägung. | Rand und Prägung, Glanz | offen |
| 24 | 10 | niedrig | Im Icon ist der Vogel angeschnitten, der Schnabel wirkt wie Lippen. | Vogel vollständig, mit Outline | ✅ It. 1 |
| 25 | 10 | niedrig | Die Feature-Grafik ist leer, und das Logo ist anders gefärbt als im Menü. | Logo wie im Menü, Vogel vollständig | ✅ It. 1 |
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

### Iteration 1 – Store-Assets: Splash, Launcher-Icons, Feature-Grafik (Bereich 10)

**Warum:** Der Splash zeigte beim Start jeder App noch „Flapsy“ (Blocker 1). Bereich 10 war mit
3/10 der schwächste.

**Was:**
- Neues Render-Werkzeug `scripts/render-assets.mjs` (Szene in `scripts/assets/`). Es rendert
  den echten Spielvogel und legt alles in 2D zusammen:
  - Himmelsverlauf, Sonnenstrahlen, Regenbogen und Wolken mit Plum-Kontur wie in der UI
  - „Birdy“-Schriftzug in Lilita One, Farben wie im Menü
- Vogel:
  - leicht von oben, Flügel halb gehoben, damit man ihre Fläche sieht statt „Stäbchen“
  - vollständig im Bild, mit Aufkleber-Kontur für die Lesbarkeit bei 48 px
- Neu gerendert:
  - Launcher-Icons (Legacy eckig und rund mit geglätteten Kanten)
  - adaptive Ebenen: Vogel im sicheren Bereich, Hintergrund mit Regenbogen
  - Splash hoch und quer: Regenbogen auf zwei Wolken, Vogel, Schriftzug „Birdy“
  - Store-Icon 512
  - Feature-Grafik DE, neu auch EN („Tap. Dodge. Fly through.“)
- Splash als WebP statt PNG, denn die Verläufe hätten die APK um 4,6 MB vergrößert. Die APK bleibt
  bei 7,2 MB.
- Die Hintergrundfarbe des Android-12-Splashs passt jetzt zum neuen Himmel (`#5AA9E6`).

![Iteration 1 vorher/nachher: Icon, adaptiv, Splash](visual/it1-a.jpg)
![Iteration 1 vorher/nachher: Feature-Grafik](visual/it1-b.jpg)

**Messwerte:**
- Playtest (`SEED=7`, 50 Runs je Stufe): identisch mit der Basis. Anfänger 17,4 s / 9 Punkte,
  Geübt 40,2 s / 23, Profi 180 s / 144. Keine Fehler.
- Perf: erster Frame 686 ms (Median aus 3, Budget 782 ms), 130–143 Draw Calls, 42 k Dreiecke.
  Am Spielcode hat sich nichts geändert; die Abweichung zur Basis ist Messrauschen.
- APK baut (7,2 MB).

**Neue Einschätzung:** Store-Assets 3 → 5. Der Blocker ist behoben, Icon und Splash sind
eigenständig und markentreu. Es fehlen noch gestaltete Store-Screenshots mit Rahmen und Claim.

### Iteration 2 – Keine Geisterbilder vor der Kamera (Bereich 9)

**Warum:** Passierte Röhrenreihen blendeten langsam aus und hingen als riesige, halbtransparente
grüne Flächen vor der Kamera. Verpasste Münzen flogen als große Scheiben ins Bild. Beides
verdeckte den Vogel (Mangel 4, „hoch“).

**Was:**
- Eine passierte Reihe verschwindet jetzt über 1,5 Einheiten hinter dem Vogel. Die Deckkraft
  hängt am Abstand statt an der Zeit, bei Spieltempo dauert das etwa 0,06 s.
- Verpasste Münzen und Power-ups schrumpfen 1,5–3 Einheiten hinter dem Vogel auf null.
- Nur die Optik ändert sich: Kollision, Punktevergabe und Magnet laufen wie vorher. Münzen
  bleiben einsammelbar, denn der Magnet kann sie noch zurückziehen.
- Zusätzlich: Der Katalog hält die Echtzeit-Schleife an (`freeze`), damit Vorher/Nachher-Bilder
  exakt dieselbe Szene zeigen.

![Iteration 2 vorher/nachher](visual/it2.jpg)

**Messwerte:**
- Playtest (`SEED=7`, 50 Runs je Stufe): Ergebnis für Ergebnis identisch mit der Basis
  (Anfänger 17,4 s / 9 Punkte / 8 Münzen, Geübt 40,2 s / 23 / 23, Profi 180 s / 144 / 138). Damit
  ist belegt, dass die Spiellogik unverändert ist.
- Perf: erster Frame 660 ms (Median, Budget 782), 135–141 Draw Calls, 42 k Dreiecke.
- APK baut.

**Neue Einschätzung:** Lesbarkeit 5 → 6. Offen bleiben Spurlinien im Himmel sowie Toasts und
Banner über den Hindernissen.

### Iteration 3 – UI-Layout: nichts verrutscht, nichts ragt heraus (Bereich 5)

**Warum:**
- Der Game-Over-Screen war bei EN 390×844 nach oben verrutscht: Ton, Sprache und Münzen waren
  abgeschnitten, unten erschien ein türkiser Streifen (Blocker 2).
- Das Unlock-Banner ragte über den Panelrand hinaus.
- Pause und Game Over hoben sich nicht vom Spiel ab.
- Die Erfolgsliste endete mitten in einer Zeile.

**Ursache des Blockers:** `scrollIntoView()` im Shop scrollte nicht nur das Artikel-Raster,
sondern auch den ganzen App-Container. Dieser ist `overflow: hidden`, lässt sich per Skript aber
trotzdem scrollen. Der Versatz blieb bis zum nächsten Bildschirm stehen.

**Was:**
- Das Raster scrollt jetzt gezielt selbst (`reveal`). Der App-Container setzt jede
  Scroll-Verschiebung sofort auf 0 zurück.
- Pause und Game Over liegen auf einem Scrim in Plum (45 %, blendet in 0,25 s ein).
- „Jetzt freischaltbar“ leuchtet, statt auf 112 % zu wachsen, und bleibt so im Panel.
- Die Erfolgsliste blendet unten weich aus, solange weitere Einträge folgen (wie das
  Shop-Raster).

![Iteration 3 vorher/nachher](visual/it3.jpg)

**Messwerte:**
- Playtest (`SEED=7`, 50 Runs je Stufe): identisch mit der Basis.
- Perf: erster Frame 687 ms gegenüber 645 ms der Basis, im direkten Wechsel gemessen (Budget
  +20 %). 130–143 Draw Calls, 42 k Dreiecke.
- APK baut.

**Neue Einschätzung:** UI-System 6 → 7. Offen sind die Titelzeile, die bei 360 px mit der
Münzanzeige kollidiert, Kacheln und Preise im Shop sowie die Icons (Rückfrage läuft).

### Iteration 4 – Treffer-Feedback beim Crash (Bereich 7)

**Warum:** Im Crash-Bild war keine Rückmeldung zu sehen (Mangel 8). Die Crash-Partikel waren
außerdem immer gelb, auch beim roten Kardinal oder beim Pinguin.

**Was:**
- **„Bonk“-Stern:** ein Comic-Stern mit Plum-Kontur, weißer Fläche und gelbem Kern.
  - Er sitzt auf der Seite, an der der Vogel anstößt: oben an der oberen Röhre, unten an Röhre,
    Kaktus und Boden, vorne an einer gesperrten Spur.
  - Er springt während des Freeze-Frames auf und blendet nach 0,5 s aus.
  - Ein Mesh, das nur in diesem Moment gezeichnet wird (+1 Draw Call).
- **Vogel:** Er wird im Freeze-Frame platt gestaucht (130 % breit, 72 % hoch).
- **Federn:** Die Partikel haben die Farben des gewählten Vogels (Körper, Bauch, Flügel, Weiß)
  statt immer Gelb. Es sind 36 statt 28, etwas länger in der Luft.
- **Blitz:** Der weiße Blitz ist weicher (55 % statt 90 %), damit Stern und Vogel sichtbar
  bleiben.

![Iteration 4 vorher/nachher: Crash](visual/it4.jpg)

**Messwerte:**
- **Playtest:** Mehr Crash-Partikel verbrauchen andere Zufallszahlen. Deshalb ist der
  Seed-Vergleich nicht mehr Run für Run gleich, sondern wird über drei Seeds verglichen (Basis
  gegen neu):

  | Seed | Anfänger | Geübt | Profi |
  |---|---|---|---|
  | 7 | 17,4 s / 9 → 17,5 s / 9 | 40,2 s / 23 → 37,4 s / 21 | 144 → 142 Punkte |
  | 11 | 15,9 s / 8 → 15,9 s / 8 | 38,8 s / 22 → 37,6 s / 22 | 141 → 144 |
  | 23 | 15,9 s / 8 → 16,1 s / 8 | 35,0 s / 20 → 36,3 s / 22 | 141 → 142 |

  Die Abweichungen liegen in beide Richtungen und im Rauschen. Die Spiellogik ist nicht
  verändert.
- **Perf:** Im direkten Wechsel gemessen: erster Frame 733 ms gegenüber 738 ms der Basis.
  136–139 Draw Calls, 41–42 k Dreiecke.
- **APK:** baut.

**Neue Einschätzung:** Effekte 4 → 5. Offen: Regenbogen-Band, Magnet-Ring, kräftigere
Speed-Lines, Münz-Effekte.

## Review 1 (nach Iteration 3) – neuer Art-Director-Subagent, nur Screenshots

Schnitt **5,0**. Die Scorecard oben ist übernommen, Noten wurden nicht angehoben. Der Reviewer ist
strenger als in Runde 0 (Vogel 6 → 5, Hindernisse 5 → 4). Zwei Abzüge gehen auf den Katalog
zurück, nicht auf das Spiel: Das Erfolge-Panel und die Zonenbanner wurden mitten in der
Einblend-Animation fotografiert, weil die Seite unter Last zu langsam lief. Seit dieser Runde
setzt der Katalog CSS-Animationen vor jeder Aufnahme auf einen festen Zeitpunkt. Der Crash im
Katalog stammt noch von vor Iteration 4.

Wichtigste neue oder bestätigte Punkte (Rangfolge des Reviewers):

1. **Store-Screenshots (Blocker):** rohe Spielbilder ohne Rahmen und Claim, mit Spurpunkten und
   EN-Knopf.
2. **Röhren (Blocker):** Sie wirken generisch und laufen oben durchsichtig aus. Sie sind in jeder
   Welt grün.
3. **Vogel von hinten (hoch):** Kugel ohne Gesicht, beige Stäbchenflügel. Das ist eine
   Vogel-Änderung und braucht deine Entscheidung.
4. **Spurlinien (hoch):** Sie gehen über den ganzen Bildschirm bis in den Himmel.
5. **Pickups und Kaktus (hoch):** zu klein und zu schwach in der Silhouette.
6. Die Spurpunkte „○●○“ lesen sich wie Seitenpunkte eines Karussells.
7. Der Mini-Vogel ist fast unsichtbar.

„Was bringt jeden Bereich auf 8“ ist im Review aufgeführt und fließt in die nächsten Iterationen ein.

### Iteration 5 – Röhren hängen aus einer Wolkenbank (Bereich 3)

**Warum:** Laut Review 1 ist das ein Blocker. Die oberen Röhren liefen über zehn Einheiten
halbtransparent in den Himmel aus und wirkten wie grüne Lichtsäulen oder ein Renderfehler.

**Was:**
- Jede Röhrenreihe hat oben eine Bank aus flachen Low-Poly-Wolken auf Höhe 16. Die oberen Röhren
  kommen sichtbar aus den Wolken, die Ausblendung ist auf 15–19 verkürzt und liegt jetzt
  innerhalb der Bank.
- Die Bank hat dieselbe Tönung wie die Himmelswolken der Zone und ist leicht von innen
  aufgehellt. So wirken die Unterseiten von unten weich statt steingrau.
- Drei gebackene Varianten mit eigenem kleinem Zufallsgenerator, ein Draw Call pro Reihe.
- Die Trefferzonen bleiben unverändert: Die Röhren sind weiterhin 40 Einheiten hoch.

![Iteration 5 vorher/nachher](visual/it5.jpg)

**Messwerte:**
- **Playtest:** Drei.js vergibt beim Anlegen neuer Objekte Zufalls-IDs. Deshalb ändert jede neue
  Geometrie die gesäte Zufallsfolge, und der Vergleich läuft über drei Seeds (Basis → neu):
  - Seed 7: Anfänger 17,4 → 15,9 s, Geübt 40,2 → 37,8 s, Profi 144 → 144 Punkte
  - Seed 11: 15,9 → 17,4 s, 38,8 → 36,1 s, 141 → 144
  - Seed 23: 15,9 → 17,4 s, 35,0 → 36,9 s, 141 → 142

  Die Abweichungen gehen in beide Richtungen und liegen im Rauschen.
- **Perf:** Im direkten Wechsel gemessen: erster Frame 721 ms gegenüber 648 ms der Basis
  (+11 %, Budget +20 %). Bis zu 152 Draw Calls (Budget 170), bis zu 46,7 k Dreiecke (Budget 90 k).
- **APK:** baut.

**Neue Einschätzung:** Hindernisse 4 → 5. Die Röhren haben jetzt einen echten Abschluss. Offen
bleiben die Größe der Pickups, die Silhouette des Kaktus und die Münzen.
