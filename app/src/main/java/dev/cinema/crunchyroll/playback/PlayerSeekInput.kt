package dev.cinema.crunchyroll.playback

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import dev.cinema.crunchyroll.data.SkipInterval
import dev.cinema.crunchyroll.design.*
import kotlinx.coroutines.delay

private data class SeekFeedback(val direction: Int, val request: Int)

/** Sibling below player controls: buttons and the slider win touch hit testing. */
@Composable
internal fun PlayerSeekGestureLayer(
    intervalSeconds: Int,
    seekEnabled: Boolean,
    controlsVisible: Boolean,
    onToggleControls: () -> Unit,
    onSeek: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val seconds = SkipInterval.seconds(intervalSeconds)
    val toggle by rememberUpdatedState(onToggleControls)
    val seek by rememberUpdatedState(onSeek)
    val enabled by rememberUpdatedState(seekEnabled)
    var feedback by remember { mutableStateOf<SeekFeedback?>(null) }
    var requests by remember { mutableIntStateOf(0) }
    fun skip(direction: Int) {
        if(enabled) {
            seek(direction)
            feedback = SeekFeedback(direction, ++requests)
        }
    }
    LaunchedEffect(feedback) { if(feedback!=null) { delay(850); feedback=null } }

    Row(modifier.fillMaxSize().focusProperties {canFocus=false}.testTag("player-gesture-layer")
        .semantics {
            contentDescription = "Playback controls"
            onClick(if(controlsVisible) "Hide playback controls" else "Show playback controls") { toggle(); true }
            customActions = if(seekEnabled) listOf(
                CustomAccessibilityAction("Rewind $seconds seconds") {skip(-1); true},
                CustomAccessibilityAction("Forward $seconds seconds") {skip(1); true},
            ) else emptyList()
        }, horizontalArrangement=Arrangement.Absolute.Left) {
        // Separate hit regions require both taps to land on the same side. The middle
        // 30 percent only toggles controls and never recognizes a seek gesture.
        for(direction in listOf(-1, 0, 1)) {
            Box(Modifier.weight(if(direction==0) .30f else .35f).fillMaxHeight()
                .pointerInput(direction) {
                    detectTapGestures(onTap={toggle()},
                        onDoubleTap=if(direction==0) null else { _ -> if(enabled) skip(direction) else toggle() })
                }, contentAlignment=Alignment.Center) {
                if(feedback?.direction==direction) {
                    Column(Modifier.background(Surface.copy(alpha=.9f), RoundedCornerShape(16.dp)).padding(16.dp),
                        horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        CinemaIcon(if(direction<0) Glyph.Rewind else Glyph.Forward, Orange, Modifier.size(28.dp))
                        Text(if(direction<0) "−$seconds s" else "+$seconds s", color=Paper)
                    }
                }
            }
        }
    }
}
