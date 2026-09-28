package de.robinrehbein.birdy.game

import de.robinrehbein.birdy.meta.Kind
import de.robinrehbein.birdy.meta.Lang
import de.robinrehbein.birdy.meta.ProgressData
import de.robinrehbein.birdy.platform.AdsStatus
import de.robinrehbein.birdy.platform.BillingStatus
import de.robinrehbein.birdy.platform.RewardKind

/**
 * Immutable snapshot the Compose overlay renders. Published by [BirdyGame] from the game thread
 * (only when something changed) through a StateFlow; the UI never touches [GameState] directly.
 *
 * Texts that main.js builds with `t()` are delivered ready-made in the current language (they may
 * contain `[icon]` tags and `<br>`/`<b>` markup, render with RichText). Positions are screen
 * fractions (0..1 of the overlay width/height). Serial counters (`*Pop`, `*Bump`, `id`) restart
 * one-shot CSS-like animations when they change; `age` fields (seconds since the animation
 * started) let a stateless renderer (headless screenshots) draw the right keyframe.
 */
data class UiState(
    val mode: GameMode = GameMode.Ready,
    val menu: Menu = Menu.Start,
    val paused: Boolean = false,
    /** Waiting for the first tap of a run (tap-zone outlines shown). */
    val hold: Boolean = false,
    val score: Int = 0,
    val runCoins: Int = 0,
    val lane: Int = 1,
    /** Remaining fraction 0..1 per [PowerType] ordinal, for the HUD chips. */
    val power: List<Float> = List(PowerType.entries.size) { 0f },
    val toast: ToastUi? = null,
    /** Full-screen flash overlay (impact / power-up), alpha 0..1 and sRGB colour. */
    val flashAlpha: Float = 0f,
    val flashColor: Int = 0xffffff,
    val tutorialHand: HandUi? = null,
    val zonesHint: ZonesHintUi? = null,
    val gameOver: RunSummary? = null,
    /** Shop tab currently previewed on the 3D bird. */
    val shopKind: Kind = Kind.Skin,
    val progress: ProgressData = ProgressData(),
    val lang: Lang = Lang.DE,
    val muted: Boolean = false,
    val ads: AdsStatus = AdsStatus(),
    val billing: BillingStatus = BillingStatus(),
    val showFps: Boolean = false,
    val fps: Int = 0,

    // --- extensions (game-loop task) ------------------------------------------------------------

    /** False until storage migration and progress loading finished (keep the splash up). */
    val booted: Boolean = false,
    /** Zone index of the run (0-based; the pause screen shows zone + 1). */
    val zone: Int = 0,
    /** Per [PowerType] ordinal: chip visible (time left > 0). */
    val powerActive: List<Boolean> = List(PowerType.entries.size) { false },
    /** Per [PowerType] ordinal: less than 1.5 s left (`.ending` blink). */
    val powerEnding: List<Boolean> = List(PowerType.entries.size) { false },
    /** Incremented on every point (`#score.pop`). */
    val scorePop: Int = 0,
    /** Incremented on every coin / near-miss coin (`#coins.bump`). */
    val coinBump: Int = 0,
    /** Incremented by `renderWallet(true)` (`#wallet.bump`). */
    val walletBump: Int = 0,
    /** Near-miss "Knapp!" popup (0.8 s). */
    val popup: PopupUi? = null,
    /** Zone-change banner (2.4 s). */
    val zoneBanner: ZoneBannerUi? = null,
    /** Tap ripples (pool of 4, 0.35 s each). */
    val tapFx: List<TapFxUi> = emptyList(),
    val start: StartMenuUi = StartMenuUi(),
    val achievements: AchievementsUi = AchievementsUi(),
    /** Non-null while the shop is open. */
    val shop: ShopUi? = null,
    /** Game-over screen details (non-null in mode Over). */
    val gameOverUi: GameOverUi? = null,
    /** Shop part thumbnails (ARGB_8888, top row first) keyed `kind:id:skinId`. */
    val thumbnails: Map<String, ThumbnailUi> = emptyMap(),
    /** Incremented whenever [thumbnails] gained entries. */
    val thumbnailVersion: Int = 0,
    /** Persisted quality tier 0..4 (dev overlay `Q{n}`). */
    val quality: Int = 0,
    /** Draw calls of the last frame (dev overlay). */
    val drawCalls: Int = 0,
    /** Ready-made dev overlay text `"{fps} fps · {dc} dc · Q{q}"`, null while hidden. */
    val fpsText: String? = null,
)

/** A toast line (i18n key + params, may contain [icon] tags); [id] restarts the animation. */
data class ToastUi(val id: Int, val text: String, val age: Float = 0f)

/**
 * Ghost-hand tutorial overlay in screen fractions (main-b.md §9). [x]/[y] anchor the hand and
 * its label under the bird; the hand itself is drawn [dx] to the side (the "side" swipe
 * animation slides it sideways) with opacity [alpha]. [mode] "flap" = tap at the bird,
 * "side" = swipe sideways starting at the bird.
 */
data class HandUi(
    val x: Float,
    val y: Float,
    val mode: String,
    val label: String = "",
    val dx: Float = 0f,
    val alpha: Float = 1f,
)

/**
 * Zone-boundary lane hint overlay (main-b.md §10); lane edges as screen-x fractions.
 * [flash] = the one-shot `show` fade (restarted when [serial] changes), [hold] = the pulsing
 * pre-flap state. [key] is the memo key `b1|b2|lane` (3 decimals); columns only change with it.
 */
data class ZonesHintUi(
    val key: String,
    val edges: List<Float>,
    val flash: Boolean,
    val hold: Boolean = false,
    val lane: Int = 1,
    val serial: Int = 0,
    /** Labels: `▲ zoneFlap` on the bird's column, `◀`/`▶ zoneMove` (swipe that way) on the others. */
    val flapLabel: String = "",
    val moveLabel: String = "",
    /** Seconds since the `show` animation (re)started; 0 while holding. */
    val age: Float = 0f,
)

/** "Knapp!" popup at the bird (text includes the `×N` chain). */
data class PopupUi(val id: Int, val text: String, val x: Float, val y: Float, val age: Float)

/** Zone banner: small localized zone line + big biome name. */
data class ZoneBannerUi(val id: Int, val zoneLabel: String, val name: String, val age: Float)

/** Touch ripple: ▲ ([dir] 0) at the bird on touch, ◀/▶ (-1/+1) at the new lane after a sideways swipe. */
data class TapFxUi(val id: Int, val x: Float, val y: Float, val dir: Int, val age: Float)

/** One mission row (`missionHTML`); [isNew] = just completed (pop-in). */
data class MissionUi(
    val id: String,
    val text: String,
    val reward: Int,
    val progress: Int,
    val goal: Int,
    val done: Boolean,
    val isNew: Boolean = false,
) {
    /** `round(progress / goal * 100)`. */
    val percent: Int get() = jsRound(progress * 100.0 / goal)
}

/** Start menu (`renderStart`). */
data class StartMenuUi(
    val best: Int = 0,
    /** `runs < 2`: show the how-to instead of the missions. */
    val firstRuns: Boolean = true,
    val missions: List<MissionUi> = emptyList(),
    /** Gift button label (rich), null when hidden. */
    val giftLabel: String? = null,
    /** Streak line (rich), null when hidden. */
    val streakLabel: String? = null,
    /** UMP requires a privacy-options entry point. */
    val adPrivacy: Boolean = false,
)

/** One achievement row (`renderAchievements`). */
data class AchievementUi(
    val id: String,
    val icon: String,
    val name: String,
    val text: String,
    /** `skinReward` line for rare achievements, else null. */
    val skinReward: String?,
    val value: Int,
    val goal: Int,
    val reward: Int,
    val done: Boolean,
) {
    val percent: Int get() = jsRound(value * 100.0 / goal)
}

data class AchievementsUi(val list: List<AchievementUi> = emptyList()) {
    val doneCount: Int get() = list.count { it.done }
    /** `#ach-count`: `"{done} / {total}"`. */
    val countText: String get() = "$doneCount / ${list.size}"
}

/** Shop tabs in `SHOP_TABS` order ([kind] null = upgrades). The dice is [UiCommand.RandomizeOutfit]. */
enum class ShopTab(val id: String, val icon: String, val kind: Kind?) {
    Skin("skin", "palette", Kind.Skin),
    Pattern("pattern", "paw", Kind.Pattern),
    Hat("hat", "tophat", Kind.Hat),
    Eyes("eyes", "glasses", Kind.Eyes),
    Beak("beak", "beak", Kind.Beak),
    Trail("trail", "sparkle", Kind.Trail),
    World("world", "globe", Kind.World),
    Pipe("pipe", "pipe", Kind.Pipe),
    Upgrade("upgrade", "bolt", null);

    companion object {
        fun of(kind: Kind): ShopTab = entries.first { it.kind == kind }
    }
}

/** A button's label (rich text) and enabled state. */
data class ButtonUi(val label: String, val enabled: Boolean = true)

/**
 * One grid tile. Colours/backgrounds come from the catalog item (`tileBg`, main-b.md §7.2);
 * [thumbKey] points into [UiState.thumbnails] for pattern/hat/eyes/beak. Upgrade tiles have
 * [level] pips out of [maxLevel] and their [icon].
 */
data class ShopTileUi(
    val id: String,
    val name: String,
    val locked: Boolean,
    val selected: Boolean,
    val equipped: Boolean,
    val rare: Boolean,
    val price: Int,
    val thumbKey: String? = null,
    /** Show a close/✕ icon (trail "none", hat "none"). */
    val closeIcon: Boolean = false,
    val icon: String? = null,
    val level: Int = 0,
    val maxLevel: Int = 0,
)

/** Coin pack button (only products the store resolved a price for). */
data class CoinPackUi(val productId: String, val coins: Int, val label: String, val enabled: Boolean)

/** Everything `renderShop()` / `renderUpgrades()` shows. */
data class ShopUi(
    val tab: ShopTab,
    val selectedId: String,
    val tiles: List<ShopTileUi>,
    val name: String,
    /** Description (rich; rare text or upgrade level), empty when none. */
    val desc: String,
    /** Buy/select/upgrade button; [actionIsBuy] = gold "buy" style with coin icon. */
    val action: ButtonUi,
    val actionIsBuy: Boolean,
    val diceEnabled: Boolean,
    /** Surprise button, null when the pool is empty. */
    val surprise: ButtonUi?,
    val rewardAd: ButtonUi?,
    val stylePass: ButtonUi?,
    val adPrivacy: Boolean,
    /** Real-money buy for the selected skin/world; [realBuyProductId] is what to request. */
    val realBuy: ButtonUi?,
    val realBuyProductId: String?,
    val coinPacks: List<CoinPackUi>,
    val billingBusy: Boolean,
)

/** Game-over details (`showGameOver`, `renderNextUnlock`). */
data class GameOverUi(
    val score: Int,
    val coins: Int,
    val best: Int,
    val newBest: Boolean,
    /** "so close" nudge, null when hidden. */
    val toBest: String?,
    /** Unlocked achievements: (`[icon] achUnlocked` text, reward), shown first. */
    val achievementLines: List<Pair<String, Int>>,
    val missions: List<MissionUi>,
    /** `zoneReached`, null in zone 1. */
    val zoneReached: String?,
    val nextUnlock: NextUnlockUi?,
    /** Wallet chip carries the `.over` class (hidden on short screens). */
    val walletOver: Boolean = true,
)

/** The cheapest cosmetic not owned yet; [ready] = affordable (glowing shortcut into the shop). */
data class NextUnlockUi(val kind: Kind, val id: String, val text: String, val percent: Int, val ready: Boolean)

/** A rendered shop thumbnail; [argb] is shared with the cache (do not mutate). */
class ThumbnailUi(val size: Int, val argb: IntArray)

/**
 * Input from the UI thread to the game thread. Posted with [BirdyGame.post] (thread-safe) and
 * applied at the start of the next frame, in order.
 */
sealed class UiCommand {
    /** Touch down on the 3D surface at screen fractions (0..1): flaps in the current lane wherever it lands. */
    data class Touch(val x: Float, val y: Float) : UiCommand()
    /** Horizontal swipe of the current touch: -1 left, +1 right (one lane, sticks until the next swipe). */
    data class Swipe(val direction: Int) : UiCommand()
    data object Back : UiCommand()
    data class SetPaused(val paused: Boolean) : UiCommand()
    data object Restart : UiCommand()
    data object GoToMenu : UiCommand()
    data class OpenShop(val open: Boolean) : UiCommand()
    data class OpenAchievements(val open: Boolean) : UiCommand()
    data class ShopTab(val kind: Kind) : UiCommand()
    /**
     * Shop tile tap: selects the tile and tries it on the 3D bird (JS `shopSel`); buying or
     * equipping is [ShopAction]. On the upgrades tab [id] is an upgrade id ([kind] ignored).
     */
    data class ShopItem(val kind: Kind, val id: String) : UiCommand()
    data class BuyUpgrade(val id: String) : UiCommand()
    data class Surprise(val kind: Kind) : UiCommand()
    data object RandomizeOutfit : UiCommand()
    data object ClaimGift : UiCommand()
    data class RewardEarned(val kind: RewardKind) : UiCommand()
    data class SetMuted(val muted: Boolean) : UiCommand()
    data class SetLang(val lang: Lang) : UiCommand()
    data object ToggleFps : UiCommand()
    /** App went to background / foreground (visibilitychange). */
    data class AppVisible(val visible: Boolean) : UiCommand()

    // --- extensions (game-loop task) ------------------------------------------------------------

    /** Pointer moved while down (screen fractions); the game detects sideways swipes. */
    data class TouchMove(val x: Float, val y: Float) : UiCommand()
    /** Pointer up / cancel. */
    data object TouchUp : UiCommand()
    /** "Los geht's" button / free area of the start screen: starts a run. */
    data object Play : UiCommand()
    /** Tap on the pause screen (outside "Menü") or "Weiter". */
    data object Resume : UiCommand()
    /** Tap on the game-over screen outside the buttons: retry after the 350 ms debounce. */
    data object GameOverTap : UiCommand()
    /** Tap on the glowing next-unlock bar: straight into the shop at that item. */
    data object NextUnlock : UiCommand()
    /** Shop tab including upgrades. */
    data class SelectShopTab(val tab: de.robinrehbein.birdy.game.ShopTab) : UiCommand()
    /** The shop's main button (buy / select / upgrade) for the selected tile. */
    data object ShopAction : UiCommand()
    /** Reward-ad or style-pass button: the game checks the guards and emits [UiEffect.ShowRewardedAd]. */
    data class RequestRewardedAd(val kind: RewardKind) : UiCommand()
    /** Result of [UiEffect.ShowRewardedAd] (earned or not). */
    data class RewardResult(val kind: RewardKind, val earned: Boolean) : UiCommand()
    /** Ad-privacy button: emits [UiEffect.ShowPrivacyOptions]. */
    data object AdPrivacy : UiCommand()
    /** Coin pack / real-money item button: emits [UiEffect.LaunchPurchase] unless one is in flight. */
    data class BuyReal(val productId: String) : UiCommand()
    /** The shell's purchase flow ended; [failed] shows `purchaseUnavailable`. */
    data class PurchaseEnded(val failed: Boolean) : UiCommand()
    /** Entitlement granted (after the shell saved it): toast, wallet bump, re-apply the bird. */
    data class PurchaseGranted(val coins: Int) : UiCommand()
    /**
     * Measured menu layout (fractions of the overlay height): bottom of the menu title and top
     * of the panel for [menu] (`measureMenuFrame`). Drives the 3D bird framing.
     */
    data class MenuFrame(val menu: Menu, val titleBottom: Float, val panelTop: Float) : UiCommand()
    /** Tap on the start title (5 quick taps toggle the dev FPS overlay). */
    data object TitleTap : UiCommand()
}

/** One-off requests from the game to the app shell (need an Activity). */
sealed class UiEffect {
    data object ExitApp : UiEffect()
    data class ShowRewardedAd(val kind: RewardKind) : UiEffect()
    data class LaunchPurchase(val productId: String) : UiEffect()
    data object ShowPrivacyOptions : UiEffect()
}

/** JS `Math.round` (half up, also for negatives). */
internal fun jsRound(v: Double): Int = kotlin.math.floor(v + 0.5).toInt()
