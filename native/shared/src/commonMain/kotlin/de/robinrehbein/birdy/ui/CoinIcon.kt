package de.robinrehbein.birdy.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * `.coin-icon` (HUD coins and wallet): a filled gold disc with
 * `radial-gradient(circle at 35% 35%, #fff6a8, #f5c518 55%, #c98f00)` and a 2px ink border.
 */
@Composable
fun CoinIcon(size: Dp = 24.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val border = 2.dp.toPx()
        val r = this.size.minDimension / 2
        val c = Offset(this.size.width / 2, this.size.height / 2)
        val inner = r - border
        val focus = Offset(border + inner * 2 * 0.35f, border + inner * 2 * 0.35f)
        // `circle` farthest-corner radius from the 35 % focus of the padding box.
        val far = (Offset(border + inner * 2, border + inner * 2) - focus).getDistance()
        drawCircle(
            Brush.radialGradient(
                0f to Color(0xFFFFF6A8),
                0.55f to Color(0xFFF5C518),
                1f to Color(0xFFC98F00),
                center = focus,
                radius = far,
            ),
            radius = inner,
            center = c,
        )
        drawCircle(BirdyColors.Ink, radius = r - border / 2, center = c, style = Stroke(border))
    }
}
