package de.robinrehbein.birdy.engine.gl

import kotlin.math.max
import kotlin.math.sqrt

/** Allocation-free column-major matrix helpers for the renderer's per-draw math. */
internal object GlMath {
    /** out = a * b (4x4). [out] must not alias [a] or [b]. */
    fun mul(out: FloatArray, a: FloatArray, b: FloatArray) {
        for (c in 0 until 4) {
            val b0 = b[c * 4]; val b1 = b[c * 4 + 1]; val b2 = b[c * 4 + 2]; val b3 = b[c * 4 + 3]
            for (r in 0 until 4) out[c * 4 + r] = a[r] * b0 + a[4 + r] * b1 + a[8 + r] * b2 + a[12 + r] * b3
        }
    }

    /**
     * Inverse transpose of the upper 3x3 of [m] into [out] (9 floats, column-major), as three.js
     * `Matrix3.getNormalMatrix`. Leaves zeros for a singular matrix (three.js does the same).
     */
    fun normalMatrix(out: FloatArray, m: FloatArray) {
        val a00 = m[0]; val a01 = m[1]; val a02 = m[2]
        val a10 = m[4]; val a11 = m[5]; val a12 = m[6]
        val a20 = m[8]; val a21 = m[9]; val a22 = m[10]
        val b01 = a22 * a11 - a12 * a21
        val b11 = -a22 * a10 + a12 * a20
        val b21 = a21 * a10 - a11 * a20
        val det = a00 * b01 + a01 * b11 + a02 * b21
        if (det == 0f) { out.fill(0f, 0, 9); return }
        val id = 1f / det
        // Inverse (column-major) then transpose.
        val i00 = b01 * id
        val i01 = (-a22 * a01 + a02 * a21) * id
        val i02 = (a12 * a01 - a02 * a11) * id
        val i10 = b11 * id
        val i11 = (a22 * a00 - a02 * a20) * id
        val i12 = (-a12 * a00 + a02 * a10) * id
        val i20 = b21 * id
        val i21 = (-a21 * a00 + a01 * a20) * id
        val i22 = (a11 * a00 - a01 * a10) * id
        // inverse stored column-major as [i00 i01 i02 | i10 i11 i12 | i20 i21 i22]; transpose it.
        out[0] = i00; out[1] = i10; out[2] = i20
        out[3] = i01; out[4] = i11; out[5] = i21
        out[6] = i02; out[7] = i12; out[8] = i22
    }

    /**
     * View matrix (inverse camera world matrix) of a camera at [eye] looking at [target] with
     * `up` = +Y, following three.js `Matrix4.lookAt` including its degenerate-case nudges.
     */
    fun lookAtView(out: FloatArray, ex: Float, ey: Float, ez: Float, tx: Float, ty: Float, tz: Float) {
        var zx = ex - tx; var zy = ey - ty; var zz = ez - tz
        var len = sqrt(zx * zx + zy * zy + zz * zz)
        if (len == 0f) { zz = 1f; len = 1f }
        zx /= len; zy /= len; zz /= len
        // x = up(0,1,0) x z
        var xx = zz; var xy = 0f; var xz = -zx
        var xl = sqrt(xx * xx + xy * xy + xz * xz)
        if (xl == 0f) {
            // up (+Y) and z parallel: three.js nudges z.z because |up.z| != 1
            zz += 0.0001f
            val l = sqrt(zx * zx + zy * zy + zz * zz)
            zx /= l; zy /= l; zz /= l
            xx = zz; xy = 0f; xz = -zx
            xl = sqrt(xx * xx + xy * xy + xz * xz)
        }
        xx /= xl; xy /= xl; xz /= xl
        val yx = zy * xz - zz * xy
        val yy = zz * xx - zx * xz
        val yz = zx * xy - zy * xx
        // World matrix columns x, y, z, eye; its inverse is the transposed rotation.
        out[0] = xx; out[1] = yx; out[2] = zx; out[3] = 0f
        out[4] = xy; out[5] = yy; out[6] = zy; out[7] = 0f
        out[8] = xz; out[9] = yz; out[10] = zz; out[11] = 0f
        out[12] = -(xx * ex + xy * ey + xz * ez)
        out[13] = -(yx * ex + yy * ey + yz * ez)
        out[14] = -(zx * ex + zy * ey + zz * ez)
        out[15] = 1f
    }

    /** three.js `Matrix4.makeOrthographic` (WebGL clip space). */
    fun ortho(out: FloatArray, left: Float, right: Float, top: Float, bottom: Float, near: Float, far: Float) {
        out.fill(0f, 0, 16)
        val w = 1f / (right - left)
        val h = 1f / (top - bottom)
        val p = 1f / (far - near)
        out[0] = 2 * w; out[5] = 2 * h; out[10] = -2 * p
        out[12] = -(right + left) * w
        out[13] = -(top + bottom) * h
        out[14] = -(far + near) * p
        out[15] = 1f
    }

    /** Largest axis scale of [m] (three.js `getMaxScaleOnAxis`). */
    fun maxScaleOnAxis(m: FloatArray): Float {
        val sx = m[0] * m[0] + m[1] * m[1] + m[2] * m[2]
        val sy = m[4] * m[4] + m[5] * m[5] + m[6] * m[6]
        val sz = m[8] * m[8] + m[9] * m[9] + m[10] * m[10]
        return sqrt(max(sx, max(sy, sz)))
    }

    /** Transforms the point ([x],[y],[z]) by [m] with perspective divide into [out]. */
    fun project(out: FloatArray, m: FloatArray, x: Float, y: Float, z: Float) {
        val w = m[3] * x + m[7] * y + m[11] * z + m[15]
        val iw = if (w != 0f) 1f / w else 1f
        out[0] = (m[0] * x + m[4] * y + m[8] * z + m[12]) * iw
        out[1] = (m[1] * x + m[5] * y + m[9] * z + m[13]) * iw
        out[2] = (m[2] * x + m[6] * y + m[10] * z + m[14]) * iw
    }

    /** Affine point transform (no divide). */
    fun transformPoint(out: FloatArray, m: FloatArray, x: Float, y: Float, z: Float) {
        out[0] = m[0] * x + m[4] * y + m[8] * z + m[12]
        out[1] = m[1] * x + m[5] * y + m[9] * z + m[13]
        out[2] = m[2] * x + m[6] * y + m[10] * z + m[14]
    }

    /** three.js `Vector3.transformDirection`: rotate by the upper 3x3 of [m] and normalize. */
    fun transformDirection(out: FloatArray, m: FloatArray, x: Float, y: Float, z: Float) {
        val rx = m[0] * x + m[4] * y + m[8] * z
        val ry = m[1] * x + m[5] * y + m[9] * z
        val rz = m[2] * x + m[6] * y + m[10] * z
        val l = sqrt(rx * rx + ry * ry + rz * rz)
        val il = if (l > 0f) 1f / l else 0f
        out[0] = rx * il; out[1] = ry * il; out[2] = rz * il
    }
}

/** Six clip planes of a view-projection matrix (three.js `Frustum.setFromProjectionMatrix`). */
internal class Frustum {
    private val p = FloatArray(24)

    fun set(m: FloatArray) {
        plane(0, m[3] - m[0], m[7] - m[4], m[11] - m[8], m[15] - m[12])
        plane(1, m[3] + m[0], m[7] + m[4], m[11] + m[8], m[15] + m[12])
        plane(2, m[3] + m[1], m[7] + m[5], m[11] + m[9], m[15] + m[13])
        plane(3, m[3] - m[1], m[7] - m[5], m[11] - m[9], m[15] - m[13])
        plane(4, m[3] - m[2], m[7] - m[6], m[11] - m[10], m[15] - m[14])
        plane(5, m[3] + m[2], m[7] + m[6], m[11] + m[10], m[15] + m[14])
    }

    private fun plane(i: Int, x: Float, y: Float, z: Float, w: Float) {
        val l = sqrt(x * x + y * y + z * z)
        val il = if (l > 0f) 1f / l else 0f
        p[i * 4] = x * il; p[i * 4 + 1] = y * il; p[i * 4 + 2] = z * il; p[i * 4 + 3] = w * il
    }

    fun intersectsSphere(cx: Float, cy: Float, cz: Float, radius: Float): Boolean {
        for (i in 0 until 6) {
            val d = p[i * 4] * cx + p[i * 4 + 1] * cy + p[i * 4 + 2] * cz + p[i * 4 + 3]
            if (d < -radius) return false
        }
        return true
    }
}
