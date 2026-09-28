package de.robinrehbein.birdy.ui.screens

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.robinrehbein.birdy.game.MissionUi
import de.robinrehbein.birdy.game.StartMenuUi
import de.robinrehbein.birdy.game.UiCommand
import de.robinrehbein.birdy.meta.Strings
import de.robinrehbein.birdy.ui.BirdyColors
import de.robinrehbein.birdy.ui.ButtonStyle
import de.robinrehbein.birdy.ui.CoinChip
import de.robinrehbein.birdy.ui.GameButton
import de.robinrehbein.birdy.ui.GameIcon
import de.robinrehbein.birdy.ui.OutlinedText
import de.robinrehbein.birdy.ui.Panel
import de.robinrehbein.birdy.ui.ProgressBar
import de.robinrehbein.birdy.ui.RichText
import de.robinrehbein.birdy.ui.TextLink
import de.robinrehbein.birdy.ui.bodyFont
import de.robinrehbein.birdy.ui.displayFont

/** index.html `.privacy-link` target. */
const val PRIVACY_URL = "https://robinrehbein.github.io/birdy/privacy/"

/** Start menu (`#start`, main-b.md §6): title, best, how-to/missions, gift/streak, main buttons. */
@Composable
fun StartMenuScreen(
    ui: StartMenuUi,
    walletCoins: Int,
    walletBump: Int,
    strings: Strings,
    shortScreen: Boolean,
    onCommand: (UiCommand) -> Unit,
    modifier: Modifier = Modifier,
) {
    val frame = de.robinrehbein.birdy.ui.rememberMenuFrameReporter(de.robinrehbein.birdy.game.Menu.Start, onCommand)
    Box(
        modifier
            .fillMaxSize()
            .then(frame.root)
            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Top))
            .clickable(
                indication = null,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
            ) { onCommand(UiCommand.Play) },
    ) {
        Column(
            Modifier.align(Alignment.TopCenter).padding(top = 60.dp).then(frame.title),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // `#start h1` pointerdown: counts toward the 5-tap dev overlay and still starts the run.
            TitleHeading(
                Modifier.pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        onCommand(UiCommand.TitleTap)
                    }
                },
            )
            OutlinedText("${strings.t("best")}: ${ui.best}", size = 20.sp, thickness = 2.dp, down = 2.dp)
        }

        Panel(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 16.dp, end = 16.dp, bottom = 20.dp)
                .then(frame.panel)
                .clickable(
                    indication = null,
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                ) {}, // swallow taps inside the panel so it doesn't also trigger Play
        ) {
            if (ui.firstRuns) {
                RichText(strings.t("howto"), size = 15.sp, font = bodyFont())
                RichText(strings.t("keys"), size = 12.sp)
                RichText(strings.t("legend"), size = 13.sp)
            }
            if (ui.giftLabel != null) {
                GameButton(ui.giftLabel, { onCommand(UiCommand.ClaimGift) }, Modifier.fillMaxWidth().padding(bottom = 12.dp), ButtonStyle.Gift)
            } else if (ui.streakLabel != null) {
                RichText(ui.streakLabel, Modifier.fillMaxWidth().padding(bottom = 10.dp), size = 16.sp, color = BirdyColors.Orange, font = displayFont(), center = true)
            }
            if (!ui.firstRuns) MissionsBlock(ui.missions, strings)
            GameButton(strings.t("play"), { onCommand(UiCommand.Play) }, Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GameButton(strings.t("shop"), { onCommand(UiCommand.OpenShop(true)) }, Modifier.weight(1f), ButtonStyle.Secondary)
                GameButton(strings.t("achievements"), { onCommand(UiCommand.OpenAchievements(true)) }, Modifier.weight(1f), ButtonStyle.Secondary)
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.Center) {
                val uri = LocalUriHandler.current
                TextLink(strings.t("privacy"), { runCatching { uri.openUri(PRIVACY_URL) } })
                if (ui.adPrivacy) {
                    Box(Modifier.padding(start = 12.dp)) { TextLink(strings.t("adPrivacy"), { onCommand(UiCommand.AdPrivacy) }) }
                }
            }
        }
    }
}

/** `.menu-title h1`: "Bir" gold + "dy" green, 56px, `0 4px / 3px` ink outline. */
@Composable
private fun TitleHeading(modifier: Modifier = Modifier) {
    OutlinedText(
        buildAnnotatedString {
            withStyle(SpanStyle(color = BirdyColors.Gold)) { append("Bir") }
            withStyle(SpanStyle(color = BirdyColors.Green)) { append("dy") }
        },
        modifier.padding(bottom = 4.dp),
        size = 56.sp,
        thickness = 3.dp,
        down = 4.dp,
    )
}

@Composable
private fun MissionsBlock(missions: List<MissionUi>, strings: Strings) {
    // `.missions`: centred orange h3, rows 6px apart, 14px below the block.
    Column(Modifier.fillMaxWidth().padding(bottom = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        androidx.compose.foundation.text.BasicText(
            strings.t("missions"),
            Modifier.padding(bottom = 3.dp),
            style = androidx.compose.ui.text.TextStyle(fontFamily = displayFont(), fontSize = 18.sp, color = BirdyColors.Orange),
        )
        for (m in missions) MissionRow(m)
    }
}

@Composable
internal fun MissionRow(m: MissionUi, modifier: Modifier = Modifier) {
    // `.mission`: inset card, text + reward on one row, the progress bar spanning below it.
    Column(
        modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .background(if (m.done) BirdyColors.MissionDone else BirdyColors.PanelInset, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (m.done) GameIcon("check", size = 16.dp)
            RichText(m.text, Modifier.weight(1f).padding(end = 8.dp), size = 14.sp)
            OutlinedText("+${m.reward}", size = 15.sp, thickness = 0.dp, down = 2.dp)
        }
        ProgressBar(m.percent, Modifier.fillMaxWidth())
    }
}
