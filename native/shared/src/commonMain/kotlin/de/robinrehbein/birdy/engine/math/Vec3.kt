package de.robinrehbein.birdy.engine.math

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Mutable 3-component float vector (render-side; simulation code uses plain Doubles).
 * Method names and formulas follow three.js `Vector3`; [set] (Vec3) and [copy] (Vec3) are three.js `copy`,
 * the no-arg [copy] is three.js `clone`.
 */
class Vec3(var x: Float = 0f, var y: Float = 0f, var z: Float = 0f) {
    fun set(x: Float, y: Float, z: Float): Vec3 { this.x = x; this.y = y; this.z = z; return this }
    fun set(o: Vec3): Vec3 = set(o.x, o.y, o.z)
    fun setScalar(s: Float): Vec3 = set(s, s, s)
    fun copy(): Vec3 = Vec3(x, y, z)
    fun copy(o: Vec3): Vec3 = set(o)
    fun clone(): Vec3 = Vec3(x, y, z)

    operator fun get(i: Int): Float = when (i) { 0 -> x; 1 -> y; 2 -> z; else -> throw IndexOutOfBoundsException("$i") }
    operator fun set(i: Int, v: Float) { when (i) { 0 -> x = v; 1 -> y = v; 2 -> z = v; else -> throw IndexOutOfBoundsException("$i") } }

    fun add(o: Vec3): Vec3 { x += o.x; y += o.y; z += o.z; return this }
    fun add(x: Float, y: Float, z: Float): Vec3 { this.x += x; this.y += y; this.z += z; return this }
    fun addScalar(s: Float): Vec3 { x += s; y += s; z += s; return this }
    fun addVectors(a: Vec3, b: Vec3): Vec3 = set(a.x + b.x, a.y + b.y, a.z + b.z)
    fun addScaledVector(v: Vec3, s: Float): Vec3 { x += v.x * s; y += v.y * s; z += v.z * s; return this }
    fun sub(o: Vec3): Vec3 { x -= o.x; y -= o.y; z -= o.z; return this }
    fun subVectors(a: Vec3, b: Vec3): Vec3 = set(a.x - b.x, a.y - b.y, a.z - b.z)
    fun scale(s: Float): Vec3 { x *= s; y *= s; z *= s; return this }
    fun multiplyScalar(s: Float): Vec3 = scale(s)
    fun multiply(o: Vec3): Vec3 { x *= o.x; y *= o.y; z *= o.z; return this }
    fun divide(o: Vec3): Vec3 { x /= o.x; y /= o.y; z /= o.z; return this }
    fun divideScalar(s: Float): Vec3 = scale(1f / s)
    fun negate(): Vec3 { x = -x; y = -y; z = -z; return this }
    fun min(o: Vec3): Vec3 = set(kotlin.math.min(x, o.x), kotlin.math.min(y, o.y), kotlin.math.min(z, o.z))
    fun max(o: Vec3): Vec3 = set(kotlin.math.max(x, o.x), kotlin.math.max(y, o.y), kotlin.math.max(z, o.z))

    fun dot(o: Vec3): Float = x * o.x + y * o.y + z * o.z
    fun cross(o: Vec3): Vec3 = set(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    fun crossVectors(a: Vec3, b: Vec3): Vec3 =
        set(a.y * b.z - a.z * b.y, a.z * b.x - a.x * b.z, a.x * b.y - a.y * b.x)

    fun lengthSq(): Float = x * x + y * y + z * z
    fun length(): Float = sqrt(x * x + y * y + z * z)
    fun normalize(): Vec3 { val l = length(); if (l > 0f) scale(1f / l); return this }
    fun setLength(l: Float): Vec3 = normalize().scale(l)

    fun distanceToSquared(o: Vec3): Float { val dx = x - o.x; val dy = y - o.y; val dz = z - o.z; return dx * dx + dy * dy + dz * dz }
    fun distanceTo(o: Vec3): Float = sqrt(distanceToSquared(o))

    /** three.js `angleTo`: angle in radians between this and [o]. */
    fun angleTo(o: Vec3): Float {
        val denominator = sqrt(lengthSq().toDouble() * o.lengthSq())
        if (denominator == 0.0) return (kotlin.math.PI / 2).toFloat()
        val theta = dot(o) / denominator
        return acos(MathUtil.clamp(theta, -1.0, 1.0)).toFloat()
    }

    fun lerp(o: Vec3, t: Float): Vec3 { x += (o.x - x) * t; y += (o.y - y) * t; z += (o.z - z) * t; return this }
    fun lerpVectors(a: Vec3, b: Vec3, t: Float): Vec3 =
        set(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t)

    fun equals(o: Vec3): Boolean = x == o.x && y == o.y && z == o.z
    fun approxEquals(o: Vec3, eps: Float = 1e-6f): Boolean = abs(x - o.x) <= eps && abs(y - o.y) <= eps && abs(z - o.z) <= eps

    /** Transforms this point by [m] (w=1) including perspective divide, like three.js `applyMatrix4`. */
    fun applyMat4(m: Mat4): Vec3 {
        val e = m.e
        val w = 1f / (e[3] * x + e[7] * y + e[11] * z + e[15])
        val nx = (e[0] * x + e[4] * y + e[8] * z + e[12]) * w
        val ny = (e[1] * x + e[5] * y + e[9] * z + e[13]) * w
        val nz = (e[2] * x + e[6] * y + e[10] * z + e[14]) * w
        return set(nx, ny, nz)
    }

    fun applyMat3(m: Mat3): Vec3 {
        val e = m.e
        return set(
            e[0] * x + e[3] * y + e[6] * z,
            e[1] * x + e[4] * y + e[7] * z,
            e[2] * x + e[5] * y + e[8] * z,
        )
    }

    /** three.js `applyNormalMatrix`: multiply by a normal matrix and normalize. */
    fun applyNormalMatrix(m: Mat3): Vec3 = applyMat3(m).normalize()

    /** Direction transform by the upper 3x3 of [m], normalized (three.js `transformDirection`). */
    fun transformDirection(m: Mat4): Vec3 {
        val e = m.e
        return set(
            e[0] * x + e[4] * y + e[8] * z,
            e[1] * x + e[5] * y + e[9] * z,
            e[2] * x + e[6] * y + e[10] * z,
        ).normalize()
    }

    /** three.js `applyQuaternion` (v + 2w(q×v) + 2q×(q×v) form). */
    fun applyQuat(q: Quat): Vec3 {
        val vx = x; val vy = y; val vz = z
        val qx = q.x; val qy = q.y; val qz = q.z; val qw = q.w
        val tx = 2 * (qy * vz - qz * vy)
        val ty = 2 * (qz * vx - qx * vz)
        val tz = 2 * (qx * vy - qy * vx)
        return set(
            vx + qw * tx + qy * tz - qz * ty,
            vy + qw * ty + qz * tx - qx * tz,
            vz + qw * tz + qx * ty - qy * tx,
        )
    }

    fun applyQuaternion(q: Quat): Vec3 = applyQuat(q)

    /** Rotation by XYZ Euler angles (three.js `applyEuler`). */
    fun applyEulerXYZ(ex: Float, ey: Float, ez: Float): Vec3 = applyQuat(Quat().setFromEulerXYZ(ex, ey, ez))

    fun applyAxisAngle(axis: Vec3, angle: Float): Vec3 = applyQuat(Quat().setFromAxisAngle(axis, angle))

    fun setFromMatrixPosition(m: Mat4): Vec3 = set(m.e[12], m.e[13], m.e[14])
    fun setFromMatrixColumn(m: Mat4, index: Int): Vec3 = set(m.e[index * 4], m.e[index * 4 + 1], m.e[index * 4 + 2])
    fun setFromMatrixScale(m: Mat4): Vec3 {
        val sx = setFromMatrixColumn(m, 0).length()
        val sy = setFromMatrixColumn(m, 1).length()
        val sz = setFromMatrixColumn(m, 2).length()
        return set(sx, sy, sz)
    }

    /**
     * three.js `setFromSphericalCoords(radius, phi, theta)`: [phi] is the polar angle from +Y,
     * [theta] the azimuth around Y measured from +Z towards +X.
     */
    fun setFromSphericalCoords(radius: Double, phi: Double, theta: Double): Vec3 {
        val sinPhiRadius = sin(phi) * radius
        return set((sinPhiRadius * sin(theta)).toFloat(), (cos(phi) * radius).toFloat(), (sinPhiRadius * cos(theta)).toFloat())
    }

    fun setFromSphericalCoords(radius: Float, phi: Float, theta: Float): Vec3 =
        setFromSphericalCoords(radius.toDouble(), phi.toDouble(), theta.toDouble())

    /** three.js `setFromCylindricalCoords(radius, theta, y)`. */
    fun setFromCylindricalCoords(radius: Double, theta: Double, y: Double): Vec3 =
        set((radius * sin(theta)).toFloat(), y.toFloat(), (radius * cos(theta)).toFloat())

    /**
     * World point -> NDC (three.js `Vector3.project(camera)`), given the camera's view matrix
     * (`matrixWorldInverse`) and projection matrix.
     */
    fun project(viewMatrix: Mat4, projectionMatrix: Mat4): Vec3 = applyMat4(viewMatrix).applyMat4(projectionMatrix)

    /** NDC -> world point (three.js `Vector3.unproject(camera)`). */
    fun unproject(projectionMatrixInverse: Mat4, cameraWorldMatrix: Mat4): Vec3 =
        applyMat4(projectionMatrixInverse).applyMat4(cameraWorldMatrix)

    fun toArray(out: FloatArray = FloatArray(3), offset: Int = 0): FloatArray {
        out[offset] = x; out[offset + 1] = y; out[offset + 2] = z
        return out
    }

    fun fromArray(a: FloatArray, offset: Int = 0): Vec3 = set(a[offset], a[offset + 1], a[offset + 2])

    override fun toString(): String = "Vec3($x, $y, $z)"
}
