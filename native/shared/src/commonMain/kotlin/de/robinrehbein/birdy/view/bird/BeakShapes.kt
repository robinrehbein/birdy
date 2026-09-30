package de.robinrehbein.birdy.view.bird

import de.robinrehbein.birdy.engine.mesh.computeVertexNormals
import de.robinrehbein.birdy.engine.scene.Geometry
import de.robinrehbein.birdy.engine.scene.Material
import de.robinrehbein.birdy.engine.scene.Mesh
import de.robinrehbein.birdy.engine.scene.Node
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Cross sections along the forward (-Z) axis; both halves share an exact mouth seam. */
private data class BeakSection(
    val z: Double, val seam: Double, val width: Double, val upper: Double, val lower: Double,
)

/** Continuous, tapered low-poly volumes, including the hooked tip (rather than attached cones). */
internal object BeakShapes {
    fun eagle(upper: Material, lower: Material): Node = halves(listOf(
        BeakSection(-0.48, -0.09, 0.20, 0.23, 0.09),
        BeakSection(-0.65, -0.09, 0.23, 0.27, 0.10),
        BeakSection(-0.83, -0.08, 0.17, 0.20, 0.08),
        BeakSection(-0.96, -0.11, 0.10, 0.10, 0.045),
        BeakSection(-1.01, -0.22, 0.055, 0.07, 0.02),
        BeakSection(-0.98, -0.31, 0.0, 0.0, 0.0),
    ), upper, lower)

    fun toucan(): Node {
        val sections = listOf(
            BeakSection(-0.47, -0.08, 0.20, 0.25, 0.11),
            BeakSection(-0.69, -0.08, 0.25, 0.39, 0.13),
            BeakSection(-1.03, -0.08, 0.23, 0.38, 0.12),
            BeakSection(-1.34, -0.07, 0.17, 0.27, 0.085),
            BeakSection(-1.53, -0.085, 0.105, 0.16, 0.055),
            BeakSection(-1.66, -0.12, 0.05, 0.075, 0.03),
            BeakSection(-1.71, -0.155, 0.0, 0.0, 0.0),
        )
        val tip = flatMat(0x252329)
        return Node().apply {
            add(halves(sections.take(5), flatMat(0xffa323), flatMat(0xffd23d)))
            // Reuse the last coloured section so the black tip joins without a gap or bulge.
            add(halves(sections.drop(4), tip, tip))
        }
    }

    private fun halves(sections: List<BeakSection>, upper: Material, lower: Material): Node = Node().apply {
        add(Mesh(surface(sections, true), upper), Mesh(surface(sections, false), lower))
    }

    private fun surface(sections: List<BeakSection>, upper: Boolean): Geometry {
        val vertices = ArrayList<Float>()
        val steps = 5
        fun point(s: BeakSection, step: Int): DoubleArray {
            val a = step * PI / steps
            return doubleArrayOf(s.width * cos(a), s.seam + sin(a) *
                (if (upper) s.upper else -s.lower), s.z)
        }
        fun triangle(a: DoubleArray, b: DoubleArray, c: DoubleArray) {
            for (p in if (upper) listOf(a, c, b) else listOf(a, b, c)) {
                for (v in p) vertices.add(v.toFloat())
            }
        }
        for (i in 0 until sections.lastIndex) {
            for (j in 0 until steps) {
                val a = point(sections[i], j)
                val b = point(sections[i], j + 1)
                val c = point(sections[i + 1], j)
                val d = point(sections[i + 1], j + 1)
                triangle(a, b, c)
                triangle(b, d, c)
            }
        }
        // Close the base and end; the two halves meet along their shared seam.
        for ((s, front) in listOf(sections.first() to false, sections.last() to true)) {
            val center = doubleArrayOf(0.0, s.seam, s.z)
            for (j in 0 until steps) {
                if (front) triangle(center, point(s, j), point(s, j + 1))
                else triangle(center, point(s, j + 1), point(s, j))
            }
        }
        return Geometry(vertices.toFloatArray()).computeVertexNormals()
    }
}
