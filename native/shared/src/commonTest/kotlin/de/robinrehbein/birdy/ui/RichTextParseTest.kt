package de.robinrehbein.birdy.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Pure-logic coverage for the `[icon]`/`<br>`/`<b>` splitter behind [RichText] (main-b.md §13). */
class RichTextParseTest {
    @Test
    fun knownIconBecomesIconRun() {
        val runs = parseRich("[gift] Tagesgeschenk")
        assertTrue(runs.first() is RichRun.Icon)
        assertEquals("gift", (runs.first() as RichRun.Icon).name)
    }

    @Test
    fun unknownBracketStaysLiteral() {
        val runs = parseRich("[nope] hi")
        val text = runs.filterIsInstance<RichRun.Text>().joinToString("") { it.s }
        assertTrue(text.contains("[nope]"))
    }

    @Test
    fun brSplitsIntoSeparateLines() {
        val runs = parseRich("line one<br>line two")
        assertTrue(runs.any { it is RichRun.Break })
    }

    @Test
    fun boldSpanIsMarked() {
        val runs = parseRich("plain <b>bold</b> plain")
        val bold = runs.filterIsInstance<RichRun.Text>().first { it.s == "bold" }
        assertTrue(bold.bold)
    }
}
