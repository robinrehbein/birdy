package de.robinrehbein.birdy.game.loop

import de.robinrehbein.birdy.game.UiEffect
import de.robinrehbein.birdy.platform.Ads
import de.robinrehbein.birdy.platform.AdsStatus
import de.robinrehbein.birdy.platform.RewardKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class InterstitialAtRoundEndTest {
    private class ReadyAds : Ads {
        override val status: StateFlow<AdsStatus> = MutableStateFlow(AdsStatus(interstitialReady = true))
        override fun init() = Unit
        override fun showRewarded(kind: RewardKind, onResult: (earned: Boolean) -> Unit) = Unit
        override fun showInterstitial(onResult: (shown: Boolean) -> Unit) = Unit
        override fun showPrivacyOptions() = Unit
    }

    @Test
    fun emitsOnlyAtRoundEndAfterEnoughFlightTime() = runBlocking {
        val h = LoopHarness(ads = ReadyAds())
        h.startRun()
        h.state.runTime = 1200.0
        h.sim.showGameOver()
        h.game.game.routeEvents()
        assertEquals(UiEffect.ShowInterstitialAd, h.game.effects.first())
    }
}
