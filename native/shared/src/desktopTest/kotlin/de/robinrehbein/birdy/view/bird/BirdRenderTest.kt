package de.robinrehbein.birdy.view.bird

import de.robinrehbein.birdy.meta.Catalog
import de.robinrehbein.birdy.view.bird.BirdShots.Companion.coverage
import de.robinrehbein.birdy.view.bird.BirdShots.Companion.save
import de.robinrehbein.birdy.view.bird.BirdShots.Companion.sheet
import kotlin.test.Test
import kotlin.test.assertTrue

/** Renders the bird rig through the real GLSL ES pipeline; PNGs land in native/build/birdtest. */
class BirdRenderTest {
    private val bg = 0x9fd8f0

    private fun shoot(shots: BirdShots, rig: BirdRig, time: Double = 1.3): IntArray {
        val scene = shots.studio(bg)
        scene.add(rig.root)
        rig.animateWings(1.2)
        rig.skinFx.tick(time)
        return shots.render(scene, shots.birdCamera()).also { scene.remove(rig.root) }
    }

    @Test
    fun defaultBirdAndSkins(): Unit = BirdShots().use { shots ->
        val rig = BirdRig()
        rig.setSkin(Catalog.skins[0])
        val px = shoot(shots, rig)
        save("bird-default", px, shots.size)
        assertTrue(coverage(px, bg) > 0.15, "bird covers the frame")
        // Plenty of sunny-yellow body pixels.
        val yellow = px.count { ((it shr 16) and 0xff) > 180 && ((it shr 8) and 0xff) in 120..225 && (it and 0xff) < 110 }
        assertTrue(yellow > px.size / 20, "yellow body pixels $yellow")

        val regular = listOf("sky", "cardinal", "robin", "parrot", "penguin", "gold")
        val tiles = regular.map { id ->
            rig.setSkin(Catalog.skins.first { it.id == id })
            shoot(shots, rig).also { save("bird-skin-$id", it, shots.size) }
        }
        sheet("bird-skins-regular", tiles, shots.size, 3)
        assertTrue(tiles[0].indices.count { tiles[0][it] != tiles[1][it] } > 5000, "skins differ")
    }

    @Test
    fun animatedFxSkins(): Unit = BirdShots().use { shots ->
        val rig = BirdRig()
        val fxSkins = Catalog.skins.filter { it.fx != null }
        val tiles = fxSkins.map { skin ->
            rig.setSkin(skin)
            shoot(shots, rig).also { save("bird-fx-${skin.fx}", it, shots.size) }
        }
        sheet("bird-skins-fx", tiles, shots.size, 4)
        // Effects move with time: lava cracks crawl.
        rig.setSkin(fxSkins.first { it.fx == "lava" })
        val a = shoot(shots, rig, 1.0)
        val b = shoot(shots, rig, 2.5)
        assertTrue(a.indices.count { a[it] != b[it] } > 200, "lava animates")
    }

    @Test
    fun workshopParts(): Unit = BirdShots(256).use { shots ->
        val rig = BirdRig()
        rig.setSkin(Catalog.skins[0])
        for (kind in BirdLook.WORKSHOP_KINDS) {
            val tiles = Catalog.items(kind).map { item ->
                rig.setLook(BirdLook().with(kind, item.id))
                shoot(shots, rig).also { save("part-${kind.id}-${item.id}", it, shots.size) }
            }
            sheet("parts-${kind.id}", tiles, shots.size, 4)
            // Every non-default part changes the picture, except the JS "brows": overEyes() overwrites
            // the box's own offset and tilt, which leaves it inside the eye sphere (bird.js:305).
            for (i in 1 until tiles.size) {
                if (kind.id == "eyes" && Catalog.items(kind)[i].id == "brows") continue
                assertTrue(tiles[0].indices.count { tiles[0][it] != tiles[i][it] } > 50, "${kind.id} ${Catalog.items(kind)[i].id} visible")
            }
        }
    }

    @Test
    fun glowTintsBirdButNotHalo(): Unit = BirdShots(256).use { shots ->
        val rig = BirdRig()
        rig.setSkin(Catalog.skins[0])
        rig.setLook(BirdLook(hat = "halo"))
        val plain = shoot(shots, rig)
        rig.setGlow(de.robinrehbein.birdy.engine.math.Color().setHSL(0.6, 1.0, 0.5), 0.7f)
        val glow = shoot(shots, rig)
        save("bird-glow", glow, shots.size)
        assertTrue(plain.indices.count { plain[it] != glow[it] } > 1000)
    }
}
