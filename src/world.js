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

  scene.add(new THREE.HemisphereLight(0xdff6ff, 0x6a8f3a, 1.4));

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

  return scene;
}

// Flappy-Bird style ground: sandy track with diagonal stripes, grass edges.
function makeGroundTexture() {
  const c = document.createElement('canvas');
  c.width = 512;
  c.height = 128;
  const g = c.getContext('2d');

  g.fillStyle = '#73bf2e';
  g.fillRect(0, 0, 512, 128);
  // grass stripes
  g.fillStyle = '#65ad26';
  for (let y = 0; y < 128; y += 32) g.fillRect(0, y, 512, 16);

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

export function createGround(scene) {
  const width = 26; // texture track (x0..x1) maps to ~ -5.3..5.3
  const length = 400;
  const tex = makeGroundTexture();
  tex.repeat.set(1, length / GROUND_TILE);
  const track = new THREE.Mesh(
    new THREE.PlaneGeometry(width, length),
    new THREE.MeshStandardMaterial({ map: tex, roughness: 1 })
  );
  track.rotation.x = -Math.PI / 2;
  track.position.z = -length / 2 + 30;
  track.receiveShadow = true;
  scene.add(track);

  // Wide grass plane underneath to fill the horizon.
  const grass = new THREE.Mesh(
    new THREE.PlaneGeometry(600, 600),
    new THREE.MeshStandardMaterial({ color: 0x73bf2e, roughness: 1 })
  );
  grass.rotation.x = -Math.PI / 2;
  grass.position.y = -0.02;
  grass.receiveShadow = true;
  scene.add(grass);

  return {
    update(distance) {
      tex.offset.y = (distance / GROUND_TILE) % 1;
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

// Trees, bushes and Flappy-like city blocks along both sides of the track.
export function createScenery(scene) {
  const items = [];
  const trunkMat = new THREE.MeshStandardMaterial({ color: 0x8b5a2b, flatShading: true });
  const leafMats = [0x5cb338, 0x4a9e2c, 0x7ccf45].map(
    (c) => new THREE.MeshStandardMaterial({ color: c, flatShading: true })
  );
  const buildingMats = [0xd7eef0, 0xc4e3e6, 0xe9f5f2].map(
    (c) => new THREE.MeshStandardMaterial({ color: c, flatShading: true })
  );
  const windowMat = new THREE.MeshStandardMaterial({ color: 0x9ad4dc, flatShading: true });

  const trunkGeo = new THREE.CylinderGeometry(0.3, 0.4, 2, 6);
  const leafGeo = new THREE.IcosahedronGeometry(1.8, 0);
  const boxGeo = new THREE.BoxGeometry(1, 1, 1);

  function makeTree() {
    const t = new THREE.Group();
    const trunk = new THREE.Mesh(trunkGeo, trunkMat);
    trunk.position.y = 1;
    const leaves = new THREE.Mesh(leafGeo, leafMats[Math.floor(Math.random() * leafMats.length)]);
    leaves.position.y = 3.2;
    leaves.scale.setScalar(0.8 + Math.random() * 0.6);
    t.add(trunk, leaves);
    return t;
  }

  function makeBuilding() {
    const b = new THREE.Group();
    const w = 4 + Math.random() * 4;
    const h = 6 + Math.random() * 14;
    const d = 4 + Math.random() * 4;
    const body = new THREE.Mesh(boxGeo, buildingMats[Math.floor(Math.random() * buildingMats.length)]);
    body.scale.set(w, h, d);
    body.position.y = h / 2;
    b.add(body);
    for (let y = 2; y < h - 1; y += 2.5) {
      const win = new THREE.Mesh(boxGeo, windowMat);
      win.scale.set(w * 0.8, 0.8, d + 0.1);
      win.position.y = y;
      b.add(win);
    }
    return b;
  }

  const bushGeo = new THREE.IcosahedronGeometry(0.8, 0);
  function makeBush() {
    const b = new THREE.Group();
    const n = 2 + Math.floor(Math.random() * 3);
    for (let i = 0; i < n; i++) {
      const puff = new THREE.Mesh(bushGeo, leafMats[Math.floor(Math.random() * leafMats.length)]);
      puff.position.set((i - n / 2) * 0.8, 0.3 + Math.random() * 0.3, Math.random() * 0.6);
      puff.scale.setScalar(0.7 + Math.random() * 0.5);
      b.add(puff);
    }
    return b;
  }

  for (const side of [-1, 1]) {
    for (let z = 0; z < SCENERY_SPAN; z += 5) {
      const bush = makeBush();
      bush.position.set(side * (6.8 + Math.random() * 0.8), 0, 20 - z - Math.random() * 2);
      items.push(bush);
    }
    for (let z = 0; z < SCENERY_SPAN; z += 7) {
      const tree = makeTree();
      tree.position.set(side * (9 + Math.random() * 4), 0, 20 - z - Math.random() * 3);
      items.push(tree);
    }
    for (let z = 0; z < SCENERY_SPAN; z += 12) {
      const b = makeBuilding();
      b.position.set(side * (22 + Math.random() * 12), 0, 20 - z);
      items.push(b);
    }
  }

  // Bake the scenery into a few chunks along the track (one draw call each)
  // that leapfrog to the far end once they are behind the camera.
  const chunkMat = new THREE.MeshStandardMaterial({ vertexColors: true, flatShading: true });
  const chunks = [];
  for (let k = 0; k < SCENERY_SPAN / SCENERY_CHUNK; k++) {
    const top = 20 - k * SCENERY_CHUNK;
    const root = new THREE.Group();
    for (const it of items) {
      const z = it.position.z;
      if (z <= top && (z > top - SCENERY_CHUNK || (k === SCENERY_SPAN / SCENERY_CHUNK - 1 && z <= top))) root.add(it);
    }
    const mesh = new THREE.Mesh(bakeGroup(root), chunkMat);
    mesh.castShadow = true;
    mesh.receiveShadow = true;
    mesh.userData.front = top - SCENERY_CHUNK; // nearest-to-horizon edge
    scene.add(mesh);
    chunks.push(mesh);
  }

  return {
    update(dz) {
      for (const c of chunks) {
        c.position.z += dz;
        // Fully behind the camera: move it to the far end.
        if (c.position.z + c.userData.front > 25) c.position.z -= SCENERY_SPAN;
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
      hasPlant: false, plantOffset: 0,
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

  function setGap(lane, center) {
    lane.gapLow = center - lane.size / 2;
    lane.gapHigh = center + lane.size / 2;
    setSegment(lane.bottom, 0, lane.gapLow, lane.gapLow - 0.4);
    setSegment(lane.top, lane.gapHigh, PIPE_TOP, lane.gapHigh + 0.4);
    lane.ring.position.y = center;
    lane.ring.scale.set(0.95, lane.size / 2 - 0.15, 1);
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
        if (lane.amp) setGap(lane, lane.center + Math.sin(time * lane.speed + lane.phase) * lane.amp);
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
const coinMat = new THREE.MeshStandardMaterial({
  color: 0xf5c518,
  emissive: 0x6b4a00,
  metalness: 0.6,
  roughness: 0.3,
});

export function createCoin(scene) {
  const mesh = new THREE.Mesh(coinGeo, coinMat);
  mesh.castShadow = true;
  mesh.visible = false;
  scene.add(mesh);
  return { mesh, active: false };
}
