package de.robinrehbein.birdy.view.world

import de.robinrehbein.birdy.engine.mesh.computeBoundingBox
import de.robinrehbein.birdy.game.Tuning
import de.robinrehbein.birdy.game.WorldConst
import kotlin.test.Test
import kotlin.test.assertTrue

class RecordMarkerTest {
    @Test
    fun labelFitsInsideTheBanner() {
        for (text in listOf("REKORD 5", "Rekord 32", "BEST 1234", "REKORD 99999")) {
            val b = RecordMarker.labelGeometry(text).computeBoundingBox()
            assertTrue(!b.isEmpty, text)
            assertTrue(b.max.x - b.min.x <= RecordMarker.BANNER_W - 0.5f, "$text is wider than the banner")
            assertTrue(b.max.y - b.min.y <= RecordMarker.BANNER_H - 0.8f, "$text is taller than the banner")
            assertTrue(kotlin.math.abs(b.max.x + b.min.x) < 0.01f, "$text is centred")
        }
    }

    @Test
    fun unknownCharactersAndEmptyLabelsStillBuild() {
        assertTrue(!RecordMarker.labelGeometry("").computeBoundingBox().isEmpty)
        assertTrue(!RecordMarker.labelGeometry("Ü?!").computeBoundingBox().isEmpty)
    }

    @Test
    fun bannerHangsAboveTheFlightCeiling() {
        // The bird (centre clamped to the ceiling) never flies through the banner.
        val birdTop = Tuning.CEILING + Tuning.BIRD_RADIUS
        assertTrue(RecordMarker.BANNER_BOTTOM - 0.15f > birdTop) // frame bar below the banner
    }

    @Test
    fun postsStayClearOfTheOuterLanes() {
        val outer = WorldConst.LANES.max() + WorldConst.PIPE_RADIUS + 0.3 // lips stick out a little
        assertTrue(RecordMarker.X - 0.7f > outer)
    }

    @Test
    fun overheadPartsHideBeforeTheCameraReachesThem() {
        // Chase camera sits 14 behind the bird; the overhead parts vanish right after the bird.
        assertTrue(RecordMarker.HIDE_BEHIND < 14.0 - 2.0)
    }
}
