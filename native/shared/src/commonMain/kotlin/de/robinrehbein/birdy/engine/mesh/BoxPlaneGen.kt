package de.robinrehbein.birdy.engine.mesh

import de.robinrehbein.birdy.engine.scene.Geometry

/** three.js `BoxGeometry` (same plane order, vertex/index layout and UVs). */
internal fun buildBox(width: Double, height: Double, depth: Double, widthSegments: Int, heightSegments: Int, depthSegments: Int): Geometry {
    val pos = DoubleList(); val nor = DoubleList(); val uv = DoubleList(); val idx = IntList()
    var numberOfVertices = 0
    val vector = DoubleArray(3)

    fun buildPlane(u: Int, v: Int, w: Int, udir: Double, vdir: Double, pw: Double, ph: Double, pd: Double, gridX: Int, gridY: Int) {
        val segmentWidth = pw / gridX
        val segmentHeight = ph / gridY
        val widthHalf = pw / 2
        val heightHalf = ph / 2
        val depthHalf = pd / 2
        val gridX1 = gridX + 1
        val gridY1 = gridY + 1
        var vertexCounter = 0
        for (iy in 0 until gridY1) {
            val y = iy * segmentHeight - heightHalf
            for (ix in 0 until gridX1) {
                val x = ix * segmentWidth - widthHalf
                vector[u] = x * udir; vector[v] = y * vdir; vector[w] = depthHalf
                pos.add(vector[0], vector[1], vector[2])
                vector[u] = 0.0; vector[v] = 0.0; vector[w] = if (pd > 0) 1.0 else -1.0
                nor.add(vector[0], vector[1], vector[2])
                uv.add(ix.toDouble() / gridX, 1 - (iy.toDouble() / gridY))
                vertexCounter++
            }
        }
        for (iy in 0 until gridY) for (ix in 0 until gridX) {
            val a = numberOfVertices + ix + gridX1 * iy
            val b = numberOfVertices + ix + gridX1 * (iy + 1)
            val c = numberOfVertices + (ix + 1) + gridX1 * (iy + 1)
            val d = numberOfVertices + (ix + 1) + gridX1 * iy
            idx.add(a, b, d)
            idx.add(b, c, d)
        }
        numberOfVertices += vertexCounter
    }

    val x = 0; val y = 1; val z = 2
    buildPlane(z, y, x, -1.0, -1.0, depth, height, width, depthSegments, heightSegments)
    buildPlane(z, y, x, 1.0, -1.0, depth, height, -width, depthSegments, heightSegments)
    buildPlane(x, z, y, 1.0, 1.0, width, depth, height, widthSegments, depthSegments)
    buildPlane(x, z, y, 1.0, -1.0, width, depth, -height, widthSegments, depthSegments)
    buildPlane(x, y, z, 1.0, -1.0, width, height, depth, widthSegments, heightSegments)
    buildPlane(x, y, z, -1.0, -1.0, width, height, -depth, widthSegments, heightSegments)
    return indexedGeometry(pos, nor, uv, idx)
}

/** three.js `PlaneGeometry` (XY plane facing +Z). */
internal fun buildPlane(width: Double, height: Double, widthSegments: Int, heightSegments: Int): Geometry {
    val widthHalf = width / 2
    val heightHalf = height / 2
    val gridX = widthSegments
    val gridY = heightSegments
    val gridX1 = gridX + 1
    val gridY1 = gridY + 1
    val segmentWidth = width / gridX
    val segmentHeight = height / gridY
    val pos = DoubleList(); val nor = DoubleList(); val uv = DoubleList(); val idx = IntList()
    for (iy in 0 until gridY1) {
        val y = iy * segmentHeight - heightHalf
        for (ix in 0 until gridX1) {
            val x = ix * segmentWidth - widthHalf
            pos.add(x, -y, 0.0)
            nor.add(0.0, 0.0, 1.0)
            uv.add(ix.toDouble() / gridX, 1 - (iy.toDouble() / gridY))
        }
    }
    for (iy in 0 until gridY) for (ix in 0 until gridX) {
        val a = ix + gridX1 * iy
        val b = ix + gridX1 * (iy + 1)
        val c = (ix + 1) + gridX1 * (iy + 1)
        val d = (ix + 1) + gridX1 * iy
        idx.add(a, b, d)
        idx.add(b, c, d)
    }
    return indexedGeometry(pos, nor, uv, idx)
}
