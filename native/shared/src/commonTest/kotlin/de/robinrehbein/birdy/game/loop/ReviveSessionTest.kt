package de.robinrehbein.birdy.game.loop

import de.robinrehbein.birdy.game.GameMode
import de.robinrehbein.birdy.game.Tuning
import de.robinrehbein.birdy.game.UiCommand
import de.robinrehbein.birdy.game.UiEffect
import de.robinrehbein.birdy.platform.Ads
import de.robinrehbein.birdy.platform.AdsStatus
import de.robinrehbein.birdy.platform.RewardKind
import de.robinrehbein.birdy.platform.purchase.ProductIds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReviveSessionTest {
    private class ReadyAds(ready: Set<RewardKind>) : Ads {
        override val status: StateFlow<AdsStatus> =
            MutableStateFlow(AdsStatus(supported = true, ready = ready, interstitialReady = true))
        override fun init() = Unit
        override fun showRewarded(kind: RewardKind, onResult: (earned: Boolean) -> Unit) = Unit
        override fun showInterstitial(onResult: (shown: Boolean) -> Unit) = Unit
        override fun showPrivacyOptions() = Unit
    }

    /** Starts a run (record 20), scores 18 and crashes into a fully blocked row. */
    private fun crashNearRecord(h: LoopHarness) {
        h.startRun(god = false)
        h.sim.clearTrack()
        h.state.lastGateZ = -1000.0
        h.state.score = 18
        h.state.y = 5.0
        h.sim.debug.scriptedRow(-0.5, listOf(null, null, null))
        h.frames(0.8)
        assertTrue(h.state.mode != GameMode.Playing)
    }

    private fun drainEffects(h: LoopHarness): List<UiEffect> = generateSequence { h.game.pollEffect() }.toList()

    @Test
    fun adReviveSkipsTheInterstitialAndKeepsTheShopAllowance() {
        val h = LoopHarness(ads = ReadyAds(setOf(RewardKind.Coins, RewardKind.Revive)))
        crashNearRecord(h)
        val ui = assertNotNull(h.ui.revive)
        assertFalse(ui.coins)
        assertTrue(ui.accept.enabled)
        drainEffects(h)

        h.post(UiCommand.ReviveAccept)
        assertEquals(listOf<UiEffect>(UiEffect.ShowRewardedAd(RewardKind.Revive)), drainEffects(h))
        h.post(UiCommand.RewardResult(RewardKind.Revive, true))
        assertEquals(GameMode.Playing, h.state.mode)
        assertEquals(18, h.state.score)
        assertNull(h.ui.revive)
        assertEquals(3, h.progress.rewardedAdsLeft, "a revive is not one of the shop's 3/day")

        // Its game over (with plenty of flight time) shows no interstitial on top.
        h.state.runTime = 1200.0
        h.sim.showGameOver()
        h.game.game.routeEvents()
        assertTrue(UiEffect.ShowInterstitialAd !in drainEffects(h))
    }

    @Test
    fun noReadyReviveAdMeansNoOffer() {
        val h = LoopHarness(ads = ReadyAds(setOf(RewardKind.Coins)))
        crashNearRecord(h)
        h.frames(0.5)
        assertNull(h.ui.revive)
        assertEquals(GameMode.Over, h.state.mode)
    }

    @Test
    fun adFreePlayersPayCoins() {
        val h = LoopHarness(
            progressJson = """{"coins":150,"best":20,"runs":6,"tutorialDone":true,"paidProducts":["${ProductIds.REMOVE_ADS}"]}""",
        )
        crashNearRecord(h)
        val ui = assertNotNull(h.ui.revive)
        assertTrue(ui.coins)
        assertTrue(ui.accept.enabled)
        val wallet = h.progress.data.value.coins // menu achievements may have paid out at boot
        h.post(UiCommand.ReviveAccept)
        assertTrue(drainEffects(h).none { it is UiEffect.ShowRewardedAd })
        assertEquals(GameMode.Playing, h.state.mode)
        assertEquals(wallet - Tuning.REVIVE_COINS, h.progress.data.value.coins)
    }

    @Test
    fun notEnoughCoinsDisablesTheButtonAndBackDeclines() {
        val h = LoopHarness(
            progressJson = """{"coins":40,"best":20,"runs":6,"tutorialDone":true,"paidProducts":["${ProductIds.REMOVE_ADS}"]}""",
        )
        crashNearRecord(h)
        val ui = assertNotNull(h.ui.revive)
        assertFalse(ui.accept.enabled)
        val wallet = h.progress.data.value.coins
        assertTrue(wallet < Tuning.REVIVE_COINS)
        h.post(UiCommand.ReviveAccept)
        assertEquals(GameMode.Dead, h.state.mode)
        assertEquals(wallet, h.progress.data.value.coins)
        h.post(UiCommand.Back)
        assertEquals(GameMode.Over, h.state.mode)
    }
}
