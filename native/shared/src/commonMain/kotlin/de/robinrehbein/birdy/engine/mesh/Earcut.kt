package de.robinrehbein.birdy.engine.mesh

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Port of mapbox earcut 3.0 as bundled with three.js 0.186 (`extras/lib/earcut.js`), same
 * node order and decisions so triangle output matches three.js.
 */
object Earcut {
    private class Node(val i: Int, val x: Double, val y: Double) {
        lateinit var prev: Node
        lateinit var next: Node
        var z = 0
        var prevZ: Node? = null
        var nextZ: Node? = null
        var steiner = false
    }

    /** [data] is flat xy pairs, [holeIndices] the vertex index where each hole starts. */
    fun triangulate(data: DoubleArray, holeIndices: IntArray = IntArray(0), dim: Int = 2): IntArray {
        val hasHoles = holeIndices.isNotEmpty()
        val outerLen = if (hasHoles) holeIndices[0] * dim else data.size
        var outerNode = linkedList(data, 0, outerLen, dim, true)
        val triangles = IntList()
        if (outerNode == null || outerNode.next === outerNode.prev) return triangles.toIntArray()
        var minX = 0.0; var minY = 0.0; var invSize = 0.0
        if (hasHoles) outerNode = eliminateHoles(data, holeIndices, outerNode, dim)
        if (data.size > 80 * dim) {
            minX = data[0]; minY = data[1]
            var maxX = minX; var maxY = minY
            var i = dim
            while (i < outerLen) {
                val x = data[i]; val y = data[i + 1]
                if (x < minX) minX = x
                if (y < minY) minY = y
                if (x > maxX) maxX = x
                if (y > maxY) maxY = y
                i += dim
            }
            invSize = max(maxX - minX, maxY - minY)
            invSize = if (invSize != 0.0) 32767 / invSize else 0.0
        }
        earcutLinked(outerNode, triangles, dim, minX, minY, invSize, 0)
        return triangles.toIntArray()
    }

    private fun linkedList(data: DoubleArray, start: Int, end: Int, dim: Int, clockwise: Boolean): Node? {
        var last: Node? = null
        if (clockwise == (signedArea(data, start, end, dim) > 0)) {
            var i = start
            while (i < end) { last = insertNode(i / dim, data[i], data[i + 1], last); i += dim }
        } else {
            var i = end - dim
            while (i >= start) { last = insertNode(i / dim, data[i], data[i + 1], last); i -= dim }
        }
        if (last != null && equals(last, last.next)) {
            removeNode(last)
            last = last.next
        }
        return last
    }

    private fun filterPoints(start: Node?, end0: Node? = null): Node? {
        if (start == null) return start
        var end: Node = end0 ?: start
        var p: Node = start
        var again: Boolean
        do {
            again = false
            if (!p.steiner && (equals(p, p.next) || area(p.prev, p, p.next) == 0.0)) {
                removeNode(p)
                p = p.prev
                end = p
                if (p === p.next) break
                again = true
            } else {
                p = p.next
            }
        } while (again || p !== end)
        return end
    }

    private fun earcutLinked(ear0: Node?, triangles: IntList, dim: Int, minX: Double, minY: Double, invSize: Double, pass: Int) {
        var ear = ear0 ?: return
        if (pass == 0 && invSize != 0.0) indexCurve(ear, minX, minY, invSize)
        var stop = ear
        while (ear.prev !== ear.next) {
            val prev = ear.prev
            val next = ear.next
            if (if (invSize != 0.0) isEarHashed(ear, minX, minY, invSize) else isEar(ear)) {
                triangles.add(prev.i, ear.i, next.i)
                removeNode(ear)
                ear = next.next
                stop = next.next
                continue
            }
            ear = next
            if (ear === stop) {
                when (pass) {
                    0 -> earcutLinked(filterPoints(ear), triangles, dim, minX, minY, invSize, 1)
                    1 -> {
                        val cured = cureLocalIntersections(filterPoints(ear)!!, triangles)
                        earcutLinked(cured, triangles, dim, minX, minY, invSize, 2)
                    }
                    2 -> splitEarcut(ear, triangles, dim, minX, minY, invSize)
                }
                break
            }
        }
    }

    private fun isEar(ear: Node): Boolean {
        val a = ear.prev; val b = ear; val c = ear.next
        if (area(a, b, c) >= 0) return false
        val ax = a.x; val bx = b.x; val cx = c.x; val ay = a.y; val by = b.y; val cy = c.y
        val x0 = min(ax, min(bx, cx)); val y0 = min(ay, min(by, cy))
        val x1 = max(ax, max(bx, cx)); val y1 = max(ay, max(by, cy))
        var p = c.next
        while (p !== a) {
            if (p.x >= x0 && p.x <= x1 && p.y >= y0 && p.y <= y1 &&
                pointInTriangleExceptFirst(ax, ay, bx, by, cx, cy, p.x, p.y) &&
                area(p.prev, p, p.next) >= 0
            ) return false
            p = p.next
        }
        return true
    }

    private fun isEarHashed(ear: Node, minX: Double, minY: Double, invSize: Double): Boolean {
        val a = ear.prev; val b = ear; val c = ear.next
        if (area(a, b, c) >= 0) return false
        val ax = a.x; val bx = b.x; val cx = c.x; val ay = a.y; val by = b.y; val cy = c.y
        val x0 = min(ax, min(bx, cx)); val y0 = min(ay, min(by, cy))
        val x1 = max(ax, max(bx, cx)); val y1 = max(ay, max(by, cy))
        val minZ = zOrder(x0, y0, minX, minY, invSize)
        val maxZ = zOrder(x1, y1, minX, minY, invSize)
        var p = ear.prevZ
        var n = ear.nextZ
        fun blocks(q: Node) = q.x >= x0 && q.x <= x1 && q.y >= y0 && q.y <= y1 && q !== a && q !== c &&
            pointInTriangleExceptFirst(ax, ay, bx, by, cx, cy, q.x, q.y) && area(q.prev, q, q.next) >= 0
        while (p != null && p.z >= minZ && n != null && n.z <= maxZ) {
            if (blocks(p)) return false
            p = p.prevZ
            if (blocks(n)) return false
            n = n.nextZ
        }
        while (p != null && p.z >= minZ) {
            if (blocks(p)) return false
            p = p.prevZ
        }
        while (n != null && n.z <= maxZ) {
            if (blocks(n)) return false
            n = n.nextZ
        }
        return true
    }

    private fun cureLocalIntersections(start0: Node, triangles: IntList): Node? {
        var start = start0
        var p = start
        do {
            val a = p.prev
            val b = p.next.next
            if (!equals(a, b) && intersects(a, p, p.next, b) && locallyInside(a, b) && locallyInside(b, a)) {
                triangles.add(a.i, p.i, b.i)
                removeNode(p)
                removeNode(p.next)
                p = b
                start = b
            }
            p = p.next
        } while (p !== start)
        return filterPoints(p)
    }

    private fun splitEarcut(start: Node, triangles: IntList, dim: Int, minX: Double, minY: Double, invSize: Double) {
        var a = start
        do {
            var b = a.next.next
            while (b !== a.prev) {
                if (a.i != b.i && isValidDiagonal(a, b)) {
                    var c: Node? = splitPolygon(a, b)
                    val a2 = filterPoints(a, a.next)
                    c = filterPoints(c, c!!.next)
                    earcutLinked(a2, triangles, dim, minX, minY, invSize, 0)
                    earcutLinked(c, triangles, dim, minX, minY, invSize, 0)
                    return
                }
                b = b.next
            }
            a = a.next
        } while (a !== start)
    }

    private fun eliminateHoles(data: DoubleArray, holeIndices: IntArray, outerNode0: Node, dim: Int): Node {
        var outerNode = outerNode0
        val queue = ArrayList<Node>()
        for (i in holeIndices.indices) {
            val start = holeIndices[i] * dim
            val end = if (i < holeIndices.size - 1) holeIndices[i + 1] * dim else data.size
            val list = linkedList(data, start, end, dim, false) ?: continue
            if (list === list.next) list.steiner = true
            queue.add(getLeftmost(list))
        }
        queue.sortWith { a, b -> compareXYSlope(a, b).let { if (it < 0) -1 else if (it > 0) 1 else 0 } }
        for (h in queue) outerNode = eliminateHole(h, outerNode)
        return outerNode
    }

    private fun compareXYSlope(a: Node, b: Node): Double {
        var result = a.x - b.x
        if (result == 0.0) {
            result = a.y - b.y
            if (result == 0.0) {
                val aSlope = (a.next.y - a.y) / (a.next.x - a.x)
                val bSlope = (b.next.y - b.y) / (b.next.x - b.x)
                result = aSlope - bSlope
            }
        }
        return result
    }

    private fun eliminateHole(hole: Node, outerNode: Node): Node {
        val bridge = findHoleBridge(hole, outerNode) ?: return outerNode
        val bridgeReverse = splitPolygon(bridge, hole)
        filterPoints(bridgeReverse, bridgeReverse.next)
        return filterPoints(bridge, bridge.next)!!
    }

    private fun findHoleBridge(hole: Node, outerNode: Node): Node? {
        var p = outerNode
        val hx = hole.x
        val hy = hole.y
        var qx = Double.NEGATIVE_INFINITY
        var m: Node? = null
        if (equals(hole, p)) return p
        do {
            if (equals(hole, p.next)) return p.next
            else if (hy <= p.y && hy >= p.next.y && p.next.y != p.y) {
                val x = p.x + (hy - p.y) * (p.next.x - p.x) / (p.next.y - p.y)
                if (x <= hx && x > qx) {
                    qx = x
                    m = if (p.x < p.next.x) p else p.next
                    if (x == hx) return m
                }
            }
            p = p.next
        } while (p !== outerNode)
        if (m == null) return null
        val stop: Node = m
        val mx = m.x
        val my = m.y
        var tanMin = Double.POSITIVE_INFINITY
        p = m
        do {
            if (hx >= p.x && p.x >= mx && hx != p.x &&
                pointInTriangle(if (hy < my) hx else qx, hy, mx, my, if (hy < my) qx else hx, hy, p.x, p.y)
            ) {
                val tan = abs(hy - p.y) / (hx - p.x)
                val mm = m!!
                if (locallyInside(p, hole) &&
                    (tan < tanMin || (tan == tanMin && (p.x > mm.x || (p.x == mm.x && sectorContainsSector(mm, p)))))
                ) {
                    m = p
                    tanMin = tan
                }
            }
            p = p.next
        } while (p !== stop)
        return m
    }

    private fun sectorContainsSector(m: Node, p: Node): Boolean =
        area(m.prev, m, p.prev) < 0 && area(p.next, m, m.next) < 0

    private fun indexCurve(start: Node, minX: Double, minY: Double, invSize: Double) {
        var p = start
        do {
            if (p.z == 0) p.z = zOrder(p.x, p.y, minX, minY, invSize)
            p.prevZ = p.prev
            p.nextZ = p.next
            p = p.next
        } while (p !== start)
        p.prevZ!!.nextZ = null
        p.prevZ = null
        sortLinked(p)
    }

    private fun sortLinked(list0: Node): Node? {
        var list: Node? = list0
        var numMerges: Int
        var inSize = 1
        do {
            var p = list
            var e: Node
            list = null
            var tail: Node? = null
            numMerges = 0
            while (p != null) {
                numMerges++
                var q: Node? = p
                var pSize = 0
                for (i in 0 until inSize) {
                    pSize++
                    q = q!!.nextZ
                    if (q == null) break
                }
                var qSize = inSize
                while (pSize > 0 || (qSize > 0 && q != null)) {
                    if (pSize != 0 && (qSize == 0 || q == null || p!!.z <= q.z)) {
                        e = p!!
                        p = p.nextZ
                        pSize--
                    } else {
                        e = q!!
                        q = q.nextZ
                        qSize--
                    }
                    if (tail != null) tail.nextZ = e else list = e
                    e.prevZ = tail
                    tail = e
                }
                p = q
            }
            tail!!.nextZ = null
            inSize *= 2
        } while (numMerges > 1)
        return list
    }

    private fun zOrder(x0: Double, y0: Double, minX: Double, minY: Double, invSize: Double): Int {
        var x = ((x0 - minX) * invSize).toInt()
        var y = ((y0 - minY) * invSize).toInt()
        x = (x or (x shl 8)) and 0x00FF00FF
        x = (x or (x shl 4)) and 0x0F0F0F0F
        x = (x or (x shl 2)) and 0x33333333
        x = (x or (x shl 1)) and 0x55555555
        y = (y or (y shl 8)) and 0x00FF00FF
        y = (y or (y shl 4)) and 0x0F0F0F0F
        y = (y or (y shl 2)) and 0x33333333
        y = (y or (y shl 1)) and 0x55555555
        return x or (y shl 1)
    }

    private fun getLeftmost(start: Node): Node {
        var p = start
        var leftmost = start
        do {
            if (p.x < leftmost.x || (p.x == leftmost.x && p.y < leftmost.y)) leftmost = p
            p = p.next
        } while (p !== start)
        return leftmost
    }

    private fun pointInTriangle(ax: Double, ay: Double, bx: Double, by: Double, cx: Double, cy: Double, px: Double, py: Double): Boolean =
        (cx - px) * (ay - py) >= (ax - px) * (cy - py) &&
            (ax - px) * (by - py) >= (bx - px) * (ay - py) &&
            (bx - px) * (cy - py) >= (cx - px) * (by - py)

    private fun pointInTriangleExceptFirst(ax: Double, ay: Double, bx: Double, by: Double, cx: Double, cy: Double, px: Double, py: Double): Boolean =
        !(ax == px && ay == py) && pointInTriangle(ax, ay, bx, by, cx, cy, px, py)

    private fun isValidDiagonal(a: Node, b: Node): Boolean =
        a.next.i != b.i && a.prev.i != b.i && !intersectsPolygon(a, b) &&
            (locallyInside(a, b) && locallyInside(b, a) && middleInside(a, b) &&
                (area(a.prev, a, b.prev) != 0.0 || area(a, b.prev, b) != 0.0) ||
                equals(a, b) && area(a.prev, a, a.next) > 0 && area(b.prev, b, b.next) > 0)

    private fun area(p: Node, q: Node, r: Node): Double = (q.y - p.y) * (r.x - q.x) - (q.x - p.x) * (r.y - q.y)

    private fun equals(p1: Node, p2: Node): Boolean = p1.x == p2.x && p1.y == p2.y

    private fun intersects(p1: Node, q1: Node, p2: Node, q2: Node): Boolean {
        val o1 = sign(area(p1, q1, p2))
        val o2 = sign(area(p1, q1, q2))
        val o3 = sign(area(p2, q2, p1))
        val o4 = sign(area(p2, q2, q1))
        if (o1 != o2 && o3 != o4) return true
        if (o1 == 0 && onSegment(p1, p2, q1)) return true
        if (o2 == 0 && onSegment(p1, q2, q1)) return true
        if (o3 == 0 && onSegment(p2, p1, q2)) return true
        if (o4 == 0 && onSegment(p2, q1, q2)) return true
        return false
    }

    private fun onSegment(p: Node, q: Node, r: Node): Boolean =
        q.x <= max(p.x, r.x) && q.x >= min(p.x, r.x) && q.y <= max(p.y, r.y) && q.y >= min(p.y, r.y)

    private fun sign(num: Double): Int = if (num > 0) 1 else if (num < 0) -1 else 0

    private fun intersectsPolygon(a: Node, b: Node): Boolean {
        var p = a
        do {
            if (p.i != a.i && p.next.i != a.i && p.i != b.i && p.next.i != b.i && intersects(p, p.next, a, b)) return true
            p = p.next
        } while (p !== a)
        return false
    }

    private fun locallyInside(a: Node, b: Node): Boolean =
        if (area(a.prev, a, a.next) < 0) area(a, b, a.next) >= 0 && area(a, a.prev, b) >= 0
        else area(a, b, a.prev) < 0 || area(a, a.next, b) < 0

    private fun middleInside(a: Node, b: Node): Boolean {
        var p = a
        var inside = false
        val px = (a.x + b.x) / 2
        val py = (a.y + b.y) / 2
        do {
            if (((p.y > py) != (p.next.y > py)) && p.next.y != p.y &&
                (px < (p.next.x - p.x) * (py - p.y) / (p.next.y - p.y) + p.x)
            ) inside = !inside
            p = p.next
        } while (p !== a)
        return inside
    }

    private fun splitPolygon(a: Node, b: Node): Node {
        val a2 = Node(a.i, a.x, a.y)
        val b2 = Node(b.i, b.x, b.y)
        val an = a.next
        val bp = b.prev
        a.next = b
        b.prev = a
        a2.next = an
        an.prev = a2
        b2.next = a2
        a2.prev = b2
        bp.next = b2
        b2.prev = bp
        return b2
    }

    private fun insertNode(i: Int, x: Double, y: Double, last: Node?): Node {
        val p = Node(i, x, y)
        if (last == null) {
            p.prev = p
            p.next = p
        } else {
            p.next = last.next
            p.prev = last
            last.next.prev = p
            last.next = p
        }
        return p
    }

    private fun removeNode(p: Node) {
        p.next.prev = p.prev
        p.prev.next = p.next
        p.prevZ?.nextZ = p.nextZ
        p.nextZ?.prevZ = p.prevZ
    }

    private fun signedArea(data: DoubleArray, start: Int, end: Int, dim: Int): Double {
        var sum = 0.0
        var j = end - dim
        var i = start
        while (i < end) {
            sum += (data[j] - data[i]) * (data[i + 1] + data[j + 1])
            j = i
            i += dim
        }
        return sum
    }
}
