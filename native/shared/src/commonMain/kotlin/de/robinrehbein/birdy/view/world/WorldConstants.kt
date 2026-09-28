package de.robinrehbein.birdy.view.world

import de.robinrehbein.birdy.meta.PipeColors

/** world.js module constants that only the world view needs (world.md §1). */
object WorldLook {
    const val SKY_TOP = 0x2a9bd0
    const val SKY_HORIZON = 0xa6e4ea
    const val FOG_NEAR = 70f
    const val FOG_FAR = 180f
    const val SKY_RADIUS = 300.0

    /** Default ground palette [track, stripe, edge]. */
    val SAND_ROAD: List<Int> = listOf(0xded895, 0xd2c26a, 0x9ce659)

    const val HAZE_START = 32.0
    const val HAZE_END = 40.0
    const val BRISTLE_LENGTH = 0.22

    const val SCENERY_CHUNK = 25.0
    const val SCENERY_SPAN = 225.0

    /** Classic pipe colours (catalog "green"), before setPipeStyle. */
    val PIPE_COLORS = PipeColors(pipe = 0x73bf2e, light = 0xb2ea6c, dark = 0x4f8a1f)
}

/** Spiky cactus obstacle palette and body shape (world.js:744-746). */
internal object CactusLook {
    const val GREEN = 0x2fa58f
    const val SPIKE = 0xfff3d6
    const val EYE = 0xffffff
    const val PUPIL = 0x222222
    const val BROW = 0x1c5a4c
    const val MOUTH = 0x3a2030
    const val TOOTH = 0xffffff
    const val PETAL = 0xff5a8a
    const val POLLEN = 0xffd84a
    const val OUTLINE = 0x3a2433

    const val R_X = 0.74f
    const val R_Y = 0.8f
    const val R_Z = 0.74f
    const val PLANT_HEIGHT = 1.8f
    const val PLANT_WIDTH = 1.2f
    const val Y = PLANT_HEIGHT - 0.95f
}
