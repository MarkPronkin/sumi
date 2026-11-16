package dev.cinema.crunchyroll.playback

data class PlaybackDisplay(val id: Int=0,val name: String="Phone display",val secure: Boolean=true,
    val protectedBuffers: Boolean=true,val width: Int=0,val height: Int=0,
    val logicalWidth: Int=width,val logicalHeight: Int=height)

fun playbackOutputTarget(viewport: VideoDimensions,display: PlaybackDisplay): VideoDimensions {
    if(!viewport.valid || display.logicalWidth<=0 || display.logicalHeight<=0) return viewport
    return VideoDimensions((viewport.width.toDouble()*display.width/display.logicalWidth).toInt().coerceAtLeast(1),
        (viewport.height.toDouble()*display.height/display.logicalHeight).toInt().coerceAtLeast(1))
}
