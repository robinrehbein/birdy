package de.robinrehbein.birdy.view.world

import de.robinrehbein.birdy.engine.mesh.bake
import de.robinrehbein.birdy.engine.scene.Geometry
import de.robinrehbein.birdy.engine.scene.Mesh
import de.robinrehbein.birdy.engine.scene.Node
import de.robinrehbein.birdy.engine.scene.Scene
import de.robinrehbein.birdy.engine.scene.StandardMaterial
import kotlin.math.PI
import kotlin.random.Random

/** One scenery theme: generators for the near, mid and far rings (world.js `THEMES`). */
internal class SceneryTheme(
    val near: SceneryItems.() -> Node,
    val mid: SceneryItems.() -> Node,
    val far: SceneryItems.() -> Node,
)

/** world.js `THEMES` (:412-455), 1:1. */
internal val SCENERY_THEMES: Map<String, SceneryTheme> = mapOf(
    "winter" to SceneryTheme(
        near = { if (random() < 0.18) snowman() else bush(intArrayOf(0xf4f8ff, 0xe3eefa, 0xd6e4f3)) },
        mid = { snowyPine() },
        far = { snowPeak() },
    ),
    "beach" to SceneryTheme(
        near = {
            if (random() < 0.5) bush(intArrayOf(0x9fc94a, 0x7fb83a))
            else mesh(SceneryGeo.rock, pick(intArrayOf(0xd9c49a, 0xc9b080)), 0.0, 0.3, 0.0, rand(0.5, 0.9), rand(0.4, 0.7), rand(0.5, 0.9))
        },
        mid = { palm() },
        far = { if (random() < 0.2) lighthouse() else hill(intArrayOf(0x5fb84a, 0x6fc45a, 0x4fa83f)) },
    ),
    "candy" to SceneryTheme(
        near = { if (random() < 0.3) candyCane() else gumdrops() },
        mid = { lollipop() },
        far = { iceCreamHill() },
    ),
    "mushroom" to SceneryTheme(
        near = { if (random() < 0.5) mushroom(false) else bush(intArrayOf(0x4fa83f, 0x5fb84a, 0x3f8f36)) },
        mid = { mushroom(true) },
        far = { hill(intArrayOf(0x3f8f6a, 0x4a9e76, 0x357f5c)) },
    ),
    "park" to SceneryTheme(
        near = { bush(intArrayOf(0x5cb338, 0x4a9e2c, 0x7ccf45)) },
        mid = { tree(intArrayOf(0x5cb338, 0x4a9e2c, 0x7ccf45)) },
        far = { building(intArrayOf(0xd7eef0, 0xc4e3e6, 0xe9f5f2), 0x9ad4dc) },
    ),
    "autumn" to SceneryTheme(
        near = { bush(intArrayOf(0xe8772e, 0xf2a93b, 0xd9492f, 0x9fb33a)) },
        mid = {
            if (random() < 0.25) {
                mesh(SceneryGeo.pine, pick(intArrayOf(0x3f7f3a, 0x4a8a3f)), 0.0, 2.6, 0.0)
                    .add(mesh(SceneryGeo.trunk, 0x7a4a24, 0.0, -1.9, 0.0, 0.8, 0.5, 0.8))
            } else {
                tree(intArrayOf(0xe8772e, 0xf2a93b, 0xd9492f, 0xf5c542), 0x7a4a24)
            }
        },
        far = { hill(intArrayOf(0xc9a23a, 0xb5892f, 0x9aa83a)) },
    ),
    "canyon" to SceneryTheme(
        near = {
            mesh(SceneryGeo.rock, pick(intArrayOf(0xc9774f, 0xb5653f, 0xd98c5f)), 0.0, 0.4, 0.0, rand(0.6, 1.2), rand(0.5, 0.9), rand(0.6, 1.2))
        },
        mid = {
            if (random() < 0.7) cactus()
            else mesh(SceneryGeo.rock, pick(intArrayOf(0xc9774f, 0xe0a070)), 0.0, 0.8, 0.0, rand(1.4, 2.2))
        },
        far = { mesa() },
    ),
    "blossom" to SceneryTheme(
        near = { bush(intArrayOf(0x6cc04a, 0x5cb338), intArrayOf(0xffffff, 0xffb7d5, 0xffe066)) },
        mid = { tree(intArrayOf(0xffb7d5, 0xffcfe3, 0xf78fb3, 0xffffff), 0x7a4f3a) },
        far = {
            building(intArrayOf(0xfff3e0, 0xffe6ea, 0xeaf6ff), 0xbfe3ea, pick(intArrayOf(0xe0584f, 0xd9534f, 0x6a8fd0)))
        },
    ),
)

/** world.js `buildChunkGroup(theme)`: items for one chunk in chunk-local z (0 .. -SCENERY_CHUNK). */
internal fun SceneryItems.buildChunkGroup(theme: String): Node {
    val t = SCENERY_THEMES[theme] ?: SCENERY_THEMES.getValue("park")
    val root = Node("chunk")
    fun place(item: Node, x: Double, z: Double, turn: Boolean) {
        item.position.x = x.toFloat()
        item.position.z = z.toFloat()
        // Plants and rocks at any angle; buildings and mesas stay square.
        item.rotation.y = if (turn) (random() * PI * 2).toFloat() else 0f
        root.add(item)
    }
    val chunk = WorldLook.SCENERY_CHUNK
    for (side in intArrayOf(-1, 1)) {
        var z = 0.0
        while (z < chunk) {
            place(t.near(this), side * rand(6.8, 7.6), -z - random() * 2, true)
            z += 5
        }
        z = rand(0.0, 3.0)
        while (z < chunk) {
            place(t.mid(this), side * rand(9.0, 13.0), -z - random() * 3, true)
            z += 7
        }
        z = rand(0.0, 6.0)
        while (z < chunk) {
            place(t.far(this), side * rand(22.0, 34.0), -z, false)
            z += 12
        }
    }
    return root
}

/**
 * world.js `createScenery(scene)`: 9 baked chunks (one draw call each) that leapfrog to the far end
 * once they are behind the camera, rebuilt in the current theme when they wrap, so a new theme
 * streams in from the horizon.
 */
class Scenery(scene: Scene, rng: Random) {
    private val items = SceneryItems(rng)
    val material = StandardMaterial().apply {
        vertexColors = true
        flatShading = true
    }
    val chunks: List<Mesh>
    private val chunkTheme = HashMap<Mesh, String>()

    var theme = "park"
        private set

    init {
        val count = (WorldLook.SCENERY_SPAN / WorldLook.SCENERY_CHUNK).toInt()
        chunks = List(count) { k ->
            Mesh(EMPTY, material, "scenery$k").apply {
                position.z = (20 - k * WorldLook.SCENERY_CHUNK).toFloat()
                castShadow = true
                receiveShadow = true
            }
        }
        for (c in chunks) {
            rebuild(c)
            scene.add(c)
        }
    }

    private fun rebuild(c: Mesh) {
        c.geometry = bake(items.buildChunkGroup(theme))
        chunkTheme[c] = theme
    }

    fun themeOf(chunk: Mesh): String = chunkTheme.getValue(chunk)

    fun setTheme(name: String, instant: Boolean = false) {
        theme = name
        if (instant) for (c in chunks) if (chunkTheme[c] != theme) rebuild(c)
    }

    fun update(dz: Double) {
        for (c in chunks) {
            c.position.z += dz.toFloat()
            // Fully behind the camera: move it to the far end (in the current theme).
            if (c.position.z - WorldLook.SCENERY_CHUNK > 25) {
                c.position.z -= WorldLook.SCENERY_SPAN.toFloat()
                if (chunkTheme[c] != theme) rebuild(c)
            }
        }
    }

    private companion object {
        val EMPTY = Geometry(FloatArray(0))
    }
}
