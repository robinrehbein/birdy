package de.robinrehbein.birdy.engine

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.abs
import kotlin.math.max
import kotlin.test.assertTrue
import kotlin.test.fail

/** Small JSON accessors for the engine golden tests. */
internal operator fun JsonElement.get(key: String): JsonElement = jsonObject[key] ?: fail("missing key $key")
internal operator fun JsonElement.get(i: Int): JsonElement = jsonArray[i]
internal val JsonElement.d: Double get() = jsonPrimitive.double
internal val JsonElement.i: Int get() = jsonPrimitive.int
internal val JsonElement.list: List<JsonElement> get() = jsonArray
internal val JsonElement.obj: JsonObject get() = jsonObject
internal val JsonElement.isNull: Boolean get() = this is JsonNull
internal fun JsonElement.doubles(): DoubleArray = (this as JsonArray).map { it.d }.toDoubleArray()
internal fun JsonElement.ints(): IntArray = (this as JsonArray).map { it.i }.toIntArray()

internal fun assertClose(expected: Double, actual: Double, tol: Double = 1e-6, msg: String = "") {
    val t = tol * max(1.0, abs(expected))
    assertTrue(abs(expected - actual) <= t, "$msg expected $expected but was $actual (tol $t)")
}

internal fun assertClose(expected: DoubleArray, actual: DoubleArray, tol: Double = 1e-6, msg: String = "") {
    assertTrue(expected.size == actual.size, "$msg size ${expected.size} != ${actual.size}")
    for (i in expected.indices) assertClose(expected[i], actual[i], tol, "$msg[$i]")
}

internal fun FloatArray.toDoubles(): DoubleArray = DoubleArray(size) { this[it].toDouble() }
