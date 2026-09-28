// Golden fixture for the bird's squash & stretch curve.
// src/main.js lines 1077-1079 (flap sets state.squash = 1),
// lines 1833-1835 (per-frame decay + scale formula).
// Run: node scripts/native-golden/bird-fx-squash-stretch.mjs
import { writeFileSync } from 'node:fs';

const DT = 1 / 60;
const BASE_SCALE = 1.1; // BIRD_SCALE

function step(squash) {
  return Math.max(0, squash - DT * 6);
}
function q(squash) {
  return Math.sin(squash * Math.PI) * 0.5 + squash * 0.2;
}
function scaleFor(baseScale, squash) {
  const k = q(squash);
  return {
    x: baseScale * (1 - 0.14 * k),
    y: baseScale * (1 + 0.24 * k),
    z: baseScale * (1 - 0.1 * k),
  };
}

// Simulate one flap: squash starts at 1, decays to 0 over frames at 60fps.
let squash = 1;
const frames = [];
for (let i = 0; i < 40; i++) {
  frames.push({
    frame: i,
    t: +(i * DT).toFixed(5),
    squash: +squash.toFixed(6),
    q: +q(squash).toFixed(6),
    scale: {
      x: +scaleFor(BASE_SCALE, squash).x.toFixed(6),
      y: +scaleFor(BASE_SCALE, squash).y.toFixed(6),
      z: +scaleFor(BASE_SCALE, squash).z.toFixed(6),
    },
  });
  squash = step(squash);
  if (squash <= 0 && i > 2) break;
}

const out = {
  source: 'src/main.js lines 1077-1079, 1833-1835',
  dtAssumed: DT,
  baseScaleAssumed: BASE_SCALE,
  formulas: {
    onFlap: 'state.squash = 1',
    decayPerFrame: 'squash = max(0, squash - dt * 6)',
    q: 'q = sin(squash * PI) * 0.5 + squash * 0.2',
    scale: 'scale = (baseScale*(1-0.14*q), baseScale*(1+0.24*q), baseScale*(1-0.1*q))  // (x,y,z)',
  },
  note: 'squash decays independently of frame rate scaling elsewhere (dt-based), so this trace is exact for any fixed dt; the values above use 60fps steps for a readable table.',
  frames,
};
writeFileSync(new URL('../../docs/native/golden/bird-fx-squash-stretch.json', import.meta.url), JSON.stringify(out, null, 2) + '\n');
console.log(`wrote ${frames.length} frames`);
