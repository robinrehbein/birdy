package de.robinrehbein.birdy.ui

import de.robinrehbein.birdy.game.ShopTab
import de.robinrehbein.birdy.meta.Icons
import kotlin.test.Test
import kotlin.test.assertTrue

/** Sanity coverage for the icon path parser and shop-tile brush selection (main-b.md §7.2, §13). */
class IconAndTileTest {
    @Test
    fun everyIconPathParsesWithoutThrowing() {
        // A Compose Path needs a real backend: Skia on desktop (desktopTest has the skiko
        // runtime); the Android host-JVM unit tests only have framework stubs, so skip there.
        if (!skiaPathAvailable()) return
        for (def in Icons.ALL) {
            for (part in def.parts) {
                val d = when (part) {
                    is de.robinrehbein.birdy.meta.IconPart.FilledPath -> part.d
                    is de.robinrehbein.birdy.meta.IconPart.Stroke -> part.d
                    is de.robinrehbein.birdy.meta.IconPart.Circle -> null
                }
                if (d != null) parseSvgPath(d) // must not throw
            }
        }
    }

    @Test
    fun everyShopTabHasATileBrush() {
        for (tab in ShopTab.entries) {
            val brush = tileBrush(tab, flatColor = 0xff0000, trailColors = listOf(0x112233, 0x445566))
            assertTrue(brush is androidx.compose.ui.graphics.Brush)
        }
    }
}

private fun skiaPathAvailable(): Boolean = try {
    androidx.compose.ui.graphics.Path()
    true
} catch (t: Throwable) {
    false
}
