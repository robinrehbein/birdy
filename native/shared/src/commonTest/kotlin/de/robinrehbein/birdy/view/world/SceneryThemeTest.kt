package de.robinrehbein.birdy.view.world

import de.robinrehbein.birdy.engine.scene.Mesh
import de.robinrehbein.birdy.engine.scene.Node
import de.robinrehbein.birdy.engine.scene.Scene
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlin.math.PI
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The scenery theme tables and chunk layout against src/world.js. */
class SceneryThemeTest {
    private val worldJs: String by lazy {
        SystemFileSystem.source(Path("../../src/world.js")).buffered().use { it.readString() }
    }

    private val hexRe = Regex("0x[0-9a-fA-F]{6}")
    private fun hexes(s: String) = hexRe.findAll(s).map { it.value.substring(2).toInt(16) }.toSet()

    /** Source text of one THEMES slot: `slot: () => ...` up to the next slot or theme. */
    private fun slotSource(theme: String, slot: String): String {
        val themes = worldJs.substring(worldJs.indexOf("const THEMES = {"))
        val t = themes.substring(themes.indexOf("\n  $theme: {"))
        val start = t.indexOf("$slot: () =>")
        val end = listOf("near: () =>", "mid: () =>", "far: () =>", "\n  },")
            .map { t.indexOf(it, start + 1) }.filter { it > 0 }.min()
        return t.substring(start, end)
    }

    /** Hex literals of all world.js helper functions a slot calls (incl. default parameters). */
    private fun helperHexes(slot: String): Set<Int> {
        val names = Regex("([a-zA-Z]+)\\(").findAll(slot).map { it.groupValues[1] }.toSet()
        val out = HashSet<Int>()
        for (n in names) {
            val i = worldJs.indexOf("\nfunction $n(")
            if (i < 0) continue
            out += hexes(worldJs.substring(i, worldJs.indexOf("\n}\n", i)))
        }
        return out
    }

    private fun colors(n: Node): Set<Int> {
        val out = HashSet<Int>()
        n.traverse { if (it is Mesh) out += it.material.color.hex() }
        return out
    }

    @Test
    fun themeTablesMatchWorldJs() {
        val items = SceneryItems(Random(7))
        assertEquals(setOf("winter", "beach", "candy", "mushroom", "park", "autumn", "canyon", "blossom"), SCENERY_THEMES.keys)
        for ((name, theme) in SCENERY_THEMES) {
            for ((slot, gen) in listOf("near" to theme.near, "mid" to theme.mid, "far" to theme.far)) {
                val src = slotSource(name, slot)
                val direct = hexes(src)
                val allowed = direct + helperHexes(src)
                val seen = HashSet<Int>()
                repeat(600) { seen += colors(gen(items)) }
                assertTrue(seen.containsAll(direct), "$name.$slot: JS colours ${direct.map(::h)} not all produced, got ${seen.map(::h)}")
                assertTrue(allowed.containsAll(seen), "$name.$slot: colours ${(seen - allowed).map(::h)} not in world.js")
            }
        }
    }

    @Test
    fun chunkLayout() {
        val items = SceneryItems(Random(11))
        repeat(20) {
            val root = items.buildChunkGroup("canyon")
            val bySide = root.children.groupBy { it.position.x > 0 }
            for ((_, list) in bySide) {
                val near = list.filter { abs(it.position.x) in 6.8f..7.6f }
                val mid = list.filter { abs(it.position.x) in 9f..13f }
                val far = list.filter { abs(it.position.x) in 22f..34f }
                assertEquals(list.size, near.size + mid.size + far.size)
                assertEquals(5, near.size)
                assertTrue(mid.size in 3..4, "mid ${mid.size}")
                assertTrue(far.size in 2..3, "far ${far.size}")
                for (n in near) assertTrue(n.position.z <= 0f && n.position.z > -22f)
                for (m in mid) assertTrue(m.position.z <= 0f && m.position.z > -28f)
                for (f in far) {
                    assertTrue(f.position.z <= 0f && f.position.z > -25f)
                    assertEquals(0f, f.rotation.y, "buildings and mesas stay square")
                }
                for (n in near + mid) assertTrue(n.rotation.y >= 0f && n.rotation.y < (2 * PI).toFloat())
            }
        }
    }

    @Test
    fun chunksRecycleAndStreamNewTheme() {
        val scenery = Scenery(Scene(), Random(3))
        val z0 = scenery.chunks.map { it.position.z }
        assertEquals(listOf(20f, -5f, -30f, -55f, -80f, -105f, -130f, -155f, -180f), z0)
        scenery.setTheme("autumn")
        assertTrue(scenery.chunks.all { scenery.themeOf(it) == "park" }, "no repaint on screen")
        scenery.update(31.0) // the first chunk passes z = 50 and wraps to the far end
        assertEquals(51f - 225f, scenery.chunks[0].position.z)
        assertEquals("autumn", scenery.themeOf(scenery.chunks[0]))
        assertEquals("park", scenery.themeOf(scenery.chunks[1]))
        scenery.setTheme("blossom", instant = true)
        assertTrue(scenery.chunks.all { scenery.themeOf(it) == "blossom" })
    }

    @Test
    fun cloudsDriftAndWrap() {
        val clouds = Clouds(Scene(), Random(5))
        assertEquals(18, clouds.clouds.size)
        for (c in clouds.clouds) {
            assertTrue(c.position.x in -45f..45f && c.position.y in 18f..32f && c.position.z in -220f..0f)
        }
        val before = clouds.clouds.map { it.position.z }
        clouds.update(1.0)
        for ((i, c) in clouds.clouds.withIndex()) {
            val dz = c.position.z - before[i]
            assertTrue(dz in 0.29f..0.61f, "parallax speed $dz")
        }
        repeat(300) { clouds.update(1.0) }
        for (c in clouds.clouds) assertTrue(c.position.z <= 30f && c.position.z > -221f)
    }

    private fun h(c: Int) = c.toString(16).padStart(6, '0')
}
