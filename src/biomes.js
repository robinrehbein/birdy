import * as THREE from 'three';

// Times of day the run cycles through. All stay bright and colourful; only
// sky, light and a tint over scenery/clouds/grass change. Pipes and the bird
// keep their colours so gameplay reads the same everywhere.
export const BIOMES = [
  {
    name: { de: 'Stadtpark', en: 'City Park' },
    scenery: 'park',
    top: 0x2a9bd0, horizon: 0xa6e4ea,
    hemiSky: 0xdff6ff, hemiGround: 0x6a8f3a, hemiI: 1.4,
    sun: 0xfff4d6, sunI: 2.2,
    tint: 0xffffff, clouds: 0xffffff, grass: 0x73bf2e, track: 0xffffff,
  },
  {
    name: { de: 'Herbstwald', en: 'Autumn Forest' },
    scenery: 'autumn',
    top: 0x4d6fc4, horizon: 0xffc08a,
    hemiSky: 0xffe2c8, hemiGround: 0x7a6a3a, hemiI: 1.35,
    sun: 0xffb877, sunI: 2.4,
    tint: 0xfff0e2, clouds: 0xffd2dc, grass: 0x9aae36, track: 0xfff0dc,
  },
  {
    name: { de: 'Canyon', en: 'Canyon' },
    scenery: 'canyon',
    top: 0x5b53b8, horizon: 0xf2a6cc,
    hemiSky: 0xebd8ff, hemiGround: 0x5d6a80, hemiI: 1.35,
    sun: 0xffd6f2, sunI: 1.9,
    tint: 0xf6ecff, clouds: 0xf6dcff, grass: 0xd9a86a, track: 0xfff0e0,
  },
  {
    name: { de: 'Blütenhain', en: 'Blossom Grove' },
    scenery: 'blossom',
    top: 0x36b3c9, horizon: 0xd6f6d2,
    hemiSky: 0xf0fff2, hemiGround: 0x6a9a4a, hemiI: 1.45,
    sun: 0xfff9e3, sunI: 2.1,
    tint: 0xffffff, clouds: 0xffffff, grass: 0x7fd35a, track: 0xffffff,
  },
];

// Blends the scene from its current colours towards a biome over `duration`.
// Each zone is a place (scenery theme) at a time of day (sky and light).
export function createBiomeBlender({ scene, ground, scenery, clouds }) {
  const env = scene.userData.env;
  const targets = {
    top: env.sky.top.value,
    horizon: env.sky.horizon.value,
    background: scene.background,
    fog: scene.fog.color,
    hemiSky: env.hemi.color,
    hemiGround: env.hemi.groundColor,
    sun: env.sun.color,
    tint: scenery.material.color,
    clouds: clouds.material.color,
    grass: ground.grassMat.color,
    track: ground.trackMat.color,
  };
  const from = {};
  const to = {};
  for (const k of Object.keys(targets)) {
    from[k] = new THREE.Color();
    to[k] = new THREE.Color();
  }
  let fromI = { hemi: env.hemi.intensity, sun: env.sun.intensity };
  let toI = { ...fromI };
  let t = 1;
  let duration = 1;
  let index = 0;

  function set(i, blend = 3) {
    index = i;
    const b = BIOMES[i % BIOMES.length];
    for (const k of Object.keys(targets)) {
      from[k].copy(targets[k]);
      const key = k === 'background' || k === 'fog' ? 'horizon' : k;
      to[k].setHex(b[key]);
    }
    fromI = { hemi: env.hemi.intensity, sun: env.sun.intensity };
    toI = { hemi: b.hemiI, sun: b.sunI };
    duration = Math.max(0.001, blend);
    t = 0;
    scenery.setTheme(b.scenery, blend === 0);
    if (blend === 0) update(0);
    return b;
  }

  function update(dt) {
    if (t >= 1) return;
    t = Math.min(1, t + dt / duration);
    const e = t * t * (3 - 2 * t);
    for (const k of Object.keys(targets)) targets[k].copy(from[k]).lerp(to[k], e);
    env.hemi.intensity = THREE.MathUtils.lerp(fromI.hemi, toI.hemi, e);
    env.sun.intensity = THREE.MathUtils.lerp(fromI.sun, toI.sun, e);
  }

  return {
    set,
    update,
    get index() {
      return index;
    },
  };
}
