package de.robinrehbein.birdy.view.thumb

import de.robinrehbein.birdy.meta.Catalog
import de.robinrehbein.birdy.view.bird.BirdLook
import de.robinrehbein.birdy.view.bird.BirdShots
import kotlin.test.Test
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ThumbnailRenderTest {
    @Test
    fun thumbnailSheet(): Unit = BirdShots(64).use { shots ->
        val thumbs = ThumbnailRenderer(shots.renderer, size = 192)
        val tiles = ArrayList<IntArray>()
        for (skin in listOf(Catalog.skins[0], Catalog.skins.first { it.id == "galaxy" })) {
            for (kind in BirdLook.WORKSHOP_KINDS) {
                for (item in Catalog.items(kind)) {
                    val px = thumbs.thumbnail(kind, item.id, skin)
                    assertTrue(px.size == 192 * 192)
                    // Transparent background, opaque bird.
                    assertTrue(px.count { (it ushr 24) == 0 } > 1000, "${kind.id}:${item.id} transparent bg")
                    assertTrue(px.count { (it ushr 24) == 255 } > 2000, "${kind.id}:${item.id} bird drawn")
                    tiles += px
                }
            }
        }
        val first = Catalog.items(BirdLook.WORKSHOP_KINDS[0])[0].id
        assertSame(tiles[0], thumbs.thumbnail(BirdLook.WORKSHOP_KINDS[0], first, Catalog.skins[0]))
        BirdShots.sheet("thumbnails", tiles, 192, 8, background = 0xfffff3d6.toInt())
        thumbs.dispose()
    }
}
