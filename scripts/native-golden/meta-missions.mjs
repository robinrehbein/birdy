// Golden fixture for the daily-mission generator in src/progress.js
// (MISSION_POOL lines 41-51, seeded() lines 62-70, dailyMissions() lines
// 75-86, REWARDS line 52). Pure function of (dateKeyString, bestScore); no
// localStorage/Date mocking needed since the RNG is seeded from the date
// string itself, not from Date.now().
//
// Run: node scripts/native-golden/meta-missions.mjs
// Writes: docs/native/golden/meta-missions.json
import { writeFileSync } from 'node:fs';

// --- verbatim copy of progress.js's pure logic -----------------------------
const MISSION_POOL = [
  { id: 'coins', stat: 'coins', per: 'day', goals: [20, 40, 70] },
  { id: 'score', stat: 'score', per: 'run', goals: [10, 20, 35] },
  { id: 'rows', stat: 'score', per: 'day', goals: [30, 60, 100] },
  { id: 'powers', stat: 'powerups', per: 'day', goals: [2, 4, 6], minBest: 8 },
  { id: 'runs', stat: 'runs', per: 'day', goals: [3, 5, 8] },
  { id: 'plants', stat: 'plants', per: 'day', goals: [3, 6, 10], minBest: 14 },
  { id: 'moving', stat: 'moving', per: 'day', goals: [4, 8, 14], minBest: 10 },
  { id: 'star', stat: 'starRows', per: 'day', goals: [3, 6, 10], minBest: 12 },
];
const REWARDS = [40, 70, 120];

function seeded(str) {
  let h = 2166136261;
  for (const c of str) h = Math.imul(h ^ c.charCodeAt(0), 16777619);
  return () => {
    h = Math.imul(h ^ (h >>> 15), 2246822507);
    h = Math.imul(h ^ (h >>> 13), 3266489909);
    return ((h ^= h >>> 16) >>> 0) / 4294967296;
  };
}

function dailyMissions(date, best) {
  const rnd = seeded(date);
  const pool = MISSION_POOL.filter((m) => best >= (m.minBest || 0));
  const list = [];
  for (let tier = 0; tier < 3; tier++) {
    const m = pool.splice(Math.floor(rnd() * pool.length), 1)[0];
    let goal = m.goals[tier];
    if (m.id === 'score') goal = Math.max(5, Math.round(Math.max(8, best) * [0.6, 0.9, 1.1][tier]));
    list.push({ id: m.id, goal, progress: 0, reward: REWARDS[tier], done: false });
  }
  return { date, list };
}

// --- fixtures ---------------------------------------------------------------
const out = { source: 'src/progress.js lines 41-86', note: 'seeded() is an FNV-1a hash (2166136261 offset basis, 16777619 prime) feeding an xorshift-ish mix, taken as a 32-bit generator; pool.splice mutates the filtered pool array in place, so draws are without replacement.' };

out.rewardsByTier = REWARDS;
out.missionPoolIds = MISSION_POOL.map((m) => m.id);
out.missionPoolMinBest = Object.fromEntries(MISSION_POOL.map((m) => [m.id, m.minBest || 0]));

// seeded() raw output for a few strings, first 5 draws each — verifies the
// PRNG algorithm bit-for-bit before trusting dailyMissions() output below.
out.seededRawDraws = {};
for (const s of ['2024-1-1', '2026-9-28', '', 'a', '2026-12-31']) {
  const rnd = seeded(s);
  out.seededRawDraws[s] = Array.from({ length: 5 }, () => rnd());
}

// dailyMissions() for a spread of dates (deterministic per date) and best
// scores (controls which missions are eligible + the 'score' mission's goal
// scaling: max(5, round(max(8,best) * tierMul)), tierMul = [0.6,0.9,1.1]).
const dates = ['2024-1-1', '2024-1-2', '2024-12-31', '2025-6-15', '2026-9-28', '2026-9-29', '2000-1-1'];
const bests = [0, 5, 8, 13, 14, 30, 100, 999];
out.dailyMissions = [];
for (const date of dates) {
  for (const best of bests) {
    out.dailyMissions.push({ date, best, ...dailyMissions(date, best) });
  }
}

// dayKey() format sample: `${Y}-${M(1-based,no pad)}-${D(no pad)}`.
out.dayKeyFormat = 'template: `${date.getFullYear()}-${date.getMonth()+1}-${date.getDate()}` — NOT zero-padded, month is 1-based (JS getMonth() is 0-based, +1 applied)';
out.dayKeyExamples = [
  { y: 2026, m0: 8, d: 28, label: 'Sep 28 2026 (getMonth()=8)', key: '2026-9-28' },
  { y: 2026, m0: 0, d: 5, label: 'Jan 5 2026', key: '2026-1-5' },
];

writeFileSync(new URL('../../docs/native/golden/meta-missions.json', import.meta.url), JSON.stringify(out, null, 2));
console.log('wrote meta-missions.json');
