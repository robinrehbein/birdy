import * as THREE from 'three';

// Pooled particles drawn with a single InstancedMesh. Particles live in world
// space and scroll with the world, so trails behind the bird come for free.
export function createParticles(scene, max = 300) {
  const geo = new THREE.IcosahedronGeometry(1, 0);
  const mat = new THREE.MeshBasicMaterial({ toneMapped: false });
  const mesh = new THREE.InstancedMesh(geo, mat, max);
  mesh.frustumCulled = false;
  scene.add(mesh);

  const parts = Array.from({ length: max }, () => ({
    life: 0, maxLife: 1, size: 0.1, gravity: 0, drag: 0,
    pos: new THREE.Vector3(), vel: new THREE.Vector3(), spin: 0,
  }));
  const dummy = new THREE.Object3D();
  const color = new THREE.Color();
  let cursor = 0;

  for (let i = 0; i < max; i++) {
    dummy.scale.setScalar(0);
    dummy.updateMatrix();
    mesh.setMatrixAt(i, dummy.matrix);
    mesh.setColorAt(i, color.set(0xffffff));
  }

  // `drift` adds a constant velocity along +z (backwards, for trails).
  function emit(pos, { count = 10, colors = [0xffffff], speed = 4, size = 0.12, life = 0.6, gravity = -6, drag = 1.5, spread = 1, drift = 0 }) {
    for (let n = 0; n < count; n++) {
      const i = cursor;
      cursor = (cursor + 1) % max;
      const p = parts[i];
      p.pos.copy(pos);
      p.vel.set(Math.random() - 0.5, Math.random() - 0.5, Math.random() - 0.5).normalize()
        .multiplyScalar(speed * (0.4 + Math.random() * 0.6) * spread);
      p.vel.z += drift;
      p.life = p.maxLife = life * (0.7 + Math.random() * 0.6);
      p.size = size * (0.6 + Math.random() * 0.8);
      p.gravity = gravity;
      p.drag = drag;
      p.spin = Math.random() * 6;
      mesh.setColorAt(i, color.set(colors[Math.floor(Math.random() * colors.length)]));
    }
    mesh.instanceColor.needsUpdate = true;
  }

  function emitColor(pos, hex, opts) {
    emit(pos, { ...opts, colors: [hex] });
  }

  function update(dt, dz) {
    for (let i = 0; i < max; i++) {
      const p = parts[i];
      if (p.life <= 0) continue;
      p.life -= dt;
      p.vel.y += p.gravity * dt;
      p.vel.multiplyScalar(Math.max(0, 1 - p.drag * dt));
      p.pos.addScaledVector(p.vel, dt);
      p.pos.z += dz;
      const k = Math.max(0, p.life / p.maxLife);
      dummy.position.copy(p.pos);
      dummy.rotation.set(p.spin * k, p.spin * 1.3 * k, 0);
      dummy.scale.setScalar(p.life > 0 ? p.size * (0.3 + 0.7 * k) : 0);
      dummy.updateMatrix();
      mesh.setMatrixAt(i, dummy.matrix);
    }
    mesh.instanceMatrix.needsUpdate = true;
  }

  function clear() {
    for (const p of parts) p.life = 0;
    update(0, 0);
  }

  return { emit, emitColor, update, clear };
}
