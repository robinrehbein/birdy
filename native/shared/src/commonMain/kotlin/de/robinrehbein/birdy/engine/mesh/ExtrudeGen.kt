package de.robinrehbein.birdy.engine.mesh

import de.robinrehbein.birdy.engine.scene.Geometry
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * three.js `ExtrudeGeometry` options (no `extrudePath`). [bevelSize] null means three.js' default
 * `bevelThickness - 0.1`.
 */
data class ExtrudeOptions(
    val curveSegments: Int = 12,
    val steps: Int = 1,
    val depth: Double = 1.0,
    val bevelEnabled: Boolean = true,
    val bevelThickness: Double = 0.2,
    val bevelSize: Double? = null,
    val bevelOffset: Double = 0.0,
    val bevelSegments: Int = 3,
)

/** three.js `ExtrudeGeometry` with the default `WorldUVGenerator`; non-indexed, flat normals. */
internal fun buildExtrude(shapes: List<Shape>, options: ExtrudeOptions): Geometry {
    val verticesArray = DoubleList()
    val uvArray = DoubleList()
    for (shape in shapes) addExtrudedShape(shape, options, verticesArray, uvArray)
    val geo = Geometry(verticesArray.toFloatArray(), null, null, uvArray.toFloatArray(), null)
    geo.computeVertexNormals()
    return geo
}

private fun addExtrudedShape(shape: Shape, o: ExtrudeOptions, verticesArray: DoubleList, uvArray: DoubleList) {
    val placeholder = DoubleList()
    val curveSegments = o.curveSegments
    val steps = o.steps
    val depth = o.depth
    val bevelEnabled = o.bevelEnabled
    var bevelThickness = o.bevelThickness
    var bevelSize = o.bevelSize ?: (bevelThickness - 0.1)
    var bevelOffset = o.bevelOffset
    var bevelSegments = o.bevelSegments
    if (!bevelEnabled) {
        bevelSegments = 0; bevelThickness = 0.0; bevelSize = 0.0; bevelOffset = 0.0
    }

    val shapePoints = shape.extractPoints(curveSegments)
    var vertices: MutableList<Point2> = shapePoints.shape
    val holes = shapePoints.holes
    if (!ShapeUtils.isClockWise(vertices)) {
        vertices.reverse()
        for (h in holes.indices) if (ShapeUtils.isClockWise(holes[h])) holes[h].reverse()
    }

    mergeOverlappingPoints(vertices)
    holes.forEach { mergeOverlappingPoints(it) }
    val numHoles = holes.size
    val contour = vertices
    if (numHoles > 0) {
        val all = ArrayList(vertices)
        for (h in holes) all.addAll(h)
        vertices = all
    }

    fun scalePt2(pt: Point2, vec: Point2, size: Double) = Point2(pt.x + vec.x * size, pt.y + vec.y * size)
    val vlen = vertices.size

    val contourMovements = ArrayList<Point2>()
    run {
        val il = contour.size
        var j = il - 1; var k = 1
        for (i in 0 until il) {
            if (j == il) j = 0
            if (k == il) k = 0
            contourMovements.add(getBevelVec(contour[i], contour[j], contour[k]))
            j++; k++
        }
    }
    val holesMovements = ArrayList<List<Point2>>()
    val verticesMovements = ArrayList(contourMovements)
    for (h in 0 until numHoles) {
        val ahole = holes[h]
        val one = ArrayList<Point2>()
        val il = ahole.size
        var j = il - 1; var k = 1
        for (i in 0 until il) {
            if (j == il) j = 0
            if (k == il) k = 0
            one.add(getBevelVec(ahole[i], ahole[j], ahole[k]))
            j++; k++
        }
        holesMovements.add(one)
        verticesMovements.addAll(one)
    }

    fun v(x: Double, y: Double, z: Double) = placeholder.add(x, y, z)

    val faces: List<IntArray>
    if (bevelSegments == 0) {
        faces = ShapeUtils.triangulateShape(contour, holes)
    } else {
        val contracted = ArrayList<Point2>()
        val expandedHoles = ArrayList<MutableList<Point2>>()
        for (b in 0 until bevelSegments) {
            val t = b.toDouble() / bevelSegments
            val z = bevelThickness * cos(t * PI / 2)
            val bs = bevelSize * sin(t * PI / 2) + bevelOffset
            for (i in contour.indices) {
                val vert = scalePt2(contour[i], contourMovements[i], bs)
                v(vert.x, vert.y, -z)
                if (t == 0.0) contracted.add(vert)
            }
            for (h in 0 until numHoles) {
                val ahole = holes[h]
                val moves = holesMovements[h]
                val oneHole = ArrayList<Point2>()
                for (i in ahole.indices) {
                    val vert = scalePt2(ahole[i], moves[i], bs)
                    v(vert.x, vert.y, -z)
                    if (t == 0.0) oneHole.add(vert)
                }
                if (t == 0.0) expandedHoles.add(oneHole)
            }
        }
        faces = ShapeUtils.triangulateShape(contracted, expandedHoles)
    }
    val bs = bevelSize + bevelOffset

    for (i in 0 until vlen) {
        val vert = if (bevelEnabled) scalePt2(vertices[i], verticesMovements[i], bs) else vertices[i]
        v(vert.x, vert.y, 0.0)
    }
    for (s in 1..steps) for (i in 0 until vlen) {
        val vert = if (bevelEnabled) scalePt2(vertices[i], verticesMovements[i], bs) else vertices[i]
        v(vert.x, vert.y, depth / steps * s)
    }
    for (b in bevelSegments - 1 downTo 0) {
        val t = b.toDouble() / bevelSegments
        val z = bevelThickness * cos(t * PI / 2)
        val bsb = bevelSize * sin(t * PI / 2) + bevelOffset
        for (i in contour.indices) {
            val vert = scalePt2(contour[i], contourMovements[i], bsb)
            v(vert.x, vert.y, depth + z)
        }
        for (h in holes.indices) {
            val ahole = holes[h]
            val moves = holesMovements[h]
            for (i in ahole.indices) {
                val vert = scalePt2(ahole[i], moves[i], bsb)
                v(vert.x, vert.y, depth + z)
            }
        }
    }

    fun addVertex(index: Int) = verticesArray.add(placeholder[index * 3], placeholder[index * 3 + 1], placeholder[index * 3 + 2])

    fun f3(a: Int, b: Int, c: Int) {
        addVertex(a); addVertex(b); addVertex(c)
        val next = verticesArray.size / 3
        for (n in next - 3 until next) uvArray.add(verticesArray[n * 3], verticesArray[n * 3 + 1])
    }

    fun f4(a: Int, b: Int, c: Int, d: Int) {
        addVertex(a); addVertex(b); addVertex(d)
        addVertex(b); addVertex(c); addVertex(d)
        val next = verticesArray.size / 3
        val ia = next - 6; val ib = next - 3; val ic = next - 2; val id = next - 1
        val ax = verticesArray[ia * 3]; val ay = verticesArray[ia * 3 + 1]; val az = verticesArray[ia * 3 + 2]
        val bx = verticesArray[ib * 3]; val by = verticesArray[ib * 3 + 1]; val bz = verticesArray[ib * 3 + 2]
        val cx = verticesArray[ic * 3]; val cy = verticesArray[ic * 3 + 1]; val cz = verticesArray[ic * 3 + 2]
        val dx = verticesArray[id * 3]; val dy = verticesArray[id * 3 + 1]; val dz = verticesArray[id * 3 + 2]
        val uvs = if (abs(ay - by) < abs(ax - bx)) {
            arrayOf(ax to 1 - az, bx to 1 - bz, cx to 1 - cz, dx to 1 - dz)
        } else {
            arrayOf(ay to 1 - az, by to 1 - bz, cy to 1 - cz, dy to 1 - dz)
        }
        for (k in intArrayOf(0, 1, 3, 1, 2, 3)) uvArray.add(uvs[k].first, uvs[k].second)
    }

    // lids
    if (bevelEnabled) {
        var offset = 0
        for (f in faces) f3(f[2] + offset, f[1] + offset, f[0] + offset)
        offset = vlen * (steps + bevelSegments * 2)
        for (f in faces) f3(f[0] + offset, f[1] + offset, f[2] + offset)
    } else {
        for (f in faces) f3(f[2], f[1], f[0])
        for (f in faces) f3(f[0] + vlen * steps, f[1] + vlen * steps, f[2] + vlen * steps)
    }
    // side walls
    fun sidewalls(c: List<Point2>, layeroffset: Int) {
        var i = c.size
        while (--i >= 0) {
            val j = i
            var k = i - 1
            if (k < 0) k = c.size - 1
            for (s in 0 until steps + bevelSegments * 2) {
                val slen1 = vlen * s
                val slen2 = vlen * (s + 1)
                f4(layeroffset + j + slen1, layeroffset + k + slen1, layeroffset + k + slen2, layeroffset + j + slen2)
            }
        }
    }
    var layeroffset = 0
    sidewalls(contour, layeroffset)
    layeroffset += contour.size
    for (h in holes) {
        sidewalls(h, layeroffset)
        layeroffset += h.size
    }
}

/** three.js `mergeOverlappingPoints` (also drops the wrap-around duplicate, removing the first point). */
private fun mergeOverlappingPoints(points: MutableList<Point2>) {
    val thresholdSq = 1e-10 * 1e-10
    if (points.isEmpty()) return
    var prev = points[0]
    var i = 1
    while (i <= points.size) {
        val currentIndex = i % points.size
        val cur = points[currentIndex]
        val dx = cur.x - prev.x
        val dy = cur.y - prev.y
        val distSq = dx * dx + dy * dy
        val scale = max(max(abs(cur.x), abs(cur.y)), max(abs(prev.x), abs(prev.y)))
        if (distSq <= thresholdSq * scale * scale) {
            points.removeAt(currentIndex)
            continue
        }
        prev = cur
        i++
    }
}

/** three.js `getBevelVec`: offset direction for [inPt] between [inPrev] and [inNext]. */
private fun getBevelVec(inPt: Point2, inPrev: Point2, inNext: Point2): Point2 {
    val vPrevX = inPt.x - inPrev.x
    val vPrevY = inPt.y - inPrev.y
    val vNextX = inNext.x - inPt.x
    val vNextY = inNext.y - inPt.y
    val vPrevLensq = vPrevX * vPrevX + vPrevY * vPrevY
    val collinear0 = vPrevX * vNextY - vPrevY * vNextX
    val vTransX: Double
    val vTransY: Double
    val shrinkBy: Double
    if (abs(collinear0) > Curve2.EPSILON) {
        val vPrevLen = sqrt(vPrevLensq)
        val vNextLen = sqrt(vNextX * vNextX + vNextY * vNextY)
        val ptPrevShiftX = inPrev.x - vPrevY / vPrevLen
        val ptPrevShiftY = inPrev.y + vPrevX / vPrevLen
        val ptNextShiftX = inNext.x - vNextY / vNextLen
        val ptNextShiftY = inNext.y + vNextX / vNextLen
        val sf = ((ptNextShiftX - ptPrevShiftX) * vNextY - (ptNextShiftY - ptPrevShiftY) * vNextX) /
            (vPrevX * vNextY - vPrevY * vNextX)
        vTransX = ptPrevShiftX + vPrevX * sf - inPt.x
        vTransY = ptPrevShiftY + vPrevY * sf - inPt.y
        val vTransLensq = vTransX * vTransX + vTransY * vTransY
        if (vTransLensq <= 2) return Point2(vTransX, vTransY)
        shrinkBy = sqrt(vTransLensq / 2)
    } else {
        var directionEq = false
        if (vPrevX > Curve2.EPSILON) {
            if (vNextX > Curve2.EPSILON) directionEq = true
        } else {
            if (vPrevX < -Curve2.EPSILON) {
                if (vNextX < -Curve2.EPSILON) directionEq = true
            } else {
                if (sign(vPrevY) == sign(vNextY)) directionEq = true
            }
        }
        if (directionEq) {
            vTransX = -vPrevY
            vTransY = vPrevX
            shrinkBy = sqrt(vPrevLensq)
        } else {
            vTransX = vPrevX
            vTransY = vPrevY
            shrinkBy = sqrt(vPrevLensq / 2)
        }
    }
    return Point2(vTransX / shrinkBy, vTransY / shrinkBy)
}
