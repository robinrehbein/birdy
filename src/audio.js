// WebAudio synth for music and sound effects; no asset files needed.
let ctx = null;
let master = null;
let musicBus = null;
let sfxBus = null;
let noise = null;
let suspended = false;
let offline = false; // true while rendering music offline (tests/preview)

const MUSIC_VOLUME = 0.3;

function loadMuted() {
  try { return localStorage.getItem('birdy-muted') === '1'; } catch { return false; }
}
let muted = loadMuted();

function ensure() {
  if (!ctx) {
    const AC = window.AudioContext || window.webkitAudioContext;
    if (!AC) return null;
    ctx = new AC();
    master = ctx.createGain();
    master.gain.value = muted ? 0 : 1;
    master.connect(ctx.destination);
    musicBus = ctx.createGain();
    musicBus.gain.value = MUSIC_VOLUME;
    musicBus.connect(master);
    sfxBus = ctx.createGain();
    sfxBus.connect(master);
    noise = ctx.createBuffer(1, ctx.sampleRate, ctx.sampleRate);
    const data = noise.getChannelData(0);
    for (let i = 0; i < data.length; i++) data[i] = Math.random() * 2 - 1;
  }
  if (ctx.state === 'suspended' && !suspended && !offline) ctx.resume();
  return ctx;
}

const midi = (m) => 440 * Math.pow(2, (m - 69) / 12);

function tone({ freq, to = freq, dur = 0.12, type = 'square', vol = 0.12, delay = 0, at, dest }) {
  const ac = ensure();
  if (!ac) return;
  const t = at ?? ac.currentTime + delay;
  const osc = ac.createOscillator();
  const gain = ac.createGain();
  osc.type = type;
  osc.frequency.setValueAtTime(freq, t);
  if (to !== freq) osc.frequency.exponentialRampToValueAtTime(to, t + dur);
  gain.gain.setValueAtTime(0.0001, t);
  gain.gain.exponentialRampToValueAtTime(vol, t + 0.006);
  gain.gain.exponentialRampToValueAtTime(0.0001, t + dur);
  osc.connect(gain).connect(dest || sfxBus);
  osc.start(t);
  osc.stop(t + dur + 0.02);
}

function noiseHit({ at, dur, vol, cutoff, dest }) {
  const src = ctx.createBufferSource();
  src.buffer = noise;
  const filter = ctx.createBiquadFilter();
  filter.type = 'highpass';
  filter.frequency.value = cutoff;
  const gain = ctx.createGain();
  gain.gain.setValueAtTime(vol, at);
  gain.gain.exponentialRampToValueAtTime(0.0001, at + dur);
  src.connect(filter).connect(gain).connect(dest || sfxBus);
  src.start(at, Math.random() * 0.5);
  src.stop(at + dur + 0.02);
}

// --- Music: chiptune song with sections and a theme per zone ----------------
// Tempo stays at 124 BPM in every theme so the piranha plants stay on beat.
const BPM = 124;
const STEP = 60 / BPM / 4; // 16th note
const TRIADS = {
  C: [60, 64, 67], Am: [57, 60, 64], F: [53, 57, 60], G: [55, 59, 62],
  Em: [52, 55, 59], Dm: [50, 53, 57],
};
// [step, midi note, length in steps] over 4 bars (64 steps).
const MELODY_A = [
  [0, 72, 2], [2, 76, 2], [4, 79, 2], [6, 76, 2], [8, 77, 2], [10, 76, 2], [12, 74, 2], [14, 72, 2],
  [16, 72, 4], [20, 69, 2], [22, 72, 2], [24, 76, 4], [28, 74, 4],
  [32, 72, 2], [34, 77, 2], [36, 81, 2], [38, 77, 2], [40, 76, 2], [42, 74, 2], [44, 72, 2], [46, 69, 2],
  [48, 71, 4], [52, 74, 4], [56, 79, 2], [58, 77, 2], [60, 74, 2], [62, 71, 2],
];
const MELODY_B = [
  [0, 77, 4], [4, 76, 2], [6, 74, 2], [8, 72, 4], [12, 69, 4],
  [16, 74, 2], [18, 76, 2], [20, 77, 2], [22, 79, 2], [24, 76, 4], [28, 74, 4],
  [32, 76, 4], [36, 79, 2], [38, 76, 2], [40, 74, 2], [42, 72, 2], [44, 71, 4],
  [48, 72, 2], [50, 74, 2], [52, 76, 4], [56, 72, 2], [58, 71, 2], [60, 69, 4],
];
const toMap = (m) => new Map(m.map(([st, n, l]) => [st, [n, l]]));
const SECTIONS = {
  A: { chords: ['C', 'Am', 'F', 'G'], melody: toMap(MELODY_A) },
  B: { chords: ['F', 'G', 'Em', 'Am'], melody: toMap(MELODY_B) },
};
// 16-bar song form: A, A with a harmony voice, B, A as a breakdown.
const FORM = [
  { sec: 'A' },
  { sec: 'A', harmony: true },
  { sec: 'B' },
  { sec: 'A', breakdown: true },
];

// One theme per zone: key, instruments and groove.
const THEMES = [
  { key: 0, lead: 'square', leadVol: 0.07, arp: [0, 1, 2, 1], arpType: 'square', drums: 'pop' }, // Stadtpark
  { key: -3, lead: 'triangle', leadVol: 0.16, arp: [0, 2, 1, 2], arpType: 'square', drums: 'shuffle' }, // Herbstwald
  { key: -5, lead: 'sawtooth', leadVol: 0.04, arp: [0, 1, 0, 2], arpType: 'triangle', drums: 'desert', minor: true }, // Canyon
  { key: 2, lead: 'sine', leadVol: 0.12, octave: 12, arp: [0, 1, 2, 3], arpType: 'sine', drums: 'light' }, // Blütenhain
];
const BASS = { 0: 0, 3: 0, 6: 12, 8: 0, 11: 0, 14: 7 };

let playing = false;
let step = 0;
let nextTime = 0;
let startTime = 0;
let timer = null;
let hype = false;
let theme = THEMES[0];
let pendingTheme = null;
let mode = 'game'; // game | menu (calm: no drums)
let pendingMode = null;

function drums(kind, inBar, t) {
  const kick = (v = 0.9) => tone({ freq: 150, to: 45, dur: 0.14, type: 'sine', vol: v, at: t, dest: musicBus });
  const snare = (v = 0.35) => noiseHit({ at: t, dur: 0.12, vol: v, cutoff: 1500, dest: musicBus });
  const hat = (v) => noiseHit({ at: t, dur: 0.03, vol: v, cutoff: 7000, dest: musicBus });
  if (kind === 'pop') {
    if (inBar % 8 === 0) kick();
    if (inBar === 4 || inBar === 12) snare();
    if (inBar % 2 === 0) hat(inBar % 4 === 2 ? 0.14 : 0.07);
  } else if (kind === 'shuffle') {
    if (inBar === 0 || inBar === 10) kick();
    if (inBar === 4 || inBar === 12) snare();
    if (inBar === 14) snare(0.12);
    if ([2, 3, 6, 7, 10, 11, 14, 15].includes(inBar)) hat(inBar % 2 ? 0.05 : 0.12);
  } else if (kind === 'desert') {
    if (inBar === 0 || inBar === 6 || inBar === 12) kick(0.85);
    if (inBar === 8) snare(0.3);
    if (inBar % 4 === 2) hat(0.1);
    if (inBar === 3 || inBar === 11) tone({ freq: 220, to: 110, dur: 0.12, type: 'sine', vol: 0.3, at: t, dest: musicBus });
  } else {
    if (inBar === 0 || inBar === 8) kick(0.7);
    if (inBar === 12) snare(0.22);
    if (inBar % 2 === 1) hat(0.05);
  }
}

function playStep(s, t) {
  const bar = Math.floor(s / 16); // 0..15
  const inBar = s % 16;
  if (inBar === 0) {
    // Theme and mode changes land on a bar line so they stay musical.
    if (pendingTheme) { theme = pendingTheme; pendingTheme = null; }
    if (pendingMode) { mode = pendingMode; pendingMode = null; }
  }
  const part = FORM[Math.floor(bar / 4)];
  const sec = SECTIONS[part.sec];
  const barInSec = bar % 4;
  let chordName = sec.chords[barInSec];
  if (theme.minor && part.sec === 'A') chordName = ['Am', 'F', 'C', 'G'][barInSec];
  const k = theme.key;
  const chord = TRIADS[chordName].map((n) => n + k);
  const calm = mode === 'menu';
  const breakdown = part.breakdown && barInSec < 2;

  if (!calm && !breakdown) drums(theme.drums, inBar, t);
  else if (!calm && breakdown && inBar % 4 === 2) noiseHit({ at: t, dur: 0.03, vol: 0.06, cutoff: 7000, dest: musicBus });

  // Bass
  if (BASS[inBar] !== undefined && !(breakdown && barInSec === 0)) {
    tone({ freq: midi(chord[0] - 12 + BASS[inBar]), dur: STEP * 1.6, type: 'triangle', vol: calm ? 0.3 : 0.55, at: t, dest: musicBus });
  }

  // Arpeggio
  const arpEvery = theme.arp.length === 4 && theme.drums === 'light' ? 1 : 2;
  if (s % arpEvery === 0) {
    const i = theme.arp[(s / arpEvery) % theme.arp.length];
    const n = i === 3 ? chord[0] + 12 : chord[i];
    tone({ freq: midi(n + 12), dur: STEP * 0.9, type: theme.arpType, vol: theme.arpType === 'square' ? 0.05 : 0.09, at: t, dest: musicBus });
  }
  // Extra sparkle layer while the rainbow power-up is active.
  if (hype && !calm) {
    const idx = [0, 1, 2, 1][s % 4];
    tone({ freq: midi(chord[idx] + 24), dur: STEP * 0.7, type: 'square', vol: 0.035, at: t, dest: musicBus });
  }

  // Melody (in the breakdown only in its second half).
  const note = sec.melody.get(s % 64);
  if (note && !(part.breakdown && barInSec < 2)) {
    const n = note[0] + k + (theme.octave || 0);
    const vol = theme.leadVol * (calm ? 0.6 : 1);
    tone({ freq: midi(n), dur: STEP * note[1] * 0.95, type: theme.lead, vol, at: t, dest: musicBus });
    if (part.harmony) tone({ freq: midi(n - 4), dur: STEP * note[1] * 0.9, type: theme.lead, vol: vol * 0.5, at: t, dest: musicBus });
  }
}

function schedule() {
  if (!ctx || suspended) return;
  while (nextTime < ctx.currentTime + 0.12) {
    playStep(step, nextTime);
    nextTime += STEP;
    step = (step + 1) % 256;
  }
}

export const music = {
  start() {
    const ac = ensure();
    if (!ac || playing) return;
    playing = true;
    step = 0;
    nextTime = startTime = ac.currentTime + 0.05;
    timer = setInterval(schedule, 25);
  },
  // Current position in beats (quarter notes), or null if music isn't running.
  beat() {
    if (!playing || !ctx) return null;
    return (ctx.currentTime - startTime) / (STEP * 4);
  },
  setHype(v) {
    hype = v;
  },
  // Zone theme (0..3), switched on the next bar line.
  setTheme(i) {
    const next = THEMES[((i % THEMES.length) + THEMES.length) % THEMES.length];
    if (next !== theme) pendingTheme = next;
  },
  // 'game' or 'menu' (calm version without drums), on the next bar line.
  setMode(m) {
    if (m !== mode) pendingMode = m;
  },
  duck() {
    if (!ctx) return;
    const t = ctx.currentTime;
    musicBus.gain.cancelScheduledValues(t);
    musicBus.gain.setTargetAtTime(0.06, t, 0.05);
    musicBus.gain.setTargetAtTime(MUSIC_VOLUME, t + 1.4, 0.4);
  },
  stop() {
    playing = false;
    clearInterval(timer);
  },
};

// Render the music offline (for checking levels and for listening to the
// themes without playing). Returns mono samples at 44.1 kHz.
export async function renderMusic({ seconds = 31, themeIndex = 0, calm = false } = {}) {
  const saved = { ctx, master, musicBus, sfxBus, noise, theme, mode, hype, offline, pendingTheme, pendingMode };
  const rate = 44100;
  const off = new OfflineAudioContext(1, Math.ceil(rate * seconds), rate);
  ctx = off;
  offline = true;
  master = off.createGain();
  master.connect(off.destination);
  musicBus = off.createGain();
  musicBus.gain.value = MUSIC_VOLUME;
  musicBus.connect(master);
  sfxBus = off.createGain();
  sfxBus.connect(master);
  noise = off.createBuffer(1, rate, rate);
  const data = noise.getChannelData(0);
  for (let i = 0; i < data.length; i++) data[i] = Math.random() * 2 - 1;
  theme = THEMES[themeIndex];
  mode = calm ? 'menu' : 'game';
  hype = false;
  pendingTheme = null;
  pendingMode = null;
  for (let t = 0.05, s = 0; t < seconds - 0.2; t += STEP, s = (s + 1) % 256) playStep(s, t);
  const out = await off.startRendering();
  ({ ctx, master, musicBus, sfxBus, noise, theme, mode, hype, offline, pendingTheme, pendingMode } = saved);
  return out.getChannelData(0);
}

export const audio = {
  unlock: ensure,
  get muted() {
    return muted;
  },
  setMuted(v) {
    muted = v;
    try { localStorage.setItem('birdy-muted', v ? '1' : '0'); } catch { /* storage unavailable */ }
    if (master) master.gain.value = v ? 0 : 1;
  },
  // Pause/resume all sound (app in background, pause screen).
  setSuspended(v) {
    suspended = v;
    if (!ctx) return;
    if (v) ctx.suspend();
    else ctx.resume();
  },
};

export const sfx = {
  flap: () => {
    tone({ freq: 380, to: 620, dur: 0.09, type: 'triangle', vol: 0.18 });
    const ac = ensure();
    if (ac) noiseHit({ at: ac.currentTime, dur: 0.06, vol: 0.05, cutoff: 3000 });
  },
  swoosh: () => {
    const ac = ensure();
    if (ac) noiseHit({ at: ac.currentTime, dur: 0.18, vol: 0.12, cutoff: 1200 });
  },
  point: () => {
    tone({ freq: 988, dur: 0.08, type: 'square', vol: 0.06 });
    tone({ freq: 1319, dur: 0.16, type: 'square', vol: 0.06, delay: 0.07 });
  },
  coin: () => {
    tone({ freq: 1568, dur: 0.06, type: 'square', vol: 0.05 });
    tone({ freq: 2093, dur: 0.12, type: 'square', vol: 0.05, delay: 0.05 });
  },
  powerup: () => {
    [60, 64, 67, 72, 76, 79, 84].forEach((n, i) =>
      tone({ freq: midi(n), dur: 0.1, type: 'square', vol: 0.06, delay: i * 0.045 })
    );
  },
  powerdown: () => {
    [79, 74, 67, 62].forEach((n, i) => tone({ freq: midi(n), dur: 0.1, type: 'triangle', vol: 0.12, delay: i * 0.06 }));
  },
  // Close call: bright upward chirp, pitched up for chains of close calls.
  near: (chain = 0) => {
    const base = 76 + Math.min(chain, 6) * 2;
    [0, 4, 7, 12].forEach((d, i) => tone({ freq: midi(base + d), dur: 0.07, type: 'square', vol: 0.05, delay: i * 0.035 }));
  },
  // New zone: rising sweep plus a sparkly major arpeggio.
  zone: () => {
    tone({ freq: 300, to: 900, dur: 0.35, type: 'triangle', vol: 0.12 });
    [72, 76, 79, 84, 88].forEach((n, i) => tone({ freq: midi(n), dur: 0.14, type: 'square', vol: 0.05, delay: 0.2 + i * 0.07 }));
  },
  bounce: () => tone({ freq: 200, to: 500, dur: 0.15, type: 'triangle', vol: 0.2 }),
  hit: () => {
    tone({ freq: 220, to: 60, dur: 0.35, type: 'sawtooth', vol: 0.18 });
    tone({ freq: 120, to: 40, dur: 0.5, type: 'square', vol: 0.1, delay: 0.05 });
    const ac = ensure();
    if (ac) noiseHit({ at: ac.currentTime, dur: 0.25, vol: 0.3, cutoff: 400 });
  },
};
