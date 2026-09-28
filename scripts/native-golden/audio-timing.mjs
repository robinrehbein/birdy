// Golden fixture for the transport/timing math in src/audio.js: STEP size,
// music.beat() formula, and the tone() envelope shape (which is pure math
// given fixed inputs, independent of WebAudio's actual DSP).
//
// Run: node scripts/native-golden/audio-timing.mjs
import fs from 'node:fs';

const BPM = 124;
const STEP = 60 / BPM / 4; // seconds per 16th note
const out = {};

out.BPM = BPM;
out.STEP = STEP; // seconds
out.stepMs = STEP * 1000;
out.schedulerIntervalMs = 25; // setInterval(schedule, 25) — audio.js:261
out.scheduleLookaheadSec = 0.12; // while (nextTime < ctx.currentTime + 0.12) — audio.js:247
out.loopLengthSteps = 256; // step = (step + 1) % 256 — audio.js:250
out.barLengthSteps = 16;
out.barsPerFormPart = 4;
out.formParts = 4; // FORM.length -> 16 bars total per loop
out.startOffsetSec = 0.05; // nextTime = startTime = ac.currentTime + 0.05 — audio.js:260

// music.beat(): (ctx.currentTime - startTime) / (STEP * 4)
// i.e. beat is in quarter notes (STEP*4 = one quarter note at 124 BPM).
function beatFromElapsed(elapsedSec) {
  return elapsedSec / (STEP * 4);
}
out.beatFormula = 'beat = (currentTime - startTime) / (STEP * 4)';
out.quarterNoteSec = STEP * 4;
out.beatSamples = [];
for (let elapsed = 0; elapsed <= 4; elapsed += 0.25) {
  out.beatSamples.push({ elapsedSec: Math.round(elapsed * 1e6) / 1e6, beat: Math.round(beatFromElapsed(elapsed) * 1e6) / 1e6 });
}

// tone() envelope (audio.js:86-101): amplitude envelope is exponential attack
// (0.0001 -> vol over 6ms) then exponential decay (vol -> 0.0001 over `dur`
// seconds starting at t+dur... actually decay ramp target time is t+dur, so
// decay spans (t+0.006, t+dur)). Frequency: linear-in-log (exponential) ramp
// from `freq` to `to` over `dur` seconds when to !== freq.
out.toneEnvelope = {
  attackSec: 0.006,
  attackFrom: 0.0001,
  decayToFloor: 0.0001,
  note: 'gain: exponentialRamp(0.0001 -> vol) over first 6ms, then exponentialRamp(vol -> 0.0001) finishing at t+dur. Oscillator stops at t+dur+0.02.',
  freqRampNote: 'if to !== freq: frequency.exponentialRampToValueAtTime(to, t+dur) starting from freq set at t (i.e. exponential/log-linear glide over the full note duration)',
};

// noiseHit() envelope (audio.js:103-115): gain set to vol at `at`, then
// exponential decay to 0.0001 by at+dur. Source stops at at+dur+0.02.
// Playback start offset into the 1-second noise buffer is Math.random()*0.5
// (not reproducible; documented as a range in the spec).
out.noiseHitEnvelope = {
  attack: 'instantaneous (setValueAtTime(vol, at))',
  decayToFloor: 0.0001,
  decayEndsAt: 'at + dur',
  stopsAt: 'at + dur + 0.02',
  bufferOffsetRange: [0, 0.5],
};

fs.writeFileSync(
  new URL('../../docs/native/golden/audio-timing.json', import.meta.url),
  JSON.stringify(out, null, 1)
);
console.log('wrote audio-timing.json');
