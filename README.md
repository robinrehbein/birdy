# Birdy

Ein Ein-Finger-Arcade-Flieger in Low-Poly-3D: Flattere durch Röhren-Lücken, wechsle zwischen drei
Spuren und fliege durch wechselnde Zonen – eine native Android-App, gebaut mit Kotlin und
[Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html) (Compose Multiplatform-UI,
eigener OpenGL-ES-Renderer).

## Spielprinzip

- Der Vogel fliegt automatisch vorwärts, die Kamera folgt von hinten.
- Die Schwerkraft zieht nach unten – mit jedem Flügelschlag geht's nach oben.
- Es gibt **drei Spuren**. Jede Röhren-Reihe hat pro Spur eine eigene Lücke (unterschiedlich hoch); ab 3 Punkten sind manche Spuren komplett blockiert.
- Pro durchflogener Röhren-Reihe gibt es einen Punkt, Münzen sind Bonus.
- Leuchtende Ringe markieren die Lücken, die nächste Reihe leuchtet gelb. Durchflogene Reihen werden durchsichtig, damit sie die Sicht nicht verdecken.
- Ab 6 Punkten bewegen sich manche Lücken auf und ab, ab 10 Punkten springen grimmige Stachelkakteen im Takt der Musik aus den Röhren. Eine Spur pro Reihe bleibt immer „einfach“.
- Mit steigender Punktzahl werden die Lücken kleiner, der Abstand kürzer und das Tempo höher.
- Der Rekord wird lokal auf dem Gerät gespeichert.

## Hochformat

Das Spiel ist fest auf Hochkant und Vollbild ausgelegt (keine Status- und Navigationsleiste), der
Bildschirm bleibt beim Spielen an.

## Steuerung

Auf dem Handy gibt es zwei Gesten:

- **Nach links/rechts wischen** wechselt eine Spur. Der Vogel bleibt in dieser Spur, bis die
  nächste seitliche Wisch-Geste kommt.
- **Nach oben wischen oder irgendwo antippen** lässt den Vogel in der aktuellen Spur flattern –
  wo genau getippt wird, spielt dabei keine Rolle.

Beim **allerersten Start** führt dich eine Geister-Hand durch die erste Runde: erst eine
Flatter-Lektion (Tippen oder nach oben wischen), dann friert das Spiel vor einer blockierten
Röhren-Reihe ein, bis du zur Seite wischst.

Ein kleiner **Zielring** an der nächsten Reihe zeigt, auf welcher Höhe du ankommst: grün heißt, du passt gerade durch; rot heißt, du würdest anstoßen.

Nach einem Crash **tippst du irgendwo**, um nochmal zu spielen. Der Vogel schwebt, bis du das erste Mal tippst.

| Aktion | Mobil |
| --- | --- |
| Flügelschlag | Irgendwo antippen oder nach oben wischen |
| Spur wechseln | Nach links/rechts wischen |
| Neustart | Nach einem Crash irgendwo antippen |
| Ton an/aus | 🔊-Knopf oben links |

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

## Sound

- Die Musik wird zur Laufzeit im Code erzeugt: ein prozeduraler Synthesizer in Kotlin, der über
  `AudioTrack` ausgegeben wird (keine Audiodateien). Jede Zone hat ihr eigenes Thema mit eigener
  Tonart, eigenen Instrumenten und eigenem Groove. Ein Durchlauf ist ein 16-taktiger Song mit
  A-Teil, B-Teil und Breakdown.
- Menü und Game-Over spielen eine ruhige Fassung ohne Schlagzeug. Die Stachelkakteen springen im Takt der Musik.
- Soundeffekte gibt es für Flattern, Ausweichen, Punkte, Münzen, „Knapp!“, Power-ups, Zonenwechsel und Crash. Oben links schaltest du den Ton aus (samt Vibration), die Einstellung bleibt gespeichert.

## Erinnerungen

Optional erinnert Birdy höchstens einmal pro Tag lokal an das Tagesgeschenk bzw. die laufende Serie
(am nächsten Tag zur typischen Spielzeit, standardmäßig 18 Uhr, nie zwischen 21:30 und 09:00). Es gibt
keinen Server. Ab Android 13 fragt die App einmalig nach dem dritten Spiel nach der Berechtigung.
Oben links schaltet die Geschenk-Schaltfläche die Erinnerungen aus und an.

## Speicherstand

Spielstand, Rekord, Münzen, freigeschaltete Inhalte und Einstellungen liegen ausschließlich lokal
auf dem Gerät in `SharedPreferences`, ohne Account und ohne Server. Wer die alte Capacitor-App
(WebView, `localStorage`) bereits installiert hatte, bekommt beim ersten Start der nativen App
über dieselbe Paket-ID eine einmalige, automatische Migration der alten Daten (Münzen, Rekord,
Skins, Einstellungen) – siehe [`native/README.md`](native/README.md#migration-alter-spielstände-capacitor--nativ).

## Entwicklung

Voraussetzungen: JDK 21, Android SDK (`ANDROID_HOME`/`ANDROID_SDK_ROOT` gesetzt oder
`native/local.properties` mit `sdk.dir=...`).

```bash
cd native
./gradlew :androidApp:assembleDebug            # Debug-APK
./gradlew :shared:testDebugUnitTest :shared:desktopTest   # Unit-Tests gegen die goldenen Fixtures
./gradlew :screenshots:run                     # Headless-Screenshots -> native/build/shots/*.png
```

Details, Modul- und Testübersicht: [`native/README.md`](native/README.md).

## Projektstruktur

- [`native/`](native/) – die App, als Kotlin-Multiplatform-Projekt:
  - `shared/` – Spielkern und UI als KMP-Bibliothek (`commonMain`: Engine/Renderer-Logik,
    Simulation, Fortschritt/Katalog/Missionen, i18n, Audio-Synthesizer, Compose-Multiplatform-UI;
    `androidMain`/`desktopMain`: Plattform-Anbindung).
  - `androidApp/` – die Android-App (Activity, GL-Fläche, Anzeigen, Play Billing).
  - `screenshots/` – Headless-Tool, das dieselbe Engine ohne GPU/Emulator rendert.
- [`docs/native/`](docs/native/) – Architektur (`ARCHITECTURE.md`), Release (`RELEASE.md`),
  iOS-Plan (`IOS.md`), Verhaltensspezifikationen (`spec/`) und goldene Testfixtures (`golden/`).
- [`docs/STORE.md`](docs/STORE.md), [`docs/store/`](docs/store/) – Play-Store-Vorbereitung: Texte,
  Datenschutz, Grafiken, Signieren.
- [`docs/ASSETS.md`](docs/ASSETS.md) – Fremde Assets und Lizenzen (Schriften).
- [`docs/MONETIZATION.md`](docs/MONETIZATION.md) – Anzeigen und Play Billing.
- [`docs/index.html`](docs/index.html), [`docs/privacy/`](docs/privacy/) – über GitHub Pages
  veröffentlichte rechtliche Seiten.

## Release

Signierte Release-Builds, Versionsnummern und der Play-Store-Veröffentlichungsweg (GitHub Actions)
stehen in [`docs/native/RELEASE.md`](docs/native/RELEASE.md); der Play-Store-Eintrag (Texte,
Checkliste, offene Entscheidungen) in [`docs/STORE.md`](docs/STORE.md).

## iOS

`shared/commonMain` enthält keinen Android-spezifischen Code, eine iOS-App kann also auf demselben
Spielkern aufsetzen. Der konkrete Plan steht in [`docs/native/IOS.md`](docs/native/IOS.md).
