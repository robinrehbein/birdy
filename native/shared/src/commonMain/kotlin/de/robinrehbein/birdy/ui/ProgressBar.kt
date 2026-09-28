package de.robinrehbein.birdy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Mission/achievement/next-unlock progress bar (`.bar > i`, style.css:389-390, 613-614). */
@Composable
fun ProgressBar(
    percent: Int,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
    fill: Color = BirdyColors.Green,
    track: Color = BirdyColors.Ink.copy(alpha = 0.25f),
) {
    Box(modifier.fillMaxWidth().height(height).background(track, RoundedCornerShape(height / 2))) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(percent.coerceIn(0, 100) / 100f)
                .align(Alignment.CenterStart)
                .background(fill, RoundedCornerShape(height / 2)),
        )
    }
}
