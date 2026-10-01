package de.robinrehbein.birdy.platform

import kotlinx.coroutines.flow.StateFlow

/** Which rewarded ad unit to show (platform.md §1): coin reward or 1 h style pass. */
enum class RewardKind { Coins, Pass }

/** Snapshot of the ads SDK state used by the UI (`ads.js` status). */
data class AdsStatus(
    val supported: Boolean = false,
    /** A rewarded ad is loaded for the given kind. */
    val ready: Set<RewardKind> = emptySet(),
    val interstitialReady: Boolean = false,
    /** UMP says a privacy-options entry point must be shown. */
    val privacyOptionsRequired: Boolean = false,
)

/**
 * Rewarded ads + UMP consent (AdMob on Android). Business rules (3/day, 30 coins, 1 h pass) live
 * in the progress repository, not here; this only reports whether the user earned the reward.
 * Methods are called from the UI thread.
 */
interface Ads {
    val status: StateFlow<AdsStatus>

    /** Runs the UMP consent flow if needed, then initializes the SDK and preloads ads. */
    fun init()

    /** Shows a rewarded ad; [onResult] gets true only if the reward was earned. */
    fun showRewarded(kind: RewardKind, onResult: (earned: Boolean) -> Unit)

    /** Reports true only after an interstitial actually appears. */
    fun showInterstitial(onResult: (shown: Boolean) -> Unit)

    fun showPrivacyOptions()
}
