# Birdy 3D

Ein Flappy-Bird-Klon in 3D-Optik im Stil von Endless-Runnern wie Subway Surfers oder Temple Run – gebaut mit [Three.js](https://threejs.org) und [Vite](https://vite.dev).

## Spielprinzip

- Der Vogel fliegt automatisch vorwärts, die Kamera folgt von hinten.
- Wie bei Flappy Bird zieht die Schwerkraft nach unten – mit jedem Flügelschlag geht's nach oben.
- Es gibt **drei Spuren**. Jede Röhren-Reihe hat pro Spur eine eigene Lücke (unterschiedlich hoch); ab 3 Punkten sind manche Spuren komplett blockiert.
- Pro durchflogener Röhren-Reihe gibt es einen Punkt, Münzen sind Bonus.
- Mit steigender Punktzahl werden die Lücken kleiner, der Abstand kürzer und das Tempo höher.
- Der Rekord wird lokal im Browser gespeichert.

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

## Struktur

- `src/main.js` – Spielschleife, Physik, Kollision, Eingabe, UI-Zustände
- `src/world.js` – Szene, Licht, Boden, Kulisse, Wolken, Röhren und Münzen
- `src/bird.js` – Low-Poly-Vogel aus Grundkörpern inkl. Flügelanimation
- `src/audio.js` – Soundeffekte per WebAudio (keine Audiodateien nötig)
- `src/style.css` – HUD und Menüs
