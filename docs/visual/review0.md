# Birdy – Visuelles Review (Runde 0)

Grundlage: nur die Screenshot-Sheets `review0/01…21`. Kein Code gesehen. Maßstab sind Crossy Road, Alto's Adventure, Subway Surfers, Smash Hit und Monument Valley. Animationen kann ich nur aus Standbildern ableiten; wo das der Fall ist, steht es dabei.

**Gesamteindruck:** Birdy ist ein ordentliches Indie-Spiel mit klarer Idee und einem freundlichen, durchgehenden Farbklima. Es hat aber noch sichtbare Rohbau-Stellen: Emoji als Icons, eine System-Fallback-Schrift, Geisterbilder durch nahe Objekte vor der Kamera, ein falsch benannter Splash und Store-Assets ohne Rahmen. Durchschnitt **5,1 / 10**.

## 1. Visual-Scorecard

| # | Bereich | Note | Begründung |
|---|---|---|---|
| 1 | Vogel & Kosmetik | **6** | In der Seitenansicht im Shop wirkt der Vogel charmant (05-shop-4-eyes, 05-shop-5-beak). Von hinten und von vorn ist er eine gelbe Facettenkugel mit blassen, knochenartigen Flügeln (01-tutorial-flap, 03-menu, 04-achievements). Die Vorschaubilder im Shop sind winzig, unscharf und im gesperrten Zustand kaum unterscheidbar (05-shop-2-pattern, 05-shop-3-hat). |
| 2 | Welten & Szenerie | **6** | Die Zonen Herbstwald, Canyon und Blütenhain haben eigene Paletten und Props (06-zones), die Kaufwelten sind thematisch klar (07-worlds). Weg-Textur, Röhren und Randstreifen bleiben aber überall gleich. Die Stadt besteht aus untexturierten Grau-Quadern, die im Shop riesig ins Bild ragen (05-shop-1-skin, 05-shop-3-hat). |
| 3 | Hindernisse & Pickups | **5** | Die Röhren sind generische Mario-artige Zylinder, die nach oben zu grünen „Lichtsäulen“ ausfaden (04-run, 07-worlds). Münzen sind flache Scheiben ohne Prägung. Die Power-up-Blasen sind in der Distanz stecknadelgroß (09-pickups). Der Kaktus ist im Kaktus-Screenshot nicht zu erkennen (15-cactus). |
| 4 | Licht, Farbe & Atmosphäre | **6** | Die Himmelsverläufe je Zone sind stimmig: violett-rosa im Canyon, warm im Herbst (06-zones, 05-powerups-cactus). Es gibt nur Blob-Schatten, die Röhren werfen keinen Schatten. Der Nebel frisst den Horizont zu flacher Farbe, und in der Candy-Welt beißen sich grüne Röhren mit Rosa (16-world-candy). |
| 5 | UI-System | **6** | Die Panels haben ein konsistentes Rezept aus Creme, Plum-Outline und 3D-Lippe (03-menu, 19-gameover). Es gibt aber abgeschnittene Listen (04-achievements, 05-shop-1-skin), ein über den Rand laufendes Unlock-Banner (11-en-360 19-gameover) und einen verrutschten Game-Over-Screen (10-en-390 19-gameover). |
| 6 | Typografie & Icons | **4** | Drei Schriften stehen nebeneinander: Display-Rundschrift, eine DejaVu-artige Fallback-Grotesk in Missionen und Pause-Text sowie eine andere Schrift für die Sheet-Beschriftungen. Die Icons sind ein Emoji-Mix (🎨🐾🎩🕶🐤✨🌍⚡🎲🔒🏆🎁👆🔊, 02-shop-a, 01-tutorial-menu). |
| 7 | Effekte & Partikel | **4** | Die Trails sind einfache Hexagon-Punkte (07-run-zone1). Der Regenbogen färbt den Vogel nur rosa (10-power-star), Speed-Lines sind kaum sichtbar, und ein Magnetfeld sieht man nicht (11-power-magnet). Beim Crash gibt es keinen sichtbaren Treffer-Effekt (18-crash). |
| 8 | Animation & Übergänge | **5** | *(aus Standbildern abgeleitet)* Die Zonenbanner liegen statisch mittig. Der Crash-Frame ist identisch mit dem Spiel-Frame, also ohne Flash, Squash oder Kameraruck (18-crash). Hinter Pause und Game Over wird nicht abgedunkelt (17-pause, 19-gameover). Reste der Get-Ready-Pfeile bleiben sichtbar (16-world-beach, 16-world-mushroom). |
| 9 | Lesbarkeit im Spiel | **5** | Nahe Röhren und Münzen werden zu riesigen halbtransparenten Geistern, die den Vogel verdecken (14-run-zone2-autumn, 14-run-zone3-canyon, 07-run-zone1). Die Spurlinien laufen durch den Himmel. Toasts und Banner liegen auf den Röhrenköpfen. Das HUD selbst ist gut lesbar. |
| 10 | Store-Assets | **3** | Der Splash sagt „**Flapsy**“ statt Birdy (20-icon-splash-feature). Der runde Launcher hat schwarze Ecken und gezackte Kanten. Im Icon ist der Vogel angeschnitten und hat „Entenlippen“. Die Store-Screens sind rohe Spielframes ohne Rahmen und Claim (21-store-screens-de). |

## 2. Priorisierte Mängelliste

| Rang | Bereich | Schwere | Problem (Wo) | Lösungsvorschlag |
|---|---|---|---|---|
| 1 | 10 | Blocker | Das Splash-Bild zeigt den Namen **„Flapsy“** statt „Birdy“ (20 splash). | Splash neu rendern mit dem Birdy-Logo in denselben Farben wie in 03-menu. Einen Check in den Asset-Build aufnehmen, der alle Store- und Splash-Texte gegen den App-Namen prüft. |
| 2 | 5 | Blocker | Der Game-Over-Screen ist bei EN 390 verrutscht: Oben sind Sound, Sprache und Münzen (2401) abgeschnitten, unten erscheint ein türkiser Streifen (10-en-390 19-gameover). | Das Overlay an einem festen Viewport verankern (`position:fixed; inset:0`) und kein Scroll-Offset übernehmen. Die Panelhöhe auf `min(…, 100dvh − Safe-Areas)` begrenzen. |
| 3 | 10 | Blocker | Der runde Launcher hat schwarze Ecken und harte, gezackte Kreiskante. Der Legacy-Launcher hat stufige Ecken (20 launcher, round). | Adaptive Icons (Vorder- und Hintergrund-Layer, 108 dp mit 72 dp Safe-Zone) liefern. Legacy und Round mit Alpha und Antialiasing (Supersampling 4×) rendern, ohne schwarzen Hintergrund. |
| 4 | 9 | hoch | Nahe Röhren und Münzen erscheinen als riesige, halbtransparente, unscharfe Geister vor der Kamera und verdecken den Vogel (14-run-zone2-autumn, 14-run-zone3-canyon, 07-run-zone1 unten). | Objekte ausblenden, sobald sie hinter den Vogel geraten (Fade über 0,15 s bei z > Vogel-z + 1), oder die Near-Plane bzw. das Culling so setzen, dass nichts vor dem Vogel ins Bild ragt. Keine Alpha-Überblendung auf Vollbildgröße. |
| 5 | 10 | hoch | Die Play-Store-Screens sind rohe Gameplay-Frames ohne Rahmen, Headline und Hintergrund (21-store-screens-de). | 5–7 gestaltete Screens mit Strahlen-Hintergrund wie in der Feature-Grafik, Gerätekontur oder abgerundeter Maske und einem Claim pro Bild (z. B. „4 Zonen erkunden“, „Über 60 Looks“). Dazu großes Logo auf Screen 1. |
| 6 | 6 | hoch | Emoji dienen als Icons in Shop-Tabs, Achievements, Toasts, Tutorial-Hand, Sound-Button und Geschenk-Button. Sie rendern je nach Gerät unterschiedlich und passen nicht zum Low-Poly-Stil (02-shop-a, 01-tutorial-menu, 04-run). | Einen eigenen Icon-Satz als SVG anlegen: 24er-Raster, 2,5 px Plum-Outline, flache Zweitonfüllung, in der UI-Palette. Alternativ die Icons aus den eigenen 3D-Modellen rendern (Hut, Brille, Schnabel). |
| 7 | 6 | hoch | Fließtexte wie Missionen, Achievement-Beschreibungen, „Tippen zum Weiterspielen“ und Upgrade-Text laufen in einer System-Fallback-Grotesk, nicht in der Hausschrift (03-menu, 04-achievements, 17-pause). | Eine zweite eigene Webfont als Body (z. B. Nunito ExtraBold) einbetten und als einzige Body-Schrift festlegen. Alle Font-Stacks prüfen. |
| 8 | 7/8 | hoch | Der Crash hat keine visuelle Rückmeldung; der Frame sieht aus wie normales Spiel (08 18-crash). | Weißer Vollbildflash für 80 ms, 6–10 gelbe Feder-Partikel, Kamera-Shake mit 0,2 s Dauer und Squash des Vogels. Danach 0,3 s Zeitlupe, bevor Game Over einblendet. |
| 9 | 3 | hoch | Die Röhren sind ab halber Höhe ein weicher Alpha-Verlauf und wirken wie grüne Lichtsäulen oder ein Rendering-Fehler (04-run, 07-worlds, 05-powerups-cactus). | Röhren oben mit einer zweiten Kappe abschließen oder in den Himmelsnebel auslaufen lassen (Fog-Farbe statt Alpha). Kein Transparenz-Stack, das spart auch Overdraw. |
| 10 | 2/3 | hoch | Alle Kaufwelten nutzen denselben beigen Streifenweg, dieselben hellgrünen Kanten und grüne Röhren. Auf Schnee und Candy wirkt das als Fremdkörper (16-world-winter, 16-world-candy). | Pro Welt eine Weg-Palette (Schnee: #EAF2F8 und #CFDDEA; Candy: #F7C6DA und #EFA3C3) und eine passende Standard-Röhrenfarbe setzen, zum Beispiel über eine Uniform-Tönung. |
| 11 | 3 | hoch | Der Kaktus ist im Kaktus-Screenshot nicht identifizierbar; die Power-up-Blasen sind in Spieldistanz 5–8 px groß (15-cactus, 09-pickups). | Kaktus mit kräftigem Grün, weißen Stacheln und rotem Blütenakzent; Silhouette mindestens 1,3× Röhrenbreite. Power-ups mit leuchtendem Halo-Ring und Bob-Animation, Mindestgröße im Bild etwa 18 px. |
| 12 | 1 | mittel | Die Flügel sind blass-creme, dünn und stäbchenartig und wirken wie Knochen (03-menu, 04-achievements, 01-tutorial-flap). | Flügel breiter anlegen (3–4 Federstufen), Farbe einen Ton dunkler als der Körper (#E8B820), damit sie sich vom Weg abheben. |
| 13 | 1 | mittel | Von hinten und von vorn ist der Vogel eine gesichtslose Kugel, weil Augen und Schnabel kaum sichtbar sind (06-run-ready, 03-menu). | Menükamera auf 3/4-Ansicht drehen. In der Rückansicht Schwanzfedern und Scheitelbüschel ergänzen und die Augen seitlich leicht herausstehen lassen. |
| 14 | 1/5 | mittel | Die Vorschaubilder im Shop sind klein, verwaschen und von Schloss- und Preisbadges überdeckt. Gesperrt und ungekauft sehen gleich aus (05-shop-2-pattern, 05-shop-3-hat, 05-shop-4-eyes). | Größere Kacheln (4 statt 5 Spalten, Thumbnails 56 px). Preis als Leiste unter der Kachel statt als Overlay. Gesperrte Kacheln leicht entsättigen, ausgewählte mit orangem Ring und Häkchen. |
| 15 | 5 | mittel | Der Grid-Inhalt wird mitten in einer Zeile abgeschnitten, ohne Scrollhinweis (05-shop-1-skin, 05-shop-6-trail). Die Achievement-Liste endet halb verdeckt („Nervenkitzel“, 04-achievements). | Innere Scrollfläche mit Fade-Maske oben und unten und einem kleinen Scrollbalken. Die Höhe so wählen, dass ganze Zeilen enden, oder nach unten eine halbe Zeile als Hinweis zeigen. |
| 16 | 5 | mittel | Das Unlock-Banner im Game Over bricht auf zwei Zeilen um und ragt über die Panelkanten (11-en-360 19-gameover, 09-small-de 19-gameover). | Das Banner in der Panelbreite halten (`margin-inline:12px`) und den Text auf 1–2 Zeilen kürzen („Bäckchen freischaltbar ›“). |
| 17 | 9 | mittel | Die gestrichelten Spurlinien laufen als Bildschirm-Overlay bis in den Himmel und durch das HUD (alle Run-Screens). | Spurmarkierungen als Bodenstreifen im 3D-Raum zeichnen, die nur auf dem Weg liegen und nach vorn ausfaden. |
| 18 | 8 | mittel | Die Get-Ready-Pfeile und -Texte („hierhin“, „flattern“) bleiben im Lauf halbtransparent stehen (16-world-beach, 16-world-mushroom). „hierhin“ sagt zudem wenig. | Beim ersten Tap in 0,2 s ausblenden. Texte ändern zu „Spur wechseln“ und „Flattern“. |
| 19 | 5/8 | mittel | Pause und Game Over dunkeln das Spiel nicht ab, das Panel konkurriert mit den Röhren. Die Score-Anzeige wird bei Pause grau (17-pause, 19-gameover). | Scrim mit #2B1E2E bei 45 % Deckkraft hinter den Overlays. Panel mit Scale-In 0,9 → 1 und Ease-out-back. |
| 20 | 9 | mittel | Toasts und Zonenbanner liegen mittig auf den Röhrenköpfen und damit im Blickfeld auf Hindernisse (13-zone-banner-4, 08-toast, 11-power-magnet). | Toasts unter die HUD-Zeile setzen (y ≈ 110 px), kompakt halten und nach 1,2 s nach oben ausblenden. Das Zonenbanner nur 1 s zeigen und oberhalb des Horizonts platzieren. |
| 21 | 2 | mittel | Die Stadt besteht aus flachen grauen Quadern ohne Fenster oder Dachdetail. Im Shop füllen sie groß und leer das Bild (05-shop-1-skin, 05-shop-9-upgrade). | Fensterraster über generierte Textur oder Vertex-Color, dazu Dachkanten in 2–3 Pastelltönen. Im Shop einen näheren, gepflegten Hintergrund zeigen (Park-Diorama, Himmel mit Strahlen). |
| 22 | 7 | mittel | Das Regenbogen-Power-up färbt den Vogel nur rosa und hat einen türkisen Punkte-Trail. Speed-Lines sind kaum sichtbar, und ein Magnet-Radius ist nicht erkennbar (10-power-star, 11-power-magnet). | Echtes Regenbogen-Band als Ribbon-Trail aus sechs Farben, Vogel mit Hue-Cycling. Speed-Lines dicker (2–3 px, weiß bei 70 %). Magnet: pulsierender Ring und Münzen, die mit kurzem Schweif heranfliegen. |
| 23 | 3 | niedrig | Münzen sind flache gelbe Scheiben ohne Rand, Prägung oder Glanz; nah vor der Kamera wirken sie wie Pfannkuchen (06-zones, 07-run-zone1). | Münze mit abgesetztem Rand und geprägtem Vogel- oder Sternsymbol, 12–16 Segmente, dazu Rotation und ein kurzer Glint. |
| 24 | 10 | niedrig | Das Icon hat einen angeschnittenen Vogel; der Schnabel liest sich als rote Lippen, und die Augen sind größer als im Spiel (20 icon512). | Vogel in 3/4-Ansicht, vollständig innerhalb von 80 % der Fläche, mit spitzem, einteiligem Schnabel und der Augenproportion aus dem Spiel. |
| 25 | 10 | niedrig | Die Feature-Grafik hat viel leere Strahlenfläche; der Vogel ist rechts angeschnitten, und das Logo ist anders gefärbt als im Menü (20 feature vs. 03-menu). | Die Logo-Farbgebung vereinheitlichen und eine Gameplay-Andeutung (Röhrenreihe, Münzen) ergänzen. Den Vogel vollständig und größer zeigen. |
| 26 | 4 | niedrig | Es gibt nur Blob-Schatten, die Röhren sind ohne Bodenkontakt. Der Horizont versinkt in flacher Nebelfarbe (06-zones). | Pro Röhre einen gebackenen Kontaktschatten als Decal (ein Quad, bleibt im Budget). Den Horizont mit einer Silhouettenkette (Hügel, Mesas) im Zonenton staffeln. |
| 27 | 5 | niedrig | Die drei Pagination-Punkte unten im Spiel wirken wie ein Karussell und nicht wie eine Spuranzeige (alle Run-Screens). | Durch drei kleine Spurpfeile ersetzen, bei denen die aktive Spur als Vogelsilhouette hervorgehoben ist, oder ganz entfernen. |
| 28 | 6 | niedrig | Das Logo „Birdy“ ist zweifarbig (Orange und Grün auf dem „y“) und wirkt unausgewogen; die Titel sind orange, das Logo gelb-orange (03-menu). | Ein festes Logo-Asset mit Verlauf #FFC21A → #F28C0F, einer Outline in #4A2E3A und einem Vogelakzent statt eines grünen Buchstabens. |

## 3. Styleguide-Empfehlungen

### Palette (aus der UI abgelesen, circa-Werte)
| Rolle | Hex (ca.) | Beobachtung |
|---|---|---|
| Panel-Fläche | `#E3DB9C` | Creme-Khaki, im Canyon leicht grünstichig. Empfehlung: wärmer, `#F4EBC4`. |
| Panel- und Button-Outline | `#4A3A3E` | Plum-Braun, einheitlich gut. |
| Innere Karten (Missionen) | `#D2C68A` | Zu geringer Kontrast zum Panel. |
| Primär-CTA (Los geht's, Nochmal) | `#F26A1C` | |
| Kaufen und Tagesgeschenk | `#FFBE0B` | Zwei Gelbtöne im Umlauf (Banner `#FFC400`). Vereinheitlichen. |
| Sekundär (Zurück, Menü, Toast) | `#6DBF2E` | Toast-Grün ist identisch mit dem Button, dadurch wirkt der Toast klickbar. |
| Überraschung und Zufall | `#7B70D8` | Einzige Violett-Nutzung, okay als Akzent. |
| Disabled (Ausgewählt) | `#C8BE8A` | Liest sich wie ein kaputter Button. Besser Grün mit Häkchen. |
| Titel | `#F36A1E` mit Outline `#4A2E3A` | Logo-Gelb `#FFC21A` und Grün `#6DBF2E`. |
| HUD-Zahlen | `#FFFFFF`, Outline `#3A2E3A` | Gut lesbar. |
| Münzpille | `#3A3A4A` bei ca. 60 % Deckkraft | Einzige dunkle Pille im System, sonst gibt es nur Creme-Buttons. Vereinheitlichen. |

### Formen
- **Eckradien:** Panel ca. 14 px, große Buttons ca. 10 px, Tabs und Kacheln ca. 6–8 px, Münzpille voll rund, Toast ca. 10 px. Vorschlag für eine Skala: 6 / 10 / 16 / voll.
- **Outline:** Panels ca. 3 px, Buttons 3 px, Kacheln 2 px, Tutorial-Pille ohne Outline (inkonsistent). Festlegen: 3 px für interaktive Elemente und Panels, 2 px für Kacheln.
- **3D-Lippe:** Buttons haben eine dunkle Unterkante von ca. 4 px. Die Sound- und Sprach-Buttons haben sie ebenfalls, die Toasts auch, obwohl sie nicht klickbar sind. Toasts sollten flach sein.
- **Abstände:** Innenabstand im Panel ca. 16 px, Buttonabstand 8–10 px, unregelmäßig. Ein 8-px-Raster einführen.

### Typo-Skala (Vorschlag, bei 390 px Breite)
- Display (Logo, Titel): 40 px, Rundschrift, Outline 3 px.
- Zonenbanner: 32 px / Unterzeile 16 px.
- Button L: 22 px, Button M: 16 px.
- Body und Missionen: 13–14 px in **derselben** eingebetteten Body-Schrift, nicht als System-Fallback.
- Caption (Preise, Stufen): 11 px, fett.
- Inkonsistenzen: „Tippen zum Weiterspielen“ steht in dünner Systemschrift, die Missionstexte in DejaVu-Bold-ähnlicher Schrift, der Rest in der Display-Rundschrift.

### Icons
- Emoji vollständig ersetzen. Stil: eigenes SVG, 24×24-Raster, 2–2,5 px Outline in `#4A3A3E`, zweitonige flache Füllung aus der Palette, abgerundete Enden.
- Tab-Icons mit Label darunter (bereits so) und fester Icon-Größe 20 px. Aktiver Tab orange mit weißem Icon.
- Tutorial-Hand als eigene Vektorhand in Creme mit Plum-Outline statt 👆.
