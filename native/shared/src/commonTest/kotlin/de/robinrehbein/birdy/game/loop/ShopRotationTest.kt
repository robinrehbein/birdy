package de.robinrehbein.birdy.game.loop

import de.robinrehbein.birdy.game.GameMode
import de.robinrehbein.birdy.game.UiCommand
import de.robinrehbein.birdy.meta.Kind
import de.robinrehbein.birdy.view.bird.BirdView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShopRotationTest {
    @Test
    fun previewTurnsContinuouslyAndKeepsAngleWhenChangingOutfit() {
        val bird = BirdView()
        val h = LoopHarness(views = GameViews(bird = bird))
        h.post(UiCommand.OpenShop(true))
        h.post(UiCommand.Touch(0.3f, 0.26f))
        h.post(UiCommand.TouchMove(0.55f, 0.26f))
        val angle = bird.rig.root.rotation.y
        assertTrue(angle > 1f, "horizontal drag must turn the bird")
        h.post(UiCommand.TouchMove(0.7f, 0.26f))
        assertTrue(bird.rig.root.rotation.y > angle, "every move continues turning")
        h.post(UiCommand.TouchUp)
        val held = bird.rig.root.rotation.y
        h.post(UiCommand.ShopItem(Kind.Beak, "toucan"))
        h.frames(0.2)
        assertEquals(held, bird.rig.root.rotation.y)
        assertEquals(GameMode.Ready, h.state.mode)
        assertEquals(1, h.state.lane)
        h.post(UiCommand.OpenShop(false))
        assertEquals(0f, bird.rig.root.rotation.y, "game/menu returns to normal orientation")
    }

    @Test
    fun touchesOutsidePreviewAndMovesAfterReleaseDoNotRotate() {
        val bird = BirdView()
        val h = LoopHarness(views = GameViews(bird = bird))
        h.post(UiCommand.OpenShop(true))
        for (y in listOf(0.05f, 0.8f)) {
            h.post(UiCommand.Touch(0.3f, y))
            h.post(UiCommand.TouchMove(0.7f, y))
            h.post(UiCommand.TouchUp)
            assertEquals(0f, bird.rig.root.rotation.y)
        }
        h.post(UiCommand.Touch(0.3f, 0.26f))
        h.post(UiCommand.TouchMove(0.3f, 0.3f))
        assertEquals(0f, bird.rig.root.rotation.y, "vertical movement does not turn")
        h.post(UiCommand.TouchUp)
        h.post(UiCommand.TouchMove(0.8f, 0.26f))
        assertEquals(0f, bird.rig.root.rotation.y)
    }
}
