package de.robinrehbein.birdy.engine.math

/** 3x3 float matrix, column-major (three.js `Matrix3`); mainly the normal matrix. */
class Mat3 {
    val e = FloatArray(9).also { it[0] = 1f; it[4] = 1f; it[8] = 1f }

    fun identity(): Mat3 {
        e.fill(0f); e[0] = 1f; e[4] = 1f; e[8] = 1f
        return this
    }

    fun set(o: Mat3): Mat3 { o.e.copyInto(e); return this }
    fun clone(): Mat3 = Mat3().set(this)

    internal fun setD(d: DoubleArray): Mat3 { for (i in 0 until 9) e[i] = d[i].toFloat(); return this }
    private fun toD(): DoubleArray = DoubleArray(9) { e[it].toDouble() }

    /** Upper-left 3x3 of [m]. */
    fun setFromMat4(m: Mat4): Mat3 {
        val me = m.e
        e[0] = me[0]; e[1] = me[1]; e[2] = me[2]
        e[3] = me[4]; e[4] = me[5]; e[5] = me[6]
        e[6] = me[8]; e[7] = me[9]; e[8] = me[10]
        return this
    }

    /** Inverse (three.js `Matrix3.invert`); singular gives zeros. */
    fun invert(): Mat3 = setD(Mat3Kernels.invert(toD()))

    fun transpose(): Mat3 {
        var t = e[1]; e[1] = e[3]; e[3] = t
        t = e[2]; e[2] = e[6]; e[6] = t
        t = e[5]; e[5] = e[7]; e[7] = t
        return this
    }

    /** Inverse-transpose of the upper 3x3 of [m] (three.js `getNormalMatrix`). */
    fun getNormalMatrix(m: Mat4): Mat3 = setD(DMat.normalMatrix(m.toD()))

    override fun toString(): String = "Mat3(${e.joinToString()})"
}
