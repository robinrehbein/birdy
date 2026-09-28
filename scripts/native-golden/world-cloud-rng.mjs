// Golden fixture for the small LCGs in world.js used for pipe-row clouds:
// pickRowCloud() (module-level sequence, starts at seed 12345) and the two
// per-geometry seeded RNGs (makeBankGeometry / makeCollarGeometry), which
// use a different LCG (h = seed*K + C, then h = (h*9301+49297) % 233280).
// Run: node scripts/native-golden/world-cloud-rng.mjs
import fs from 'node:fs';

// --- pickRowCloud sequence -------------------------------------------------
// cloudGeos = [...bank(1,2,3), ...collar(1,2,3)] -> 6 entries, indices 0..5
// (0,1,2 = bank seed 1/2/3; 3,4,5 = collar seed 1/2/3).
let cloudPick = 12345;
function pickRowCloudIndex() {
  cloudPick = (cloudPick * 9301 + 49297) % 233280;
  const r = cloudPick / 233280;
  if (r < 1 / 3) return null; // no cloud
  const idx = Math.floor(((r - 1 / 3) * 1.5) * 6) % 6;
  return idx;
}
const rowPicks = [];
for (let i = 0; i < 60; i++) rowPicks.push(pickRowCloudIndex());

// --- per-geometry seeded rnd() ---------------------------------------------
function makeRnd(seed, mul, add) {
  let h = seed * mul + add;
  return () => ((h = (h * 9301 + 49297) % 233280) / 233280);
}
const bankFirst8 = {};
for (const seed of [1, 2, 3]) {
  const rnd = makeRnd(seed, 9301, 49297);
  bankFirst8[seed] = Array.from({ length: 8 }, () => Math.round(rnd() * 1e6) / 1e6);
}
const collarFirst8 = {};
for (const seed of [1, 2, 3]) {
  const rnd = makeRnd(seed, 7919, 104729);
  collarFirst8[seed] = Array.from({ length: 8 }, () => Math.round(rnd() * 1e6) / 1e6);
}

fs.writeFileSync(
  new URL('../../docs/native/golden/world-cloud-rng.json', import.meta.url),
  JSON.stringify({
    description: 'world.js pipe-row cloud LCGs: pickRowCloud() index sequence (module seed 12345; null = no cloud, 0-2 = bank seed 1/2/3, 3-5 = collar seed 1/2/3) and the first 8 outputs of makeBankGeometry/makeCollarGeometry\'s own per-call RNG for seeds 1..3.',
    rowPicks,
    bankFirst8,
    collarFirst8,
  }, null, 2) + '\n'
);
console.log('wrote world-cloud-rng.json');
