package de.robinrehbein.birdy.engine.mesh

import de.robinrehbein.birdy.engine.scene.Geometry
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** three.js `SphereGeometry`. */
internal fun buildSphere(
    radius: Double, widthSegments0: Int, heightSegments0: Int,
    phiStart: Double, phiLength: Double, thetaStart: Double, thetaLength: Double,
): Geometry {
    val widthSegments = max(3, widthSegments0)
    val heightSegments = max(2, heightSegments0)
    val thetaEnd = min(thetaStart + thetaLength, PI)
    var index = 0
    val grid = ArrayList<IntArray>()
    val pos = DoubleList(); val nor = DoubleList(); val uv = DoubleList(); val idx = IntList()
    for (iy in 0..heightSegments) {
        val row = IntArray(widthSegments + 1)
        val v = iy.toDouble() / heightSegments
        val theta = thetaStart + v * thetaLength
        val y = radius * cos(theta)
        val ringRadius = sqrt(radius * radius - y * y)
        var uOffset = 0.0
        if (iy == 0 && thetaStart == 0.0) uOffset = 0.5 / widthSegments
        else if (iy == heightSegments && thetaEnd == PI) uOffset = -0.5 / widthSegments
        for (ix in 0..widthSegments) {
            val u = ix.toDouble() / widthSegments
            val phi = phiStart + u * phiLength
            val vx = -ringRadius * cos(phi)
            val vz = ringRadius * sin(phi)
            pos.add(vx, y, vz)
            val l = sqrt(vx * vx + y * y + vz * vz)
            val inv = 1 / (if (l == 0.0) 1.0 else l)
            nor.add(vx * inv, y * inv, vz * inv)
            uv.add(u + uOffset, 1 - v)
            row[ix] = index++
        }
        grid.add(row)
    }
    for (iy in 0 until heightSegments) for (ix in 0 until widthSegments) {
        val a = grid[iy][ix + 1]
        val b = grid[iy][ix]
        val c = grid[iy + 1][ix]
        val d = grid[iy + 1][ix + 1]
        if (iy != 0 || thetaStart > 0) idx.add(a, b, d)
        if (iy != heightSegments - 1 || thetaEnd < PI) idx.add(b, c, d)
    }
    return indexedGeometry(pos, nor, uv, idx)
}

/** three.js `CylinderGeometry` (a cone is `radiusTop = 0`). */
internal fun buildCylinder(
    radiusTop: Double, radiusBottom: Double, height: Double, radialSegments: Int, heightSegments: Int,
    openEnded: Boolean, thetaStart: Double, thetaLength: Double,
): Geometry {
    val pos = DoubleList(); val nor = DoubleList(); val uv = DoubleList(); val idx = IntList()
    var index = 0
    val halfHeight = height / 2

    // torso
    val indexArray = ArrayList<IntArray>()
    val slope = (radiusBottom - radiusTop) / height
    for (y in 0..heightSegments) {
        val row = IntArray(radialSegments + 1)
        val v = y.toDouble() / heightSegments
        val radius = v * (radiusBottom - radiusTop) + radiusTop
        for (x in 0..radialSegments) {
            val u = x.toDouble() / radialSegments
            val theta = u * thetaLength + thetaStart
            val sinTheta = sin(theta)
            val cosTheta = cos(theta)
            pos.add(radius * sinTheta, -v * height + halfHeight, radius * cosTheta)
            val l = sqrt(sinTheta * sinTheta + slope * slope + cosTheta * cosTheta)
            val inv = 1 / (if (l == 0.0) 1.0 else l)
            nor.add(sinTheta * inv, slope * inv, cosTheta * inv)
            uv.add(u, 1 - v)
            row[x] = index++
        }
        indexArray.add(row)
    }
    for (x in 0 until radialSegments) for (y in 0 until heightSegments) {
        val a = indexArray[y][x]
        val b = indexArray[y + 1][x]
        val c = indexArray[y + 1][x + 1]
        val d = indexArray[y][x + 1]
        if (radiusTop > 0 || y != 0) idx.add(a, b, d)
        if (radiusBottom > 0 || y != heightSegments - 1) idx.add(b, c, d)
    }

    fun cap(top: Boolean) {
        val centerIndexStart = index
        val radius = if (top) radiusTop else radiusBottom
        val sign = if (top) 1.0 else -1.0
        for (x in 1..radialSegments) {
            pos.add(0.0, halfHeight * sign, 0.0)
            nor.add(0.0, sign, 0.0)
            uv.add(0.5, 0.5)
            index++
        }
        val centerIndexEnd = index
        for (x in 0..radialSegments) {
            val u = x.toDouble() / radialSegments
            val theta = u * thetaLength + thetaStart
            val cosTheta = cos(theta)
            val sinTheta = sin(theta)
            pos.add(radius * sinTheta, halfHeight * sign, radius * cosTheta)
            nor.add(0.0, sign, 0.0)
            uv.add((cosTheta * 0.5) + 0.5, (sinTheta * 0.5 * sign) + 0.5)
            index++
        }
        for (x in 0 until radialSegments) {
            val c = centerIndexStart + x
            val i = centerIndexEnd + x
            if (top) idx.add(i, i + 1, c) else idx.add(i + 1, i, c)
        }
    }
    if (!openEnded) {
        if (radiusTop > 0) cap(true)
        if (radiusBottom > 0) cap(false)
    }
    return indexedGeometry(pos, nor, uv, idx)
}

/** three.js `TorusGeometry` (ring in the XY plane). */
internal fun buildTorus(
    radius: Double, tube: Double, radialSegments: Int, tubularSegments: Int, arc: Double,
    thetaStart: Double, thetaLength: Double,
): Geometry {
    val pos = DoubleList(); val nor = DoubleList(); val uv = DoubleList(); val idx = IntList()
    for (j in 0..radialSegments) {
        val v = thetaStart + (j.toDouble() / radialSegments) * thetaLength
        for (i in 0..tubularSegments) {
            val u = i.toDouble() / tubularSegments * arc
            val vx = (radius + tube * cos(v)) * cos(u)
            val vy = (radius + tube * cos(v)) * sin(u)
            val vz = tube * sin(v)
            pos.add(vx, vy, vz)
            val nx = vx - radius * cos(u)
            val ny = vy - radius * sin(u)
            val l = sqrt(nx * nx + ny * ny + vz * vz)
            val inv = 1 / (if (l == 0.0) 1.0 else l)
            nor.add(nx * inv, ny * inv, vz * inv)
            uv.add(i.toDouble() / tubularSegments, j.toDouble() / radialSegments)
        }
    }
    for (j in 1..radialSegments) for (i in 1..tubularSegments) {
        val a = (tubularSegments + 1) * j + i - 1
        val b = (tubularSegments + 1) * (j - 1) + i - 1
        val c = (tubularSegments + 1) * (j - 1) + i
        val d = (tubularSegments + 1) * j + i
        idx.add(a, b, d)
        idx.add(b, c, d)
    }
    return indexedGeometry(pos, nor, uv, idx)
}

/** three.js `CircleGeometry` (fan in the XY plane). */
internal fun buildCircle(radius: Double, segments0: Int, thetaStart: Double, thetaLength: Double): Geometry {
    val segments = max(3, segments0)
    val pos = DoubleList(); val nor = DoubleList(); val uv = DoubleList(); val idx = IntList()
    pos.add(0.0, 0.0, 0.0)
    nor.add(0.0, 0.0, 1.0)
    uv.add(0.5, 0.5)
    for (s in 0..segments) {
        val segment = thetaStart + s.toDouble() / segments * thetaLength
        val x = radius * cos(segment)
        val y = radius * sin(segment)
        pos.add(x, y, 0.0)
        nor.add(0.0, 0.0, 1.0)
        uv.add((x / radius + 1) / 2, (y / radius + 1) / 2)
    }
    for (i in 1..segments) idx.add(i, i + 1, 0)
    return indexedGeometry(pos, nor, uv, idx)
}

/** three.js `RingGeometry` (annulus in the XY plane). */
internal fun buildRing(
    innerRadius: Double, outerRadius: Double, thetaSegments0: Int, phiSegments0: Int,
    thetaStart: Double, thetaLength: Double,
): Geometry {
    val thetaSegments = max(3, thetaSegments0)
    val phiSegments = max(1, phiSegments0)
    val pos = DoubleList(); val nor = DoubleList(); val uv = DoubleList(); val idx = IntList()
    var radius = innerRadius
    val radiusStep = (outerRadius - innerRadius) / phiSegments
    for (j in 0..phiSegments) {
        for (i in 0..thetaSegments) {
            val segment = thetaStart + i.toDouble() / thetaSegments * thetaLength
            val x = radius * cos(segment)
            val y = radius * sin(segment)
            pos.add(x, y, 0.0)
            nor.add(0.0, 0.0, 1.0)
            uv.add((x / outerRadius + 1) / 2, (y / outerRadius + 1) / 2)
        }
        radius += radiusStep
    }
    for (j in 0 until phiSegments) {
        val level = j * (thetaSegments + 1)
        for (i in 0 until thetaSegments) {
            val segment = i + level
            val a = segment
            val b = segment + thetaSegments + 1
            val c = segment + thetaSegments + 2
            val d = segment + 1
            idx.add(a, b, d)
            idx.add(b, c, d)
        }
    }
    return indexedGeometry(pos, nor, uv, idx)
}
