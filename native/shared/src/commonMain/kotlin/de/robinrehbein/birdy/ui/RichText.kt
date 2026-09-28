package de.robinrehbein.birdy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.robinrehbein.birdy.meta.Icons

/** One run of `rich()`-expanded text: plain text, an inline icon, or a forced line break (`<br>`). */
internal sealed class RichRun {
    data class Text(val s: String, val bold: Boolean) : RichRun()
    data class Icon(val name: String) : RichRun()
    object Break : RichRun()
}

/**
 * Splits `rich()` markup used throughout the JS strings: `[iconName]` tokens (icons.js §4.3,
 * unknown names left as literal text), `<br>`/`<br/>` line breaks and `<b>...</b>` bold spans
 * (used by `howto`, main-b.md §6). Not a general HTML parser — just what i18n.js actually emits.
 */
internal fun parseRich(text: String): List<RichRun> {
    val runs = mutableListOf<RichRun>()
    val sb = StringBuilder()
    var bold = false
    fun flush() {
        if (sb.isNotEmpty()) { runs.add(RichRun.Text(sb.toString(), bold)); sb.clear() }
    }
    var i = 0
    while (i < text.length) {
        val c = text[i]
        when {
            c == '[' -> {
                val end = text.indexOf(']', i + 1)
                val name = if (end > i) text.substring(i + 1, end) else null
                if (name != null && Icons.find(name) != null) {
                    flush(); runs.add(RichRun.Icon(name)); i = end + 1
                } else { sb.append(c); i++ }
            }
            text.startsWith("<br", i) -> {
                flush(); runs.add(RichRun.Break)
                i = text.indexOf('>', i).let { if (it >= 0) it + 1 else i + 1 }
            }
            text.startsWith("<b>", i) -> { flush(); bold = true; i += 3 }
            text.startsWith("</b>", i) -> { flush(); bold = false; i += 4 }
            else -> { sb.append(c); i++ }
        }
    }
    flush()
    return runs
}

/**
 * Renders JS `rich()`/`setRich()` text: inline [name] icons, `<br>` breaks, `<b>` bold — laid out
 * as a wrapping row of outlined-text spans and icons (main-b.md §13). `<br>` starts a fresh row.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RichText(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 16.sp,
    color: Color = BirdyColors.Ink,
    outline: Color? = null,
    font: FontFamily = bodyFont(),
    iconStyle: IconStyle = IconStyle.Badge,
    outlineWidth: Dp = 1.dp,
    shadowDown: Dp = outlineWidth + 1.dp,
    center: Boolean = false,
) {
    val runs = remember(text) { parseRich(text) }
    // Split on hard breaks into lines; each line wraps independently as a FlowRow.
    val lines = remember(runs) {
        val out = mutableListOf<MutableList<RichRun>>(mutableListOf())
        for (r in runs) if (r is RichRun.Break) out.add(mutableListOf()) else out.last().add(r)
        out
    }
    androidx.compose.foundation.layout.Column(
        modifier,
        horizontalAlignment = if (center) androidx.compose.ui.Alignment.CenterHorizontally else androidx.compose.ui.Alignment.Start,
    ) {
        for (line in lines) {
            FlowRow(
                horizontalArrangement = if (center) Arrangement.spacedBy(2.dp, androidx.compose.ui.Alignment.CenterHorizontally) else Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.Center,
                itemVerticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                for (run in line) when (run) {
                    is RichRun.Icon -> GameIcon(run.name, size = iconDp(size), style = iconStyle)
                    is RichRun.Text -> if (outline != null) {
                        OutlinedText(run.s, size = size, color = color, outline = outline, thickness = outlineWidth, font = font, down = shadowDown)
                    } else {
                        BasicText(run.s, style = TextStyle(fontFamily = font, fontSize = size, color = color))
                    }
                    RichRun.Break -> Unit
                }
            }
        }
    }
}

/** Icon glyphs render at ~1.35x the surrounding text size (icons.js `icon(name,'1.35em')`). */
private fun iconDp(textSize: TextUnit): Dp = (textSize.value * 1.35).dp
