# Birdy

Ein Ein-Tipp-Arcade-Flieger in Low-Poly-3D: Flattere durch Röhren-Lücken, weiche über drei Spuren aus und fliege durch wechselnde Zonen – gebaut mit [Three.js](https://threejs.org) und [Vite](https://vite.dev).

## Spielprinzip

- Der Vogel fliegt automatisch vorwärts, die Kamera folgt von hinten.
- Die Schwerkraft zieht nach unten – mit jedem Flügelschlag geht's nach oben.
- Es gibt **drei Spuren**. Jede Röhren-Reihe hat pro Spur eine eigene Lücke (unterschiedlich hoch); ab 3 Punkten sind manche Spuren komplett blockiert.
- Pro durchflogener Röhren-Reihe gibt es einen Punkt, Münzen sind Bonus.
- Leuchtende Ringe markieren die Lücken, die nächste Reihe leuchtet gelb. Durchflogene Reihen werden durchsichtig, damit sie die Sicht nicht verdecken.
- Ab 6 Punkten bewegen sich manche Lücken auf und ab, ab 10 Punkten springen grimmige Stachelkakteen im Takt der Musik aus den Röhren. Eine Spur pro Reihe bleibt immer „einfach“.
- Mit steigender Punktzahl werden die Lücken kleiner, der Abstand kürzer und das Tempo höher.
- Der Rekord wird lokal im Browser gespeichert.

## Hochformat

Das Spiel ist für Hochkant ausgelegt. Am Desktop wird es als zentrierte 9:16-Spalte angezeigt. Hält man das Handy quer, erscheint ein Hinweis zum Drehen und das Spiel pausiert.

## Steuerung

Auf dem Handy reicht eine einzige Geste: **Tippe auf eine Bahn.** Die Bahn, in der der Vogel fliegt, lässt ihn flattern; eine andere Bahn wechselt direkt dorthin (mit einem kleinen Hüpfer). Die Kamera schwenkt nicht seitlich mit, deshalb liegen die drei Bahnen immer an derselben Stelle (links, Mitte, rechts) und sind auf der Strecke markiert. Ein Tipp direkt auf den Vogel lässt ihn immer flattern, auch während er noch die Bahn wechselt.

Beim **allerersten Start** führt dich eine Geister-Hand durch die erste Runde: erst flattern, dann vor einer blockierten Röhre ausweichen (das Spiel wartet, bis du richtig tippst).

Ein kleiner **Zielring** an der nächsten Reihe zeigt, auf welcher Höhe du ankommst: grün heißt, du passt gerade durch; rot heißt, du würdest anstoßen.

| Aktion | Desktop | Mobil |
| --- | --- | --- |
| Flügelschlag | Leertaste / ↑ / W / Klick | Auf die eigene Bahn oder den Vogel tippen |
| Spur wechseln | ← → / A D / Klick auf eine andere Bahn | Auf eine andere Bahn tippen |
| Neustart | Enter / Leertaste | „Nochmal“-Button |
| Ton an/aus | 🔊-Knopf oben links | 🔊-Knopf oben links |

## Power-ups

Power-ups schweben in Blasen zwischen den Röhren:

| Power-up | Wirkung |
| --- | --- |
| 🌈 Regenbogen | 6 s unverwundbar und schneller, Regenbogen-Spur, Boden federt ab |
| 🧲 Magnet | 9 s lang werden Münzen angezogen |
| 🍄 Mini | 9 s lang ist der Vogel kleiner und passt leichter durch Lücken |

## Sprache

Das Spiel gibt es auf **Deutsch und Englisch**. Die Sprache richtet sich nach dem Handy und lässt sich oben links (DE/EN) umschalten.

## Münzen, Vögel & Missionen

- Gesammelte Münzen werden gespeichert (lokal auf dem Gerät, ohne Account).
- Im **🐦 Shop** schaltest du mit Münzen neue Farben für den Vogel frei (7 Stück, 100–1500 Münzen) und Flugspuren wie Funkeln, Konfetti oder Feuerschweif (6 Stück, 150–1400 Münzen). Die Auswahl wird direkt am 3D-Vogel angezeigt.
- Jeden Tag gibt es **3 Tagesmissionen**, z. B. Münzen sammeln oder an Pflanzen vorbeifliegen. Sie bringen +40, +70 bzw. +120 Münzen und passen zu deinem Rekord: Pflanzen-Missionen gibt es erst, wenn du Pflanzen schon gesehen hast.
- **🏆 Erfolge:** 14 Meilensteine (z. B. 100 Punkte in einem Flug, 5× „Knapp!“ in Folge, 7-Tage-Serie) bringen einmalig 30–300 Münzen.
- Das **🎁 Tagesgeschenk** bringt 20 Münzen, an jedem Folgetag 10 mehr (bis 80). Die Serie reißt ab, wenn du einen Tag auslässt.

Nach einem Crash **tippst du irgendwo**, um nochmal zu spielen. Der Vogel schwebt, bis du das erste Mal tippst.

## Sound

- Die Musik wird im Code erzeugt (WebAudio, keine Audiodateien). Jede Zone hat ihr eigenes Thema mit eigener Tonart, eigenen Instrumenten und eigenem Groove. Ein Durchlauf ist ein 16-taktiger Song mit A-Teil, B-Teil und Breakdown.
- Menü und Game-Over spielen eine ruhige Fassung ohne Schlagzeug. Die Stachelkakteen springen im Takt der Musik.
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

Die Android-App wird mit [Capacitor](https://capacitorjs.com) gebaut: Das Spiel läuft in einer nativen App (WebView mit WebGL). Das eigentliche Spiel funktioniert offline; freiwillige Anzeigen und optionale Play-Käufe benötigen eine Internetverbindung. Die Optik entspricht der Web-Version.

- Hochformat fest, Vollbild ohne Status- und Navigationsleiste
- Bildschirm bleibt beim Spielen an
- Beim Wechsel in den Hintergrund pausiert das Spiel
- App-Icon und Splash-Screen zeigen den 3D-Vogel von vorn im Zielring
- Die Zurück-Taste pausiert im Spiel, führt aus Pause, Shop und Game-Over ins Menü und schließt im Menü die App

Voraussetzungen: Node.js, JDK 21 und das Android SDK (z. B. über Android Studio).

```bash
npm run android:sync   # Web-Build erzeugen und in das Android-Projekt kopieren
npm run android:open   # Projekt in Android Studio öffnen (Emulator/Gerät starten)
npm run android:apk    # Debug-APK bauen -> android/app/build/outputs/apk/debug/app-debug.apk
npm run android:aab    # signiertes Release-Bundle für den Play Store (siehe docs/STORE.md)
```

Die Debug-APK lässt sich direkt auf dem Handy installieren (Installation aus unbekannten Quellen erlauben). Alles für den Play Store – Signatur-Schlüssel, Store-Texte, Datenschutz, Grafiken und offene Entscheidungen – steht in [`docs/STORE.md`](docs/STORE.md).

## Native App (Kotlin/KMP)

Parallel zur Capacitor-App entsteht in [`native/`](native/) eine eigenständige native
Android-App: dieselbe Spiellogik, aber in Kotlin statt JavaScript, mit dem Spielkern als
**Kotlin-Multiplatform**-Modul, damit später eine iOS-App darauf aufsetzen kann. Details, Build-
und Test-Anleitung stehen in [`native/README.md`](native/README.md); Architektur in
[`docs/native/ARCHITECTURE.md`](docs/native/ARCHITECTURE.md), der iOS-Plan in
[`docs/native/IOS.md`](docs/native/IOS.md).

Die Capacitor-App in diesem Verzeichnis (`android/`, `src/`) bleibt der **produktive Build**, bis
die native App vollständige Feature-Parität erreicht hat und das explizit freigegeben ist
(„parity sign-off“). Bis dahin ändert sich am obigen Abschnitt „Android-App“ nichts.

## Struktur

- `src/main.js` – Spielschleife, Physik, Kollision, Eingabe, UI-Zustände
- `src/world.js` – Szene, Himmel, Boden, Kulisse, Röhren (inkl. Bewegung, Ringe, Stachelkakteen) und Münzen
- `src/bird.js` – Low-Poly-Vogel aus Grundkörpern inkl. Flügelanimation
- `src/audio.js` – Musik und Soundeffekte per WebAudio (keine Audiodateien nötig)
- `src/powerups.js` – Power-up-Definitionen und Modelle
- `src/i18n.js` – Texte auf Deutsch/Englisch, Sprachwahl
- `src/progress.js` – Münzen, Skins, Tagesmissionen (localStorage)
- `src/bot.js` – Playtest-Bots (Anfänger / geübt / Profi) für die Headless-Simulation
- `scripts/playtest.mjs`, `scripts/perf.mjs` – automatischer Bot-Playtest und Render-Budget-Messung
- `docs/PROGRESS.md` – Bewertungsbogen und Iterations-Log
- `docs/STORE.md`, `docs/store/` – Play-Store-Vorbereitung: Texte, Datenschutz, Grafiken, Signieren
- `scripts/store-shots.mjs` – rendert die Store-Screenshots aus echten Spielszenen
- `src/effects.js` – Partikel (Münz-Funken, Federn, Regenbogen-Spur)
- `src/style.css` – HUD und Menüs
- `android/` – natives Android-Projekt (Capacitor), inkl. Icons, Splash und `MainActivity`
