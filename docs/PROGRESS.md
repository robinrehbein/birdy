# Birdy 3D – Bewertungsbogen & Iterations-Log

Ziel: jeder Bereich ≥ 8/10, jede Bewertung mit Beleg.
Messwerkzeuge:

- `node scripts/playtest.mjs 50` – Bot-Playtest (headless, feste 60-Hz-Simulation). Drei Bots mit
  denselben Ein-Tipp-Regeln wie auf dem Handy:
  - **Anfänger:** 0,22 s Reaktionszeit, ungenau, übersieht oft die obere Röhre.
  - **Geübt:** 0,15 s Reaktionszeit.
  - **Profi:** keine Verzögerung, sagt bewegte Lücken und Pflanzen voraus. Dient zum Aufspüren
    unfairer Reihen.
- `node scripts/perf.mjs` – Draw Calls, Dreiecke und Zeit bis zum ersten Frame.
  Headless-GPU ist Software; fps sind dort nicht aussagekräftig.

Bot-Werte sind Näherungen an echte Spieler, keine Messung an Menschen.

## Bewertungsbogen (aktuell)

Nach dem ersten Publisher-Review (Iteration 3) habe ich überhöhte Noten korrigiert.

| # | Bereich | Note | Beleg / Begründung |
|---|---------|------|--------------------|
| 1 | Onboarding | 5 | Anfänger-Bot: erster Tod im Median bei 17,4 s. Aber: Die Erklärung steht nur als Text im Menü. Die Steuerung ist jetzt einfacher erklärbar („beim Vogel = flattern, daneben = ausweichen“), der Zielring zeigt die Höhe. Bot-Überlebenszeit ist kein Beleg für Verständnis. |
| 2 | Game Feel / Juice | 7 | Jede Spieleraktion hat visuelles und akustisches Feedback, die wichtigen zusätzlich Vibration (Tabelle in Iteration 7). Squash & Stretch, Hit-Stop 0,14 s, „Knapp!“-Serien, Tempo-Kick. Offen: nicht auf einem Gerät gefühlt; keine Tempo-Linien, keine Kamera-Neigung. |
| 3 | Fairness & Kurve | 6 | Profi-Bot überlebt 94 % der 150-s-Runs dank garantierter Erreichbarkeit. Aber: Der Bot kennt die exakten Lückenhöhen. Die Tiefenwahrnehmung von Menschen ist nicht geprüft, die Kurve endet bei 40 Punkten. |
| 4 | Abwechslung | 3 | Ab Punkt 10 ist alles freigeschaltet, es gibt nur ein Biom. |
| 5 | Session-Loop | 7 | Runs im Median: Anfänger 16 s, geübt 41 s ✓. Neustart per Tipp irgendwo 0,35 s nach dem Game-Over, danach schwebt der Vogel, bis getippt wird (getestet). „Nur noch X bis zum Rekord“. Offen: Game-Over erscheint erst ca. 1 s nach dem Crash. |
| 6 | Meta-Progression | 6 | Münzen, 7 Skins, 3 Tagesmissionen passend zum Rekord, Tagesgeschenk mit Serie (20 → 80 Münzen). Fehlt: Zufalls-Freischaltung, Erfolge, zweite Kosmetik-Kategorie. |
| 7 | Audio | 4 | Korrektur: Der Loop dauert nur 7,7 s (64 Sechzehntel bei 124 BPM), nicht 31 s. Menü und Spiel nutzen denselben Loop, das wiederholt sich stark. |
| 8 | Performance | 6 | 892 → **165 Draw Calls** (Szenerie und Röhren zusammengefasst, Optik unverändert). Automatische Qualitätsstufen (DPR 2 → 1,5 → 1,25 → 1 → ohne Schatten, mit Ersatz-Schatten). Offen: Messung auf einem echten Gerät (Anzeige: 5× auf den Titel tippen). |
| 9 | Politur | 5 | Panels sauber. Münzen wirken bräunlich (Metall ohne Umgebungslicht). Der Ring-„Pop“ einer passierten Reihe wird nahe der Kamera riesig. Der Shop zeigt nur Farbpunkte. |
| 10 | Store-Reife | 1 | Debug-APK, Platzhalter-App-ID, kein Release-Signing, kein Datenschutztext. Die Android-Zurück-Taste wird nicht behandelt. |

## Backlog (nach Hebel sortiert)

1. Abwechslung: Biome oder Tageszeiten alle ca. 25 Punkte, Musik-Varianten für Menü und Spiel, längerer Loop.
2. Politur: Münz-Material, Shop-Vorschau.
3. Onboarding: Geister-Hand im ersten Run statt Text.
4. Store: Zurück-Taste, Release-Build-Setup, Store-Texte, Datenschutz, Screenshots.

## Review 1 (nach Iteration 3) – Publisher-Subagent

Urteil: Soft Launch „heute nein“. Die drei wichtigsten Kritikpunkte:

| Rang | Einstufung | Kritik |
|---|---|---|
| 1 | Blocker | Tiefen-Lesbarkeit und Fehltipps an den Zonengrenzen |
| 2 | Blocker | Performance auf Mittelklasse-Android |
| 3 | wichtig | Neustart ohne Bereit-Zustand; kaum Grund, am nächsten Tag wiederzukommen |

Der Reviewer fand die Noten für Audio, Fairness, Onboarding, Session-Loop und Meta überhöht. Ich habe sie oben korrigiert.

## Iterations-Log

### Iteration 1 – Messbasis, Onboarding & Fairness beim Spurwechsel

**Was:**
- Headless-Simulation (`?sim`) mit Bot-Spielern und Erfassung der Todesursachen.
- Tipp auf eine andere Spur wechselt die Spur mit einem kleinen Hüpfer statt einem vollen
  Flügelschlag.
- Die ersten 6 Reihen sind breiter, liegen nahe der Starthöhe und haben alle Spuren offen.
- Bugfix: Wenn alle 8 Röhren-Reihen im Pool belegt waren, fror das Spiel ein (Endlosschleife in
  `spawnGate`). Die Schleife rückt jetzt immer vor, der Pool ist auf 12 vergrößert.

**Warum:**
- Der Anfänger-Bot starb in 100 % der Fälle vor 12 s.
- Jeder Spurwechsel erzwang einen vollen Flügelschlag und führte oft zum Anstoßen oben.

**Messung (50 Runs je Bot, gleicher Bot-Code):**

| Bot | Überlebenszeit Median vorher → nachher | 10 % der Runs enden vor … vorher → nachher | Punkte Median vorher → nachher |
|-----|------|------|------|
| Anfänger | 8,4 s → **11,9 s** | 5,0 s → **8,4 s** | 3 → **6** |
| Geübt | 24,8 s → **28,6 s** | 19,3 s → **20,8 s** | 14 → **17** |
| Profi | 37,7 s → 40,3 s | 26,1 s → 29,9 s | 25 → 28 |

### Iteration 2 – Fairness: erreichbare Reihen, zeitbasierter Abstand

**Was:**
- Jede neue Reihe wird so erzeugt, dass von jeder Lücke der Vorreihe aus mindestens eine Lücke
  erreichbar ist. Die erlaubte Höhendifferenz richtet sich nach der Zeit zwischen den Reihen,
  Fallen geht schneller als Steigen, ein Spurwechsel kostet 40 % Spielraum.
- Bewegte Lücken zählen nur, wenn ihr ganzer Bewegungsbereich erreichbar ist.
- Der Reihenabstand richtet sich nach der Zeit statt nach der Strecke: 1,7 s zu Beginn,
  1,1 s bei Höchsttempo (vorher bis zu 0,76 s).
- Piranha-Pflanzen ragen voll ausgefahren nur noch 0,9 statt 1,35 Einheiten in die Lücke.
  So bleibt Platz für einen vollen Flügelschlag.
- Bot-Korrekturen:
  - Kein doppelter Tipp mehr nach einem Spurwechsel.
  - Der Wechsel erfolgt früh statt kurz vor der Röhre.
  - Der Profi-Bot plant zwei Reihen voraus.

**Warum:** Die Profi-Aufzeichnungen zeigten immer dasselbe Muster. Nach einer hohen Lücke war die
eigene Spur blockiert, und die Lücke in der Nachbarspur lag 2,5 bis 4 Einheiten tiefer. Dafür
blieben rund 0,7 s – das ist auch mit perfekter Reaktion kaum zu schaffen.

**Messung (100 Runs je Bot, gleicher Bot-Code für vorher und nachher):**

| Bot | Überlebenszeit Median vorher → nachher | 10 % der Runs enden vor … vorher → nachher | Punkte Median | Todesursachen nachher |
|-----|------|------|------|------|
| Anfänger | 16,4 s → **17,4 s** | 8,6 s → **11,4 s** | 8 → 9 | untere Röhre 89 %, Boden 11 % |
| Geübt | 34,2 s → **42,5 s** | 26,1 s → **27,4 s** | 22 → 28 | untere Röhre 70 %, Boden 16 %, Pflanze 11 %, obere Röhre 3 % |
| Profi | 56 s → **≥ 150 s** (Limit) | 48 s → **150 s** | 49 → 128 | Tod nur noch in 6 % der Runs (vorher 100 %) |

Beim Hüpfer für den Spurwechsel habe ich drei Varianten getestet:

| Hüpfer | Überlebenszeit Anfänger (Median) |
|-----|-----|
| +6 | 18,6 s |
| +2 | 16,5 s |
| ohne | 13,4 s |

Der Hüpfer +6 bleibt.

### Iteration 3 – Meta-Progression: Münzen, Skins, Tagesmissionen

**Was:**
- `src/progress.js`: Münzen, Rekord, Runden, freigeschaltete Skins und Missionen, alles lokal in
  localStorage. Ohne Account, offline. Der alte Rekord wird übernommen.
- **7 Vogel-Skins:** Sunny, Himmel, Kardinal, Minze, Flamingo, Nachteule, Goldvogel. Gleiches
  Modell und gleicher Low-Poly-Look, nur andere Farben. Preise 100–1500 Münzen.
- **Shop („🐦 Vögel“):** Die Kamera schwenkt auf die Seitenansicht des Vogels, eine Auswahl
  färbt den echten 3D-Vogel sofort um. Beim Kauf gibt es Konfetti und einen Sound.
- **3 Tagesmissionen** aus 8 Vorlagen mit wachsender Schwierigkeit und +40/+70/+120 Münzen.
  Sie sind nach Datum festgelegt, für alle Spieler gleich. Beim Erreichen erscheint mitten im
  Flug eine Einblendung.
- **Startmenü neu:** Titel oben, Vogel in der Mitte, Panel unten. In den ersten 2 Runden
  steht dort die Steuerungs-Erklärung, danach die Tagesmissionen.
- **Game-Over:** erfüllte Missionen, „Nur noch X bis zum Rekord!“, Münz-Anzeige, Buttons
  „Nochmal“ und „Menü“.

**Warum:** Meta-Progression war mit 1/10 der schwächste Bereich. Münzen hatten keinen Zweck,
und es gab keinen Grund, morgen wiederzukommen.

**Beleg:**
- Screenshots von Startmenü, Shop, Kauf und Game-Over, ohne Konsolenfehler.
- Logik-Test: Ein Run mit 25 Münzen erfüllt „Sammle 20 Münzen“ (+40 → 65 Münzen), ein zweiter
  Run erfüllt die übrigen Missionen (285 Münzen). Der Kauf von „Himmel“ zieht 100 Münzen ab,
  der Kauf von „Gold“ wird bei zu wenig Münzen abgelehnt.
- Ökonomie: Der geübte Bot sammelt im Median 25 Münzen pro Run. Mit den Missionen ist der
  erste Skin also nach etwa 3 Runs erreichbar, alle Skins (4300 Münzen) nach etwa 3 Wochen
  täglichen Spielens.

### Iteration 4 – Performance: Draw Calls −81 %, adaptive Qualität

**Was:**
- `bakeGroup()` fasst statische Teile zu einer Geometrie mit Vertex-Farben zusammen, bei
  gleichen Formen, Farben und Flat Shading:
  - **Szenerie:** Büsche, Bäume und Gebäude liegen in 9 Blöcken à 25 Einheiten, die beim
    Weiterfliegen nach hinten wandern. Vorher waren es rund 600 einzelne Meshes.
  - **Wolken:** 1 Mesh pro Wolke statt 3 bis 5.
  - **Röhren:** Körper und Streifen bilden 1 Mesh, Rand und Band 1 Mesh, mit einem Material
    pro Reihe. Das sind 2 statt 5 Meshes pro Segment.
- **Adaptive Qualität:** Liegt die Framerate zweimal hintereinander für 1,5 s unter 48 fps,
  sinkt die Pixeldichte stufenweise (2 → 1,5 → 1,25 → 1). Auf der letzten Stufe werden die
  Schatten-Maps abgeschaltet, der Vogel behält einen einfachen Schatten auf dem Boden. Die Stufe
  wird für den nächsten Start gespeichert.
- **Entwickler-Anzeige:** 5× schnell auf den Titel tippen oder `?fps` zeigt fps, Draw Calls und
  Qualitätsstufe. So lässt sich die Leistung am echten Handy prüfen.

**Warum:** Der Reviewer stufte die Performance als Blocker ein. 892 Draw Calls pro Frame
überfordern die GPU von Mittelklasse-Handys.

**Messung (`scripts/perf.mjs`, gleiche Szene):**

| Wert | vorher | nachher |
|---|---|---|
| Draw Calls (max) | 892 | **165** |
| Dreiecke | 42,9k | 49,7k (größere Blöcke werden nicht einzeln ausgeblendet; für Handy-GPUs unkritisch) |

Screenshots vorher und nachher sind optisch gleich. Die Qualitätsstufen wurden im Headless-Test
durchlaufen (Software-GPU), ohne Fehler. Auf einem echten Gerät ist nichts gemessen.

### Iteration 5 – Relative Tipp-Zonen und Höhen-Marker (Review-Blocker 1)

**Befund:**
- Die Kamera folgt dem Vogel seitlich. Deshalb erscheint er in der linken Spur bei 37,7 % der
  Bildschirmbreite und in der rechten bei 62,4 %, also jeweils schon in der **mittleren**
  Tipp-Zone (33–67 %).
- Wer in einer Außenspur auf den Vogel tippte, um zu flattern, bekam stattdessen einen
  Spurwechsel mit kleinem Hüpfer. Berechnet mit `three` und der echten Kamera
  (`scratchpad/proj.mjs`).

**Was:**
- **Relative Steuerung:**
  - Ein Tipp in ±18 % der Breite um die aktuelle Bildschirmposition des Vogels lässt ihn
    flattern.
  - Ein Tipp links oder rechts daneben wechselt eine Spur in diese Richtung.
  - Ein Tipp neben den Vogel in Richtung Rand (es gibt dort keine Spur mehr) lässt ihn
    flattern.
  - Die Anleitung im Menü und die Zonen-Einblendung wurden angepasst.
- **Höhen-Marker:**
  - Ein Ring an der nächsten Reihe, in der Spur und auf der Höhe des Vogels. Grün heißt, er
    würde jetzt durchpassen; rot heißt, er würde anstoßen.
  - Der Ring blendet ab 48 Einheiten Entfernung ein, wird mit der Entfernung skaliert und
    verschwindet während des Regenbogens.
- **Erreichbarkeitsregel:** Ein Wechsel über zwei Spuren braucht jetzt zwei Tipps, der
  Spielraum wird pro Spurschritt mit 0,6 multipliziert.

**Messung:**
- **Fehltipp-Modell** (Monte-Carlo, 200k Tipps, Streuung σ = 5–7 % der Breite; die Hälfte der
  Flatter-Tipps zielt auf den Vogel, die andere Hälfte auf die Spur):

  | Tipp-Zonen | Fehltipps |
  |---|---|
  | Drittel (alt) | **13,4 %** |
  | relativ (neu) | **0,0–0,5 %** |

- **Headless-Test:** Ein Tipp links wechselt in Spur 0. Ein Tipp auf den Vogel in Spur 0 lässt
  ihn flattern (vorher: Wechsel in die Mitte).
- **Bot-Playtest (100 Runs):**

  | Bot | Überlebenszeit (Median) | Überlebensrate beim Profi |
  |---|---|---|
  | Anfänger | 15,9 s (vorher 17,4 s) | – |
  | Geübt | 41,2 s (vorher 42,5 s) | – |
  | Profi | – | 88 % (vorher 94 %) |

  Die leichte Verschlechterung kommt daher, dass ein Doppelwechsel jetzt zwei Tipps braucht.
  Bots tippen nie daneben und profitieren deshalb nicht vom eigentlichen Gewinn (13,4 % → 0,5 %
  Fehltipps). Der Höhen-Marker hilft nur Menschen; das muss ein Test mit echten Spielern
  zeigen.

### Iteration 6 – Session-Loop: Bereit-Zustand, Tap-Neustart, Tagesgeschenk

**Was:**
- **Bereit-Zustand:** Nach dem Start und nach jedem Neustart schwebt der Vogel, bis zum ersten
  Tipp. Die Tipp-Zonen („ausweichen · flattern · ausweichen“) pulsieren in dieser Zeit. Vorher
  fiel der Vogel nach „Nochmal“ sofort.
- **Neustart per Tipp irgendwo** auf dem Game-Over-Screen, frühestens nach 0,35 s echter Zeit,
  damit ein Panik-Tipp den Screen nicht überspringt. „Menü“ bleibt als Ausweg.
- **Missionen passend zum Niveau:**
  - Pflanzen erst ab Rekord 14, bewegte Lücken ab 10, Regenbogen ab 12, Power-ups ab 8.
  - Das Punkteziel für einen Flug richtet sich nach dem Rekord (60 / 90 / 110 %).
- **Tagesgeschenk mit Serie:** 20 Münzen, an jedem Folgetag 10 mehr (maximal 80). Wird ein Tag
  ausgelassen, beginnt die Serie von vorn. Das Geschenk erscheint ab dem 2. Run im Startmenü,
  danach zeigt das Menü „🔥 Serie: Tag N · morgen +X“.

**Warum:** Review-Punkt 3 – kein Bereit-Zustand und kaum Gründe, am nächsten Tag
wiederzukommen.

**Beleg (Headless-Test `loop.mjs`):**

| Prüfung | Ergebnis |
|---|---|
| Missionen bei Rekord 6 | `coins`, `runs`, `rows`, keine Hindernis-Missionen ✓ |
| Geschenk nach Serie 2 am Vortag | +40, Serie „Tag 3 · morgen +50“ ✓ |
| Vogel nach Start, ohne Tipp | schwebt nach 2,5 s bei y ≈ 5,2 ✓ |
| Neustart | Tipp 0,5 s nach dem Game-Over startet neu, Vogel schwebt ✓ |

Regressions-Playtest ohne Änderung: Anfänger 16 s, geübt 41 s. Keine Konsolenfehler.

### Iteration 7 – Game Feel / Juice

**Was:**
- **Squash & Stretch:** Nach einem Flügelschlag streckt sich der Vogel kurz in die Höhe und
  federt zurück, beim Spurwechsel etwas schwächer.
- **Hit-Stop:** Beim Crash friert das Bild 0,14 s ein (nur die Kamera wackelt), danach folgen
  Blitz, Federn und Fall.
- **„Knapp!“:**
  - Zählt, wenn beim Durchflug weniger als 0,45 Einheiten Abstand zur Röhre bleiben.
  - Belohnung: +1 Münze, schwebendes „Knapp!“-Label, Funken, heller Akkord und 15 ms
    Vibration.
  - Weitere knappe Durchflüge in Folge ergeben „Knapp! ×N“, der Akkord klingt jedes Mal
    höher. Ein normaler Durchflug setzt die Serie zurück.
- **Vibration:** 70 ms beim Crash, 15 ms bei „Knapp!“, 30 ms bei neuem Rekord. Ist der Ton
  stummgeschaltet, bleibt auch die Vibration aus. Die Android-Berechtigung `VIBRATE` ist
  ergänzt.
- **Tempo-Kick:** Im Regenbogen weitet sich das Sichtfeld um 8°.
- **Rekord-Fanfare** auf dem Game-Over-Screen.
- **Ring-Pop beruhigt:** Er dauert jetzt 0,22 s statt 0,35 s, wird um 25 % statt 60 % größer
  und blendet schneller aus. Vorher füllte er nahe der Kamera den halben Bildschirm.

**Feedback-Abdeckung (Beleg):**

| Aktion | Visuell | Audio | Haptik |
|---|---|---|---|
| Flattern | Flügel schneller, Stretch | Flatter-Sound | – |
| Spurwechsel | Rollen, kleiner Stretch, Spur-Punkte | Swoosh | – |
| Reihe passiert | Punkte-Pop, Ring-Pop | Punkt-Sound | – |
| Knapp! | Label, Funken, +1 Münze | Akkord (steigt in Serie) | 15 ms |
| Münze | Funken, Zähler | Münz-Sound | – |
| Power-up | Funken, Anzeige, Leuchten, Tempo-Kick | Arpeggio, Musik-Hype | – |
| Crash | Hit-Stop, Shake, Blitz, Federn | Crash, Musik-Ducking | 70 ms |
| Mission erfüllt | Einblendung | Arpeggio | – |
| Neuer Rekord | pulsierender Text | Fanfare | 30 ms |
| Skin gekauft / Geschenk | Konfetti | Arpeggio / Münzregen | – |

**Test:** Headless mit einem Autopiloten, der knapp über der unteren Röhre fliegt.
- „Knapp!“ wurde angezeigt, dabei +1 Münze gutgeschrieben und `navigator.vibrate(15)` 2× ausgelöst.
- Stretch ist im Screenshot sichtbar.
- Keine Konsolenfehler.

**Regression:** Anfänger 16,0 s, geübt 39,9 s im Median (100 Runs). Das liegt im Rahmen der
Streuung.
