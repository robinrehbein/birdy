package de.robinrehbein.birdy.meta

import de.robinrehbein.birdy.Golden
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

private fun JsonObject.deEn(key: String) = LocalizedText(this[key]!!.jsonObject["de"]!!.jsonPrimitive.content, this[key]!!.jsonObject["en"]!!.jsonPrimitive.content)

class CatalogGoldenTest {
    private val golden = Golden.json("meta-catalog.json").jsonObject

    @Test
    fun countsAndFreeFirst() {
        val counts = golden["countsPerKind"]!!.jsonObject
        assertEquals(counts["skin"]!!.jsonPrimitive.int, Catalog.skins.size)
        assertEquals(counts["pattern"]!!.jsonPrimitive.int, Catalog.patterns.size)
        assertEquals(counts["hat"]!!.jsonPrimitive.int, Catalog.hats.size)
        assertEquals(counts["eyes"]!!.jsonPrimitive.int, Catalog.eyes.size)
        assertEquals(counts["beak"]!!.jsonPrimitive.int, Catalog.beaks.size)
        assertEquals(counts["trail"]!!.jsonPrimitive.int, Catalog.trails.size)
        assertEquals(counts["world"]!!.jsonPrimitive.int, Catalog.worlds.size)
        assertEquals(counts["pipe"]!!.jsonPrimitive.int, Catalog.pipes.size)
        assertEquals(golden["upgradeMax"]!!.jsonPrimitive.int, Catalog.UPGRADE_MAX)
        for (kind in Kind.entries) assertEquals(0, Catalog.free(kind).price, "kind ${kind.id} free item must be price 0")
    }

    @Test
    fun skins() {
        val arr = golden["skins"]!!.jsonArray
        assertEquals(arr.size, Catalog.skins.size)
        arr.forEachIndexed { i, el ->
            val o = el.jsonObject
            val s = Catalog.skins[i]
            assertEquals(o["id"]!!.jsonPrimitive.content, s.id)
            assertEquals(o.deEn("name"), s.name)
            assertEquals(o["price"]!!.jsonPrimitive.int, s.price)
            assertEquals(o["body"]!!.jsonPrimitive.int, s.body, s.id)
            assertEquals(o["belly"]!!.jsonPrimitive.int, s.belly, s.id)
            assertEquals(o["wing"]!!.jsonPrimitive.int, s.wing, s.id)
            assertEquals(o["cover"]!!.jsonPrimitive.int, s.cover, s.id)
            assertEquals(o["tail"]!!.jsonPrimitive.int, s.tail, s.id)
            assertEquals(o["beak"]!!.jsonPrimitive.int, s.beak, s.id)
            assertEquals(o["beakLow"]!!.jsonPrimitive.int, s.beakLow, s.id)
            assertEquals(o["metal"]?.jsonPrimitive?.content?.toBoolean() ?: false, s.metal, s.id)
            assertEquals(o["fx"]?.jsonPrimitive?.content, s.fx, s.id)
            assertEquals(o["rare"]?.jsonPrimitive?.content?.toBoolean() ?: false, s.rare, s.id)
        }
    }

    private fun accessory(golden: kotlinx.serialization.json.JsonArray, items: List<AccessoryItem>) {
        assertEquals(golden.size, items.size)
        golden.forEachIndexed { i, el ->
            val o = el.jsonObject
            assertEquals(o["id"]!!.jsonPrimitive.content, items[i].id)
            assertEquals(o.deEn("name"), items[i].name)
            assertEquals(o["price"]!!.jsonPrimitive.int, items[i].price)
            assertEquals(o["icon"]!!.jsonPrimitive.content, items[i].icon, items[i].id)
        }
    }

    @Test fun patterns() = accessory(golden["patterns"]!!.jsonArray, Catalog.patterns)
    @Test fun hats() = accessory(golden["hats"]!!.jsonArray, Catalog.hats)
    @Test fun eyes() = accessory(golden["eyes"]!!.jsonArray, Catalog.eyes)
    @Test fun beaks() = accessory(golden["beaks"]!!.jsonArray, Catalog.beaks)

    @Test
    fun trails() {
        val arr = golden["trails"]!!.jsonArray
        assertEquals(arr.size, Catalog.trails.size)
        arr.forEachIndexed { i, el ->
            val o = el.jsonObject
            val t = Catalog.trails[i]
            assertEquals(o["id"]!!.jsonPrimitive.content, t.id)
            assertEquals(o["price"]!!.jsonPrimitive.int, t.price)
            val colors = o["colors"]!!.jsonArray.map { it.jsonPrimitive.int }
            assertEquals(colors, t.colors, t.id)
            if (colors.isNotEmpty()) {
                assertEquals(o["size"]!!.jsonPrimitive.doubleOrNull?.toFloat(), t.size, t.id)
                assertEquals(o["life"]!!.jsonPrimitive.doubleOrNull?.toFloat(), t.life, t.id)
                assertEquals(o["gravity"]!!.jsonPrimitive.doubleOrNull?.toFloat(), t.gravity, t.id)
                assertEquals(o["speed"]!!.jsonPrimitive.doubleOrNull?.toFloat(), t.speed, t.id)
            }
        }
    }

    @Test
    fun worlds() {
        val arr = golden["worlds"]!!.jsonArray
        assertEquals(arr.size, Catalog.worlds.size)
        arr.forEachIndexed { i, el ->
            val o = el.jsonObject
            val w = Catalog.worlds[i]
            assertEquals(o["id"]!!.jsonPrimitive.content, w.id)
            assertEquals(o["icon"]!!.jsonPrimitive.content, w.icon, w.id)
            assertEquals(o["price"]!!.jsonPrimitive.int, w.price, w.id)
            assertEquals(o["scenery"]!!.jsonPrimitive.content, w.scenery, w.id)
            assertEquals(o["top"]!!.jsonPrimitive.int, w.palette.top, w.id)
            assertEquals(o["horizon"]!!.jsonPrimitive.int, w.palette.horizon, w.id)
            assertEquals(o["hemiSky"]!!.jsonPrimitive.int, w.palette.hemiSky, w.id)
            assertEquals(o["hemiGround"]!!.jsonPrimitive.int, w.palette.hemiGround, w.id)
            assertEquals(o["hemiI"]!!.jsonPrimitive.doubleOrNull?.toFloat(), w.palette.hemiI, w.id)
            assertEquals(o["sun"]!!.jsonPrimitive.int, w.palette.sun, w.id)
            assertEquals(o["sunI"]!!.jsonPrimitive.doubleOrNull?.toFloat(), w.palette.sunI, w.id)
            assertEquals(o["tint"]!!.jsonPrimitive.int, w.palette.tint, w.id)
            assertEquals(o["clouds"]!!.jsonPrimitive.int, w.palette.clouds, w.id)
            assertEquals(o["grass"]!!.jsonPrimitive.int, w.palette.grass, w.id)
            assertEquals(o["track"]!!.jsonPrimitive.int, w.palette.track, w.id)
            val road = o["road"]?.jsonArray?.map { it.jsonPrimitive.int }
            assertEquals(road, w.road, w.id)
            val pipesObj = o["pipes"]?.jsonObject
            if (pipesObj != null) {
                assertEquals(pipesObj["pipe"]!!.jsonPrimitive.int, w.pipes?.pipe, w.id)
                assertEquals(pipesObj["light"]!!.jsonPrimitive.int, w.pipes?.light, w.id)
                assertEquals(pipesObj["dark"]!!.jsonPrimitive.int, w.pipes?.dark, w.id)
            } else {
                assertEquals(null, w.pipes, w.id)
            }
        }
    }

    @Test
    fun pipes() {
        val arr = golden["pipes"]!!.jsonArray
        assertEquals(arr.size, Catalog.pipes.size)
        arr.forEachIndexed { i, el ->
            val o = el.jsonObject
            val p = Catalog.pipes[i]
            assertEquals(o["id"]!!.jsonPrimitive.content, p.id)
            assertEquals(o["price"]!!.jsonPrimitive.int, p.price)
            assertEquals(o["pipe"]!!.jsonPrimitive.int, p.colors.pipe, p.id)
            assertEquals(o["light"]!!.jsonPrimitive.int, p.colors.light, p.id)
            assertEquals(o["dark"]!!.jsonPrimitive.int, p.colors.dark, p.id)
        }
    }

    @Test
    fun upgrades() {
        val arr = golden["upgrades"]!!.jsonArray
        assertEquals(arr.size, Catalog.upgrades.size)
        arr.forEachIndexed { i, el ->
            val o = el.jsonObject
            val u = Catalog.upgrades[i]
            assertEquals(o["id"]!!.jsonPrimitive.content, u.id)
            assertEquals(o["icon"]!!.jsonPrimitive.content, u.icon)
            assertEquals(o.deEn("name"), u.name)
            assertEquals(o.deEn("text"), u.text)
            assertEquals(o["prices"]!!.jsonArray.map { it.jsonPrimitive.int }, u.prices)
        }
    }

    @Test
    fun rareSkinsMatchAchievementLinks() {
        val rare = golden["rareSkins"]?.jsonArray?.map { it.jsonObject["id"]!!.jsonPrimitive.content }
            ?: Catalog.skins.filter { it.rare }.map { it.id }
        val fromCatalog = Catalog.skins.filter { it.rare }.map { it.id }
        assertEquals(rare.toSet(), fromCatalog.toSet())
        assertEquals(7, fromCatalog.size)
    }
}
