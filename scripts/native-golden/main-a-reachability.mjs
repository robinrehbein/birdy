// Golden fixture for the fairness/reachability check in src/main.js
// lines 926-955 (makeReachable's `reach` and `fits` helpers).
// These are pure functions of (score) -> (t, maxDrop, maxRise) and then
// (from, to, steps) -> boolean.
// Run: node scripts/native-golden/main-a-reachability.mjs
import { writeFileSync } from 'node:fs';

function difficulty(score) { return Math.min(1, score / 40); }
function baseSpeed(score) {
  const over = Math.max(0, score - 40);
  return 18 + 16 * difficulty(score) + 8 * (1 - Math.exp(-over / 50));
}
function spacing(score) { return baseSpeed(score) * (1.7 - 0.6 * difficulty(score)); }

const SWITCH_FACTOR = 0.6;

function limits(score) {
  const t = spacing(score) / baseSpeed(score); // = (1.7 - 0.6*difficulty)
  const maxDrop = 1 + 3.5 * t;
  const maxRise = 0.8 + 3 * t;
  return { t, maxDrop, maxRise };
}
function reach(score, from, to, steps) {
  const { maxDrop, maxRise } = limits(score);
  const k = SWITCH_FACTOR ** steps;
  const d = to - from;
  return d <= maxRise * k && -d <= maxDrop * k;
}

const cases = [];
for (const score of [0, 10, 40, 100]) {
  const { t, maxDrop, maxRise } = limits(score);
  for (const steps of [0, 1, 2]) {
    for (const d of [-6, -3, -1, 0, 1, 3, 6]) {
      cases.push({ score, t, maxDrop, maxRise, steps, delta: d, reachable: reach(score, 0, d, steps) });
    }
  }
}

const out = {
  source: 'src/main.js lines 926-955 (makeReachable)',
  notes: [
    't = spacing(score) / baseSpeed(score) = (1.7 - 0.6*difficulty(score)) — the time in seconds between two rows at the current speed.',
    'maxDrop = 1 + 3.5*t; maxRise = 0.8 + 3*t.',
    'k = 0.6^steps where steps = abs(laneIndexTo - laneIndexFrom); a lane switch multiplies both budgets by k.',
    'reach(from,to,steps) = (to-from) <= maxRise*k AND (from-to) <= maxDrop*k.',
  ],
  constants: { SWITCH_FACTOR },
  cases,
};
writeFileSync(new URL('../../docs/native/golden/main-a-reachability.json', import.meta.url), JSON.stringify(out, null, 2) + '\n');
console.log(`wrote ${cases.length} cases`);
