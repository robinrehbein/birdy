package de.robinrehbein.birdy.engine.scene

import de.robinrehbein.birdy.engine.math.Color
import de.robinrehbein.birdy.engine.math.Vec3

/** Base light; lights are found by traversing the scene. Colours are sRGB hex like in JS. */
sealed class Light(name: String) : Node(name) {
    val color = Color(1f, 1f, 1f)
    var intensity = 1f
}

/** three.js `HemisphereLight`: sky colour from +Y, ground colour from -Y. */
class HemisphereLight(sky: Int, ground: Int, intensity: Float) : Light("hemi") {
    val groundColor = Color.hex(ground)

    init {
        color.setHex(sky)
        this.intensity = intensity
    }
}

/**
 * three.js `DirectionalLight`: light travels from [position] toward [target] (world space).
 * With [castShadow] the backend renders an orthographic depth map using [shadow].
 */
class DirectionalLight(color: Int, intensity: Float) : Light("sun") {
    val target = Vec3()
    val shadow = ShadowConfig()

    init {
        this.color.setHex(color)
        this.intensity = intensity
    }
}

/** Orthographic shadow camera + map parameters (three.js `DirectionalLightShadow`). */
class ShadowConfig {
    var mapSize = 1024
    var left = -10f
    var right = 10f
    var top = 10f
    var bottom = -10f
    var near = 0.5f
    var far = 500f
    var bias = 0f
    var normalBias = 0f
}
