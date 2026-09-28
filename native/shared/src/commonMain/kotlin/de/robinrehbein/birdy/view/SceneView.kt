package de.robinrehbein.birdy.view

import de.robinrehbein.birdy.engine.scene.PerspectiveCamera
import de.robinrehbein.birdy.engine.scene.Scene
import de.robinrehbein.birdy.game.GameSimulation

/** Per-frame inputs for views. [dt] is already clamped (<= 1/30 s). */
class FrameInfo(
    val dt: Double,
    /** Free-running game time (state.time). */
    val time: Double,
    /** Music/fallback beat used by pulsing gaps and plants. */
    val beat: Double,
    val sim: GameSimulation,
    val camera: PerspectiveCamera,
)

/**
 * A visual layer that mirrors simulation state into scene-graph nodes (world, bird, effects).
 * Views never mutate simulation state; they create their nodes in [attach] and update transforms,
 * visibility and uniforms in [update]. Game thread only.
 */
interface SceneView {
    fun attach(scene: Scene)
    fun update(frame: FrameInfo)
}
