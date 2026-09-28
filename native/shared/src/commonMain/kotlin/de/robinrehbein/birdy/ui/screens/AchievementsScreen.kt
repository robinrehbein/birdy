package de.robinrehbein.birdy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import de.robinrehbein.birdy.game.Menu
import de.robinrehbein.birdy.ui.MenuLayout
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.robinrehbein.birdy.game.AchievementUi
import de.robinrehbein.birdy.game.AchievementsUi
import de.robinrehbein.birdy.game.UiCommand
import de.robinrehbein.birdy.meta.Strings
import de.robinrehbein.birdy.ui.BirdyColors
import de.robinrehbein.birdy.ui.ButtonStyle
import de.robinrehbein.birdy.ui.GameButton
import de.robinrehbein.birdy.ui.GameIcon
import de.robinrehbein.birdy.ui.IconStyle
import de.robinrehbein.birdy.ui.OutlinedText
import de.robinrehbein.birdy.ui.Panel
import de.robinrehbein.birdy.ui.ProgressBar
import de.robinrehbein.birdy.ui.RichText

/** Achievements screen (`#achievements`, main-b.md §11.2): a scrollable list with progress bars. */
@Composable
fun AchievementsScreen(ui: AchievementsUi, strings: Strings, onCommand: (UiCommand) -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val listMax = maxHeight * 0.5f
        MenuLayout(
            menu = Menu.Achievements,
            onCommand = onCommand,
            title = {
                de.robinrehbein.birdy.ui.Heading(strings.t("achTitle"), Modifier.padding(bottom = 4.dp))
                OutlinedText(ui.countText, size = 20.sp, thickness = 2.dp, down = 2.dp)
            },
        ) { frame ->
            Panel(frame) {
                LazyColumn(Modifier.fillMaxWidth().padding(bottom = 8.dp).heightIn(max = listMax)) {
                    items(ui.list, key = { it.id }) { AchievementRow(it) }
                }
                GameButton(strings.t("back"), { onCommand(UiCommand.OpenAchievements(false)) }, Modifier.fillMaxWidth(), ButtonStyle.Secondary)
            }
        }
    }
}

@Composable
private fun AchievementRow(a: AchievementUi) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .background(if (a.done) BirdyColors.MissionDone else BirdyColors.PanelInset, RoundedCornerShape(10.dp))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (a.done) GameIcon(a.icon, size = 24.dp) else GameIcon("lock", size = 24.dp)
        Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
            OutlinedText(a.name, size = 15.sp, thickness = 1.dp)
            RichText(a.text, size = 12.sp)
            if (a.skinReward != null) RichText(a.skinReward, size = 12.sp, color = BirdyColors.RareTag)
            ProgressBar(a.percent, Modifier.fillMaxWidth().padding(top = 2.dp))
        }
        if (a.done) GameIcon("check", size = 20.dp) else OutlinedText("+${a.reward}", size = 14.sp, thickness = 1.dp)
    }
}
