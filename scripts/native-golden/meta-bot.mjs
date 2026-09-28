// Golden fixture for the pure formulas in src/bot.js (the ?sim playtest
// bots): SKILLS table (lines 10-17), plantRise() (lines 19-25, identical to
// world.js's own plantRise used for the real cactus animation — see
// world-plant-timing.json for that copy), pickLane() cost formula (34-54),
// and the flap/apex/needFlap decision math (140-166). This is NOT gameplay
// logic that ships in the native app (the bot is a dev/QA tool, ?sim query
// param), but the ARCHITECTURE doc lists it as commonMain-portable so CI can
// run the same headless playtests against the Kotlin engine; hence a full
// behaviour fixture here too.
// Run: node scripts/native-golden/meta-bot.mjs
// Writes: docs/native/golden/meta-bot.json
import { writeFileSync } from 'node:fs';

const GRAVITY = 36;
const FLAP = 11.5;
const HOP = 6;
const BPM = 124;

const SKILLS = {
  novice: { plan2: false, delay: 0.22, anticipate: 0.6, interval: 0.2, noise: 0.9, lookAhead: 30, mistake: 0.12, predict: false, lateSwitch: false, apexCheck: 0.4 },
  good: { plan2: false, delay: 0.15, anticipate: 0.9, interval: 0.12, noise: 0.45, lookAhead: 45, mistake: 0.04, predict: false, lateSwitch: true, apexCheck: 0.85 },
  pro: { plan2: true, delay: 0, anticipate: 1, interval: 1 / 60, noise: 0, lookAhead: 80, mistake: 0, predict: true, lateSwitch: true, apexCheck: 1 },
};

function plantRise(beat) {
  const p = ((beat % 4) + 4) % 4;
  if (p < 2) return 0;
  if (p < 2.4) return (p - 2) / 0.4;
  if (p < 3.9) return 1;
  return 0;
}

// pickLane() cost formula (lines 41-47), for a synthetic gate with 3 lanes.
function pickLaneCost(l, i, state) {
  return Math.abs(i - state.lane) * 1.2
    + Math.abs((l.gapLow + l.gapHigh) / 2 - state.y) * 0.25
    + (l.hasPlant ? 2.5 : 0)
    + (l.amp ? 1.5 : 0)
    - (l.gapHigh - l.gapLow) * 0.3;
}

const out = { source: 'src/bot.js', constants: { GRAVITY, FLAP, HOP, BPM } };
out.skills = SKILLS;

// plantRise(beat) sampled across a full 4-beat cactus cycle (0 -> flat,
// 2.0-2.4 -> smoothstep-free LINEAR rise (note: bot.js's copy is linear,
// NOT the smoothstep used by world.js's real plantRise — see
// "Porting notes" in meta.md), 2.4-3.9 -> fully up, 3.9-4.0 -> instant drop
// to 0 (bot.js has no fall ramp, unlike world.js which eases both ends).
out.plantRiseSamples = [];
for (let beat = 0; beat <= 8; beat += 0.1) out.plantRiseSamples.push({ beat: +beat.toFixed(2), rise: plantRise(beat) });

// pickLane cost table: 3 synthetic lanes with varying gap center, plant,
// moving-gap flags, for bird currently in lane 1 (middle) at y=6.
const state = { lane: 1, y: 6 };
const lanesA = [
  { gapLow: 4, gapHigh: 8, hasPlant: false, amp: 0 }, // lane 0: centered near bird, plain
  { gapLow: 9, gapHigh: 13, hasPlant: false, amp: 0 }, // lane 1: higher, plain
  { gapLow: 3, gapHigh: 6, hasPlant: true, amp: 0 }, // lane 2: has a cactus (penalised)
];
out.pickLaneCostExample = {
  state,
  lanes: lanesA,
  costs: lanesA.map((l, i) => ({ lane: i, cost: pickLaneCost(l, i, state) })),
  rule: 'pickLane() picks the lane with the LOWEST cost among non-blocked lanes, unless Math.random() < mistake (then a uniformly random non-blocked lane is picked instead)',
};

// Flap decision math (lines 140-166), reproduced as pure functions of
// (state.y, state.vy, aim, ceiling, reactDelay).
function apexOf(y, vy) { return y + (vy * vy) / (2 * GRAVITY); } // used for hopApex with vy=max(state.vy,HOP)
function predictedY(y, vy, react) { return y + vy * react - 0.5 * GRAVITY * react * react; }
const flapApexFromRest = (y0) => y0 + (FLAP * FLAP) / (2 * GRAVITY); // apex if flapping now from y0,vy=FLAP-equivalent — matches bot.js's `apex` (uses current state.y, not vy)

out.flapDecisionFormulas = {
  reactFormula: 'react = max(delay * anticipate, 0.05)',
  predictedYFormula: 'yPred = y + vy*react - 0.5*GRAVITY*react^2',
  apexFormula: 'apex = y + FLAP^2 / (2*GRAVITY)  [NOTE: uses current y and the FIXED flap impulse FLAP=11.5, not the current vy — this estimates "if I flapped right now", not "given my current velocity"]',
  apexSafeFormula: 'apexSafe = apex < ceiling + 0.12 || Math.random() > apexCheck',
  needFlapFormula: 'needFlap = (yPred < aim - 0.9) && apexSafe',
  hopApexFormula: 'hopApex = y + (v>0 ? v^2/(2*GRAVITY) : 0), where v = max(vy, HOP=6)  [a lane-switch tap only applies a small HOP impulse, not a full FLAP]',
  switchGateFormula: '!lateSwitch || close || needFlap || hopSafe || vy<0   (close = target gate z > -14)',
};
out.flapDecisionSamples = [];
for (const y of [3, 6, 9, 12]) {
  for (const vy of [-15, -5, 0, 5, 11.5]) {
    out.flapDecisionSamples.push({
      y, vy,
      apex: flapApexFromRest(y),
      predictedY_react0_15: +predictedY(y, vy, 0.15).toFixed(4),
      hopApex_vyOrHop: +apexOf(y, Math.max(vy, HOP)).toFixed(4),
    });
  }
}

// aim() clamp formula (lines 122-137): the target y is clamped into
// [lo+1, hi-1] (keeping the ~0.9-amplitude flap oscillation inside the gap),
// then an aimOffset noise term (fixed per-gate, drawn once) is added and the
// whole thing re-clamped to [lo,hi].
out.aimClampFormula = {
  formula: 'a = min(lo+1, mid); b = max(hi-1, mid); clampedWant = max(a, min(b, want)); aim = clamp(clampedWant + aimOffset, lo, hi)  where mid=(lo+hi)/2, want = mid (or the pre-planned next-gate center if plan2)',
  aimOffsetFormula: 'aimOffset = (Math.random()-0.5) * 2 * noise, drawn ONCE per newly-targeted gate (not per frame)',
};

writeFileSync(new URL('../../docs/native/golden/meta-bot.json', import.meta.url), JSON.stringify(out, null, 2));
console.log('wrote meta-bot.json');
