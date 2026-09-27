# Birdy 3D

Ein Flappy-Bird-Klon in 3D-Optik im Stil von Endless-Runnern wie Subway Surfers oder Temple Run – gebaut mit [Three.js](https://threejs.org) und [Vite](https://vite.dev).

## Spielprinzip

- Der Vogel fliegt automatisch vorwärts, die Kamera folgt von hinten.
- Wie bei Flappy Bird zieht die Schwerkraft nach unten – mit jedem Flügelschlag geht's nach oben.
- Es gibt **drei Spuren**. Jede Röhren-Reihe hat pro Spur eine eigene Lücke (unterschiedlich hoch); ab 3 Punkten sind manche Spuren komplett blockiert.
- Pro durchflogener Röhren-Reihe gibt es einen Punkt, Münzen sind Bonus.
- Leuchtende Ringe markieren die Lücken, die nächste Reihe leuchtet gelb. Durchflogene Reihen werden durchsichtig, damit sie die Sicht nicht verdecken.
- Ab 6 Punkten bewegen sich manche Lücken auf und ab, ab 10 Punkten schnappen Piranha-Pflanzen im Takt der Musik aus den Röhren. Eine Spur pro Reihe bleibt immer „einfach“.
- Mit steigender Punktzahl werden die Lücken kleiner, der Abstand kürzer und das Tempo höher.
- Der Rekord wird lokal im Browser gespeichert.

## Hochformat

Das Spiel ist für Hochkant ausgelegt. Am Desktop wird es als zentrierte 9:16-Spalte angezeigt. Hält man das Handy quer, erscheint ein Hinweis zum Drehen und das Spiel pausiert.

## Steuerung

Auf dem Handy reicht eine einzige Geste: **Tippe beim Vogel**, dann flattert er. **Tippe links oder rechts neben den Vogel**, dann weicht er eine Spur in diese Richtung aus und macht dabei nur einen kleinen Hüpfer. Die Zonen richten sich nach der Position, an der der Vogel auf dem Bildschirm zu sehen ist. So löst ein Tipp auf den Vogel auch in den Außenspuren immer einen Flügelschlag aus.

Beim **allerersten Start** führt dich eine Geister-Hand durch die erste Runde: erst flattern, dann vor einer blockierten Röhre ausweichen (das Spiel wartet, bis du richtig tippst).

Ein kleiner **Zielring** an der nächsten Reihe zeigt, auf welcher Höhe du ankommst: grün heißt, du passt gerade durch; rot heißt, du würdest anstoßen.

| Aktion | Desktop | Mobil |
| --- | --- | --- |
| Flügelschlag | Leertaste / ↑ / W / Klick | Beim Vogel tippen |
| Spur wechseln | ← → / A D / Klick neben den Vogel | Links/rechts neben den Vogel tippen |
| Neustart | Enter / Leertaste | „Nochmal“-Button |
| Ton an/aus | 🔊-Knopf oben links | 🔊-Knopf oben links |

## Power-ups

Power-ups schweben in Blasen zwischen den Röhren:

| Power-up | Wirkung |
| --- | --- |
| 🌈 Regenbogen | 6 s unverwundbar und schneller, Regenbogen-Spur, Boden federt ab |
| 🧲 Magnet | 9 s lang werden Münzen angezogen |
| 🍄 Mini | 9 s lang ist der Vogel kleiner und passt leichter durch Lücken |

## Münzen, Vögel & Missionen

- Gesammelte Münzen werden gespeichert (lokal auf dem Gerät, ohne Account).
- Unter **🐦 Vögel** schaltest du mit Münzen neue Farben für den Vogel frei (7 Stück, 100–1500 Münzen). Die Auswahl wird direkt am 3D-Vogel angezeigt.
- Jeden Tag gibt es **3 Tagesmissionen**, z. B. Münzen sammeln oder an Pflanzen vorbeifliegen. Sie bringen +40, +70 bzw. +120 Münzen und passen zu deinem Rekord: Pflanzen-Missionen gibt es erst, wenn du Pflanzen schon gesehen hast.
- Das **🎁 Tagesgeschenk** bringt 20 Münzen, an jedem Folgetag 10 mehr (bis 80). Die Serie reißt ab, wenn du einen Tag auslässt.

Nach einem Crash **tippst du irgendwo**, um nochmal zu spielen. Der Vogel schwebt, bis du das erste Mal tippst.

## Sound

- Die Musik wird im Code erzeugt (WebAudio, keine Audiodateien). Jede Zone hat ihr eigenes Thema mit eigener Tonart, eigenen Instrumenten und eigenem Groove. Ein Durchlauf ist ein 16-taktiger Song mit A-Teil, B-Teil und Breakdown.
- Menü und Game-Over spielen eine ruhige Fassung ohne Schlagzeug. Die Piranha-Pflanzen schnappen im Takt der Musik.
- Soundeffekte gibt es für Flattern, Ausweichen, Punkte, Münzen, „Knapp!“, Power-ups, Zonenwechsel und Crash. Oben links schaltest du den Ton aus (samt Vibration), die Einstellung bleibt gespeichert.

## Entwicklung

```bash
npm install
npm run dev      # Dev-Server (auch im LAN erreichbar, z. B. zum Testen am Handy)
npm run build    # Produktions-Build nach dist/
npm run preview  # Build lokal ansehen
```

Der Build nutzt relative Pfade und kann daher auf jedem statischen Hosting (z. B. GitHub Pages, Netlify, Vercel) unter beliebigem Pfad liegen.

## Android-App

Die Android-App wird mit [Capacitor](https://capacitorjs.com) gebaut: Das Spiel läuft in einer nativen App (WebView mit WebGL), komplett offline, Optik identisch zur Web-Version.

- Hochformat fest, Vollbild ohne Status- und Navigationsleiste
- Bildschirm bleibt beim Spielen an
- Beim Wechsel in den Hintergrund pausiert das Spiel
- App-Icon und Splash-Screen zeigen den 3D-Vogel

Voraussetzungen: Node.js, JDK 21 und das Android SDK (z. B. über Android Studio).

```bash
npm run android:sync   # Web-Build erzeugen und in das Android-Projekt kopieren
npm run android:open   # Projekt in Android Studio öffnen (Emulator/Gerät starten)
npm run android:apk    # Debug-APK bauen -> android/app/build/outputs/apk/debug/app-debug.apk
```

Die Debug-APK lässt sich direkt auf dem Handy installieren (Installation aus unbekannten Quellen erlauben). Für den Play Store braucht es einen eigenen Signatur-Schlüssel und ein Release-Bundle (`./gradlew bundleRelease`). Die App-ID `app.birdy.game` in `capacitor.config.json` und `android/app/build.gradle` sollte vorher auf eine eigene Domain geändert werden, da sie nach der Veröffentlichung nicht mehr änderbar ist.

## Struktur

- `src/main.js` – Spielschleife, Physik, Kollision, Eingabe, UI-Zustände
- `src/world.js` – Szene, Himmel, Boden, Kulisse, Röhren (inkl. Bewegung, Ringe, Piranha-Pflanzen) und Münzen
- `src/bird.js` – Low-Poly-Vogel aus Grundkörpern inkl. Flügelanimation
- `src/audio.js` – Musik und Soundeffekte per WebAudio (keine Audiodateien nötig)
- `src/powerups.js` – Power-up-Definitionen und Modelle
- `src/progress.js` – Münzen, Skins, Tagesmissionen (localStorage)
- `src/bot.js` – Playtest-Bots (Anfänger / geübt / Profi) für die Headless-Simulation
- `scripts/playtest.mjs`, `scripts/perf.mjs` – automatischer Bot-Playtest und Render-Budget-Messung
- `docs/PROGRESS.md` – Bewertungsbogen und Iterations-Log
- `src/effects.js` – Partikel (Münz-Funken, Federn, Regenbogen-Spur)
- `src/style.css` – HUD und Menüs
- `android/` – natives Android-Projekt (Capacitor), inkl. Icons, Splash und `MainActivity`
