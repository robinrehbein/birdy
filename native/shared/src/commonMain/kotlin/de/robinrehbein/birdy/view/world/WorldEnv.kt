package de.robinrehbein.birdy.view.world

import de.robinrehbein.birdy.engine.math.Color
import de.robinrehbein.birdy.engine.mesh.Primitives
import de.robinrehbein.birdy.engine.scene.CustomShaderMaterial
import de.robinrehbein.birdy.engine.scene.DirectionalLight
import de.robinrehbein.birdy.engine.scene.Fog
import de.robinrehbein.birdy.engine.scene.HemisphereLight
import de.robinrehbein.birdy.engine.scene.Mesh
import de.robinrehbein.birdy.engine.scene.Scene
import de.robinrehbein.birdy.engine.scene.Side
import de.robinrehbein.birdy.engine.scene.Uniform

/**
 * world.js `createScene()`: background, linear fog, gradient sky dome, hemisphere light and the
 * shadow-casting sun. [skyTop]/[skyHorizon] are the live uniforms that biomes blend and the pipe
 * haze reads (`scene.userData.env`).
 */
class WorldEnv(val scene: Scene) {
    val skyTop = Uniform.C(Color.hex(WorldLook.SKY_TOP))
    val skyHorizon = Uniform.C(Color.hex(WorldLook.SKY_HORIZON))

    val background: Color = Color.hex(WorldLook.SKY_HORIZON)
    val fog = Fog(WorldLook.SKY_HORIZON, WorldLook.FOG_NEAR, WorldLook.FOG_FAR)

    val sky = Mesh(
        Primitives.sphere(WorldLook.SKY_RADIUS, 24, 12),
        CustomShaderMaterial(
            key = "birdy-sky",
            vertexSource = SKY_VERTEX,
            fragmentSource = SKY_FRAGMENT,
            uniforms = linkedMapOf("top" to skyTop, "horizon" to skyHorizon),
        ).apply {
            side = Side.Back
            depthWrite = false
            fog = false
        },
        "sky",
    ).apply { renderOrder = -1 }

    val hemi = HemisphereLight(0xdff6ff, 0x6a8f3a, 1.4f)

    val sun = DirectionalLight(0xfff4d6, 2.2f).apply {
        name = "sun"
        position.set(12f, 30f, 10f)
        target.set(0f, 0f, -15f)
        castShadow = true
        shadow.mapSize = 1024
        shadow.left = -18f
        shadow.right = 18f
        shadow.top = 40f
        shadow.bottom = -30f
        shadow.near = 1f
        shadow.far = 90f
        shadow.bias = -0.0005f
    }

    init {
        scene.background = background
        scene.fog = fog
        scene.add(sky, hemi, sun)
    }

    companion object {
        private const val SKY_VERTEX = """
out vec3 vDir;
void main() {
    vDir = normalize( position );
    gl_Position = projectionMatrix * modelViewMatrix * vec4( position, 1.0 );
}
"""
        private const val SKY_FRAGMENT = """
uniform vec3 top;
uniform vec3 horizon;
in vec3 vDir;
void main() {
    float h = clamp( vDir.y * 1.8, 0.0, 1.0 );
    fragColor = linearToOutputTexel( vec4( mix( horizon, top, pow( h, 0.7 ) ), 1.0 ) );
}
"""
    }
}
