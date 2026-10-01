package de.robinrehbein.birdy.game.loop

import de.robinrehbein.birdy.audio.GameAudio
import de.robinrehbein.birdy.audio.MusicMode
import de.robinrehbein.birdy.audio.NullAudioOut
import de.robinrehbein.birdy.audio.Sfx
import de.robinrehbein.birdy.engine.RenderBackend
import de.robinrehbein.birdy.engine.RenderStats
import de.robinrehbein.birdy.engine.RenderTarget
import de.robinrehbein.birdy.engine.scene.PerspectiveCamera
import de.robinrehbein.birdy.engine.scene.Scene
import de.robinrehbein.birdy.game.BirdyGame
import de.robinrehbein.birdy.game.GameMode
import de.robinrehbein.birdy.meta.KeyEchoStrings
import de.robinrehbein.birdy.meta.LocalProgressRepository
import de.robinrehbein.birdy.platform.FakeClock
import de.robinrehbein.birdy.platform.Ads
import de.robinrehbein.birdy.platform.Haptics
import de.robinrehbein.birdy.platform.MemoryKeyValueStore
import de.robinrehbein.birdy.platform.PlatformServices
import de.robinrehbein.birdy.platform.StorageKeys
import kotlin.random.Random

class FakeRenderer : RenderBackend {
    override fun onContextCreated() = Unit
    override fun setSurfaceSize(width: Int, height: Int) = Unit
    override var resolutionScale = 1f
    override var shadowsEnabled = true
    var frames = 0
    override fun render(scene: Scene, camera: PerspectiveCamera, target: RenderTarget?) {
        frames++
    }
    override fun createRenderTarget(width: Int, height: Int): RenderTarget = object : RenderTarget {
        override val width = width
        override val height = height
        override fun dispose() = Unit
    }
    override fun readPixels(target: RenderTarget) = IntArray(target.width * target.height)
    override val stats = RenderStats()
    override fun dispose() = Unit
}

class FakeAudio : GameAudio {
    val log = ArrayList<String>()
    val sfxLog = ArrayList<Sfx>()
    var audioOff = false
    private var mutedFlag = false
    override val muted: Boolean get() = mutedFlag
    override fun setMuted(muted: Boolean) { mutedFlag = muted; log += "muted:$muted" }
    override fun setSuspended(suspended: Boolean) { audioOff = suspended; log += "suspended:$suspended" }
    override fun unlock() = Unit
    override fun sfx(effect: Sfx) { sfxLog += effect }
    override fun musicStart() = Unit
    override fun musicStop() = Unit
    override fun musicBeat(): Double? = null
    override fun setHype(hype: Boolean) { log += "hype:$hype" }
    override fun setTheme(index: Int) { log += "theme:$index" }
    override fun setMode(mode: MusicMode) { log += "mode:$mode" }
    override fun duck() { log += "duck" }
}

class RecordingHaptics : Haptics {
    val calls = ArrayList<Int>()
    override fun vibrate(millis: Int) { calls += millis }
}

/** A game on fakes (no views) with an experienced save by default (tutorial done, 6 runs). */
class LoopHarness(
    progressJson: String = """{"coins":500,"best":20,"runs":6,"tutorialDone":true}""",
    seed: Int = 7,
    ads: Ads? = null,
    views: GameViews = GameViews(),
) {
    val store = MemoryKeyValueStore(mapOf(StorageKeys.PROGRESS to progressJson))
    val clock = FakeClock(millis = 1_790_000_000_000)
    val haptics = RecordingHaptics()
    val audio = FakeAudio()
    val renderer = FakeRenderer()
    val services = PlatformServices(store, clock, haptics, NullAudioOut, ads, null, deviceLanguage = "de-DE")
    val progress = LocalProgressRepository(store, clock)
    val game = BirdyGame(services, renderer, progress, KeyEchoStrings(), audio, Random(seed), views = views, uiRandom = Random(seed + 1))
    private var nanos = 0L

    init {
        game.onSurfaceChanged(1080, 2400)
        frame()
    }

    val sim get() = game.sim
    val state get() = game.sim.state
    val ui get() = game.ui.value

    /** One real frame of [dt] seconds. */
    fun frame(dt: Double = 1.0 / 60) {
        nanos += (dt * 1e9).toLong()
        game.frame(nanos)
    }

    fun frames(seconds: Double, dt: Double = 1.0 / 60) {
        var t = 0.0
        while (t < seconds) {
            frame(dt)
            t += dt
        }
    }

    fun post(cmd: de.robinrehbein.birdy.game.UiCommand) {
        game.post(cmd)
        frame()
    }

    /** Starts a run and ends the hover with a flap; god mode keeps it alive. */
    fun startRun(god: Boolean = true) {
        post(de.robinrehbein.birdy.game.UiCommand.Play)
        check(state.mode == GameMode.Playing)
        sim.debug.setGod(god)
        post(de.robinrehbein.birdy.game.UiCommand.Touch(0.5f, 0.7f))
    }
}
