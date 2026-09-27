import * as THREE from 'three';
import './style.css';
import { createBird } from './bird.js';
import { sfx, music, audio } from './audio.js';
import { createParticles } from './effects.js';
import { POWERUPS, POWERUP_TYPES, createPowerupPickup, animatePickup } from './powerups.js';
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
const MAX_FALL = -22;
const CEILING = 14;
const BIRD_RADIUS = 0.5;
const MINI_RADIUS = 0.3;
const BIRD_SCALE = 1.1;
const MINI_SCALE = 0.6;
const LANE_SWITCH_SPEED = 12;
const SPAWN_DISTANCE = 170;
const FIRST_GATE_Z = -60;
const COIN_RADIUS = 1.1;
const MAGNET_RANGE = 9;
const STAR_SPEED_BOOST = 1.35;
const GRACE_TIME = 1.2; // invulnerable blinking after the rainbow ends
const FALLBACK_BPM = 124;

// --- Setup ------------------------------------------------------------------
const canvas = document.getElementById('game');
const renderer = new THREE.WebGLRenderer({ canvas, antialias: true });
renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
renderer.shadowMap.enabled = true;
renderer.shadowMap.type = THREE.PCFSoftShadowMap;

const scene = createScene();
const camera = new THREE.PerspectiveCamera(60, 1, 0.1, 400);

const ground = createGround(scene);
const scenery = createScenery(scene);
const clouds = createClouds(scene);
const particles = createParticles(scene);

const bird = createBird();
bird.group.scale.setScalar(BIRD_SCALE);
scene.add(bird.group);

const gates = Array.from({ length: 8 }, () => {
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

function loadBest() {
  try { return Number(localStorage.getItem('birdy-best')) || 0; } catch { return 0; }
}
function saveBest(v) {
  try { localStorage.setItem('birdy-best', String(v)); } catch { /* storage unavailable */ }
}
let best = loadBest();
$('best-start').textContent = best;

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
};

const invincible = () => state.power.star > 0 || state.grace > 0;

function difficulty() {
  return Math.min(1, state.score / 40);
}

function spacing() {
  return 32 - 6 * difficulty();
}

function resetGame() {
  state.mode = 'playing';
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
  overEl.classList.add('hidden');
  updateLaneDots();
  // Briefly show the three tap zones at the start of every run.
  zonesEl.classList.remove('show');
  void zonesEl.offsetWidth;
  zonesEl.classList.add('show');
}

// Build one row of pipes. Later rows add moving gaps and piranha plants.
function gateSpec() {
  const d = difficulty();
  const size = 5.2 - 1.4 * d;
  const lo = size / 2 + 1.2;
  const hi = CEILING - 2.5 - size / 2;
  const spec = LANES.map(() => ({ center: lo + Math.random() * (hi - lo), size }));

  // After a short warm-up, block some lanes (always keep at least one open).
  if (state.score >= 3) {
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

  const open = spec.map((g, i) => (g ? i : -1)).filter((i) => i >= 0);
  // Keep one open lane "easy" (no plant, no movement).
  const easy = open[Math.floor(Math.random() * open.length)];
  for (const i of open) {
    if (i === easy && open.length > 1) continue;
    const g = spec[i];
    if (state.score >= 6 && Math.random() < 0.25 + 0.3 * d) {
      // Moving gap: slides up and down within the playable range.
      g.amp = Math.min(1.2 + Math.random() * 1.3, (hi - lo) / 2);
      g.center = THREE.MathUtils.clamp(g.center, lo + g.amp, hi - g.amp);
      g.speed = 1.2 + Math.random() * 1.2;
      g.phase = Math.random() * Math.PI * 2;
    } else if (state.score >= 10 && Math.random() < 0.25 + 0.25 * d) {
      // Piranha plant: pops out of the lower pipe in time with the music.
      g.plant = true;
      g.plantOffset = Math.random() < 0.5 ? 0 : 2;
    }
  }
  return spec;
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

function spawnGate(z) {
  const gate = gates.find((g) => !g.active);
  if (!gate) return;
  const spec = gateSpec();
  gate.active = true;
  gate.configure(z, spec);
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
  if (state.mode === 'ready') resetGame();
  if (state.mode !== 'playing') return;
  state.vy = FLAP_VELOCITY;
  state.wingSpeed = 38;
  sfx.flap();
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
  if (state.mode === 'over' && state.deadTimer > 1.0) {
    resetGame();
    flap();
  }
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
  if (state.mode === 'playing') {
    const rect = canvas.getBoundingClientRect();
    setLane(Math.floor(((e.clientX - rect.left) / rect.width) * 3));
  }
  flap();
});
startEl.addEventListener('pointerdown', () => {
  startAudio();
  flap();
});
pauseEl.addEventListener('pointerdown', () => setPaused(false));

$('retry-btn').addEventListener('click', () => {
  startAudio();
  resetGame();
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

function die() {
  if (state.mode !== 'playing') return;
  state.mode = 'dead';
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
  const isBest = state.score > best;
  if (isBest) {
    best = state.score;
    saveBest(best);
  }
  $('final-score').textContent = state.score;
  $('final-coins').textContent = state.coins;
  $('final-best').textContent = best;
  $('best-start').textContent = best;
  $('new-best').classList.toggle('hidden', !isBest);
  hud.classList.add('hidden');
  overEl.classList.remove('hidden');
}

function addScore() {
  state.score++;
  scoreEl.textContent = state.score;
  scoreEl.classList.remove('pop');
  void scoreEl.offsetWidth; // restart the CSS animation
  scoreEl.classList.add('pop');
  sfx.point();
}

function activatePower(type) {
  state.power[type] = POWERUPS[type].duration;
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
  const d = difficulty();
  const boost = state.power.star > 0 ? STAR_SPEED_BOOST : 1;
  state.speed = THREE.MathUtils.lerp(state.speed, (18 + 16 * d) * boost, dt * (boost > 1 ? 3 : 0.8));
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
      addScore();
    }
    // Passed rows fade out so they don't hide what's coming next.
    gate.setOpacity(THREE.MathUtils.lerp(gate.opacity, gate.passed ? 0.1 : 1, Math.min(1, dt * 12)));
    if (!gate.passed && (!next || gz > next.group.position.z)) next = gate;

    if (!invincible() && Math.abs(gz) < PIPE_RADIUS + 0.25 + r) {
      for (const lane of gate.lanes) {
        if (Math.hypot(state.x - lane.x, gz) > PIPE_RADIUS + 0.15 + r) continue;
        const inGap = !lane.blocked && state.y - r * 0.8 > lane.hitLow && state.y + r * 0.8 < lane.hitHigh;
        if (!inGap) die();
      }
    }
  }
  // Rings mark the gaps: the next row glows brighter the closer it gets.
  for (const gate of gates) {
    if (!gate.active || gate.passed) continue;
    const gz = gate.group.position.z;
    const near = THREE.MathUtils.clamp(1 + gz / 45, 0, 1);
    gate.ringMat.color.set(gate === next ? 0xfff176 : 0xffffff);
    gate.ringMat.opacity = gate === next ? 0.3 + 0.6 * near : 0.15 * near;
  }

  state.lastGateZ += dz;
  while (state.lastGateZ > -SPAWN_DISTANCE) spawnGate(state.lastGateZ - spacing());

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
      die();
    }
  }
}

function updateDead(dt) {
  state.deadTimer += dt;
  if (state.y > state.radius) {
    state.vy = Math.max(MAX_FALL, state.vy - GRAVITY * dt);
    state.y = Math.max(state.radius, state.y + state.vy * dt);
    bird.group.rotation.z += dt * 6;
  } else if (state.mode === 'dead' && state.deadTimer > 0.7) {
    showGameOver();
  }
  for (const gate of gates) if (gate.active) gate.update(state.time, state.beat, dt);
}

function updateBirdVisual(dt) {
  const g = bird.group;
  g.position.set(state.x, state.y, 0);
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
  g.scale.setScalar(THREE.MathUtils.lerp(g.scale.x, scale, Math.min(1, dt * 8)));
  if (state.mode === 'playing' && state.power.star > 0) {
    bird.setGlow(tmpColor.setHSL((state.time * 1.5) % 1, 1, 0.5), 0.7);
  }
  // Blink during the grace period after the rainbow ends.
  g.visible = !(state.grace > 0 && Math.floor(state.time * 12) % 2 === 0);
}

const camTarget = new THREE.Vector3();
const camLook = new THREE.Vector3();
function updateCamera(dt) {
  // Camera sits above and behind the bird so it stays in the lower third
  // and the gaps ahead remain visible (Temple Run / Subway Surfers style).
  camTarget.set(state.x * 0.5, 7.5 + state.y * 0.6, 14);
  camLook.set(state.x * 0.7, 1.8 + state.y * 0.6, -22);
  const k = Math.min(1, dt * 6);
  camera.position.lerp(camTarget, k);
  if (state.shake > 0) {
    state.shake = Math.max(0, state.shake - dt);
    const s = state.shake * 0.8;
    camera.position.x += (Math.random() - 0.5) * s;
    camera.position.y += (Math.random() - 0.5) * s;
  }
  camera.lookAt(camLook);
}

function resize() {
  const w = app.clientWidth;
  const h = app.clientHeight;
  renderer.setSize(w, h, false);
  camera.aspect = w / h;
  // Narrow portrait screens need a wider vertical FOV to still see all lanes.
  camera.fov = camera.aspect < 0.5 ? 74 : 68;
  camera.updateProjectionMatrix();
}
window.addEventListener('resize', resize);
resize();

const clock = new THREE.Clock();
camera.position.set(0, 10.5, 14);

function tick() {
  const dt = Math.min(clock.getDelta(), 1 / 30);
  if (landscapeTouch.matches || state.paused) {
    renderer.render(scene, camera);
    requestAnimationFrame(tick);
    return;
  }
  state.time += dt;
  // Plants follow the music's beat; without audio fall back to game time.
  state.beat = music.beat() ?? (state.time * FALLBACK_BPM) / 60;

  if (state.mode === 'ready') {
    moveWorld(10 * dt);
    state.y = 5 + Math.sin(state.time * 3) * 0.4;
    state.vy = Math.cos(state.time * 3) * 1.2;
  } else if (state.mode === 'playing') {
    updatePlaying(dt);
  } else {
    updateDead(dt);
  }

  particles.update(dt, state.mode === 'playing' ? state.speed * dt : 0);
  updateBirdVisual(dt);
  updateCamera(dt);
  renderer.render(scene, camera);
  requestAnimationFrame(tick);
}
tick();

// Expose a tiny hook for automated smoke tests.
window.__birdy = { state, gates, pickups, activatePower };
