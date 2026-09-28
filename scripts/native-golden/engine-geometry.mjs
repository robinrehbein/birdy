// Golden fixture for the Kotlin engine's procedural geometry (engine/mesh): every three.js
// generator the game uses (same parameters as src/*.js) plus edge cases, geometry ops,
// mergeGeometries and world.js bakeGroup. Per case: counts, the full index array, sampled
// attribute values and whole-array checksums.
//   THREE_DIR=/path/to/node_modules/three node scripts/native-golden/engine-geometry.mjs
import fs from 'node:fs';
import { THREE, mergeGeometries, f, arr, outPath } from './engine-lib-three.mjs';

const PI = Math.PI;

function starShape(points, outer, inner, close) {
  const shape = new THREE.Shape();
  for (let i = 0; i < points * 2; i++) {
    const r = i % 2 ? inner : outer;
    const a = (i / (points * 2)) * PI * 2 + PI / 2;
    if (i === 0) shape.moveTo(Math.cos(a) * r, Math.sin(a) * r);
    else shape.lineTo(Math.cos(a) * r, Math.sin(a) * r);
  }
  if (close) shape.closePath();
  return shape;
}
function heartShape(s) {
  const h = new THREE.Shape();
  h.moveTo(0, -0.9 * s);
  h.bezierCurveTo(-1.3 * s, 0, -0.6 * s, 1 * s, 0, 0.45 * s);
  h.bezierCurveTo(0.6 * s, 1 * s, 1.3 * s, 0, 0, -0.9 * s);
  return h;
}
function wingGeometry() { // bird.js createWingGeometry
  const shape = new THREE.Shape();
  shape.moveTo(0, 0.26);
  shape.bezierCurveTo(0.3, 0.38, 0.7, 0.28, 0.9, 0.04);
  shape.quadraticCurveTo(0.9, -0.16, 0.72, -0.13);
  shape.quadraticCurveTo(0.66, -0.33, 0.48, -0.24);
  shape.quadraticCurveTo(0.39, -0.4, 0.22, -0.28);
  shape.quadraticCurveTo(0.08, -0.32, 0, -0.16);
  shape.lineTo(0, 0.26);
  const geo = new THREE.ExtrudeGeometry(shape, { depth: 0.04, bevelEnabled: true, bevelThickness: 0.035, bevelSize: 0.03, bevelSegments: 2, curveSegments: 5 });
  geo.translate(0, 0, -0.02);
  geo.rotateX(-PI / 2);
  return geo;
}
function holeShape() {
  const s = new THREE.Shape();
  s.moveTo(-1, -1); s.lineTo(1, -1); s.lineTo(1, 1); s.lineTo(-1, 1); s.lineTo(-1, -1);
  const hole = new THREE.Path();
  hole.absarc(0.1, 0, 0.4, 0.1, 0.1 + PI * 2, true); // offset start: avoids exact |dx| == |dy| side-wall UV ties
  s.holes.push(hole);
  return s;
}
function colored(g, hex) {
  const c = new THREE.Color(hex);
  const a = new Float32Array(g.attributes.position.count * 3);
  for (let i = 0; i < a.length; i += 3) a.set([c.r, c.g, c.b], i);
  g.setAttribute('color', new THREE.BufferAttribute(a, 3));
  return g;
}

// world.js bakeGroup, verbatim.
function bakeGroup(root, spikes = false) {
  root.updateMatrixWorld(true);
  const inv = root.matrixWorld.clone().invert();
  const geos = [];
  const m = new THREE.Matrix4();
  root.traverse((o) => {
    if (!o.isMesh) return;
    const g = o.geometry.index ? o.geometry.toNonIndexed() : o.geometry.clone();
    if (spikes) g.userData.local = g.attributes.position.clone();
    g.applyMatrix4(m.multiplyMatrices(inv, o.matrixWorld));
    const c = o.material.color;
    const n = g.attributes.position.count;
    const col = new Float32Array(n * 3);
    for (let i = 0; i < n; i++) col.set([c.r, c.g, c.b], i * 3);
    g.setAttribute('color', new THREE.BufferAttribute(col, 3));
    if (spikes) {
      const dir = o.userData.spikeDir;
      const sp = new Float32Array(n * 3);
      if (dir) {
        const local = g.userData.local;
        for (let i = 0; i < n; i++) if (local.getY(i) > 0.01) sp.set([dir.x, dir.y, dir.z], i * 3);
      }
      g.setAttribute('spike', new THREE.BufferAttribute(sp, 3));
    }
    for (const key of Object.keys(g.attributes)) {
      if (key !== 'position' && key !== 'normal' && key !== 'color' && key !== 'spike') g.deleteAttribute(key);
    }
    geos.push(g);
  });
  return mergeGeometries(geos);
}
const basic = (hex) => new THREE.MeshBasicMaterial({ color: hex });

function bakeTwo() {
  const root = new THREE.Group();
  root.position.set(1, 2, 3);
  root.rotation.set(0.1, 0.2, 0.3);
  root.scale.setScalar(2);
  const a = new THREE.Mesh(new THREE.BoxGeometry(1, 2, 3), basic(0xff8800));
  a.position.set(0.5, 0, 0);
  a.rotation.set(0, 0.7, 0);
  a.scale.set(1, 2, 1);
  const sub = new THREE.Group();
  sub.position.set(0, 1, 0);
  sub.rotation.z = 0.4;
  const b = new THREE.Mesh(new THREE.ConeGeometry(0.05, 0.32, 4), basic(0xfff3d6));
  const n = new THREE.Vector3(0.3, 0.8, 0.52).normalize();
  b.position.set(0.2, 0.1, -0.3);
  b.quaternion.setFromUnitVectors(new THREE.Vector3(0, 1, 0), n);
  b.userData.spikeDir = new THREE.Vector3(0, 1, 0).applyQuaternion(b.quaternion);
  sub.add(b);
  root.add(a, sub);
  return root;
}
function coinGroup() { // world.js coinGeo
  const root = new THREE.Group();
  const face = new THREE.Mesh(new THREE.CylinderGeometry(0.4, 0.4, 0.1, 16), basic(0xffcf33));
  face.rotation.x = PI / 2;
  const rim = new THREE.Mesh(new THREE.TorusGeometry(0.41, 0.055, 4, 16), basic(0xf2a100));
  const star = new THREE.Shape();
  for (let i = 0; i < 10; i++) {
    const r = i % 2 ? 0.09 : 0.21;
    const a = (i / 10) * PI * 2 + PI / 2;
    if (i === 0) star.moveTo(Math.cos(a) * r, Math.sin(a) * r);
    else star.lineTo(Math.cos(a) * r, Math.sin(a) * r);
  }
  const starGeo = new THREE.ExtrudeGeometry(star, { depth: 0.03, bevelEnabled: false });
  const front = new THREE.Mesh(starGeo, basic(0xfff0a0));
  front.position.z = 0.04;
  const back = new THREE.Mesh(starGeo, basic(0xfff0a0));
  back.position.z = -0.04;
  back.rotation.y = PI;
  root.add(face, rim, front, back);
  return root;
}

const G = THREE;
const cases = {
  'box.default': () => new G.BoxGeometry(),
  'box.stripe': () => new G.BoxGeometry(0.28, 1, 0.28),
  'box.segments': () => new G.BoxGeometry(2, 3, 4, 2, 3, 1),
  'plane.track': () => new G.PlaneGeometry(26, 400),
  'plane.segments': () => new G.PlaneGeometry(2, 3, 2, 3),
  'sphere.body': () => new G.SphereGeometry(0.6, 14, 10),
  'sphere.cactus': () => new G.SphereGeometry(1, 10, 7),
  'sphere.mouth': () => new G.SphereGeometry(0.2, 10, 6, 0, PI * 2, 0, PI / 2),
  'sphere.tiny': () => new G.SphereGeometry(0.04, 4, 3),
  'sphere.sky': () => new G.SphereGeometry(300, 24, 12),
  'sphere.band': () => new G.SphereGeometry(1, 8, 6, 0.3, PI, PI / 4, PI / 2),
  'cylinder.trunk': () => new G.CylinderGeometry(0.3, 0.4, 2, 6),
  'cylinder.pipe': () => new G.CylinderGeometry(1.1, 1.1, 1, 16),
  'cylinder.brim': () => new G.CylinderGeometry(0.2, 0.2, 0.03, 10, 1, false, -PI / 2, PI),
  'cylinder.crown': () => new G.CylinderGeometry(0.24, 0.22, 0.14, 10, 1, true),
  'cylinder.segments': () => new G.CylinderGeometry(1, 0.5, 2, 5, 3),
  'cone.spike': () => new G.ConeGeometry(0.05, 0.32, 4),
  'cone.pine': () => new G.ConeGeometry(1.5, 3.2, 7),
  'cone.default': () => new G.ConeGeometry(),
  'torus.coinRim': () => new G.TorusGeometry(0.41, 0.055, 4, 16),
  'torus.bodyRingHalf': () => new G.TorusGeometry(1, 0.05, 4, 18, PI),
  'torus.halo': () => new G.TorusGeometry(0.26, 0.045, 6, 20),
  'torus.default': () => new G.TorusGeometry(),
  'icosahedron.leaf': () => new G.IcosahedronGeometry(1.8, 0),
  'icosahedron.hill': () => new G.IcosahedronGeometry(1, 1),
  'icosahedron.detail2': () => new G.IcosahedronGeometry(0.5, 2),
  'dodecahedron.rock': () => new G.DodecahedronGeometry(0.9, 0),
  'dodecahedron.detail1': () => new G.DodecahedronGeometry(1, 1),
  'circle.shadow': () => new G.CircleGeometry(0.7, 20),
  'circle.arc': () => new G.CircleGeometry(1, 5, 0.5, PI),
  'ring.marker': () => new G.RingGeometry(0.2, 0.36, 20),
  'ring.aura': () => new G.RingGeometry(0.82, 1, 40),
  'ring.segments': () => new G.RingGeometry(0.5, 1, 8, 3, 0.2, PI),
  'shape.bonkStar': () => new G.ShapeGeometry(starShape(8, 1.25, 0.62, false)),
  'shape.heart': () => new G.ShapeGeometry(heartShape(0.3)),
  'shape.hole': () => new G.ShapeGeometry(holeShape(), 6),
  'extrude.wing': () => wingGeometry(),
  'extrude.powerupStar': () => new G.ExtrudeGeometry(starShape(5, 0.5, 0.22, true), { depth: 0.14, bevelEnabled: true, bevelThickness: 0.06, bevelSize: 0.05, bevelSegments: 1 }).center(),
  'extrude.coinStar': () => new G.ExtrudeGeometry(starShape(5, 0.21, 0.09, false), { depth: 0.03, bevelEnabled: false }),
  'extrude.heart': () => new G.ExtrudeGeometry(heartShape(0.3), { depth: 0.03, bevelEnabled: false }).rotateX(-PI / 2),
  'extrude.hole': () => new G.ExtrudeGeometry(holeShape(), { depth: 0.5, bevelSegments: 2, steps: 2, curveSegments: 6 }),
  'extrude.defaults': () => new G.ExtrudeGeometry(starShape(4, 1, 0.5, true)),
  'ops.pipeTranslated': () => new G.CylinderGeometry(1.1, 1.1, 1, 16).translate(0, 0.5, 0),
  'ops.discRotated': () => new G.CylinderGeometry(0.19, 0.19, 0.03, 10).rotateX(PI / 2),
  'ops.scaledRotZ': () => new G.SphereGeometry(1, 6, 4).scale(1, 2, 3).rotateZ(0.5).rotateY(-0.25),
  'ops.nonIndexedSphere': () => new G.SphereGeometry(0.6, 14, 10).toNonIndexed(),
  'ops.smoothNormals': () => { const g = new G.CylinderGeometry(0.3, 0.4, 2, 6); g.computeVertexNormals(); return g; },
  'ops.flatNormals': () => { const g = new G.SphereGeometry(0.5, 5, 4).toNonIndexed(); g.computeVertexNormals(); return g; },
  'ops.applyMatrix': () => new G.BoxGeometry(1, 2, 3).applyMatrix4(new G.Matrix4().compose(new G.Vector3(1, -2, 0.5), new G.Quaternion().setFromEuler(new G.Euler(0.3, -0.6, 1.1)), new G.Vector3(1.5, 0.5, -2))),
  'merge.rim': () => mergeGeometries([colored(new G.RingGeometry(1.14, 1.32, 32), 0xffffff), colored(new G.RingGeometry(1.32, 1.4, 32), 0x543847)]),
  'bake.two': () => bakeGroup(bakeTwo(), true),
  'bake.coin': () => bakeGroup(coinGroup()),
};

const SAMPLES = 48;
function describe(g) {
  const out = { vertexCount: g.attributes.position.count, index: g.index ? arr(g.index.array, (v) => v) : null, attributes: {} };
  const n = out.vertexCount;
  const step = Math.max(1, Math.floor(n / SAMPLES));
  const sampleIdx = [];
  for (let i = 0; i < n; i += step) sampleIdx.push(i);
  if (sampleIdx[sampleIdx.length - 1] !== n - 1) sampleIdx.push(n - 1);
  out.sampleIndices = sampleIdx;
  for (const [name, a] of Object.entries(g.attributes)) {
    const s = a.itemSize;
    const vals = [];
    for (const i of sampleIdx) for (let k = 0; k < s; k++) vals.push(f(a.array[i * s + k]));
    let sum = 0, abs = 0;
    for (const v of a.array) { sum += v; abs += Math.abs(v); }
    out.attributes[name] = { itemSize: s, samples: vals, sum: Number(sum.toPrecision(10)), absSum: Number(abs.toPrecision(10)) };
  }
  return out;
}

const result = {
  description: 'three.js r' + THREE.REVISION + ' geometry output for the Kotlin engine.mesh port (see scripts/native-golden/engine-geometry.mjs).',
  cases: Object.fromEntries(Object.entries(cases).map(([k, fn]) => [k, describe(fn())])),
};
fs.writeFileSync(outPath('engine-geometry.json'), JSON.stringify(result) + '\n');
console.log('wrote engine-geometry.json with', Object.keys(cases).length, 'cases');
