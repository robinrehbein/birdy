package de.robinrehbein.birdy.game.loop

import de.robinrehbein.birdy.game.GameMode
import de.robinrehbein.birdy.game.Menu

/** What the Android back button does (main.js `handleBack`, main-b.md §3). */
enum class BackAction { Pause, ToMenu, CloseShop, CloseAchievements, Exit }

object MenuFlow {
    fun backAction(mode: GameMode, paused: Boolean, menu: Menu): BackAction = when {
        mode == GameMode.Playing && !paused -> BackAction.Pause
        paused || mode == GameMode.Over || mode == GameMode.Dead -> BackAction.ToMenu
        menu == Menu.Shop -> BackAction.CloseShop
        menu == Menu.Achievements -> BackAction.CloseAchievements
        else -> BackAction.Exit
    }
}

/** Hidden developer overlay gesture: 5 title taps, each within 400 ms of the previous one. */
class SecretTaps(private val count: Int = 5, private val windowMs: Double = 400.0) {
    private var taps = 0
    private var last = Double.NEGATIVE_INFINITY

    /** Returns true when the gesture completed. */
    fun tap(nowMs: Double): Boolean {
        taps = if (nowMs - last < windowMs) taps + 1 else 1
        last = nowMs
        return taps >= count
    }
}
