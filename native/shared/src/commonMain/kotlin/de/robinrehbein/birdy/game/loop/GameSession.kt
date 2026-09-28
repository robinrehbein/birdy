package de.robinrehbein.birdy.game.loop

import de.robinrehbein.birdy.audio.GameAudio
import de.robinrehbein.birdy.audio.MusicMode
import de.robinrehbein.birdy.audio.Sfx
import de.robinrehbein.birdy.engine.RenderBackend
import de.robinrehbein.birdy.engine.scene.PerspectiveCamera
import de.robinrehbein.birdy.engine.scene.Scene
import de.robinrehbein.birdy.game.GameEvent
import de.robinrehbein.birdy.game.GameMode
import de.robinrehbein.birdy.game.GameSimulation
import de.robinrehbein.birdy.game.GameOverUi
import de.robinrehbein.birdy.game.Menu
import de.robinrehbein.birdy.game.PowerType
import de.robinrehbein.birdy.game.RunSummary
import de.robinrehbein.birdy.game.ShopTab
import de.robinrehbein.birdy.game.ShopUi
import de.robinrehbein.birdy.game.StartMenuUi
import de.robinrehbein.birdy.game.AchievementsUi
import de.robinrehbein.birdy.game.ThumbnailUi
import de.robinrehbein.birdy.game.UiCommand
import de.robinrehbein.birdy.game.UiEffect
import de.robinrehbein.birdy.game.UiState
import de.robinrehbein.birdy.game.WorldConst
import de.robinrehbein.birdy.meta.Kind
import de.robinrehbein.birdy.meta.LocalizedText
import de.robinrehbein.birdy.meta.Missions
import de.robinrehbein.birdy.meta.PipeItem
import de.robinrehbein.birdy.meta.ProgressData
import de.robinrehbein.birdy.meta.ProgressRepository
import de.robinrehbein.birdy.meta.Settings
import de.robinrehbein.birdy.meta.SkinItem
import de.robinrehbein.birdy.meta.Strings
import de.robinrehbein.birdy.meta.TrailItem
import de.robinrehbein.birdy.meta.WorldItem
import de.robinrehbein.birdy.platform.AdsStatus
import de.robinrehbein.birdy.platform.BillingStatus
import de.robinrehbein.birdy.platform.PlatformServices
import de.robinrehbein.birdy.view.FrameInfo
import de.robinrehbein.birdy.view.SceneView
import de.robinrehbein.birdy.view.bird.BirdLook
import de.robinrehbein.birdy.view.bird.BirdView
import de.robinrehbein.birdy.view.fx.Bursts
import de.robinrehbein.birdy.view.fx.FxView
import de.robinrehbein.birdy.view.thumb.ThumbnailRenderer
import de.robinrehbein.birdy.view.world.Biome
import de.robinrehbein.birdy.view.world.PipePreview
import de.robinrehbein.birdy.view.world.WorldPicks
import de.robinrehbein.birdy.view.world.WorldView
import kotlin.math.min
import kotlin.random.Random

/** The visual layers the game drives besides generic [SceneView]s (all optional for tests). */
class GameViews(
    val world: WorldView? = null,
    val bird: BirdView? = null,
    val fx: FxView? = null,
    val thumbs: ThumbnailRenderer? = null,
) {
    val layers: List<SceneView> get() = listOfNotNull(world, bird, fx)
}

/**
 * Everything main.js does around the simulation once storage is ready: input mapping, command
 * handling, [GameEvent] routing (audio, haptics, toasts, flash, HUD animations, fx), menus and
 * the shop, camera, quality and the [UiState] snapshot. Owned by [de.robinrehbein.birdy.game.BirdyGame];
 * game thread only.
 */
class GameSession(
    private val services: PlatformServices,
    private val renderer: RenderBackend?,
    val strings: Strings,
    val progress: ProgressRepository,
    val audio: GameAudio,
    val views: GameViews,
    val scene: Scene,
    val camera: PerspectiveCamera,
    simRandom: Random,
    private val uiRandom: Random,
    private val emitEffect: (UiEffect) -> Unit,
) {
    private val pending = ArrayList<GameEvent>()
    val sim = GameSimulation(progress, simRandom) { pending += it }
    val settings = Settings(services.storage)
    val rig = CameraRig(camera, uiRandom)
    val input = InputMapper(rig)
    val toasts = ToastQueue()
    val scheduler = Scheduler()
    val flash = Flash()
    val hud = HudFx()
    private val zones = ZonesOverlay()
    val quality = QualityController(settings, renderer)
    val texts = MenuTexts(progress, strings)
    private val extraViews = ArrayList<SceneView>()
    private var pipePreview: PipePreview? = null
    val shop: ShopController

    /** Monotonic ms of real (unclamped) time, the `performance.now()` of this session. */
    var realTimeMs = 0.0
        private set
    var surfaceWidth = 1
        private set
    var surfaceHeight = 1
        private set
    var density = 1.0
        set(value) {
            field = value
            quality.density = value
        }

    private val menuFrames = HashMap<Menu, MenuFrameInfo>()
    private var showFps = settings.fpsOverlay
    private val secretTaps = SecretTaps()
    private var scorePop = 0
    private var coinBump = 0
    private var walletBump = 0
    private var summary: RunSummary? = null
    private var gameOverUi: GameOverUi? = null
    private val thumbnails = HashMap<String, ThumbnailUi>()
    private var thumbnailVersion = 0

    // Menu snapshots are rebuilt only when something they show may have changed.
    private var menuDirty = true
    private var lastData: ProgressData? = null
    private var lastAds: AdsStatus? = null
    private var lastBilling: BillingStatus? = null
    private var lastLang = strings.lang.value
    private var shopRefresh = 0.0
    private var startUi = StartMenuUi()
    private var achUi = AchievementsUi()
    private var shopUi: ShopUi? = null
    private var trailOf: ProgressData? = null

    private val preview = object : ShopPreview {
        override fun applyBird() = this@GameSession.applyBird()
        override fun showBird(skin: SkinItem, look: Map<Kind, String>) {
            views.bird?.setSkin(skin)
            views.bird?.setLook(lookOf(look))
        }
        override fun previewTrail(trail: TrailItem?) {
            views.fx?.previewTrail = trail
        }
        override fun previewWorld(world: WorldItem) {
            val w = views.world ?: return
            val b = Biome.of(world)
            if (w.biomes.current === b) return
            w.biomes.set(0, 0.5, b)
            w.scenery.setTheme(world.scenery, true)
        }
        override fun pipeStyle(world: WorldItem, pipe: PipeItem) {
            views.world?.setPipeStyle(WorldPicks.pipeFor(world, pipe))
        }
        override fun pipePreviewVisible(visible: Boolean) {
            pipePreview?.group?.visible = visible
        }
    }

    private val feedback = object : ShopFeedback {
        override fun sfx(effect: Sfx) = audio.sfx(effect)
        override fun toast(text: String) = toasts.push(text)
        override fun burst(burst: MenuBurst) = this@GameSession.burst(burst)
        override fun celebrateLater(delayMs: Int) = scheduler.after(delayMs) { celebrateMenuAchievements() }
        override fun walletBump() {
            walletBump++
        }
        override fun effect(effect: UiEffect) = emitEffect(effect)
    }

    init {
        shop = ShopController(progress, strings, preview, feedback, uiRandom)
        sim.clockMillis = { realTimeMs.toLong() }
        views.world?.attach(scene)
        views.bird?.attach(scene)
        views.fx?.attach(scene)
        views.world?.let { w ->
            w.boot(progress)
            // Beside the bird as seen by the shop camera.
            pipePreview = w.createPipePreview().apply {
                group.position.set(-2.4f, 0f, 2.6f)
                setGap(3.3, 6.7)
            }
        }
        applyBird()
        audio.setMode(MusicMode.Menu) // calm version until the first run starts
        if (!progress.data.value.tutorialDone) {
            // First launch: straight into the guided first run.
            sim.startTutorialRun()
        } else {
            // Existing saves: pay out achievements already earned before they existed.
            scheduler.after(800) { celebrateMenuAchievements() }
        }
        routeEvents()
    }

    fun addView(view: SceneView) {
        extraViews += view
        view.attach(scene)
    }

    fun resize(width: Int, height: Int) {
        surfaceWidth = width.coerceAtLeast(1)
        surfaceHeight = height.coerceAtLeast(1)
        rig.resize(surfaceWidth, surfaceHeight)
    }

    // --- per frame --------------------------------------------------------------------------

    /** Real-time parts of a tick (timers, CSS-like animations, quality windows). */
    fun tickRealtime(rawDt: Double) {
        realTimeMs += rawDt * 1000
        val s = sim.state
        quality.tick(rawDt, s.mode == GameMode.Playing && !s.paused)
        scheduler.advance(rawDt)
        flash.update(rawDt)
        hud.update(rawDt)
        zones.tick(rawDt)
        shopRefresh += rawDt
        if (shopRefresh >= 60) {
            // The style-pass countdown ticks while the shop is open.
            shopRefresh = 0.0
            menuDirty = true
        }
        routeEvents()
    }

    /** main.js `update(rawDt)` (skipped by the caller while paused). [beat] null = game time. */
    fun update(rawDt: Double, beat: Double?) {
        val dt = min(rawDt, 1.0 / 30)
        val hitStop = sim.state.hitStop > 0
        sim.update(rawDt, beat)
        routeEvents()
        if (!hitStop) toasts.update(dt)
        syncTrail()
        val info = FrameInfo(dt, sim.state.time, sim.state.beat, sim, camera)
        for (v in views.layers) v.update(info)
        for (v in extraViews) v.update(info)
        val s = sim.state
        rig.update(dt, s, if (s.mode == GameMode.Ready) menuFrames[s.menu] ?: DEFAULT_FRAMES[s.menu] else null)
    }

    fun render(target: de.robinrehbein.birdy.engine.RenderTarget? = null) {
        renderer?.render(scene, camera, target)
    }

    // --- commands -----------------------------------------------------------------------------

    private fun startAudio() {
        audio.unlock()
        audio.musicStart()
    }

    fun handle(cmd: UiCommand) {
        val s = sim.state
        menuDirty = true
        when (cmd) {
            is UiCommand.Touch -> pointerDown(cmd.x.toDouble(), cmd.y.toDouble())
            is UiCommand.TouchMove -> {
                val dir = input.move(cmd.x.toDouble(), cmd.y.toDouble(), surfaceWidth.toDouble(), surfaceHeight.toDouble(), density)
                if (dir != 0) swipe(dir)
            }
            is UiCommand.TouchUp -> {
                input.up()
                sim.pointerUp()
            }
            is UiCommand.Swipe -> swipe(cmd.direction)
            is UiCommand.Back -> back()
            is UiCommand.SetPaused -> if (!cmd.paused || s.mode == GameMode.Playing) sim.setPaused(cmd.paused)
            is UiCommand.Resume -> if (s.paused) sim.setPaused(false)
            is UiCommand.Restart, is UiCommand.GameOverTap -> {
                startAudio()
                sim.tryRestart()
            }
            is UiCommand.GoToMenu -> goToMenu()
            is UiCommand.NextUnlock -> {
                startAudio()
                val next = texts.nextUnlock()
                goToMenu()
                openShop(true)
                if (next != null) shop.show(ShopTab.of(next.kind), next.id)
            }
            is UiCommand.Play -> {
                startAudio()
                sim.flap()
            }
            is UiCommand.OpenShop -> {
                if (cmd.open) startAudio()
                openShop(cmd.open)
            }
            is UiCommand.OpenAchievements -> {
                if (cmd.open) {
                    startAudio()
                    audio.sfx(Sfx.Swoosh)
                }
                openAchievements(cmd.open)
            }
            is UiCommand.ShopTab -> shop.selectTab(ShopTab.of(cmd.kind))
            is UiCommand.SelectShopTab -> shop.selectTab(cmd.tab)
            is UiCommand.ShopItem -> shop.selectItem(cmd.id)
            is UiCommand.ShopAction -> shop.action()
            is UiCommand.BuyUpgrade -> shop.buyUpgrade(cmd.id)
            is UiCommand.Surprise -> shop.surprise()
            is UiCommand.RandomizeOutfit -> shop.dice()
            is UiCommand.ClaimGift -> claimGift()
            is UiCommand.RewardEarned -> shop.adResult(cmd.kind, true)
            is UiCommand.RewardResult -> shop.adResult(cmd.kind, cmd.earned)
            is UiCommand.RequestRewardedAd -> shop.requestAd(cmd.kind)
            is UiCommand.AdPrivacy -> emitEffect(UiEffect.ShowPrivacyOptions)
            is UiCommand.BuyReal -> shop.buyReal(cmd.productId)
            is UiCommand.PurchaseEnded -> shop.purchaseEnded(cmd.failed)
            is UiCommand.PurchaseGranted -> shop.purchaseGranted(cmd.coins, s.mode == GameMode.Ready && s.menu == Menu.Shop)
            is UiCommand.SetMuted -> {
                startAudio()
                audio.setMuted(cmd.muted)
            }
            is UiCommand.SetLang -> {
                strings.setLang(cmd.lang)
                zones.invalidate()
                gameOverUi = summary?.let { texts.gameOver(it) }
            }
            is UiCommand.ToggleFps -> toggleFps()
            is UiCommand.TitleTap -> if (secretTaps.tap(realTimeMs)) toggleFps()
            is UiCommand.AppVisible -> visibility(cmd.visible)
            is UiCommand.MenuFrame -> menuFrames[cmd.menu] = MenuFrameInfo.fromLayout(cmd.titleBottom.toDouble(), cmd.panelTop.toDouble())
        }
        routeEvents()
    }

    /** Canvas `pointerdown`: unpause, or lane targeting + tap + swipe tracking + ripple. */
    private fun pointerDown(x: Double, y: Double) {
        startAudio()
        val s = sim.state
        if (s.mode == GameMode.Ready && s.menu != Menu.Start) return // menus cover the canvas
        if (s.mode == GameMode.Over && !s.paused) {
            // The game-over panel covers the canvas: a tap retries.
            sim.tryRestart()
            return
        }
        val lane = input.laneAtScreen(x, s.x, s.y, s.lane)
        val from = s.lane
        val wasPlaying = sim.pointerDown(lane)
        input.down(x, y)
        // Feedback at the lane the bird goes to (the finger would hide it).
        if (wasPlaying) laneFx(from)
    }

    private fun swipe(dir: Int) {
        val before = sim.state.lane
        if (sim.swipe(dir)) laneFx(before)
    }

    private fun laneFx(from: Int) {
        val s = sim.state
        val p = rig.project(WorldConst.LANES[s.lane], s.y, 0.0)
        hud.tap(p[0], p[1], sign(s.lane - from))
    }

    private fun back() {
        val s = sim.state
        when (MenuFlow.backAction(s.mode, s.paused, s.menu)) {
            BackAction.Pause -> sim.setPaused(true)
            BackAction.ToMenu -> goToMenu()
            BackAction.CloseShop -> openShop(false)
            BackAction.CloseAchievements -> openAchievements(false)
            BackAction.Exit -> emitEffect(UiEffect.ExitApp)
        }
    }

    /** Background / foreground (`visibilitychange`). */
    private fun visibility(visible: Boolean) {
        val s = sim.state
        if (!visible) {
            if (s.mode == GameMode.Playing) sim.setPaused(true) else audio.setSuspended(true)
        } else if (!s.paused) {
            audio.setSuspended(false)
        }
    }

    fun goToMenu() {
        sim.goToMenu()
        routeEvents()
    }

    fun openShop(open: Boolean) {
        sim.state.menu = if (open) Menu.Shop else Menu.Start
        shop.open(open)
        menuDirty = true
    }

    fun openAchievements(open: Boolean) {
        sim.state.menu = if (open) Menu.Achievements else Menu.Start
        menuDirty = true
    }

    private fun claimGift() {
        startAudio()
        progress.claimGift() ?: return
        scheduler.after(600) { celebrateMenuAchievements() }
        audio.sfx(Sfx.PowerUp(PowerType.Star))
        for (i in 0 until 5) scheduler.after(120 + i * 70) { audio.sfx(Sfx.Coin) }
        burst(MenuBurst.Gift)
        walletBump++
    }

    /** Achievements earned outside a run (gift streak, unlocks) are paid at once. */
    fun celebrateMenuAchievements() {
        for (a in progress.checkAchievements()) {
            toasts.push("[trophy] ${L(a.name)} +${a.reward}")
            audio.sfx(Sfx.PowerUp(PowerType.Star))
        }
        walletBump++
        menuDirty = true
    }

    private fun toggleFps() {
        showFps = !showFps
        runCatching { settings.fpsOverlay = showFps }
    }

    private fun burst(b: MenuBurst) {
        val fx = views.fx ?: return
        val o = when (b) {
            MenuBurst.Confetti -> Bursts.confetti
            MenuBurst.Gift -> Bursts.celebrate
            is MenuBurst.Dice -> Bursts.skin(progress.equipped(Kind.Skin) as SkinItem)
            is MenuBurst.Upgrade -> Bursts.upgrade(b.color)
            is MenuBurst.Item -> Bursts.item(b.colors)
        }
        fx.burst(sim, o)
    }

    /** `applyBird()`: the equipped skin and look on the 3D bird. */
    fun applyBird() {
        val bird = views.bird ?: return
        bird.setSkin(progress.equipped(Kind.Skin) as SkinItem)
        bird.setLook(lookOf(LOOK_KINDS.associateWith { progress.equipped(it).id }))
        bird.setTrail(progress.equipped(Kind.Trail) as TrailItem)
    }

    private fun lookOf(m: Map<Kind, String>) = BirdLook(
        pattern = m[Kind.Pattern] ?: "plain",
        hat = m[Kind.Hat] ?: "none",
        eyes = m[Kind.Eyes] ?: "normal",
        beak = m[Kind.Beak] ?: "round",
    )

    /** The run trail follows the equipped one (JS reads `progress.trail` live). */
    private fun syncTrail() {
        val d = progress.data.value
        if (d === trailOf) return
        trailOf = d
        views.bird?.setTrail(progress.equipped(Kind.Trail) as TrailItem)
    }

    // --- event routing ----------------------------------------------------------------------------

    /** Routes [event] as if the simulation had emitted it (tests, tools). */
    fun inject(event: GameEvent) {
        pending += event
        routeEvents()
    }

    fun routeEvents() {
        while (pending.isNotEmpty()) {
            val batch = ArrayList(pending)
            pending.clear()
            for (e in batch) route(e)
        }
    }

    private fun t(key: String, params: Map<String, Any?> = emptyMap()) = strings.t(key, params)
    private fun L(text: LocalizedText) = text.get(strings.lang.value)

    private fun zoneBiome(zone: Int): Biome = WorldPicks.zoneBiome(zone, progress)

    private fun route(e: GameEvent) {
        views.fx?.onEvent(e, sim)
        val s = sim.state
        when (e) {
            GameEvent.Flap -> audio.sfx(Sfx.Flap)
            is GameEvent.LaneSwitch -> audio.sfx(Sfx.Swoosh)
            is GameEvent.Point -> {
                scorePop++
                audio.sfx(Sfx.Point)
            }
            is GameEvent.CoinCollected -> {
                coinBump++
                audio.sfx(Sfx.Coin)
            }
            is GameEvent.PowerUp -> {
                if (e.type == PowerType.Star) audio.setHype(true)
                audio.sfx(Sfx.PowerUp(e.type))
            }
            is GameEvent.PowerDown -> {
                audio.sfx(Sfx.PowerDown)
                if (e.type == PowerType.Star) audio.setHype(false)
            }
            is GameEvent.NearMiss -> {
                coinBump++
                audio.sfx(Sfx.Near(e.chain - 1))
                val p = rig.project(s.x, s.y + 1.2, 0.0)
                hud.popup(if (e.chain > 1) "${t("near")} ×${e.chain}" else t("near"), p[0], p[1])
            }
            is GameEvent.ZoneEntered -> {
                val b = zoneBiome(e.zone)
                views.world?.biomes?.set(e.zone, 3.0, b)
                audio.setTheme(e.zone)
                hud.banner(t("zone", mapOf("n" to e.zone + 1)), L(b.name))
                audio.sfx(Sfx.Zone)
            }
            GameEvent.Bounce -> audio.sfx(Sfx.Bounce)
            is GameEvent.Died -> {
                audio.sfx(Sfx.Hit)
                audio.duck()
                audio.setHype(false)
                flash.hit()
            }
            is GameEvent.GameOver -> {
                audio.setMode(MusicMode.Menu)
                summary = e.summary
                gameOverUi = texts.gameOver(e.summary)
                val r = e.summary.result
                if (r.completed.isNotEmpty() || r.achievements.isNotEmpty() || s.coins > 0) walletBump++
            }
            is GameEvent.MissionPreview -> for (id in e.missionIds) {
                val m = progress.missions().firstOrNull { it.id == id } ?: continue
                toasts.push("[check] ${L(Missions.byId(m.id).text(m.goal))} +${m.reward}")
                audio.sfx(Sfx.PowerUp(PowerType.Star))
            }
            is GameEvent.AchievementPreview -> for (a in e.achievements) {
                toasts.push("[trophy] ${L(a.name)} +${a.reward}")
                audio.sfx(Sfx.PowerUp(PowerType.Star))
            }
            is GameEvent.Buzz -> if (!audio.muted) runCatching { services.haptics.vibrate(e.millis) }
            is GameEvent.Toast -> {
                if (e.delayMs > 0) scheduler.after(e.delayMs) { toasts.push(t(e.key, e.params)) }
                else toasts.push(t(e.key, e.params))
            }
            is GameEvent.RunStarted -> {
                toasts.clear() // no leftovers from the menu or the last run
                views.world?.let { w ->
                    val b0 = zoneBiome(0)
                    if (w.biomes.current !== b0) w.biomes.set(0, 1.2, b0)
                }
                audio.setTheme(0)
                audio.setMode(MusicMode.Game)
                audio.setHype(false)
                applyBird()
                summary = null
                gameOverUi = null
                zones.invalidate()
            }
            GameEvent.HoldEnded -> Unit
            is GameEvent.PauseChanged -> audio.setSuspended(e.paused)
            GameEvent.WentToMenu -> {
                audio.setHype(false)
                audio.setMode(MusicMode.Menu)
                applyBird()
                summary = null
                gameOverUi = null
            }
            is GameEvent.SceneryTheme -> views.world?.scenery?.setTheme(zoneBiome(e.zone).scenery)
            is GameEvent.Fanfare -> {
                if (e.delayMs > 0) scheduler.after(e.delayMs) { audio.sfx(Sfx.PowerUp(PowerType.Star)) }
                else audio.sfx(Sfx.PowerUp(PowerType.Star))
            }
        }
        menuDirty = true
    }

    // --- snapshot -------------------------------------------------------------------------------

    fun snapshot(base: UiState): UiState {
        val s = sim.state
        val data = progress.data.value
        val ads = services.ads?.status?.value ?: AdsStatus()
        val billing = services.billing?.status?.value ?: BillingStatus()
        val lang = strings.lang.value
        if (data !== lastData || ads != lastAds || billing != lastBilling || lang != lastLang) {
            lastData = data
            lastAds = ads
            lastBilling = billing
            lastLang = lang
            menuDirty = true
        }
        shop.ads = ads
        shop.billing = billing
        val ready = s.mode == GameMode.Ready
        if (menuDirty && ready) {
            menuDirty = false
            startUi = texts.start(ads.privacyOptionsRequired)
            if (ready && s.menu == Menu.Achievements) achUi = texts.achievements()
            shopUi = if (ready && s.menu == Menu.Shop) shop.ui(ads.privacyOptionsRequired) else null
            shopUi?.let { renderThumbnails(it) }
        }
        val needsBird = s.hand != de.robinrehbein.birdy.game.HandMode.None
        val bird = if (needsBird) rig.project(s.x, s.y, 0.0) else null
        val hand = if (bird != null) {
            TutorialHand.ui(
                s.hand, bird[0], bird[1], input.screenX(WorldConst.LANES[0], s.y),
                t(if (s.hand == de.robinrehbein.birdy.game.HandMode.Side) "handSide" else "handFlap"),
            )
        } else null
        val zonesUi = zones.update(s.zonesHint, s.zonesShowSerial, s.lane, { input.laneBounds(s.y) }, t("zoneFlap"), t("zoneMove"))
        val power = PowerType.entries.map { (s.power(it) / sim.powerDuration(it)).toFloat().coerceIn(0f, 1f) }
        val fps = quality.fps
        return base.copy(
            booted = true,
            mode = s.mode,
            menu = s.menu,
            paused = s.paused,
            hold = s.hold,
            score = s.score,
            runCoins = s.coins,
            lane = s.lane,
            zone = s.zone,
            power = if (power == base.power) base.power else power,
            powerActive = PowerType.entries.map { s.power(it) > 0 },
            powerEnding = PowerType.entries.map { s.power(it) > 0 && s.power(it) < 1.5 },
            toast = toasts.visible,
            flashAlpha = flash.alpha,
            tutorialHand = hand,
            zonesHint = zonesUi,
            gameOver = if (s.mode == GameMode.Over) summary else null,
            gameOverUi = if (s.mode == GameMode.Over) gameOverUi else null,
            shopKind = shop.tab.kind ?: base.shopKind,
            progress = data,
            lang = lang,
            muted = audio.muted,
            ads = ads,
            billing = billing,
            showFps = showFps,
            fps = fps.toInt(),
            fpsText = if (showFps) "${jsFixed0(fps)} fps · ${renderer?.stats?.drawCalls ?: 0} dc · Q${quality.quality}" else null,
            scorePop = scorePop,
            coinBump = coinBump,
            walletBump = walletBump,
            popup = hud.popup,
            zoneBanner = hud.banner,
            tapFx = hud.tapFx,
            start = startUi,
            achievements = achUi,
            shop = shopUi,
            thumbnails = if (thumbnails.size == base.thumbnails.size) base.thumbnails else HashMap(thumbnails),
            thumbnailVersion = thumbnailVersion,
            quality = quality.quality,
            drawCalls = if (showFps) renderer?.stats?.drawCalls ?: 0 else 0,
        )
    }

    /** `thumbUrl()` for the visible workshop tiles, rendered on demand on the GL thread. */
    private fun renderThumbnails(ui: ShopUi) {
        val thumbs = views.thumbs ?: return
        val kind = ui.tab.kind ?: return
        if (kind !in LOOK_KINDS) return
        val skin = progress.equipped(Kind.Skin) as SkinItem
        var added = false
        for (tile in ui.tiles) {
            val key = tile.thumbKey ?: continue
            if (key in thumbnails) continue
            val px = runCatching { thumbs.thumbnail(kind, tile.id, skin) }.getOrNull() ?: continue
            thumbnails[key] = ThumbnailUi(thumbs.size, px)
            added = true
        }
        if (added) thumbnailVersion++
    }

    companion object {
        /**
         * Menu framing when the UI has not reported its layout yet: the JS layout measured at
         * 412x915 CSS px (see scripts/native-shots/js-reference.mjs, `menuFrames`).
         */
        val DEFAULT_FRAMES: Map<Menu, MenuFrameInfo> = mapOf(
            Menu.Start to MenuFrameInfo.fromLayout(0.1552, 0.5137),
            Menu.Shop to MenuFrameInfo.fromLayout(0.1355, 0.4083),
            Menu.Achievements to MenuFrameInfo.fromLayout(0.1596, 0.3979),
        )

        private fun sign(v: Int) = if (v > 0) 1 else if (v < 0) -1 else 0

        /** `Number.toFixed(0)`. */
        private fun jsFixed0(v: Double): String = kotlin.math.floor(v + 0.5).toLong().toString()
    }
}
