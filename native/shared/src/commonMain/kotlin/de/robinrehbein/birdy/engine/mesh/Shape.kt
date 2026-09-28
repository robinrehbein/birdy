package de.robinrehbein.birdy.engine.mesh

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** 2D point used by the shape/extrude pipeline (three.js `Vector2`, doubles like JS). */
class Point2(var x: Double, var y: Double) {
    fun equalsPoint(o: Point2): Boolean = x == o.x && y == o.y
    fun copy(): Point2 = Point2(x, y)
    override fun toString(): String = "Point2($x, $y)"
}

/** A 2D curve segment of a [Path] (three.js `Curve` subclasses used by `Path`). */
sealed class Curve2 {
    abstract fun getPoint(t: Double): Point2

    /** three.js `Curve.getPoints(divisions)`. */
    fun getPoints(divisions: Int): List<Point2> = (0..divisions).map { getPoint(it.toDouble() / divisions) }

    class Line(val v1: Point2, val v2: Point2) : Curve2() {
        override fun getPoint(t: Double): Point2 =
            if (t == 1.0) v2.copy() else Point2((v2.x - v1.x) * t + v1.x, (v2.y - v1.y) * t + v1.y)
    }

    class QuadraticBezier(val v0: Point2, val v1: Point2, val v2: Point2) : Curve2() {
        override fun getPoint(t: Double): Point2 = Point2(quad(t, v0.x, v1.x, v2.x), quad(t, v0.y, v1.y, v2.y))
    }

    class CubicBezier(val v0: Point2, val v1: Point2, val v2: Point2, val v3: Point2) : Curve2() {
        override fun getPoint(t: Double): Point2 =
            Point2(cubic(t, v0.x, v1.x, v2.x, v3.x), cubic(t, v0.y, v1.y, v2.y, v3.y))
    }

    class Ellipse(
        val aX: Double, val aY: Double, val xRadius: Double, val yRadius: Double,
        val aStartAngle: Double, val aEndAngle: Double, val aClockwise: Boolean, val aRotation: Double,
    ) : Curve2() {
        override fun getPoint(t: Double): Point2 {
            val twoPi = PI * 2
            var deltaAngle = aEndAngle - aStartAngle
            val samePoints = abs(deltaAngle) < EPSILON
            while (deltaAngle < 0) deltaAngle += twoPi
            while (deltaAngle > twoPi) deltaAngle -= twoPi
            if (deltaAngle < EPSILON) deltaAngle = if (samePoints) 0.0 else twoPi
            if (aClockwise && !samePoints) deltaAngle = if (deltaAngle == twoPi) -twoPi else deltaAngle - twoPi
            val angle = aStartAngle + t * deltaAngle
            var x = aX + xRadius * cos(angle)
            var y = aY + yRadius * sin(angle)
            if (aRotation != 0.0) {
                val c = cos(aRotation); val s = sin(aRotation)
                val tx = x - aX; val ty = y - aY
                x = tx * c - ty * s + aX
                y = tx * s + ty * c + aY
            }
            return Point2(x, y)
        }
    }

    companion object {
        /** JS `Number.EPSILON`. */
        const val EPSILON = 2.220446049250313e-16

        private fun quad(t: Double, p0: Double, p1: Double, p2: Double): Double {
            val k = 1 - t
            return k * k * p0 + 2 * (1 - t) * t * p1 + t * t * p2
        }

        private fun cubic(t: Double, p0: Double, p1: Double, p2: Double, p3: Double): Double {
            val k = 1 - t
            return k * k * k * p0 + 3 * k * k * t * p1 + 3 * (1 - t) * t * t * p2 + t * t * t * p3
        }
    }
}

/**
 * three.js `Path`: a sequence of curves built with canvas-like commands. Coordinates are Doubles,
 * as in JS, so tessellation matches three.js.
 */
open class Path {
    val curves = ArrayList<Curve2>()
    var currentPoint = Point2(0.0, 0.0)
        private set
    /** three.js `CurvePath.autoClose`. */
    var autoClose = false

    fun moveTo(x: Double, y: Double): Path { currentPoint = Point2(x, y); return this }

    fun lineTo(x: Double, y: Double): Path {
        curves.add(Curve2.Line(currentPoint.copy(), Point2(x, y)))
        currentPoint = Point2(x, y)
        return this
    }

    fun quadraticCurveTo(cpx: Double, cpy: Double, x: Double, y: Double): Path {
        curves.add(Curve2.QuadraticBezier(currentPoint.copy(), Point2(cpx, cpy), Point2(x, y)))
        currentPoint = Point2(x, y)
        return this
    }

    fun bezierCurveTo(cp1x: Double, cp1y: Double, cp2x: Double, cp2y: Double, x: Double, y: Double): Path {
        curves.add(Curve2.CubicBezier(currentPoint.copy(), Point2(cp1x, cp1y), Point2(cp2x, cp2y), Point2(x, y)))
        currentPoint = Point2(x, y)
        return this
    }

    /** Arc relative to the current point (three.js `arc`). */
    fun arc(x: Double, y: Double, radius: Double, startAngle: Double, endAngle: Double, clockwise: Boolean = false): Path =
        absarc(x + currentPoint.x, y + currentPoint.y, radius, startAngle, endAngle, clockwise)

    fun absarc(x: Double, y: Double, radius: Double, startAngle: Double, endAngle: Double, clockwise: Boolean = false): Path =
        absellipse(x, y, radius, radius, startAngle, endAngle, clockwise)

    fun ellipse(
        x: Double, y: Double, xRadius: Double, yRadius: Double,
        startAngle: Double, endAngle: Double, clockwise: Boolean = false, rotation: Double = 0.0,
    ): Path = absellipse(x + currentPoint.x, y + currentPoint.y, xRadius, yRadius, startAngle, endAngle, clockwise, rotation)

    fun absellipse(
        x: Double, y: Double, xRadius: Double, yRadius: Double,
        startAngle: Double, endAngle: Double, clockwise: Boolean = false, rotation: Double = 0.0,
    ): Path {
        val curve = Curve2.Ellipse(x, y, xRadius, yRadius, startAngle, endAngle, clockwise, rotation)
        if (curves.isNotEmpty()) {
            val first = curve.getPoint(0.0)
            if (!first.equalsPoint(currentPoint)) lineTo(first.x, first.y)
        }
        curves.add(curve)
        currentPoint = curve.getPoint(1.0)
        return this
    }

    /** Adds a line back to the start if the path is open (three.js `CurvePath.closePath`). */
    fun closePath(): Path {
        if (curves.isEmpty()) return this
        val start = curves[0].getPoint(0.0)
        val end = curves[curves.size - 1].getPoint(1.0)
        if (!start.equalsPoint(end)) curves.add(Curve2.Line(end, start))
        return this
    }

    /** three.js `CurvePath.getPoints(divisions)`: per-curve resolution, consecutive duplicates dropped. */
    fun getPoints(divisions: Int = 12): MutableList<Point2> {
        val points = ArrayList<Point2>()
        var last: Point2? = null
        for (curve in curves) {
            val resolution = when (curve) {
                is Curve2.Ellipse -> divisions * 2
                is Curve2.Line -> 1
                else -> divisions
            }
            for (p in curve.getPoints(resolution)) {
                if (last != null && last.equalsPoint(p)) continue
                points.add(p)
                last = p
            }
        }
        if (autoClose && points.size > 1 && !points[points.size - 1].equalsPoint(points[0])) points.add(points[0])
        return points
    }

    fun setFromPoints(points: List<Point2>): Path {
        moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
        return this
    }
}

/** three.js `Shape`: an outer [Path] with optional [holes]. */
class Shape : Path() {
    val holes = ArrayList<Path>()

    class Points(val shape: MutableList<Point2>, val holes: MutableList<MutableList<Point2>>)

    fun extractPoints(divisions: Int): Points = Points(getPoints(divisions), holes.mapTo(ArrayList()) { it.getPoints(divisions) })
}

/** three.js `ShapeUtils`. */
object ShapeUtils {
    fun area(contour: List<Point2>): Double {
        val n = contour.size
        var a = 0.0
        var p = n - 1
        var q = 0
        while (q < n) {
            a += contour[p].x * contour[q].y - contour[q].x * contour[p].y
            p = q++
        }
        return a * 0.5
    }

    fun isClockWise(pts: List<Point2>): Boolean = area(pts) < 0

    /** Triangulates [contour] with [holes] via earcut; indices refer to contour followed by holes. Mutates like three.js. */
    fun triangulateShape(contour: MutableList<Point2>, holes: List<MutableList<Point2>>): List<IntArray> {
        removeDupEndPts(contour)
        val vertices = DoubleList()
        for (p in contour) vertices.add(p.x, p.y)
        val holeIndices = IntList()
        var holeIndex = contour.size
        holes.forEach { removeDupEndPts(it) }
        for (h in holes) {
            holeIndices.add(holeIndex)
            holeIndex += h.size
            for (p in h) vertices.add(p.x, p.y)
        }
        val data = DoubleArray(vertices.size) { vertices[it] }
        val triangles = Earcut.triangulate(data, holeIndices.toIntArray())
        return (0 until triangles.size / 3).map { intArrayOf(triangles[it * 3], triangles[it * 3 + 1], triangles[it * 3 + 2]) }
    }

    private fun removeDupEndPts(points: MutableList<Point2>) {
        val l = points.size
        if (l > 2 && points[l - 1].equalsPoint(points[0])) points.removeAt(l - 1)
    }
}
