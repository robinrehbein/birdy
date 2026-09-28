package de.robinrehbein.birdy.engine.scene

import de.robinrehbein.birdy.engine.math.Color
import de.robinrehbein.birdy.engine.math.Mat4
import de.robinrehbein.birdy.engine.math.Vec3

/** Drawable node: [geometry] rendered with [material] (three.js `Mesh`). */
open class Mesh(var geometry: Geometry, var material: Material, name: String = "") : Node(name) {
    /** Non-null turns this into an instanced draw (three.js `InstancedMesh`). */
    var instances: InstanceData? = null

    /**
     * Bristle direction for `bake(root, spikes = true)` (world.js `userData.spikeDir`): every vertex of
     * this mesh with local Y > 0.01 gets this vector in the baked `spike` attribute.
     */
    var spikeDir: Vec3? = null
}

/**
 * Per-instance transforms (+ optional colours) for instanced meshes. Instance matrices are
 * relative to the owning mesh's world matrix. Call [markDirty] after edits.
 */
class InstanceData(val capacity: Int, withColors: Boolean = false) {
    /** 16 floats (column-major) per instance. */
    val matrices = FloatArray(capacity * 16)
    /** rgb per instance (linear, like three.js `instanceColor`), or null. */
    val colors: FloatArray? = if (withColors) FloatArray(capacity * 3) { 1f } else null
    /** Number of instances drawn (three.js `InstancedMesh.count`). */
    var count = capacity

    var version = 0
        private set

    fun markDirty() { version++ }

    fun setMatrix(i: Int, m: Mat4) { m.e.copyInto(matrices, i * 16) }

    fun getMatrix(i: Int, target: Mat4): Mat4 = target.fromArray(matrices, i * 16)

    /** three.js `setColorAt`: stores the colour's linear components. */
    fun setColor(i: Int, c: Color) {
        val a = colors ?: return
        a[i * 3] = c.linearR; a[i * 3 + 1] = c.linearG; a[i * 3 + 2] = c.linearB
    }
}
