// Animated premium skins: a small procedural pattern painted over the bird's
// own materials in the shader (object space, so it sticks to the body and
// wings). Same meshes and draw calls; the flat low-poly shading stays.
export const FX = { none: 0, lava: 1, diamond: 2, water: 3, galaxy: 4, basketball: 5, football: 6, toadstool: 7 };

const uniforms = { uFxTime: { value: 0 }, uFx: { value: 0 } };
// The effect shader is only compiled while a premium skin is worn, so the
// normal skins keep their plain (fast to compile) materials.
const fxMats = new Set();
let active = false;
export function setSkinFx(name) {
  uniforms.uFx.value = FX[name] || 0;
  if (active !== uniforms.uFx.value > 0) {
    active = !active;
    for (const m of fxMats) m.needsUpdate = true;
  }
}
export function tickSkinFx(time) {
  uniforms.uFxTime.value = time % 1000;
}

const FRAG = /* glsl */ `
uniform float uFxTime;
uniform int uFx;
uniform float uFxPart; // 0 body, 1 belly, 2 wing, 3 cover, 4 tail
varying vec3 vFxPos;
float fxHash(vec3 p) { return fract(sin(dot(p, vec3(127.1, 311.7, 74.7))) * 43758.5453); }
// Distance to the nearest cell border of a 3D Voronoi pattern (cracks, seams).
float fxCells(vec3 p) {
  vec3 i = floor(p), f = fract(p);
  float d1 = 8.0, d2 = 8.0;
  for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) for (int z = -1; z <= 1; z++) {
    vec3 o = vec3(x, y, z);
    vec3 r = o + vec3(fxHash(i + o), fxHash(i + o + 17.0), fxHash(i + o + 31.0)) - f;
    float d = dot(r, r);
    if (d < d1) { d2 = d1; d1 = d; } else if (d < d2) d2 = d;
  }
  return sqrt(d2) - sqrt(d1);
}
vec3 fxRainbow(float h) { return 0.55 + 0.45 * cos(6.2832 * (h + vec3(0.0, 0.33, 0.67))); }
mat2 fxRot(float a) { float c = cos(a), s = sin(a); return mat2(c, -s, s, c); }
// Nearest panels of a football (truncated icosahedron) for direction q:
// distances to the closest and second closest panel centre; pent = the
// closest is one of the 12 pentagons (icosahedron vertices).
void fxBall(vec3 q, out float d1, out float d2, out bool pent) {
  const float g = 1.618;
  const float ig = 0.618;
  d1 = 9.0; d2 = 9.0; pent = false;
  for (int i = 0; i < 32; i++) {
    vec3 c;
    int k = i;
    if (k < 12) {
      int a = k / 4; float s1 = (k & 1) == 0 ? 1.0 : -1.0; float s2 = (k & 2) == 0 ? 1.0 : -1.0;
      c = a == 0 ? vec3(0.0, s1, s2 * g) : a == 1 ? vec3(s1, s2 * g, 0.0) : vec3(s1 * g, 0.0, s2);
    } else if (k < 20) {
      k -= 12;
      c = vec3((k & 1) == 0 ? 1.0 : -1.0, (k & 2) == 0 ? 1.0 : -1.0, (k & 4) == 0 ? 1.0 : -1.0);
    } else {
      k -= 20;
      int a = k / 4; float s1 = (k & 1) == 0 ? 1.0 : -1.0; float s2 = (k & 2) == 0 ? 1.0 : -1.0;
      c = a == 0 ? vec3(0.0, s1 * ig, s2 * g) : a == 1 ? vec3(s1 * ig, s2 * g, 0.0) : vec3(s1 * g, 0.0, s2 * ig);
    }
    float d = distance(q, normalize(c));
    if (d < d1) { d2 = d1; d1 = d; pent = i < 12; } else if (d < d2) d2 = d;
  }
}
`;

// Returns [albedo, emissive] for the active effect.
const APPLY = /* glsl */ `
{
  vec3 p = vFxPos;
  float t = uFxTime;
  vec3 col = diffuseColor.rgb;
  vec3 glow = vec3(0.0);
  float light = uFxPart == 1.0 ? 0.35 : 0.0; // belly a little lighter
  if (uFx == 1) { // lava: dark rock, glowing cracks that crawl
    float e = fxCells(p * 3.2 + vec3(0.0, -t * 0.35, t * 0.2));
    float crack = smoothstep(0.1, 0.0, e);
    float pulse = 0.75 + 0.25 * sin(t * 3.0 + p.y * 6.0);
    col = mix(vec3(0.16, 0.09, 0.08), vec3(0.3, 0.14, 0.1), light * 2.0);
    col = mix(col, vec3(1.0, 0.45, 0.08), crack);
    glow = vec3(1.0, 0.32, 0.04) * crack * pulse * 1.3;
  } else if (uFx == 2) { // diamond: icy facets, rainbow sheen, sparkles and a sweeping shine
    vec3 cell = floor(p * 5.0);
    float h = fxHash(cell);
    col = mix(vec3(0.45, 0.82, 1.0), fxRainbow(h + t * 0.15), 0.35) * (0.8 + 0.4 * h);
    col = mix(col, vec3(1.0), light);
    vec3 sc = floor(p * 9.0);
    float spark = step(0.55, fxHash(sc + 7.0)) * pow(max(0.0, sin(t * 3.5 + fxHash(sc) * 40.0)), 30.0);
    float sweep = exp(-pow((p.x * 0.8 + p.y - mod(t * 0.9, 5.0) + 2.0) * 8.0, 2.0));
    glow = vec3(0.2, 0.35, 0.45) * 0.4 + vec3(1.0) * (spark * 1.8 + sweep * 0.45);
  } else if (uFx == 3) { // water: moving waves and rising air bubbles
    float w = sin(p.x * 9.0 + t * 2.2 + sin(p.z * 7.0 + t * 1.3) * 1.5) * sin(p.z * 8.0 - p.y * 5.0 - t * 1.8);
    float crest = smoothstep(0.35, 0.75, w) * (0.6 + 0.4 * sin(p.y * 14.0 + t * 3.0));
    col = mix(vec3(0.05, 0.35, 0.8), vec3(0.2, 0.65, 1.0), 0.5 + 0.5 * sin(p.y * 5.0 + t));
    col = mix(col, vec3(0.85, 0.97, 1.0), crest * 0.7 + light * 0.5);
    vec3 bp = p * 5.0 + vec3(0.0, -t * 0.9, 0.0);
    vec3 bc = floor(bp);
    float bh = fxHash(bc + 11.0);
    vec3 bf = fract(bp) - 0.5 - (vec3(fxHash(bc), fxHash(bc + 3.0), fxHash(bc + 5.0)) - 0.5) * 0.3;
    float br = 0.16 + 0.12 * fxHash(bc + 9.0);
    float bl = length(bf);
    float bubble = step(0.5, bh) * (smoothstep(0.05, 0.0, abs(bl - br)) + 0.25 * step(bl, br));
    col = mix(col, vec3(0.92, 0.99, 1.0), clamp(bubble, 0.0, 1.0));
    glow = vec3(0.3, 0.6, 0.8) * crest * 0.3 + vec3(0.5, 0.8, 1.0) * bubble * 0.35;
  } else if (uFx == 4) { // galaxy: drifting nebula, moving twinkling stars, shooting stars
    vec3 q = p * 3.0;
    q.xz *= fxRot(t * 0.15);
    float neb = 0.5 + 0.5 * sin(q.x * 2.1 + sin(q.y * 2.7 + t * 0.4) * 1.8 + q.z * 1.3);
    col = mix(vec3(0.1, 0.07, 0.3), vec3(0.55, 0.2, 0.7), neb * 0.8);
    col = mix(col, vec3(0.25, 0.55, 0.9), smoothstep(0.7, 1.0, neb) * 0.6);
    vec3 g1 = (p + vec3(t * 0.12, t * 0.04, 0.0)) * 14.0;
    vec3 s1 = floor(g1);
    float star = step(0.7, fxHash(s1)) * smoothstep(0.32, 0.12, length(fract(g1) - 0.5))
      * (0.35 + 0.65 * pow(0.5 + 0.5 * sin(t * 4.0 + fxHash(s1 + 3.0) * 30.0), 4.0));
    vec3 g2 = (p + vec3(t * 0.3, -t * 0.08, t * 0.1)) * 24.0;
    vec3 s2 = floor(g2);
    star = max(star, step(0.85, fxHash(s2 + 21.0)) * smoothstep(0.3, 0.1, length(fract(g2) - 0.5)) * 0.7);
    float ph = fract(t / 3.5);
    float head = mix(-1.0, 1.0, ph / 0.35);
    float across = abs(p.y - p.x * 0.35 - 0.15 + 0.3 * fxHash(vec3(floor(t / 3.5))));
    float streak = step(ph, 0.35) * smoothstep(0.035, 0.0, across) * step(p.x, head) * smoothstep(0.5, 0.0, head - p.x);
    col = mix(col, vec3(1.0), max(star, streak));
    glow = col * 0.35 + vec3(1.0) * (star * 1.2 + streak * 1.5);
  } else if (uFx == 5) { // basketball: orange with dark seams, slowly spinning
    vec3 q = p;
    q.xz *= fxRot(t * 0.8);
    float seam = min(abs(q.x), abs(q.y));
    seam = min(seam, abs(length(vec2(abs(q.x) - 0.75, q.y)) - 0.55));
    col = mix(vec3(1.0, 0.42, 0.06), vec3(1.0, 0.66, 0.36), light);
    col *= 0.93 + 0.07 * fxHash(floor(p * 40.0));
    col = mix(col, vec3(0.2, 0.1, 0.07), smoothstep(0.04, 0.02, seam));
  } else if (uFx == 6) { // football: 12 black pentagons, white hexagons, dark seams, rolling
    vec3 q = normalize(p + vec3(0.0001));
    q.yz *= fxRot(t * 0.9);
    float d1, d2;
    bool pent;
    fxBall(q, d1, d2, pent);
    col = pent ? vec3(0.13) : vec3(0.97);
    col = mix(col, vec3(0.3), smoothstep(0.03, 0.012, d2 - d1));
  } else if (uFx == 7) { // toadstool: red cap with white dots that breathe
    col = mix(vec3(0.88, 0.16, 0.12), vec3(1.0, 0.93, 0.85), light * 2.5);
    float d1, d2;
    bool pent;
    fxBall(normalize(p + vec3(0.0001)), d1, d2, pent);
    float r = (pent ? 0.2 : 0.13) + 0.025 * sin(t * 2.0 + d2 * 20.0);
    col = mix(col, vec3(1.0, 0.97, 0.92), smoothstep(r, r - 0.03, d1) * (1.0 - light));
  }
  if (uFx > 0) diffuseColor.rgb = col;
  fxGlow = glow;
}
`;

// Hooks a bird material up to the effect; `part` tweaks it per body part.
export function addSkinFx(material, part) {
  fxMats.add(material);
  material.onBeforeCompile = (shader) => {
    if (!active) return;
    shader.uniforms.uFxTime = uniforms.uFxTime;
    shader.uniforms.uFx = uniforms.uFx;
    shader.uniforms.uFxPart = { value: part };
    shader.vertexShader = shader.vertexShader
      .replace('#include <common>', '#include <common>\nvarying vec3 vFxPos;')
      .replace('#include <begin_vertex>', '#include <begin_vertex>\nvFxPos = position;');
    shader.fragmentShader = shader.fragmentShader
      .replace('#include <common>', `#include <common>\n${FRAG}`)
      .replace('#include <color_fragment>', `#include <color_fragment>\nvec3 fxGlow = vec3(0.0);\n${APPLY}`)
      .replace('#include <emissivemap_fragment>', '#include <emissivemap_fragment>\ntotalEmissiveRadiance += fxGlow;');
  };
  material.customProgramCacheKey = () => (active ? 'skinfx' : 'plain');
}
