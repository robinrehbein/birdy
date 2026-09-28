package de.robinrehbein.birdy.view.world

import de.robinrehbein.birdy.engine.math.Mat4
import de.robinrehbein.birdy.engine.math.Quat
import de.robinrehbein.birdy.engine.math.Vec3
import de.robinrehbein.birdy.engine.mesh.ExtrudeOptions
import de.robinrehbein.birdy.engine.mesh.Primitives
import de.robinrehbein.birdy.engine.mesh.Shape
import de.robinrehbein.birdy.engine.mesh.bake
import de.robinrehbein.birdy.engine.scene.BasicMaterial
import de.robinrehbein.birdy.engine.scene.Geometry
import de.robinrehbein.birdy.engine.scene.InstanceData
import de.robinrehbein.birdy.engine.scene.Mesh
import de.robinrehbein.birdy.engine.scene.Node
import de.robinrehbein.birdy.engine.scene.Scene
import de.robinrehbein.birdy.engine.scene.StandardMaterial
import de.robinrehbein.birdy.game.Coin
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The gold coin: face, raised rim and an embossed star on both faces (world.js:1025-1046). */
internal object CoinGeometry {
    val geometry: Geometry by lazy { build() }

    private fun m(hex: Int) = BasicMaterial().apply { color.setHex(hex) }

    private fun build(): Geometry {
        val root = Node("coin")
        val face = Mesh(Primitives.cylinder(0.4, 0.4, 0.1, 16), m(0xffcf33))
        face.rotation.x = (PI / 2).toFloat()
        val rim = Mesh(Primitives.torus(0.41, 0.055, 4, 16), m(0xf2a100))
        val star = Shape()
        for (i in 0 until 10) {
            val r = if (i % 2 != 0) 0.09 else 0.21
            val a = (i / 10.0) * PI * 2 + PI / 2
            if (i == 0) star.moveTo(cos(a) * r, sin(a) * r) else star.lineTo(cos(a) * r, sin(a) * r)
        }
        val starGeo = Primitives.extrude(star, ExtrudeOptions(depth = 0.03, bevelEnabled = false))
        val front = Mesh(starGeo, m(0xfff0a0))
        front.position.z = 0.04f
        val back = Mesh(starGeo, m(0xfff0a0))
        back.position.z = -0.04f
        back.rotation.y = PI.toFloat()
        root.add(face, rim, front, back)
        return bake(root)
    }
}

/**
 * world.js `createCoinField(scene, count)`: all coins as one instanced mesh. [sync] copies the
 * visible simulation coins (position, spin, shrink) into the instance matrices each frame.
 */
class CoinField(scene: Scene, capacity: Int) {
    /** Bright gold: little metalness and a warm glow of its own (no environment map). */
    val material = StandardMaterial().apply {
        vertexColors = true
        color.setHex(0xffffff)
        emissive.setHex(0xb07800)
        emissiveIntensity = 0.55f
        metalness = 0.15f
        roughness = 0.35f
        flatShading = true
    }
    private val data = InstanceData(capacity).apply { count = 0 }
    val mesh = Mesh(CoinGeometry.geometry, material, "coins").apply {
        instances = data
        visible = false
    }
    private val m = Mat4()
    private val q = Quat()
    private val p = Vec3()
    private val s = Vec3()

    init {
        scene.add(mesh)
    }

    fun sync(coins: List<Coin>) {
        var n = 0
        for (c in coins) {
            if (!c.visible || n >= data.capacity) continue
            p.set(c.x.toFloat(), c.y.toFloat(), c.z.toFloat())
            q.setFromEulerXYZ(0.0, c.spin, 0.0)
            s.setScalar(c.scale.toFloat())
            m.compose(p, q, s)
            data.setMatrix(n++, m)
        }
        data.count = n
        mesh.visible = n > 0
        data.markDirty()
    }
}
