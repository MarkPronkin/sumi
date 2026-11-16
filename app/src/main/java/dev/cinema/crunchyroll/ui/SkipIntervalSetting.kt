package dev.cinema.crunchyroll.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.cinema.crunchyroll.data.SkipInterval
import dev.cinema.crunchyroll.design.LocalTv
import dev.cinema.crunchyroll.design.Muted

@Composable
fun SkipIntervalSetting(seconds: Int, onSelect: (Int) -> Unit) {
    Text("Skip interval", color=Muted, fontSize=13.sp)
    Text(if(LocalTv.current) "Used by rewind, forward and media controls."
        else "Double-tap the left or right side of the video to skip. Also used by rewind, forward and media controls.",
        color=Muted, fontSize=12.sp)
    FlowRow(horizontalArrangement=Arrangement.spacedBy(10.dp), verticalArrangement=Arrangement.spacedBy(10.dp)) {
        SkipInterval.choices.forEach { value ->
            ChoiceChip("$value seconds", selected=SkipInterval.seconds(seconds)==value,
                modifier=Modifier.testTag("skip-interval-$value")) { onSelect(value) }
        }
    }
}
