package de.robinrehbein.birdy.view.fx

import de.robinrehbein.birdy.game.PowerType
import de.robinrehbein.birdy.meta.SkinItem
import de.robinrehbein.birdy.meta.TrailItem

/** Every particle burst main.js fires (bird-fx.md §4.1 caller table), as [EmitOptions]. */
object Bursts {
    /** main.js:522 menu confetti (big unlock / surprise). */
    val confetti = EmitOptions(count = 40, colors = listOf(0xff5a8a, 0x5ad1ff, 0xffd84a, 0x7be07b, 0xffffff), speed = 7.0, size = 0.13, life = 0.9, gravity = -4.0)

    /** main.js:573 skin bought/equipped in the shop. */
    fun skin(skin: SkinItem) = EmitOptions(count = 20, colors = listOf(skin.body, 0xffffff, 0xfff176), speed = 5.0, size = 0.1, life = 0.6, gravity = -3.0)

    /** main.js:595 power-up upgrade bought ([color] = the power-up colour, 0x7be07b fallback). */
    fun upgrade(color: Int) = EmitOptions(count = 30, colors = listOf(color, 0xffffff, 0xfff176), speed = 6.0, size = 0.12, life = 0.8, gravity = -4.0)

    /** main.js:607 item bought; [colors] = the item's colours. */
    fun item(colors: List<Int>) = EmitOptions(count = 30, colors = colors + listOf(0xffffff, 0xfff176), speed = 6.0, size = 0.12, life = 0.8, gravity = -4.0)

    /** main.js:676 mission/achievement celebration in the menu. */
    val celebrate = EmitOptions(count = 36, colors = listOf(0xfff176, 0xffd400, 0xffffff), speed = 7.0, size = 0.13, life = 0.9, gravity = -5.0)

    /** main.js:1409 feathers in the bird's own colours on death. */
    fun death(skin: SkinItem) = EmitOptions(count = 36, colors = listOf(skin.body, skin.body, skin.belly, skin.wing, 0xffffff), speed = 9.0, size = 0.15, life = 1.3, gravity = -8.0)

    /** main.js:1531 near miss. */
    val nearMiss = EmitOptions(count = 10, colors = listOf(0xffffff, 0xfff176), speed = 5.0, size = 0.09, life = 0.4, gravity = 0.0)

    /** main.js:1584 power-up picked up. */
    fun powerUp(type: PowerType) = EmitOptions(count = 24, colors = listOf(type.color, 0xffffff), speed = 7.0, size = 0.13, life = 0.6, gravity = 0.0)

    /** main.js:1756 coin collected. */
    val coin = EmitOptions(count = 8, colors = listOf(0xfff176, 0xffd400, 0xffffff), speed = 5.0, size = 0.1, life = 0.45, gravity = 0.0)

    /** main.js:1782 rainbow trail while the star is active (colour set per frame). */
    val starTrail = EmitOptions(count = 2, speed = 0.8, size = 0.18, life = 0.5, gravity = 0.0)

    /** main.js:1848 one particle of a cosmetic flight trail. */
    fun trail(trail: TrailItem, drift: Double) = EmitOptions(
        count = 1, colors = trail.colors, speed = trail.speed.exact(), size = trail.size.exact() * 2,
        life = trail.life.exact() * 1.2, gravity = trail.gravity.exact(), drag = 1.0, drift = drift,
    )

    /** The catalog stores trail tuning as Float; recover the JS decimal literal. */
    private fun Float.exact(): Double = toString().toDouble()
}
