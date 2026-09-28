package de.robinrehbein.birdy.ui.screens

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.robinrehbein.birdy.game.ZonesHintUi
import de.robinrehbein.birdy.ui.BirdyColors
import de.robinrehbein.birdy.ui.OutlinedText

private val EASE_OUT = CubicBezierEasing(0f, 0f, 0.58f, 1f)
private const val FLASH_S = 2.6f

/** `zone-fade` / `zone-border`: constant until 60 %, then an ease-out segment to the end value. */
private fun fadeProgress(age: Float): Float {
    val t = age / FLASH_S
    return if (t <= 0.6f) 0f else EASE_OUT.transform(((t - 0.6f) / 0.4f).coerceIn(0f, 1f))
}

/**
 * The three tap zones (`#zones`, style.css): full-height columns split at the lane edges with a
 * dashed divider, masked to the lower screen (`linear-gradient(transparent 28%, #000 52%)`).
 * `hold` pulses until the first tap; `show` flashes labels + tint for 2.6 s, after which only
 * the faint dividers and the own-lane tint remain for the rest of the run.
 */
@Composable
fun ZoneHintOverlay(hint: ZonesHintUi?, sizeOf: DpSize, modifier: Modifier = Modifier) {
    if (hint == null || hint.edges.size < 2) return
    val fade = if (hint.flash) fadeProgress(hint.age) else 0f
    val labelAlpha = 1f - fade
    val borderAlpha = if (hint.flash) 0.5f + (0.2f - 0.5f) * fade else 0.5f
    val pulse = if (hint.hold) {
        val tr = rememberInfiniteTransition(label = "zones")
        val v by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
        v
    } else 0f
    val e0 = hint.edges[0].coerceIn(0.001f, 0.998f)
    val e1 = hint.edges[1].coerceIn(e0 + 0.001f, 0.999f)
    val weights = listOf(e0, e1 - e0, 1f - e1)
    Row(
        modifier
            .fillMaxSize()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(
                    Brush.verticalGradient(
                        0.28f to Color.Transparent,
                        0.52f to Color.Black,
                        startY = 0f,
                        endY = size.height,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            },
    ) {
        for (i in 0..2) {
            val own = i == hint.lane
            val last = i == 2
            Box(
                Modifier
                    .weight(weights[i])
                    .fillMaxHeight()
                    .drawBehind {
                        // `#zones.show div.own` (7 %) outranks `#zones div.own` (18 %) by specificity.
                        if (own) drawRect(Color(0xFFFFF176).copy(alpha = if (hint.flash) 0.07f else 0.18f))
                        if (hint.hold) drawRect(Color.White.copy(alpha = 0.08f * pulse))
                        // `::before` white 12 % veil, fading with the labels.
                        drawRect(Color.White.copy(alpha = 0.12f * labelAlpha))
                        if (!last) {
                            val w = 2.dp.toPx()
                            val x = size.width - w / 2
                            drawLine(
                                Color.White.copy(alpha = borderAlpha),
                                Offset(x, 0f),
                                Offset(x, size.height),
                                strokeWidth = w,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(2 * w, 2 * w)),
                            )
                        }
                    },
            ) {
                if (labelAlpha > 0.001f) {
                    Column(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = sizeOf.height * 0.22f)
                            .alpha(labelAlpha),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        ArrowGlyph(if (own) 0 else if (i < hint.lane) -1 else 1)
                        OutlinedText(if (own) hint.flapLabel else hint.moveLabel, size = 20.sp, thickness = 2.dp)
                    }
                }
            }
        }
    }
}

/**
 * The `<b>` arrow (▲ / ◀ / ▶ at 34px) drawn as a triangle with the same `0 2px / 2px` ink outline,
 * so no platform emoji font substitutes the glyph. [dir]: -1 left, 0 up, 1 right.
 */
@Composable
private fun ArrowGlyph(dir: Int) {
    androidx.compose.foundation.Canvas(Modifier.size(34.dp, 34.dp)) {
        val s = size.minDimension * 0.62f
        val c = Offset(size.width / 2, size.height / 2)
        fun tri(dx: Float, dy: Float): androidx.compose.ui.graphics.Path = androidx.compose.ui.graphics.Path().apply {
            val h = s * 0.87f
            when (dir) {
                0 -> { moveTo(c.x + dx, c.y - h / 2 + dy); lineTo(c.x + s / 2 + dx, c.y + h / 2 + dy); lineTo(c.x - s / 2 + dx, c.y + h / 2 + dy) }
                -1 -> { moveTo(c.x - h / 2 + dx, c.y + dy); lineTo(c.x + h / 2 + dx, c.y - s / 2 + dy); lineTo(c.x + h / 2 + dx, c.y + s / 2 + dy) }
                else -> { moveTo(c.x + h / 2 + dx, c.y + dy); lineTo(c.x - h / 2 + dx, c.y - s / 2 + dy); lineTo(c.x - h / 2 + dx, c.y + s / 2 + dy) }
            }
            close()
        }
        val o = 2.dp.toPx()
        for ((dx, dy) in listOf(0f to o, o to 0f, -o to 0f, 0f to -o)) drawPath(tri(dx, dy), BirdyColors.Ink)
        drawPath(tri(0f, 0f), Color.White)
    }
}
