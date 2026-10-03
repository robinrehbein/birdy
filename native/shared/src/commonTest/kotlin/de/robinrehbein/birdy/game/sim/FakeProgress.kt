package de.robinrehbein.birdy.game.sim

import de.robinrehbein.birdy.game.GameEvent
import de.robinrehbein.birdy.game.GameSimulation
import de.robinrehbein.birdy.meta.Achievement
import de.robinrehbein.birdy.meta.CatalogItem
import de.robinrehbein.birdy.meta.GiftClaim
import de.robinrehbein.birdy.meta.Kind
import de.robinrehbein.birdy.meta.Mission
import de.robinrehbein.birdy.meta.ProgressData
import de.robinrehbein.birdy.meta.ProgressRepository
import de.robinrehbein.birdy.meta.RunResult
import de.robinrehbein.birdy.meta.RunStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.random.Random

/** In-memory [ProgressRepository] for simulation tests; only what the simulation calls works. */
class FakeProgress(
    best: Int = 0,
    runs: Int = 5,
    val levels: MutableMap<String, Int> = mutableMapOf(),
) : ProgressRepository {
    private val state = MutableStateFlow(ProgressData(best = best, runs = runs, tutorialDone = true))
    override val data: StateFlow<ProgressData> = state

    /** Mission id -> completion predicate for [wouldComplete]. */
    val missionRules = LinkedHashMap<String, (RunStats) -> Boolean>()
    val achievementRules = LinkedHashMap<Achievement, (RunStats) -> Boolean>()
    val finished = ArrayList<RunStats>()
    var tutorialDoneCalls = 0

    fun setBest(best: Int) { state.value = state.value.copy(best = best) }
    fun setRuns(runs: Int) { state.value = state.value.copy(runs = runs) }

    override fun equipped(kind: Kind): CatalogItem = TODO("not used by the simulation")
    override fun owns(kind: Kind, id: String): Boolean = false
    override fun permanentlyOwns(kind: Kind, id: String): Boolean = false
    override fun buy(kind: Kind, id: String): Boolean = false
    override fun select(kind: Kind, id: String): Boolean = false
    override fun buySurprise(price: Int): Boolean = false
    override fun grant(kind: Kind, id: String) = Unit
    override fun level(upgradeId: String): Int = levels[upgradeId] ?: 0
    override fun upgradePrice(upgradeId: String): Int? = null
    override fun buyUpgrade(upgradeId: String): Boolean = false

    override fun missions(): List<Mission> = missionRules.keys.map { Mission(it, 1, 0, 10) }
    override fun missionText(m: Mission): String = m.id
    override fun finishRun(run: RunStats): RunResult {
        finished += run
        val d = state.value
        val isBest = run.score > d.best
        state.value = d.copy(runs = d.runs + 1, best = maxOf(d.best, run.score))
        return RunResult(isBest, emptyList(), emptyList())
    }
    override fun wouldComplete(run: RunStats): List<String> = missionRules.filter { it.value(run) }.keys.toList()
    override fun wouldUnlock(run: RunStats): List<Achievement> = achievementRules.filter { it.value(run) }.keys.toList()
    override fun checkAchievements(): List<Achievement> = emptyList()
    override fun achievements(): List<Pair<Achievement, Int>> = emptyList()
    override fun setTutorialDone() {
        tutorialDoneCalls++
        state.value = state.value.copy(tutorialDone = true)
    }

    override fun giftAvailable(): Boolean = false
    override val streak: Int get() = 0
    override fun giftAmount(streak: Int): Int = 0
    override fun claimGift(): GiftClaim? = null
    override val rewardedAdsLeft: Int get() = 0
    override fun grantRewardedCoins(): Int = 0
    override val doubleCoinsAdsLeft: Int get() = 0
    override fun grantDoubleCoins(runCoins: Int): Int = 0
    override val stylePassMinutesLeft: Int get() = 0
    override fun grantStylePass(): Boolean = false
    override fun grantPaidProduct(productId: String): Boolean? = false
    override fun syncPaidProducts(productIds: List<String>) = true
}

/** A simulation with a fake progress store and a recorded event list. */
class SimHarness(seed: Int = 1, val progress: FakeProgress = FakeProgress()) {
    val events = ArrayList<GameEvent>()
    val sim = GameSimulation(progress, Random(seed)) { events += it }
    val state get() = sim.state

    inline fun <reified T : GameEvent> eventsOf(): List<T> = events.filterIsInstance<T>()

    /** 60 Hz steps (beat from game time). */
    fun run(seconds: Double, dt: Double = 1.0 / 60, stopWhen: () -> Boolean = { false }) {
        var t = 0.0
        while (t < seconds - 1e-9) {
            sim.step(dt, null)
            if (stopWhen()) return
            t += dt
        }
    }
}
