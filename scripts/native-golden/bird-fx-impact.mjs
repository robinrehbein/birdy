// Golden fixture for the crash "bonk" star (effects.js createImpact,
// lines 126-176): scale pop-in and opacity fade curves over time since hit().
// Run: node scripts/native-golden/bird-fx-impact.mjs
import { writeFileSync } from 'node:fs';

// three.js MathUtils.smoothstep(x, min, max) reference implementation.
function smoothstep(x, min, max) {
  if (x <= min) return 0;
  if (x >= max) return 1;
  x = (x - min) / (max - min);
  return x * x * (3 - 2 * x);
}

function sample(t) {
  const scale = 0.5 + 0.3 * Math.min(1, t / 0.12);
  const opacity = 1 - smoothstep(t, 0.25, 0.5);
  const visible = t <= 0.5; // bonk.visible set false once t > 0.5 (checked after this sample's update)
  return { t: +t.toFixed(4), scale: +scale.toFixed(6), opacity: +opacity.toFixed(6), visibleAfterThisFrame: t <= 0.5 };
}

const times = [];
for (let t = 0; t <= 0.6; t += 0.02) times.push(+t.toFixed(4));
const rows = times.map(sample);

const out = {
  source: 'src/effects.js lines 126-176',
  layers: [
    { name: 'outline', points: 8, outerRadius: 1.25, innerRadius: 0.62, colorHex: 0x543847, zOffset: 0 },
    { name: 'white', points: 8, outerRadius: 1.08, innerRadius: 0.52, colorHex: 0xffffff, zOffset: 0.01 },
    { name: 'core', points: 8, outerRadius: 0.62, innerRadius: 0.34, colorHex: 0xffe14a, zOffset: 0.02 },
  ],
  starShapeAngleOffset: 'PI/2  // first point straight up',
  merge: 'the three star outlines are baked into ONE non-indexed BufferGeometry with a per-vertex `color` attribute (vertexColors:true material); one draw call for all three layers',
  material: 'MeshBasicMaterial: vertexColors=true, transparent=true, depthTest=false, depthWrite=false, toneMapped=false, fog=false',
  renderOrder: 11,
  onHit: {
    resetT: 't = 0',
    position: 'bonk.position = pos (world space, passed in by caller: HIT_OFFSET-adjusted death point, see main-b spec)',
    visible: true,
    opacity: 1,
    scale: 0.5,
  },
  perFrameUpdate: {
    guard: 'no-op if bonk.visible === false',
    advanceT: 't += dt',
    billboard: 'bonk.quaternion = camera.quaternion, then extra rotateZ(t * 1.5)  // spins around the view axis after billboarding',
    scale: '0.5 + 0.3 * min(1, t/0.12)  // pops from 0.5 to 0.8 over the first 0.12s, then holds at 0.8',
    opacity: '1 - smoothstep(t, 0.25, 0.5)  // holds at 1 until t=0.25, eases to 0 by t=0.5',
    hideAfter: 'if (t > 0.5) bonk.visible = false',
  },
  smoothstepFormula: 'clamp x to [min,max] -> u=(x-min)/(max-min) -> u*u*(3-2u)',
  samples: rows,
};
writeFileSync(new URL('../../docs/native/golden/bird-fx-impact.json', import.meta.url), JSON.stringify(out, null, 2) + '\n');
console.log(`wrote ${rows.length} samples`);
