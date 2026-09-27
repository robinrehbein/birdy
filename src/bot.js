// Playtest bots for the headless simulation (?sim). They play with the same
// one-tap controls as a touch player: every action picks a lane AND flaps.
// Skill levels differ in reaction time, aim noise, look-ahead and mistakes.
const GRAVITY = 36;
const FLAP = 11.5;
const HOP = 6; // a tap on another lane only hops
const BPM = 124;
import { PLANT_HEIGHT, PLANT_REACH } from './world.js';

const SKILLS = {
  // First-time player: sees rows late, aims sloppily, sometimes picks badly.
  novice: { plan2: false, delay: 0.22, anticipate: 0.6, interval: 0.2, noise: 0.9, lookAhead: 30, mistake: 0.12, predict: false, lateSwitch: false, apexCheck: 0.4 },
  // Casual player after a few runs.
  good: { plan2: false, delay: 0.15, anticipate: 0.9, interval: 0.12, noise: 0.45, lookAhead: 45, mistake: 0.04, predict: false, lateSwitch: true, apexCheck: 0.85 },
  // Near-perfect play: used to detect rows that are impossible (unfair).
  pro: { plan2: true, delay: 0, anticipate: 1, interval: 1 / 60, noise: 0, lookAhead: 80, mistake: 0, predict: true, lateSwitch: true, apexCheck: 1 },
};

function plantRise(beat) {
  const p = ((beat % 4) + 4) % 4;
  if (p < 2) return 0;
  if (p < 2.4) return (p - 2) / 0.4;
  if (p < 3.9) return 1;
  return 0;
}

export function createBot({ skill = 'good', hop = HOP } = {}) {
  const P = SKILLS[skill];
  let cooldown = 0;
  let target = null;
  let lane = 1;
  let aimOffset = 0;

  function pickLane(gate, state) {
    const options = gate.lanes
      .map((l, i) => ({ l, i }))
      .filter(({ l }) => !l.blocked);
    if (Math.random() < P.mistake) return options[Math.floor(Math.random() * options.length)].i;
    let bestI = options[0].i;
    let bestCost = Infinity;
    for (const { l, i } of options) {
      const cost =
        Math.abs(i - state.lane) * 1.2 +
        Math.abs((l.gapLow + l.gapHigh) / 2 - state.y) * 0.25 +
        (l.hasPlant ? 2.5 : 0) +
        (l.amp ? 1.5 : 0) -
        (l.gapHigh - l.gapLow) * 0.3;
      if (cost < bestCost) {
        bestCost = cost;
        bestI = i;
      }
    }
    return bestI;
  }

  // Safe band [lo, hi] for the bird's centre when it reaches the gate.
  function band(gate, state) {
    const l = gate.lanes[lane];
    const r = state.radius * 0.8 + 0.15;
    let low = l.gapLow;
    let high = l.gapHigh;
    if (P.predict) {
      const t = Math.max(0, -gate.group.position.z / Math.max(1, state.speed));
      if (l.pulse) {
        // Plan for the narrowest point of a breathing gap.
        const c = (l.gapLow + l.gapHigh) / 2;
        low = Math.max(low, c - l.size * 0.35);
        high = Math.min(high, c + l.size * 0.35);
      }
      if (l.amp) {
        const c = l.center + Math.sin((state.time + t) * l.speed + l.phase) * l.amp;
        low = c - l.size / 2;
        high = c + l.size / 2;
      }
      if (l.hasPlant) {
        const beat = ((state.time + t) * BPM) / 60 + l.plantOffset;
        // Worst case over the crossing window.
        let rise = 0;
        for (let k = -2; k <= 2; k++) rise = Math.max(rise, plantRise(beat + k * 0.12));
        if (rise > 0) low = Math.max(low, low + PLANT_REACH - PLANT_HEIGHT * (1 - rise));
      }
    } else if (l.hasPlant) {
      low = Math.max(low, l.hitLow);
    }
    return [low + r, high - r];
  }

  // Taps take effect after the player's reaction delay.
  const queue = [];

  return {
    decide(dt, ctx) {
      for (const a of queue) a.t -= dt;
      const due = queue.length && queue[0].t <= 0 ? queue.shift() : null;
      // The player already "pressed" these; don't decide them again.
      const pending = queue.length > 0 || !!due;
      const act = pending ? null : think(dt, ctx);
      if (act) {
        if (P.delay > 0) queue.push({ ...act, t: P.delay });
        else return act;
      }
      return due && { lane: due.lane };
    },
  };

  function think(dt, { state, gates }) {
    {
      cooldown -= dt;
      const ahead = gates
        .filter((g) => g.active && !g.passed && g.group.position.z > -P.lookAhead)
        .sort((a, b) => b.group.position.z - a.group.position.z);
      const next = ahead[0] || null;
      const after = ahead[1] || null;
      if (next !== target) {
        target = next;
        if (next) {
          lane = pickLane(next, state);
          aimOffset = (Math.random() - 0.5) * 2 * P.noise;
        }
      }

      let aim = 6;
      let ceiling = 13;
      if (target) {
        const [lo, hi] = band(target, state);
        let want = (lo + hi) / 2;
        // Skilled players already line up for the row after this one.
        if (P.plan2 && after) {
          const open = after.lanes.filter((l) => !l.blocked);
          const l2 = after.lanes[lane].blocked ? open[0] : after.lanes[lane];
          if (l2) want = (l2.gapLow + l2.gapHigh) / 2;
        }
        // Keep the flap oscillation (about ±0.9) inside the band.
        const a = Math.min(lo + 1, (lo + hi) / 2);
        const b = Math.max(hi - 1, (lo + hi) / 2);
        aim = Math.max(lo, Math.min(hi, Math.max(a, Math.min(b, want)) + aimOffset));
        ceiling = hi;
      }
      if (cooldown > 0) return null;

      const react = Math.max(P.delay * P.anticipate, 0.05);
      const yPred = state.y + state.vy * react - 0.5 * GRAVITY * react * react;
      const apex = state.y + (FLAP * FLAP) / (2 * GRAVITY);
      const wantsSwitch = lane !== state.lane;
      const close = target && target.group.position.z > -14;
      // Would a flap now carry the bird into the upper pipe? (Players judge
      // this by eye; less skilled ones often don't.)
      const apexSafe = apex < ceiling + 0.12 || Math.random() > P.apexCheck;
      const needFlap = yPred < aim - 0.9 && apexSafe;

      if (wantsSwitch) {
        // Switching lanes costs only a small hop.
        const v = Math.max(state.vy, hop);
        const hopApex = state.y + (v > 0 ? (v * v) / (2 * GRAVITY) : 0);
        const hopSafe = hopApex < ceiling + 0.12 || Math.random() > P.apexCheck;
        if (!P.lateSwitch || close || needFlap || hopSafe || state.vy < 0) {
          cooldown = P.interval;
          return { lane };
        }
        return null;
      }
      if (needFlap) {
        cooldown = P.interval;
        return { lane };
      }
      return null;
    }
  }
}
