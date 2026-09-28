package de.robinrehbein.birdy.game.bot

/** bot.js `SKILLS`: reaction time, aim noise, look-ahead and mistakes per player type. */
data class BotSkill(
    val name: String,
    val plan2: Boolean,
    val delay: Double,
    val anticipate: Double,
    val interval: Double,
    val noise: Double,
    val lookAhead: Double,
    val mistake: Double,
    val predict: Boolean,
    val lateSwitch: Boolean,
    val apexCheck: Double,
) {
    companion object {
        /** First-time player: sees rows late, aims sloppily, sometimes picks badly. */
        val NOVICE = BotSkill("novice", plan2 = false, delay = 0.22, anticipate = 0.6, interval = 0.2, noise = 0.9, lookAhead = 30.0, mistake = 0.12, predict = false, lateSwitch = false, apexCheck = 0.4)
        /** Casual player after a few runs. */
        val GOOD = BotSkill("good", plan2 = false, delay = 0.15, anticipate = 0.9, interval = 0.12, noise = 0.45, lookAhead = 45.0, mistake = 0.04, predict = false, lateSwitch = true, apexCheck = 0.85)
        /** Near-perfect play: used to detect rows that are impossible (unfair). */
        val PRO = BotSkill("pro", plan2 = true, delay = 0.0, anticipate = 1.0, interval = 1.0 / 60, noise = 0.0, lookAhead = 80.0, mistake = 0.0, predict = true, lateSwitch = true, apexCheck = 1.0)

        val ALL = listOf(NOVICE, GOOD, PRO)

        fun byName(name: String): BotSkill = ALL.first { it.name == name }
    }
}

/** bot.js constants (GRAVITY, FLAP, HOP, BPM). */
object BotConst {
    const val GRAVITY = 36.0
    const val FLAP = 11.5
    const val HOP = 6.0
    const val BPM = 124.0

    /** The bot's OWN linear plant rise (bot.js:19-25); deliberately not world.js's smoothstep. */
    fun plantRise(beat: Double): Double {
        val p = ((beat % 4) + 4) % 4
        if (p < 2) return 0.0
        if (p < 2.4) return (p - 2) / 0.4
        if (p < 3.9) return 1.0
        return 0.0
    }
}
