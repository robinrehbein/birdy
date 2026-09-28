# Birdy native: architecture

A Kotlin rewrite of the Three.js/Capacitor game (`src/`). The game core is Kotlin Multiplatform
so an iOS app can follow. The JS app stays in the repo as the reference until parity is
confirmed. Behaviour specs: `docs/native/spec/*.md`. Golden fixtures generated from the JS:
`docs/native/golden/*.json` (generators in `scripts/native-golden/`).

## Toolchain (verified 2026-09-28)

| Piece | Version | Why |
|---|---|---|
| Gradle wrapper | 8.14.4 | Kotlin 2.4 warns on 8.14.3 |
| AGP | 8.13.2 | Newest line on Gradle 8 that still supports `com.android.library` + KMP `androidTarget()` |
| Kotlin | 2.4.20 | |
| Compose Multiplatform | 1.11.1 | CMP 1.12 pulls androidx Compose 1.12, which needs compileSdk 37 + AGP 9.1 |
| androidx.core | 1.18.0 | 1.19 needs compileSdk 37 |
| LWJGL | 3.4.3 | `lwjgl-egl` + `lwjgl-opengles` (+ linux natives) for headless desktop GL |
| compileSdk / targetSdk / minSdk | 36 / 36 / 24 | |
| JDK | 21 (bytecode target 17 for shared/app) | |

All versions are in `native/gradle/libs.versions.toml`. CMP no longer publishes `iosX64`, so the
iOS targets are `iosArm64` and `iosSimulatorArm64`. They are declared but can't be built on Linux
(`kotlin.native.ignoreDisabledTargets=true`).

Commands (from `native/`, with `ANDROID_HOME=/opt/android-sdk`):

```
./gradlew :shared:testDebugUnitTest :androidApp:assembleDebug   # unit tests + APK
./gradlew :shared:desktopTest      # JVM tests, including headless GL renderer tests
./gradlew :screenshots:run         # PNGs at 1080x2400 -> native/build/shots/
```

Maven Central sometimes returns HTTP 429. If that happens, re-run the build.

## Modules

```
native/
├── shared/            KMP library: everything that is not app shell
│   ├── commonMain     engine, engine.gl (renderer on a GL facade), game, view, meta, audio, ui, platform
│   ├── jvmSharedMain  shared by android + desktop: NioScratch (direct buffers), JvmClock
│   ├── androidMain    AndroidGl (GLES30), SharedPreferences store, haptics, AudioTrack out, WebView migration
│   ├── desktopMain    LwjglGl, HeadlessEglContext (Mesa EGL surfaceless)
│   ├── (iosMain)      not created yet: iOS targets are declared, the source set is added with the iOS app (native/docs/ios.md)
│   ├── commonTest     unit tests vs goldens (run on Android host JVM and desktop JVM)
│   └── desktopTest    headless GL tests
├── androidApp/        Android application de.robinrehbein.birdy: MainActivity, GLSurfaceView, ads, billing
└── screenshots/       JVM app: same engine + shaders via LWJGL, Compose overlay via ImageComposeScene
```

```
                 ┌──────────────── androidApp (Activity, GLSurfaceView, AdMob/UMP, Play Billing) ─┐
                 │                                                                               │
 screenshots ────┤            createBirdyGame(PlatformServices, RenderBackend)                   │
 (desktop JVM)   │                              │                                                │
                 ▼                              ▼                                                ▼
      ui (Compose) ◄── UiState ── game.BirdyGame (loop, camera, input, routing) ── UiEffect ──► shell
          │  UiCommand ──────────────►  │        │              │
          ▼                             ▼        ▼              ▼
        meta ◄──────────────── game.GameSimulation   view.* (world, bird, fx) ──► engine.scene
  (progress, catalog,                     │                                        │
   i18n, icons)                           ▼                                        ▼
          │                      audio.GameAudio ──► AudioOut          engine.RenderBackend
          ▼                                                             └ engine.gl.GlRenderer ─► Gl facade
     platform (KeyValueStore, Clock, Haptics, Ads, Billing, LegacyMigration)     ├ AndroidGl (GLES30)
                                                                                 └ LwjglGl (LWJGL GLES)
```

Package root is `de.robinrehbein.birdy`:

| Package | Contents | Depends on |
|---|---|---|
| `engine.math` | Vec3, Quat, Mat4 (column-major), Color (sRGB hex), MathUtil (three.js formulas) | none |
| `engine.scene` | Node, Mesh, InstanceData, Geometry, Material (Basic/Standard/CustomShader), ShaderPatch, Uniform, Texture, Lights, PerspectiveCamera, Scene, Fog | math |
| `engine.mesh` | procedural geometry (three.js-compatible primitives, extrude, merge/bake) | scene |
| `engine` | `RenderBackend`, `RenderTarget`, `RenderStats` | scene |
| `engine.gl` | `Gl` facade, `GL` constants, `GlRenderer`, shaders, GPU caches | engine, scene |
| `game` | Tuning, GameState, world objects (GateRow/LaneState/Coin/Pickup), GameEvent, GameSimulation, UiContract, BirdyGame, factory | meta, audio, engine (only for BirdyGame) |
| `view` | SceneView/FrameInfo; `view.world`, `view.bird`, `view.fx`, `view.thumb` | engine, game (read-only), meta |
| `meta` | ProgressData/Repository, Catalog, Strings (i18n), icons, achievements, missions, migration import | platform |
| `audio` | GameAudio (music + sfx), synth, AudioOut | platform, game.PowerType |
| `ui` | Compose Multiplatform screens (menu, HUD, shop, dialogs), theme, fonts | game (UiState/UiCommand), meta |
| `platform` | Platform interfaces plus common helpers (PurchaseProcessor) | none |

Rules:
- `commonMain` holds nothing Android-specific. Platform code goes behind an interface in
  `platform`, `audio.AudioOut` or `engine.gl.Gl`, and is injected through `PlatformServices`.
- The simulation (`game`) never references `engine`/`view`. Views read simulation state and
  never write it.
- Colours stay as the exact JS hex ints. The renderer does the linear-space math like three.js
  (ColorManagement on, sRGB output, no tone mapping). Geometry vertex colours are linear.

## Threading model

| Thread | Owns | Talks to others via |
|---|---|---|
| **Game/GL thread** (GLSurfaceView renderer thread; the main thread in `screenshots`) | `BirdyGame`, `GameSimulation`, `ProgressRepository` (all mutations), the scene graph, `GlRenderer` and every GL call | Drains `UiCommand` and `runOnGameThread` queues at frame start. Publishes `StateFlow<UiState>` and emits `UiEffect`s |
| **UI/main thread** | Compose overlay, Activity, AdMob/UMP, Play Billing client callbacks | `game.post(UiCommand)`, `game.runOnGameThread {}`, collects `game.ui` / `game.effects` |
| **Audio thread** (AudioTrack writer, URGENT_AUDIO) | Synth voices, sequencer position | Lock-free command queue from the game thread. The beat clock is read back as a volatile value derived from rendered frames |

Invariants:
- Only the game thread mutates `ProgressRepository`. The UI reads `ProgressData` snapshots from
  `UiState.progress` (immutable data class). Shop taps, gift claims, ad rewards and purchases are
  `UiCommand`s or `runOnGameThread` blocks.
- Channels are `kotlinx.coroutines.channels.Channel(UNLIMITED)` with `trySend`/`tryReceive`,
  which are safe from any thread and never block the GL thread.
- `UiState` is published only when it changes (data-class equality) to limit recomposition. Fast
  per-frame values (tutorial hand position, flash alpha) are included only while they animate.
- Storage writes (`SharedPreferences.commit`) happen on the game thread. They are small and rare
  (after actions, not per frame), matching JS `save()` timing.
- GL context loss (Android): `onSurfaceCreated` → `RenderBackend.onContextCreated()` drops all GPU
  caches, and objects are re-uploaded lazily. CPU-side scene data is never lost.

## Game loop and timestep

`BirdyGame.frame(nanos)` mirrors main.js `tick()`/`update()`:

1. Run queued `runOnGameThread` tasks, then `UiCommand`s, in order.
2. `rawDt` = time since last frame; `dt = min(rawDt, 1/30)` (JS parity: variable step, clamped).
   There is no fixed-step accumulator. Tuning was authored against rAF variable steps, and JS
   `simulate()`/bot use a fixed 1/60, which the same `step(dt)` supports.
3. If paused (or rotate-lock, which native drops), skip simulation. If `hitStop > 0`, only
   count down hit-stop with `rawDt` and update camera shake + impact FX (main.js `update`).
4. `sim.step(dt, audio.musicBeat())`. When music isn't playing, the beat comes from game time at
   FALLBACK_BPM, as in JS.
5. Route `GameEvent`s: audio sfx, haptics (dropped while muted), toasts, flashes, camera shake,
   mission/achievement previews.
6. `SceneView.update(FrameInfo)` for world, bird and fx; camera rig; quality adaptation.
7. `renderer.render(scene, camera)`; publish `UiState`.

Determinism: `GameSimulation` takes an injected `kotlin.random.Random`, and time only enters
through `step(dt)`. Tests, the bot playtest and screenshots use fixed seeds and fixed dt (1/60 or
1/30, as in JS `advance()`). JS `Math.random()` sequences are not reproducible, so gate
generation is tested with property tests on the spec's invariants. Pure formulas (difficulty,
reachability, plant timing, missions PRNG, song trace, …) are tested bit-exactly against goldens.

Resolved spec question (main-a.md §12.4): hitboxes are **not** frozen at spawn. world.js:961-1001
`update()` recomputes `hitLow/hitHigh` every frame from the moving/pulsing gap and the rising
plant, and main.js:1707-1712 tests those live values. The simulation therefore owns that
per-frame lane update (`LaneState`, including plant pose). The world view only renders it.
`BIOMES.length` is 4 (biomes.js), so every 4th zone shows the equipped world.

## How Compose UI talks to the simulation

- `BirdyApp(state: UiState, strings: Strings, onCommand: (UiCommand) -> Unit)` is stateless and
  lives in `ui` (commonMain), so the same code renders on Android and headless on desktop.
- Touches on the play area fall through to `UiCommand.Touch(x, y)` in screen fractions. Lane
  targeting (main-a.md §9.4, `screenX` with camera projection) runs on the game thread, where the
  camera is.
- Transient UI (toast queue, tutorial hand, zone hints) is driven by game-side state machines
  that reproduce main.js timing. The UI renders it and animates only cosmetics.
- The shell handles effects that need an Activity (`UiEffect.ShowRewardedAd`, `LaunchPurchase`,
  `ShowPrivacyOptions`, `ExitApp`) and reports results back as commands or `runOnGameThread`.
- Shop 3D thumbnails: `view.thumb` renders to an offscreen `RenderTarget` on the game thread.
  `readPixels` output goes into an ARGB cache keyed `kind:id:skinId` (main-b.md §7.1), and
  `UiState` carries a thumbnail version so tiles refresh.

## Shader hooks (`ShaderPatch`)

The GL renderer's standard program (GLSL ES 3.00) has the following in scope at the patch
hooks. It replaces three.js `onBeforeCompile`:
- `vertexBody`: `vec3 transformed`, `vec3 objectNormal`, attributes `position/normal/color/uv`,
  extra attributes by name, uniforms `modelMatrix/viewMatrix/projectionMatrix`.
- `fragmentColor`: `vec4 diffuseColor`, `vec3 totalEmissive`, varyings `vWorldPosition`,
  `vNormal`, `vColor`, `vUv`.
- `fragmentOutput`: `vec3 outgoingLight` (linear) before sRGB encode and fog.
- Uniforms declared in the patch head are fed from `ShaderPatch.uniforms` by name. `Uniform.C`
  is converted sRGB→linear.

The GL task may extend the hook set (e.g. shadow, specular) but must keep these names.

## iOS plan

- **Shared code**: all of `commonMain` compiles for `iosArm64`/`iosSimulatorArm64`: game, meta,
  audio synth, UI (Compose Multiplatform supports iOS), engine and renderer logic.
- **Rendering**: two options behind `RenderBackend`:
  1. `IosGl : Gl` over OpenGLES.framework in a `GLKView`/`CAEAGLLayer`. This reuses `GlRenderer`
     and all shaders unchanged. OpenGL ES is deprecated on iOS but still works, so it's the
     fastest path.
  2. `MetalRenderer : RenderBackend`, which translates the same scene graph and hand-ports the
     shaders to MSL. Choose this if GL deprecation becomes a problem.
- **Audio**: `AudioOut` on `AVAudioEngine` + `AVAudioSourceNode` render block, calling the same
  synth `render(buffer, frames)`.
- **Storage/clock/haptics**: `NSUserDefaults` KeyValueStore, `NSDate`/`NSCalendar` Clock,
  `UIImpactFeedbackGenerator` Haptics. There is no legacy migration on iOS (`NoLegacyMigration`).
- **Ads/Billing**: Google Mobile Ads iOS SDK + UMP behind `Ads`, StoreKit 2 behind `Billing`.
  Same product ids. `PurchaseProcessor` (common) keeps the entitlement rules.
- **UI**: `ComposeUIViewController { BirdyApp(...) }` layered over the GL/Metal view. Fonts come
  from the same compose resources.
- **Entry**: an Xcode project links the `shared` framework (add `binaries.framework` to the iOS
  targets when that work starts) and calls `createBirdyGame(services, renderer)`.

See also `native/docs/ios.md`.

## Migration from the Capacitor app

On first launch the Android shell runs `WebViewLegacyMigration`, a hidden WebView on
`https://localhost` that reads `birdy-progress`, `birdy-best`, `birdy-muted`, `birdy-lang`,
`birdy-quality` and `birdy-fps`. The meta import writes them into the native `KeyValueStore`
under the **same keys**, applies `best = max(progress.best, birdy-best)`, and sets
`birdy-native-migrated=1`. It must be idempotent (assign, never add). "No legacy data" is the
normal case. Details are in platform.md §4.7. The origin is still unverified, so the platform
task must check it against a real Capacitor install on the emulator.

## File ownership map

Paths are relative to `native/`. `S/` = `shared/src/`, `P/` = `kotlin/de/robinrehbein/birdy/`.
**Frozen scaffold APIs**: signatures in `engine/scene/*`, `engine/RenderBackend.kt`,
`engine/gl/Gl.kt`, `platform/*` interfaces, `audio/GameAudio.kt`, `audio/AudioOut.kt`,
`meta/ProgressRepository.kt`, `meta/Strings.kt`, `game/UiContract.kt`, `game/WorldObjects.kt`,
`game/GameEvents.kt`, `view/SceneView.kt` and `ui/BirdyApp.kt` may only be **extended** (new
members, new files). Renaming or removing needs the owner and every consumer to agree. Build files
(`*.gradle.kts`, `libs.versions.toml`, `settings.gradle.kts`) belong to the lead. A task that needs
a new dependency notes it in its report instead of editing them. Exception: the platform task may
edit `androidApp/build.gradle.kts`.

| Wave | Task | Owns |
|---|---|---|
| 0 | lead (scaffold) | build files, `gradle/`, `S/commonTest/P/Golden.kt`, `S/commonTest/P/ScaffoldTest.kt`, this doc |
| 1 | engine | `S/commonMain/P/engine/math/**`, `engine/scene/**`, `engine/mesh/**`, `engine/texture/**` (new), `S/commonTest/P/engine/**` (except `gl`) |
| 1 | gl | `S/commonMain/P/engine/gl/**`, `S/commonMain/P/engine/RenderBackend.kt`, `S/androidMain/P/engine/gl/**`, `S/desktopMain/P/engine/gl/**`, `S/jvmSharedMain/P/engine/gl/**`, `S/desktopTest/P/engine/gl/**` |
| 1 | sim | `S/commonMain/P/game/GameSimulation.kt`, `game/sim/**` (new), `game/bot/**` (new), `game/Tuning.kt`, `game/GameState.kt`, `game/WorldObjects.kt`, `game/GameEvents.kt`, `S/commonTest/P/game/**` |
| 1 | meta | `S/commonMain/P/meta/**`, `S/commonMain/P/platform/KeyValueStore.kt`, `S/androidMain/P/platform/SharedPrefsKeyValueStore.kt`, `S/androidMain/P/platform/WebViewLegacyMigration.kt`, `S/commonTest/P/meta/**` |
| 1 | audio | `S/commonMain/P/audio/**`, `S/androidMain/P/audio/**`, `S/commonTest/P/audio/**`, `S/desktopTest/P/audio/**` |
| 2 | world | `S/commonMain/P/view/world/**`, `S/commonTest/P/view/world/**`, `S/desktopTest/P/view/world/**` |
| 2 | bird-fx | `S/commonMain/P/view/bird/**`, `view/fx/**`, `view/thumb/**`, matching `commonTest`/`desktopTest` dirs |
| 2 | loop | `S/commonMain/P/game/BirdyGame.kt`, `game/GameFactory.kt`, `game/UiContract.kt`, `game/loop/**` (new), `view/SceneView.kt`, `S/commonTest/P/game/loop/**`, `screenshots/src/**`, `scripts/native-shots/**` (repo root) |
| 2 | ui | `S/commonMain/P/ui/**`, `S/commonMain/composeResources/**`, `S/commonTest/P/ui/**`, `S/desktopTest/P/ui/**` |
| 2 | platform | `androidApp/**`, `S/androidMain/P/platform/**` except the two meta files, `S/commonMain/P/platform/**` except `KeyValueStore.kt`, `S/jvmSharedMain/P/platform/**`, `docs/native/RELEASE.md` (repo root) |
