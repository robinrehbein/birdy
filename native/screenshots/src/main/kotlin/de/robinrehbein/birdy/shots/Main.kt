package de.robinrehbein.birdy.shots

import de.robinrehbein.birdy.engine.gl.GL
import de.robinrehbein.birdy.engine.gl.GlRenderer
import de.robinrehbein.birdy.engine.gl.HeadlessEglContext
import de.robinrehbein.birdy.engine.gl.LwjglGl
import de.robinrehbein.birdy.game.GameMode
import de.robinrehbein.birdy.game.UiCommand
import java.io.File

/**
 * Headless screenshot tool: renders scripted, deterministic scenes of the real game (BirdyGame
 * with the shared engine and GLSL ES shaders via LWJGL GLES on Mesa llvmpipe, EGL surfaceless)
 * and composites the Compose overlay rendered with ImageComposeScene. Output: PNGs at 1080x2400
 * (`NN-name.png`) in the directory given as the first argument (default native/build/shots).
 * An optional second argument filters scenes by substring.
 *
 * Run: ./gradlew :screenshots:run   (the Gradle task sets EGL_PLATFORM=surfaceless)
 */
fun main(args: Array<String>) {
    val outDir = File(args.firstOrNull() ?: "build/shots").apply { mkdirs() }
    val filter = args.getOrNull(1)
    HeadlessEglContext().use {
        val gl = LwjglGl()
        println("GL: ${gl.getString(GL.RENDERER)} / ${gl.getString(GL.VERSION)}")
        val renderer = GlRenderer(gl)
        renderer.onContextCreated()
        val target = renderer.createRenderTarget(Shots.WIDTH, Shots.HEIGHT, 4)
        var count = 0
        for (session in Shots.sessions) {
            val g = ShotGame(renderer, session.save, session.lang, store = session.store)
            val capture = Capture { name ->
                if (filter != null && !name.contains(filter)) return@Capture
                val file = File(outDir, "$name.png")
                frameMenus(g)
                if (System.getenv("SHOT_DEBUG") != null) {
                    val r = g.game.game.rig
                    val p = r.project(g.state.x, g.state.y, 0.0)
                    println("$name cam=${g.game.camera.position.x},${g.game.camera.position.y},${g.game.camera.position.z} look=${r.lookCur.x},${r.lookCur.y},${r.lookCur.z} shift=${r.viewShift} bird=${p.toList()} y=${g.state.y} vis=${g.bird.rig.root.visible} fov=${g.game.camera.fov}")
                }
                g.render(target)
                val base = renderer.readPixels(target)
                val overlay = Overlay.render(Shots.WIDTH, Shots.HEIGHT, Shots.DENSITY, Overlay.settled(g.ui), g.strings)
                Png.write(Png.composite(base, overlay.argb), Shots.WIDTH, Shots.HEIGHT, file)
                count++
                println("wrote ${file.path}")
            }
            session.script(capture, g)
        }
        println("$count shots")
        target.dispose()
        renderer.dispose()
    }
}

/**
 * Menus frame the bird in the free band the overlay measured (`measureMenuFrame`): a first
 * overlay pass reports [UiCommand.MenuFrame]; the camera then eases there.
 */
private fun frameMenus(g: ShotGame) {
    if (g.state.mode != GameMode.Ready) return
    val probe = Overlay.render(Shots.WIDTH, Shots.HEIGHT, Shots.DENSITY, g.ui, g.strings)
    val frames = probe.commands.filterIsInstance<UiCommand.MenuFrame>()
    if (frames.isEmpty()) return
    frames.forEach { g.game.post(it) }
    g.advance(2.0)
}
