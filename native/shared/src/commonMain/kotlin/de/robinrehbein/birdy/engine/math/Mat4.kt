package de.robinrehbein.birdy.engine.math

import kotlin.math.max
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * 4x4 float matrix, column-major like three.js `Matrix4.elements` and GL uniforms.
 * Formulas are three.js `Matrix4`; intermediate products are evaluated in Double.
 */
class Mat4 {
    val e = FloatArray(16).also { it[0] = 1f; it[5] = 1f; it[10] = 1f; it[15] = 1f }

    fun identity(): Mat4 {
        e.fill(0f); e[0] = 1f; e[5] = 1f; e[10] = 1f; e[15] = 1f
        return this
    }

    fun set(o: Mat4): Mat4 { o.e.copyInto(e); return this }
    fun copy(o: Mat4): Mat4 = set(o)
    fun clone(): Mat4 = Mat4().set(this)

    /** Row-major setter, same argument order as three.js `Matrix4.set(n11, n12, ..., n44)`. */
    fun setRowMajor(
        n11: Float, n12: Float, n13: Float, n14: Float,
        n21: Float, n22: Float, n23: Float, n24: Float,
        n31: Float, n32: Float, n33: Float, n34: Float,
        n41: Float, n42: Float, n43: Float, n44: Float,
    ): Mat4 {
        e[0] = n11; e[4] = n12; e[8] = n13; e[12] = n14
        e[1] = n21; e[5] = n22; e[9] = n23; e[13] = n24
        e[2] = n31; e[6] = n32; e[10] = n33; e[14] = n34
        e[3] = n41; e[7] = n42; e[11] = n43; e[15] = n44
        return this
    }

    fun fromArray(a: FloatArray, offset: Int = 0): Mat4 { a.copyInto(e, 0, offset, offset + 16); return this }
    fun toArray(out: FloatArray = FloatArray(16), offset: Int = 0): FloatArray { e.copyInto(out, offset); return out }

    internal fun setD(d: DoubleArray): Mat4 { for (i in 0 until 16) e[i] = d[i].toFloat(); return this }
    internal fun toD(out: DoubleArray = DoubleArray(16)): DoubleArray { for (i in 0 until 16) out[i] = e[i].toDouble(); return out }

    /** this = a * b (allocation-free; [a] or [b] may be this). */
    fun multiply(a: Mat4, b: Mat4): Mat4 {
        val ae = a.e; val be = b.e
        val a11 = ae[0].toDouble(); val a12 = ae[4].toDouble(); val a13 = ae[8].toDouble(); val a14 = ae[12].toDouble()
        val a21 = ae[1].toDouble(); val a22 = ae[5].toDouble(); val a23 = ae[9].toDouble(); val a24 = ae[13].toDouble()
        val a31 = ae[2].toDouble(); val a32 = ae[6].toDouble(); val a33 = ae[10].toDouble(); val a34 = ae[14].toDouble()
        val a41 = ae[3].toDouble(); val a42 = ae[7].toDouble(); val a43 = ae[11].toDouble(); val a44 = ae[15].toDouble()
        val b11 = be[0].toDouble(); val b12 = be[4].toDouble(); val b13 = be[8].toDouble(); val b14 = be[12].toDouble()
        val b21 = be[1].toDouble(); val b22 = be[5].toDouble(); val b23 = be[9].toDouble(); val b24 = be[13].toDouble()
        val b31 = be[2].toDouble(); val b32 = be[6].toDouble(); val b33 = be[10].toDouble(); val b34 = be[14].toDouble()
        val b41 = be[3].toDouble(); val b42 = be[7].toDouble(); val b43 = be[11].toDouble(); val b44 = be[15].toDouble()
        e[0] = (a11 * b11 + a12 * b21 + a13 * b31 + a14 * b41).toFloat()
        e[4] = (a11 * b12 + a12 * b22 + a13 * b32 + a14 * b42).toFloat()
        e[8] = (a11 * b13 + a12 * b23 + a13 * b33 + a14 * b43).toFloat()
        e[12] = (a11 * b14 + a12 * b24 + a13 * b34 + a14 * b44).toFloat()
        e[1] = (a21 * b11 + a22 * b21 + a23 * b31 + a24 * b41).toFloat()
        e[5] = (a21 * b12 + a22 * b22 + a23 * b32 + a24 * b42).toFloat()
        e[9] = (a21 * b13 + a22 * b23 + a23 * b33 + a24 * b43).toFloat()
        e[13] = (a21 * b14 + a22 * b24 + a23 * b34 + a24 * b44).toFloat()
        e[2] = (a31 * b11 + a32 * b21 + a33 * b31 + a34 * b41).toFloat()
        e[6] = (a31 * b12 + a32 * b22 + a33 * b32 + a34 * b42).toFloat()
        e[10] = (a31 * b13 + a32 * b23 + a33 * b33 + a34 * b43).toFloat()
        e[14] = (a31 * b14 + a32 * b24 + a33 * b34 + a34 * b44).toFloat()
        e[3] = (a41 * b11 + a42 * b21 + a43 * b31 + a44 * b41).toFloat()
        e[7] = (a41 * b12 + a42 * b22 + a43 * b32 + a44 * b42).toFloat()
        e[11] = (a41 * b13 + a42 * b23 + a43 * b33 + a44 * b43).toFloat()
        e[15] = (a41 * b14 + a42 * b24 + a43 * b34 + a44 * b44).toFloat()
        return this
    }

    fun multiplyMatrices(a: Mat4, b: Mat4): Mat4 = multiply(a, b)

    /** this = this * m */
    fun multiply(m: Mat4): Mat4 = multiply(this, m)

    /** this = m * this */
    fun premultiply(m: Mat4): Mat4 = multiply(m, this)

    fun multiplyScalar(s: Float): Mat4 { for (i in 0 until 16) e[i] *= s; return this }

    /** Compose translation * rotation(quaternion) * scale, as three.js `Matrix4.compose`. */
    fun compose(p: Vec3, q: Quat, s: Vec3): Mat4 {
        val x = q.x.toDouble(); val y = q.y.toDouble(); val z = q.z.toDouble(); val w = q.w.toDouble()
        val sx = s.x.toDouble(); val sy = s.y.toDouble(); val sz = s.z.toDouble()
        val x2 = x + x; val y2 = y + y; val z2 = z + z
        val xx = x * x2; val xy = x * y2; val xz = x * z2
        val yy = y * y2; val yz = y * z2; val zz = z * z2
        val wx = w * x2; val wy = w * y2; val wz = w * z2
        e[0] = ((1 - (yy + zz)) * sx).toFloat(); e[1] = ((xy + wz) * sx).toFloat(); e[2] = ((xz - wy) * sx).toFloat(); e[3] = 0f
        e[4] = ((xy - wz) * sy).toFloat(); e[5] = ((1 - (xx + zz)) * sy).toFloat(); e[6] = ((yz + wx) * sy).toFloat(); e[7] = 0f
        e[8] = ((xz + wy) * sz).toFloat(); e[9] = ((yz - wx) * sz).toFloat(); e[10] = ((1 - (xx + yy)) * sz).toFloat(); e[11] = 0f
        e[12] = p.x; e[13] = p.y; e[14] = p.z; e[15] = 1f
        return this
    }

    /** three.js `Matrix4.decompose` (negative determinant flips the X scale). */
    fun decompose(position: Vec3, quaternion: Quat, scale: Vec3): Mat4 {
        val te = toD()
        position.set(e[12], e[13], e[14])
        val det = DMat.determinantAffine(te)
        if (det == 0.0) {
            scale.set(1f, 1f, 1f)
            quaternion.identity()
            return this
        }
        var sx = sqrt(te[0] * te[0] + te[1] * te[1] + te[2] * te[2])
        val sy = sqrt(te[4] * te[4] + te[5] * te[5] + te[6] * te[6])
        val sz = sqrt(te[8] * te[8] + te[9] * te[9] + te[10] * te[10])
        if (det < 0) sx = -sx
        val ix = 1 / sx; val iy = 1 / sy; val iz = 1 / sz
        quaternion.setFromRotation3(
            te[0] * ix, te[4] * iy, te[8] * iz,
            te[1] * ix, te[5] * iy, te[9] * iz,
            te[2] * ix, te[6] * iy, te[10] * iz,
        )
        scale.set(sx.toFloat(), sy.toFloat(), sz.toFloat())
        return this
    }

    fun makeTranslation(x: Float, y: Float, z: Float): Mat4 {
        identity(); e[12] = x; e[13] = y; e[14] = z
        return this
    }

    fun makeScale(x: Float, y: Float, z: Float): Mat4 {
        identity(); e[0] = x; e[5] = y; e[10] = z
        return this
    }

    fun makeRotationX(theta: Float): Mat4 = setD(DMat.rotationX(theta.toDouble()))
    fun makeRotationY(theta: Float): Mat4 = setD(DMat.rotationY(theta.toDouble()))
    fun makeRotationZ(theta: Float): Mat4 = setD(DMat.rotationZ(theta.toDouble()))

    /** [axis] must be normalized (three.js `makeRotationAxis`). */
    fun makeRotationAxis(axis: Vec3, angle: Float): Mat4 =
        setD(DMat.rotationAxis(axis.x.toDouble(), axis.y.toDouble(), axis.z.toDouble(), angle.toDouble()))

    fun makeRotationFromQuaternion(q: Quat): Mat4 = compose(ZERO, q, ONE)

    fun makeRotationFromEulerXYZ(x: Float, y: Float, z: Float): Mat4 = makeRotationFromQuaternion(Quat().setFromEulerXYZ(x, y, z))

    fun makeBasis(xAxis: Vec3, yAxis: Vec3, zAxis: Vec3): Mat4 = setRowMajor(
        xAxis.x, yAxis.x, zAxis.x, 0f,
        xAxis.y, yAxis.y, zAxis.y, 0f,
        xAxis.z, yAxis.z, zAxis.z, 0f,
        0f, 0f, 0f, 1f,
    )

    fun extractBasis(xAxis: Vec3, yAxis: Vec3, zAxis: Vec3): Mat4 {
        xAxis.setFromMatrixColumn(this, 0); yAxis.setFromMatrixColumn(this, 1); zAxis.setFromMatrixColumn(this, 2)
        return this
    }

    /** Rotation part of [m] with its scale removed (three.js `extractRotation`). */
    fun extractRotation(m: Mat4): Mat4 {
        val me = m.toD()
        if (DMat.determinantAffine(me) == 0.0) return identity()
        val sx = 1 / sqrt(me[0] * me[0] + me[1] * me[1] + me[2] * me[2])
        val sy = 1 / sqrt(me[4] * me[4] + me[5] * me[5] + me[6] * me[6])
        val sz = 1 / sqrt(me[8] * me[8] + me[9] * me[9] + me[10] * me[10])
        val r = DoubleArray(16)
        r[0] = me[0] * sx; r[1] = me[1] * sx; r[2] = me[2] * sx
        r[4] = me[4] * sy; r[5] = me[5] * sy; r[6] = me[6] * sy
        r[8] = me[8] * sz; r[9] = me[9] * sz; r[10] = me[10] * sz
        r[15] = 1.0
        return setD(r)
    }

    /**
     * three.js `Matrix4.lookAt(eye, target, up)`: writes only the rotation part so that +Z points from
     * [target] to [eye]. Translation and the last row are left untouched.
     */
    fun lookAt(eye: Vec3, target: Vec3, up: Vec3): Mat4 {
        val r = DMat.lookAtRotation(
            eye.x.toDouble(), eye.y.toDouble(), eye.z.toDouble(),
            target.x.toDouble(), target.y.toDouble(), target.z.toDouble(),
            up.x.toDouble(), up.y.toDouble(), up.z.toDouble(),
        )
        e[0] = r[0].toFloat(); e[4] = r[3].toFloat(); e[8] = r[6].toFloat()
        e[1] = r[1].toFloat(); e[5] = r[4].toFloat(); e[9] = r[7].toFloat()
        e[2] = r[2].toFloat(); e[6] = r[5].toFloat(); e[10] = r[8].toFloat()
        return this
    }

    fun setPosition(x: Float, y: Float, z: Float): Mat4 { e[12] = x; e[13] = y; e[14] = z; return this }
    fun setPosition(v: Vec3): Mat4 = setPosition(v.x, v.y, v.z)

    /** Scales the basis columns (three.js `Matrix4.scale(v)`). */
    fun scale(v: Vec3): Mat4 {
        for (i in 0..3) { e[i] *= v.x; e[4 + i] *= v.y; e[8 + i] *= v.z }
        return this
    }

    fun getMaxScaleOnAxis(): Float {
        val sx = e[0] * e[0] + e[1] * e[1] + e[2] * e[2]
        val sy = e[4] * e[4] + e[5] * e[5] + e[6] * e[6]
        val sz = e[8] * e[8] + e[9] * e[9] + e[10] * e[10]
        return sqrt(max(sx, max(sy, sz)))
    }

    fun determinant(): Float = DMat.determinant(toD()).toFloat()

    fun transpose(): Mat4 {
        fun swap(a: Int, b: Int) { val t = e[a]; e[a] = e[b]; e[b] = t }
        swap(1, 4); swap(2, 8); swap(6, 9); swap(3, 12); swap(7, 13); swap(11, 14)
        return this
    }

    /** Symmetric perspective projection (fov in degrees, vertical), GL clip space. */
    fun perspective(fovDeg: Float, aspect: Float, near: Float, far: Float): Mat4 {
        val n = near.toDouble()
        val top = n * tan(MathUtil.DEG2RAD * 0.5 * fovDeg)
        val height = 2 * top
        val width = aspect * height
        val left = -0.5 * width
        return setD(DMat.frustum(left, left + width, top, top - height, n, far.toDouble()))
    }

    /** Off-axis frustum (three.js `makePerspective(left, right, top, bottom, near, far)`). */
    fun frustum(left: Float, right: Float, top: Float, bottom: Float, near: Float, far: Float): Mat4 =
        setD(DMat.frustum(left.toDouble(), right.toDouble(), top.toDouble(), bottom.toDouble(), near.toDouble(), far.toDouble()))

    fun makePerspective(left: Float, right: Float, top: Float, bottom: Float, near: Float, far: Float): Mat4 =
        frustum(left, right, top, bottom, near, far)

    /** three.js `makeOrthographic` (WebGL clip space). */
    fun orthographic(left: Float, right: Float, top: Float, bottom: Float, near: Float, far: Float): Mat4 {
        val w = 1.0 / (right - left)
        val h = 1.0 / (top - bottom)
        val p = 1.0 / (far - near)
        val x = (right + left) * w
        val y = (top + bottom) * h
        val z = (far + near) * p
        val r = DoubleArray(16)
        r[0] = 2 * w; r[12] = -x
        r[5] = 2 * h; r[13] = -y
        r[10] = -2 * p; r[14] = -z
        r[15] = 1.0
        return setD(r)
    }

    fun makeOrthographic(left: Float, right: Float, top: Float, bottom: Float, near: Float, far: Float): Mat4 =
        orthographic(left, right, top, bottom, near, far)

    /** this = inverse([m]) (three.js `invert`); a singular [m] yields the zero matrix, like three.js. */
    fun invert(m: Mat4): Mat4 = setD(DMat.invert(m.toD()))

    /** In-place inverse. */
    fun invert(): Mat4 = invert(this)

    fun equals(o: Mat4): Boolean = e.contentEquals(o.e)

    override fun toString(): String = "Mat4(${e.joinToString()})"

    private companion object {
        val ZERO = Vec3()
        val ONE = Vec3(1f, 1f, 1f)
    }
}
