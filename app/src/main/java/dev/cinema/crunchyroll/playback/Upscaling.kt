package dev.cinema.crunchyroll.playback

import kotlin.math.min
import kotlin.math.roundToInt

data class VideoDimensions(val width: Int=0,val height: Int=0) {
    val valid get() = width>0 && height>0
    override fun toString() = "$width × $height"
}

/** The physical surface must fit the configured frame even when normal downscaling is needed. */
fun videoSurfaceDimensions(video: VideoDimensions,target: VideoDimensions): VideoDimensions {
    if(!video.valid || !target.valid) return target
    val scale=min(target.width.toDouble()/video.width,target.height.toDouble()/video.height)
    return VideoDimensions((video.width*scale).roundToInt().coerceAtMost(target.width),
        (video.height*scale).roundToInt().coerceAtMost(target.height))
}

/** Fit the video into the output without stretching, downscaling, or changing its orientation. */
fun upscaleDimensions(input: VideoDimensions,target: VideoDimensions,maxTextureSize: Int=Int.MAX_VALUE): VideoDimensions {
    if(!input.valid || !target.valid || maxTextureSize<=0) return input
    val scale=min(min(target.width.toDouble(),maxTextureSize.toDouble())/input.width,
        min(target.height.toDouble(),maxTextureSize.toDouble())/input.height)
    if(scale<=1.0) return input
    return VideoDimensions((input.width*scale).roundToInt().coerceAtMost(min(target.width,maxTextureSize)),
        (input.height*scale).roundToInt().coerceAtMost(min(target.height,maxTextureSize)))
}

data class UpscalingStatus(
    val input: VideoDimensions=VideoDimensions(),
    val output: VideoDimensions=VideoDimensions(),
    val active: Boolean=false,
    val rendered: Boolean=false,
    val notice: String?=null,
)
