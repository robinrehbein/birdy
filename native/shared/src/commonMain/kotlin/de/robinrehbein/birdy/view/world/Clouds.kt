package de.robinrehbein.birdy.view.world

import de.robinrehbein.birdy.engine.mesh.Primitives
import de.robinrehbein.birdy.engine.mesh.bake
import de.robinrehbein.birdy.engine.scene.BasicMaterial
import de.robinrehbein.birdy.engine.scene.Geometry
import de.robinrehbein.birdy.engine.scene.Mesh
import de.robinrehbein.birdy.engine.scene.Node
import de.robinrehbein.birdy.engine.scene.Scene
import de.robinrehbein.birdy.engine.scene.StandardMaterial
import de.robinrehbein.birdy.game.WorldConst
import de.robinrehbein.birdy.game.sim.RowCloudPicker
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

/** Cloud materials: lit a little from within so the undersides seen from below stay soft. */
internal object CloudMaterials {
    fun sky() = StandardMaterial().apply {
        vertexColors = true
        roughness = 1f
        flatShading = true
        emissive.setHex(0xffffff)
        emissiveIntensity = 0.22f
    }

    /** world.js `bankMat`; its colour follows the sky clouds' (aliased in JS). */
    fun bank() = StandardMaterial().apply {
        vertexColors = true
        roughness = 1f
        flatShading = true
        emissive.setHex(0xffffff)
        emissiveIntensity = 0.32f
    }
}

/** world.js `createClouds(scene)`: 18 baked cloud clusters drifting with parallax. */
class Clouds(scene: Scene, private val rng: Random) {
    val material = CloudMaterials.sky()
    val clouds: List<Mesh>
    private val speeds = DoubleArray(COUNT)

    init {
        val geo = Primitives.icosahedron(1.0, 1)
        val white = BasicMaterial()
        clouds = List(COUNT) { i ->
            val g = Node()
            val n = 3 + floor(rng.nextDouble() * 3).toInt()
            for (j in 0 until n) {
                val puff = Mesh(geo, white)
                puff.position.set((j * 1.6 - n * 0.8).toFloat(), (rng.nextDouble() * 0.6).toFloat(), (rng.nextDouble() * 1.2).toFloat())
                puff.scale.setScalar((1.3 + rng.nextDouble() * 0.9).toFloat())
                g.add(puff)
            }
            Mesh(bake(g), material, "cloud$i").apply {
                position.set(
                    ((rng.nextDouble() - 0.5) * 90).toFloat(),
                    (18 + rng.nextDouble() * 14).toFloat(),
                    (-rng.nextDouble() * 220).toFloat(),
                )
                speeds[i] = 0.3 + rng.nextDouble() * 0.3
                scene.add(this)
            }
        }
    }

    fun update(dz: Double) {
        for ((i, c) in clouds.withIndex()) {
            c.position.z += (dz * speeds[i]).toFloat()
            if (c.position.z > 30) {
                c.position.z -= 250f
                c.position.x = ((rng.nextDouble() - 0.5) * 90).toFloat()
            }
        }
    }

    private companion object {
        const val COUNT = 18
    }
}

/**
 * The six baked pipe-row cloud decorations (world.js:684-719): banks the pipes pass through and
 * collars around each upper pipe, three seeded variants each, in `cloudGeos` order.
 */
internal object RowCloudGeometry {
    val all: List<Geometry> by lazy { (1..3).map(::bank) + (1..3).map(::collar) }

    fun bank(seed: Int): Geometry {
        val rnd = RowCloudPicker.bankRandom(seed)
        val root = Node()
        val puff = Primitives.icosahedron(1.0, 1)
        val white = BasicMaterial().apply { color.setHex(0xffffff) }
        val shade = BasicMaterial().apply { color.setHex(0xe9eef5) }
        var x = -6.5
        while (x <= 6.5) {
            val s = 1.5 + rnd() * 0.9
            val m = Mesh(puff, if (rnd() < 0.3) shade else white)
            val y = WorldConst.BANK_Y + rnd() * 0.9
            val z = (rnd() - 0.5) * 1.6
            m.position.set(x.toFloat(), y.toFloat(), z.toFloat())
            m.scale.set((s * 1.2).toFloat(), (s * 0.62).toFloat(), s.toFloat())
            root.add(m)
            x += 1.6 + rnd() * 0.5
        }
        return bake(root)
    }

    fun collar(seed: Int): Geometry {
        val rnd = RowCloudPicker.collarRandom(seed)
        val root = Node()
        val puff = Primitives.icosahedron(1.0, 1)
        val white = BasicMaterial().apply { color.setHex(0xffffff) }
        for (x in WorldConst.LANES) {
            for (k in 0 until 5) {
                val a = (k / 5.0) * PI * 2 + rnd()
                val m = Mesh(puff, white)
                val s = 0.8 + rnd() * 0.4
                val y = WorldConst.BANK_Y + (rnd() - 0.5) * 0.5
                m.position.set((x + cos(a) * 1.25).toFloat(), y.toFloat(), (sin(a) * 1.25).toFloat())
                m.scale.set((s * 1.1).toFloat(), (s * 0.65).toFloat(), s.toFloat())
                root.add(m)
            }
        }
        return bake(root)
    }
}
