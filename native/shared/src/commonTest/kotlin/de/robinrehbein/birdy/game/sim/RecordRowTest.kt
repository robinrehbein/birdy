package de.robinrehbein.birdy.game.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecordRowTest {
    @Test
    fun markerSitsOnTheRowThatBeatsTheBest() {
        // Passing row index n scores n + 1, so row index == best is the record row.
        assertTrue(isRecordRow(5, 5))
        assertTrue(isRecordRow(40, 40))
        assertFalse(isRecordRow(4, 5))
        assertFalse(isRecordRow(6, 5))
    }

    @Test
    fun noMarkerForSmallRecords() {
        for (best in 0..4) assertFalse(isRecordRow(best, best), "best $best")
    }

    @Test
    fun exactlyOneRowIsMarked() {
        assertEquals(1, (0..100).count { isRecordRow(it, 17) })
    }
}
