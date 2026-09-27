import * as THREE from 'three';

// Low-poly Flappy-style bird built from primitives. Faces -Z.
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

  // Beak: two rounded mandibles like Flappy's lips, faceted like the body.
  const beakMat = mat(0xf57c21);
  const beakLowMat = mat(0xe0521b);
  const beakTop = new THREE.Mesh(new THREE.SphereGeometry(0.26, 10, 7), beakMat);
  beakTop.scale.set(1.15, 0.5, 1.25);
  beakTop.position.set(0, 0.0, -0.66);
  group.add(beakTop);
  const beakBottom = new THREE.Mesh(new THREE.SphereGeometry(0.24, 10, 7), beakLowMat);
  beakBottom.scale.set(1.0, 0.42, 1.05);
  beakBottom.position.set(0, -0.15, -0.6);
  group.add(beakBottom);

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
    feather.scale.set(0.55, 0.25, 1.2);
    feather.position.set(x, 0.18, 0.72);
    feather.rotation.set(0.45, rotY, 0);
    group.add(feather);
  }

  group.traverse((o) => {
    if (o.isMesh) o.castShadow = true;
  });

  // flapPhase is advanced by the game; faster after a flap.
  function animateWings(phase) {
    const a = Math.sin(phase) * 0.8;
    for (const w of wings) w.rotation.z = w.userData.side * a;
  }

  // Emissive glow used for power-ups (rainbow star); null switches it off.
  const glowMats = new Set();
  group.traverse((o) => {
    if (o.isMesh) glowMats.add(o.material);
  });
  function setGlow(color, intensity = 0.6) {
    for (const m of glowMats) {
      if (color === null) m.emissive.setRGB(0, 0, 0);
      else m.emissive.copy(color).multiplyScalar(intensity);
    }
  }

  // Recolour the bird (see SKINS in progress.js). Same model and shading.
  const skinMats = { body: yellow, belly: cream, wing: wingMat, cover: coverMat, tail: tailMat, beak: beakMat, beakLow: beakLowMat };
  function setSkin(skin) {
    for (const [key, m] of Object.entries(skinMats)) {
      m.color.setHex(skin[key]);
      m.metalness = skin.metal && key !== 'beak' && key !== 'beakLow' ? 0.55 : 0;
      m.roughness = skin.metal ? 0.3 : key === 'tail' ? 0.45 : 0.55;
    }
  }

  return { group, animateWings, setGlow, setSkin };
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
