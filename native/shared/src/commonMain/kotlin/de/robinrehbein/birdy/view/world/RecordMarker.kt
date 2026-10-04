package de.robinrehbein.birdy.view.world

import de.robinrehbein.birdy.engine.mesh.ExtrudeOptions
import de.robinrehbein.birdy.engine.mesh.Primitives
import de.robinrehbein.birdy.engine.mesh.Shape
import de.robinrehbein.birdy.engine.mesh.mergeGeometries
import de.robinrehbein.birdy.engine.mesh.translate
import de.robinrehbein.birdy.engine.scene.Geometry
import de.robinrehbein.birdy.engine.scene.Mesh
import de.robinrehbein.birdy.engine.scene.Node
import de.robinrehbein.birdy.engine.scene.ShaderPatch
import de.robinrehbein.birdy.engine.scene.StandardMaterial
import de.robinrehbein.birdy.game.Tuning
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The record marker: a golden finish gate just in front of the row that beats the previous best.
 * Two striped posts outside the lanes carry spinning stars; the beam and a plum banner with the
 * label ("REKORD 32" / "BEST 32", blocky 5×7 letters built from merged pixel runs) hang entirely above the flight ceiling, and a
 * checkered finish line lies on the ground. The overhead parts hide once the gate is behind the
 * bird, so the chase camera (up to ~16 high) never flies through them. Purely visual: the
 * simulation has no collider for it.
 */
internal class RecordMarker(private val haze: ShaderPatch) {
    private fun mat(hex: Int, glow: Int, glowIntensity: Float) = StandardMaterial().apply {
        color.setHex(hex); emissive.setHex(glow); emissiveIntensity = glowIntensity; roughness = 0.45f; flatShading = true
        patch = haze
    }

    private val gold = mat(0xffd400, 0xffb000, GLOW)
    private val orange = mat(0xff7a00, 0xff5a00, 0.5f)
    private val plum = mat(0x5b3456, 0x2a1428, 0.4f)
    private val white = mat(0xffffff, 0xffffff, 0.55f)

    val group = Node("record").apply {
        visible = false
        position.z = Z_FRONT // in front of the pipes and the row cloud (rows come towards +z)
    }

    /** Everything above the flight ceiling; hidden once the gate has passed the bird. */
    private val overhead = Node("record-overhead")
    private val stars = ArrayList<Mesh>(2)
    private val flags = ArrayList<Mesh>(2)
    private val label = Mesh(labelGeometry(""), white, "record-label").apply {
        position.set(0f, BANNER_Y, BANNER_D / 2 + 0.05f); castShadow = false
    }
    private var labelText = ""

    init {
        for (sx in floatArrayOf(-X, X)) {
            group.add(Mesh(post, gold, "record-post").apply { position.set(sx, TOP / 2, 0f); castShadow = false })
            // Orange bands: a candy-striped post that reads as "special" next to the green pipes.
            for (k in 1..BANDS) {
                group.add(Mesh(band, orange, "record-band").apply {
                    position.set(sx, TOP * k / (BANDS + 1), 0f); rotation.x = (PI / 2).toFloat(); castShadow = false
                })
            }
            val star = Mesh(starGeo, orange, "record-star").apply { position.set(sx, TOP + 1.3f, 0f); castShadow = false }
            stars += star
            overhead.add(star)
            // Pennant hanging outward from the beam end, swinging in the wind.
            val flag = Mesh(flagGeo, orange, "record-flag").apply { position.set(sx + Math.signum(sx) * 0.5f, TOP - 0.2f, 0f); castShadow = false }
            if (sx < 0) flag.rotation.y = PI.toFloat()
            flags += flag
            overhead.add(flag)
        }
        overhead.add(Mesh(beam, gold, "record-beam").apply { position.set(0f, TOP, 0f); castShadow = false })
        overhead.add(Mesh(banner, plum, "record-banner").apply { position.set(0f, BANNER_Y, 0f); castShadow = false })
        overhead.add(Mesh(frame, gold, "record-frame").apply { position.set(0f, BANNER_Y, 0f); castShadow = false })
        overhead.add(label)
        group.add(overhead)
        group.add(Mesh(checkWhite, white, "record-line-a").apply { position.set(0f, 0.06f, 0f); castShadow = false })
        group.add(Mesh(checkPlum, plum, "record-line-b").apply { position.set(0f, 0.06f, 0f); castShadow = false })
    }

    /** Shows the gate for this row; [rowZ] is the row's z, [time] the free-running game time. */
    fun sync(visible: Boolean, rowZ: Double, text: String, time: Double) {
        group.visible = visible
        if (!visible) return
        if (text != labelText) {
            labelText = text
            label.geometry = labelGeometry(text)
        }
        overhead.visible = rowZ + Z_FRONT < HIDE_BEHIND
        val t = time.toFloat()
        gold.emissiveIntensity = GLOW + 0.25f * sin(t * 4f)
        for ((i, star) in stars.withIndex()) star.rotation.y = t * 2.2f + i * 1.3f
        for ((i, flag) in flags.withIndex()) {
            val base = if (i == 0) PI.toFloat() else 0f
            flag.rotation.y = base + 0.35f * sin(t * 5f + i * 1.7f)
        }
    }

    companion object {
        const val X = 6.2f
        /** Banner bottom stays above the flight ceiling plus the bird's radius (no fly-through). */
        val BANNER_BOTTOM = (Tuning.CEILING + Tuning.BIRD_RADIUS + 0.6).toFloat()
        const val BANNER_H = 3.2f
        const val BANNER_W = 11.4f
        const val BANNER_D = 0.4f
        val BANNER_Y = BANNER_BOTTOM + BANNER_H / 2
        val TOP = BANNER_BOTTOM + BANNER_H + 0.6f
        const val Z_FRONT = 5f
        /** Overhead parts hide once the gate is this far behind the bird (camera sits at z = 14). */
        const val HIDE_BEHIND = 1.0
        const val GLOW = 0.6f
        const val BANDS = 4

        private val post by lazy { Primitives.cylinder(0.6, 0.7, TOP.toDouble(), 8) }
        private val band by lazy { Primitives.torus(0.72, 0.16, 6, 12) }
        private val beam by lazy { Primitives.box(2.0 * X + 1.2, 0.8, 0.8) }
        private val banner by lazy { Primitives.box(BANNER_W.toDouble(), BANNER_H.toDouble(), BANNER_D.toDouble()) }
        private val frame by lazy {
            val t = 0.3
            val w = BANNER_W + 2 * t
            val h = BANNER_H.toDouble()
            val d = BANNER_D + 0.12
            mergeGeometries(listOf(
                Primitives.box(w, t, d).translate(0.0, h / 2 + t / 2, 0.0),
                Primitives.box(w, t, d).translate(0.0, -h / 2 - t / 2, 0.0),
                Primitives.box(t, h, d).translate(-BANNER_W / 2 - t / 2, 0.0, 0.0),
                Primitives.box(t, h, d).translate(BANNER_W / 2 + t / 2, 0.0, 0.0),
            ))
        }
        private val starGeo by lazy {
            val star = Shape()
            for (i in 0 until 10) {
                val r = if (i % 2 != 0) 0.55 else 1.25
                val a = (i / 10.0) * PI * 2 + PI / 2
                if (i == 0) star.moveTo(cos(a) * r, sin(a) * r) else star.lineTo(cos(a) * r, sin(a) * r)
            }
            Primitives.extrude(star, ExtrudeOptions(depth = 0.35, bevelEnabled = true, bevelThickness = 0.1, bevelSize = 0.1, bevelSegments = 1))
                .translate(0.0, 0.0, -0.175)
        }
        /** A swallow-tail pennant pointing along +x from its pole end. */
        private val flagGeo by lazy {
            val s = Shape()
            s.moveTo(0.0, 0.8); s.lineTo(2.6, 0.8); s.lineTo(1.9, 0.0); s.lineTo(2.6, -0.8); s.lineTo(0.0, -0.8); s.lineTo(0.0, 0.8)
            Primitives.extrude(s, ExtrudeOptions(depth = 0.12, bevelEnabled = false)).translate(0.0, 0.0, -0.06)
        }
        private const val TILES = 14
        private const val TILE_ROWS = 2
        private val checkWhite by lazy { checker(0) }
        private val checkPlum by lazy { checker(1) }

        /** Finish-line tiles across the track: every other tile of the 14×2 grid. */
        private fun checker(parity: Int): Geometry {
            val w = (2.0 * X + 1.2) / TILES
            val d = 0.7
            val parts = ArrayList<Geometry>()
            for (i in 0 until TILES) for (j in 0 until TILE_ROWS) {
                if ((i + j) % 2 != parity) continue
                parts += Primitives.box(w, 0.1, d).translate(-X - 0.6 + w * (i + 0.5), 0.0, d * (j - (TILE_ROWS - 1) / 2.0))
            }
            return mergeGeometries(parts)
        }

        /** Blocky 5×7 letters as merged boxes, centred and scaled to fit inside the banner. */
        internal fun labelGeometry(text: String): Geometry {
            val glyphs = text.uppercase().map { GLYPHS[it] }
            val cols = glyphs.sumOf { (it?.get(0)?.length ?: 3) + 1 } - 1
            val cell = if (cols <= 0) 0.2 else min(0.3, (BANNER_W - 1.0) / cols)
            val parts = ArrayList<Geometry>()
            var cx = 0
            for (g in glyphs) {
                if (g != null) {
                    // One bar per horizontal run of pixels: solid strokes instead of loose cubes.
                    for ((r, row) in g.withIndex()) {
                        var c = 0
                        while (c < row.length) {
                            if (row[c] != '#') { c++; continue }
                            var end = c
                            while (end + 1 < row.length && row[end + 1] == '#') end++
                            val n = end - c + 1
                            parts += Primitives.box(cell * (n - 0.04), cell * 0.96, 0.18)
                                .translate((cx + c + n / 2.0 - cols / 2.0) * cell, (3 - r) * cell, 0.0)
                            c = end + 1
                        }
                    }
                }
                cx += (g?.get(0)?.length ?: 3) + 1
            }
            // An empty label still needs a geometry: a single invisible-sized box.
            if (parts.isEmpty()) parts += Primitives.box(0.001, 0.001, 0.001)
            return mergeGeometries(parts)
        }

        private val GLYPHS: Map<Char, List<String>> = mapOf(
            'B' to listOf("####.", "#...#", "#...#", "####.", "#...#", "#...#", "####."),
            'D' to listOf("####.", "#...#", "#...#", "#...#", "#...#", "#...#", "####."),
            'E' to listOf("#####", "#....", "#....", "####.", "#....", "#....", "#####"),
            'K' to listOf("#...#", "#..#.", "#.#..", "##...", "#.#..", "#..#.", "#...#"),
            'O' to listOf(".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."),
            'R' to listOf("####.", "#...#", "#...#", "####.", "#.#..", "#..#.", "#...#"),
            'S' to listOf(".####", "#....", "#....", ".###.", "....#", "....#", "####."),
            'T' to listOf("#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#.."),
            '0' to listOf(".###.", "#...#", "#..##", "#.#.#", "##..#", "#...#", ".###."),
            '1' to listOf("..#..", ".##..", "..#..", "..#..", "..#..", "..#..", ".###."),
            '2' to listOf(".###.", "#...#", "....#", "...#.", "..#..", ".#...", "#####"),
            '3' to listOf("####.", "....#", "....#", ".###.", "....#", "....#", "####."),
            '4' to listOf("...#.", "..##.", ".#.#.", "#..#.", "#####", "...#.", "...#."),
            '5' to listOf("#####", "#....", "####.", "....#", "....#", "#...#", ".###."),
            '6' to listOf(".###.", "#....", "#....", "####.", "#...#", "#...#", ".###."),
            '7' to listOf("#####", "....#", "...#.", "..#..", ".#...", ".#...", ".#..."),
            '8' to listOf(".###.", "#...#", "#...#", ".###.", "#...#", "#...#", ".###."),
            '9' to listOf(".###.", "#...#", "#...#", ".####", "....#", "....#", ".###."),
        )
    }
}
