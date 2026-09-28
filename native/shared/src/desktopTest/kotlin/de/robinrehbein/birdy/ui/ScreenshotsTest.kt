package de.robinrehbein.birdy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import de.robinrehbein.birdy.game.AchievementUi
import de.robinrehbein.birdy.game.AchievementsUi
import de.robinrehbein.birdy.game.GameMode
import de.robinrehbein.birdy.game.GameOverUi
import de.robinrehbein.birdy.game.Menu
import de.robinrehbein.birdy.game.MissionUi
import de.robinrehbein.birdy.game.NextUnlockUi
import de.robinrehbein.birdy.game.PowerType
import de.robinrehbein.birdy.game.ShopTab
import de.robinrehbein.birdy.game.ShopTileUi
import de.robinrehbein.birdy.game.ShopUi
import de.robinrehbein.birdy.game.StartMenuUi
import de.robinrehbein.birdy.game.UiState
import de.robinrehbein.birdy.game.ButtonUi
import de.robinrehbein.birdy.meta.Kind
import de.robinrehbein.birdy.meta.Lang
import de.robinrehbein.birdy.meta.ProgressData
import de.robinrehbein.birdy.meta.TableStrings
import de.robinrehbein.birdy.platform.MemoryKeyValueStore
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

/**
 * Renders every screen this task owns headless (Skia raster) at phone resolution, for DE and EN,
 * so they can be eyeballed with the Read tool (main-b UI acceptance criteria). Writes PNGs to
 * `native/build/uitest/` as PNGs; does not assert pixels (that's the JS-diff step, done by hand).
 */
class ScreenshotsTest {
    private val width = 1080
    private val height = 2400
    private val density = 2.625f
    private val outDir = File("build/uitest").apply { mkdirs() }

    private fun strings(lang: Lang): TableStrings {
        val store = MemoryKeyValueStore()
        val s = TableStrings(store, if (lang == Lang.DE) "de" else "en")
        s.setLang(lang)
        return s
    }

    private fun shoot(name: String, state: UiState) {
        check(skiaAvailable()) { "Skia/skiko runtime missing from desktopTest" }
        for (lang in Lang.entries) {
            val s = strings(lang)
            val image = ImageComposeScene(width, height, Density(density)) {
                Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFF4EC0CA))) {
                    BirdyApp(state.copy(lang = lang), s) {}
                }
            }.use { scene ->
                scene.render(0L)
                scene.render(16_000_000L)
            }
            val png = image.encodeToData(EncodedImageFormat.PNG) ?: error("encode failed")
            File(outDir, "$name-${lang.code}.png").writeBytes(png.bytes)
        }
    }

    @Test
    fun startMenuFirstRun() = shoot(
        "start-firstrun",
        UiState(mode = GameMode.Ready, menu = Menu.Start, start = StartMenuUi(best = 0, firstRuns = true)),
    )

    @Test
    fun startMenuWithMissionsAndGift() = shoot(
        "start-missions-gift",
        UiState(
            mode = GameMode.Ready,
            menu = Menu.Start,
            progress = ProgressData(coins = 420),
            start = StartMenuUi(
                best = 87,
                firstRuns = false,
                missions = listOf(
                    MissionUi("m1", "Sammle 20 Muenzen", 10, progress = 14, goal = 20, done = false),
                    MissionUi("m2", "Erreiche Zone 3", 15, progress = 3, goal = 3, done = true),
                ),
                giftLabel = "[gift] Tagesgeschenk · +40",
            ),
        ),
    )

    @Test
    fun hudPlaying() = shoot(
        "hud-playing",
        UiState(
            mode = GameMode.Playing,
            score = 42,
            runCoins = 7,
            lane = 2,
            powerActive = listOf(true, false, false),
            power = listOf(0.6f, 0f, 0f),
            progress = ProgressData(coins = 420),
        ),
    )

    @Test
    fun pauseScreen() = shoot(
        "pause",
        UiState(mode = GameMode.Playing, paused = true, score = 128, runCoins = 12, zone = 2),
    )

    @Test
    fun gameOverScreen() = shoot(
        "gameover",
        UiState(
            mode = GameMode.Over,
            progress = ProgressData(coins = 300),
            gameOverUi = GameOverUi(
                score = 99,
                coins = 15,
                best = 120,
                newBest = false,
                toBest = "Nur noch 22 bis zum Rekord!",
                achievementLines = listOf("[trophy] Erfolg: Frühstarter" to 20),
                missions = listOf(MissionUi("m1", "Sammle 20 Muenzen", 10, progress = 20, goal = 20, done = true)),
                zoneReached = "Zone 2 erreicht: Wiese",
                nextUnlock = NextUnlockUi(Kind.Skin, "sky", "Noch 50 [coin] bis Farbe „Himmel“", percent = 60, ready = false),
            ),
        ),
    )

    @Test
    fun shopSkinsTab() = shoot(
        "shop-skins",
        UiState(
            mode = GameMode.Ready,
            menu = Menu.Shop,
            progress = ProgressData(coins = 250),
            shop = ShopUi(
                tab = ShopTab.Skin,
                selectedId = "sunny",
                tiles = listOf(
                    ShopTileUi("sunny", "Sunny", locked = false, selected = true, equipped = true, rare = false, price = 0),
                    ShopTileUi("sky", "Sky", locked = false, selected = false, equipped = false, rare = false, price = 100),
                    ShopTileUi("cardinal", "Cardinal", locked = true, selected = false, equipped = false, rare = false, price = 250),
                    ShopTileUi("gold", "Gold", locked = true, selected = false, equipped = false, rare = true, price = 2000),
                ),
                name = "Sunny",
                desc = "",
                action = ButtonUi("Ausgewählt", enabled = false),
                actionIsBuy = false,
                diceEnabled = true,
                surprise = ButtonUi("[gift] Überraschung · 150"),
                rewardAd = ButtonUi("▶ Anzeige ansehen · +30 Münzen (2/3 heute)"),
                stylePass = null,
                adPrivacy = false,
                realBuy = null,
                realBuyProductId = null,
                billingBusy = false,
            ),
        ),
    )

    @Test
    fun achievementsScreen() = shoot(
        "achievements",
        UiState(
            mode = GameMode.Ready,
            menu = Menu.Achievements,
            achievements = AchievementsUi(
                list = listOf(
                    AchievementUi("a1", "star", "Frühstarter", "Erreiche 50 Punkte", null, value = 50, goal = 50, reward = 20, done = true),
                    AchievementUi("a2", "trophy", "Sammler", "Besitze 5 Skins", null, value = 2, goal = 5, reward = 40, done = false),
                ),
            ),
        ),
    )
}

private fun skiaAvailable(): Boolean = try {
    androidx.compose.ui.graphics.Path()
    true
} catch (t: Throwable) {
    false
}
