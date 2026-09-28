package de.robinrehbein.birdy.view.world

import de.robinrehbein.birdy.engine.mesh.Primitives
import de.robinrehbein.birdy.engine.scene.BasicMaterial
import de.robinrehbein.birdy.engine.scene.Geometry
import de.robinrehbein.birdy.engine.scene.Mesh
import de.robinrehbein.birdy.engine.scene.Node
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

/** Shared primitive geometries of the scenery (world.js `G`, :234-245). */
internal object SceneryGeo {
    val trunk: Geometry by lazy { Primitives.cylinder(0.3, 0.4, 2.0, 6) }
    val leaf: Geometry by lazy { Primitives.icosahedron(1.8, 0) }
    val box: Geometry by lazy { Primitives.box(1.0, 1.0, 1.0) }
    val bush: Geometry by lazy { Primitives.icosahedron(0.8, 0) }
    val rock: Geometry by lazy { Primitives.dodecahedron(0.9, 0) }
    val cactus: Geometry by lazy { Primitives.cylinder(0.35, 0.4, 1.0, 7) }
    val cone: Geometry by lazy { Primitives.cone(1.0, 1.0, 4) }
    val pine: Geometry by lazy { Primitives.cone(1.5, 3.2, 7) }
    val hill: Geometry by lazy { Primitives.icosahedron(1.0, 1) }
    val flower: Geometry by lazy { Primitives.icosahedron(0.22, 0) }
}

/**
 * The scenery item generators of world.js (:247-410). [rng] replaces `Math.random()` (unseeded in
 * the game, seeded in tests and screenshots). Call order of the random draws follows the JS.
 */
internal class SceneryItems(private val rng: Random) {
    private val materials = HashMap<Int, BasicMaterial>()

    fun random(): Double = rng.nextDouble()
    fun rand(a: Double, b: Double): Double = a + rng.nextDouble() * (b - a)
    fun pick(colors: IntArray): Int = colors[floor(rng.nextDouble() * colors.size).toInt()]

    /** Colours only matter for baking, so one material per colour (world.js `sceneryMat`). */
    private fun mat(color: Int) = materials.getOrPut(color) { BasicMaterial().apply { this.color.setHex(color) } }

    fun mesh(
        geo: Geometry, color: Int,
        x: Double = 0.0, y: Double = 0.0, z: Double = 0.0,
        sx: Double = 1.0, sy: Double = sx, sz: Double = sx,
    ): Mesh = Mesh(geo, mat(color)).apply {
        position.set(x.toFloat(), y.toFloat(), z.toFloat())
        scale.set(sx.toFloat(), sy.toFloat(), sz.toFloat())
    }

    private fun group(vararg nodes: Node): Node = Node().apply { add(*nodes) }

    fun tree(leafColors: IntArray, trunk: Int = 0x8b5a2b): Node = group(
        mesh(SceneryGeo.trunk, trunk, 0.0, 1.0),
        mesh(SceneryGeo.leaf, pick(leafColors), 0.0, 3.2, 0.0, rand(0.8, 1.4)),
    )

    fun bush(colors: IntArray, flowers: IntArray? = null): Node {
        val b = Node()
        val n = 2 + floor(random() * 3).toInt()
        for (i in 0 until n) {
            val x = (i - n / 2.0) * 0.8
            val y = rand(0.3, 0.6)
            val z = random() * 0.6
            val s = rand(0.7, 1.2)
            b.add(mesh(SceneryGeo.bush, pick(colors), x, y, z, s))
            if (flowers != null) {
                b.add(mesh(SceneryGeo.flower, pick(flowers), x + rand(-0.3, 0.3), y + 0.6 * s, z + rand(-0.2, 0.3)))
            }
        }
        return b
    }

    fun building(colors: IntArray, windowColor: Int, roof: Int? = null): Node {
        val b = Node()
        val w = rand(4.0, 8.0)
        val h = if (roof != null) rand(4.0, 8.0) else rand(6.0, 20.0)
        val d = rand(4.0, 8.0)
        b.add(mesh(SceneryGeo.box, pick(colors), 0.0, h / 2, 0.0, w, h, d))
        var y = 2.0
        while (y < h - 1) {
            b.add(mesh(SceneryGeo.box, windowColor, 0.0, y, 0.0, w * 0.8, 0.8, d + 0.1))
            y += 2.5
        }
        if (roof != null) {
            val r = mesh(SceneryGeo.cone, roof, 0.0, h + 1.4, 0.0, w * 0.78, 2.8, d * 0.78)
            r.rotation.y = (PI / 4).toFloat()
            b.add(r)
        }
        return b
    }

    fun cactus(): Node {
        val c = Node()
        val h = rand(2.2, 3.6)
        val green = pick(intArrayOf(0x4f9d3a, 0x5cae45, 0x3f8a33))
        c.add(mesh(SceneryGeo.cactus, green, 0.0, h / 2, 0.0, 1.0, h, 1.0))
        for (side in intArrayOf(-1, 1)) {
            if (random() < 0.3) continue
            val ah = rand(0.8, 1.4)
            val y = rand(h * 0.4, h * 0.7)
            c.add(mesh(SceneryGeo.cactus, green, side * 0.55, y, 0.0, 0.45, 0.35, 0.45).rotateZ((PI / 2).toFloat()))
            c.add(mesh(SceneryGeo.cactus, green, side * 0.8, y + ah / 2, 0.0, 0.5, ah, 0.5))
        }
        return c
    }

    fun mesa(): Node {
        val m = Node()
        val w = rand(6.0, 11.0)
        val d = rand(6.0, 10.0)
        var y = 0.0
        val layers = 2 + floor(random() * 3).toInt()
        for (i in 0 until layers) {
            val h = rand(2.5, 5.0)
            val k = 1 - i * 0.12
            m.add(mesh(SceneryGeo.box, pick(intArrayOf(0xd9774a, 0xe8915a, 0xc9653f, 0xf0b27a)), 0.0, y + h / 2, 0.0, w * k, h, d * k))
            y += h
        }
        return m
    }

    fun hill(colors: IntArray): Node {
        val s = rand(4.0, 7.0)
        return mesh(SceneryGeo.hill, pick(colors), 0.0, s * 0.25, 0.0, s * 1.4, s * 0.8, s)
    }

    fun snowman(): Node {
        val g = Node()
        g.add(
            mesh(SceneryGeo.hill, 0xffffff, 0.0, 0.7, 0.0, 0.8),
            mesh(SceneryGeo.hill, 0xffffff, 0.0, 1.75, 0.0, 0.55),
            mesh(SceneryGeo.hill, 0xffffff, 0.0, 2.5, 0.0, 0.38),
        )
        g.add(mesh(SceneryGeo.cone, 0xff8a1f, 0.0, 2.5, 0.45, 0.1, 0.4, 0.1).rotateX((PI / 2).toFloat()))
        for (x in doubleArrayOf(-0.13, 0.13)) g.add(mesh(SceneryGeo.flower, 0x2e2530, x, 2.62, 0.33, 0.25))
        g.add(mesh(SceneryGeo.cactus, 0xe8453c, 0.0, 2.08, 0.0, 1.25, 0.22, 1.25))
        return g
    }

    fun snowyPine(): Node {
        val g = Node()
        val s = rand(0.9, 1.4)
        g.add(mesh(SceneryGeo.trunk, 0x7a4a24, 0.0, 0.5, 0.0, 0.7, 0.5, 0.7))
        g.add(mesh(SceneryGeo.pine, pick(intArrayOf(0x2f6b4a, 0x3a7a55)), 0.0, 2.4 * s, 0.0, s))
        g.add(mesh(SceneryGeo.pine, 0xffffff, 0.0, 3.4 * s, 0.0, s * 0.62, s * 0.5, s * 0.62))
        return g
    }

    fun snowPeak(): Node {
        val g = Node()
        val w = rand(7.0, 12.0)
        val h = rand(9.0, 16.0)
        g.add(mesh(SceneryGeo.cone, pick(intArrayOf(0x9fb4c8, 0xa9bfd6, 0x8fa6bd)), 0.0, h / 2, 0.0, w, h, w))
        g.add(mesh(SceneryGeo.cone, 0xffffff, 0.0, h * 0.8, 0.0, w * 0.42, h * 0.4, w * 0.42))
        return g
    }

    fun palm(): Node {
        val g = Node()
        val lean = rand(-0.25, 0.25)
        var x = 0.0
        for (i in 0 until 5) {
            val r = 0.8 - i * 0.07
            g.add(mesh(SceneryGeo.trunk, if (i % 2 != 0) 0xa8733f else 0x9a6835, x, 0.5 + i * 0.95, 0.0, r, 0.5, r))
            x += lean
        }
        val top = 4.9
        for (i in 0 until 6) {
            val a = (i / 6.0) * PI * 2
            val leaf = mesh(SceneryGeo.leaf, pick(intArrayOf(0x3fae3a, 0x55c244)), x + cos(a) * 1.3, top - 0.35, sin(a) * 1.3, 0.9, 0.12, 0.35)
            leaf.rotation.y = (-a).toFloat()
            leaf.rotation.z = -0.35f
            g.add(leaf)
        }
        for (i in 0 until 3) g.add(mesh(SceneryGeo.flower, 0x6b4423, x + rand(-0.3, 0.3), top - 0.5, rand(-0.3, 0.3), 1.1))
        return g
    }

    fun lighthouse(): Node {
        val g = Node()
        for (i in 0 until 6) {
            val r = 3.2 - i * 0.2
            g.add(mesh(SceneryGeo.cactus, if (i % 2 != 0) 0xffffff else 0xe8453c, 0.0, 1.0 + i * 2, 0.0, r, 2.0, r))
        }
        g.add(mesh(SceneryGeo.box, 0xfff3a0, 0.0, 12.8, 0.0, 1.4, 1.4, 1.4), mesh(SceneryGeo.cone, 0xe8453c, 0.0, 14.3, 0.0, 1.4, 1.6, 1.4))
        return g
    }

    fun lollipop(): Node {
        val g = Node()
        val h = rand(2.4, 3.6)
        g.add(mesh(SceneryGeo.cactus, 0xffffff, 0.0, h / 2, 0.0, 0.3, h, 0.3))
        val disc = mesh(SceneryGeo.cactus, pick(intArrayOf(0xff6fa8, 0x7ee0ff, 0xb07eff, 0xffd84a)), 0.0, h + 1, 0.0, 3.4, 0.35, 3.4)
        disc.rotation.x = (PI / 2).toFloat()
        val inner = mesh(SceneryGeo.cactus, 0xffffff, 0.0, h + 1, 0.05, 1.8, 0.4, 1.8)
        inner.rotation.x = (PI / 2).toFloat()
        g.add(disc, inner)
        return g
    }

    fun candyCane(): Node {
        val g = Node()
        for (i in 0 until 6) g.add(mesh(SceneryGeo.cactus, if (i % 2 != 0) 0xffffff else 0xe8453c, 0.0, 0.25 + i * 0.5, 0.0, 0.45, 0.5, 0.45))
        g.add(mesh(SceneryGeo.hill, 0xe8453c, 0.3, 3.1, 0.0, 0.35))
        return g
    }

    fun gumdrops(): Node {
        val g = Node()
        val n = 2 + floor(random() * 3).toInt()
        for (i in 0 until n) {
            g.add(
                mesh(
                    SceneryGeo.cone, pick(intArrayOf(0xff7eb6, 0x7ee0ff, 0xfff07e, 0xb07eff, 0x8ff0a0)),
                    (i - n / 2.0) * 0.9, 0.4, rand(0.0, 0.6), rand(0.6, 0.9), 0.8, rand(0.6, 0.9),
                ),
            )
        }
        return g
    }

    fun iceCreamHill(): Node {
        val s = rand(4.0, 7.0)
        val g = Node()
        g.add(mesh(SceneryGeo.hill, pick(intArrayOf(0xffc2dc, 0xc8f5dc, 0xfff1c8, 0xd9c8ff)), 0.0, s * 0.25, 0.0, s * 1.4, s * 0.8, s))
        g.add(mesh(SceneryGeo.hill, 0xe8453c, 0.0, s * 0.95, 0.0, 0.8))
        return g
    }

    fun mushroom(big: Boolean): Node {
        val g = Node()
        val s = if (big) rand(1.4, 2.2) else rand(0.35, 0.6)
        val cap = pick(intArrayOf(0x7b6cff, 0x3fb7ff, 0xff9f43, 0x2fc6a8))
        g.add(mesh(SceneryGeo.cactus, 0xfff3de, 0.0, 1.2 * s, 0.0, 0.9 * s, 2.4 * s, 0.9 * s))
        g.add(mesh(SceneryGeo.hill, cap, 0.0, 2.5 * s, 0.0, 1.5 * s, 0.75 * s, 1.5 * s))
        if (big) for (i in 0 until 4) {
            val a = (i / 4.0) * PI * 2 + 0.4
            g.add(mesh(SceneryGeo.flower, 0xfff6e8, cos(a) * 1.05 * s, 2.95 * s, sin(a) * 1.05 * s, 1.4 * s))
        }
        return g
    }
}
