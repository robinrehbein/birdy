package de.robinrehbein.birdy.engine.math

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Unit quaternion (x, y, z, w) with three.js `Quaternion` formulas (evaluated in Double, stored as Float). */
class Quat(var x: Float = 0f, var y: Float = 0f, var z: Float = 0f, var w: Float = 1f) {
    fun set(x: Float, y: Float, z: Float, w: Float): Quat { this.x = x; this.y = y; this.z = z; this.w = w; return this }
    fun set(o: Quat): Quat = set(o.x, o.y, o.z, o.w)
    fun copy(o: Quat): Quat = set(o)
    fun clone(): Quat = Quat(x, y, z, w)
    fun identity(): Quat = set(0f, 0f, 0f, 1f)

    internal fun setD(x: Double, y: Double, z: Double, w: Double): Quat =
        set(x.toFloat(), y.toFloat(), z.toFloat(), w.toFloat())

    /** Euler angles in radians, three.js default order 'XYZ'. */
    fun setFromEulerXYZ(ex: Float, ey: Float, ez: Float): Quat = setFromEulerXYZ(ex.toDouble(), ey.toDouble(), ez.toDouble())

    fun setFromEulerXYZ(ex: Double, ey: Double, ez: Double): Quat {
        val c1 = cos(ex / 2); val c2 = cos(ey / 2); val c3 = cos(ez / 2)
        val s1 = sin(ex / 2); val s2 = sin(ey / 2); val s3 = sin(ez / 2)
        return setD(
            s1 * c2 * c3 + c1 * s2 * s3,
            c1 * s2 * c3 - s1 * c2 * s3,
            c1 * c2 * s3 + s1 * s2 * c3,
            c1 * c2 * c3 - s1 * s2 * s3,
        )
    }

    fun setFromEuler(euler: Vec3): Quat = setFromEulerXYZ(euler.x, euler.y, euler.z)

    /** [axis] must be normalized. */
    fun setFromAxisAngle(axis: Vec3, angle: Float): Quat = setFromAxisAngle(axis, angle.toDouble())

    fun setFromAxisAngle(axis: Vec3, angle: Double): Quat {
        val s = sin(angle / 2)
        return setD(axis.x * s, axis.y * s, axis.z * s, cos(angle / 2))
    }

    /** From the upper 3x3 of [m], which must be a pure (unscaled) rotation. */
    fun setFromRotationMatrix(m: Mat4): Quat {
        val te = m.e
        return setFromRotation3(
            te[0].toDouble(), te[4].toDouble(), te[8].toDouble(),
            te[1].toDouble(), te[5].toDouble(), te[9].toDouble(),
            te[2].toDouble(), te[6].toDouble(), te[10].toDouble(),
        )
    }

    internal fun setFromRotation3(
        m11: Double, m12: Double, m13: Double,
        m21: Double, m22: Double, m23: Double,
        m31: Double, m32: Double, m33: Double,
    ): Quat {
        val trace = m11 + m22 + m33
        return if (trace > 0) {
            val s = 0.5 / sqrt(trace + 1.0)
            setD((m32 - m23) * s, (m13 - m31) * s, (m21 - m12) * s, 0.25 / s)
        } else if (m11 > m22 && m11 > m33) {
            val s = 2.0 * sqrt(1.0 + m11 - m22 - m33)
            setD(0.25 * s, (m12 + m21) / s, (m13 + m31) / s, (m32 - m23) / s)
        } else if (m22 > m33) {
            val s = 2.0 * sqrt(1.0 + m22 - m11 - m33)
            setD((m12 + m21) / s, 0.25 * s, (m23 + m32) / s, (m13 - m31) / s)
        } else {
            val s = 2.0 * sqrt(1.0 + m33 - m11 - m22)
            setD((m13 + m31) / s, (m23 + m32) / s, 0.25 * s, (m21 - m12) / s)
        }
    }

    /** Shortest-arc rotation taking unit vector [from] to unit vector [to] (three.js `setFromUnitVectors`). */
    fun setFromUnitVectors(from: Vec3, to: Vec3): Quat {
        val fx = from.x.toDouble(); val fy = from.y.toDouble(); val fz = from.z.toDouble()
        val tx = to.x.toDouble(); val ty = to.y.toDouble(); val tz = to.z.toDouble()
        var r = fx * tx + fy * ty + fz * tz + 1
        val qx: Double; val qy: Double; val qz: Double
        if (r < 1e-8) {
            r = 0.0
            if (abs(fx) > abs(fz)) { qx = -fy; qy = fx; qz = 0.0 } else { qx = 0.0; qy = -fz; qz = fy }
        } else {
            qx = fy * tz - fz * ty
            qy = fz * tx - fx * tz
            qz = fx * ty - fy * tx
        }
        return normalizeD(qx, qy, qz, r)
    }

    private fun normalizeD(x: Double, y: Double, z: Double, w: Double): Quat {
        val l = sqrt(x * x + y * y + z * z + w * w)
        if (l == 0.0) return set(0f, 0f, 0f, 1f)
        val inv = 1 / l
        return setD(x * inv, y * inv, z * inv, w * inv)
    }

    fun dot(o: Quat): Float = x * o.x + y * o.y + z * o.z + w * o.w
    fun lengthSq(): Float = x * x + y * y + z * z + w * w
    fun length(): Float = sqrt(lengthSq())
    fun normalize(): Quat = normalizeD(x.toDouble(), y.toDouble(), z.toDouble(), w.toDouble())

    /** Conjugate; equals the inverse for unit quaternions (three.js `invert`). */
    fun invert(): Quat = conjugate()
    fun conjugate(): Quat { x = -x; y = -y; z = -z; return this }

    /** Angle in radians between two unit quaternions. */
    fun angleTo(q: Quat): Float {
        val dot = x.toDouble() * q.x + y.toDouble() * q.y + z.toDouble() * q.z + w.toDouble() * q.w
        return (2 * acos(abs(MathUtil.clamp(dot, -1.0, 1.0)))).toFloat()
    }

    /** this = this * q */
    fun multiply(q: Quat): Quat = multiplyQuaternions(this, q)

    /** this = q * this */
    fun premultiply(q: Quat): Quat = multiplyQuaternions(q, this)

    fun multiplyQuaternions(a: Quat, b: Quat): Quat {
        val qax = a.x.toDouble(); val qay = a.y.toDouble(); val qaz = a.z.toDouble(); val qaw = a.w.toDouble()
        val qbx = b.x.toDouble(); val qby = b.y.toDouble(); val qbz = b.z.toDouble(); val qbw = b.w.toDouble()
        return setD(
            qax * qbw + qaw * qbx + qay * qbz - qaz * qby,
            qay * qbw + qaw * qby + qaz * qbx - qax * qbz,
            qaz * qbw + qaw * qbz + qax * qby - qay * qbx,
            qaw * qbw - qax * qbx - qay * qby - qaz * qbz,
        )
    }

    /** Spherical interpolation towards [qb] (three.js `slerp`, including its nlerp fallback). */
    fun slerp(qb: Quat, t: Float): Quat {
        var bx = qb.x.toDouble(); var by = qb.y.toDouble(); var bz = qb.z.toDouble(); var bw = qb.w.toDouble()
        val ax = x.toDouble(); val ay = y.toDouble(); val az = z.toDouble(); val aw = w.toDouble()
        var dot = ax * bx + ay * by + az * bz + aw * bw
        if (dot < 0) { bx = -bx; by = -by; bz = -bz; bw = -bw; dot = -dot }
        var s = 1 - t.toDouble()
        var tt = t.toDouble()
        if (dot < 0.9995) {
            val theta = acos(dot)
            val sinT = sin(theta)
            s = sin(s * theta) / sinT
            tt = sin(tt * theta) / sinT
            return setD(ax * s + bx * tt, ay * s + by * tt, az * s + bz * tt, aw * s + bw * tt)
        }
        return normalizeD(ax * s + bx * tt, ay * s + by * tt, az * s + bz * tt, aw * s + bw * tt)
    }

    fun slerpQuaternions(a: Quat, b: Quat, t: Float): Quat = set(a).slerp(b, t)

    /** Rotates towards [q] by at most [step] radians (three.js `rotateTowards`). */
    fun rotateTowards(q: Quat, step: Float): Quat {
        val angle = angleTo(q)
        if (angle == 0f) return this
        val t = min(1f, step / angle)
        return slerp(q, t)
    }

    /** XYZ Euler angles of this rotation, written to [target] (three.js `Euler.setFromQuaternion`). */
    fun toEulerXYZ(target: Vec3 = Vec3()): Vec3 = Euler.fromQuat(this, target)

    fun equals(o: Quat): Boolean = x == o.x && y == o.y && z == o.z && w == o.w

    override fun toString(): String = "Quat($x, $y, $z, $w)"
}
