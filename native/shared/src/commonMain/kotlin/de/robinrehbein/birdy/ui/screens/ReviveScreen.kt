package de.robinrehbein.birdy.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.robinrehbein.birdy.game.ReviveUi
import de.robinrehbein.birdy.game.UiCommand
import de.robinrehbein.birdy.ui.BirdyColors
import de.robinrehbein.birdy.ui.ButtonStyle
import de.robinrehbein.birdy.ui.GameButton
import de.robinrehbein.birdy.ui.Heading
import de.robinrehbein.birdy.ui.Panel
import de.robinrehbein.birdy.ui.ProgressBar
import de.robinrehbein.birdy.ui.Scrim

/**
 * "Weiterfliegen?" offer after a crash near the record (main-b.md §5.1): a countdown bar, the
 * pay button (rewarded ad, or coins once automatic ads were removed) and "No thanks". A tap
 * anywhere outside the buttons declines, like the countdown running out (the game decides).
 */
@Composable
fun ReviveScreen(ui: ReviveUi, onCommand: (UiCommand) -> Unit, modifier: Modifier = Modifier) {
    val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Box(
        modifier.fillMaxSize().clickable(source, indication = null) { onCommand(UiCommand.ReviveDecline) },
        contentAlignment = Alignment.Center,
    ) {
        Scrim(Modifier.fillMaxSize())

        Panel(Modifier.padding(16.dp)) {
            Heading(ui.title, Modifier.padding(bottom = 14.dp), size = 40.sp)
            ProgressBar(ui.percent, Modifier.fillMaxWidth().padding(bottom = 16.dp), height = 10.dp, fill = BirdyColors.Orange)
            GameButton(
                ui.accept.label,
                { onCommand(UiCommand.ReviveAccept) },
                Modifier.fillMaxWidth(),
                if (ui.coins) ButtonStyle.Gold else ButtonStyle.Primary,
                enabled = ui.accept.enabled,
            )
            GameButton(ui.decline, { onCommand(UiCommand.ReviveDecline) }, Modifier.fillMaxWidth().padding(top = 10.dp), ButtonStyle.Secondary)
        }
    }
}
