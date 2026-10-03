package de.robinrehbein.birdy.game.loop

import de.robinrehbein.birdy.audio.Sfx
import de.robinrehbein.birdy.game.ButtonUi
import de.robinrehbein.birdy.game.PowerType
import de.robinrehbein.birdy.game.ShopTab
import de.robinrehbein.birdy.game.ShopTileUi
import de.robinrehbein.birdy.game.ShopUi
import de.robinrehbein.birdy.game.UiEffect
import de.robinrehbein.birdy.meta.Achievements
import de.robinrehbein.birdy.meta.Catalog
import de.robinrehbein.birdy.meta.CatalogItem
import de.robinrehbein.birdy.meta.Kind
import de.robinrehbein.birdy.meta.LocalizedText
import de.robinrehbein.birdy.meta.PipeItem
import de.robinrehbein.birdy.meta.ProgressRepository
import de.robinrehbein.birdy.meta.SkinItem
import de.robinrehbein.birdy.meta.Strings
import de.robinrehbein.birdy.meta.TrailItem
import de.robinrehbein.birdy.meta.WorldItem
import de.robinrehbein.birdy.platform.AdsStatus
import de.robinrehbein.birdy.platform.BillingStatus
import de.robinrehbein.birdy.platform.RewardKind
import de.robinrehbein.birdy.platform.purchase.ProductIds
import kotlin.random.Random

/** Workshop slots the bird wears (`LOOK_KINDS`). */
val LOOK_KINDS = listOf(Kind.Pattern, Kind.Hat, Kind.Eyes, Kind.Beak)

/** The 3D side of the shop (live try-on preview), implemented by the game with the views. */
interface ShopPreview {
    /** `applyBird()`: the equipped skin and look. */
    fun applyBird()
    /** Shows [skin] with [look] (kind -> id for [LOOK_KINDS]) without equipping anything. */
    fun showBird(skin: SkinItem, look: Map<Kind, String>)
    /** Trail on the hovering shop bird (null = none / leave the shop). */
    fun previewTrail(trail: TrailItem?)
    /** `previewWorld(world)`: 0.5 s cross-fade unless already showing it. */
    fun previewWorld(world: WorldItem)
    /** `setPipeStyle(pipeFor(world, pipe))`. */
    fun pipeStyle(world: WorldItem, pipe: PipeItem)
    fun pipePreviewVisible(visible: Boolean)
}

/** Menu-time particle bursts at the bird (`particles.emit(bird.group.position, …)`). */
sealed class MenuBurst {
    data object Confetti : MenuBurst()
    data class Dice(val skinBody: Int) : MenuBurst()
    data class Upgrade(val color: Int) : MenuBurst()
    data class Item(val colors: List<Int>) : MenuBurst()
    data object Gift : MenuBurst()
}

/** Non-3D feedback of shop actions (audio, toasts, particles, timers, shell requests). */
interface ShopFeedback {
    fun sfx(effect: Sfx)
    fun toast(text: String)
    fun burst(burst: MenuBurst)
    /** `setTimeout(celebrateMenuAchievements, ms)`. */
    fun celebrateLater(delayMs: Int)
    /** `renderWallet(true)`. */
    fun walletBump()
    fun effect(effect: UiEffect)
}

/**
 * The shop of main.js (main-b.md §7/§8, main-a.md §3.2/§3.3): tabs, selection with live try-on,
 * buy/select/upgrade, surprise, dice, rewarded ads / style pass and the real-money flow.
 * Game thread only; [ui] builds the snapshot for the overlay.
 */
class ShopController(
    private val progress: ProgressRepository,
    private val strings: Strings,
    private val preview: ShopPreview,
    private val feedback: ShopFeedback,
    private val random: Random = Random.Default,
) {
    var tab: ShopTab = ShopTab.Skin
        private set
    var sel: String = progress.equipped(Kind.Skin).id
        private set
    var billingBusy = false
        private set
    private var adBusy = false
    private var passBusy = false

    var ads = AdsStatus()
    var billing = BillingStatus()

    private fun t(key: String, vararg params: Pair<String, Any?>) = strings.t(key, mapOf(*params))
    private fun L(text: LocalizedText) = text.get(strings.lang.value)

    private val adsAvailable get() = RewardKind.Coins in ads.ready
    private val passAvailable get() = RewardKind.Pass in ads.ready

    fun defaultSel(): String = tab.kind?.let { progress.equipped(it).id } ?: Catalog.upgrades[0].id

    private fun equippedLook(): Map<Kind, String> = LOOK_KINDS.associateWith { progress.equipped(it).id }
    private fun skin() = progress.equipped(Kind.Skin) as SkinItem
    private fun world() = progress.equipped(Kind.World) as WorldItem
    private fun pipe() = progress.equipped(Kind.Pipe) as PipeItem
    private fun trail() = progress.equipped(Kind.Trail) as TrailItem

    /** `openShop(open)` minus the menu state (the game sets `state.menu`). */
    fun open(open: Boolean) {
        sel = defaultSel()
        if (open) {
            applyPreview()
        } else {
            preview.previewTrail(null)
            preview.applyBird()
            preview.previewWorld(world())
            preview.pipeStyle(world(), pipe())
            preview.pipePreviewVisible(false)
        }
    }

    /** Jump to [tab]/[id] (next-unlock shortcut, surprise result). */
    fun show(tab: ShopTab, id: String) {
        this.tab = tab
        sel = id
        applyPreview()
    }

    fun selectTab(tab: ShopTab) {
        this.tab = tab
        sel = defaultSel()
        feedback.sfx(Sfx.Swoosh)
        applyPreview()
    }

    fun selectItem(id: String) {
        sel = id
        feedback.sfx(Sfx.Swoosh)
        applyPreview()
    }

    /** `shopAction` click. */
    fun action() {
        val kind = tab.kind
        if (kind == null) {
            buyUpgrade(sel)
            return
        }
        val item = Catalog.find(kind, sel) ?: return
        if (progress.owns(kind, item.id)) {
            progress.select(kind, item.id)
        } else if (progress.buy(kind, item.id)) {
            feedback.celebrateLater(400)
            feedback.sfx(Sfx.PowerUp(PowerType.Star))
            val colors = when {
                item is SkinItem -> listOf(item.body)
                item is TrailItem && item.colors.isNotEmpty() -> item.colors
                else -> listOf(skin().body)
            }
            feedback.burst(MenuBurst.Item(colors))
        }
        applyPreview()
    }

    fun buyUpgrade(id: String) {
        if (progress.buyUpgrade(id)) {
            feedback.celebrateLater(400)
            feedback.sfx(Sfx.PowerUp(PowerType.Star))
            feedback.burst(MenuBurst.Upgrade(PowerType.entries.firstOrNull { it.id == id }?.color ?: 0x7be07b))
        }
        applyPreview()
    }

    /** The dice tab: a random owned item per bird kind, equipped. */
    fun dice() {
        for (kind in DICE_KINDS) {
            val owned = Catalog.items(kind).filter { progress.owns(kind, it.id) }
            if (owned.isEmpty()) continue
            progress.select(kind, owned[random.nextInt(owned.size)].id)
        }
        feedback.sfx(Sfx.PowerUp(PowerType.Star))
        feedback.burst(MenuBurst.Dice(skin().body))
        sel = defaultSel()
        applyPreview()
    }

    fun diceEnabled(): Boolean = !DICE_KINDS.all { k -> Catalog.items(k).count { progress.owns(k, it.id) } < 2 }

    fun surprisePool(): List<Pair<Kind, CatalogItem>> = SURPRISE_KINDS.flatMap { kind ->
        Catalog.items(kind).filter { it.price in 1..SURPRISE_MAX && !progress.owns(kind, it.id) }.map { kind to it }
    }

    fun surprise() {
        val pool = surprisePool()
        if (pool.isEmpty() || !progress.buySurprise(SURPRISE_PRICE)) return
        val (kind, item) = pool[random.nextInt(pool.size)]
        progress.grant(kind, item.id)
        tab = ShopTab.of(kind)
        sel = item.id
        feedback.sfx(Sfx.PowerUp(PowerType.Star))
        feedback.burst(MenuBurst.Confetti)
        feedback.toast(t("surpriseGot", "name" to L(item.name)))
        feedback.celebrateLater(400)
        applyPreview()
    }

    // --- rewarded ads -------------------------------------------------------------------------

    fun requestAd(kind: RewardKind) {
        when (kind) {
            RewardKind.Coins -> {
                if (!adsAvailable || progress.rewardedAdsLeft == 0 || adBusy) return
                adBusy = true
            }
            RewardKind.Pass -> {
                if (!passAvailable || progress.rewardedAdsLeft == 0 || progress.stylePassMinutesLeft > 0 || passBusy) return
                passBusy = true
            }
            RewardKind.Revive -> return // offered by the game-over flow, not the shop
        }
        feedback.effect(UiEffect.ShowRewardedAd(kind))
    }

    fun adResult(kind: RewardKind, earned: Boolean) {
        when (kind) {
            RewardKind.Coins -> {
                if (earned && progress.grantRewardedCoins() > 0) {
                    feedback.toast(t("rewardGranted"))
                    feedback.walletBump()
                } else if (!earned) {
                    feedback.toast(t("rewardUnavailable"))
                }
                adBusy = false
            }
            RewardKind.Pass -> {
                if (earned && progress.grantStylePass()) {
                    feedback.toast(t("stylePassGranted"))
                } else if (!earned) {
                    feedback.toast(t("rewardUnavailable"))
                }
                passBusy = false
            }
            RewardKind.Revive -> return
        }
        applyPreview()
    }

    // --- real money -----------------------------------------------------------------------------

    /** `startRealPurchase(id)`: one purchase in flight at a time. */
    fun buyReal(productId: String) {
        if (billingBusy || !billing.ready || !ProductIds.isPermanent(productId)
            || productId !in billing.products) return
        billingBusy = true
        feedback.effect(UiEffect.LaunchPurchase(productId))
    }

    fun purchaseEnded(failed: Boolean) {
        if (failed) feedback.toast(t("purchaseUnavailable"))
        billingBusy = false
    }

    /** Billing grant callback: re-apply the newly owned cosmetic. */
    fun purchaseGranted(shopOpen: Boolean) {
        feedback.toast(t("purchaseGranted"))
        preview.applyBird()
        if (shopOpen) applyPreview()
    }

    private fun price(productId: String): String? = if (billing.ready) billing.products[productId]?.formattedPrice else null

    private fun removeAdsBuy(): ButtonUi? {
        if (ProductIds.REMOVE_ADS in progress.data.value.paidProducts) return ButtonUi(t("removeAdsOwned"), false)
        val formatted = price(ProductIds.REMOVE_ADS) ?: return null
        return ButtonUi(t("removeAdsBuy", "price" to formatted), !billingBusy)
    }

    // --- 3D preview and snapshot ----------------------------------------------------------------

    /** The 3D part of `renderShop()` / `renderUpgrades()`. */
    fun applyPreview() {
        val kind = tab.kind
        if (kind == null) {
            preview.applyBird()
            preview.previewTrail(trail())
            preview.previewWorld(world())
            preview.pipeStyle(world(), pipe())
            preview.pipePreviewVisible(false)
            return
        }
        val item = Catalog.find(kind, sel) ?: Catalog.items(kind).first()
        val look = equippedLook().let { if (kind in LOOK_KINDS) it + (kind to item.id) else it }
        preview.showBird(if (item is SkinItem) item else skin(), look)
        preview.previewTrail(if (item is TrailItem) item else trail())
        val w = if (item is WorldItem) item else world()
        preview.previewWorld(w)
        preview.pipeStyle(w, if (item is PipeItem) item else pipe())
        preview.pipePreviewVisible(kind == Kind.Pipe)
    }

    /** Thumbnail cache key of a workshop tile (`kind:id:skinId`). */
    fun thumbKey(kind: Kind, id: String): String = "${kind.id}:$id:${skin().id}"

    fun ui(adPrivacy: Boolean): ShopUi {
        val coins = progress.data.value.coins
        val left = progress.rewardedAdsLeft
        val rewardAd = if (!adsAvailable || left == 0) null else ButtonUi(t("rewardAd", "n" to left), !adBusy)
        val passMinutes = progress.stylePassMinutesLeft
        val stylePass = if (passMinutes == 0 && (!passAvailable || left == 0)) null
        else ButtonUi(if (passMinutes > 0) t("stylePassActive", "n" to passMinutes) else t("stylePassAd", "n" to left), passMinutes == 0 && !passBusy)
        val pool = surprisePool()
        val missingSurprise = SURPRISE_PRICE - coins
        val surprise = if (pool.isEmpty()) null else ButtonUi(
            if (missingSurprise > 0) "${t("surprise", "n" to SURPRISE_PRICE)} · ${t("needMore", "n" to missingSurprise)}"
            else t("surprise", "n" to SURPRISE_PRICE),
            missingSurprise <= 0,
        )
        val kind = tab.kind ?: return upgradesUi(coins, rewardAd, stylePass, surprise, adPrivacy)

        val list = Catalog.items(kind)
        val equipped = progress.equipped(kind).id
        val tiles = list.map { k ->
            val owned = progress.owns(kind, k.id)
            ShopTileUi(
                id = k.id,
                name = L(k.name),
                locked = !owned,
                selected = k.id == sel,
                equipped = k.id == equipped,
                rare = (k as? SkinItem)?.rare == true,
                price = k.price,
                thumbKey = if (kind in LOOK_KINDS) thumbKey(kind, k.id) else null,
                closeIcon = (k is TrailItem && k.colors.isEmpty()) || (kind == Kind.Hat && k.id == Catalog.hats[0].id),
                icon = when (k) {
                    is WorldItem -> k.icon
                    else -> null
                },
            )
        }
        val item = list.firstOrNull { it.id == sel } ?: list[0]
        var realBuy: ButtonUi? = null
        var realBuyId: String? = null
        if ((kind == Kind.Skin || kind == Kind.World) && item.price > 0 && !progress.permanentlyOwns(kind, item.id)) {
            val id = "birdy_${kind.id}_${item.id}"
            price(id)?.let {
                realBuy = ButtonUi(t("realBuy", "price" to it), !billingBusy)
                realBuyId = id
            }
        }
        var desc = ""
        if ((item as? SkinItem)?.rare == true) {
            val ach = Achievements.ALL.firstOrNull { it.skin == item.id }
            desc = if (progress.owns(kind, item.id) || ach == null) t("rareOwned") else t("rareOr", "text" to L(ach.text))
        }
        val action: ButtonUi
        val isBuy: Boolean
        if (!progress.owns(kind, item.id)) {
            val missing = item.price - coins
            action = ButtonUi(if (missing > 0) t("needMore", "n" to missing) else t("buy", "n" to item.price), missing <= 0)
            isBuy = true
        } else if (item.id == equipped) {
            action = ButtonUi(t("selected"), false)
            isBuy = false
        } else {
            action = ButtonUi(t("select"), true)
            isBuy = false
        }
        return ShopUi(
            tab = tab, selectedId = item.id, tiles = tiles, name = L(item.name), desc = desc,
            action = action, actionIsBuy = isBuy, diceEnabled = diceEnabled(), surprise = surprise,
            rewardAd = rewardAd, stylePass = stylePass, adPrivacy = adPrivacy,
            realBuy = realBuy, realBuyProductId = realBuyId, removeAdsBuy = removeAdsBuy(), billingBusy = billingBusy,
        )
    }

    private fun upgradesUi(
        coins: Int, rewardAd: ButtonUi?, stylePass: ButtonUi?, surprise: ButtonUi?, adPrivacy: Boolean,
    ): ShopUi {
        val tiles = Catalog.upgrades.map { u ->
            ShopTileUi(
                id = u.id, name = L(u.name), locked = false, selected = u.id == sel, equipped = false, rare = false,
                price = 0, icon = u.icon, level = progress.level(u.id), maxLevel = Catalog.UPGRADE_MAX,
            )
        }
        val u = Catalog.upgrades.firstOrNull { it.id == sel } ?: Catalog.upgrades[0]
        val lvl = progress.level(u.id)
        val price = progress.upgradePrice(u.id)
        val action = if (price == null) ButtonUi(t("maxed"), false)
        else ButtonUi(if (coins < price) t("needMore", "n" to price - coins) else t("upgrade", "n" to price), coins >= price)
        return ShopUi(
            tab = tab, selectedId = u.id, tiles = tiles, name = L(u.name),
            desc = "${L(u.text)} · ${t("level", "n" to lvl, "max" to Catalog.UPGRADE_MAX)}",
            action = action, actionIsBuy = price != null, diceEnabled = diceEnabled(), surprise = surprise,
            rewardAd = rewardAd, stylePass = stylePass, adPrivacy = adPrivacy,
            realBuy = null, realBuyProductId = null, removeAdsBuy = removeAdsBuy(), billingBusy = billingBusy,
        )
    }

    companion object {
        const val SURPRISE_PRICE = 150
        const val SURPRISE_MAX = 900
        val SURPRISE_KINDS = listOf(Kind.Skin, Kind.Pattern, Kind.Hat, Kind.Eyes, Kind.Beak, Kind.Trail, Kind.Pipe)
        val DICE_KINDS = listOf(Kind.Skin) + LOOK_KINDS + Kind.Trail
    }
}
