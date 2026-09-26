import * as THREE from 'three';

// Low-poly Flappy-style bird built from primitives. Faces -Z.
export function createBird() {
  const group = new THREE.Group();

  const mat = (color) => new THREE.MeshStandardMaterial({ color, roughness: 0.55, flatShading: true });
  const yellow = mat(0xf7d23e);
  const cream = mat(0xfff3c4);
  const white = mat(0xffffff);
  const black = mat(0x222222);
  const orange = mat(0xf26b1d);

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

  const beakTop = new THREE.Mesh(new THREE.ConeGeometry(0.2, 0.5, 6), orange);
  beakTop.rotation.x = -Math.PI / 2;
  beakTop.position.set(0, 0.0, -0.85);
  beakTop.scale.set(1.3, 1, 0.6);
  group.add(beakTop);
  const beakBottom = beakTop.clone();
  beakBottom.position.y = -0.14;
  beakBottom.scale.set(1.1, 0.8, 0.45);
  group.add(beakBottom);

  const wings = [];
  for (const side of [-1, 1]) {
    const pivot = new THREE.Group();
    pivot.position.set(side * 0.5, 0.05, 0.05);
    const wing = new THREE.Mesh(new THREE.BoxGeometry(0.75, 0.1, 0.5), cream);
    wing.position.x = side * 0.35;
    pivot.add(wing);
    pivot.userData.side = side;
    group.add(pivot);
    wings.push(pivot);
  }

  const tail = new THREE.Mesh(new THREE.BoxGeometry(0.35, 0.1, 0.4), yellow);
  tail.position.set(0, 0.15, 0.7);
  tail.rotation.x = 0.4;
  group.add(tail);

  group.traverse((o) => {
    if (o.isMesh) o.castShadow = true;
  });

  // flapPhase is advanced by the game; faster after a flap.
  function animateWings(phase) {
    const a = Math.sin(phase) * 0.8;
    for (const w of wings) w.rotation.z = w.userData.side * a;
  }

  return { group, animateWings };
}
