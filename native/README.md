# Birdy native (Kotlin / Kotlin Multiplatform)

Native Android-App für Birdy: dieselbe Spielidee wie die Web-/Capacitor-Version in
[`../src`](../src), aber als eigenständiges Gradle-Projekt in Kotlin statt JavaScript/WebView.
Das Spielmodul (`shared`) ist als **Kotlin-Multiplatform**-Bibliothek gebaut, damit später eine
iOS-App auf demselben Code aufsetzen kann (siehe [`docs/native/IOS.md`](../docs/native/IOS.md)).
Solange die native App nicht die Feature-Parität der JS-Version erreicht und freigegeben ist,
bleibt die Capacitor-App (`../android`) der produktive Build – siehe die Hauptdatei
[`../README.md`](../README.md).

Architektur, Modulaufteilung und Dateibesitz stehen in
[`../docs/native/ARCHITECTURE.md`](../docs/native/ARCHITECTURE.md); die Fachspezifikation
(Physik, Tuning-Werte, Formeln) in [`../docs/native/spec/`](../docs/native/spec/), goldene
Testfixtures (exakte Zahlen, 1:1 aus der JS-Version übernommen) in
[`../docs/native/golden/`](../docs/native/golden/).

## Module

- **`shared`** — KMP-Bibliothek. `commonMain` enthält die komplette Spiellogik (Engine/Szenengraph,
  Simulation, Fortschritt/Katalog/Missionen/Erfolge, i18n-Texte, prozeduraler Audio-Synthesizer,
  Compose-Multiplatform-UI) sowie Plattform-Interfaces (Renderer, Audio-Ausgabe, Key-Value-Storage,
  Haptik, Werbung, Käufe, Uhrzeit). `androidMain` implementiert diese Interfaces für Android
  (OpenGL ES 3.0, `AudioTrack`, `SharedPreferences`, Vibration). `desktopMain`/`jvmSharedMain`
  implementieren dieselben Interfaces headless für die Screenshot-Pipeline (siehe unten). Die
  iOS-Targets (`iosArm64`, `iosSimulatorArm64`) sind deklariert, aber auf Linux nicht baubar.
- **`androidApp`** — die eigentliche Android-App (`de.robinrehbein.birdy`): eine Activity mit
  GL-Flächen für die 3D-Szene und Compose-Overlay für HUD/Menüs/Shop.
- **`screenshots`** — JVM-Tool, das dieselbe Engine + dieselben GLSL-ES-Shader headless über
  Mesa/llvmpipe rendert (siehe „Screenshots ohne GPU“ unten).

## Build

Voraussetzungen: JDK 21, Android SDK (`compileSdk 36`, `minSdk 24`). `local.properties` zeigt mit
`sdk.dir=...` auf den SDK-Pfad (liegt in `.gitignore`, lokal selbst anlegen oder
`ANDROID_HOME`/`ANDROID_SDK_ROOT` setzen — der Gradle-Android-Plugin liest beides).

```bash
cd native
export ANDROID_HOME=/opt/android-sdk   # falls local.properties fehlt
./gradlew :androidApp:assembleDebug     # Debug-APK, automatisch mit dem Debug-Key signiert
```

Das erzeugte APK liegt unter `androidApp/build/outputs/apk/debug/androidApp-debug.apk` und lässt
sich direkt auf ein Testgerät installieren (Installation aus unbekannten Quellen erlauben).

## Tests

```bash
cd native
./gradlew :shared:testDebugUnitTest
```

Die Unit-Tests in `shared/src/commonTest` und `shared/src/desktopTest` prüfen die Spiellogik
gegen die goldenen Fixtures in `../docs/native/golden/*.json` — dieselben Zahlen, die die
JS-Version bei identischem Zufalls-Seed und identischer Zeit liefert. Ein Test schlägt fehl, wenn
sich Tuning-Werte oder Formeln unbeabsichtigt von der JS-Version unterscheiden.

Kompletter CI-Lauf (Tests + Debug-Build + Lint), wie ihn auch
[`../.github/workflows/native-build.yml`](../.github/workflows/native-build.yml) ausführt:

```bash
cd native
./gradlew :shared:testDebugUnitTest :androidApp:assembleDebug :androidApp:lintDebug --console=plain
```

## Screenshots ohne GPU/Emulator

Diese Maschine hat keine GPU und kein KVM. Zwei Wege, das Spiel trotzdem visuell zu prüfen:

1. **3D-Szene headless rendern** (`screenshots`-Modul): Mesa/llvmpipe stellt über
   `EGL_PLATFORM=surfaceless` eine Software-OpenGL-ES-3.x-Implementierung bereit. Das
   `screenshots`-Modul lädt dasselbe `shared`-Rendermodul (dieselben GLSL-ES-Shader wie Android)
   über LWJGL (`lwjgl-egl` + `lwjgl-opengles`, Linux-Natives) und rendert feste Szenen
   (fester Seed, feste Zeit: Menü, früher Run, jede Zone, jedes aktive Power-up, Kaktus, Crash,
   ein paar Skins/Trails) in PNGs bei 1080×2400:
   ```bash
   cd native
   ./gradlew :screenshots:run
   # -> native/build/shots/*.png
   ```
   Damit das funktioniert, ist der GL-Renderer gegen eine kleine GL-Facade geschrieben; Android
   (`GLES30`) und LWJGL (`GLES30`) teilen sich denselben Renderer-Code.
2. **Compose-UI headless rendern**: Menüs/HUD/Shop sind Compose-Multiplatform-Code in `shared`
   (`commonMain`), lassen sich also auch ohne Android-Gerät auf dem Desktop rendern
   (`ImageComposeScene`/`runDesktopComposeUiTest`) und vom `screenshots`-Tool über den 3D-Shot
   gelegt werden.
3. **Emulator** (`/opt/android-sdk/emulator`, AVD `test`, `android-36 google_apis x86_64`): ohne
   KVM nur reine Software-Emulation (`-accel off -gpu swiftshader_indirect -no-window`), der
   Boot dauert mehrere Minuten. Nur für den finalen Smoke-Test verwenden, nicht während der
   Entwicklung.

Die JS-Referenzversion lässt sich parallel mit Playwright + vorinstalliertem Chromium
screenshotten (`PLAYWRIGHT_BROWSERS_PATH=/opt/pw-browsers`, WebGL über SwiftShader) — siehe
`../scripts/store-shots.mjs` und `../scripts/playtest.mjs` dafür, wie Spielzustände angesteuert
werden.

## Release-Signatur

Details, Formeln und die Play-Publishing-Migration stehen vollständig in
[`../docs/native/RELEASE.md`](../docs/native/RELEASE.md). Kurzfassung:

```bash
cd native
./gradlew :androidApp:bundleRelease   # .aab für den Play Store
```

`androidApp/build.gradle.kts` sucht zuerst `native/keystore.properties`, dann (als Fallback)
`../android/keystore.properties` der Capacitor-App, damit beide Apps denselben Upload-Key nutzen
können. Beide Dateien haben dasselbe Format:

```properties
storeFile=birdy-upload.jks   # relativ zu dieser Properties-Datei aufgelöst
storePassword=...
keyAlias=birdy-upload
keyPassword=...
```

Fehlt die Datei, entsteht ein **unsigniertes** Release-Artefakt (kein Fehler) — das entspricht
dem Verhalten der Capacitor-App und ist der Grund, warum `assembleRelease`/`bundleRelease` auch
auf dieser Maschine ohne Schlüssel funktionieren.

## versionCode

`versionCode = (Umgebungsvariable BIRDY_VERSION_CODE, sonst 5)`. Der Default `5` liegt bewusst
über dem letzten Capacitor-Release (`4`), damit ein lokaler Build nie mit einem bereits
hochgeladenen Capacitor-versionCode auf demselben Play-Eintrag kollidiert. CI muss
`BIRDY_VERSION_CODE` mit exakt derselben Formel berechnen wie die bestehende Capacitor-Pipeline
(siehe `../docs/native/spec/platform.md` §4.2 und
`../docs/native/golden/platform-version-code.json`):

```
suffix = GITHUB_RUN_NUMBER * 10 + GITHUB_RUN_ATTEMPT   # muss <= 9999 pro UTC-Tag bleiben
BIRDY_VERSION_CODE = floor(aktuelle_UTC_Epochensekunden / 86400) * 10000 + suffix
```

`versionName` ist fest `"2.0.0"` (kennzeichnet den nativen Rewrite gegenüber der Capacitor-`1.x`).

## Migration alter Spielstände (Capacitor → nativ)

Wer die bestehende Capacitor-App (`de.robinrehbein.birdy`) bereits installiert hat, hat seinen
Fortschritt im `localStorage` der WebView unter diesen Schlüsseln gespeichert (siehe
`../src/progress.js`, `../src/audio.js`, `../src/i18n.js`, `../src/main.js`):

| Key | Inhalt |
| --- | --- |
| `birdy-progress` | JSON: Münzen, freigeschaltete Skins/Trails, Missionen, Erfolge, Tagesgeschenk-Serie |
| `birdy-best` | Bestenrekord (Zahl, älteres/redundantes Format neben `birdy-progress.best`) |
| `birdy-muted` | Ton aus (`'1'`/`'0'`) |
| `birdy-lang` | gewählte Sprache (`de`/`en`) |
| `birdy-quality` | Grafik-Qualitätsstufe |
| `birdy-fps` | FPS-Anzeige an/aus |

Ein **Update über dieselbe App** (`de.robinrehbein.birdy` bleibt applicationId) löst beim ersten
Start der nativen App eine einmalige Migration aus: `WebViewLegacyMigration`
(`shared/src/androidMain/.../platform/WebViewLegacyMigration.kt`) öffnet eine unsichtbare
WebView auf `https://localhost` (Capacitor-Androids dokumentierter Standard-Origin), liest die
obigen Schlüssel per JS aus `localStorage`, überträgt sie unverändert (gleiche Schlüssel, gleiches
Format) in den nativen `KeyValueStore` (`SharedPreferences`), bildet
`best = max(natives best, birdy-best)` und markiert den Vorgang mit `birdy-native-migrated=1`,
damit er nie erneut läuft. Ein **Neben-einander-Install** (andere applicationId oder Erststart auf
einem Gerät ohne Capacitor-App) findet keine Daten — das ist der normale, sichere Fall (No-Op).

Mehr Kontext: [`../docs/native/ARCHITECTURE.md`](../docs/native/ARCHITECTURE.md) („Migration from
the Capacitor app“) und [`../docs/native/RELEASE.md`](../docs/native/RELEASE.md) („Legacy
migration verification“).

## iOS

Nichts in `shared/commonMain` ist Android-spezifisch. Der konkrete Plan für die iOS-App steht in
[`../docs/native/IOS.md`](../docs/native/IOS.md).
