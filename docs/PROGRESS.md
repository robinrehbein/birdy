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

| # | Bereich | Note | Beleg / Begründung |
|---|---------|------|--------------------|
| 1 | Onboarding | 5 | Anfänger-Bot: erster Tod im Median bei 17,4 s, 10 % der Runs enden vor 11,4 s. Aber: Erklärung nur als Text im Startmenü, kein Tutorial im Spiel. |
| 2 | Game Feel / Juice | 5 | Vorhanden: Flatter-Sound, Punkte-Pop, Partikel, Kamera-Shake und Blitz beim Tod. Fehlt: Squash & Stretch, Hit-Stop, Near-Miss-Feedback, Speed-Lines. |
| 3 | Fairness & Kurve | 7 | Profi-Bot (perfekte Reaktion) überlebt 94 % der 150-s-Runs, vorher starb er in 100 % der Runs. Reihen haben eine garantierte Erreichbarkeit, der Abstand ist zeitbasiert. Offen: Ankündigung von Pflanzen und bewegten Lücken ist nicht an Menschen getestet, 6 % Rest-Tode beim Profi. |
| 4 | Abwechslung | 4 | Neues nur bei 6 (bewegte Lücken) und 10 Punkten (Pflanzen), plus Power-ups. Keine Biome oder Events, gleiche Optik über den ganzen Run. |
| 5 | Session-Loop | 6 | Runs im Median: Anfänger 17 s, geübt 42,5 s ✓ (Ziel 30–90 s). Game-Over zeigt „Nur noch X bis zum Rekord“ und erfüllte Missionen. Neustart per Button erst nach ca. 1,7 s. |
| 6 | Meta-Progression | 6 | Münzen werden gespeichert. 7 Vogel-Skins (100–1500 Münzen) mit Live-Vorschau am 3D-Vogel. Täglich 3 Missionen (+40/+70/+120) mit Einblendung beim Erreichen. Fehlt: Erfolge und Meilensteine, weitere Kosmetik (Spuren, Hüte), Langzeitziele. |
| 7 | Audio | 6 | Prozedurale Chiptune-Musik und Effekte. Der Loop ist nur ca. 31 s lang, Wiederholung nervt vermutlich nach 10 Minuten. Mix ist ungeprüft. |
| 8 | Performance | 4 | 892 Draw Calls pro Frame inkl. Schattenpass, 43k Dreiecke, weiche Schatten, DPR 2 – zu viel für Mittelklasse-Android. APK 5,5 MB ✓, erster Frame nach 0,7 s ✓. |
| 9 | Politur | 5 | Einfache HTML-Panels, Game-Over ohne Animation. Icon und Splash gerendert ✓. |
| 10 | Store-Reife | 2 | Nur Debug-APK, Platzhalter-App-ID, keine Store-Grafiken, kein Datenschutztext, kein Release-Signing. |

## Backlog (nach Hebel sortiert)

1. Performance: Szenerie per Instancing oder Merge zusammenfassen, Schattenwerfer reduzieren, DPR begrenzen.
2. Juice: Squash & Stretch, Hit-Stop, Near-Miss („knapp!“), Kombo für perfekte Durchflüge.
3. Abwechslung: Biome oder Tageszeiten alle ca. 25 Punkte, Musik-Variationen.
4. Session-Loop: schnellerer Neustart (Tippen überall), „Noch X bis zum Rekord“.
5. Onboarding: Geister-Hand im ersten Run statt Text.
6. Store: Release-Build-Setup, Store-Texte, Datenschutz, Screenshots.
7. Meta: Erfolge und Meilensteine, Flug-Spuren als zweite Kosmetik-Kategorie.

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
