package de.robinrehbein.birdy.view.fx

import de.robinrehbein.birdy.engine.math.MathUtil
import de.robinrehbein.birdy.engine.math.Quat
import de.robinrehbein.birdy.engine.math.Vec3
import de.robinrehbein.birdy.engine.mesh.Primitives
import de.robinrehbein.birdy.engine.mesh.Shape
import de.robinrehbein.birdy.engine.mesh.mergeGeometries
import de.robinrehbein.birdy.engine.mesh.setVertexColor
import de.robinrehbein.birdy.engine.mesh.toNonIndexed
import de.robinrehbein.birdy.engine.mesh.translate
import de.robinrehbein.birdy.engine.scene.BasicMaterial
import de.robinrehbein.birdy.engine.scene.Geometry
import de.robinrehbein.birdy.engine.scene.Mesh
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Crash feedback: a comic "bonk" star at the point of impact that pops up during the
 * freeze-frame and fades (effects.js `createImpact`). Three baked star layers, one draw call,
 * billboarded and spinning about the view axis, drawn on top of everything.
 */
class Impact {
    val mesh = Mesh(
        bonkGeometry(),
        BasicMaterial().apply {
            vertexColors = true; transparent = true; depthTest = false; depthWrite = false; fog = false
        },
        "impact",
    ).apply {
        renderOrder = 11
        visible = false
        quaternionOverride = Quat()
    }

    /** Seconds since [hit]. */
    var t = 1.0
        private set

    private val spinAxis = Vec3(0f, 0f, 1f)
    private val spin = Quat()

    /** Impact at world position ([x], [y], [z]). */
    fun hit(x: Double, y: Double, z: Double) {
        t = 0.0
        mesh.position.set(x.toFloat(), y.toFloat(), z.toFloat())
        mesh.visible = true
        mesh.material.opacity = 1f
        mesh.scale.setScalar(0.5f)
    }

    /** Advances the pop/fade by [dt]; [cameraQuat] is the camera's orientation (billboard). */
    fun update(dt: Double, cameraQuat: Quat) {
        if (!mesh.visible) return
        t += dt
        mesh.quaternionOverride!!.set(cameraQuat).multiply(spin.setFromAxisAngle(spinAxis, t * 1.5))
        mesh.scale.setScalar(scaleAt(t).toFloat())
        mesh.material.opacity = opacityAt(t).toFloat()
        if (t > 0.5) mesh.visible = false
    }

    fun clear() {
        mesh.visible = false
    }

    companion object {
        /** Pops from 0.5 to 0.8 over 0.12 s, then holds. */
        fun scaleAt(t: Double): Double = 0.5 + 0.3 * min(1.0, t / 0.12)

        /** Opaque until 0.25 s, eased out by 0.5 s. */
        fun opacityAt(t: Double): Double = 1 - MathUtil.smoothstep(t, 0.25, 0.5)

        /** Plum outline star, white star, yellow core: (points, outer, inner, colour, z). */
        val LAYERS = listOf(
            Layer(8, 1.25, 0.62, 0x543847, 0.0),
            Layer(8, 1.08, 0.52, 0xffffff, 0.01),
            Layer(8, 0.62, 0.34, 0xffe14a, 0.02),
        )

        fun starShape(points: Int, outer: Double, inner: Double): Shape {
            val shape = Shape()
            for (i in 0 until points * 2) {
                val r = if (i % 2 == 1) inner else outer
                val a = i.toDouble() / (points * 2) * PI * 2 + PI / 2
                if (i == 0) shape.moveTo(cos(a) * r, sin(a) * r) else shape.lineTo(cos(a) * r, sin(a) * r)
            }
            return shape
        }

        private fun bonkGeometry(): Geometry = mergeGeometries(
            LAYERS.map { l ->
                val g = Primitives.shape(starShape(l.points, l.outer, l.inner)).translate(0.0, 0.0, l.z).toNonIndexed()
                Geometry(g.positions, null, null, null, null).setVertexColor(l.color)
            },
        )
    }

    data class Layer(val points: Int, val outer: Double, val inner: Double, val color: Int, val z: Double)
}
