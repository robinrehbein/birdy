package de.robinrehbein.birdy.game.loop

import de.robinrehbein.birdy.Golden
import de.robinrehbein.birdy.engine.scene.PerspectiveCamera
import de.robinrehbein.birdy.game.GameMode
import de.robinrehbein.birdy.game.GameState
import de.robinrehbein.birdy.game.Menu
import de.robinrehbein.birdy.game.PowerType
import de.robinrehbein.birdy.game.UiCommand
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CameraTest {
    @Test
    fun fovMatchesGolden() {
        val rows = Golden.json("main-b-camera-fov.json").jsonArray
        assertTrue(rows.size >= 6)
        for (r in rows) {
            val o = r.jsonObject
            val speed = o["speed"]!!.jsonPrimitive.double
            val star = o["star"]!!.jsonPrimitive.boolean
            assertEquals(o["fov68"]!!.jsonPrimitive.double, CameraRig.targetFov(68.0, true, speed, star), 1e-9, "68 @ $speed $star")
            assertEquals(o["fov74"]!!.jsonPrimitive.double, CameraRig.targetFov(74.0, true, speed, star), 1e-9, "74 @ $speed $star")
        }
        // No speed kick outside runs.
        assertEquals(68.0, CameraRig.targetFov(68.0, false, 36.0, true))
    }

    @Test
    fun resizeSwitchesBaseFov() {
        assertEquals(74.0, CameraRig.baseFovFor(1080.0 / 2400))
        assertEquals(68.0, CameraRig.baseFovFor(1080.0 / 1920))
        assertEquals(68.0, CameraRig.baseFovFor(0.5))
        val rig = CameraRig(PerspectiveCamera())
        rig.resize(1080, 2400)
        assertEquals(74f, rig.camera.fov)
        rig.resize(1080, 1920)
        assertEquals(68f, rig.camera.fov)
    }

    @Test
    fun fovEasesTowardsTheSpeedKick() {
        val rig = CameraRig(PerspectiveCamera(), Random(1))
        rig.resize(1080, 2400)
        val s = GameState().apply { mode = GameMode.Playing; speed = 36.0; power[PowerType.Star.ordinal] = 3.0 }
        repeat(600) { rig.update(1.0 / 60, s, null) }
        assertEquals(86f, rig.camera.fov, 0.06f)
    }

    @Test
    fun menuFrameFromLayout() {
        val f = MenuFrameInfo.fromLayout(0.2, 0.6)
        assertEquals(0.4, f.center, 1e-12)
        assertEquals(1.0, f.far, 1e-12)
        // Cramped: free = max(0.12, …) -> far clamps at 2.4 / 0.4/0.2 = 2.
        assertEquals(2.0, MenuFrameInfo.fromLayout(0.3, 0.5).far, 1e-12)
        assertEquals(0.4 / 0.12, MenuFrameInfo.fromLayout(0.3, 0.35).far.let { if (it > 2.4) 0.4 / 0.12 else it }, 1.0)
        assertEquals(2.4, MenuFrameInfo.fromLayout(0.3, 0.3).far, 1e-12)
    }

    @Test
    fun menuFramingPutsTheBirdAtTheFreeBandCentre() {
        val h = LoopHarness()
        h.post(UiCommand.MenuFrame(Menu.Start, 0.25f, 0.55f))
        h.frames(4.0)
        val p = h.game.game.rig.project(h.state.x, h.state.y, 0.0)
        assertTrue(abs(p[1] - 0.4) < 0.03, "bird at y=${p[1]}")
        assertTrue(h.game.game.rig.viewShift != 0.0)
        // Leaving the menu eases the view shift back to zero.
        h.startRun()
        h.frames(3.0)
        assertEquals(0.0, h.game.game.rig.viewShift)
        assertEquals(null, h.game.camera.view)
    }
}
