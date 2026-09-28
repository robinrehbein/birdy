# Play-Store-Vorbereitung

Stand: 28. September 2026. Version 1.0.3 (versionCode 4) ist im internen und im geschlossenen
Alpha-Test verfügbar. Die aktualisierten deutschen und englischen Store-Texte sowie sieben neue
deutsche Screenshots sind zur Prüfung eingereicht.

## Play Console

- Paket-ID: `de.robinrehbein.birdy`
- [App-Dashboard](https://play.google.com/console/u/0/developers/7715182646695737952/app/4976256029001868876/app-dashboard)
- [Interner Test](https://play.google.com/console/u/0/developers/7715182646695737952/app/4976256029001868876/tracks/4700770597020690505)
- [Interner Testbeitritt](https://play.google.com/apps/internaltest/4700770597020690505)
- [Geschlossener Alpha-Test](https://play.google.com/console/u/0/developers/7715182646695737952/app/4976256029001868876/tracks/4699009912977551786)
- Tester-Liste „Tester“: 4 Personen; diese Liste ist bereits unter **Einstellungen → Lizenztest**
  ausgewählt. Im geschlossenen Test ist derzeit eine Person angemeldet. Für Produktionszugriff
  verlangt Google mindestens 12 angemeldete Tester über 14 Tage; Produktion ist hier nicht Teil
  des Releases.
- Alpha-Track: Deutschland, Feedback an `hello@robinrehbein.de`.
- Store-Eintrag: Deutsch und Englisch (USA) mit Icon und Vorstellungsgrafik. Die neuen
  deutschen Screenshots sind zur Prüfung eingereicht; Englisch nutzt aktuell die deutschen
  Grafiken als Fallback.
- Datenschutzerklärung: <https://robinrehbein.github.io/birdy/privacy/> (Deutsch und Englisch),
  auch im App-Menü von Version 1.0.2 verlinkt.
- Entwickler-Website: <https://robinrehbein.github.io/>; die zugehörige
  [app-ads.txt](https://robinrehbein.github.io/app-ads.txt) enthält die AdMob-Publisher-ID.
- Zielgruppe: 13–15, 16–17 und 18+. IARC: USK 12, PEGI 7; unverändert.
- Werbung ist in Play als vorhanden deklariert. Die neuen Datensicherheitsangaben sind gespeichert,
  aber noch nicht über „Veröffentlichungen – Übersicht“ zur Prüfung eingereicht.
- Google Play Games auf dem PC wurde deaktiviert. Die ausstehende Aktivierung wurde vor der
  Einreichung aus der Änderungsliste entfernt.

## Checkliste

| Punkt | Status |
|---|---|
| Release-Bundle (AAB) | ✅ Kotlin-Bundle baut lokal; CI-Signatur und Play-Annahme erst beim Test-Upload zu bestätigen |
| Versionsnummer | ✅ nativ: `versionName "2.0.0"` (`native/androidApp/build.gradle.kts`), `versionCode` aus `BIRDY_VERSION_CODE` (Default 5); der letzte Capacitor-Release war 1.0.3 (versionCode 4) |
| Android-Zurück-Taste | ✅ Spiel → Pause → Menü; Shop und Game-Over → Menü; Menü → App schließen |
| Hochformat, Vollbild, Bildschirm bleibt an | ✅ |
| Freiwillige Rewarded Ads und optionale In-App-Käufe | ⚠️ Kotlin-Release im PR in Vorbereitung; Veröffentlichung gesperrt bis `BIRDY_MONETIZATION_RELEASE_READY` (siehe `docs/MONETIZATION.md`) |
| App-Icon 512×512 | ✅ `docs/store/icon-512.png` (eigenständig: Vogel frontal vor Regenbogen und Sonnenuntergangshimmel) |
| Feature-Grafik 1024×500 | ✅ `docs/store/feature-birdy-1024x500.png` (DE), `docs/store/feature-birdy-1024x500-en.png` (EN). Das Render-Skript `scripts/render-assets.mjs` (JS-Tooling) wurde mit dem Web-Build entfernt; neue Grafiken müssen manuell oder mit einem neuen Skript auf Basis der nativen Screenshots (`native/screenshots`, siehe unten) erzeugt werden. |
| Screenshots 1080×1920 | ⚠️ `docs/store/de/` und `docs/store/en/` zeigen noch die **alte Web-/Capacitor-Version** (je 7 Bilder, gerendert mit dem entfernten `scripts/store-shots.mjs`). Das Skript ist mit dem Web-Build weg; neue Screenshots aus echten Spielszenen lassen sich mit dem nativen Headless-Tool erzeugen: `cd native && ./gradlew :screenshots:run` (→ `native/build/shots/*.png`), danach ggf. mit Rahmen/Claim wie bisher weiterverarbeiten. **Vor jedem neuen Release müssen diese Bilder ersetzt werden.** |
| Store-Texte DE/EN | ✅ aktualisiert und am 28.09. in Play als Entwurf gespeichert; Einreichung offen |
| Datenschutzerklärung | ✅ <https://robinrehbein.github.io/birdy/privacy/> über GitHub Pages veröffentlicht |
| Angaben zur Datensicherheit für Monetarisierung | ✅ Fragebogen am 28.09. gespeichert; ⚠️ Einreichung zur Prüfung offen |
| **App-ID** | ✅ `de.robinrehbein.birdy` in `native/androidApp/build.gradle.kts` konfiguriert |
| **Signatur-Schlüssel** | ✅ Upload-Schlüssel lokal erstellt; Backup außerhalb des Projekts erforderlich |
| **Rechte-Check** | ⚠️ siehe „Risiken“ |
| Test auf Play-Gerät | ⚠️ Debug-APK startet im lokalen Emulator; dessen Play-Dienste sind für AdMob zu alt. Consent, Rewarded Ads und Testkäufe benötigen ein aktuelles Play-Testgerät. |

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

Der Upload-Schlüssel liegt lokal unter `native/birdy-upload.jks`; die Zugangsdaten liegen in
`native/keystore.properties`. Beide Dateien sind aus Git ausgeschlossen. Erstelle ein sicheres
Backup außerhalb dieses Projekts. Bei Play App Signing kann ein verlorener Upload-Schlüssel
zurückgesetzt werden, aber das erfordert ein Verfahren über die Play Console.

```bash
keytool -list -keystore native/birdy-upload.jks -alias birdy-upload
```

Die vorhandene `native/keystore.properties` hat dieses Format:

```properties
storeFile=birdy-upload.jks
storePassword=…
keyAlias=birdy-upload
keyPassword=…
```

Dann `cd native && ./gradlew :androidApp:bundleRelease`. Das Bundle liegt unter
`native/androidApp/build/outputs/bundle/release/androidApp-release.aab`. In der Play Console
**Play App Signing** aktivieren; dann ist dein Schlüssel nur noch der Upload-Schlüssel.

Für manuelle Updates einen unbenutzten `versionCode` (per `BIRDY_VERSION_CODE`-Umgebungsvariable)
setzen; `versionName` ist fest `"2.0.0"` (`native/androidApp/build.gradle.kts`). Auch
Entwurfs-Bundles verbrauchen einen Versionscode. Die GitHub-Pipeline berechnet ihren Code aus
UTC-Tag, Workflow-Laufnummer und Versuch; damit liegt er oberhalb des am 28. September 2026
hochgeladenen Capacitor-Entwurfs `26092801`. Mehr zu Build/Signatur-Details:
[`docs/native/RELEASE.md`](native/RELEASE.md).

## Automatischer Release nach einem Merge

`.github/workflows/play-release.yml` baut nach jedem Push auf `main` (also auch nach einem
Merge) das signierte native Bundle (`native/androidApp:bundleRelease`) und veröffentlicht es im
internen Test und im geschlossenen
Alpha-Test, sobald sowohl `PLAY_PUBLISH_ENABLED` als auch
`BIRDY_MONETIZATION_RELEASE_READY` auf `true` gesetzt sind. Die zweite Variable bleibt bis
zum Abschluss der Monetarisierungsprüfungen deaktiviert; auch ein manueller Workflow-Start
umgeht diese Sperre nicht. Offener Test und Produktion bleiben ausgeschlossen, solange der
Produktionszugriff fehlt. `PLAY_PUBLISH_ENABLED` ist bereits aktiviert.

Für die Einrichtung unter **GitHub → Settings → Secrets and variables → Actions** werden diese
Repository-Secrets benötigt:

| Secret | Inhalt |
|---|---|
| `UPLOAD_KEYSTORE_BASE64` | Base64-Inhalt von `native/birdy-upload.jks` (ohne Zeilenumbrüche) |
| `UPLOAD_STORE_PASSWORD` | `storePassword` aus der lokalen `native/keystore.properties` |
| `UPLOAD_KEY_ALIAS` | `keyAlias` aus der lokalen `native/keystore.properties` |
| `UPLOAD_KEY_PASSWORD` | `keyPassword` aus der lokalen `native/keystore.properties` |
| `PLAY_SERVICE_ACCOUNT_JSON` | JSON-Schlüssel eines Dienstkontos mit Zugriff auf Birdy in der Play Console |

Das Google-Cloud-Projekt muss die **Google Play Developer API** aktiviert haben. Das Dienstkonto
braucht in der Play Console die Berechtigung, Releases für Birdy in den Test-Tracks zu erstellen
und zu veröffentlichen. Die Variable `PLAY_CLOSED_TRACK` kann den API-Namen des geschlossenen
Tracks festlegen; Standard ist `alpha`. Die Play API meldet `internal` und `alpha` als vorhandene
Tracks. Der Workflow validiert beide Tracks vor dem Commit und veröffentlicht
niemals in `production`.

Secrets und Keystore-Dateien bleiben außerhalb von Git. Nach dem Einrichten einmal den
manuellen Workflow starten und den Status in GitHub Actions und in beiden Play-Tracks prüfen.

> **Hinweis:** Der aktuell live geschaltete Play-Store-Eintrag sowie die Screenshots in
> `docs/store/` zeigen noch die alte Fassung mit Tipp-Steuerung (Tippen auf eine Spur). Die
> Steuerung wurde auf Wischen umgestellt (siehe [README](../README.md#steuerung)); Titel,
> Kurzbeschreibung und Beschreibung unten sind bereits auf die neue Steuerung aktualisiert, müssen
> aber **manuell in der Play Console** nachgezogen werden, bevor sie live gehen. Neue Screenshots
> fehlen noch (siehe Checkliste oben).

## Store-Eintrag – Deutsch

**Titel (max. 30):** Birdy – Wischen & Fliegen

**Kurzbeschreibung (max. 80):** Wischen, ausweichen, durchfliegen – ein Ein-Finger-Flieger in Low-Poly-3D.

**Beschreibung:**

> Flieg so weit du kannst! Birdy ist ein bunter Ein-Finger-Arcade-Flieger in Low-Poly-3D.
>
> 👆 **Wischen genügt:** Wische nach links oder rechts, um die Spur zu wechseln – tippe irgendwo
> oder wische nach oben, um zu flattern. Ein Zielring zeigt dir, ob du durch die nächste Lücke
> passt.
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
> und Schnäbel – von der Krone bis zur Propellermütze. Dazu 12 Flugspuren und sieben seltene,
> animierte Skins wie Lava, Galaxie oder Fußball.
>
> 🌍 **Neue Welten:** Schalte Winterland, Südsee, Zuckerland und Pilzwald frei, gib den Röhren ein
> neues Design und verbessere deine Power-ups.
>
> 📅 **Jeden Tag etwas Neues:** Tagesmissionen, Erfolge und ein Tagesgeschenk mit Serien-Bonus.
>
> Kostenlos spielbar, ohne Konto. Freiwillige Videoanzeigen geben Münzen oder eine Stunde
> Zugang zu Skins und Welten. Einzelne Skins und Welten sind dauerhaft kaufbar.
> Eine Internetverbindung wird für Anzeigen und Käufe benötigt.

## Store listing – English

**Title:** Birdy – Swipe & Fly

**Short description:** Swipe, dodge, fly through – a one-finger low-poly 3D flyer.

**Description:**

> Fly as far as you can! Birdy is a colourful one-finger arcade flyer in low-poly 3D.
>
> 👆 **Just swipe:** swipe left or right to change lane – tap anywhere or swipe up to flap.
> A target ring shows whether you'll make the next gap.
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
> from a crown to a propeller cap. Plus 12 flight trails and seven rare animated skins such as
> lava, galaxy or football.
>
> 🌍 **New worlds:** unlock Winterland, South Seas, Candyland and Mushroom Woods, restyle the pipes
> and upgrade your power-ups.
>
> 📅 **Something new every day:** daily missions, awards and a daily gift with a streak bonus.
>
> Free to play, with no account required. Optional video ads grant coins or one hour of
> access to skins and worlds. Individual skins and worlds can be bought permanently.
> Ads and purchases require an internet connection.

**Kategorie:** Spiele → Arcade.
**Tags:** Arcade, Auto-Runner, Casual.

## Datenschutz und Datensicherheit für den Monetarisierungs-Build

Die Datenschutzerklärung liegt unter <https://robinrehbein.github.io/birdy/privacy/>.

Der Play-Fragebogen wurde am 28.09.2026 mit „Datenerhebung/Weitergabe: Ja“ gespeichert.
Erfasst und weitergegeben: ungefährer Standort (IP), bisherige Käufe, App-Interaktionen,
Diagnosedaten, andere App-Leistungsdaten und Geräte-/andere IDs. Übertragung verschlüsselt:
Ja. Es gibt kein Birdy-Konto und keinen eigenen Server. Die Angabe wartet auf Einreichung
über „Veröffentlichungen – Übersicht“ und muss auf einem Play-Testgerät geprüft werden.

### Quellen und geprüfte technische Fakten (28. September 2026)

- [Google Mobile Ads SDK 25.5.0: Play Data Disclosure](https://developers.google.com/admob/android/privacy/play-data-disclosure): Das SDK überträgt IP-Adresse, Nutzungsinteraktionen, Diagnosedaten und Gerätekennungen an Google für Werbung, Analyse und Betrugsprävention; Google nennt TLS für die Übertragung. Die IP kann zur ungefähren Standortbestimmung dienen.
- [Google UMP: Android-Integration](https://developers.google.com/admob/android/privacy): Der Consent-Status wird beim App-Start aktualisiert; eine notwendige Nachricht wird aus der in AdMob veröffentlichten Konfiguration geladen. `AdMobAds.kt` setzt TFUA und maximale Inhaltsbewertung G. Die Nachricht „Birdy – EU-Einwilligung“ ist in AdMob seit 28.09. als „Veröffentlicht“ bestätigt (Englisch und Deutsch, mit Ablehnen-Option).
- [Google Play Billing: Integration und Kaufzustände](https://developer.android.com/google/play/billing/integrate): Käufe werden über Google Play abgewickelt. Birdy fragt bestehende Käufe ab und verarbeitet lokal Produkt-ID, Token und Kaufzustand; es gibt kein Birdy-Konto und keinen eigenen Kaufserver. Käufe werden erst im Zustand `PURCHASED` freigeschaltet und anschließend bestätigt.
- Das native Manifest entfernt `com.google.android.gms.permission.AD_ID` ausdrücklich. Der Release-Merge enthält `BILLING` und `android.permission.ACCESS_ADSERVICES_AD_ID`, aber keine klassische `AD_ID`-Berechtigung. Die Play-Frage zur **klassischen Android-Werbe-ID** ist deshalb mit „Nein“ beantwortet; Gerätekennungen aus dem Ads-SDK sind im Datensicherheitsformular dennoch angegeben.

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
