# iOS app: concrete step-by-step plan

This is the actionable, ordered plan for building the iOS app on top of the existing `shared`
Kotlin Multiplatform module. It expands the short pointer in `native/docs/ios.md` and the
"iOS plan" section of `ARCHITECTURE.md` into concrete steps, in the order they should be done,
with what each step needs from a macOS machine (nothing here can be built or verified on this
Linux box — no Xcode, no macOS toolchain, no iOS simulator).

Nothing in `shared/commonMain` is Android-specific: the engine, game simulation, progress/catalog/
missions/achievements, i18n strings, procedural audio synth and the Compose Multiplatform UI are
all pure Kotlin behind `expect`/`actual` platform interfaces. Only the platform implementations
(`iosMain`) and the Xcode app shell are new.

## 0. Prerequisites (macOS only)

- Xcode (current stable) + command line tools.
- Kotlin Multiplatform Mobile tooling (`kdoctor` to verify the setup).
- The `shared` module currently only *declares* `iosArm64()` and `iosSimulatorArm64()` in
  `shared/build.gradle.kts` (no `iosX64` — Compose Multiplatform 1.11+ no longer publishes it);
  `kotlin.native.ignoreDisabledTargets=true` in `native/gradle.properties` lets Linux CI skip them
  without failing. On macOS this flag has no effect and the targets build normally.

## 1. Produce a linkable framework

Add to both iOS targets in `shared/build.gradle.kts`:

```kotlin
listOf(iosArm64(), iosSimulatorArm64()).forEach {
    it.binaries.framework {
        baseName = "BirdyShared"
        isStatic = true   // static framework avoids dynamic-framework code-signing friction
    }
}
```

Build with `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` (or `Release...`) on macOS to
get `BirdyShared.framework` for local Xcode integration, or add the
[KMP CocoaPods plugin](https://kotlinlang.org/docs/native-cocoapods.html) /
Swift Package Manager XCFramework export if the Xcode project should consume it as a normal
dependency instead of a manually-linked framework.

## 2. Create the Xcode project

- New iOS App target, matching bundle id convention (`de.robinrehbein.birdy` for iOS, or a
  separate id if Apple requires app-specific ids — decide at App Store Connect setup time).
- Portrait-only, fullscreen, matching the Android app's fixed-portrait/immersive behaviour
  (`Info.plist`: `UISupportedInterfaceOrientations` = portrait only, status bar hidden).
- Link `BirdyShared.framework` (or the SPM package).
- Reuse the fonts and other assets from `shared/src/commonMain/composeResources` — Compose
  Multiplatform resources are bundled automatically when the iOS app links `shared` via CMP's
  resource pipeline; no manual asset copy needed.

## 3. Implement the platform interfaces (`iosMain`)

Add `shared/src/iosMain/kotlin/de/robinrehbein/birdy/platform/` (and `engine/gl/`, `audio/`)
mirroring the `androidMain` layout. Each interface listed here is defined in `commonMain` today
(see `ARCHITECTURE.md`'s "frozen scaffold APIs") and only needs a new `actual`/implementation, no
changes to the common contract:

| Interface (commonMain) | Android implementation (reference) | iOS implementation |
| --- | --- | --- |
| `engine.gl.Gl` / `engine.RenderBackend` | `androidMain/engine/gl/AndroidGl.kt` (`GLES30`) | `IosGl` over `OpenGLES.framework` in a `GLKView`/`CAEAGLLayer` — reuses `GlRenderer` and every GLSL ES 3.00 shader unchanged (fastest path; OpenGL ES is deprecated but functional on iOS). If GL deprecation becomes blocking, implement `RenderBackend` directly in Metal instead and hand-port the shaders to MSL — this is a larger, separate effort, so ship the GL path first. |
| `audio.AudioOut` | `androidMain/audio/AndroidAudioOut.kt` (`AudioTrack`) | `AVAudioEngine` + an `AVAudioSourceNode` render callback that calls the same synth `render(buffer, frames)` used on Android — the synth itself is `commonMain` and unchanged. |
| `platform.KeyValueStore` | `SharedPrefsKeyValueStore.kt` | `NSUserDefaults`-backed store, **same key names** (`birdy-progress`, `birdy-best`, `birdy-muted`, `birdy-lang`, `birdy-quality`, `birdy-fps`) so a future cross-platform sync story isn't blocked by divergent keys. |
| `platform.Clock` | Android `System.currentTimeMillis()`/`Calendar` wrapper | `NSDate`/`NSCalendar` wrapper, same contract (day boundaries for daily missions/gift must match). |
| `platform.Haptics` | `Vibrator`/`VibrationEffect` | `UIImpactFeedbackGenerator` (`.light`/`.medium` mapped to the same event types Android maps). |
| `platform.Ads` | `BirdyAdsPlugin.java`-equivalent using Google Mobile Ads Android SDK + UMP | Google Mobile Ads **iOS** SDK + UMP (same rewarded-ad flow and consent gate; same ad unit *behaviour*, iOS ad unit ids are separate from Android's in AdMob). |
| `platform.Billing` | `BirdyBillingPlugin.java`-equivalent using Play Billing | **StoreKit 2**, same product ids as `native/androidApp` uses (which match `src/billing.js`'s `birdy_coins_500`/`birdy_coins_1500`, ported via `PurchaseProcessor` in `commonMain`) — register matching in-app purchase products in App Store Connect. |
| `platform.LegacyMigration` | `WebViewLegacyMigration.kt` (reads Capacitor WebView `localStorage`) | `NoLegacyMigration` — there is no Capacitor iOS install to migrate from (Birdy never shipped a Capacitor iOS build), so this is a no-op `actual`. |

`PurchaseProcessor` and every entitlement/coin-grant rule already live in `commonMain` — iOS only
supplies the StoreKit transport, not the business logic.

## 4. Wire up the UI

- `ComposeUIViewController { BirdyApp(state, strings, game::post) }` — `ui/BirdyApp.kt` and every
  screen under `shared/src/commonMain/kotlin/de/robinrehbein/birdy/ui/` are Compose Multiplatform
  already and need no iOS-specific changes.
- Layer the GL/Metal render view underneath the Compose overlay (same architecture as Android's
  `SceneView` + Compose HUD split — see `ARCHITECTURE.md` "How Compose UI talks to the
  simulation").
- Drive the frame loop from `CADisplayLink` calling `BirdyGame.frame(dtSeconds)` on the render
  thread, matching Android's choreographer-driven loop (`game/loop/**`, owned by the "loop" task —
  the loop's public shape is platform-agnostic already).

## 5. Entry point

```swift
let game = createBirdyGame(services: iosPlatformServices, renderer: iosRenderBackend)
```

`createBirdyGame(PlatformServices, RenderBackend)` (`game/GameFactory.kt`) is the same factory
Android's `MainActivity` calls — the iOS `AppDelegate`/`SceneDelegate` just supplies iOS
implementations of `PlatformServices`' members from step 3.

## 6. Testing and screenshots without a Mac

None of steps 0–5 can be exercised on this Linux machine. What *can* be prepared ahead of time
here, so the iOS work starts from verified ground:

- `shared/commonTest` and the golden-fixture tests (`docs/native/golden/*.json`) already run on
  the JVM (`desktopTest`) and validate the simulation, catalog, i18n and audio-synth logic that
  iOS will reuse byte-for-byte — passing these before iOS work starts is the real parity gate,
  since iOS adds no new game logic, only platform glue.
- The `screenshots` module's GL facade (`engine.gl.Gl`) is written so a third implementation
  (`IosGl`) is a small, isolated addition once on macOS — the renderer and shaders it wraps need
  no port.
- CI for the iOS app itself (build + unit tests on `macos-*` GitHub Actions runners) is future
  work, to be added once iOS work actually starts; it is intentionally out of scope for
  `native-build.yml`, which only covers Android (`native/**`) today.

## Open decisions (resolve when iOS work actually starts)

- GL-on-deprecated-API vs. Metal rewrite for the renderer (step 3) — start with GL, revisit if
  Apple removes `OpenGLES.framework` before the Metal path is built.
- Separate vs. shared bundle id / Play-vs-App-Store-Connect app entity (step 2).
- Whether `shared` is distributed to the Xcode project via a manually linked `.framework`, a local
  Swift Package (XCFramework), or CocoaPods — any of the three works with the module as designed;
  pick whichever the iOS team's existing tooling prefers.
