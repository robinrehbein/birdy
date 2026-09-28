package de.robinrehbein.birdy.shots

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import de.robinrehbein.birdy.game.UiCommand
import de.robinrehbein.birdy.game.UiState
import de.robinrehbein.birdy.meta.Strings
import de.robinrehbein.birdy.ui.BirdyApp
import org.jetbrains.skia.EncodedImageFormat
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

/** Renders the shared Compose overlay headless (Skia raster) to ARGB pixels. */
object Overlay {
    /** Overlay pixels plus the commands the UI posted while laying out (e.g. menu frames). */
    class Result(val argb: IntArray, val commands: List<UiCommand>)

    /**
     * The JS reference finishes every finite CSS animation before its shot
     * (`document.getAnimations()…finish()`). Compose animations are settled by rendering ahead
     * (see [render]); the tap-zone flash is driven by game time instead, so jump it to its end
     * state here (labels and veil faded out, faint dividers kept).
     */
    fun settled(state: UiState): UiState {
        val zones = state.zonesHint ?: return state
        if (!zones.flash) return state
        return state.copy(zonesHint = zones.copy(age = maxOf(zones.age, ZONE_FLASH_S)))
    }

    /** `#zones.show` animation length (style.css `zone-fade` 2.6s). */
    private const val ZONE_FLASH_S = 2.6f

    fun render(width: Int, height: Int, density: Float, state: UiState, strings: Strings): Result {
        val commands = ArrayList<UiCommand>()
        val image = ImageComposeScene(width, height, Density(density)) {
            BirdyApp(state, strings) { commands += it }
        }.use { scene ->
            scene.render(0L)
            // Later frames: fonts/resources resolved and entrance animations settled.
            scene.render(16_000_000L)
            scene.render(2_000_000_000L)
            // One-shot animations launched on the previous frames (wallet bump) start their
            // clock on the frame above; let them finish.
            scene.render(4_000_000_000L)
        }
        val png = image.encodeToData(EncodedImageFormat.PNG) ?: error("overlay encode failed")
        val img = ImageIO.read(ByteArrayInputStream(png.bytes))
        return Result(img.getRGB(0, 0, width, height, null, 0, width), commands)
    }
}
