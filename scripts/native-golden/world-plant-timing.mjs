// Golden fixture for the cactus "plant" pop-up timing in world.js
// (plantPeek, plantRise) and the breathing-gap pulseScale.
// Run: node scripts/native-golden/world-plant-timing.mjs
import fs from 'node:fs';

function smoothstep(x, min, max) {
  // THREE.MathUtils.smoothstep
  if (x <= min) return 0;
  if (x >= max) return 1;
  x = (x - min) / (max - min);
  return x * x * (3 - 2 * x);
}

function plantPeek(beat) {
  const p = ((beat % 4) + 4) % 4;
  return p >= 1.1 && p < 2;
}

function plantRise(beat) {
  const p = ((beat % 4) + 4) % 4;
  if (p < 2) return 0;
  if (p < 2.4) return smoothstep(p, 2, 2.4);
  if (p < 3.5) return 1;
  if (p < 3.9) return 1 - smoothstep(p, 3.5, 3.9);
  return 0;
}

function pulseScale(beat) {
  return 0.85 + 0.15 * Math.cos(beat * Math.PI);
}

const samples = [];
for (let beat = -4; beat <= 8 + 1e-9; beat += 0.1) {
  const b = Math.round(beat * 100) / 100;
  samples.push({
    beat: b,
    peek: plantPeek(b),
    rise: Math.round(plantRise(b) * 1e6) / 1e6,
    pulseScale: Math.round(pulseScale(b) * 1e6) / 1e6,
  });
}

fs.writeFileSync(
  new URL('../../docs/native/golden/world-plant-timing.json', import.meta.url),
  JSON.stringify({ description: 'world.js plantPeek/plantRise/pulseScale sampled every 0.1 beat from -4..8 (negative beats exercise the (%+4)%4 wrap)', samples }, null, 2) + '\n'
);
console.log('wrote world-plant-timing.json,', samples.length, 'samples');
