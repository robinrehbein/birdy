package de.robinrehbein.birdy.view.fx

import de.robinrehbein.birdy.engine.math.Color
import de.robinrehbein.birdy.engine.math.Mat4
import de.robinrehbein.birdy.engine.math.Quat
import de.robinrehbein.birdy.engine.math.Vec3
import de.robinrehbein.birdy.engine.mesh.Primitives
import de.robinrehbein.birdy.engine.scene.BasicMaterial
import de.robinrehbein.birdy.engine.scene.InstanceData
import de.robinrehbein.birdy.engine.scene.Mesh
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.random.Random

/** effects.js `emit` options with the JS defaults. */
data class EmitOptions(
    val count: Int = 10,
    val colors: List<Int> = listOf(0xffffff),
    val speed: Double = 4.0,
    val size: Double = 0.12,
    val life: Double = 0.6,
    val gravity: Double = -6.0,
    val drag: Double = 1.5,
    val spread: Double = 1.0,
    /** Constant velocity added along +Z (backwards, for trails). */
    val drift: Double = 0.0,
)

/**
 * Pooled particles drawn as one instanced mesh of unit icosahedra (effects.js `createParticles`).
 * Slots are recycled in strict round-robin order, overwriting live particles when a burst is
 * larger than what is free. Simulation state is kept in doubles (JS numbers) so traces match the
 * goldens; [random] supplies `Math.random()`.
 */
class Particles(private val random: Random, val max: Int = 300) {
    // Per-slot state: life, maxLife, size, gravity, drag, spin; pos and vel as xyz triples.
    private val life = DoubleArray(max)
    private val maxLife = DoubleArray(max) { 1.0 }
    private val size = DoubleArray(max) { 0.1 }
    private val gravity = DoubleArray(max)
    private val drag = DoubleArray(max)
    private val spin = DoubleArray(max)
    private val pos = DoubleArray(max * 3)
    private val vel = DoubleArray(max * 3)
    private var cursor = 0

    private val instances = InstanceData(max, withColors = true)
    val mesh = Mesh(Primitives.icosahedron(1.0, 0), BasicMaterial(), "particles").also { it.instances = instances }

    private val m = Mat4()
    private val q = Quat()
    private val p = Vec3()
    private val s = Vec3()
    private val color = Color()

    init {
        s.setScalar(0f)
        m.compose(p, q, s)
        for (i in 0 until max) instances.setMatrix(i, m)
        instances.markDirty()
    }

    /** Emits [o.count] particles at ([x], [y], [z]). */
    fun emit(x: Double, y: Double, z: Double, o: EmitOptions) {
        for (n in 0 until o.count) {
            val i = cursor
            cursor = (cursor + 1) % max
            pos[i * 3] = x; pos[i * 3 + 1] = y; pos[i * 3 + 2] = z
            var vx = random.nextDouble() - 0.5
            var vy = random.nextDouble() - 0.5
            var vz = random.nextDouble() - 0.5
            // three.js normalize(): multiplyScalar(1 / (length || 1)).
            val inv = 1 / sqrt(vx * vx + vy * vy + vz * vz).let { if (it == 0.0) 1.0 else it }
            vx *= inv; vy *= inv; vz *= inv
            val k = o.speed * (0.4 + random.nextDouble() * 0.6) * o.spread
            vel[i * 3] = vx * k; vel[i * 3 + 1] = vy * k; vel[i * 3 + 2] = vz * k + o.drift
            val l = o.life * (0.7 + random.nextDouble() * 0.6)
            life[i] = l; maxLife[i] = l
            size[i] = o.size * (0.6 + random.nextDouble() * 0.8)
            gravity[i] = o.gravity
            drag[i] = o.drag
            spin[i] = random.nextDouble() * 6
            val hex = o.colors[floor(random.nextDouble() * o.colors.size).toInt()]
            instances.setColor(i, color.setHex(hex))
        }
        instances.markDirty()
    }

    /** effects.js `emitColor`: single-colour burst. */
    fun emitColor(x: Double, y: Double, z: Double, hex: Int, o: EmitOptions) = emit(x, y, z, o.copy(colors = listOf(hex)))

    /** Advances live particles by [dt]; [dz] is how far the world scrolled this frame. */
    fun update(dt: Double, dz: Double) {
        for (i in 0 until max) {
            if (life[i] <= 0) continue
            life[i] -= dt
            vel[i * 3 + 1] += gravity[i] * dt
            val damp = max(0.0, 1 - drag[i] * dt)
            for (c in 0 until 3) {
                vel[i * 3 + c] *= damp
                pos[i * 3 + c] += vel[i * 3 + c] * dt
            }
            pos[i * 3 + 2] += dz
            val k = max(0.0, life[i] / maxLife[i])
            p.set(pos[i * 3].toFloat(), pos[i * 3 + 1].toFloat(), pos[i * 3 + 2].toFloat())
            q.setFromEulerXYZ(spin[i] * k, spin[i] * 1.3 * k, 0.0)
            s.setScalar(if (life[i] > 0) renderScale(size[i], k).toFloat() else 0f)
            m.compose(p, q, s)
            instances.setMatrix(i, m)
        }
        instances.markDirty()
    }

    /** Kills every particle and hides all instances. */
    fun clear() {
        life.fill(0.0)
        s.setScalar(0f)
        p.set(0f, 0f, 0f)
        q.identity()
        m.compose(p, q, s)
        for (i in 0 until max) instances.setMatrix(i, m)
        instances.markDirty()
    }

    /** Snapshot of one slot for tests and debugging. */
    fun slot(i: Int): Slot = Slot(
        life[i], maxLife[i], size[i], gravity[i], drag[i], spin[i],
        pos.copyOfRange(i * 3, i * 3 + 3), vel.copyOfRange(i * 3, i * 3 + 3),
        instances.colors!!.copyOfRange(i * 3, i * 3 + 3),
    )

    /** Number of particles still alive. */
    val alive: Int get() = life.count { it > 0 }

    class Slot(
        val life: Double, val maxLife: Double, val size: Double, val gravity: Double, val drag: Double,
        val spin: Double, val pos: DoubleArray, val vel: DoubleArray,
        /** Linear instance colour. */
        val color: FloatArray,
    )

    companion object {
        /** Render size: shrinks toward 30 % of the base size as the particle dies. */
        fun renderScale(size: Double, k: Double): Double = size * (0.3 + 0.7 * k)
    }
}
