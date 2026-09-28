# Birdy native: release builds and Play publishing

This documents how to build and sign the native (`native/androidApp`) app, and what the
existing Capacitor CI pipeline (`.github/workflows/play-release.yml`,
`scripts/configure_play_ci.py`, `scripts/publish_play.py`) needs to change to publish it instead
of (or alongside) the JS/Capacitor app. See `docs/native/spec/platform.md` §4 for the full spec
this is ported from; only the deltas and the "what to do" summary live here.

## Local release builds

```
cd native
export ANDROID_HOME=/opt/android-sdk
./gradlew :androidApp:assembleRelease   # unsigned if no keystore.properties (see below)
./gradlew :androidApp:bundleRelease     # the .aab Play actually wants
```

Signing (`native/androidApp/build.gradle.kts`): looks for `native/keystore.properties` first,
then falls back to the Capacitor project's `android/keystore.properties` so both apps can share
one upload key (recommended — see "Same Play listing" below). Either file has the same four
properties as the Capacitor scaffold:

```
storeFile=birdy-upload.jks   # resolved relative to this properties file
storePassword=...
keyAlias=birdy-upload
keyPassword=...
```

If neither file exists, `assembleRelease`/`bundleRelease` still succeed but produce an
**unsigned** artifact (`signingConfigs.release` has no values, `buildTypes.release` never
attaches it) — this matches the Capacitor `build.gradle`'s behaviour exactly and is why
`assembleRelease` is safe to run in the acceptance build on this machine (no keystore present).

`minifyEnabled` stays `false`, matching the Capacitor app (no ProGuard/R8 shrink-and-obfuscate
pass has been tested against the KMP/Compose bytecode yet — turn it on only after verifying a
release build still runs, since Compose/coroutines/kotlinx.serialization reflection-ish paths
are common R8 footguns).

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

The native app keeps `applicationId de.robinrehbein.birdy` — the plan is to **replace** the
Capacitor build on the *same* Play Console app entry (numeric id `4976256029001868876`, see
`platform.md` §4.6), not create a second listing. That means:

- Every native build's `BIRDY_VERSION_CODE` must be strictly greater than every versionCode
  Play has ever accepted for this app, Capacitor or native. Since both pipelines would use the
  same day-based CI formula, this holds automatically as long as the native pipeline's first
  run happens after the Capacitor pipeline's last run (true here — day-based codes only
  collide if both pipelines build on the exact same UTC day with the same run-number math,
  which can't happen once the Capacitor workflow is retired per the cutover step below).
- Do **not** run both pipelines' `publish` jobs concurrently once the native app replaces the
  Capacitor one; keep the Capacitor workflow's `publish` job (or the whole workflow) but flip
  it to manual-only / disabled once the native app's first release track upload is verified,
  so nobody accidentally re-publishes the old JS build over a newer native one.
- The upload key must be the **same** key Play already has on file for this `applicationId`
  (Play rejects an AAB signed with an unknown upload key for an existing app). Reuse the
  existing `android/birdy-upload.jks` — this is exactly why `androidApp/build.gradle.kts`
  falls back to `../android/keystore.properties` when `native/keystore.properties` is absent.

## What the CI pipeline needs (not modified here — describing the change, per task scope)

`.github/workflows/play-release.yml`, `scripts/configure_play_ci.py` and
`scripts/publish_play.py` are unmodified by this task (out of the platform task's owned paths).
To publish the native app instead of/alongside the Capacitor one, a future change needs to:

1. **Build step**: replace (or add a parallel job for) the `npm run android:aab` step with:
   ```
   cd native
   export ANDROID_HOME=... (already set up by the existing Android SDK action)
   ./gradlew :androidApp:bundleRelease
   ```
   producing `native/androidApp/build/outputs/bundle/release/androidApp-release.aab` instead of
   `android/app/build/outputs/bundle/release/app-release.aab`. `publish_play.py`'s path argument
   changes accordingly; its Android Publisher API logic (edit/upload/track/commit) is
   **completely unaffected** — it only cares about the `.aab` bytes and the package name, both
   unchanged.
2. **Signing step**: `configure_play_ci.py` already writes `android/keystore.properties` +
   `android/birdy-upload.jks`; either point it at `native/keystore.properties` /
   `native/birdy-upload.jks` too (simplest: write both, or write only the `native/` copy since
   the native `build.gradle.kts` also checks `../android/keystore.properties` as a fallback —
   pick one canonical location once the Capacitor build is retired to avoid maintaining two
   copies of the same secret-derived files).
3. **No changes** needed to `scripts/publish_play.py` itself, the versionCode formula, the
   `internal`/closed-track-only safety property, or the release-notes text — all of that is
   pure Android-Publisher-API plumbing that doesn't know or care which Gradle project produced
   the `.aab`.
4. **Cutover sequencing**: run the native job against `PLAY_PUBLISH_ENABLED=true` on the
   `internal` track only first (manually verify installs/updates correctly over an existing
   Capacitor install, including the legacy-progress migration — see below), before touching the
   closed test track or disabling the Capacitor workflow.
5. Repository variables (`PLAY_PUBLISH_ENABLED`, `BIRDY_MONETIZATION_RELEASE_READY`,
   `PLAY_CLOSED_TRACK`) and secrets (`UPLOAD_KEYSTORE_BASE64`, `UPLOAD_STORE_PASSWORD`,
   `UPLOAD_KEY_ALIAS`, `UPLOAD_KEY_PASSWORD`, `PLAY_SERVICE_ACCOUNT_JSON`) are reused as-is —
   nothing native-specific needs to be added to GitHub's secrets/variables store.

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
