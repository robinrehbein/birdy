package de.robinrehbein.birdy.engine.math

import de.robinrehbein.birdy.Golden
import de.robinrehbein.birdy.engine.assertClose
import de.robinrehbein.birdy.engine.d
import de.robinrehbein.birdy.engine.doubles
import de.robinrehbein.birdy.engine.get
import de.robinrehbein.birdy.engine.i
import de.robinrehbein.birdy.engine.list
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ColorTest {
    private val g = Golden.json("engine-math.json")

    @Test
    fun hslMatchesThree() {
        for (c in g["colorHsl"].list) {
            val col = Color.hex(c["hex"].i)
            val s = col.getHSL(space = ColorSpace.SRGB)
            // three.js round-trips through linear with the 0.41666 exponent, hence ~1e-5 noise.
            assertClose(c["srgb"].doubles(), doubleArrayOf(s.h, s.s, s.l), 5e-5, "getHSL srgb ${c["hex"].i}")
            val l = col.getHSL()
            assertClose(c["linear"].doubles(), doubleArrayOf(l.h, l.s, l.l), 1e-6, "getHSL linear")
            assertClose(c["linearRgb"].doubles(), doubleArrayOf(col.linearR.toDouble(), col.linearG.toDouble(), col.linearB.toDouble()), 1e-6, "linear rgb")
        }
        for (c in g["colorSetHsl"].list) {
            val h = c["hsl"].doubles()
            assertEquals(c["hex"].i, Color().setHSL(h[0], h[1], h[2]).hex(), "setHSL ${h.toList()}")
            // sRGB variant: the HSL->RGB result is stored as is.
            val srgb = Color().setHSL(h[0], h[1], h[2], ColorSpace.SRGB)
            val lin = Color().setHSL(h[0], h[1], h[2])
            assertClose(srgb.r.toDouble(), lin.linearR.toDouble(), 1e-5, "setHSL SRGB vs linear")
        }
    }

    @Test
    fun lerpAndMultiplyAreColourManaged() {
        for (c in g["colorLerp"].list) {
            val got = Color.hex(c["a"].i).lerp(Color.hex(c["b"].i), c["t"].d).hex()
            assertEquals(c["hex"].i, got, "lerp ${c["a"].i.toString(16)} ${c["b"].i.toString(16)} ${c["t"].d}")
        }
        for (c in g["colorMultiply"].list) {
            assertEquals(c["hex"].i, Color.hex(c["a"].i).multiply(Color.hex(c["b"].i)).hex(), "multiply")
        }
    }

    /** biomes.js paintRoad: fixture computed with the exact sRGB curve; three.js' 0.41666 exponent may differ by 1. */
    @Test
    fun biomeRoadBlendFixture() {
        val bb = Golden.json("world-biome-blend.json")
        val from = bb["roadFrom"].list.map { it.jsonPrimitive.content.toInt(16) }
        val to = bb["roadTo"].list.map { it.jsonPrimitive.content.toInt(16) }
        for (step in bb["roadSteps"].list) {
            val e = step["step"].d
            val road = step["road"].list.map { it.jsonPrimitive.content.toInt(16) }
            for (k in 0..2) {
                val got = Color.hex(from[k]).lerp(Color.hex(to[k]), e).hex()
                for (sh in intArrayOf(16, 8, 0)) {
                    val diff = abs(((got shr sh) and 0xFF) - ((road[k] shr sh) and 0xFF))
                    assertTrue(diff <= 1, "road step $e colour $k: ${got.toString(16)} vs ${road[k].toString(16)}")
                }
            }
        }
        for (c in bb["easing"].list) {
            assertClose(c["eased"].d, MathUtil.smoothstep(c["t"].d, 0.0, 1.0), 1e-9)
        }
    }

    /**
     * bird.js wingColor: branch and lightness agree with bird-fx-wing-color-rule.json; the blended wing
     * colour is checked against real three.js (engine-math.json), which lerps in linear space.
     */
    @Test
    fun wingColorRule() {
        val fixture = Golden.json("bird-fx-wing-color-rule.json")["perSkinResults"].list.associateBy { it["id"].jsonPrimitive.content }
        for (s in g["wingColor"].list) {
            val id = s["id"].jsonPrimitive.content
            val l = Color.hex(s["wingIn"].i).getHSL(space = ColorSpace.SRGB).l
            assertClose(s["lightness"].d, l, 5e-5, "lightness $id")
            val f = fixture.getValue(id)
            assertClose(f["lightness"].d, l, 1e-7, "fixture lightness $id")
            val own = l < 0.8
            assertEquals(f["branch"].jsonPrimitive.content == "ownWing", own, "branch $id")
            val wing = if (own) s["wingIn"].i else Color.hex(s["body"].i).lerp(Color.hex(s["belly"].i), 0.12).hex()
            assertEquals(s["wing"].i, wing, "wing $id")
        }
    }

    @Test
    fun srgbTransferRoundTrip() {
        for (i in 0..255) {
            val c = i / 255.0
            val back = Color.linearToSrgb(Color.srgbToLinear(c))
            assertTrue(abs(back - c) < 2e-5, "transfer $i")
        }
        assertEquals(0x543847, Color.hex(0x543847).hex())
        assertEquals("543847", Color.hex(0x543847).getHexString())
    }
}
