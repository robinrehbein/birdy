package de.robinrehbein.birdy.shots

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

object Png {
    fun write(argb: IntArray, width: Int, height: Int, file: File) {
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        img.setRGB(0, 0, width, height, argb, 0, width)
        ImageIO.write(img, "png", file)
    }

    /** Source-over blend of [top] (non-premultiplied ARGB) onto opaque [base]. */
    fun composite(base: IntArray, top: IntArray): IntArray = IntArray(base.size) { i ->
        val t = top[i]
        val a = (t ushr 24) and 0xFF
        if (a == 0) base[i] else {
            val b = base[i]
            fun ch(shift: Int) = (((t shr shift) and 0xFF) * a + ((b shr shift) and 0xFF) * (255 - a)) / 255
            (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
        }
    }
}
