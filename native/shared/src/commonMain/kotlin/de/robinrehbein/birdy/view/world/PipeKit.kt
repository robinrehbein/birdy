package de.robinrehbein.birdy.view.world

import de.robinrehbein.birdy.engine.mesh.Primitives
import de.robinrehbein.birdy.engine.mesh.bake
import de.robinrehbein.birdy.engine.mesh.translate
import de.robinrehbein.birdy.engine.scene.BasicMaterial
import de.robinrehbein.birdy.engine.scene.Geometry
import de.robinrehbein.birdy.engine.scene.Mesh
import de.robinrehbein.birdy.engine.scene.Node
import de.robinrehbein.birdy.engine.scene.ShaderPatch
import de.robinrehbein.birdy.engine.scene.StandardMaterial
import de.robinrehbein.birdy.engine.scene.Uniform
import de.robinrehbein.birdy.game.WorldConst
import de.robinrehbein.birdy.meta.PipeColors

/**
 * The shared, baked pipe geometries (world.js:560-634) and their colour style. Every gate and the
 * shop preview use these geometries, so [setStyle] restyles all pipes at once by rewriting only
 * the colour attribute; materials registered with [register] get the metal/roughness of the style.
 */
class PipeKit {
    private val radius = WorldConst.PIPE_RADIUS
    private val pipeGeo = Primitives.cylinder(radius, radius, 1.0, 16).translate(0.0, 0.5, 0.0)
    private val lipGeo = Primitives.cylinder(radius + 0.25, radius + 0.25, 0.8, 16)
    private val bandGeo = Primitives.cylinder(radius + 0.28, radius + 0.28, 0.14, 16)
    private val stripeGeo = Primitives.box(0.28, 1.0, 0.28).translate(0.0, 0.5, 0.0)

    /** Body with highlight/shadow stripes; origin at the bottom, scale.y = height. */
    val body: Geometry = bodyParts(WorldLook.PIPE_COLORS)
    /** Lip with its dark band below (bottom pipe). */
    val capBelow: Geometry = capParts(WorldLook.PIPE_COLORS, -0.4)
    /** Lip with its dark band above (top pipe). */
    val capAbove: Geometry = capParts(WorldLook.PIPE_COLORS, 0.4)

    var style: PipeColors? = null
        private set
    private val materials = ArrayList<StandardMaterial>()

    private class Part(val geo: Geometry, val color: Int, val x: Double = 0.0, val y: Double = 0.0, val z: Double = 0.0)

    private fun bakeParts(parts: List<Part>): Geometry {
        val root = Node()
        for (p in parts) {
            root.add(Mesh(p.geo, BasicMaterial().apply { color.setHex(p.color) }).apply {
                position.set(p.x.toFloat(), p.y.toFloat(), p.z.toFloat())
            })
        }
        return bake(root)
    }

    private fun bodyParts(s: PipeColors) = bakeParts(
        listOf(
            Part(pipeGeo, s.pipe),
            Part(stripeGeo, s.light, -radius * 0.57, 0.0, radius * 0.8),
            Part(stripeGeo, s.dark, radius * 0.64, 0.0, radius * 0.75),
        ),
    )

    private fun capParts(s: PipeColors, bandY: Double) = bakeParts(listOf(Part(lipGeo, s.pipe), Part(bandGeo, s.dark, 0.0, bandY)))

    /** world.js `setPipeStyle(style)`: recolours the shared geometries in place. */
    fun setStyle(s: PipeColors) {
        recolor(body, bodyParts(s))
        recolor(capBelow, capParts(s, -0.4))
        recolor(capAbove, capParts(s, 0.4))
        style = s
        for (m in materials) apply(m)
    }

    private fun recolor(target: Geometry, fresh: Geometry) {
        fresh.colors!!.copyInto(target.colors!!)
        target.markDirty()
    }

    /** A lit pipe material (world.js `pipeMats` entry) that follows the style's metal flag. */
    fun newMaterial(): StandardMaterial = StandardMaterial().apply {
        vertexColors = true
        roughness = 0.45f
        flatShading = true
    }.also { register(it) }

    fun register(m: StandardMaterial) {
        materials.add(m)
        apply(m)
    }

    private fun apply(m: StandardMaterial) {
        val metal = style?.metal == true
        m.metalness = if (metal) 0.5f else 0f
        m.roughness = if (metal) 0.3f else 0.45f
    }
}

/**
 * world.js `addSkyHaze`: above HAZE_START the pipe colour blends into the sky colour seen behind
 * it (same gradient as the dome, fed by the live sky uniforms), fully sky-coloured at HAZE_END.
 * Runs after lighting, before the sRGB encode and fog, like the `tonemapping_fragment` hook.
 */
internal fun skyHazePatch(top: Uniform.C, horizon: Uniform.C) = ShaderPatch(
    key = "sky-haze",
    fragmentHead = "uniform vec3 skyTop;\nuniform vec3 skyHorizon;\n",
    fragmentOutput = """
{
    vec3 hazeDir = normalize( vWorldPosition - cameraPosition );
    vec3 skyCol = mix( skyHorizon, skyTop, pow( clamp( hazeDir.y * 1.8, 0.0, 1.0 ), 0.7 ) );
    float haze = smoothstep( ${WorldLook.HAZE_START}, ${WorldLook.HAZE_END}, vWorldPosition.y );
    outgoingLight = mix( outgoingLight, skyCol, haze );
}
""",
    uniforms = linkedMapOf("skyTop" to top, "skyHorizon" to horizon),
)
