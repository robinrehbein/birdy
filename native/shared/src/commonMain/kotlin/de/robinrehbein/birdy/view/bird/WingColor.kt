package de.robinrehbein.birdy.view.bird

import de.robinrehbein.birdy.engine.math.Color
import de.robinrehbein.birdy.engine.math.ColorSpace
import de.robinrehbein.birdy.meta.SkinItem

/** Wing and covert colours actually drawn for a skin (bird.js `wingColor`). */
data class WingColors(val wing: Int, val cover: Int)

/**
 * Pale wings (CSS lightness >= 0.8) read as sticks from behind, so they are repainted in the
 * body's colour family: the wing 12 % from body toward belly (three.js linear lerp), the covert
 * layer in the tail colour. Distinctly coloured wings stay as set.
 */
fun wingColor(skin: SkinItem): WingColors {
    val l = Color.hex(skin.wing).getHSL(space = ColorSpace.SRGB).l
    if (l < 0.8) return WingColors(skin.wing, skin.cover)
    val w = Color.hex(skin.body).lerp(Color.hex(skin.belly), 0.12)
    return WingColors(w.getHex(), skin.tail)
}
