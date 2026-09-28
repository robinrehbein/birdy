package de.robinrehbein.birdy.view.fx

import de.robinrehbein.birdy.engine.math.Mat4
import de.robinrehbein.birdy.engine.math.Quat
import de.robinrehbein.birdy.engine.math.Vec3
import de.robinrehbein.birdy.engine.mesh.Primitives
import de.robinrehbein.birdy.engine.scene.BasicMaterial
import de.robinrehbein.birdy.engine.scene.InstanceData
import de.robinrehbein.birdy.engine.scene.Mesh

/**
 * Speed streaks beside and above the track that rush past the camera (effects.js
 * `createSpeedLines`). Opacity and length follow the rush amount; while invisible the lines freeze.
 */
class SpeedLines(private val random: kotlin.random.Random, val count: Int = 28) {
    private val material = BasicMaterial().apply {
        color.setHex(0xffffff); transparent = true; opacity = 0f; depthWrite = false; fog = false
    }
    private val instances = InstanceData(count)
    val mesh = Mesh(Primitives.box(0.11f, 0.11f, 3.6f), material, "speedLines").also { it.instances = instances }
    private val x = DoubleArray(count)
    private val y = DoubleArray(count)
    private val z = DoubleArray(count)
    private val m = Mat4()
    private val q = Quat()
    private val p = Vec3()
    private val s = Vec3()

    init {
        val id = Mat4()
        for (i in 0 until count) instances.setMatrix(i, id)
        for (i in 0 until count) place(i, -random.nextDouble() * 60)
    }

    private fun place(i: Int, zz: Double) {
        val side = if (random.nextDouble() < 0.5) -1 else 1
        x[i] = side * (4.2 + random.nextDouble() * 5)
        y[i] = 1 + random.nextDouble() * 11
        z[i] = zz
    }

    /** [dz] = world movement this frame, [amount] = rush 0..1. */
    fun update(dz: Double, amount: Double) {
        material.opacity = opacityFor(amount).toFloat()
        mesh.visible = amount > 0.01
        if (!mesh.visible) return
        for (i in 0 until count) {
            z[i] += dz * 1.6 // a bit faster than the world for a rush
            if (z[i] > 12) place(i, -50 - random.nextDouble() * 15)
            p.set(x[i].toFloat(), y[i].toFloat(), z[i].toFloat())
            s.set(1f, 1f, scaleZFor(amount).toFloat())
            m.compose(p, q, s)
            instances.setMatrix(i, m)
        }
        instances.markDirty()
    }

    val opacity: Float get() = material.opacity
    fun linePosition(i: Int): DoubleArray = doubleArrayOf(x[i], y[i], z[i])

    companion object {
        fun opacityFor(amount: Double): Double = 0.7 * amount
        fun scaleZFor(amount: Double): Double = 0.6 + amount

        /** main.js rush: 0 unless playing and not holding; star adds a flat 0.6. */
        fun rush(playing: Boolean, hold: Boolean, speed: Double, star: Boolean): Double =
            if (playing && !hold) kotlin.math.min(1.0, kotlin.math.max(0.0, (speed - 22) / 14) + (if (star) 0.6 else 0.0)) else 0.0
    }
}
