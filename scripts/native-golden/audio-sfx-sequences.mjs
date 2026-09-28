// Golden fixture for the deterministic parts of the SFX definitions in
// src/audio.js (the `sfx` object, midi() helper). Random components
// (flap pitch jitter, noise buffer phase) are documented as ranges/formulas
// in the spec instead of fixed numbers, since they are seeded from
// Math.random()/performance.now() and are not reproducible.
//
// Run: node scripts/native-golden/audio-sfx-sequences.mjs
import fs from 'node:fs';

const midi = (m) => 440 * Math.pow(2, (m - 69) / 12);

const out = {};

// midi() reference table for notes actually used anywhere in audio.js,
// rounded to 6 decimals of Hz.
const usedMidiNotes = [40, 41, 45, 48, 50, 52, 53, 55, 57, 59, 60, 62, 64, 67, 69, 71, 72, 74, 76, 77, 79, 81, 84, 86, 88, 91, 93, 96, 100, 103, 105];
out.midiTable = Object.fromEntries(usedMidiNotes.map((n) => [n, Math.round(midi(n) * 1e6) / 1e6]));

// sfx.point(): two fixed tones.
out.point = [
  { freq: 988, dur: 0.08, type: 'square', vol: 0.06, delay: 0 },
  { freq: 1319, dur: 0.16, type: 'square', vol: 0.06, delay: 0.07 },
];

// sfx.coin(): combo 0..7 (clamped), formula k = 2^(combo/12); combo resets to
// 0 if > 700ms since the previous coin, else increments (clamped to 7).
out.coin = { comboMax: 7, comboResetMs: 700 };
out.coin.byCombo = [];
for (let combo = 0; combo <= 7; combo++) {
  const k = Math.pow(2, combo / 12);
  out.coin.byCombo.push({
    combo,
    k: Math.round(k * 1e6) / 1e6,
    tone1: { freq: Math.round(1568 * k * 1e6) / 1e6, dur: 0.06, type: 'square', vol: 0.05, delay: 0 },
    tone2: { freq: Math.round(2093 * k * 1e6) / 1e6, dur: 0.12, type: 'square', vol: 0.05, delay: 0.05 },
  });
}

// sfx.powerup(): fixed 7-note ascending square arpeggio, C major add higher octaves.
out.powerup = [60, 64, 67, 72, 76, 79, 84].map((n, i) => ({
  midi: n, freq: Math.round(midi(n) * 1e6) / 1e6, dur: 0.1, type: 'square', vol: 0.06, delay: Math.round(i * 0.045 * 1e6) / 1e6,
}));

// sfx.powerdown(): fixed 4-note descending triangle.
out.powerdown = [79, 74, 67, 62].map((n, i) => ({
  midi: n, freq: Math.round(midi(n) * 1e6) / 1e6, dur: 0.1, type: 'triangle', vol: 0.12, delay: Math.round(i * 0.06 * 1e6) / 1e6,
}));

// sfx.near(chain): chain clamped to 0..6, base = 76 + chain*2, chord offsets [0,4,7,12].
out.near = { chainMax: 6, byChain: [] };
for (let chain = 0; chain <= 8; chain++) { // include a couple beyond clamp to show clamping
  const clamped = Math.min(chain, 6);
  const base = 76 + clamped * 2;
  out.near.byChain.push({
    chain,
    clampedChain: clamped,
    base,
    notes: [0, 4, 7, 12].map((d, i) => ({
      midi: base + d, freq: Math.round(midi(base + d) * 1e6) / 1e6, dur: 0.07, type: 'square', vol: 0.05, delay: Math.round(i * 0.035 * 1e6) / 1e6,
    })),
  });
}

// sfx.zone(): sweep tone + 5-note major arpeggio starting at delay 0.2.
out.zone = {
  sweep: { freq: 300, to: 900, dur: 0.35, type: 'triangle', vol: 0.12, delay: 0 },
  arpeggio: [72, 76, 79, 84, 88].map((n, i) => ({
    midi: n, freq: Math.round(midi(n) * 1e6) / 1e6, dur: 0.14, type: 'square', vol: 0.05, delay: Math.round((0.2 + i * 0.07) * 1e6) / 1e6,
  })),
};

// sfx.bounce(): single tone.
out.bounce = { freq: 200, to: 500, dur: 0.15, type: 'triangle', vol: 0.2, delay: 0 };

// sfx.hit(): two tones + noise hit.
out.hit = {
  tone1: { freq: 220, to: 60, dur: 0.35, type: 'sawtooth', vol: 0.18, delay: 0 },
  tone2: { freq: 120, to: 40, dur: 0.5, type: 'square', vol: 0.1, delay: 0.05 },
  noise: { dur: 0.25, vol: 0.3, cutoff: 400 },
};

// sfx.flap(): random pitch jitter k in [0.94, 1.06), base 380->620 triangle,
// plus a short highpass noise tick. Only the deterministic bounds/shape are
// fixture-worthy; k itself is Math.random()-seeded per call.
out.flap = {
  kMin: 0.94, kMaxExclusive: 1.06, // k = 0.94 + Math.random()*0.12
  toneAtKEquals1: { freq: 380, to: 620, dur: 0.09, type: 'triangle', vol: 0.18 },
  noise: { dur: 0.06, vol: 0.05, cutoff: 3000 },
};

// sfx.swoosh(): single noise hit.
out.swoosh = { dur: 0.18, vol: 0.12, cutoff: 1200 };

fs.writeFileSync(
  new URL('../../docs/native/golden/audio-sfx-sequences.json', import.meta.url),
  JSON.stringify(out, null, 1)
);
console.log('wrote audio-sfx-sequences.json');
