import * as THREE from 'three';
import './style.css';
import { createBird } from './bird.js';
import { sfx, music, audio, renderMusic } from './audio.js';
import { createParticles } from './effects.js';
import { POWERUPS, POWERUP_TYPES, createPowerupPickup, animatePickup } from './powerups.js';
import { progress, SKINS, TRAILS, ACHIEVEMENTS } from './progress.js';
import { BIOMES, createBiomeBlender } from './biomes.js';
import {
  LANES,
  PIPE_RADIUS,
  createScene,
  createGround,
  createScenery,
  createClouds,
  createGate,
  createCoin,
} from './world.js';

// --- Tuning -----------------------------------------------------------------
const GRAVITY = 36;
const FLAP_VELOCITY = 11.5;
const SWITCH_HOP = 6; // tapping another lane only hops a little (no overshoot)
const WARMUP_GATES = 6; // first rows are wide and all lanes open
const MAX_FALL = -22;
const CEILING = 14;
const BIRD_RADIUS = 0.5;
const MINI_RADIUS = 0.3;
const BIRD_SCALE = 1.1;
const MINI_SCALE = 0.6;
const LANE_SWITCH_SPEED = 18; // ~0.17 s to reach 95 % of a lane change
const SPAWN_DISTANCE = 170;
const FIRST_GATE_Z = -60;
const COIN_RADIUS = 1.1;
const MAGNET_RANGE = 9;
const STAR_SPEED_BOOST = 1.35;
const GRACE_TIME = 1.2; // invulnerable blinking after the rainbow ends
const FALLBACK_BPM = 124;
const ZONE_ROWS = 10; // a new zone (place + time of day) every 10 rows, opened by a coin rush
const NEAR_MISS = 0.45; // gap clearance below which a pass counts as "Knapp!"
const HIT_STOP = 0.14; // freeze-frame on impact (s)

// Short haptic pulses (Android); silently ignored where unsupported.
function buzz(ms) {
  if (audio.muted) return;
  try { navigator.vibrate?.(ms); } catch { /* ignore */ }
}

// --- Setup ------------------------------------------------------------------
const canvas = document.getElementById('game');
const renderer = new THREE.WebGLRenderer({ canvas, antialias: true });
// Adaptive quality: if a phone can't hold ~50 fps, lower the render
// resolution step by step (and finally drop shadows). The chosen level is
// remembered for the next launch.
const QUALITY_DPR = [Math.min(window.devicePixelRatio, 2), 1.5, 1.25, 1];
let quality = 0;
try {
  quality = Math.min(QUALITY_DPR.length, Number(localStorage.getItem('birdy-quality')) || 0);
} catch { /* storage unavailable */ }
renderer.setPixelRatio(Math.min(QUALITY_DPR[0], QUALITY_DPR[Math.min(quality, QUALITY_DPR.length - 1)]));
renderer.shadowMap.enabled = true;
renderer.shadowMap.type = THREE.PCFSoftShadowMap;
if (quality >= QUALITY_DPR.length) renderer.shadowMap.enabled = false;

const scene = createScene();
const camera = new THREE.PerspectiveCamera(60, 1, 0.1, 400);

const ground = createGround(scene);
const scenery = createScenery(scene);
const clouds = createClouds(scene);
const particles = createParticles(scene);
const biomes = createBiomeBlender({ scene, ground, scenery, clouds });
const zoneMarks = []; // { z, zone } – where the next zone begins

// Simple blob shadow under the bird, used when real shadows are off (lowest
// quality level) so the flight height stays readable.
const blob = new THREE.Mesh(
  new THREE.CircleGeometry(0.7, 20),
  new THREE.MeshBasicMaterial({ color: 0x000000, transparent: true, opacity: 0.22, depthWrite: false })
);
blob.rotation.x = -Math.PI / 2;
blob.position.y = 0.03;
blob.visible = false;
scene.add(blob);
function setShadows(on) {
  renderer.shadowMap.enabled = on;
  blob.visible = !on;
  scene.traverse((o) => { if (o.material) o.material.needsUpdate = true; });
}
if (!renderer.shadowMap.enabled) blob.visible = true;

// Height marker: a small ring at the next row, in the bird's lane and at the
// bird's current height. Green = would pass right now, red = would hit.
// Makes the gap height readable despite the depth of the 3D view.
const MARKER_OK = new THREE.Color(0x8cff5a);
const MARKER_BAD = new THREE.Color(0xff4a3d);
const marker = new THREE.Mesh(
  new THREE.RingGeometry(0.2, 0.36, 20),
  new THREE.MeshBasicMaterial({ color: MARKER_OK, transparent: true, opacity: 0, depthTest: false, depthWrite: false })
);
marker.renderOrder = 10;
marker.visible = false;
scene.add(marker);

function updateMarker(next, r) {
  const z = next ? next.group.position.z : -999;
  marker.visible = !!next && state.mode === 'playing' && state.power.star <= 0 && z > -48;
  if (!marker.visible) return;
  const lane = next.lanes[state.lane];
  const ok = !lane.blocked && state.y - r * 0.8 > lane.hitLow && state.y + r * 0.8 < lane.hitHigh;
  marker.material.color.copy(ok ? MARKER_OK : MARKER_BAD);
  marker.material.opacity = 0.9 * THREE.MathUtils.clamp((z + 48) / 16, 0, 1);
  marker.position.set(LANES[state.lane], state.y, z + PIPE_RADIUS + 0.3);
  // Keep it readable in the distance; bigger and pulsing in the tutorial.
  const pulse = tut.active ? 1.6 + 0.25 * Math.sin(state.time * 8) : 1;
  marker.scale.setScalar(Math.max(1, -z / 14) * pulse);
  marker.quaternion.copy(camera.quaternion);
}

const bird = createBird();
bird.group.scale.setScalar(BIRD_SCALE);
bird.setSkin(progress.skin);
scene.add(bird.group);

const gates = Array.from({ length: 12 }, () => {
  const g = createGate(scene);
  g.group.visible = false;
  g.active = false;
  return g;
});
const coins = Array.from({ length: 60 }, () => createCoin(scene));
const pickups = POWERUP_TYPES.flatMap((type) => [createPowerupPickup(scene, type), createPowerupPickup(scene, type)]);

// --- DOM --------------------------------------------------------------------
const $ = (id) => document.getElementById(id);
const app = $('app');
const hud = $('hud');
const scoreEl = $('score');
const coinCountEl = $('coin-count');
const coinsEl = $('coins');
const startEl = $('start');
const overEl = $('gameover');
const pauseEl = $('pause');
const zonesEl = $('zones');
const laneDots = [...document.querySelectorAll('#lanes span')];
const muteBtn = $('mute');

const flash = document.createElement('div');
flash.id = 'flash';
app.appendChild(flash);
// Phones held sideways get a "rotate" hint and the game pauses.
const landscapeTouch = window.matchMedia('(orientation: landscape) and (pointer: coarse)');

// Power-up chips in the HUD (icon + remaining-time bar).
const powersEl = $('powers');
const powerChips = {};
for (const type of POWERUP_TYPES) {
  const chip = document.createElement('div');
  chip.className = 'power hidden';
  chip.innerHTML = `<span class="power-icon">${POWERUPS[type].icon}</span><span class="power-bar"><i></i></span>`;
  powersEl.appendChild(chip);
  powerChips[type] = { chip, fill: chip.querySelector('i') };
}

// --- Menus: start, skin shop, missions -------------------------------------
const walletEl = $('wallet');
const walletCount = $('wallet-count');
const shopEl = $('shop');
const skinsEl = $('skins');
const shopName = $('shop-name');
const shopAction = $('shop-action');
const toastEl = $('toast');
let shopSel = progress.skin.id;

function renderWallet(bump = false) {
  walletCount.textContent = progress.coins;
  if (bump) {
    walletEl.classList.remove('bump');
    void walletEl.offsetWidth;
    walletEl.classList.add('bump');
  }
}

function missionHTML(m, isNew = false) {
  const pct = Math.round((m.progress / m.goal) * 100);
  return `<div class="mission${m.done ? ' done' : ''}${isNew ? ' new' : ''}">
    <span class="text">${m.text}</span><span class="reward">+${m.reward}</span>
    <span class="bar"><i style="width:${pct}%"></i></span></div>`;
}

function renderStart() {
  $('best-start').textContent = progress.best;
  // First runs explain the controls; afterwards the daily missions.
  const firstRuns = progress.runs < 2;
  $('howto').classList.toggle('hidden', !firstRuns);
  const ms = $('missions-start');
  ms.classList.toggle('hidden', firstRuns);
  ms.innerHTML = '<h3>Tagesmissionen</h3>' + progress.missions().map((m) => missionHTML(m)).join('');
  // Daily gift (from the second run on, so the first launch stays simple).
  const giftBtn = $('gift-btn');
  const streakEl = $('streak');
  const gift = !firstRuns && progress.giftAvailable();
  giftBtn.classList.toggle('hidden', !gift);
  if (gift) giftBtn.textContent = `🎁 Tagesgeschenk · +${progress.giftAmount(progress.streak + 1)}`;
  streakEl.classList.toggle('hidden', gift || progress.streak === 0);
  streakEl.textContent = `🔥 Serie: Tag ${progress.streak} · morgen +${progress.giftAmount(progress.streak + 1)}`;
  renderWallet();
}

// Shop with two tabs: bird colours and flight trails.
let shopTab = 'skins';
let previewTrail = null; // trail shown on the hovering bird in the shop
const shopKind = () => (shopTab === 'skins'
  ? { list: SKINS, owns: progress.owns, equipped: progress.skin.id, buy: (id) => progress.buy(id), select: (id) => progress.select(id) }
  : { list: TRAILS, owns: progress.ownsTrail, equipped: progress.trail.id, buy: (id) => progress.buyTrail(id), select: (id) => progress.selectTrail(id) });
const hexColor = (c) => `#${c.toString(16).padStart(6, '0')}`;

function renderShop() {
  const kind = shopKind();
  for (const t of document.querySelectorAll('#shop .tab')) t.classList.toggle('on', t.dataset.tab === shopTab);
  skinsEl.innerHTML = kind.list.map((k) => {
    const owned = kind.owns(k.id);
    const cls = ['skin'];
    if (!owned) cls.push('locked');
    if (k.id === shopSel) cls.push('sel');
    if (k.id === kind.equipped) cls.push('equipped');
    const bg = shopTab === 'skins'
      ? hexColor(k.body)
      : k.colors.length ? `linear-gradient(135deg, ${k.colors.map(hexColor).join(', ')})` : '#cbb968';
    const inner = shopTab === 'trails' && !k.colors.length ? '✕' : '';
    const price = owned ? '' : `<span class="price">${k.price}</span>`;
    return `<button class="${cls.join(' ')}" data-id="${k.id}" aria-label="${k.name}"><span class="dot" style="background:${bg}">${inner}</span>${price}</button>`;
  }).join('');
  const item = kind.list.find((k) => k.id === shopSel);
  shopName.textContent = item.name;
  shopAction.classList.remove('buy');
  shopAction.disabled = false;
  if (!kind.owns(item.id)) {
    shopAction.innerHTML = `Kaufen · ${item.price} <span class="coin-icon" style="display:inline-block;vertical-align:-3px;width:20px;height:20px"></span>`;
    shopAction.classList.add('buy');
    shopAction.disabled = progress.coins < item.price;
  } else if (item.id === kind.equipped) {
    shopAction.textContent = 'Ausgewählt';
    shopAction.disabled = true;
  } else {
    shopAction.textContent = 'Auswählen';
  }
  // Live preview on the 3D bird.
  if (shopTab === 'skins') {
    bird.setSkin(item);
    previewTrail = progress.trail;
  } else {
    bird.setSkin(progress.skin);
    previewTrail = item;
  }
  renderWallet();
}

function openShop(open) {
  state.menu = open ? 'shop' : 'start';
  shopSel = shopKind().equipped;
  shopEl.classList.toggle('hidden', !open);
  startEl.classList.toggle('hidden', open);
  if (open) renderShop();
  else {
    previewTrail = null;
    bird.setSkin(progress.skin);
    renderStart();
  }
}

for (const tab of document.querySelectorAll('#shop .tab')) {
  tab.addEventListener('click', () => {
    shopTab = tab.dataset.tab;
    shopSel = shopKind().equipped;
    sfx.swoosh();
    renderShop();
  });
}
skinsEl.addEventListener('click', (e) => {
  const btn = e.target.closest('.skin');
  if (!btn) return;
  shopSel = btn.dataset.id;
  sfx.swoosh();
  renderShop();
});
shopAction.addEventListener('click', () => {
  const kind = shopKind();
  const item = kind.list.find((k) => k.id === shopSel);
  if (kind.owns(item.id)) kind.select(item.id);
  else if (kind.buy(item.id)) {
    setTimeout(celebrateMenuAchievements, 400);
    sfx.powerup();
    const colors = shopTab === 'skins' ? [item.body] : item.colors.length ? item.colors : [0xffffff];
    particles.emit(bird.group.position, { count: 30, colors: [...colors, 0xffffff, 0xfff176], speed: 6, size: 0.12, life: 0.8, gravity: -4 });
  }
  renderShop();
});
$('shop-btn').addEventListener('click', () => { startAudio(); openShop(true); });

// Achievements overview.
const achEl = $('achievements');
function renderAchievements() {
  const list = progress.achievements();
  $('ach-count').textContent = `${list.filter((a) => a.done).length} / ${list.length}`;
  $('ach-list').innerHTML = list.map((a) => {
    const pct = Math.round((a.value / a.goal) * 100);
    return `<div class="ach${a.done ? ' done' : ''}">
      <span class="ach-icon">${a.done ? a.icon : '🔒'}</span>
      <span class="ach-body"><b>${a.name}</b><small>${a.text}</small>
        <span class="bar"><i style="width:${pct}%"></i></span></span>
      <span class="reward">${a.done ? '✓' : `+${a.reward}`}</span></div>`;
  }).join('');
}
function openAchievements(open) {
  state.menu = open ? 'achievements' : 'start';
  achEl.classList.toggle('hidden', !open);
  startEl.classList.toggle('hidden', open);
  if (open) renderAchievements();
  else renderStart();
}
$('ach-btn').addEventListener('click', () => { startAudio(); sfx.swoosh(); openAchievements(true); });
$('ach-back').addEventListener('click', () => openAchievements(false));

// Achievements earned outside a run (gift streak, unlocks) are paid at once.
function celebrateMenuAchievements() {
  for (const a of progress.checkAchievements()) {
    toast(`🏆 ${a.name} +${a.reward}`);
    sfx.powerup();
  }
  renderWallet(true);
}
$('gift-btn').addEventListener('click', () => {
  startAudio();
  const res = progress.claimGift();
  if (!res) return;
  setTimeout(celebrateMenuAchievements, 600);
  sfx.powerup();
  for (let i = 0; i < 5; i++) setTimeout(() => sfx.coin(), 120 + i * 70);
  particles.emit(bird.group.position, { count: 36, colors: [0xfff176, 0xffd400, 0xffffff], speed: 7, size: 0.13, life: 0.9, gravity: -5 });
  renderStart();
  renderWallet(true);
});
$('shop-back').addEventListener('click', () => openShop(false));
$('menu-btn').addEventListener('click', () => goToMenu());

let toastTimer = 0;
const toastQueue = [];
function toast(text) {
  toastQueue.push(text);
}
function updateToast(dt) {
  if (toastTimer > 0) {
    toastTimer -= dt;
    if (toastTimer <= 0) toastEl.classList.remove('show');
    return;
  }
  if (toastQueue.length && toastTimer <= 0) {
    toastEl.textContent = toastQueue.shift();
    toastEl.classList.add('show');
    toastTimer = 2.2;
  }
}

// --- Game state -------------------------------------------------------------
const state = {
  mode: 'ready', // ready | playing | dead | over
  paused: false,
  x: 0,
  y: 5,
  vy: 0,
  lane: 1,
  radius: BIRD_RADIUS,
  speed: 10,
  distance: 0,
  score: 0,
  coins: 0,
  lastGateZ: 0,
  prevGaps: null,
  gatesSpawned: 0,
  gatesToPower: 6,
  power: { star: 0, magnet: 0, mini: 0 },
  grace: 0,
  wingPhase: 0,
  wingSpeed: 10,
  deadTimer: 0,
  shake: 0,
  time: 0,
  beat: 0,
  runTime: 0,
  hold: false,
  overAt: 0,
  zone: 0,
  rushAt: -1,
  squash: 0, // 1 right after a flap, decays (squash & stretch)
  hitStop: 0,
  nearChain: 0,
  menu: 'start', // start | shop (only while mode is 'ready')
  run: null, // per-run counters for missions
  celebrated: new Set(),
};
let lastRun = null;

// `god` is only set by the store-screenshot script.
const invincible = () => state.power.star > 0 || state.grace > 0 || state.god;

function difficulty() {
  return Math.min(1, state.score / 40);
}

// Rows are spaced by time, not distance: faster play keeps enough time to
// react, switch lanes and climb or drop between two rows.
function baseSpeed() {
  // After the main curve (40 points) the pace keeps creeping up slowly.
  const over = Math.max(0, state.score - 40);
  return 18 + 16 * difficulty() + 8 * (1 - Math.exp(-over / 50));
}
function spacing() {
  return baseSpeed() * (1.7 - 0.6 * difficulty());
}

function resetGame() {
  state.mode = 'playing';
  state.hold = true; // "get ready": hover until the first tap
  state.x = 0;
  state.y = 5;
  state.vy = 0;
  state.lane = 1;
  state.radius = BIRD_RADIUS;
  state.speed = 18;
  state.score = 0;
  state.coins = 0;
  state.prevGaps = null;
  state.gatesSpawned = 0;
  state.gatesToPower = 5 + Math.floor(Math.random() * 3);
  state.power = { star: 0, magnet: 0, mini: 0 };
  state.grace = 0;
  state.deadTimer = 0;
  state.shake = 0;
  state.runTime = 0;
  state.nearChain = 0;
  state.zone = 0;
  state.rushAt = -1;
  zoneMarks.length = 0;
  if (biomes.index !== 0) biomes.set(0, 1.2);
  music.setTheme(0);
  music.setMode('game');
  state.squash = 0;
  state.run = { coins: 0, score: 0, powerups: 0, plants: 0, moving: 0, starRows: 0, near: 0, bestChain: 0, zone: 0 };
  state.celebrated = new Set();
  bird.group.rotation.set(0, 0, 0);
  bird.group.visible = true;
  bird.setGlow(null);
  music.setHype(false);
  particles.clear();

  for (const g of gates) { g.active = false; g.group.visible = false; }
  for (const c of coins) { c.active = false; c.mesh.visible = false; }
  for (const p of pickups) { p.active = false; p.group.visible = false; }
  state.lastGateZ = FIRST_GATE_Z + spacing();
  while (state.lastGateZ > -SPAWN_DISTANCE) spawnGate(state.lastGateZ - spacing());

  scoreEl.textContent = '0';
  coinCountEl.textContent = '0';
  hud.classList.remove('hidden');
  startEl.classList.add('hidden');
  shopEl.classList.add('hidden');
  overEl.classList.add('hidden');
  walletEl.classList.add('hidden');
  state.menu = 'start';
  bird.setSkin(progress.skin);
  updateLaneDots();
  // Briefly show the three tap zones at the start of every run.
  // The tap zones stay visible while the bird waits for the first tap
  // (in the tutorial the ghost hand explains instead).
  zonesEl.classList.remove('show');
  if (tut.active) {
    tut.step = 'flap';
    showHand('flap');
  } else {
    zonesEl.classList.add('hold');
  }
}

// --- First-run tutorial -----------------------------------------------------
// The very first launch skips the menu. A ghost hand shows the two gestures:
// rows 0–2 only have a middle gap (tap the bird to flap), row 3 blocks the
// middle – the game freezes in front of it until the player taps beside the
// bird to dodge.
const TUT_SWITCH_ROW = 3;
const tut = { active: false, step: '', gate: null, freezeY: 0 };
const handEl = document.createElement('div');
handEl.id = 'hand';
handEl.innerHTML = '<span class="finger">👆</span><span class="label"></span>';
app.appendChild(handEl);
const handLabel = handEl.querySelector('.label');

function showHand(mode) {
  handEl.className = mode ? `show ${mode}` : '';
  handLabel.innerHTML = mode === 'flap' ? 'Tippen = flattern' : mode === 'side' ? 'Daneben tippen<br>= ausweichen' : '';
}

function updateHand() {
  if (!handEl.className) return;
  tmpProj.set(state.x, state.y, 0).project(camera);
  const bx = (tmpProj.x + 1) / 2;
  const by = (1 - tmpProj.y) / 2;
  const x = handEl.classList.contains('side') ? Math.max(0.2, bx - 0.28) : bx;
  handEl.style.left = `${x * 100}%`;
  handEl.style.top = `${(by + 0.06) * 100}%`;
}

function tutorialSpec() {
  const gap = { center: 5.2, size: 6.2 };
  if (state.gatesSpawned < TUT_SWITCH_ROW) return [null, { ...gap }, null];
  if (state.gatesSpawned === TUT_SWITCH_ROW) return [{ ...gap }, null, { ...gap }];
  return null;
}

// Build one row of pipes. Later rows add moving gaps and piranha plants.
function gateSpec() {
  if (tut.active) {
    const t = tutorialSpec();
    if (t) return t;
  }
  const d = difficulty();
  // Warm-up: the first rows are extra wide and near the start height so a
  // first-time player gets a few easy successes.
  const warm = Math.max(0, 1 - state.gatesSpawned / WARMUP_GATES);
  const size = 5.2 - 1.4 * d + 1.8 * warm;
  const lo = Math.max(size / 2 + 1.2, THREE.MathUtils.lerp(0, 4.2, warm));
  const hi = Math.max(lo, Math.min(CEILING - 2.5 - size / 2, THREE.MathUtils.lerp(99, 6.5, warm)));
  const spec = LANES.map(() => ({ center: lo + Math.random() * (hi - lo), size }));

  // After a short warm-up, block some lanes (always keep at least one open).
  if (state.score >= 3 && state.gatesSpawned >= WARMUP_GATES) {
    const pBlock = 0.2 + 0.3 * d;
    const order = [0, 1, 2].sort(() => Math.random() - 0.5);
    let open = 3;
    for (const i of order) {
      if (open > 1 && Math.random() < pBlock) {
        spec[i] = null;
        open--;
      }
    }
  }

  // Each zone has a speciality (the zone this row will be in).
  const zone = Math.floor(state.gatesSpawned / ZONE_ROWS) % BIOMES.length;
  const moveBoost = zone === 2 ? 1.6 : 1;
  const plantBoost = zone === 3 ? 1.8 : 1;
  const pulseChance = zone === 1 ? 0.45 : state.gatesSpawned > ZONE_ROWS * BIOMES.length ? 0.15 : 0;

  const open = spec.map((g, i) => (g ? i : -1)).filter((i) => i >= 0);
  // Keep one open lane "easy" (no plant, no movement).
  const easy = open[Math.floor(Math.random() * open.length)];
  for (const i of open) {
    if (i === easy && open.length > 1) continue;
    const g = spec[i];
    if (pulseChance && Math.random() < pulseChance) {
      // Breathing gap (Herbstwald): opens and narrows with the beat.
      g.pulse = true;
      g.size = Math.max(g.size, 4.6);
      g.plantOffset = Math.random() < 0.5 ? 0 : 1;
    } else if (state.score >= 6 && Math.random() < (0.25 + 0.3 * d) * moveBoost) {
      // Moving gap: slides up and down within the playable range.
      g.amp = Math.min(1.2 + Math.random() * 1.3, (hi - lo) / 2);
      g.center = THREE.MathUtils.clamp(g.center, lo + g.amp, hi - g.amp);
      g.speed = (1.2 + Math.random() * 1.2) * (zone === 2 ? 1.25 : 1);
      g.phase = Math.random() * Math.PI * 2;
    } else if (state.score >= 10 && Math.random() < (0.25 + 0.25 * d) * plantBoost) {
      // Piranha plant: pops out of the lower pipe in time with the music.
      g.plant = true;
      g.plantOffset = Math.random() < 0.5 ? 0 : 2;
    }
  }
  makeReachable(spec, lo, hi);
  return spec;
}

// Fairness: from every gap of the previous row at least one gap of this row
// must be reachable in the time between the rows. Falling is quicker than
// climbing, and a lane switch costs extra time.
function makeReachable(spec, lo, hi) {
  const prev = state.prevGaps;
  if (!prev) return;
  const t = spacing() / baseSpeed();
  const maxDrop = 1 + 3.5 * t;
  const maxRise = 0.8 + 3 * t;
  const SWITCH_FACTOR = 0.6;
  // Each lane step is one tap with a small hop, so moving two lanes costs more.
  const reach = (from, to, steps) => {
    const k = SWITCH_FACTOR ** steps;
    const d = to - from;
    return d <= maxRise * k && -d <= maxDrop * k;
  };
  // A moving gap only counts if its whole travel range is in reach.
  const fits = (p, g, steps) =>
    reach(p.center, g.center - (g.amp || 0), steps) && reach(p.center, g.center + (g.amp || 0), steps);
  prev.forEach((p, j) => {
    if (!p) return;
    const open = spec.map((g, i) => (g ? i : -1)).filter((i) => i >= 0);
    if (open.some((i) => fits(p, spec[i], Math.abs(i - j)))) return;
    // Pull the closest open lane (same lane preferred) into reach and stop
    // it from moving.
    const i = open.sort((a, b) => Math.abs(a - j) - Math.abs(b - j))[0];
    const g = spec[i];
    const k = SWITCH_FACTOR ** Math.abs(i - j);
    g.amp = 0;
    g.center = THREE.MathUtils.clamp(g.center, p.center - maxDrop * k * 0.9, p.center + maxRise * k * 0.9);
    g.center = THREE.MathUtils.clamp(g.center, lo, hi);
  });
}

function placeCoin(x, y, z) {
  const c = coins.find((c) => !c.active);
  if (!c) return;
  c.active = true;
  c.mesh.visible = true;
  c.mesh.position.set(x, y, z);
}

function placePickup(x, y, z) {
  const type = POWERUP_TYPES[Math.floor(Math.random() * POWERUP_TYPES.length)];
  const p = pickups.find((p) => !p.active && p.type === type);
  if (!p) return false;
  p.active = true;
  p.group.visible = true;
  p.group.position.set(x, y, z);
  return true;
}

// Zone change: a pipe-free stretch with a wave of coins across the lanes,
// then the sky blends into the next time of day.
function spawnRush(z) {
  const gap = spacing();
  const pattern = [1, 1, 0, 0, 1, 2, 2, 1, 1];
  pattern.forEach((lane, k) => {
    const t = k / (pattern.length - 1);
    placeCoin(LANES[lane], 5 + Math.sin(t * Math.PI * 2) * 1.6, z + gap * 0.45 - t * gap * 0.9);
  });
  const zone = state.zone + zoneMarks.length + 1;
  zoneMarks.push({ z: z + gap * 0.45, zone });
  // Switch the scenery now: chunks wrapping from here on are built in the new
  // theme, so the new place starts right where the banner appears.
  scenery.setTheme(BIOMES[zone % BIOMES.length].scenery);
  state.prevGaps = null; // plenty of time after the rush: no reach limit
}

function spawnGate(z) {
  state.lastGateZ = z; // always advance, even if the pool is exhausted
  if (state.gatesSpawned > 0 && state.gatesSpawned % ZONE_ROWS === 0 && state.rushAt !== state.gatesSpawned) {
    state.rushAt = state.gatesSpawned;
    spawnRush(z);
    return;
  }
  const gate = gates.find((g) => !g.active);
  if (!gate) return;
  const spec = gateSpec();
  gate.active = true;
  gate.configure(z, spec);
  if (tut.active && state.gatesSpawned === TUT_SWITCH_ROW) tut.gate = gate;
  state.gatesSpawned++;

  // Coins inside some (static) gaps.
  spec.forEach((g, i) => {
    if (g && !g.amp && Math.random() < 0.3) placeCoin(LANES[i], g.center, z);
  });

  const prev = state.prevGaps;
  const calm = [0, 1, 2].filter((i) => prev && prev[i] && spec[i] && !prev[i].amp && !spec[i].amp);

  // Now and then a power-up floats between two rows.
  let pickupPlaced = false;
  if (--state.gatesToPower <= 0 && calm.length) {
    const i = calm[Math.floor(Math.random() * calm.length)];
    pickupPlaced = placePickup(LANES[i], (prev[i].center + spec[i].center) / 2, z + spacing() / 2);
    state.gatesToPower = 6 + Math.floor(Math.random() * 4);
  }

  // A guiding trail of coins leading from the previous row into this one.
  if (!pickupPlaced && calm.length && Math.random() < 0.55) {
    const i = calm[Math.floor(Math.random() * calm.length)];
    const gap = spacing();
    for (let k = 1; k <= 4; k++) {
      const t = k / 5;
      placeCoin(LANES[i], THREE.MathUtils.lerp(prev[i].center, spec[i].center, t), z + gap * (1 - t));
    }
  }

  state.prevGaps = spec;
  state.lastGateZ = z;
}

// --- Input ------------------------------------------------------------------
// One gesture on touch: tap the left, middle or right third of the screen to
// fly into that lane and flap at the same time.
function startAudio() {
  audio.unlock();
  music.start();
}

function setPaused(paused) {
  state.paused = paused;
  pauseEl.classList.toggle('hidden', !paused);
  audio.setSuspended(paused);
}

function flap() {
  if (state.paused) {
    setPaused(false);
    return;
  }
  if (state.mode === 'ready') {
    resetGame();
    return;
  }
  if (state.mode !== 'playing') return;
  if (state.hold) {
    state.hold = false;
    zonesEl.classList.remove('hold');
    if (tut.active) {
      tut.step = 'fly';
      showHand(null);
    } else {
      void zonesEl.offsetWidth;
      zonesEl.classList.add('show');
    }
  }
  state.vy = FLAP_VELOCITY;
  state.wingSpeed = 38;
  state.squash = 1;
  sfx.flap();
}

// Touch controls are relative to where the bird is drawn: a tap near the
// bird flaps, a tap left or right of it moves one lane that way with a small
// hop (so dodging sideways never throws the bird into the upper pipe).
const OWN_ZONE = 0.18; // half-width of the "flap" zone around the bird (screen fraction)
const tmpProj = new THREE.Vector3();
// Zones are centred on the lane the bird is heading to (not its in-between
// position during a switch), so quick tap sequences behave predictably.
function birdScreenX() {
  tmpProj.set(LANES[state.lane], state.y, 0).project(camera);
  return (tmpProj.x + 1) / 2;
}

// The zone hint follows the bird: flap zone around it, dodge zones beside it
// (none towards the edge in an outer lane).
const zoneCols = [...zonesEl.children];
let zonesKey = '';
function updateZonesOverlay() {
  if (!zonesEl.classList.contains('show') && !zonesEl.classList.contains('hold')) return;
  const bx = birdScreenX();
  const left = state.lane > 0 ? Math.max(0, bx - OWN_ZONE) : 0;
  const right = state.lane < LANES.length - 1 ? Math.max(0, 1 - (bx + OWN_ZONE)) : 0;
  const mid = 1 - left - right;
  const key = `${left.toFixed(3)}|${right.toFixed(3)}`;
  if (key === zonesKey) return;
  zonesKey = key;
  zonesEl.style.gridTemplateColumns = `${left}fr ${mid}fr ${right}fr`;
  zoneCols[0].style.visibility = left > 0.05 ? '' : 'hidden';
  zoneCols[2].style.visibility = right > 0.05 ? '' : 'hidden';
}

// Small ripple where the finger touched, showing what the tap did.
const tapFxPool = Array.from({ length: 4 }, () => {
  const el = document.createElement('div');
  el.className = 'tap-fx';
  app.appendChild(el);
  return el;
});
let tapFxNext = 0;
function tapFx(x, y, dir) {
  const el = tapFxPool[tapFxNext];
  tapFxNext = (tapFxNext + 1) % tapFxPool.length;
  el.textContent = dir < 0 ? '◀' : dir > 0 ? '▶' : '▲';
  el.style.left = `${x}px`;
  el.style.top = `${y}px`;
  el.classList.remove('show');
  void el.offsetWidth;
  el.classList.add('show');
}

function tapDir(dir) {
  if (state.hold) return flap(); // first tap just starts
  if (tut.step === 'switch') {
    if (dir === 0) return; // frozen until the player taps beside the bird
    tut.step = 'go';
    showHand(null);
  }
  const target = state.lane + dir;
  if (state.mode === 'playing' && !state.paused && dir !== 0 && target >= 0 && target < LANES.length) {
    setLane(target);
    state.vy = Math.max(state.vy, SWITCH_HOP);
    state.wingSpeed = 26;
    state.squash = 0.5;
    return;
  }
  flap();
}

// Lane-based variant used by the playtest bots: one step towards `lane`.
function tap(lane) {
  tapDir(Math.sign(lane - state.lane));
}

function updateLaneDots() {
  laneDots.forEach((d, i) => d.classList.toggle('on', i === state.lane));
}

function setLane(lane) {
  if (state.mode !== 'playing') return;
  const next = THREE.MathUtils.clamp(lane, 0, LANES.length - 1);
  if (next !== state.lane) {
    state.lane = next;
    updateLaneDots();
    sfx.swoosh();
  }
}

function tryRestart() {
  if (state.mode === 'over' && performance.now() - state.overAt > 350) resetGame();
}

window.addEventListener('keydown', (e) => {
  if (e.repeat) return;
  startAudio();
  switch (e.code) {
    case 'Space':
    case 'ArrowUp':
    case 'KeyW':
      e.preventDefault();
      if (state.mode === 'over') tryRestart();
      else flap();
      break;
    case 'ArrowLeft':
    case 'KeyA':
      setLane(state.lane - 1);
      break;
    case 'ArrowRight':
    case 'KeyD':
      setLane(state.lane + 1);
      break;
    case 'Enter':
      tryRestart();
      break;
  }
});

canvas.addEventListener('pointerdown', (e) => {
  startAudio();
  if (state.paused) return setPaused(false);
  const rect = canvas.getBoundingClientRect();
  const d = (e.clientX - rect.left) / rect.width - birdScreenX();
  const dir = Math.abs(d) <= OWN_ZONE ? 0 : Math.sign(d);
  const wasPlaying = state.mode === 'playing' && !state.hold;
  tapDir(dir);
  if (wasPlaying) tapFx(e.clientX - rect.left, e.clientY - rect.top, dir);
});
// Tapping the free area of the start screen starts right away; the panel's
// buttons do their own thing.
startEl.addEventListener('pointerdown', (e) => {
  startAudio();
  if (e.target.closest('.panel')) return;
  flap();
});
$('play-btn').addEventListener('click', () => {
  startAudio();
  flap();
});
pauseEl.addEventListener('pointerdown', () => setPaused(false));

// Game over: tap anywhere (except "Menü") to go again, after a short delay
// so a panicked tap at the moment of death doesn't skip the screen.
overEl.addEventListener('pointerdown', (e) => {
  startAudio();
  if (e.target.closest('#menu-btn')) return;
  if (e.target.closest('#next-unlock.ready')) {
    // Straight to the shop when something can be unlocked.
    goToMenu();
    openShop(true);
    return;
  }
  if (state.mode === 'over' && performance.now() - state.overAt > 350) resetGame();
});

function renderMute() {
  muteBtn.textContent = audio.muted ? '🔇' : '🔊';
  muteBtn.setAttribute('aria-label', audio.muted ? 'Ton an' : 'Ton aus');
}
muteBtn.addEventListener('pointerdown', (e) => e.stopPropagation());
muteBtn.addEventListener('click', () => {
  startAudio();
  audio.setMuted(!audio.muted);
  renderMute();
});
renderMute();

// Android back button: pause a run, leave a paused run / shop / game over
// to the menu, and close the app from the menu.
function goToMenu() {
  setPaused(false);
  state.mode = 'ready';
  state.hold = false;
  music.setMode('menu');
  hud.classList.add('hidden');
  overEl.classList.add('hidden');
  shopEl.classList.add('hidden');
  achEl.classList.add('hidden');
  startEl.classList.remove('hidden');
  walletEl.classList.remove('hidden');
  zonesEl.classList.remove('hold', 'show');
  marker.visible = false;
  showHand(null);
  bird.group.rotation.set(0, 0, 0);
  bird.group.visible = true;
  state.y = 5;
  state.menu = 'start';
  bird.setSkin(progress.skin);
  // Clear the track so the menu shows only the bird and scenery.
  for (const g of gates) { g.active = false; g.group.visible = false; }
  for (const c of coins) { c.active = false; c.mesh.visible = false; }
  for (const pu of pickups) { pu.active = false; pu.group.visible = false; }
  renderStart();
}
function handleBack(exitApp) {
  if (state.mode === 'playing' && !state.paused) setPaused(true);
  else if (state.paused || state.mode === 'over' || state.mode === 'dead') goToMenu();
  else if (state.menu === 'shop') openShop(false);
  else if (state.menu === 'achievements') openAchievements(false);
  else exitApp();
}
if (window.Capacitor?.isNativePlatform?.()) {
  import('@capacitor/app').then(({ App }) => {
    App.addListener('backButton', () => handleBack(() => App.exitApp()));
  });
}

// Pause when the app goes to the background (e.g. home button on Android).
document.addEventListener('visibilitychange', () => {
  if (document.hidden) {
    if (state.mode === 'playing') setPaused(true);
    else audio.setSuspended(true);
  } else if (!state.paused) {
    audio.setSuspended(false);
  }
});

// --- Game events ------------------------------------------------------------
const birdPos = new THREE.Vector3();
const tmpColor = new THREE.Color();

function die(cause) {
  if (state.mode !== 'playing') return;
  state.mode = 'dead';
  marker.visible = false;
  showHand(null);
  state.hitStop = HIT_STOP;
  buzz(70);
  lastRun = { score: state.score, coins: state.coins, time: state.runTime, cause, zone: state.zone };
  state.deadTimer = 0;
  state.shake = 0.5;
  state.vy = Math.max(state.vy, 6);
  sfx.hit();
  music.duck();
  music.setHype(false);
  bird.setGlow(null);
  particles.emit(birdPos, {
    count: 28, colors: [0xf7d23e, 0xfff3c4, 0xf57c21], speed: 8, size: 0.14, life: 1.2, gravity: -9,
  });
  flash.style.transition = 'none';
  flash.style.opacity = '0.9';
  requestAnimationFrame(() => {
    flash.style.transition = 'opacity 0.35s';
    flash.style.opacity = '0';
  });
}

function showGameOver() {
  state.mode = 'over';
  music.setMode('menu');
  state.overAt = performance.now();
  state.run.coins = state.coins;
  state.run.score = state.score;
  const { isBest, completed, achievements } = progress.finishRun(state.run);
  $('final-score').textContent = state.score;
  $('final-coins').textContent = state.coins;
  $('final-best').textContent = progress.best;
  $('new-best').classList.toggle('hidden', !isBest);
  if (isBest && state.score > 0) {
    // Record fanfare.
    setTimeout(() => sfx.powerup(), 250);
    buzz(30);
  }
  const toBest = $('to-best');
  const missing = progress.best - state.score;
  toBest.classList.toggle('hidden', isBest || missing > 15 || progress.best < 5);
  toBest.textContent = missing === 0 ? 'Rekord eingestellt!' : `Nur noch ${missing + 1} bis zum Rekord!`;
  // Next goals: all of today's missions (just completed ones pop in), the
  // zone reached and the next thing to unlock.
  const doneNow = new Set(completed.map((m) => m.id));
  $('missions-done').innerHTML =
    achievements.map((a) => `<div class="mission done new achievement"><span class="text">${a.icon} Erfolg: ${a.name}</span><span class="reward">+${a.reward}</span></div>`).join('') +
    progress.missions().map((m) => missionHTML(m, doneNow.has(m.id))).join('');
  $('zone-reached').textContent = `Zone ${state.zone + 1} erreicht: ${BIOMES[state.zone % BIOMES.length].name}`;
  renderNextUnlock();
  hud.classList.add('hidden');
  overEl.classList.remove('hidden');
  walletEl.classList.remove('hidden');
  renderWallet(completed.length > 0 || achievements.length > 0 || state.coins > 0);
}

// The cheapest cosmetic not owned yet, as a goal on the game-over screen.
function renderNextUnlock() {
  const el = $('next-unlock');
  const items = [
    ...SKINS.filter((k) => !progress.owns(k.id)).map((k) => ({ ...k, kind: 'Vogel' })),
    ...TRAILS.filter((t) => !progress.ownsTrail(t.id)).map((t) => ({ ...t, kind: 'Spur' })),
  ].sort((a, b) => a.price - b.price);
  const next = items[0];
  el.classList.toggle('hidden', !next);
  if (!next) return;
  const pct = Math.min(100, Math.round((progress.coins / next.price) * 100));
  el.classList.toggle('ready', progress.coins >= next.price);
  el.innerHTML = progress.coins >= next.price
    ? `<span class="text">✨ ${next.kind} „${next.name}“ jetzt freischaltbar! ›</span><span class="bar"><i style="width:100%"></i></span>`
    : `<span class="text">Noch ${next.price - progress.coins} 🪙 bis ${next.kind} „${next.name}“</span><span class="bar"><i style="width:${pct}%"></i></span>`;
}

function addScore(gate) {
  state.score++;
  if (state.score === progress.best + 1 && progress.best >= 5) {
    // Beat the record mid-run: celebrate right away.
    toast('🏆 Neuer Rekord!');
    sfx.powerup();
    buzz(30);
  }
  const lane = gate.lanes[state.lane];
  if (lane.hasPlant) state.run.plants++;
  if (lane.amp) state.run.moving++;
  if (state.power.star > 0) state.run.starRows++;
  checkMissions();
  scoreEl.textContent = state.score;
  scoreEl.classList.remove('pop');
  void scoreEl.offsetWidth; // restart the CSS animation
  scoreEl.classList.add('pop');
  sfx.point();
}

// Close call: reward with a coin, a chirp that climbs with each consecutive
// close call, a tiny buzz and a floating "Knapp!" label.
const popupEl = document.createElement('div');
popupEl.id = 'popup';
app.appendChild(popupEl);
function popup(text) {
  tmpProj.set(state.x, state.y + 1.2, 0).project(camera);
  popupEl.style.left = `${((tmpProj.x + 1) / 2) * 100}%`;
  popupEl.style.top = `${((1 - tmpProj.y) / 2) * 100}%`;
  popupEl.textContent = text;
  popupEl.classList.remove('show');
  void popupEl.offsetWidth;
  popupEl.classList.add('show');
}
function bumpCoins() {
  coinsEl.classList.remove('bump');
  void coinsEl.offsetWidth;
  coinsEl.classList.add('bump');
}

function nearMiss() {
  state.nearChain++;
  state.run.near++;
  state.run.bestChain = Math.max(state.run.bestChain, state.nearChain);
  state.coins++;
  coinCountEl.textContent = state.coins;
  bumpCoins();
  sfx.near(state.nearChain - 1);
  buzz(15);
  popup(state.nearChain > 1 ? `Knapp! ×${state.nearChain}` : 'Knapp!');
  particles.emit(birdPos, { count: 10, colors: [0xffffff, 0xfff176], speed: 5, size: 0.09, life: 0.4, gravity: 0 });
  checkMissions();
}

const zoneBanner = document.createElement('div');
zoneBanner.id = 'zone-banner';
app.appendChild(zoneBanner);
function enterZone(zone) {
  state.zone = zone;
  const b = biomes.set(zone, 3);
  music.setTheme(zone);
  zoneBanner.innerHTML = `<small>Zone ${zone + 1}</small>${b.name}`;
  zoneBanner.classList.remove('show');
  void zoneBanner.offsetWidth;
  zoneBanner.classList.add('show');
  sfx.zone();
  if (state.run) {
    state.run.zones = Math.max(state.run.zones || 0, zone);
    state.run.zone = Math.max(state.run.zone, zone);
    checkMissions();
  }
}

// Celebrate a daily mission the moment it is reached.
function checkMissions() {
  const run = { ...state.run, coins: state.coins, score: state.score };
  for (const id of progress.wouldComplete(run)) {
    if (state.celebrated.has(id)) continue;
    state.celebrated.add(id);
    const m = progress.missions().find((x) => x.id === id);
    toast(`✓ ${m.text} +${m.reward}`);
    sfx.powerup();
  }
  for (const a of progress.wouldUnlock(run)) {
    if (state.celebrated.has(`a:${a.id}`)) continue;
    state.celebrated.add(`a:${a.id}`);
    toast(`🏆 ${a.name} +${a.reward}`);
    sfx.powerup();
    buzz(25);
  }
}

function activatePower(type) {
  state.power[type] = POWERUPS[type].duration;
  if (state.run) {
    state.run.powerups++;
    checkMissions();
  }
  if (type === 'star') {
    music.setHype(true);
    state.grace = 0;
  }
  sfx.powerup();
  particles.emit(birdPos, { count: 24, colors: [POWERUPS[type].color, 0xffffff], speed: 7, size: 0.13, life: 0.6, gravity: 0 });
}

function updatePowers(dt) {
  for (const type of POWERUP_TYPES) {
    const left = state.power[type];
    const { chip, fill } = powerChips[type];
    chip.classList.toggle('hidden', left <= 0);
    if (left <= 0) continue;
    state.power[type] = Math.max(0, left - dt);
    fill.style.width = `${(state.power[type] / POWERUPS[type].duration) * 100}%`;
    chip.classList.toggle('ending', state.power[type] < 1.5);
    if (state.power[type] === 0) {
      sfx.powerdown();
      if (type === 'star') {
        music.setHype(false);
        bird.setGlow(null);
        state.grace = GRACE_TIME; // time to get clear of any pipe
      }
    }
  }
  state.grace = Math.max(0, state.grace - dt);
}

// --- Game loop --------------------------------------------------------------
function moveWorld(dz) {
  state.distance += dz;
  ground.update(state.distance);
  scenery.update(dz);
  clouds.update(dz);
}

function updatePlaying(dt) {
  if (state.hold) {
    // Get ready: hover in place, ground and scenery keep scrolling.
    moveWorld(8 * dt);
    state.y = 5 + Math.sin(state.time * 3) * 0.35;
    state.vy = Math.cos(state.time * 3) * 1.05;
    return;
  }
  if (tut.active) {
    if (tut.step === 'fly' && tut.gate && tut.gate.group.position.z > -13) {
      if (tut.gate.lanes[state.lane].blocked) {
        tut.step = 'switch';
        tut.freezeY = state.y;
        showHand('side');
      } else {
        tut.step = 'go'; // already dodged on their own
      }
    }
    if (tut.step === 'switch') {
      // Frozen in front of the blocked row; the bird just hovers.
      state.y = tut.freezeY + Math.sin(state.time * 3) * 0.15;
      state.vy = 0;
      return;
    }
    if (tut.step === 'go' && tut.gate && tut.gate.passed) {
      tut.active = false;
      tut.step = '';
      progress.finishTutorial();
      toast('Super! Jetzt allein weiter 🎉');
      sfx.powerup();
    }
  }
  state.runTime += dt;
  const d = difficulty();
  const boost = state.power.star > 0 ? STAR_SPEED_BOOST : 1;
  state.speed = THREE.MathUtils.lerp(state.speed, baseSpeed() * boost, dt * (boost > 1 ? 3 : 0.8));
  const dz = state.speed * dt;
  moveWorld(dz);
  updatePowers(dt);

  const targetRadius = state.power.mini > 0 ? MINI_RADIUS : BIRD_RADIUS;
  state.radius = THREE.MathUtils.lerp(state.radius, targetRadius, Math.min(1, dt * 8));
  const r = state.radius;

  // Bird physics.
  state.vy = Math.max(MAX_FALL, state.vy - GRAVITY * dt);
  state.y += state.vy * dt;
  if (state.y > CEILING) {
    state.y = CEILING;
    state.vy = Math.min(state.vy, 0);
  }
  const targetX = LANES[state.lane];
  state.x += (targetX - state.x) * Math.min(1, dt * LANE_SWITCH_SPEED);
  birdPos.set(state.x, state.y, 0);

  // Gates: move, animate, score, fade, collide, recycle.
  let next = null;
  for (const gate of gates) {
    if (!gate.active) continue;
    const gz = (gate.group.position.z += dz);
    if (gz > 20) {
      gate.active = false;
      gate.group.visible = false;
      continue;
    }
    gate.update(state.time, state.beat, dt);
    if (!gate.passed && gz > PIPE_RADIUS + r) {
      gate.passed = true;
      gate.popTime = 0;
      addScore(gate);
      if (state.mode === 'playing' && !invincible() && gate.minClear !== undefined && gate.minClear < NEAR_MISS) nearMiss();
      else state.nearChain = 0;
      gate.minClear = undefined;
    }
    // Passed rows fade out so they don't hide what's coming next.
    // Passed rows fade out completely so nothing blocks the view ahead.
    gate.setOpacity(THREE.MathUtils.lerp(gate.opacity, gate.passed ? 0 : 1, Math.min(1, dt * 12)));
    if (gate.passed && gate.opacity < 0.03) gate.group.visible = false;
    if (!gate.passed && (!next || gz > next.group.position.z)) next = gate;

    if (!invincible() && Math.abs(gz) < PIPE_RADIUS + 0.25 + r) {
      for (const lane of gate.lanes) {
        if (Math.hypot(state.x - lane.x, gz) > PIPE_RADIUS + 0.15 + r) continue;
        if (!lane.blocked) {
          // Track the tightest clearance while inside the pipe (for "Knapp!").
          const clear = Math.min(state.y - r * 0.8 - lane.hitLow, lane.hitHigh - (state.y + r * 0.8));
          gate.minClear = Math.min(gate.minClear ?? Infinity, clear);
        }
        if (lane.blocked) die('blocked');
        else if (state.y + r * 0.8 >= lane.hitHigh) die('pipe-top');
        else if (state.y - r * 0.8 <= lane.hitLow) die(lane.hitLow > lane.gapLow + 0.01 ? 'plant' : 'pipe-bottom');
      }
    }
  }
  updateMarker(next, r);

  // Rings mark the gaps: the next row glows brighter the closer it gets.
  for (const gate of gates) {
    if (!gate.active || gate.passed) continue;
    const gz = gate.group.position.z;
    const near = THREE.MathUtils.clamp(1 + gz / 45, 0, 1);
    // Fade out right in front of the camera instead of filling the screen.
    const close = THREE.MathUtils.clamp((-gz - 3) / 7, 0, 1);
    gate.ringMat.color.set(gate === next ? 0xfff176 : 0xffffff);
    gate.ringMat.opacity = (gate === next ? 0.3 + 0.6 * near : 0.15 * near) * close;
  }

  state.lastGateZ += dz;
  while (state.lastGateZ > -SPAWN_DISTANCE) spawnGate(state.lastGateZ - spacing());

  for (let i = zoneMarks.length - 1; i >= 0; i--) {
    const m = zoneMarks[i];
    m.z += dz;
    if (m.z > 0) {
      zoneMarks.splice(i, 1);
      enterZone(m.zone);
    }
  }

  // Coins (pulled in by the magnet).
  const magnet = state.power.magnet > 0;
  for (const c of coins) {
    if (!c.active) continue;
    const p = c.mesh.position;
    p.z += dz;
    c.mesh.rotation.y += dt * 4;
    if (magnet && p.distanceTo(birdPos) < MAGNET_RANGE && p.z > -MAGNET_RANGE) {
      p.lerp(birdPos, Math.min(1, dt * 7));
    }
    if (p.z > 15) {
      c.active = false;
      c.mesh.visible = false;
    } else if (p.distanceTo(birdPos) < COIN_RADIUS + (r - BIRD_RADIUS)) {
      c.active = false;
      c.mesh.visible = false;
      state.coins++;
      coinCountEl.textContent = state.coins;
      bumpCoins();
      checkMissions();
      sfx.coin();
      particles.emit(p, { count: 8, colors: [0xfff176, 0xffd400, 0xffffff], speed: 5, size: 0.1, life: 0.45, gravity: 0 });
    }
  }

  // Power-up pickups.
  for (const pu of pickups) {
    if (!pu.active) continue;
    const p = pu.group.position;
    p.z += dz;
    animatePickup(pu, state.time);
    if (p.z > 15) {
      pu.active = false;
      pu.group.visible = false;
    } else if (p.distanceTo(birdPos) < 1.4) {
      pu.active = false;
      pu.group.visible = false;
      activatePower(pu.type);
    }
  }

  // Rainbow trail while invincible.
  if (state.power.star > 0) {
    tmpColor.setHSL((state.time * 1.5) % 1, 1, 0.6);
    particles.emitColor(birdPos, tmpColor.getHex(), { count: 2, speed: 0.8, size: 0.18, life: 0.5, gravity: 0 });
  }

  if (state.y - r < 0) {
    state.y = r;
    if (invincible()) {
      state.vy = FLAP_VELOCITY * 0.9;
      sfx.bounce();
    } else {
      die('ground');
    }
  }
}

function updateDead(dt) {
  state.deadTimer += dt;
  if (state.y > state.radius) {
    state.vy = Math.max(MAX_FALL, state.vy - GRAVITY * dt);
    state.y = Math.max(state.radius, state.y + state.vy * dt);
    bird.group.rotation.z += dt * 6;
  }
  // Game over comes quickly; the fall keeps playing behind the panel.
  if (state.mode === 'dead' && state.deadTimer > 0.45) showGameOver();
  for (const gate of gates) if (gate.active) gate.update(state.time, state.beat, dt);
}

const trailPos = new THREE.Vector3();
function updateBirdVisual(dt) {
  const g = bird.group;
  g.position.set(state.x, state.y, 0);
  if (blob.visible) {
    blob.position.x = state.x;
    blob.scale.setScalar(THREE.MathUtils.clamp(1.1 - state.y * 0.04, 0.5, 1) * (g.scale.x / BIRD_SCALE));
  }
  if (state.mode === 'playing' || state.mode === 'ready') {
    const pitch = THREE.MathUtils.clamp(state.vy * 0.06, -0.9, 0.5);
    g.rotation.x = THREE.MathUtils.lerp(g.rotation.x, pitch, Math.min(1, dt * 10));
    const roll = (LANES[state.lane] - state.x) * -0.25;
    g.rotation.z = THREE.MathUtils.lerp(g.rotation.z, roll, Math.min(1, dt * 10));
  } else {
    g.rotation.x = THREE.MathUtils.lerp(g.rotation.x, -1.2, Math.min(1, dt * 5));
  }
  if (state.mode !== 'over') {
    state.wingSpeed = THREE.MathUtils.lerp(state.wingSpeed, 12, dt * 4);
    state.wingPhase += dt * state.wingSpeed;
    bird.animateWings(state.wingPhase);
  }
  const scale = state.power.mini > 0 ? MINI_SCALE : BIRD_SCALE;
  state.baseScale = THREE.MathUtils.lerp(state.baseScale ?? BIRD_SCALE, scale, Math.min(1, dt * 8));
  // Squash & stretch: stretched tall right after a flap, springing back.
  state.squash = Math.max(0, state.squash - dt * 6);
  const q = Math.sin(state.squash * Math.PI) * 0.5 + state.squash * 0.2;
  g.scale.set(state.baseScale * (1 - 0.14 * q), state.baseScale * (1 + 0.24 * q), state.baseScale * (1 - 0.1 * q));
  if (state.mode === 'playing' && state.power.star > 0) {
    bird.setGlow(tmpColor.setHSL((state.time * 1.5) % 1, 1, 0.5), 0.7);
  }
  // Flight trail (cosmetic): during a run, and as a preview in the shop.
  const trail = state.mode === 'playing' && !state.hold && state.power.star <= 0 ? progress.trail
    : state.mode === 'ready' && state.menu === 'shop' ? previewTrail : null;
  if (trail && trail.colors.length) {
    state.trailAcc = (state.trailAcc || 0) + dt * 30;
    const drift = state.mode === 'playing' ? 0 : 6; // the world stands still in the shop
    trailPos.set(state.x, state.y - 0.1, 0.45);
    while (state.trailAcc >= 1) {
      state.trailAcc -= 1;
      particles.emit(trailPos, { count: 1, colors: trail.colors, speed: trail.speed, size: trail.size, life: trail.life, gravity: trail.gravity, drag: 1, drift });
    }
  }
  // Blink during the grace period after the rainbow ends.
  g.visible = !(state.grace > 0 && Math.floor(state.time * 12) % 2 === 0);
}

const camTarget = new THREE.Vector3();
const camLook = new THREE.Vector3();
const camLookCur = new THREE.Vector3(0, 0.5, -9.5);
function updateCamera(dt) {
  const menuFrame = state.mode === 'ready' ? measureMenuFrame() : null;
  if (state.mode === 'ready' && state.menu === 'shop') {
    // Shop: side view of the bird, pulled back when little space is free.
    const far = menuFrame.far * (baseFov < 70 ? 1.15 : 1);
    camTarget.set(5.6 * far, state.y + 0.9, -2.1 * far);
    camLook.set(0, state.y - 0.3, 0);
  } else if (state.mode === 'ready') {
    // Start menu: behind the bird, pulled back when little space is free.
    const far = menuFrame.far;
    camTarget.set(0, state.y + 1.2 * far, 6.5 * far);
    camLook.set(0, state.y - 0.6, -9.5);
  } else {
    // Camera sits above and behind the bird so it stays in the lower third
    // and the gaps ahead remain visible (Temple Run / Subway Surfers style).
    camTarget.set(state.x * 0.5, 7.5 + state.y * 0.6, 14);
    camLook.set(state.x * 0.7, 1.8 + state.y * 0.6, -22);
  }
  const k = Math.min(1, dt * (state.mode === 'ready' ? 3.5 : 6));
  camera.position.lerp(camTarget, k);
  camLookCur.lerp(camLook, k);
  if (state.shake > 0) {
    state.shake = Math.max(0, state.shake - dt);
    const s = state.shake * 0.8;
    camera.position.x += (Math.random() - 0.5) * s;
    camera.position.y += (Math.random() - 0.5) * s;
  }
  camera.lookAt(camLookCur);
  frameBird(menuFrame);
  // Speed kick: the view widens while the rainbow boost is active.
  const fov = baseFov + (state.mode === 'playing' && state.power.star > 0 ? 8 : 0);
  if (Math.abs(camera.fov - fov) > 0.05) {
    camera.fov = THREE.MathUtils.lerp(camera.fov, fov, Math.min(1, dt * 4));
    camera.updateProjectionMatrix();
  }
}

// Menus: find the free space between the title and the panel so the bird
// is framed there on every screen size (small phones included).
function measureMenuFrame() {
  const wrap = state.menu === 'shop' ? shopEl : state.menu === 'achievements' ? achEl : startEl;
  const title = wrap.querySelector('.menu-title');
  const panel = wrap.querySelector('.panel');
  const box = app.getBoundingClientRect();
  if (!title || !panel || !box.height) return { center: 0.4, far: 1 };
  const top = title.getBoundingClientRect().bottom - box.top;
  const bottom = panel.getBoundingClientRect().top - box.top;
  const free = Math.max(0.12, (bottom - top) / box.height);
  return { center: (top + bottom) / 2 / box.height, far: THREE.MathUtils.clamp(0.4 / free, 1, 2.4) };
}

// Shift the rendered view so the bird sits at the free-space centre.
let viewShift = 0;
function frameBird(frame) {
  if (!frame) {
    // Leaving the menu: ease the shift back to zero instead of jumping.
    if (viewShift === 0) return;
    viewShift = THREE.MathUtils.lerp(viewShift, 0, 0.15);
    if (Math.abs(viewShift) < 0.5) {
      viewShift = 0;
      camera.clearViewOffset();
    } else {
      camera.setViewOffset(app.clientWidth, app.clientHeight, 0, viewShift, app.clientWidth, app.clientHeight);
    }
    return;
  }
  camera.clearViewOffset();
  camera.updateMatrixWorld();
  tmpProj.set(state.x, state.y, 0).project(camera);
  const h = app.clientHeight;
  const want = ((1 - tmpProj.y) / 2 - frame.center) * h;
  viewShift = THREE.MathUtils.lerp(viewShift, want, 0.25);
  camera.setViewOffset(app.clientWidth, h, 0, viewShift, app.clientWidth, h);
}

let baseFov = 68;
function resize() {
  const w = app.clientWidth;
  const h = app.clientHeight;
  renderer.setSize(w, h, false);
  camera.aspect = w / h;
  // Narrow portrait screens need a wider vertical FOV to still see all lanes.
  baseFov = camera.aspect < 0.5 ? 74 : 68;
  camera.fov = baseFov;
  camera.updateProjectionMatrix();
}
window.addEventListener('resize', resize);
resize();

const clock = new THREE.Clock();
camera.position.set(0, 6.2, 6.5);
music.setMode('menu'); // calm version until the first run starts
renderStart();

// One fixed game-logic step (no rendering). Shared by the render loop and the
// headless simulation used for automated playtests.
function step(dt, beat) {
  state.time += dt;
  // Plants follow the music's beat; without audio fall back to game time.
  state.beat = beat ?? (state.time * FALLBACK_BPM) / 60;

  if (state.mode === 'ready') {
    moveWorld(10 * dt);
    state.y = 5 + Math.sin(state.time * 3) * 0.4;
    state.vy = Math.cos(state.time * 3) * 1.2;
  } else if (state.mode === 'playing') {
    updatePlaying(dt);
  } else {
    updateDead(dt);
  }
}

const perf = { frames: 0, time: 0, cooldown: 3, fps: 0, slow: 0 };
function adaptQuality(rawDt) {
  perf.frames++;
  perf.time += rawDt;
  if (perf.time < 1.5) return;
  perf.fps = perf.frames / perf.time;
  perf.frames = 0;
  perf.time = 0;
  if (fpsEl) fpsEl.textContent = `${perf.fps.toFixed(0)} fps · ${renderer.info.render.calls} dc · Q${quality}`;
  perf.cooldown -= 1.5;
  // Only judge real gameplay, and give each step time to settle.
  if (state.mode !== 'playing' || state.paused || perf.cooldown > 0) return;
  // Two slow windows in a row, so a single hiccup doesn't lower quality.
  perf.slow = perf.fps < 48 ? perf.slow + 1 : 0;
  if (perf.slow < 2 || quality >= QUALITY_DPR.length) return;
  perf.slow = 0;
  quality++;
  perf.cooldown = 3;
  if (quality < QUALITY_DPR.length) {
    renderer.setPixelRatio(Math.min(QUALITY_DPR[0], QUALITY_DPR[quality]));
    resize();
  } else {
    setShadows(false);
  }
  try { localStorage.setItem('birdy-quality', String(quality)); } catch { /* ignore */ }
}

// Hidden developer overlay: tap the title 5 times (or open with ?fps).
let fpsEl = null;
function toggleFps(on) {
  if (on && !fpsEl) {
    fpsEl = document.createElement('div');
    fpsEl.id = 'fps';
    app.appendChild(fpsEl);
  } else if (!on && fpsEl) {
    fpsEl.remove();
    fpsEl = null;
  }
  try { localStorage.setItem('birdy-fps', on ? '1' : ''); } catch { /* ignore */ }
}
{
  let taps = 0;
  let last = 0;
  document.querySelector('#start h1').addEventListener('pointerdown', () => {
    const now = performance.now();
    taps = now - last < 400 ? taps + 1 : 1;
    last = now;
    if (taps >= 5) toggleFps(!fpsEl);
  });
  let saved = '';
  try { saved = localStorage.getItem('birdy-fps'); } catch { /* ignore */ }
  if (saved || new URLSearchParams(location.search).has('fps')) toggleFps(true);
}

function tick() {
  const rawDt = clock.getDelta();
  const dt = Math.min(rawDt, 1 / 30);
  adaptQuality(rawDt);
  if (landscapeTouch.matches || state.paused) {
    renderer.render(scene, camera);
    requestAnimationFrame(tick);
    return;
  }
  if (state.hitStop > 0) {
    // Freeze-frame on impact; only the camera shake keeps going.
    state.hitStop -= rawDt;
    updateCamera(dt);
    renderer.render(scene, camera);
    requestAnimationFrame(tick);
    return;
  }
  step(dt, music.beat());
  particles.update(dt, state.mode === 'playing' ? state.speed * dt : 0);
  updateToast(dt);
  updateHand();
  updateZonesOverlay();
  biomes.update(dt);
  updateBirdVisual(dt);
  updateCamera(dt);
  renderer.render(scene, camera);
  requestAnimationFrame(tick);
}

const SIM = new URLSearchParams(location.search).has('sim');
if (!SIM) {
  // First launch: straight into the guided first run.
  if (!progress.tutorialDone) {
    tut.active = true;
    resetGame();
  }
  tick();
}

// Headless playtest: run `runs` games with a bot at a fixed 60 Hz step and
// return per-run stats. Only used by scripts/playtest.mjs (?sim in the URL).
async function simulate({ runs = 50, bot: botOpts = {}, maxTime = 240 } = {}) {
  const { createBot } = await import('./bot.js');
  const results = [];
  const dt = 1 / 60;
  for (let i = 0; i < runs; i++) {
    const bot = createBot({ ...botOpts, hop: SWITCH_HOP });
    lastRun = null;
    resetGame();
    flap();
    while (state.mode === 'playing' && state.runTime < maxTime) {
      const act = bot.decide(dt, { state, gates, lanes: LANES, pickups, coins });
      if (act) tap(act.lane ?? state.lane);
      step(dt);
    }
    results.push(lastRun ?? { score: state.score, coins: state.coins, time: state.runTime, cause: 'timeout', zone: state.zone });
    state.mode = 'over';
  }
  return results;
}

// Expose a tiny hook for automated smoke tests.
window.__birdy = { state, gates, pickups, coins, activatePower, simulate, renderer, progress, enterZone, renderMusic, handleBack, openShop, camera };
