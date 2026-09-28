package de.robinrehbein.birdy

import de.robinrehbein.birdy.engine.math.MathUtil
import de.robinrehbein.birdy.engine.math.Mat4
import de.robinrehbein.birdy.engine.math.Vec3
import de.robinrehbein.birdy.engine.scene.PerspectiveCamera
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScaffoldTest {
    @Test
    fun goldenFixturesAreReadable() {
        val root = Golden.json("world-plant-timing.json").jsonObject
        assertTrue(root.isNotEmpty())
    }

    @Test
    fun smoothstepMatchesThreeJs() {
        assertEquals(0.0, MathUtil.smoothstep(1.0, 2.0, 3.0))
        assertEquals(0.5, MathUtil.smoothstep(2.5, 2.0, 3.0), 1e-12)
        assertEquals(1.0, MathUtil.smoothstep(4.0, 2.0, 3.0))
    }

    @Test
    fun cameraProjectsForwardPointToCenter() {
        val cam = PerspectiveCamera(60f, 0.45f, 0.1f, 400f)
        cam.updateProjection()
        cam.updateWorldMatrix(null)
        cam.updateView()
        val p = cam.project(Vec3(0f, 0f, -10f))
        assertEquals(0f, p.x, 1e-6f)
        assertEquals(0f, p.y, 1e-6f)
        assertTrue(p.z > -1f && p.z < 1f)
    }

    @Test
    fun inverseTimesMatrixIsIdentity() {
        val m = Mat4().perspective(60f, 1.3f, 0.1f, 100f)
        val r = Mat4().multiply(m, Mat4().invert(m))
        for (i in 0 until 16) assertEquals(if (i % 5 == 0) 1f else 0f, r.e[i], 1e-4f)
    }
}
