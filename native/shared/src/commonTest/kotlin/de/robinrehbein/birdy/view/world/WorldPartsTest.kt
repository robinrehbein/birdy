package de.robinrehbein.birdy.view.world

import de.robinrehbein.birdy.engine.math.Color
import de.robinrehbein.birdy.engine.math.Vec3
import de.robinrehbein.birdy.engine.mesh.SPIKE_ATTRIBUTE
import de.robinrehbein.birdy.engine.scene.Scene
import de.robinrehbein.birdy.engine.texture.RasterCanvas
import de.robinrehbein.birdy.game.Coin
import de.robinrehbein.birdy.game.GapSpec
import de.robinrehbein.birdy.game.GateRow
import de.robinrehbein.birdy.game.LaneState
import de.robinrehbein.birdy.game.sim.GateRows
import de.robinrehbein.birdy.meta.Catalog
import de.robinrehbein.birdy.meta.PipeColors
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorldPartsTest {
    private fun px(c: RasterCanvas, x: Int, y: Int) = c.getPixel(x, y)
    private fun rgba(hex: Int, a: Int = 0xff) = (hex shl 8) or a

    private fun assertPixel(expected: Int, actual: Int, tol: Int = 1, what: String) {
        for (s in intArrayOf(24, 16, 8, 0)) {
            val e = (expected ushr s) and 0xff
            val a = (actual ushr s) and 0xff
            assertTrue(abs(e - a) <= tol, "$what: expected ${expected.toUInt().toString(16)} got ${actual.toUInt().toString(16)}")
        }
    }

    /** drawGround pixels vs samples computed by hand from world.js:73-102. */
    @Test
    fun groundTexturePixels() {
        val c = RasterCanvas(512, 128)
        GroundTextures.drawGround(c, WorldLook.SAND_ROAD)
        // Beside the track: transparent (the grass plane shows through the alpha test).
        assertEquals(0, px(c, 100, 64))
        assertEquals(0, px(c, 400, 10))
        // Borders [140,150) and [362,372), dark trim [136,140) and [372,376).
        assertPixel(rgba(0x9ce659), px(c, 145, 64), 0, "left border")
        assertPixel(rgba(0x9ce659), px(c, 366, 64), 0, "right border")
        assertPixel(rgba(0x543847), px(c, 138, 64), 0, "left trim")
        assertPixel(rgba(0x543847), px(c, 374, 100), 0, "right trim")
        // Stripe centre lines y = i + (x - 150) * 128 / 212 for i = -128 + 32k, 14 px wide.
        // At x = 300.5 the centres sit at 26.87 (mod 32): row 26 is stripe, row 10 plain road.
        assertPixel(rgba(0xd2c26a), px(c, 300, 26), 0, "stripe")
        assertPixel(rgba(0xded895), px(c, 300, 10), 0, "road")
        // Lane divider (226.5 ± 2.5, y 8..56) between stripes at y = 30:
        // 0.75 * (255, 252, 235) + 0.25 * (0xde, 0xd8, 0x95) = (246.75, 243, 213.5).
        val divider = (247 shl 24) or (243 shl 16) or (213 shl 8) or 0xff
        assertPixel(divider, px(c, 226, 30), 1, "divider")
        assertPixel(divider, px(c, 285, 30), 1, "second divider")
        // Below the dashes the road continues (stripe centres at x = 226.5: 46.19 and 78.19).
        assertPixel(rgba(0xded895), px(c, 226, 60), 0, "below divider")
    }

    @Test
    fun grassTexture() {
        val g = GroundTextures.drawGrass()
        assertPixel(rgba(0xe0e6d8), g.getPixel(0, 0), 0, "grey band")
        assertPixel(rgba(0xffffff), g.getPixel(3, 20), 0, "white band")
        assertPixel(rgba(0xe0e6d8), g.getPixel(2, 111), 0, "last grey band")
        assertPixel(rgba(0xffffff), g.getPixel(1, 127), 0, "last white band")
    }

    @Test
    fun groundScrollAndRepaint() {
        val ground = Ground(Scene())
        assertEquals(40f, ground.trackTexture.repeatV)
        assertEquals(60f, ground.grassTexture.repeatV)
        ground.update(123.0)
        assertEquals(0.3f, ground.trackTexture.offsetV, 1e-5f)
        assertEquals(ground.trackTexture.offsetV, ground.grassTexture.offsetV)
        val v = ground.trackTexture.version
        ground.setRoad(WorldLook.SAND_ROAD)
        assertEquals(v, ground.trackTexture.version, "same palette: no repaint")
        ground.setRoad(listOf(0xeef4fb, 0xd6e4f2, 0xbfe3ff))
        assertTrue(ground.trackTexture.version > v)
        assertEquals(1, ground.repaints)
    }

    @Test
    fun laneLayout() {
        val bottom = Segment(); val top = Segment()
        val lane = LaneState(0.0).apply { gapLow = 3.0; gapHigh = 7.5 }
        layoutLane(lane, bottom, top)
        assertEquals(0.0, bottom.from); assertEquals(3.0, bottom.height); assertEquals(2.6, bottom.lipAt!!, 1e-12)
        assertTrue(top.visible)
        assertEquals(7.5, top.from); assertEquals(32.5, top.height); assertEquals(7.9, top.lipAt!!, 1e-12)
        lane.blocked = true
        layoutLane(lane, bottom, top)
        assertEquals(40.0, bottom.height); assertNull(bottom.lipAt); assertFalse(top.visible)
        // Never a zero-height body.
        lane.blocked = false; lane.gapLow = 0.0
        layoutLane(lane, bottom, top)
        assertEquals(0.01, bottom.height)
    }

    @Test
    fun wanderingGapColumnsSwapPlaces() {
        val row = GateRow()
        GateRows.configure(row, -40.0, listOf(GapSpec(5.0, 4.0), GapSpec(6.0, 4.0, wanderFrom = 2), null), -1)
        val xz = DoubleArray(2)
        assertFalse(wanderColumn(row, 0, xz), "other lanes draw normally")
        assertTrue(wanderColumn(row, 2, xz))
        assertEquals(3.0, xz[0], 1e-12, "open column starts in its origin lane")
        assertTrue(wanderColumn(row, 1, xz))
        assertEquals(0.0, xz[0], 1e-12, "pipe column starts in the destination lane")
        row.wanderT = 0.1
        assertTrue(wanderColumn(row, 2, xz))
        assertTrue(xz[0] > 3.0, "telegraph: a small flinch away first (${xz[0]})")
        row.wanderT = 0.5
        assertTrue(wanderColumn(row, 2, xz))
        assertEquals(1.5, xz[0], 1e-12)
        assertTrue(xz[1] > row.z, "the open column passes in front")
        assertTrue(wanderColumn(row, 1, xz))
        assertEquals(1.5, xz[0], 1e-12)
        assertTrue(xz[1] < row.z)
        assertEquals(1.0, wanderEase(1.0), 1e-12)
        row.wanderT = 1.0
        assertFalse(wanderColumn(row, 2, xz), "settled: plain lanes again")
    }

    @Test
    fun pipeStyleRecolorsSharedGeometry() {
        val kit = PipeKit()
        val mat = kit.newMaterial()
        val first = Color.hex(0x73bf2e)
        assertEquals(first.linearR, kit.body.colors!![0], 1e-6f)
        val v = kit.body.version
        kit.setStyle(PipeColors(0x3f9be0, 0x9fd6ff, 0x2a6fb0, metal = true))
        assertTrue(kit.body.version > v)
        assertEquals(Color.hex(0x3f9be0).linearB, kit.body.colors!![2], 1e-6f)
        assertEquals(Color.hex(0x3f9be0).linearG, kit.capAbove.colors!![1], 1e-6f)
        // The dark band is the last part of the caps.
        val c = kit.capBelow.colors!!
        assertEquals(Color.hex(0x2a6fb0).linearR, c[c.size - 3], 1e-6f)
        assertEquals(0.5f, mat.metalness); assertEquals(0.3f, mat.roughness)
        kit.setStyle(Catalog.pipes[0].colors)
        assertEquals(0f, mat.metalness); assertEquals(0.45f, mat.roughness)
    }

    @Test
    fun pipeForAndZoneBiome() {
        val park = Catalog.worlds[0]
        val winter = Catalog.worlds.first { it.id == "winter" }
        val classic = Catalog.pipes[0]
        val blue = Catalog.pipes.first { it.id == "blue" }
        assertEquals(classic.colors, WorldPicks.pipeFor(park, classic))
        assertEquals(winter.pipes, WorldPicks.pipeFor(winter, classic))
        assertEquals(blue.colors, WorldPicks.pipeFor(winter, blue))
        assertEquals(winter, WorldPicks.zoneBiome(0, winter).source)
        assertEquals(winter, WorldPicks.zoneBiome(8, winter).source)
        assertEquals(Catalog.zoneBiomes[1], WorldPicks.zoneBiome(1, winter).source)
        assertEquals(Catalog.zoneBiomes[3], WorldPicks.zoneBiome(7, winter).source)
        assertTrue(WorldPicks.zoneBiome(5, winter) === WorldPicks.zoneBiome(1, park), "stable identity")
    }

    @Test
    fun cactusGeometryHasBristleTips() {
        val geo = CactusGeometry.geometry
        val spike = geo.extraAttributes.getValue(SPIKE_ATTRIBUTE)
        var tips = 0
        for (i in 0 until geo.vertexCount) {
            val d = Vec3(spike.data[i * 3], spike.data[i * 3 + 1], spike.data[i * 3 + 2])
            if (d.lengthSq() > 0f) {
                tips++
                assertEquals(1f, d.length(), 1e-4f)
            }
        }
        var spikes = 0
        for (i in 0 until 46) {
            val phi = acos(1 - (2 * (i + 0.5)) / 46)
            val d = Vec3().setFromSphericalCoords(1.0, phi, i * 2.399)
            val face = d.z > 0.3f && d.y > -0.6f && abs(d.x) < 0.8f
            if (!(face || d.y > 0.8f || d.y < -0.6f)) spikes++
        }
        assertTrue(spikes in 20..40, "spikes $spikes")
        // ConeGeometry(radial 4): three.js skips the degenerate apex triangle of each side
        // segment, leaving one apex vertex per segment after toNonIndexed.
        assertEquals(spikes * 4, tips)
        assertTrue(geo.colors != null && geo.normals != null)
    }

    @Test
    fun gateLayerInstancesOpaqueRows() {
        val scene = Scene()
        val world = WorldView(kotlin.random.Random(2)).also { it.attach(scene) }
        val rows = List(12) { GateRow() }
        GateRows.configure(rows[0], -10.0, listOf(GapSpec(5.0, 5.0), null, GapSpec(5.0, 5.0, plant = true)), cloud = 2)
        GateRows.configure(rows[1], -30.0, listOf(GapSpec(5.0, 5.0), GapSpec(5.0, 5.0), GapSpec(5.0, 5.0)), cloud = -1)
        GateRows.update(rows[0], 1.4516, 3.0)
        world.sync(1.0 / 30, 0.0, rows, emptyList())
        val g = world.gates
        assertEquals(6, g.bottomBodies.instances!!.count)
        assertEquals(5, g.bottomLips.instances!!.count)
        assertEquals(5, g.topBodies.instances!!.count)
        assertEquals(5, g.topLips.instances!!.count)
        assertTrue(g.rows[0].bank.visible && g.rows[0].group.visible)
        assertFalse(g.rows[1].bank.visible)
        assertFalse(g.rows[2].group.visible)
        assertTrue(g.rows[0].plants[2].group.visible, "risen cactus")
        assertEquals(rows[0].lanes[2].plantY.toFloat(), g.rows[0].plants[2].group.position.y)
        // A fading row leaves the instances and uses its own transparent meshes.
        rows[1].passed = true
        GateRows.setOpacity(rows[1], 0.5)
        world.sync(1.0 / 30, 0.0, rows, emptyList())
        assertEquals(3, g.bottomBodies.instances!!.count)
        assertTrue(g.rows[1].fadeMat.transparent && !g.rows[1].fadeMat.depthWrite)
        assertEquals(0.5f, g.rows[1].fadeMat.opacity)
    }

    @Test
    fun coinInstances() {
        val world = WorldView(kotlin.random.Random(2)).also { it.attach(Scene()) }
        val coins = List(60) { Coin() }
        world.sync(0.0, 0.0, emptyList(), coins)
        assertFalse(world.coins.mesh.visible)
        coins[3].apply { visible = true; x = 3.0; y = 4.0; z = -12.0; spin = 1.0; scale = 0.5 }
        coins[9].apply { visible = true; x = 0.0; y = 5.0; z = -20.0 }
        world.sync(0.0, 0.0, emptyList(), coins)
        val data = world.coins.mesh.instances!!
        assertEquals(2, data.count)
        assertTrue(world.coins.mesh.visible)
        val m = data.matrices
        assertEquals(3f, m[12]); assertEquals(4f, m[13]); assertEquals(-12f, m[14])
        // rotation.y = 1, uniform scale 0.5: first column = 0.5 * (cos 1, 0, -sin 1).
        assertEquals((0.5 * kotlin.math.cos(1.0)).toFloat(), m[0], 1e-6f)
        assertEquals((-0.5 * kotlin.math.sin(1.0)).toFloat(), m[2], 1e-6f)
        assertEquals(-20f, m[16 + 14])
    }

    @Test
    fun distanceDrivesScroll() {
        val world = WorldView(kotlin.random.Random(2)).also { it.attach(Scene()) }
        world.sync(0.0, 5.0, emptyList(), emptyList())
        val z = world.scenery.chunks[3].position.z
        world.sync(0.0, 7.5, emptyList(), emptyList())
        assertEquals(z + 2.5f, world.scenery.chunks[3].position.z, 1e-5f)
        assertEquals(0.75f, world.ground.trackTexture.offsetV, 1e-6f)
        // A new run resets the distance: nothing jumps backwards.
        world.sync(0.0, 0.0, emptyList(), emptyList())
        assertEquals(z + 2.5f, world.scenery.chunks[3].position.z, 1e-5f)
        assertEquals(0, (world.ground.trackTexture.offsetV * 100).roundToInt())
    }
}
