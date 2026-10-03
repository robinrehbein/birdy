package de.robinrehbein.birdy.game.sim

import de.robinrehbein.birdy.game.GapSpec
import de.robinrehbein.birdy.game.GateRow
import de.robinrehbein.birdy.game.LaneState
import de.robinrehbein.birdy.game.WorldConst
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

/**
 * The logic half of world.js `createGate` (world.md §7.4): configure a pooled row from a spec,
 * animate moving/breathing gaps and the beat-driven cactus, and keep the live hitbox
 * (`hitLow/hitHigh`) that collisions test every frame.
 */
object GateRows {
    fun configure(row: GateRow, z: Double, spec: List<GapSpec?>, cloud: Int) {
        row.z = z
        row.cloud = cloud
        row.visible = true
        row.passed = false
        row.record = false
        setOpacity(row, 1.0)
        spec.forEachIndexed { i, gap ->
            val lane = row.lanes[i]
            lane.plantVisible = false
            if (gap == null) {
                lane.blocked = true
                lane.hasPlant = false
                lane.gapLow = 0.0
                lane.gapHigh = 0.0
                lane.hitLow = 0.0
                lane.hitHigh = 0.0
                return@forEachIndexed
            }
            lane.blocked = false
            lane.center = gap.center
            lane.size = gap.size
            lane.amp = gap.amp
            lane.speed = gap.speed
            lane.phase = gap.phase
            lane.hasPlant = gap.plant
            lane.pulse = gap.pulse
            lane.plantOffset = gap.plantOffset
            setGap(lane, gap.center, lane.size)
            lane.hitLow = lane.gapLow
            lane.hitHigh = lane.gapHigh
        }
    }

    private fun setGap(lane: LaneState, center: Double, size: Double) {
        lane.gapLow = center - size / 2
        lane.gapHigh = center + size / 2
    }

    /** `gate.update(time, beat, dt)`: moving gaps and cacti; beat drives the plants. */
    fun update(row: GateRow, time: Double, beat: Double) {
        for (lane in row.lanes) {
            if (lane.blocked) continue
            val center = if (lane.amp != 0.0) lane.center + sin(time * lane.speed + lane.phase) * lane.amp else lane.center
            if (lane.pulse) {
                setGap(lane, center, lane.size * PlantTiming.pulseScale(beat + lane.plantOffset))
            } else if (lane.amp != 0.0) {
                setGap(lane, center, lane.size)
            }
            lane.hitLow = lane.gapLow
            lane.hitHigh = lane.gapHigh
            if (!lane.hasPlant) continue
            if (row.passed) {
                lane.plantVisible = false
                continue
            }
            val rise = PlantTiming.plantRise(beat + lane.plantOffset)
            if (rise <= 0 && PlantTiming.plantPeek(beat + lane.plantOffset)) {
                // Only the flower and the top of the head show above the lip; no hitbox.
                lane.plantVisible = true
                lane.plantY = lane.gapLow + 0.12 - (WorldConst.CACTUS_Y + WorldConst.CACTUS_R_Y)
                lane.plantWiggle = sin(time * 22) * 0.1
                lane.plantPuff = 0.0
                continue
            }
            lane.plantWiggle = 0.0
            lane.plantVisible = rise > 0
            if (rise <= 0) continue
            // Hidden inside the pipe at rise 0; head sticks out of the gap at 1.
            lane.plantY = lane.gapLow + WorldConst.PLANT_REACH + 0.15 - WorldConst.PLANT_HEIGHT * (2 - rise)
            lane.hitLow = max(lane.gapLow, lane.plantY + WorldConst.PLANT_HEIGHT - 0.15)
            // Fully up: it puffs itself up and down; the top of the hitbox stays put.
            lane.plantPuff = if (rise > 0.9) 0.5 + 0.5 * sin(time * 16) else 0.0
        }
    }

    /** world.js `setOpacity`: ignores changes below 0.001 (so a fade can stop just above 0). */
    fun setOpacity(row: GateRow, o: Double) {
        if (abs(o - row.opacity) < 0.001) return
        row.opacity = o
    }
}
