# Birdy native: release builds and Play publishing

This documents how to build and sign the native (`native/androidApp`) app, and how the CI
pipeline (`.github/workflows/play-release.yml`, `scripts/configure_play_ci.py`,
`scripts/publish_play.py`) builds and publishes it. The pipeline previously built the old
JS/Capacitor app; the cutover to building and signing the native bundle is done in this repo (see
"What the CI pipeline does" below) — the remaining steps are device testing and the actual Play
rollout. See `docs/native/spec/platform.md` §4 for the full spec this was originally ported from;
only the deltas and the "what to do" summary live here.

## Local release builds

```
cd native
export ANDROID_HOME=/opt/android-sdk
./gradlew :androidApp:assembleRelease   # unsigned if no keystore.properties (see below)
./gradlew :androidApp:bundleRelease     # the .aab Play actually wants
```

Signing (`native/androidApp/build.gradle.kts`): looks for `native/keystore.properties`. The file
has these four properties (the same format the old Capacitor scaffold used):

```
storeFile=birdy-upload.jks   # resolved relative to this properties file
storePassword=...
keyAlias=birdy-upload
keyPassword=...
```

If the file doesn't exist, `assembleRelease`/`bundleRelease` still succeed but produce an
**unsigned** artifact (`signingConfigs.release` has no values, `buildTypes.release` never
attaches it) — this matches the old Capacitor `build.gradle`'s behaviour and is why
`assembleRelease` is safe to run in the acceptance build on this machine (no keystore present).

The release build enables R8 minification and resource shrinking. A local bundle and lint
build passed on 28 September 2026; device installation and purchase flows still need testing.

## versionCode / versionName

- `native/androidApp/build.gradle.kts`: `versionCode = (BIRDY_VERSION_CODE env, else "5").toInt()`,
  `versionName = "2.0.0"`. The default of `5` is deliberately above the last Capacitor release
  (`4`, see `docs/native/spec/platform.md` §3.1) so a local build never collides with an
  already-uploaded Capacitor versionCode on the same Play listing.
- CI must compute `BIRDY_VERSION_CODE` with the **exact same formula** the Capacitor pipeline
  uses today (`platform.md` §4.2, golden fixture `docs/native/golden/platform-version-code.json`):
  ```
  suffix = GITHUB_RUN_NUMBER * 10 + GITHUB_RUN_ATTEMPT   # must stay <= 9999 per UTC day
  BIRDY_VERSION_CODE = floor(current_UTC_epoch_seconds / 86400) * 10000 + suffix
  ```
  This is not duplicated as code anywhere in `native/` (it's a one-line shell/Python
  expression in the CI step, not application logic) — reuse `scripts/configure_play_ci.py`'s
  sibling logic in `play-release.yml` verbatim; do not reimplement it for the native job.

## Same Play listing, one increasing versionCode sequence

The native app keeps `applicationId de.robinrehbein.birdy` and **replaces** the old Capacitor
build on the *same* Play Console app entry (numeric id `4976256029001868876`, see `platform.md`
§4.6) rather than creating a second listing. That means:

- Every native build's `BIRDY_VERSION_CODE` must be strictly greater than every versionCode Play
  has ever accepted for this app, Capacitor or native. Both pipelines used the same day-based CI
  formula, so this holds automatically as long as the native pipeline's first run happens after
  the old Capacitor pipeline's last run (true here).
- Do **not** run a native and a Capacitor publish job concurrently against the same Play app —
  moot now that the workflow only builds the native bundle (see below), but keep in mind if an
  old Capacitor workflow run is ever manually re-triggered from an older commit.
- The upload key must be the **same** key Play already has on file for this `applicationId`
  (Play rejects an AAB signed with an unknown upload key for an existing app). CI reuses the
  existing `birdy-upload.jks`/keystore secrets for this.

## What the CI pipeline does

`.github/workflows/play-release.yml` builds and publishes the **native** app:

1. **Build step**: `cd native && ./gradlew :shared:testDebugUnitTest :androidApp:bundleRelease`, producing
   `native/androidApp/build/outputs/bundle/release/androidApp-release.aab`. `publish_play.py`
   uploads that `.aab`; its Android Publisher API logic (edit/upload/track/commit) only cares
   about the `.aab` bytes and the package name, unchanged from the Capacitor era.
2. **Signing step**: `scripts/configure_play_ci.py` writes `native/keystore.properties` and
   `native/birdy-upload.jks` from the repository secrets before the build step runs.
3. `scripts/publish_play.py`, the versionCode formula, the `internal`/closed-track-only safety
   property, and the release-notes text are unchanged from the Capacitor pipeline — all of that
   is pure Android-Publisher-API plumbing that doesn't know or care which Gradle project produced
   the `.aab`.
4. **Remaining checks**: verify an install/update over an existing Capacitor install,
   including legacy-progress migration (see below), on a Play test device. The pipeline
   uploads to `internal` and the closed `alpha` test track only.
5. Repository variables (`PLAY_PUBLISH_ENABLED`, `BIRDY_MONETIZATION_RELEASE_READY`,
   `PLAY_CLOSED_TRACK`) and secrets (`UPLOAD_KEYSTORE_BASE64`, `UPLOAD_STORE_PASSWORD`,
   `UPLOAD_KEY_ALIAS`, `UPLOAD_KEY_PASSWORD`, `PLAY_SERVICE_ACCOUNT_JSON`) are unchanged from the
   Capacitor setup — nothing native-specific needed to be added to GitHub's secrets/variables
   store.

## Legacy migration verification

`native/shared/src/androidMain/.../WebViewLegacyMigration.kt` reads the Capacitor WebView's
`localStorage` via a hidden WebView at `https://localhost` (Capacitor Android's documented
default origin). This is **only exercised on an in-place update of the same package**
(`de.robinrehbein.birdy`) — a side-by-side install sees no legacy data, which is the normal/safe
case (idempotent no-op). See `ARCHITECTURE.md` "Migration from the Capacitor app" and the
emulator smoke test results below for what was actually verified on this machine.

## Known gap: no server-side purchase verification

Preserved intentionally from the JS app (`platform.md` §2.9): every purchase/ad-reward grant
trusts the client SDK's local state (Play Billing / AdMob reward callback) with no backend
receipt validation. This is a pre-existing, documented limitation (`docs/MONETIZATION.md`), not
something this port regressed or is expected to fix.
