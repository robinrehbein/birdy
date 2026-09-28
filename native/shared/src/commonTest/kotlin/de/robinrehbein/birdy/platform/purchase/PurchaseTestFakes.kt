package de.robinrehbein.birdy.platform.purchase

import de.robinrehbein.birdy.meta.Achievement
import de.robinrehbein.birdy.meta.CatalogItem
import de.robinrehbein.birdy.meta.GiftClaim
import de.robinrehbein.birdy.meta.Kind
import de.robinrehbein.birdy.meta.Mission
import de.robinrehbein.birdy.meta.ProgressData
import de.robinrehbein.birdy.meta.ProgressRepository
import de.robinrehbein.birdy.meta.RunResult
import de.robinrehbein.birdy.meta.RunStats
import de.robinrehbein.birdy.platform.Billing
import de.robinrehbein.birdy.platform.BillingStatus
import de.robinrehbein.birdy.platform.StorePurchase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * A real (not stubbed) port of `progress.js`'s purchase-related fields (platform.md §2.6), so
 * [PurchaseProcessorTest] exercises [PurchaseProcessor] against faithful entitlement semantics
 * instead of trivial stubs. Every other [ProgressRepository] member is unused by billing and
 * left as a minimal stub.
 */
class FakePurchaseProgress : ProgressRepository {
    private val state = MutableStateFlow(ProgressData())
    override val data: StateFlow<ProgressData> = state

    /** Set true to make the next mutating call behave like a failed `save()`. */
    var failNextSave = false

    override fun grantPaidProduct(productId: String): Boolean? {
        val match = Regex("^birdy_(skin|world)_([a-z]+)$").matchEntire(productId) ?: return false
        val kind = Kind.of(match.groupValues[1]) ?: return false
        val itemId = match.groupValues[2]
        val before = state.value
        val wasOwned = before.paidProducts.contains(productId)
        val paid = if (wasOwned) before.paidProducts else before.paidProducts + productId
        state.value = before.copy(
            paidProducts = paid,
            equip = before.equip + (kind.id to itemId),
        )
        if (consumeFailure()) {
            state.value = before
            return null
        }
        return !wasOwned
    }

    override fun syncPaidProducts(productIds: List<String>): Boolean {
        val before = state.value
        state.value = before.copy(paidProducts = productIds.distinct())
        if (!consumeFailure()) return true
        state.value = before
        return false
    }

    private fun consumeFailure(): Boolean {
        if (!failNextSave) return false
        failNextSave = false
        return true
    }

    // --- unused by billing ---
    override fun equipped(kind: Kind): CatalogItem = throw NotImplementedError()
    override fun owns(kind: Kind, id: String): Boolean = false
    override fun permanentlyOwns(kind: Kind, id: String): Boolean = false
    override fun buy(kind: Kind, id: String): Boolean = false
    override fun select(kind: Kind, id: String): Boolean = false
    override fun buySurprise(price: Int): Boolean = false
    override fun grant(kind: Kind, id: String) = Unit
    override fun level(upgradeId: String): Int = 0
    override fun upgradePrice(upgradeId: String): Int? = null
    override fun buyUpgrade(upgradeId: String): Boolean = false
    override fun missions(): List<Mission> = emptyList()
    override fun missionText(m: Mission): String = ""
    override fun finishRun(run: RunStats): RunResult = RunResult(false, emptyList(), emptyList())
    override fun wouldComplete(run: RunStats): List<String> = emptyList()
    override fun wouldUnlock(run: RunStats): List<Achievement> = emptyList()
    override fun checkAchievements(): List<Achievement> = emptyList()
    override fun achievements(): List<Pair<Achievement, Int>> = emptyList()
    override fun setTutorialDone() = Unit
    override fun giftAvailable(): Boolean = false
    override val streak: Int get() = 0
    override fun giftAmount(streak: Int): Int = 0
    override fun claimGift(): GiftClaim? = null
    override val rewardedAdsLeft: Int get() = 0
    override fun grantRewardedCoins(): Int = 0
    override val stylePassMinutesLeft: Int get() = 0
    override fun grantStylePass(): Boolean = false
}

/** Records every call; [purchaseHandler] is what [PurchaseProcessor.start] registered. */
class FakeBilling : Billing {
    private val state = MutableStateFlow(BillingStatus())
    override val status: StateFlow<BillingStatus> = state

    var purchaseHandler: ((StorePurchase) -> Unit)? = null
    var initIds: List<String>? = null
    val acknowledged = ArrayList<String>()
    var refreshCalls = 0
    var acknowledgeShouldFail = false

    override fun init(productIds: List<String>, onPurchase: (StorePurchase) -> Unit) {
        initIds = productIds
        purchaseHandler = onPurchase
    }

    override fun refresh() { refreshCalls++ }

    override fun launchPurchase(productId: String) = Unit

    override fun acknowledge(token: String, onDone: (ok: Boolean) -> Unit) {
        val ok = !acknowledgeShouldFail
        if (ok) acknowledged += token
        onDone(ok)
    }
}

fun purchase(
    products: List<String>,
    token: String,
    state: Int = 1,
    acknowledged: Boolean = false,
) = StorePurchase(products, token, state, acknowledged)
