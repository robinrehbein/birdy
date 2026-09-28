package de.robinrehbein.birdy.platform.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import de.robinrehbein.birdy.platform.Billing
import de.robinrehbein.birdy.platform.BillingStatus
import de.robinrehbein.birdy.platform.StoreProduct
import de.robinrehbein.birdy.platform.StorePurchase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Port of `billing.js` + `BirdyBillingPlugin.java` (platform.md §2.8) onto Play Billing Library
 * 9.x. [activity] resolves the Activity needed to launch checkout; [PurchaseProcessor] (shared)
 * owns the entitlement logic and calls back into [consume]/[acknowledge]/[refresh].
 */
class PlayBilling(
    context: Context,
    private val activity: () -> Activity?,
) : Billing {
    private val state = MutableStateFlow(BillingStatus())
    override val status: StateFlow<BillingStatus> = state

    private var onPurchase: (StorePurchase) -> Unit = {}
    /**
     * `billing.js`'s `purchase()` resolves once the checkout UI closes (not once a purchase
     * completes; the actual result arrives via [onPurchase], platform.md §2.7). Play Billing has
     * no direct "flow closed" callback, so this fires from the same listener whenever it reports
     * a non-OK code (user cancelled, error) — the shell uses it to clear the busy/"in flight" UI
     * state ([de.robinrehbein.birdy.game.UiCommand.PurchaseEnded]). A successful purchase clears
     * that state itself once [onPurchase] -> [PurchaseProcessor] grants it.
     */
    var onPurchaseFlowEnded: ((failed: Boolean) -> Unit)? = null

    /**
     * `refresh()`'s full owned-purchase list (`billing.js:60-82` step 1-2): distinct from
     * `onPurchase` (real-time `PurchasesUpdatedListener` pushes) because only this path also
     * needs to overwrite the authoritative paid-product set (`PurchaseProcessor.syncOwnedPurchases`)
     * before replaying grants — a single pushed purchase must not trigger that overwrite.
     */
    var onRestore: (List<StorePurchase>) -> Unit = {}

    /** Product details cached by [refresh]/init's `getProducts`, keyed by id (needed for launch). */
    private val details = HashMap<String, ProductDetails>()
    private var ready = false

    private val client = BillingClient.newBuilder(context)
        .setListener { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
                purchases.forEach { onPurchase(it.toStorePurchase()) }
            } else if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                onPurchaseFlowEnded?.invoke(true)
            }
        }
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    override fun init(productIds: List<String>, onPurchase: (StorePurchase) -> Unit) {
        this.onPurchase = onPurchase
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    ready = true
                    refreshProducts(productIds)
                }
            }

            // Deliberate no-op: enableAutoServiceReconnection() handles retries.
            override fun onBillingServiceDisconnected() = Unit
        })
    }

    override fun refresh() {
        if (!ready) return
        client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(ProductType.INAPP).build()) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                onRestore(purchases.map { it.toStorePurchase() })
            }
        }
    }

    override fun launchPurchase(productId: String) {
        // platform.md §2.7: "Product unavailable" if not in the locally cached product map.
        val act = activity() ?: run { onPurchaseFlowEnded?.invoke(true); return }
        val product = details[productId] ?: run { onPurchaseFlowEnded?.invoke(true); return }
        val offerToken = product.oneTimePurchaseOfferDetails?.offerToken
            ?: run { onPurchaseFlowEnded?.invoke(true); return }
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(product)
                        .setOfferToken(offerToken)
                        .build()
                )
            )
            .build()
        val result = client.launchBillingFlow(act, flowParams)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) onPurchaseFlowEnded?.invoke(true)
    }

    override fun consume(token: String, onDone: (ok: Boolean) -> Unit) {
        val params = ConsumeParams.newBuilder().setPurchaseToken(token).build()
        client.consumeAsync(params) { result, _ -> onDone(result.responseCode == BillingClient.BillingResponseCode.OK) }
    }

    override fun acknowledge(token: String, onDone: (ok: Boolean) -> Unit) {
        val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(token).build()
        client.acknowledgePurchase(params) { result -> onDone(result.responseCode == BillingClient.BillingResponseCode.OK) }
    }

    private fun refreshProducts(productIds: List<String>) {
        val products = productIds.map {
            QueryProductDetailsParams.Product.newBuilder().setProductId(it).setProductType(ProductType.INAPP).build()
        }
        val params = QueryProductDetailsParams.newBuilder().setProductList(products).build()
        client.queryProductDetailsAsync(params) { result, queryResult ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                state.value = state.value.copy(ready = false)
                return@queryProductDetailsAsync
            }
            details.clear()
            val storeProducts = HashMap<String, StoreProduct>()
            queryResult.productDetailsList.forEach { pd ->
                val offer = pd.oneTimePurchaseOfferDetails ?: return@forEach
                details[pd.productId] = pd
                storeProducts[pd.productId] = StoreProduct(pd.productId, offer.formattedPrice)
            }
            state.value = BillingStatus(supported = true, ready = true, products = storeProducts)
        }
        // Restored purchases are delivered separately (refresh()); this only refreshes prices.
        refresh()
    }

    private fun Purchase.toStorePurchase() = StorePurchase(
        products = products,
        token = purchaseToken,
        state = purchaseState,
        acknowledged = isAcknowledged,
    )
}
