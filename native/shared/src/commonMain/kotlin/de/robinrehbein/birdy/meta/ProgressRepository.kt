package de.robinrehbein.birdy.meta

import kotlinx.coroutines.flow.StateFlow

/**
 * The progress.js API (meta.md §1). Loads once from storage, keeps the model in memory and saves
 * after every mutating call. Not thread-safe: call only from the game thread (see
 * ARCHITECTURE.md threading model); the UI observes [data].
 */
interface ProgressRepository {
    val data: StateFlow<ProgressData>

    // --- shop ---
    fun equipped(kind: Kind): CatalogItem
    fun owns(kind: Kind, id: String): Boolean
    fun permanentlyOwns(kind: Kind, id: String): Boolean
    fun buy(kind: Kind, id: String): Boolean
    fun select(kind: Kind, id: String): Boolean
    fun buySurprise(price: Int): Boolean
    /** Takes [amount] coins from the wallet if there are enough (revive for coins). */
    fun spendCoins(amount: Int): Boolean
    fun grant(kind: Kind, id: String)
    fun level(upgradeId: String): Int
    /** Null when maxed. */
    fun upgradePrice(upgradeId: String): Int?
    fun buyUpgrade(upgradeId: String): Boolean

    // --- runs, missions, achievements ---
    /** Today's 3 missions (generated from the day-seeded RNG on first access per day). */
    fun missions(): List<Mission>
    fun missionText(m: Mission): String
    fun finishRun(run: RunStats): RunResult
    fun wouldComplete(run: RunStats): List<String>
    fun wouldUnlock(run: RunStats): List<Achievement>
    fun checkAchievements(): List<Achievement>
    fun achievements(): List<Pair<Achievement, Int>>
    fun setTutorialDone()

    // --- daily gift ---
    fun giftAvailable(): Boolean
    val streak: Int
    fun giftAmount(streak: Int): Int
    fun claimGift(): GiftClaim?

    // --- rewarded ads / style pass ---
    val rewardedAdsLeft: Int
    fun grantRewardedCoins(): Int
    /** Game-over "coins x2" ads left today (own limit, separate from [rewardedAdsLeft]). */
    val doubleCoinsAdsLeft: Int
    /** Credits [runCoins] a second time and uses one x2 slot; returns the amount granted (0 = limit reached / nothing to grant). */
    fun grantDoubleCoins(runCoins: Int): Int
    val stylePassMinutesLeft: Int
    fun grantStylePass(): Boolean

    // --- billing entitlements (platform.md §2.6) ---
    /** true = newly owned, false = already owned, null = save failed (do not acknowledge). */
    fun grantPaidProduct(productId: String): Boolean?
    /** Overwrites the paid-product set; false = save failed (skip replaying grants). */
    fun syncPaidProducts(productIds: List<String>): Boolean
}
