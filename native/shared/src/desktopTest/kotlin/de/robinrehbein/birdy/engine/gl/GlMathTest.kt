package de.robinrehbein.birdy.engine.gl

import de.robinrehbein.birdy.engine.math.Mat4
import de.robinrehbein.birdy.engine.math.Quat
import de.robinrehbein.birdy.engine.math.Vec3
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Pure-math parts of the renderer (no GL context needed). */
class GlMathTest {
    private fun near(a: Float, b: Float, eps: Float = 1e-4f) = assertTrue(abs(a - b) <= eps, "$a != $b")

    @Test
    fun normalMatrixIsInverseTranspose() {
        val m = Mat4().compose(Vec3(1f, 2f, 3f), Quat().setFromEulerXYZ(0.3f, -0.7f, 1.1f), Vec3(3f, 0.5f, 2f))
        val n = FloatArray(9)
        GlMath.normalMatrix(n, m.e)
        val inv = Mat4().invert(m).e
        for (c in 0 until 3) for (r in 0 until 3) near(inv[r * 4 + c], n[c * 3 + r])
    }

    @Test
    fun mulMatchesMat4() {
        val a = Mat4().compose(Vec3(1f, -2f, 0.5f), Quat().setFromEulerXYZ(0.1f, 0.2f, 0.3f), Vec3(1f, 2f, 3f))
        val b = Mat4().perspective(60f, 1.5f, 0.1f, 100f)
        val out = FloatArray(16)
        GlMath.mul(out, b.e, a.e)
        val ref = Mat4().multiply(b, a).e
        for (i in 0 until 16) near(ref[i], out[i])
    }

    @Test
    fun lookAtViewMapsEyeAndTarget() {
        val v = FloatArray(16)
        GlMath.lookAtView(v, 12f, 30f, 10f, 0f, 0f, -15f)
        val p = FloatArray(3)
        GlMath.transformPoint(p, v, 12f, 30f, 10f)
        near(0f, p[0]); near(0f, p[1]); near(0f, p[2])
        GlMath.transformPoint(p, v, 0f, 0f, -15f)
        val d = kotlin.math.sqrt(12f * 12f + 30f * 30f + 25f * 25f)
        near(0f, p[0], 1e-3f); near(0f, p[1], 1e-3f); near(-d, p[2], 1e-3f)
        // Straight down: degenerate up vector handled like three.js (no NaNs).
        GlMath.lookAtView(v, 0f, 20f, 0f, 0f, 0f, 0f)
        assertFalse(v.any { it.isNaN() })
    }

    @Test
    fun orthoMapsBoxToClipCube() {
        val o = FloatArray(16)
        GlMath.ortho(o, -18f, 18f, 40f, -30f, 1f, 90f)
        val p = FloatArray(3)
        GlMath.project(p, o, -18f, 40f, -1f)
        near(-1f, p[0]); near(1f, p[1]); near(-1f, p[2])
        GlMath.project(p, o, 18f, -30f, -90f)
        near(1f, p[0]); near(-1f, p[1]); near(1f, p[2])
    }

    @Test
    fun frustumSphereTest() {
        val proj = Mat4().perspective(60f, 1f, 0.1f, 100f)
        val f = Frustum()
        f.set(proj.e)
        assertTrue(f.intersectsSphere(0f, 0f, -10f, 1f))
        assertFalse(f.intersectsSphere(0f, 0f, 10f, 1f))
        assertTrue(f.intersectsSphere(0f, 0f, 0.5f, 1f))
        assertFalse(f.intersectsSphere(0f, 0f, -200f, 50f))
    }

    @Test
    fun dfgLutDecodesThreeJsHalfFloats() {
        assertEquals(1f, DfgLut.halfToFloat(0x3c00))
        assertEquals(0f, DfgLut.halfToFloat(0x0000))
        assertEquals(-2f, DfgLut.halfToFloat(0xc000))
        near(0.14709f, DfgLut.value(0, 0, 0), 1e-4f) // 0x30b5
        near(0.85205f, DfgLut.value(0, 0, 1), 1e-4f) // 0x3ad1
        assertEquals(DfgLut.SIZE * DfgLut.SIZE * 4, DfgLut.bytes().size)
        // Smooth, head-on: almost all energy in the single-scatter lobe.
        val (a, b) = DfgLut.value(0, 15, 0) to DfgLut.value(0, 15, 1)
        assertTrue(a + b > 0.95f && a + b <= 1.001f, "E(0, 1) = ${a + b}")
    }

    @Test
    fun defineNamesFollowFlagBits() {
        assertEquals(listOf("INSTANCED", "LIT", "OPAQUE"), GlRenderer.defineNames(GlRenderer.F_INSTANCED or GlRenderer.F_LIT or GlRenderer.F_OPAQUE))
        assertEquals(emptyList(), GlRenderer.defineNames(0))
    }
}
