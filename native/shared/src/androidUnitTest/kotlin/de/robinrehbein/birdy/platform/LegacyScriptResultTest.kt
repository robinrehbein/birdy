package de.robinrehbein.birdy.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LegacyScriptResultTest {
    @Test
    fun emptyObjectMeansNothingToMigrate() {
        assertEquals(emptyMap(), parseLegacyScriptResult("\"{}\""))
    }

    @Test
    fun parsesKeysFromWrappedJson() {
        val raw = "\"{\\\"birdy-best\\\":\\\"12\\\",\\\"birdy-lang\\\":\\\"de\\\"}\""
        assertEquals(mapOf("birdy-best" to "12", "birdy-lang" to "de"), parseLegacyScriptResult(raw))
    }

    @Test
    fun missingOrMalformedResultIsAFailureNotEmpty() {
        assertNull(parseLegacyScriptResult(null))
        assertNull(parseLegacyScriptResult("null"))
        assertNull(parseLegacyScriptResult("garbage"))
        assertNull(parseLegacyScriptResult("\"[1,2]\""))
    }
}
