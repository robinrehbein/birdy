package de.robinrehbein.birdy.engine.mesh

import de.robinrehbein.birdy.engine.math.Color
import de.robinrehbein.birdy.engine.math.Vec3
import de.robinrehbein.birdy.engine.scene.BasicMaterial
import de.robinrehbein.birdy.engine.scene.Mesh
import de.robinrehbein.birdy.engine.scene.Node
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GeometryOpsTest {
    @Test
    fun bakeTwoMeshGroup() {
        val root = GeometryCases.bakeTwo()
        val g = bake(root, spikes = true)
        // box: 12 triangles, cone(4 segments, 1 cap): 8 triangles -> non-indexed.
        assertEquals((12 + 8) * 3, g.vertexCount)
        assertNull(g.indices)
        assertNull(g.uvs, "uvs are dropped")
        val colors = assertNotNull(g.colors)
        val spike = assertNotNull(g.extraAttributes[SPIKE_ATTRIBUTE]).data
        // Box vertices carry the linear orange, no spike.
        val orange = Color.hex(0xff8800)
        assertEquals(orange.linearG, colors[1])
        for (i in 0 until 36 * 3) assertEquals(0f, spike[i])
        // Cone apex vertices (local y = +0.16) carry spikeDir, base vertices zero.
        val tips = (36 until g.vertexCount).count { spike[it * 3] != 0f || spike[it * 3 + 1] != 0f || spike[it * 3 + 2] != 0f }
        assertEquals(4, tips)
        // Without spikes: no extra attribute.
        assertTrue(bake(root).extraAttributes.isEmpty())
    }

    @Test
    fun bakeIncludesRootMeshAndInvisibleChildren() {
        val root = Mesh(Primitives.box(), BasicMaterial()).apply { position.set(5f, 0f, 0f) }
        root.add(Mesh(Primitives.plane(), BasicMaterial()).apply { visible = false })
        val g = bake(root)
        assertEquals(36 + 6, g.vertexCount)
        // Root transform is not applied (baked into root-local space).
        val b = g.computeBoundingBox()
        assertEquals(-0.5f, b.min.x)
        assertEquals(0.5f, b.max.x)
    }

    @Test
    fun mergeRejectsMismatchedAttributes() {
        assertFailsWith<IllegalArgumentException> { mergeGeometries(listOf(Primitives.box(), Primitives.box().toNonIndexed())) }
        assertFailsWith<IllegalArgumentException> { mergeGeometries(listOf(Primitives.box().setVertexColor(0xffffff), Primitives.box())) }
        val m = mergeGeometries(listOf(Primitives.box(), Primitives.box()))
        assertEquals(48, m.vertexCount)
        assertEquals(24, m.indices!![36])
    }

    @Test
    fun reverseWindingFlipsFlatNormals() {
        val g = Primitives.sphere(1.0, 10, 7)
        val flipped = g.clone().reverseWinding().toNonIndexed().computeVertexNormals()
        val flat = g.toNonIndexed().computeVertexNormals()
        for (i in 0 until flat.vertexCount * 3) assertEquals(-flat.normals!![i], flipped.normals!![i], 1e-6f)
    }

    @Test
    fun centerAndBounds() {
        val g = Primitives.box(2.0, 4.0, 6.0).translate(1.0, 2.0, 3.0).center()
        val b = g.computeBoundingBox()
        assertEquals(-1f, b.min.x); assertEquals(2f, b.max.y); assertEquals(3f, b.max.z)
    }

    @Test
    fun cloneIsDeep() {
        val g = Primitives.cone(0.05, 0.32, 4)
        g.setAttribute("spike", FloatArray(g.vertexCount * 3), 3)
        val c = g.clone()
        c.positions[0] = 42f
        c.extraAttributes["spike"]!!.data[0] = 1f
        assertTrue(g.positions[0] != 42f)
        assertEquals(0f, g.extraAttributes["spike"]!!.data[0])
    }

    @Test
    fun earcutSquareAndHole() {
        val tris = Earcut.triangulate(doubleArrayOf(0.0, 0.0, 1.0, 0.0, 1.0, 1.0, 0.0, 1.0))
        assertEquals(6, tris.size)
        val shape = GeometryCases.holeShape()
        val geo = Primitives.shape(shape, 6)
        // Area of the triangulation = square minus the 12-gon hole.
        var area = 0.0
        val p = geo.positions; val idx = geo.indices!!
        for (t in 0 until idx.size / 3) {
            val a = idx[t * 3]; val b = idx[t * 3 + 1]; val c = idx[t * 3 + 2]
            area += ((p[b * 3] - p[a * 3]) * (p[c * 3 + 1] - p[a * 3 + 1]) - (p[c * 3] - p[a * 3]) * (p[b * 3 + 1] - p[a * 3 + 1])) / 2.0
        }
        val hole = 0.5 * 12 * 0.4 * 0.4 * kotlin.math.sin(2 * kotlin.math.PI / 12) // absarc: curveSegments * 2 segments
        assertEquals(4.0 - hole, kotlin.math.abs(area), 1e-5)
    }

    @Test
    fun vertexColourIsLinear() {
        val g = Primitives.plane().setVertexColor(0x808080)
        assertEquals(Color.srgbToLinear(128 / 255.0).toFloat(), g.colors!![0], 1e-7f)
        val v = Vec3(1f, 0f, 0f)
        assertEquals(1f, v.length())
        assertTrue(Node().children.isEmpty())
    }
}
