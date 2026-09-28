package de.robinrehbein.birdy.shots

import de.robinrehbein.birdy.audio.NullAudioOut
import de.robinrehbein.birdy.audio.SilentAudio
import de.robinrehbein.birdy.engine.RenderBackend
import de.robinrehbein.birdy.engine.RenderTarget
import de.robinrehbein.birdy.game.BirdyGame
import de.robinrehbein.birdy.game.GameMode
import de.robinrehbein.birdy.game.UiCommand
import de.robinrehbein.birdy.game.UiState
import de.robinrehbein.birdy.game.loop.GameViews
import de.robinrehbein.birdy.meta.LocalProgressRepository
import de.robinrehbein.birdy.meta.TableStrings
import de.robinrehbein.birdy.platform.Ads
import de.robinrehbein.birdy.platform.AdsStatus
import de.robinrehbein.birdy.platform.Billing
import de.robinrehbein.birdy.platform.BillingStatus
import de.robinrehbein.birdy.platform.FakeClock
import de.robinrehbein.birdy.platform.LocalDay
import de.robinrehbein.birdy.platform.MemoryKeyValueStore
import de.robinrehbein.birdy.platform.NoHaptics
import de.robinrehbein.birdy.platform.PlatformServices
import de.robinrehbein.birdy.platform.RewardKind
import de.robinrehbein.birdy.platform.StorageKeys
import de.robinrehbein.birdy.platform.StoreProduct
import de.robinrehbein.birdy.platform.StorePurchase
import de.robinrehbein.birdy.view.bird.BirdView
import de.robinrehbein.birdy.view.fx.FxView
import de.robinrehbein.birdy.view.thumb.ThumbnailRenderer
import de.robinrehbein.birdy.view.world.WorldView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.random.Random

/** The save the JS store shots use (scripts/store-shots.mjs), so both sides show the same player. */
const val STORE_SAVE = """{"coins":2400,"best":32,"runs":6,"tutorialDone":true,"achieved":["score10","score25","unlock5"],""" +
    """"items":{"skin":["sunny","sky","cardinal"],"hat":["none","party","crown"],"eyes":["normal","shades"],"trail":["none","sparkle"]},""" +
    """"equip":{"skin":"sunny","hat":"party","eyes":"shades","trail":"sparkle"}}"""

/** Rewarded ads that are always loaded (for the shop's ad buttons). */
class ReadyAds : Ads {
    override val status: StateFlow<AdsStatus> = MutableStateFlow(AdsStatus(true, setOf(RewardKind.Coins, RewardKind.Pass), false))
    override fun init() = Unit
    override fun showRewarded(kind: RewardKind, onResult: (earned: Boolean) -> Unit) = onResult(true)
    override fun showPrivacyOptions() = Unit
}

/** A store that knows prices for the coin packs and the paid skins/worlds. */
class PricedBilling : Billing {
    private val ids = listOf("birdy_coins_500" to "0,99 €", "birdy_coins_1500" to "2,49 €") +
        listOf("sky", "cardinal", "robin", "mint", "coral", "flamingo", "parrot", "penguin", "night", "snowy", "peacock", "gold").map { "birdy_skin_$it" to "1,99 €" } +
        listOf("winter", "beach", "candy", "mushroom").map { "birdy_world_$it" to "1,99 €" }
    override val status: StateFlow<BillingStatus> =
        MutableStateFlow(BillingStatus(true, true, ids.associate { (id, p) -> id to StoreProduct(id, p) }))
    override fun init(productIds: List<String>, onPurchase: (StorePurchase) -> Unit) = Unit
    override fun refresh() = Unit
    override fun launchPurchase(productId: String) = Unit
    override fun consume(token: String, onDone: (ok: Boolean) -> Unit) = onDone(true)
    override fun acknowledge(token: String, onDone: (ok: Boolean) -> Unit) = onDone(true)
}

/**
 * One deterministic game for a scripted scene: in-memory storage with [save], a pinned clock
 * (2026-09-28), seeded randomness, silent audio and the real world/bird/fx views. Advances in
 * 1/30 s steps like JS `advance()`.
 */
class ShotGame(
    renderer: RenderBackend,
    save: String = STORE_SAVE,
    lang: String = "de",
    seed: Int = 4242,
    store: Boolean = false,
) {
    val storage = MemoryKeyValueStore(mapOf(StorageKeys.PROGRESS to save, StorageKeys.LANG to lang, StorageKeys.MIGRATED to "1"))
    val clock = FakeClock(millis = 1_790_000_000_000L, day = LocalDay(2026, 9, 28))
    val services = PlatformServices(
        storage, clock, NoHaptics, NullAudioOut,
        ads = if (store) ReadyAds() else null,
        billing = if (store) PricedBilling() else null,
        deviceLanguage = if (lang == "de") "de-DE" else "en-US",
    )
    val progress = LocalProgressRepository(storage, clock)
    val strings = TableStrings(storage, services.deviceLanguage)
    val bird = BirdView()
    val world = WorldView(Random(seed))
    val fx = FxView(bird, Random(seed + 1))
    val game = BirdyGame(
        services, renderer, progress, strings, SilentAudio(), Random(seed + 2),
        GameViews(world, bird, fx, ThumbnailRenderer(renderer)), Random(seed + 3),
    )

    init {
        game.displayDensity = Shots.DENSITY.toDouble()
        game.onSurfaceChanged(Shots.WIDTH, Shots.HEIGHT)
        game.flush()
    }

    val sim get() = game.sim
    val state get() = game.sim.state
    val ui: UiState get() = game.ui.value

    fun post(cmd: UiCommand) {
        game.post(cmd)
        game.flush()
    }

    fun advance(seconds: Double) = game.advance(seconds)

    /** store-shots.mjs `__run(sec, until)`: autopilot + advance in 1/30 s steps. */
    fun run(seconds: Double, until: (() -> Boolean)? = null): Boolean {
        var t = 0.0
        while (t < seconds) {
            sim.debug.pilotStep()
            game.advance(1.0 / 30)
            if (until != null && until()) return true
            t += 1.0 / 30
        }
        return until == null
    }

    /** Play button, first flap and god mode (store-shots: `#play-btn`, a tap, `state.god`). */
    fun startRun(god: Boolean = true) {
        post(UiCommand.Play)
        post(UiCommand.Touch(0.5f, 0.6f))
        post(UiCommand.TouchUp)
        sim.debug.setGod(god)
        check(state.mode == GameMode.Playing && !state.hold)
    }

    fun render(target: RenderTarget) = game.renderTo(target)
}
