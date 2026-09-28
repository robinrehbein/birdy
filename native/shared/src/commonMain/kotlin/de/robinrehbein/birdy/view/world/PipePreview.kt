package de.robinrehbein.birdy.view.world

import de.robinrehbein.birdy.engine.scene.Mesh
import de.robinrehbein.birdy.engine.scene.Node
import de.robinrehbein.birdy.engine.scene.Scene

/**
 * world.js `createPipePreview(scene)`: a bottom and a top pipe framing a gap, shown next to the
 * bird in the shop (no sky haze). Hidden until the shop shows the pipe tab.
 */
class PipePreview(scene: Scene, pipes: PipeKit) {
    private val mat = pipes.newMaterial()
    val group = Node("pipe-preview").apply { visible = false }
    private val bottomG = Node()
    private val bottomBody = seg(pipes.body)
    private val bottomLip = seg(pipes.capBelow)
    private val topG = Node()
    private val topBody = seg(pipes.body)
    private val topLip = seg(pipes.capAbove)

    private fun seg(geo: de.robinrehbein.birdy.engine.scene.Geometry) = Mesh(geo, mat).apply {
        castShadow = true
        receiveShadow = true
    }

    init {
        bottomG.add(bottomBody, bottomLip)
        topG.add(topBody, topLip)
        group.add(bottomG, topG)
        scene.add(group)
    }

    /** Gap between [y0] and [y1] (world units); the top pipe is a fixed 12 units tall. */
    fun setGap(y0: Double, y1: Double) {
        bottomBody.scale.y = y0.toFloat()
        bottomLip.position.y = (y0 - 0.4).toFloat()
        topG.position.y = y1.toFloat()
        topBody.scale.y = 12f
        topLip.position.y = 0.4f
    }
}
