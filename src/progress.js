// Persistent player progress: coin wallet, unlocked bird skins and daily
// missions. Everything lives in localStorage (offline, no account).

const KEY = 'birdy-progress';

import { SKINS, TRAILS, CATALOG, KINDS, UPGRADES, UPGRADE_MAX } from './catalog.js';

export { SKINS, TRAILS };

// Long-term achievements (milestones). `stat` is a lifetime value; rewards
// are paid out the moment one is unlocked.
export const ACHIEVEMENTS = [
  { id: 'score10', icon: '🐣', name: { de: 'Abgehoben', en: "Lift-off" }, text: { de: '10 Punkte in einem Flug', en: 'Score 10 in one flight' }, stat: 'bestScore', goal: 10, reward: 30 },
  { id: 'score25', icon: '🐤', name: { de: 'Flugschüler', en: "Student Pilot" }, text: { de: '25 Punkte in einem Flug', en: 'Score 25 in one flight' }, stat: 'bestScore', goal: 25, reward: 60 },
  { id: 'score50', icon: '🦅', name: { de: 'Himmelsstürmer', en: "Sky Racer" }, text: { de: '50 Punkte in einem Flug', en: 'Score 50 in one flight' }, stat: 'bestScore', goal: 50, reward: 120 },
  { id: 'score100', icon: '👑', name: { de: 'Legende', en: "Legend" }, text: { de: '100 Punkte in einem Flug', en: 'Score 100 in one flight' }, stat: 'bestScore', goal: 100, reward: 300 },
  { id: 'zone4', icon: '🌸', name: { de: 'Weltenbummler', en: "Globetrotter" }, text: { de: 'Erreiche den Blütenhain (Zone 4)', en: 'Reach the Blossom Grove (zone 4)' }, stat: 'bestZone', goal: 3, reward: 150 },
  { id: 'near10', icon: '😬', name: { de: 'Haarscharf', en: "Hair's Breadth" }, text: { de: '10× „Knapp!“ insgesamt', en: '10 close calls in total' }, stat: 'nearTotal', goal: 10, reward: 40 },
  { id: 'chain5', icon: '🔥', name: { de: 'Nervenkitzel', en: "Thrill Seeker" }, text: { de: '5× „Knapp!“ in Folge', en: '5 close calls in a row' }, stat: 'bestChain', goal: 5, reward: 150 },
  { id: 'powers3', icon: '🌈', name: { de: 'Power-Sammler', en: "Power Collector" }, text: { de: '3 Power-ups in einem Flug', en: '3 power-ups in one flight' }, stat: 'bestPowerups', goal: 3, reward: 60 },
  { id: 'plants25', icon: '🌱', name: { de: 'Gärtner', en: "Gardener" }, text: { de: 'An 25 Stachelkakteen vorbei', en: 'Pass 25 spiky cacti' }, stat: 'plantsTotal', goal: 25, reward: 80 },
  { id: 'coins500', icon: '🪙', name: { de: 'Sparschwein', en: "Piggy Bank" }, text: { de: '500 Münzen eingesammelt', en: 'Collect 500 coins' }, stat: 'coinsTotal', goal: 500, reward: 80 },
  { id: 'coins2000', icon: '💰', name: { de: 'Schatzmeister', en: "Treasurer" }, text: { de: '2000 Münzen eingesammelt', en: 'Collect 2000 coins' }, stat: 'coinsTotal', goal: 2000, reward: 200 },
  { id: 'runs50', icon: '🎮', name: { de: 'Dauerflieger', en: "Frequent Flyer" }, text: { de: '50 Runden gespielt', en: 'Play 50 rounds' }, stat: 'runs', goal: 50, reward: 100 },
  { id: 'streak7', icon: '📅', name: { de: 'Stammgast', en: "Regular" }, text: { de: '7 Tage Geschenk-Serie', en: '7-day gift streak' }, stat: 'bestStreak', goal: 7, reward: 200 },
  { id: 'unlock5', icon: '🎨', name: { de: 'Sammler', en: "Collector" }, text: { de: '5 Shop-Artikel freigeschaltet', en: 'Unlock 5 shop items' }, stat: 'unlocks', goal: 5, reward: 100 },
];
const RUN_MAX_STATS = { bestScore: 'score', bestZone: 'zone', bestChain: 'bestChain', bestPowerups: 'powerups' };
const RUN_SUM_STATS = { nearTotal: 'near', plantsTotal: 'plants', coinsTotal: 'coins' };

// Mission templates. `stat` is what is counted, `per` whether it counts
// within one run (best run) or adds up over the day.
const MISSION_POOL = [
  { id: 'coins', text: (n) => ({ de: `Sammle ${n} Münzen`, en: `Collect ${n} coins` }), stat: 'coins', per: 'day', goals: [20, 40, 70] },
  { id: 'score', text: (n) => ({ de: `Erreiche ${n} Punkte in einem Flug`, en: `Score ${n} in one flight` }), stat: 'score', per: 'run', goals: [10, 20, 35] },
  { id: 'rows', text: (n) => ({ de: `Flieg durch ${n} Röhren`, en: `Fly through ${n} pipes` }), stat: 'score', per: 'day', goals: [30, 60, 100] },
  { id: 'powers', text: (n) => ({ de: `Schnapp dir ${n} Power-ups`, en: `Grab ${n} power-ups` }), stat: 'powerups', per: 'day', goals: [2, 4, 6], minBest: 8 },
  { id: 'runs', text: (n) => ({ de: `Spiele ${n} Runden`, en: `Play ${n} rounds` }), stat: 'runs', per: 'day', goals: [3, 5, 8] },
  // Only offered once the player has seen these obstacles (best score).
  { id: 'plants', text: (n) => ({ de: `Flieg an ${n} Kakteen vorbei`, en: `Pass ${n} spiky cacti` }), stat: 'plants', per: 'day', goals: [3, 6, 10], minBest: 14 },
  { id: 'moving', text: (n) => ({ de: `Durchquere ${n} bewegte Lücken`, en: `Fly through ${n} moving gaps` }), stat: 'moving', per: 'day', goals: [4, 8, 14], minBest: 10 },
  { id: 'star', text: (n) => ({ de: `Als Regenbogen durch ${n} Reihen`, en: `Pass ${n} rows as a rainbow` }), stat: 'starRows', per: 'day', goals: [3, 6, 10], minBest: 12 },
];
const REWARDS = [40, 70, 120];

function dayKey(offsetDays = 0) {
  const d = new Date();
  d.setDate(d.getDate() + offsetDays);
  return `${d.getFullYear()}-${d.getMonth() + 1}-${d.getDate()}`;
}
const today = () => dayKey(0);

// Small deterministic RNG so everyone gets the same missions on a day.
function seeded(str) {
  let h = 2166136261;
  for (const c of str) h = Math.imul(h ^ c.charCodeAt(0), 16777619);
  return () => {
    h = Math.imul(h ^ (h >>> 15), 2246822507);
    h = Math.imul(h ^ (h >>> 13), 3266489909);
    return ((h ^= h >>> 16) >>> 0) / 4294967296;
  };
}

// Three missions of rising difficulty, matched to what the player can do:
// obstacle missions only after they have been reached, and the one-run
// score goal scales with the record.
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

// Daily gift with a streak: 20 coins, +10 per consecutive day (max 80).
const GIFT_BASE = 20;
const GIFT_STEP = 10;
const GIFT_MAX_STREAK = 7;

function load() {
  let data = null;
  try {
    data = JSON.parse(localStorage.getItem(KEY));
  } catch { /* storage unavailable or corrupt */ }
  data = {
    coins: 0,
    best: 0,
    runs: 0,
    owned: ['sunny'],
    skin: 'sunny',
    missions: null,
    gift: { last: '', streak: 0 },
    tutorialDone: false,
    trails: ['none'],
    trail: 'none',
    stats: {},
    achieved: [],
    ...(data || {}),
  };
  // Shop: owned items and the equipped one per kind. Older saves only had
  // skins (owned/skin) and trails (trails/trail).
  data.items = { ...(data.items || {}) };
  data.equip = { ...(data.equip || {}) };
  data.items.skin = [...new Set([...(data.items.skin || []), ...data.owned])];
  data.items.trail = [...new Set([...(data.items.trail || []), ...data.trails])];
  data.equip.skin ??= data.skin;
  data.equip.trail ??= data.trail;
  for (const kind of KINDS) {
    const free = CATALOG[kind][0].id;
    if (!data.items[kind]?.includes(free)) data.items[kind] = [free, ...(data.items[kind] || [])];
    if (!data.items[kind].includes(data.equip[kind])) data.equip[kind] = free;
  }
  data.upgrades = { ...(data.upgrades || {}) };
  data.stats = { bestScore: data.best, bestZone: 0, nearTotal: 0, bestChain: 0, bestPowerups: 0, plantsTotal: 0, coinsTotal: 0, bestStreak: 0, ...data.stats };
  // Migrate the old best score.
  try {
    data.best = Math.max(data.best, Number(localStorage.getItem('birdy-best')) || 0);
  } catch { /* ignore */ }
  return data;
}

const data = load();

function statValue(stat) {
  if (stat === 'runs') return data.runs;
  if (stat === 'unlocks') {
    return KINDS.reduce((n, k) => n + data.items[k].length - 1, 0) + Object.values(data.upgrades).reduce((n, l) => n + l, 0);
  }
  return data.stats[stat] || 0;
}

// Unlock every achievement whose goal is reached; pays the reward.
function unlockAchievements() {
  const unlocked = [];
  for (const a of ACHIEVEMENTS) {
    if (data.achieved.includes(a.id) || statValue(a.stat) < a.goal) continue;
    data.achieved.push(a.id);
    data.coins += a.reward;
    unlocked.push(a);
  }
  return unlocked;
}

function save() {
  try {
    localStorage.setItem(KEY, JSON.stringify(data));
  } catch { /* storage unavailable */ }
}

function missions() {
  if (!data.missions || data.missions.date !== today()) {
    data.missions = dailyMissions(today(), data.best);
    save();
  }
  return data.missions.list;
}

export const progress = {
  get coins() { return data.coins; },
  get best() { return data.best; },
  get runs() { return data.runs; },
  // Players from before the tutorial existed (runs > 0) skip it.
  get tutorialDone() { return data.tutorialDone || data.runs > 0; },
  finishTutorial() {
    data.tutorialDone = true;
    save();
  },
  // Shop (see catalog.js). `kind` is one of KINDS.
  equipped(kind) {
    return CATALOG[kind].find((x) => x.id === data.equip[kind]) || CATALOG[kind][0];
  },
  get skin() { return this.equipped('skin'); },
  get trail() { return this.equipped('trail'); },
  owns: (kind, id) => data.items[kind].includes(id),
  buy(kind, id) {
    const item = CATALOG[kind].find((x) => x.id === id);
    if (!item || data.items[kind].includes(id) || data.coins < item.price) return false;
    data.coins -= item.price;
    data.items[kind].push(id);
    data.equip[kind] = id;
    save();
    return true;
  },
  select(kind, id) {
    if (!data.items[kind].includes(id)) return false;
    data.equip[kind] = id;
    save();
    return true;
  },
  // Surprise purchase: pay, then grant() the randomly picked item.
  buySurprise(price) {
    if (data.coins < price) return false;
    data.coins -= price;
    save();
    return true;
  },
  grant(kind, id) {
    if (!data.items[kind].includes(id)) data.items[kind].push(id);
    data.equip[kind] = id;
    save();
  },
  // Upgrade levels 0..UPGRADE_MAX.
  level: (id) => data.upgrades[id] || 0,
  upgradePrice(id) {
    const u = UPGRADES.find((x) => x.id === id);
    const lvl = data.upgrades[id] || 0;
    return lvl < UPGRADE_MAX ? u.prices[lvl] : null;
  },
  buyUpgrade(id) {
    const price = this.upgradePrice(id);
    if (price === null || data.coins < price) return false;
    data.coins -= price;
    data.upgrades[id] = (data.upgrades[id] || 0) + 1;
    save();
    return true;
  },

  missions() {
    return missions().map((m) => {
      const tpl = MISSION_POOL.find((t) => t.id === m.id);
      return { ...m, text: tpl.text(m.goal) };
    });
  },

  // Daily gift: available once per calendar day. Claiming on consecutive
  // days grows the streak.
  giftAvailable() {
    return data.gift.last !== today();
  },
  get streak() {
    const g = data.gift;
    return g.last === today() || g.last === dayKey(-1) ? g.streak : 0;
  },
  giftAmount(streak) {
    return GIFT_BASE + GIFT_STEP * (Math.min(streak, GIFT_MAX_STREAK) - 1);
  },
  claimGift() {
    if (!this.giftAvailable()) return null;
    const streak = data.gift.last === dayKey(-1) ? data.gift.streak + 1 : 1;
    const amount = this.giftAmount(streak);
    data.gift = { last: today(), streak };
    data.coins += amount;
    data.stats.bestStreak = Math.max(data.stats.bestStreak || 0, streak);
    save();
    return { amount, streak };
  },

  // Called once when a run ends. `run` holds the run's counters.
  // Returns the missions completed by this run.
  finishRun(run) {
    data.runs++;
    data.coins += run.coins;
    const isBest = run.score > data.best;
    if (isBest) data.best = run.score;
    for (const [stat, key] of Object.entries(RUN_MAX_STATS)) data.stats[stat] = Math.max(data.stats[stat] || 0, run[key] || 0);
    for (const [stat, key] of Object.entries(RUN_SUM_STATS)) data.stats[stat] = (data.stats[stat] || 0) + (run[key] || 0);
    const completed = [];
    for (const m of missions()) {
      if (m.done) continue;
      const tpl = MISSION_POOL.find((t) => t.id === m.id);
      const value = tpl.stat === 'runs' ? 1 : run[tpl.stat] || 0;
      m.progress = tpl.per === 'run' ? Math.max(m.progress, value) : m.progress + value;
      if (m.progress >= m.goal) {
        m.progress = m.goal;
        m.done = true;
        data.coins += m.reward;
        completed.push({ ...m, text: tpl.text(m.goal) });
      }
    }
    const achievements = unlockAchievements();
    save();
    return { isBest, completed, achievements };
  },

  // Achievements with current progress, for the overview.
  achievements() {
    return ACHIEVEMENTS.map((a) => ({
      ...a,
      value: Math.min(a.goal, statValue(a.stat)),
      done: data.achieved.includes(a.id),
    }));
  },

  // Live check during a run (nothing is stored until finishRun).
  wouldUnlock(run) {
    const hits = [];
    for (const a of ACHIEVEMENTS) {
      if (data.achieved.includes(a.id)) continue;
      let v = statValue(a.stat);
      if (RUN_MAX_STATS[a.stat]) v = Math.max(v, run[RUN_MAX_STATS[a.stat]] || 0);
      if (RUN_SUM_STATS[a.stat]) v += run[RUN_SUM_STATS[a.stat]] || 0;
      if (v >= a.goal) hits.push(a);
    }
    return hits;
  },
  // For achievements earned outside a run (gift streak, unlocks).
  checkAchievements() {
    const unlocked = unlockAchievements();
    if (unlocked.length) save();
    return unlocked;
  },

  // Live check during a run so a mission can be celebrated the moment it
  // is reached (progress is only stored in finishRun).
  wouldComplete(run) {
    const hits = [];
    for (const m of missions()) {
      if (m.done) continue;
      const tpl = MISSION_POOL.find((t) => t.id === m.id);
      const value = tpl.stat === 'runs' ? 0 : run[tpl.stat] || 0;
      const p = tpl.per === 'run' ? Math.max(m.progress, value) : m.progress + value;
      if (p >= m.goal) hits.push(m.id);
    }
    return hits;
  },
};
