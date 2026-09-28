// Golden fixture for the pure difficulty / pacing curve in src/main.js
// lines 743-756 (difficulty, baseSpeed, spacing).
//
// Run: node scripts/native-golden/main-a-difficulty.mjs
// Writes: docs/native/golden/main-a-difficulty.json
import { writeFileSync } from 'node:fs';

function difficulty(score) {
  return Math.min(1, score / 40);
}
function baseSpeed(score) {
  const over = Math.max(0, score - 40);
  const d = difficulty(score);
  return 18 + 16 * d + 8 * (1 - Math.exp(-over / 50));
}
function spacing(score) {
  const d = difficulty(score);
  return baseSpeed(score) * (1.7 - 0.6 * d);
}

const scores = [];
for (let s = 0; s <= 300; s += 5) scores.push(s);
// A few interesting exact edge points too.
for (const s of [0, 1, 39, 40, 41, 90]) if (!scores.includes(s)) scores.push(s);
scores.sort((a, b) => a - b);

const rows = scores.map((score) => ({
  score,
  difficulty: difficulty(score),
  baseSpeed: baseSpeed(score),
  spacing: spacing(score),
}));

const out = {
  source: 'src/main.js lines 743-756',
  formulas: {
    difficulty: 'min(1, score / 40)',
    baseSpeed: '18 + 16*difficulty + 8*(1 - exp(-max(0, score-40)/50))',
    spacing: 'baseSpeed * (1.7 - 0.6*difficulty)',
  },
  rows,
};
writeFileSync(new URL('../../docs/native/golden/main-a-difficulty.json', import.meta.url), JSON.stringify(out, null, 2) + '\n');
console.log(`wrote ${rows.length} rows`);
