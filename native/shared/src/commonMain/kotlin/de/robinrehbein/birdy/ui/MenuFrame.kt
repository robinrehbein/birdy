package de.robinrehbein.birdy.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import de.robinrehbein.birdy.game.Menu
import de.robinrehbein.birdy.game.UiCommand
import kotlin.math.abs

/**
 * main.js `measureMenuFrame`: reports the bottom of the menu title and the top of the panel
 * (fractions of the overlay height) as [UiCommand.MenuFrame], so the game frames the 3D bird in
 * the free band between them. Sends only when a value moved by more than 0.001.
 */
class MenuFrameReporter internal constructor(private val menu: Menu, private val send: (UiCommand) -> Unit) {
    private var rootTop = 0f
    private var rootHeight = 0f
    private var titleBottom = Float.NaN
    private var panelTop = Float.NaN
    private var sentTitle = Float.NaN
    private var sentPanel = Float.NaN

    val root: Modifier = Modifier.onGloballyPositioned {
        rootTop = it.positionInRoot().y
        rootHeight = it.size.height.toFloat()
        report()
    }
    val title: Modifier = Modifier.onGloballyPositioned {
        titleBottom = it.positionInRoot().y + it.size.height
        report()
    }
    val panel: Modifier = Modifier.onGloballyPositioned {
        panelTop = it.positionInRoot().y
        report()
    }

    private fun report() {
        if (rootHeight <= 0f || titleBottom.isNaN() || panelTop.isNaN()) return
        val t = (titleBottom - rootTop) / rootHeight
        val p = (panelTop - rootTop) / rootHeight
        if (!sentTitle.isNaN() && abs(t - sentTitle) < 0.001f && abs(p - sentPanel) < 0.001f) return
        sentTitle = t
        sentPanel = p
        send(UiCommand.MenuFrame(menu, t, p))
    }
}

@Composable
fun rememberMenuFrameReporter(menu: Menu, onCommand: (UiCommand) -> Unit): MenuFrameReporter {
    val latest by rememberUpdatedState(onCommand)
    return remember(menu) { MenuFrameReporter(menu) { latest(it) } }
}

/**
 * `.panel-wrap.menu`: title on top, bird visible in the free middle band, panel at the bottom
 * (padding `max(16px, safe-top) + 44px` / `max(20px, safe-bottom)`), with the frame reported.
 */
@Composable
fun MenuLayout(
    menu: Menu,
    onCommand: (UiCommand) -> Unit,
    title: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    background: @Composable BoxScope.() -> Unit = {},
    panel: @Composable (Modifier) -> Unit,
) {
    val frame = rememberMenuFrameReporter(menu, onCommand)
    Box(modifier.fillMaxSize().then(frame.root).windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Top))) {
        background()
        Column(
            Modifier.align(Alignment.TopCenter).padding(top = 60.dp, start = 16.dp, end = 16.dp).then(frame.title),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = title,
        )
        Box(Modifier.align(Alignment.BottomCenter).padding(start = 16.dp, end = 16.dp, bottom = 20.dp)) {
            panel(frame.panel)
        }
    }
}
