// Golden fixture for power-up pickup bob/spin/colour-cycle animation
// (powerups.js animatePickup, lines 118-127) and the world-space shrink /
// pickup-radius formulas applied to pickups in main.js (lines 1760-1777).
// Run: node scripts/native-golden/bird-fx-powerup-pickup.mjs
import { writeFileSync } from 'node:fs';

function animatePickup(time) {
  return {
    iconRotationY: time * 2.5,
    iconPositionY: Math.sin(time * 3) * 0.12,
    // Only the star ("rainbow") icon has a rainbow material; magnet/mushroom icons are static-coloured.
    rainbowColorHSL: { h: (time * 0.5) % 1, s: 1, l: 0.55 },
    rainbowEmissiveHSL: { h: (time * 0.5) % 1, s: 1, l: 0.2 },
  };
}

function worldShrink(z) {
  // main.js: k = clamp(0,1, 1 - (p.z - 1.5) / 1.5); group.scale.setScalar(k); group.visible = k > 0
  const k = Math.max(0, Math.min(1, 1 - (z - 1.5) / 1.5));
  return { z, k, visible: k > 0 };
}

const times = [0, 0.5, 1, 1.5, 2, 2.5, 4 / 2.5, 3, 4];
const animRows = times.map((t) => ({ time: t, ...animatePickup(t) }));
const zs = [4.5, 3, 1.5, 1, 0.5, 0, -0.5, -1];
const shrinkRows = zs.map(worldShrink);

const out = {
  source: 'src/powerups.js lines 100-127; src/main.js lines 1760-1777',
  perTypeDurationSeconds: { star: 6, magnet: 9, mini: 9 }, // base, before shop upgrades (see powerDuration in main-a.md)
  colorHex: { star: 0xffd400, magnet: 0xe53935, mini: 0x9b59b6 },
  iconBuild: {
    star: 'ExtrudeGeometry of a 10-point star (outer r=0.5, inner r=0.22), depth 0.14, bevel(thickness 0.06, size 0.05, segments 1), geo.center(); material color 0xffd400 emissive 0x664400, flatShading, roughness 0.4; userData.rainbow = material (drives the HSL cycle)',
    magnet: 'Group: red torus arc (radius 0.3, tube 0.12, radialSeg 6, tubularSeg 12, arc=PI, opening down) + two red cylinder legs (r 0.12, h 0.22) + two metal (0xd9d9d9, metalness 0.6) cylinder tips (r 0.12, h 0.14), legs/tips at x=side*0.3',
    mini: 'mushroom: purple half-sphere cap (r 0.42, scale.y 0.85) at y=0.02 + 5 white spot spheres (r 0.09) placed with setFromSphericalCoords(0.4, phi, theta) at the [theta,phi] pairs [[0,0.3],[1.3,1.0],[2.8,0.9],[4.4,1.0],[5.6,0.95]], then y = y*0.85+0.02 + cream cylinder stem (top r 0.18, bottom r 0.22, h 0.34) at y=-0.15 + two small dark eye spheres (r 0.04) at (±0.07,-0.12,0.2)',
  },
  iconScale: 1.7, // uniform scale applied to whichever icon group is built
  bubble: {
    geometry: 'SphereGeometry(radius 1.15, widthSeg 16, heightSeg 12)  // shared across all 3 types',
    material: 'MeshStandardMaterial: color=POWERUPS[type].color, transparent, opacity 0.3, roughness 0.1, depthWrite=false, emissive=POWERUPS[type].color, emissiveIntensity 0.35',
  },
  rim: {
    geometry: 'merged: RingGeometry(1.14,1.32,32) vertex-coloured white (0xffffff) + RingGeometry(1.32,1.4,32) vertex-coloured plum (0x543847), one BufferGeometry via mergeGeometries, shared across all pickups',
    material: 'MeshBasicMaterial: vertexColors=true, transparent, opacity 0.9, depthWrite=false, toneMapped=false',
    billboard: 'rim.quaternion = camera.quaternion every frame (full billboard; icon and bubble do NOT billboard, only rotate/bob per animatePickup)',
  },
  animateFormulas: {
    iconRotationY: 'time * 2.5  rad/s, constant spin',
    iconBobY: 'sin(time * 3) * 0.12',
    rainbowColor: 'only if icon.userData.rainbow is set (star type): HSL((time*0.5)%1, s=1, l=0.55)',
    rainbowEmissive: 'HSL((time*0.5)%1, s=1, l=0.2)',
  },
  worldSpace: {
    pickupCollisionRadius: '1.4 (hard-coded in main.js line 1772, comment: "Looks only: the pickup radius (1.4, main.js) stays the same" — i.e. independent of the bubble\'s visual radius 1.15/1.4)',
    forwardShrink: 'k = clamp(0,1, 1 - (pos.z - 1.5)/1.5); group.scale.setScalar(k); group.visible = k>0  // same formula used for coins; shrinks a pickup to nothing over the 1.5 world-units just before it would pass behind/through the bird plane (z=0), so it never visibly clips through the camera',
    typeSelection: 'placePickup() (main.js) picks POWERUP_TYPES[floor(rand()*3)] uniformly, then reuses the first inactive pickup instance of THAT type (2 pooled instances per type, main.js line 154: POWERUP_TYPES.flatMap(t => [createPowerupPickup(scene,t), createPowerupPickup(scene,t)]))',
  },
  animatePickupSamples: animRows,
  worldShrinkSamples: shrinkRows,
};
writeFileSync(new URL('../../docs/native/golden/bird-fx-powerup-pickup.json', import.meta.url), JSON.stringify(out, null, 2) + '\n');
console.log(`wrote ${animRows.length + shrinkRows.length} rows`);
