package de.robinrehbein.birdy.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.alpha

/**
 * Button presets from style.css: `button` (orange, 26px, padding 10/28, 5px lip) and its variants
 * (`.secondary`, `button.gift`, `#shop-action.buy`, `button.surprise`, `.reward-ad`, `.real-buy`,
 * `.coin-packs button`). [disabledAlpha] is the variant's `:disabled` opacity.
 */
enum class ButtonStyle(
    val bg: Color,
    val textSp: Int,
    val padV: Int,
    val padH: Int,
    val lip: Int = 5,
    val disabledAlpha: Float = 0.55f,
    val disabledBg: Color? = null,
) {
    Primary(BirdyColors.Orange, 26, 10, 28, disabledAlpha = 0.8f, disabledBg = Color(0xFFB7AD7A)),
    Secondary(BirdyColors.Green, 20, 6, 20),
    Gift(BirdyColors.Gold, 20, 8, 16),
    Gold(BirdyColors.Gold, 26, 10, 28, disabledAlpha = 0.8f, disabledBg = Color(0xFFB7AD7A)),
    Surprise(BirdyColors.Purple, 16, 5, 10, lip = 3),
    Purple(BirdyColors.Purple, 14, 6, 8, disabledAlpha = 0.65f),
    RealMoney(BirdyColors.RealMoney, 15, 7, 8),
    CoinPack(BirdyColors.Green, 12, 6, 4),
}

/**
 * A "3D lip" button: an ink drop shadow behind the face, which slides down onto it on press
 * (`box-shadow: 0 5px 0 ink`, `:active { translateY(4px) }`, 80 ms). The face always fills the
 * button's width, with the centred label in Lilita One and a `0 2px 0` ink text shadow.
 */
@Composable
fun GameButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ButtonStyle = ButtonStyle.Primary,
    enabled: Boolean = true,
    richIcon: IconStyle = IconStyle.Badge,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val lip = style.lip.dp
    val pressOffset by animateDpAsState(if (pressed && enabled) lip - 1.dp else 0.dp, tween(80), label = "press")
    val bg = if (enabled) style.bg else style.disabledBg ?: style.bg
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .alpha(if (enabled) 1f else style.disabledAlpha)
            .clickable(interaction, indication = null, enabled = enabled, onClick = onClick),
        propagateMinConstraints = true,
    ) {
        Box(Modifier.matchParentSize().offset(y = lip).background(BirdyColors.Ink, shape))
        Box(
            Modifier
                .offset(y = pressOffset)
                .background(bg, shape)
                .border(4.dp, BirdyColors.Ink, shape)
                .padding(horizontal = style.padH.dp, vertical = style.padV.dp),
            contentAlignment = Alignment.Center,
        ) {
            RichText(
                label,
                size = style.textSp.sp,
                color = BirdyColors.White,
                outline = BirdyColors.Ink,
                outlineWidth = 0.dp,
                shadowDown = 2.dp,
                font = displayFont(),
                iconStyle = richIcon,
                center = true,
            )
        }
    }
}

/** `.privacy-link`: 12px Fredoka, 75 % opacity, underlined, no button chrome. */
@Composable
fun TextLink(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = BirdyColors.Ink) {
    androidx.compose.foundation.text.BasicText(
        label,
        modifier.clickable(onClick = onClick),
        style = androidx.compose.ui.text.TextStyle(
            fontFamily = bodyFont(),
            fontSize = androidx.compose.ui.unit.TextUnit(12f, androidx.compose.ui.unit.TextUnitType.Sp),
            color = color.copy(alpha = color.alpha * 0.75f),
            textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        ),
    )
}
