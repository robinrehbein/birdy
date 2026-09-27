# Play-Store-Vorbereitung

Stand: technisch bereit für einen internen Test über die Play Console. Vor der Veröffentlichung
brauchst du die Entscheidungen unten.

## Checkliste

| Punkt | Status |
|---|---|
| Release-Bundle (AAB), signiert | ✅ `npm run android:aab`, getestet mit einem Test-Schlüssel (4,1 MB, `jarsigner` verifiziert) |
| Versionsnummer | ✅ `versionName 1.0.0`, `versionCode 1` (`android/app/build.gradle`) |
| Android-Zurück-Taste | ✅ Spiel → Pause → Menü; Shop und Game-Over → Menü; Menü → App schließen |
| Hochformat, Vollbild, Bildschirm bleibt an | ✅ |
| Offline, keine Werbung, keine Käufe, kein Tracking | ✅ |
| App-Icon 512×512 | ✅ `docs/store/icon-512.png` (eigenständig: Vogel von vorn im Zielring) |
| Feature-Grafik 1024×500 | ✅ `docs/store/feature-1024x500.png` |
| Screenshots 1080×1920 | ✅ `docs/store/screenshot-1…6` aus einem echten Run (`scripts/store-shots.mjs`): Menü, Park, Zonen-Banner, Piranha-Pflanze, Regenbogen, Spuren-Shop |
| Store-Texte DE/EN | ✅ unten |
| Datenschutzerklärung | ✅ Text unten; muss unter einer öffentlichen URL liegen |
| Angaben zur Datensicherheit | ✅ unten |
| **App-ID** | ⚠️ Entscheidung nötig (siehe unten) |
| **Signatur-Schlüssel** | ⚠️ Du musst ihn einmalig erzeugen und sicher aufbewahren |
| **Rechte-Check** | ⚠️ siehe „Risiken“ |
| Test auf echten Geräten | ⚠️ noch offen (fps-Anzeige: 5× auf den Titel tippen) |

## Offene Entscheidungen für dich

1. **App-ID**
   - Die App-ID `app.birdy.game` ist ein Platzhalter und lässt sich nach der Veröffentlichung
     nie mehr ändern.
   - Üblich ist eine umgekehrte Domain, die dir gehört, z. B. `de.deinefirma.birdy`.
   - Ändern musst du sie an zwei Stellen:
     - `capacitor.config.json` (`appId`)
     - `android/app/build.gradle` (`namespace` und `applicationId`)
   - Außerdem muss der Paketordner von `MainActivity.java` passend umbenannt werden.
2. **Name im Store**
   - „Birdy 3D“ ist kurz und passend. Es gibt aber mehrere Apps und Spiele mit „Birdy“ im
     Namen, deshalb vor dem Launch eine Markenrecherche machen (DPMA/EUIPO, Play-Store-Suche).
   - Alternativen: „Birdy Dash 3D“, „Flutter Lanes“, „Tap Wings 3D“.
3. **Zielgruppe:** Richtest du das Spiel ausdrücklich an Kinder unter 13, gelten die
   Families-Richtlinien. Die sind erfüllbar, weil die App keine Daten, Werbung oder Käufe hat,
   bedeuten aber zusätzlichen Prüfaufwand. Einfacher ist die Zielgruppe „13+“.
4. **Datenschutz-URL:** Der Text unten muss öffentlich erreichbar sein, z. B. als Seite auf
   eurer Firmen-Website.

## Release signieren

Einmalig einen Schlüssel erzeugen. Er bleibt **außerhalb von Git**, und du solltest ein Backup
machen – ohne ihn gibt es keine Updates mehr, außer über Play App Signing.

```bash
keytool -genkeypair -v -keystore ~/birdy-release.jks -alias birdy \
  -keyalg RSA -keysize 2048 -validity 10000
```

`android/keystore.properties` anlegen (steht in `.gitignore`):

```properties
storeFile=/absoluter/pfad/birdy-release.jks
storePassword=…
keyAlias=birdy
keyPassword=…
```

Dann `npm run android:aab`. Das Bundle liegt unter
`android/app/build/outputs/bundle/release/app-release.aab`. In der Play Console **Play App
Signing** aktivieren; dann ist dein Schlüssel nur noch der Upload-Schlüssel.

Für jedes Update `versionCode` (+1) und `versionName` in `android/app/build.gradle` erhöhen.

## Store-Eintrag – Deutsch

**Titel (max. 30):** Birdy 3D

**Kurzbeschreibung (max. 80):** Tippen, ausweichen, durchfliegen – ein Ein-Finger-Flieger in Low-Poly-3D.

**Beschreibung:**

> Flieg so weit du kannst! Birdy 3D ist ein bunter Ein-Finger-Arcade-Flieger in Low-Poly-3D.
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

**Title:** Birdy 3D

**Short description:** Tap, dodge, fly through – a one-finger low-poly 3D flyer.

**Description:**

> Fly as far as you can! Birdy 3D is a colourful one-finger arcade flyer in low-poly 3D.
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
**Tags:** Arcade, Casual, Einzelspieler, Offline.

## Datenschutzerklärung (Text zum Veröffentlichen)

> **Datenschutzerklärung für „Birdy 3D“**
>
> Birdy 3D erhebt, speichert oder überträgt keine personenbezogenen Daten.
>
> - Die App stellt keine Verbindung zu Servern her und enthält keine Werbung, keine
>   Analyse- oder Tracking-Dienste und keine In-App-Käufe.
> - Spielstand, Rekord, Münzen, freigeschaltete Vögel und Einstellungen (Ton an/aus,
>   Grafikstufe) werden ausschließlich lokal auf deinem Gerät gespeichert und beim
>   Deinstallieren gelöscht.
> - Die Berechtigung „Vibration“ wird nur für kurze Vibrationen im Spiel genutzt.
>
> Kontakt: [Name / Firma, Anschrift, E-Mail eintragen]
>
> Stand: [Datum]

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
| Vogel-Design | Gelber Vogel mit großen Augen und orangem Schnabel, ähnlich wie Flappy Bird. Im Spiel bleibt er (Vorgabe). Das Store-Icon zeigt ihn jetzt in 3D-Frontansicht im Zielring statt im Profil vor türkisem Himmel. | reduziert |
| Wortlaut | „Flappy“ kommt in Store-Texten, README und Code nicht mehr vor. | erledigt |
| Schrift „Lilita One“ | SIL Open Font License, kommerziell nutzbar. | ok |
| Musik und Sounds | im Code erzeugt, keine fremden Samples | ok |
