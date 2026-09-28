package de.robinrehbein.birdy

import android.content.res.Configuration
import android.os.Bundle
import android.os.SystemClock
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import de.robinrehbein.birdy.audio.AudioTrackOut
import de.robinrehbein.birdy.engine.gl.AndroidGl
import de.robinrehbein.birdy.engine.gl.GlRenderer
import de.robinrehbein.birdy.game.BirdyGame
import de.robinrehbein.birdy.game.UiCommand
import de.robinrehbein.birdy.game.UiEffect
import de.robinrehbein.birdy.game.createBirdyGame
import de.robinrehbein.birdy.platform.AndroidHaptics
import de.robinrehbein.birdy.platform.JvmClock
import de.robinrehbein.birdy.platform.PlatformServices
import de.robinrehbein.birdy.platform.SharedPrefsKeyValueStore
import de.robinrehbein.birdy.platform.WebViewLegacyMigration
import de.robinrehbein.birdy.platform.ads.AdMobAds
import de.robinrehbein.birdy.platform.billing.PlayBilling
import de.robinrehbein.birdy.platform.purchase.PurchaseProcessor
import de.robinrehbein.birdy.ui.BirdyApp
import java.lang.ref.WeakReference
import java.util.Locale

/**
 * Single activity: fullscreen immersive portrait, GL surface for the 3D scene with the Compose
 * overlay on top. The game itself is built by [createBirdyGame] (game-loop task); this class
 * wires the real platform services (ads/UMP, Play Billing, haptics, storage, legacy migration)
 * and forwards [UiEffect]s that need an Activity.
 */
class MainActivity : ComponentActivity() {
    private lateinit var game: BirdyGame
    private lateinit var surface: GameSurfaceView
    private lateinit var ads: AdMobAds
    private lateinit var billing: PlayBilling
    private lateinit var purchases: PurchaseProcessor

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enterImmersive()

        val storage = SharedPrefsKeyValueStore(this)
        val clock = JvmClock()
        // Weak so ads/billing callbacks never keep a destroyed Activity alive.
        val self = WeakReference(this)
        val activityProvider = { self.get() }

        ads = AdMobAds(applicationContext, isDebug = BuildConfig.DEBUG, activity = activityProvider)
        billing = PlayBilling(applicationContext, activity = activityProvider)

        val services = PlatformServices(
            storage = storage,
            clock = clock,
            haptics = AndroidHaptics(this),
            audioOut = AudioTrackOut(),
            ads = ads,
            billing = billing,
            migration = WebViewLegacyMigration(this),
            deviceLanguage = Locale.getDefault().toLanguageTag(),
        )
        game = createBirdyGame(services, GlRenderer(AndroidGl()))
        game.displayDensity = resources.displayMetrics.density.toDouble()
        surface = GameSurfaceView(this, game)
        // Keep the splash up until the storage migration finished and the first real frame is
        // built (UiState.booted); bounded so a stuck migration never hides the app for good.
        val splashUntil = SystemClock.uptimeMillis() + SPLASH_MAX_MS
        splash.setKeepOnScreenCondition {
            !game.ui.value.booted && SystemClock.uptimeMillis() < splashUntil
        }

        purchases = PurchaseProcessor(
            billing = billing,
            progress = { game.progress },
            runOnGame = { game.runOnGameThread(it) },
            onGrant = { game.post(UiCommand.PurchaseGranted) },
        )
        billing.onPurchaseFlowEnded = { failed -> game.post(UiCommand.PurchaseEnded(failed)) }
        billing.onRestore = { owned -> purchases.syncOwnedPurchases(owned) }
        ads.init()
        purchases.start()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = game.post(UiCommand.Back)
        })

        // Plain view stack: the GL SurfaceView at the bottom, the Compose overlay (transparent
        // window content) above it. Keeps the SurfaceView out of Compose's own render layers.
        val overlay = ComposeView(this).apply {
            setContent {
            val state by game.ui.collectAsState()
            LaunchedEffect(Unit) {
                game.effects.collect { effect ->
                    when (effect) {
                        UiEffect.ExitApp -> finish()
                        is UiEffect.ShowRewardedAd -> ads.showRewarded(effect.kind) { earned ->
                            game.post(UiCommand.RewardResult(effect.kind, earned))
                        }
                        is UiEffect.LaunchPurchase -> billing.launchPurchase(effect.productId)
                        UiEffect.ShowPrivacyOptions -> ads.showPrivacyOptions()
                    }
                }
            }
            // Before boot the GL surface shows the plain sky; menus appear with real data.
            if (state.booted) BirdyApp(state, game.strings) { game.post(it) }
            }
        }
        setContentView(
            FrameLayout(this).apply {
                addView(surface, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
                addView(overlay, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            },
        )
    }

    // configChanges includes density: the Activity survives a display-size change, so refresh it here.
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val density = resources.displayMetrics.density.toDouble()
        game.runOnGameThread { game.displayDensity = density }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersive()
    }

    override fun onPause() {
        super.onPause()
        // The GL (game) thread is paused once this returns, so a posted command would only run
        // after resume: apply the background transition (pause the run, suspend music) directly.
        surface.onPause()
        game.handleWhileStopped(UiCommand.AppVisible(false))
    }

    override fun onResume() {
        super.onResume()
        surface.onResume()
        game.post(UiCommand.AppVisible(true))
        // billing.js: re-runs refresh() unconditionally on every foreground return (platform.md §2.3).
        purchases.refresh()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Joining the audio thread can take up to a second: keep it off the main thread.
        if (isFinishing) Thread(game::release, "birdy-release").start()
    }

    private fun enterImmersive() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private companion object {
        const val SPLASH_MAX_MS = 4000L
    }
}
