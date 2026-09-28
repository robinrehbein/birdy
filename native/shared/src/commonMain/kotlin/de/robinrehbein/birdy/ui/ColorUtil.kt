package de.robinrehbein.birdy.ui

import androidx.compose.ui.graphics.Color

/** JS `0xRRGGBB` int -> Compose [Color] (opaque, sRGB — matches how the JS/catalog stores hex ints). */
fun Int.toComposeColor(alpha: Float = 1f): Color {
    val r = (this shr 16) and 0xFF
    val g = (this shr 8) and 0xFF
    val b = this and 0xFF
    return Color(r / 255f, g / 255f, b / 255f, alpha)
}

/** `#rrggbb`/`#rgb` hex string (icons.js palette, CSS colors) -> Compose [Color]. */
fun String.toComposeColor(): Color {
    val h = removePrefix("#")
    return when (h.length) {
        3 -> {
            val r = h[0].digitToInt(16) * 17
            val g = h[1].digitToInt(16) * 17
            val b = h[2].digitToInt(16) * 17
            Color(r / 255f, g / 255f, b / 255f)
        }
        else -> {
            val v = h.toLong(16)
            Color((v shr 16 and 0xFF).toInt() / 255f, (v shr 8 and 0xFF).toInt() / 255f, (v and 0xFF).toInt() / 255f)
        }
    }
}
