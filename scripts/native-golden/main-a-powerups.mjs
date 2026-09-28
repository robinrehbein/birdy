// Golden fixture for power-up duration / magnet range scaling with shop
// upgrade levels. src/main.js lines 44-48.
// Run: node scripts/native-golden/main-a-powerups.mjs
import { writeFileSync } from 'node:fs';

const POWERUPS = {
  star: { duration: 6 },
  magnet: { duration: 9 },
  mini: { duration: 9 },
};
const UPGRADE_BONUS = { star: 1.5, magnet: 3, mini: 3 };
const MAGNET_RANGE = 9;

function powerDuration(type, level) {
  return POWERUPS[type].duration + UPGRADE_BONUS[type] * level;
}
function magnetRange(magnetLevel) {
  return MAGNET_RANGE + 2 * magnetLevel;
}

const rows = [];
for (const type of Object.keys(POWERUPS)) {
  for (let level = 0; level <= 3; level++) {
    rows.push({ type, level, duration: powerDuration(type, level) });
  }
}
const magnetRows = [0, 1, 2, 3].map((level) => ({ level, magnetRange: magnetRange(level) }));

const out = {
  source: 'src/main.js lines 44-48',
  formulas: {
    powerDuration: 'POWERUPS[type].duration + UPGRADE_BONUS[type] * progress.level(type)',
    magnetRange: 'MAGNET_RANGE(9) + 2 * progress.level("magnet")',
  },
  baseDurations: { star: 6, magnet: 9, mini: 9 },
  upgradeBonus: UPGRADE_BONUS,
  durationRows: rows,
  magnetRangeRows: magnetRows,
};
writeFileSync(new URL('../../docs/native/golden/main-a-powerups.json', import.meta.url), JSON.stringify(out, null, 2) + '\n');
console.log(`wrote ${rows.length + magnetRows.length} rows`);
