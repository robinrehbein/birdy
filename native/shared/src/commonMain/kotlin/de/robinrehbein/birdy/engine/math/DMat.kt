package de.robinrehbein.birdy.engine.math

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Double-precision column-major 4x4 matrix kernels (three.js formulas; JS numbers are doubles).
 * Used by [Mat4] for intermediate math and by CPU mesh processing where precision matters.
 */
object DMat {
    fun identity(): DoubleArray = DoubleArray(16).also { it[0] = 1.0; it[5] = 1.0; it[10] = 1.0; it[15] = 1.0 }

    /** a * b (three.js `multiplyMatrices`). */
    fun mul(a: DoubleArray, b: DoubleArray): DoubleArray {
        val r = DoubleArray(16)
        val a11 = a[0]; val a12 = a[4]; val a13 = a[8]; val a14 = a[12]
        val a21 = a[1]; val a22 = a[5]; val a23 = a[9]; val a24 = a[13]
        val a31 = a[2]; val a32 = a[6]; val a33 = a[10]; val a34 = a[14]
        val a41 = a[3]; val a42 = a[7]; val a43 = a[11]; val a44 = a[15]
        val b11 = b[0]; val b12 = b[4]; val b13 = b[8]; val b14 = b[12]
        val b21 = b[1]; val b22 = b[5]; val b23 = b[9]; val b24 = b[13]
        val b31 = b[2]; val b32 = b[6]; val b33 = b[10]; val b34 = b[14]
        val b41 = b[3]; val b42 = b[7]; val b43 = b[11]; val b44 = b[15]
        r[0] = a11 * b11 + a12 * b21 + a13 * b31 + a14 * b41
        r[4] = a11 * b12 + a12 * b22 + a13 * b32 + a14 * b42
        r[8] = a11 * b13 + a12 * b23 + a13 * b33 + a14 * b43
        r[12] = a11 * b14 + a12 * b24 + a13 * b34 + a14 * b44
        r[1] = a21 * b11 + a22 * b21 + a23 * b31 + a24 * b41
        r[5] = a21 * b12 + a22 * b22 + a23 * b32 + a24 * b42
        r[9] = a21 * b13 + a22 * b23 + a23 * b33 + a24 * b43
        r[13] = a21 * b14 + a22 * b24 + a23 * b34 + a24 * b44
        r[2] = a31 * b11 + a32 * b21 + a33 * b31 + a34 * b41
        r[6] = a31 * b12 + a32 * b22 + a33 * b32 + a34 * b42
        r[10] = a31 * b13 + a32 * b23 + a33 * b33 + a34 * b43
        r[14] = a31 * b14 + a32 * b24 + a33 * b34 + a34 * b44
        r[3] = a41 * b11 + a42 * b21 + a43 * b31 + a44 * b41
        r[7] = a41 * b12 + a42 * b22 + a43 * b32 + a44 * b42
        r[11] = a41 * b13 + a42 * b23 + a43 * b33 + a44 * b43
        r[15] = a41 * b14 + a42 * b24 + a43 * b34 + a44 * b44
        return r
    }

    /** three.js `Matrix4.compose`. */
    fun compose(
        px: Double, py: Double, pz: Double,
        x: Double, y: Double, z: Double, w: Double,
        sx: Double, sy: Double, sz: Double,
    ): DoubleArray {
        val x2 = x + x; val y2 = y + y; val z2 = z + z
        val xx = x * x2; val xy = x * y2; val xz = x * z2
        val yy = y * y2; val yz = y * z2; val zz = z * z2
        val wx = w * x2; val wy = w * y2; val wz = w * z2
        val e = DoubleArray(16)
        e[0] = (1 - (yy + zz)) * sx; e[1] = (xy + wz) * sx; e[2] = (xz - wy) * sx
        e[4] = (xy - wz) * sy; e[5] = (1 - (xx + zz)) * sy; e[6] = (yz + wx) * sy
        e[8] = (xz + wy) * sz; e[9] = (yz - wx) * sz; e[10] = (1 - (xx + yy)) * sz
        e[12] = px; e[13] = py; e[14] = pz; e[15] = 1.0
        return e
    }

    fun translation(x: Double, y: Double, z: Double): DoubleArray = identity().also { it[12] = x; it[13] = y; it[14] = z }
    fun scale(x: Double, y: Double, z: Double): DoubleArray = identity().also { it[0] = x; it[5] = y; it[10] = z }

    fun rotationX(t: Double): DoubleArray {
        val c = cos(t); val s = sin(t)
        return identity().also { it[5] = c; it[9] = -s; it[6] = s; it[10] = c }
    }

    fun rotationY(t: Double): DoubleArray {
        val c = cos(t); val s = sin(t)
        return identity().also { it[0] = c; it[8] = s; it[2] = -s; it[10] = c }
    }

    fun rotationZ(t: Double): DoubleArray {
        val c = cos(t); val s = sin(t)
        return identity().also { it[0] = c; it[4] = -s; it[1] = s; it[5] = c }
    }

    fun rotationAxis(x: Double, y: Double, z: Double, angle: Double): DoubleArray {
        val c = cos(angle); val s = sin(angle); val t = 1 - c
        val tx = t * x; val ty = t * y
        val e = identity()
        e[0] = tx * x + c; e[4] = tx * y - z * s; e[8] = tx * z + y * s
        e[1] = tx * y + z * s; e[5] = ty * y + c; e[9] = ty * z - x * s
        e[2] = tx * z - y * s; e[6] = ty * z + x * s; e[10] = t * z * z + c
        return e
    }

    /** three.js `makePerspective` for the WebGL coordinate system. */
    fun frustum(left: Double, right: Double, top: Double, bottom: Double, near: Double, far: Double): DoubleArray {
        val e = DoubleArray(16)
        e[0] = 2 * near / (right - left)
        e[5] = 2 * near / (top - bottom)
        e[8] = (right + left) / (right - left)
        e[9] = (top + bottom) / (top - bottom)
        e[10] = -(far + near) / (far - near)
        e[11] = -1.0
        e[14] = -2 * far * near / (far - near)
        return e
    }

    /**
     * Rotation basis of three.js `Matrix4.lookAt(eye, target, up)` as a column-major 3x3
     * (x axis, y axis, z axis).
     */
    fun lookAtRotation(
        ex: Double, ey: Double, ez: Double,
        tx: Double, ty: Double, tz: Double,
        ux: Double, uy: Double, uz: Double,
    ): DoubleArray {
        var zx = ex - tx; var zy = ey - ty; var zz = ez - tz
        if (zx * zx + zy * zy + zz * zz == 0.0) zz = 1.0
        var l = sqrt(zx * zx + zy * zy + zz * zz)
        zx /= l; zy /= l; zz /= l
        var xx = uy * zz - uz * zy; var xy = uz * zx - ux * zz; var xz = ux * zy - uy * zx
        if (xx * xx + xy * xy + xz * xz == 0.0) {
            if (abs(uz) == 1.0) zx += 0.0001 else zz += 0.0001
            l = sqrt(zx * zx + zy * zy + zz * zz)
            zx /= l; zy /= l; zz /= l
            xx = uy * zz - uz * zy; xy = uz * zx - ux * zz; xz = ux * zy - uy * zx
        }
        l = sqrt(xx * xx + xy * xy + xz * xz)
        xx /= l; xy /= l; xz /= l
        val yx = zy * xz - zz * xy
        val yy = zz * xx - zx * xz
        val yz = zx * xy - zy * xx
        return doubleArrayOf(xx, xy, xz, yx, yy, yz, zx, zy, zz)
    }

    fun determinantAffine(te: DoubleArray): Double {
        val a = te[0]; val b = te[4]; val c = te[8]
        val d = te[1]; val e = te[5]; val f = te[9]
        val g = te[2]; val h = te[6]; val i = te[10]
        return a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g)
    }

    /** three.js `Matrix4.determinant`. */
    fun determinant(te: DoubleArray): Double {
        val n11 = te[0]; val n12 = te[4]; val n13 = te[8]; val n14 = te[12]
        val n21 = te[1]; val n22 = te[5]; val n23 = te[9]; val n24 = te[13]
        val n31 = te[2]; val n32 = te[6]; val n33 = te[10]; val n34 = te[14]
        val n41 = te[3]; val n42 = te[7]; val n43 = te[11]; val n44 = te[15]
        return n41 * (n14 * n23 * n32 - n13 * n24 * n32 - n14 * n22 * n33 + n12 * n24 * n33 + n13 * n22 * n34 - n12 * n23 * n34) +
            n42 * (n11 * n23 * n34 - n11 * n24 * n33 + n14 * n21 * n33 - n13 * n21 * n34 + n13 * n24 * n31 - n14 * n23 * n31) +
            n43 * (n11 * n24 * n32 - n11 * n22 * n34 - n14 * n21 * n32 + n12 * n21 * n34 + n14 * n22 * n31 - n12 * n24 * n31) +
            n44 * (-n13 * n22 * n31 - n11 * n23 * n32 + n11 * n22 * n33 + n13 * n21 * n32 - n12 * n21 * n33 + n12 * n23 * n31)
    }

    /** three.js `Matrix4.invert`; singular input gives all zeros. */
    fun invert(te: DoubleArray): DoubleArray {
        val n11 = te[0]; val n21 = te[1]; val n31 = te[2]; val n41 = te[3]
        val n12 = te[4]; val n22 = te[5]; val n32 = te[6]; val n42 = te[7]
        val n13 = te[8]; val n23 = te[9]; val n33 = te[10]; val n43 = te[11]
        val n14 = te[12]; val n24 = te[13]; val n34 = te[14]; val n44 = te[15]
        val t1 = n11 * n22 - n21 * n12
        val t2 = n11 * n32 - n31 * n12
        val t3 = n11 * n42 - n41 * n12
        val t4 = n21 * n32 - n31 * n22
        val t5 = n21 * n42 - n41 * n22
        val t6 = n31 * n42 - n41 * n32
        val t7 = n13 * n24 - n23 * n14
        val t8 = n13 * n34 - n33 * n14
        val t9 = n13 * n44 - n43 * n14
        val t10 = n23 * n34 - n33 * n24
        val t11 = n23 * n44 - n43 * n24
        val t12 = n33 * n44 - n43 * n34
        val det = t1 * t12 - t2 * t11 + t3 * t10 + t4 * t9 - t5 * t8 + t6 * t7
        val r = DoubleArray(16)
        if (det == 0.0) return r
        val detInv = 1 / det
        r[0] = (n22 * t12 - n32 * t11 + n42 * t10) * detInv
        r[1] = (n31 * t11 - n21 * t12 - n41 * t10) * detInv
        r[2] = (n24 * t6 - n34 * t5 + n44 * t4) * detInv
        r[3] = (n33 * t5 - n23 * t6 - n43 * t4) * detInv
        r[4] = (n32 * t9 - n12 * t12 - n42 * t8) * detInv
        r[5] = (n11 * t12 - n31 * t9 + n41 * t8) * detInv
        r[6] = (n34 * t3 - n14 * t6 - n44 * t2) * detInv
        r[7] = (n13 * t6 - n33 * t3 + n43 * t2) * detInv
        r[8] = (n12 * t11 - n22 * t9 + n42 * t7) * detInv
        r[9] = (n21 * t9 - n11 * t11 - n41 * t7) * detInv
        r[10] = (n14 * t5 - n24 * t3 + n44 * t1) * detInv
        r[11] = (n23 * t3 - n13 * t5 - n43 * t1) * detInv
        r[12] = (n22 * t8 - n12 * t10 - n32 * t7) * detInv
        r[13] = (n11 * t10 - n21 * t8 + n31 * t7) * detInv
        r[14] = (n24 * t2 - n14 * t4 - n34 * t1) * detInv
        r[15] = (n13 * t4 - n23 * t2 + n33 * t1) * detInv
        return r
    }

    /** three.js `Matrix3.getNormalMatrix(m4)`: inverse-transpose of the upper 3x3 (column-major 3x3). */
    fun normalMatrix(m: DoubleArray): DoubleArray {
        val n = Mat3Kernels.fromMat4(m)
        return Mat3Kernels.transpose(Mat3Kernels.invert(n))
    }
}

/** Double 3x3 kernels (column-major), three.js `Matrix3` formulas. */
internal object Mat3Kernels {
    fun fromMat4(m: DoubleArray): DoubleArray = doubleArrayOf(m[0], m[1], m[2], m[4], m[5], m[6], m[8], m[9], m[10])

    fun invert(te: DoubleArray): DoubleArray {
        val n11 = te[0]; val n21 = te[1]; val n31 = te[2]
        val n12 = te[3]; val n22 = te[4]; val n32 = te[5]
        val n13 = te[6]; val n23 = te[7]; val n33 = te[8]
        val t11 = n33 * n22 - n32 * n23
        val t12 = n32 * n13 - n33 * n12
        val t13 = n23 * n12 - n22 * n13
        val det = n11 * t11 + n21 * t12 + n31 * t13
        val r = DoubleArray(9)
        if (det == 0.0) return r
        val detInv = 1 / det
        r[0] = t11 * detInv
        r[1] = (n31 * n23 - n33 * n21) * detInv
        r[2] = (n32 * n21 - n31 * n22) * detInv
        r[3] = t12 * detInv
        r[4] = (n33 * n11 - n31 * n13) * detInv
        r[5] = (n31 * n12 - n32 * n11) * detInv
        r[6] = t13 * detInv
        r[7] = (n21 * n13 - n23 * n11) * detInv
        r[8] = (n22 * n11 - n21 * n12) * detInv
        return r
    }

    fun transpose(m: DoubleArray): DoubleArray = doubleArrayOf(m[0], m[3], m[6], m[1], m[4], m[7], m[2], m[5], m[8])
}
