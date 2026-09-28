# Play-Store-Vorbereitung

Stand: 27. September 2026. Version 1.0.3 (versionCode 4) ist im internen und im geschlossenen
Alpha-Test verfügbar. Die aktualisierten deutschen und englischen Store-Texte sowie sieben neue
deutsche Screenshots sind zur Prüfung eingereicht.

## Play Console

- Paket-ID: `de.robinrehbein.birdy`
- [App-Dashboard](https://play.google.com/console/u/0/developers/7715182646695737952/app/4976256029001868876/app-dashboard)
- [Interner Test](https://play.google.com/console/u/0/developers/7715182646695737952/app/4976256029001868876/tracks/4700770597020690505)
- [Interner Testbeitritt](https://play.google.com/apps/internaltest/4700770597020690505)
- [Geschlossener Alpha-Test](https://play.google.com/console/u/0/developers/7715182646695737952/app/4976256029001868876/tracks/4699009912977551786)
- Tester-Liste „Tester“: 4 Personen; für Produktionszugriff verlangt Google mindestens 12
  angemeldete Tester im geschlossenen Test über 14 Tage.
- Alpha-Track: Deutschland, Feedback an `hello@robinrehbein.de`.
- Store-Eintrag: Deutsch und Englisch (USA) mit Icon und Vorstellungsgrafik. Die neuen
  deutschen Screenshots sind zur Prüfung eingereicht; Englisch nutzt aktuell die deutschen
  Grafiken als Fallback.
- Datenschutzerklärung: <https://robinrehbein.github.io/birdy/privacy/> (Deutsch und Englisch),
  auch im App-Menü von Version 1.0.2 verlinkt.
- Zielgruppe: 13–15, 16–17 und 18+. IARC: USK 12, PEGI 7. Die bisherige
  Datensicherheitsangabe „keine Datenerhebung“ gilt nur für den bereits veröffentlichten Build.
  Vor einem Release mit AdMob und Play Billing muss der Fragebogen neu beantwortet werden.
- Google Play Games auf dem PC wurde deaktiviert. Die ausstehende Aktivierung wurde vor der
  Einreichung aus der Änderungsliste entfernt.

## Checkliste

| Punkt | Status |
|---|---|
| Release-Bundle (AAB), signiert | ✅ `ANDROID_HOME=/Users/robinrehbein/Library/Android/sdk npm run android:aab` erfolgreich; Bundle 1.0.3 von Google Play angenommen |
| Versionsnummer | ✅ `versionName 1.0.3`, `versionCode 4` (`android/app/build.gradle`); 1.0.2 (versionCode 3) liegt bereits bei Google Play |
| Android-Zurück-Taste | ✅ Spiel → Pause → Menü; Shop und Game-Over → Menü; Menü → App schließen |
| Hochformat, Vollbild, Bildschirm bleibt an | ✅ |
| Freiwillige Rewarded Ads und optionale In-App-Käufe | ⚠️ im Entwicklungszweig, noch nicht veröffentlicht |
| App-Icon 512×512 | ✅ `docs/store/icon-512.png` (eigenständig: Vogel frontal vor Regenbogen und Sonnenuntergangshimmel) |
| Feature-Grafik 1024×500 | ✅ `docs/store/feature-birdy-1024x500.png` (DE), `docs/store/feature-birdy-1024x500-en.png` (EN), gerendert mit `scripts/render-assets.mjs` |
| Screenshots 1080×1920 | ✅ `docs/store/de/` und `docs/store/en/`, je 7 gestaltete Bilder: echter Spielframe im Rahmen auf Himmel mit Claim (`scripts/store-shots.mjs de` bzw. `en`): Menü, Park, Herbstwald, Stachelkaktus, Regenbogen, Vogel-Werkstatt, Welten. Neu seit der visuellen Politur, müssen in Play neu hochgeladen werden. |
| Store-Texte DE/EN | ✅ unten |
| Datenschutzerklärung | ✅ <https://robinrehbein.github.io/birdy/privacy/> über GitHub Pages veröffentlicht |
| Angaben zur Datensicherheit für Monetarisierung | ⚠️ Play-Console-Fragebogen vor Release aktualisieren |
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
5. **Geschlossener Test:** Version 1.0.3 wird an Tester ausgeliefert. Für den späteren
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

Für manuelle Updates einen unbenutzten `versionCode` und `versionName` in
`android/app/build.gradle` setzen. Auch Entwurfs-Bundles verbrauchen einen Versionscode.
Die GitHub-Pipeline berechnet ihren Code aus UTC-Tag, Workflow-Laufnummer und Versuch;
damit liegt er oberhalb des am 28. September 2026 hochgeladenen Entwurfs `26092801`.

## Automatischer Release nach einem Merge

`.github/workflows/play-release.yml` baut nach jedem Push auf `main` (also auch nach einem
Merge) ein signiertes Bundle und veröffentlicht es im internen Test und im geschlossenen
Alpha-Test, sobald sowohl `PLAY_PUBLISH_ENABLED` als auch
`BIRDY_MONETIZATION_RELEASE_READY` auf `true` gesetzt sind. Die zweite Variable bleibt bis
zum Abschluss der Monetarisierungsprüfungen deaktiviert; auch ein manueller Workflow-Start
umgeht diese Sperre nicht. Offener Test und Produktion bleiben ausgeschlossen, solange der
Produktionszugriff fehlt. `PLAY_PUBLISH_ENABLED` ist bereits aktiviert.

Für die Einrichtung unter **GitHub → Settings → Secrets and variables → Actions** werden diese
Repository-Secrets benötigt:

| Secret | Inhalt |
|---|---|
| `UPLOAD_KEYSTORE_BASE64` | Base64-Inhalt von `android/birdy-upload.jks` (ohne Zeilenumbrüche) |
| `UPLOAD_STORE_PASSWORD` | `storePassword` aus der lokalen `android/keystore.properties` |
| `UPLOAD_KEY_ALIAS` | `keyAlias` aus der lokalen `android/keystore.properties` |
| `UPLOAD_KEY_PASSWORD` | `keyPassword` aus der lokalen `android/keystore.properties` |
| `PLAY_SERVICE_ACCOUNT_JSON` | JSON-Schlüssel eines Dienstkontos mit Zugriff auf Birdy in der Play Console |

Das Google-Cloud-Projekt muss die **Google Play Developer API** aktiviert haben. Das Dienstkonto
braucht in der Play Console die Berechtigung, Releases für Birdy in den Test-Tracks zu erstellen
und zu veröffentlichen. Die Variable `PLAY_CLOSED_TRACK` kann den API-Namen des geschlossenen
Tracks festlegen; Standard ist `alpha`. Die Play API meldet `internal` und `alpha` als vorhandene
Tracks. Der Workflow validiert beide Tracks vor dem Commit und veröffentlicht
niemals in `production`.

Secrets und Keystore-Dateien bleiben außerhalb von Git. Nach dem Einrichten einmal den
manuellen Workflow starten und den Status in GitHub Actions und in beiden Play-Tracks prüfen.

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
> wandernde und atmende Lücken und grimmige Stachelkakteen, die im Takt aus den Röhren springen.
>
> 🌈 **Power-ups:** Regenbogen-Unverwundbarkeit, Münz-Magnet und Mini-Vogel.
>
> ✨ **Knapp!** Fliege haarscharf an den Röhren vorbei und hole dir Bonus-Münzen.
>
> 🎩 **Bau deinen Vogel:** In der Vogel-Werkstatt kombinierst du Farben, Muster, Hüte, Brillen
> und Schnäbel – von der Krone bis zur Propellermütze. Dazu 12 Flugspuren.
>
> 🌍 **Neue Welten:** Schalte Winterland, Südsee, Zuckerland und Pilzwald frei, gib den Röhren ein
> neues Design und verbessere deine Power-ups.
>
> 📅 **Jeden Tag etwas Neues:** Tagesmissionen, Erfolge und ein Tagesgeschenk mit Serien-Bonus.
>
> Kostenlos spielbar, ohne Konto. Freiwillige Videoanzeigen geben Münzen oder eine Stunde
> Zugang zu Skins und Welten. Einzelne Skins und Welten sind dauerhaft kaufbar; Münzpakete
> sind optional. Eine Internetverbindung wird für Anzeigen und Käufe benötigt.

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
> grumpy spiky cacti that pop up on the beat.
>
> 🌈 **Power-ups:** rainbow invincibility, coin magnet and mini bird.
>
> ✨ **Close call!** Skim past the pipes for bonus coins.
>
> 🎩 **Build your bird:** mix colours, patterns, hats, glasses and beaks in the bird workshop –
> from a crown to a propeller cap. Plus 12 flight trails.
>
> 🌍 **New worlds:** unlock Winterland, South Seas, Candyland and Mushroom Woods, restyle the pipes
> and upgrade your power-ups.
>
> 📅 **Something new every day:** daily missions, awards and a daily gift with a streak bonus.
>
> Free to play, with no account required. Optional video ads grant coins or one hour of
> access to skins and worlds. Individual skins and worlds can be bought permanently;
> coin packs are optional. Ads and purchases require an internet connection.

**Kategorie:** Spiele → Arcade.
**Tags:** Arcade, Auto-Runner, Casual.

## Datenschutz und Datensicherheit für den Monetarisierungs-Build

Die neue Datenschutzerklärung liegt in `docs/privacy/index.html`. Vor der Veröffentlichung
dieses Builds muss die GitHub-Pages-Seite mit dieser Fassung aktualisiert werden.

Die bisherige Play-Console-Erklärung zum veröffentlichten Build gibt „keine Datenerhebung“,
„keine Werbung“ und „keine In-App-Käufe“ an. Sie ist für den neuen Build falsch. Vor einem
Test-Release in der Play Console Werbung und In-App-Käufe angeben und den
Datensicherheitsfragebogen anhand der tatsächlichen AdMob-, UMP- und Play-Billing-SDK-Daten
neu ausfüllen. Die Übermittlung durch Google-SDKs darf nicht als „keine Daten“ deklariert
werden. Die Angaben müssen nach einem Test auf einem echten Gerät geprüft werden.

**Einstufung (IARC-Fragebogen):**
- Keine Gewalt gegen Figuren, kein Blut; der Vogel stößt nur gegen Röhren.
- Keine Interaktion zwischen Nutzern und kein Glücksspiel; optionale Käufe vorhanden.
- Erwartet: USK 0 / PEGI 3.

## Risiken beim Rechte-Check (bitte bewusst entscheiden)

| Element | Einschätzung | Stand |
|---|---|---|
| Hindernis | Die Piranha-Pflanze (rot mit weißen Punkten) ist durch einen eigenen grimmigen Stachelkaktus mit Blüte ersetzt. | erledigt |
| Grüne Röhren | Generisches Motiv, in vielen Spielen verbreitet. In Kombination mit der Pflanze aber näher an Mario. | niedrig |
| Vogel-Design | Gelber Vogel mit großen Augen und orangem Schnabel, ähnlich wie Flappy Bird. Im Spiel bleibt er (Vorgabe). Das Store-Icon zeigt ihn jetzt in 3D-Frontansicht vor Regenbogen und Sonnenuntergangshimmel statt im Profil vor türkisem Himmel. | reduziert |
| Wortlaut | „Flappy“ kommt in Store-Texten, README und Code nicht mehr vor. | erledigt |
| Schrift „Lilita One“ | SIL Open Font License, kommerziell nutzbar. | ok |
| Musik und Sounds | im Code erzeugt, keine fremden Samples | ok |
