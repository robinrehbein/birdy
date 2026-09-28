package de.robinrehbein.birdy.view.fx

import de.robinrehbein.birdy.engine.math.Color
import de.robinrehbein.birdy.engine.math.Quat
import de.robinrehbein.birdy.engine.mesh.Primitives
import de.robinrehbein.birdy.engine.scene.BasicMaterial
import de.robinrehbein.birdy.engine.scene.Mesh
import de.robinrehbein.birdy.engine.scene.Side
import de.robinrehbein.birdy.game.PowerType
import kotlin.math.sin

/**
 * Power-up aura: one billboard ring around the bird (effects.js `createAura`). A colour-cycling
 * rainbow ring for the star, outward-running waves for the magnet, a pulsing ring for mini.
 */
class Aura {
    val mesh = Mesh(
        Primitives.ring(0.82, 1.0, 40),
        BasicMaterial().apply {
            color.setHex(0xffffff); transparent = true; depthWrite = false; fog = false; side = Side.Double
        },
        "aura",
    ).apply {
        renderOrder = 9
        visible = false
        quaternionOverride = Quat()
    }

    /**
     * [kind] null hides the ring. [size] is the bird's base scale; ([x], [y], [z]) the bird position.
     */
    fun update(kind: PowerType?, x: Double, y: Double, z: Double, time: Double, size: Double, cameraQuat: Quat) {
        mesh.visible = kind != null
        if (kind == null) return
        mesh.position.set(x.toFloat(), y.toFloat(), z.toFloat())
        mesh.quaternionOverride!!.set(cameraQuat)
        val look = look(kind, time, size, mesh.material.color)
        mesh.material.opacity = look.opacity.toFloat()
        mesh.scale.setScalar(look.scale.toFloat())
    }

    class Look(val opacity: Double, val scale: Double)

    companion object {
        /** Aura precedence (main.js): star, then magnet, then mini; only while playing. */
        fun kindFor(playing: Boolean, star: Double, magnet: Double, mini: Double): PowerType? = when {
            !playing -> null
            star > 0 -> PowerType.Star
            magnet > 0 -> PowerType.Magnet
            mini > 0 -> PowerType.Mini
            else -> null
        }

        /** Writes the ring colour into [color] and returns opacity and scale for [kind] at [time]. */
        fun look(kind: PowerType, time: Double, size: Double, color: Color): Look = when (kind) {
            PowerType.Star -> {
                color.setHSL((time * 1.5) % 1, 1.0, 0.6)
                Look(0.75, size * (1.25 + 0.08 * sin(time * 12)))
            }
            PowerType.Magnet -> {
                val k = (time * 1.6) % 1 // a wave every 0.625 s
                color.setHex(0xff4a4a)
                Look(0.7 * (1 - k), size * (1 + 2.2 * k))
            }
            PowerType.Mini -> {
                color.setHex(0xc58bff)
                Look(0.85, size * (1.5 + 0.15 * sin(time * 8)))
            }
        }
    }
}
