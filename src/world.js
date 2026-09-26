import * as THREE from 'three';

export const LANES = [-3, 0, 3];
export const PIPE_RADIUS = 1.1;
export const PIPE_TOP = 40; // pipes extend well above the visible sky
export const GROUND_TILE = 10; // world units per ground texture repeat

const SKY = 0x4ec0ca;

export function createScene() {
  const scene = new THREE.Scene();
  scene.background = new THREE.Color(SKY);
  scene.fog = new THREE.Fog(SKY, 60, 170);

  scene.add(new THREE.HemisphereLight(0xdff6ff, 0x6a8f3a, 1.4));

  const sun = new THREE.DirectionalLight(0xfff4d6, 2.2);
  sun.position.set(12, 30, 10);
  sun.target.position.set(0, 0, -15);
  sun.castShadow = true;
  sun.shadow.mapSize.set(1024, 1024);
  const cam = sun.shadow.camera;
  cam.left = -18; cam.right = 18; cam.top = 40; cam.bottom = -20;
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

const SCENERY_SPAN = 200;

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

  for (const side of [-1, 1]) {
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

  for (const it of items) {
    it.traverse((o) => {
      if (o.isMesh) {
        o.castShadow = true;
        o.receiveShadow = true;
      }
    });
    scene.add(it);
  }

  return {
    update(dz) {
      for (const it of items) {
        it.position.z += dz;
        if (it.position.z > 25) it.position.z -= SCENERY_SPAN;
      }
    },
  };
}

export function createClouds(scene) {
  const mat = new THREE.MeshStandardMaterial({ color: 0xffffff, roughness: 1, flatShading: true, fog: true });
  const geo = new THREE.IcosahedronGeometry(1, 1);
  const clouds = [];
  for (let i = 0; i < 18; i++) {
    const c = new THREE.Group();
    const n = 3 + Math.floor(Math.random() * 3);
    for (let j = 0; j < n; j++) {
      const puff = new THREE.Mesh(geo, mat);
      puff.position.set(j * 1.6 - n * 0.8, Math.random() * 0.6, Math.random() * 1.2);
      puff.scale.setScalar(1.3 + Math.random() * 0.9);
      c.add(puff);
    }
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

const pipeMat = new THREE.MeshStandardMaterial({ color: 0x73bf2e, roughness: 0.45, flatShading: true });
const pipeDark = new THREE.MeshStandardMaterial({ color: 0x558c22, roughness: 0.45, flatShading: true });
const pipeGeo = new THREE.CylinderGeometry(PIPE_RADIUS, PIPE_RADIUS, 1, 16);
pipeGeo.translate(0, 0.5, 0); // origin at bottom, scale.y = height
const lipGeo = new THREE.CylinderGeometry(PIPE_RADIUS + 0.25, PIPE_RADIUS + 0.25, 0.8, 16);
const stripeGeo = new THREE.BoxGeometry(0.3, 1, 0.3);
stripeGeo.translate(0, 0.5, 0);

function makePipeSegment() {
  const g = new THREE.Group();
  const body = new THREE.Mesh(pipeGeo, pipeMat);
  const stripe = new THREE.Mesh(stripeGeo, pipeDark);
  stripe.position.set(-PIPE_RADIUS * 0.55, 0, -PIPE_RADIUS * 0.8);
  const lip = new THREE.Mesh(lipGeo, pipeMat);
  for (const m of [body, stripe, lip]) {
    m.castShadow = true;
    m.receiveShadow = true;
  }
  g.add(body, stripe, lip);
  return { g, body, stripe, lip };
}

// A gate is one row of pipes across all three lanes.
export function createGate(scene) {
  const group = new THREE.Group();
  const lanes = LANES.map((x) => {
    const bottom = makePipeSegment();
    const top = makePipeSegment();
    bottom.g.position.x = x;
    top.g.position.x = x;
    group.add(bottom.g, top.g);
    return { x, bottom, top, gapLow: 0, gapHigh: 0, blocked: false };
  });
  scene.add(group);

  function setSegment(seg, from, to, lipAt) {
    const h = Math.max(0.01, to - from);
    seg.g.position.y = from;
    seg.body.scale.y = h;
    seg.stripe.scale.y = h;
    seg.lip.position.y = lipAt - from;
    seg.lip.visible = lipAt !== null;
  }

  return {
    group,
    lanes,
    passed: false,
    // gaps: array per lane of { center, size } or null for a blocked lane
    configure(z, gaps) {
      group.position.z = z;
      group.visible = true;
      this.passed = false;
      gaps.forEach((gap, i) => {
        const lane = lanes[i];
        if (!gap) {
          lane.blocked = true;
          lane.gapLow = lane.gapHigh = 0;
          setSegment(lane.bottom, 0, PIPE_TOP, null);
          lane.top.g.visible = false;
          return;
        }
        lane.blocked = false;
        lane.top.g.visible = true;
        lane.gapLow = gap.center - gap.size / 2;
        lane.gapHigh = gap.center + gap.size / 2;
        setSegment(lane.bottom, 0, lane.gapLow, lane.gapLow - 0.4);
        setSegment(lane.top, lane.gapHigh, PIPE_TOP, lane.gapHigh + 0.4);
      });
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
