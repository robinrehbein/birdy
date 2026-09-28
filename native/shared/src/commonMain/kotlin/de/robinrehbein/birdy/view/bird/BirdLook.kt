package de.robinrehbein.birdy.view.bird

import de.robinrehbein.birdy.meta.Kind

/** Workshop selection, one id per category (bird.js `look`). Defaults are the free items. */
data class BirdLook(
    val pattern: String = "plain",
    val hat: String = "none",
    val eyes: String = "normal",
    val beak: String = "round",
) {
    /** The id for a workshop [kind] (pattern/hat/eyes/beak), null for other kinds. */
    fun idOf(kind: Kind): String? = when (kind) {
        Kind.Pattern -> pattern
        Kind.Hat -> hat
        Kind.Eyes -> eyes
        Kind.Beak -> beak
        else -> null
    }

    /** Copy with [kind] set to [id]; other kinds are ignored. */
    fun with(kind: Kind, id: String): BirdLook = when (kind) {
        Kind.Pattern -> copy(pattern = id)
        Kind.Hat -> copy(hat = id)
        Kind.Eyes -> copy(eyes = id)
        Kind.Beak -> copy(beak = id)
        else -> this
    }

    companion object {
        val WORKSHOP_KINDS = listOf(Kind.Pattern, Kind.Hat, Kind.Eyes, Kind.Beak)
    }
}
