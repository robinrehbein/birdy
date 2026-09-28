package de.robinrehbein.birdy.engine.math

import de.robinrehbein.birdy.Golden
import de.robinrehbein.birdy.engine.assertClose
import de.robinrehbein.birdy.engine.d
import de.robinrehbein.birdy.engine.doubles
import de.robinrehbein.birdy.engine.get
import de.robinrehbein.birdy.engine.list
import de.robinrehbein.birdy.engine.scene.Node
import de.robinrehbein.birdy.engine.scene.PerspectiveCamera
import de.robinrehbein.birdy.engine.toDoubles
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** engine.math and camera/lookAt against three.js r186 (docs/native/golden/engine-math.json). */
class MathGoldenTest {
    private val g = Golden.json("engine-math.json")

    private fun vec(a: DoubleArray) = Vec3(a[0].toFloat(), a[1].toFloat(), a[2].toFloat())
    private fun Quat.d() = doubleArrayOf(x.toDouble(), y.toDouble(), z.toDouble(), w.toDouble())
    private fun Vec3.d() = doubleArrayOf(x.toDouble(), y.toDouble(), z.toDouble())

    /** q and -q are the same rotation. */
    private fun assertSameRotation(expected: DoubleArray, q: Quat, msg: String, tol: Double = 2e-6) {
        val a = q.d()
        val sign = if (expected.indices.sumOf { expected[it] * a[it] } < 0) -1.0 else 1.0
        assertClose(expected, DoubleArray(4) { a[it] * sign }, tol, msg)
    }

    @Test
    fun eulerQuaternionRoundTrips() {
        for (c in g["euler"].list) {
            val e = c["euler"].doubles()
            val q = Quat().setFromEulerXYZ(e[0], e[1], e[2])
            assertClose(c["quat"].doubles(), q.d(), 1e-6, "quat $e")
            val back = q.toEulerXYZ()
            // Near gimbal lock several Euler triples are valid; compare the resulting rotation instead.
            // Float quaternions near gimbal lock lose ~sqrt(eps) of precision in the recovered angles.
            val gimbal = abs(abs(e[1]) - PI / 2) < 1e-3
            assertSameRotation(c["quat"].doubles(), Quat().setFromEuler(back), "euler back", if (gimbal) 1e-3 else 2e-6)
            if (abs(abs(e[1]) - PI / 2) > 1e-3) assertClose(c["eulerBack"].doubles(), back.d(), 2e-6, "eulerBack")
            val m = Mat4().makeRotationFromEulerXYZ(e[0].toFloat(), e[1].toFloat(), e[2].toFloat())
            assertClose(c["matrix"].doubles(), m.e.toDoubles(), 2e-6, "matrix")
            assertSameRotation(c["quatFromMatrix"].doubles(), Quat().setFromRotationMatrix(m), "fromMatrix")
        }
    }

    @Test
    fun composeInvertDecompose() {
        for (c in g["compose"].list) {
            val q = c["quat"].doubles()
            val quat = Quat(q[0].toFloat(), q[1].toFloat(), q[2].toFloat(), q[3].toFloat())
            val m = Mat4().compose(vec(c["position"].doubles()), quat, vec(c["scale"].doubles()))
            assertClose(c["matrix"].doubles(), m.e.toDoubles(), 1e-6, "compose")
            assertClose(c["inverse"].doubles(), Mat4().invert(m).e.toDoubles(), 1e-5, "invert")
            assertClose(c["determinant"].d, m.determinant().toDouble(), 1e-5, "det")
            assertClose(c["normalMatrix"].doubles(), Mat3().getNormalMatrix(m).e.toDoubles(), 1e-5, "normal")
            assertClose(c["extractRotation"].doubles(), Mat4().extractRotation(m).e.toDoubles(), 1e-6, "extractRotation")
            val p = Vec3(); val dq = Quat(); val s = Vec3()
            m.decompose(p, dq, s)
            val dec = c["decomposed"]
            assertClose(dec["position"].doubles(), p.d(), 1e-6, "dec p")
            assertClose(dec["scale"].doubles(), s.d(), 1e-6, "dec s")
            assertSameRotation(dec["quat"].doubles(), dq, "dec q")
            // Inverse really inverts.
            val id = Mat4().multiply(m, Mat4().invert(m))
            assertClose(Mat4().e.toDoubles(), id.e.toDoubles(), 1e-5, "m*inv")
        }
        val mm = g["multiply"]
        val a = Mat4().fromArray(mm["a"].doubles().map { it.toFloat() }.toFloatArray())
        val b = Mat4().makeRotationAxis(Vec3(1f, 1f, 0f).normalize(), 0.8f).setPosition(-1f, 0f, 4f)
        assertClose(mm["axisAngle"].doubles(), b.e.toDoubles(), 1e-6, "axis")
        assertClose(mm["ab"].doubles(), Mat4().multiply(a, b).e.toDoubles(), 1e-6, "ab")
        assertClose(mm["ab"].doubles(), a.clone().multiply(b).e.toDoubles(), 1e-6, "a.multiply(b)")
        assertClose(mm["ab"].doubles(), b.clone().premultiply(a).e.toDoubles(), 1e-6, "premultiply")
    }

    @Test
    fun lookAtMatrix() {
        for (c in g["lookAt"].list) {
            val m = Mat4().lookAt(vec(c["eye"].doubles()), vec(c["target"].doubles()), vec(c["up"].doubles()))
            assertClose(c["matrix"].doubles(), m.e.toDoubles(), 2e-6, "lookAt")
        }
    }

    @Test
    fun perspectiveAndViewOffset() {
        for (c in g["perspective"].list) {
            val cam = PerspectiveCamera(c["fov"].d.toFloat(), c["aspect"].d.toFloat(), c["near"].d.toFloat(), c["far"].d.toFloat())
            cam.updateProjection()
            assertClose(c["projection"].doubles(), cam.projectionMatrix.e.toDoubles(), 1e-5, "proj")
            assertClose(c["inverse"].doubles(), cam.projectionMatrixInverse.e.toDoubles(), 1e-5, "projInv")
            val m = Mat4().perspective(c["fov"].d.toFloat(), c["aspect"].d.toFloat(), c["near"].d.toFloat(), c["far"].d.toFloat())
            assertClose(c["projection"].doubles(), m.e.toDoubles(), 1e-5, "Mat4.perspective")
        }
        val v = g["viewOffset"]
        val cam = PerspectiveCamera(v["fov"].d.toFloat(), v["aspect"].d.toFloat(), v["near"].d.toFloat(), v["far"].d.toFloat())
        val o = v["view"].doubles().map { it.toFloat() }
        cam.setViewOffset(o[0], o[1], o[2], o[3], o[4], o[5])
        cam.updateProjection()
        assertClose(v["projection"].doubles(), cam.projectionMatrix.e.toDoubles(), 1e-5, "viewOffset")
    }

    @Test
    fun cameraLookAtProjectUnproject() {
        val c = g["camera"]
        val cam = PerspectiveCamera(c["fov"].d.toFloat(), c["aspect"].d.toFloat(), c["near"].d.toFloat(), c["far"].d.toFloat())
        cam.updateProjection()
        cam.position.set(vec(c["position"].doubles()))
        cam.lookAt(vec(c["lookAt"].doubles()))
        cam.updateWorldMatrix(null)
        cam.updateView()
        assertSameRotation(c["quaternion"].doubles(), cam.getQuaternion(), "cam quat")
        assertSameRotation(c["quaternion"].doubles(), cam.worldQuaternion, "cam worldQuat")
        assertClose(c["rotation"].doubles(), cam.rotation.d(), 2e-6, "cam euler")
        assertClose(c["viewMatrix"].doubles(), cam.viewMatrix.e.toDoubles(), 2e-5, "view")
        assertClose(c["worldDirection"].doubles(), cam.getWorldDirection(Vec3()).d(), 2e-6, "dir")
        for (p in c["project"].list) {
            val ndc = cam.project(vec(p["point"].doubles()))
            assertClose(p["ndc"].doubles(), ndc.d(), 2e-5, "project")
            val viaVec = vec(p["point"].doubles()).project(cam.viewMatrix, cam.projectionMatrix)
            assertClose(p["ndc"].doubles(), viaVec.d(), 2e-5, "Vec3.project")
        }
        for (p in c["unproject"].list) {
            assertClose(p["world"].doubles(), cam.unproject(vec(p["ndc"].doubles())).d(), 5e-4, "unproject")
        }
    }

    @Test
    fun objectLookAtWithRotatedParent() {
        val c = g["objectLookAt"]
        val parent = Node()
        val pp = c["parent"]
        parent.position.set(vec(pp["position"].doubles()))
        parent.rotation.set(vec(pp["rotation"].doubles()))
        parent.scale.set(vec(pp["scale"].doubles()))
        val o = Node()
        o.position.set(vec(c["position"].doubles()))
        parent.add(o)
        o.lookAt(vec(c["target"].doubles()))
        assertSameRotation(c["quaternion"].doubles(), o.getQuaternion(), "lookAt quat")
        assertClose(c["rotation"].doubles(), o.rotation.d(), 2e-6, "lookAt euler")
        assertClose(c["worldPosition"].doubles(), o.getWorldPosition().d(), 1e-6, "worldPos")
        assertClose(c["worldDirection"].doubles(), o.getWorldDirection(Vec3()).d(), 2e-6, "worldDir")

        val r = g["rotateOnAxis"]
        val n = Node()
        n.rotation.set(vec(r["start"].doubles()))
        n.rotateZ(0.4f)
        n.rotateX(-0.25f)
        assertSameRotation(r["quaternion"].doubles(), n.getQuaternion(), "rotateOnAxis")
        assertClose(r["rotation"].doubles(), n.rotation.d(), 2e-6, "rotateOnAxis euler")
        // Same result through the quaternion override path.
        val m = Node().apply { quaternionOverride = Quat().setFromEuler(vec(r["start"].doubles())) }
        m.rotateZ(0.4f).rotateX(-0.25f)
        assertSameRotation(r["quaternion"].doubles(), m.quaternionOverride!!, "override")
    }

    @Test
    fun quaternionOps() {
        for (c in g["unitVectors"].list) {
            val q = Quat().setFromUnitVectors(vec(c["from"].doubles()), vec(c["to"].doubles()))
            assertClose(c["quat"].doubles(), q.d(), 1e-6, "unitVectors")
            assertClose(c["upRotated"].doubles(), Vec3(0f, 1f, 0f).applyQuat(q).d(), 2e-6, "applyQuat")
        }
        for (c in g["slerp"].list) {
            val a = c["a"].doubles().let { Quat(it[0].toFloat(), it[1].toFloat(), it[2].toFloat(), it[3].toFloat()) }
            val b = c["b"].doubles().let { Quat(it[0].toFloat(), it[1].toFloat(), it[2].toFloat(), it[3].toFloat()) }
            assertClose(c["result"].doubles(), a.clone().slerp(b, c["t"].d.toFloat()).d(), 2e-6, "slerp")
            assertClose(c["product"].doubles(), a.clone().multiply(b).d(), 1e-6, "multiply")
            assertTrue(abs(c["angle"].d - a.angleTo(b)) < 2e-3, "angleTo") // acos near 1 amplifies float rounding
        }
        for (c in g["axisAngle"].list) {
            val q = Quat().setFromAxisAngle(vec(c["axis"].doubles()), c["angle"].d)
            assertClose(c["quat"].doubles(), q.d(), 1e-6, "axisAngle")
            assertClose(c["rotated"].doubles(), Vec3(1f, 2f, 3f).applyQuat(q).d(), 2e-6, "rotated")
        }
    }

    @Test
    fun sphericalCoords() {
        for ((i, c) in g["spherical"].list.withIndex()) {
            val v = Vec3().setFromSphericalCoords(c["radius"].d, c["phi"].d, c["theta"].d)
            assertClose(c["v"].doubles(), v.d(), 1e-6, "spherical $i")
        }
        // Cactus spike loop inputs recomputed in Kotlin match the fixture's phi/theta.
        for (i in 0 until 46) {
            val phi = acos(1 - (2 * (i + 0.5)) / 46)
            assertClose(g["spherical"][i]["phi"].d, phi, 1e-12)
            assertEquals(g["spherical"][i]["theta"].d, i * 2.399, 1e-12)
        }
    }
}
