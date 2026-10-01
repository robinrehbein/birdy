package de.robinrehbein.birdy.platform.purchase

import de.robinrehbein.birdy.meta.Catalog
import de.robinrehbein.birdy.meta.Kind
import de.robinrehbein.birdy.meta.SkinItem

/**
 * Play product id derivation (platform.md §2.1, `billing.js:7-11`). Regenerated from the live
 * [Catalog] on every call so it can never drift from `docs/native/golden/platform-product-ids.json`
 * (golden test compares against this list, not a hand-copied one).
 */
object ProductIds {
    const val REMOVE_ADS = "birdy_remove_ads"
    /**
     * One id per non-rare skin/world with `price > 0`, in catalog declaration order. Rare skins
     * are coin/achievement-only and never sold for real money (`billing.js:8-9`).
     */
    val itemIds: List<String> = listOf(Kind.Skin, Kind.World).flatMap { kind ->
        Catalog.items(kind).filter { it.price > 0 && (it as? SkinItem)?.rare != true }.map { itemId(kind, it.id) }
    }

    val ids: List<String> = itemIds + REMOVE_ADS

    fun isPermanent(id: String): Boolean = id == REMOVE_ADS || itemIds.contains(id)

    fun itemId(kind: Kind, id: String): String = "birdy_${kind.id}_$id"
}
