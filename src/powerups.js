import * as THREE from 'three';
import { mergeGeometries } from 'three/examples/jsm/utils/BufferGeometryUtils.js';

// Power-up definitions. Duration in seconds.
export const POWERUPS = {
  star: { label: 'Regenbogen', icon: 'rainbow', duration: 6, color: 0xffd400 },
  magnet: { label: 'Magnet', icon: 'magnet', duration: 9, color: 0xe53935 },
  mini: { label: 'Mini', icon: 'mushroom', duration: 9, color: 0x9b59b6 },
};
export const POWERUP_TYPES = Object.keys(POWERUPS);

const mat = (color, extra = {}) =>
  new THREE.MeshStandardMaterial({ color, roughness: 0.4, flatShading: true, ...extra });

function starShape(outer, inner) {
  const shape = new THREE.Shape();
  for (let i = 0; i < 10; i++) {
    const r = i % 2 ? inner : outer;
    const a = (i / 10) * Math.PI * 2 + Math.PI / 2;
    const x = Math.cos(a) * r;
    const y = Math.sin(a) * r;
    if (i === 0) shape.moveTo(x, y);
    else shape.lineTo(x, y);
  }
  shape.closePath();
  return shape;
}

function buildStar() {
  const geo = new THREE.ExtrudeGeometry(starShape(0.5, 0.22), {
    depth: 0.14, bevelEnabled: true, bevelThickness: 0.06, bevelSize: 0.05, bevelSegments: 1,
  });
  geo.center();
  const m = mat(0xffd400, { emissive: 0x664400 });
  const mesh = new THREE.Mesh(geo, m);
  mesh.userData.rainbow = m;
  return mesh;
}

function buildMagnet() {
  const g = new THREE.Group();
  const red = mat(0xe53935);
  const metal = mat(0xd9d9d9, { metalness: 0.6 });
  const arc = new THREE.Mesh(new THREE.TorusGeometry(0.3, 0.12, 6, 12, Math.PI), red);
  arc.rotation.z = 0; // opening points down
  arc.position.y = 0.05;
  g.add(arc);
  for (const side of [-1, 1]) {
    const leg = new THREE.Mesh(new THREE.CylinderGeometry(0.12, 0.12, 0.22, 6), red);
    leg.position.set(side * 0.3, -0.06, 0);
    const tip = new THREE.Mesh(new THREE.CylinderGeometry(0.12, 0.12, 0.14, 6), metal);
    tip.position.set(side * 0.3, -0.24, 0);
    g.add(leg, tip);
  }
  return g;
}

function buildMushroom() {
  const g = new THREE.Group();
  const cap = new THREE.Mesh(new THREE.SphereGeometry(0.42, 10, 6, 0, Math.PI * 2, 0, Math.PI / 2), mat(0x9b59b6));
  cap.position.y = 0.02;
  cap.scale.y = 0.85;
  g.add(cap);
  const dotMat = mat(0xffffff);
  for (const [theta, phi] of [[0, 0.3], [1.3, 1.0], [2.8, 0.9], [4.4, 1.0], [5.6, 0.95]]) {
    const dot = new THREE.Mesh(new THREE.SphereGeometry(0.09, 5, 4), dotMat);
    dot.position.setFromSphericalCoords(0.4, phi, theta);
    dot.position.y = dot.position.y * 0.85 + 0.02;
    g.add(dot);
  }
  const stem = new THREE.Mesh(new THREE.CylinderGeometry(0.18, 0.22, 0.34, 8), mat(0xfff3d6));
  stem.position.y = -0.15;
  g.add(stem);
  const eyeMat = mat(0x222222);
  for (const side of [-1, 1]) {
    const eye = new THREE.Mesh(new THREE.SphereGeometry(0.04, 4, 3), eyeMat);
    eye.position.set(side * 0.07, -0.12, 0.2);
    g.add(eye);
  }
  return g;
}

// Looks only: the pickup radius (1.4, main.js) stays the same.
const bubbleGeo = new THREE.SphereGeometry(1.15, 16, 12);
// A billboard rim (white ring with a plum edge) makes the bubble read from afar.
const rimGeo = (() => {
  const outer = new THREE.RingGeometry(1.14, 1.32, 32);
  const edge = new THREE.RingGeometry(1.32, 1.4, 32);
  const col = (g, hex) => {
    const c = new THREE.Color(hex);
    const a = new Float32Array(g.attributes.position.count * 3);
    for (let i = 0; i < a.length; i += 3) a.set([c.r, c.g, c.b], i);
    g.setAttribute('color', new THREE.BufferAttribute(a, 3));
    return g;
  };
  return mergeGeometries([col(outer, 0xffffff), col(edge, 0x543847)]);
})();
const rimMat = new THREE.MeshBasicMaterial({ vertexColors: true, transparent: true, opacity: 0.9, depthWrite: false, toneMapped: false });

export function createPowerupPickup(scene, type) {
  const group = new THREE.Group();
  const icon = type === 'star' ? buildStar() : type === 'magnet' ? buildMagnet() : buildMushroom();
  icon.scale.setScalar(1.7);
  const bubble = new THREE.Mesh(
    bubbleGeo,
    new THREE.MeshStandardMaterial({
      color: POWERUPS[type].color, transparent: true, opacity: 0.3, roughness: 0.1, depthWrite: false,
      emissive: POWERUPS[type].color, emissiveIntensity: 0.35,
    })
  );
  const rim = new THREE.Mesh(rimGeo, rimMat);
  group.add(icon, bubble, rim);
  group.visible = false;
  scene.add(group);
  return { type, group, icon, rim, active: false };
}

export function animatePickup(p, time, camera) {
  if (camera) p.rim.quaternion.copy(camera.quaternion);
  p.icon.rotation.y = time * 2.5;
  p.icon.position.y = Math.sin(time * 3) * 0.12;
  const rainbow = p.icon.userData.rainbow;
  if (rainbow) {
    rainbow.color.setHSL((time * 0.5) % 1, 1, 0.55);
    rainbow.emissive.setHSL((time * 0.5) % 1, 1, 0.2);
  }
}
