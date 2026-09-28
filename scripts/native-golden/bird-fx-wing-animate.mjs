// Golden fixture for bird.js animateWings(phase) (bird.js lines 104-110) and
// the wingPhase/wingSpeed integration in main.js (lines 1056-1080, 1824-1829).
// Run: node scripts/native-golden/bird-fx-wing-animate.mjs
import { writeFileSync } from 'node:fs';

function animateWings(phase) {
  const a = Math.sin(phase) * 0.8;
  return {
    wingRotZ_perSide: a, // actual rotation.z = side(-1|1) * a
    propellerRotY: phase * 2.2,
    haloRingY: 0.34 + Math.sin(phase * 0.35) * 0.04,
  };
}

// wingSpeed integration: on flap -> 38 rad/s; relaxes toward 12 rad/s with
// lerp factor min(1, dt*4) applied BEFORE advancing phase by dt*wingSpeed.
const DT = 1 / 60;
function simulate(frames, flapAtFrame = 0) {
  let wingSpeed = 12;
  let phase = 0;
  const rows = [];
  for (let i = 0; i < frames; i++) {
    if (i === flapAtFrame) wingSpeed = 38;
    wingSpeed = wingSpeed + (12 - wingSpeed) * Math.min(1, DT * 4); // lerp toward 12
    phase += DT * wingSpeed;
    rows.push({ frame: i, wingSpeed: +wingSpeed.toFixed(6), phase: +phase.toFixed(6), ...animateWings(phase) });
  }
  return rows;
}

const phaseSamples = [0, 0.5, 1, 1.5707963267948966, 3.141592653589793, 4.71238898038469, 6.283185307179586].map((phase) => ({
  phase,
  ...animateWings(phase),
}));

const out = {
  source: 'src/bird.js lines 104-110; src/main.js lines 1056-1080 (flap), 1824-1829 (per-frame)',
  formulas: {
    animateWings: {
      a: 'sin(phase) * 0.8',
      wingRotationZ: 'side * a  // side is -1 (left) or +1 (right), mirrored via wing.scale.x = side',
      propellerRotationY: 'phase * 2.2  // only visible while the propeller-cap hat is equipped',
      haloRingPositionY: '0.34 + sin(phase * 0.35) * 0.04  // only visible while the halo hat is equipped',
    },
    wingSpeedOnFlap: 'state.wingSpeed = 38  // rad/s, set instantly in flap()',
    wingSpeedRelax: 'wingSpeed = lerp(wingSpeed, 12, min(1, dt * 4))  // every frame except mode === "over"',
    phaseIntegration: 'wingPhase += dt * wingSpeed  // every frame except mode === "over"; phase is NOT wrapped, grows unbounded (fine for sin/cos)',
  },
  phaseSamples,
  simulation60fps_flapAtFrame0: simulate(60, 0),
};
writeFileSync(new URL('../../docs/native/golden/bird-fx-wing-animate.json', import.meta.url), JSON.stringify(out, null, 2) + '\n');
console.log(`wrote ${out.simulation60fps_flapAtFrame0.length} sim frames + ${phaseSamples.length} phase samples`);
