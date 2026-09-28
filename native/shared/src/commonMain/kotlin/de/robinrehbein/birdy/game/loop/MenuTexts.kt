package de.robinrehbein.birdy.game.loop

import de.robinrehbein.birdy.game.AchievementUi
import de.robinrehbein.birdy.game.AchievementsUi
import de.robinrehbein.birdy.game.GameOverUi
import de.robinrehbein.birdy.game.MissionUi
import de.robinrehbein.birdy.game.NextUnlockUi
import de.robinrehbein.birdy.game.RunSummary
import de.robinrehbein.birdy.game.StartMenuUi
import de.robinrehbein.birdy.game.Tuning
import de.robinrehbein.birdy.game.jsRound
import de.robinrehbein.birdy.meta.Catalog
import de.robinrehbein.birdy.meta.KINDS_ORDER
import de.robinrehbein.birdy.meta.Kind
import de.robinrehbein.birdy.meta.LocalizedText
import de.robinrehbein.birdy.meta.Mission
import de.robinrehbein.birdy.meta.Missions
import de.robinrehbein.birdy.meta.ProgressRepository
import de.robinrehbein.birdy.meta.Strings
import de.robinrehbein.birdy.meta.WorldItem
import kotlin.math.min

/**
 * Builds the menu snapshots main.js renders into the DOM: `renderStart`, `missionHTML`,
 * `renderAchievements`, `renderNextUnlock` and the game-over panel of `showGameOver`.
 */
class MenuTexts(private val progress: ProgressRepository, private val strings: Strings) {
    private fun t(key: String, vararg params: Pair<String, Any?>) = strings.t(key, mapOf(*params))
    private fun L(text: LocalizedText) = text.get(strings.lang.value)

    fun missionText(m: Mission): String = L(Missions.byId(m.id).text(m.goal))

    fun mission(m: Mission, isNew: Boolean = false) =
        MissionUi(m.id, missionText(m), m.reward, m.progress, m.goal, m.done, isNew)

    /** `renderStart()`. */
    fun start(adPrivacy: Boolean): StartMenuUi {
        val d = progress.data.value
        // First runs explain the controls; afterwards the daily missions.
        val firstRuns = d.runs < 2
        // Daily gift from the second run on, so the first launch stays simple.
        val gift = !firstRuns && progress.giftAvailable()
        val streak = progress.streak
        val next = progress.giftAmount(streak + 1)
        return StartMenuUi(
            best = d.best,
            firstRuns = firstRuns,
            missions = progress.missions().map { mission(it) },
            giftLabel = if (gift) t("gift", "n" to next) else null,
            streakLabel = if (gift || streak == 0) null else t("streak", "d" to streak, "n" to next),
            adPrivacy = adPrivacy,
        )
    }

    /** `renderAchievements()`. */
    fun achievements(): AchievementsUi {
        val achieved = progress.data.value.achieved.toSet()
        return AchievementsUi(
            progress.achievements().map { (a, value) ->
                val skin = a.skin?.let { id -> Catalog.skins.firstOrNull { it.id == id } }
                AchievementUi(
                    id = a.id,
                    icon = a.icon,
                    name = L(a.name),
                    text = L(a.text),
                    skinReward = skin?.let { t("skinReward", "name" to L(it.name)) },
                    value = value,
                    goal = a.goal,
                    reward = a.reward,
                    done = a.id in achieved,
                )
            },
        )
    }

    /** `renderNextUnlock()`: the cheapest cosmetic not owned yet, across every kind. */
    fun nextUnlock(): NextUnlockUi? {
        val next = KINDS_ORDER.flatMap { kind -> Catalog.items(kind).filter { !progress.owns(kind, it.id) }.map { kind to it } }
            .sortedBy { it.second.price } // stable, like Array.prototype.sort
            .firstOrNull() ?: return null
        val (kind, item) = next
        val coins = progress.data.value.coins
        val kindName = t("kind_${kind.id}")
        val ready = coins >= item.price
        val pct = if (item.price == 0) 100 else min(100, jsRound(coins.toDouble() / item.price * 100))
        val text = if (ready) {
            t("unlockReady", "kind" to kindName, "name" to L(item.name))
        } else {
            t("unlockNext", "n" to item.price - coins, "kind" to kindName, "name" to L(item.name))
        }
        return NextUnlockUi(kind, item.id, text, if (ready) 100 else pct, ready)
    }

    /** The game-over panel (`showGameOver`, after `finishRun`). */
    fun gameOver(summary: RunSummary): GameOverUi {
        val result = summary.result
        val best = progress.data.value.best
        val missing = best - summary.score
        val toBest = if (result.isBest || missing > 15 || best < 5) null
        else if (missing == 0) t("tieRecord") else t("toRecord", "n" to missing + 1)
        val doneNow = result.completed.map { it.id }.toSet()
        return GameOverUi(
            score = summary.score,
            coins = summary.coins,
            best = best,
            newBest = result.isBest,
            toBest = toBest,
            achievementLines = result.achievements.map { "[${it.icon}] ${t("achUnlocked", "name" to L(it.name))}" to it.reward },
            missions = progress.missions().map { mission(it, it.id in doneNow) },
            zoneReached = if (summary.zone == 0) null
            else t("zoneReached", "n" to summary.zone + 1, "name" to L(zoneName(summary.zone))),
            nextUnlock = nextUnlock(),
        )
    }

    /** Name of `zoneBiome(zone)`: every 4th zone is the equipped world. */
    fun zoneName(zone: Int): LocalizedText {
        val i = zone % Tuning.BIOME_COUNT
        return if (i == 0) (progress.equipped(Kind.World) as WorldItem).name else Catalog.zoneBiomes[i].name
    }
}
