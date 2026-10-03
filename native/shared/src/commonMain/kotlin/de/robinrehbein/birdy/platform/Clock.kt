package de.robinrehbein.birdy.platform

/** Local calendar day; [month] is 1-based. */
data class LocalDay(val year: Int, val month: Int, val day: Int) {
    /**
     * The JS day key `${y}-${m+1}-${d}`: NOT zero-padded (e.g. "2026-9-8"). It also seeds the daily
     * missions RNG, so the format must never change (meta.md risk list).
     */
    fun key(): String = "$year-$month-$day"
}

/** Time source; injected so tests and screenshots can pin time. */
interface Clock {
    /** Wall clock, epoch millis (`Date.now()`). */
    fun nowMillis(): Long
    /** Monotonic nanoseconds for frame timing (`performance.now()`). */
    fun monotonicNanos(): Long
    /** Local calendar day in the device time zone. */
    fun today(): LocalDay
    /** Local time zone offset from UTC at [atMillis] (DST aware); 0 where unknown. */
    fun utcOffsetMillis(atMillis: Long): Long = 0L
}

/** Fixed/controllable clock for tests and deterministic screenshots. */
class FakeClock(var millis: Long = 0L, var day: LocalDay = LocalDay(2026, 1, 1), var offsetMillis: Long = 0L) : Clock {
    override fun utcOffsetMillis(atMillis: Long): Long = offsetMillis
    var nanos = 0L
    override fun nowMillis(): Long = millis
    override fun monotonicNanos(): Long = nanos
    override fun today(): LocalDay = day
}
