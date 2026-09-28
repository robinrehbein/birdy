package de.robinrehbein.birdy.meta

import de.robinrehbein.birdy.Golden
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class IconsGoldenTest {
    private val golden = Golden.json("meta-icons.json").jsonObject

    @Test
    fun iconNamesMatch() {
        val expected = golden["iconNames"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertEquals(expected, Icons.ALL.map { it.name })
        assertEquals(expected.size, Icons.ALL.size)
    }

    @Test
    fun badgeColorsMatch() {
        val badges = golden["badgeColors"]!!.jsonObject
        for (icon in Icons.ALL) {
            assertEquals(badges[icon.name]!!.jsonPrimitive.content, icon.badge, icon.name)
        }
    }

    @Test
    fun parsedPartsMatch() {
        val parsed = golden["parsedParts"]!!.jsonObject
        for (icon in Icons.ALL) {
            val expectedParts = parsed[icon.name]!!.jsonArray
            assertEquals(expectedParts.size, icon.parts.size, icon.name)
            expectedParts.forEachIndexed { i, el ->
                val o = el.jsonObject
                val part = icon.parts[i]
                when (o["kind"]!!.jsonPrimitive.content) {
                    "circle" -> {
                        val c = part as IconPart.Circle
                        assertEquals(o["cx"]!!.jsonPrimitive.content.toDouble(), c.cx, "${icon.name}[$i].cx")
                        assertEquals(o["cy"]!!.jsonPrimitive.content.toDouble(), c.cy, "${icon.name}[$i].cy")
                        assertEquals(o["r"]!!.jsonPrimitive.content.toDouble(), c.r, "${icon.name}[$i].r")
                        assertEquals(o["fill"]!!.jsonPrimitive.content, c.fill, "${icon.name}[$i].fill")
                    }
                    "openStroke" -> {
                        val s = part as IconPart.Stroke
                        assertEquals(o["d"]!!.jsonPrimitive.content, s.d, "${icon.name}[$i].d")
                        assertEquals(o["strokeColor"]!!.jsonPrimitive.content, s.color, "${icon.name}[$i].strokeColor")
                    }
                    else -> {
                        val fp = part as IconPart.FilledPath
                        assertEquals(o["d"]!!.jsonPrimitive.content, fp.d, "${icon.name}[$i].d")
                        assertEquals(o["fill"]!!.jsonPrimitive.content, fp.fill, "${icon.name}[$i].fill")
                    }
                }
            }
        }
    }

    @Test
    fun richTextParsesIconTags() {
        val segments = RichText.parse("[gift] Daily gift · +{n}")
        assertEquals(listOf(RichSegment.Icon("gift"), RichSegment.Text(" Daily gift · +{n}")), segments)
        val noTag = RichText.parse("Plain text")
        assertEquals(listOf(RichSegment.Text("Plain text")), noTag)
        val unknown = RichText.parse("[notAnIcon] text")
        assertEquals(listOf(RichSegment.Text("[notAnIcon] text")), unknown)
    }
}
