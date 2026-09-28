package de.robinrehbein.birdy.engine.scene

/**
 * Indexed or non-indexed triangle geometry (three.js `BufferGeometry` equivalent).
 *
 * Attribute arrays are plain Kotlin arrays so they can be built in commonMain and uploaded by any
 * backend. After mutating an array in place call [markDirty] so the backend re-uploads it
 * (three.js `attribute.needsUpdate = true`).
 */
class Geometry(
    /** xyz per vertex. */
    var positions: FloatArray,
    /** xyz per vertex, or null for flat/unlit materials. */
    var normals: FloatArray? = null,
    /**
     * rgb per vertex in LINEAR space, i.e. the values three.js stores (THREE.Color components are
     * linear after `setHex` because ColorManagement is on). Used when the material has vertexColors.
     */
    var colors: FloatArray? = null,
    /** uv per vertex. */
    var uvs: FloatArray? = null,
    /** Triangle list indices, or null for non-indexed triangles. */
    var indices: IntArray? = null,
) {
    /** Extra per-vertex attributes for [ShaderPatch] code, by GLSL attribute name. */
    val extraAttributes = LinkedHashMap<String, VertexAttribute>()

    val vertexCount: Int get() = positions.size / 3

    /** Incremented on every [markDirty]; backends compare against their uploaded version. */
    var version = 0
        private set

    fun markDirty() { version++ }

    /** Optional draw range (in indices, or vertices if non-indexed); -1 = everything. */
    var drawCount = -1
}

/** A named extra vertex attribute: [itemSize] floats per vertex. */
class VertexAttribute(val data: FloatArray, val itemSize: Int)
