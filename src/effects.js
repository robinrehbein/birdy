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

// Speed streaks: thin light lines beside and above the track that rush past
// the camera. Their opacity follows how fast the run is.
export function createSpeedLines(scene, count = 28) {
  const geo = new THREE.BoxGeometry(0.11, 0.11, 3.6);
  const mat = new THREE.MeshBasicMaterial({ color: 0xffffff, transparent: true, opacity: 0, depthWrite: false, fog: false });
  const mesh = new THREE.InstancedMesh(geo, mat, count);
  mesh.frustumCulled = false;
  scene.add(mesh);
  const dummy = new THREE.Object3D();
  const lines = Array.from({ length: count }, () => ({ pos: new THREE.Vector3() }));
  function place(l, z) {
    // Around the track: left/right of the lanes and above the pipes' gaps.
    const side = Math.random() < 0.5 ? -1 : 1;
    l.pos.set(side * (4.2 + Math.random() * 5), 1 + Math.random() * 11, z);
  }
  for (const l of lines) place(l, -Math.random() * 60);
  return {
    // amount 0..1; dz = world movement this frame.
    update(dz, amount) {
      mat.opacity = 0.7 * amount;
      mesh.visible = amount > 0.01;
      if (!mesh.visible) return;
      lines.forEach((l, i) => {
        l.pos.z += dz * 1.6; // a bit faster than the world for a rush
        if (l.pos.z > 12) place(l, -50 - Math.random() * 15);
        dummy.position.copy(l.pos);
        dummy.scale.set(1, 1, 0.6 + amount);
        dummy.updateMatrix();
        mesh.setMatrixAt(i, dummy.matrix);
      });
      mesh.instanceMatrix.needsUpdate = true;
    },
  };
}

// Crash feedback: a comic "bonk" star at the point of impact that pops up
// during the freeze-frame and fades. One mesh, drawn only while needed.
function starGeometry(points, outer, inner) {
  const shape = new THREE.Shape();
  for (let i = 0; i < points * 2; i++) {
    const r = i % 2 ? inner : outer;
    const a = (i / (points * 2)) * Math.PI * 2 + Math.PI / 2;
    if (i === 0) shape.moveTo(Math.cos(a) * r, Math.sin(a) * r);
    else shape.lineTo(Math.cos(a) * r, Math.sin(a) * r);
  }
  return new THREE.ShapeGeometry(shape);
}

export function createImpact(scene, camera) {
  // Bonk: plum outline star, white star, yellow core (baked, one draw call).
  const layers = [
    [starGeometry(8, 1.25, 0.62), 0x543847, 0],
    [starGeometry(8, 1.08, 0.52), 0xffffff, 0.01],
    [starGeometry(8, 0.62, 0.34), 0xffe14a, 0.02],
  ];
  const geos = layers.map(([g, hex, z]) => {
    g.translate(0, 0, z);
    const n = g.attributes.position.count;
    const c = new THREE.Color(hex);
    const col = new Float32Array(n * 3);
    for (let i = 0; i < n; i++) col.set([c.r, c.g, c.b], i * 3);
    g.setAttribute('color', new THREE.BufferAttribute(col, 3));
    g.deleteAttribute('uv');
    g.deleteAttribute('normal');
    return g;
  });
  const bonkGeo = mergeSimple(geos);
  const bonk = new THREE.Mesh(bonkGeo, new THREE.MeshBasicMaterial({
    vertexColors: true, transparent: true, depthTest: false, depthWrite: false, toneMapped: false, fog: false,
  }));
  bonk.renderOrder = 11;
  bonk.visible = false;
  scene.add(bonk);

  let t = 1;
  return {
    // Impact at `pos` (world space).
    hit(pos) {
      t = 0;
      bonk.position.copy(pos);
      bonk.visible = true;
      bonk.material.opacity = 1;
      bonk.scale.setScalar(0.5);
    },
    update(dt) {
      if (!bonk.visible) return;
      t += dt;
      bonk.quaternion.copy(camera.quaternion);
      bonk.rotateZ(t * 1.5);
      // Pops to full size right away, holds through the freeze, then fades.
      bonk.scale.setScalar(0.5 + 0.3 * Math.min(1, t / 0.12));
      bonk.material.opacity = 1 - THREE.MathUtils.smoothstep(t, 0.25, 0.5);
      if (t > 0.5) bonk.visible = false;
    },
    clear() {
      bonk.visible = false;
    },
  };
}

// Merge geometries that share the same attributes (position + color).
function mergeSimple(geos) {
  const out = new THREE.BufferGeometry();
  const pos = [];
  const col = [];
  for (const g of geos) {
    const gi = g.index ? g.toNonIndexed() : g;
    pos.push(...gi.attributes.position.array);
    col.push(...gi.attributes.color.array);
  }
  out.setAttribute('position', new THREE.Float32BufferAttribute(pos, 3));
  out.setAttribute('color', new THREE.Float32BufferAttribute(col, 3));
  return out;
}

// Power-up aura around the bird (one billboard ring, drawn only while a
// power-up is active): a colour-cycling rainbow ring, magnet waves that run
// outwards, or a pulsing ring that keeps the tiny mini bird easy to spot.
export function createAura(scene, camera) {
  const ring = new THREE.Mesh(
    new THREE.RingGeometry(0.82, 1, 40),
    new THREE.MeshBasicMaterial({ color: 0xffffff, transparent: true, depthWrite: false, toneMapped: false, fog: false, side: THREE.DoubleSide }),
  );
  ring.renderOrder = 9;
  ring.visible = false;
  scene.add(ring);
  const col = new THREE.Color();
  return {
    // kind: 'star' | 'magnet' | 'mini' | null; `size` = bird scale.
    update(kind, pos, time, size) {
      ring.visible = !!kind;
      if (!kind) return;
      ring.position.copy(pos);
      ring.quaternion.copy(camera.quaternion);
      const m = ring.material;
      if (kind === 'star') {
        m.color.copy(col.setHSL((time * 1.5) % 1, 1, 0.6));
        m.opacity = 0.75;
        ring.scale.setScalar(size * (1.25 + 0.08 * Math.sin(time * 12)));
      } else if (kind === 'magnet') {
        const k = (time * 1.6) % 1; // a wave every 0.6 s
        m.color.setHex(0xff4a4a);
        m.opacity = 0.7 * (1 - k);
        ring.scale.setScalar(size * (1 + 2.2 * k));
      } else {
        m.color.setHex(0xc58bff);
        m.opacity = 0.85;
        ring.scale.setScalar(size * (1.5 + 0.15 * Math.sin(time * 8)));
      }
    },
  };
}
