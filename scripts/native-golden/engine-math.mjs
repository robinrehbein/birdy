// Golden fixture for the Kotlin engine math (engine/math, engine/scene camera + lookAt):
// three.js r186 outputs for matrices, quaternions, Euler XYZ, spherical coords, camera
// projection and Color (HSL, colour-managed lerp/multiply).
//   THREE_DIR=/path/to/node_modules/three node scripts/native-golden/engine-math.mjs
import fs from 'node:fs';
import { THREE, d, outPath } from './engine-lib-three.mjs';

const { Vector3, Quaternion, Euler, Matrix4, Matrix3, Color, Object3D, PerspectiveCamera, Group } = THREE;
const v3 = (v) => [d(v.x), d(v.y), d(v.z)];
const q4 = (q) => [d(q.x), d(q.y), d(q.z), d(q.w)];
const m16 = (m) => m.elements.map(d);
const m9 = (m) => m.elements.map(d);

const out = { description: 'three.js r' + THREE.REVISION + ' math reference values for engine/math (engine-math.mjs).' };

// Euler XYZ -> quaternion -> Euler round trips (includes gimbal-lock and >pi/2 inputs).
const eulers = [[0, 0, 0], [0.1, 0.2, 0.3], [-1.2, 0.7, 2.5], [0.45, 0.35, 0], [Math.PI / 2, Math.PI / 2, 0.3], [2.8, -1.4, -3.0], [0, -Math.PI / 2, 1]];
out.euler = eulers.map((e) => {
  const q = new Quaternion().setFromEuler(new Euler(...e));
  const back = new Euler().setFromQuaternion(q);
  const m = new Matrix4().makeRotationFromEuler(new Euler(...e));
  return { euler: e, quat: q4(q), eulerBack: [d(back.x), d(back.y), d(back.z)], matrix: m16(m), quatFromMatrix: q4(new Quaternion().setFromRotationMatrix(m)) };
});

// compose / decompose / invert / determinant / normal matrix
const composes = [
  [[1, 2, 3], [0.1, 0.2, 0.3], [1, 1, 1]],
  [[-4, 0.5, 9], [1.2, -0.4, 2.2], [2, 0.5, 3]],
  [[0, 16, -30], [0, 0.7, 0], [-1, 2, 1]],
];
out.compose = composes.map(([p, e, s]) => {
  const q = new Quaternion().setFromEuler(new Euler(...e));
  const m = new Matrix4().compose(new Vector3(...p), q, new Vector3(...s));
  const P = new Vector3(), Q = new Quaternion(), S = new Vector3();
  m.decompose(P, Q, S);
  return {
    position: p, quat: q4(q), scale: s, matrix: m16(m), inverse: m16(m.clone().invert()), determinant: d(m.determinant()),
    normalMatrix: m9(new Matrix3().getNormalMatrix(m)),
    decomposed: { position: v3(P), quat: q4(Q), scale: v3(S) },
    extractRotation: m16(new Matrix4().extractRotation(m)),
  };
});
{
  const a = new Matrix4().compose(new Vector3(1, 2, 3), new Quaternion().setFromEuler(new Euler(0.3, 0.2, 0.1)), new Vector3(1, 2, 0.5));
  const b = new Matrix4().makeRotationAxis(new Vector3(1, 1, 0).normalize(), 0.8).setPosition(-1, 0, 4);
  out.multiply = { a: m16(a), b: m16(b), ab: m16(new Matrix4().multiplyMatrices(a, b)), axisAngle: m16(b) };
}
out.lookAt = [
  [[0, 4, 9], [0, 2, -10], [0, 1, 0]],
  [[1, 1, 1], [1, 5, 1], [0, 1, 0]],
  [[0, 0, 0], [0, 0, 0], [0, 1, 0]],
  [[3, -2, 7], [-1, 0.5, 2], [0, 0, 1]],
].map(([eye, target, up]) => ({ eye, target, up, matrix: m16(new Matrix4().lookAt(new Vector3(...eye), new Vector3(...target), new Vector3(...up))) }));
out.perspective = [[50, 0.45, 0.1, 400], [70, 1.7777, 0.5, 120]].map(([fov, aspect, near, far]) => {
  const c = new PerspectiveCamera(fov, aspect, near, far);
  return { fov, aspect, near, far, projection: m16(c.projectionMatrix), inverse: m16(c.projectionMatrixInverse) };
});
{
  const c = new PerspectiveCamera(60, 1080 / 2400, 0.1, 400);
  c.setViewOffset(1080, 2400, 120, -200, 1080, 2400);
  out.viewOffset = { fov: 60, aspect: 1080 / 2400, near: 0.1, far: 400, view: [1080, 2400, 120, -200, 1080, 2400], projection: m16(c.projectionMatrix) };
}
{
  const c = new PerspectiveCamera(55, 0.45, 0.1, 400);
  c.position.set(0.5, 4.2, 9);
  c.lookAt(0, 2.5, -12);
  c.updateMatrixWorld();
  const pts = [[0, 3, 0], [3, 0, -20], [-3, 6, -5]];
  out.camera = {
    fov: 55, aspect: 0.45, near: 0.1, far: 400, position: [0.5, 4.2, 9], lookAt: [0, 2.5, -12],
    quaternion: q4(c.quaternion), rotation: [d(c.rotation.x), d(c.rotation.y), d(c.rotation.z)],
    viewMatrix: m16(c.matrixWorldInverse), worldDirection: v3(c.getWorldDirection(new Vector3())),
    project: pts.map((p) => ({ point: p, ndc: v3(new Vector3(...p).project(c)) })),
    unproject: [[0, 0, 0.5], [0.3, -0.7, 0.9], [-1, 1, -1]].map((p) => ({ ndc: p, world: v3(new Vector3(...p).unproject(c)) })),
  };
}
{
  // Object3D.lookAt under a rotated, translated parent (non-camera: +Z faces the target).
  const parent = new Group();
  parent.position.set(1, 2, 3);
  parent.rotation.set(0.3, -0.5, 0.2);
  parent.scale.set(2, 1, 1);
  const o = new Object3D();
  o.position.set(0.5, -1, 2);
  parent.add(o);
  parent.updateMatrixWorld(true);
  o.lookAt(4, 0, -2);
  const wp = o.getWorldPosition(new Vector3());
  out.objectLookAt = {
    parent: { position: [1, 2, 3], rotation: [0.3, -0.5, 0.2], scale: [2, 1, 1] }, position: [0.5, -1, 2], target: [4, 0, -2],
    quaternion: q4(o.quaternion), rotation: [d(o.rotation.x), d(o.rotation.y), d(o.rotation.z)], worldPosition: v3(wp),
    worldDirection: v3(o.getWorldDirection(new Vector3())),
  };
  const r = new Object3D();
  r.rotation.set(0.2, 0.4, -0.1);
  r.rotateZ(0.4);
  r.rotateX(-0.25);
  out.rotateOnAxis = { start: [0.2, 0.4, -0.1], ops: [['z', 0.4], ['x', -0.25]], quaternion: q4(r.quaternion), rotation: [d(r.rotation.x), d(r.rotation.y), d(r.rotation.z)] };
}

// Quaternion ops
const up = new Vector3(0, 1, 0);
out.unitVectors = [
  [[0, 1, 0], [0.3, 0.8, 0.52]], [[0, 0, 1], [0.7, 0.1, -0.7]], [[0, 1, 0], [0, -1, 0]], [[1, 0, 0], [-1, 0, 0]], [[0, 0, 1], [0, 0, 1]],
].map(([a, b]) => {
  const A = new Vector3(...a).normalize(), B = new Vector3(...b).normalize();
  const q = new Quaternion().setFromUnitVectors(A, B);
  return { from: v3(A), to: v3(B), quat: q4(q), upRotated: v3(up.clone().applyQuaternion(q)) };
});
out.slerp = [
  [[0.1, 0.2, 0.3], [1.0, -0.5, 2.0], 0.3],
  [[0.1, 0.2, 0.3], [0.1001, 0.2, 0.3001], 0.5],
  [[0, 0, 0], [3.0, 0.1, 0.2], 0.75],
].map(([e1, e2, t]) => {
  const a = new Quaternion().setFromEuler(new Euler(...e1)), b = new Quaternion().setFromEuler(new Euler(...e2));
  return { a: q4(a), b: q4(b), t, result: q4(a.clone().slerp(b, t)), product: q4(a.clone().multiply(b)), angle: d(a.angleTo(b)) };
});
out.axisAngle = [[[1, 0, 0], 0.5], [[0.3, 0.4, 0.5], -1.2]].map(([ax, ang]) => {
  const A = new Vector3(...ax).normalize();
  const q = new Quaternion().setFromAxisAngle(A, ang);
  return { axis: v3(A), angle: ang, quat: q4(q), rotated: v3(new Vector3(1, 2, 3).applyQuaternion(q)) };
});

// Spherical coords: cactus spikes (world.js) and mushroom spots (powerups.js)
out.spherical = [];
for (let i = 0; i < 46; i++) {
  const phi = Math.acos(1 - (2 * (i + 0.5)) / 46);
  out.spherical.push({ radius: 1, phi: d(phi), theta: d(i * 2.399), v: v3(new Vector3().setFromSphericalCoords(1, phi, i * 2.399)) });
}
for (const [theta, phi] of [[0, 0.3], [1.3, 1.0], [2.8, 0.9], [4.4, 1.0], [5.6, 0.95]]) {
  out.spherical.push({ radius: 0.4, phi, theta, v: v3(new Vector3().setFromSphericalCoords(0.4, phi, theta)) });
}

// Color
const hexes = [0xfff6d5, 0xf7d23e, 0x2fa58f, 0x543847, 0x000000, 0xffffff, 0x808080, 0x3cc45a, 0xff5a4a, 0x1e7fd6];
out.colorHsl = hexes.map((h) => {
  const c = new Color(h);
  const s = {}; c.getHSL(s, THREE.SRGBColorSpace);
  const l = {}; c.getHSL(l);
  return { hex: h, srgb: [d(s.h), d(s.s), d(s.l)], linear: [d(l.h), d(l.s), d(l.l)], linearRgb: [d(c.r), d(c.g), d(c.b)] };
});
out.colorSetHsl = [[0, 1, 0.5], [0.33, 1, 0.6], [0.75, 0.5, 0.25], [1.4, 1, 0.55], [-0.2, 1, 0.2], [0.5, 0, 0.3], [0.9, 2, 1.2]].map(([h, s, l]) => ({ hsl: [h, s, l], hex: new Color().setHSL(h, s, l).getHex() }));
const pairs = [[0xded895, 0xeef4fb], [0xd2c26a, 0xd6e4f2], [0x9ce659, 0xbfe3ff], [0xf7d23e, 0xfff3c4], [0x000000, 0xffffff], [0x2a9bd0, 0x1b1b3a]];
out.colorLerp = [];
for (const [a, b] of pairs) for (const t of [0, 0.12, 0.125, 0.3, 0.5, 0.875, 1]) {
  out.colorLerp.push({ a, b, t, hex: new Color(a).lerp(new Color(b), t).getHex() });
}
out.colorMultiply = pairs.map(([a, b]) => ({ a, b, hex: new Color(a).multiply(new Color(b)).getHex() }));
// bird.js wingColor with real three.js (colour-managed lerp), for every skin of catalog.js
const { SKINS } = await import(new URL('../../src/catalog.js', import.meta.url).href);
out.wingColor = SKINS.map((skin) => {
  const tmp = new Color(skin.wing);
  const hsl = {};
  tmp.getHSL(hsl, THREE.SRGBColorSpace);
  const own = hsl.l < 0.8;
  const w = own ? skin.wing : new Color(skin.body).lerp(new Color(skin.belly), 0.12).getHex();
  return { id: skin.id, body: skin.body, belly: skin.belly, wingIn: skin.wing, cover: skin.cover, tail: skin.tail, lightness: d(hsl.l), wing: w, coverOut: own ? skin.cover : skin.tail };
});

fs.writeFileSync(outPath('engine-math.json'), JSON.stringify(out) + '\n');
console.log('wrote engine-math.json');
