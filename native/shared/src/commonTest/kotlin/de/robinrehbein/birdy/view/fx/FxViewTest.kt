package de.robinrehbein.birdy.view.fx

import de.robinrehbein.birdy.engine.assertClose
import de.robinrehbein.birdy.engine.scene.PerspectiveCamera
import de.robinrehbein.birdy.engine.scene.Scene
import de.robinrehbein.birdy.game.DeathCause
import de.robinrehbein.birdy.game.GameEvent
import de.robinrehbein.birdy.game.GameMode
import de.robinrehbein.birdy.game.GameSimulation
import de.robinrehbein.birdy.game.GateRow
import de.robinrehbein.birdy.game.Menu
import de.robinrehbein.birdy.game.PowerType
import de.robinrehbein.birdy.game.sim.FakeProgress
import de.robinrehbein.birdy.meta.Catalog
import de.robinrehbein.birdy.view.FrameInfo
import de.robinrehbein.birdy.view.bird.BirdView
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FxViewTest {
    private val sim = GameSimulation(FakeProgress(), Random(3)) {}
    private val bird = BirdView()
    private val fx = FxView(bird, Mulberry32(11))
    private val camera = PerspectiveCamera()
    private val scene = Scene()

    init {
        bird.attach(scene)
        fx.attach(scene)
    }

    private fun frame(dt: Double = 1.0 / 60) = FrameInfo(dt, sim.state.time, 0.0, sim, camera)

    @Test
    fun deathFiresFeathersAndBonk() {
        val s = sim.state
        s.mode = GameMode.Dead
        s.x = 3.0; s.y = 4.0
        fx.onEvent(GameEvent.Died(DeathCause.Blocked), sim)
        assertEquals(36, fx.particles.alive)
        assertTrue(fx.impact.mesh.visible)
        assertEquals(3.8f, fx.impact.mesh.position.x)
        assertEquals(4.45f, fx.impact.mesh.position.y)
        assertEquals(-0.6f, fx.impact.mesh.position.z)
        // Feathers use body/body/belly/wing/white of the worn skin.
        val skin = bird.skin
        val allowed = listOf(skin.body, skin.belly, skin.wing, 0xffffff).map { de.robinrehbein.birdy.engine.math.Color.hex(it).linearR }
        repeat(36) { i -> assertTrue(allowed.any { kotlin.math.abs(it - fx.particles.slot(i).color[0]) < 1e-6 }) }
        // Hit-stop: the frame after death only advances the bonk.
        s.hitStop = 0.14
        fx.update(frame())
        val before = fx.particles.slot(0).life
        fx.update(frame())
        assertEquals(before, fx.particles.slot(0).life)
        assertTrue(fx.impact.t > 0.0)
        // Reset clears.
        fx.onEvent(GameEvent.RunStarted(false), sim)
        assertEquals(0, fx.particles.alive)
        assertFalse(fx.impact.mesh.visible)
    }

    @Test
    fun trailEmitsFiftyPerSecondWhilePlaying() {
        val s = sim.state
        s.mode = GameMode.Playing
        s.hold = false
        s.speed = 10.0
        bird.setTrail(Catalog.trails.first { it.id == "sparkle" })
        repeat(60) { fx.update(frame()) }
        assertTrue(fx.particles.alive in 20..50, "alive ${fx.particles.alive}")
        // No trail while holding or with the "none" trail.
        fx.onEvent(GameEvent.RunStarted(false), sim)
        s.hold = true
        repeat(10) { fx.update(frame()) }
        assertEquals(0, fx.particles.alive)
        s.hold = false
        bird.setTrail(Catalog.trails[0])
        repeat(10) { fx.update(frame()) }
        assertEquals(0, fx.particles.alive)
    }

    @Test
    fun shopPreviewTrailDriftsBackwards() {
        val s = sim.state
        s.mode = GameMode.Ready
        s.menu = Menu.Shop
        fx.previewTrail = Catalog.trails.first { it.id == "neon" }
        fx.update(frame(0.1))
        assertEquals(5, fx.particles.alive)
        // Drift 6 on z: with neon speed 1.4 the z velocity stays positive-biased.
        var sumVz = 0.0
        repeat(5) { sumVz += fx.particles.slot(it).vel[2] }
        assertTrue(sumVz / 5 > 3.0)
        s.menu = Menu.Start
        fx.onEvent(GameEvent.RunStarted(false), sim)
        fx.update(frame(0.1))
        assertEquals(0, fx.particles.alive)
    }

    @Test
    fun auraFollowsPowerPrecedence() {
        val s = sim.state
        s.mode = GameMode.Playing
        s.power[PowerType.Mini.ordinal] = 2.0
        s.power[PowerType.Magnet.ordinal] = 2.0
        fx.update(frame())
        assertTrue(fx.aura.mesh.visible)
        assertEquals(0xff4a4a, fx.aura.mesh.material.color.getHex())
        s.mode = GameMode.Dead
        fx.update(frame())
        assertFalse(fx.aura.mesh.visible)
    }

    @Test
    fun starEmitsRainbowTrailAndSpeedLines() {
        val s = sim.state
        s.mode = GameMode.Playing
        s.speed = 10.0
        s.power[PowerType.Star.ordinal] = 2.0
        fx.update(frame())
        assertEquals(2, fx.particles.alive)
        assertTrue(fx.speedLines.mesh.visible)
        assertClose(0.7 * 0.6, fx.speedLines.opacity.toDouble(), 1e-6)
    }

    @Test
    fun blobShadowScalesWithHeightAndBird() {
        assertClose(1.0, BlobShadow.scaleFor(0.0, 1.1))
        assertClose(0.9, BlobShadow.scaleFor(5.0, 1.1), 1e-9)
        assertClose(0.5, BlobShadow.scaleFor(20.0, 1.1))
        assertClose(0.9 * 0.6 / 1.1, BlobShadow.scaleFor(5.0, 0.6), 1e-9)
        val s = sim.state
        s.x = -3.0; s.y = 5.0
        s.pose.scaleX = 1.1
        fx.update(frame())
        assertEquals(-3f, fx.blob.mesh.position.x)
        assertClose(0.9, fx.blob.mesh.scale.x.toDouble(), 1e-6)
        assertClose(0.03, fx.blob.mesh.position.y.toDouble(), 1e-6)
    }

    @Test
    fun heightMarkerTelegraphsTheNextRow() {
        val gate = GateRow().apply {
            z = -20.0
            lanes[1].hitLow = 3.0
            lanes[1].hitHigh = 7.0
        }
        val m = HeightMarker()
        val cam = de.robinrehbein.birdy.engine.math.Quat()
        m.update(gate, playing = true, star = false, lane = 1, birdY = 5.0, r = 0.5, tutorial = false, time = 0.0, cameraQuat = cam)
        assertTrue(m.mesh.visible)
        assertEquals(HeightMarker.OK, m.mesh.material.color.getHex())
        assertClose(0.9, m.mesh.material.opacity.toDouble(), 1e-6)
        assertClose(-20 + 1.1 + 0.3, m.mesh.position.z.toDouble(), 1e-5)
        assertClose(20.0 / 14, m.mesh.scale.x.toDouble(), 1e-6)
        // The 0.8 radius factor: 6.6 + 0.4 = 7.0 is not below hitHigh.
        m.update(gate, true, false, 1, 6.6, 0.5, false, 0.0, cam)
        assertEquals(HeightMarker.BAD, m.mesh.material.color.getHex())
        m.update(gate, true, false, 1, 6.59, 0.5, false, 0.0, cam)
        assertEquals(HeightMarker.OK, m.mesh.material.color.getHex())
        gate.lanes[1].blocked = true
        m.update(gate, true, false, 1, 5.0, 0.5, false, 0.0, cam)
        assertEquals(HeightMarker.BAD, m.mesh.material.color.getHex())
        // Hidden with the star, when too far, or not playing.
        m.update(gate, true, true, 1, 5.0, 0.5, false, 0.0, cam)
        assertFalse(m.mesh.visible)
        gate.z = -50.0
        m.update(gate, true, false, 1, 5.0, 0.5, false, 0.0, cam)
        assertFalse(m.mesh.visible)
        gate.z = -40.0
        m.update(gate, true, false, 1, 5.0, 0.5, false, 0.0, cam)
        assertClose(0.9 * 0.5, m.mesh.material.opacity.toDouble(), 1e-6)
        // Tutorial pulse.
        assertClose(1.6 + 0.25 * kotlin.math.sin(8.0), HeightMarker.scaleFor(-10.0, true, 1.0), 1e-12)
        assertTrue(m.mesh.renderOrder == 10 && !m.mesh.material.depthTest)
    }

    @Test
    fun pickupsMirrorSimulation() {
        val s = sim.state
        s.mode = GameMode.Playing
        val pu = sim.pickups.first { it.type == PowerType.Magnet }
        pu.active = true; pu.visible = true
        pu.x = 3.0; pu.y = 6.0; pu.z = -30.0; pu.scale = 1.0
        s.time = 1.0
        fx.update(frame())
        val node = scene.children.first { it.name == "pickup-magnet" && it.visible }
        assertEquals(-30f, node.position.z)
        assertEquals(6, scene.children.count { it.name.startsWith("pickup-") })
        pu.visible = false
        fx.update(frame())
        assertFalse(node.visible)
    }
}
