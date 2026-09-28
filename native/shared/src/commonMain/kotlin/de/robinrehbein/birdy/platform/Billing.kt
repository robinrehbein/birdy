package de.robinrehbein.birdy.platform

import kotlinx.coroutines.flow.StateFlow

/** Purchase as reported by the store (Play Billing `Purchase`). */
data class StorePurchase(
    val products: List<String>,
    val token: String,
    /** Play `PurchaseState`: 1 = PURCHASED, 2 = PENDING. */
    val state: Int,
    val acknowledged: Boolean,
)

/** Localized price info for a permanent skin or world product. */
data class StoreProduct(val id: String, val formattedPrice: String)

data class BillingStatus(
    val supported: Boolean = false,
    val ready: Boolean = false,
    val products: Map<String, StoreProduct> = emptyMap(),
)

/**
 * Store connection (Play Billing on Android, StoreKit 2 on iOS later). Entitlement logic
 * (grantPaidProduct / syncPaidProducts / acknowledge-after-save) lives in a common `PurchaseProcessor` built on this interface
 * (platform.md §2). Callbacks may arrive on any thread; implementations forward them as-is.
 */
interface Billing {
    val status: StateFlow<BillingStatus>

    /** Connects, queries product details for [productIds] and delivers owned purchases to [onPurchase]. */
    fun init(productIds: List<String>, onPurchase: (StorePurchase) -> Unit)

    /** Re-queries owned purchases (app resume / restore); results go to the init callback. */
    fun refresh()

    /** Starts the purchase UI for [productId]; the result arrives via the init callback. */
    fun launchPurchase(productId: String)

    fun acknowledge(token: String, onDone: (ok: Boolean) -> Unit)
}
