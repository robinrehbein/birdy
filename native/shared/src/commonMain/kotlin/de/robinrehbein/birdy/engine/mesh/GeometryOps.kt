package de.robinrehbein.birdy.engine.mesh

import de.robinrehbein.birdy.engine.math.Color
import de.robinrehbein.birdy.engine.math.DMat
import de.robinrehbein.birdy.engine.math.Mat4
import de.robinrehbein.birdy.engine.math.Vec3
import de.robinrehbein.birdy.engine.scene.Geometry
import de.robinrehbein.birdy.engine.scene.VertexAttribute
import kotlin.math.sqrt

/*
 * three.js `BufferGeometry` utilities as extensions on [Geometry]. All transforms mutate in place,
 * call [Geometry.markDirty] and return the geometry for chaining (like three.js).
 * Math runs in Double on the Float attribute values, exactly like three.js does with Float32 attributes.
 */

/** Deep copy (three.js `clone`). */
fun Geometry.clone(): Geometry {
    val g = Geometry(positions.copyOf(), normals?.copyOf(), colors?.copyOf(), uvs?.copyOf(), indices?.copyOf())
    for ((k, a) in extraAttributes) g.extraAttributes[k] = VertexAttribute(a.data.copyOf(), a.itemSize)
    g.drawCount = drawCount
    return g
}

/** three.js `applyMatrix4`: positions by [m] (with perspective divide), normals by its normal matrix. */
fun Geometry.applyMat4(m: Mat4): Geometry = applyMatrixD(DoubleArray(16) { m.e[it].toDouble() })

internal fun Geometry.applyMatrixD(m: DoubleArray): Geometry {
    val p = positions
    for (i in 0 until p.size / 3) {
        val x = p[i * 3].toDouble(); val y = p[i * 3 + 1].toDouble(); val z = p[i * 3 + 2].toDouble()
        val w = 1 / (m[3] * x + m[7] * y + m[11] * z + m[15])
        p[i * 3] = ((m[0] * x + m[4] * y + m[8] * z + m[12]) * w).toFloat()
        p[i * 3 + 1] = ((m[1] * x + m[5] * y + m[9] * z + m[13]) * w).toFloat()
        p[i * 3 + 2] = ((m[2] * x + m[6] * y + m[10] * z + m[14]) * w).toFloat()
    }
    val n = normals
    if (n != null) {
        val nm = DMat.normalMatrix(m)
        for (i in 0 until n.size / 3) {
            val x = n[i * 3].toDouble(); val y = n[i * 3 + 1].toDouble(); val z = n[i * 3 + 2].toDouble()
            val nx = nm[0] * x + nm[3] * y + nm[6] * z
            val ny = nm[1] * x + nm[4] * y + nm[7] * z
            val nz = nm[2] * x + nm[5] * y + nm[8] * z
            val l = sqrt(nx * nx + ny * ny + nz * nz)
            val inv = 1 / (if (l == 0.0) 1.0 else l)
            n[i * 3] = (nx * inv).toFloat(); n[i * 3 + 1] = (ny * inv).toFloat(); n[i * 3 + 2] = (nz * inv).toFloat()
        }
    }
    markDirty()
    return this
}

fun Geometry.translate(x: Double, y: Double, z: Double): Geometry = applyMatrixD(DMat.translation(x, y, z))
fun Geometry.scale(x: Double, y: Double, z: Double): Geometry = applyMatrixD(DMat.scale(x, y, z))
fun Geometry.rotateX(angle: Double): Geometry = applyMatrixD(DMat.rotationX(angle))
fun Geometry.rotateY(angle: Double): Geometry = applyMatrixD(DMat.rotationY(angle))
fun Geometry.rotateZ(angle: Double): Geometry = applyMatrixD(DMat.rotationZ(angle))

/** Axis-aligned bounds of [Geometry.positions] as (min, max). */
class Bounds(val min: Vec3, val max: Vec3) {
    val isEmpty: Boolean get() = min.x > max.x || min.y > max.y || min.z > max.z
}

fun Geometry.computeBoundingBox(): Bounds {
    val inf = Float.POSITIVE_INFINITY
    val mn = Vec3(inf, inf, inf)
    val mx = Vec3(-inf, -inf, -inf)
    val p = positions
    for (i in 0 until p.size / 3) {
        val x = p[i * 3]; val y = p[i * 3 + 1]; val z = p[i * 3 + 2]
        if (x < mn.x) mn.x = x; if (y < mn.y) mn.y = y; if (z < mn.z) mn.z = z
        if (x > mx.x) mx.x = x; if (y > mx.y) mx.y = y; if (z > mx.z) mx.z = z
    }
    return Bounds(mn, mx)
}

/** Moves the bounding-box centre to the origin (three.js `center`). */
fun Geometry.center(): Geometry {
    val b = computeBoundingBox()
    if (b.isEmpty) return this
    val cx = (b.min.x.toDouble() + b.max.x) * 0.5
    val cy = (b.min.y.toDouble() + b.max.y) * 0.5
    val cz = (b.min.z.toDouble() + b.max.z) * 0.5
    return translate(-cx, -cy, -cz)
}

/** Expands indices so every triangle has its own 3 vertices (three.js `toNonIndexed`). */
fun Geometry.toNonIndexed(): Geometry {
    val idx = indices ?: return clone()
    fun expand(src: FloatArray?, itemSize: Int): FloatArray? {
        if (src == null) return null
        val out = FloatArray(idx.size * itemSize)
        for (i in idx.indices) src.copyInto(out, i * itemSize, idx[i] * itemSize, idx[i] * itemSize + itemSize)
        return out
    }
    val g = Geometry(expand(positions, 3)!!, expand(normals, 3), expand(colors, 3), expand(uvs, 2), null)
    for ((k, a) in extraAttributes) g.extraAttributes[k] = VertexAttribute(expand(a.data, a.itemSize)!!, a.itemSize)
    return g
}

/**
 * three.js `computeVertexNormals`: indexed geometry gets area-weighted smooth normals, non-indexed
 * geometry flat face normals.
 */
fun Geometry.computeVertexNormals(): Geometry {
    val p = positions
    val n = normals?.also { it.fill(0f) } ?: FloatArray(p.size).also { normals = it }
    val idx = indices
    val triCount = if (idx != null) idx.size / 3 else p.size / 9
    for (t in 0 until triCount) {
        val a = idx?.get(t * 3) ?: (t * 3)
        val b = idx?.get(t * 3 + 1) ?: (t * 3 + 1)
        val c = idx?.get(t * 3 + 2) ?: (t * 3 + 2)
        val ax = p[a * 3].toDouble(); val ay = p[a * 3 + 1].toDouble(); val az = p[a * 3 + 2].toDouble()
        val bx = p[b * 3].toDouble(); val by = p[b * 3 + 1].toDouble(); val bz = p[b * 3 + 2].toDouble()
        val cx = p[c * 3].toDouble(); val cy = p[c * 3 + 1].toDouble(); val cz = p[c * 3 + 2].toDouble()
        val cbx = cx - bx; val cby = cy - by; val cbz = cz - bz
        val abx = ax - bx; val aby = ay - by; val abz = az - bz
        val nx = cby * abz - cbz * aby
        val ny = cbz * abx - cbx * abz
        val nz = cbx * aby - cby * abx
        if (idx != null) {
            for (v in intArrayOf(a, b, c)) {
                n[v * 3] = (n[v * 3] + nx).toFloat()
                n[v * 3 + 1] = (n[v * 3 + 1] + ny).toFloat()
                n[v * 3 + 2] = (n[v * 3 + 2] + nz).toFloat()
            }
        } else {
            for (v in intArrayOf(a, b, c)) {
                n[v * 3] = nx.toFloat(); n[v * 3 + 1] = ny.toFloat(); n[v * 3 + 2] = nz.toFloat()
            }
        }
    }
    normalizeNormals()
    markDirty()
    return this
}

/** three.js `normalizeNormals`. */
fun Geometry.normalizeNormals(): Geometry {
    val n = normals ?: return this
    for (i in 0 until n.size / 3) {
        val x = n[i * 3].toDouble(); val y = n[i * 3 + 1].toDouble(); val z = n[i * 3 + 2].toDouble()
        val l = sqrt(x * x + y * y + z * z)
        val inv = 1 / (if (l == 0.0) 1.0 else l)
        n[i * 3] = (x * inv).toFloat(); n[i * 3 + 1] = (y * inv).toFloat(); n[i * 3 + 2] = (z * inv).toFloat()
    }
    return this
}

/** Writes [color]'s linear components to every vertex (the `color` attribute three.js code builds by hand). */
fun Geometry.setVertexColor(color: Color): Geometry {
    val r = color.linearR; val g = color.linearG; val b = color.linearB
    val c = FloatArray(vertexCount * 3)
    for (i in 0 until vertexCount) { c[i * 3] = r; c[i * 3 + 1] = g; c[i * 3 + 2] = b }
    colors = c
    markDirty()
    return this
}

fun Geometry.setVertexColor(hex: Int): Geometry = setVertexColor(Color.hex(hex))

/** Adds or replaces a named extra attribute (three.js `setAttribute` for custom attributes). */
fun Geometry.setAttribute(name: String, data: FloatArray, itemSize: Int): Geometry {
    require(data.size == vertexCount * itemSize) { "attribute $name has ${data.size} floats, expected ${vertexCount * itemSize}" }
    extraAttributes[name] = VertexAttribute(data, itemSize)
    markDirty()
    return this
}

/** Flips the triangle winding (swaps the 2nd and 3rd vertex of every triangle), e.g. for inverted hulls. */
fun Geometry.reverseWinding(): Geometry {
    val idx = indices
    if (idx != null) {
        for (i in 0 until idx.size / 3) { val t = idx[i * 3 + 1]; idx[i * 3 + 1] = idx[i * 3 + 2]; idx[i * 3 + 2] = t }
    } else {
        fun swap(a: FloatArray?, itemSize: Int) {
            if (a == null) return
            for (t in 0 until vertexCount / 3) {
                val v1 = (t * 3 + 1) * itemSize; val v2 = (t * 3 + 2) * itemSize
                for (k in 0 until itemSize) { val tmp = a[v1 + k]; a[v1 + k] = a[v2 + k]; a[v2 + k] = tmp }
            }
        }
        swap(positions, 3); swap(normals, 3); swap(colors, 3); swap(uvs, 2)
        for (a in extraAttributes.values) swap(a.data, a.itemSize)
    }
    markDirty()
    return this
}
