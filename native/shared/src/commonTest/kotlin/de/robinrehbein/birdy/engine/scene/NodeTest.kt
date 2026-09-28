package de.robinrehbein.birdy.engine.scene

import de.robinrehbein.birdy.engine.math.Quat
import de.robinrehbein.birdy.engine.math.Vec3
import de.robinrehbein.birdy.engine.mesh.Primitives
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class NodeTest {
    @Test
    fun traverseVisibleSkipsHiddenSubtrees() {
        val root = Node("root")
        val a = Node("a")
        val b = Node("b").apply { visible = false }
        val c = Node("c")
        root.add(a, b)
        b.add(c)
        val all = ArrayList<String>()
        root.traverse { all.add(it.name) }
        assertEquals(listOf("root", "a", "b", "c"), all)
        val vis = ArrayList<String>()
        root.traverseVisible { vis.add(it.name) }
        assertEquals(listOf("root", "a"), vis)
        assertSame(c, root.findByName("c"))
    }

    @Test
    fun removeFromParentAndReparent() {
        val p1 = Node(); val p2 = Node(); val n = Node()
        p1.add(n)
        p2.add(n)
        assertTrue(p1.children.isEmpty())
        assertSame(p2, n.parent)
        n.removeFromParent()
        assertNull(n.parent)
        assertTrue(p2.children.isEmpty())
        n.removeFromParent()
    }

    @Test
    fun worldPositionUpdatesAncestors() {
        val root = Node().apply { position.set(1f, 0f, 0f); rotation.y = (PI / 2).toFloat() }
        val child = Node().apply { position.set(0f, 0f, 2f) }
        root.add(child)
        val wp = child.getWorldPosition()
        assertEquals(3f, wp.x, 1e-6f)
        assertEquals(0f, wp.y, 1e-6f)
        assertEquals(0f, wp.z, 1e-6f)
        val local = root.worldToLocal(Vec3(3f, 0f, 0f))
        assertEquals(2f, local.z, 1e-5f)
        assertEquals(0f, child.worldToLocal(Vec3(3f, 0f, 0f)).length(), 1e-5f)
    }

    @Test
    fun billboardCopiesCameraQuaternion() {
        val cam = PerspectiveCamera(50f, 0.5f, 0.1f, 100f)
        cam.position.set(0f, 5f, 10f)
        cam.lookAt(Vec3(0f, 0f, 0f))
        val scene = Scene().apply { add(cam) }
        scene.updateWorldMatrix(null)
        cam.updateView()
        val marker = Mesh(Primitives.plane(), BasicMaterial()).apply { quaternionOverride = Quat().set(cam.worldQuaternion) }
        // A billboard faces the camera: its +Z points back towards the camera.
        val dir = Vec3(0f, 0f, 1f).applyQuat(marker.quaternionOverride!!)
        val toCam = Vec3(0f, 5f, 10f).normalize()
        assertTrue(dir.dot(toCam) > 0.999f)
        // Nested camera: world quaternion is decomposed from the world matrix.
        val rig = Node().apply { rotation.y = 0.3f }
        rig.add(cam)
        rig.updateWorldMatrix(null)
        cam.updateView()
        val expected = Quat().setFromEulerXYZ(0f, 0.3f, 0f).multiply(cam.getQuaternion())
        assertTrue(kotlin.math.abs(expected.dot(cam.worldQuaternion)) > 0.99999f)
    }
}
