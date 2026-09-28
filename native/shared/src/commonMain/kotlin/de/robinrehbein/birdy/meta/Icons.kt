package de.robinrehbein.birdy.meta

/** One drawable part of an [IconDef] on the 24x24 grid (icons.js §4.1). */
sealed class IconPart {
    /** A filled shape, `[pathData, fillColor]`. */
    data class FilledPath(val d: String, val fill: String) : IconPart()
    /** An open stroke (no fill), `[pathData, null, strokeColor]` — waves/arcs. */
    data class Stroke(val d: String, val color: String) : IconPart()
    data class Circle(val cx: Double, val cy: Double, val r: Double, val fill: String) : IconPart()
}

data class IconDef(val name: String, val parts: List<IconPart>, val badge: String)

/** Named colour palette (icons.js `C`) plus the two standalone constants. */
object IconPalette {
    const val INK = "#543847"
    const val LAND = "#6cbb35"
    const val GOLD = "#fcb800"
    const val ORANGE = "#f26b1d"
    const val GREEN = "#73bf2e"
    const val RED = "#e8453c"
    const val BLUE = "#4ab8ff"
    const val VIOLET = "#8f63d6"
    const val CREAM = "#fff6d5"
    const val WHITE = "#ffffff"
    const val GREY = "#c9d1d9"
    const val PINK = "#ff8fb8"
    const val TEAL = "#2fa58f"
    const val SKY = "#8fd3ff"
    const val YELLOW = "#ffe14a"
    const val COIN = "#ffcf33"
}

/**
 * Birdy's own 36-icon vector set (icons.js §4; meta.md's "33 icons" is stale vs. the current
 * source, verified against docs/native/golden/meta-icons.json's `iconNames`). Exact path/circle
 * data, ported 1:1 (do not
 * approximate). Rendering styles (sticker/glyph/badge) are a UI concern (meta.md §4.2); this
 * object only holds the vector data + the badge-style colour assignment.
 */
object Icons {
    private val C = IconPalette
    private fun fp(d: String, fill: String) = IconPart.FilledPath(d, fill)
    private fun st(d: String, color: String) = IconPart.Stroke(d, color)
    private fun ci(cx: Double, cy: Double, r: Double, fill: String) = IconPart.Circle(cx, cy, r, fill)

    /** Formats like a JS number literal: whole values with no trailing ".0" (e.g. `4`, not
     * `4.0`), since these numbers land in SVG path strings that must match icons.js byte-for-byte. */
    private fun n(v: Double): String = if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()

    /** icons.js `rr(x,y,w,h,r)`: a rounded-rect path. Kept as a path builder, not a literal. */
    fun roundedRect(x: Double, y: Double, w: Double, h: Double, r: Double): String {
        val rs = n(r)
        return "M${n(x + r)} ${n(y)}h${n(w - 2 * r)}a$rs $rs 0 0 1 $rs ${n(r)}v${n(h - 2 * r)}a$rs $rs 0 0 1 -$rs ${n(r)}h-${n(w - 2 * r)}a$rs $rs 0 0 1 -$rs -${n(r)}v-${n(h - 2 * r)}a$rs $rs 0 0 1 $rs -${n(r)}z"
    }

    private val RAW: Map<String, List<IconPart>> = linkedMapOf(
        "sound" to listOf(
            fp("M3.5 9h4l5-4.5v15l-5-4.5h-4z", C.GOLD),
            st("M16 9a4 4 0 0 1 0 6", C.WHITE),
            st("M18.5 6.5a7.5 7.5 0 0 1 0 11", C.WHITE),
        ),
        "mute" to listOf(
            fp("M3.5 9h4l5-4.5v15l-5-4.5h-4z", C.GOLD),
            st("M16 9.5l5 5M21 9.5l-5 5", C.RED),
        ),
        "gift" to listOf(
            fp(roundedRect(4.0, 11.0, 16.0, 9.5, 1.0), C.RED),
            fp(roundedRect(3.0, 7.5, 18.0, 4.0, 1.0), C.RED),
            fp("M10.8 7.5h2.4v13h-2.4z", C.GOLD),
            fp("M12 7.5C10 3.5 5.5 3.5 6 6.5c.4 2 4 1 6 1zM12 7.5c2-4 6.5-4 6-1-.4 2-4 1-6 1z", C.GOLD),
        ),
        "trophy" to listOf(
            st("M7 6H4v1.5a3.5 3.5 0 0 0 3.5 3.5M17 6h3v1.5a3.5 3.5 0 0 1-3.5 3.5", C.GOLD),
            fp("M6.5 3.5h11v5.5a5.5 5.5 0 0 1-11 0z", C.GOLD),
            fp("M10.8 14.5h2.4v3h-2.4z", C.GOLD),
            fp(roundedRect(7.5, 17.5, 9.0, 3.5, 1.0), C.ORANGE),
        ),
        "lock" to listOf(
            st("M8 11V8a4 4 0 0 1 8 0v3", C.GREY),
            fp(roundedRect(5.5, 10.5, 13.0, 10.0, 2.0), C.GOLD),
            ci(12.0, 15.0, 1.4, C.INK),
        ),
        "bird" to listOf(
            fp("M3.5 13.5c0-4.5 3.4-7.8 7.8-7.8 3.3 0 5.4 1.8 6.5 4.3l3.2-.3-2.3 3.4c-.5 4-3.6 6.9-7.8 6.9-4.4 0-7.4-2.3-7.4-6.5z", C.GOLD),
            fp("M6.5 13.5c2.4-.2 4.6 1 5 3.3-2.6.3-4.6-1-5-3.3z", C.CREAM),
            ci(14.3, 10.2, 1.3, C.INK),
            fp("M18.5 10l3.5 1.3-3.6 1.2", C.ORANGE),
        ),
        "palette" to listOf(
            fp("M12 3a9 9 0 1 0 0 18c1.6 0 2.2-1 2.2-2.1S13 17.3 13 16.3c0-1 .9-1.6 2-1.6h2.2A3.8 3.8 0 0 0 21 11c0-4.5-4-8-9-8z", C.CREAM),
            ci(7.3, 11.5, 1.6, C.RED),
            ci(9.0, 7.2, 1.6, C.GOLD),
            ci(13.6, 6.3, 1.6, C.GREEN),
            ci(17.2, 9.2, 1.6, C.BLUE),
        ),
        "paw" to listOf(
            ci(12.0, 15.5, 4.0, C.CREAM),
            ci(6.5, 11.0, 2.0, C.CREAM),
            ci(9.5, 7.0, 2.0, C.CREAM),
            ci(14.5, 7.0, 2.0, C.CREAM),
            ci(17.5, 11.0, 2.0, C.CREAM),
        ),
        "tophat" to listOf(
            fp("M7 16.5V5.5a1 1 0 0 1 1-1h8a1 1 0 0 1 1 1v11", C.INK),
            fp("M7 12.5h10v3H7z", C.RED),
            fp("M2.5 17.5c0-1.2 4.3-2.3 9.5-2.3s9.5 1.1 9.5 2.3-4.3 2.3-9.5 2.3-9.5-1.1-9.5-2.3z", C.INK),
        ),
        "glasses" to listOf(
            st("M3.5 11.5L2 8M20.5 11.5L22 8", C.INK),
            ci(7.0, 13.0, 3.8, C.INK),
            ci(17.0, 13.0, 3.8, C.INK),
            st("M10.3 12.2c1.1-.9 2.3-.9 3.4 0", C.INK),
            ci(5.8, 11.8, 1.0, C.WHITE),
            ci(15.8, 11.8, 1.0, C.WHITE),
        ),
        "beak" to listOf(
            fp("M3 12c3-4 9-6 18-1-9 5-15 3-18 1z", C.ORANGE),
            st("M3 12c5 1 11 1 18-1", C.INK),
        ),
        "sparkle" to listOf(
            fp("M11 3l2.2 6.4L19.5 12l-6.3 2.6L11 21l-2.2-6.4L2.5 12l6.3-2.6z", C.YELLOW),
            fp("M19 2.5l.8 2.2 2.2.8-2.2.8-.8 2.2-.8-2.2-2.2-.8 2.2-.8z", C.WHITE),
        ),
        "globe" to listOf(
            ci(12.0, 12.0, 9.0, C.BLUE),
            fp(
                "M6.2 7.5c2-.8 4.3.2 4.1 2.3-.2 2-2.8 2.2-2 4.3.8 2-.8 3.3-2 2.3C4.5 14.6 4 9.5 6.2 7.5zM13.5 4.2c2.4.2 4.8 1.7 5.9 3.9-1.2 1-3.2.1-4 2.1-.8 1.9 1.3 2.8 3.2 3 .1 2.4-2 4.6-4.4 5.4.1-2.2-2-3.2-2-5.2s1.4-2.6.2-4.2c-1.1-1.4-.9-3.6 1.1-5z",
                C.LAND,
            ),
        ),
        "pipe" to listOf(
            fp("M7.5 10h9v11h-9z", C.GREEN),
            fp("M9.3 10h1.6v11H9.3z", "#b2ea6c"),
            fp(roundedRect(5.0, 4.0, 14.0, 6.0, 1.0), C.GREEN),
            fp("M7 4h1.8v6H7z", "#b2ea6c"),
        ),
        "bolt" to listOf(fp("M13.5 2L5 13.5h6.2L9.5 22 19 9.8h-6.3z", C.GOLD)),
        "dice" to listOf(
            fp(roundedRect(4.0, 4.0, 16.0, 16.0, 3.0), C.WHITE),
            ci(8.5, 8.5, 1.5, C.INK),
            ci(15.5, 8.5, 1.5, C.INK),
            ci(12.0, 12.0, 1.5, C.INK),
            ci(8.5, 15.5, 1.5, C.INK),
            ci(15.5, 15.5, 1.5, C.INK),
        ),
        "magnet" to listOf(
            fp("M4.5 4h5v8.3a2.5 2.5 0 0 0 5 0V4h5v8.3a7.5 7.5 0 0 1-15 0z", C.RED),
            fp("M4.5 4h5v3.5h-5zM14.5 4h5v3.5h-5z", C.GREY),
        ),
        "rainbow" to listOf(
            st("M2.8 18.5a9.2 9.2 0 0 1 18.4 0", C.RED),
            st("M6 18.5a6 6 0 0 1 12 0", C.GOLD),
            st("M9.2 18.5a2.8 2.8 0 0 1 5.6 0", C.BLUE),
        ),
        "mushroom" to listOf(
            fp("M8.8 12.5h6.4v5.5a2.5 2.5 0 0 1-2.5 2.5h-1.4a2.5 2.5 0 0 1-2.5-2.5z", C.CREAM),
            fp("M2.5 13a9.5 8.5 0 0 1 19 0z", C.VIOLET),
            ci(8.0, 9.3, 1.5, C.WHITE),
            ci(14.5, 7.2, 1.3, C.WHITE),
            ci(17.2, 11.0, 1.1, C.WHITE),
        ),
        "clover" to listOf(
            ci(12.0, 7.5, 3.6, C.GREEN),
            ci(7.5, 12.0, 3.6, C.GREEN),
            ci(16.5, 12.0, 3.6, C.GREEN),
            ci(12.0, 16.5, 3.6, C.GREEN),
            st("M12 13l3 8", C.GREEN),
        ),
        "hand" to listOf(
            fp(
                "M10 21.5c-2.2 0-3.4-1.2-4.4-3.2l-2-4.2c-.6-1.2.7-2.3 1.8-1.6L8 14.8V5.3a1.6 1.6 0 0 1 3.2 0v5.2V9.6a1.6 1.6 0 0 1 3.2 0v1.8-.8a1.6 1.6 0 0 1 3.2 0v1.3a1.6 1.6 0 0 1 3.2 0v5.2c0 2.6-2 4.4-4.5 4.4z",
                C.CREAM,
            ),
        ),
        "check" to listOf(st("M4.5 12.5l5 5 10-11", C.GREEN)),
        "star" to listOf(fp("M12 2.8l2.8 5.9 6.4.8-4.7 4.4 1.2 6.4L12 17.2l-5.7 3.1 1.2-6.4L2.8 9.5l6.4-.8z", C.GOLD)),
        "pause" to listOf(
            fp(roundedRect(6.0, 4.5, 4.0, 15.0, 1.2), C.WHITE),
            fp(roundedRect(14.0, 4.5, 4.0, 15.0, 1.2), C.WHITE),
        ),
        "coin" to listOf(
            ci(12.0, 12.0, 8.5, C.COIN),
            ci(12.0, 12.0, 5.2, "#f5b800"),
            st("M10.2 9.3c.8-1.2 2.8-1.4 3.6-.2", C.WHITE),
        ),
        "close" to listOf(st("M6.5 6.5l11 11M17.5 6.5l-11 11", C.RED)),
        "tree" to listOf(
            fp("M10.6 14h2.8v7h-2.8z", "#8b5a2b"),
            ci(12.0, 9.5, 6.5, C.GREEN),
        ),
        "snowflake" to listOf(
            st("M12 3v18M4.2 7.5l15.6 9M4.2 16.5l15.6-9", C.SKY),
            st("M9.5 4.5L12 6.5l2.5-2M9.5 19.5l2.5-2 2.5 2", C.SKY),
        ),
        "palm" to listOf(
            st("M12.5 21.5c0-5 .8-8.6 2.5-12", "#a8733f"),
            fp(
                "M15 9.5C12 6.5 8 6.3 5.5 8.5c3.2 0 6.4.5 9.5 1zM15 9.5c.6-3.8 3.4-6 7-5.5-2.4 1.2-4.6 3.2-7 5.5zM15 9.5c3.2-.8 6.2.3 7.5 3.3-2.8-1.2-5.2-2-7.5-3.3z",
                C.GREEN,
            ),
        ),
        "lollipop" to listOf(
            st("M12 13.5v8", C.WHITE),
            ci(12.0, 8.5, 5.8, C.PINK),
            st("M12 8.5a1.8 1.8 0 1 1 1.8 1.8 3.4 3.4 0 1 1-3.4-3.4", C.WHITE),
        ),
        "crown" to listOf(
            fp("M3.5 17.5l-1-10 5.2 4 4.3-7 4.3 7 5.2-4-1 10z", C.GOLD),
            fp(roundedRect(3.5, 17.5, 17.0, 3.0, 1.0), C.ORANGE),
        ),
        "fire" to listOf(
            fp(
                "M12 2.5c1 3.5 5.8 5.6 5.8 11.2a5.8 5.8 0 0 1-11.6 0c0-3.1 1.8-4.8 2.9-6.4.4 1.8 1.2 2.8 2.3 3.3-.6-3.1.1-5.8.6-8.1z",
                C.ORANGE,
            ),
        ),
        "flower" to listOf(
            ci(12.0, 6.8, 3.2, C.PINK),
            ci(17.0, 10.5, 3.2, C.PINK),
            ci(15.0, 16.3, 3.2, C.PINK),
            ci(9.0, 16.3, 3.2, C.PINK),
            ci(7.0, 10.5, 3.2, C.PINK),
            ci(12.0, 12.0, 2.6, C.YELLOW),
        ),
        "cactus" to listOf(
            fp("M10 21.5V6a2 2 0 0 1 4 0v15.5z", C.TEAL),
            st("M10 13H7.5a1.8 1.8 0 0 1-1.8-1.8V8.5M14 11h2.5a1.8 1.8 0 0 0 1.8-1.8V7", C.TEAL),
        ),
        "calendar" to listOf(
            fp(roundedRect(3.5, 5.5, 17.0, 15.0, 2.5), C.WHITE),
            st("M3.5 10h17", C.RED),
            st("M8 3.5v4M16 3.5v4", C.GREY),
        ),
        "play" to listOf(fp("M8.5 5.5v13l10.5-6.5z", C.GREEN)),
    )

    /** icons.js `BADGE`: one colour per icon for the badge render style. */
    private val BADGE: Map<String, String> = mapOf(
        "sound" to "#4ab8ff", "mute" to "#9aa3ad", "gift" to "#e8453c", "trophy" to "#fcb800",
        "lock" to "#9aa3ad", "bird" to "#fcb800", "palette" to "#f26b1d", "paw" to "#8f63d6",
        "tophat" to "#4ab8ff", "glasses" to "#2fa58f", "beak" to "#f26b1d", "sparkle" to "#8f63d6",
        "globe" to "#4ab8ff", "pipe" to "#73bf2e", "bolt" to "#f26b1d", "dice" to "#8f63d6",
        "magnet" to "#e8453c", "rainbow" to "#8f63d6", "mushroom" to "#8f63d6", "clover" to "#73bf2e",
        "hand" to "#f26b1d", "check" to "#73bf2e", "star" to "#fcb800", "pause" to "#4ab8ff",
        "coin" to "#fcb800", "close" to "#9aa3ad", "tree" to "#73bf2e", "snowflake" to "#4ab8ff",
        "palm" to "#2fa58f", "lollipop" to "#ff6fa8", "crown" to "#fcb800", "fire" to "#f26b1d",
        "flower" to "#ff6fa8", "cactus" to "#2fa58f", "calendar" to "#e8453c", "play" to "#73bf2e",
    )

    val NAMES: Set<String> = RAW.keys

    val ALL: List<IconDef> = RAW.map { (name, parts) -> IconDef(name, parts, BADGE.getValue(name)) }

    private val byName: Map<String, IconDef> = ALL.associateBy { it.name }

    fun find(name: String): IconDef? = byName[name]
}
