package de.robinrehbein.birdy.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Text with the CSS 4-direction hard outline used everywhere in the JS UI
 * (`text-shadow: 0 D 0 ink, N 0 0 ink, -N 0 0 ink, 0 -N 0 ink`, D = [down], N = [thickness]).
 */
@Composable
fun OutlinedText(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 26.sp,
    color: Color = BirdyColors.White,
    outline: Color = BirdyColors.Ink,
    thickness: Dp = 3.dp,
    font: FontFamily = displayFont(),
    down: Dp = thickness + 1.dp,
    align: TextAlign? = null,
) = OutlinedText(AnnotatedString(text), modifier, size, color, outline, thickness, font, down, align)

/** [OutlinedText] for multi-colour text (span colours apply to the fill only, e.g. "Bir" + "dy"). */
@Composable
fun OutlinedText(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    size: TextUnit = 26.sp,
    color: Color = BirdyColors.White,
    outline: Color = BirdyColors.Ink,
    thickness: Dp = 3.dp,
    font: FontFamily = displayFont(),
    down: Dp = thickness + 1.dp,
    align: TextAlign? = null,
) {
    val base = TextStyle(fontFamily = font, fontSize = size, textAlign = align ?: TextAlign.Unspecified)
    val plain = AnnotatedString(text.text)
    Box(modifier) {
        for ((dx, dy) in listOf(0.dp to down, thickness to 0.dp, -thickness to 0.dp, 0.dp to -thickness)) {
            BasicText(plain, Modifier.offset(dx, dy), style = base.copy(color = outline))
        }
        BasicText(text, style = base.copy(color = color))
    }
}

/** `text-shadow: 0 N 0 ink` only: the drop shadow buttons and chips use (no side strokes). */
@Composable
fun ShadowText(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 26.sp,
    color: Color = BirdyColors.White,
    shadow: Color = BirdyColors.Ink,
    down: Dp = 2.dp,
    font: FontFamily = displayFont(),
) {
    val base = TextStyle(fontFamily = font, fontSize = size)
    Box(modifier) {
        BasicText(text, Modifier.offset(0.dp, down), style = base.copy(color = shadow))
        BasicText(text, style = base.copy(color = color))
    }
}
