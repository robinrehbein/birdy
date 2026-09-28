package de.robinrehbein.birdy.platform.purchase

import de.robinrehbein.birdy.meta.ProgressRepository
import de.robinrehbein.birdy.platform.Billing
import de.robinrehbein.birdy.platform.StorePurchase

/** `Purchase.PurchaseState`: only this one grants anything (platform.md §2.4). */
private const val STATE_PURCHASED = 1

/**
 * Common port of `billing.js`'s `handlePurchase`/`refresh` (platform.md §2.4-§2.5), built on the
 * injected [Billing] backend and [ProgressRepository] entitlement calls. Framework-agnostic:
 * Android wires it from [de.robinrehbein.birdy.platform.billing.PlayBilling]'s purchase callback
 * and app-resume; iOS will wire the same class from a StoreKit backend.
 *
 * Every entitlement mutation (and therefore every call into [progress]) is dispatched through
 * [runOnGame] (`BirdyGame.runOnGameThread`) since [Billing] callbacks may arrive on any thread
 * but [ProgressRepository] is only safe to touch from the game thread (ARCHITECTURE.md). Callers
 * that are already certain to be on the game thread (e.g. tests) may pass `{ it() }`.
 *
 * [onGrant] mirrors `billing.js`'s `onGrant` hook (coins/permanent toasts); it is invoked on the
 * game thread, inside the same [runOnGame] dispatch as the grant itself.
 *
 * [progress] is resolved lazily (not at construction) so the app shell can build this before
 * `BirdyGame` finishes booting (storage migration, ARCHITECTURE.md): [runOnGame] only actually
 * runs its block once the game thread has a session, by which point [progress] is safe to call.
 */
class PurchaseProcessor(
    private val billing: Billing,
    private val progress: () -> ProgressRepository,
    private val runOnGame: (() -> Unit) -> Unit,
    private val onGrant: (PurchaseGrant) -> Unit = {},
) {
    /** Wires the [Billing] backend; call once. Mirrors `billing.init` (platform.md §2.3). */
    fun start() {
        billing.init(ProductIds.ids) { purchase -> handlePurchase(purchase) }
    }

    /** App resumed to foreground, or an explicit restore request. */
    fun refresh() = billing.refresh()

    /**
     * `billing.js:34-58`. Only [STATE_PURCHASED] grants anything; every product id in the
     * purchase is processed independently (coin pack vs. permanent item).
     */
    fun handlePurchase(purchase: StorePurchase) {
        if (!isGrantable(purchase)) return
        runOnGame { grant(purchase) }
    }

    private fun isGrantable(purchase: StorePurchase) =
        purchase.state == STATE_PURCHASED && purchase.token.isNotEmpty()

    /** Must run on the game thread. */
    private fun grant(purchase: StorePurchase) {
        for (id in purchase.products) {
            if (ProductIds.coinIds.contains(id)) {
                handleCoinPack(id, purchase)
            } else if (ProductIds.isPermanent(id)) {
                handlePermanent(id, purchase)
            }
        }
    }

    private fun handleCoinPack(id: String, purchase: StorePurchase) {
        val credited = progress().grantPurchasedCoins(id, purchase.token)
        if (credited > 0) onGrant(PurchaseGrant.Coins(id, credited))
        // Consume if just credited, or if this token was already credited in a past run (a
        // consume that failed after the coins were saved) — never re-credit, always retry consume.
        if (credited > 0 || progress().hasProcessedPurchase(purchase.token)) {
            billing.consume(purchase.token) { /* swallow: retried on the next restore/refresh */ }
        }
    }

    private fun handlePermanent(id: String, purchase: StorePurchase) {
        // null = local save failed: skip both the grant callback and acknowledge (platform.md
        // §2.4) so Play keeps offering it as unacknowledged and it is retried later.
        val newlyOwned = progress().grantPaidProduct(id) ?: return
        if (newlyOwned) onGrant(PurchaseGrant.Permanent(id))
        if (!purchase.acknowledged) {
            billing.acknowledge(purchase.token) { /* swallow: retried on the next restore/refresh */ }
        }
    }

    /**
     * `billing.js:60-82` minus the single-flight/network parts, which live in [Billing] itself:
     * overwrites [ProgressRepository]'s authoritative paid-product set from every currently owned
     * purchase, then replays [handlePurchase] for each (idempotent via the token/product dedup
     * inside [progress]) so interrupted consume/acknowledge flows are retried. If persisting the
     * synced set fails, nothing is replayed (`if (!progress.syncPaidProducts(paid)) return;`).
     */
    fun syncOwnedPurchases(purchases: List<StorePurchase>) {
        runOnGame {
            val paid = purchases
                .filter { it.state == STATE_PURCHASED }
                .flatMap { it.products }
                .filter { ProductIds.isPermanent(it) }
                .distinct()
            if (!progress().syncPaidProducts(paid)) return@runOnGame
            purchases.filter(::isGrantable).forEach { grant(it) }
        }
    }
}

/** Mirrors `billing.js`'s `onGrant({ id, coins })` / `onGrant({ id, permanent: true })`. */
sealed class PurchaseGrant {
    abstract val productId: String
    data class Coins(override val productId: String, val amount: Int) : PurchaseGrant()
    data class Permanent(override val productId: String) : PurchaseGrant()
}
