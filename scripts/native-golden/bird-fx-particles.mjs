// Golden fixture for the pooled particle system (effects.js createParticles,
// lines 5-76): traces emit() + update() with a SEEDED substitute for
// Math.random() so the algorithm (draw order, formulas, clamping) can be
// checked bit-for-bit by a Kotlin port using its own seeded RNG at the same
// call sites. Real gameplay does NOT seed Math.random(); exact particle
// positions are not a parity requirement (see lib-prng.mjs).
// Run: node scripts/native-golden/bird-fx-particles.mjs
import { writeFileSync } from 'node:fs';
import { mulberry32 } from './lib-prng.mjs';

const MAX = 300;

function createParticles(rand, max = MAX) {
  const parts = Array.from({ length: max }, () => ({
    life: 0, maxLife: 1, size: 0.1, gravity: 0, drag: 0,
    pos: { x: 0, y: 0, z: 0 }, vel: { x: 0, y: 0, z: 0 }, spin: 0,
  }));
  let cursor = 0;

  function emit(pos, { count = 10, colors = [0xffffff], speed = 4, size = 0.12, life = 0.6, gravity = -6, drag = 1.5, spread = 1, drift = 0 }) {
    const emitted = [];
    for (let n = 0; n < count; n++) {
      const i = cursor;
      cursor = (cursor + 1) % max;
      const p = parts[i];
      p.pos = { ...pos };
      const vx = rand() - 0.5, vy = rand() - 0.5, vz = rand() - 0.5;
      const len = Math.hypot(vx, vy, vz) || 1;
      const s = speed * (0.4 + rand() * 0.6) * spread;
      p.vel = { x: (vx / len) * s, y: (vy / len) * s, z: (vz / len) * s };
      p.vel.z += drift;
      p.life = p.maxLife = life * (0.7 + rand() * 0.6);
      p.size = size * (0.6 + rand() * 0.8);
      p.gravity = gravity;
      p.drag = drag;
      p.spin = rand() * 6;
      const colorIdx = Math.floor(rand() * colors.length);
      emitted.push({ index: i, color: colors[colorIdx] });
    }
    return emitted;
  }

  function update(dt, dz) {
    for (const p of parts) {
      if (p.life <= 0) continue;
      p.life -= dt;
      p.vel.y += p.gravity * dt;
      const drag = Math.max(0, 1 - p.drag * dt);
      p.vel.x *= drag; p.vel.y *= drag; p.vel.z *= drag;
      p.pos.x += p.vel.x * dt; p.pos.y += p.vel.y * dt; p.pos.z += p.vel.z * dt;
      p.pos.z += dz;
    }
  }

  return { parts, emit, update };
}

// Draw order per particle, PER SLOT, in emit(): vel.x, vel.y, vel.z, speedJitter, life, size, spin, colorIndex = 8 draws.
const rand = mulberry32(1234567);
const sys = createParticles(rand);

const emitted1 = sys.emit({ x: 0, y: 5, z: 0 }, { count: 5, colors: [0xff5a8a, 0x5ad1ff, 0xffd84a], speed: 7, size: 0.13, life: 0.9, gravity: -4 });
const afterEmit1 = emitted1.map((e) => ({ ...e, particle: JSON.parse(JSON.stringify(sys.parts[e.index])) }));

sys.update(1 / 60, 0.3); // one frame, world scrolling by dz=0.3
const afterOneFrame = emitted1.map((e) => ({ index: e.index, particle: JSON.parse(JSON.stringify(sys.parts[e.index])) }));

// Scale/opacity-fraction curve used when building the render transform
// (main render loop, not shown here): k = max(0, life/maxLife); renderScale = size * (0.3 + 0.7*k) while life>0 else 0.
function renderScale(size, life, maxLife) {
  if (life <= 0) return 0;
  const k = Math.max(0, life / maxLife);
  return size * (0.3 + 0.7 * k);
}
const shrinkCurve = [1, 0.75, 0.5, 0.25, 0.1, 0].map((k) => ({ k, renderScaleFactor: +(0.3 + 0.7 * k).toFixed(6) }));

const out = {
  source: 'src/effects.js lines 5-76',
  seed: 1234567,
  rng: 'mulberry32 (test-only substitute for Math.random; production code is unseeded)',
  pool: { max: MAX, structure: 'flat array of {life, maxLife, size, gravity, drag, pos:Vec3, vel:Vec3, spin}; a single ring-buffer cursor picks the next slot to overwrite regardless of whether it is still alive (oldest-slot eviction, not oldest-particle)' },
  emitFormulas: {
    velocityDirection: 'normalize(rand()-0.5, rand()-0.5, rand()-0.5)  // uniform in a CUBE then normalized (not a uniform sphere distribution)',
    velocityMagnitude: 'speed * (0.4 + rand()*0.6) * spread',
    driftAddedToZ: 'vel.z += drift  // constant backward bias, e.g. trail particles',
    life: 'life = maxLife = life_param * (0.7 + rand()*0.6)',
    size: 'size = size_param * (0.6 + rand()*0.8)',
    spin: 'spin = rand() * 6  // radians, used as rotation.x = spin*k, rotation.y = spin*1.3*k in update-render (k = life/maxLife)',
    color: 'colors[floor(rand() * colors.length)]  // uniform pick from the palette array, independent per particle',
    drawOrderPerParticle: ['vel.x rand()', 'vel.y rand()', 'vel.z rand()', 'speed jitter rand()', 'life jitter rand()', 'size jitter rand()', 'spin rand()', 'color index rand()'],
  },
  updateFormulas: {
    lifeDecay: 'life -= dt',
    gravity: 'vel.y += gravity * dt  // gravity is typically negative (down) but callers pass positive too (e.g. trail gravity:-4..3)',
    drag: 'vel *= max(0, 1 - drag*dt)  // uniform scalar damping on all 3 axes',
    integrate: 'pos += vel * dt',
    worldScroll: 'pos.z += dz  // dz = state.speed*dt while playing, 0 otherwise; makes particles scroll with the world so trails read as stationary in world-space',
    renderScale: 'visible while life>0; renderScale = size * (0.3 + 0.7 * max(0, life/maxLife)); rotation.set(spin*k, spin*1.3*k, 0)',
  },
  defaults: { count: 10, colors: [0xffffff], speed: 4, size: 0.12, life: 0.6, gravity: -6, drag: 1.5, spread: 1, drift: 0 },
  traces: {
    emit1_params: { count: 5, colors: [0xff5a8a, 0x5ad1ff, 0xffd84a], speed: 7, size: 0.13, life: 0.9, gravity: -4 },
    afterEmit1,
    afterOneFrame_dt: 1 / 60,
    afterOneFrame_dz: 0.3,
    afterOneFrame,
  },
  renderShrinkCurveSamples: shrinkCurve,
};
writeFileSync(new URL('../../docs/native/golden/bird-fx-particles.json', import.meta.url), JSON.stringify(out, null, 2) + '\n');
console.log('wrote particle trace fixture');
