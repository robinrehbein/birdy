package de.robinrehbein.birdy.engine.gl

import de.robinrehbein.birdy.engine.scene.Geometry
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Object-space bounding sphere of a [Geometry] like three.js `computeBoundingSphere`
 * (centre = bounding-box centre, radius = farthest vertex). Recomputed when the version changes.
 */
internal class GeometryBounds {
    var cx = 0f; var cy = 0f; var cz = 0f; var radius = 0f
    private var version = -1
    private var vertexCount = -1
    var lastUsed = 0

    fun sync(geo: Geometry) {
        val p = geo.positions
        if (version == geo.version && vertexCount == p.size) return
        version = geo.version
        vertexCount = p.size
        if (p.size < 3) { cx = 0f; cy = 0f; cz = 0f; radius = 0f; return }
        var minX = Float.POSITIVE_INFINITY; var minY = minX; var minZ = minX
        var maxX = Float.NEGATIVE_INFINITY; var maxY = maxX; var maxZ = maxX
        var i = 0
        while (i + 2 < p.size) {
            val x = p[i]; val y = p[i + 1]; val z = p[i + 2]
            if (x < minX) minX = x; if (x > maxX) maxX = x
            if (y < minY) minY = y; if (y > maxY) maxY = y
            if (z < minZ) minZ = z; if (z > maxZ) maxZ = z
            i += 3
        }
        cx = (minX + maxX) * 0.5f; cy = (minY + maxY) * 0.5f; cz = (minZ + maxZ) * 0.5f
        var r2 = 0f
        i = 0
        while (i + 2 < p.size) {
            val dx = p[i] - cx; val dy = p[i + 1] - cy; val dz = p[i + 2] - cz
            r2 = max(r2, dx * dx + dy * dy + dz * dz)
            i += 3
        }
        radius = sqrt(r2)
    }
}
