# Play-Store-Vorbereitung

Stand: 27. September 2026. App in der Play Console angelegt; interner Test mit Version 1.0.2
veröffentlicht. Geschlossener Alpha-Test mit Version 1.0.2 und Store-Einträgen zur Prüfung
eingereicht; die schnellen Vorabprüfungen laufen noch.

## Play Console

- Paket-ID: `de.robinrehbein.birdy`
- [App-Dashboard](https://play.google.com/console/u/0/developers/7715182646695737952/app/4976256029001868876/app-dashboard)
- [Interner Test](https://play.google.com/console/u/0/developers/7715182646695737952/app/4976256029001868876/tracks/4700770597020690505)
- [Interner Testbeitritt](https://play.google.com/apps/internaltest/4700770597020690505)
- [Geschlossener Alpha-Test](https://play.google.com/console/u/0/developers/7715182646695737952/app/4976256029001868876/tracks/4699009912977551786)
- Tester-Liste „Tester“: 4 Personen; für Produktionszugriff verlangt Google mindestens 12
  angemeldete Tester im geschlossenen Test über 14 Tage.
- Alpha-Track: Deutschland, Feedback an `hello@robinrehbein.de`.
- Store-Eintrag: Deutsch und Englisch (USA) mit Icon, Vorstellungsgrafik und sechs Screenshots
  zur Prüfung eingereicht.
- Datenschutzerklärung: <https://robinrehbein.github.io/birdy/privacy/> (Deutsch und Englisch),
  auch im App-Menü von Version 1.0.2 verlinkt.
- Zielgruppe: 13–15, 16–17 und 18+. IARC: USK 12, PEGI 7. Datensicherheit: keine Datenerhebung
  oder Weitergabe angegeben; lokale Speicherung und mögliche Android-Backups sind in der
  Datenschutzerklärung beschrieben.
- Google Play Games auf dem PC wurde deaktiviert. Die ausstehende Aktivierung wurde vor der
  Einreichung aus der Änderungsliste entfernt.

## Checkliste

| Punkt | Status |
|---|---|
| Release-Bundle (AAB), signiert | ✅ `ANDROID_HOME=/Users/robinrehbein/Library/Android/sdk npm run android:aab` erfolgreich; Bundle 1.0.2 von Google Play angenommen |
| Versionsnummer | ✅ `versionName 1.0.2`, `versionCode 3` (`android/app/build.gradle`) |
| Android-Zurück-Taste | ✅ Spiel → Pause → Menü; Shop und Game-Over → Menü; Menü → App schließen |
| Hochformat, Vollbild, Bildschirm bleibt an | ✅ |
| Offline, keine Werbung, keine Käufe, kein Tracking | ✅ |
| App-Icon 512×512 | ✅ `docs/store/icon-512.png` (eigenständig: Vogel frontal vor Regenbogen und Sonnenuntergangshimmel) |
| Feature-Grafik 1024×500 | ✅ `docs/store/feature-birdy-1024x500.png` |
| Screenshots 1080×1920 | ✅ `docs/store/screenshot-1…6` aus einem echten Run (`scripts/store-shots.mjs`): Menü, Park, Zonen-Banner, Piranha-Pflanze, Regenbogen, Spuren-Shop |
| Store-Texte DE/EN | ✅ unten |
| Datenschutzerklärung | ✅ <https://robinrehbein.github.io/birdy/privacy/> über GitHub Pages veröffentlicht |
| Angaben zur Datensicherheit | ✅ unten |
| **App-ID** | ✅ `de.robinrehbein.birdy` in Capacitor und Android konfiguriert |
| **Signatur-Schlüssel** | ✅ Upload-Schlüssel lokal erstellt; Backup außerhalb des Projekts erforderlich |
| **Rechte-Check** | ⚠️ siehe „Risiken“ |
| Test auf echten Geräten | ⚠️ noch offen (fps-Anzeige: 5× auf den Titel tippen) |

## Entscheidungen und verbleibende Schritte

1. **App-ID:** `de.robinrehbein.birdy` ist eingerichtet. Nach dem ersten Play-Upload kann die
   Paket-ID für diese App nicht mehr geändert werden.
2. **Name:** Birdy ist als App- und Store-Name vorgesehen. Vor einem öffentlichen Launch
   sollte eine Markenrecherche (DPMA/EUIPO/WIPO, Klassen 9 und 41) erfolgen.
3. **Zielgruppe:** 13 Jahre und älter wurde bestätigt und in der Play Console gespeichert.
4. **Datenschutz-URL:** Die App-spezifische GitHub-Pages-Seite ist veröffentlicht und in der
   Play Console sowie in Version 1.0.2 der App verlinkt.
5. **Geschlossener Test:** Google muss die eingereichten Änderungen prüfen. Für den späteren
   Produktionszugriff fehlen noch mindestens 12 angemeldete Tester über 14 Tage; derzeit
   stehen vier Personen auf der Tester-Liste und es sind noch keine Beitritte erfasst.

## Release signieren

Der Upload-Schlüssel liegt lokal unter `android/birdy-upload.jks`; die Zugangsdaten liegen in
`android/keystore.properties`. Beide Dateien sind aus Git ausgeschlossen. Erstelle ein sicheres
Backup außerhalb dieses Projekts. Bei Play App Signing kann ein verlorener Upload-Schlüssel
zurückgesetzt werden, aber das erfordert ein Verfahren über die Play Console.

```bash
keytool -list -keystore android/birdy-upload.jks -alias birdy-upload
```

Die vorhandene `android/keystore.properties` hat dieses Format:

```properties
storeFile=birdy-upload.jks
storePassword=…
keyAlias=birdy-upload
keyPassword=…
```

Dann `npm run android:aab`. Das Bundle liegt unter
`android/app/build/outputs/bundle/release/app-release.aab`. In der Play Console **Play App
Signing** aktivieren; dann ist dein Schlüssel nur noch der Upload-Schlüssel.

Für jedes Update `versionCode` (+1) und `versionName` in `android/app/build.gradle` erhöhen.

## Store-Eintrag – Deutsch

**Titel (max. 30):** Birdy – Tippen & Fliegen

**Kurzbeschreibung (max. 80):** Tippen, ausweichen, durchfliegen – ein Ein-Finger-Flieger in Low-Poly-3D.

**Beschreibung:**

> Flieg so weit du kannst! Birdy ist ein bunter Ein-Finger-Arcade-Flieger in Low-Poly-3D.
>
> 👆 **Ein Tipp genügt:** Tippe beim Vogel, um zu flattern – tippe daneben, um auf eine andere
> Spur auszuweichen. Ein Zielring zeigt dir, ob du durch die nächste Lücke passt.
>
> 🌳 **Vier Zonen:** Fliege vom Stadtpark in den Herbstwald, durch den Canyon bis in den
> Blütenhain – jede Zone mit eigener Tageszeit, eigener Musik und eigenen Hindernissen:
> wandernde und atmende Lücken und Piranha-Pflanzen, die im Takt schnappen.
>
> 🌈 **Power-ups:** Regenbogen-Unverwundbarkeit, Münz-Magnet und Mini-Vogel.
>
> ✨ **Knapp!** Fliege haarscharf an den Röhren vorbei und hole dir Bonus-Münzen.
>
> 🐦 **Sammeln:** Schalte mit Münzen neue Vögel frei, erfülle Tagesmissionen und hole dir jeden
> Tag dein Geschenk – mit Serien-Bonus.
>
> Keine Werbung. Keine In-App-Käufe. Kein Konto. Funktioniert komplett offline.

## Store listing – English

**Title:** Birdy – Tap & Fly

**Short description:** Tap, dodge, fly through – a one-finger low-poly 3D flyer.

**Description:**

> Fly as far as you can! Birdy is a colourful one-finger arcade flyer in low-poly 3D.
>
> 👆 **One tap is all it takes:** tap the bird to flap, tap beside it to dodge into another
> lane. A target ring shows whether you'll make the next gap.
>
> 🌳 **Four zones:** from the city park through the autumn forest and the canyon to the blossom
> grove – each with its own time of day, music and obstacles: moving and breathing gaps and
> snapping plants that bite on the beat.
>
> 🌈 **Power-ups:** rainbow invincibility, coin magnet and mini bird.
>
> ✨ **Close call!** Skim past the pipes for bonus coins.
>
> 🐦 **Collect:** unlock new birds with coins, complete daily missions and claim a daily gift
> with a streak bonus.
>
> No ads. No in-app purchases. No account. Works completely offline.

**Kategorie:** Spiele → Arcade.
**Tags:** Arcade, Auto-Runner, Casual.

## Datenschutzerklärung (Text zum Veröffentlichen)

> **Datenschutzerklärung für „Birdy“**
>
> Birdy erhebt oder überträgt keine Nutzerdaten an den Entwickler oder Dritte.
>
> - Die App stellt keine Verbindung zu Servern her und enthält keine Werbung, keine
>   Analyse- oder Tracking-Dienste und keine In-App-Käufe.
> - Spielstand, Rekord, Münzen, freigeschaltete Vögel und Einstellungen (Ton an/aus,
>   Grafikstufe) werden ausschließlich lokal auf deinem Gerät gespeichert und beim
>   Deinstallieren gelöscht.
> - Die Berechtigung „Vibration“ wird nur für kurze Vibrationen im Spiel genutzt.
>
> Verantwortlicher und Kontakt: Robin Rehbein, Stiegelstraße 26, 71701 Schwieberdingen,
> Deutschland; hello@robinrehbein.de.
>
> Stand: 27. September 2026

## Angaben zur Datensicherheit (Play Console)

| Frage | Antwort |
|---|---|
| Erhebt oder teilt die App Nutzerdaten? | **Nein** |
| Werden Daten verschlüsselt übertragen? | nicht zutreffend (keine Übertragung) |
| Können Nutzer die Löschung beantragen? | nicht zutreffend (Daten nur lokal; Deinstallation löscht sie) |
| Werbung | **Nein** |
| In-App-Käufe | **Nein** |

**Einstufung (IARC-Fragebogen):**
- Keine Gewalt gegen Figuren, kein Blut; der Vogel stößt nur gegen Röhren.
- Keine Interaktion zwischen Nutzern, keine Käufe, kein Glücksspiel.
- Erwartet: USK 0 / PEGI 3.

## Risiken beim Rechte-Check (bitte bewusst entscheiden)

| Element | Einschätzung | Stand |
|---|---|---|
| **Piranha-Pflanze** | Rot mit weißen Punkten und Röhren-Kontext. Das erinnert stark an Nintendos Piranha-Pflanze, sie war ja auch ausdrücklich so gewünscht („aka Mario“). **Größtes Risiko.** Empfehlung: eigenes Design, z. B. violette Venusfliegenfalle mit Zähnen und ohne weiße Punkte. | offen – deine Entscheidung |
| Grüne Röhren | Generisches Motiv, in vielen Spielen verbreitet. In Kombination mit der Pflanze aber näher an Mario. | niedrig |
| Vogel-Design | Gelber Vogel mit großen Augen und orangem Schnabel, ähnlich wie Flappy Bird. Im Spiel bleibt er (Vorgabe). Das Store-Icon zeigt ihn jetzt in 3D-Frontansicht vor Regenbogen und Sonnenuntergangshimmel statt im Profil vor türkisem Himmel. | reduziert |
| Wortlaut | „Flappy“ kommt in Store-Texten, README und Code nicht mehr vor. | erledigt |
| Schrift „Lilita One“ | SIL Open Font License, kommerziell nutzbar. | ok |
| Musik und Sounds | im Code erzeugt, keine fremden Samples | ok |
