package de.robinrehbein.birdy.view.fx

import de.robinrehbein.birdy.engine.math.MathUtil
import de.robinrehbein.birdy.engine.math.Quat
import de.robinrehbein.birdy.engine.mesh.Primitives
import de.robinrehbein.birdy.engine.scene.BasicMaterial
import de.robinrehbein.birdy.engine.scene.Mesh
import de.robinrehbein.birdy.game.GateRow
import de.robinrehbein.birdy.game.Tuning
import de.robinrehbein.birdy.game.WorldConst
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

/**
 * Blob shadow straight under the bird (main.js:98-106): shows its lane and height exactly, since
 * the bird casts no sun shadow.
 */
class BlobShadow {
    val mesh = Mesh(
        Primitives.circle(0.7, 20),
        BasicMaterial().apply { color.setHex(0x000000); transparent = true; opacity = 0.22f; depthWrite = false },
        "blob",
    ).apply {
        rotation.x = (-PI / 2).toFloat()
        position.y = 0.03f
    }

    /** [birdScaleX] = the bird group's current x scale (includes BIRD_SCALE). */
    fun update(x: Double, y: Double, birdScaleX: Double) {
        if (!mesh.visible) return
        mesh.position.x = x.toFloat()
        mesh.scale.setScalar(scaleFor(y, birdScaleX).toFloat())
    }

    companion object {
        fun scaleFor(y: Double, birdScaleX: Double): Double =
            MathUtil.clamp(1.1 - y * 0.04, 0.5, 1.0) * (birdScaleX / Tuning.BIRD_SCALE)
    }
}

/**
 * Height marker (main.js `updateMarker`): a ring at the next row, in the bird's lane and at the
 * bird's height; green = would pass right now, red = would hit. Always on top and billboarded.
 */
class HeightMarker {
    val mesh = Mesh(
        Primitives.ring(0.2, 0.36, 20),
        BasicMaterial().apply {
            color.setHex(OK); transparent = true; opacity = 0f; depthTest = false; depthWrite = false
        },
        "marker",
    ).apply {
        renderOrder = 10
        visible = false
        quaternionOverride = Quat()
    }

    fun hide() {
        mesh.visible = false
    }

    /**
     * [next] = nearest unpassed row, [r] = bird radius, [tutorial] = `tut.active` (pulses bigger).
     */
    fun update(
        next: GateRow?, playing: Boolean, star: Boolean, lane: Int, birdY: Double, r: Double,
        tutorial: Boolean, time: Double, cameraQuat: Quat,
    ) {
        val z = next?.z ?: -999.0
        mesh.visible = next != null && playing && !star && z > -48
        if (!mesh.visible || next == null) return
        val l = next.lanes[lane]
        val ok = passes(l.blocked, birdY, r, l.hitLow, l.hitHigh)
        mesh.material.color.setHex(if (ok) OK else BAD)
        mesh.material.opacity = opacityFor(z).toFloat()
        mesh.position.set(WorldConst.LANES[lane].toFloat(), birdY.toFloat(), (z + WorldConst.PIPE_RADIUS + 0.3).toFloat())
        mesh.scale.setScalar(scaleFor(z, tutorial, time).toFloat())
        mesh.quaternionOverride!!.set(cameraQuat)
    }

    companion object {
        const val OK = 0x8cff5a
        const val BAD = 0xff4a3d

        /** The preview shrinks the radius by 0.8 like the real collision test. */
        fun passes(blocked: Boolean, y: Double, r: Double, hitLow: Double, hitHigh: Double): Boolean =
            !blocked && y - r * 0.8 > hitLow && y + r * 0.8 < hitHigh

        fun opacityFor(z: Double): Double = 0.9 * MathUtil.clamp((z + 48) / 16, 0.0, 1.0)

        fun scaleFor(z: Double, tutorial: Boolean, time: Double): Double {
            val pulse = if (tutorial) 1.6 + 0.25 * sin(time * 8) else 1.0
            return max(1.0, -z / 14) * pulse
        }
    }
}
