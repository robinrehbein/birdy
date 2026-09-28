package de.robinrehbein.birdy.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import de.robinrehbein.birdy.ui.res.Res
import de.robinrehbein.birdy.ui.res.fredoka_semibold
import de.robinrehbein.birdy.ui.res.lilita_one
import org.jetbrains.compose.resources.Font

/** style.css palette (main-b.md §2). */
object BirdyColors {
    val App = Color(0xFF4EC0CA)
    val Ink = Color(0xFF543847)
    val Panel = Color(0xFFDED895)
    val PanelInset = Color(0xFFCBB968)
    val Gold = Color(0xFFFCB800)
    val Green = Color(0xFF73BF2E)
    val Orange = Color(0xFFF26B1D)
    val White = Color(0xFFFFFFFF)
    val Popup = Color(0xFFFFF176)
    val Purple = Color(0xFF7A6FC9)
    val RareTag = Color(0xFF8F63D6)
    val RealMoney = Color(0xFF4A9ACB)
    val MissionDone = Color(0xFFB5D98A)
    val Scrim = Color(0x4D2B1E2E)
}

/** Display font (Lilita One 400): headings, score, buttons. */
@Composable
fun displayFont(): FontFamily = FontFamily(Font(Res.font.lilita_one))

/** Body font (Fredoka 600): descriptions and small print. */
@Composable
fun bodyFont(): FontFamily = FontFamily(Font(Res.font.fredoka_semibold))
