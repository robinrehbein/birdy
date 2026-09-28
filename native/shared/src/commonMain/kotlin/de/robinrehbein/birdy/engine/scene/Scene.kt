package de.robinrehbein.birdy.engine.scene

import de.robinrehbein.birdy.engine.math.Color

/** Linear fog (three.js `Fog`). */
class Fog(color: Int, var near: Float, var far: Float) {
    val color = Color.hex(color)
}

/** Root of a renderable graph. [background] clears the colour buffer when non-null. */
class Scene : Node("scene") {
    var background: Color? = Color.hex(0x87ceeb)
    var fog: Fog? = null
}
