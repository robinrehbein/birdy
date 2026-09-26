// Tiny WebAudio synth for game sound effects; no asset files needed.
let ctx = null;

function ensure() {
  if (!ctx) {
    const AC = window.AudioContext || window.webkitAudioContext;
    if (!AC) return null;
    ctx = new AC();
  }
  if (ctx.state === 'suspended') ctx.resume();
  return ctx;
}

function tone({ freq, to = freq, dur = 0.12, type = 'square', vol = 0.12, delay = 0 }) {
  const ac = ensure();
  if (!ac) return;
  const t = ac.currentTime + delay;
  const osc = ac.createOscillator();
  const gain = ac.createGain();
  osc.type = type;
  osc.frequency.setValueAtTime(freq, t);
  osc.frequency.exponentialRampToValueAtTime(to, t + dur);
  gain.gain.setValueAtTime(vol, t);
  gain.gain.exponentialRampToValueAtTime(0.0001, t + dur);
  osc.connect(gain).connect(ac.destination);
  osc.start(t);
  osc.stop(t + dur + 0.02);
}

export const sfx = {
  unlock: ensure,
  flap: () => tone({ freq: 380, to: 620, dur: 0.09, type: 'triangle', vol: 0.15 }),
  swoosh: () => tone({ freq: 900, to: 300, dur: 0.12, type: 'sine', vol: 0.08 }),
  point: () => {
    tone({ freq: 880, dur: 0.08, type: 'square', vol: 0.07 });
    tone({ freq: 1320, dur: 0.12, type: 'square', vol: 0.07, delay: 0.08 });
  },
  coin: () => {
    tone({ freq: 1400, dur: 0.06, type: 'square', vol: 0.05 });
    tone({ freq: 2100, dur: 0.1, type: 'square', vol: 0.05, delay: 0.05 });
  },
  hit: () => {
    tone({ freq: 220, to: 60, dur: 0.35, type: 'sawtooth', vol: 0.18 });
    tone({ freq: 120, to: 40, dur: 0.5, type: 'square', vol: 0.1, delay: 0.05 });
  },
};
