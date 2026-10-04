package de.robinrehbein.birdy.meta

import de.robinrehbein.birdy.platform.LocalDay

/**
 * Proleptic-Gregorian civil-date <-> day-count conversion (Howard Hinnant's `days_from_civil` /
 * `civil_from_days`), used only to compute "yesterday" for the gift-streak and mission-seed day
 * keys (progress.js `dayKey(-1)`). [Clock] only exposes "today"; this stays pure calendar math.
 */
private fun daysFromCivil(y: Int, m: Int, d: Int): Long {
    val yy = (if (m <= 2) y - 1 else y).toLong()
    val era = (if (yy >= 0) yy else yy - 399) / 400
    val yoe = yy - era * 400 // [0, 399]
    val doy = (153L * (if (m > 2) m - 3 else m + 9) + 2) / 5 + d - 1 // [0, 365]
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy // [0, 146096]
    return era * 146097L + doe - 719468L
}

private fun civilFromDays(z0: Long): Triple<Int, Int, Int> {
    val z = z0 + 719468
    val era = (if (z >= 0) z else z - 146096) / 146097
    val doe = z - era * 146097 // [0, 146096]
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365 // [0, 399]
    val y = yoe + era * 400
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100) // [0, 365]
    val mp = (5 * doy + 2) / 153 // [0, 11]
    val d = (doy - (153 * mp + 2) / 5 + 1).toInt() // [1, 31]
    val m = (if (mp < 10) mp + 3 else mp - 9).toInt() // [1, 12]
    val yFinal = (if (m <= 2) y + 1 else y).toInt()
    return Triple(yFinal, m, d)
}

/** Days since 1970-01-01 of this civil date. */
fun LocalDay.epochDay(): Long = daysFromCivil(year, month, day)

/** [LocalDay] shifted by [offsetDays] (may be negative), matching progress.js `dayKey(offset)`. */
fun LocalDay.plusDays(offsetDays: Int): LocalDay {
    if (offsetDays == 0) return this
    val (y, m, d) = civilFromDays(daysFromCivil(year, month, day) + offsetDays)
    return LocalDay(y, m, d)
}

/** progress.js `dayKey(offsetDays)`: non-padded `Y-M-D`, month 1-based — see [LocalDay.key]. */
fun LocalDay.keyOffset(offsetDays: Int): String = plusDays(offsetDays).key()
