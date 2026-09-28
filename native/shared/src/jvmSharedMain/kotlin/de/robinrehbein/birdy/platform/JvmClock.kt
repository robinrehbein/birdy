package de.robinrehbein.birdy.platform

import java.time.LocalDate

/** [Clock] for JVM platforms (Android + desktop) using System time and the default time zone. */
class JvmClock : Clock {
    override fun nowMillis(): Long = System.currentTimeMillis()
    override fun monotonicNanos(): Long = System.nanoTime()
    override fun today(): LocalDay {
        val d = LocalDate.now()
        return LocalDay(d.year, d.monthValue, d.dayOfMonth)
    }
}
