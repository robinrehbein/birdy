# Birdy 3D

Ein Flappy-Bird-Klon in 3D-Optik im Stil von Endless-Runnern wie Subway Surfers oder Temple Run – gebaut mit [Three.js](https://threejs.org) und [Vite](https://vite.dev).

## Spielprinzip

- Der Vogel fliegt automatisch vorwärts, die Kamera folgt von hinten.
- Wie bei Flappy Bird zieht die Schwerkraft nach unten – mit jedem Flügelschlag geht's nach oben.
- Es gibt **drei Spuren**. Jede Röhren-Reihe hat pro Spur eine eigene Lücke (unterschiedlich hoch); ab 3 Punkten sind manche Spuren komplett blockiert.
- Pro durchflogener Röhren-Reihe gibt es einen Punkt, Münzen sind Bonus.
- Mit steigender Punktzahl werden die Lücken kleiner, der Abstand kürzer und das Tempo höher.
- Der Rekord wird lokal im Browser gespeichert.

## Hochformat

Das Spiel ist für Hochkant ausgelegt. Am Desktop wird es als zentrierte 9:16-Spalte angezeigt. Hält man das Handy quer, erscheint ein Hinweis zum Drehen und das Spiel pausiert.

## Steuerung

| Aktion | Desktop | Mobil |
| --- | --- | --- |
| Flügelschlag | Leertaste / ↑ / W / Klick | Tippen |
| Spur wechseln | ← → / A D | Nach links/rechts wischen |
| Neustart | Enter / Leertaste | „Nochmal“-Button |

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
- `src/world.js` – Szene, Licht, Boden, Kulisse, Wolken, Röhren und Münzen
- `src/bird.js` – Low-Poly-Vogel aus Grundkörpern inkl. Flügelanimation
- `src/audio.js` – Soundeffekte per WebAudio (keine Audiodateien nötig)
- `src/style.css` – HUD und Menüs
- `android/` – natives Android-Projekt (Capacitor), inkl. Icons, Splash und `MainActivity`
