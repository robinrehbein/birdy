// Golden fixture for the speed-streak instanced mesh (effects.js
// createSpeedLines, lines 80-111): opacity/scale/visibility as a function of
// the "amount" (rush) parameter, and the per-line recycle rule.
// Run: node scripts/native-golden/bird-fx-speedlines.mjs
import { writeFileSync } from 'node:fs';

function frameFor(amount) {
  return {
    amount,
    materialOpacity: +(0.7 * amount).toFixed(6),
    meshVisible: amount > 0.01,
    perLineScaleZ: +(0.6 + amount).toFixed(6), // dummy.scale.set(1,1,0.6+amount); base box depth 3.6 world units
  };
}

const amounts = [0, 0.005, 0.01, 0.011, 0.25, 0.5, 0.75, 1];
const rows = amounts.map(frameFor);

const out = {
  source: 'src/effects.js lines 80-111',
  count: 28,
  geometry: 'BoxGeometry(0.11, 0.11, 3.6)  // thin long box per streak',
  material: 'MeshBasicMaterial: color=0xffffff, transparent=true, depthWrite=false, fog=false; opacity is the ONLY thing update() changes on the shared material',
  placement: {
    formula: 'x = side(-1|1, 50/50) * (4.2 + rand()*5); y = 1 + rand()*11; z = -rand()*60 initially, or -50 - rand()*15 on recycle',
    note: 'x range is therefore [-9.2,-4.2] or [4.2,9.2] (either side of the 3-lane track, which spans roughly x in [-3,3]); y range [1,12]',
  },
  perFrameUpdate: {
    zAdvance: 'line.pos.z += dz * 1.6  // dz = state.speed*dt while playing else 0; 1.6x the world scroll speed so streaks visibly outrun the track',
    recycle: 'if (pos.z > 12) re-place with z = -50 - rand()*15  (and a fresh random x/y as in placement above)',
    earlyOutIfInvisible: 'if amount <= 0.01, mesh.visible=false and the per-line loop is skipped entirely that frame (positions freeze, no z advance) until amount rises again',
  },
  amountParam: 'passed in by main.js as `rush` = clamp(0,1, (state.speed-22)/14 + (star power active ? 0.6 : 0)), 0 when not playing (main.js lines 2067-2069)',
  samples: rows,
};
writeFileSync(new URL('../../docs/native/golden/bird-fx-speedlines.json', import.meta.url), JSON.stringify(out, null, 2) + '\n');
console.log(`wrote ${rows.length} samples`);
