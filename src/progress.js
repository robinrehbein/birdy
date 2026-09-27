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

// Flight trails: a second cosmetic category. Particles behind the bird.
export const TRAILS = [
  { id: 'none', name: 'Keine Spur', price: 0, colors: [] },
  { id: 'sparkle', name: 'Funkeln', price: 150, colors: [0xfff176, 0xffffff, 0xffd400], size: 0.08, life: 0.45, gravity: 0, speed: 0.8 },
  { id: 'bubbles', name: 'Blasen', price: 300, colors: [0xbfe9ff, 0xe8f7ff, 0x8fd3ff], size: 0.14, life: 0.8, gravity: 2.5, speed: 0.5 },
  { id: 'confetti', name: 'Konfetti', price: 500, colors: [0xff5a5a, 0x5ad1ff, 0xffd84a, 0x7be07b, 0xc58bff], size: 0.09, life: 0.7, gravity: -4, speed: 2 },
  { id: 'leaves', name: 'Herbstlaub', price: 700, colors: [0xe8772e, 0xf2a93b, 0xd9492f], size: 0.12, life: 0.9, gravity: -2, speed: 1.2 },
  { id: 'stardust', name: 'Sternenstaub', price: 1000, colors: [0xc58bff, 0xffffff, 0x8f7bff], size: 0.07, life: 0.9, gravity: 0.5, speed: 0.6 },
  { id: 'fire', name: 'Feuerschweif', price: 1400, colors: [0xff7a1a, 0xffc93c, 0xff3d2e], size: 0.13, life: 0.4, gravity: 3, speed: 0.9 },
];

// Long-term achievements (milestones). `stat` is a lifetime value; rewards
// are paid out the moment one is unlocked.
export const ACHIEVEMENTS = [
  { id: 'score10', icon: '🐣', name: 'Abgehoben', text: '10 Punkte in einem Flug', stat: 'bestScore', goal: 10, reward: 30 },
  { id: 'score25', icon: '🐤', name: 'Flugschüler', text: '25 Punkte in einem Flug', stat: 'bestScore', goal: 25, reward: 60 },
  { id: 'score50', icon: '🦅', name: 'Himmelsstürmer', text: '50 Punkte in einem Flug', stat: 'bestScore', goal: 50, reward: 120 },
  { id: 'score100', icon: '👑', name: 'Legende', text: '100 Punkte in einem Flug', stat: 'bestScore', goal: 100, reward: 300 },
  { id: 'zone4', icon: '🌸', name: 'Weltenbummler', text: 'Erreiche den Blütenhain (Zone 4)', stat: 'bestZone', goal: 3, reward: 150 },
  { id: 'near10', icon: '😬', name: 'Haarscharf', text: '10× „Knapp!“ insgesamt', stat: 'nearTotal', goal: 10, reward: 40 },
  { id: 'chain5', icon: '🔥', name: 'Nervenkitzel', text: '5× „Knapp!“ in Folge', stat: 'bestChain', goal: 5, reward: 150 },
  { id: 'powers3', icon: '🌈', name: 'Power-Sammler', text: '3 Power-ups in einem Flug', stat: 'bestPowerups', goal: 3, reward: 60 },
  { id: 'plants25', icon: '🌱', name: 'Gärtner', text: 'An 25 Piranha-Pflanzen vorbei', stat: 'plantsTotal', goal: 25, reward: 80 },
  { id: 'coins500', icon: '🪙', name: 'Sparschwein', text: '500 Münzen eingesammelt', stat: 'coinsTotal', goal: 500, reward: 80 },
  { id: 'coins2000', icon: '💰', name: 'Schatzmeister', text: '2000 Münzen eingesammelt', stat: 'coinsTotal', goal: 2000, reward: 200 },
  { id: 'runs50', icon: '🎮', name: 'Dauerflieger', text: '50 Runden gespielt', stat: 'runs', goal: 50, reward: 100 },
  { id: 'streak7', icon: '📅', name: 'Stammgast', text: '7 Tage Geschenk-Serie', stat: 'bestStreak', goal: 7, reward: 200 },
  { id: 'unlock5', icon: '🎨', name: 'Sammler', text: '5 Vögel oder Spuren freigeschaltet', stat: 'unlocks', goal: 5, reward: 100 },
];
const RUN_MAX_STATS = { bestScore: 'score', bestZone: 'zone', bestChain: 'bestChain', bestPowerups: 'powerups' };
const RUN_SUM_STATS = { nearTotal: 'near', plantsTotal: 'plants', coinsTotal: 'coins' };

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
    trails: ['none'],
    trail: 'none',
    stats: {},
    achieved: [],
    ...(data || {}),
  };
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
  if (stat === 'unlocks') return data.owned.length - 1 + data.trails.length - 1;
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
  get skin() { return SKINS.find((s) => s.id === data.skin) || SKINS[0]; },
  owns: (id) => data.owned.includes(id),
  get trail() { return TRAILS.find((t) => t.id === data.trail) || TRAILS[0]; },
  ownsTrail: (id) => data.trails.includes(id),
  buyTrail(id) {
    const t = TRAILS.find((x) => x.id === id);
    if (!t || data.trails.includes(id) || data.coins < t.price) return false;
    data.coins -= t.price;
    data.trails.push(id);
    data.trail = id;
    save();
    return true;
  },
  selectTrail(id) {
    if (!data.trails.includes(id)) return false;
    data.trail = id;
    save();
    return true;
  },

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
