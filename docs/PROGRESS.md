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
| 4 | Abwechslung | 5 | 4 Zonen mit eigener Szenerie und Musik, aber: Anfänger sehen Zone 2 selten, Zone 4 erreicht kaum jemand. Die Szenerie kommt ~8 s nach dem Banner an, deshalb mischen sich die Biome. |
| 5 | Session-Loop | 7 | 0,8 s vom Crash zum nächsten Run. Aber: Der Game-Over-Screen zeigt kein nächstes Ziel. |
| 6 | Meta-Progression | 6 | 7 Vogel-Farben und 7 Flugspuren (zwei Kategorien, Reiter im Shop, Live-Vorschau), Missionen, Tagesgeschenk. Fehlt: nächstes Ziel sichtbar machen. |
| 7 | Audio | 5 | 4 Zonen-Themen, Menü-Modus, Pegel gemessen. Nur Oszillator-Klänge, von keinem Menschen gehört. |
| 8 | Performance | 5 | 162 Draw Calls, 50k Dreiecke, erster Frame 0,79 s (Headless), adaptive Qualität. Auf keinem Gerät gemessen. |
| 9 | Politur | 5 | Münzen golden, Zielringe blenden vor der Kamera aus, Shop-Kacheln mit Preis. Aber: Das Menü-Panel verdeckt den Vogel, die Shop-Kamera ist auf manchen Displays zu nah. |
| 10 | Store-Reife | 5 | AAB signierbar, Texte, Datenschutz, Icon. Aber: Die Screenshots zeigen keine Mechaniken (Pflanzen, Power-ups, „Knapp!“), und die Zonen-Bilder sind inszeniert. Offen: App-ID (deine Entscheidung). |

## Backlog (nach Hebel sortiert)

1. **Zonen erreichbar und sauber (Review 3, Punkt 1):**
   - Zonen alle 10 statt 15 Reihen.
   - Die Szenerie beim Anlegen der Zonen-Marke umstellen, damit sie beim Banner fertig ist.
2. **Nächstes Ziel auf dem Game-Over-Screen:**
   - Fortschritt zum nächsten Kauf.
   - Offene Missionen mit Balken.
   - Erreichte Zone.
   - „Neuer Rekord!“ schon während des Runs.
3. **Bildaufteilung:** Menü-Panel und Vogel, Shop-Kamera je nach Seitenverhältnis, Höhe der oberen Röhren.
4. **Store-Screenshots mit Mechaniken,** ohne Inszenierung.

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
