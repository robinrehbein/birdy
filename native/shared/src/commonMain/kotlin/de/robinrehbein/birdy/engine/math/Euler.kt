package de.robinrehbein.birdy.engine.math

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2

/**
 * three.js `Euler` conversions for the default 'XYZ' order (the only order the game uses).
 * Euler angles are stored in a [Vec3] (x, y, z radians), like [de.robinrehbein.birdy.engine.scene.Node.rotation].
 */
object Euler {
    /** three.js `Euler.setFromRotationMatrix(m, 'XYZ')`; [m]'s upper 3x3 must be unscaled. */
    fun fromRotationMatrix(m: Mat4, target: Vec3 = Vec3()): Vec3 {
        val te = m.e
        return fromRotation3(
            te[0].toDouble(), te[4].toDouble(), te[8].toDouble(),
            te[5].toDouble(), te[9].toDouble(),
            te[6].toDouble(), te[10].toDouble(), target,
        )
    }

    /** three.js `Euler.setFromQuaternion(q, 'XYZ')`. */
    fun fromQuat(q: Quat, target: Vec3 = Vec3()): Vec3 {
        val x = q.x.toDouble(); val y = q.y.toDouble(); val z = q.z.toDouble(); val w = q.w.toDouble()
        val x2 = x + x; val y2 = y + y; val z2 = z + z
        val xx = x * x2; val xy = x * y2; val xz = x * z2
        val yy = y * y2; val yz = y * z2; val zz = z * z2
        val wx = w * x2; val wy = w * y2; val wz = w * z2
        return fromRotation3(
            1 - (yy + zz), xy - wz, xz + wy,
            1 - (xx + zz), yz - wx,
            yz + wx, 1 - (xx + yy), target,
        )
    }

    private fun fromRotation3(
        m11: Double, m12: Double, m13: Double,
        m22: Double, m23: Double,
        m32: Double, m33: Double,
        target: Vec3,
    ): Vec3 {
        val ey = asin(MathUtil.clamp(m13, -1.0, 1.0))
        val ex: Double
        val ez: Double
        if (abs(m13) < 0.9999999) {
            ex = atan2(-m23, m33)
            ez = atan2(-m12, m11)
        } else {
            ex = atan2(m32, m22)
            ez = 0.0
        }
        return target.set(ex.toFloat(), ey.toFloat(), ez.toFloat())
    }
}
