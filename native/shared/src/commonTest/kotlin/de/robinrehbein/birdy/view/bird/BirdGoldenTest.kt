package de.robinrehbein.birdy.view.bird

import de.robinrehbein.birdy.Golden
import de.robinrehbein.birdy.engine.assertClose
import de.robinrehbein.birdy.engine.d
import de.robinrehbein.birdy.engine.get
import de.robinrehbein.birdy.engine.i
import de.robinrehbein.birdy.engine.list
import de.robinrehbein.birdy.engine.math.Color
import de.robinrehbein.birdy.engine.scene.Mesh
import de.robinrehbein.birdy.engine.scene.PerspectiveCamera
import de.robinrehbein.birdy.engine.scene.Scene
import de.robinrehbein.birdy.engine.scene.StandardMaterial
import de.robinrehbein.birdy.game.GameMode
import de.robinrehbein.birdy.game.PowerType
import de.robinrehbein.birdy.game.sim.FakeProgress
import de.robinrehbein.birdy.game.sim.squashAmount
import de.robinrehbein.birdy.game.sim.updateBirdVisual
import de.robinrehbein.birdy.meta.Catalog
import de.robinrehbein.birdy.meta.Kind
import de.robinrehbein.birdy.view.FrameInfo
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BirdGoldenTest {
    @Test
    fun wingColorRuleForAllSkins() {
        val g = Golden.json("bird-fx-wing-color-rule.json")
        val rows = g["perSkinResults"].list
        assertEquals(Catalog.skins.size, rows.size)
        for (r in rows) {
            val id = r["id"].jsonPrimitive.content
            val skin = Catalog.skins.first { it.id == id }
            val wc = wingColor(skin)
            val tinted = r["branch"].jsonPrimitive.content == "bodyTinted"
            assertEquals(tinted, wc.wing != skin.wing || wc.cover != skin.cover, "$id branch")
            assertEquals(r["cover"].i, wc.cover, "$id cover")
            if (tinted) {
                // The generator lerps the sRGB bytes; bird.js uses three.js Color.lerp, which (with
                // ColorManagement on) lerps linear components. We follow three.js, e.g. sunny ->
                // 0xf8d65d (three r186) where the fixture says 0xf8d54e.
                assertEquals(Color.hex(skin.body).lerp(Color.hex(skin.belly), 0.12).getHex(), wc.wing, "$id wing")
                assertEquals(srgbLerp(skin.body, skin.belly, 0.12), r["wing"].i, "$id fixture formula")
            } else {
                assertEquals(r["wing"].i, wc.wing, "$id wing")
            }
            // The rig applies it to the shared wing/cover materials.
            val rig = BirdRig()
            rig.setSkin(skin)
            assertEquals(wc.wing, rig.material("wing").color.getHex(), "$id wing material")
            assertEquals(wc.cover, rig.material("cover").color.getHex(), "$id cover material")
        }
    }

    @Test
    fun sunnyWingMatchesThreeJs() {
        assertEquals(0xf8d65d, wingColor(Catalog.skins[0]).wing)
    }

    private fun srgbLerp(a: Int, b: Int, t: Double): Int {
        fun ch(h: Int, s: Int) = (h shr s) and 0xff
        fun mix(s: Int): Int = kotlin.math.floor((ch(a, s) / 255.0 + (ch(b, s) / 255.0 - ch(a, s) / 255.0) * t) * 255 + 0.5).toInt()
        return (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }

    @Test
    fun animateWingsMatchesGolden() {
        val g = Golden.json("bird-fx-wing-animate.json")
        val rig = BirdRig()
        for (s in g["phaseSamples"].list) checkPose(rig, s)
        for (s in g["simulation60fps_flapAtFrame0"].list) checkPose(rig, s)
    }

    private fun checkPose(rig: BirdRig, s: kotlinx.serialization.json.JsonElement) {
        rig.animateWings(s["phase"].d)
        val w = rig.wingRotations()
        val a = s["wingRotZ_perSide"].d
        assertClose(-a, w[0].toDouble(), 2e-6, "left wing")
        assertClose(a, w[1].toDouble(), 2e-6, "right wing")
        assertClose(s["propellerRotY"].d, rig.propellerRotY.toDouble(), 1e-5, "propeller")
        assertClose(s["haloRingY"].d, rig.haloRingY.toDouble(), 1e-6, "halo")
    }

    @Test
    fun wingSpeedRelaxationFeedsTheRig() {
        // The simulation integrates wing speed/phase; the view animates from state.wingPhase.
        val g = Golden.json("bird-fx-wing-animate.json")
        val h = harness()
        val s = h.sim.state
        s.wingSpeed = 38.0
        s.wingPhase = 0.0
        for (row in g["simulation60fps_flapAtFrame0"].list) {
            h.sim.updateBirdVisual(DT)
            h.view.update(h.frame(DT))
            assertClose(row["wingSpeed"].d, s.wingSpeed, 1e-5, "wingSpeed")
            assertClose(row["phase"].d, s.wingPhase, 1e-5, "phase")
            assertClose(row["wingRotZ_perSide"].d, h.view.rig.wingRotations()[1].toDouble(), 2e-5, "rot")
        }
    }

    @Test
    fun squashStretchMatchesGolden() {
        val g = Golden.json("bird-fx-squash-stretch.json")
        val h = harness()
        val s = h.sim.state
        s.mode = GameMode.Ready
        val frames = g["frames"].list
        // Frame 0 is the flap itself (squash = 1, not decayed yet).
        assertClose(frames[0]["q"].d, squashAmount(1.0), 1e-6)
        s.squash = 1.0
        for (f in frames.drop(1)) {
            h.sim.updateBirdVisual(DT)
            h.view.update(h.frame(DT))
            assertClose(f["squash"].d, s.squash, 1e-6, "squash")
            val sc = h.view.rig.root.scale
            assertClose(f["scale"]["x"].d, sc.x.toDouble(), 1e-5, "x frame ${f["frame"].i}")
            assertClose(f["scale"]["y"].d, sc.y.toDouble(), 1e-5, "y")
            assertClose(f["scale"]["z"].d, sc.z.toDouble(), 1e-5, "z")
        }
    }

    @Test
    fun viewCopiesPoseBlinkAndDeathSplat() {
        val h = harness()
        val s = h.sim.state
        s.mode = GameMode.Playing
        s.x = 3.0; s.y = 6.0
        s.pose.rotX = 0.3; s.pose.rotZ = -0.2
        s.pose.scaleX = 1.43; s.pose.scaleY = 0.792; s.pose.scaleZ = 1.375
        s.pose.visible = false
        h.view.update(h.frame(0.0))
        val g = h.view.rig.root
        assertEquals(3f, g.position.x)
        assertEquals(6f, g.position.y)
        assertEquals(0.3f, g.rotation.x)
        assertEquals(-0.2f, g.rotation.z)
        assertEquals(0.792f, g.scale.y)
        assertFalse(g.visible)
        // Over: wings freeze.
        s.mode = GameMode.Over
        s.wingPhase = 1.0
        h.view.rig.animateWings(0.0)
        h.view.update(h.frame(0.0))
        assertEquals(0f, h.view.rig.wingRotations()[1])
    }

    @Test
    fun rainbowGlowSkipsHalo() {
        val h = harness()
        val s = h.sim.state
        s.mode = GameMode.Playing
        s.time = 0.2
        s.power[PowerType.Star.ordinal] = 3.0
        h.view.update(h.frame(0.0))
        val expected = Color().setHSL(0.3, 1.0, 0.5).multiplyScalar(0.7f)
        val rig = h.view.rig
        assertEquals(expected.getHex(), rig.material("body").emissive.getHex())
        assertTrue(rig.glowMaterials.isNotEmpty())
        assertFalse(rig.haloMaterial in rig.glowMaterials)
        assertEquals(0xffc629, rig.haloMaterial.emissive.getHex())
        // Star over: glow off, halo untouched.
        s.power[PowerType.Star.ordinal] = 0.0
        h.view.update(h.frame(0.0))
        assertEquals(0, rig.material("body").emissive.getHex())
        assertEquals(0xffc629, rig.haloMaterial.emissive.getHex())
        // Every other bird mesh material is in the glow set.
        var meshes = 0
        rig.root.traverse { n ->
            val m = (n as? Mesh)?.material as? StandardMaterial ?: return@traverse
            meshes++
            if (m !== rig.haloMaterial) assertTrue(m in rig.glowMaterials)
        }
        assertTrue(meshes > 40)
    }

    @Test
    fun lookIsRadioPerCategory() {
        val rig = BirdRig()
        rig.setLook(BirdLook(pattern = "spots", hat = "crown", eyes = "shades", beak = "toucan"))
        for (kind in BirdLook.WORKSHOP_KINDS) {
            val parts = rig.part { it.of(kind) }
            val visible = parts.filterValues { it.visible }.keys
            assertEquals(setOf(rig.look.idOf(kind)), visible, "$kind")
            // Every catalog id of this kind has a part.
            for (item in Catalog.items(kind)) assertNotNull(parts[item.id], "${kind.id}:${item.id}")
        }
        rig.setLook(BirdLook())
        assertTrue(rig.part { it.beak }.getValue("round").visible)
        assertFalse(rig.part { it.hat }.getValue("crown").visible)
    }

    @Test
    fun skinMaterialsAndFx() {
        val rig = BirdRig()
        val gold = Catalog.skins.first { it.metal }
        rig.setSkin(gold)
        assertEquals(0.55f, rig.material("body").metalness)
        assertEquals(0f, rig.material("beak").metalness)
        assertEquals(0.3f, rig.material("beak").roughness)
        assertEquals(0.3f, rig.material("tail").roughness)
        val sunny = Catalog.skins[0]
        rig.setSkin(sunny)
        assertEquals(0.45f, rig.material("tail").roughness)
        assertEquals(0.55f, rig.material("belly").roughness)
        assertEquals(0f, rig.material("body").metalness)
        assertEquals(sunny.body, rig.material("body").color.getHex())
        assertNull(rig.material("body").patch)
        for (skin in Catalog.skins.filter { it.fx != null }) {
            rig.setSkin(skin)
            assertEquals(SkinFx.fxId(skin.fx), rig.skinFx.active, skin.id)
            assertTrue(SkinFx.fxId(skin.fx) > 0, "${skin.fx} is a known effect")
            for (key in listOf("body", "belly", "wing", "cover", "tail")) assertNotNull(rig.material(key).patch, "$key patched")
            assertNull(rig.material("beak").patch)
        }
        rig.setSkin(sunny)
        assertEquals(0, rig.skinFx.active)
        assertNull(rig.material("wing").patch)
        assertEquals(7, Catalog.skins.count { it.fx != null })
    }

    @Test
    fun patternColoursFollowSkin() {
        val rig = BirdRig()
        val skin = Catalog.skins.first { it.id == "parrot" }
        rig.setSkin(skin)
        rig.setLook(BirdLook(pattern = "stripes"))
        val stripe = rig.part { it.pattern }.getValue("stripes").children[0] as Mesh
        assertEquals(skin.tail, stripe.material.color.getHex())
        rig.setLook(BirdLook(pattern = "spots"))
        val spot = rig.part { it.pattern }.getValue("spots").children[0] as Mesh
        assertEquals(skin.belly, spot.material.color.getHex())
        rig.setSkin(skin.copy(belly = skin.body))
        assertEquals(0xffffff, spot.material.color.getHex())
    }

    @Test
    fun skinFxTimeWraps() {
        val fx = SkinFx()
        fx.tick(1234.5)
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6, 7), SkinFx.NAMES.indices.toList())
        assertEquals(0, SkinFx.fxId("nope"))
        assertEquals(0, SkinFx.fxId(null))
        assertEquals(7, SkinFx.fxId("toadstool"))
    }

    private class Harness(val sim: de.robinrehbein.birdy.game.GameSimulation, val view: BirdView) {
        val camera = PerspectiveCamera()
        fun frame(dt: Double) = FrameInfo(dt, sim.state.time, 0.0, sim, camera)
    }

    private fun harness(): Harness {
        val sim = de.robinrehbein.birdy.game.GameSimulation(FakeProgress(), kotlin.random.Random(1)) {}
        val view = BirdView()
        view.attach(Scene())
        return Harness(sim, view)
    }

    private companion object {
        const val DT = 1.0 / 60
    }
}
