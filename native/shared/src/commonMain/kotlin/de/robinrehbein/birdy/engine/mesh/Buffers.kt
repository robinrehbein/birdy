package de.robinrehbein.birdy.engine.mesh

import de.robinrehbein.birdy.engine.scene.Geometry

/** Growable DoubleArray used while generating geometry (JS arrays hold doubles until Float32 upload). */
internal class DoubleList(capacity: Int = 64) {
    var data = DoubleArray(capacity)
        private set
    var size = 0
        private set

    fun add(v: Double) {
        if (size == data.size) data = data.copyOf(maxOf(8, size * 2))
        data[size++] = v
    }

    fun add(a: Double, b: Double) { add(a); add(b) }
    fun add(a: Double, b: Double, c: Double) { add(a); add(b); add(c) }

    operator fun get(i: Int): Double = data[i]
    operator fun set(i: Int, v: Double) { data[i] = v }

    fun toFloatArray(): FloatArray = FloatArray(size) { data[it].toFloat() }
}

internal class IntList(capacity: Int = 64) {
    var data = IntArray(capacity)
        private set
    var size = 0
        private set

    fun add(v: Int) {
        if (size == data.size) data = data.copyOf(maxOf(8, size * 2))
        data[size++] = v
    }

    fun add(a: Int, b: Int, c: Int) { add(a); add(b); add(c) }

    fun toIntArray(): IntArray = data.copyOf(size)
}

/** Indexed geometry from generator buffers (three.js `setIndex` + Float32BufferAttribute). */
internal fun indexedGeometry(pos: DoubleList, nor: DoubleList, uv: DoubleList, idx: IntList): Geometry =
    Geometry(pos.toFloatArray(), nor.toFloatArray(), null, uv.toFloatArray(), idx.toIntArray())
