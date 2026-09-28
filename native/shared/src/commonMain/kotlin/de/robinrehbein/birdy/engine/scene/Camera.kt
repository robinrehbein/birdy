package de.robinrehbein.birdy.engine.scene

import de.robinrehbein.birdy.engine.math.DMat
import de.robinrehbein.birdy.engine.math.Mat4
import de.robinrehbein.birdy.engine.math.Quat
import de.robinrehbein.birdy.engine.math.Vec3
import kotlin.math.PI
import kotlin.math.tan

/**
 * three.js `PerspectiveCamera` (vertical [fov] in degrees). Supports `setViewOffset` for the
 * asymmetric menu framing (main-b.md §17). Call [updateProjection] after changing any parameter.
 */
class PerspectiveCamera(
    var fov: Float = 60f,
    var aspect: Float = 1f,
    var near: Float = 0.1f,
    var far: Float = 400f,
) : Node("camera") {
    val projectionMatrix = Mat4()
    /** Inverse of [projectionMatrix]; refreshed by [updateProjection]. */
    val projectionMatrixInverse = Mat4()
    /** Inverse of the world matrix; refreshed by [updateView]. */
    val viewMatrix = Mat4()
    private val viewProjection = Mat4()

    var view: ViewOffset? = null

    /** World-space orientation, valid after [updateView]; billboards copy it. */
    val worldQuaternion = Quat()

    fun setViewOffset(fullWidth: Float, fullHeight: Float, x: Float, y: Float, width: Float, height: Float) {
        view = ViewOffset(fullWidth, fullHeight, x, y, width, height)
    }

    fun clearViewOffset() { view = null }

    /** Same math as three.js `PerspectiveCamera.updateProjectionMatrix` (zoom = 1, filmOffset = 0). */
    fun updateProjection() {
        val n = near.toDouble()
        var top = n * tan(PI / 180.0 * 0.5 * fov)
        var height = 2 * top
        var width = aspect * height
        var left = -0.5 * width
        val v = view
        if (v != null) {
            val fullWidth = v.fullWidth.toDouble()
            val fullHeight = v.fullHeight.toDouble()
            left += v.offsetX * width / fullWidth
            top -= v.offsetY * height / fullHeight
            width *= v.width / fullWidth
            height *= v.height / fullHeight
        }
        val m = DMat.frustum(left, left + width, top, top - height, n, far.toDouble())
        for (i in 0 until 16) projectionMatrix.e[i] = m[i].toFloat()
        val inv = DMat.invert(m)
        for (i in 0 until 16) projectionMatrixInverse.e[i] = inv[i].toFloat()
    }

    /**
     * Recomputes [viewMatrix]/[worldQuaternion] from the (already updated) world matrix.
     * For a root-level camera the orientation is taken directly from its rotation (exact); otherwise
     * it is decomposed from [worldMatrix].
     */
    fun updateView() {
        viewMatrix.invert(worldMatrix)
        if (parent == null) getQuaternion(worldQuaternion)
        else worldMatrix.decompose(Vec3(), worldQuaternion, Vec3())
    }

    /** World point -> normalized device coordinates in place (three.js `Vector3.project`). */
    fun project(p: Vec3): Vec3 {
        viewProjection.multiply(projectionMatrix, viewMatrix)
        return p.applyMat4(viewProjection)
    }

    /** NDC -> world point in place (three.js `Vector3.unproject`). */
    fun unproject(p: Vec3): Vec3 = p.applyMat4(projectionMatrixInverse).applyMat4(worldMatrix)

    /** Cameras look down their local -Z (three.js `Camera.getWorldDirection`). */
    override fun getWorldDirection(target: Vec3): Vec3 {
        updateWorldMatrix(true, false)
        val e = worldMatrix.e
        return target.set(-e[8], -e[9], -e[10]).normalize()
    }

    class ViewOffset(
        val fullWidth: Float, val fullHeight: Float,
        val offsetX: Float, val offsetY: Float,
        val width: Float, val height: Float,
    )
}
