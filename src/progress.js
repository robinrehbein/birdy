// Persistent player progress: coin wallet, unlocked bird skins and daily
// missions. Everything lives in localStorage (offline, no account).

const KEY = 'birdy-progress';

// Colour sets for the bird. Same model, same low-poly look.
export const SKINS = [
  { id: 'sunny', name: 'Sunny', price: 0, body: 0xf7d23e, belly: 0xfff3c4, wing: 0xfff6d5, cover: 0xf6e3a1, tail: 0xf2c230, beak: 0xf57c21, beakLow: 0xe0521b },
  { id: 'sky', name: 'Himmel', price: 100, body: 0x4aa8f0, belly: 0xe8f6ff, wing: 0xdff1ff, cover: 0x9fd2fa, tail: 0x2f86d0, beak: 0xf5a623, beakLow: 0xe07b1b },
  { id: 'cardinal', name: 'Kardinal', price: 250, body: 0xe8453c, belly: 0xffd7c9, wing: 0xffe3dc, cover: 0xf28b82, tail: 0xc4302b, beak: 0xffc93c, beakLow: 0xf0a020 },
  { id: 'mint', name: 'Minze', price: 400, body: 0x5fd39a, belly: 0xeafff3, wing: 0xe3fff0, cover: 0xa6ecc8, tail: 0x3bb37b, beak: 0xff8a5c, beakLow: 0xe8643a },
  { id: 'flamingo', name: 'Flamingo', price: 600, body: 0xff8fb8, belly: 0xffe6f0, wing: 0xfff0f6, cover: 0xffc2d8, tail: 0xf2649a, beak: 0x4a3b47, beakLow: 0x2f2530 },
  { id: 'night', name: 'Nachteule', price: 900, body: 0x5b4b8a, belly: 0xd9d0f5, wing: 0xc7bdf0, cover: 0x8f80c9, tail: 0x44376e, beak: 0xffc93c, beakLow: 0xe8a820 },
  { id: 'gold', name: 'Goldvogel', price: 1500, body: 0xffc629, belly: 0xfff1b0, wing: 0xffe57a, cover: 0xffd23d, tail: 0xe0a100, beak: 0xff7a1a, beakLow: 0xd9530f, metal: true },
];

// Mission templates. `stat` is what is counted, `per` whether it counts
// within one run (best run) or adds up over the day.
const MISSION_POOL = [
  { id: 'coins', text: (n) => `Sammle ${n} Münzen`, stat: 'coins', per: 'day', goals: [20, 40, 70] },
  { id: 'score', text: (n) => `Erreiche ${n} Punkte in einem Flug`, stat: 'score', per: 'run', goals: [10, 20, 35] },
  { id: 'rows', text: (n) => `Fliege durch ${n} Röhren`, stat: 'score', per: 'day', goals: [30, 60, 100] },
  { id: 'powers', text: (n) => `Schnapp dir ${n} Power-ups`, stat: 'powerups', per: 'day', goals: [2, 4, 6], minBest: 8 },
  { id: 'runs', text: (n) => `Spiele ${n} Runden`, stat: 'runs', per: 'day', goals: [3, 5, 8] },
  // Only offered once the player has seen these obstacles (best score).
  { id: 'plants', text: (n) => `Flieg an ${n} Piranha-Pflanzen vorbei`, stat: 'plants', per: 'day', goals: [3, 6, 10], minBest: 14 },
  { id: 'moving', text: (n) => `Durchquere ${n} bewegte Lücken`, stat: 'moving', per: 'day', goals: [4, 8, 14], minBest: 10 },
  { id: 'star', text: (n) => `Fliege als Regenbogen durch ${n} Reihen`, stat: 'starRows', per: 'day', goals: [3, 6, 10], minBest: 12 },
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
    ...(data || {}),
  };
  // Migrate the old best score.
  try {
    data.best = Math.max(data.best, Number(localStorage.getItem('birdy-best')) || 0);
  } catch { /* ignore */ }
  return data;
}

const data = load();

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
  get skin() { return SKINS.find((s) => s.id === data.skin) || SKINS[0]; },
  owns: (id) => data.owned.includes(id),

  missions() {
    return missions().map((m) => {
      const tpl = MISSION_POOL.find((t) => t.id === m.id);
      return { ...m, text: tpl.text(m.goal) };
    });
  },

  buy(id) {
    const skin = SKINS.find((s) => s.id === id);
    if (!skin || data.owned.includes(id) || data.coins < skin.price) return false;
    data.coins -= skin.price;
    data.owned.push(id);
    data.skin = id;
    save();
    return true;
  },
  select(id) {
    if (!data.owned.includes(id)) return false;
    data.skin = id;
    save();
    return true;
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
    save();
    return { isBest, completed };
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
