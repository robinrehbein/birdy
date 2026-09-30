package de.robinrehbein.birdy.view.bird

import de.robinrehbein.birdy.engine.math.Color
import de.robinrehbein.birdy.engine.scene.Scene
import de.robinrehbein.birdy.game.GameMode
import de.robinrehbein.birdy.game.PowerType
import de.robinrehbein.birdy.game.Menu
import de.robinrehbein.birdy.meta.Catalog
import de.robinrehbein.birdy.meta.SkinItem
import de.robinrehbein.birdy.meta.TrailItem
import de.robinrehbein.birdy.view.FrameInfo
import de.robinrehbein.birdy.view.SceneView

/**
 * The game bird (main.js `updateBirdVisual`, bird-fx.md §2). Copies the simulation's bird pose
 * ([de.robinrehbein.birdy.game.BirdPose]: pitch/roll lerps, mini-scale easing, squash & stretch,
 * the death splat, the grace blink) onto [rig], animates the wings and skin effect, and drives the
 * rainbow-star glow.
 *
 * Wiring: `attach(scene)` once, then per frame `update(frame)`. Cosmetics come from progress:
 * call [setSkin], [setLook] and [setTrail] whenever the equipped items (or shop previews) change.
 * The trail is only stored here; [de.robinrehbein.birdy.view.fx.FxView] emits it.
 */
class BirdView : SceneView {
    val rig = BirdRig()

    /** User-controlled orientation retained across shop outfit previews. Radians, game thread. */
    var shopYaw = 0f

    /** Currently worn skin (feather particles on death use its colours). */
    val skin: SkinItem get() = rig.skin

    /** Equipped flight trail (bird-fx.md §4.1.1); "none" has no colours. */
    var trail: TrailItem = Catalog.trails[0]
        private set

    private val glowColor = Color()
    private var glowOn = false

    override fun attach(scene: Scene) {
        scene.add(rig.root)
    }

    fun setSkin(skin: SkinItem) = rig.setSkin(skin)

    fun setLook(look: BirdLook) = rig.setLook(look)

    /** bird.js `setLook({ pattern, hat, eyes, beak })` with catalog ids. */
    fun setLook(pattern: String, hat: String, eyes: String, beak: String) = setLook(BirdLook(pattern, hat, eyes, beak))

    fun setTrail(trail: TrailItem) {
        this.trail = trail
    }

    /** Group-wide emissive glow (null = off); normally driven by [update] for the rainbow star. */
    fun setGlow(color: Color?, intensity: Float = 0.6f) {
        glowOn = color != null
        rig.setGlow(color, intensity)
    }

    override fun update(frame: FrameInfo) {
        val s = frame.sim.state
        val pose = s.pose
        val g = rig.root
        g.position.set(s.x.toFloat(), s.y.toFloat(), 0f)
        g.rotation.set(pose.rotX.toFloat(),
            if (s.mode == GameMode.Ready && s.menu == Menu.Shop) shopYaw else 0f,
            pose.rotZ.toFloat())
        g.scale.set(pose.scaleX.toFloat(), pose.scaleY.toFloat(), pose.scaleZ.toFloat())
        g.visible = pose.visible
        if (s.mode != GameMode.Over) {
            rig.animateWings(s.wingPhase)
            rig.skinFx.tick(s.time)
        }
        // Rainbow glow while the star is active; JS clears it on star end, death, reset and menu,
        // which are exactly the cases where this condition turns false.
        if (s.mode == GameMode.Playing && s.power(PowerType.Star) > 0) {
            setGlow(glowColor.setHSL(starHue(s.time), 1.0, 0.5), 0.7f)
        } else if (glowOn) {
            setGlow(null)
        }
    }

    companion object {
        /** `(time * 1.5) % 1`: rainbow hue shared by the glow, the star aura and the star trail. */
        fun starHue(time: Double): Double = (time * 1.5) % 1.0
    }
}
