// Golden fixture for the power-up aura ring (effects.js createAura,
// lines 196-229) across all three kinds, sampled over time.
// Run: node scripts/native-golden/bird-fx-aura.mjs
import { writeFileSync } from 'node:fs';

// three.js Color.setHSL(h, s, l) -> sRGB byte-ish floats (0..1), same
// algorithm as three.js Color.js for reference/verification in the port.
function hslToRgb(h, s, l) {
  h = ((h % 1) + 1) % 1;
  function hue2rgb(p, q, t) {
    if (t < 0) t += 1;
    if (t > 1) t -= 1;
    if (t < 1 / 6) return p + (q - p) * 6 * t;
    if (t < 1 / 2) return q;
    if (t < 2 / 3) return p + (q - p) * (2 / 3 - t) * 6;
    return p;
  }
  if (s === 0) return { r: l, g: l, b: l };
  const q = l < 0.5 ? l * (1 + s) : l + s - l * s;
  const p = 2 * l - q;
  return { r: hue2rgb(p, q, h + 1 / 3), g: hue2rgb(p, q, h), b: hue2rgb(p, q, h - 1 / 3) };
}

function auraStar(time, size) {
  const h = (time * 1.5) % 1;
  return { color: hslToRgb(h < 0 ? h + 1 : h, 1, 0.6), opacity: 0.75, scale: size * (1.25 + 0.08 * Math.sin(time * 12)) };
}
function auraMagnet(time, size) {
  const k = (time * 1.6) % 1;
  return { colorHex: 0xff4a4a, opacity: 0.7 * (1 - k), scale: size * (1 + 2.2 * k) };
}
function auraMini(time, size) {
  return { colorHex: 0xc58bff, opacity: 0.85, scale: size * (1.5 + 0.15 * Math.sin(time * 8)) };
}

const size = 1.1; // BIRD_SCALE (baseScale passed in)
const times = [0, 0.1, 0.25, 0.333, 0.5, 0.625, 0.75, 1, 1.5, 2];
const rows = times.map((time) => ({
  time,
  star: auraStar(time, size),
  magnet: auraMagnet(time, size),
  mini: auraMini(time, size),
}));

const out = {
  source: 'src/effects.js lines 196-229',
  ringGeometry: 'RingGeometry(innerRadius=0.82, outerRadius=1, thetaSegments=40)',
  materialBase: 'MeshBasicMaterial: transparent, depthWrite=false, toneMapped=false, fog=false, side=DoubleSide, color starts 0xffffff',
  renderOrder: 9,
  billboarding: 'ring.quaternion = camera.quaternion every frame it is visible (full billboard, not just Y-axis)',
  formulas: {
    star: {
      color: 'HSL((time*1.5) % 1, s=1, l=0.6)  // cycles hue once every 2/3 s',
      opacity: '0.75 (constant)',
      scale: 'size * (1.25 + 0.08 * sin(time*12))',
    },
    magnet: {
      colorHex: '0xff4a4a (constant)',
      opacity: '0.7 * (1 - k), k = (time*1.6) % 1  // sawtooth, one wave every 1/1.6 = 0.625s (comment in source incorrectly says "every 0.6 s")',
      scale: 'size * (1 + 2.2 * k)',
    },
    mini: {
      colorHex: '0xc58bff (constant)',
      opacity: '0.85 (constant)',
      scale: 'size * (1.5 + 0.15 * sin(time*8))',
    },
  },
  visibility: 'ring.visible = (kind !== null); kind precedence in main.js is star > magnet > mini (main.js line 1853), and null (hidden) whenever state.mode !== "playing"',
  samples: rows,
};
writeFileSync(new URL('../../docs/native/golden/bird-fx-aura.json', import.meta.url), JSON.stringify(out, null, 2) + '\n');
console.log(`wrote ${rows.length} samples`);
