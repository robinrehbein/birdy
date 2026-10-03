# main.js — Part A (lines 1–1100): setup, menus, game state, gate/coin generation, input

Source: `/home/user/birdy-native/src/main.js`, lines 1–1100 of 2129, as of this
writing. All line numbers below refer to that file. Part B (lines 1100–2129:
the per-frame update loop, physics integration, collision detection, scoring,
camera follow, and cleanup) is a separate spec (`main-b.md`); this document
does not re-derive that logic, but calls out every hand-off point.

This is a behaviour spec for a Kotlin/KMP port. It is meant to be sufficient
without reading the JS. Every constant is listed with its exact value and
unit; every formula is written out; every piece of `Math.random()` use is
flagged (JS does not expose a seedable RNG, so the game itself never seeds
one — ports do not need bit-exact random sequences, only the same
*distributions and constraints*, see "Porting notes" at the end).

## 0. Module dependencies (imports, lines 1–26)

`main.js` is the composition root. It imports and wires together:

| Module | Used for (relevant to lines 1–1100) |
|---|---|
| `three` (THREE) | Renderer, scene graph, math (`Vector3`, `MathUtils`, `Color`), materials/geometries for the blob shadow and height marker, thumbnail rendering. |
| `bird.js` → `createBird()` | The bird mesh/rig; `setSkin`, `setLook`, `animateWings`. |
| `audio.js` → `sfx`, `music`, `audio`, `renderMusic` | Procedural WebAudio SFX/music. `audio.unlock()`, `audio.muted`, `audio.setSuspended(bool)`. |
| `effects.js` | Particle bursts, speed lines, hit impact flash/shake, invincibility aura. |
| `powerups.js` → `POWERUPS`, `POWERUP_TYPES`, `createPowerupPickup`, `animatePickup` | Power-up catalogue and pickup meshes (see §1.2). |
| `progress.js` → `progress`, `ACHIEVEMENTS` | Persistent save data: coins, best score, owned items, equipped items, upgrade levels, missions, achievements, daily gift, ad/IAP entitlements. Treated as an injected `Storage`/`ProgressRepository` port; its own spec covers the persisted schema and localStorage keys. |
| `catalog.js` → `CATALOG`, `KINDS`, `UPGRADES`, `UPGRADE_MAX` | Static cosmetic/world/pipe/upgrade catalogue data. |
| `biomes.js` → `BIOMES`, `createBiomeBlender` | World/biome definitions and the scene's biome cross-fade controller. |
| `ads.js` → `ads` | AdMob rewarded-ad + UMP consent wrapper. |
| `billing.js` → `billing` | Play Billing wrapper. |
| `i18n.js` → `t`, `L`, `applyI18n`, `getLang`, `setLang` | DE/EN string lookup (`t(key, params)`), localized-field lookup (`L({de,en})`), and DOM `data-i18n` application. |
| `icons.js` → `ICONS`, `icon`, `iconSvg`, `rich`, `setRich` | Inline SVG icon set; `rich`/`setRich` interpolate icons into translated strings (e.g. a coin glyph inside "+50"). |
| `skinfx.js` → `tickSkinFx` | Per-frame skin particle effects (used in part B). |
| `world.js` | `LANES`, `PIPE_RADIUS`, and scene/gate/coin-field factories — see §0.1. |

### 0.1 `world.js` constants re-exported and used throughout main.js

```
LANES = [-3, 0, 3]        // world X position of the 3 lanes (left, middle, right), in world units
PIPE_RADIUS = 1.1         // pipe cylinder radius, world units; used as a z-offset for the height marker
```
(`world.js` also exports `PIPE_TOP = 40` and `GROUND_TILE = 10`, not used directly in lines 1–1100.)

The gate object returned by `createGate(scene)` (defined in `world.js`,
lines 876–958+) is a dependency of this spec's §4 (gate spawning) even though
its own rendering logic belongs to the `world.js` spec. Its per-lane shape,
which `main.js` reads and writes, is:

```
lane = {
  x,                          // LANES[i], fixed
  blocked: bool,               // no gap in this lane this row
  center: number, size: number,// gap center-Y and gap height, world units
  amp: number, speed: number, phase: number, // moving-gap sine params (0 if not moving)
  hasPlant: bool, plantOffset: number,       // cactus/plant obstacle
  pulse: bool,                               // "breathing" gap (Herbstwald zone)
  gapLow, gapHigh: number,     // = center ± size/2 (visual pipe cut)
  hitLow, hitHigh: number,     // = gapLow/gapHigh at configure() time — the values collision
                                // in part B actually tests against (NOT re-derived every
                                // frame for moving/pulsing gaps: the hitbox is fixed at
                                // spawn time even though the pipe visually moves — see
                                // Porting notes, "hitbox is frozen at spawn").
}
```
`gate.configure(z, spec)` (world.js:925) takes the `spec` array produced by
`gateSpec()` (§4.1 below): `spec[i]` is `null` (blocked lane) or
`{ center, size, amp?, speed?, phase?, plant?, plantOffset?, pulse? }`.

## 1. Tuning constants (lines 28–53)

All of these are top-level `const` — global, immutable for the process
lifetime (no difficulty/settings ever change them; only shop *upgrades*,
applied via the derived functions in §1.1, modify effective values).

| Constant | Value | Unit | Meaning |
|---|---|---|---|
| `GRAVITY` | `36` | units/s² | Downward acceleration applied to the bird every frame. |
| `FLAP_VELOCITY` | `11.5` | units/s | Vertical velocity set on tap/flap (replaces `vy`, does not add). |
| `SWITCH_HOP` | `6` | units/s | Vertical velocity applied when tapping a **different** lane than the current one (a small hop, not a full flap — see part B for where this is applied). |
| `WARMUP_GATES` | `6` | gates (rows) | Number of initial rows treated as "warm-up": extra-wide gaps, near start height, no lane blocking. |
| `MAX_FALL` | `-22` | units/s | Terminal (clamped) downward velocity. |
| `CEILING` | `14` | world Y | Maximum bird height / upper bound used when sizing gate placement range. |
| `BIRD_RADIUS` | `0.5` | world units | Collision radius, normal size. |
| `MINI_RADIUS` | `0.3` | world units | Collision radius while the "mini" power-up is active. |
| `BIRD_SCALE` | `1.1` | scale factor | Visual scale of the bird mesh, normal size. |
| `MINI_SCALE` | `0.6` | scale factor | Visual scale while "mini" is active. |
| `LANE_SWITCH_SPEED` | `18` | units/s (approach rate) | Lane-crossing horizontal speed; comment: "~0.17 s to reach 95% of a lane change" (exponential/critically-damped approach, applied in part B). |
| `SPAWN_DISTANCE` | `170` | world units (−Z ahead of bird) | How far ahead of the bird gates/rows are kept pre-spawned. |
| `FIRST_GATE_Z` | `-60` | world Z | Z position of the very first gate at run start (before adding one `spacing()`). |
| `COIN_RADIUS` | `1.1` | world units | Coin pickup radius (used in part B collision code). |
| `MAGNET_RANGE` | `9` | world units | Base coin-magnet attraction radius before upgrades. |
| `UPGRADE_BONUS` | `{ star: 1.5, magnet: 3, mini: 3 }` | seconds / units per level | Per-shop-level bonus added by `powerDuration`/`magnetRange` (§1.1). Matches the upgrade descriptions in `catalog.js` UPGRADES (`+1.5s per level` star, `+3s and wider reach per level` magnet, `+3s per level` mini). |
| `STAR_SPEED_BOOST` | `1.35` | multiplier | Speed multiplier while the star (rainbow/invincibility) power-up is active (applied in part B). |
| `GRACE_TIME` | `1.2` | seconds | Invulnerable + blinking window right after the star power-up ends. |
| `FALLBACK_BPM` | `124` | BPM | Used if the active music track exposes no BPM (part B beat-tracking). |
| `ZONE_ROWS` | `10` | rows | A new "zone" (place + time of day) begins every 10 spawned gate-rows, introduced by a coin-rush interlude. |
| `NEAR_MISS` | `0.45` | world units | Vertical clearance below which a passed gap counts as a "Knapp!" (near-miss) event (part B). |
| `HIT_STOP` | `0.14` | seconds | Freeze-frame duration on impact (part B). |

### 1.1 Derived tuning functions (lines 44–48)

```
powerDuration(type) = POWERUPS[type].duration + UPGRADE_BONUS[type] * progress.level(type)
magnetRange()       = MAGNET_RANGE + 2 * progress.level('magnet')
```
`POWERUPS[type].duration` (from `powerups.js`) is the **base** duration in
seconds before any shop upgrade:
```
POWERUPS = {
  star:   { duration: 6, color: 0xffd400, icon: 'rainbow' },
  magnet: { duration: 9, color: 0xe53935, icon: 'magnet'  },
  mini:   { duration: 9, color: 0x9b59b6, icon: 'mushroom'},
}
POWERUP_TYPES = ['star', 'magnet', 'mini']   // Object.keys order
```
`progress.level(type)` is an integer in `[0, UPGRADE_MAX]` = `[0, 3]` (from
`catalog.js UPGRADE_MAX = 3`), read from persisted save data.

Golden fixture: `docs/native/golden/main-a-powerups.json` (generated by
`scripts/native-golden/main-a-powerups.mjs`) tabulates `powerDuration` for
all 3 types × levels 0–3, and `magnetRange` for levels 0–3. Sample: level 0
→ star 6.0s / magnet 9.0s / mini 9.0s / magnetRange 9.0; level 3 → star
10.5s / magnet 18.0s / mini 18.0s / magnetRange 15.0.

Note: `UPGRADES` also defines a 4th upgrade, `luck`, which has no
`UPGRADE_BONUS` entry — it does not change duration/range, it changes the
`gatesToPower` countdown (§4.3) and the `-progress.level('luck')` offsets in
`resetGame()`/`spawnGate()`.

### 1.2 Haptics (lines 55–59)

```js
function buzz(ms) {
  if (audio.muted) return;
  try { navigator.vibrate?.(ms); } catch {}
}
```
- No-ops if the user muted audio (`audio.muted` gates haptics too — there is
  no separate haptics toggle).
- Calls the Web Vibration API with a single duration in milliseconds.
- Swallows all errors (unsupported browsers/platforms).
- **Not called anywhere in lines 1–1100** — its call sites are in part B
  (flap/impact feedback). Documented here because it's defined here.
- Porting: Android `Vibrator`/`VibratorManager` `.vibrate(VibrationEffect.createOneShot(ms, DEFAULT_AMPLITUDE))`, gated the same way on a mute/haptics flag, behind the `Haptics` platform interface.

## 2. Renderer / scene / engine object setup (lines 61–154)

Executed once at module load (before any menu is shown).

### 2.1 Renderer and adaptive quality (lines 62–75)

```
renderer = new THREE.WebGLRenderer({ canvas: #game, antialias: true })
QUALITY_DPR = [min(devicePixelRatio, 2), 1.5, 1.25, 1]
quality = clamp(int(localStorage['birdy-quality']) || 0, 0, QUALITY_DPR.length)   // read, try/catch swallowed
renderer.setPixelRatio( min(QUALITY_DPR[0], QUALITY_DPR[min(quality, 3)]) )
renderer.shadowMap.enabled = true
renderer.shadowMap.type = THREE.PCFShadowMap
if (quality >= 4) renderer.shadowMap.enabled = false     // quality level 4 = shadows off entirely
```
- `quality` is a **persisted, self-adjusting** step index (0 = full
  resolution + shadows, 1..3 = reduced pixel ratio 1.5/1.25/1, 4 = pixel
  ratio 1 **and** shadows disabled). The code that *raises* `quality` when
  frame time is bad lives in part B; this block only reads the stored value
  at boot and applies it.
- Reading `localStorage.getItem('birdy-quality')` is wrapped in try/catch —
  must not throw if storage is unavailable (private browsing etc.); default
  to `0`.
- `setShadows(on)` (lines 107–110): toggles `renderer.shadowMap.enabled` and
  forces `needsUpdate = true` on every material in the scene graph (a full
  material recompile) — this is the same "disable shadows" path, callable
  at runtime, not just at boot.
- Porting: `quality` → an Int in the KV-storage port, key literally
  `"birdy-quality"` for migration purposes (§ Porting notes / migration).
  On Android there is no adaptive "pixel ratio" the same way; the
  equivalent is rendering the GL surface at a fraction of the physical
  size (an off-screen FBO scaled up, or `SurfaceView` buffer size) plus a
  shadow-pass on/off switch.

### 2.2 Scene graph objects created at boot (lines 77–154)

In order:
1. `scene = createScene()` (world.js) — sky dome, fog, background color.
2. `camera = new THREE.PerspectiveCamera(60, aspect=1, near=0.1, far=400)` — **FOV 60°**, aspect is set later on resize (part B), near/far planes 0.1/400.
3. `ground`, `scenery`, `clouds` (world.js factories).
4. `particles`, `speedLines`, `impact`, `aura` (effects.js factories; `impact`/`aura` are given the camera).
5. `biomes = createBiomeBlender({ scene, ground, scenery, clouds })`.
6. `zoneMarks = []` — mutable array of `{ z, zone }`, one entry per zone transition ahead of the bird, consumed/rendered in part B and cleared on reset.
7. `zoneBiome(zone) = (zone % BIOMES.length === 0) ? progress.equipped('world') : BIOMES[zone % BIOMES.length]`
   — **every 4th zone** (if `BIOMES.length === 4`; verify against `biomes.js`) is the world the player equipped in the shop; the other zones cycle through the fixed `BIOMES` list by `zone % BIOMES.length`.
8. `biomes.set(0, 0, zoneBiome(0))` — instantiate zone 0 immediately, no cross-fade (`0` transition time).
9. `pipeFor(world, pipe)`: if the equipped pipe is the **first** catalogue pipe (`CATALOG.pipe[0].id`, the "classic" default) *and* the world defines its own `pipes` colour override, merge `{ ...pipe, ...world.pipes }` — i.e. the default pipe skin is world-themed, but any purchased pipe skin always overrides the world's colours.
10. `equippedPipes() = pipeFor(progress.equipped('world'), progress.equipped('pipe'))`, applied via `setPipeStyle(...)`.
11. **Blob shadow** (lines 100–106): a flat circle directly under the bird.
    - Geometry: `CircleGeometry(radius=0.7, segments=20)`.
    - Material: `MeshBasicMaterial({ color: 0x000000, transparent: true, opacity: 0.22, depthWrite: false })`.
    - Rotated flat (`rotation.x = -π/2`), `position.y = 0.03` (just above ground to avoid z-fighting).
    - Comment: "The bird itself casts no sun shadow (that one fell into the next lane)" — i.e. `bird.group` has `castShadow = false` on every child (line 141) and this blob is the only "shadow" feedback, always directly below the bird in X/Z (its X/Z are updated in part B to track the bird).
12. **Height marker ring** (lines 112–138): a ring shown at the *next* gate's height in the bird's current lane, colored green/red to telegraph whether the bird would pass.
    - Colors: `MARKER_OK = 0x8cff5a` (green), `MARKER_BAD = 0xff4a3d` (red).
    - Geometry: `RingGeometry(innerRadius=0.2, outerRadius=0.36, segments=20)`.
    - Material: `MeshBasicMaterial({ color: MARKER_OK, transparent: true, opacity: 0, depthTest: false, depthWrite: false })` — depth test disabled so it always draws on top; `renderOrder = 10`.
    - `updateMarker(next, r)` (lines 125–138), called every frame from part B with `next` = the next not-yet-passed gate and `r` = current bird radius:
      - `z = next ? next.group.position.z : -999`.
      - **Visible** only if: `next` exists AND `state.mode === 'playing'` AND `state.power.star <= 0` (star/invincibility hides it — no need to telegraph danger) AND `z > -48` (only within 48 units ahead).
      - If not visible, bail out (opacity/position untouched, but `visible=false` already set).
      - `lane = next.lanes[state.lane]` (the lane object at the bird's current lane index).
      - `ok = !lane.blocked && (state.y - r*0.8 > lane.hitLow) && (state.y + r*0.8 < lane.hitHigh)` — note the **0.8 shrink factor** on the radius used only for this preview (not the real collision radius; real collision in part B may differ slightly — flag for cross-check when porting part B).
      - Color set to `MARKER_OK` or `MARKER_BAD` accordingly.
      - `opacity = 0.9 * clamp((z + 48) / 16, 0, 1)` — fades in over the last 16 units before the 48-unit cutoff (fully invisible at z=-48, fully 0.9 opacity by z=-32 and beyond, clamped).
      - Position: `x = LANES[state.lane]`, `y = state.y` (bird's current height, not the gap center), `z = z + PIPE_RADIUS + 0.3` (in front of the pipe face, offset by pipe radius + 0.3).
      - Scale: `max(1, -z/14) * pulse`, where `pulse = tut.active ? 1.6 + 0.25*sin(state.time*8) : 1` — grows as the row gets closer (`-z` shrinks below 14 → clamped to a minimum scale of 1; distant gates scale down... actually `-z/14` grows as `z` becomes more negative, i.e. the marker is scaled *up* the farther away the row is, floor of 1×). During the tutorial it also pulses (breathing scale, ±0.25 around 1.6, at 8 rad/s ≈ 1.27 Hz).
      - `marker.quaternion.copy(camera.quaternion)` — always billboarded to face the camera.
13. **Bird**: `bird = createBird()`; every child's `castShadow = false`; `bird.group.scale.setScalar(BIRD_SCALE)`; `bird.setSkin(progress.skin)`; added to scene.
14. **Gate pool**: `gates = Array.from({length: 12}, () => createGate(scene))`, each starting `group.visible = false, active = false`. A fixed-size object pool of **12** gates — the spawn logic (§4) must never need more than 12 simultaneously live gates given `SPAWN_DISTANCE=170` and typical spacing; if it ever does, `spawnGate` silently no-ops (`gates.find(g => !g.active)` returns undefined and the function returns early, line 1000) — **this is a silent failure mode to preserve**, not an error.
15. **Coin field**: `coinField = createCoinField(scene, 60)` → pool of 60 coin objects (`coins = coinField.coins`).
16. **Pickup pool**: `pickups = POWERUP_TYPES.flatMap(type => [createPowerupPickup(scene,type), createPowerupPickup(scene,type)])` — **exactly 2 pooled pickup instances per power-up type** (6 total), each tagged with its `type`.

## 3. DOM / UI wiring (lines 156–700)

This section is HTML/CSS/DOM-specific and will be re-expressed as Compose
Multiplatform state/composables on the native side; only the **behavioural
contract** (state transitions, formulas, i18n keys, conditions) is
normative here, not the DOM structure.

### 3.1 HUD elements referenced (line 156–189)
`#app`, `#hud`, `#score`, `#coin-count`, `#coins`, `#start`, `#gameover`,
`#pause`, `#zones`, `#lanes span` (3 lane-indicator dots), `#mute`.
Two CSS custom properties `--ic-check` / `--ic-lock` are set to data-URI SVGs
(for `::before` badge icons in CSS) — a native UI has no CSS pseudo-content
equivalent; treat as ordinary icon views.

A `#flash` div is created and appended to `#app` (full-screen flash overlay,
driven from part B on impact/star pickup, etc.).

`landscapeTouch = matchMedia('(orientation: landscape) and (pointer: coarse)')`
— used elsewhere (not in lines 1–1100) to show a "rotate your phone" hint and
pause; native: `Configuration.orientation` + touch-input-always-true.

Power-up HUD chips (lines 180–189): one DOM chip per `POWERUP_TYPES` entry,
each with an icon (`POWERUPS[type].icon`) and a fill bar (`<i>`), stored in
`powerChips[type] = { chip, fill }`. Driven in part B from `state.power[type]`
as a countdown-fraction fill.

### 3.2 Start / shop / achievements menu logic (lines 191–680)

State machine for `state.menu` (only meaningful while `state.mode === 'ready'`):
`'start' | 'shop' | 'achievements'`.

`renderStart()` (lines 270–287):
- `#best-start` = `progress.best`.
- `firstRuns = progress.runs < 2` → shows `#howto` (control explanation) and
  hides the missions panel, or vice versa.
- Missions panel: `<h3>t('missions')</h3>` + one `missionHTML(m)` per
  `progress.missions()` entry.
- `missionHTML(m, isNew=false)`: renders `L(m.text)`, `+${m.reward}`, and a
  progress bar `width: round(m.progress/m.goal*100)%`; adds CSS class `done`
  if `m.done`, `new` if `isNew` (the `isNew` flag is only ever passed `true`
  from call sites outside lines 1–1100).
- Daily gift button: shown only if **not** `firstRuns` AND
  `progress.giftAvailable()`; its label is
  `t('gift', { n: progress.giftAmount(progress.streak + 1) })` (via `rich`,
  i.e. it can embed an icon).
- Streak line: shown when gift is *not* available and `progress.streak > 0`
  (mutually exclusive display with the gift button); text
  `t('streak', { d: progress.streak, n: progress.giftAmount(progress.streak+1) })`.
- Always refreshes the wallet display (`renderWallet()`, no bump).

`renderWallet(bump=false)` (lines 254–261): sets wallet count text to
`progress.coins`; if `bump`, re-triggers a CSS bump animation via a
class-remove/reflow/class-add trick (`void el.offsetWidth`) — native
equivalent: replay a scale/pulse animation.

**Shop** (`SHOP_TABS`, line 292–302): 9 tabs in fixed order —
`skin, pattern, hat, eyes, beak, trail, world, pipe, upgrade`, plus a
"dice" random-outfit button. `LOOK_KINDS = ['pattern','hat','eyes','beak']`
(the 4 kinds that compose the bird's "look", separate from `skin` = base
color/material).

`defaultSel()`: when opening a tab, the initially-selected tile is
`UPGRADES[0].id` for the `upgrade` tab, else `progress.equipped(shopTab).id`.

Tile thumbnails for the 4 `LOOK_KINDS` (lines 322–360, `thumbUrl`): a
**second, offscreen `THREE.WebGLRenderer`** (112×112, alpha, `preserveDrawingBuffer:true`)
renders a small bird posed per `THUMB_VIEW[kind]` (fixed camera
position/lookAt/fov per kind — see table below) wearing *only* that one part
non-default (all other look-parts reset to their defaults: `pattern:'plain',
hat:'none', eyes:'normal', beak:'round'`) and the player's currently equipped
skin colour, then reads back `renderer.domElement.toDataURL('image/png')` and
memoizes by `` `${kind}:${id}:${progress.skin.id}` ``. This is pure
CPU/GPU-image caching, not gameplay logic; a native port renders shop tiles
with its own thumbnail pipeline (e.g. render-to-texture/`ImageBitmap`, or
precomputed thumbnails), keyed the same way (kind, item id, current skin id).

```
THUMB_VIEW = {
  pattern: { cam:[1.9,1.4,2.4],  look:[0,0.05,0.1],   fov:30 },
  hat:     { cam:[1.5,1.4,-2.3], look:[0,0.62,-0.12], fov:24 },
  eyes:    { cam:[1.1,0.5,-2.5], look:[0.1,0.24,-0.4],fov:22 },
  beak:    { cam:[2.3,0.4,-1.7], look:[0,-0.02,-0.75],fov:26 },
}
```

`tileBg(kind, item)` / `tileInner(kind, item)` (lines 362–379): compute a CSS
background and inner icon per catalogue kind —
- `skin`: solid color `item.swatch || hex(item.body)`.
- `trail`: if `item.colors` is empty → flat tan (`#cbb968`) with a close-icon
  (i.e. "no trail" tile); else 6 fixed-position radial-gradient dots cycling
  through `item.colors`, over a sky-blue (`#6fb8e6`) backdrop. Dot positions
  (percent of tile): `[25,30],[55,22],[75,50],[40,60],[62,78],[22,70]`, each
  dot solid-colored to 11% radius then transparent from 12%.
- the 4 look kinds: the cached thumbnail PNG, backdrop `#bfe6f5`.
- `world`: 3-band vertical gradient `top → horizon (55%) → grass (56%)` from
  the world's own colors.
- `pipe`: a 5-band horizontal gradient approximating a pipe cross-section:
  `pipe(0–20%), light(20–36%), pipe(36–66%), dark(66–80%), pipe(80–100%)`.
- everything else (upgrades use a separate renderer, see below): flat cream
  `#fff6d5`.
- `tileInner`: trail with no colors, or the "hat: none" default tile, shows a
  close/× icon; otherwise the catalogue item's own `icon` glyph (or nothing
  for look-kinds, since the thumbnail already shows it).

`renderUpgrades()` (lines 382–412): the `upgrade` tab lists `UPGRADES` as
tiles showing pip indicators (`UPGRADE_MAX = 3` pips, filled up to
`progress.level(u.id)`). Selected item's action button:
- if `progress.upgradePrice(id)` is `null` (already at max level): label
  `t('maxed')`, disabled.
- else: label is `t('needMore', {n: price-coins})` if short of coins, else
  `t('upgrade', {n: price})` (with an inline coin icon), `disabled = coins <
  price`.
- Also refreshes the live 3D preview (bird skin/look, world, pipes) to the
  **currently equipped** state (upgrades don't have a "try-on" preview since
  they're not cosmetic).

`renderShop()` (lines 414–489) — the general (non-upgrade) tab renderer:
- Refreshes ad/style-pass UI (`updateAdsUi()`, §3.3).
- Real-money coin packs: offers `birdy_coins_500` and `birdy_coins_1500` IAP
  SKUs, filtered to only those `billing.price(id)` resolves (i.e. store
  catalogue must have returned a localized price) — button text
  `t('coinPack', {n: amount, price})`.
  - **500 and 1500 are the only coin-pack sizes** in lines 1–1100.
- The "dice" random-outfit tab button is `disabled` unless the player owns
  ≥2 items in **any** of `skin` + the 4 look kinds + `trail` (i.e. there must
  be something to actually randomize).
- Tile grid: one button per `CATALOG[kind]` entry; CSS classes `locked` (not
  owned), `sel` (currently highlighted in the grid), `equipped`, `rare`.
  Price badge shown only if not owned; a "rare" tag (`t('rare')`) if
  `item.rare`.
- **Real-money buy button** (`realBuyBtn`): shown only for `skin`/`world`
  tabs, only for items with `price > 0` that the player does not
  **permanently** own (a style-pass rental doesn't count), and only if
  `billing.price(billing.itemId(kind, item.id))` resolves to a real IAP SKU
  price — i.e. not every cosmetic has a real-money equivalent.
- Rare items (`item.rare`): description text is either "already owned/no
  matching achievement" (`t('rareOwned')`) or "unlock via achievement X"
  (`t('rareOr', {text: L(achievement.text)})`), found by
  `ACHIEVEMENTS.find(a => a.skin === item.id)`.
- Action button: `t('select')` if equipped item already this one and owned
  → disabled `t('selected')`; if not owned → buy button
  `t('needMore'|'buy', {n})`, disabled if short of coins; if owned but not
  equipped → `t('select')`.
- Live 3D preview: sets the bird's skin/look, world backdrop, and pipe style
  to reflect **hovering the currently-selected tile**, not the equipped
  item (except where they're the same) — this lets the player "try on"
  anything before buying/equipping. `pipePreview.group.visible` only true on
  the `pipe` tab.

`previewWorld(world)` (line 492–496): no-ops if already showing that biome
(`biomes.current === world`); else `biomes.set(0, 0.5, world)` (0.5s
cross-fade) and `scenery.setTheme(world.scenery, true)`.

**Surprise purchase** ("mystery box", lines 498–526):
```
SURPRISE_PRICE = 150      // coins
SURPRISE_MAX   = 900      // an item must cost <= this to be surprise-eligible
SURPRISE_KINDS = ['skin','pattern','hat','eyes','beak','trail','pipe']   // NOT world, NOT upgrade
```
`surprisePool()`: flattens all catalogue items across `SURPRISE_KINDS` with
`0 < price <= SURPRISE_MAX` that the player does not yet own. Button hidden
if the pool is empty; label `t('surprise',{n:150})` optionally suffixed with
`t('needMore', {n: 150-coins})`; `disabled` if short of coins.
On click: pick a uniformly random `{kind,item}` from the pool
(`Math.floor(Math.random() * pool.length)`), call
`progress.buySurprise(150)` (deduct coins) then `progress.grant(kind,
item.id)` (unconditionally own it, no price charged again), switch the shop
to that tab/item, play `sfx.powerup()`, emit a 40-particle confetti burst
(colors `[0xff5a8a,0x5ad1ff,0xffd84a,0x7be07b,0xffffff]`, speed 7, size
0.13, life 0.9s, gravity -4), toast `t('surpriseGot', {name})`, and after
400ms check for newly-completed achievements (`celebrateMenuAchievements`).

**Dice (random outfit)** (lines 566–577): for each of
`['skin', ...LOOK_KINDS, 'trail']`, pick a uniformly random **owned** item
and `progress.select(kind, id)` it. Plays `sfx.powerup()`, a 20-particle
burst in the new skin color + white/pale-yellow, speed 5, size 0.1, life
0.6s, gravity -3.

**Buy/select/upgrade action button click** (lines 590–610): for the
`upgrade` tab, `progress.buyUpgrade(shopSel)`; else, if already owned,
`progress.select(kind, id)` (equip); else `progress.buy(kind, id)` (deduct
coins, grant). On any successful purchase: check achievements after 400ms,
play `sfx.powerup()`, particle burst — colors are `[item.body]` for `skin`,
else `item.colors` (falling back to the equipped skin's body color if the
item has no `colors` array), plus white and pale yellow; speed 6, size
0.12, life 0.8s, gravity -4.

**Real purchase flow** (`startRealPurchase`, lines 611–631): guarded by a
single in-flight `billingBusy` boolean (re-entrant clicks ignored while a
purchase is pending); calls `billing.purchase(id)`; on any thrown error,
toast `t('purchaseUnavailable')`; always re-renders the shop in a `finally`.

**Achievements screen** (`renderAchievements`, lines 636–648): lists
`progress.achievements()`; header `"${doneCount} / ${total}"`; each row
shows a lock icon until done, then its real icon; a progress bar
`value/goal*100%`; reward text is either a check icon (`done`) or `+reward`;
rows for rare/skin-granting achievements get an extra `<em>` line
`t('skinReward', {name: L(skin.name)})`.

**Menu-triggered achievement payout** (`celebrateMenuAchievements`, lines
662–668): iterates `progress.checkAchievements()` (server/save-side: returns
newly-completed achievements and pays their reward into `progress.coins`),
toasts each as `` `[trophy] ${name} +${reward}` `` (the literal
`[trophy]` bracket-tag string — likely meant to be replaced by an icon via
`rich`/`setRich` on the toast render path; **verify against the actual
toast rendering in part B / effects — if `toast()` never runs its argument
through `rich`, this is a copy bug to preserve or fix consciously, flag for
product decision**), plays `sfx.powerup()`, bumps the wallet.

**Daily gift** (lines 669–679): on click, `progress.claimGift()`; if it
returns falsy, abort (already claimed). Else: check achievements 600ms
later, `sfx.powerup()` once, `sfx.coin()` 5× at 120ms + i×70ms (i=0..4,
i.e. a coin-jingle roll at ~14 Hz with a fixed onset), a 36-particle burst
(`[0xfff176,0xffd400,0xffffff]`, speed 7, size 0.13, life 0.9s, gravity -5),
re-render the start screen and bump the wallet.

### 3.3 Ads/billing UI glue (lines 208–252)

```js
function updateAdsUi() {
  rewardAdBtn.hidden = !ads.available || progress.rewardedAdsLeft === 0
  rewardAdBtn.text = t('rewardAd', { n: progress.rewardedAdsLeft })
  passMinutes = progress.stylePassMinutesLeft
  stylePassBtn.hidden = !passMinutes && (!ads.passAvailable || progress.rewardedAdsLeft === 0)
  stylePassBtn.disabled = passMinutes > 0
  setRich(stylePassBtn, passMinutes ? t('stylePassActive',{n:passMinutes}) : t('stylePassAd',{n:progress.rewardedAdsLeft}))  // UPDATE main #6: rich text, strings now start with [play]/[sparkle] icon tokens
  adPrivacyBtn.hidden = !ads.privacyOptionsRequired
}
```
- Rewarded-coins button click: guarded on `ads.available`, remaining daily
  quota (`rewardedAdsLeft !== 0`), and not already mid-flight
  (`rewardAdBtn.disabled`). Disables itself, `await ads.showRewarded()`; if
  it resolved "earned" **and** `progress.grantRewardedCoins()` succeeds
  (double-checks the daily cap server-side), toast `t('rewardGranted')` and
  refresh shop+wallet(bump); if not earned, toast
  `t('rewardUnavailable')`. Always re-enables the button and refreshes ad UI
  afterward.
- Style-pass button: same shape but `ads.showRewarded('pass')` and
  `progress.grantStylePass()`; guarded also on `stylePassMinutesLeft === 0`
  (can't stack passes).
- Privacy-options button: `ads.showPrivacyOptions()` (UMP consent form),
  unconditional.
- `ads.init(updateAdsUi)` at boot — the ads module calls this back when its
  availability state changes.
- `setInterval(60_000, () => if (shop visible) renderShop())` — while the
  shop is open, silently re-render every 60s (keeps the reward-ad countdown
  and any timed style-pass display fresh even if the player idles on the
  shop screen).

### 3.4 Toast queue (lines 683–699)

A simple **FIFO queue**, one toast visible at a time:
```
toastQueue: string[] = []
toast(text) { toastQueue.push(text) }
updateToast(dt) {           // called every frame from part B
  if (toastTimer > 0) { toastTimer -= dt; if (<=0) hide; return }
  if (queue not empty && timer <= 0) { show queue.shift() via setRich(); timer = 2.2 }
}
```
- Each toast is visible for exactly **2.2 seconds**, then immediately the
  next queued one begins its own 2.2s window (no gap, no fade time
  accounted for beyond CSS transition).
- Toast text is rendered through `setRich` (icon interpolation).

## 4. Game state (lines 701–741)

The canonical mutable game state, a single object (`state`). Field, initial
value at module load (**not** the same as `resetGame()`'s per-run reset —
see §5), and meaning:

```
state = {
  mode: 'ready',        // 'ready' | 'playing' | 'dead' | 'over'  (state machine, see below)
  paused: false,
  x: 0,                 // NOTE: bird's world X is actually LANES[lane] + lane-switch offset,
                         // tracked/consumed in part B; x=0 here is just the initial field value.
  y: 5,                 // world Y (height)
  vy: 0,                // vertical velocity
  lane: 1,              // 0=left,1=middle,2=right — starts in the MIDDLE lane
  radius: BIRD_RADIUS,  // 0.5, current collision radius (swaps to MINI_RADIUS under mini power-up)
  speed: 10,            // forward (−Z) speed; overwritten to 18 in resetGame()
  distance: 0,
  score: 0,
  coins: 0,              // coins collected THIS run
  lastGateZ: 0,          // z of the most recently placed gate/rush
  prevGaps: null,        // the previous row's per-lane gap spec, or null (used by makeReachable + coin trails)
  gatesSpawned: 0,        // count of spawned gate ROWS (rush rows do not increment this — see §4.4)
  gatesToPower: 6,        // countdown of gates until the next power-up may spawn
  power: { star: 0, magnet: 0, mini: 0 },  // remaining seconds per power-up, 0 = inactive
  grace: 0,               // remaining invulnerable-blink seconds after star ends
  wingPhase: 0, wingSpeed: 10,
  deadTimer: 0,
  shake: 0,
  time: 0,                // free-running clock, used e.g. for marker pulse
  beat: 0,                // music-beat phase, drives pulsing gaps / plants
  runTime: 0,             // elapsed seconds in the current run
  hold: false,            // true while waiting for the player's first tap ("get ready" hover)
  overAt: 0,
  zone: 0,                // current zone index
  rushAt: -1,             // gatesSpawned value at which the last coin-rush was triggered (dedup guard)
  squash: 0,              // 0..1 squash-and-stretch amount, decays after a flap
  hitStop: 0,
  nearChain: 0,           // consecutive near-miss counter
  menu: 'start',           // 'start' | 'shop' | 'achievements', only meaningful while mode==='ready'
  run: null,                // per-run mission/achievement counters, see §5
  celebrated: Set(),        // achievement ids already toasted this session, to avoid duplicate toasts
}
let lastRun = null          // set in part B when a run ends; holds a summary for the game-over screen
```

`invincible()` (line 741): `state.power.star > 0 || state.grace > 0 ||
state.god`. `state.god` is **not otherwise set anywhere in lines 1–1100** —
comment: "`god` is only set by the store-screenshot script" (an external
Playwright/automation hook that pokes `state.god = true` via devtools to
take safe screenshots). **Porting note**: the native port needs an
equivalent debug/god-mode flag reachable from its own screenshot tooling
(the `native/screenshots` JVM tool), not exposed in the shipped app.

## 5. Difficulty / pacing curve (lines 743–756)

```
difficulty()  = min(1, state.score / 40)                          // 0..1, saturates at score 40
baseSpeed()   = 18 + 16*difficulty() + 8*(1 - exp(-max(0,score-40)/50))
spacing()     = baseSpeed() * (1.7 - 0.6*difficulty())
```
- `baseSpeed` ranges from `18` (score 0) to `18+16=34` at score 40
  (difficulty saturates), then keeps creeping up via the second term,
  asymptotically approaching `34 + 8 = 42` as `score - 40 → ∞` (an
  exponential approach with a "half-life" of `50*ln2 ≈ 34.7` score points
  past 40; at score 90 (`over=50`) it's `34 + 8*(1-e^-1) ≈ 34+5.06 =
  39.06`; effectively flat by ~score 250).
- `spacing()` is the **time-based** row gap (row spacing in world units,
  despite the name reading like a distance — it's actually
  `baseSpeed * timeGapFactor`, i.e. literally the world-Z distance a row
  should be from the previous one so that, at the current forward speed,
  rows arrive at a roughly constant *time* interval): the factor
  `(1.7 - 0.6*difficulty)` shrinks from `1.7` (score 0) to `1.1` (score
  ≥40), so **rows get both faster and relatively closer together** as
  difficulty rises, compounding the challenge increase — comment at line
  747: "Rows are spaced by time, not distance: faster play keeps enough
  time to react, switch lanes and climb or drop between two rows."

Golden fixture: `docs/native/golden/main-a-difficulty.json` (from
`scripts/native-golden/main-a-difficulty.mjs`) tabulates `difficulty`,
`baseSpeed`, `spacing` for `score = 0, 5, 10, …, 300` plus the exact edge
points `{0,1,39,40,41,90}`. Sample rows: score 0 → speed 18.0, spacing
30.6; score 40 → speed 34.0, spacing 37.4; score 90 → speed 39.06,
spacing 42.97 (values rounded here to 2dp; the JSON has full precision).

### 5.1 Native addition: zone waves and late-game intensity (not in main.js)

Speed and spacing stay exactly the JS curve above (golden fixtures unchanged). Row
*generation* (§8.1) scales with `intensity` instead of `difficulty`:

```
zonePos   = (gatesSpawned % ZONE_ROWS) / (ZONE_ROWS - 1)    // 0 right after a coin rush .. 1 before the next
wave      = gatesSpawned < ZONE_ROWS ? 0 : 0.2 * difficulty() * (zonePos - 0.4)
late      = 0.2 * (1 - exp(-max(0, score - 40) / 60))
intensity = difficulty() + late + wave
```
- **Waves**: within every zone after the first the rows ramp up towards the next coin rush
  and dip slightly right after it (−0.08 … +0.12 at full difficulty). The amplitude scales
  with `difficulty()`, so before score 40 the wave is small, and the **whole first zone
  (rows 0–9) is identical to JS** (`intensity == difficulty`).
- **Late game**: the extra term keeps rows getting a little harder after score 40 (+0.2 at
  most), where JS plateaus.
- **Safety**: gap size is floored at `MIN_GAP = 3.6`; a cactus gap keeps at least `3.8`
  (the JS minimum). `pBlock` uses `min(1, intensity)` (still at most 0.5). The reachability
  budgets (§8.2) depend only on `spacing/baseSpeed` and are unchanged.

## 6. `resetGame()` (lines 758–825)

Called from `flap()` when `state.mode === 'ready'` (§8), i.e. the player's
first tap starts the run. Exact order of operations (order matters for
anything with side effects, e.g. RNG draws, DOM visibility):

1. If `progress.runs === 1 || progress.runs === 3` (i.e. this is about to
   become the player's **2nd or 4th** run — checked against the *old* runs
   count, before this run increments it elsewhere), schedule (900ms
   `setTimeout`) a toast `t('swipeHint')` — a one-time-per-milestone
   reminder that swiping between lanes also works (not just tapping).
2. `mode = 'playing'`, `hold = true` (wait for first tap/flap before
   physics starts falling — the bird hovers).
3. Reset kinematics: `x=0, y=5, vy=0, lane=1 (middle), radius=BIRD_RADIUS,
   speed=18` (note: differs from the module-load default of `10`),
   `score=0, coins=0`.
4. `prevGaps = null`, `gatesSpawned = 0`.
5. `gatesToPower = 5 + floor(random()*3) - progress.level('luck')` — i.e. a
   uniform-random integer in `{5,6,7}` minus the player's `luck` upgrade
   level (0..3), so with max luck the very first power-up can appear after
   as few as **2** gates.
6. `power = {star:0, magnet:0, mini:0}`, `grace=0`, `deadTimer=0`, `shake=0`,
   `runTime=0`, `nearChain=0`.
7. `toastQueue.length = 0` — hard-clear any pending toasts from the
   menu/previous run (the swipe-hint scheduled in step 1 is a *new*
   `setTimeout` closure, unaffected by this).
8. `zone=0`, `rushAt=-1`, `zoneMarks.length=0` (clear array in place).
9. If the currently-displayed biome isn't already zone 0's biome
   (`biomes.current !== zoneBiome(0)`), cross-fade to it over **1.2s**
   (`biomes.set(0, 1.2, zoneBiome(0))`) — if it's already correct (e.g. the
   shop preview happened to land on the same world), skip the fade
   entirely (no-op, avoids an unnecessary re-fade).
10. `music.setTheme(0); music.setMode('game')`.
11. `squash=0`.
12. `run = { coins:0, score:0, powerups:0, plants:0, moving:0, starRows:0, near:0, bestChain:0, zone:0 }` — fresh per-run mission/achievement accumulators.
13. `celebrated = new Set()`.
14. Bird visual reset: `bird.group.rotation.set(0,0,0)`, `visible=true`,
    `bird.setGlow(null)`.
15. `music.setHype(false)`.
16. `particles.clear(); impact.clear()`.
17. Deactivate every pooled gate (`active=false, visible=false`), every coin
    (`active=false, visible=false`), every pickup (`active=false,
    visible=false`).
18. **Pre-spawn the initial row of gates**:
    `lastGateZ = FIRST_GATE_Z + spacing()` (= `-60 + spacing()`);
    then `while (lastGateZ > -SPAWN_DISTANCE) spawnGate(lastGateZ -
    spacing())` — i.e. repeatedly place a gate `spacing()` further back
    than the last, until the frontier has been pushed out to
    `-170` or beyond. Note `spacing()` is re-evaluated on every loop
    iteration and depends on `state.score`, which does not change during
    this loop (score is 0 throughout pre-spawn) — so all initial gates use
    the same (score=0) spacing, **except** that `spawnGate` internally
    calls `gateSpec()` which reads `state.gatesSpawned` (incrementing each
    call) — warm-up widening therefore *does* vary row-to-row even though
    spacing doesn't.
19. Reset HUD text (`score→'0'`, `coin-count→'0'`), show `#hud`, hide
    `#start`/`#shop`/`#gameover`, hide+un-"over" the wallet chip, hide the
    language button, set `menu='start'`.
20. `applyBird()` (re-apply equipped skin/look — in case the shop preview
    left the 3D bird showing a try-on item).
21. `updateLaneDots()` (not shown in lines 1–1100; a stub in this range —
    verify definition location in part B).
22. Tap-zone hint: hide `#zones`' "show" class; if the first-run tutorial is
    active (`tut.active`), set `tut.step='flap'` and show the ghost hand in
    `'flap'` mode; **else** add the `'hold'` class to `#zones` (the three
    tap-zone outlines are shown, dimmed/holding, while the bird waits for
    the first tap — see §7 tap-zone visuals, and §8 `flap()` for how `hold`
    is cleared).

## 7. First-run tutorial (lines 827–860)

Only the very first launch ever (`tut.active`, set/read outside lines
1–1100 — presumably gated on `progress.runs === 0`, verify in part B/init
code) replaces normal gate generation for its first few rows with a fixed
scripted sequence, and shows a "ghost hand" overlay demonstrating the two
gestures.

```
TUT_SWITCH_ROW = 3     // the 4th row (index 3) is the "learn to switch lanes" row
tut = { active: false, step: '', gate: null, freezeY: 0 }
```
- `showHand(mode)`: `mode ∈ {'flap','side', falsy}`. Sets the hand overlay's
  CSS class to `` `show ${mode}` `` or clears it entirely; label text is
  `t('handFlap')` for `'flap'`, `t('handSide')` for `'side'`, empty
  otherwise.
- `updateHand()` (per-frame, called from part B): if the hand isn't shown,
  no-op. Otherwise projects the bird's world position
  (`state.x, state.y, 0`) through the camera to normalized device
  coordinates, converts to a 0..1 screen fraction `(bx,by)`. If the hand is
  currently in `'side'` mode, its X position is instead
  `max(0.2, screenX(LANES[0], state.y))` — i.e. pinned over the **left
  lane** (clamped to at least 20% from the left edge) to demonstrate
  tapping a side lane, rather than tracking the bird. Y is always
  `by + 0.06` (6% of screen height below the bird's projected position, so
  the hand appears to be "tapping" just below it).
- `tutorialSpec()` (lines 855–860), the scripted gap layout, entirely
  deterministic (no RNG):
  ```
  gap = { center: 5.2, size: 6.2 }             // fixed, generous gap
  gatesSpawned < 3  → [null, {...gap}, null]     // ONLY the middle lane is open
  gatesSpawned === 3 → [{...gap}, null, {...gap}] // ONLY the middle lane is now BLOCKED
  gatesSpawned > 3  → null                        // tutorial layout ends; fall through to normal gateSpec()
  ```
  Each returned gap is a **fresh object copy** (`{...gap}`) per lane so
  later per-gate mutation (e.g. by `configure`) never aliases between
  lanes/rows.
- This is consumed at the top of `gateSpec()` (§ next section): while
  `tut.active`, `tutorialSpec()` is tried first, and only if it returns
  `null` (row index > 3) does normal procedural generation run.
- `tut.gate` is set (line 1004, in `spawnGate`) to the gate object created
  for the switch-row (`gatesSpawned === TUT_SWITCH_ROW`), so part B can
  freeze the game in front of it and drive the "swipe to dodge" teaching
  moment (`tut.freezeY` presumably captures the bird's height to hold it
  steady — its use site is outside lines 1–1100).
- Tutorial step transitions visible in this range: `flap()` sets
  `tut.step='fly'` and hides the hand the instant the player performs the
  very first flap (§8); `resetGame()` sets `tut.step='flap'` and shows the
  flap hand when a tutorial run (re)starts.

## 8. Gate-row generation

### 8.1 `gateSpec()` — one row's per-lane gap layout (lines 862–921)

Called once per spawned gate row (from `spawnGate`, not for rush rows).
Returns an array of length 3 (index = lane), each entry `null` (lane
blocked) or a gap descriptor object.

Step by step:
1. **Tutorial override**: if `tut.active`, try `tutorialSpec()` (§7); if it
   returns non-null, return it immediately — none of the steps below run.
2. `d = difficulty()`. (Native: `d = intensity`, see §5.1; equal to `difficulty()` in the
   first zone.)
3. **Warm-up factor**: `warm = max(0, 1 - gatesSpawned/WARMUP_GATES)` — `1`
   at row 0, linearly down to `0` at row `WARMUP_GATES=6` and beyond (never
   negative).
4. **Gap size**: `size = 5.2 - 1.4*d + 1.8*warm` — ranges from `5.2+1.8=7.0`
   (row 0, d≈0) down to a minimum of `5.2-1.4=3.8` (once both `d=1` and
   `warm=0`, i.e. after warm-up AND at/above score 40).
5. **Vertical placement band** `[lo, hi]`:
   ```
   lo = max(size/2 + 1.2, lerp(0, 4.2, warm))
   hi = max(lo, min(CEILING - 2.5 - size/2, lerp(99, 6.5, warm)))
   ```
   Read carefully: `lerp(0, 4.2, warm)` goes from `4.2` (row 0, warm=1) down
   to `0` (warm=0) — i.e. **early rows have a higher floor** (gaps start
   higher up, since `lo` is also floored at `size/2+1.2` which is large
   early on due to large `size`). `lerp(99, 6.5, warm)` goes from `6.5`
   (row 0) up toward `99` (i.e. effectively unbounded, then clamped by
   `CEILING-2.5-size/2`) as warm→0 — so **early rows have a tight, low
   ceiling on gap center** (keeps the very first gaps easy and centered low
   near the bird's start height 5), while later rows use the full playable
   band up to just under the world ceiling.
6. **Per-lane raw gap**: `spec = LANES.map(() => ({ center: lo +
   random()*(hi-lo), size }))` — **3 independent `Math.random()` draws**,
   one per lane, all lanes open by default with the same `size` but
   independently randomized `center` within `[lo,hi]`.
7. **Lane blocking** (only if `state.score >= 3 && gatesSpawned >=
   WARMUP_GATES`, i.e. never during warm-up nor in the first 3 score
   points even post-warm-up):
   ```
   pBlock = 0.2 + 0.3*d                         // 0.2..0.5
   order = [0,1,2].sort(() => Math.random()-0.5) // ad-hoc shuffle (NOT a proper Fisher-Yates —
                                                   // biased/engine-dependent, see Porting notes)
   open = 3
   for i in order:
     if open > 1 && random() < pBlock:
       spec[i] = null; open--
   ```
   Invariant preserved: **at least one lane is always open** (`open > 1`
   guard prevents blocking the last remaining open lane). Each of the 3
   lanes is tested at most once, in shuffled order, each independently at
   probability `pBlock` (subject to the "last lane" guard), so the
   **expected** number of blocked lanes is *not* simply `3*pBlock` because
   of the guard and order-dependence — see Porting notes on reproducing
   this exactly vs. functionally.
8. **Zone specialities**:
   ```
   zone = floor(gatesSpawned / ZONE_ROWS) % BIOMES.length     // ZONE_ROWS=10
   moveBoost   = zone === 2 ? 1.6 : 1
   plantBoost  = zone === 3 ? 1.8 : 1
   pulseChance = zone === 1 ? 0.45
               : gatesSpawned > ZONE_ROWS * BIOMES.length ? 0.15
               : 0
   ```
   Zone indices are **relative to `BIOMES.length`**, not to `state.zone`
   (the actual current zone counter used for biome selection elsewhere) —
   this `zone` local is purely a "which biome's obstacle flavor applies to
   this row" lookup based on row count, decoupled from — but intended to
   line up with — the zone transitions driven by `spawnRush` (§8.3). Zone 1
   = "breathing gaps" specialty (pulseChance 0.45, presumably the
   "Herbstwald"/autumn-forest biome per the code comment elsewhere), zone 2
   = moving-gap specialty (`moveBoost`), zone 3 = plant/cactus specialty
   (`plantBoost`). After all biomes have been cycled through once
   (`gatesSpawned > ZONE_ROWS*BIOMES.length`), a flat 15% pulse chance
   applies everywhere as a baseline (verify `BIOMES.length`, likely 4, in
   `biomes.js`).
9. **Per-lane obstacle assignment**, only for lanes left open by step 7:
   ```
   open = indices where spec[i] != null
   easy = open[floor(random() * open.length)]      // one open lane exempted from all obstacles
   for i in open:
     if i === easy && open.length > 1: continue      // the easy lane is skipped ONLY if there's another open lane
     g = spec[i]
     if pulseChance>0 && random() < pulseChance:
       g.pulse = true
       g.size = max(g.size, 4.6)                       // pulsing gaps are never narrower than 4.6 at rest
       g.plantOffset = random()<0.5 ? 0 : 1             // phase offset (2 possible values) for the breathing animation
     elif score>=6 && random() < (0.25+0.3*d)*moveBoost:
       g.amp   = min(1.2 + random()*1.3, (hi-lo)/2)      // moving amplitude, capped at half the playable band
       g.center = clamp(g.center, lo+g.amp, hi-g.amp)     // re-clamp center so the full sine sweep stays in [lo,hi]
       g.speed = (1.2 + random()*1.2) * (zone===2 ? 1.25 : 1)
       g.phase = random() * 2π
     elif score>=10 && random() < (0.25+0.25*d)*plantBoost:
       g.plant = true
       g.plantOffset = random()<0.5 ? 0 : 2              // NOTE: plant uses offsets {0,2}, pulse uses {0,1} — different ranges, not reused
   ```
   Obstacle types are **mutually exclusive per lane** (`elif` chain): a lane
   is at most one of {pulsing, moving, planted, or plain}. Gating by
   `score>=6` (moving) and `score>=10` (plant) means the very easiest early
   rows (post-warm-up but score<6) can only ever get plain or pulsing gaps.
   The `easy` lane is chosen **before** knowing which obstacle it would
   have gotten — it unconditionally skips the whole if/elif chain (when
   there's more than one open lane), guaranteeing at least one
   obstacle-free open lane in every non-trivial row.
10. `makeReachable(spec, lo, hi)` — enforce the fairness constraint against
    the *previous* row (§8.2), potentially mutating `spec` in place.
11. Return `spec`.

Golden coverage: the **pure, RNG-free** parts of this function
(`difficulty`, `warm`, `size`, `lo`/`hi` bounds, and the `makeReachable`
math) are covered by `main-a-difficulty.json` and `main-a-reachability.json`.
The RNG-dependent parts (blocking, obstacle assignment) are **not**
bit-exact-fixtured — see "Porting notes" below for why, and for the
correctness invariants a port should test instead.

### 8.1a Native addition: wandering gap (not in main.js)

From score 40, with chance `0.1 + 0.1*zonePos` per row (rolled right after `easy` is picked),
one non-easy open lane `from` is chosen together with a neighbouring lane `to` (`|to-from| = 1`,
`to != easy`). `from` skips the obstacle chain (its gap stays plain); afterwards the gap moves to
`to` (replacing whatever was there) with `wanderFrom = from`, and `from` becomes pipe. The spec
therefore holds the **final** layout; `makeReachable`, `prevGaps`, coins and pickups all see it.
The easy lane is never touched. About 5 % of late rows get one.

- **Reachability**: a wandering gap is budgeted as one extra lane step (`k = 0.6^(steps+1)`)
  in `fits` and in the pull-in fix of `makeReachable`, because its final lane shows late.
- **Timing** (`GateRows.wander`, every frame before `update`): progress
  `p = clamp((2.0 - t) / (2.0 - 0.6), 0, 1)` with `t = -z / speed` seconds to arrival, never
  decreasing. The lanes swap open/blocked at `p = 0.5`; from 0.6 s before arrival it is settled,
  so collisions only ever see the final layout. Both lanes keep the gap numbers.
- **Look** (`view/world/Gates.kt`): the open column (pipes + lips) and the pipe column trade
  places sideways with a smoothstep; the open one passes in front (towards the bird), and a
  short flinch the other way at the start telegraphs the move.
- **Bot**: predicting skills (pro) plan for the destination as soon as the slide starts;
  others re-pick their lane when the chosen one closes.
- No coins are placed in a wandering gap and no coin trail/pickup leads into it.

### 8.2 `makeReachable(spec, lo, hi)` — fairness pass (lines 923–955)

Ensures the player can always get from *some* open gap in the previous row
to *some* open gap in this row, given the time between rows and how far a
lane-switch tap can move the bird vertically in that time. No-ops if there
is no previous row (`state.prevGaps === null`, e.g. right after a coin
rush, or the very first row).

```
t = spacing() / baseSpeed()                 // = (1.7 - 0.6*difficulty()), seconds between rows
maxDrop = 1 + 3.5*t                          // downward reach budget, world units
maxRise = 0.8 + 3*t                          // upward reach budget, world units
SWITCH_FACTOR = 0.6
reach(from, to, steps) = let k = SWITCH_FACTOR**steps, d = to-from
                          in d <= maxRise*k && -d <= maxDrop*k
fits(prevGap, gap, steps) = reach(prevGap.center, gap.center - (gap.amp||0), steps)
                         && reach(prevGap.center, gap.center + (gap.amp||0), steps)
```
- `steps = abs(laneIndexTo - laneIndexFrom)` — the number of lane-switch
  taps needed; each additional switch multiplies **both** budgets by
  `0.6` (i.e. switching lanes "costs" reach, modeling the time spent on
  the horizontal glide instead of climbing/falling).
- For a **moving** gap, `fits` requires the reach check to hold at **both**
  extremes of its sine sweep (`center - amp` and `center + amp`), i.e. the
  entire travel range must be reachable, not just its resting center.
- For each lane `j` that had an open gap in the previous row:
  ```
  open = indices where spec[i] != null
  if any i in open such that fits(prev[j], spec[i], abs(i-j)): continue  // already fine, do nothing
  // otherwise, force-fix the CLOSEST open lane to lane j:
  i = open sorted by abs(i-j) ascending, take first
  g = spec[i]
  k = SWITCH_FACTOR ** abs(i-j)
  g.amp = 0                                    // strip any movement — a moving gap that was unreachable becomes static
  g.center = clamp(g.center, prev[j].center - maxDrop*k*0.9, prev[j].center + maxRise*k*0.9)
  g.center = clamp(g.center, lo, hi)           // re-clamp into the row's overall playable band
  ```
  The `*0.9` safety margin means the forced position is pulled to 90% of
  the theoretical maximum reach, not the exact edge (a small buffer against
  rounding/frame-time jitter). This mutates `spec[i]` **in place**, so a
  lane could be touched by this fix-up multiple times (once per previous
  lane `j` that needed it) — later fixes can move it again; the final
  state after the `forEach` is authoritative.
- **This check runs once per previous-row-lane, independently** — it does
  not verify that a single fix satisfies *all* previous lanes
  simultaneously, only that each previous lane individually has *a* path
  forward (possibly to different lanes for different previous lanes). This
  is a deliberate simplification, not a bug — flag as expected behavior,
  not something to "fix" when porting.

Golden fixture: `docs/native/golden/main-a-reachability.json` (from
`scripts/native-golden/main-a-reachability.mjs`) tabulates `t`, `maxDrop`,
`maxRise` for `score ∈ {0,10,40,100}`, and `reach(0, delta, steps)` for
`delta ∈ {-6,-3,-1,0,1,3,6}`, `steps ∈ {0,1,2}` at each score. This is a
complete, order-independent, RNG-free characterization of the reachability
predicate — a Kotlin port can unit-test its own `reach()` against every row
of this file directly.

### 8.3 Row/coin/pickup placement — `placeCoin`, `placePickup`, `spawnRush`, `spawnGate` (lines 957–1035)

```js
placeCoin(x,y,z):    find first inactive pooled coin; if none, silently no-op; else activate+position it.
placePickup(x,y,z):  type = uniform random POWERUP_TYPES entry;
                      find first inactive pooled pickup of THAT type (pool is per-type, 2 each — §2.2 item 16);
                      if none available for that specific type, no-op and return false;
                      else activate+position it, return true.
```
Both pools are hard caps: exhausting them is a **silent no-op**, never an
error — a port must replicate the pool-exhaustion behavior (drop the
spawn) rather than e.g. growing the pool or asserting.

`spawnRush(z)` (lines 977–990) — the zone-transition "coin rush" interlude
(no pipes, a decorative coin wave), triggered from `spawnGate` (see below):
```
gap = spacing()
pattern = [1,1,0,0,1,2,2,1,1]          // 9 lane indices, a fixed S-curve across the 3 lanes
pattern.forEach((lane, k) => {
  t = k / (pattern.length - 1)          // 0..1 over the 9 coins
  placeCoin(LANES[lane], 5 + sin(t*2π)*1.6, z + gap*0.45 - t*gap*0.9)
})
zone = state.zone + zoneMarks.length + 1     // the NEXT zone index this rush introduces
zoneMarks.push({ z: z + gap*0.45, zone })     // banner position, mid-rush
scenery.setTheme(zoneBiome(zone).scenery)      // switch ground/scenery texture set immediately
state.prevGaps = null                          // no reachability constraint needed after a coin-only stretch
```
- The **9 coins** trace one full sine oscillation in height
  (`5 + 1.6*sin`, i.e. centered at y=5 ±1.6) while their lane sequence
  `1,1,0,0,1,2,2,1,1` weaves middle→left→middle→right→middle.
- Coins are placed from `z + gap*0.45` (slightly ahead of the rush's
  nominal position) back to `z + gap*0.45 - gap*0.9` (i.e. spanning `0.9 *
  spacing()` of Z-depth, centered on `z + gap*0.45`).
- `state.zone + zoneMarks.length + 1`: because multiple rushes can be
  pre-spawned ahead of the bird before any of them is actually "entered"
  (the bird passing the banner is what increments `state.zone` — that
  logic is in part B), `zoneMarks.length` disambiguates *which* upcoming
  rush this is, so zone numbering for scenery/coin-rush purposes stays
  monotonic even several rushes ahead of the player.
- The scenery theme swap happens at **spawn time**, not when the bird
  reaches the rush — comment (line 987-988): "Switch the scenery now:
  chunks wrapping from here on are built in the new theme, so the new
  place starts right where the banner appears" — i.e. world-generation
  (ground/scenery chunk recycling, part B/world.js) always builds *new*
  chunks in whatever theme is current, so pre-committing the theme change
  at spawn time (rather than at "arrival" time) ensures the visual biome
  boundary lines up exactly with the physical banner/rush location instead
  of trailing behind it.

`spawnGate(z)` (lines 992–1035) — the top-level per-row dispatch, called
repeatedly by the pre-spawn loop in `resetGame()` and (in part B) whenever
the frontier needs to be pushed further:
1. `state.lastGateZ = z` unconditionally, **first thing**, even if the rest
   of the function bails out early — "always advance, even if the pool is
   exhausted" (comment, line 993) — this guarantees the spawn frontier
   always advances by the intended spacing even under gate-pool exhaustion,
   preventing a runaway tight loop from repeatedly retrying the same z.
2. **Rush trigger**: if `gatesSpawned > 0 && gatesSpawned % ZONE_ROWS ===
   0 && rushAt !== gatesSpawned` → this row *replaces* a normal gate row
   with a coin rush: set `rushAt = gatesSpawned` (dedup guard — without it,
   re-entrant calls at the same `gatesSpawned` value would spawn duplicate
   rushes), call `spawnRush(z)`, and **return early** — no gate object is
   allocated, `gatesSpawned` is **not incremented** for a rush row (rushes
   don't count as rows for zone/warm-up/etc. purposes; the *next* real gate
   row after a rush is still `gatesSpawned` at the same value as before the
   rush).
3. Otherwise, allocate an inactive gate from the pool
   (`gates.find(g => !g.active)`); if the pool is exhausted, **silently
   return** (no gate placed at all this call — see §2.2 item 14).
4. `spec = gateSpec()` (§8.1); `gate.active = true`; `gate.configure(z,
   spec)` (world.js — builds pipe meshes/hitboxes from `spec`).
5. If tutorial active and this is exactly the switch row
   (`gatesSpawned === TUT_SWITCH_ROW`), remember it: `tut.gate = gate`.
6. `state.gatesSpawned++`.
7. **Coins in static gaps**: for each lane with a gap that is **not**
   moving (`g && !g.amp`), 30% chance (`random() < 0.3`) to place a single
   coin at `(LANES[i], g.center, z)` — i.e. coins only ever spawn dead-
   center in a *static* gap this way (moving gaps never get a
   center-hover coin via this path).
8. `calm` lanes: lane indices where **both** the previous row and this row
   have an open, non-moving gap (`prev[i] && spec[i] && !prev[i].amp &&
   !spec[i].amp`) — i.e. a lane that was and remains calm/static across
   the row-to-row transition, a safe place to lay a coin trail or float a
   pickup without fighting a moving gap.
9. **Pickup spawn**: `if (--gatesToPower <= 0 && calm.length)`: **always
   decrements** `gatesToPower` every gate-row call (even if `calm` is
   empty and nothing spawns this time — the countdown still ticks and
   could go negative until a calm row finally appears); when it fires,
   pick a random calm lane, `placePickup(LANES[i], (prev[i].center +
   spec[i].center)/2, z + spacing()/2)` — positioned **between** the two
   rows both vertically (average of both gaps' centers) and horizontally
   in Z (halfway between them). Reset the countdown:
   `gatesToPower = 6 + floor(random()*3) [i.e. {6,7,8}... wait check: floor(random()*4) → {0,1,2,3}] - progress.level('luck')`.
   **Re-verify exact literal**: line 1020 is
   `state.gatesToPower = 6 + Math.floor(Math.random() * 4) - progress.level('luck');`
   — uniform integer in `{6,7,8,9}` minus luck level (0..3), so the
   post-first-pickup cadence is **denser at minimum** (6-3=3) and **sparser
   at maximum** (9-0=9) than the very first cooldown (`{5,6,7} -
   luck`, from `resetGame`, step 5 above) — the first power-up of a run can
   come slightly sooner than subsequent ones, on average.
10. **Coin trail** (only if no pickup was placed this call): 55% chance
    (`random() < 0.55`), pick a random calm lane, lay **4** coins
    (`k=1..4`, `t=k/5` i.e. at 20/40/60/80% ) interpolating
    `lerp(prev[i].center, spec[i].center, t)` in height and `z + gap*(1-t)`
    in depth (`gap = spacing()`) — a visual "breadcrumb trail" guiding the
    eye/bird from the previous row's gap into this row's, only ever within
    a single calm lane (never crossing lanes).
11. `state.prevGaps = spec; state.lastGateZ = z` (redundant re-set of
    `lastGateZ`, already set in step 1 — harmless, not a bug, just leftover
    from refactor; preserve or simplify at the porter's discretion, note it
    in the port rather than silently "fixing" behavior that has zero
    observable effect).

## 9. Input — flap, pause, lane math (lines 1037–1100+)

### 9.1 Audio unlock (lines 1040–1043)
```js
function startAudio() { audio.unlock(); music.start(); }
```
Called from any user gesture that should be allowed to start audio (shop
button, achievements button, gift button — browser autoplay policies
require a user gesture to unlock a `AudioContext`). Android has no such
restriction, but the **same call site pattern** (unlock once per session on
first interaction) should be kept so a future iOS `AVAudioSession` port
doesn't need new call sites.

### 9.2 Pause (lines 1045–1054)
```js
function setPaused(paused) {
  state.paused = paused
  if (paused) {
    #pause-score = state.score; #pause-coins = state.coins; #pause-zone = state.zone + 1  // 1-indexed display
  }
  #pause.hidden = !paused
  audio.setSuspended(paused)
}
```
Pausing suspends the WebAudio graph (`audio.setSuspended`); no separate
handling of the render loop is in this range (part B's `requestAnimationFrame`
callback presumably checks `state.paused` to skip physics but keep
rendering — verify in part B). Un-pausing (`flap()` while paused, see next)
does **not** re-run any of the score/coins/zone display refresh — those are
write-only at pause time, read by the (hidden) pause DOM while paused.

### 9.3 `flap()` (lines 1056–1081) — the single input action

```js
function flap() {
  if (state.paused) { setPaused(false); return }              // any tap while paused just unpauses
  if (state.mode === 'ready') { resetGame(); return }          // any tap on the start/shop screen starts a run
  if (state.mode !== 'playing') return                          // no-op while dead/over (must use explicit UI buttons)
  if (state.hold) {
    state.hold = false
    #zones.classList.remove('hold')
    if (tut.active) { tut.step = 'fly'; showHand(null) }
    else { reflow #zones; #zones.classList.add('show') }         // re-trigger the "show tap zones" flash animation
  }
  state.vy = FLAP_VELOCITY   // 11.5 — SET, not additive
  state.wingSpeed = 38        // fast wing-flap animation speed while ascending from a flap
  state.squash = 1             // full squash-and-stretch pulse
  sfx.flap()
}
```
- **Every** call while `mode==='playing'` and not paused applies the same
  flap impulse, regardless of current lane/position/velocity — there is no
  "can't flap while already rising fast" restriction.
- The very first flap of a run additionally clears `hold` (ends the
  "hover and wait" pre-run state) and either ends the tutorial's "flap"
  step or (normal play) replays the tap-zone-outlines intro animation via a
  forced reflow (`void zonesEl.offsetWidth`) — the standard JS trick to
  restart a CSS animation by re-triggering layout between removing and
  re-adding the class.
- Note this function is `flap()`, the **vertical thrust** gesture; lane
  *switching* is a related-but-distinct action driven by the touch-zone
  math below (§9.4) and applied in code outside lines 1–1100 (the tap
  handler that decides "flap in place" vs "switch lane" — its policy is
  described next).

### 9.4 Touch lane targeting (lines 1083–1100, cut off mid-section)

Comment block (lines 1083–1087) states the overall design: the camera never
pans horizontally, so each lane occupies a **fixed screen-space band**
(~20%/50%/80% of width for left/middle/right). Tapping:
- inside your **own current lane's** band → flap in place.
- inside **another lane's** band → move to that lane (a small hop, using
  `SWITCH_HOP=6`, not a full flap — see constants table) **and** switch
  lanes.
- a tap **near the bird's own drawn (interpolated) screen position**
  (within `NEAR_BIRD = 0.12`, 12% of screen width) **always** flaps in
  place, even mid-lane-transition — this prevents an accidental lane
  switch from a slightly-mistimed tap meant to just flap, per the comment
  at lines 1086–1087: "so flapping never changes lanes by accident."

```
NEAR_BIRD = 0.12                       // screen-width fraction around the drawn bird that always flaps
tmpProj = new THREE.Vector3()          // scratch vector reused every call, avoids per-frame allocation
screenX(x, y) = project (x, y, 0) through camera → NDC → (ndc.x+1)/2   // 0..1 screen fraction
laneBounds() = midpoints between successive screenX(LANES[i], state.y):
              [ (screenX(LANES[0])+screenX(LANES[1]))/2, (screenX(LANES[1])+screenX(LANES[2]))/2 ]
laneAtScreen(x):                        // (body cut off at line 1100 — the boundary comparisons
                                          // against laneBounds()'s two midpoints are in part B/the
                                          // remaining lines of this function; NOT specified by this
                                          // document — see main-b.md for the continuation starting
                                          // at line 1100)
```
`screenX`/`laneBounds` are recomputed **every call** at the bird's *current*
height (`state.y`), not a fixed height — because the camera is a
perspective projection, the fixed-width-looking lane bands in the comment
are only approximately fixed; they actually shift slightly as the bird
rises/falls (foreshortening) and these functions re-derive the true
current screen bounds each time rather than hard-coding 20/50/80%. A native
port must replicate this **dynamic re-projection**, not hard-code the
lane-band boundaries as fixed fractions.

**Hand-off to main-b.md**: `laneAtScreen(x)`'s body (comparing `x` against
`b1`/`b2` to return a lane index 0/1/2), the actual pointer/touch event
listeners that call it and `flap()`, `NEAR_BIRD` gating logic, and the
lane-switch application to `state.lane`/`state.x` all continue past line
1100 and are out of scope for this document.

## 10. Visual reference tables

### 10.1 Hex colors introduced in lines 1–1100

| Name | Hex | Usage |
|---|---|---|
| Blob shadow | `0x000000` @ 0.22 opacity | Under-bird contact shadow |
| `MARKER_OK` | `0x8cff5a` | Height-marker ring, safe |
| `MARKER_BAD` | `0xff4a3d` | Height-marker ring, danger |
| Surprise-purchase confetti | `0xff5a8a, 0x5ad1ff, 0xffd84a, 0x7be07b, 0xffffff` | Particle burst colors |
| Dice-reroll confetti | player's skin body color, `0xffffff, 0xfff176` | Particle burst colors |
| Buy-item confetti | item color(s) (skin body / item.colors), `0xffffff, 0xfff176` | Particle burst colors |
| Daily-gift confetti | `0xfff176, 0xffd400, 0xffffff` | Particle burst colors |
| Trail-tile fallback background | `#cbb968` | "no trail" shop tile |
| Trail-tile backdrop | `#6fb8e6` | Sky blue behind trail dot swatches |
| Look-kind tile backdrop | `#bfe6f5` | Behind cached 3D thumbnails |
| Default/fallback tile background | `#fff6d5` | Upgrades tab dot background |

(`POWERUPS[type].color` hex values — `0xffd400` star, `0xe53935` magnet,
`0x9b59b6` mini — are defined in `powerups.js`, referenced here via
`powerDuration`'s sibling data, not re-declared in main.js.)

### 10.2 Geometry/material specifics

| Object | Geometry | Material |
|---|---|---|
| Blob shadow | `CircleGeometry(0.7, 20)` | `MeshBasicMaterial{color:0x000000, transparent, opacity:0.22, depthWrite:false}` |
| Height marker | `RingGeometry(0.2, 0.36, 20)` | `MeshBasicMaterial{color:MARKER_OK, transparent, opacity:0(animated), depthTest:false, depthWrite:false}`, `renderOrder=10` |
| Camera | `PerspectiveCamera(fov=60, aspect=1(resized later), near=0.1, far=400)` | — |
| Shop thumbnail renderer | offscreen `WebGLRenderer{antialias:true, alpha:true, preserveDrawingBuffer:true}`, size 112×112 | `PerspectiveCamera(fov varies 22-30, near=0.1, far=30)`; lit by `HemisphereLight(0xffffff,0x998866,1.9)` + `DirectionalLight(0xffffff,2.2)` at `(2,5,1)` |

## 11. Strings / i18n keys referenced in lines 1–1100

`missions`, `swipeHint`, `gift` (`{n}`), `streak` (`{d,n}`), `rewardAd`
(`{n}`), `stylePassActive` (`{n}`), `stylePassAd` (`{n}`), `rewardGranted`,
`rewardUnavailable`, `stylePassGranted`, `level` (`{n,max}`), `maxed`,
`needMore` (`{n}`), `upgrade` (`{n}`), `coinPack` (`{n,price}`), `rare`,
`rareOwned`, `rareOr` (`{text}`), `select`, `selected`, `buy` (`{n}`),
`purchaseUnavailable`, `skinReward` (`{name}`), `surprise` (`{n}`),
`surpriseGot` (`{name}`), `handFlap`, `handSide`, `realBuy` (`{price}`).

Every one of these must exist in both `de` and `en` locale tables (see
`i18n.js`); this spec does not restate their translated text — that lives
in `i18n.js`'s own data, to be ported verbatim into the shared `i18n`
Kotlin module (string-for-string, including any embedded rich-icon
placeholders consumed by `rich`/`setRich`).

## 12. Porting notes

### 12.1 Three.js features used in lines 1–1100, and how to reproduce them

- **`WebGLRenderer` + `PCFShadowMap`** → OpenGL ES 3.0 context (EGL on
  Android/desktop-JVM via the shared GL facade) with a depth-only shadow
  pass sampled with a small PCF kernel (a 3×3 or 5×5 percentage-closer
  filter in the fragment shader) — Three's `PCFShadowMap` is not a single
  GL feature but a shadow-mapping technique; reimplement as: render scene
  depth from the sun's view into a depth texture/FBO, then in the main pass
  sample that depth texture at several offsets and average the
  in-shadow test.
- **Adaptive `setPixelRatio`** → render the 3D scene into an FBO sized at
  `screenSize * qualityScale` and blit/upscale to the real surface size;
  or, simpler, resize the actual `GLSurfaceView`/`SurfaceTexture` buffer
  via `setFixedSize` at the desired scaled resolution while the view
  itself stays full-screen.
- **`MeshBasicMaterial` (blob shadow, height marker)** → unlit
  (no-lighting) shader: output a flat color × vertex alpha × uniform
  opacity; `depthWrite:false`/`depthTest:false` map directly to GL state
  (`glDepthMask(GL_FALSE)`, `glDisable(GL_DEPTH_TEST)`).
- **`CircleGeometry`/`RingGeometry`** → generate a small triangle-fan (or
  triangle-strip for the ring) mesh procedurally at startup; trivial, no
  library needed (this is exactly the kind of "small custom GL engine,
  procedural meshes" the architecture calls for).
- **`renderOrder`** → an explicit draw-order sort in the custom renderer
  (draw opaque/depth-tested geometry first, then draw `renderOrder`-sorted
  overlay quads/rings on top with depth test off).
- **`Vector3.project(camera)`** (used in `updateMarker`, `updateHand`,
  `screenX`) → standard `viewProjectionMatrix * vec4(pos,1)` then perspective
  divide by `w`, giving NDC `[-1,1]`; then `(ndc+1)/2` for a 0..1 screen
  fraction. Implement as a small `Mat4`/`Vec3` utility in the shared
  `commonMain` math module — this is one of the most-reused primitives in
  this spec (marker, tutorial hand, lane hit-testing) and belongs in the
  core math package, not duplicated per call site.
- **`quaternion.copy(camera.quaternion)` (billboarding)** → same idea:
  store the camera's orientation quaternion and apply it directly to the
  marker's model matrix instead of a full lookAt recomputation.
- **Offscreen `WebGLRenderer` for shop thumbnails, `toDataURL`** → on
  Android, render the small preview scene into an FBO-backed texture, then
  either (a) draw that texture directly as a Compose `Bitmap`/`ImageBitmap`
  via `glReadPixels` into a `Bitmap`, cached by the same
  `(kind:id:skinId)` key, or (b) keep the tile preview as a native
  (non-3D) rendering if that is materially simpler — this spec does not
  mandate reproducing the exact offscreen-3D-thumbnail architecture, only
  the **visual result and cache key**.
- **`Color`/hex ints** (`0xRRGGBB`) → Kotlin `Int` (e.g. `0xFFD400`) or a
  small `Color(r,g,b)` value class; keep the exact hex values from the
  tables above, do not convert through any color space that could round
  differently (sRGB byte values must match bit-for-bit, since players will
  compare screenshots against the JS reference).
- **`MathUtils.clamp/lerp/smoothstep`** → trivial utility functions in
  `commonMain`; formulas are the standard ones Three.js uses (`lerp(a,b,t)
  = a+(b-a)*t`, `smoothstep` = the classic Hermite `3t²-2t³` after clamping
  `t` to `[0,1]` — verify against `world.js`'s `plantRise` usage of
  `THREE.MathUtils.smoothstep`, which appears only outside lines 1–1100 in
  main.js but is exercised there).

### 12.2 Web APIs used in lines 1–1100, and native equivalents

| Web API | Used for | Native (Android) equivalent |
|---|---|---|
| `navigator.vibrate(ms)` | `buzz()` haptic pulses | `Vibrator`/`VibratorManager.vibrate(VibrationEffect.createOneShot(ms, DEFAULT_AMPLITUDE))`, behind a `Haptics` platform interface |
| `localStorage.getItem/setItem` | `birdy-quality` (and, per the migration section of the architecture doc, `birdy-progress…`, `birdy-best`, `birdy-muted`, `birdy-lang`) | `SharedPreferences` (Android `actual` of the shared `KeyValueStore` interface) — see Migration note below |
| `matchMedia(...)` | Landscape+touch detection (used outside 1–1100, wired here) | `Configuration.orientation` + `Configuration.touchscreen`, observed via `onConfigurationChanged` or a Compose `LocalConfiguration` |
| `setTimeout`/`setInterval` | Delayed toasts, achievement-payout delay, ad-UI refresh interval | Kotlin coroutines: `delay(ms)` in a scoped coroutine, or a `Handler.postDelayed` equivalent; the 60s shop-refresh interval → a `while(isActive){delay(60_000); …}` loop scoped to the shop screen's lifecycle |
| `requestAnimationFrame` loop (part B, referenced here for pause behavior) | Per-frame update/render | `Choreographer.postFrameCallback` (Android) or the `GLSurfaceView` renderer's own render-thread loop |
| `AudioContext` suspend/resume (`audio.setSuspended`) | Pause/resume music+SFX graph | `AudioTrack`/oscillator-graph equivalent in the shared `AudioEngine` — pause = stop feeding PCM buffers, not necessarily tearing down the track |
| `HTMLCanvasElement.toDataURL('image/png')` | Shop thumbnail caching | `Bitmap.compress`/`glReadPixels` → `Bitmap`, cached in-memory (an `LruCache<String, Bitmap>` keyed identically) |
| DOM `classList`/CSS animations (reflow trick, bump animation, toast show/hide) | All shop/HUD chrome | Compose state-driven visibility + `AnimatedVisibility`/`Animatable` — the "force reflow to restart a CSS animation" trick has no native equivalent need: Compose recomposition on a state flip (e.g. a toggled `key()` or restarting an `Animatable` from 0) achieves the same "restart the animation" effect cleanly |

### 12.3 On not reproducing `Math.random()` bit-exactly

JavaScript's `Math.random()` is **not seedable** and the game never
attempts to seed it — so there is no canonical "reference sequence" of
random numbers to match against, unlike the deterministic formulas in
§5/§8.2. Treat every `Math.random()` call site in this spec (gate center
placement, lane blocking, obstacle-type rolls, pickup type/lane choice,
coin-trail/rush placement, dice reroll, surprise-box pick) as specifying a
**distribution and a set of invariants**, not a sequence to replay. A
Kotlin port should:
1. Use `kotlin.random.Random` (or an injected `Random` for testability)
   at exactly the same call sites and with exactly the same formulas
   (same ranges, same `floor`/`clamp` treatment) as documented above.
2. Verify correctness via **property-based tests** on the invariants this
   spec calls out explicitly, e.g.:
   - `gateSpec()` never returns all-3-lanes-blocked.
   - the "easy" lane (when `open.length > 1`) never receives `pulse`,
     `amp`, or `plant`.
   - every gap's `center ± size/2` stays within `[0, CEILING]` after
     `makeReachable`'s adjustments (i.e. the fairness fix-up never pushes a
     gap out of the world's vertical bounds — verified by the final
     `clamp(g.center, lo, hi)`).
   - `makeReachable` never leaves a previous-row open lane with **zero**
     reachable lane in the new row (this is the entire point of the
     function; a regression here would make runs occasionally
     unwinnable).
   - `gatesToPower` monotonically decrements to ≤0 before any pickup
     spawns, and is always re-seeded to a value **> 0** afterward (so the
     countdown can never get permanently stuck at 0 spawning a pickup
     every single row).
3. The one exception worth flagging for a **product** decision (not
   silently resolved): the lane-blocking shuffle
   (`[0,1,2].sort(() => Math.random()-0.5)`, §8.1 step 7) is a well-known
   *not-uniform* shuffle in JS engines (its bias depends on the specific
   sort algorithm/stability); a Kotlin `shuffled()` call would actually be
   *more* uniformly random than the original. This is very unlikely to be
   perceptible to a player, but note it rather than silently "fixing" it,
   per the project's "match tuning constants exactly, do not improve game
   feel" rule — a proper Fisher-Yates shuffle over 3 elements is the
   recommended replacement, called out here as an intentional, harmless
   deviation rather than an oversight.

### 12.4 "Hitbox is frozen at spawn" — a subtlety for the collision spec (main-b)

`gate.configure()` (world.js) sets `lane.hitLow`/`lane.hitHigh` once, at
spawn time, to that gap's **resting** `gapLow`/`gapHigh` — even though a
moving (`amp>0`) or pulsing gap's **visual** pipe geometry animates every
frame via `gate.update(time, beat, dt)` (world.js, calling `setGap` with
the animated center/size). `updateMarker()` in this file (§2.2 item 12)
reads `lane.hitLow`/`lane.hitHigh` directly — i.e. **the height-marker
preview, and (per this naming) presumably the real collision test in part
B, check against the frozen spawn-time hitbox, not the currently-animated
visual position**, for moving/pulsing gaps. This must be verified
explicitly against part B's actual collision code before porting — if
confirmed, it is either (a) an intentional simplification (collision uses
the gap's rest position/size, ignoring live animation, making moving gaps
effectively decorative-only for hit-testing) or (b) collision is
recomputed elsewhere from `lane.center`/`amp`/`phase` at test time and
`hitLow`/`hitHigh` are legacy/unused for that purpose. **Do not assume
either answer — this spec only establishes what lines 1–1100 set and read;
main-b.md must state which is true and both specs must agree.**

### 12.5 Migration-relevant keys touched in this range

Only `birdy-quality` is read directly in lines 1–1100
(`localStorage.getItem('birdy-quality')`, §2.1). It is read once at boot,
wrapped in try/catch, defaulting to `0` on any failure (missing key,
storage disabled, non-numeric value — `Number(null) === 0` and
`Number(undefined) === NaN`, both falsy after `|| 0`... but note
`Number("")` is also `0` and `Number("abc")` is `NaN` → falls back to `0`
either way via `|| 0`). The one-time WebView-localStorage migration
(described in the architecture doc) must import this key (integer 0–4) into
the native KV store under an equivalent key/field before first native-app
launch reads it, or the player's chosen quality tier silently resets to 0
(full quality) on migration — decide with product whether that's
acceptable or whether the migration must special-case it.
