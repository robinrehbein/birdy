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

Die Noten sind nach Review 3 (nach Iteration 12) korrigiert; überhöhte Werte wurden gesenkt.

| # | Bereich | Note | Beleg / Begründung |
|---|---------|------|--------------------|
| 1 | Onboarding | 7 | Geführter erster Run mit Geister-Hand und Einfrieren, per E2E-Test belegt. Offen: Test mit Menschen. |
| 2 | Game Feel / Juice | 6 | Hit-Stop, Stretch, „Knapp!“, Vibration, Flugspuren. Aber: Die oberen Röhren füllen 40–50 % des Bildes, das Tempo ist kaum spürbar. |
| 3 | Fairness & Kurve | 7 | Profi-Bot erreicht in über 90 % der Runs das Zeitlimit, Erreichbarkeit ist garantiert. Tiefenwahrnehmung von Menschen ungeprüft. |
| 4 | Abwechslung | 6 | 4 Zonen mit eigener Szenerie, Tageszeit, Musik und Spezialität. Aber: Anfänger sehen meist 1–2 Zonen (Zone 3 nur ~2 %), ab Reihe 40 wiederholen sich die Zonen (Review 4). |
| 5 | Session-Loop | 7 | 0,8 s vom Crash zum Neustart, nächstes Ziel, Missionen, Erfolge. Das Game-Over auf kleinen Displays ist entschlackt (Iteration 20). |
| 6 | Meta-Progression | 6 | Farben, Spuren, Missionen, Geschenk, 14 Erfolge. Aber: Vögel sind nur Umfärbungen. |
| 7 | Audio | 5 | Mischkette, Hall, Variation. Pegelmessungen belegen Lautheit, nicht Qualität. Von keinem Menschen gehört. |
| 8 | Performance | 5 | 162 Draw Calls, 50k Dreiecke, erster Frame 0,79 s (Headless), adaptive Qualität. Auf keinem Gerät gemessen. |
| 9 | Politur | 6 | Münzen golden, Zielringe blenden vor der Kamera aus, Shop-Kacheln mit Preis, Menü und Shop rahmen den Vogel auf jeder Displaygröße (360×640 und 390×844 geprüft). Offen: Die oberen Röhren nehmen viel Bildfläche ein (bewusst belassen, siehe Iteration 15). |
| 10 | Store-Reife | 5 | AAB, Texte, Datenschutz, neues Icon. Aber: englisches Listing zu einer rein deutschen App. Offen: App-ID, Name. |

## Backlog (nach Hebel sortiert)

Ab hier braucht es echte Geräte und echte Spieler, siehe Abschlussbericht.

## Review 1 (nach Iteration 3) – Publisher-Subagent

Urteil: Soft Launch „heute nein“. Die drei wichtigsten Kritikpunkte:

| Rang | Einstufung | Kritik |
|---|---|---|
| 1 | Blocker | Tiefen-Lesbarkeit und Fehltipps an den Zonengrenzen |
| 2 | Blocker | Performance auf Mittelklasse-Android |
| 3 | wichtig | Neustart ohne Bereit-Zustand; kaum Grund, am nächsten Tag wiederzukommen |

Der Reviewer fand die Noten für Audio, Fairness, Onboarding, Session-Loop und Meta überhöht. Ich habe sie oben korrigiert.

## Review 2 (nach Iteration 7) – Publisher-Subagent

Urteil: Soft Launch weiter „nein“.

Zu den Punkten aus Review 1:
- **Bereit-Zustand und Rückkehr-Gründe:** erledigt.
- **Lesbarkeit:** technisch gelöst, aber der Zielring ist in der Ferne zu klein.
- **Performance:** gute Architektur, auf keinem Gerät belegt.

Die neuen Top 3:

| Rang | Einstufung | Kritik |
|---|---|---|
| 1 | Blocker | Nach Run 2 nichts Neues: ein Biom, Kurve endet bei 40, ein Musik-Loop |
| 2 | Blocker | Onboarding hängt am Text |
| 3 | wichtig | Zeit vom Crash bis zum nächsten Run; Geister-Röhren; Zurück-Taste und eigenes Icon als Pflicht vor dem Store |

Als überhöht bewertet: Game Feel, Session-Loop, Meta, Audio und Performance, jeweils um einen
Punkt. Oben korrigiert.

## Review 3 (nach Iteration 12) – Publisher-Subagent

Urteil: **interner Test-Track ja, sobald die App-ID steht.** Für einen öffentlichen Soft Launch
reicht es noch nicht.

Zu den Punkten aus Review 2:
- **Onboarding, Neustart-Tempo, Geister-Röhren, Zurück-Taste, Icon:** erledigt.
- **Abwechslung:** nur auf dem Papier erledigt. Anfänger sehen Zone 2 kaum, und die Szenerie
  kommt verspätet.

Die neuen Top 3:

| Rang | Kritik |
|---|---|
| 1 | Zonen erreichbar und sauber machen |
| 2 | Nächstes Ziel auf dem Game-Over-Screen |
| 3 | Bildaufteilung (Röhren, Menü-Panel, Shop-Kamera) |

Außerdem gefunden: `scripts/perf.mjs` war seit dem Tutorial kaputt, und die Store-Screenshots
waren inszeniert. Als überhöht bewertet: Session-Loop, Abwechslung, Game Feel, Audio und
Store-Reife. Oben korrigiert.

## Review 4 (nach Iteration 18) – Publisher-Subagent

Urteil: **interner Test-Track ja, öffentlicher Soft Launch nein.**

- **Erledigt:** das nächste Ziel beim Game-Over.
- **Halb erledigt:**
  - Zonen: Anfänger sehen meist nur 1–2.
  - Bildaufteilung: Menü und Shop sind gelöst, aber der Röhrenturm füllt weiter rund 40 % des
    Bildes.
- **Blocker:**
  - Englisches Store-Listing, obwohl die App nur Deutsch spricht.
  - Der Röhrenturm dominiert die Screenshots.
- **Kleine Fehler:**
  - Erfolge alter Spielstände.
  - Einblendungen bleiben nach dem Neustart in der Warteschlange.
  - Tipp-Feedback unter dem Daumen.
  - Überladenes Game-Over auf 360×640.
  - Veraltete `STORE.md`.

  Alle sind in Iteration 20 behoben.
- **Als überhöht bewertet:** Audio, Meta, Session-Loop, Abwechslung und Store, jeweils um einen
  Punkt. Oben korrigiert.

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

### Iteration 8 – Abwechslung: 4 Zonen, Münz-Rausch, atmende Lücken (Review-2-Blocker 1)

**Was:**
- **4 Zonen,** die sich im Kreis wiederholen; jede ist ein Ort zu einer Tageszeit:

  | Zone | Tageszeit | Szenerie |
  |---|---|---|
  | Stadtpark | Tag | Büsche, Bäume, Hochhäuser |
  | Herbstwald | Sonnenuntergang | Orange und rote Bäume, Tannen, Hügel |
  | Canyon | violette Dämmerung | Felsen, Kakteen, Tafelberge, Sandboden |
  | Blütenhain | Morgen | Rosa Blütenbäume, blühende Büsche, Häuschen mit Dächern |

  Himmel, Nebel, Licht, Wolken und Boden blenden in 3 s über. Die neue Szenerie strömt vom
  Horizont herein, weil jeder Block beim Zurückspringen im neuen Stil gebaut wird. Es gibt
  keinen harten Schnitt.
- **Zonenwechsel alle 15 Reihen:**
  - Ein röhrenfreier Abschnitt mit einer Münz-Welle über die Spuren, als Verschnaufpause
    und Belohnung.
  - Ein Banner „Zone N · Name“ und ein Sound.
- **Spezialität je Zone:**
  - Herbstwald: neue Mechanik **atmende Lücken**, die im Takt der Musik auf 70 % schrumpfen
    und wieder aufgehen.
  - Canyon: 1,6× mehr und 25 % schnellere bewegte Lücken.
  - Blütenhain: 1,8× mehr Pflanzen.
  - Ab der zweiten Runde durch alle Zonen: atmende Lücken überall.
- **Tempo nach 40 Punkten:** Es steigt langsam weiter, um bis zu +8. Der Abstand zwischen den
  Reihen bleibt zeitbasiert, damit es fair bleibt.
- **Grasränder:** Sie sind jetzt Teil einer einfärbbaren gestreiften Grasfläche statt der
  Streckentextur. So nimmt auch der Boden die Zonenfarbe an, am Tag sieht er aus wie vorher.

**Beleg:**
- Screenshots aller Zonen (Herbst, Canyon, Blütenhain) inklusive Banner, ohne
  Konsolenfehler. Der Tag sieht unverändert aus.
- Bot-Playtest (100 Runs):

  | Bot | Überlebenszeit (Median) | Punkte (Median) | Münzen/Run vorher → nachher | Profi-Überlebensrate |
  |---|---|---|---|---|
  | Anfänger | 17,4 s | 9 | – | – |
  | Geübt | 37,6 s | 23 | 24 → 28 | – |
  | Profi | – | – | – | **91 %** |

  Mehr als die Hälfte der geübten Runs erreicht den Herbstwald (15 Punkte), etwa 25 % den
  Canyon.

### Iteration 9 – Geführter erster Run (Review-2-Blocker 2)

**Was:**
- **Erster Start ohne Menü,** direkt im Bereit-Zustand. Eine Geister-Hand 👆 tippt auf den Vogel,
  darunter steht „Tippen = flattern“.
- **Reihen 1–3** haben nur eine große Lücke in der Mitte, also genau dort, wo der Vogel ist.
  Dabei übt der Spieler das Flattern.
- **Reihe 4** ist in der Mitte blockiert. Kurz davor friert das Spiel ein, der Zielring leuchtet
  rot, und die Hand tippt links neben den Vogel („Daneben tippen = ausweichen“). Ein Tipp auf
  den Vogel ändert nichts, erst ein Tipp daneben geht weiter.
- Nach der Reihe erscheint „Super! Jetzt allein weiter 🎉“, und das Tutorial gilt als erledigt.
  Wer vorher stirbt, bekommt es im nächsten Run noch einmal.
- Der Zielring ist im Tutorial 1,6× größer und pulsiert.
- Bestehende Spieler (mit mindestens einem Run) überspringen das Tutorial.

**Warum:** Beide Reviews kritisierten, dass die Regeln nur als Text im Menü stehen. Top-Titel wie
Stack oder Helix Jump erklären sich wortlos durch Spielen.

**Beleg (E2E-Test `tut.mjs`, leerer Speicher):**

| Schritt | Ergebnis |
|---|---|
| Start | Menü übersprungen, Bereit-Zustand, Hand im Modus „flap“ ✓ |
| 3 Mittel-Reihen | durchflogen, danach Einfrieren mit Hand im Modus „side“ ✓ |
| Tipp auf den Vogel | Welt bleibt stehen (Röhren-Positionen identisch) ✓ |
| Tipp daneben | Spur 0, Hand weg, Reihe passiert, `tutorialDone = true` ✓ |

Screenshots `t-1-hold`, `t-2-freeze` und `t-3-done`, ohne Konsolenfehler. Ob Erstspieler es
wirklich verstehen, muss ein Test mit Menschen zeigen.

### Iteration 10 – Audio: Song-Struktur, Zonen-Themen, Menü-Modus

**Was:**
- **Song statt 7,7-s-Loop,** 16 Takte (31 s) pro Durchlauf:
  1. A-Teil (C–Am–F–G)
  2. A-Teil mit Zweitstimme
  3. B-Teil mit eigener Melodie über F–G–Em–Am
  4. Breakdown: 2 Takte ohne Schlagzeug, dann setzt die Melodie wieder ein
- **Ein Thema pro Zone,** gleiches Tempo (124 BPM, damit die Pflanzen im Takt bleiben):

  | Zone | Tonart | Lead | Groove |
  |---|---|---|---|
  | Stadtpark | C | Rechteck | Pop-Beat |
  | Herbstwald | A | weiches Dreieck | Shuffle |
  | Canyon | G, Moll-Umdeutung des A-Teils | Sägezahn | Tresillo-Beat mit Tom |
  | Blütenhain | D | Glocken-Sinus eine Oktave höher | leichter Beat, 16tel-Arpeggio |

- **Menü-Modus:** Menü und Game-Over-Screen spielen eine ruhige Fassung ohne Schlagzeug, etwa
  7 dB leiser. Thema und Modus wechseln immer auf einem Taktanfang, damit es musikalisch bleibt.
- **`renderMusic()`:** rendert die Musik offline, zur Messung und zum Anhören ohne Spielen.

**Beleg (offline gerendert, je 31 s):**

| Thema | RMS | Spitze |
|---|---|---|
| Stadtpark | −32,7 dBFS | 0,42 |
| Herbstwald | −32,4 dBFS | 0,32 |
| Canyon | −31,5 dBFS | 0,41 |
| Blütenhain | −33,0 dBFS | 0,43 |
| Menü | −39,5 dBFS | 0,11 |

Alle Themen liegen innerhalb von 1,5 dB, keine Übersteuerung. Der Breakdown ist rund 2 dB leiser.
Beim Messen fiel ein Fehler auf: Ein noch ausstehender Moduswechsel sickerte in das
Offline-Rendern. Er ist behoben. Die WAV-Dateien liegen im Scratchpad unter `audio/`.

### Iteration 11 – Schnelleres Game-Over, keine Geister-Röhren

**Was:**
- Der Game-Over-Screen erscheint fest 0,45 s nach dem Crash. Der Fall des Vogels läuft hinter
  dem Panel weiter. Vorher wartete das Spiel, bis der Vogel am Boden lag, plus 0,7 s.
- Durchflogene Reihen blenden auf 0 aus und werden dann unsichtbar geschaltet (vorher 10 %
  Deckkraft). Damit ist auch dein ursprünglicher Wunsch „durchflogene Säulen transparent“
  konsequent umgesetzt.

**Messung (Headless, Crash auf Höhe 8,6):**

| Wert | vorher | nachher |
|---|---|---|
| Crash bis Game-Over (Spielzeit) | ≈ 0,67 s Fall + 0,7 s = **1,4 s** | **0,46 s** |

Mit der Tipp-Sperre von 0,35 s ist ein neuer Run rund 0,8 s nach dem Crash möglich. Keine Fehler.

### Iteration 12 – Store-Reife

**Was:**
- **Zurück-Taste** (`@capacitor/app`):
  - Im Spiel pausiert sie, aus Pause, Shop und Game-Over geht es ins Menü, im Menü schließt
    sich die App.
  - Beim Weg ins Menü wird die Strecke geräumt.
- **Release-Build:**
  - Signing über `android/keystore.properties` (steht in `.gitignore`), `npm run android:aab`.
  - Version 1.0.0 / Code 1.
  - Mit einem Wegwerf-Schlüssel gebaut: AAB 4,1 MB, `jarsigner`: „jar verified“.
- **Eigenständiges Icon:** Der Reviewer sah im alten Icon einen Flappy-Bird-Lookalike (gelber
  Vogel im Profil vor türkisem Himmel mit grünem Boden). Neu: derselbe 3D-Vogel von vorn mit
  erhobenen Flügeln im gelben Zielring vor einem Himmelsverlauf. Launcher-Icons, adaptives
  Icon, Splash und Store-Icon sind neu gerendert.
- **Store-Grafiken:**
  - Feature-Grafik 1024×500.
  - 6 Screenshots in 1080×1920 aus echten Spielszenen: Menü, Park, Herbstwald-Banner, Canyon,
    Blütenhain, Shop (`scripts/store-shots.mjs`).
- **`docs/STORE.md`:**
  - Checkliste und Anleitung zum Signieren.
  - Store-Texte auf Deutsch und Englisch.
  - Datenschutzerklärung und Antworten zur Datensicherheit.
  - Erwartete Einstufung USK 0 / PEGI 3.
  - Rechte-Risiken und offene Entscheidungen.
- **Wortlaut:** „Flappy“ aus README und Code-Kommentaren entfernt.
- **Shop-Kacheln** zeigen den Preis mit Schloss; die Farbpunkte sind nicht mehr entsättigt.

**Nicht ohne dich entschieden** (siehe `docs/STORE.md`): App-ID, Store-Name bzw.
Markenrecherche, Datenschutz-URL und Kontakt. Das Piranha-Pflanzen-Design ist das größte
Rechte-Risiko; es war ausdrücklich „wie Mario“ gewünscht, deshalb ändere ich es nicht ungefragt.

### Iteration 13 – Politur und Flugspuren

**Was:**
- **Münzen:** goldgelb statt bräunlich. Weniger Metallanteil (ohne Umgebungsbild wirkte Metall
  braun), dazu ein eigenes Leuchten.
- **Zielringe** blenden in den letzten Einheiten vor der Kamera aus, statt den Bildschirm zu
  füllen.
- **Münzzähler** hüpft beim Einsammeln.
- **Flugspuren (zweite Kosmetik-Kategorie):**
  - Funkeln 150, Blasen 300, Konfetti 500, Herbstlaub 700, Sternenstaub 1000,
    Feuerschweif 1400.
  - Sie werden als Partikel hinter dem Vogel dargestellt; während des Regenbogens pausieren sie.
  - Der Shop hat jetzt die Reiter „🐦 Vögel“ und „✨ Spuren“, mit Live-Vorschau der Spur am
    schwebenden Vogel.
- **`scripts/perf.mjs` repariert:** Seit dem Tutorial war mit leerem Speicher der Start-Button
  unsichtbar. Das Skript belegt den Speicher jetzt vor dem ersten Laden vor. Ein Reload hätte
  den Abbau der alten Seite mitgemessen (3,2 s statt 0,8 s).

**Beleg:**
- E2E-Test: Reiter wechseln, Konfetti kaufen (800 → 300 Münzen, ausgewählt), Konfetti-Spur im
  Spiel sichtbar. Keine Fehler.
- Perf-Messung: 162 Draw Calls, 49,7k Dreiecke, erster Frame nach 0,79 s.

### Iteration 14 – Zonen erreichbar, nächstes Ziel beim Game-Over (Review-3-Punkte 1 und 2)

**Was:**
- **Zonenwechsel alle 10 statt 15 Reihen.**
- **Szenerie früher umgestellt:** Sie wechselt schon beim Anlegen der Zonen-Marke, also etwa
  5 Reihen vorher. Die neue Umgebung beginnt dadurch genau dort, wo das Banner erscheint (vorher
  kam sie rund 8 s später).
- **Game-Over zeigt das nächste Ziel:**
  - „Noch X 🪙 bis Vogel/Spur ‚Name‘“ mit Fortschrittsbalken.
  - Ist das Ziel schon bezahlbar, erscheint „jetzt freischaltbar ›“; ein Tipp darauf führt direkt
    in den Shop.
  - Alle drei Tagesmissionen mit Balken; gerade erfüllte ploppen auf.
  - „Zone N erreicht: Name“.
- **„🏆 Neuer Rekord!“** erscheint sofort im Run, sobald der Rekord (mindestens 5) übertroffen
  ist, mit Sound und Vibration.
- **Playtest-Skript** misst jetzt, welcher Anteil der Runs die Zonen 2, 3 und 4 erreicht.

**Messung (100 Runs je Bot, gleicher Bot-Code):**

| Bot | Zone 2 | Zone 3 | Zone 4 | Überlebenszeit (Median) |
|---|---|---|---|---|
| Anfänger | 15 % → **47 %** | 0 % → 5 % | 0 % → 2 % | 17,4 s → 17,5 s |
| Geübt | 91 % → **100 %** | 26 % → **59 %** | 1 % → **23 %** | 37,7 s → 37,4 s |
| Profi | 100 % | 99 % | 96 % → 97 % | 91 % überleben |

Die Schwierigkeit bleibt dabei gleich, die Überlebenszeiten sind praktisch unverändert.
Screenshots: Beim Banner „Zone 2 · Herbstwald“ steht der Herbstwald schon komplett. Der
Game-Over-Screen zeigt Rekord, Zone, nächstes Ziel und Missionen. Keine Fehler.

### Iteration 15 – Bildaufteilung und ehrliche Store-Screenshots (Review-3-Punkt 3)

**Was:**
- **Menü und Shop rahmen den Vogel auf jedem Display:**
  - Die Kamera misst den freien Platz zwischen Titel und Panel, verschiebt den Bildausschnitt
    (`setViewOffset`), damit der Vogel genau dort sitzt, und geht bei wenig Platz weiter zurück.
  - Beim Spielstart gleitet die Verschiebung weich zurück.
  - Auf niedrigen Displays (bis 720 px Höhe) sind die Menüs kompakter.
  - Vorher war der Vogel auf 360×640 im Menü und im Shop komplett verdeckt.
- **Store-Screenshots aus einem echten Run:**
  - Punkte und Münzen sind echt. Ein Autopilot mit Unverwundbarkeits-Flag (nur für Tests) hält
    den Vogel am Leben, und das Skript wartet, bis die Szenen wirklich eintreten.
  - Motive: Menü, Park, Herbstwald-Banner bei 10 Punkten, ausgefahrene Piranha-Pflanze (bei 45 Punkten),
    Regenbogen, Spuren-Shop mit Konfetti-Vorschau.
- **Bewusst nicht geändert:** die Höhe der oberen Röhren. Kürzere Röhren würden in der Ferne als
  schwebende Enden im Himmel sichtbar. Eine andere Kamera würde den Look verändern, den du
  ausdrücklich behalten wolltest.

**Beleg:**
- Screenshots für Menü und Shop auf 360×640 und 390×844: Der Vogel ist jeweils zwischen Titel
  und Panel sichtbar.
- Regressionen ohne Fehler: Perf 165 Draw Calls, Bot-Playtest, Tutorial-E2E-Test (relative Tipps
  mit Bildverschiebung).

---

## Abschlussbericht (Stopp: 15 Iterationen erreicht)

**Das Ziel „alle Bereiche ≥ 8 mit Belegen“ ist nicht erreicht.** Erreicht ist: ein spielbares,
messbar faireres und deutlich reicheres Spiel, das für einen internen Test über die Play Console
bereit ist. Die übrigen Lücken lassen sich nur mit echten Geräten und Menschen schließen.

### Bewertungsbogen vorher → nachher

„Vorher“ ist der Stand vor Iteration 1, nach den Korrekturen durch Review 1.

| # | Bereich | vorher | nachher | Was fehlt noch zur 8 |
|---|---|---|---|---|
| 1 | Onboarding | 4 | **7** | Test mit 5–10 Erstspielern: Verstehen sie die Regeln ohne Hilfe? |
| 2 | Game Feel / Juice | 4 | **6** | Auf dem Gerät fühlen (Vibration, Hit-Stop). Die oberen Röhren dominieren das Bild. |
| 3 | Fairness & Kurve | 5 | **7** | Todesursachen echter Spieler; Tiefenwahrnehmung. |
| 4 | Abwechslung | 3 | **7** | Mehr Mechaniken nach der 4. Zone; Bestätigung durch echte Spieler. |
| 5 | Session-Loop | 5 | **8** | – |
| 6 | Meta-Progression | 1 | **6** | Erfolge und Langzeitziele; die Ökonomie mit echten Daten tunen. |
| 7 | Audio | 3 | **5** | Von Menschen gehört? Eventuell echte Samples oder Instrumente. |
| 8 | Performance | 3 | **5** | fps auf einem Mittelklasse-Android messen (Anzeige: 5× auf den Titel tippen). |
| 9 | Politur | 5 | **6** | Feinschliff nach Gerätetest. |
| 10 | Store-Reife | 1 | **6** | App-ID, eigener Schlüssel, Datenschutz-URL, Pflanzen-Design (Rechte). |

### Endmessung (Stand nach Iteration 15)

**Bot-Playtest, 100 Runs je Bot, 150 s Limit:**

| Bot | Überlebenszeit Median | 10 % der Runs enden vor … | Punkte Median | Münzen/Run | Zone 2 / 3 / 4 erreicht |
|---|---|---|---|---|---|
| Anfänger | 14,5 s | 9,9 s | 7 | 7 | 33 % / 1 % / 0 % |
| Geübt | 40,2 s | 21,9 s | 23 | 26 | 100 % / 63 % / 25 % |
| Profi | 88 % der Runs bis zum Limit | 136,9 s | 117 | 116 | 100 % / 100 % / 99 % |

Der Anfänger-Bot streut zwischen Messungen um etwa ±3 s und ±15 Prozentpunkte. Kleine
Unterschiede sind also Rauschen.

**Render-Budget:** 162 Draw Calls, 49k Dreiecke, erster Frame nach 0,83 s (Headless-Software-GPU).

**Konsolenfehler:** keine, in allen Testskripten.

### Die 5 wichtigsten Änderungen

1. **Fairness per Konstruktion:**
   - Jede Reihe ist von der vorherigen aus erreichbar, der Abstand zwischen Reihen ist zeitbasiert.
   - Profi-Bot: vorher starb er in 100 % der Runs, jetzt überlebt er rund 90 % der 150-s-Runs.
2. **Steuerung relativ zum Vogel plus Zielring:**
   - Modellierte Fehltipps sinken von 13,4 % auf 0,5 %.
   - Der Spurwechsel ist nur ein kleiner Hüpfer, dazu kommt der geführte erste Run.
3. **4 Zonen mit eigener Szenerie, Tageszeit, Musik und Mechanik:**
   - Der Münz-Rausch leitet jede Zone ein.
   - Geübte Spieler erreichen Zone 4 jetzt in 23 % statt 1 % der Runs.
4. **Meta-Schleife:**
   - Münzen, 7 Vögel und 7 Spuren.
   - Tagesmissionen passend zum Niveau, Tagesgeschenk mit Serie.
   - Game-Over mit nächstem Ziel.
5. **Performance und Store:**
   - 892 → 165 Draw Calls, adaptive Qualität.
   - Signierbares AAB, Zurück-Taste, eigenständiges Icon, Store-Paket.

### Was nur echte Spieler und Geräte klären können – Testplan

1. **Geräte-Test (1 Tag, ihr drei):**
   - Debug-APK auf 3 Handys installieren: eines alt, eines Mittelklasse, eines neu.
   - Je 10 Minuten spielen, dabei die fps-Anzeige einschalten (5× auf den Titel tippen).
   - Ziel: stabile ~60 fps auf der Mittelklasse, keine automatische Qualitätsstufe unter Q2.
   - Außerdem prüfen: Vibration, Ton, Zurück-Taste, Pause beim App-Wechsel.
2. **Erstspieler-Test (5–10 Personen, die das Spiel nicht kennen):**
   - Wortlos das Handy geben, nur zuschauen und notieren:
     - Verstehen sie Flattern und Ausweichen im Tutorial?
     - Woran sterben sie? Lesen sie den Zielring?
     - Spielen sie freiwillig eine zweite Runde?
   - Ziel: 8 von 10 schaffen das Tutorial ohne Hilfe, und der Median des ersten Runs liegt
     über 20 s.
3. **Interner Test-Track (Play Console, 20–50 Personen, 2 Wochen):**
   - Vorher App-ID festlegen und Schlüssel erzeugen (`docs/STORE.md`).
   - Da die App bewusst kein Tracking hat, D1- und D7-Rückkehr per kurzer Umfrage oder
     über die Play-Console-Statistik („Aktive Nutzer“) abschätzen.
   - Richtwerte für Casual-Top-Titel: D1 ≥ 35–40 %, D7 ≥ 12–15 %.
4. **Danach tunen:** Tempo und Lückengröße (`gateSpec()` und `baseSpeed()` in `src/main.js`)
   und die Münz-Ökonomie (`progress.js`) anhand der Beobachtungen anpassen.

---

## Zweite Runde (Iterationen 16–20, nach „Mach weiter“)

### Iteration 16 – Erfolge und Meilensteine

**Was:**
- **14 Erfolge** mit Münzbelohnung (30–300 Münzen), die beim Freischalten sofort ausgezahlt
  werden. Beispiele:
  - Abgehoben / Flugschüler / Himmelsstürmer / Legende: 10 / 25 / 50 / 100 Punkte in einem Flug
  - Weltenbummler: Zone 4 erreichen
  - Haarscharf: 10× „Knapp!“ insgesamt; Nervenkitzel: 5× „Knapp!“ in Folge
  - Power-Sammler: 3 Power-ups in einem Flug; Gärtner: an 25 Pflanzen vorbei
  - Sparschwein / Schatzmeister: 500 / 2000 Münzen eingesammelt
  - Dauerflieger: 50 Runden; Stammgast: 7 Tage Geschenk-Serie; Sammler: 5 Freischaltungen
- **Lebenszeit-Statistiken** in `progress.js`. Bestehende Spielstände werden übernommen (der
  Rekord zählt sofort).
- **Anzeige:**
  - Im Run erscheint „🏆 Name +X“ im Moment des Erreichens, mit Sound und Vibration.
  - Auf dem Game-Over-Screen stehen die freigeschalteten Erfolge über den Missionen.
  - Erfolge durch Kauf oder Tagesgeschenk werden direkt im Menü gefeiert.
- **Übersicht „🏆 Erfolge“** im Startmenü: alle 14 mit Symbol, Fortschrittsbalken und
  Belohnung, Zähler „5 / 14“. Die Zurück-Taste funktioniert auch dort.

**Warum:** Der Bewertungsbogen nennt ausdrücklich Rekorde und Meilensteine, und alle Reviews
bemängelten fehlende Langzeitziele.

**Beleg (Logiktest `ach.mjs`):**

| Schritt | Ergebnis |
|---|---|
| Run mit 26 Punkten, 12× Knapp!, Serie 5 | 4 Erfolge (+280), plus die Mission „20 Münzen“ (+40): 300 → 650 ✓ |
| 50. Runde | „Dauerflieger“ ✓ |
| 4 Freischaltungen | „Sammler“ (Ziel 5) wird korrekt noch nicht vergeben ✓ |
| Erneute Prüfung | keine Doppelvergabe ✓ |

Screenshots von Übersicht und Menü, keine Fehler. Regressions-Playtest unauffällig.

### Iteration 17 – Neues App-Icon (dein Wunsch)

**Was:**
- Vier Varianten mit dem echten 3D-Vogel gerendert und jeweils groß sowie in Launcher-Größe
  (48 px) verglichen:

  | Variante | Motiv | Ergebnis |
  |---|---|---|
  | A | Gesicht in Großaufnahme | stark, aber beliebig |
  | B | Vogel zwischen grünen Röhren | zu nah an Flappy Bird, verworfen |
  | **C** | **Vogel frontal vor Regenbogen und Sonnenuntergangshimmel** | **gewählt** |
  | D | bisheriges Icon (Vogel im Ring) | Vogel in 48 px zu klein |

- **Gewählt: C.** Der Vogel ist groß und frontal mit erhobenen Flügeln, vor Sonnenstrahlen und
  Regenbogen. Das ist farbig, eigenständig, bei 48 px noch klar erkennbar und zeigt das
  Regenbogen-Power-up.
- **Adaptives Android-Icon** jetzt mit eigener Hintergrund-Ebene (Himmel und Regenbogen) statt
  Farbfläche. Der Vogel liegt im Sicherheitsbereich; geprüft mit runder und abgerundeter Maske.
- Legacy- und Rund-Icons, Store-Icon 512, Feature-Grafik und Splash sind neu gerendert. Die
  Hintergrundfarbe des Android-12-Splashs ist an den Himmel angeglichen.

**Beleg:** Kontaktbogen der vier Varianten und Maskenvorschau (Scratchpad `icon-sheet.png`,
`icon-preview.png`). Die APK baut.

### Iteration 18 – Steuerung verfeinert (dein Feedback „besser, aber nicht tip top“)

**Befund:**
- Ein Spurwechsel brauchte 0,25 s bis 95 % der Strecke (zum Vergleich: Subway Surfers liegt
  bei etwa 0,15 s).
- Die Tipp-Zonen richteten sich während eines Wechsels nach der Zwischenposition des Vogels.
- Es gab kein sichtbares Feedback, was ein Tipp ausgelöst hat.
- Die Zonen-Einblendung zeigte feste Drittel, auch wenn der Vogel in einer Außenspur war.

**Was:**
- **Schnellerer Spurwechsel:** 95 % der Strecke nach 0,17 s statt 0,25 s.
- **Zonen richten sich nach der Zielspur:** Direkt nach „links“ zählt ein Tipp auf die neue
  Position des Vogels als Flattern und nicht als weiterer Wechsel.
- **Tipp-Feedback:** Ein kurzer Ring mit Pfeil (◀ ▲ ▶) erscheint dort, wo der Finger war, und
  zeigt die ausgelöste Aktion.
- **Zonen-Einblendung folgt dem Vogel:** In einer Außenspur gibt es nur noch „flattern“ und
  „ausweichen“ zur anderen Seite.

**Messung:**
- Headless-Test „links tippen, dann sofort auf die Zielposition tippen“: Spur 0, Flügelschlag,
  kein zweiter Wechsel ✓.
- Bot-Playtest (100 Runs, vorher → nachher): Anfänger 16,1 → 16,1 s. Geübt 36,4 → 37,9 s, die
  Tode oben an der Röhre sinken von 10 auf 5. Profi 90 → 91 % Überlebende.
- Tutorial-E2E-Test weiterhin grün.

Ob es sich „tip top“ anfühlt, zeigt nur das Handy. Konkrete Hinweise, was stört (Flattern zu
stark oder schwach? Wechsel? Zonen?), helfen beim nächsten Schritt.

### Iteration 19 – Audio: Mischkette, Raum, weniger Wiederholung

**Was:**
- **Mischkette** (`buildGraph()` in `src/audio.js`), gleich für das Spiel und fürs
  Offline-Rendern:
  - Musik über einen sanften Tiefpass bei 6,5 kHz, der den Rechteckwellen die Härte nimmt.
  - Kurzer erzeugter Raumhall als Send: Musik 22 %, Effekte 12 %.
  - Kompressor auf der Summe.
- **Flügelschlag** mit zufälliger Tonhöhe von ±6 %, damit schnelles Flattern nicht nach
  Maschinengewehr klingt.
- **Münz-Kombo:** Münzen kurz hintereinander (innerhalb von 0,7 s) klingen jeweils einen
  Halbton höher, bis zu einer Quinte.

**Messung (offline gerendert, je 20 s, vorher → nachher):**

| Thema | RMS | Spitze | Anteil über 5 kHz |
|---|---|---|---|
| Stadtpark | −32,1 → −29,1 dBFS | 0,42 → 0,55 | 1,7 → 1,0 % |
| Herbstwald | −31,9 → −28,8 dBFS | 0,32 → 0,45 | 1,5 → 0,8 % |
| Canyon | −31,0 → −28,0 dBFS | 0,42 → 0,55 | 0,5 → 0,3 % |
| Blütenhain | −32,5 → −29,5 dBFS | 0,43 → 0,57 | 1,1 → 0,9 % |

Der Klang ist voller und weicher, ohne Übersteuerung; die Themen bleiben innerhalb von 1,5 dB.
Live-Spieltest mit Musik und Effekten (`audiolive.mjs`): keine Fehler, der Takt läuft mit. Nebenbei sind die letzten Three.js-Warnungen behoben (`Timer` statt des veralteten `Clock`, `PCFShadowMap`), die Konsole ist jetzt leer. Die WAVs liegen zum Anhören im Scratchpad
unter `audio2/`.

### Iteration 20 – Tempo-Gefühl und Fehler aus Review 4

**Was:**
- **Tempo-Streifen:**
  - Feine helle Linien neben und über der Strecke rauschen an der Kamera vorbei.
  - Sie werden ab etwa 22 Einheiten/s sichtbar und stärker, je schneller es wird, am
    stärksten im Regenbogen.
  - Technisch 1 Instanced Mesh, also 1 Draw Call.
- **Sichtfeld:** wird mit dem Tempo bis zu 4° weiter, im Regenbogen zusätzlich 8°.
- **Behoben aus Review 4:**
  - Erfolge alter Spielstände werden beim Start ausgezahlt, statt nach dem nächsten Run falsch
    angezeigt zu werden.
  - Die Warteschlange der Einblendungen wird beim Neustart geleert.
  - Das Tipp-Feedback erscheint am Vogel, statt unter dem Daumen.
  - „Zone 1 erreicht“ ist ausgeblendet.
  - Auf niedrigen Displays ist das Game-Over kompakter und ohne Münzanzeige, die den Titel
    überdeckt hat.
  - `STORE.md` beschreibt jetzt das aktuelle Icon.
- **Eigener Fehler behoben:** Die Erfolgs-Fanfare beim Start erzeugte den Audio-Kontext vor der
  ersten Berührung. Der Browser blockierte das und warnte bei jedem Ton. Jetzt werden Töne erst
  nach dem ersten Tipp erzeugt.

**Beleg:**

| Test | Ergebnis |
|---|---|
| `fix4.mjs` (alter Spielstand: Rekord 26, 60 Runs) | Beim Start 3 Erfolge ausgezahlt (+190) ✓; Übersicht konsistent; Game-Over auf 360×640 ohne Überlappung ✓ |
| Live-Audiotest, Tutorial-E2E, Musikmessung | unverändert grün, Konsole ohne Fehler und Warnungen |

---

## Bericht der zweiten Runde (Iterationen 16–20)

Geliefert:
- Erfolge (16)
- neues App-Icon (17)
- feinere Steuerung (18)
- Audio mit Raum und Variation (19)
- Tempo-Gefühl und Review-Fehler (20)

**Das Ziel „alle ≥ 8“ ist weiterhin nicht erreicht.** Die Noten liegen bei 5–7; der Reviewer
sieht das Spiel bereit für den internen Test-Track.

**Deine Entscheidungen, bevor es sinnvoll weitergeht:**
1. **Name** (Recherche-Ergebnisse im Chat).
2. **Röhrentürme:** die oberen Röhren nach oben in den Himmel ausblenden. Das ändert die Optik
   leicht, der Reviewer hält es für einen Blocker.
3. **Sprache:** Englisch einbauen oder nur in DE/AT/CH starten.
4. **Steuerung:** Was genau fühlt sich nicht „tip top“ an? (Flattern zu stark oder schwach,
   Wechsel, Zonen, Verzögerung?)

---

## Dritte Runde (Iterationen 21–30, nach deinen Vorgaben)

Deine Vorgaben:
- Name „Flapsy“
- Röhren ausblenden: okay
- Deutsch und Englisch
- Steuerung: Beim Spurwechsel ist es schwer, die Bahn genau zu treffen und zu halten
- deutlich mehr Shop-Optionen (Kosmetik, Welten, Spuren usw.)

### Iteration 21 – Umbenennung in „Flapsy“

**Was:**
- Neuer Name überall: Titel und Startmenü (Schriftzug „Flap“ in Gelb, „sy“ in Grün),
  `capacitor.config.json`, Android-`strings.xml`, README, Store-Texte (DE „Flapsy – Tippen &
  Fliegen“, EN „Flapsy – Tap & Fly“), Datenschutztext.
- Splash und Feature-Grafik mit neuem Schriftzug neu gerendert.
- Der Speicherschlüssel bleibt intern gleich, damit Spielstände erhalten bleiben.

**Rechte-Hinweis** (Details in `docs/STORE.md`):
- Die Websuche fand ein Browser-Spiel „Flapsy“ mit Röhren, einen „Flapsy Bird“-Klon und die
  Figur „Flapsy“ aus *Dino Ranch* (Disney Junior).
- Dazu die Nähe zu „Flappy Bird“.
- Vor dem Launch ist eine Markenrecherche Pflicht.
