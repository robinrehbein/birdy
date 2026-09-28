// Golden fixtures for the pure numeric formulas used by main.js lines 1100-2129
// (and the constants near the top of the file that those lines depend on).
// These are re-implemented here (not imported) because main.js has DOM/THREE
// side effects on import; the formulas are copied verbatim from src/main.js.
// Run: node scripts/native-golden/main-b-formulas.mjs
import { writeFileSync } from 'node:fs';

const UPGRADE_BONUS = { star: 1.5, magnet: 3, mini: 3 };
const POWER_BASE_DURATION = { star: 6, magnet: 9, mini: 9 }; // from POWERUPS[type].duration, see powerups.js
const MAGNET_RANGE = 9;
const STAR_SPEED_BOOST = 1.35;
const GRACE_TIME = 1.2;
const HIT_STOP = 0.14;
const NEAR_MISS = 0.45;

function powerDuration(type, level) {
  return POWER_BASE_DURATION[type] + UPGRADE_BONUS[type] * level;
}
function magnetRange(level) {
  return MAGNET_RANGE + 2 * level;
}
function difficulty(score) {
  return Math.min(1, score / 40);
}
function baseSpeed(score) {
  const over = Math.max(0, score - 40);
  return 18 + 16 * difficulty(score) + 8 * (1 - Math.exp(-over / 50));
}
function spacing(score) {
  return baseSpeed(score) * (1.7 - 0.6 * difficulty(score));
}
function paceFov(baseFov, speed, starActive) {
  const pace = Math.min(1, Math.max(0, (speed - 18) / 18));
  return baseFov + pace * 4 + (starActive ? 8 : 0);
}
// makeReachable's reach() test: can a lane change of `steps` lanes cover a
// vertical delta `d` (positive = rise) within time `t` (spacing/baseSpeed)?
function reach(t, d, steps) {
  const maxDrop = 1 + 3.5 * t;
  const maxRise = 0.8 + 3 * t;
  const SWITCH_FACTOR = 0.6;
  const k = SWITCH_FACTOR ** steps;
  return d <= maxRise * k && -d <= maxDrop * k;
}

const scores = [0, 1, 5, 10, 20, 30, 40, 50, 70, 90, 120, 160, 200];
const difficultyCurve = scores.map((score) => ({
  score,
  difficulty: difficulty(score),
  baseSpeed: baseSpeed(score),
  spacing: spacing(score),
}));

const powerDurations = ['star', 'magnet', 'mini'].flatMap((type) =>
  [0, 1, 2, 3].map((level) => ({ type, level, duration: powerDuration(type, level) })));

const magnetRanges = [0, 1, 2, 3].map((level) => ({ level, range: magnetRange(level) }));

const fovSamples = [
  { speed: 10, star: false }, { speed: 18, star: false }, { speed: 26, star: false },
  { speed: 36, star: false }, { speed: 26, star: true }, { speed: 36, star: true },
].map((s) => ({ ...s, fov68: paceFov(68, s.speed, s.star), fov74: paceFov(74, s.speed, s.star) }));

// reach() truth table for a representative set of (t, d, steps).
const reachTable = [];
for (const t of [0.5, 0.85, 1.2]) {
  for (const d of [-4, -1, 0, 1, 3, 5]) {
    for (const steps of [0, 1, 2]) {
      reachTable.push({ t, d, steps, ok: reach(t, d, steps) });
    }
  }
}

const constants = {
  UPGRADE_BONUS, MAGNET_RANGE, STAR_SPEED_BOOST, GRACE_TIME, HIT_STOP, NEAR_MISS,
  QUALITY_DPR_STEPS: 4, // [dpr, 1.5, 1.25, 1] where dpr = min(devicePixelRatio, 2)
  SURPRISE_PRICE: 150,
  SURPRISE_MAX: 900,
  TUT_SWITCH_ROW: 3,
  NEAR_BIRD: 0.12,
};

writeFileSync(new URL('../../docs/native/golden/main-b-difficulty-curve.json', import.meta.url),
  JSON.stringify(difficultyCurve, null, 2) + '\n');
writeFileSync(new URL('../../docs/native/golden/main-b-power-durations.json', import.meta.url),
  JSON.stringify(powerDurations, null, 2) + '\n');
writeFileSync(new URL('../../docs/native/golden/main-b-magnet-ranges.json', import.meta.url),
  JSON.stringify(magnetRanges, null, 2) + '\n');
writeFileSync(new URL('../../docs/native/golden/main-b-camera-fov.json', import.meta.url),
  JSON.stringify(fovSamples, null, 2) + '\n');
writeFileSync(new URL('../../docs/native/golden/main-b-reachability.json', import.meta.url),
  JSON.stringify(reachTable, null, 2) + '\n');
writeFileSync(new URL('../../docs/native/golden/main-b-constants.json', import.meta.url),
  JSON.stringify(constants, null, 2) + '\n');

console.log('Wrote 6 golden fixture files to docs/native/golden/');
