package de.robinrehbein.birdy.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import de.robinrehbein.birdy.meta.Lang
import de.robinrehbein.birdy.game.GameMode
import de.robinrehbein.birdy.game.Menu
import de.robinrehbein.birdy.game.UiCommand
import de.robinrehbein.birdy.game.UiState
import de.robinrehbein.birdy.meta.Strings
import de.robinrehbein.birdy.ui.screens.AchievementsScreen
import de.robinrehbein.birdy.ui.screens.GameOverScreen
import de.robinrehbein.birdy.ui.screens.HudScreen
import de.robinrehbein.birdy.ui.screens.PauseScreen
import de.robinrehbein.birdy.ui.screens.PopupOverlay
import de.robinrehbein.birdy.ui.screens.ShopScreen
import de.robinrehbein.birdy.ui.screens.StartMenuScreen
import de.robinrehbein.birdy.ui.screens.TapFxOverlay
import de.robinrehbein.birdy.ui.screens.ToastOverlay
import de.robinrehbein.birdy.ui.screens.TutorialHandOverlay
import de.robinrehbein.birdy.ui.screens.ZoneBannerOverlay
import de.robinrehbein.birdy.ui.screens.ZoneHintOverlay

/** Short-screen rule (main-b.md §20.11): `@media (max-height:720px)` hides the wallet on game-over.
 * 720 CSS px maps to ~436dp at the density that CSS px assumed (mdpi baseline, matching the JS media
 * query's raw px number to Compose dp 1:1, since the query is about physical screen size not scale). */
private const val SHORT_SCREEN_MAX_HEIGHT_DP = 720

/**
 * Root of the Compose overlay drawn above the 3D surface (Android) or composited over the
 * offscreen 3D render (desktop screenshots). Stateless: renders [state], reports input via
 * [onCommand]. Touches not consumed by a widget fall through as [UiCommand.Touch]/[UiCommand.Swipe].
 */
@Composable
fun BirdyApp(state: UiState, strings: Strings, onCommand: (UiCommand) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val sizeOf = DpSize(maxWidth, maxHeight)
        val shortScreen = maxHeight.value < SHORT_SCREEN_MAX_HEIGHT_DP

        Box(
            Modifier.fillMaxSize().pointerInput(Unit) {
                // Canvas pointerdown/move/up: touch-down flaps (position does not matter), the
                // sideways-swipe detector runs in the game (InputMapper). Widgets consume their own touches before this sees them.
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val w = size.width.toFloat().coerceAtLeast(1f)
                    val h = size.height.toFloat().coerceAtLeast(1f)
                    onCommand(UiCommand.Touch(down.position.x / w, down.position.y / h))
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        if (change.positionChanged()) {
                            onCommand(UiCommand.TouchMove(change.position.x / w, change.position.y / h))
                        }
                    }
                    onCommand(UiCommand.TouchUp)
                }
            },
        ) {
            when (state.mode) {
                GameMode.Ready -> when (state.menu) {
                    Menu.Start -> StartMenuScreen(state.start, state.progress.coins, state.walletBump, strings, shortScreen, onCommand)
                    Menu.Shop -> state.shop?.let { ShopScreen(it, strings, onCommand, thumbnails = state.thumbnails) }
                    Menu.Achievements -> AchievementsScreen(state.achievements, strings, onCommand)
                }
                GameMode.Playing, GameMode.Dead -> {
                    HudScreen(state, strings)
                    ZoneHintOverlay(state.zonesHint, sizeOf)
                    TutorialHandOverlay(state.tutorialHand, sizeOf)
                    PopupOverlay(state.popup, sizeOf)
                    ZoneBannerOverlay(state.zoneBanner)
                    TapFxOverlay(state.tapFx, sizeOf)
                    if (state.paused) PauseScreen(state, strings, onCommand)
                }
                GameMode.Over -> state.gameOverUi?.let {
                    GameOverScreen(it, state.progress.coins, state.walletBump, strings, shortScreen, onCommand)
                }
            }
            TopButtons(state, strings, onCommand)
            // `#wallet`: shown outside runs; on game over only when the panel leaves room.
            val walletVisible = when (state.mode) {
                GameMode.Ready -> true
                GameMode.Over -> !shortScreen && state.gameOverUi?.walletOver != false
                else -> false
            }
            if (walletVisible) {
                CoinChip(
                    state.progress.coins,
                    state.walletBump,
                    Modifier
                        .align(Alignment.TopEnd)
                        .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                        .padding(top = 20.dp, end = 16.dp),
                )
            }
            ToastOverlay(state.toast)
            state.fpsText?.let { FpsOverlay(it, Modifier.align(Alignment.BottomStart)) }
            if (state.flashAlpha > 0f) {
                Box(
                    Modifier.fillMaxSize().then(
                        Modifier.background(state.flashColor.toComposeColor(alpha = state.flashAlpha)),
                    ),
                )
            }
        }
    }
}

/**
 * `#mute` (always) and `#lang-btn` (hidden during a run) in the top-left corner (style.css
 * `#mute`, `#lang-btn`): tan chips with an ink border and a 3 px lip.
 */
@Composable
private fun TopButtons(state: UiState, strings: Strings, onCommand: (UiCommand) -> Unit) {
    androidx.compose.foundation.layout.Row(
        Modifier.windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)).padding(start = 14.dp, top = 16.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
    ) {
        CornerChip(onClick = { onCommand(UiCommand.SetMuted(!state.muted)) }) {
            GameIcon(if (state.muted) "mute" else "sound", size = 26.dp)
        }
        val inRun = state.mode == GameMode.Playing || state.mode == GameMode.Dead
        if (!inRun) {
            CornerChip(onClick = { onCommand(UiCommand.SetReminders(!state.remindersOn)) }) {
                // The gift icon doubles as the "daily reminder" symbol; dimmed when switched off.
                GameIcon("gift", size = 26.dp, modifier = Modifier.alpha(if (state.remindersOn) 1f else 0.35f))
            }
            CornerChip(onClick = {
                onCommand(UiCommand.SetLang(if (state.lang == Lang.DE) Lang.EN else Lang.DE))
            }) {
                androidx.compose.foundation.text.BasicText(
                    strings.t("lang"),
                    style = androidx.compose.ui.text.TextStyle(
                        fontFamily = displayFont(),
                        fontSize = 15.sp,
                        color = BirdyColors.Ink,
                    ),
                )
            }
        }
    }
}

@Composable
private fun CornerChip(onClick: () -> Unit, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier
            .height(44.dp)
            .widthIn(min = 44.dp)
            .clickable(onClick = onClick),
    ) {
        Box(Modifier.matchParentSize().offset(y = 3.dp).background(BirdyColors.Ink, shape))
        Box(
            Modifier
                .matchParentSize()
                .background(BirdyColors.Panel.copy(alpha = 0.9f), shape)
                .border(3.dp, BirdyColors.Ink, shape),
        )
        Box(Modifier.align(Alignment.Center).padding(horizontal = 10.dp)) { content() }
    }
}

/** Hidden developer overlay (`#fps`): bottom-left, monospace, not touchable. */
@Composable
private fun FpsOverlay(text: String, modifier: Modifier) {
    androidx.compose.foundation.text.BasicText(
        text,
        modifier
            .padding(8.dp)
            .background(androidx.compose.ui.graphics.Color(0x80000000), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        style = androidx.compose.ui.text.TextStyle(
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            fontSize = 11.sp,
            color = BirdyColors.White,
        ),
    )
}
