package de.robinrehbein.birdy.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.robinrehbein.birdy.meta.IconDef
import de.robinrehbein.birdy.meta.IconPalette
import de.robinrehbein.birdy.meta.IconPart
import de.robinrehbein.birdy.meta.Icons

/** The three `iconSvg` render styles (meta.md §4.2). */
enum class IconStyle { Sticker, Glyph, Badge }

private val INK = IconPalette.INK.toComposeColor()
private val WHITE = IconPalette.WHITE.toComposeColor()

/** Draws one Birdy vector icon (24x24 grid) into a [size]x[size] box. Unknown names draw nothing. */
@Composable
fun GameIcon(name: String, size: Dp = 22.dp, style: IconStyle = IconStyle.Badge, modifier: Modifier = Modifier) {
    val def = Icons.find(name) ?: return
    Canvas(modifier.size(size)) {
        val scale = this.size.width / 24f
        drawIcon(def, style, scale)
    }
}

/** Renders [def] centred in a 24x24 unit grid already scaled into the current [DrawScope]. */
internal fun DrawScope.drawIcon(def: IconDef, style: IconStyle, scale: Float) {
    when (style) {
        IconStyle.Sticker -> drawParts(def.parts, scale, offset = Offset.Zero, partScale = 1f, forceColor = null, badge = null)
        IconStyle.Glyph -> {
            // Drop-shadow duplicate, offset down 1.4 units, solid ink.
            translate(0f, 1.4f * scale) { drawParts(def.parts, scale, Offset.Zero, 1f, forceColor = INK, badge = null, outlineOnly = true) }
            drawParts(def.parts, scale, Offset.Zero, 1f, forceColor = WHITE, badge = null)
        }
        IconStyle.Badge -> {
            val badgeColor = def.badge.toComposeColor()
            drawCircle(badgeColor, radius = 11f * scale, center = Offset(12f * scale, 12f * scale))
            drawCircle(INK, radius = 11f * scale, center = Offset(12f * scale, 12f * scale), style = Stroke(1.6f * scale))
            // Icon itself in white, scaled 0.7x and re-centred; LAND parts become the badge colour.
            val partScale = 0.7f
            val cx = 12f * scale
            val cy = 12f * scale
            translate(cx - cx * partScale, cy - cy * partScale) {
                drawParts(def.parts, scale * partScale, Offset.Zero, 1f, forceColor = WHITE, badge = badgeColor)
            }
        }
    }
}

private fun DrawScope.drawParts(
    parts: List<IconPart>,
    scale: Float,
    offset: Offset,
    partScale: Float,
    forceColor: Color?,
    badge: Color?,
    outlineOnly: Boolean = false,
) {
    for (part in parts) {
        when (part) {
            is IconPart.FilledPath -> {
                val path = parseSvgPath(part.d)
                val m = androidx.compose.ui.graphics.Matrix().apply { scale(scale * partScale, scale * partScale) }
                path.transform(m)
                val fill = colorFor(part.fill, forceColor, badge)
                if (!outlineOnly) drawPath(path, fill)
                if (forceColor == null) drawPath(path, INK, style = Stroke(2f * scale))
            }
            is IconPart.Stroke -> {
                val path = parseSvgPath(part.d)
                val m = androidx.compose.ui.graphics.Matrix().apply { scale(scale * partScale, scale * partScale) }
                path.transform(m)
                val col = forceColor ?: colorFor(part.color, null, badge)
                drawPath(path, col, style = Stroke(2.6f * scale, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
            }
            is IconPart.Circle -> {
                val cx = (part.cx.toFloat()) * scale * partScale
                val cy = (part.cy.toFloat()) * scale * partScale
                val r = part.r.toFloat() * scale * partScale
                val fill = colorFor(part.fill, forceColor, badge)
                if (!outlineOnly) drawCircle(fill, r, Offset(cx, cy))
                if (forceColor == null) drawCircle(INK, r, Offset(cx, cy), style = Stroke(2f * scale))
            }
        }
    }
}

/** `LAND` parts become the badge colour in badge style; ink-coloured parts stay ink even in glyph/badge. */
private fun colorFor(hex: String, forceColor: Color?, badge: Color?): Color {
    if (hex == IconPalette.INK) return INK
    if (badge != null && hex == IconPalette.LAND) return badge
    return forceColor ?: hex.toComposeColor()
}
