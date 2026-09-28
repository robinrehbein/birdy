package de.robinrehbein.birdy.ui.screens

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.robinrehbein.birdy.game.PowerType
import de.robinrehbein.birdy.game.UiState
import de.robinrehbein.birdy.meta.Strings
import de.robinrehbein.birdy.ui.BirdyColors
import de.robinrehbein.birdy.ui.GameIcon
import de.robinrehbein.birdy.ui.IconStyle
import de.robinrehbein.birdy.ui.OutlinedText

/** In-run HUD (`#hud`, main-b.md §12): score top centre, coins top right, power pills, lane dots. */
@Composable
fun HudScreen(state: UiState, strings: Strings, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxSize().then(Modifier.windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)))) {
        // `font-size: clamp(48px, 10vw, 80px)`, `#score.pop` 1.25 -> 1 over 0.2 s.
        val scoreSize = (maxWidth.value * 0.10f).coerceIn(48f, 80f)
        val pop = remember { Animatable(1f) }
        LaunchedEffect(state.scorePop) {
            if (state.scorePop > 0) {
                pop.snapTo(1.25f)
                pop.animateTo(1f, tween(200, easing = LinearOutSlowInEasing))
            }
        }
        OutlinedText(
            state.score.toString(),
            Modifier.align(Alignment.TopCenter).padding(top = 16.dp).graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
            },
            size = scoreSize.sp,
            thickness = 3.dp,
        )
        Row(
            Modifier.align(Alignment.TopEnd).padding(top = 20.dp, end = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            de.robinrehbein.birdy.ui.CoinIcon(24.dp)
            OutlinedText(state.runCoins.toString(), size = 28.sp, thickness = 2.dp)
        }
        Column(
            Modifier.align(Alignment.TopEnd).padding(top = 64.dp, end = 16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (p in PowerType.entries) {
                val i = p.ordinal
                if (state.powerActive.getOrElse(i) { false }) {
                    PowerPill(p, state.power.getOrElse(i) { 0f }, state.powerEnding.getOrElse(i) { false })
                }
            }
        }
        LaneDots(state.lane, Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp))
    }
}

@Composable
private fun PowerPill(power: PowerType, remaining: Float, ending: Boolean) {
    Row(
        Modifier
            .padding(vertical = 2.dp)
            .alpha(if (ending && blinkOff()) 0.35f else 1f)
            .background(BirdyColors.Ink.copy(alpha = 0.55f), RoundedCornerShape(50))
            .padding(start = 4.dp, top = 3.dp, bottom = 3.dp, end = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GameIcon(power.icon, size = 20.dp)
        Box(Modifier.width(54.dp).height(8.dp).background(BirdyColors.White.copy(alpha = 0.3f), RoundedCornerShape(4.dp))) {
            Box(
                Modifier
                    .fillMaxWidth(remaining.coerceIn(0f, 1f))
                    .height(8.dp)
                    .background(BirdyColors.Gold, RoundedCornerShape(4.dp)),
            )
        }
    }
}

/**
 * `#lanes`: three lane pills (11x16, radius 5, 2px ink border) in a plum 45 % capsule; the bird's
 * lane is gold and 22 tall, bottom-aligned (style.css "Current lane" override).
 */
@Composable
fun LaneDots(lane: Int, modifier: Modifier = Modifier) {
    Row(
        modifier
            .background(BirdyColors.Ink.copy(alpha = 0.45f), RoundedCornerShape(50))
            .padding(horizontal = 9.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        for (i in 0..2) {
            val on = i == lane
            val shape = RoundedCornerShape(5.dp)
            Box(
                Modifier
                    .width(11.dp)
                    .height(if (on) 22.dp else 16.dp)
                    .background(if (on) BirdyColors.Gold else BirdyColors.White.copy(alpha = 0.45f), shape)
                    .border(2.dp, BirdyColors.Ink, shape),
            )
        }
    }
}

/** `.power.ending`: `blink 0.25s steps(2)`: half of every 0.25 s period at 35 % opacity. */
@Composable
private fun blinkOff(): Boolean {
    val t = rememberInfiniteTransition(label = "blink")
    val v by t.animateFloat(0f, 1f, infiniteRepeatable(tween(250, easing = LinearEasing)), label = "blink")
    return v >= 0.5f
}
