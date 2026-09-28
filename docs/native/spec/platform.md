# platform: ads, billing, Android shell, signing, and the Play release pipeline

Source of truth (current files, all line numbers 1-indexed):

- `src/ads.js` (46 lines) — rewarded-ads/UMP-consent client, called from `src/main.js`.
- `src/billing.js` (91 lines) — Play Billing client, called from `src/main.js`; consumes
  `src/catalog.js` (`CATALOG`, product-id derivation) and `src/progress.js` (wallet/entitlement
  storage — `grantPurchasedCoins`, `grantPaidProduct`, `syncPaidProducts`,
  `hasProcessedPurchase`, all cited below for completeness even though `progress.js`'s
  non-purchase behaviour, e.g. missions/achievements/gift, is out of scope for this doc).
- `android/app/src/main/java/de/robinrehbein/birdy/BirdyAdsPlugin.java` (156 lines) — the
  native half of `ads.js`, a Capacitor plugin wrapping AdMob + UMP.
- `android/app/src/main/java/de/robinrehbein/birdy/BirdyBillingPlugin.java` (198 lines) — the
  native half of `billing.js`, a Capacitor plugin wrapping Play Billing Library 9.1.0.
- `android/app/src/main/java/de/robinrehbein/birdy/MainActivity.java` (34 lines) — the single
  Activity: immersive fullscreen, keep-screen-on, plugin registration.
- `android/app/src/main/AndroidManifest.xml`, `android/app/build.gradle`,
  `android/variables.gradle`, `android/app/src/main/res/**` (icons, splash, strings, styles),
  `capacitor.config.json` — app shell configuration, signing, resources.
- `.github/workflows/play-release.yml`, `scripts/configure_play_ci.py`,
  `scripts/publish_play.py` — the CI pipeline that builds a signed bundle and publishes it to
  Play's internal + closed-test tracks.
- `docs/STORE.md`, `docs/MONETIZATION.md` — product IDs, prices, consent/versioning narrative
  (German-language source docs; this spec translates the load-bearing facts to English and
  cites exact values).

Golden fixtures (pure, catalog-derived logic only — everything else here is either I/O-bound
native SDK glue with no meaningful "golden" output, or literal config data already given
verbatim in tables below):

- `docs/native/golden/platform-product-ids.json` ← `scripts/native-golden/platform-product-ids.mjs`
  — the exact list of Play product ids the app knows about, derived from `CATALOG` exactly as
  `billing.js:7-11` derives it.
- `docs/native/golden/platform-version-code.json` ← `scripts/native-golden/platform-version-code.mjs`
  — sample inputs/outputs of the CI versionCode formula (`play-release.yml`'s "Build signed
  bundle" step), including the overflow-failure case.

Re-run either with `node scripts/native-golden/<file>.mjs` (both import only pure code/data,
no DOM/THREE side effects).

---

## 1. Rewarded ads (`ads.js`, `BirdyAdsPlugin.java`)

### 1.1 Overview

Two independent rewarded-ad placements, both entirely optional (never required to play), both
gated behind a single UMP (Google User Messaging Platform) consent flow. The JS side
(`ads.js`) is a thin async wrapper around a Capacitor plugin (`BirdyAds`) that does all real
work natively. On non-native platforms (`Capacitor.isNativePlatform() === false`, i.e. the
Vite dev server / browser) `plugin` is `null` and every method becomes a safe no-op — **iOS
port note**: the native plugin is what must be reimplemented per platform; the JS/KMP-shared
call surface (`init`, `showRewarded`, `showPrivacyOptions`, the three reactive flags) stays
identical.

### 1.2 JS-side state (`ads.js:6-9`)

```js
export const ads = {
  available: false,            // rewarded "coins" ad ready to show
  passAvailable: false,        // rewarded "pass" ad ready to show
  privacyOptionsRequired: false, // UMP: player must be offered a privacy-options entry point
};
```

These three booleans are the entire reactive surface `main.js` reads (see `main-a.md` /
`main-b.md` for the HUD wiring — `#reward-ad-btn`, `#style-pass-btn`, `#ad-privacy-btn`). They
are updated only via the `onChange` callback passed to `init`.

### 1.3 `ads.init(onChange)` (`ads.js:11-23`)

1. No-op if `plugin` is null (web build).
2. Defines `update(status)`: sets the three booleans from `status.ready`, `status.passReady`,
   `status.privacyOptionsRequired` (each coerced with `Boolean(...)`, so any truthy/falsy JSON
   value works), then calls `onChange()` (a render-trigger, no arguments).
3. `await plugin.addListener('status', update)` — subscribes to the native `status` event
   (fired by the plugin whenever ad-load state changes, `BirdyAdsPlugin.java:76,83,119`).
4. `update(await plugin.getStatus())` — seeds the initial state from one `getStatus()` call.
5. The whole body is wrapped in `try {} catch { /* no Google services or offline */ }` — any
   failure (Play Services missing, no network, plugin exception) leaves the three flags at
   their `false` defaults and `init` resolves silently. **No retry loop** exists in JS; the
   native side keeps trying in the background (see §1.6) and pushes `status` events when ads
   become available.

Called exactly once, from `main.js:249` (`ads.init(updateAdsUi)`), at module load — not gated
on any user gesture.

### 1.4 `ads.showRewarded(kind = 'coins')` (`ads.js:25-41`)

`kind` is `'coins'` or `'pass'`.

1. Guard: if `!plugin`, or the relevant flag (`passAvailable` for `'pass'`, `available`
   otherwise) is falsy, return `false` immediately (no plugin call at all).
2. **Optimistic flag clear**: before calling the plugin, set that kind's flag to `false` (so a
   second tap while the first ad is showing is rejected by the guard above — this is the only
   re-entrancy protection; there's no explicit "is showing" lock). This does **not** call
   `onChange()` — the button's own disabled state during the async call is what `main.js`
   relies on for the visible feedback (see `main-b.md` §"reward-ad-btn"/"style-pass-btn").
3. `try`: `await plugin.showRewarded({ kind })`, return `Boolean(result.earned)`.
4. `catch`: return `false` (ad failed to show, or was rejected/threw for any reason).
5. `finally`: **always** re-fetch `plugin.getStatus()` and reassign all three flags from it
   (wrapped in its own try/catch that leaves state as the optimistic clear left it on failure).
   This is what makes the UI correctly reflect "still loading" vs. "loaded again" right after a
   show attempt, without waiting for a `status` event.

Return value contract: `true` only if the user watched the ad to completion and Play's reward
callback fired; `false` for every other outcome (unavailable, dismissed early, load/show
error). Callers ( `main.js`) then call the matching `progress.grantRewardedCoins()` /
`progress.grantStylePass()` **only when `true`** — the coin/pass grant is entirely
client-side, gated purely on this boolean (no server-side reward verification exists;
`docs/MONETIZATION.md` explicitly flags this as unresolved for real ad revenue but it is fine
for a reward the player can only "cheat" into their own progress file).

### 1.5 `ads.showPrivacyOptions()` (`ads.js:43-45`)

No-op unless `plugin` exists **and** `privacyOptionsRequired` is currently `true`. Otherwise
awaits `plugin.showPrivacyOptions()` (native shows Google's UMP "privacy options" form; no
return value is read). Wired to a menu button that itself is hidden unless
`ads.privacyOptionsRequired` (`main.js:217`).

### 1.6 Native plugin: `BirdyAdsPlugin` (`BirdyAdsPlugin.java`)

Constants (`:27-29`):

| Name | Value | Used when |
|---|---|---|
| `TEST_UNIT` | `ca-app-pub-3940256099942544/5224354917` | `BuildConfig.DEBUG == true` (any kind) |
| `LIVE_COINS_UNIT` | `ca-app-pub-1786159152036324/7854280106` | release build, `kind == "coins"` |
| `LIVE_PASS_UNIT` | `ca-app-pub-1786159152036324/8020434087` | release build, `kind == "pass"` |
| AdMob App ID (manifest meta-data, `AndroidManifest.xml:14-15`) | `ca-app-pub-1786159152036324~5284705514` | always |

Per-kind state: `rewardedAds: Map<String,RewardedAd>` (loaded, ready ads keyed by kind),
`loading: Set<String>` (kinds currently mid-load, prevents duplicate `RewardedAd.load` calls),
`consent: ConsentInformation`, `initialized: boolean` (true once `MobileAds.initialize`'s
callback has fired).

**`load()`** (Capacitor plugin lifecycle hook, called once on plugin attach, `:36-52`), all on
the UI thread:
1. `MobileAds.setRequestConfiguration(...)` with **`TAG_FOR_UNDER_AGE_OF_CONSENT_TRUE`** and
   **`MAX_AD_CONTENT_RATING_G`** — every player is treated as under the age of consent
   (comment: "Birdy targets ages 13+"; regardless, ads requested are always
   non-personalized/age-appropriate). **Porting note**: this must be set before any ad
   request, on every platform — it is a compliance requirement, not a Birdy-specific choice.
2. Build `ConsentInformation` via `UserMessagingPlatform.getConsentInformation(context)`.
3. `ConsentRequestParameters` with `setTagForUnderAgeOfConsent(true)`.
4. `consent.requestConsentInfoUpdate(activity, params, onSuccess, onFailure)`:
   - `onSuccess` → `UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity, error ->
     startAdsIfAllowed())` — Google decides whether a consent dialog needs to be shown (EEA/UK
     players; elsewhere this is a no-op that calls its callback immediately) and shows it if
     so; either way `startAdsIfAllowed()` runs once the form flow (if any) completes.
   - `onFailure` (`error ->`) → also calls `startAdsIfAllowed()` directly (so a consent-info
     fetch failure, e.g. offline, does not permanently block ads — it just means
     `canRequestAds()` will read `false` and ads stay unavailable until it's retried, which
     only happens on the next process start; there is no in-session retry of the consent
     fetch itself).

**`startAdsIfAllowed()`** (`:54-62`): no-op unless `consent != null && consent.canRequestAds()`
and not already `initialized`. Otherwise `MobileAds.initialize(context, status -> { runOnUiThread
{ initialized = true; loadRewarded("coins"); loadRewarded("pass"); } })` — both placements are
pre-loaded immediately, before the player has done anything ad-related.

**`loadRewarded(kind)`** (`:64-86`): no-op if not initialized, consent missing/denied, the kind
is already loaded (`rewardedAds.containsKey`), or already loading. Otherwise marks it loading
and calls `RewardedAd.load(context, unit, new AdRequest.Builder().build(), callback)` where
`unit` is `TEST_UNIT` in debug builds, else the live unit for that kind. On success: unmark
loading, store the ad, `notifyListeners("status", status())` (pushes the new availability to
JS). On failure (`onAdFailedToLoad`): unmark loading, **do not** retry automatically — the next
opportunity to retry is the next `getStatus()` call from JS (see below) or another
`showRewarded`/`getStatus` round trip; there is no backoff timer.

**`status()`** (`:88-95`) — the exact JSON shape both `getStatus` and the `status` event push:
```json
{ "ready": <bool>, "passReady": <bool>, "privacyOptionsRequired": <bool> }
```
`ready` = `rewardedAds` has `"coins"` **and** `consent.canRequestAds()`; `passReady` likewise
for `"pass"`; `privacyOptionsRequired` = `consent.getPrivacyOptionsRequirementStatus() ==
REQUIRED` (independent of whether ads are currently allowed — this can be true even while
`canRequestAds()` is false, e.g. consent was explicitly declined but the privacy-options entry
point must still exist).

**`getStatus` plugin method** (`:97-104`): on the UI thread, opportunistically calls
`loadRewarded("coins")` and `loadRewarded("pass")` (so every status poll is also a load-retry
trigger) then resolves with `status()`.

**`showRewarded` plugin method** (`:106-139`): on the UI thread.
1. `kind` defaults to `"coins"` via `call.getString("kind","coins")`; rejects with
   `"Unknown reward"` if not `"coins"`/`"pass"`.
2. Pops (`remove`s) the ad for that kind from `rewardedAds` — **the ad instance is consumed
   here regardless of whether it ends up shown successfully**, so a failed show does not leave
   a stale ad object around.
3. Rejects with `"Ad unavailable"` if there was no ad, or consent no longer allows ads (the
   latter can happen if consent state changed between load and show).
4. Otherwise immediately `notifyListeners("status", status())` (both `ready`/`passReady` will
   now read false for that kind since the ad was just removed — the JS optimistic flag clear
   at `ads.js:27-28` is thus redundant-but-harmless with this, both fire).
5. `ad.setImmersiveMode(true)`; sets a `FullScreenContentCallback`:
   - `onAdDismissedFullScreenContent()` → resolve the call with `{ earned: earned[0] }` (a
     boolean captured by the reward callback below), then `loadRewarded(kind)` to refill.
   - `onAdFailedToShowFullScreenContent(error)` → `call.reject("Ad could not be shown")`, then
     also `loadRewarded(kind)`.
6. `ad.show(activity, reward -> earned[0] = true)` — the reward callback fires (per AdMob
   semantics) only once the player has watched enough of the ad to earn it; it is **not**
   itself what resolves the plugin call — dismissal is. If the player earns the reward then
   the ad is dismissed abnormally (should not happen per SDK contract, but the code is
   defensive: `earned[0]` is read at dismissal time, whatever its value is then), the resolved
   value simply reflects the last known state of `earned[0]`.

**`showPrivacyOptions` plugin method** (`:141-155`): resolves immediately (no-op) unless
`consent.getPrivacyOptionsRequirementStatus() == REQUIRED`. Otherwise shows Google's privacy
options form; on completion (error or not) always calls `startAdsIfAllowed()` again (consent
may have just changed, e.g. player revoked/changed it, so ad eligibility must be
re-evaluated), then resolves or rejects with the error message.

### 1.7 Reward semantics / business rules (cross-referenced with `docs/MONETIZATION.md`)

- At most **3 rewarded ads per local calendar day**, shared across both placements — enforced
  entirely client-side in `progress.js`: `REWARDED_ADS_PER_DAY = 3` (`progress.js:92`),
  `progress.useRewardedSlot()` (`:197-201`) increments a day-keyed counter and refuses once
  exhausted; `progress.rewardedAdsLeft` (`:190-193`) is what `ads`-adjacent UI reads.
- Coins reward: `REWARDED_COINS = 30` (`progress.js:93`), granted by
  `progress.grantRewardedCoins()` (`:202-207`) — consumes one slot, then `+= 30` coins, saves,
  returns the amount (or `0` if no slot left).
- Style Pass reward: `STYLE_PASS_MS = 60 * 60 * 1000` (one hour, `progress.js:94`), granted by
  `progress.grantStylePass()` (`:208-213`) — refuses if a pass is already active
  (`stylePassMinutesLeft > 0`) or no slot left; otherwise sets
  `data.stylePassUntil = Date.now() + STYLE_PASS_MS`. While active, `progress.owns(kind, id)`
  (`:257-260`) treats **every** skin and world as owned (`kind === 'skin' || kind === 'world'`)
  in addition to permanently-owned ones — the pass is a temporary "try everything" unlock, not
  a coin grant, and expires automatically the moment `Date.now()` passes `stylePassUntil`
  (checked live via the `stylePassMinutesLeft` getter, `:194-196`, no timer/persistence of
  "expired" state needed).
- No ad is ever shown mid-flight (enforced by the HUD only showing these buttons on the
  menu/game-over screens, not during a run — see `main-a.md`/`main-b.md`).
- Debug builds always use Google's test ad unit regardless of kind (`BuildConfig.DEBUG`).

### 1.8 Porting notes (ads)

- Web APIs used: none beyond the Capacitor bridge itself (this is 100% native-delegated).
- Android-specific SDKs: Google Mobile Ads SDK (`play-services-ads:25.5.0`) and User Messaging
  Platform (`user-messaging-platform:4.0.0`).
- **iOS equivalent**: Google Mobile Ads SDK has an iOS build with an equivalent rewarded-ad and
  UMP consent API (`GADRewardedAd`, `UMPConsentInformation`); the shared KMP interface (an
  `expect`/injected `AdsPort` with `init`, `showRewarded(kind)`, `showPrivacyOptions`,
  `status` flow) can be implemented 1:1 against it. The three ad-unit IDs and the AdMob App ID
  are platform-specific (iOS needs its own AdMob app + ad unit IDs from the AdMob console —
  not yet created for Birdy; do not reuse the Android IDs).
- The `status` push-event model (native → JS listener) should become the KMP port's
  `StateFlow<AdsStatus>` (or equivalent reactive stream) with the same three fields.

---

## 2. Play Billing (`billing.js`, `BirdyBillingPlugin.java`)

### 2.1 Product catalog & IDs

Billing knows exactly two categories of product:
1. **Consumable coin packs** — `coinIds = ['birdy_coins_500', 'birdy_coins_1500']`
   (`billing.js:7`), hardcoded, not catalog-derived.
2. **Permanent unlocks** — one product per **skin** and **world** in `CATALOG` (from
   `catalog.js`) that has `price > 0` (i.e. excluding the always-free first item of each kind):
   `itemIds = ['skin','world'].flatMap(kind => CATALOG[kind].filter(item => item.price > 0)
   .map(item => \`birdy_${kind}_${item.id}\`))` (`billing.js:8-9`). **Patterns, hats, eyes,
   beaks, trails and pipes are coin-only — never real-money purchasable**, regardless of their
   coin price in `catalog.js`.

`ids = [...coinIds, ...itemIds]` (`billing.js:10`) is the full list ever queried from Play.
`isPermanent(id) = itemIds.includes(id)` (`:11`) — an O(n) membership check against that fixed
list (23 items as of this snapshot — see the golden fixture for the exact current list; it
changes whenever `catalog.js` gains/removes a priced skin or world).

The exact current 25-entry list (2 coin packs + 23 permanent items) is in
`docs/native/golden/platform-product-ids.json`, generated from the live `CATALOG` by
`scripts/native-golden/platform-product-ids.mjs` — **do not hand-copy this list into Kotlin;
regenerate it from whatever `catalog.js`'s Kotlin equivalent ends up being**, using the same
rule (`skin`/`world`, `price > 0`, id format `birdy_<kind>_<itemId>`), so it can never drift.

Google Play–side product IDs and EUR prices are configured out-of-band in the Play Console
(not in code); `docs/MONETIZATION.md`'s table is the source for what to configure there:

| Product ID pattern | Play Console content | EUR price (Germany) | Type |
|---|---|---|---|
| `birdy_coins_500` | 500 coins | €0.99 | Consumable, repeatable |
| `birdy_coins_1500` | 1500 coins (~16% cheaper per coin) | €2.49 | Consumable, repeatable |
| `birdy_skin_<id>` | One bird skin, permanent | €0.99 (sky/cardinal/robin/mint/coral) / €1.49 (flamingo/parrot/penguin/night/snowy) / €1.99 (peacock/gold) | One-time, permanent |
| `birdy_world_<id>` | One world/zone, permanent | winter €1.99, beach €2.49, candy €2.99, mushroom €3.49 | One-time, permanent |

Note the rare animated skins (`toadstool`, `basketball`, `football`, `water`, `lava`,
`diamond`, `galaxy` in `catalog.js`) are **coin-only achievement rewards** — they are never
included in `itemIds` as real-money products even though they have a nonzero coin `price`
(only `skin`/`world` kinds are ever billing-eligible, and these are still `kind: 'skin'` with
real coin prices 3000–6000 — they ARE technically eligible by the `price > 0` rule and DO
appear in `itemIds`/the golden fixture; treat this note as informational, not a special case
to hand-code — the code makes no distinction between "rare" and "regular" skins for billing
purposes, `rare`/`fx` are cosmetic-effect flags, not entitlement flags).

### 2.2 JS-side state & helpers (`billing.js:13-20`)

```js
export const billing = {
  products: new Map(),      // id -> {id, title, description, price} from Play, refreshed by refresh()
  ready: false,             // true once refresh() has completed at least once successfully
  onChange: () => {},
  onGrant: () => {},
  get supported() { return Boolean(plugin); },
  itemId: (kind, id) => `birdy_${kind}_${id}`,
  price(id) { return this.products.get(id)?.price ?? null; }, // Play's localized, formatted price string, or null if unknown
};
```
`price(id)` returns Play's **formatted price string** (e.g. `"0,99 €"`), not a number — it is
whatever `ProductDetails.OneTimePurchaseOfferDetails.getFormattedPrice()` returns natively,
already localized. A Kotlin port must preserve "formatted string, locale-correct, or null
until Play has answered" as the contract; do not attempt to reconstruct/reformat it.

### 2.3 `billing.init(onChange, onGrant)` (`billing.js:22-32`)

No-op if unsupported (web/no plugin). Stores the two callbacks, then, all in one try/catch
(silently swallowing "Play Store missing or billing temporarily unavailable"):
1. `plugin.addListener('purchase', purchase => this.handlePurchase(purchase))` — fires once per
   purchase event pushed by the native `PurchasesUpdatedListener` (new purchases **and**
   purchases discovered on billing-client reconnect, per Play Billing semantics).
2. `plugin.addListener('ready', () => this.refresh())` — fires once when the native
   `BillingClient` finishes `startConnection` successfully; triggers the first `refresh()`.
3. `App.addListener('resume', () => this.refresh())` (Capacitor `@capacitor/app` plugin) —
   **every time the app returns to the foreground**, re-run `refresh()` unconditionally. This
   is the mechanism that picks up purchases completed outside the app (e.g. in the Play Store
   app, or a purchase that was pending and got confirmed while backgrounded).
4. If `(await plugin.getStatus()).ready` is already true (billing client connected before
   `init` ran — a race that can happen if `load()`'s connection finished very fast), call
   `refresh()` once immediately rather than waiting for the `ready` event that will never fire
   again.

### 2.4 `billing.handlePurchase(purchase)` (`billing.js:34-58`)

`purchase` shape (from `BirdyBillingPlugin.toJs`, `:50-58`): `{ products: string[], token:
string, state: number, acknowledged: boolean, time: number }` where `state` is Play's
`Purchase.PurchaseState` int (`0` UNSPECIFIED, `1` PURCHASED, `2` PENDING — Play Billing
constants, not Birdy-defined).

1. **Guard**: `if (purchase.state !== 1 || !purchase.token) return;` — **only state `1`
   (PURCHASED) grants anything.** Pending purchases (state `2`, e.g. a cash/PayPal payment
   method awaiting confirmation) are silently ignored until Play later reports them as state
   `1` via a fresh `purchase`/`ready`/`resume`-triggered event. There is no separate "show
   pending UI" path in Birdy.
2. For each product id in `purchase.products` (a purchase can bundle multiple products, though
   Birdy never actually creates multi-product purchases):
   - **If it's a coin pack** (`coinIds.includes(id)`):
     - `const credited = progress.grantPurchasedCoins(id, purchase.token)` — see §2.6.1. If
       `credited` (nonzero), call `this.onGrant({ id, coins: credited })` (UI toast/animation
       hook).
     - **Consume it** if either `credited` is truthy (just paid out) **or**
       `progress.hasProcessedPurchase(purchase.token)` is already true (this token was already
       credited in the past — e.g. a previous `consume()` call failed after the coins were
       already saved, and Play is re-delivering the still-owned purchase on `restore()`). In
       both cases: `try { await plugin.consume({ token }) } catch { /* retry on restore */ }`
       — swallow consume failures; the purchase stays in Play's owned list and will be
       revisited on the next `restore()`/`refresh()` cycle. **A consumable that was credited
       but never successfully consumed cannot be re-credited** (the token dedup in
       `processedPurchases` prevents double-payout even if `consume` never succeeds).
   - **Else if it's a permanent item** (`isPermanent(id)`):
     - `const newlyOwned = progress.grantPaidProduct(id)` — see §2.6.2. Three possible return
       values: `true` (grant succeeded, wasn't owned before), `false` (grant succeeded — or was
       a no-op because already owned — but not newly owned), `null` (the local save failed —
       **do not acknowledge in this case**: `if (newlyOwned === null) continue;` skips both the
       `onGrant` call and the acknowledge call for this product entirely, so Play keeps
       offering it as unacknowledged and it will be retried on the next sync).
     - If `newlyOwned === true`, call `this.onGrant({ id, permanent: true })`.
     - If `!purchase.acknowledged`: `try { await plugin.acknowledge({ token }) } catch {}` —
       **Play requires every non-consumable purchase to be acknowledged within 3 days or it is
       automatically refunded.** Acknowledge failures are swallowed and retried on the next
       sync (via `restore()`, which re-delivers unacknowledged purchases).
3. `this.onChange()` is called unconditionally at the end (even if nothing was granted) — this
   still triggers a re-render of the shop UI (e.g. wallet display).

### 2.5 `billing.refresh()` (`billing.js:60-82`)

No-op if unsupported. **Deduplicates concurrent calls**: if a refresh is already in flight
(`this.refreshing` set), returns/awaits that same promise rather than starting a second one
(`refresh()` can be triggered by `ready`, `resume`, and the initial `init()` check in quick
succession). Inside the single-flight body:

1. `const { items } = await plugin.restore()` — asks Play for all currently **owned**
   purchases (`INAPP` type; see §2.7 `restore` plugin method — this returns purchases in any
   state Play still has recorded for this Play account/device, not just PURCHASED).
2. `const purchases = Array.isArray(items) ? items : []` (defensive; native should always
   return an array).
3. `const paid = purchases.filter(p => p.state === 1).flatMap(p => p.products ||
   []).filter(isPermanent)` — the full, authoritative set of permanent-item product ids Play
   currently confirms as purchased.
4. `if (!progress.syncPaidProducts(paid)) return;` — **overwrites** the local
   `data.paidProducts` list with exactly this set (see §2.6.3); if the local save fails, abort
   the rest of `refresh()` entirely (leaves `ready`/`products` untouched, does **not** call
   `onChange`).
5. `for (const purchase of purchases) await this.handlePurchase(purchase)` — **sequentially**
   (not `Promise.all`) re-runs the full grant/consume/acknowledge logic (§2.4) for every
   restored purchase, including ones already fully processed (idempotent by the
   `processedPurchases`/`paidProducts` dedup inside `progress.js`). This is the mechanism that
   catches purchases that were interrupted mid-flow (paid but never consumed/acknowledged).
6. `const result = await plugin.getProducts({ ids })` — re-fetches localized product details
   for the **entire** `ids` list (all 25, not just owned ones) so prices are always fresh.
7. `this.products = new Map((result.items || []).map(p => [p.id, p]))`, `this.ready = true`,
   `this.onChange()`.
8. On any exception in the above: `this.ready = false; this.onChange();` (so the shop can show
   "prices unavailable" but **previously restored/synced purchases from step 4 are still kept
   usable** — the failure only affects step 6/7, unless it happened earlier, since steps run
   sequentially and an early throw skips later steps but the JS already committed step 4's
   `syncPaidProducts` before any network call that's likely to fail).
9. `finally`: clear `this.refreshing` after the awaited promise settles either way.

### 2.6 Progress-side purchase storage (`progress.js`) — needed by billing, spec'd here since it is billing's persistence contract

Storage key: `birdy-progress` (same JSON blob as the rest of progress — see a future
progress-focused spec for the non-purchase fields; only the purchase-related fields are specced
here). Relevant fields, with defaults from `load()` (`progress.js:101-119`):
- `paidProducts: string[]` — product ids of every permanent item this device/save currently
  owns via real money (mirrors what Play last confirmed, kept in sync by `syncPaidProducts`).
- `processedPurchases: string[]` — purchase **tokens** (not product ids) of every coin-pack
  purchase that has already been credited, ever — a token, once present, blocks that exact
  purchase from paying out coins again, permanently (grows unboundedly; Birdy never prunes it,
  since consumable purchase tokens are one-shot and Play never reuses a token).

#### 2.6.1 `grantPurchasedCoins(productId, token)` (`progress.js:244-255`)

```
amount = {'birdy_coins_500': 500, 'birdy_coins_1500': 1500}[productId]
if amount is undefined, or !token, or token already in processedPurchases: return 0
processedPurchases.push(token)
coins += amount
if save() fails: undo both (pop the token, subtract the coins), return 0
return amount
```
The save-rollback-on-failure is important: an unsaved credit must not be observable, or a
later successful save could persist an over-credit inconsistent with what was actually
recorded. **Kotlin port note**: this must be one atomic write (or an in-memory rollback
identical to this) — never allow `coins += amount` to be visible before the persisted token
list also reflects it.

#### 2.6.2 `grantPaidProduct(productId)` (`progress.js:229-236`)

```
match = /^birdy_(skin|world)_([a-z]+)$/.exec(productId)
if no match, or CATALOG[match[1]] has no item with that id and price > 0: return false
wasOwned = permanentlyOwns(match[1], match[2])   // items[kind].includes(id) || paidProducts.includes(productId)
if productId not already in paidProducts: push it
equip[match[1]] = match[2]                        // auto-equips the newly (re-)confirmed item
return save() ? !wasOwned : null
```
Note this **always re-equips** the item on every successful grant call, even if it was already
owned (e.g. on a `restore()` replay) — i.e. restoring purchases changes what's currently
equipped to the last-processed purchased item of each kind. This is intentional existing
behavior to preserve exactly, not a bug to "fix" in the port.

#### 2.6.3 `syncPaidProducts(productIds)` (`progress.js:237-243`)

```
paidProducts = unique(productIds) filtered to ids matching the same skin|world + price>0 regex/catalog check
return save()
```
This is a full **overwrite**, not a merge — anything in the old `paidProducts` not present in
the new authoritative list from Play is dropped. (It does not touch `items[kind]`, i.e.
coin-purchased/achievement-granted ownership is untouched — `permanentlyOwns` ORs both
sources, so a coin-bought item remains owned even if it's absent from `paidProducts`.)

#### 2.6.4 `permanentlyOwns(kind, id)` / `owns(kind, id)` (`progress.js:227-228,257-260`)

```
permanentlyOwns(kind, id) = items[kind].includes(id) || paidProducts.includes(`birdy_${kind}_${id}`)
owns(kind, id) = permanentlyOwns(kind, id) || (stylePassMinutesLeft > 0 && (kind==='skin'||kind==='world'))
```

### 2.7 `billing.purchase(id)` (`billing.js:84-90`)

Throws `Error('Product unavailable')` if unsupported, the id isn't in the locally cached
`products` map, or isn't in the known `ids` list. Otherwise **re-queries** Play for just that
one product (`plugin.getProducts({ ids: [id] })`) immediately before launching checkout — a
freshness check against price/availability changing between browsing the shop and tapping
"buy" — and throws the same error if it's no longer offered. Only then:
`await plugin.purchase({ id })` (native launches Play's checkout UI; resolves once the flow UI
closes, **not** once a purchase completes — the actual purchase result arrives later via the
`purchase` listener event, §2.4).

### 2.8 Native plugin: `BirdyBillingPlugin` (`BirdyBillingPlugin.java`)

`load()` (`:31-48`): builds a `BillingClient` with
`enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())`
and `enableAutoServiceReconnection()` (Billing Library 9.x auto-reconnect — no manual retry
loop needed for transient disconnects), a `PurchasesUpdatedListener` that, on
`BillingResponseCode.OK` with a non-null purchase list, pushes a `"purchase"` event per
purchase (`toJs`, §2.4's `purchase` shape). Calls `client.startConnection(...)`; on
`BillingResponseCode.OK` pushes a `"ready"` event (empty payload); `onBillingServiceDisconnected`
is a deliberate no-op (auto-reconnect handles it, and the client itself will surface
`isReady()==false` to any plugin method called meanwhile).

`getProducts` (`:67-103`): builds one `QueryProductDetailsParams.Product` per requested id,
type `INAPP` (all Birdy products are one-time in-app products, never subscriptions), queries
async. For each returned `ProductDetails`, **skips it entirely** if it has no
`OneTimePurchaseOfferDetailsList` entries (defensive — shouldn't happen for INAPP products but
guards a malformed Console config), otherwise caches it in the in-memory `products` map (used
later by `purchase`) and returns `{id, title, description, price}` where `price` is
`offers.get(0).getFormattedPrice()` (first/only offer — Birdy products have exactly one).

`purchase` (`:105-120`): looks up the cached `ProductDetails` by id (must have been fetched by
a prior `getProducts` call — this is why `billing.js:87` always re-fetches immediately before
purchasing), builds `BillingFlowParams` with that product + its first offer's token, launches
`client.launchBillingFlow` on the UI thread. Resolves on `BillingResponseCode.OK` (flow
launched), rejects with Play's debug message otherwise.

`restore` (`:123-137`): `queryPurchasesAsync` for `INAPP` type, returns every purchase Play
still has on record for the current Play account (any state), mapped through `toJs`.

`consume`/`acknowledge` (`:139-197`): both re-query `queryPurchasesAsync` fresh (not cached) to
find the purchase matching the given token **and** in state `PURCHASED`, then call
`consumeAsync`/`acknowledgePurchase` respectively; `acknowledge` short-circuits to success if
already acknowledged. Both reject `"Purchase not found"` if no match. `handleOnDestroy` ends
the billing connection.

### 2.9 Porting notes (billing)

- Web APIs used: none (fully native-delegated), same pattern as ads.
- **iOS equivalent**: StoreKit 2 (`Product`, `Transaction`, `Transaction.updates` async
  sequence in place of the `purchase`-event listener, `AppStore.sync()`/`Transaction.currentEntitlements`
  in place of `restore()`). StoreKit's transaction model is verification-based rather than
  acknowledge/consume-based — the KMP shared interface should model the operations
  (`getProducts`, `purchase`, `restore`, and a stream of incoming purchase updates) abstractly
  enough that both an Android (`consume`+`acknowledge`) and iOS (`finish`) backend can satisfy
  it; the shared `handlePurchase`/grant logic in `progress`-equivalent code should not need to
  know which backend it's running on.
- Apple's product IDs are configured independently in App Store Connect; they do not have to
  match Android's `birdy_*` ids, but keeping them identical is simplest for a shared catalog
  derivation (`platform-product-ids.json`).
- There is **no server-side receipt/token validation anywhere in this app** — all verification
  is "trust the client SDK's local state." This is called out as a known gap in
  `docs/MONETIZATION.md` (§"Vor einem Test-Release..."). A Kotlin/iOS port should preserve this
  (do not silently add server validation the JS reference doesn't have) but the architecture
  doc/README should keep flagging it as future work if server-side validation is ever added.

---

## 3. Android app shell

### 3.1 Identity & manifest (`AndroidManifest.xml`, `capacitor.config.json`, `variables.gradle`)

| Field | Value |
|---|---|
| `applicationId` / Capacitor `appId` | `de.robinrehbein.birdy` |
| App display name | `Birdy` (`res/values/strings.xml` `app_name`) |
| `compileSdk` / `targetSdk` | `36` |
| `minSdk` | `24` |
| `versionName` (current) | `1.0.3` |
| `versionCode` (current, local default) | `4` (overridable by `BIRDY_VERSION_CODE` env var, see §4) |
| Custom URL scheme | `de.robinrehbein.birdy` (`custom_url_scheme` string — Capacitor deep-link scheme; not otherwise used by Birdy today) |
| Capacitor `webDir` | `dist` (the Vite build output — this is what fills the WebView) |
| Capacitor `android.backgroundColor` | `#4ec0ca` (a teal, shown before/around the WebView while it loads) |

Manifest highlights (`AndroidManifest.xml`):
- Single `<activity android:name=".MainActivity">`, `launchMode="singleTask"`,
  `screenOrientation="portrait"` (locked, never rotates), `exported="true"` with the standard
  `MAIN`/`LAUNCHER` intent filter, `configChanges="orientation|keyboardHidden|keyboard|
  screenSize|locale|smallestScreenSize|screenLayout|uiMode|navigation|density"` (the Activity
  handles all these config changes itself — i.e. it is **not** recreated on any of them,
  including a locale change from system settings, since `i18n.js` manages language internally
  via `birdy-lang`, not via system locale).
- AdMob App ID meta-data (see §1.6) lives directly on `<application>`, not the activity.
- A `FileProvider` (`android:authorities="${applicationId}.fileprovider"`, not exported,
  grants URI permissions) with `file_paths.xml` exposing `external-path`/`cache-path` rooted at
  `.` (i.e. the entire external/cache dir tree) — a Capacitor-stock file-sharing provider,
  not Birdy-specific; **not currently used for anything Birdy-specific** (no share/export
  feature in the game as of this snapshot) but should be preserved if any native file-sharing
  plugin is later ported in the same slot.
- Permissions: `INTERNET` (ads/billing/store), `VIBRATE` (haptics — see below), and an explicit
  **removal** of `com.google.android.gms.permission.AD_ID` via `tools:node="remove"` — Birdy
  deliberately opts out of the advertising-ID permission Google Mobile Ads would otherwise pull
  in transitively, consistent with treating every player as under the age of consent /
  non-personalized ads only (§1.6). **Porting note: the Kotlin/native app must make the same
  deliberate opt-out** (there is no AndroidManifest merger magic needed in Compose/KMP — just
  don't declare `AD_ID`, and if any pulled-in AAR declares it, add the same `tools:node="remove"`
  override) — this is a compliance choice, not an oversight, and dropping it silently would
  change ad targeting behavior.

### 3.2 `MainActivity` (`MainActivity.java`)

- Registers both plugins (`BirdyAdsPlugin`, `BirdyBillingPlugin`) in `onCreate`, **before**
  `super.onCreate(savedInstanceState)` (Capacitor's `registerPlugin` must run before the bridge
  initializes to be picked up).
- `getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)` — screen never sleeps
  while the app is open (a tap-to-flap game would be unplayable if the screen dimmed
  mid-flight).
- `onWindowFocusChanged(hasFocus)`: whenever the window (re)gains focus, calls
  `hideSystemBars()` — an **immersive-sticky** fullscreen mode:
  `WindowInsetsControllerCompat.hide(WindowInsetsCompat.Type.systemBars())` +
  `setSystemBarsBehavior(BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE)` (status/nav bars are fully
  hidden but reappear temporarily on an edge swipe, standard Android immersive UX — not
  triggered on every frame, only on focus-change events, which include returning from another
  app, dismissing a system dialog, or first launch).
- No `onBackPressed`/`onBackInvokedCallback` override exists in this file — back-button
  handling (menu → close app; pause/shop/game-over → menu) is implemented in **JS**
  (`main.js`'s history/hardware-back handling — out of scope for this doc, see `main-a.md`/
  `main-b.md`) via Capacitor's `App` plugin `backButton` event, not natively. **Porting note**:
  a Kotlin/Compose app must implement this navigation stack itself (there's no WebView history
  to delegate to) — `docs/STORE.md`'s checklist row "Android-Zurück-Taste" (back button:
  game→pause→menu; shop/game-over→menu; menu→app closes) is the acceptance spec to replicate.

### 3.3 Splash & theme (`res/values/styles.xml`, `res/drawable*/splash.webp`, `capacitor.config.json`)

- `AppTheme.NoActionBarLaunch` (the launch theme, `parent="Theme.SplashScreen"` — Android 12+
  SplashScreen API, backward-compatible via the `core-splashscreen` androidx artifact):
  - `android:background="@drawable/splash"` — a static image background (per-density/
    orientation `.webp` files under `drawable-{port,land}-{m,h,xh,xxh,xxx}dpi`).
  - `windowSplashScreenBackground = "#5AA9E6"` (a sky blue) — the solid backdrop color shown
    behind/around the animated icon on Android 12+'s native splash API.
  - `windowSplashScreenAnimatedIcon = @mipmap/ic_launcher_foreground` — the launcher icon's
    foreground layer, reused as the splash's centered icon (Capacitor default wiring, not a
    custom animation).
  - `postSplashScreenTheme = @style/AppTheme.NoActionBar` — the theme the Activity switches to
    once the splash dismisses (plain, no action bar, `android:background="@null"` so the
    WebView shows through immediately without a visible flash).
- `AppTheme` (base, used pre-Android-12 as the splash driver via the legacy `drawable/splash`):
  `parent="Theme.AppCompat.Light.DarkActionBar"` with `colorPrimary`/`colorPrimaryDark`/
  `colorAccent` from `res/values/colors.xml` (not read in this pass — **verify exact hex values
  there before porting the legacy (pre-12) splash path**, they were not part of the file set
  reviewed for this spec).
- `capacitor.config.json`'s `android.backgroundColor: "#4ec0ca"` is a **separate** color
  (Capacitor's own WebView-container background, shown for the brief gap between the native
  splash dismissing and the WebView's first paint) — do not conflate it with the
  `#5AA9E6` splash-screen background above; a native rewrite has no WebView gap to fill, so this
  color has no direct native equivalent (it existed only to hide iframe/webview loading flicker).

### 3.4 Icon (`res/mipmap-*/ic_launcher*.png`, `res/drawable/ic_launcher_background.xml`, `res/values/ic_launcher_background.xml`)

The in-repo Android launcher icon set is **Capacitor/Android Studio's stock default
adaptive-icon template**, not a custom Birdy icon:
- Background color: `#6F7FC6` (a blue-violet, `ic_launcher_background.xml` color resource).
- Background drawable (`drawable/ic_launcher_background.xml`, 108×108dp vector): the stock
  Android Studio "grid of thin white lines on a solid teal-ish square" placeholder pattern
  (`fillColor="#26A69A"` base square with `#33FFFFFF` 0.8dp grid lines — note this teal
  `#26A69A` is the vector's own fill and does **not** match the `#6F7FC6` color resource above;
  the actual rendered background comes from whichever the adaptive-icon XML
  (`mipmap-anydpi-v26/ic_launcher.xml`, not read in this pass) selects — **verify which one
  wins before treating either as "the" brand color**).
- Foreground: `drawable-v24/ic_launcher_foreground.xml` (not read in this pass) plus
  pre-rendered flattened PNGs per density (`ic_launcher.png`, `ic_launcher_round.png`,
  `ic_launcher_foreground.png`, `ic_launcher_background.png` at `mdpi` 48px up to `xxxhdpi`
  192px, confirmed `xxxhdpi`: 192×192 RGBA PNG).
- **This is very likely stale/placeholder relative to the actual store presence**:
  `docs/STORE.md` describes a bespoke 512×512 store icon at `docs/store/icon-512.png`
  ("Vogel frontal vor Regenbogen und Sonnenuntergangshimmel" — bird facing forward against a
  rainbow and sunset sky) that is **separate from and does not match** this in-app launcher
  icon set. **Porting note / open question to flag to the team**: confirm whether the native
  app should ship a launcher icon derived from `docs/store/icon-512.png` (matching the store
  listing) instead of carrying over this stock placeholder — this spec does not resolve that
  product decision, it only documents what exists in the current repo.

### 3.5 Fonts

Referenced by `docs/STORE.md`'s rights-check table: **"Lilita One"**, SIL Open Font License,
commercially usable, no attribution string mandated by Birdy's own docs beyond the license
file itself. The task context states fonts live at `src/fonts` for the native build to source
from directly (this spec's file-read scope did not include enumerating that directory's exact
files — do so before wiring `androidApp`'s font resources; embed as a Compose/`FontFamily`
resource with the same license file carried into `native/androidApp`'s licensing/about
material).

### 3.6 Haptics

The manifest declares `VIBRATE`. No Java/Kotlin vibration call exists in the files reviewed for
this spec (`ads.js`/`billing.js`/the two plugins/`MainActivity`) — haptic triggers, if any,
are JS-side (likely via a Capacitor Haptics plugin call from `main.js`, out of scope here).
**Porting note**: the KMP `HapticsPort` interface should wrap `Vibrator`/`VibratorManager`
(Android) — confirm the exact trigger points and vibration patterns from `main.js` when that
file is specced, this doc only confirms the permission exists and is required.

---

## 4. Release signing, versioning, and the CI publish pipeline

### 4.1 Local release signing (`android/app/build.gradle:3-44`, `docs/STORE.md` §"Release signieren")

- `android/keystore.properties` (git-ignored, `.gitignore: android/keystore.properties`) holds
  four properties: `storeFile` (relative path, e.g. `birdy-upload.jks`), `storePassword`,
  `keyAlias` (`birdy-upload`), `keyPassword`.
- `build.gradle` loads it via plain `java.util.Properties` (`FileInputStream`) **only if the
  file exists**; if absent, `signingConfigs.release` has no values set and `buildTypes.release`
  does not apply a signing config at all (release build is **unsigned** — a local dev build
  without the properties file simply produces an unsigned, non-uploadable AAB/APK; this is not
  an error, just an inert default).
- The keystore file itself (`android/*.jks`, also git-ignored) is never committed; CI
  reconstructs both files from secrets (§4.3).
- `minifyEnabled false` — **no code shrinking/obfuscation** on release builds (deliberate or at
  least current-state; preserve unless told otherwise — changing this could affect crash
  reporting/proguard-rule needs that don't currently exist, `proguard-rules.pro` is the
  all-comments stock template, no active rules).
- `keytool -list -keystore android/birdy-upload.jks -alias birdy-upload` is the documented way
  to inspect the existing key's fingerprint (for verifying Play App Signing setup) — informational, no code dependency.

### 4.2 Version numbering

- **Local/manual builds**: `versionName`/`versionCode` are literal values in
  `android/app/build.gradle` (`"1.0.3"` / effectively `4` when `BIRDY_VERSION_CODE` is unset,
  since the Groovy expression is `(System.getenv('BIRDY_VERSION_CODE') ?: '4').toInteger()`
  — i.e. **the env var, if set to anything including an empty string that is falsy... note
  Groovy's `?:` (Elvis) treats `null` OR an empty-string env var as falsy**, so
  `BIRDY_VERSION_CODE=""` would also fall back to `'4'`). For a manual release, bump both by
  hand to an unused pair (`docs/STORE.md`: "Auch Entwurfs-Bundles verbrauchen einen
  Versionscode" — even discarded/draft bundle uploads permanently consume that versionCode on
  Play; never reuse one).
- **CI builds** (`play-release.yml`'s "Build signed bundle" step) compute `BIRDY_VERSION_CODE`
  fresh on every run, **ignoring** the `4` in `build.gradle` entirely:
  ```
  suffix = GITHUB_RUN_NUMBER * 10 + GITHUB_RUN_ATTEMPT      # fails the job if > 9999
  BIRDY_VERSION_CODE = floor(current_UTC_epoch_seconds / 86400) * 10000 + suffix
  ```
  i.e. `(days since Unix epoch) * 10000 + (run_number*10 + run_attempt)`, reserving exactly
  4 decimal digits of "same-day run slot" per calendar day (UTC), guaranteeing strictly
  increasing values across days and, within a day, across runs/retries — **as long as
  `run_number*10 + run_attempt` never exceeds 9999 in one UTC day** (the build step explicitly
  `exit 1`s with a message telling a human to "update the allocation scheme" if it does — this
  is a deliberate hard stop, not a bug). `versionName` is **not** changed by CI (stays whatever
  is committed in `build.gradle`, currently `1.0.3` — i.e. the human-readable version string is
  managed manually, only the machine-readable Play versionCode is CI-computed). See
  `docs/native/golden/platform-version-code.json` for exact worked examples including the
  overflow case. **Porting note**: replicate this formula exactly if the Kotlin project's own
  CI/release pipeline needs a Play versionCode scheme (or reuse the same GitHub Actions
  workflow structure against the new Gradle module paths) — do not invent a different scheme
  that could collide with already-uploaded Android (Capacitor) versionCodes on the **same**
  Play application listing, since a native rewrite is presumably still `de.robinrehbein.birdy`
  in the same Play Console app and versionCodes must stay strictly increasing across every
  build ever uploaded to it, JS-Capacitor or native alike.

### 4.3 CI signing setup (`scripts/configure_play_ci.py`)

Run before the Gradle build in CI. Requires five env vars (all secrets):
`UPLOAD_KEYSTORE_BASE64`, `UPLOAD_STORE_PASSWORD`, `UPLOAD_KEY_ALIAS`, `UPLOAD_KEY_PASSWORD`,
`PLAY_SERVICE_ACCOUNT_JSON` — hard-fails (`SystemExit`) listing every missing one if any are
absent. Writes:
- `android/birdy-upload.jks` — base64-decoded from `UPLOAD_KEYSTORE_BASE64` (strict decode,
  `validate=True` — malformed base64 raises), `chmod 0o600`.
- `android/keystore.properties` — the four properties in the exact format `build.gradle`
  expects (`storeFile=birdy-upload.jks\nstorePassword=...\nkeyAlias=...\nkeyPassword=...\n`),
  `chmod 0o600`. Each of `storePassword`/`keyAlias`/`keyPassword` is validated to contain no
  `\n`, `\r`, or `\\` (`property_value()`) — a defense against a secret value that would break
  Java `Properties` parsing or inject extra lines; raises `SystemExit` if violated.
- (`PLAY_SERVICE_ACCOUNT_JSON` itself is not written to a file by this script — it's read
  directly as an env var by `publish_play.py`, §4.5.)

### 4.4 The GitHub Actions workflow (`.github/workflows/play-release.yml`)

- Triggers: every push to `main`, or manual `workflow_dispatch`.
- **Gate**: the `publish` job only runs `if: vars.PLAY_PUBLISH_ENABLED == 'true' &&
  vars.BIRDY_MONETIZATION_RELEASE_READY == 'true'` — **both** repository variables (not
  secrets — plain GitHub Actions "variables", visible in Settings → Secrets and variables →
  Actions → Variables tab) must be the literal string `'true'`. A manual `workflow_dispatch`
  run does **not** bypass this gate (`docs/STORE.md` explicitly calls this out — the second
  variable stays off "bis zum Abschluss der Monetarisierungsprüfungen", i.e. until
  monetization review is complete, and per that doc `PLAY_PUBLISH_ENABLED` is already `true`
  while `BIRDY_MONETIZATION_RELEASE_READY` is still gating actual publishes as of the doc's
  stated date).
- `concurrency: group: play-release, cancel-in-progress: false` — overlapping runs queue
  rather than cancel each other (a publish must never be interrupted mid-flight).
- Steps: checkout → Node 22 (npm cache) → JDK 21 (Temurin, Gradle cache) → Python 3.12 →
  `npm ci` → `configure_play_ci.py` (signing setup) → build (`BIRDY_VERSION_CODE=...; npm run
  android:aab`, which runs `vite build && cap sync android && cd android && ./gradlew
  bundleRelease`) → `pip install google-auth requests` then `publish_play.py
  android/app/build/outputs/bundle/release/app-release.aab`.
- `PLAY_CLOSED_TRACK` (repo variable, default `alpha` if unset) names the closed test track's
  Play API track id.
- `timeout-minutes: 45`.

### 4.5 `publish_play.py` — the actual Play Developer API publish

Package name hardcoded: `de.robinrehbein.birdy`. Uses a service-account JSON
(`PLAY_SERVICE_ACCOUNT_JSON` env, parsed as JSON) scoped to
`https://www.googleapis.com/auth/androidpublisher`, via `google.oauth2.service_account` +
`AuthorizedSession`. Flow, all against the Android Publisher v3 "edits" API:
1. `POST .../edits` → get an `edit_id`.
2. Upload the `.aab` via the media-upload endpoint (`uploadType=media`,
   `Content-Type: application/octet-stream`) → response gives the assigned `versionCode`
   (Play's own assignment — should match the `BIRDY_VERSION_CODE` env var used to build it,
   since that's what's embedded in the AAB, but the script trusts **Play's** returned value as
   ground truth, not the env var, for everything downstream).
3. `GET .../edits/{id}/tracks` → the set of existing track names for this app. **Hard
   validation**: `PLAY_CLOSED_TRACK` must not be `internal`/`beta`/`production` and must not
   contain `:` (`raise SystemExit` otherwise) — i.e. it must genuinely name a **custom closed
   track** (Play's closed tracks can have colon-suffixed sub-track ids like `alpha:foo` for
   multiple closed tracks; this script only supports the single-segment default-alpha-style
   track). Then checks both `"internal"` and the closed track name actually exist in the
   fetched track list — `SystemExit` naming which is missing and listing what's available if
   not (**the script never creates tracks**, only publishes to pre-existing ones).
4. For **both** `internal` and the closed track: `PUT .../edits/{id}/tracks/{track}` with a
   release body:
   ```json
   {
     "name": "Birdy {versionCode}",
     "versionCodes": ["{versionCode}"],
     "status": "completed",
     "releaseNotes": [
       {"language": "de-DE", "text": "Neues Update mit Verbesserungen."},
       {"language": "en-US", "text": "New update with improvements."}
     ]
   }
   ```
   Release notes are **always this same generic bilingual string** — there is no per-release
   changelog authoring in this pipeline; a Kotlin-app rewrite of this script should preserve
   that unless told to add real changelogs.
5. `POST .../edits/{id}:commit` — atomically commits both track updates together (an edit is
   transactional across every track touched within it).
6. **`production` is never a target of this script under any configuration** — there is no
   code path, env var, or flag that can make it publish to production; this is a hardcoded
   safety property of the script (`targets = ("internal", closed_track)` — literally only two
   tuple elements, ever).

### 4.6 Play Console / store metadata (narrative facts from `docs/STORE.md`, current as of the doc's stated date 27/28 September 2026 — treat as a snapshot, re-check `docs/STORE.md` for anything current at port time)

- Package/app: `de.robinrehbein.birdy`, Play Console app numeric id `4976256029001868876`
  (visible only in the internal/closed-track URLs cited in the doc — not otherwise used in
  code; the Android Publisher API is addressed by package name, not this numeric id).
- Age target: 13+ (confirmed in Play Console); IARC rating expectation stated as USK 0 / PEGI 3
  in the monetization doc's IARC section (note: `docs/STORE.md`'s own earlier line states
  "USK 12, PEGI 7" as the **currently configured** rating at time of writing, while
  `docs/MONETIZATION.md`'s IARC section states the **expected outcome of re-answering the
  questionnaire** is USK 0/PEGI 3 — these are two different points in time/process, not a
  contradiction to resolve in code; a rating change happens in the Play Console questionnaire,
  never in the app).
- Privacy policy: `https://robinrehbein.github.io/birdy/privacy/` (DE+EN), served from
  `docs/privacy/index.html` via GitHub Pages — linked from the app's own menu since version
  1.0.2 (JS/UI concern, out of scope here beyond noting the URL is a fixed, hardcoded external
  link, not configurable at runtime).
- Data-safety declaration **must be re-submitted** before any monetized build ships — the
  previously-approved "no data collection" declaration becomes false the moment AdMob/UMP/Play
  Billing SDKs are active (they do collect data per their own privacy disclosures). This is a
  Play Console content task, not a code task, but a Kotlin/native rewrite that adds these same
  SDKs inherits the exact same obligation — **do not assume a "no data collection" claim can
  carry over to the native app.**
- Required secrets for a from-scratch CI setup are exactly the five listed in §4.3, plus the
  Play service account needing the Play Console permission "manage store presence"/release
  permissions for this specific app, and the associated Google Cloud project needing the
  **Google Play Developer API** enabled.

### 4.7 Migration: existing Capacitor players' local storage

All persisted Birdy state lives in the WebView's `localStorage`, under exactly these keys
(grepped across every `src/*.js` file for `localStorage.getItem`/`setItem` calls):

| Key | Written by | Value format | Meaning |
|---|---|---|---|
| `birdy-progress` | `progress.js:173` | JSON blob (see `progress.js:101-146`'s `load()` defaults for the full current shape) | Coins, best score, owned/equipped shop items, missions, achievements, gift streak, rewarded-ad quota/style-pass timer, paid-product/processed-purchase lists, tutorial flag, per-stat lifetime counters |
| `birdy-best` | (no longer written; only read) `progress.js:143` | plain integer string | **Legacy** pre-`birdy-progress` best-score key; still read once at load and merged in via `Math.max` — a fresh migration only needs to read `birdy-progress`, but if a device somehow has this key with a *higher* value than what's in `birdy-progress` (an old, never-upgraded save), the migration must replicate this same `max()` merge to not regress a returning player's best score |
| `birdy-muted` | `audio.js:323` | `'1'` (muted) or `'0'`/absent (unmuted) | Mute toggle |
| `birdy-lang` | `i18n.js:203` | `'de'` or `'en'` (see `i18n.js` for the exact set/detection logic — out of scope here) | UI language override |
| `birdy-quality` | `main.js:2015` | small integer string (index into a quality-tier array, `QUALITY_DPR`) | Render-quality tier picked in settings |
| `birdy-fps` | `main.js:2029` | `'1'` (on) or `''`/absent (off) | FPS counter overlay toggle (secret gesture: tap the title 5× per `docs/STORE.md`) |

**Origin**: this repo's `capacitor.config.json` sets no `server.hostname`/`server.androidScheme`
override, so Capacitor Android uses its documented **default origin `https://localhost`** for
the bundled WebView (Capacitor serves the `webDir` contents from that fixed local origin
regardless of device, with no real network request involved) — `localStorage` for the app is
therefore scoped to `https://localhost` inside the app's WebView data directory, **not** to any
public domain. **Verify this against the installed `@capacitor/android` version before relying
on it** (this spec's read of the file set did not include `node_modules`, since dependencies
were not installed in the reviewed checkout — if in doubt, the safest verification is to load
the existing Capacitor APK, open `chrome://inspect` against it, and read
`document.location.origin` directly, or grep the installed `@capacitor/android` AAR's
`Bridge`/`WebViewLocalServer` source for its default host constant).

**One-time migration procedure** (native app, first launch after upgrade-from-Capacitor, or
fresh install where a Capacitor install's data might still be present — the exact detection
condition is a product decision the native app must make, e.g. "this package's `versionCode`
history shows a prior Capacitor install" or simply "no native-storage migration-done flag is
set yet, and a WebView data directory for this app exists on disk"):
1. Load a hidden (never-attached-to-window, zero-size, or off-screen) `android.webkit.WebView`
   configured to the **same origin** the Capacitor app used (`https://localhost` per the above)
   — this only works because Android's WebView storage is scoped per-package, not per-App-instance,
   so the same app package's prior Capacitor WebView data (if the upgrade is an
   in-place Play Store update of the *same* `de.robinrehbein.birdy` package, keeping the same
   Android app-data directory) is still on disk and visible to a fresh WebView instance created
   at that origin within the new app's process. **This migration is only possible for an
   in-place update of the same package** — a side-by-side install or a switch to a new
   `applicationId` would have no Capacitor WebView data to read at all, and the native app
   must handle "no legacy data found" as the normal case for fresh installs.
2. Navigate/evaluate JS in that hidden WebView to read each of the six keys above via
   `localStorage.getItem(key)` (e.g. via `webView.evaluateJavascript("localStorage.getItem('birdy-progress')", callback)`
   for each key, or one script that returns all six as a single JSON object to minimize
   round-trips).
3. Parse `birdy-progress`'s JSON exactly per the shape/defaults in `progress.js:101-146` (a
   Kotlin data class mirroring that shape, with the same defaulting rules, e.g. `owned:
   ['sunny']` if absent) and import every field into the native `KeyValueStorage`/game-state
   store.
4. Apply the `birdy-best` vs `birdy-progress.best` `Math.max()` merge (see table above).
5. Import the five standalone keys (`birdy-muted`, `birdy-lang`, `birdy-quality`, `birdy-fps`,
   plus `birdy-best` only for the merge in step 4, not as its own persisted native key) into
   native equivalents of their respective settings.
6. Mark migration complete in native storage (a dedicated flag, e.g. `migratedFromWebView:
   true`) so this entire procedure never runs again, **before or atomically with** committing
   the imported data — if migration is interrupted (crash, kill) before the flag is set, it
   should be safe to simply run again (idempotent: importing the same source data twice and
   re-computing the same `max()` merge produces the same result, as long as the destination
   store's import is itself an overwrite of the same fields rather than an additive merge on
   top of partially-migrated native data — **do not just save `progress.coins += imported.coins`**;
   assign fields directly).
7. The hidden WebView can then be destroyed; it is never needed again post-migration (Birdy's
   native app has no other use for a WebView).

---

## 5. Cross-file constants quick-reference (for grep-ability)

| Constant | Value | Source |
|---|---|---|
| AdMob App ID | `ca-app-pub-1786159152036324~5284705514` | `AndroidManifest.xml:15` |
| AdMob test rewarded unit | `ca-app-pub-3940256099942544/5224354917` | `BirdyAdsPlugin.java:27` |
| AdMob live "coins" rewarded unit | `ca-app-pub-1786159152036324/7854280106` | `BirdyAdsPlugin.java:28` |
| AdMob live "pass" rewarded unit | `ca-app-pub-1786159152036324/8020434087` | `BirdyAdsPlugin.java:29` |
| Max rewarded ads/day | `3` | `progress.js:92` |
| Rewarded coin amount | `30` | `progress.js:93` |
| Style Pass duration | `3,600,000 ms` (1 hour) | `progress.js:94` |
| Coin-pack ids | `birdy_coins_500`, `birdy_coins_1500` | `billing.js:7` |
| Permanent-item id pattern | `birdy_{skin|world}_{catalogId}` | `billing.js:8-9`, `progress.js:230` |
| Purchase state "PURCHASED" | `1` (Play Billing constant) | `billing.js:37`, `BirdyBillingPlugin.java` (`Purchase.PurchaseState.PURCHASED`) |
| applicationId / Play package | `de.robinrehbein.birdy` | `build.gradle:16`, `publish_play.py:13` |
| compileSdk/targetSdk | `36` | `variables.gradle` |
| minSdk | `24` | `variables.gradle` |
| Default local versionCode | `4` | `build.gradle:19` |
| Current versionName | `1.0.3` | `build.gradle:20` |
| CI versionCode formula | `floor(epoch_s/86400)*10000 + runNumber*10+runAttempt` (fails if suffix > 9999) | `play-release.yml:46-53` |
| Splash screen background | `#5AA9E6` | `styles.xml` |
| Capacitor WebView container background | `#4ec0ca` | `capacitor.config.json` |
| Adaptive icon background color resource | `#6F7FC6` | `res/values/ic_launcher_background.xml` |
| Adaptive icon background vector fill | `#26A69A` (stock template, likely superseded — see §3.4) | `res/drawable/ic_launcher_background.xml` |
| Default Capacitor Android WebView origin | `https://localhost` (unverified against installed package version — see §4.7) | Capacitor framework default, no `server.*` override in `capacitor.config.json` |
| localStorage keys | `birdy-progress`, `birdy-best`, `birdy-muted`, `birdy-lang`, `birdy-quality`, `birdy-fps` | grep across `src/*.js`, table in §4.7 |

## UPDATE after merging main (#6–#8)

- Play Billing item IDs now exclude rare skins: `CATALOG[kind].filter(item => item.price > 0 && !item.rare)` (src/billing.js:9). The 7 rare animated skins are NOT sold for real money. Golden: docs/native/golden/platform-product-ids.json (regenerated).
- Style pass no longer covers rare skins (see meta.md `owns`).
- i18n: `stylePassAd` / `stylePassActive` now start with `[play]` / `[sparkle]` rich icon tokens instead of emoji, rendered via setRich; `stylePassGranted` mentions "(außer seltene)" / "(except rare ones)". Golden: meta-i18n.json (regenerated).
- Store claim: ads are optional, not "no ads" (docs/STORE.md, scripts/store-shots.mjs).
