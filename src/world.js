import * as THREE from 'three';
import { mergeGeometries } from 'three/examples/jsm/utils/BufferGeometryUtils.js';

export const LANES = [-3, 0, 3];
export const PIPE_RADIUS = 1.1;
export const PIPE_TOP = 40; // pipes extend well above the visible sky
export const GROUND_TILE = 10; // world units per ground texture repeat

const SKY_TOP = 0x2a9bd0;
const SKY_HORIZON = 0xa6e4ea;

export function createScene() {
  const scene = new THREE.Scene();
  scene.background = new THREE.Color(SKY_HORIZON);
  scene.fog = new THREE.Fog(SKY_HORIZON, 70, 180);

  // Gradient sky dome: deeper blue overhead, light towards the horizon.
  const sky = new THREE.Mesh(
    new THREE.SphereGeometry(300, 24, 12),
    new THREE.ShaderMaterial({
      side: THREE.BackSide,
      depthWrite: false,
      fog: false,
      uniforms: {
        top: { value: new THREE.Color(SKY_TOP) },
        horizon: { value: new THREE.Color(SKY_HORIZON) },
      },
      vertexShader: `
        varying vec3 vDir;
        void main() {
          vDir = normalize(position);
          gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
        }`,
      fragmentShader: `
        uniform vec3 top;
        uniform vec3 horizon;
        varying vec3 vDir;
        void main() {
          float h = clamp(vDir.y * 1.8, 0.0, 1.0);
          gl_FragColor = vec4(mix(horizon, top, pow(h, 0.7)), 1.0);
          #include <colorspace_fragment>
        }`,
    })
  );
  sky.renderOrder = -1;
  scene.add(sky);

  const hemi = new THREE.HemisphereLight(0xdff6ff, 0x6a8f3a, 1.4);
  scene.add(hemi);

  const sun = new THREE.DirectionalLight(0xfff4d6, 2.2);
  sun.position.set(12, 30, 10);
  sun.target.position.set(0, 0, -15);
  sun.castShadow = true;
  sun.shadow.mapSize.set(1024, 1024);
  const cam = sun.shadow.camera;
  cam.left = -18; cam.right = 18; cam.top = 40; cam.bottom = -30;
  cam.near = 1; cam.far = 90;
  sun.shadow.bias = -0.0005;
  scene.add(sun, sun.target);

  // Handles for the biome/time-of-day blending (see biomes.js).
  scene.userData.env = { sky: sky.material.uniforms, hemi, sun };
  return scene;
}

// Ground: sandy track with diagonal stripes and borders.
function makeGroundTexture() {
  const c = document.createElement('canvas');
  c.width = 512;
  c.height = 128;
  const g = c.getContext('2d');

  // The grass beside the track is transparent here: it comes from the grass
  // plane below, whose colour changes with the zone.
  g.clearRect(0, 0, 512, 128);

  // sand track across the lanes (center ~ 40% of the width)
  const x0 = 150, x1 = 362;
  g.fillStyle = '#ded895';
  g.fillRect(x0, 0, x1 - x0, 128);
  g.strokeStyle = '#d2c26a';
  g.lineWidth = 14;
  for (let i = -128; i < 256; i += 32) {
    g.beginPath();
    g.moveTo(x0, i);
    g.lineTo(x1, i + 128);
    g.stroke();
  }
  // track borders
  g.fillStyle = '#9ce659';
  g.fillRect(x0 - 10, 0, 10, 128);
  g.fillRect(x1, 0, 10, 128);
  g.fillStyle = '#543847';
  g.fillRect(x0 - 14, 0, 4, 128);
  g.fillRect(x1 + 10, 0, 4, 128);

  const tex = new THREE.CanvasTexture(c);
  tex.colorSpace = THREE.SRGBColorSpace;
  tex.wrapS = THREE.ClampToEdgeWrapping;
  tex.wrapT = THREE.RepeatWrapping;
  tex.anisotropy = 8;
  return tex;
}

// Grass stripes in greys; the grass material's colour tints them (so the
// default green gives the original #73bf2e / #65ad26 stripes).
function makeGrassTexture() {
  const c = document.createElement('canvas');
  c.width = 4;
  c.height = 128;
  const g = c.getContext('2d');
  g.fillStyle = '#ffffff';
  g.fillRect(0, 0, 4, 128);
  g.fillStyle = '#e0e6d8';
  for (let y = 0; y < 128; y += 32) g.fillRect(0, y, 4, 16);
  const tex = new THREE.CanvasTexture(c);
  tex.colorSpace = THREE.SRGBColorSpace;
  tex.wrapS = THREE.RepeatWrapping;
  tex.wrapT = THREE.RepeatWrapping;
  tex.anisotropy = 8;
  return tex;
}

export function createGround(scene) {
  const width = 26; // texture track (x0..x1) maps to ~ -5.3..5.3
  const length = 400;
  const tex = makeGroundTexture();
  tex.repeat.set(1, length / GROUND_TILE);
  const track = new THREE.Mesh(
    new THREE.PlaneGeometry(width, length),
    new THREE.MeshStandardMaterial({ map: tex, roughness: 1, alphaTest: 0.5 })
  );
  track.rotation.x = -Math.PI / 2;
  track.position.z = -length / 2 + 30;
  track.receiveShadow = true;
  scene.add(track);

  // Wide striped grass plane underneath, also filling the horizon.
  const grassTex = makeGrassTexture();
  grassTex.repeat.set(1, 600 / GROUND_TILE);
  const grass = new THREE.Mesh(
    new THREE.PlaneGeometry(600, 600),
    new THREE.MeshStandardMaterial({ map: grassTex, color: 0x73bf2e, roughness: 1 })
  );
  grass.rotation.x = -Math.PI / 2;
  grass.position.y = -0.02;
  grass.receiveShadow = true;
  scene.add(grass);

  return {
    trackMat: track.material,
    grassMat: grass.material,
    update(distance) {
      tex.offset.y = (distance / GROUND_TILE) % 1;
      grassTex.offset.y = tex.offset.y;
    },
  };
}

// Bake all meshes below `root` into one geometry with vertex colours, so a
// whole group of static parts renders with a single draw call. The look is
// unchanged: same shapes, same colours, same flat shading.
export function bakeGroup(root) {
  root.updateMatrixWorld(true);
  const inv = root.matrixWorld.clone().invert();
  const geos = [];
  const m = new THREE.Matrix4();
  root.traverse((o) => {
    if (!o.isMesh) return;
    const g = o.geometry.index ? o.geometry.toNonIndexed() : o.geometry.clone();
    g.applyMatrix4(m.multiplyMatrices(inv, o.matrixWorld));
    const c = o.material.color;
    const n = g.attributes.position.count;
    const col = new Float32Array(n * 3);
    for (let i = 0; i < n; i++) col.set([c.r, c.g, c.b], i * 3);
    g.setAttribute('color', new THREE.BufferAttribute(col, 3));
    for (const key of Object.keys(g.attributes)) {
      if (key !== 'position' && key !== 'normal' && key !== 'color') g.deleteAttribute(key);
    }
    geos.push(g);
  });
  const merged = mergeGeometries(geos);
  for (const g of geos) g.dispose();
  return merged;
}

const SCENERY_CHUNK = 25;
const SCENERY_SPAN = 225;

// Scenery along both sides of the track, in themes (park, autumn forest,
// canyon, blossom grove). Everything is low-poly and flat-shaded like the
// rest of the world.
const matCache = new Map();
function sceneryMat(color) {
  if (!matCache.has(color)) matCache.set(color, new THREE.MeshStandardMaterial({ color, flatShading: true }));
  return matCache.get(color);
}
const pick = (arr) => arr[Math.floor(Math.random() * arr.length)];
const rand = (a, b) => a + Math.random() * (b - a);

const G = {
  trunk: new THREE.CylinderGeometry(0.3, 0.4, 2, 6),
  leaf: new THREE.IcosahedronGeometry(1.8, 0),
  box: new THREE.BoxGeometry(1, 1, 1),
  bush: new THREE.IcosahedronGeometry(0.8, 0),
  rock: new THREE.DodecahedronGeometry(0.9, 0),
  cactus: new THREE.CylinderGeometry(0.35, 0.4, 1, 7),
  cone: new THREE.ConeGeometry(1, 1, 4),
  pine: new THREE.ConeGeometry(1.5, 3.2, 7),
  hill: new THREE.IcosahedronGeometry(1, 1),
  flower: new THREE.IcosahedronGeometry(0.22, 0),
};

function mesh(geo, color, x = 0, y = 0, z = 0, sx = 1, sy = sx, sz = sx) {
  const m = new THREE.Mesh(geo, sceneryMat(color));
  m.position.set(x, y, z);
  m.scale.set(sx, sy, sz);
  return m;
}

function tree(leafColors, trunk = 0x8b5a2b) {
  const t = new THREE.Group();
  t.add(mesh(G.trunk, trunk, 0, 1));
  t.add(mesh(G.leaf, pick(leafColors), 0, 3.2, 0, rand(0.8, 1.4)));
  return t;
}
function bush(colors, flowers) {
  const b = new THREE.Group();
  const n = 2 + Math.floor(Math.random() * 3);
  for (let i = 0; i < n; i++) {
    const x = (i - n / 2) * 0.8;
    const y = rand(0.3, 0.6);
    const z = Math.random() * 0.6;
    const s = rand(0.7, 1.2);
    b.add(mesh(G.bush, pick(colors), x, y, z, s));
    if (flowers) b.add(mesh(G.flower, pick(flowers), x + rand(-0.3, 0.3), y + 0.6 * s, z + rand(-0.2, 0.3)));
  }
  return b;
}
function building(colors, windowColor, roof) {
  const b = new THREE.Group();
  const w = rand(4, 8);
  const h = roof ? rand(4, 8) : rand(6, 20);
  const d = rand(4, 8);
  b.add(mesh(G.box, pick(colors), 0, h / 2, 0, w, h, d));
  for (let y = 2; y < h - 1; y += 2.5) b.add(mesh(G.box, windowColor, 0, y, 0, w * 0.8, 0.8, d + 0.1));
  if (roof) {
    const r = mesh(G.cone, roof, 0, h + 1.4, 0, w * 0.78, 2.8, d * 0.78);
    r.rotation.y = Math.PI / 4;
    b.add(r);
  }
  return b;
}
function cactus() {
  const c = new THREE.Group();
  const h = rand(2.2, 3.6);
  const green = pick([0x4f9d3a, 0x5cae45, 0x3f8a33]);
  c.add(mesh(G.cactus, green, 0, h / 2, 0, 1, h, 1));
  for (const side of [-1, 1]) {
    if (Math.random() < 0.3) continue;
    const ah = rand(0.8, 1.4);
    const y = rand(h * 0.4, h * 0.7);
    c.add(mesh(G.cactus, green, side * 0.55, y, 0, 0.45, 0.35, 0.45).rotateZ(Math.PI / 2));
    c.add(mesh(G.cactus, green, side * 0.8, y + ah / 2, 0, 0.5, ah, 0.5));
  }
  return c;
}
function mesa() {
  const m = new THREE.Group();
  const w = rand(6, 11);
  const d = rand(6, 10);
  let y = 0;
  const layers = 2 + Math.floor(Math.random() * 3);
  for (let i = 0; i < layers; i++) {
    const h = rand(2.5, 5);
    const k = 1 - i * 0.12;
    m.add(mesh(G.box, pick([0xd9774a, 0xe8915a, 0xc9653f, 0xf0b27a]), 0, y + h / 2, 0, w * k, h, d * k));
    y += h;
  }
  return m;
}
function hill(colors) {
  const s = rand(4, 7);
  return mesh(G.hill, pick(colors), 0, s * 0.25, 0, s * 1.4, s * 0.8, s);
}

function snowman() {
  const g = new THREE.Group();
  g.add(mesh(G.hill, 0xffffff, 0, 0.7, 0, 0.8), mesh(G.hill, 0xffffff, 0, 1.75, 0, 0.55), mesh(G.hill, 0xffffff, 0, 2.5, 0, 0.38));
  g.add(mesh(G.cone, 0xff8a1f, 0, 2.5, 0.45, 0.1, 0.4, 0.1).rotateX(Math.PI / 2));
  for (const x of [-0.13, 0.13]) g.add(mesh(G.flower, 0x2e2530, x, 2.62, 0.33, 0.25));
  g.add(mesh(G.cactus, 0xe8453c, 0, 2.08, 0, 1.25, 0.22, 1.25));
  return g;
}
function snowyPine() {
  const g = new THREE.Group();
  const s = rand(0.9, 1.4);
  g.add(mesh(G.trunk, 0x7a4a24, 0, 0.5, 0, 0.7, 0.5, 0.7));
  g.add(mesh(G.pine, pick([0x2f6b4a, 0x3a7a55]), 0, 2.4 * s, 0, s));
  g.add(mesh(G.pine, 0xffffff, 0, 3.4 * s, 0, s * 0.62, s * 0.5, s * 0.62));
  return g;
}
function snowPeak() {
  const g = new THREE.Group();
  const w = rand(7, 12);
  const h = rand(9, 16);
  g.add(mesh(G.cone, pick([0x9fb4c8, 0xa9bfd6, 0x8fa6bd]), 0, h / 2, 0, w, h, w));
  g.add(mesh(G.cone, 0xffffff, 0, h * 0.8, 0, w * 0.42, h * 0.4, w * 0.42));
  return g;
}
function palm() {
  const g = new THREE.Group();
  const lean = rand(-0.25, 0.25);
  let x = 0;
  for (let i = 0; i < 5; i++) {
    g.add(mesh(G.trunk, i % 2 ? 0xa8733f : 0x9a6835, x, 0.5 + i * 0.95, 0, 0.8 - i * 0.07, 0.5, 0.8 - i * 0.07));
    x += lean;
  }
  const top = 4.9;
  for (let i = 0; i < 6; i++) {
    const a = (i / 6) * Math.PI * 2;
    const leaf = mesh(G.leaf, pick([0x3fae3a, 0x55c244]), x + Math.cos(a) * 1.3, top - 0.35, Math.sin(a) * 1.3, 0.9, 0.12, 0.35);
    leaf.rotation.y = -a;
    leaf.rotation.z = -0.35;
    g.add(leaf);
  }
  for (let i = 0; i < 3; i++) g.add(mesh(G.flower, 0x6b4423, x + rand(-0.3, 0.3), top - 0.5, rand(-0.3, 0.3), 1.1));
  return g;
}
function lighthouse() {
  const g = new THREE.Group();
  for (let i = 0; i < 6; i++) g.add(mesh(G.cactus, i % 2 ? 0xffffff : 0xe8453c, 0, 1 + i * 2, 0, 3.2 - i * 0.2, 2, 3.2 - i * 0.2));
  g.add(mesh(G.box, 0xfff3a0, 0, 12.8, 0, 1.4, 1.4, 1.4), mesh(G.cone, 0xe8453c, 0, 14.3, 0, 1.4, 1.6, 1.4));
  return g;
}
function lollipop() {
  const g = new THREE.Group();
  const h = rand(2.4, 3.6);
  g.add(mesh(G.cactus, 0xffffff, 0, h / 2, 0, 0.3, h, 0.3));
  const disc = mesh(G.cactus, pick([0xff6fa8, 0x7ee0ff, 0xb07eff, 0xffd84a]), 0, h + 1, 0, 3.4, 0.35, 3.4);
  disc.rotation.x = Math.PI / 2;
  const inner = mesh(G.cactus, 0xffffff, 0, h + 1, 0.05, 1.8, 0.4, 1.8);
  inner.rotation.x = Math.PI / 2;
  g.add(disc, inner);
  return g;
}
function candyCane() {
  const g = new THREE.Group();
  for (let i = 0; i < 6; i++) g.add(mesh(G.cactus, i % 2 ? 0xffffff : 0xe8453c, 0, 0.25 + i * 0.5, 0, 0.45, 0.5, 0.45));
  g.add(mesh(G.hill, 0xe8453c, 0.3, 3.1, 0, 0.35));
  return g;
}
function gumdrops() {
  const g = new THREE.Group();
  const n = 2 + Math.floor(Math.random() * 3);
  for (let i = 0; i < n; i++) g.add(mesh(G.cone, pick([0xff7eb6, 0x7ee0ff, 0xfff07e, 0xb07eff, 0x8ff0a0]), (i - n / 2) * 0.9, 0.4, rand(0, 0.6), rand(0.6, 0.9), 0.8, rand(0.6, 0.9)));
  return g;
}
function iceCreamHill() {
  const s = rand(4, 7);
  const g = new THREE.Group();
  g.add(mesh(G.hill, pick([0xffc2dc, 0xc8f5dc, 0xfff1c8, 0xd9c8ff]), 0, s * 0.25, 0, s * 1.4, s * 0.8, s));
  g.add(mesh(G.hill, 0xe8453c, 0, s * 0.95, 0, 0.8));
  return g;
}
function mushroom(big) {
  const g = new THREE.Group();
  const s = big ? rand(1.4, 2.2) : rand(0.35, 0.6);
  const cap = pick([0x7b6cff, 0x3fb7ff, 0xff9f43, 0x2fc6a8]);
  g.add(mesh(G.cactus, 0xfff3de, 0, 1.2 * s, 0, 0.9 * s, 2.4 * s, 0.9 * s));
  g.add(mesh(G.hill, cap, 0, 2.5 * s, 0, 1.5 * s, 0.75 * s, 1.5 * s));
  if (big) for (let i = 0; i < 4; i++) {
    const a = (i / 4) * Math.PI * 2 + 0.4;
    g.add(mesh(G.flower, 0xfff6e8, Math.cos(a) * 1.05 * s, 2.95 * s, Math.sin(a) * 1.05 * s, 1.4 * s));
  }
  return g;
}

const THEMES = {
  winter: {
    near: () => (Math.random() < 0.18 ? snowman() : bush([0xf4f8ff, 0xe3eefa, 0xd6e4f3])),
    mid: () => snowyPine(),
    far: () => snowPeak(),
  },
  beach: {
    near: () => (Math.random() < 0.5 ? bush([0x9fc94a, 0x7fb83a]) : mesh(G.rock, pick([0xd9c49a, 0xc9b080]), 0, 0.3, 0, rand(0.5, 0.9), rand(0.4, 0.7), rand(0.5, 0.9))),
    mid: () => palm(),
    far: () => (Math.random() < 0.2 ? lighthouse() : hill([0x5fb84a, 0x6fc45a, 0x4fa83f])),
  },
  candy: {
    near: () => (Math.random() < 0.3 ? candyCane() : gumdrops()),
    mid: () => lollipop(),
    far: () => iceCreamHill(),
  },
  mushroom: {
    near: () => (Math.random() < 0.5 ? mushroom(false) : bush([0x4fa83f, 0x5fb84a, 0x3f8f36])),
    mid: () => mushroom(true),
    far: () => hill([0x3f8f6a, 0x4a9e76, 0x357f5c]),
  },
  park: {
    near: () => bush([0x5cb338, 0x4a9e2c, 0x7ccf45]),
    mid: () => tree([0x5cb338, 0x4a9e2c, 0x7ccf45]),
    far: () => building([0xd7eef0, 0xc4e3e6, 0xe9f5f2], 0x9ad4dc),
  },
  autumn: {
    near: () => bush([0xe8772e, 0xf2a93b, 0xd9492f, 0x9fb33a]),
    mid: () => (Math.random() < 0.25
      ? mesh(G.pine, pick([0x3f7f3a, 0x4a8a3f]), 0, 2.6, 0).add(mesh(G.trunk, 0x7a4a24, 0, -1.9, 0, 0.8, 0.5, 0.8))
      : tree([0xe8772e, 0xf2a93b, 0xd9492f, 0xf5c542], 0x7a4a24)),
    far: () => hill([0xc9a23a, 0xb5892f, 0x9aa83a]),
  },
  canyon: {
    near: () => mesh(G.rock, pick([0xc9774f, 0xb5653f, 0xd98c5f]), 0, 0.4, 0, rand(0.6, 1.2), rand(0.5, 0.9), rand(0.6, 1.2)),
    mid: () => (Math.random() < 0.7 ? cactus() : mesh(G.rock, pick([0xc9774f, 0xe0a070]), 0, 0.8, 0, rand(1.4, 2.2))),
    far: () => mesa(),
  },
  blossom: {
    near: () => bush([0x6cc04a, 0x5cb338], [0xffffff, 0xffb7d5, 0xffe066]),
    mid: () => tree([0xffb7d5, 0xffcfe3, 0xf78fb3, 0xffffff], 0x7a4f3a),
    far: () => building([0xfff3e0, 0xffe6ea, 0xeaf6ff], 0xbfe3ea, pick([0xe0584f, 0xd9534f, 0x6a8fd0])),
  },
};

// Items for one chunk, in chunk-local z (0 .. -SCENERY_CHUNK).
function buildChunkGroup(theme) {
  const t = THEMES[theme];
  const root = new THREE.Group();
  const place = (item, x, z, turn) => {
    item.position.x = x;
    item.position.z = z;
    // Plants and rocks at any angle; buildings and mesas stay square.
    item.rotation.y = turn ? Math.random() * Math.PI * 2 : 0;
    root.add(item);
  };
  for (const side of [-1, 1]) {
    for (let z = 0; z < SCENERY_CHUNK; z += 5) place(t.near(), side * rand(6.8, 7.6), -z - Math.random() * 2, true);
    for (let z = rand(0, 3); z < SCENERY_CHUNK; z += 7) place(t.mid(), side * rand(9, 13), -z - Math.random() * 3, true);
    for (let z = rand(0, 6); z < SCENERY_CHUNK; z += 12) place(t.far(), side * rand(22, 34), -z, false);
  }
  return root;
}

export function createScenery(scene) {
  // Baked into chunks along the track (one draw call each) that leapfrog to
  // the far end once they are behind the camera. A chunk is rebuilt in the
  // current theme when it wraps, so a new theme streams in from the horizon.
  const chunkMat = new THREE.MeshStandardMaterial({ vertexColors: true, flatShading: true });
  const chunks = [];
  let theme = 'park';
  function rebuild(c) {
    const geo = bakeGroup(buildChunkGroup(theme));
    c.geometry.dispose();
    c.geometry = geo;
    c.userData.theme = theme;
  }
  for (let k = 0; k < SCENERY_SPAN / SCENERY_CHUNK; k++) {
    const c = new THREE.Mesh(new THREE.BufferGeometry(), chunkMat);
    c.position.z = 20 - k * SCENERY_CHUNK;
    c.castShadow = true;
    c.receiveShadow = true;
    rebuild(c);
    scene.add(c);
    chunks.push(c);
  }

  return {
    material: chunkMat,
    setTheme(name, instant = false) {
      theme = name;
      if (instant) for (const c of chunks) if (c.userData.theme !== theme) rebuild(c);
    },
    update(dz) {
      for (const c of chunks) {
        c.position.z += dz;
        // Fully behind the camera: move it to the far end (in the current theme).
        if (c.position.z - SCENERY_CHUNK > 25) {
          c.position.z -= SCENERY_SPAN;
          if (c.userData.theme !== theme) rebuild(c);
        }
      }
    },
  };
}

export function createClouds(scene) {
  const mat = new THREE.MeshStandardMaterial({ color: 0xffffff, roughness: 1, flatShading: true, fog: true });
  const geo = new THREE.IcosahedronGeometry(1, 1);
  const clouds = [];
  const bakedMat = new THREE.MeshStandardMaterial({ vertexColors: true, roughness: 1, flatShading: true, fog: true });
  for (let i = 0; i < 18; i++) {
    const g = new THREE.Group();
    const n = 3 + Math.floor(Math.random() * 3);
    for (let j = 0; j < n; j++) {
      const puff = new THREE.Mesh(geo, mat);
      puff.position.set(j * 1.6 - n * 0.8, Math.random() * 0.6, Math.random() * 1.2);
      puff.scale.setScalar(1.3 + Math.random() * 0.9);
      g.add(puff);
    }
    const c = new THREE.Mesh(bakeGroup(g), bakedMat);
    c.position.set((Math.random() - 0.5) * 90, 18 + Math.random() * 14, -Math.random() * 220);
    c.userData.speed = 0.3 + Math.random() * 0.3;
    clouds.push(c);
    scene.add(c);
  }
  return {
    material: bakedMat,
    update(dz) {
      for (const c of clouds) {
        c.position.z += dz * c.userData.speed;
        if (c.position.z > 30) {
          c.position.z -= 250;
          c.position.x = (Math.random() - 0.5) * 90;
        }
      }
    },
  };
}

// --- Pipes ------------------------------------------------------------------

const PIPE_COLORS = { pipe: 0x73bf2e, dark: 0x4f8a1f, light: 0xb2ea6c };
const pipeGeo = new THREE.CylinderGeometry(PIPE_RADIUS, PIPE_RADIUS, 1, 16);
pipeGeo.translate(0, 0.5, 0); // origin at bottom, scale.y = height
const lipGeo = new THREE.CylinderGeometry(PIPE_RADIUS + 0.25, PIPE_RADIUS + 0.25, 0.8, 16);
const bandGeo = new THREE.CylinderGeometry(PIPE_RADIUS + 0.28, PIPE_RADIUS + 0.28, 0.14, 16);
const stripeGeo = new THREE.BoxGeometry(0.28, 1, 0.28);
stripeGeo.translate(0, 0.5, 0);
const ringGeo = new THREE.TorusGeometry(1, 0.07, 6, 32);

// The upper pipes fade into the sky with height, so the tall towers don't
// fill the top of the screen: above HAZE_START the pipe colour blends into
// the sky colour seen behind it (same gradient maths as the sky dome, same
// uniforms, so it follows every zone's sky). No transparency, no dithering.
export const HAZE_START = 15;
export const HAZE_END = 26;
function addSkyHaze(mat, sky) {
  mat.onBeforeCompile = (shader) => {
    shader.uniforms.skyTop = sky.top;
    shader.uniforms.skyHorizon = sky.horizon;
    shader.vertexShader = shader.vertexShader
      .replace('#include <common>', '#include <common>\nvarying vec3 vHazePos;')
      .replace('#include <begin_vertex>', '#include <begin_vertex>\nvHazePos = (modelMatrix * vec4(transformed, 1.0)).xyz;');
    shader.fragmentShader = shader.fragmentShader
      .replace('#include <common>', '#include <common>\nvarying vec3 vHazePos;\nuniform vec3 skyTop;\nuniform vec3 skyHorizon;')
      .replace('#include <tonemapping_fragment>', `
{
  vec3 dir = normalize(vHazePos - cameraPosition);
  vec3 skyCol = mix(skyHorizon, skyTop, pow(clamp(dir.y * 1.8, 0.0, 1.0), 0.7));
  float haze = smoothstep(${HAZE_START.toFixed(1)}, ${HAZE_END.toFixed(1)}, vHazePos.y);
  gl_FragColor.rgb = mix(gl_FragColor.rgb, skyCol, haze);
}
#include <tonemapping_fragment>`);
  };
}

// Pipe body with its highlight/shadow stripes, and the lip with its dark
// band, each baked into one geometry (2 draw calls per segment instead of 5).
const pipeColor = (hex) => new THREE.MeshBasicMaterial({ color: hex });
function bakeParts(parts) {
  const root = new THREE.Group();
  for (const [geo, hex, x = 0, y = 0, z = 0] of parts) {
    const m = new THREE.Mesh(geo, pipeColor(hex));
    m.position.set(x, y, z);
    root.add(m);
  }
  return bakeGroup(root);
}
const pipeBodyGeo = bakeParts([
  [pipeGeo, PIPE_COLORS.pipe],
  [stripeGeo, PIPE_COLORS.light, -PIPE_RADIUS * 0.57, 0, PIPE_RADIUS * 0.8],
  [stripeGeo, PIPE_COLORS.dark, PIPE_RADIUS * 0.64, 0, PIPE_RADIUS * 0.75],
]);
// Band below the lip (bottom pipe) or above it (top pipe).
const capBelowGeo = bakeParts([[lipGeo, PIPE_COLORS.pipe], [bandGeo, PIPE_COLORS.dark, 0, -0.4]]);
const capAboveGeo = bakeParts([[lipGeo, PIPE_COLORS.pipe], [bandGeo, PIPE_COLORS.dark, 0, 0.4]]);

// Pipe designs (see PIPES in catalog.js): the shared pipe geometries are
// recoloured in place, so every gate changes at once.
const pipeMats = [];
export function setPipeStyle(style) {
  const recolor = (geo, parts) => {
    const fresh = bakeParts(parts);
    geo.attributes.color.array.set(fresh.attributes.color.array);
    geo.attributes.color.needsUpdate = true;
    fresh.dispose();
  };
  recolor(pipeBodyGeo, [
    [pipeGeo, style.pipe],
    [stripeGeo, style.light, -PIPE_RADIUS * 0.57, 0, PIPE_RADIUS * 0.8],
    [stripeGeo, style.dark, PIPE_RADIUS * 0.64, 0, PIPE_RADIUS * 0.75],
  ]);
  recolor(capBelowGeo, [[lipGeo, style.pipe], [bandGeo, style.dark, 0, -0.4]]);
  recolor(capAboveGeo, [[lipGeo, style.pipe], [bandGeo, style.dark, 0, 0.4]]);
  pipeStyle = style;
  for (const m of pipeMats) applyPipeMat(m);
}
let pipeStyle = null;

// A bottom and a top pipe framing a gap, shown next to the bird in the shop.
export function createPipePreview(scene) {
  const mat = new THREE.MeshStandardMaterial({ vertexColors: true, roughness: 0.45, flatShading: true });
  pipeMats.push(mat);
  applyPipeMat(mat);
  const group = new THREE.Group();
  const bottom = makePipeSegment(mat, capBelowGeo);
  const top = makePipeSegment(mat, capAboveGeo);
  group.add(bottom.g, top.g);
  group.visible = false;
  scene.add(group);
  return {
    group,
    // Gap between y0 and y1 (world units).
    setGap(y0, y1) {
      bottom.body.scale.y = y0;
      bottom.lip.position.y = y0 - 0.4;
      top.g.position.y = y1;
      top.body.scale.y = 12;
      top.lip.position.y = 0.4;
    },
  };
}
function applyPipeMat(m) {
  m.metalness = pipeStyle?.metal ? 0.5 : 0;
  m.roughness = pipeStyle?.metal ? 0.3 : 0.45;
}

function makePipeSegment(mat, capGeo) {
  const g = new THREE.Group();
  const body = new THREE.Mesh(pipeBodyGeo, mat);
  const lip = new THREE.Mesh(capGeo, mat);
  for (const m of [body, lip]) {
    m.castShadow = true;
    m.receiveShadow = true;
  }
  g.add(body, lip);
  return { g, body, lip };
}

// --- Piranha plant ----------------------------------------------------------

export const PLANT_HEIGHT = 1.8;
// How far the plant's head reaches into the gap when fully up. Leaves enough
// room above it for a full flap arc.
export const PLANT_REACH = 0.9;
const plantMat = (color) => new THREE.MeshStandardMaterial({ color, roughness: 0.5, flatShading: true });
const plantMats = {
  stem: plantMat(0x3aa833),
  leaf: plantMat(0x2f8f2a),
  head: plantMat(0xd62f2f),
  mouth: plantMat(0x5a0a0a),
  lip: plantMat(0xfff1d6),
  dot: plantMat(0xffffff),
};
const stemGeo = new THREE.CylinderGeometry(0.12, 0.16, 1.2, 6);
const leafGeo = new THREE.SphereGeometry(0.3, 6, 4);
const upperJawGeo = new THREE.SphereGeometry(0.5, 10, 6, 0, Math.PI * 2, 0, Math.PI / 2);
const lowerJawGeo = new THREE.SphereGeometry(0.5, 10, 6, 0, Math.PI * 2, Math.PI / 2, Math.PI / 2);
const mouthGeo = new THREE.SphereGeometry(0.42, 8, 6);
const jawLipGeo = new THREE.TorusGeometry(0.47, 0.07, 5, 14);
jawLipGeo.rotateX(Math.PI / 2);
const dotGeo = new THREE.SphereGeometry(0.08, 5, 4);

function createPlant() {
  const group = new THREE.Group();
  const stem = new THREE.Mesh(stemGeo, plantMats.stem);
  stem.position.y = 0.6;
  group.add(stem);
  for (const side of [-1, 1]) {
    const leaf = new THREE.Mesh(leafGeo, plantMats.leaf);
    leaf.scale.set(1.5, 0.3, 0.8);
    leaf.position.set(side * 0.32, 0.5, 0);
    leaf.rotation.z = side * 0.4;
    group.add(leaf);
  }
  const head = new THREE.Group();
  head.scale.setScalar(1.35);
  head.position.y = PLANT_HEIGHT - 0.5 * 1.35;
  head.rotation.x = 0.35; // mouth faces the player
  const mouth = new THREE.Mesh(mouthGeo, plantMats.mouth);
  const upper = new THREE.Group();
  upper.add(new THREE.Mesh(upperJawGeo, plantMats.head), new THREE.Mesh(jawLipGeo, plantMats.lip));
  for (const [theta, phi] of [[0.3, 0.5], [1.6, 0.8], [2.9, 0.45], [4.2, 0.9], [5.4, 0.55]]) {
    const dot = new THREE.Mesh(dotGeo, plantMats.dot);
    dot.position.setFromSphericalCoords(0.49, phi, theta);
    upper.add(dot);
  }
  const lower = new THREE.Group();
  lower.add(new THREE.Mesh(lowerJawGeo, plantMats.head), new THREE.Mesh(jawLipGeo, plantMats.lip));
  head.add(mouth, upper, lower);
  group.add(head);
  group.traverse((o) => {
    if (o.isMesh) o.castShadow = true;
  });
  group.visible = false;
  return { group, upper, lower };
}

// Gap size factor for breathing gaps: open (1) → narrow (0.7) → open, over
// two beats.
export function pulseScale(beat) {
  return 0.85 + 0.15 * Math.cos(beat * Math.PI);
}

// Rise amount (0..1) over a 4-beat cycle: hidden, pop up, chomp, retreat.
function plantRise(beat) {
  const p = ((beat % 4) + 4) % 4;
  if (p < 2) return 0;
  if (p < 2.4) return THREE.MathUtils.smoothstep(p, 2, 2.4);
  if (p < 3.5) return 1;
  if (p < 3.9) return 1 - THREE.MathUtils.smoothstep(p, 3.5, 3.9);
  return 0;
}

// A gate is one row of pipes across all three lanes. Each gate owns its
// materials so it can fade out on its own once the bird has passed.
export function createGate(scene) {
  const group = new THREE.Group();
  const mats = {
    pipe: new THREE.MeshStandardMaterial({ vertexColors: true, roughness: 0.45, flatShading: true }),
  };
  addSkyHaze(mats.pipe, scene.userData.env.sky);
  pipeMats.push(mats.pipe);
  applyPipeMat(mats.pipe);
  const ringMat = new THREE.MeshBasicMaterial({ color: 0xffffff, transparent: true, opacity: 0, depthWrite: false });
  const lanes = LANES.map((x) => {
    const bottom = makePipeSegment(mats.pipe, capBelowGeo);
    const top = makePipeSegment(mats.pipe, capAboveGeo);
    bottom.g.position.x = x;
    top.g.position.x = x;
    const ring = new THREE.Mesh(ringGeo, ringMat);
    ring.position.x = x;
    const plant = createPlant();
    plant.group.position.x = x;
    group.add(bottom.g, top.g, ring, plant.group);
    return {
      x, bottom, top, ring, plant,
      blocked: false, center: 0, size: 0, amp: 0, speed: 0, phase: 0,
      hasPlant: false, plantOffset: 0, pulse: false,
      gapLow: 0, gapHigh: 0, hitLow: 0, hitHigh: 0,
    };
  });
  scene.add(group);

  function setSegment(seg, from, to, lipAt) {
    const h = Math.max(0.01, to - from);
    seg.g.position.y = from;
    seg.body.scale.y = h;
    seg.lip.visible = lipAt !== null;
    if (lipAt !== null) seg.lip.position.y = lipAt - from;
  }

  function setGap(lane, center, size = lane.size) {
    lane.gapLow = center - size / 2;
    lane.gapHigh = center + size / 2;
    setSegment(lane.bottom, 0, lane.gapLow, lane.gapLow - 0.4);
    setSegment(lane.top, lane.gapHigh, PIPE_TOP, lane.gapHigh + 0.4);
    lane.ring.position.y = center;
    lane.ring.scale.set(0.95, size / 2 - 0.15, 1);
  }

  let opacity = 1;

  return {
    group,
    lanes,
    ringMat,
    passed: false,
    popTime: -1,
    // spec per lane: null (blocked) or { center, size, amp, speed, phase, plant, plantOffset }
    configure(z, spec) {
      group.position.z = z;
      group.visible = true;
      this.passed = false;
      this.popTime = -1;
      this.setOpacity(1);
      ringMat.opacity = 0;
      spec.forEach((gap, i) => {
        const lane = lanes[i];
        lane.plant.group.visible = false;
        if (!gap) {
          lane.blocked = true;
          lane.hasPlant = false;
          lane.gapLow = lane.gapHigh = lane.hitLow = lane.hitHigh = 0;
          setSegment(lane.bottom, 0, PIPE_TOP, null);
          lane.top.g.visible = false;
          lane.ring.visible = false;
          return;
        }
        lane.blocked = false;
        lane.top.g.visible = true;
        lane.ring.visible = true;
        lane.center = gap.center;
        lane.size = gap.size;
        lane.amp = gap.amp || 0;
        lane.speed = gap.speed || 0;
        lane.phase = gap.phase || 0;
        lane.hasPlant = !!gap.plant;
        lane.pulse = !!gap.pulse;
        lane.plantOffset = gap.plantOffset || 0;
        setGap(lane, gap.center);
        lane.hitLow = lane.gapLow;
        lane.hitHigh = lane.gapHigh;
      });
    },
    // Animate moving gaps and piranha plants. `beat` drives the plants so
    // they pop up in time with the music.
    update(time, beat, dt) {
      for (const lane of lanes) {
        if (lane.blocked) continue;
        const center = lane.amp ? lane.center + Math.sin(time * lane.speed + lane.phase) * lane.amp : lane.center;
        if (lane.pulse) {
          // "Breathing" gap: narrows and opens again on every second beat.
          setGap(lane, center, lane.size * pulseScale(beat + lane.plantOffset));
        } else if (lane.amp) {
          setGap(lane, center);
        }
        lane.hitLow = lane.gapLow;
        lane.hitHigh = lane.gapHigh;
        if (!lane.hasPlant) continue;
        if (this.passed) {
          lane.plant.group.visible = false;
          continue;
        }
        const rise = plantRise(beat + lane.plantOffset);
        const plant = lane.plant;
        plant.group.visible = rise > 0;
        if (rise <= 0) continue;
        // Hidden inside the pipe at rise 0; head sticks out of the gap at 1.
        plant.group.position.y = lane.gapLow + PLANT_REACH + 0.15 - PLANT_HEIGHT * (2 - rise);
        lane.hitLow = Math.max(lane.gapLow, plant.group.position.y + PLANT_HEIGHT - 0.15);
        const chomp = rise > 0.9 ? 0.5 + 0.5 * Math.sin(time * 16) : 0.3;
        plant.upper.rotation.x = -0.6 * chomp;
        plant.lower.rotation.x = 0.35 * chomp;
      }
      if (this.popTime >= 0) {
        // Ring "pop" feedback right after passing the gate.
        this.popTime += dt;
        // Short and small: the row is already right next to the camera.
        const t = Math.min(1, this.popTime / 0.22);
        for (const lane of lanes) lane.ring.scale.x = 0.95 * (1 + t * 0.25);
        ringMat.opacity = 0.7 * (1 - t) * (1 - t);
        if (t >= 1) {
          for (const lane of lanes) lane.ring.visible = false;
          this.popTime = -1;
        }
      }
    },
    setOpacity(o) {
      if (Math.abs(o - opacity) < 0.001) return;
      const fading = o < 0.999;
      for (const m of Object.values(mats)) {
        if (m.transparent !== fading) {
          m.transparent = fading;
          m.depthWrite = !fading;
          m.needsUpdate = true;
        }
        m.opacity = o;
      }
      opacity = o;
    },
    get opacity() {
      return opacity;
    },
  };
}

// --- Coins ------------------------------------------------------------------

const coinGeo = new THREE.CylinderGeometry(0.45, 0.45, 0.12, 20);
coinGeo.rotateX(Math.PI / 2);
// Bright gold: little metalness (there is no environment map to reflect,
// so a metallic coin would look brown) and a warm glow of its own.
const coinMat = new THREE.MeshStandardMaterial({
  color: 0xffcf33,
  emissive: 0xb07800,
  emissiveIntensity: 0.55,
  metalness: 0.15,
  roughness: 0.35,
  flatShading: true,
});

export function createCoin(scene) {
  const mesh = new THREE.Mesh(coinGeo, coinMat);
  mesh.castShadow = true;
  mesh.visible = false;
  scene.add(mesh);
  return { mesh, active: false };
}
