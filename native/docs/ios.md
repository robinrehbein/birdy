# iOS app: how it plugs in

The iOS app reuses the `shared` KMP module unchanged. Only platform implementations and the app
shell are new. Full context: `docs/native/ARCHITECTURE.md` (section "iOS plan").

1. **Build**: add `binaries.framework { baseName = "BirdyShared" }` to `iosArm64()` and
   `iosSimulatorArm64()` in `shared/build.gradle.kts`. Building needs macOS + Xcode. `iosX64`
   isn't available because Compose Multiplatform 1.11+ no longer publishes it.
2. **Renderer**: implement `engine.gl.Gl` over OpenGLES.framework (`IosGl`) and reuse `GlRenderer`
   plus all GLSL ES 3.00 shaders. Alternatively, implement `engine.RenderBackend` in Metal and port
   the shaders to MSL.
3. **Audio**: `AudioOut` backed by `AVAudioEngine` + `AVAudioSourceNode`, calling the shared synth.
4. **Platform**: `KeyValueStore` → `NSUserDefaults` (same `birdy-*` keys), `Clock` →
   `NSDate`/`NSCalendar`, `Haptics` → `UIImpactFeedbackGenerator`, `Ads` → Google Mobile Ads iOS +
   UMP, `Billing` → StoreKit 2 (same product ids), `LegacyMigration` → `NoLegacyMigration`.
5. **UI**: `ComposeUIViewController { BirdyApp(state, strings, game::post) }` on top of the render
   view. Drive `BirdyGame.frame()` from `CADisplayLink` on the render thread.
6. **Entry**: `createBirdyGame(PlatformServices(...), renderer)`. This is the same factory Android
   uses.
