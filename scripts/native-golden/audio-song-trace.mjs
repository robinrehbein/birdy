// Golden fixture for the music sequencer's note-selection logic in
// src/audio.js (playStep, THEMES, SECTIONS, FORM, BASS).
//
// This re-implements ONLY the pure decision logic of playStep() — which
// notes/drum-hits/etc are chosen at each 16th-note step — WITHOUT any
// WebAudio calls. It must stay numerically identical to audio.js; if you
// change the tuning constants there, regenerate this file.
//
// Run: node scripts/native-golden/audio-song-trace.mjs
import fs from 'node:fs';

const BPM = 124;
const STEP = 60 / BPM / 4; // 16th note, seconds
const midi = (m) => 440 * Math.pow(2, (m - 69) / 12);

const TRIADS = {
  C: [60, 64, 67], Am: [57, 60, 64], F: [53, 57, 60], G: [55, 59, 62],
  Em: [52, 55, 59], Dm: [50, 53, 57],
};
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
const FORM = [
  { sec: 'A' },
  { sec: 'A', harmony: true },
  { sec: 'B' },
  { sec: 'A', breakdown: true },
];
const THEMES = [
  { key: 0, lead: 'square', leadVol: 0.07, arp: [0, 1, 2, 1], arpType: 'square', drums: 'pop' },
  { key: -3, lead: 'triangle', leadVol: 0.16, arp: [0, 2, 1, 2], arpType: 'square', drums: 'shuffle' },
  { key: -5, lead: 'sawtooth', leadVol: 0.04, arp: [0, 1, 0, 2], arpType: 'triangle', drums: 'desert', minor: true },
  { key: 2, lead: 'sine', leadVol: 0.12, octave: 12, arp: [0, 1, 2, 3], arpType: 'sine', drums: 'light' },
];
const BASS = { 0: 0, 3: 0, 6: 12, 8: 0, 11: 0, 14: 7 };

function drumEvents(kind, inBar) {
  const ev = [];
  const kick = (v = 0.9) => ev.push({ type: 'kick', freq: 150, to: 45, dur: 0.14, vol: v });
  const snare = (v = 0.35) => ev.push({ type: 'snare', dur: 0.12, vol: v, cutoff: 1500 });
  const hat = (v) => ev.push({ type: 'hat', dur: 0.03, vol: v, cutoff: 7000 });
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
    if (inBar === 3 || inBar === 11) ev.push({ type: 'desertBlip', freq: 220, to: 110, dur: 0.12, vol: 0.3 });
  } else {
    if (inBar === 0 || inBar === 8) kick(0.7);
    if (inBar === 12) snare(0.22);
    if (inBar % 2 === 1) hat(0.05);
  }
  return ev;
}

// Mirrors playStep(s, t) in audio.js:196-243, but returns a description of
// events instead of touching WebAudio. `t` (absolute schedule time) is not
// needed for note SELECTION, only for playback, so it is omitted here; step
// index `s` (0..255, wrapping every 256 steps = 16 bars) is authoritative.
function playStep(s, theme, mode) {
  const bar = Math.floor(s / 16);
  const inBar = s % 16;
  const part = FORM[Math.floor(bar / 4)];
  const sec = SECTIONS[part.sec];
  const barInSec = bar % 4;
  let chordName = sec.chords[barInSec];
  if (theme.minor && part.sec === 'A') chordName = ['Am', 'F', 'C', 'G'][barInSec];
  const k = theme.key;
  const chord = TRIADS[chordName].map((n) => n + k);
  const calm = mode === 'menu';
  const breakdown = part.breakdown && barInSec < 2;

  const events = { step: s, bar, inBar, section: part.sec, chordName, chord, harmony: !!part.harmony, breakdown };

  const drums = [];
  if (!calm && !breakdown) drums.push(...drumEvents(theme.drums, inBar));
  else if (!calm && breakdown && inBar % 4 === 2) drums.push({ type: 'hat', dur: 0.03, vol: 0.06, cutoff: 7000, note: 'breakdown-tick' });
  events.drums = drums;

  events.bass = null;
  if (BASS[inBar] !== undefined && !(breakdown && barInSec === 0)) {
    const n = chord[0] - 12 + BASS[inBar];
    events.bass = { midi: n, freq: midi(n), dur: STEP * 1.6, vol: calm ? 0.3 : 0.55 };
  }

  const arpEvery = theme.arp.length === 4 && theme.drums === 'light' ? 1 : 2;
  events.arp = null;
  if (s % arpEvery === 0) {
    const i = theme.arp[(s / arpEvery) % theme.arp.length];
    const n = i === 3 ? chord[0] + 12 : chord[i];
    events.arp = { arpIndex: i, midi: n + 12, freq: midi(n + 12), dur: STEP * 0.9, type: theme.arpType, vol: theme.arpType === 'square' ? 0.05 : 0.09 };
  }

  events.hypeSparkle = null; // depends on external `hype` flag, see hype-variant trace below

  events.melody = null;
  const note = sec.melody.get(s % 64);
  if (note && !(part.breakdown && barInSec < 2)) {
    const n = note[0] + k + (theme.octave || 0);
    const vol = theme.leadVol * (calm ? 0.6 : 1);
    events.melody = { midi: n, freq: midi(n), dur: STEP * note[1] * 0.95, type: theme.lead, vol, lengthSteps: note[1] };
    if (part.harmony) events.melody.harmonyVoice = { midi: n - 4, freq: midi(n - 4), dur: STEP * note[1] * 0.9, type: theme.lead, vol: vol * 0.5 };
  }

  return events;
}

function hypeSparkle(s, theme, chord, calm) {
  if (calm) return null;
  const idx = [0, 1, 2, 1][s % 4];
  const n = chord[idx] + 24;
  return { midi: n, freq: midi(n), dur: STEP * 0.7, type: 'square', vol: 0.035 };
}

const out = {
  BPM,
  STEP,
  note: 'One 16-bar cycle = 256 steps; step 256 repeats step 0 (loop point). ' +
    'Only mode="game" is traced in full here: mode="menu" (calm) is a pure ' +
    'function of the game-mode trace — see calmTransform below — so it is ' +
    'not duplicated step-by-step.',
  calmTransform: {
    drums: 'suppressed entirely, EXCEPT during a breakdown bar (part.breakdown && barInSec<2) where game mode plays nothing anyway, so calm has no breakdown tick either (tick is itself gated on !calm)',
    bass: 'vol becomes 0.3 (vs 0.55 in game mode); note/timing unchanged',
    arp: 'unchanged (arp does not depend on calm/mode at all)',
    hypeSparkle: 'never plays in calm mode regardless of the hype flag (gated on `!calm`)',
    melody: 'vol *= 0.6 (vs *1 in game mode); note/timing/harmony unchanged',
  },
  themes: [],
};

for (let ti = 0; ti < THEMES.length; ti++) {
  const theme = THEMES[ti];
  const mode = 'game';
  const steps = [];
  for (let s = 0; s < 256; s++) {
    const e = playStep(s, theme, mode);
    e.hypeSparkleIfHype = hypeSparkle(s, theme, e.chord, false);
    steps.push(e);
  }
  out.themes.push({ themeIndex: ti, key: theme.key, drumsKind: theme.drums, minor: !!theme.minor, mode, steps });
}

fs.writeFileSync(
  new URL('../../docs/native/golden/audio-song-trace.json', import.meta.url),
  JSON.stringify(out)
);
console.log('wrote audio-song-trace.json');
