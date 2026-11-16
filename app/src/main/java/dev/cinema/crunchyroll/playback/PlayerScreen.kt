@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package dev.cinema.crunchyroll.playback

import android.os.SystemClock
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.C
import dev.cinema.crunchyroll.data.*
import dev.cinema.crunchyroll.design.*
import dev.cinema.crunchyroll.ui.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import dev.cinema.crunchyroll.data.account.accountMessage

@Composable
fun PlayerScreen(episode: Episode,vm: CinemaViewModel,profileId: String,preferences: ProfileData,onBack: ()->Unit,
                 engine: PlaybackEngine = viewModel()) {
    val state by engine.state.collectAsStateWithLifecycle()
    val activeEpisode by vm.playerEpisode.collectAsStateWithLifecycle()
    val connected=remember(episode.id) {vm.isConnected}
    val nativeService=remember(episode.id) {if(connected) vm.nativePlaybackService() else null}
    val activity=LocalActivity.current
    val show=vm.catalogue.show(episode.showId) ?: return
    val tv=LocalTv.current
    val motion=LocalMotion.current
    val display=currentPlaybackDisplay()
    val lifecycle=LocalLifecycleOwner.current
    var controls by remember {mutableStateOf(true)}
    var interaction by remember {mutableIntStateOf(0)}
    var tracks by remember {mutableStateOf(false)}
    var quality by remember {mutableStateOf(false)}
    var upscaler by remember {mutableStateOf(false)}
    var viewport by remember {mutableStateOf(VideoDimensions())}
    val outputTarget=playbackOutputTarget(viewport,display)
    val nativeTvOutput=tv && state.upscaler==Upscaler.Fsr1 && outputTarget.valid
    var displayInfo by remember {mutableStateOf(false)}
    var trackConfirmation by remember {mutableStateOf<String?>(null)}
    var cancelledNext by rememberSaveable(episode.id) {mutableStateOf(false)}
    var countdown by remember {mutableIntStateOf(8)}
    var drag by remember(profileId,episode.id) {mutableStateOf<Float?>(null)}
    val scrubHaptics=rememberCinemaHaptics(preferences.haptics)
    val scrubTicks=remember(profileId,episode.id) {TimelineHapticGate()}
    val scrubInteractions=remember(profileId,episode.id) {MutableInteractionSource()}
    LaunchedEffect(scrubInteractions) {
        scrubInteractions.interactions.collect {interaction ->
            if(interaction is DragInteraction.Cancel) {drag=null;scrubTicks.reset()}
        }
    }
    var resumeError by remember(episode.id) {mutableStateOf<String?>(null)}
    var resumeRetry by remember(episode.id) {mutableIntStateOf(0)}
    var forceBeginning by remember(episode.id) {mutableStateOf(false)}
    val focus=remember {FocusRequester()}
    var next by remember(profileId,episode.id) {mutableStateOf(vm.catalogue.next(episode))}
    var nextLoading by remember(profileId,episode.id) {mutableStateOf(connected)}
    var nextError by remember(profileId,episode.id) {mutableStateOf(false)}
    var nextRetry by remember(profileId,episode.id) {mutableIntStateOf(0)}
    val skippable=if(state.ready && !state.ended && state.error==null && resumeError==null)
        state.skipSegments.filter {it.contains(state.position,state.duration)} else emptyList()
    val skipSeconds=SkipInterval.seconds(preferences.skipIntervalSeconds)
    val skipMillis=SkipInterval.millis(skipSeconds)
    // Keep repeated seeks available while a known timeline is buffering after a seek.
    val canSeek=state.duration>0 && state.error==null && resumeError==null
    fun interact() {controls=true;interaction++}
    fun seekByInterval(direction: Int) {if(canSeek) {engine.skip(direction*skipMillis);interaction++}}
    fun exit() {engine.close();onBack()}
    fun playNext(upcoming: Episode) {
        if(vm.playerEpisode.value==episode.id) {engine.persist();vm.play(upcoming.id)}
    }
    LaunchedEffect(engine,skipSeconds) {engine.configureSkipInterval(skipSeconds)}
    LaunchedEffect(profileId,episode.id,preferences.audio,nextRetry) {
        nextLoading=true;nextError=false
        try {next=vm.nextEpisode(episode,preferences.audio)}
        catch(e: CancellationException) {throw e}
        catch(_: Exception) {nextError=true}
        finally {nextLoading=false}
    }
    LaunchedEffect(preferences.upscaler,preferences.fsrSharpness,outputTarget) {
        if(outputTarget.valid) engine.configureUpscaling(preferences.upscaler,preferences.fsrSharpness,outputTarget)
    }
    LaunchedEffect(nativeTvOutput) {engine.reportOutputSurface(nativeTvOutput)}
    LaunchedEffect(profileId,episode.id,resumeRetry) {
        resumeError=null
        var stage="resume_lookup"
        try {
        val position=if(forceBeginning || engine.ownsEpisode(profileId,episode.id)) 0 else vm.playbackPosition(episode,profileId)
        stage="player_initialization"
        withContext(Dispatchers.Main.immediate) {
        engine.open(profileId,episode.id,position,preferences,vm::saveProgress,
            nativeService,"${show.title} · Episode ${episode.number}",{p,id,pos ->vm.markProgressSynced(p,id,pos);Unit})
        controls=true;cancelledNext=false;interact()
        if(tv) runCatching {focus.requestFocus()}
        }
        } catch(e: CancellationException) {throw e} catch(e: Exception) {
            android.util.Log.w("CinemaPreparation",stage+": "+e.javaClass.simpleName+" at "+e.stackTrace.take(3).joinToString {it.className+":"+it.lineNumber})
            resumeError=e.accountMessage()
        }
    }
    DisposableEffect(lifecycle,engine) {
        val observer=LifecycleEventObserver {_,event -> when(event) {
            Lifecycle.Event.ON_STOP -> engine.background()
            Lifecycle.Event.ON_START -> engine.foreground()
            else -> Unit
        } }
        lifecycle.lifecycle.addObserver(observer)
        onDispose {lifecycle.lifecycle.removeObserver(observer);engine.persist();if(activity?.isChangingConfigurations!=true) engine.closeIfEpisode(episode.id)}
    }
    BackHandler {
        if(displayInfo) displayInfo=false else if(upscaler) upscaler=false else if(quality) quality=false else if(tracks) tracks=false else if(controls && tv) controls=false else exit()
    }
    LaunchedEffect(interaction,state.playing,tracks,quality,upscaler,displayInfo,controls) {
        if(controls && state.playing && !tracks && !quality && !upscaler && !displayInfo) {delay(5000);controls=false}
    }
    LaunchedEffect(episode.id,state.ended,cancelledNext,preferences.autoplay,next?.id) {
        val upcoming=next
        if(state.ended && !cancelledNext && preferences.autoplay && upcoming!=null) {
            countdown=8
            while(countdown>0) {delay(1000);countdown--}
            playNext(upcoming)
        }
    }
    LaunchedEffect(trackConfirmation) {if(trackConfirmation!=null) {delay(2200);trackConfirmation=null}}
    Box(Modifier.fillMaxSize().background(Oled).focusRequester(focus).onPreviewKeyEvent { event ->
        if(event.type!=KeyEventType.KeyDown) false else {
            when(event.nativeKeyEvent.keyCode) {
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {engine.toggle();interact();true}
                KeyEvent.KEYCODE_MEDIA_PLAY -> {engine.player.play();interact();true}
                KeyEvent.KEYCODE_MEDIA_PAUSE -> {engine.player.pause();interact();true}
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {seekByInterval(1);interact();true}
                KeyEvent.KEYCODE_MEDIA_REWIND -> {seekByInterval(-1);interact();true}
                // Let a focused skip button receive OK even after the main controls fade.
                KeyEvent.KEYCODE_DPAD_CENTER,KeyEvent.KEYCODE_ENTER -> if(!controls && skippable.isEmpty()) {interact();true} else {interact();false}
                KeyEvent.KEYCODE_DPAD_UP,KeyEvent.KEYCODE_DPAD_DOWN,KeyEvent.KEYCODE_DPAD_LEFT,KeyEvent.KEYCODE_DPAD_RIGHT -> {interact();false}
                else -> false
            }
        }
    }.focusable()) {
        VideoOutputView(engine.player,Modifier.fillMaxSize().onSizeChanged {viewport=VideoDimensions(it.width,it.height)},
            nativeOutput=videoSurfaceDimensions(state.upscaling.output,outputTarget).takeIf {nativeTvOutput})
        if(!state.renderedVideo) {
            Art(show,Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Oled.copy(alpha=.5f)))
        }
        // A separate overlay handles input without applying transforms to the video surface.
        if(tv) Box(Modifier.fillMaxSize().focusProperties {canFocus=false}.clickable {controls=!controls;interaction++})
        else PlayerSeekGestureLayer(skipSeconds,canSeek,controls,
            onToggleControls={controls=!controls;interaction++},onSeek={direction ->seekByInterval(direction)})
        PlaybackStartFeedback("$profileId:${episode.id}",episode.id,engine.player,
            enabled=activeEpisode==episode.id && state.error==null && resumeError==null && !state.ended,
            modifier=Modifier.align(Alignment.Center))
        AnimatedVisibility(controls,enter=fadeIn(tween(motion.millis(250)))+slideInVertically{if(motion==MotionLevel.Maximal) -32 else 0},
            exit=fadeOut(tween(180)),modifier=Modifier.align(Alignment.TopCenter)) {
            Row(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Oled.copy(alpha=.95f),Oled.copy(alpha=0f))))
                .systemBarsPadding().padding(20.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                IconAction("Exit playback",Glyph.Back,onClick=::exit)
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    Text("${show.title} · ${if(connected) "Episode" else "Demo chapter"} ${episode.number}",fontWeight=FontWeight.Bold,fontSize=if(tv) 20.sp else 16.sp)
                    Text(if(connected) "${episode.title}  ${state.currentHeight.takeIf {it>0}?.let {"· ${it}p"}.orEmpty()}" else "NOW PLAYING: BIG BUCK BUNNY · SAMPLE VIDEO",color=Orange,fontSize=10.sp,letterSpacing=1.sp)
                }
            }
        }
        AnimatedVisibility(controls || skippable.isNotEmpty(),enter=fadeIn()+slideInVertically{if(motion==MotionLevel.Maximal) 40 else 0},exit=fadeOut()+slideOutVertically{it/4},
            modifier=Modifier.align(Alignment.BottomCenter)) {
            Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Oled.copy(alpha=0f),Oled.copy(alpha=.95f))))
                .systemBarsPadding().padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                if(skippable.isNotEmpty()) FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp,Alignment.End),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    skippable.forEach {segment ->
                        Action(segment.kind.label,Glyph.Forward,filled=true,modifier=Modifier.testTag("skip-${segment.kind.name.lowercase()}")) {
                            engine.skipSegment(segment);interact()
                        }
                    }
                }
                AnimatedVisibility(controls) {
                    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        if(state.progressError) Text("Progress is saved locally. Account sync will retry.",color=Orange,fontSize=12.sp)
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                            Text(formatTime(drag?.toLong() ?: state.position),fontSize=12.sp)
                            Text(formatTime(state.duration),color=Muted,fontSize=12.sp)
                        }
                        Slider(value=drag ?: state.position.toFloat(),onValueChange={
                            if(drag==null) scrubTicks.begin(state.position)
                            if(scrubTicks.update(it.toLong(),SystemClock.elapsedRealtime(),scrubHaptics.enabled))
                                scrubHaptics.perform(CinemaHaptic.TimelineTick)
                            drag=it;interact()
                        },
                            valueRange=0f..state.duration.toFloat().coerceAtLeast(1f),enabled=state.ready,
                            onValueChangeFinished={drag?.let {engine.seek(it.toLong())};drag=null;scrubTicks.reset();interact()},
                            interactionSource=scrubInteractions,
                            colors=SliderDefaults.colors(thumbColor=Orange,activeTrackColor=Orange,inactiveTrackColor=Divider))
                        FlowRow(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                            Action("−$skipSeconds s",Glyph.Rewind,enabled=canSeek,accessibilityLabel="Rewind $skipSeconds seconds",
                                modifier=Modifier.testTag("player-rewind")){seekByInterval(-1);interact()}
                            Action(if(state.playing) "Pause" else "Play",if(state.playing) Glyph.Pause else Glyph.Play,filled=true){engine.toggle();interact()}
                            Action("+$skipSeconds s",Glyph.Forward,enabled=canSeek,accessibilityLabel="Forward $skipSeconds seconds",
                                modifier=Modifier.testTag("player-forward")){seekByInterval(1);interact()}
                            next?.let {upcoming -> Action("Next chapter",Glyph.Chevron,modifier=Modifier.testTag("next-chapter")){playNext(upcoming)}}
                            if(next==null && nextLoading) Action("Next chapter…",Glyph.Chevron,enabled=false) {}
                            if(next==null && nextError && !nextLoading) Action("Retry next chapter",Glyph.Refresh){nextRetry++;interact()}
                            if(state.subtitleChoices.isNotEmpty() || state.tracks.any {it.type==C.TRACK_TYPE_AUDIO || it.type==C.TRACK_TYPE_TEXT}) Action("Tracks",Glyph.Tracks){tracks=true;interact()}
                            if(state.qualityHeights.isNotEmpty()) Action("Quality: ${if(state.selectedHeight==0) "Auto" else "${state.selectedHeight}p"}",Glyph.Settings){quality=true;interact()}
                            Action("Upscaler: ${state.upscaler.label}",Glyph.Settings,modifier=Modifier.testTag("upscaler-picker")){upscaler=true;interact()}
                            Action("Display info",Glyph.Info){displayInfo=true;interact()}
                        }
                    }
                }
            }
        }
        if(state.buffering && state.error==null && resumeError==null) Column(Modifier.align(Alignment.Center),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)) {
            CircularProgressIndicator(color=Orange);Text(if(connected) "Preparing your episode…" else "Loading sample video…",color=Paper)
        }
        state.error?.let {message -> Box(Modifier.fillMaxSize().background(Oled.copy(alpha=.85f)),contentAlignment=Alignment.Center) {
            Column(Modifier.widthIn(max=480.dp).padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)) {
                Text(message,fontSize=18.sp);Action(if(connected) "Retry episode" else "Retry sample playback",Glyph.Refresh,true){engine.retry();interact()};Action("Back to anime",Glyph.Back,onClick=::exit)
                Action("Display details",Glyph.Info){displayInfo=true;interact()}
                Action("Upscaler settings",Glyph.Settings){upscaler=true;interact()}
            }
        } }
        resumeError?.let {message ->Box(Modifier.fillMaxSize().background(Oled.copy(alpha=.9f)),contentAlignment=Alignment.Center) {
            Column(Modifier.widthIn(max=480.dp).padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                Text("Couldn’t load your resume position",fontSize=20.sp,fontWeight=FontWeight.Bold)
                Text(message,color=Muted)
                Action("Retry resume",Glyph.Refresh,true){resumeRetry++}
                Action("Play from beginning",Glyph.Play){forceBeginning=true;resumeRetry++}
                Action("Back",Glyph.Back,onClick=::exit)
            }
        }}
        AnimatedVisibility(trackConfirmation!=null,modifier=Modifier.align(Alignment.Center),enter=scaleIn()+fadeIn(),exit=scaleOut()+fadeOut()) {
            Text(trackConfirmation.orEmpty(),Modifier.clip(RoundedCornerShape(12.dp)).background(Surface).padding(20.dp),color=Orange)
        }
        val upcoming=next
        if(state.ended && upcoming!=null && preferences.autoplay && !cancelledNext) {
            Column(Modifier.align(Alignment.Center).clip(RoundedCornerShape(20.dp)).background(Surface).padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                Text("Next ${if(connected) "episode" else "demo chapter"} in $countdown",fontSize=20.sp,fontWeight=FontWeight.Bold)
                LinearProgressIndicator({countdown/8f},Modifier.fillMaxWidth(.5f),color=Orange)
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Action("Play next",Glyph.Play,true){playNext(upcoming)}
                    Action("Cancel"){cancelledNext=true;interact()}
                }
            }
        }
    }
    if(tracks) PlayerDialog(onDismissRequest={tracks=false;interact()}) {
        Reveal {
            LazyColumn(Modifier.widthIn(max=480.dp).heightIn(max=500.dp).clip(RoundedCornerShape(24.dp)).background(Surface).padding(24.dp).testTag("tracks-dialog"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                item {Text("Available tracks",fontSize=24.sp,fontWeight=FontWeight.Bold)}
                for((type,label) in listOf(C.TRACK_TYPE_AUDIO to "Audio",C.TRACK_TYPE_TEXT to "Subtitles")) {
                    val available=state.tracks.filter {it.type==type && (type!=C.TRACK_TYPE_TEXT || !connected)}
                    if(available.isNotEmpty() && (type!=C.TRACK_TYPE_VIDEO || available.size>1)) {
                        item {Text(label,color=Muted,fontSize=14.sp)}
                        items(available) { option -> Action(option.label,glyph=if(option.selected) Glyph.Check else null,selected=option.selected,modifier=Modifier.fillMaxWidth()) {
                            engine.select(option);trackConfirmation="${option.label} selected";tracks=false;interact()
                        } }
                        if(type==C.TRACK_TYPE_TEXT) item {Action("Subtitles off",modifier=Modifier.fillMaxWidth()){engine.subtitlesOff();tracks=false;trackConfirmation="Subtitles off";interact()}}
                    }
                }
                if(connected && state.subtitleChoices.isNotEmpty()) {
                    item {Text("Subtitles",color=Muted,fontSize=14.sp)}
                    item {Action("Off",selected=state.selectedSubtitle=="off",modifier=Modifier.fillMaxWidth()) {
                        engine.selectSubtitle("off");vm.preferences {it.copy(subtitles="off")};tracks=false;trackConfirmation="Subtitles off";interact()
                    }}
                    items(state.subtitleChoices,key={it.language}) {choice ->
                        val label=LanguagePreferences.label(choice.language)+if(choice.styled) " · Styled" else ""
                        Action(label,selected=state.selectedSubtitle==choice.language,modifier=Modifier.fillMaxWidth()) {
                            engine.selectSubtitle(choice.language);vm.preferences {it.copy(subtitles=choice.language)};tracks=false;trackConfirmation=label;interact()
                        }
                    }
                }
                item {Action("Done",modifier=Modifier.fillMaxWidth()){tracks=false;interact()}}
            }
        }
    }
    if(quality) PlayerDialog(onDismissRequest={quality=false;interact()}) {
        Reveal {LazyColumn(Modifier.widthIn(max=440.dp).heightIn(max=520.dp).clip(RoundedCornerShape(24.dp)).background(Surface).padding(24.dp).testTag("quality-dialog"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {Text("Video quality",fontSize=24.sp,fontWeight=FontWeight.Bold)}
            item {Text("Available on this stream and device. Auto adapts to your connection.",color=Muted,fontSize=13.sp)}
            item {Action("Auto",glyph=if(state.selectedHeight==0) Glyph.Check else null,selected=state.selectedHeight==0,modifier=Modifier.fillMaxWidth()) {
                engine.selectQuality(0);vm.preferences {it.copy(qualityHeight=0)};quality=false;trackConfirmation="Quality: Auto";interact()
            }}
            items(state.qualityHeights) {height ->Action("${height}p",glyph=if(state.selectedHeight==height) Glyph.Check else null,selected=state.selectedHeight==height,modifier=Modifier.fillMaxWidth()) {
                engine.selectQuality(height);vm.preferences {it.copy(qualityHeight=height)};quality=false;trackConfirmation="Quality: ${height}p";interact()
            }}
            item {Action("Done",modifier=Modifier.fillMaxWidth()){quality=false;interact()}}
        }}
    }
    if(upscaler) UpscalerDialog(state,outputTarget,onSelect={option ->
        engine.configureUpscaling(option,state.fsrSharpness,outputTarget)
        vm.preferences {it.copy(upscaler=option)};interact()
    },onSharpness={value,save ->
        engine.configureUpscaling(state.upscaler,value,outputTarget)
        if(save) vm.preferences {it.copy(fsrSharpness=value)}
        interact()
    },onDismiss={upscaler=false;interact()})
    if(displayInfo) DisplayInfoDialog(display,state,onDismiss={displayInfo=false;interact()})
}

private fun formatTime(millis: Long): String {
    val seconds=millis.coerceAtLeast(0)/1000
    return "%d:%02d".format(seconds/60,seconds%60)
}
