package de.robinrehbein.birdy.view.world

import de.robinrehbein.birdy.engine.math.Mat4
import de.robinrehbein.birdy.engine.math.Quat
import de.robinrehbein.birdy.engine.math.Vec3
import de.robinrehbein.birdy.engine.scene.Geometry
import de.robinrehbein.birdy.engine.scene.InstanceData
import de.robinrehbein.birdy.engine.scene.Mesh
import de.robinrehbein.birdy.engine.scene.Node
import de.robinrehbein.birdy.engine.scene.Scene
import de.robinrehbein.birdy.engine.scene.ShaderPatch
import de.robinrehbein.birdy.engine.scene.StandardMaterial
import de.robinrehbein.birdy.game.GateRow
import de.robinrehbein.birdy.game.LaneState
import de.robinrehbein.birdy.game.WorldConst
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** One pipe segment's placement: body from [from] to [from] + [height], lip centre at [lipAt]. */
internal class Segment {
    var visible = false
    var from = 0.0
    var height = 0.01
    var lipAt: Double? = null

    /** world.js `setSegment(seg, from, to, lipAt)`. */
    fun set(from: Double, to: Double, lipAt: Double?) {
        visible = true
        this.from = from
        height = max(0.01, to - from)
        this.lipAt = lipAt
    }
}

/** world.js `setGap` / blocked-lane layout of a lane's bottom and top segments. */
internal fun layoutLane(lane: LaneState, bottom: Segment, top: Segment) =
    layoutColumn(lane.blocked, lane.gapLow, lane.gapHigh, bottom, top)

internal fun layoutColumn(blocked: Boolean, gapLow: Double, gapHigh: Double, bottom: Segment, top: Segment) {
    if (blocked) {
        bottom.set(0.0, WorldConst.PIPE_TOP, null)
        top.visible = false
        return
    }
    bottom.set(0.0, gapLow, gapLow - 0.4)
    top.set(gapHigh, WorldConst.PIPE_TOP, gapHigh + 0.4)
}

/** Wandering gap slide 0..1 for progress [t]: a small flinch backwards first (the telegraph). */
internal fun wanderEase(t: Double): Double {
    val c = t.coerceIn(0.0, 1.0)
    val flinch = if (c < WANDER_FLINCH) -0.08 * sin(PI * c / WANDER_FLINCH) else 0.0
    return c * c * (3 - 2 * c) + flinch
}

private const val WANDER_FLINCH = 0.3
/** The two columns swap places; the open one passes in front (towards the bird). */
internal const val WANDER_DODGE = 1.3

/**
 * Where lane slot [k] of [row] is drawn while a gap wanders: the `wanderFrom` slot carries the
 * open column, the `wanderTo` slot the full pipe, and they trade places. Writes x and z into
 * [out]; returns false for lanes that are drawn normally.
 */
internal fun wanderColumn(row: GateRow, k: Int, out: DoubleArray): Boolean {
    if (row.wanderTo < 0 || row.wanderT >= 1.0 || (k != row.wanderFrom && k != row.wanderTo)) return false
    val e = wanderEase(row.wanderT)
    val open = k == row.wanderFrom
    val a = WorldConst.LANES[row.wanderFrom]
    val b = WorldConst.LANES[row.wanderTo]
    out[0] = if (open) a + (b - a) * e else b + (a - b) * e
    out[1] = row.z + (if (open) 1 else -1) * sin(PI * min(1.0, max(0.0, e))) * WANDER_DODGE
    return true
}

/**
 * The pipe rows of world.js `createGate` rendered from the simulation's [GateRow]s. Fully opaque
 * rows share four instanced meshes (bottom bodies/lips cast shadows, the upper ones don't), so
 * all pipes cost 4 draw calls. A row fading out after being passed switches to its own
 * transparent meshes (world.js gives each gate its own material for this). Cloud banks and
 * cacti are per row; they don't fade, as in JS.
 */
internal class GateLayer(private val scene: Scene, private val pipes: PipeKit, haze: ShaderPatch, poolSize: Int) {
    private val capacity = poolSize * 3
    private val pipeMat = pipes.newMaterial().apply { patch = haze }
    private val bankMat = CloudMaterials.bank()

    private fun instanced(geo: Geometry, name: String, casts: Boolean) = Mesh(geo, pipeMat, name).apply {
        instances = InstanceData(capacity).apply { count = 0 }
        castShadow = casts
        receiveShadow = true
        scene.add(this)
    }

    val bottomBodies = instanced(pipes.body, "pipes-bottom", true)
    val bottomLips = instanced(pipes.capBelow, "lips-bottom", true)
    val topBodies = instanced(pipes.body, "pipes-top", false)
    val topLips = instanced(pipes.capAbove, "lips-top", false)

    val rows = List(poolSize) { RowNodes(scene, pipes, haze, bankMat) }

    /** Bank material colour (aliased to the sky clouds' colour in JS). */
    val bankColor get() = bankMat.color

    private val m = Mat4()
    private val q = Quat()
    private val p = Vec3()
    private val s = Vec3()
    private val bottom = Segment()
    private val top = Segment()
    private val xz = DoubleArray(2)

    fun sync(gates: List<GateRow>) {
        var nb = 0; var nbl = 0; var nt = 0; var ntl = 0
        for ((i, row) in gates.withIndex()) {
            val nodes = rows.getOrNull(i) ?: break
            nodes.group.visible = row.visible
            if (!row.visible) continue
            nodes.sync(row)
            val fading = row.opacity < 0.999
            nodes.setFading(fading, row.opacity.toFloat())
            val z = row.z
            for ((k, lane) in row.lanes.withIndex()) {
                var x = lane.x
                var lz = z
                if (wanderColumn(row, k, xz)) {
                    x = xz[0]
                    lz = xz[1]
                    val gap = row.lanes[row.wanderTo]
                    layoutColumn(k != row.wanderFrom, gap.gapLow, gap.gapHigh, bottom, top)
                } else {
                    layoutLane(lane, bottom, top)
                }
                if (fading) {
                    nodes.lanes[k].place(bottom, top, x, lz - z)
                    continue
                }
                setBody(bottomBodies, nb++, x, bottom, lz)
                bottom.lipAt?.let { setLip(bottomLips, nbl++, x, it, lz) }
                if (top.visible) {
                    setBody(topBodies, nt++, x, top, lz)
                    top.lipAt?.let { setLip(topLips, ntl++, x, it, lz) }
                }
            }
        }
        finish(bottomBodies, nb); finish(bottomLips, nbl); finish(topBodies, nt); finish(topLips, ntl)
    }

    private fun setBody(mesh: Mesh, i: Int, x: Double, seg: Segment, z: Double) {
        p.set(x.toFloat(), seg.from.toFloat(), z.toFloat())
        s.set(1f, seg.height.toFloat(), 1f)
        mesh.instances!!.setMatrix(i, m.compose(p, q.identity(), s))
    }

    private fun setLip(mesh: Mesh, i: Int, x: Double, y: Double, z: Double) {
        p.set(x.toFloat(), y.toFloat(), z.toFloat())
        s.set(1f, 1f, 1f)
        mesh.instances!!.setMatrix(i, m.compose(p, q.identity(), s))
    }

    private fun finish(mesh: Mesh, n: Int) {
        val d = mesh.instances!!
        d.count = n
        d.markDirty()
        mesh.visible = n > 0
    }
}

/** Per-row nodes: the cloud bank, three cacti and the transparent pipes used while fading. */
internal class RowNodes(scene: Scene, pipes: PipeKit, haze: ShaderPatch, bankMat: StandardMaterial) {
    val group = Node("gate").apply { visible = false }
    val bank = Mesh(RowCloudGeometry.all[0], bankMat, "bank")
    val fadeMat: StandardMaterial = pipes.newMaterial().apply { patch = haze }
    private val fade = Node("fade").apply { visible = false }
    val lanes = WorldConst.LANES.map { x -> LaneNodes(x, pipes, fadeMat, fade) }
    val plants = WorldConst.LANES.map { x -> PlantNodes(x) }

    init {
        group.add(bank, fade)
        for (pl in plants) group.add(pl.group)
        scene.add(group)
    }

    fun sync(row: GateRow) {
        group.position.z = row.z.toFloat()
        bank.visible = row.cloud >= 0
        if (row.cloud >= 0) bank.geometry = RowCloudGeometry.all[row.cloud]
        for ((k, lane) in row.lanes.withIndex()) plants[k].sync(lane)
    }

    fun setFading(fading: Boolean, opacity: Float) {
        fade.visible = fading
        fadeMat.transparent = fading
        fadeMat.depthWrite = !fading
        fadeMat.opacity = if (fading) opacity else 1f
    }
}

/** A lane's own bottom/top pipe meshes (only drawn while the row fades). */
internal class LaneNodes(x: Double, pipes: PipeKit, private val mat: StandardMaterial, parent: Node) {
    private fun seg(geo: Geometry, casts: Boolean) = Mesh(geo, mat).apply {
        castShadow = casts
        receiveShadow = true
    }

    private val bottomG = Node().apply { position.x = x.toFloat() }
    private val bottomBody = seg(pipes.body, true)
    private val bottomLip = seg(pipes.capBelow, true)
    private val topG = Node().apply { position.x = x.toFloat() }
    private val topBody = seg(pipes.body, false)
    private val topLip = seg(pipes.capAbove, false)

    init {
        bottomG.add(bottomBody, bottomLip)
        topG.add(topBody, topLip)
        parent.add(bottomG, topG)
    }

    /** [x] and [dz] (relative to the row) only differ from the lane's own while a gap wanders. */
    fun place(bottom: Segment, top: Segment, x: Double, dz: Double) {
        for (g in arrayOf(bottomG, topG)) {
            g.position.x = x.toFloat()
            g.position.z = dz.toFloat()
        }
        apply(bottom, bottomG, bottomBody, bottomLip)
        topG.visible = top.visible
        if (top.visible) apply(top, topG, topBody, topLip)
    }

    private fun apply(seg: Segment, g: Node, body: Mesh, lip: Mesh) {
        g.position.y = seg.from.toFloat()
        body.scale.y = seg.height.toFloat()
        val at = seg.lipAt
        lip.visible = at != null
        if (at != null) lip.position.y = (at - seg.from).toFloat()
    }
}

/** world.js `createPlant()`: a cactus in its own group, posed from [LaneState]'s plant fields. */
internal class PlantNodes(x: Double) {
    private val mat = CactusMaterial()
    val body = Mesh(CactusGeometry.geometry, mat.material, "cactus").apply { castShadow = false }
    val group = Node("plant").apply {
        position.x = x.toFloat()
        visible = false
        add(body)
    }

    val bristle get() = mat.bristle.value

    fun sync(lane: LaneState) {
        group.visible = !lane.blocked && lane.hasPlant && lane.plantVisible
        if (!group.visible) return
        group.position.y = lane.plantY.toFloat()
        group.rotation.z = lane.plantWiggle.toFloat()
        val puff = lane.plantPuff.toFloat()
        val w = CactusLook.PLANT_WIDTH * (1 + 0.08f * puff)
        body.scale.set(w, 1 - 0.03f * puff, w)
        mat.bristle.value = puff
    }
}
