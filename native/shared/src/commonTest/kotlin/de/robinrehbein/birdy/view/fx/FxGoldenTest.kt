package de.robinrehbein.birdy.view.fx

import de.robinrehbein.birdy.Golden
import de.robinrehbein.birdy.engine.assertClose
import de.robinrehbein.birdy.engine.d
import de.robinrehbein.birdy.engine.get
import de.robinrehbein.birdy.engine.i
import de.robinrehbein.birdy.engine.list
import de.robinrehbein.birdy.engine.math.Color
import de.robinrehbein.birdy.engine.math.Quat
import de.robinrehbein.birdy.game.PowerType
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FxGoldenTest {
    @Test
    fun auraMatchesGolden() {
        val g = Golden.json("bird-fx-aura.json")
        val c = Color()
        for (s in g["samples"].list) {
            val t = s["time"].d
            val star = Aura.look(PowerType.Star, t, 1.1, c)
            assertClose(s["star"]["opacity"].d, star.opacity, 1e-12, "star opacity t=$t")
            assertClose(s["star"]["scale"].d, star.scale, 1e-9, "star scale t=$t")
            // three.js stores the HSL result as linear components.
            assertClose(s["star"]["color"]["r"].d, c.linearR.toDouble(), 1e-5, "star r t=$t")
            assertClose(s["star"]["color"]["g"].d, c.linearG.toDouble(), 1e-5, "star g t=$t")
            assertClose(s["star"]["color"]["b"].d, c.linearB.toDouble(), 1e-5, "star b t=$t")
            val magnet = Aura.look(PowerType.Magnet, t, 1.1, c)
            assertEquals(s["magnet"]["colorHex"].i, c.getHex())
            assertClose(s["magnet"]["opacity"].d, magnet.opacity, 1e-9, "magnet opacity t=$t")
            assertClose(s["magnet"]["scale"].d, magnet.scale, 1e-9, "magnet scale t=$t")
            val mini = Aura.look(PowerType.Mini, t, 1.1, c)
            assertEquals(s["mini"]["colorHex"].i, c.getHex())
            assertClose(s["mini"]["opacity"].d, mini.opacity, 1e-12)
            assertClose(s["mini"]["scale"].d, mini.scale, 1e-9, "mini scale t=$t")
        }
    }

    @Test
    fun auraPrecedenceAndVisibility() {
        assertEquals(null, Aura.kindFor(false, 3.0, 3.0, 3.0))
        assertEquals(PowerType.Star, Aura.kindFor(true, 1.0, 1.0, 1.0))
        assertEquals(PowerType.Magnet, Aura.kindFor(true, 0.0, 1.0, 1.0))
        assertEquals(PowerType.Mini, Aura.kindFor(true, 0.0, 0.0, 1.0))
        assertEquals(null, Aura.kindFor(true, 0.0, 0.0, 0.0))
        val aura = Aura()
        val cam = Quat().setFromEulerXYZ(-0.3, 0.2, 0.0)
        aura.update(PowerType.Mini, 1.0, 2.0, 0.0, 0.0, 0.6, cam)
        assertTrue(aura.mesh.visible)
        assertEquals(2f, aura.mesh.position.y)
        assertTrue(aura.mesh.quaternionOverride!!.equals(cam))
        aura.update(null, 1.0, 2.0, 0.0, 0.0, 0.6, cam)
        assertFalse(aura.mesh.visible)
    }

    @Test
    fun impactMatchesGolden() {
        val g = Golden.json("bird-fx-impact.json")
        for (s in g["samples"].list) {
            val t = s["t"].d
            assertClose(s["scale"].d, Impact.scaleAt(t), 1e-6, "scale t=$t")
            assertClose(s["opacity"].d, Impact.opacityAt(t), 1e-6, "opacity t=$t")
        }
        // Frame-stepped: visible through t = 0.5, hidden after.
        val impact = Impact()
        impact.hit(1.0, 2.0, 3.0)
        assertEquals(0.5f, impact.mesh.scale.x)
        val cam = Quat()
        var frames = 0
        while (impact.mesh.visible) {
            impact.update(0.02, cam); frames++
        }
        // Hidden once t > 0.5 (t = 0.52 is the golden's first hidden sample; float summation may land on 0.5+eps).
        assertTrue(frames in 25..26, "frames $frames")
        val layers = g["layers"].list
        for ((k, l) in Impact.LAYERS.withIndex()) {
            assertEquals(layers[k]["colorHex"].i, l.color)
            assertClose(layers[k]["outerRadius"].d, l.outer)
            assertClose(layers[k]["innerRadius"].d, l.inner)
            assertClose(layers[k]["zOffset"].d, l.z)
        }
        assertEquals(11, impact.mesh.renderOrder)
        assertFalse(impact.mesh.material.depthTest)
    }

    @Test
    fun impactBillboardsAndSpins() {
        val impact = Impact()
        impact.hit(0.0, 0.0, 0.0)
        val cam = Quat().setFromEulerXYZ(-0.2, 0.0, 0.0)
        impact.update(0.1, cam)
        val expected = cam.clone().multiply(Quat().setFromAxisAngle(de.robinrehbein.birdy.engine.math.Vec3(0f, 0f, 1f), 0.15))
        val q = impact.mesh.quaternionOverride!!
        assertClose(expected.x.toDouble(), q.x.toDouble(), 1e-6)
        assertClose(expected.w.toDouble(), q.w.toDouble(), 1e-6)
    }

    @Test
    fun speedLinesMatchGolden() {
        val g = Golden.json("bird-fx-speedlines.json")
        assertEquals(28, g["count"].i)
        val lines = SpeedLines(Mulberry32(7))
        for (s in g["samples"].list) {
            val a = s["amount"].d
            assertClose(s["materialOpacity"].d, SpeedLines.opacityFor(a), 1e-9, "opacity a=$a")
            assertClose(s["perLineScaleZ"].d, SpeedLines.scaleZFor(a), 1e-9, "scale a=$a")
            lines.update(0.0, a)
            assertEquals(s["meshVisible"].jsonPrimitive.boolean, lines.mesh.visible, "visible a=$a")
        }
    }

    @Test
    fun speedLinesPlacementAndRecycle() {
        val lines = SpeedLines(Mulberry32(99))
        repeat(28) {
            val p = lines.linePosition(it)
            assertTrue(kotlin.math.abs(p[0]) in 4.2..9.2 && p[1] in 1.0..12.0 && p[2] in -60.0..0.0)
        }
        // Frozen while invisible.
        val before = lines.linePosition(0)[2]
        lines.update(10.0, 0.0)
        assertEquals(before, lines.linePosition(0)[2])
        // Moves 1.6x the world and recycles far ahead past z = 12.
        repeat(200) { lines.update(1.0, 1.0) }
        repeat(28) { assertTrue(lines.linePosition(it)[2] <= 12.0) }
    }

    @Test
    fun rushFormula() {
        assertEquals(0.0, SpeedLines.rush(false, false, 40.0, true))
        assertEquals(0.0, SpeedLines.rush(true, true, 40.0, true))
        assertEquals(0.0, SpeedLines.rush(true, false, 20.0, false))
        assertClose(0.5, SpeedLines.rush(true, false, 29.0, false))
        assertClose(0.6, SpeedLines.rush(true, false, 10.0, true))
        assertEquals(1.0, SpeedLines.rush(true, false, 36.0, true))
    }

    @Test
    fun particleTraceMatchesGolden() {
        val g = Golden.json("bird-fx-particles.json")
        val tr = g["traces"]
        val p = tr["emit1_params"]
        val colors = p["colors"].list.map { it.i }
        val particles = Particles(Mulberry32(g["seed"].i))
        particles.emit(
            0.0, 5.0, 0.0,
            EmitOptions(count = p["count"].i, colors = colors, speed = p["speed"].d, size = p["size"].d, life = p["life"].d, gravity = p["gravity"].d),
        )
        for (e in tr["afterEmit1"].list) {
            val slot = particles.slot(e["index"].i)
            checkSlot(e["particle"], slot, "emit ${e["index"].i}")
            val c = Color.hex(e["color"].i)
            assertClose(c.linearR.toDouble(), slot.color[0].toDouble(), 1e-6)
            assertClose(c.linearB.toDouble(), slot.color[2].toDouble(), 1e-6)
        }
        particles.update(tr["afterOneFrame_dt"].d, tr["afterOneFrame_dz"].d)
        for (e in tr["afterOneFrame"].list) checkSlot(e["particle"], particles.slot(e["index"].i), "update ${e["index"].i}")
        for (s in g["renderShrinkCurveSamples"].list) {
            assertClose(s["renderScaleFactor"].d, Particles.renderScale(1.0, s["k"].d), 1e-12)
        }
    }

    private fun checkSlot(j: kotlinx.serialization.json.JsonElement, s: Particles.Slot, what: String) {
        assertClose(j["life"].d, s.life, 1e-12, "$what life")
        assertClose(j["maxLife"].d, s.maxLife, 1e-12, "$what maxLife")
        assertClose(j["size"].d, s.size, 1e-12, "$what size")
        assertClose(j["gravity"].d, s.gravity, 1e-12)
        assertClose(j["drag"].d, s.drag, 1e-12)
        assertClose(j["spin"].d, s.spin, 1e-12, "$what spin")
        for ((k, axis) in listOf("x", "y", "z").withIndex()) {
            assertClose(j["pos"][axis].d, s.pos[k], 1e-12, "$what pos.$axis")
            assertClose(j["vel"][axis].d, s.vel[k], 1e-12, "$what vel.$axis")
        }
    }

    @Test
    fun particlePoolIsRoundRobin() {
        val particles = Particles(Mulberry32(1), max = 8)
        particles.emit(0.0, 0.0, 0.0, EmitOptions(count = 6, life = 10.0))
        particles.emit(1.0, 0.0, 0.0, EmitOptions(count = 4, life = 10.0))
        // Slots 0 and 1 were overwritten although still alive.
        assertEquals(1.0, particles.slot(0).pos[0])
        assertEquals(1.0, particles.slot(1).pos[0])
        assertEquals(0.0, particles.slot(2).pos[0])
        assertEquals(8, particles.alive)
        particles.clear()
        assertEquals(0, particles.alive)
    }

    @Test
    fun particleDirectionsAreCubeNormalized() {
        val particles = Particles(Mulberry32(5), max = 50)
        particles.emit(0.0, 0.0, 0.0, EmitOptions(count = 50, speed = 1.0, spread = 1.0))
        repeat(50) {
            val v = particles.slot(it).vel
            val len = kotlin.math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
            assertTrue(len in 0.4 - 1e-9..1.0 + 1e-9, "speed jitter $len")
        }
    }

    @Test
    fun pickupAnimationMatchesGolden() {
        val g = Golden.json("bird-fx-powerup-pickup.json")
        for (s in g["animatePickupSamples"].list) {
            val t = s["time"].d
            assertClose(s["iconRotationY"].d, PickupVisual.iconRotationY(t), 1e-12)
            assertClose(s["iconPositionY"].d, PickupVisual.iconBobY(t), 1e-12)
            assertClose(s["rainbowColorHSL"]["h"].d, PickupVisual.rainbowHue(t), 1e-12)
        }
        for (s in g["worldShrinkSamples"].list) {
            val k = PickupVisual.shrink(s["z"].d)
            assertClose(s["k"].d, k, 1e-12)
            assertEquals(s["visible"].jsonPrimitive.boolean, k > 0)
        }
        assertClose(g["iconScale"].d, 1.7)
        val colors = g["colorHex"]
        for (type in PowerType.entries) assertEquals(colors[type.id].i, type.color)
    }

    @Test
    fun pickupVisualAnimates() {
        val star = PickupVisual(PowerType.Star)
        val cam = Quat().setFromEulerXYZ(-0.4, 0.0, 0.0)
        star.animate(0.5, cam)
        assertClose(1.25, star.icon.rotation.y.toDouble(), 1e-6)
        assertClose(PickupVisual.iconBobY(0.5), star.icon.position.y.toDouble(), 1e-6)
        assertTrue(star.rim.quaternionOverride!!.equals(cam))
        // Star icon hue-cycles: HSL(0.25, 1, 0.55) in the linear working space.
        val mat = (star.icon as de.robinrehbein.birdy.engine.scene.Mesh).material
        val expected = Color().setHSL(0.25, 1.0, 0.55)
        assertEquals(expected.getHex(), mat.color.getHex())
        assertEquals(1.7f, star.icon.scale.x)
        // Magnet and mushroom are groups without a rainbow material.
        val magnet = PickupVisual(PowerType.Magnet)
        magnet.animate(1.0, null)
        assertClose(2.5, magnet.icon.rotation.y.toDouble(), 1e-6)
    }
}
