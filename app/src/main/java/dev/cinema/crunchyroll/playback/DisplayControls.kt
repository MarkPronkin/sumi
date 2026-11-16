@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package dev.cinema.crunchyroll.playback

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import dev.cinema.crunchyroll.R
import dev.cinema.crunchyroll.design.*
import dev.cinema.crunchyroll.ui.CinemaViewModel
import dev.cinema.crunchyroll.ui.Action

@Composable
fun VideoOutputView(player: Player,modifier: Modifier=Modifier,nativeOutput: VideoDimensions?=null) {
    key(nativeOutput!=null) {
        AndroidView(factory={context ->
            (LayoutInflater.from(context).inflate(if(nativeOutput!=null) R.layout.cinema_player_surface else R.layout.cinema_player,null) as PlayerView).apply {this.player=player}
        },update={view ->
            view.player=player
            // Use the configured GPU frame dimensions, which are available even if a surface
            // replacement temporarily resets Player.videoSize to UNKNOWN.
            if(nativeOutput!=null) (view.videoSurfaceView as? SurfaceView)?.holder?.let {holder ->
                if(holder.surfaceFrame.width()!=nativeOutput.width || holder.surfaceFrame.height()!=nativeOutput.height)
                    holder.setFixedSize(nativeOutput.width,nativeOutput.height)
            }
        },modifier=modifier,onRelease={it.player=null})
    }
}

@Composable
private fun DisplaySummary(display: PlaybackDisplay,state: PlaybackState) {
    Text("${display.name} · ${display.width} × ${display.height}",color=Muted,fontSize=13.sp)
    Text("${state.outputSurface} · Software Widevine",color=Orange,fontSize=13.sp)
    Text("Secure display: ${if(display.secure) "yes" else "no"} · Protected buffers: ${if(display.protectedBuffers) "yes" else "no"}",color=Muted,fontSize=12.sp)
    if(state.drmSecurityLevel.isNotBlank()) Text("Active DRM: ${state.drmSecurityLevel} · License keys: ${if(state.drmKeysLoaded) "loaded" else "pending"}",color=Muted,fontSize=12.sp)
    Text("Upscaler: ${state.upscaler.label}"+if(state.upscaler==dev.cinema.crunchyroll.data.Upscaler.Fsr1) " · Sharpness: ${state.fsrSharpness}%" else "",color=Orange,fontSize=13.sp)
    if(state.upscaling.input.valid) Text("${state.upscaling.input} → ${state.upscaling.output}",color=Muted,fontSize=13.sp)
    state.upscaling.notice?.let {Text(it,color=Muted,fontSize=12.sp)}
}

@Composable
private fun CopyDisplayReport(display: PlaybackDisplay,state: PlaybackState) {
    val context=LocalContext.current
    var copied by remember {mutableStateOf(false)}
    Action(if(copied) "Report copied" else "Copy display report",Glyph.Info) {
        val clipboard=context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Sumi display report",displayDiagnostics(display,state)))
        copied=true
    }
}

@Composable
fun DisplayInfoDialog(display: PlaybackDisplay,state: PlaybackState,onDismiss: ()->Unit) {
    PlayerDialog(onDismissRequest=onDismiss) {
        Column(Modifier.widthIn(max=560.dp).heightIn(max=600.dp).clip(RoundedCornerShape(24.dp)).background(Surface)
            .verticalScroll(rememberScrollState()).padding(24.dp).testTag("display-info"),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text("Playback display",fontSize=24.sp,fontWeight=FontWeight.Bold)
            DisplaySummary(display,state)
            state.errorCode.takeIf {it.isNotBlank()}?.let {Text(it,color=Orange,fontSize=12.sp)}
            CopyDisplayReport(display,state)
            Action("Done",modifier=Modifier.fillMaxWidth(),onClick=onDismiss)
        }
    }
}

@Composable
fun DisplayCheckScreen(vm: CinemaViewModel,engine: PlaybackEngine=viewModel(key="display-check")) {
    val display=currentPlaybackDisplay()
    val state by engine.state.collectAsStateWithLifecycle()
    val lifecycle=LocalLifecycleOwner.current
    var test by rememberSaveable {mutableStateOf("")}
    LaunchedEffect(test) {
        if(test.isBlank()) engine.clearTestVideo() else engine.openDisplayTest(test=="widevine")
    }
    DisposableEffect(engine,lifecycle) {
        val observer=LifecycleEventObserver {_,event ->when(event) {
            Lifecycle.Event.ON_STOP ->engine.background()
            Lifecycle.Event.ON_START ->engine.foreground()
            else ->Unit
        }}
        lifecycle.lifecycle.addObserver(observer)
        onDispose {lifecycle.lifecycle.removeObserver(observer);engine.close()}
    }
    BackHandler {vm.navigate("Settings")}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp).testTag("display-check"),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Action("Back to settings",Glyph.Back){vm.navigate("Settings")}
        Text("Check this display",fontFamily=DisplayFont,fontSize=36.sp,fontWeight=FontWeight.Bold)
        Text("Run this inside Unbound, then compare ordinary screen mirroring. The clips use public test media; your account history stays separate.",color=Muted,fontSize=14.sp,lineHeight=22.sp)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Action("Play clear test",Glyph.Play,selected=test=="clear"){if(test=="clear") engine.openDisplayTest(false) else test="clear"}
            Action("Play Widevine test",Glyph.Play,selected=test=="widevine"){if(test=="widevine") engine.openDisplayTest(true) else test="widevine"}
            if(test.isNotBlank()) Action("Stop",Glyph.Pause){test=""}
        }
        Box(Modifier.widthIn(max=800.dp).fillMaxWidth().aspectRatio(16f/9).background(Oled).testTag("display-test-video"),contentAlignment=androidx.compose.ui.Alignment.Center) {
            VideoOutputView(engine.player,Modifier.fillMaxSize())
            if(test.isBlank()) Text("Choose a test clip above",color=Muted,fontSize=14.sp)
        }
        Text(when {test.isBlank()->"Choose a test clip."
            state.error!=null->state.error.orEmpty()
            state.renderedVideo->"Player has rendered video. Check whether the image is visible in your glasses."
            else->"Preparing test video…"},color=if(state.error!=null) Orange else Paper,fontSize=14.sp)
        DisplaySummary(display,state)
        Text("If clear video is visible but Widevine is gray, the protected playback path needs investigation. A working public Widevine clip does not guarantee an account episode permits the same output.",color=Muted,fontSize=13.sp,lineHeight=20.sp)
        CopyDisplayReport(display,state)
        Text("Clear clip: Big Buck Bunny, Blender Foundation. Widevine clip: Tears of Steel, Blender Foundation; public Media3 demo license.",color=Muted,fontSize=11.sp)
    }
}
