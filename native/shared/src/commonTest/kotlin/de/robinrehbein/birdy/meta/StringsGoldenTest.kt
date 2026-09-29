package de.robinrehbein.birdy.meta

import de.robinrehbein.birdy.Golden
import de.robinrehbein.birdy.platform.MemoryKeyValueStore
import de.robinrehbein.birdy.platform.StorageKeys
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class StringsGoldenTest {
    private val golden = Golden.json("meta-i18n.json").jsonObject

    @Test
    fun everyKeyMatchesBothLanguages() {
        val de = golden["de"]!!.jsonObject
        val en = golden["en"]!!.jsonObject
        assertEquals(de.keys, en.keys)
        assertEquals(de.keys, TableStrings.STRINGS.keys, "Kotlin strings must match the documented translation table")
        val storage = MemoryKeyValueStore()
        val strDe = TableStrings(storage, "de-DE")
        val strEn = TableStrings(storage, "en-US")
        // The golden dump calls i18n.js's t(key) with NO values argument at all, which skips
        // substitution entirely and keeps literal "{n}" etc.; our t() (meta.md §3.2's stated port
        // behaviour) always substitutes, turning a missing placeholder into "". Normalize the
        // golden value the same way before comparing so this is an intentional, documented
        // difference rather than a real mismatch.
        for (key in de.keys) {
            assertEquals(TableStrings.substitute(de[key]!!.jsonPrimitive.content, emptyMap()), strDe.t(key), "de.$key")
            assertEquals(TableStrings.substitute(en[key]!!.jsonPrimitive.content, emptyMap()), strEn.t(key), "en.$key")
        }
    }

    @Test
    fun placeholderSubstitution() {
        val storage = MemoryKeyValueStore()
        val en = TableStrings(storage, "en-US")
        assertEquals("▶ Watch an ad · +30 coins (3/3 today)", en.t("rewardAd", mapOf("n" to 3)))
        assertEquals("▶ Watch an ad · +30 coins (/3 today)", en.t("rewardAd"))
        assertEquals("[fire] Streak: day 5 · tomorrow +60", en.t("streak", mapOf("d" to 5, "n" to 60)))
    }

    @Test
    fun fallbackAndUnknownKey() {
        val storage = MemoryKeyValueStore()
        // A key present only under a hypothetical missing translation would fall back to German;
        // an entirely unknown key returns itself verbatim (i18n.js `t()`).
        val en = TableStrings(storage, "en-US")
        assertEquals("___doesNotExist___", en.t("___doesNotExist___"))
    }

    @Test
    fun setLangUnknownIsNoOp() {
        val storage = MemoryKeyValueStore()
        val strings = TableStrings(storage, "en-US")
        // Lang is a closed enum here (unlike JS's stringly-typed setLang), so "unknown language"
        // is naturally impossible to express; verify setLang persists only valid values instead.
        strings.setLang(Lang.DE)
        assertEquals("de", storage.getString(StorageKeys.LANG))
        assertEquals(Lang.DE, strings.lang.value)
    }

    @Test
    fun detectPrefersSavedLangOverDevice() {
        val storage = MemoryKeyValueStore(mapOf(StorageKeys.LANG to "en"))
        assertEquals(Lang.EN, TableStrings.detect(storage, "de-DE"))
        assertEquals(Lang.DE, TableStrings.detect(MemoryKeyValueStore(), "de-AT"))
        assertEquals(Lang.EN, TableStrings.detect(MemoryKeyValueStore(), "fr-FR"))
    }
}
