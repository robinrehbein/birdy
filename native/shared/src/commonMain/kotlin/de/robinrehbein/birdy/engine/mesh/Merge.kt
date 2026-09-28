package de.robinrehbein.birdy.engine.mesh

import de.robinrehbein.birdy.engine.scene.Geometry
import de.robinrehbein.birdy.engine.scene.VertexAttribute

/**
 * three.js `BufferGeometryUtils.mergeGeometries(geometries)`: concatenates attributes (and offsets
 * indices). Like three.js, all inputs must be either indexed or non-indexed and carry the same
 * attribute set; otherwise an [IllegalArgumentException] is thrown.
 */
fun mergeGeometries(geometries: List<Geometry>): Geometry {
    require(geometries.isNotEmpty()) { "mergeGeometries: no geometries" }
    val first = geometries[0]
    val indexed = first.indices != null
    for ((i, g) in geometries.withIndex()) {
        require((g.indices != null) == indexed) { "mergeGeometries: geometry $i indexed mismatch" }
        require((g.normals != null) == (first.normals != null)) { "mergeGeometries: geometry $i normal attribute mismatch" }
        require((g.colors != null) == (first.colors != null)) { "mergeGeometries: geometry $i color attribute mismatch" }
        require((g.uvs != null) == (first.uvs != null)) { "mergeGeometries: geometry $i uv attribute mismatch" }
        require(g.extraAttributes.keys == first.extraAttributes.keys) { "mergeGeometries: geometry $i extra attribute mismatch" }
        for ((k, a) in g.extraAttributes) require(a.itemSize == first.extraAttributes.getValue(k).itemSize) { "mergeGeometries: $k itemSize mismatch" }
    }
    fun concat(get: (Geometry) -> FloatArray?): FloatArray? {
        if (get(first) == null) return null
        val out = FloatArray(geometries.sumOf { get(it)!!.size })
        var o = 0
        for (g in geometries) { val a = get(g)!!; a.copyInto(out, o); o += a.size }
        return out
    }
    val indices = if (indexed) {
        val out = IntArray(geometries.sumOf { it.indices!!.size })
        var o = 0
        var offset = 0
        for (g in geometries) {
            for (v in g.indices!!) out[o++] = v + offset
            offset += g.vertexCount
        }
        out
    } else null
    val merged = Geometry(concat { it.positions }!!, concat { it.normals }, concat { it.colors }, concat { it.uvs }, indices)
    for ((k, a) in first.extraAttributes) {
        merged.extraAttributes[k] = VertexAttribute(concat { it.extraAttributes.getValue(k).data }!!, a.itemSize)
    }
    return merged
}
