package de.robinrehbein.birdy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.times
import de.robinrehbein.birdy.game.HandUi
import de.robinrehbein.birdy.game.PopupUi
import de.robinrehbein.birdy.game.TapFxUi
import de.robinrehbein.birdy.game.ToastUi
import de.robinrehbein.birdy.game.ZoneBannerUi
import de.robinrehbein.birdy.ui.BirdyColors
import de.robinrehbein.birdy.ui.GameIcon
import de.robinrehbein.birdy.ui.OutlinedText
import de.robinrehbein.birdy.ui.RichText

/** One-line toast banner (`#toast`, main-b.md §14). Visible 2.2s per queued message. */
@Composable
fun ToastOverlay(toast: ToastUi?, modifier: Modifier = Modifier) {
    if (toast == null) return
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Box(
            Modifier.padding(top = 140.dp).background(BirdyColors.Ink.copy(alpha = 0.85f), RoundedCornerShape(10.dp)).padding(10.dp, 6.dp),
        ) {
            RichText(toast.text, color = BirdyColors.White, size = 17.sp)
        }
    }
}

/** Ghost-hand tutorial overlay (main-b.md §9). [x]/[y] are screen fractions. */
@Composable
fun TutorialHandOverlay(hand: HandUi?, sizeOf: androidx.compose.ui.unit.DpSize, modifier: Modifier = Modifier) {
    if (hand == null) return
    Box(modifier.fillMaxSize()) {
        Box(Modifier.offset(sizeOf.width * hand.x - 24.dp, sizeOf.height * hand.y - 24.dp)) {
            GameIcon("hand", size = 48.dp, style = de.robinrehbein.birdy.ui.IconStyle.Sticker)
        }
        if (hand.label.isNotEmpty()) {
            // `#hand .label`: plum 75 % pill, 18px, `0 2px 0` ink shadow, centred under the hand.
            Box(
                Modifier.offset(sizeOf.width * hand.x - 150.dp, sizeOf.height * hand.y + 30.dp).width(300.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                Box(
                    Modifier
                        .background(BirdyColors.Ink.copy(alpha = 0.75f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    RichText(
                        hand.label, size = 18.sp, color = BirdyColors.White, outline = BirdyColors.Ink,
                        outlineWidth = 0.dp, shadowDown = 2.dp, font = de.robinrehbein.birdy.ui.displayFont(), center = true,
                    )
                }
            }
        }
    }
}

/** "Knapp!" near-miss popup (`#popup`, 0.8s float-up-and-fade). */
@Composable
fun PopupOverlay(popup: PopupUi?, sizeOf: androidx.compose.ui.unit.DpSize, modifier: Modifier = Modifier) {
    if (popup == null) return
    val fadeAlpha = (1f - (popup.age / 0.8f)).coerceIn(0f, 1f)
    Box(modifier.fillMaxSize()) {
        Box(Modifier.offset(sizeOf.width * popup.x, sizeOf.height * popup.y).alpha(fadeAlpha)) {
            OutlinedText(popup.text, size = 20.sp, color = BirdyColors.Popup, thickness = 2.dp)
        }
    }
}

/** Zone-change banner (`#zone-banner`, 2.4s in/hold/out, main-b.md §12). */
@Composable
fun ZoneBannerOverlay(banner: ZoneBannerUi?, modifier: Modifier = Modifier) {
    if (banner == null) return
    val fadeAlpha = when {
        banner.age < 0.3f -> banner.age / 0.3f
        banner.age > 2.1f -> ((2.4f - banner.age) / 0.3f).coerceIn(0f, 1f)
        else -> 1f
    }
    Box(modifier.fillMaxSize().alpha(fadeAlpha), contentAlignment = Alignment.Center) {
        Box(Modifier.padding(top = 60.dp), contentAlignment = Alignment.Center) {
            androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
                OutlinedText(banner.zoneLabel, size = 20.sp, color = BirdyColors.Gold, thickness = 2.dp)
                OutlinedText(banner.name, size = 40.sp, thickness = 3.dp)
            }
        }
    }
}

/** Tap ripple pool (4 reused ripples, 0.35s scale+fade, main-b.md §12). */
@Composable
fun TapFxOverlay(fx: List<TapFxUi>, sizeOf: androidx.compose.ui.unit.DpSize, modifier: Modifier = Modifier) {
    if (fx.isEmpty()) return
    Box(modifier.fillMaxSize()) {
        for (r in fx) {
            val progress = (r.age / 0.35f).coerceIn(0f, 1f)
            val scale = 0.5f + 0.75f * progress
            val fadeAlpha = 1f - progress
            Box(
                Modifier
                    .offset(sizeOf.width * r.x - 16.dp * scale, sizeOf.height * r.y - 16.dp * scale)
                    .alpha(fadeAlpha),
            ) {
                val glyph = when { r.dir < 0 -> "◀"; r.dir > 0 -> "▶"; else -> "▲" }
                OutlinedText(glyph, size = (20 * scale).sp, thickness = 1.dp)
            }
        }
    }
}
