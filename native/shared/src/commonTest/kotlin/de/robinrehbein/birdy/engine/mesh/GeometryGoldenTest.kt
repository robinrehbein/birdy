package de.robinrehbein.birdy.engine.mesh

import de.robinrehbein.birdy.Golden
import de.robinrehbein.birdy.engine.assertClose
import de.robinrehbein.birdy.engine.d
import de.robinrehbein.birdy.engine.doubles
import de.robinrehbein.birdy.engine.get
import de.robinrehbein.birdy.engine.i
import de.robinrehbein.birdy.engine.ints
import de.robinrehbein.birdy.engine.isNull
import de.robinrehbein.birdy.engine.obj
import de.robinrehbein.birdy.engine.scene.Geometry
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Every generator/op against three.js r186 output (docs/native/golden/engine-geometry.json). */
class GeometryGoldenTest {
    private val golden = Golden.json("engine-geometry.json")["cases"].obj

    @Test
    fun allCasesCovered() {
        assertEquals(golden.keys, GeometryCases.cases.keys)
    }

    @Test
    fun generatorsMatchThree() {
        val failures = ArrayList<String>()
        for ((name, build) in GeometryCases.cases) {
            try {
                check(name, build())
            } catch (e: AssertionError) {
                failures.add("$name: ${e.message}")
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    private fun attribute(geo: Geometry, name: String): Pair<FloatArray?, Int> = when (name) {
        "position" -> geo.positions to 3
        "normal" -> geo.normals to 3
        "uv" -> geo.uvs to 2
        "color" -> geo.colors to 3
        else -> geo.extraAttributes[name]?.let { it.data to it.itemSize } ?: (null to 0)
    }

    private fun check(name: String, geo: Geometry) {
        val g = golden.getValue(name)
        assertEquals(g["vertexCount"].i, geo.vertexCount, "vertexCount")
        if (g["index"].isNull) assertNull(geo.indices, "should be non-indexed")
        else assertContentEquals(g["index"].ints(), assertNotNull(geo.indices, "should be indexed"), "index")
        val attrs = g["attributes"].obj
        val expectedNames = attrs.keys
        val actualNames = buildSet {
            add("position")
            if (geo.normals != null) add("normal")
            if (geo.uvs != null) add("uv")
            if (geo.colors != null) add("color")
            addAll(geo.extraAttributes.keys)
        }
        assertEquals(expectedNames, actualNames, "attribute set")
        val samples = g["sampleIndices"].ints()
        for ((attrName, a) in attrs) {
            val (data, itemSize) = attribute(geo, attrName)
            assertNotNull(data)
            assertEquals(a["itemSize"].i, itemSize)
            val expected = a["samples"].doubles()
            val actual = DoubleArray(expected.size)
            var k = 0
            for (s in samples) for (c in 0 until itemSize) actual[k++] = data[s * itemSize + c].toDouble()
            assertClose(expected, actual, 2e-6, "$attrName samples")
            var sum = 0.0; var absSum = 0.0
            for (v in data) { sum += v; absSum += abs(v) }
            // Checksums over all values catch differences between the samples.
            assertClose(a["absSum"].d, absSum, 1e-5, "$attrName absSum")
            assertTrue(abs(a["sum"].d - sum) <= 1e-5 * maxOf(1.0, a["absSum"].d), "$attrName sum ${a["sum"].d} vs $sum")
        }
    }
}
