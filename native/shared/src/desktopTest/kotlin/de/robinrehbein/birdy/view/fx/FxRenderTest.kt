package de.robinrehbein.birdy.view.fx

import de.robinrehbein.birdy.engine.math.Quat
import de.robinrehbein.birdy.engine.scene.PerspectiveCamera
import de.robinrehbein.birdy.game.PowerType
import de.robinrehbein.birdy.meta.Catalog
import de.robinrehbein.birdy.view.bird.BirdRig
import de.robinrehbein.birdy.view.bird.BirdShots
import de.robinrehbein.birdy.view.bird.BirdShots.Companion.coverage
import de.robinrehbein.birdy.view.bird.BirdShots.Companion.save
import de.robinrehbein.birdy.view.bird.BirdShots.Companion.sheet
import kotlin.test.Test
import kotlin.test.assertTrue

/** Renders pickups, aura, bonk and particle bursts headless; PNGs land in native/build/birdtest. */
class FxRenderTest {
    private val bg = 0x9fd8f0

    /** Game-like chase camera behind and above the bird, looking down the track (-Z). */
    private fun chaseCamera(): PerspectiveCamera = PerspectiveCamera(60f, 1f, 0.1f, 400f).apply {
        position.set(0f, 7f, 9f)
        lookAt(0f, 4.2f, -6f)
        updateProjection()
    }

    private fun cam(c: PerspectiveCamera) = c.getQuaternion(Quat())

    @Test
    fun pickups(): Unit = BirdShots(320).use { shots ->
        val camera = PerspectiveCamera(40f, 1f, 0.1f, 100f).apply {
            position.set(0f, 0.4f, 6.5f); lookAt(0f, 0f, 0f); updateProjection()
        }
        val tiles = PowerType.entries.map { type ->
            val scene = shots.studio(bg)
            val p = PickupVisual(type)
            p.group.visible = true
            p.animate(0.35, cam(camera))
            scene.add(p.group)
            shots.render(scene, camera).also {
                save("pickup-${type.id}", it, shots.size)
                assertTrue(coverage(it, bg) > 0.2, "${type.id} bubble visible")
            }
        }
        sheet("pickups", tiles, shots.size, 3)
    }

    @Test
    fun auraAndBonk(): Unit = BirdShots(320).use { shots ->
        val camera = chaseCamera()
        val tiles = PowerType.entries.map { type ->
            val scene = shots.studio(bg)
            val rig = BirdRig().apply { root.position.set(0f, 5f, 0f); root.scale.setScalar(1.1f); animateWings(1.2) }
            val aura = Aura()
            val size = if (type == PowerType.Mini) 0.6 else 1.1
            rig.root.scale.setScalar(size.toFloat())
            aura.update(type, 0.0, 5.0, 0.0, 0.3, size, cam(camera))
            scene.add(rig.root, aura.mesh)
            shots.render(scene, camera).also { save("aura-${type.id}", it, shots.size) }
        }
        sheet("aura", tiles, shots.size, 3)

        // Impact: the death splat plus the bonk 0.1 s after the hit (pipe-top offset).
        val scene = shots.studio(bg)
        val rig = BirdRig().apply { root.position.set(0f, 5f, 0f); root.scale.set(1.43f, 0.792f, 1.375f); animateWings(1.2) }
        val impact = Impact()
        impact.hit(0.25, 5.8, 0.0)
        impact.update(0.1, cam(camera))
        scene.add(rig.root, impact.mesh)
        val px = shots.render(scene, camera)
        save("impact", px, shots.size)
        // The bonk's yellow core is on top of everything.
        assertTrue(px.count { ((it shr 16) and 0xff) > 230 && ((it shr 8) and 0xff) > 200 && (it and 0xff) < 120 } > 200, "yellow core")
    }

    @Test
    fun trailAndDeathBursts(): Unit = BirdShots(320).use { shots ->
        val camera = chaseCamera()
        val tiles = ArrayList<IntArray>()
        for (id in listOf("sparkle", "confetti", "rainbow", "fire")) {
            val trail = Catalog.trails.first { it.id == id }
            val scene = shots.studio(bg)
            val rig = BirdRig().apply { root.position.set(0f, 5f, 0f); root.scale.setScalar(1.1f); animateWings(1.2) }
            val particles = Particles(Mulberry32(42))
            // One second of a run at speed 14: 50 particles/s behind the bird, scrolling with the world.
            val dt = 1.0 / 60
            var acc = 0.0
            repeat(60) {
                acc += dt * 50
                while (acc >= 1) { acc -= 1; particles.emit(0.0, 4.9, 0.45, Bursts.trail(trail, 0.0)) }
                particles.update(dt, 14 * dt)
            }
            scene.add(rig.root, particles.mesh)
            tiles += shots.render(scene, camera).also { save("trail-$id", it, shots.size) }
        }
        // Death feathers 0.15 s after the hit.
        val scene = shots.studio(bg)
        val particles = Particles(Mulberry32(7))
        particles.emit(0.0, 5.0, 0.0, Bursts.death(Catalog.skins[0]))
        repeat(9) { particles.update(1.0 / 60, 0.0) }
        scene.add(particles.mesh)
        tiles += shots.render(scene, camera).also {
            save("burst-death", it, shots.size)
            assertTrue(coverage(it, bg) > 0.005, "feathers visible")
        }
        sheet("bursts", tiles, shots.size, 5)
    }

    @Test
    fun speedLinesAndGroundCues(): Unit = BirdShots(320).use { shots ->
        val camera = chaseCamera()
        val scene = shots.studio(bg)
        val lines = SpeedLines(Mulberry32(3))
        repeat(30) { lines.update(0.5, 1.0) }
        val blob = BlobShadow()
        blob.update(0.0, 5.0, 1.1)
        val rig = BirdRig().apply { root.position.set(0f, 5f, 0f); root.scale.setScalar(1.1f); animateWings(1.2) }
        val gate = de.robinrehbein.birdy.game.GateRow().apply {
            z = -24.0
            lanes[1].hitLow = 3.0
            lanes[1].hitHigh = 7.5
        }
        val marker = HeightMarker()
        marker.update(gate, true, false, 1, 5.0, 0.5, false, 0.0, cam(camera))
        scene.add(lines.mesh, blob.mesh, rig.root, marker.mesh)
        val px = shots.render(scene, camera)
        save("speedlines-blob-marker", px, shots.size)
        // Green marker ring is drawn.
        assertTrue(px.count { ((it shr 8) and 0xff) > 200 && ((it shr 16) and 0xff) < 190 && (it and 0xff) < 150 } > 20, "green marker")
    }
}
