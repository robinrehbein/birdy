package de.robinrehbein.birdy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * `.stats`: three equal inset cards (8px gap, radius 10, padding 8/4), each a small orange
 * label over a big white outlined number (`strong` 32px, `0 2px / 2px` outline).
 */
@Composable
fun StatsGrid(items: List<Pair<String, String>>, modifier: Modifier = Modifier, valueSize: TextUnit = 32.sp) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((label, value) in items) {
            Column(
                Modifier
                    .weight(1f)
                    .background(BirdyColors.PanelInset, RoundedCornerShape(10.dp))
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BasicText(label, style = TextStyle(fontFamily = displayFont(), fontSize = 14.sp, color = BirdyColors.Orange))
                OutlinedText(value, size = valueSize, thickness = 2.dp, down = 2.dp)
            }
        }
    }
}
