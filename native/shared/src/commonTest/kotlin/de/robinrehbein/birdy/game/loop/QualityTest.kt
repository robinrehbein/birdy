package de.robinrehbein.birdy.game.loop

import de.robinrehbein.birdy.meta.Settings
import de.robinrehbein.birdy.platform.MemoryKeyValueStore
import de.robinrehbein.birdy.platform.StorageKeys
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QualityTest {
    private fun window(q: QualityController, fps: Double, judge: Boolean = true) {
        // Frames of 1/fps until the 1.5 s window closes.
        while (!q.tick(1.0 / fps, judge)) Unit
    }

    @Test
    fun oneWayRatchetPersisted() {
        val store = MemoryKeyValueStore()
        val backend = FakeRenderer()
        val q = QualityController(Settings(store), backend)
        q.density = 2.625
        assertEquals(0, q.quality)
        assertEquals((2.0 / 2.625).toFloat(), backend.resolutionScale, 1e-6f)
        assertTrue(backend.shadowsEnabled)
        // The first window is inside the 3 s cooldown and never judged.
        window(q, 30.0)
        assertEquals(0, q.quality)
        // Two slow judged windows in a row lower one tier.
        window(q, 30.0)
        assertEquals(0, q.quality)
        window(q, 30.0)
        assertEquals(1, q.quality)
        assertEquals("1", store.getString(StorageKeys.QUALITY))
        assertEquals((1.5 / 2.625).toFloat(), backend.resolutionScale, 1e-6f)
        // New 3 s cooldown, then a single hiccup (slow, fast, slow) does not lower it.
        window(q, 30.0)
        window(q, 30.0)
        window(q, 60.0)
        window(q, 30.0)
        assertEquals(1, q.quality)
        window(q, 30.0)
        assertEquals(2, q.quality)
        // Not judged outside runs.
        repeat(10) { window(q, 20.0, judge = false) }
        assertEquals(2, q.quality)
        // Down to 4: pixel ratio 1 and shadows off, then it stops.
        repeat(20) { window(q, 20.0) }
        assertEquals(4, q.quality)
        assertFalse(backend.shadowsEnabled)
        assertEquals((1.0 / 2.625).toFloat(), backend.resolutionScale, 1e-6f)
        assertEquals("4", store.getString(StorageKeys.QUALITY))
        // Never raised again, even at 120 fps.
        repeat(20) { window(q, 120.0) }
        assertEquals(4, q.quality)
    }

    @Test
    fun savedTierAppliesAtBoot() {
        val store = MemoryKeyValueStore(mapOf(StorageKeys.QUALITY to "4"))
        val backend = FakeRenderer()
        val q = QualityController(Settings(store), backend)
        assertEquals(4, q.quality)
        assertFalse(backend.shadowsEnabled)
        val q2 = QualityController(Settings(MemoryKeyValueStore(mapOf(StorageKeys.QUALITY to "2"))), backend)
        q2.density = 3.0
        assertTrue(backend.shadowsEnabled)
        assertEquals((1.25 / 3.0).toFloat(), backend.resolutionScale, 1e-6f)
    }

    @Test
    fun secretTaps() {
        val s = SecretTaps()
        assertFalse(s.tap(0.0))
        assertFalse(s.tap(300.0))
        assertFalse(s.tap(600.0))
        assertFalse(s.tap(1100.0)) // 500 ms gap: starts over at 1
        assertFalse(s.tap(1200.0))
        assertFalse(s.tap(1300.0))
        assertFalse(s.tap(1400.0))
        assertTrue(s.tap(1500.0))
    }
}
