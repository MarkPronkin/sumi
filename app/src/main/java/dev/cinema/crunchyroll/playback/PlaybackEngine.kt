@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package dev.cinema.crunchyroll.playback

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.*
import androidx.media3.datasource.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.drm.*
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import dev.cinema.crunchyroll.data.LanguagePreferences
import dev.cinema.crunchyroll.data.ProfileData
import dev.cinema.crunchyroll.data.SkipInterval
import dev.cinema.crunchyroll.data.Upscaler
import dev.cinema.crunchyroll.data.account.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

data class PlaybackState(
    val playing: Boolean=false,val buffering: Boolean=true,val ready: Boolean=false,val renderedVideo: Boolean=false,
    val ended: Boolean=false,val position: Long=0,val duration: Long=0,val error: String?=null,
    val tracks: List<TrackOption> = emptyList(),val qualityHeights: List<Int> = emptyList(),val selectedHeight: Int=0,
    val currentHeight: Int=0,val native: Boolean=false,val progressError: Boolean=false,val drmKeysLoaded: Boolean=false,
    val subtitleChoices: List<SubtitleChoice> = emptyList(),val selectedSubtitle: String="off",val styledSubtitles: Boolean=false,
    val renderedFrames: Int=0,
    val drmSecurityLevel: String="",val videoDecoder: String="",val outputNotice: String?=null,val errorCode: String="",
    val upscaler: Upscaler=Upscaler.Off,val fsrSharpness: Int=50,val upscaling: UpscalingStatus=UpscalingStatus(),
    val outputSurface: String="TextureView",
    val skipSegments: List<SkipSegment> = emptyList(),
)
data class TrackOption(val type: Int,val group: TrackGroup,val index: Int,val label: String,val selected: Boolean,val height: Int=0)
private object PlaybackCleanup {val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)}

class PlaybackEngine(application: Application) : AndroidViewModel(application) {
    val player=ExoPlayer.Builder(application)
        .setSeekBackIncrementMs(SkipInterval.millis(null)).setSeekForwardIncrementMs(SkipInterval.millis(null)).build().apply {
        // Media3 requires an effects call before prepare to enable later changes during playback.
        setVideoEffects(emptyList())
        setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),true)
        setHandleAudioBecomingNoisy(true)
    }
    val state=MutableStateFlow(PlaybackState())
    val cleanupResult=MutableStateFlow("idle")
    private var mediaSession: MediaSession?=null
    private var episode: String?=null
    private var profile: String?=null
    private var save: ((String,String,Long,Long)->Unit)?=null
    private var lastSave=0L
    private var foreground=true
    private var resumeOnForeground=false
    private var resumePosition=0L
    private var preferences=ProfileData()
    private var title=""
    private var service: NativePlaybackService?=null
    private var stream: NativeStream?=null
    private var opening: Job?=null
    private var heartbeat: Job?=null
    private var skipMarkers: Job?=null
    private var generation=0L
    private var selectedHeight=0
    private var applyingQuality=false
    private var displayTest: Boolean?=null
    private var selectedSubtitle="off"
    private var fsr: Fsr1Pipeline?=null
    private var outputTarget=VideoDimensions()
    @Volatile private var effectGeneration=0L
    private var synced: ((String,String,Long)->Unit)?=null
    private var pendingRelease: Job?=null
    private data class Checkpoint(val backend: NativePlaybackService,val descriptor: NativeStream,val episode: String,val position: Long,val profile: String)
    private val pendingCheckpoint=AtomicReference<Checkpoint?>(null)
    private val checkpointWorker=AtomicBoolean(false)

    init {
        player.addAnalyticsListener(object: AnalyticsListener {
            override fun onDrmKeysLoaded(eventTime: AnalyticsListener.EventTime) {state.update {it.copy(drmKeysLoaded=true)}}
            override fun onVideoDecoderInitialized(eventTime: AnalyticsListener.EventTime,decoderName: String,initializedTimestampMs: Long,initializationDurationMs: Long) {
                state.update {it.copy(videoDecoder=decoderName)}
            }
        })
        player.addListener(object: Player.Listener {
            override fun onRenderedFirstFrame() {state.update {it.copy(renderedVideo=true,renderedFrames=it.renderedFrames+1)}}
            override fun onEvents(player: Player,events: Player.Events) {publish()}
            override fun onTracksChanged(tracks: Tracks) {applyPreferredAudio();applyPreferredQuality();publish()}
            override fun onPlayerError(error: PlaybackException) {
                val message=state.value.outputNotice ?: when {
                    error.errorCode in 6000..6999 -> "Android couldn’t obtain a usable Widevine license (${error.errorCodeName}). Retry or check this device’s DRM support."
                    service!=null -> "This episode couldn’t play (${error.errorCodeName}). Retry the stream or choose Auto quality."
                    else -> "The sample video couldn’t play. Check your connection and retry."
                }
                state.update {it.copy(buffering=false,error=message,errorCode=error.errorCodeName)}
                if(service!=null) releaseStream()
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {if(!isPlaying) persist()}
        })
        viewModelScope.launch {
            while(true) {
                delay(1000)
                if(player.mediaItemCount>0) {
                    publish()
                    if(player.isPlaying && System.currentTimeMillis()-lastSave>=10_000) persist()
                }
            }
        }
    }

    fun open(profileId: String,episodeId: String,startMs: Long,preferences: ProfileData,onSave: (String,String,Long,Long)->Unit,
             nativeService: NativePlaybackService?=null,episodeTitle: String="Big Buck Bunny · Demo sample",onSynced: ((String,String,Long)->Unit)?=null) {
        configureSkipInterval(preferences.skipIntervalSeconds)
        if(profile==profileId && episode==episodeId) {save=onSave;return}
        persist();releaseStream()
        // Finish the old media before changing the identity used by pause/save callbacks.
        player.pause();player.stop();player.clearMediaItems()
        save=onSave;profile=profileId;episode=episodeId;this.preferences=preferences;service=nativeService;title=episodeTitle
        synced=onSynced
        selectedHeight=preferences.qualityHeight
        displayTest=null
        if(mediaSession==null) mediaSession=MediaSession.Builder(getApplication(),player).build()
        player.trackSelectionParameters=player.trackSelectionParameters.buildUpon().clearOverrides()
            .setPreferredAudioLanguage(LanguagePreferences.serviceAudioLocale(preferences.audio))
            .setPreferredTextLanguage(if(LanguagePreferences.isOff(preferences.subtitles)) null else preferences.subtitles)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT,LanguagePreferences.isOff(preferences.subtitles)).build()
        load(startMs.coerceAtLeast(0),foreground)
    }

    private fun load(position: Long,play: Boolean) {
        val previousOpening=opening;previousOpening?.cancel();val operation=++generation
        val id=episode ?: return
        val backend=service
        resumePosition=position
        state.value=PlaybackState(native=backend!=null,selectedHeight=selectedHeight).withUpscaling()
        if(backend==null) {prepare(null,position,play);return}
        opening=viewModelScope.launch {
            var descriptor: NativeStream?=null
            var accepted=false
            try {
                previousOpening?.join()
                pendingRelease?.join()
                descriptor=backend.open(id,preferences.audio)
                ensureActive()
                if(operation!=generation) return@launch
                stream=descriptor
                selectedSubtitle=descriptor.preferredSubtitleLanguage(preferences.subtitles)
                prepare(descriptor,position,play)
                accepted=true
                skipMarkers=launch {
                    val segments=backend.skipSegments(id,descriptor.contentId)
                    if(operation==generation) state.update {it.copy(skipSegments=segments)}
                }
                heartbeat=launch {
                    while(true) {
                        delay(descriptor.renewSeconds*1000L)
                        try {backend.renew(descriptor,player.currentPosition)}
                        catch(e: CancellationException) {throw e}
                        catch(e: Exception) {player.pause();state.update {it.copy(buffering=false,error="The playback session couldn’t be renewed. Retry the episode.")};break}
                    }
                }
            } catch(e: CancellationException) {throw e}
            catch(e: Exception) {if(operation==generation) {if(!accepted) stream=null;state.update {it.copy(buffering=false,error=e.accountMessage())}}}
            finally {if(descriptor!=null && !accepted) cleanup(backend,descriptor)}
        }
    }

    private fun prepare(descriptor: NativeStream?,position: Long,play: Boolean) {
        val styled=descriptor?.hardSubtitles?.containsKey(selectedSubtitle)==true
        val item=MediaItem.Builder().setMediaId(episode.orEmpty()).setUri(descriptor?.manifestFor(selectedSubtitle) ?: SAMPLE_URL)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
        if(descriptor!=null) {
            item.setMimeType(MimeTypes.APPLICATION_MPD)
            descriptor.licenseUrl?.let {item.setDrmConfiguration(MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID).setLicenseUri(it).setMultiSession(true).build())}
            item.setSubtitleConfigurations((if(styled || selectedSubtitle=="off") emptyList() else descriptor.subtitles.filter {it.language==selectedSubtitle}).map {sub ->
                MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(sub.url)).setLanguage(sub.language)
                    .setMimeType(when(sub.format) {"ass"->MimeTypes.TEXT_SSA;"srt"->MimeTypes.APPLICATION_SUBRIP;else->MimeTypes.TEXT_VTT})
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build()
            })
            player.trackSelectionParameters=player.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT,styled || selectedSubtitle=="off").setPreferredTextLanguage(selectedSubtitle.takeUnless {it=="off"}).build()
            state.update {it.copy(subtitleChoices=descriptor.subtitleChoices(),selectedSubtitle=selectedSubtitle,styledSubtitles=styled)}
        }
        val media=item.build()
        if(descriptor==null) player.setMediaItem(media,position)
        else {
            val http=DefaultHttpDataSource.Factory().setUserAgent(NativeClientProtocol.USER_AGENT)
            val source=ResolvingDataSource.Factory(http) {spec ->
                val host=spec.uri.host.orEmpty()
                if(host=="crunchyroll.com" || host.endsWith(".crunchyroll.com") || host.endsWith(".crunchyrollsvc.com"))
                    spec.withRequestHeaders(spec.httpRequestHeaders+mapOf("Authorization" to "Bearer ${descriptor.authorization.accessToken}"))
                else spec
            }
            val defaultDrm=DefaultDrmSessionManagerProvider()
            val factory=DefaultMediaSourceFactory(getApplication<Application>()).setDataSourceFactory(DefaultDataSource.Factory(getApplication(),source))
                .setDrmSessionManagerProvider {current ->
                    if(current.localConfiguration?.drmConfiguration!=null) DefaultDrmSessionManager.Builder()
                        .setUuidAndExoMediaDrmProvider(C.WIDEVINE_UUID) {uuid ->acquireDisplayDrm(uuid,
                            {level->state.update {it.copy(drmSecurityLevel=level)}},
                            {state.update {it.copy(outputNotice="Software Widevine isn’t available on this device.")}})}
                        .setMultiSession(true).build(NativeWidevineCallback(descriptor))
                    else defaultDrm.get(current)
                }
            player.setMediaSource(factory.createMediaSource(media),position)
        }
        player.prepare();player.playWhenReady=play && foreground
    }

    fun toggle() {if(player.isPlaying) player.pause() else {if(player.playbackState==Player.STATE_ENDED) player.seekTo(0);player.play()}}
    fun seek(position: Long) {player.seekTo(position.coerceIn(0,state.value.duration.coerceAtLeast(0)));publish();persist()}
    fun skip(delta: Long) {seek(relativeSeekPosition(player.currentPosition,delta,state.value.duration))}
    fun configureSkipInterval(seconds: Int) {
        val millis=SkipInterval.millis(seconds)
        player.setSeekBackIncrementMs(millis)
        player.setSeekForwardIncrementMs(millis)
    }
    fun skipSegment(segment: SkipSegment) {
        if(state.value.ready && segment in state.value.skipSegments && segment.contains(player.currentPosition,state.value.duration))
            seek(segment.endMs)
    }
    fun retry() {
        displayTest?.let {openDisplayTest(it);return}
        val position=player.currentPosition.takeIf {it>0} ?: resumePosition;releaseStream();load(position,true)
    }
    fun select(option: TrackOption) {player.trackSelectionParameters=player.trackSelectionParameters.buildUpon()
        .setTrackTypeDisabled(option.type,false).setOverrideForType(TrackSelectionOverride(option.group,option.index)).build()}
    fun selectQuality(height: Int) {selectedHeight=height.coerceAtLeast(0);applyPreferredQuality();publish()}
    fun configureUpscaling(upscaler: Upscaler,sharpness: Int,target: VideoDimensions) {
        val strength=sharpness.coerceIn(0,100)
        val rebuild=upscaler!=state.value.upscaler || (upscaler!=Upscaler.Off && target!=outputTarget)
        outputTarget=target
        fsr?.sharpness=strength
        state.update {it.copy(upscaler=upscaler,fsrSharpness=strength)}
        if(!rebuild) return
        val operation=++effectGeneration
        state.update {it.copy(upscaling=UpscalingStatus())}
        fsr=if(upscaler==Upscaler.Fsr1) Fsr1Pipeline(target,strength) {status ->
            if(effectGeneration==operation) state.update {it.copy(upscaling=status)}
        } else null
        player.setVideoEffects(fsr?.effects ?: emptyList())
    }
    fun reportOutputSurface(nativeTv: Boolean) {
        val surface=if(nativeTv) "SurfaceView (native TV output)" else "TextureView"
        if(state.value.outputSurface!=surface) state.update {it.copy(outputSurface=surface)}
    }

    private fun PlaybackState.withUpscaling()=copy(upscaler=state.value.upscaler,
        fsrSharpness=state.value.fsrSharpness,upscaling=state.value.upscaling,outputSurface=state.value.outputSurface)
    fun selectSubtitle(language: String) {
        val descriptor=stream ?: return
        if(language!="off" && descriptor.subtitleChoices().none {it.language==language}) return
        if(language==selectedSubtitle) return
        val position=player.currentPosition;val playing=player.playWhenReady
        selectedSubtitle=language
        state.update {it.copy(buffering=true,error=null)}
        prepare(descriptor,position,playing)
    }
    fun ownsEpisode(profileId: String,id: String)=profile==profileId && episode==id
    fun progressSyncPending()=checkpointWorker.get()
    fun clearTestVideo() {
        if(episode!=null || stream!=null) return
        close();state.value=PlaybackState(buffering=false).withUpscaling()
    }
    /** Public Media3 demo media and license, completely separate from account playback. */
    fun openDisplayTest(widevine: Boolean) {
        close();displayTest=widevine;state.value=PlaybackState().withUpscaling()
        if(!widevine) player.setMediaItem(MediaItem.fromUri(SAMPLE_URL))
        else {
            val license="https://proxy.uat.widevine.com/proxy?video_id=GTS_SW_SECURE_CRYPTO&provider=widevine_test"
            val media=MediaItem.Builder().setUri("https://storage.googleapis.com/wvmedia/cenc/h264/tears/tears.mpd")
                .setDrmConfiguration(MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID).setLicenseUri(license).build()).build()
            val factory=DefaultMediaSourceFactory(getApplication<Application>()).setDrmSessionManagerProvider {
                DefaultDrmSessionManager.Builder().setUuidAndExoMediaDrmProvider(C.WIDEVINE_UUID) {uuid ->
                    acquireDisplayDrm(uuid,{level->state.update {it.copy(drmSecurityLevel=level)}},
                        {state.update {it.copy(outputNotice="Software Widevine isn’t available on this device.")}})
                }.build(HttpMediaDrmCallback(license,DefaultHttpDataSource.Factory()))
            }
            player.setMediaSource(factory.createMediaSource(media))
        }
        player.prepare();player.playWhenReady=foreground
    }
    private fun applyPreferredAudio() {
        val languages=player.currentTracks.groups.filter {it.type==C.TRACK_TYPE_AUDIO}.flatMap {group ->
            (0 until group.length).filter {group.isTrackSupported(it)}.mapNotNull {group.getTrackFormat(it).language}
        }
        val language=LanguagePreferences.bestMatch(preferences.audio,languages) {it} ?: return
        // Track preferences preserve the media item, position, play state, and manual overrides.
        val next=player.trackSelectionParameters.buildUpon().setPreferredAudioLanguage(language).build()
        if(next!=player.trackSelectionParameters) player.trackSelectionParameters=next
    }

    private fun applyPreferredQuality() {
        if(applyingQuality) return
        val video=player.currentTracks.groups.filter {it.type==C.TRACK_TYPE_VIDEO}
        if(video.isEmpty()) return
        applyingQuality=true
        try {
            val builder=player.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                .setMaxVideoSize(Int.MAX_VALUE,Int.MAX_VALUE).setMinVideoSize(0,0)
            if(selectedHeight>0) {
                val group=video.filter {g->(0 until g.length).any {g.isTrackSupported(it) && g.getTrackFormat(it).height==selectedHeight}}
                    .sortedByDescending {it.isSelected}.firstOrNull()
                if(group!=null) builder.setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup,(0 until group.length).filter {group.isTrackSupported(it) && group.getTrackFormat(it).height==selectedHeight}))
                else selectedHeight=0
            }
            val next=builder.build()
            if(next!=player.trackSelectionParameters) player.trackSelectionParameters=next
        } finally {applyingQuality=false}
    }
    fun subtitlesOff() {player.trackSelectionParameters=player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT,true).build()}
    fun background() {
        foreground=false;resumeOnForeground=player.playWhenReady;resumePosition=player.currentPosition;persist();player.pause()
        if(service!=null) {releaseStream();player.stop();player.clearMediaItems()}
    }
    fun foreground() {
        foreground=true
        if(service!=null && stream==null && episode!=null) load(resumePosition,resumeOnForeground)
        else if(resumeOnForeground && player.mediaItemCount>0) player.play()
        resumeOnForeground=false
    }
    fun persist() {
        val id=episode ?: return;val p=profile ?: return;val duration=player.duration
        if(duration<=0) return
        val position=player.currentPosition.coerceAtLeast(0)
        save?.invoke(p,id,position,duration);lastSave=System.currentTimeMillis();resumePosition=position
        val backend=service;val descriptor=stream
        if(backend!=null && descriptor!=null) {pendingCheckpoint.set(Checkpoint(backend,descriptor,id,position,p));drainCheckpoints()}
    }
    private fun drainCheckpoints() {
        if(!checkpointWorker.compareAndSet(false,true)) return
        PlaybackCleanup.scope.launch {
            try {
                while(true) {
                    val next=pendingCheckpoint.getAndSet(null) ?: break
                    try {withTimeout(10_000) {next.backend.saveProgress(next.descriptor,next.episode,next.position)};synced?.invoke(next.profile,next.episode,next.position);state.update {it.copy(progressError=false)}}
                    catch(_: Exception) {state.update {it.copy(progressError=true)}}
                }
            } finally {checkpointWorker.set(false);if(pendingCheckpoint.get()!=null) drainCheckpoints()}
        }
    }
    private fun cleanup(backend: NativePlaybackService,descriptor: NativeStream) {
        cleanupResult.value="closing"
        pendingRelease=PlaybackCleanup.scope.launch {
            try {withTimeout(10_000) {backend.close(descriptor)};cleanupResult.value="closed"}
            catch(_: Exception) {cleanupResult.value="failed"}
        }
    }
    private fun releaseStream() {
        opening?.cancel();heartbeat?.cancel();skipMarkers?.cancel();generation++
        val old=stream;stream=null
        if(old!=null) service?.let {cleanup(it,old)}
    }
    fun close() {
        displayTest=null
        persist();releaseStream();episode=null;profile=null;player.pause();player.stop();player.clearMediaItems()
        service=null;mediaSession?.release();mediaSession=null;state.value=PlaybackState().withUpscaling();resumeOnForeground=false
    }
    fun closeIfEpisode(id: String) {if(episode==id) close()}
    private fun publish() {
        val tracks=player.currentTracks.groups.flatMap {group ->(0 until group.length).filter {group.isTrackSupported(it)}.map {i ->
            val format=group.getTrackFormat(i)
            val language=format.language?.takeUnless {it=="und"}?.let(LanguagePreferences::label)
            TrackOption(group.type,group.mediaTrackGroup,i,when(group.type) {
                C.TRACK_TYPE_VIDEO -> if(format.height>0) "${format.height}p" else "Video ${i+1}"
                C.TRACK_TYPE_AUDIO -> language ?: format.label ?: "Audio ${i+1}"
                else -> language ?: format.label ?: "Subtitles ${i+1}"
            },group.isTrackSelected(i),format.height)
        }}
        state.update {current -> current.copy(playing=player.isPlaying,buffering=player.playbackState==Player.STATE_BUFFERING || (opening?.isActive==true && !current.ready),
            ready=player.playbackState==Player.STATE_READY || player.playbackState==Player.STATE_ENDED,
            ended=player.playbackState==Player.STATE_ENDED,position=player.currentPosition.coerceAtLeast(0),duration=player.duration.coerceAtLeast(0),
            tracks=tracks,qualityHeights=tracks.filter {it.type==C.TRACK_TYPE_VIDEO && it.height>0}.map {it.height}.distinct().sortedDescending(),
            selectedHeight=selectedHeight,currentHeight=player.videoFormat?.height?.coerceAtLeast(0) ?: 0,native=service!=null)}
    }
    override fun onCleared() {persist();releaseStream();mediaSession?.release();player.release()}
    companion object {const val SAMPLE_URL="https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4"}
}
