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
import androidx.compose.ui.graphics.drawscope.rotate
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
import de.robinrehbein.birdy.ui.screens.ReviveScreen
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
                    state.revive?.let { ReviveScreen(it, onCommand) }
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
            if (state.recordFx < 1f) RecordCelebration(state.recordFx)
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

/**
 * New-record celebration in screen space: a golden glow along the edges and two party poppers
 * shooting confetti up from the bottom corners. Everything stays in the outer quarter of the
 * screen, so the bird and the rows ahead remain in clear view. [t] runs 0..1.
 */
@Composable
private fun RecordCelebration(t: Float) {
    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
        val fade = 1f - t
        val edge = androidx.compose.ui.graphics.Color(0xFFFFC400).copy(alpha = 0.8f * fade * fade)
        val clear = edge.copy(alpha = 0f)
        val band = size.minDimension * 0.16f
        val v = androidx.compose.ui.graphics.Brush
        drawRect(v.verticalGradient(listOf(edge, clear), startY = 0f, endY = band))
        drawRect(v.verticalGradient(listOf(clear, edge), startY = size.height - band, endY = size.height))
        drawRect(v.horizontalGradient(listOf(edge, clear), startX = 0f, endX = band))
        drawRect(v.horizontalGradient(listOf(clear, edge), startX = size.width - band, endX = size.width))
        // Confetti: fixed pseudo-random pieces, ballistic from each bottom corner, kept to the
        // outer 28 % of the width; they fade out over the last third.
        val w = size.width
        val h = size.height
        val alpha = if (t < 0.66f) 1f else (1f - t) / 0.34f
        for (side in 0..1) {
            for (i in 0 until CONFETTI_PIECES) {
                val r1 = hash(i * 7 + side * 131 + 1)
                val r2 = hash(i * 13 + side * 71 + 5)
                val r3 = hash(i * 29 + side * 17 + 9)
                val vx = (0.05f + 0.20f * r1) * w // inward
                val vy = (0.75f + 0.55f * r2) * h // upward
                val g = 1.6f * h
                val x0 = vx * t
                val y = h - (vy * t - 0.5f * g * t * t)
                val x = (if (side == 0) x0 else w - x0).coerceIn(0f, w)
                if (x0 > w * 0.28f || y > h) continue
                val s = size.minDimension * (0.012f + 0.012f * r3)
                val col = CONFETTI_COLORS[(i + side) % CONFETTI_COLORS.size]
                rotate(degrees = 720f * t * (if (r3 > 0.5f) 1f else -1f) + 90f * r1, pivot = androidx.compose.ui.geometry.Offset(x, y)) {
                    drawRect(
                        androidx.compose.ui.graphics.Color(col).copy(alpha = alpha),
                        topLeft = androidx.compose.ui.geometry.Offset(x - s, y - s * 0.5f),
                        size = androidx.compose.ui.geometry.Size(2 * s, s),
                    )
                }
            }
        }
    }
}

private const val CONFETTI_PIECES = 26
private val CONFETTI_COLORS = longArrayOf(0xFFFFD400, 0xFFFF7A00, 0xFFFF5A8A, 0xFF5AD1FF, 0xFF7BE07B, 0xFFFFFFFF)

/** Deterministic 0..1 noise for the confetti layout (no per-frame randomness). */
private fun hash(n: Int): Float {
    var x = n * 374761393 + 668265263
    x = (x xor (x ushr 13)) * 1274126177
    return ((x xor (x ushr 16)) and 0xffff) / 65535f
}
