package dev.cinema.crunchyroll.playback

import android.content.Context
import android.hardware.display.DisplayManager
import android.media.MediaDrm
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import java.util.UUID

@Composable
fun currentPlaybackDisplay(): PlaybackDisplay {
    val context=LocalContext.current
    val view=LocalView.current
    val manager=context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    fun snapshot(): PlaybackDisplay {
        val display=view.display ?: manager.getDisplay(Display.DEFAULT_DISPLAY) ?: return PlaybackDisplay()
        val mode=display.mode
        val logical=android.graphics.Point()
        @Suppress("DEPRECATION")
        display.getRealSize(logical)
        val landscape=logical.x>=logical.y
        val width=if(landscape) maxOf(mode.physicalWidth,mode.physicalHeight) else minOf(mode.physicalWidth,mode.physicalHeight)
        val height=if(landscape) minOf(mode.physicalWidth,mode.physicalHeight) else maxOf(mode.physicalWidth,mode.physicalHeight)
        return PlaybackDisplay(display.displayId,display.name,display.flags and Display.FLAG_SECURE!=0,
            display.flags and Display.FLAG_SUPPORTS_PROTECTED_BUFFERS!=0,width,height,logical.x,logical.y)
    }
    var value by remember(view) {mutableStateOf(snapshot())}
    DisposableEffect(view,manager) {
        val listener=object: DisplayManager.DisplayListener {
            override fun onDisplayAdded(id: Int) {value=snapshot()}
            override fun onDisplayRemoved(id: Int) {value=snapshot()}
            override fun onDisplayChanged(id: Int) {value=snapshot()}
        }
        val layout=android.view.View.OnLayoutChangeListener {_,_,_,_,_,_,_,_,_->value=snapshot()}
        manager.registerDisplayListener(listener,Handler(Looper.getMainLooper()));view.addOnLayoutChangeListener(layout)
        onDispose {manager.unregisterDisplayListener(listener);view.removeOnLayoutChangeListener(layout)}
    }
    return value
}

fun displayDiagnostics(display: PlaybackDisplay,state: PlaybackState): String = buildString {
    appendLine("Sumi display report")
    appendLine("Android ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}; ${Build.MANUFACTURER} ${Build.MODEL}")
    appendLine("Window display: ${display.id} (${display.name}); ${display.width}x${display.height}")
    appendLine("Secure display: ${display.secure}; protected buffers: ${display.protectedBuffers}")
    appendLine("Surface: ${state.outputSurface}; requested Widevine level: L3")
    appendLine("Active DRM level: ${state.drmSecurityLevel.ifBlank {"not opened"}}; keys loaded: ${state.drmKeysLoaded}")
    appendLine("Video decoder: ${state.videoDecoder.ifBlank {"not initialized"}}")
    appendLine("Rendered frame events: ${state.renderedFrames}; ready: ${state.ready}; playing: ${state.playing}")
    appendLine("Video height: ${state.currentHeight}; position: ${state.position/1000}s")
    appendLine("Upscaler: ${state.upscaler.label}; FSR sharpness: ${state.fsrSharpness}%")
    appendLine("FSR input: ${state.upscaling.input}; output: ${state.upscaling.output}; active: ${state.upscaling.active}; processed frame: ${state.upscaling.rendered}")
    appendLine("Upscaler note: ${state.upscaling.notice.orEmpty()}")
    appendLine("Output note: ${state.outputNotice.orEmpty()}")
    appendLine("Playback error: ${state.errorCode.ifBlank {"none"}}")
    if(Build.VERSION.SDK_INT>=28) {
        var drm: MediaDrm?=null
        try {
            drm=MediaDrm(UUID.fromString("edef8ba9-79d6-4ace-a3c8-27dcd51d21ed"))
            appendLine("HDCP connected: ${hdcpName(drm.connectedHdcpLevel)}; maximum: ${hdcpName(drm.maxHdcpLevel)}")
        } catch(_: Exception) {appendLine("HDCP: unavailable")}
        finally {drm?.close()}
    }
    appendLine("HDCP readbacks are informational. The Android CDM enforces the license.")
}

private fun hdcpName(level: Int): String=when(level) {
    MediaDrm.HDCP_NONE -> "none"
    MediaDrm.HDCP_V1 -> "1.x"
    MediaDrm.HDCP_V2 -> "2.0"
    MediaDrm.HDCP_V2_1 -> "2.1"
    MediaDrm.HDCP_V2_2 -> "2.2"
    MediaDrm.HDCP_V2_3 -> "2.3"
    MediaDrm.HDCP_NO_DIGITAL_OUTPUT -> "no digital output"
    else -> "unknown"
}
