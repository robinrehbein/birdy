package de.robinrehbein.birdy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.robinrehbein.birdy.game.GameOverUi
import de.robinrehbein.birdy.game.UiCommand
import de.robinrehbein.birdy.meta.Strings
import de.robinrehbein.birdy.ui.BirdyColors
import de.robinrehbein.birdy.ui.ButtonStyle
import de.robinrehbein.birdy.ui.Heading
import de.robinrehbein.birdy.ui.StatsGrid
import de.robinrehbein.birdy.ui.displayFont
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import de.robinrehbein.birdy.ui.GameButton
import de.robinrehbein.birdy.ui.GameIcon
import de.robinrehbein.birdy.ui.OutlinedText
import de.robinrehbein.birdy.ui.Panel
import de.robinrehbein.birdy.ui.ProgressBar
import de.robinrehbein.birdy.ui.RichText
import de.robinrehbein.birdy.ui.Scrim

/**
 * Game-over screen (`#gameover`, main-b.md §5). Tapping anywhere outside "Menü" and the
 * ready next-unlock bar retries after the 350ms debounce (owned by the game thread; this UI
 * just always emits [UiCommand.GameOverTap] and trusts the sim to apply the debounce/short-circuit
 * order from §5/§20.8).
 */
@Composable
fun GameOverScreen(
    ui: GameOverUi,
    walletCoins: Int,
    walletBump: Int,
    strings: Strings,
    shortScreenHideWallet: Boolean,
    onCommand: (UiCommand) -> Unit,
    modifier: Modifier = Modifier,
) {
    val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Box(
        modifier.fillMaxSize().clickable(source, indication = null) { onCommand(UiCommand.GameOverTap) },
        contentAlignment = Alignment.Center,
    ) {
        Scrim(Modifier.fillMaxSize())

        Panel(Modifier.padding(16.dp)) {
            // `#gameover h2` shrinks to 38px on short screens (@media max-height 720px).
            Heading(strings.t("gameOver"), Modifier.padding(bottom = if (shortScreenHideWallet) 10.dp else 16.dp), size = if (shortScreenHideWallet) 38.sp else 48.sp)
            StatsGrid(
                listOf(
                    strings.t("score") to ui.score.toString(),
                    strings.t("coins") to ui.coins.toString(),
                    strings.t("best") to ui.best.toString(),
                ),
                Modifier.padding(bottom = 16.dp),
                valueSize = if (shortScreenHideWallet) 26.sp else 32.sp,
            )
            if (ui.newBest) {
                BasicText(strings.t("newBest"), Modifier.padding(bottom = 14.dp), style = TextStyle(fontFamily = displayFont(), fontSize = 22.sp, color = BirdyColors.Orange))
            }
            if (ui.toBest != null) {
                BasicText(ui.toBest, Modifier.padding(bottom = 12.dp), style = TextStyle(fontFamily = displayFont(), fontSize = 18.sp, color = BirdyColors.Ink, textAlign = TextAlign.Center))
            }
            if (ui.zoneReached != null) {
                RichText(ui.zoneReached, Modifier.padding(bottom = 8.dp), size = 16.sp, color = BirdyColors.Orange, font = displayFont(), center = true)
            }
            if (ui.nextUnlock != null) {
                NextUnlockBar(ui.nextUnlock.text, ui.nextUnlock.percent, ui.nextUnlock.ready) { onCommand(UiCommand.NextUnlock) }
            }
            Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                for ((line, reward) in ui.achievementLines) AchievementLine(line, reward)
                for (m in ui.missions) MissionRow(m)
            }
            GameButton(strings.t("again"), { onCommand(UiCommand.Restart) }, Modifier.fillMaxWidth())
            GameButton(strings.t("menu"), { onCommand(UiCommand.GoToMenu) }, Modifier.fillMaxWidth().padding(top = 10.dp), ButtonStyle.Secondary)
        }
    }
}

/** `.mission.done.achievement`: a gold mission card with the done check, white ink-shadowed text and the reward. */
@Composable
private fun AchievementLine(line: String, reward: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .background(BirdyColors.Gold, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GameIcon("check", size = 17.dp, modifier = Modifier.padding(end = 4.dp))
        RichText(line, Modifier.weight(1f).padding(end = 8.dp), size = 14.sp, color = BirdyColors.White, outline = BirdyColors.Ink, outlineWidth = 0.dp, shadowDown = 1.dp)
        OutlinedText("+$reward", size = 15.sp, thickness = 0.dp, down = 2.dp)
    }
}

@Composable
private fun NextUnlockBar(text: String, percent: Int, ready: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .background(BirdyColors.Gold, RoundedCornerShape(10.dp))
            .clickable(enabled = ready, onClick = onClick)
            .padding(10.dp, 6.dp),
    ) {
        // `.next-unlock`: gold card, white 15px text with a `0 2px 0` shadow, white bar on a plum track.
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            RichText(text, size = 15.sp, color = BirdyColors.White, outline = BirdyColors.Ink, outlineWidth = 0.dp, shadowDown = 2.dp, font = displayFont(), center = true)
            ProgressBar(percent, Modifier.fillMaxWidth().padding(top = 4.dp), height = 8.dp, fill = BirdyColors.White, track = BirdyColors.Ink.copy(alpha = 0.3f))
        }
    }
}
