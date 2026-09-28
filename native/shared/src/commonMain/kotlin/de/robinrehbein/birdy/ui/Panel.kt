package de.robinrehbein.birdy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.dp

/**
 * The tan card used by every menu/dialog (`.panel`): 4px ink border, 16px radius,
 * `box-shadow: 0 6px 0 ink, inset 0 -6px 0 #cbb968`, padding 24/20, centred content.
 */
@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .widthIn(max = 360.dp)
            .drawBehind {
                val r = CornerRadius(16.dp.toPx())
                val lip = 6.dp.toPx()
                drawRoundRect(BirdyColors.Ink, topLeft = Offset(0f, lip), size = size, cornerRadius = r)
                drawRoundRect(BirdyColors.Panel, size = size, cornerRadius = r)
                val clip = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, size.width, size.height, r)) }
                clipPath(clip) {
                    drawRect(BirdyColors.PanelInset, topLeft = Offset(0f, size.height - lip), size = Size(size.width, lip))
                }
            }
            .border(4.dp, BirdyColors.Ink, shape)
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

/** Dim scrim behind pause/game-over (`rgba(43,30,46,0.3)`; the backdrop blur is not reproduced). */
@Composable
fun Scrim(modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Box(modifier.background(BirdyColors.Scrim))
}

/** `h2` (and `.menu-title h1`): Lilita 48px, `0 4px / 3px` ink outline, orange by default. */
@Composable
fun Heading(
    text: String,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit(48f, androidx.compose.ui.unit.TextUnitType.Sp),
    color: androidx.compose.ui.graphics.Color = BirdyColors.Orange,
) {
    OutlinedText(text, modifier, size = size, color = color, thickness = 3.dp, down = 4.dp)
}
