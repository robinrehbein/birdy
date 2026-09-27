// WebAudio synth for music and sound effects; no asset files needed.
let ctx = null;
let master = null;
let musicBus = null;
let sfxBus = null;
let noise = null;
let suspended = false;

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
  if (ctx.state === 'suspended' && !suspended) ctx.resume();
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

// --- Music: upbeat 4-bar chiptune loop (C – Am – F – G) ---------------------
const BPM = 124;
const STEP = 60 / BPM / 4; // 16th note
const CHORDS = [
  [60, 64, 67], // C
  [57, 60, 64], // Am
  [53, 57, 60], // F
  [55, 59, 62], // G
];
const BASS = { 0: 0, 3: 0, 6: 12, 8: 0, 11: 0, 14: 7 };
// [step, midi note, length in steps]
const MELODY = [
  [0, 72, 2], [2, 76, 2], [4, 79, 2], [6, 76, 2], [8, 77, 2], [10, 76, 2], [12, 74, 2], [14, 72, 2],
  [16, 72, 4], [20, 69, 2], [22, 72, 2], [24, 76, 4], [28, 74, 4],
  [32, 72, 2], [34, 77, 2], [36, 81, 2], [38, 77, 2], [40, 76, 2], [42, 74, 2], [44, 72, 2], [46, 69, 2],
  [48, 71, 4], [52, 74, 4], [56, 79, 2], [58, 77, 2], [60, 74, 2], [62, 71, 2],
];
const melodyAt = new Map(MELODY.map(([s, n, l]) => [s, [n, l]]));

let playing = false;
let step = 0;
let nextTime = 0;
let startTime = 0;
let timer = null;
let hype = false;

function playStep(s, t) {
  const bar = Math.floor(s / 16);
  const inBar = s % 16;
  const chord = CHORDS[bar];

  // Drums
  if (inBar % 8 === 0) tone({ freq: 150, to: 45, dur: 0.14, type: 'sine', vol: 0.9, at: t, dest: musicBus });
  if (inBar === 4 || inBar === 12) noiseHit({ at: t, dur: 0.12, vol: 0.35, cutoff: 1500, dest: musicBus });
  if (inBar % 2 === 0) noiseHit({ at: t, dur: 0.03, vol: inBar % 4 === 2 ? 0.14 : 0.07, cutoff: 7000, dest: musicBus });

  // Bass
  if (BASS[inBar] !== undefined) {
    tone({ freq: midi(chord[0] - 12 + BASS[inBar]), dur: STEP * 1.6, type: 'triangle', vol: 0.55, at: t, dest: musicBus });
  }

  // Arpeggio
  if (s % 2 === 0) {
    const idx = [0, 1, 2, 1][(s / 2) % 4];
    tone({ freq: midi(chord[idx] + 12), dur: STEP * 0.9, type: 'square', vol: 0.05, at: t, dest: musicBus });
  }
  // Extra sparkle layer while the rainbow power-up is active.
  if (hype) {
    const idx = [0, 1, 2, 1][s % 4];
    tone({ freq: midi(chord[idx] + 24), dur: STEP * 0.7, type: 'square', vol: 0.035, at: t, dest: musicBus });
  }

  // Melody
  const note = melodyAt.get(s);
  if (note) tone({ freq: midi(note[0]), dur: STEP * note[1] * 0.95, type: 'square', vol: 0.07, at: t, dest: musicBus });
}

function schedule() {
  if (!ctx || suspended) return;
  while (nextTime < ctx.currentTime + 0.12) {
    playStep(step, nextTime);
    nextTime += STEP;
    step = (step + 1) % 64;
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
