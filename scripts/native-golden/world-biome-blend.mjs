// Golden fixture for biomes.js createBiomeBlender: the smoothstep easing
// applied to the 0..1 blend progress `t`, and the stepped road-palette
// lerp (paintRoad rounds the eased progress to 1/8 steps before mixing
// each of the 3 road colours, so the canvas is only repainted 9 times
// per transition instead of every frame).
// Run: node scripts/native-golden/world-biome-blend.mjs
import fs from 'node:fs';

function ease(t) {
  return t * t * (3 - 2 * t); // update()'s `e`
}

// three.js Color.lerp is done in linear-sRGB space after implicit sRGB->linear
// decode of the hex, then re-encoded to sRGB. Reproduce that here.
function srgbToLinear(c) {
  return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
}
function linearToSrgb(c) {
  return c <= 0.0031308 ? c * 12.92 : 1.055 * Math.pow(c, 1 / 2.4) - 0.055;
}
function hexToRgb(hex) {
  return [((hex >> 16) & 255) / 255, ((hex >> 8) & 255) / 255, (hex & 255) / 255];
}
function rgbToHex([r, g, b]) {
  const q = (v) => Math.max(0, Math.min(255, Math.round(v * 255)));
  return (q(r) << 16) | (q(g) << 8) | q(b);
}
// NOTE: three.js Color defaults to SRGBColorSpace working space for .lerp()
// as of the version pinned in package.json (colorManagement on): .setHex()
// stores linear values internally, decoding sRGB->linear on the way in, and
// .getHex() re-encodes. We reproduce that round trip so the golden numbers
// match runtime output; see "Porting notes" in world.md for what a from-
// scratch GL engine should do (it may skip the sRGB round trip entirely and
// lerp raw sRGB bytes if it never uses color-managed textures).
function mix(aHex, bHex, e) {
  const a = hexToRgb(aHex).map(srgbToLinear);
  const b = hexToRgb(bHex).map(srgbToLinear);
  const m = a.map((av, i) => av + (b[i] - av) * e);
  return rgbToHex(m.map(linearToSrgb));
}

const SAND_ROAD = [0xded895, 0xd2c26a, 0x9ce659];
const WINTER_ROAD = [0xeef4fb, 0xd6e4f2, 0xbfe3ff]; // from catalog.js WORLDS[1] (winter)

const tSamples = [];
for (let t = 0; t <= 1.0001; t += 0.05) {
  const tt = Math.round(t * 100) / 100;
  tSamples.push({ t: tt, eased: Math.round(ease(tt) * 1e6) / 1e6 });
}

const roadSteps = [];
for (let t = 0; t <= 1.0001; t += 0.125) {
  const tt = Math.round(t * 1000) / 1000;
  const e = ease(tt);
  const step = Math.round(e * 8) / 8;
  roadSteps.push({
    t: tt,
    eased: Math.round(e * 1e6) / 1e6,
    step,
    road: SAND_ROAD.map((c, i) => mix(c, WINTER_ROAD[i], step).toString(16).padStart(6, '0')),
  });
}

fs.writeFileSync(
  new URL('../../docs/native/golden/world-biome-blend.json', import.meta.url),
  JSON.stringify({
    description: "biomes.js createBiomeBlender: smoothstep easing e=t*t*(3-2t) over the blend duration, and paintRoad's 1/8-stepped road-palette lerp (example: SAND_ROAD -> winter road, colors mixed in linear-sRGB then re-encoded, matching THREE.Color.lerp with color management on).",
    easing: tSamples,
    roadFrom: SAND_ROAD.map((c) => c.toString(16).padStart(6, '0')),
    roadTo: WINTER_ROAD.map((c) => c.toString(16).padStart(6, '0')),
    roadSteps,
  }, null, 2) + '\n'
);
console.log('wrote world-biome-blend.json');
