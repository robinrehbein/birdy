package de.robinrehbein.birdy.view.bird

import de.robinrehbein.birdy.engine.scene.Material
import de.robinrehbein.birdy.engine.scene.ShaderPatch
import de.robinrehbein.birdy.engine.scene.Uniform

/**
 * Animated premium skins (skinfx.js): a procedural pattern painted over the bird's own materials
 * in the shader, in object space. One instance per bird rig (JS keeps module-level state because it
 * only ever has one live bird plus the thumbnail bird; per-rig state is equivalent).
 *
 * Like JS, the patch is only attached while an effect is active, so plain skins keep the plain
 * (cheaper) program; `setSkinFx` swapping the patch mirrors `customProgramCacheKey`.
 */
class SkinFx {
    private val time = Uniform.F(0f)
    private val fx = Uniform.F(0f)
    private val hooked = ArrayList<Pair<Material, ShaderPatch>>()

    /** Current effect number (`FX[name] || 0`). */
    val active: Int get() = fx.value.toInt()

    /** `addSkinFx(material, part)`: part 0 body, 1 belly, 2 wing, 3 cover, 4 tail. */
    fun add(material: Material, part: Int) {
        val patch = ShaderPatch(
            key = SkinFxShader.KEY,
            vertexHead = SkinFxShader.VERTEX_HEAD,
            vertexBody = SkinFxShader.VERTEX_BODY,
            fragmentHead = SkinFxShader.FRAGMENT_HEAD,
            fragmentColor = SkinFxShader.FRAGMENT_COLOR,
            uniforms = linkedMapOf("uFxTime" to time, "uFx" to fx, "uFxPart" to Uniform.F(part.toFloat())),
        )
        hooked += material to patch
        material.patch = if (active > 0) patch else null
    }

    /** `setSkinFx(name)`: unknown or null names switch the effect off. */
    fun set(name: String?) {
        val id = fxId(name)
        fx.value = id.toFloat()
        for ((m, p) in hooked) m.patch = if (id > 0) p else null
    }

    /** `tickSkinFx(time)`: game time wrapped at 1000 s. */
    fun tick(seconds: Double) {
        time.value = (seconds % 1000.0).toFloat()
    }

    companion object {
        /** skinfx.js `FX` enum order. */
        val NAMES = listOf("none", "lava", "diamond", "water", "galaxy", "basketball", "football", "toadstool")

        fun fxId(name: String?): Int = NAMES.indexOf(name).coerceAtLeast(0)
    }
}
