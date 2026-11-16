package dev.cinema.crunchyroll.playback

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import dev.cinema.crunchyroll.data.Upscaler
import dev.cinema.crunchyroll.design.*
import dev.cinema.crunchyroll.ui.Action
import kotlin.math.roundToInt

@Composable
fun UpscalerDialog(state: PlaybackState,target: VideoDimensions,onSelect: (Upscaler)->Unit,
                   onSharpness: (Int,Boolean)->Unit,onDismiss: ()->Unit) {
    var sharpness by remember(state.fsrSharpness) {mutableIntStateOf(state.fsrSharpness)}
    PlayerDialog(onDismissRequest=onDismiss) {
        Column(Modifier.widthIn(max=480.dp).heightIn(max=600.dp).clip(RoundedCornerShape(24.dp))
            .background(Surface).verticalScroll(rememberScrollState()).padding(24.dp).testTag("upscaler-dialog"),
            verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Text("Video upscaler",fontSize=24.sp,fontWeight=FontWeight.Bold)
            Text("Output: $target · Fits your playback display and keeps the video’s proportions.",color=Muted,fontSize=13.sp)
            Upscaler.entries.forEach {option ->
                Action(option.label,glyph=if(state.upscaler==option) Glyph.Check else null,
                    selected=state.upscaler==option,modifier=Modifier.fillMaxWidth().testTag("upscaler-${option.key}")) {onSelect(option)}
            }
            if(state.upscaler==Upscaler.Off) Text("Use the player’s standard scaling.",color=Muted,fontSize=13.sp)
            if(state.upscaler==Upscaler.Fsr1) {
                Text("AMD FidelityFX Super Resolution 1",fontWeight=FontWeight.Bold,fontSize=15.sp)
                Text("Reconstructs video edges, then applies adjustable sharpening. Stream quality stays separate.",color=Muted,fontSize=13.sp)
                if(state.upscaling.input.valid) Text("${state.upscaling.input} → ${state.upscaling.output}",color=Orange,fontSize=14.sp)
                state.upscaling.notice?.let {Text(it,color=Muted,fontSize=13.sp)}
                Text("Sharpness: $sharpness%",fontWeight=FontWeight.Bold,modifier=Modifier.testTag("fsr-sharpness-value"))
                Slider(value=sharpness.toFloat(),onValueChange={sharpness=(it/5).roundToInt()*5;onSharpness(sharpness,false)},
                    onValueChangeFinished={onSharpness(sharpness,true)},valueRange=0f..100f,steps=19,
                    modifier=Modifier.testTag("fsr-sharpness"),
                    colors=SliderDefaults.colors(thumbColor=Orange,activeTrackColor=Orange,inactiveTrackColor=Divider))
                Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    Action("−5%",modifier=Modifier.weight(1f)){sharpness=(sharpness-5).coerceAtLeast(0);onSharpness(sharpness,true)}
                    Action("+5%",modifier=Modifier.weight(1f)){sharpness=(sharpness+5).coerceAtMost(100);onSharpness(sharpness,true)}
                }
                Text("0% disables sharpening and keeps upscaling. 100% gives the strongest sharpening. FSR runs only when the video is smaller than the output.",color=Muted,fontSize=12.sp)
            }
            Action("Done",modifier=Modifier.fillMaxWidth(),onClick=onDismiss)
        }
    }
}
