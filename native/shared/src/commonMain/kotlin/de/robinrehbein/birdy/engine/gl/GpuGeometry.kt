package de.robinrehbein.birdy.engine.gl

import de.robinrehbein.birdy.engine.scene.Geometry
import de.robinrehbein.birdy.engine.scene.InstanceData

/**
 * GPU copy of a [Geometry]: one VAO with a VBO per attribute, re-uploaded when
 * [Geometry.version] changes. Instance buffers live in [GpuInstances] and are attached to the VAO
 * per draw, so several instanced meshes can share one geometry.
 */
internal class GpuGeometry(private val gl: GlApi, private val geo: Geometry) {
    val vao = gl.genVertexArray()
    private val buffers = HashMap<String, Int>()
    private var ebo = 0
    private var uploadedVersion = -1
    private var instancedAttribs = false
    var lastUsed = 0

    /** Names of [Geometry.extraAttributes] in location order, and a cache key for them. */
    var extraNames: List<String> = emptyList()
        private set
    var extrasKey = ""
        private set

    val indexed: Boolean get() = geo.indices != null
    var elementCount = 0
        private set

    fun syncIfDirty() {
        if (uploadedVersion == geo.version && elementCount == expectedCount()) return
        gl.bindVertexArray(vao)
        attribute("position", Attr.POSITION, geo.positions, 3)
        val normals = geo.normals
        if (normals != null) attribute("normal", Attr.NORMAL, normals, 3) else disable(Attr.NORMAL)
        val colors = geo.colors
        if (colors != null) attribute("color", Attr.COLOR, colors, 3) else disable(Attr.COLOR)
        val uvs = geo.uvs
        if (uvs != null) attribute("uv", Attr.UV, uvs, 2) else disable(Attr.UV)
        extraNames = geo.extraAttributes.keys.toList()
        extrasKey = extraNames.joinToString(",")
        var loc = Attr.FIRST_EXTRA
        for ((name, attr) in geo.extraAttributes) attribute("x:$name", loc++, attr.data, attr.itemSize)
        val idx = geo.indices
        if (idx != null) {
            if (ebo == 0) ebo = gl.genBuffer()
            gl.bindBuffer(GL.ELEMENT_ARRAY_BUFFER, ebo)
            gl.bufferData(GL.ELEMENT_ARRAY_BUFFER, idx, idx.size, GL.STATIC_DRAW)
        }
        elementCount = expectedCount()
        gl.bindVertexArray(0)
        uploadedVersion = geo.version
    }

    private fun expectedCount() = geo.indices?.size ?: geo.vertexCount

    private fun disable(location: Int) = gl.disableVertexAttribArray(location)

    private fun attribute(key: String, location: Int, data: FloatArray, size: Int) {
        val buf = buffers.getOrPut(key) { gl.genBuffer() }
        gl.bindBuffer(GL.ARRAY_BUFFER, buf)
        gl.bufferData(GL.ARRAY_BUFFER, data, data.size, GL.STATIC_DRAW)
        gl.enableVertexAttribArray(location)
        gl.vertexAttribPointer(location, size, GL.FLOAT, false, 0, 0)
    }

    /** Points locations 4..8 at [inst]'s buffers while this VAO is bound. */
    fun attachInstances(inst: GpuInstances) {
        gl.bindBuffer(GL.ARRAY_BUFFER, inst.matrixBuffer)
        for (i in 0 until 4) {
            gl.enableVertexAttribArray(Attr.INSTANCE_MATRIX + i)
            gl.vertexAttribPointer(Attr.INSTANCE_MATRIX + i, 4, GL.FLOAT, false, 64, i * 16)
            gl.vertexAttribDivisor(Attr.INSTANCE_MATRIX + i, 1)
        }
        if (inst.colorBuffer != 0) {
            gl.bindBuffer(GL.ARRAY_BUFFER, inst.colorBuffer)
            gl.enableVertexAttribArray(Attr.INSTANCE_COLOR)
            gl.vertexAttribPointer(Attr.INSTANCE_COLOR, 3, GL.FLOAT, false, 0, 0)
            gl.vertexAttribDivisor(Attr.INSTANCE_COLOR, 1)
        } else {
            gl.disableVertexAttribArray(Attr.INSTANCE_COLOR)
        }
        instancedAttribs = true
    }

    /** Disables the instance arrays again so plain draws never fetch from a stale buffer. */
    fun detachInstances() {
        if (!instancedAttribs) return
        for (i in 0 until 5) gl.disableVertexAttribArray(Attr.INSTANCE_MATRIX + i)
        instancedAttribs = false
    }

    fun dispose() {
        buffers.values.forEach { gl.deleteBuffer(it) }
        if (ebo != 0) gl.deleteBuffer(ebo)
        gl.deleteVertexArray(vao)
    }
}

/** Per-instance matrix (+ colour) buffers of one [InstanceData]. */
internal class GpuInstances(private val gl: GlApi, private val data: InstanceData) {
    val matrixBuffer = gl.genBuffer()
    val colorBuffer = if (data.colors != null) gl.genBuffer() else 0
    private var version = -1
    private var count = -1
    var lastUsed = 0

    fun syncIfDirty() {
        val n = data.count.coerceIn(0, data.capacity)
        if (version == data.version && count == n) return
        gl.bindBuffer(GL.ARRAY_BUFFER, matrixBuffer)
        gl.bufferData(GL.ARRAY_BUFFER, data.matrices, maxOf(n, 1) * 16, GL.DYNAMIC_DRAW)
        val colors = data.colors
        if (colors != null) {
            gl.bindBuffer(GL.ARRAY_BUFFER, colorBuffer)
            gl.bufferData(GL.ARRAY_BUFFER, colors, maxOf(n, 1) * 3, GL.DYNAMIC_DRAW)
        }
        version = data.version
        count = n
    }

    fun dispose() {
        gl.deleteBuffer(matrixBuffer)
        if (colorBuffer != 0) gl.deleteBuffer(colorBuffer)
    }
}
