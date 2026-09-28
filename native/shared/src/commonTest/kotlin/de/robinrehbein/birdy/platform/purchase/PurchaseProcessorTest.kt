package de.robinrehbein.birdy.platform.purchase

import de.robinrehbein.birdy.Golden
import de.robinrehbein.birdy.meta.Catalog
import de.robinrehbein.birdy.meta.Kind
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PurchaseProcessorTest {
    private fun harness(): Triple<PurchaseProcessor, FakePurchaseProgress, FakeBilling> {
        val progress = FakePurchaseProgress()
        val billing = FakeBilling()
        val grants = ArrayList<PurchaseGrant>()
        val processor = PurchaseProcessor(billing, { progress }, runOnGame = { it() }, onGrant = { grants += it })
        return Triple(processor, progress, billing)
    }

    @Test
    fun startRegistersTheFullProductIdListAndPurchaseHandler() {
        val (processor, _, billing) = harness()
        processor.start()
        assertEquals(ProductIds.ids, billing.initIds)
        assertTrue(billing.purchaseHandler != null)
    }

    @Test
    fun pendingPurchaseStateIsIgnored() {
        val (processor, progress, billing) = harness()
        processor.handlePurchase(purchase(listOf("birdy_coins_500"), "tok", state = 2))
        assertEquals(0, progress.data.value.coins)
        assertTrue(billing.consumed.isEmpty())
    }

    @Test
    fun purchaseWithoutTokenIsIgnored() {
        val (processor, progress, _) = harness()
        processor.handlePurchase(purchase(listOf("birdy_coins_500"), token = ""))
        assertEquals(0, progress.data.value.coins)
    }

    @Test
    fun coinPackCreditsAndConsumes() {
        val (processor, progress, billing) = harness()
        val grants = ArrayList<PurchaseGrant>()
        val p = PurchaseProcessor(billing, { progress }, { it() }) { grants += it }
        p.handlePurchase(purchase(listOf("birdy_coins_500"), "tok-1"))
        assertEquals(500, progress.data.value.coins)
        assertEquals(listOf<PurchaseGrant>(PurchaseGrant.Coins("birdy_coins_500", 500)), grants)
        assertEquals(listOf("tok-1"), billing.consumed)
    }

    @Test
    fun sameTokenNeverCreditsTwiceButIsAlwaysRetriedForConsume() {
        val (processor, progress, billing) = harness()
        processor.handlePurchase(purchase(listOf("birdy_coins_500"), "tok-1"))
        billing.consumed.clear()
        // Play redelivers the still-owned purchase (consume failed previously).
        processor.handlePurchase(purchase(listOf("birdy_coins_500"), "tok-1"))
        assertEquals(500, progress.data.value.coins) // not double-credited
        assertEquals(listOf("tok-1"), billing.consumed) // but consume is retried
    }

    @Test
    fun failedSaveRollsBackCoinCreditAndSkipsConsume() {
        val (processor, progress, billing) = harness()
        progress.failNextSave = true
        processor.handlePurchase(purchase(listOf("birdy_coins_1500"), "tok-2"))
        assertEquals(0, progress.data.value.coins)
        assertFalse(progress.hasProcessedPurchase("tok-2"))
        assertTrue(billing.consumed.isEmpty())
    }

    @Test
    fun permanentItemGrantsAndAcknowledges() {
        val (processor, progress, billing) = harness()
        val id = ProductIds.itemIds.first { it.startsWith("birdy_skin_") }
        processor.handlePurchase(purchase(listOf(id), "tok-3"))
        assertTrue(progress.data.value.paidProducts.contains(id))
        assertEquals(listOf("tok-3"), billing.acknowledged)
    }

    @Test
    fun alreadyAcknowledgedPermanentItemIsNotAcknowledgedAgain() {
        val (processor, progress, billing) = harness()
        val id = ProductIds.itemIds.first { it.startsWith("birdy_world_") }
        processor.handlePurchase(purchase(listOf(id), "tok-4", acknowledged = true))
        assertTrue(progress.data.value.paidProducts.contains(id))
        assertTrue(billing.acknowledged.isEmpty())
    }

    @Test
    fun failedSaveOnPermanentItemSkipsGrantAndAcknowledge() {
        val (processor, progress, billing) = harness()
        val id = ProductIds.itemIds.first()
        progress.failNextSave = true
        processor.handlePurchase(purchase(listOf(id), "tok-5"))
        assertFalse(progress.data.value.paidProducts.contains(id))
        assertTrue(billing.acknowledged.isEmpty())
    }

    @Test
    fun replayingAnAlreadyOwnedPermanentItemStillAcknowledgesButDoesNotGrantAgain() {
        val (processor, progress, billing) = harness()
        val id = ProductIds.itemIds.first()
        processor.handlePurchase(purchase(listOf(id), "tok-a", acknowledged = true))
        val grants = ArrayList<PurchaseGrant>()
        val p2 = PurchaseProcessor(billing, { progress }, { it() }) { grants += it }
        // Restore redelivers the purchase, unacknowledged this time (e.g. a fresh install state).
        p2.handlePurchase(purchase(listOf(id), "tok-a"))
        assertTrue(grants.isEmpty()) // grantPaidProduct returns false: already owned
        assertEquals(listOf("tok-a"), billing.acknowledged)
    }

    @Test
    fun syncOwnedPurchasesOverwritesPaidProductsAndReplaysGrants() {
        val (processor, progress, billing) = harness()
        val skin = ProductIds.itemIds.first { it.startsWith("birdy_skin_") }
        val world = ProductIds.itemIds.first { it.startsWith("birdy_world_") }
        // Local state has `skin` from a stale sync; Play now authoritatively reports only `world`.
        progress.syncPaidProducts(listOf(skin))
        processor.syncOwnedPurchases(listOf(purchase(listOf(world), "tok-sync")))
        assertEquals(listOf(world), progress.data.value.paidProducts.filter { it == world })
        assertFalse(progress.data.value.paidProducts.contains(skin))
        assertTrue(billing.acknowledged.contains("tok-sync"))
    }

    @Test
    fun failedPaidProductSyncSkipsReplayingGrants() {
        val (processor, progress, billing) = harness()
        progress.failNextSave = true
        processor.syncOwnedPurchases(listOf(purchase(listOf("birdy_coins_500"), "tok-fail")))
        assertEquals(0, progress.data.value.coins)
        assertTrue(billing.consumed.isEmpty())
    }

    @Test
    fun coinAndPermanentIdsMatchTheGoldenProductList() {
        val golden = Golden.json("platform-product-ids.json").jsonObject
        fun ids(key: String) = golden[key]!!.jsonArray.map { it.jsonPrimitive.content }
        assertEquals(ids("coinIds"), ProductIds.coinIds)
        assertEquals(ids("itemIds"), ProductIds.itemIds)
        assertEquals(ids("ids"), ProductIds.ids)
        assertEquals(golden["count"]!!.jsonPrimitive.int, ProductIds.ids.size)
    }

    @Test
    fun rareSkinsAreNeverSoldForRealMoney() {
        val rare = Catalog.skins.filter { it.rare }
        assertTrue(rare.isNotEmpty())
        for (item in rare) {
            val id = ProductIds.itemId(Kind.Skin, item.id)
            assertFalse(id in ProductIds.ids, id)
            assertFalse(ProductIds.isPermanent(id), id)
        }
    }

    @Test
    fun rarePurchaseIsNotGranted() {
        val (processor, progress, billing) = harness()
        val rare = Catalog.skins.first { it.rare }
        processor.handlePurchase(purchase(listOf(ProductIds.itemId(Kind.Skin, rare.id)), "tok-rare"))
        assertTrue(progress.data.value.paidProducts.isEmpty())
        assertTrue(billing.acknowledged.isEmpty())
    }

    @Test
    fun grantPaidProductNullSaveFailureIsDistinctFromAlreadyOwnedFalse() {
        val progress = FakePurchaseProgress()
        val id = ProductIds.itemIds.first()
        progress.failNextSave = true
        assertNull(progress.grantPaidProduct(id))
        assertTrue(progress.grantPaidProduct(id) == true) // now saves fine, newly owned
        assertEquals(false, progress.grantPaidProduct(id)) // already owned
    }
}
