package de.robinrehbein.birdy.game

import de.robinrehbein.birdy.audio.SynthAudio
import de.robinrehbein.birdy.engine.RenderBackend
import de.robinrehbein.birdy.game.loop.DeferredStrings
import de.robinrehbein.birdy.game.loop.GameViews
import de.robinrehbein.birdy.meta.LocalProgressRepository
import de.robinrehbein.birdy.meta.TableStrings
import de.robinrehbein.birdy.meta.migration.LegacyImport
import de.robinrehbein.birdy.meta.migration.runMigrationIfNeeded
import de.robinrehbein.birdy.platform.PlatformServices
import de.robinrehbein.birdy.view.bird.BirdView
import de.robinrehbein.birdy.view.fx.FxView
import de.robinrehbein.birdy.view.thumb.ThumbnailRenderer
import de.robinrehbein.birdy.view.world.WorldView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile
import kotlin.random.Random

/**
 * Single entry point the app shells (Android now, iOS later) use to build the game; shells must
 * not construct its pieces themselves.
 *
 * Storage migration: the one-time import of the Capacitor WebView `localStorage`
 * ([runMigrationIfNeeded]) must finish before progress, language and mute are read. It runs on a
 * background coroutine (the Android implementation hops to the main thread for its hidden
 * WebView and times out on its own); the GL thread never waits for it. Until it is done,
 * [BirdyGame.frame] only clears the screen and queues input ([UiState.booted] = false, the shell
 * keeps its splash screen up). The first frame after it finished builds the real parts on the GL
 * thread: [LocalProgressRepository], [TableStrings], [SynthAudio] and the world/bird/fx views.
 * Installs that already migrated (the normal case) boot on the very first frame.
 */
fun createBirdyGame(services: PlatformServices, renderer: RenderBackend): BirdyGame {
    val storage = services.storage
    val strings = DeferredStrings(TableStrings(storage, services.deviceLanguage))
    val gate = MigrationGate(LegacyImport.alreadyMigrated(storage))
    if (!gate.done) {
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            // A failed import is retried on the next launch (the migrated flag stays unset).
            runCatching { runMigrationIfNeeded(storage, services.migration) }
            gate.done = true
        }
    }
    return BirdyGame(services, renderer, strings) {
        if (!gate.done) {
            null
        } else {
            strings.replace(TableStrings(storage, services.deviceLanguage))
            val seed = services.clock.nowMillis()
            val bird = BirdView()
            GameParts(
                progress = LocalProgressRepository(storage, services.clock),
                audio = SynthAudio(services.audioOut, storage, Random(seed)),
                views = GameViews(
                    world = WorldView(Random(seed + 1)),
                    bird = bird,
                    fx = FxView(bird, Random(seed + 2)),
                    thumbs = ThumbnailRenderer(renderer),
                ),
                simRandom = Random(seed + 3),
                uiRandom = Random(seed + 4),
            )
        }
    }
}

private class MigrationGate(initial: Boolean) {
    @Volatile
    var done: Boolean = initial
}
