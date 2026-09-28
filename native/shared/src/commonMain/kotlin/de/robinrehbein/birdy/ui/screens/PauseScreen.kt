package de.robinrehbein.birdy.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.robinrehbein.birdy.game.UiCommand
import de.robinrehbein.birdy.game.UiState
import de.robinrehbein.birdy.meta.Strings
import de.robinrehbein.birdy.ui.ButtonStyle
import de.robinrehbein.birdy.ui.GameButton
import de.robinrehbein.birdy.ui.Heading
import de.robinrehbein.birdy.ui.StatsGrid
import de.robinrehbein.birdy.ui.Panel
import de.robinrehbein.birdy.ui.Scrim

/**
 * Pause screen (`#pause`, main-b.md §4). Tapping anywhere except "Menü" resumes the run
 * (`main.js:1268-1272`) — the panel itself is a big tap target for [UiCommand.Resume].
 */
@Composable
fun PauseScreen(state: UiState, strings: Strings, onCommand: (UiCommand) -> Unit, modifier: Modifier = Modifier) {
    val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Box(
        modifier.fillMaxSize().clickable(source, indication = null) { onCommand(UiCommand.Resume) },
        contentAlignment = Alignment.Center,
    ) {
        Scrim(Modifier.fillMaxSize())
        Panel(Modifier.padding(16.dp)) {
            Heading(strings.t("pause"), Modifier.padding(bottom = 16.dp))
            StatsGrid(
                listOf(
                    strings.t("score") to state.score.toString(),
                    strings.t("coins") to state.runCoins.toString(),
                    strings.t("zoneLabel") to (state.zone + 1).toString(),
                ),
                Modifier.padding(bottom = 14.dp),
            )
            GameButton(strings.t("continue"), { onCommand(UiCommand.Resume) }, Modifier.fillMaxWidth())
            GameButton(
                strings.t("menu"),
                { onCommand(UiCommand.GoToMenu) },
                Modifier.fillMaxWidth().padding(top = 10.dp),
                ButtonStyle.Secondary,
            )
        }
    }
}
