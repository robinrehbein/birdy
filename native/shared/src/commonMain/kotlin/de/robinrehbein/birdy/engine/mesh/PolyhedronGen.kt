package de.robinrehbein.birdy.engine.mesh

import de.robinrehbein.birdy.engine.scene.Geometry
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * three.js `PolyhedronGeometry`: non-indexed, each face subdivided [detail] times and projected onto the
 * sphere of [radius]. detail 0 gets flat face normals, detail > 0 smooth (normalized position) normals.
 */
internal fun buildPolyhedron(vertices: DoubleArray, indices: IntArray, radius: Double, detail: Int): Geometry {
    val vb = DoubleList()
    val uvb = DoubleList()

    fun lerp(a: DoubleArray, b: DoubleArray, t: Double) =
        doubleArrayOf(a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t)

    fun push(v: DoubleArray) = vb.add(v[0], v[1], v[2])

    fun subdivideFace(a: DoubleArray, b: DoubleArray, c: DoubleArray) {
        val cols = detail + 1
        val v = ArrayList<Array<DoubleArray?>>()
        for (i in 0..cols) {
            val aj = lerp(a, c, i.toDouble() / cols)
            val bj = lerp(b, c, i.toDouble() / cols)
            val rows = cols - i
            val row = arrayOfNulls<DoubleArray>(rows + 1)
            for (j in 0..rows) row[j] = if (j == 0 && i == cols) aj else lerp(aj, bj, j.toDouble() / rows)
            v.add(row)
        }
        for (i in 0 until cols) for (j in 0 until 2 * (cols - i) - 1) {
            val k = floor(j / 2.0).toInt()
            if (j % 2 == 0) {
                push(v[i][k + 1]!!); push(v[i + 1][k]!!); push(v[i][k]!!)
            } else {
                push(v[i][k + 1]!!); push(v[i + 1][k + 1]!!); push(v[i + 1][k]!!)
            }
        }
    }

    fun vertex(i: Int) = doubleArrayOf(vertices[i * 3], vertices[i * 3 + 1], vertices[i * 3 + 2])
    for (i in indices.indices step 3) subdivideFace(vertex(indices[i]), vertex(indices[i + 1]), vertex(indices[i + 2]))

    // applyRadius: normalize().multiplyScalar(radius)
    for (i in 0 until vb.size step 3) {
        val x = vb[i]; val y = vb[i + 1]; val z = vb[i + 2]
        val l = sqrt(x * x + y * y + z * z)
        val inv = 1 / (if (l == 0.0) 1.0 else l)
        vb[i] = x * inv * radius; vb[i + 1] = y * inv * radius; vb[i + 2] = z * inv * radius
    }

    fun azimuth(x: Double, z: Double) = atan2(z, -x)
    fun inclination(x: Double, y: Double, z: Double) = atan2(-y, sqrt(x * x + z * z))

    for (i in 0 until vb.size step 3) {
        val u = azimuth(vb[i], vb[i + 2]) / 2 / PI + 0.5
        val v = inclination(vb[i], vb[i + 1], vb[i + 2]) / PI + 0.5
        uvb.add(u, 1 - v)
    }
    // correctUVs
    var i = 0; var j = 0
    while (i < vb.size) {
        val cx = (vb[i] + vb[i + 3] + vb[i + 6]) * (1.0 / 3)
        val cz = (vb[i + 2] + vb[i + 5] + vb[i + 8]) * (1.0 / 3)
        val azi = azimuth(cx, cz)
        for (k in 0 until 3) {
            val stride = j + k * 2
            val ux = uvb[stride]
            if (azi < 0 && ux == 1.0) uvb[stride] = ux - 1
            if (vb[i + k * 3] == 0.0 && vb[i + k * 3 + 2] == 0.0) uvb[stride] = azi / 2 / PI + 0.5
        }
        i += 9; j += 6
    }
    // correctSeam
    var s = 0
    while (s < uvb.size) {
        val x0 = uvb[s]; val x1 = uvb[s + 2]; val x2 = uvb[s + 4]
        val mx = max(x0, max(x1, x2))
        val mn = min(x0, min(x1, x2))
        if (mx > 0.9 && mn < 0.1) {
            if (x0 < 0.2) uvb[s] = uvb[s] + 1
            if (x1 < 0.2) uvb[s + 2] = uvb[s + 2] + 1
            if (x2 < 0.2) uvb[s + 4] = uvb[s + 4] + 1
        }
        s += 6
    }

    val positions = vb.toFloatArray()
    val geo = Geometry(positions, positions.copyOf(), null, uvb.toFloatArray(), null)
    if (detail == 0) geo.computeVertexNormals() else geo.normalizeNormals()
    return geo
}

private val PHI = (1 + sqrt(5.0)) / 2

internal val ICOSAHEDRON_VERTICES = doubleArrayOf(
    -1.0, PHI, 0.0, 1.0, PHI, 0.0, -1.0, -PHI, 0.0, 1.0, -PHI, 0.0,
    0.0, -1.0, PHI, 0.0, 1.0, PHI, 0.0, -1.0, -PHI, 0.0, 1.0, -PHI,
    PHI, 0.0, -1.0, PHI, 0.0, 1.0, -PHI, 0.0, -1.0, -PHI, 0.0, 1.0,
)

internal val ICOSAHEDRON_INDICES = intArrayOf(
    0, 11, 5, 0, 5, 1, 0, 1, 7, 0, 7, 10, 0, 10, 11,
    1, 5, 9, 5, 11, 4, 11, 10, 2, 10, 7, 6, 7, 1, 8,
    3, 9, 4, 3, 4, 2, 3, 2, 6, 3, 6, 8, 3, 8, 9,
    4, 9, 5, 2, 4, 11, 6, 2, 10, 8, 6, 7, 9, 8, 1,
)

private val R = 1 / PHI

internal val DODECAHEDRON_VERTICES = doubleArrayOf(
    -1.0, -1.0, -1.0, -1.0, -1.0, 1.0,
    -1.0, 1.0, -1.0, -1.0, 1.0, 1.0,
    1.0, -1.0, -1.0, 1.0, -1.0, 1.0,
    1.0, 1.0, -1.0, 1.0, 1.0, 1.0,
    0.0, -R, -PHI, 0.0, -R, PHI,
    0.0, R, -PHI, 0.0, R, PHI,
    -R, -PHI, 0.0, -R, PHI, 0.0,
    R, -PHI, 0.0, R, PHI, 0.0,
    -PHI, 0.0, -R, PHI, 0.0, -R,
    -PHI, 0.0, R, PHI, 0.0, R,
)

internal val DODECAHEDRON_INDICES = intArrayOf(
    3, 11, 7, 3, 7, 15, 3, 15, 13,
    7, 19, 17, 7, 17, 6, 7, 6, 15,
    17, 4, 8, 17, 8, 10, 17, 10, 6,
    8, 0, 16, 8, 16, 2, 8, 2, 10,
    0, 12, 1, 0, 1, 18, 0, 18, 16,
    6, 10, 2, 6, 2, 13, 6, 13, 15,
    2, 16, 18, 2, 18, 3, 2, 3, 13,
    18, 1, 9, 18, 9, 11, 18, 11, 3,
    4, 14, 12, 4, 12, 0, 4, 0, 8,
    11, 9, 5, 11, 5, 19, 11, 19, 7,
    19, 5, 14, 19, 14, 4, 19, 4, 17,
    1, 12, 14, 1, 14, 5, 1, 5, 9,
)
