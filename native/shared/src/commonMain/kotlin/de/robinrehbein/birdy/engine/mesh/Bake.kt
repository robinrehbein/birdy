package de.robinrehbein.birdy.engine.mesh

import de.robinrehbein.birdy.engine.math.DMat
import de.robinrehbein.birdy.engine.math.Quat
import de.robinrehbein.birdy.engine.scene.Geometry
import de.robinrehbein.birdy.engine.scene.Mesh
import de.robinrehbein.birdy.engine.scene.Node

/** Name of the baked bristle attribute (`attribute vec3 spike` in the cactus shader). */
const val SPIKE_ATTRIBUTE = "spike"

/**
 * world.js `bakeGroup(root, spikes)`: flattens every [Mesh] below (and including) [root] into one
 * non-indexed geometry. Each mesh is transformed into [root]'s local space, gets its material colour as a
 * constant linear vertex colour, and keeps only position, normal and colour (plus `spike` when [spikes]:
 * vertices with local Y > 0.01 of meshes with [Mesh.spikeDir] carry that direction, all others zero).
 * Invisible meshes are included, like three.js `traverse`. Instancing on source meshes is ignored.
 */
fun bake(root: Node, spikes: Boolean = false): Geometry {
    val parts = ArrayList<Geometry>()
    collect(root, root, DMat.identity(), spikes, parts)
    require(parts.isNotEmpty()) { "bake: no meshes below ${root.name}" }
    return mergeGeometries(parts)
}

private fun collect(node: Node, root: Node, relative: DoubleArray, spikes: Boolean, out: MutableList<Geometry>) {
    val m = if (node === root) relative else DMat.mul(relative, localMatrixD(node))
    if (node is Mesh) out.add(bakeMesh(node, m, spikes))
    for (c in node.children) collect(c, root, m, spikes, out)
}

private fun localMatrixD(n: Node): DoubleArray {
    if (!n.matrixAutoUpdate) return DoubleArray(16) { n.localMatrix.e[it].toDouble() }
    val q = n.quaternionOverride
    val qx: Double; val qy: Double; val qz: Double; val qw: Double
    if (q != null) {
        qx = q.x.toDouble(); qy = q.y.toDouble(); qz = q.z.toDouble(); qw = q.w.toDouble()
    } else {
        val t = Quat().setFromEulerXYZ(n.rotation.x, n.rotation.y, n.rotation.z)
        qx = t.x.toDouble(); qy = t.y.toDouble(); qz = t.z.toDouble(); qw = t.w.toDouble()
    }
    return DMat.compose(
        n.position.x.toDouble(), n.position.y.toDouble(), n.position.z.toDouble(),
        qx, qy, qz, qw,
        n.scale.x.toDouble(), n.scale.y.toDouble(), n.scale.z.toDouble(),
    )
}

private fun bakeMesh(mesh: Mesh, m: DoubleArray, spikes: Boolean): Geometry {
    val src = mesh.geometry.toNonIndexed()
    val local = if (spikes) src.positions.copyOf() else null
    val g = Geometry(src.positions, src.normals, null, null, null)
    if (g.normals == null) g.computeVertexNormals()
    g.applyMatrixD(m)
    g.setVertexColor(mesh.material.color)
    if (spikes) {
        val sp = FloatArray(g.vertexCount * 3)
        val dir = mesh.spikeDir
        if (dir != null) {
            for (i in 0 until g.vertexCount) if (local!![i * 3 + 1].toDouble() > 0.01) {
                sp[i * 3] = dir.x; sp[i * 3 + 1] = dir.y; sp[i * 3 + 2] = dir.z
            }
        }
        g.setAttribute(SPIKE_ATTRIBUTE, sp, 3)
    }
    return g
}
