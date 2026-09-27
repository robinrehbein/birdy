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
| 1 | Onboarding | 5 | Anfänger-Bot: erster Tod im Median bei 11,9 s (vorher 8,4 s). Aber: Erklärung nur als Text im Startmenü, kein Tutorial im Spiel. |
| 2 | Game Feel / Juice | 5 | Vorhanden: Flatter-Sound, Punkte-Pop, Partikel, Kamera-Shake und Blitz beim Tod. Fehlt: Squash & Stretch, Hit-Stop, Near-Miss-Feedback, Speed-Lines. |
| 3 | Fairness & Kurve | 5 | Profi-Bot stirbt im Median bei 28 Punkten, 96 % an der oberen Röhre. Höhensprünge zwischen Reihen sind unbegrenzt (bis 5 Einheiten in 0,8 s). |
| 4 | Abwechslung | 4 | Neues nur bei 6 (bewegte Lücken) und 10 Punkten (Pflanzen), plus Power-ups. Keine Biome oder Events, gleiche Optik über den ganzen Run. |
| 5 | Session-Loop | 5 | Neustart per Button nach ca. 1,7 s. Runs im Median: Anfänger 12 s, geübt 29 s (Ziel 30–90 s). Kein Sog-Element („nur noch 2 bis zum Rekord“). |
| 6 | Meta-Progression | 1 | Münzen haben keinen Zweck, nur ein Rekord-Wert. |
| 7 | Audio | 6 | Prozedurale Chiptune-Musik und Effekte. Der Loop ist nur ca. 31 s lang, Wiederholung nervt vermutlich nach 10 Minuten. Mix ist ungeprüft. |
| 8 | Performance | 4 | 892 Draw Calls pro Frame inkl. Schattenpass, 43k Dreiecke, weiche Schatten, DPR 2 – zu viel für Mittelklasse-Android. APK 5,5 MB ✓, erster Frame nach 0,7 s ✓. |
| 9 | Politur | 5 | Einfache HTML-Panels, Game-Over ohne Animation. Icon und Splash gerendert ✓. |
| 10 | Store-Reife | 2 | Nur Debug-APK, Platzhalter-App-ID, keine Store-Grafiken, kein Datenschutztext, kein Release-Signing. |

## Backlog (nach Hebel sortiert)

1. Fairness: Höhenunterschied zwischen aufeinanderfolgenden Reihen an die erreichbare Flughöhe koppeln.
2. Meta: Münz-Shop mit Vogel-Skins, tägliche Missionen, Meilensteine.
3. Performance: Szenerie per Instancing oder Merge zusammenfassen, Schattenwerfer reduzieren, DPR begrenzen.
4. Juice: Squash & Stretch, Hit-Stop, Near-Miss („knapp!“), Kombo für perfekte Durchflüge.
5. Abwechslung: Biome oder Tageszeiten alle ca. 25 Punkte, Musik-Variationen.
6. Session-Loop: schnellerer Neustart (Tippen überall), „Noch X bis zum Rekord“.
7. Onboarding: Geister-Hand im ersten Run statt Text.
8. Store: Release-Build-Setup, Store-Texte, Datenschutz, Screenshots.

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
