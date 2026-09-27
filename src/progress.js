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
  { id: 'powers', text: (n) => `Schnapp dir ${n} Power-ups`, stat: 'powerups', per: 'day', goals: [2, 4, 6] },
  { id: 'runs', text: (n) => `Spiele ${n} Runden`, stat: 'runs', per: 'day', goals: [3, 5, 8] },
  { id: 'plants', text: (n) => `Flieg an ${n} Piranha-Pflanzen vorbei`, stat: 'plants', per: 'day', goals: [3, 6, 10] },
  { id: 'moving', text: (n) => `Durchquere ${n} bewegte Lücken`, stat: 'moving', per: 'day', goals: [4, 8, 14] },
  { id: 'star', text: (n) => `Fliege als Regenbogen durch ${n} Reihen`, stat: 'starRows', per: 'day', goals: [3, 6, 10] },
];
const REWARDS = [40, 70, 120];

function today() {
  const d = new Date();
  return `${d.getFullYear()}-${d.getMonth() + 1}-${d.getDate()}`;
}

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

function dailyMissions(date) {
  const rnd = seeded(date);
  const pool = [...MISSION_POOL];
  const list = [];
  for (let tier = 0; tier < 3; tier++) {
    const m = pool.splice(Math.floor(rnd() * pool.length), 1)[0];
    list.push({ id: m.id, goal: m.goals[tier], progress: 0, reward: REWARDS[tier], done: false });
  }
  return { date, list };
}

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
    data.missions = dailyMissions(today());
    save();
  }
  return data.missions.list;
}

export const progress = {
  get coins() { return data.coins; },
  get best() { return data.best; },
  get runs() { return data.runs; },
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
