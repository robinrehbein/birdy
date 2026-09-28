package de.robinrehbein.birdy.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Coin count chip (`.wallet`): plum 55 % pill, gold coin, 24px count with a `0 2px 0` ink shadow;
 * [bumpKey] retriggers the pop-scale animation (`pop-wallet` 1.35 -> 1 over 0.35 s).
 */
@Composable
fun CoinChip(coins: Int, bumpKey: Int, modifier: Modifier = Modifier) {
    val scale = remember { Animatable(1f) }
    LaunchedEffect(bumpKey) {
        if (bumpKey != 0) {
            scale.snapTo(1.35f)
            scale.animateTo(1f, androidx.compose.animation.core.tween(350))
        }
    }
    Row(
        modifier
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
            .background(BirdyColors.Ink.copy(alpha = 0.55f), RoundedCornerShape(50))
            .padding(start = 6.dp, top = 4.dp, end = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
    ) {
        CoinIcon(24.dp)
        OutlinedText(coins.toString(), size = 24.sp, thickness = 0.dp, down = 2.dp, color = BirdyColors.White)
    }
}
