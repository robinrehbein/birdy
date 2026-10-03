package de.robinrehbein.birdy.shots

import de.robinrehbein.birdy.game.GameMode
import de.robinrehbein.birdy.game.GapSpec
import de.robinrehbein.birdy.game.PowerType
import de.robinrehbein.birdy.game.ShopTab
import de.robinrehbein.birdy.game.TutorialStep
import de.robinrehbein.birdy.game.UiCommand
import de.robinrehbein.birdy.meta.Kind
import kotlin.math.ceil

/**
 * A scripted session: one fresh deterministic [ShotGame] (same save, seed and fixed clock every
 * time) driven like scripts/store-shots.mjs; each [Capture.shot] writes `NN-name.png`.
 * The JS side (scripts/native-shots/js-reference.mjs) takes the same named states.
 */
class Session(
    val save: String = STORE_SAVE,
    val lang: String = "de",
    val store: Boolean = false,
    val script: Capture.(ShotGame) -> Unit,
)

/** Callback that renders the current state (3D + overlay) under [name]. */
fun interface Capture {
    fun shot(name: String)
}

/** A save wearing [skin] and [trail] (both owned). */
private fun wearing(skin: String, trail: String, hat: String = "none") =
    """{"coins":2400,"best":32,"runs":6,"tutorialDone":true,"achieved":["score10","score25"],""" +
        """"items":{"skin":["sunny","$skin"],"trail":["none","$trail"],"hat":["none","$hat"]},""" +
        """"equip":{"skin":"$skin","trail":"$trail","hat":"$hat"}}"""

object Shots {
    const val WIDTH = 1080
    // Play phone screenshots must be no taller than twice their width; 9:16 is recommended.
    const val HEIGHT = 1920
    /** 1080 physical pixels at devicePixelRatio 2.625 (about 412 CSS px wide). */
    const val DENSITY = 2.625f

    val sessions: List<Session> = listOf(
        // Menus.
        Session { g ->
            g.advance(1.5)
            shot("01-menu")
            g.post(UiCommand.OpenShop(true))
            g.post(UiCommand.SelectShopTab(ShopTab.Skin))
            g.post(UiCommand.ShopItem(Kind.Skin, "cardinal"))
            g.post(UiCommand.ShopAction)
            g.advance(2.5)
            shot("02-shop-skins")
            g.post(UiCommand.SelectShopTab(ShopTab.Hat))
            g.post(UiCommand.ShopItem(Kind.Hat, "crown"))
            g.advance(2.5)
            shot("03-shop-hats")
            g.post(UiCommand.SelectShopTab(ShopTab.World))
            g.post(UiCommand.ShopItem(Kind.World, "candy"))
            g.advance(3.0)
            shot("04-shop-worlds")
            g.post(UiCommand.SelectShopTab(ShopTab.Upgrade))
            g.advance(1.5)
            shot("05-shop-upgrades")
        },
        Session(store = true) { g ->
            g.post(UiCommand.OpenShop(true))
            g.post(UiCommand.SelectShopTab(ShopTab.Skin))
            g.post(UiCommand.ShopItem(Kind.Skin, "gold"))
            g.advance(2.5)
            shot("06-shop-store")
        },
        Session { g ->
            g.post(UiCommand.OpenAchievements(true))
            g.advance(2.0)
            shot("07-achievements")
        },
        // A run through the zones (store-shots: park, herbstwald, cactus, rainbow).
        Session { g ->
            g.startRun()
            g.run(9.0)
            shot("08-run-park")
            g.run(90.0) { g.state.zone >= 1 }
            g.run(3.0)
            shot("09-zone-2")
            g.run(90.0) { g.state.zone >= 2 }
            g.run(2.0)
            shot("10-zone-3")
            cactus(g)
            shot("11-cactus")
            g.run(90.0) { g.state.zone >= 3 }
            g.run(3.0)
            shot("12-zone-4")
            crash(g)
            g.advance(0.1)
            shot("16-crash")
            g.advance(1.5)
            shot("17-gameover")
        },
        // The golden record marker on the row that beats the previous best (best >= 5).
        Session(save = """{"best":5,"runs":6,"tutorialDone":true,"coins":120}""") { g ->
            g.startRun()
            g.run(1.0)
            // Normally the row with index == best; here simply the third row ahead.
            g.sim.gates.filter { it.active && !it.passed }.sortedByDescending { it.z }.getOrNull(2)?.record = true
            g.advance(0.2)
            shot("25-record-marker")
        },
        // Game over with a ready rewarded ad: the gold "+N coins (ad)" button.
        Session(store = true) { g ->
            g.startRun()
            g.run(8.0)
            g.state.coins = 17
            crash(g)
            g.advance(1.6)
            shot("26-gameover-x2")
        },
        Session { g ->
            g.startRun()
            g.run(5.0)
            g.sim.debug.activatePower(PowerType.Star)
            g.run(1.5)
            shot("13-power-star")
        },
        Session { g ->
            g.startRun()
            g.run(5.0)
            g.sim.debug.activatePower(PowerType.Magnet)
            g.run(1.5)
            shot("14-power-magnet")
        },
        Session { g ->
            g.startRun()
            g.run(5.0)
            g.sim.debug.activatePower(PowerType.Mini)
            g.run(1.5)
            shot("15-power-mini")
        },
        Session { g ->
            g.startRun()
            g.run(4.0)
            g.post(UiCommand.Back)
            g.advance(0.5)
            shot("18-pause")
        },
        // First launch: the guided tutorial run.
        Session(save = "{}") { g ->
            g.advance(1.0)
            shot("19-tutorial-flap")
            g.post(UiCommand.Touch(0.5f, 0.6f))
            g.post(UiCommand.TouchUp)
            var t = 0.0
            while (g.state.tutorialStep != TutorialStep.Switch && g.state.mode == GameMode.Playing && t < 30) {
                if (g.state.y < 5.2 && g.state.vy < 2) g.sim.flap()
                g.advance(1.0 / 30)
                t += 1.0 / 30
            }
            g.advance(0.6)
            shot("20-tutorial-dodge")
        },
        // Skins and trails in flight.
        Session(save = wearing("galaxy", "rainbow")) { g ->
            g.startRun()
            g.run(4.0)
            shot("21-skin-galaxy-rainbow")
        },
        Session(save = wearing("gold", "fire", hat = "crown")) { g ->
            g.startRun()
            g.run(4.0)
            shot("22-skin-gold-fire")
        },
        Session(save = wearing("diamond", "stardust", hat = "halo")) { g ->
            g.advance(1.5)
            g.post(UiCommand.OpenShop(true))
            g.post(UiCommand.SelectShopTab(ShopTab.Trail))
            g.advance(2.5)
            shot("23-shop-trail-diamond")
        },
        Session(lang = "en") { g ->
            g.advance(1.5)
            shot("24-menu-en")
        },
    )

    /** No god mode, dive into the ground. */
    private fun crash(g: ShotGame) {
        g.sim.debug.setGod(false)
        for (p in PowerType.entries) g.state.power[p.ordinal] = 0.0
        g.state.grace = 0.0
        var t = 0.0
        while (g.state.mode == GameMode.Playing && t < 10) {
            g.state.vy = -22.0
            g.advance(1.0 / 30)
            t += 1.0 / 30
        }
    }

    /** store-shots.mjs "kaktus": a real row with a fully risen cactus next to the bird. */
    private fun cactus(g: ShotGame) {
        val s = g.state
        val row = g.sim.gates.filter { it.active && !it.passed }.maxByOrNull { it.z }
        g.sim.debug.scriptedRow(
            -5.5,
            listOf(GapSpec(5.0, 5.0), GapSpec(5.4, 5.0, plant = true, plantOffset = 0.0), GapSpec(5.0, 5.0)),
            row,
        )
        s.lane = 0
        s.x = -3.0
        s.y = 5.2
        val bar = 240.0 / 124
        s.time = 2.95 * 60 / 124 + ceil(s.time / bar) * bar - 1.0 / 30
        g.advance(1.0 / 30)
    }
}
