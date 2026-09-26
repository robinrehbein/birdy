import * as THREE from 'three';
import './style.css';
import { createBird } from './bird.js';
import { sfx } from './audio.js';
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
const LANE_SWITCH_SPEED = 12;
const SPAWN_DISTANCE = 170;
const FIRST_GATE_Z = -60;
const COIN_RADIUS = 1.1;

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

const bird = createBird();
bird.group.scale.setScalar(1.1);
scene.add(bird.group);

const gates = Array.from({ length: 8 }, () => {
  const g = createGate(scene);
  g.group.visible = false;
  g.active = false;
  return g;
});
const coins = Array.from({ length: 60 }, () => createCoin(scene));

// --- DOM --------------------------------------------------------------------
const $ = (id) => document.getElementById(id);
const hud = $('hud');
const scoreEl = $('score');
const coinCountEl = $('coin-count');
const startEl = $('start');
const overEl = $('gameover');

const app = $('app');
const flash = document.createElement('div');
flash.id = 'flash';
app.appendChild(flash);
// Phones held sideways get a "rotate" hint and the game pauses.
const landscapeTouch = window.matchMedia('(orientation: landscape) and (pointer: coarse)');

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
  x: 0,
  y: 5,
  vy: 0,
  lane: 1,
  speed: 10,
  distance: 0,
  score: 0,
  coins: 0,
  lastGateZ: 0,
  prevGaps: null,
  wingPhase: 0,
  wingSpeed: 10,
  deadTimer: 0,
  shake: 0,
  time: 0,
};

function difficulty() {
  return Math.min(1, state.score / 40);
}

function resetGame() {
  state.mode = 'playing';
  state.x = 0;
  state.y = 5;
  state.vy = 0;
  state.lane = 1;
  state.speed = 18;
  state.score = 0;
  state.coins = 0;
  state.prevGaps = null;
  state.deadTimer = 0;
  state.shake = 0;
  bird.group.rotation.set(0, 0, 0);

  for (const g of gates) { g.active = false; g.group.visible = false; }
  for (const c of coins) { c.active = false; c.mesh.visible = false; }
  state.lastGateZ = FIRST_GATE_Z + spacing();
  while (state.lastGateZ > -SPAWN_DISTANCE) spawnGate(state.lastGateZ - spacing());

  scoreEl.textContent = '0';
  coinCountEl.textContent = '0';
  hud.classList.remove('hidden');
  startEl.classList.add('hidden');
  overEl.classList.add('hidden');
}

function spacing() {
  return 32 - 6 * difficulty();
}

function randomGaps() {
  const d = difficulty();
  const size = 5.2 - 1.6 * d;
  const lo = size / 2 + 1.2;
  const hi = CEILING - 2.5 - size / 2;
  const gaps = LANES.map(() => ({ center: lo + Math.random() * (hi - lo), size }));

  // After a short warm-up, block some lanes (always keep at least one open).
  if (state.score >= 3) {
    const pBlock = 0.2 + 0.3 * d;
    const order = [0, 1, 2].sort(() => Math.random() - 0.5);
    let open = 3;
    for (const i of order) {
      if (open > 1 && Math.random() < pBlock) {
        gaps[i] = null;
        open--;
      }
    }
  }
  return gaps;
}

function takeCoin() {
  return coins.find((c) => !c.active);
}

function placeCoin(x, y, z) {
  const c = takeCoin();
  if (!c) return;
  c.active = true;
  c.mesh.visible = true;
  c.mesh.position.set(x, y, z);
}

function spawnGate(z) {
  const gate = gates.find((g) => !g.active);
  if (!gate) return;
  const gaps = randomGaps();
  gate.active = true;
  gate.configure(z, gaps);

  // Coins inside some gaps.
  gaps.forEach((gap, i) => {
    if (gap && Math.random() < 0.3) placeCoin(LANES[i], gap.center, z);
  });

  // A guiding trail of coins leading from the previous gate into this one.
  const prev = state.prevGaps;
  if (prev && Math.random() < 0.55) {
    const candidates = [0, 1, 2].filter((i) => prev[i] && gaps[i]);
    if (candidates.length) {
      const i = candidates[Math.floor(Math.random() * candidates.length)];
      const gap = spacing();
      for (let k = 1; k <= 4; k++) {
        const t = k / 5;
        placeCoin(LANES[i], THREE.MathUtils.lerp(prev[i].center, gaps[i].center, t), z + gap * (1 - t));
      }
    }
  }

  state.prevGaps = gaps;
  state.lastGateZ = z;
}

// --- Input ------------------------------------------------------------------
function flap() {
  if (state.mode === 'ready') resetGame();
  if (state.mode !== 'playing') return;
  state.vy = FLAP_VELOCITY;
  state.wingSpeed = 38;
  sfx.flap();
}

function changeLane(dir) {
  if (state.mode !== 'playing') return;
  const next = THREE.MathUtils.clamp(state.lane + dir, 0, LANES.length - 1);
  if (next !== state.lane) {
    state.lane = next;
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
      changeLane(-1);
      break;
    case 'ArrowRight':
    case 'KeyD':
      changeLane(1);
      break;
    case 'Enter':
      tryRestart();
      break;
  }
});

// Tap = flap immediately; a horizontal swipe during the same touch switches lane.
let pointer = null;
function onPointerDown(e) {
  sfx.unlock();
  pointer = { id: e.pointerId, x: e.clientX, y: e.clientY, swiped: false };
  flap();
}
function onPointerMove(e) {
  if (!pointer || pointer.id !== e.pointerId || pointer.swiped) return;
  const dx = e.clientX - pointer.x;
  const dy = e.clientY - pointer.y;
  if (Math.abs(dx) > 35 && Math.abs(dx) > Math.abs(dy)) {
    changeLane(Math.sign(dx));
    pointer.swiped = true;
  }
}
function onPointerUp(e) {
  if (pointer && pointer.id === e.pointerId) pointer = null;
}
canvas.addEventListener('pointerdown', onPointerDown);
startEl.addEventListener('pointerdown', onPointerDown);
window.addEventListener('pointermove', onPointerMove);
window.addEventListener('pointerup', onPointerUp);
window.addEventListener('pointercancel', onPointerUp);

$('retry-btn').addEventListener('click', () => {
  sfx.unlock();
  resetGame();
});

// --- Game loop --------------------------------------------------------------
function die() {
  if (state.mode !== 'playing') return;
  state.mode = 'dead';
  state.deadTimer = 0;
  state.shake = 0.5;
  state.vy = Math.max(state.vy, 6);
  sfx.hit();
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

function moveWorld(dz) {
  state.distance += dz;
  ground.update(state.distance);
  scenery.update(dz);
  clouds.update(dz);
}

function updatePlaying(dt) {
  const d = difficulty();
  state.speed = THREE.MathUtils.lerp(state.speed, 18 + 16 * d, dt * 0.5);
  const dz = state.speed * dt;
  moveWorld(dz);

  // Bird physics.
  state.vy = Math.max(MAX_FALL, state.vy - GRAVITY * dt);
  state.y += state.vy * dt;
  if (state.y > CEILING) {
    state.y = CEILING;
    state.vy = Math.min(state.vy, 0);
  }
  const targetX = LANES[state.lane];
  state.x += (targetX - state.x) * Math.min(1, dt * LANE_SWITCH_SPEED);

  // Gates: move, score, collide, recycle.
  for (const gate of gates) {
    if (!gate.active) continue;
    const gz = (gate.group.position.z += dz);
    if (gz > 20) {
      gate.active = false;
      gate.group.visible = false;
      continue;
    }
    if (!gate.passed && gz > PIPE_RADIUS + BIRD_RADIUS) {
      gate.passed = true;
      addScore();
    }
    if (Math.abs(gz) < PIPE_RADIUS + 0.25 + BIRD_RADIUS) {
      for (const lane of gate.lanes) {
        const dist = Math.hypot(state.x - lane.x, gz);
        if (dist > PIPE_RADIUS + 0.15 + BIRD_RADIUS) continue;
        const inGap = !lane.blocked &&
          state.y - BIRD_RADIUS * 0.8 > lane.gapLow &&
          state.y + BIRD_RADIUS * 0.8 < lane.gapHigh;
        if (!inGap) die();
      }
    }
  }
  state.lastGateZ += dz;
  while (state.lastGateZ > -SPAWN_DISTANCE) spawnGate(state.lastGateZ - spacing());

  // Coins.
  for (const c of coins) {
    if (!c.active) continue;
    c.mesh.position.z += dz;
    c.mesh.rotation.y += dt * 4;
    const p = c.mesh.position;
    if (p.z > 15) {
      c.active = false;
      c.mesh.visible = false;
    } else if (Math.hypot(p.x - state.x, p.y - state.y, p.z) < COIN_RADIUS) {
      c.active = false;
      c.mesh.visible = false;
      state.coins++;
      coinCountEl.textContent = state.coins;
      sfx.coin();
    }
  }

  if (state.y - BIRD_RADIUS < 0) {
    state.y = BIRD_RADIUS;
    die();
  }
}

function updateDead(dt) {
  state.deadTimer += dt;
  if (state.y > BIRD_RADIUS) {
    state.vy = Math.max(MAX_FALL, state.vy - GRAVITY * dt);
    state.y = Math.max(BIRD_RADIUS, state.y + state.vy * dt);
    bird.group.rotation.z += dt * 6;
  } else if (state.mode === 'dead' && state.deadTimer > 0.7) {
    showGameOver();
  }
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
  if (landscapeTouch.matches) {
    requestAnimationFrame(tick);
    return;
  }
  state.time += dt;

  if (state.mode === 'ready') {
    moveWorld(10 * dt);
    state.y = 5 + Math.sin(state.time * 3) * 0.4;
    state.vy = Math.cos(state.time * 3) * 1.2;
  } else if (state.mode === 'playing') {
    updatePlaying(dt);
  } else {
    updateDead(dt);
  }

  updateBirdVisual(dt);
  updateCamera(dt);
  renderer.render(scene, camera);
  requestAnimationFrame(tick);
}
tick();

// Expose a tiny hook for automated smoke tests.
window.__birdy = { state, gates };
