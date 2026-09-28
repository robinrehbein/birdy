import * as THREE from 'three';

// Low-poly bird built from primitives. Faces -Z.
export function createBird() {
  const group = new THREE.Group();

  const mat = (color) => new THREE.MeshStandardMaterial({ color, roughness: 0.55, flatShading: true });
  const yellow = mat(0xf7d23e);
  const cream = mat(0xfff3c4);
  const white = mat(0xffffff);
  const black = mat(0x222222);

  const body = new THREE.Mesh(new THREE.SphereGeometry(0.6, 14, 10), yellow);
  body.scale.set(1, 0.9, 1.15);
  group.add(body);

  const belly = new THREE.Mesh(new THREE.SphereGeometry(0.42, 12, 8), cream);
  belly.position.set(0, -0.2, -0.3);
  group.add(belly);

  for (const side of [-1, 1]) {
    const eye = new THREE.Mesh(new THREE.SphereGeometry(0.22, 12, 8), white);
    eye.position.set(side * 0.38, 0.25, -0.38);
    group.add(eye);
    const pupil = new THREE.Mesh(new THREE.SphereGeometry(0.09, 8, 6), black);
    pupil.position.set(side * 0.5, 0.27, -0.5);
    group.add(pupil);
  }

  // Beak: two rounded mandibles, faceted like the body.
  const beakMat = mat(0xf57c21);
  const beakLowMat = mat(0xe0521b);
  const beakTop = new THREE.Mesh(new THREE.SphereGeometry(0.26, 10, 7), beakMat);
  beakTop.scale.set(1.15, 0.5, 1.25);
  beakTop.position.set(0, 0.0, -0.66);
  const beakBottom = new THREE.Mesh(new THREE.SphereGeometry(0.24, 10, 7), beakLowMat);
  beakBottom.scale.set(1.0, 0.42, 1.05);
  beakBottom.position.set(0, -0.15, -0.6);
  const roundBeak = new THREE.Group();
  roundBeak.add(beakTop, beakBottom);
  group.add(roundBeak);

  // Wings: feather silhouette with scalloped trailing edge, extruded with a
  // bevel and kept low in segments so it reads faceted like the body.
  const smooth = (color) => new THREE.MeshStandardMaterial({ color, roughness: 0.45 });
  const wingGeo = createWingGeometry();
  const wingMat = mat(0xfff6d5);
  const coverMat = mat(0xf6e3a1);
  const wings = [];
  for (const side of [-1, 1]) {
    const pivot = new THREE.Group();
    pivot.position.set(side * 0.48, 0.08, 0.08);
    const wing = new THREE.Mesh(wingGeo, wingMat);
    wing.scale.x = side; // mirror the left wing
    wing.rotation.z = side * -0.15; // slight droop
    // Smaller covert-feather layer on top for a layered look.
    const cover = new THREE.Mesh(wingGeo, coverMat);
    cover.scale.set(0.62, 1, 0.7);
    cover.position.set(0, 0.045, -0.04);
    wing.add(cover);
    pivot.add(wing);
    pivot.userData.side = side;
    group.add(pivot);
    wings.push(pivot);
  }

  // Tail: three rounded feathers fanned out.
  const tailGeo = new THREE.SphereGeometry(0.2, 14, 10);
  const tailMat = smooth(0xf2c230);
  for (const [x, rotY] of [[-0.12, 0.35], [0, 0], [0.12, -0.35]]) {
    const feather = new THREE.Mesh(tailGeo, tailMat);
    // A little bigger than the first model, so the bird reads from behind.
    feather.scale.set(0.7, 0.3, 1.5);
    feather.position.set(x * 1.2, 0.2, 0.8);
    feather.rotation.set(0.45, rotY, 0);
    group.add(feather);
  }

  // Workshop parts (patterns, hats, eyewear, beaks), all hidden until chosen.
  const patDark = mat(0xf2c230);
  const patLight = mat(0xfff3c4);
  const parts = buildParts({ mat, beakMat, beakLowMat, patDark, patLight });
  parts.beak.round = roundBeak;
  for (const kind of Object.values(parts)) {
    for (const obj of Object.values(kind)) {
      if (obj === roundBeak) continue;
      obj.visible = false;
      group.add(obj);
    }
  }
  const look = { pattern: 'plain', hat: 'none', eyes: 'normal', beak: 'round' };
  function setLook(next) {
    Object.assign(look, next);
    for (const [kind, id] of Object.entries(look)) {
      for (const [key, obj] of Object.entries(parts[kind])) obj.visible = key === id;
    }
  }

  group.traverse((o) => {
    if (o.isMesh) o.castShadow = true;
  });

  // flapPhase is advanced by the game; faster after a flap.
  function animateWings(phase) {
    const a = Math.sin(phase) * 0.8;
    for (const w of wings) w.rotation.z = w.userData.side * a;
    parts.hat.cap.userData.propeller.rotation.y = phase * 2.2;
    parts.hat.halo.userData.ring.position.y = 0.34 + Math.sin(phase * 0.35) * 0.04;
  }

  // Emissive glow used for power-ups (rainbow star); null switches it off.
  const glowMats = new Set();
  group.traverse((o) => {
    if (o.isMesh && !o.material.userData.ownGlow) glowMats.add(o.material);
  });
  function setGlow(color, intensity = 0.6) {
    for (const m of glowMats) {
      if (color === null) m.emissive.setRGB(0, 0, 0);
      else m.emissive.copy(color).multiplyScalar(intensity);
    }
  }

  // Recolour the bird (see SKINS in progress.js). Same model and shading.
  const skinMats = { body: yellow, belly: cream, wing: wingMat, cover: coverMat, tail: tailMat, beak: beakMat, beakLow: beakLowMat };
  // Pale wings (cream on the yellow bird) read as sticks from behind: those
  // are drawn in the body's colour family instead (a lighter body tone, the
  // covert layer in the tail colour). Distinctly coloured wings stay as set.
  const tmp = new THREE.Color();
  const wingColor = (skin) => {
    tmp.setHex(skin.wing);
    const hsl = {};
    tmp.getHSL(hsl, THREE.SRGBColorSpace);
    if (hsl.l < 0.8) return { wing: skin.wing, cover: skin.cover };
    const w = new THREE.Color(skin.body).lerp(new THREE.Color(skin.belly), 0.12);
    return { wing: w.getHex(), cover: skin.tail };
  };
  function setSkin(skin) {
    patDark.color.setHex(skin.tail);
    patLight.color.setHex(skin.belly === skin.body ? 0xffffff : skin.belly);
    const wc = wingColor(skin);
    for (const [key, m] of Object.entries(skinMats)) {
      m.color.setHex(key === 'wing' || key === 'cover' ? wc[key] : skin[key]);
      m.metalness = skin.metal && key !== 'beak' && key !== 'beakLow' ? 0.55 : 0;
      m.roughness = skin.metal ? 0.3 : key === 'tail' ? 0.45 : 0.55;
    }
  }

  return { group, animateWings, setGlow, setSkin, setLook };
}

function createWingGeometry() {
  // Drawn in the XY plane: x = span (outward), y = chord (+y = forward).
  const shape = new THREE.Shape();
  shape.moveTo(0, 0.26);
  shape.bezierCurveTo(0.3, 0.38, 0.7, 0.28, 0.9, 0.04); // leading edge to tip
  // Scalloped trailing edge: three rounded feathers back to the root.
  shape.quadraticCurveTo(0.9, -0.16, 0.72, -0.13);
  shape.quadraticCurveTo(0.66, -0.33, 0.48, -0.24);
  shape.quadraticCurveTo(0.39, -0.4, 0.22, -0.28);
  shape.quadraticCurveTo(0.08, -0.32, 0, -0.16);
  shape.lineTo(0, 0.26);

  const geo = new THREE.ExtrudeGeometry(shape, {
    depth: 0.04,
    bevelEnabled: true,
    bevelThickness: 0.035,
    bevelSize: 0.03,
    bevelSegments: 2,
    curveSegments: 5,
  });
  geo.translate(0, 0, -0.02);
  geo.rotateX(-Math.PI / 2); // lay flat: chord along -Z (forward), thickness along Y
  return geo;
}

// --- Workshop parts ---------------------------------------------------------
// Body is an ellipsoid with these radii (sphere 0.6 scaled 1, 0.9, 1.15).
const BODY = new THREE.Vector3(0.6, 0.54, 0.69);
const UP = new THREE.Vector3(0, 1, 0);

// A flat piece lying on the body surface in direction `dir`.
function decal(geo, material, dir, lift = 0.005) {
  const d = new THREE.Vector3(...dir).normalize();
  const p = d.clone().multiply(BODY);
  const n = d.clone().divide(BODY).normalize();
  const m = new THREE.Mesh(geo, material);
  m.position.copy(p).addScaledVector(n, lift);
  m.quaternion.setFromUnitVectors(UP, n);
  return m;
}

// A ring around the body at depth z (a slice of the ellipsoid).
function bodyRing(material, z, tube, arc = Math.PI * 2, y = 0) {
  const f = Math.sqrt(Math.max(0, 1 - (z / BODY.z) ** 2 - (y / BODY.y) ** 2));
  const ring = new THREE.Mesh(new THREE.TorusGeometry(1, tube, 4, 18, arc), material);
  ring.scale.set(BODY.x * f + 0.01, BODY.y * f + 0.01, 1);
  ring.position.set(0, y, z);
  return ring;
}

function heartShape(s) {
  const h = new THREE.Shape();
  h.moveTo(0, -0.9 * s);
  h.bezierCurveTo(-1.3 * s, 0, -0.6 * s, 1 * s, 0, 0.45 * s);
  h.bezierCurveTo(0.6 * s, 1 * s, 1.3 * s, 0, 0, -0.9 * s);
  return h;
}

const EYE = [-1, 1].map((side) => ({ side, pos: new THREE.Vector3(side * 0.38, 0.25, -0.38), dir: new THREE.Vector3(side * 0.7, 0.1, -0.7).normalize() }));

// Something placed in front of each eye (lens, frame), facing outwards.
function overEyes(make, dist = 0.2) {
  const g = new THREE.Group();
  for (const e of EYE) {
    const m = make(e.side);
    m.position.copy(e.pos).addScaledVector(e.dir, dist);
    m.quaternion.setFromUnitVectors(new THREE.Vector3(0, 0, 1), e.dir);
    g.add(m);
  }
  return g;
}

function buildParts({ mat, beakMat, beakLowMat, patDark, patLight }) {
  const dark = mat(0x2e2530);
  const pink = mat(0xff8fb0);
  const red = mat(0xff3d6e);
  const gold = new THREE.MeshStandardMaterial({ color: 0xffc629, roughness: 0.3, metalness: 0.6, flatShading: true });
  const disc = (r) => new THREE.CylinderGeometry(r, r, 0.03, 10);
  const add = (g, ...objs) => { g.add(...objs); return g; };
  const at = (m, x, y, z, rx = 0, ry = 0, rz = 0) => { m.position.set(x, y, z); m.rotation.set(rx, ry, rz); return m; };
  const sph = (r, material, sx = 1, sy = sx, sz = sx) => { const m = new THREE.Mesh(new THREE.SphereGeometry(r, 8, 6), material); m.scale.set(sx, sy, sz); return m; };

  // Patterns
  const pattern = { plain: new THREE.Group() };
  pattern.cheeks = add(new THREE.Group(), ...[-1, 1].map((s) => decal(disc(0.1), pink, [s * 0.75, 0.02, -0.66])));
  pattern.spots = add(new THREE.Group(), ...[
    [0.3, 0.8, 0.4, 0.1], [-0.35, 0.75, 0.45, 0.12], [0, 0.5, 0.85, 0.11], [0.55, 0.4, 0.6, 0.09],
    [-0.6, 0.35, 0.6, 0.1], [0.1, 0.95, -0.05, 0.09], [-0.2, 0.15, 0.95, 0.09], [0.35, 0.1, 0.9, 0.08],
  ].map(([x, y, z, r]) => decal(disc(r), patLight, [x, y, z])));
  pattern.stripes = add(new THREE.Group(), ...[0.12, 0.34, 0.54].map((z) => bodyRing(patDark, z, 0.05, Math.PI)));
  const band = bodyRing(dark, 0, 0.13, Math.PI * 2);
  band.rotation.x = Math.PI / 2;
  band.scale.set(0.54, 0.62, 0.6);
  band.position.set(0, 0.26, -0.02);
  pattern.mask = add(new THREE.Group(), band, at(sph(0.08, dark, 1, 0.7, 1.4), 0.1, 0.22, 0.66, 0.6, 0.4), at(sph(0.08, dark, 1, 0.7, 1.4), -0.1, 0.22, 0.66, 0.6, -0.4));
  const heart = decal(new THREE.ExtrudeGeometry(heartShape(0.3), { depth: 0.03, bevelEnabled: false }), red, [0, 0.5, 1], 0.01);
  heart.geometry.rotateX(-Math.PI / 2);
  pattern.heart = add(new THREE.Group(), heart);

  // Hats (anchored on the top of the head)
  const hat = { none: new THREE.Group() };
  const head = () => at(new THREE.Group(), 0, 0.5, -0.12, 0.12);
  hat.crest = add(head(), ...[[-0.35, 0.12], [0, 0], [0.35, 0.12]].map(([rz, z]) => at(sph(0.1, patDark, 1, 3, 1), 0, 0.2, z, -0.35, 0, rz)));
  const flower = at(new THREE.Group(), 0.24, 0.02, -0.05, 0, 0, -0.6);
  for (let i = 0; i < 5; i++) {
    const a = (i / 5) * Math.PI * 2;
    flower.add(at(sph(0.08, mat(0xffffff), 1, 0.5, 1), Math.cos(a) * 0.1, 0.02, Math.sin(a) * 0.1));
  }
  flower.add(at(sph(0.06, mat(0xffc93c)), 0, 0.05, 0));
  hat.flower = add(head(), flower);
  hat.party = add(head(),
    at(new THREE.Mesh(new THREE.ConeGeometry(0.2, 0.5, 8), mat(0x5ad1ff)), 0, 0.22, 0, 0, 0, 0.15),
    at(bodyRing(mat(0xffd84a), 0, 0.03), -0.02, 0.08, 0, Math.PI / 2),
    at(sph(0.07, mat(0xff5a8a)), -0.07, 0.47, 0));
  hat.party.children[1].scale.set(0.19, 0.19, 1);
  const cap = add(head(),
    at(new THREE.Mesh(new THREE.SphereGeometry(0.3, 10, 5, 0, Math.PI * 2, 0, Math.PI / 2), mat(0xe8453c)), 0, -0.02, 0),
    at(new THREE.Mesh(new THREE.CylinderGeometry(0.2, 0.2, 0.03, 10, 1, false, -Math.PI / 2, Math.PI), mat(0x2f86d0)), 0, -0.02, -0.18, 0, Math.PI / 2),
    at(new THREE.Mesh(new THREE.CylinderGeometry(0.02, 0.02, 0.14, 5), mat(0x555555)), 0, 0.33, 0));
  const propeller = at(new THREE.Group(), 0, 0.4, 0);
  propeller.add(at(sph(0.06, mat(0xffd84a), 3.2, 0.35, 1), 0.17, 0, 0), at(sph(0.06, mat(0x5ad1ff), 3.2, 0.35, 1), -0.17, 0, 0));
  cap.add(propeller);
  cap.userData.propeller = propeller;
  hat.cap = cap;
  hat.tophat = add(head(),
    at(new THREE.Mesh(new THREE.CylinderGeometry(0.36, 0.36, 0.04, 12), dark), 0, 0, 0),
    at(new THREE.Mesh(new THREE.CylinderGeometry(0.22, 0.24, 0.42, 12), dark), 0, 0.22, 0),
    at(new THREE.Mesh(new THREE.CylinderGeometry(0.245, 0.245, 0.08, 12), red), 0, 0.07, 0));
  hat.viking = add(head(),
    at(new THREE.Mesh(new THREE.SphereGeometry(0.36, 10, 5, 0, Math.PI * 2, 0, Math.PI / 2), mat(0xa9b3bd)), 0, -0.1, 0),
    at(new THREE.Mesh(new THREE.CylinderGeometry(0.37, 0.37, 0.07, 12), gold), 0, -0.08, 0),
    ...[-1, 1].map((s) => at(new THREE.Mesh(new THREE.ConeGeometry(0.07, 0.36, 6), mat(0xfff3d6)), s * 0.38, 0.12, 0, 0, 0, -s * 0.8)));
  const crown = add(head(), at(new THREE.Mesh(new THREE.CylinderGeometry(0.24, 0.22, 0.14, 10, 1, true), gold), 0, 0.02, 0));
  crown.children[0].material = gold.clone();
  crown.children[0].material.side = THREE.DoubleSide;
  for (let i = 0; i < 5; i++) {
    const a = (i / 5) * Math.PI * 2;
    crown.add(at(new THREE.Mesh(new THREE.ConeGeometry(0.06, 0.14, 4), gold), Math.cos(a) * 0.22, 0.15, Math.sin(a) * 0.22));
    crown.add(at(sph(0.035, i % 2 ? red : mat(0x4ab8ff)), Math.cos(a) * 0.235, 0.02, Math.sin(a) * 0.235));
  }
  hat.crown = crown;
  const ring = at(new THREE.Mesh(new THREE.TorusGeometry(0.26, 0.045, 6, 20),
    new THREE.MeshStandardMaterial({ color: 0xffe066, emissive: 0xffc629, emissiveIntensity: 0.6, flatShading: true })), 0, 0.34, 0, Math.PI / 2);
  ring.material.userData.ownGlow = true;
  hat.halo = add(head(), ring);
  hat.halo.userData.ring = ring;

  // Eyes
  const eyes = { normal: new THREE.Group() };
  eyes.lashes = overEyes((side) => add(new THREE.Group(), ...[-0.1, 0, 0.1].map((x) => at(new THREE.Mesh(new THREE.BoxGeometry(0.03, 0.12, 0.03), dark), x, 0.2, -0.02, 0, 0, -x * 2.5 * side))), 0.05);
  eyes.brows = overEyes((side) => at(new THREE.Mesh(new THREE.BoxGeometry(0.3, 0.06, 0.06), dark), 0, 0.23, 0, 0, 0, side * -0.4), 0.08);
  const lens = (material, shape) => (shape ? new THREE.Mesh(new THREE.ExtrudeGeometry(heartShape(0.17), { depth: 0.03, bevelEnabled: false }), material) : new THREE.Mesh(disc(0.19).rotateX(Math.PI / 2), material));
  const bridge = (material) => at(new THREE.Mesh(new THREE.BoxGeometry(0.42, 0.04, 0.04), material), 0, 0.32, -0.62);
  eyes.shades = add(overEyes(() => lens(new THREE.MeshStandardMaterial({ color: 0x1e1e28, roughness: 0.15, metalness: 0.3, flatShading: true }))), bridge(dark));
  eyes.hearts = add(overEyes(() => lens(red, true)), bridge(red));
  const strap = bodyRing(mat(0x7a4a24), 0, 0.06);
  strap.rotation.x = Math.PI / 2;
  strap.scale.set(0.53, 0.62, 0.6);
  strap.position.set(0, 0.3, 0);
  eyes.goggles = add(overEyes(() => add(new THREE.Group(),
    new THREE.Mesh(new THREE.TorusGeometry(0.19, 0.05, 5, 14), mat(0x7a4a24)),
    new THREE.Mesh(disc(0.17).rotateX(Math.PI / 2), mat(0xbfe9ff)))), strap);

  // Beaks (round = the default, added by createBird)
  const beak = {};
  beak.duck = add(new THREE.Group(), at(sph(0.26, beakMat, 1.45, 0.34, 1.5), 0, -0.03, -0.74), at(sph(0.24, beakLowMat, 1.3, 0.3, 1.3), 0, -0.13, -0.68));
  beak.hook = add(new THREE.Group(),
    at(sph(0.26, beakMat, 0.85, 0.72, 1.15), 0, 0.02, -0.66),
    at(new THREE.Mesh(new THREE.ConeGeometry(0.1, 0.26, 6), beakMat), 0, -0.1, -0.88, Math.PI * 0.85),
    at(sph(0.2, beakLowMat, 0.8, 0.45, 0.9), 0, -0.14, -0.6));
  beak.toucan = add(new THREE.Group(),
    at(sph(0.26, mat(0xff9a1f), 1.05, 0.75, 2.4), 0, 0.02, -0.98),
    at(sph(0.22, mat(0xffd23d), 1, 0.55, 2.2), 0, -0.14, -0.92),
    at(sph(0.1, dark), 0, -0.04, -1.56));

  return { pattern, hat, eyes, beak };
}
