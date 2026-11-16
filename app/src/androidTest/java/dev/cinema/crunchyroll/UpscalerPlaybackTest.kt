@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package dev.cinema.crunchyroll

import android.content.res.Configuration
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.media3.ui.PlayerView
import dev.cinema.crunchyroll.data.Upscaler
import dev.cinema.crunchyroll.data.account.AccountState
import dev.cinema.crunchyroll.playback.*
import dev.cinema.crunchyroll.ui.CinemaViewModel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class UpscalerPlaybackTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()

    @Test fun pickerAppliesFsrSavesSharpnessAndCanDisableItWithoutLosingPlayback() {
        lateinit var vm: CinemaViewModel
        lateinit var engine: PlaybackEngine
        compose.runOnUiThread {
            vm=ViewModelProvider(compose.activity)[CinemaViewModel::class.java]
            engine=ViewModelProvider(compose.activity)[PlaybackEngine::class.java]
        }
        compose.waitUntil(30_000) {vm.account.value !is AccountState.Restoring}
        compose.runOnUiThread {
            vm.enterDemo()
            vm.preferences {it.copy(upscaler=Upscaler.Off,fsrSharpness=50)}
        }
        compose.waitUntil(10_000) {vm.local.value.current.upscaler==Upscaler.Off}
        compose.runOnUiThread {vm.play("frieren-s1-e1",true)}
        compose.waitUntil(60_000) {engine.state.value.renderedVideo || engine.state.value.error!=null}
        assertNull(engine.state.value.error)
        compose.runOnUiThread {engine.player.pause();engine.seek(15_000)}
        compose.onNodeWithTag("upscaler-picker").performClick()
        compose.onNodeWithTag("upscaler-fsr1").performScrollTo().performClick()
        // Resume to make sure frames pass through the newly selected shaders.
        compose.runOnUiThread {engine.player.play()}
        compose.waitUntil(30_000) {engine.state.value.upscaling.rendered || engine.state.value.error!=null}
        assertNull(engine.state.value.error)
        assertTrue(engine.state.value.upscaling.notice,engine.state.value.upscaling.active)
        assertTrue(engine.state.value.position>=14_000)
        compose.runOnUiThread {engine.player.pause()}
        compose.onNodeWithContentDescription("+5%",substring=false).performScrollTo().performClick()
        compose.waitUntil(10_000) {vm.local.value.current.fsrSharpness==55}
        assertEquals(55,engine.state.value.fsrSharpness)
        compose.onNodeWithTag("fsr-sharpness-value").assertTextEquals("Sharpness: 55%")
        val tv=compose.activity.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK==Configuration.UI_MODE_TYPE_TELEVISION
        fun find(view: View): PlayerView? {
            if(view is PlayerView) return view
            if(view is ViewGroup) for(i in 0 until view.childCount) find(view.getChildAt(i))?.let {return it}
            return null
        }
        if(tv) compose.waitUntil(10_000) {
            var sized=false
            compose.runOnUiThread {
                val frame=(find(compose.activity.window.decorView)?.videoSurfaceView as? SurfaceView)?.holder?.surfaceFrame
                sized=frame?.width()==engine.state.value.upscaling.output.width && frame.height()==engine.state.value.upscaling.output.height
            }
            sized
        }
        compose.runOnUiThread {
            val surface=find(compose.activity.window.decorView)?.videoSurfaceView
            if(tv) {
                assertTrue("TV upscaling requires native-resolution SurfaceView output",surface is SurfaceView)
                val frame=(surface as SurfaceView).holder.surfaceFrame
                assertEquals(engine.state.value.upscaling.output.width,frame.width())
                assertEquals(engine.state.value.upscaling.output.height,frame.height())
                val mode=compose.activity.window.decorView.display.mode
                assertEquals(upscaleDimensions(VideoDimensions(320,180),VideoDimensions(mode.physicalWidth,mode.physicalHeight)),engine.state.value.upscaling.output)
            } else assertTrue("Phones retain the compatible TextureView",surface is TextureView)
        }
        val instrumentation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val screenshot=instrumentation.uiAutomation.takeScreenshot()
        java.io.File(instrumentation.targetContext.getExternalFilesDir(null),"fsr-picker.png").outputStream().use {
            screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
        }
        screenshot.recycle()
        compose.onNodeWithContentDescription("Done",substring=false).performScrollTo().performClick()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(15_000) {compose.onAllNodesWithTag("upscaler-picker").fetchSemanticsNodes().isNotEmpty()}
        assertEquals(Upscaler.Fsr1,engine.state.value.upscaler)
        assertEquals(55,engine.state.value.fsrSharpness)
        assertTrue(engine.state.value.position>=14_000)
        assertFalse(engine.state.value.playing)
        compose.onNodeWithTag("upscaler-picker").performClick()
        compose.onNodeWithTag("upscaler-off").performScrollTo().performClick()
        compose.waitUntil(10_000) {vm.local.value.current.upscaler==Upscaler.Off}
        compose.onNodeWithTag("fsr-sharpness").assertDoesNotExist()
        compose.onNodeWithContentDescription("Done",substring=false).performScrollTo().performClick()
        compose.runOnUiThread {engine.player.play()}
        compose.waitUntil(15_000) {engine.state.value.playing && engine.state.value.position>16_000}
        assertNull(engine.state.value.error)
        compose.runOnUiThread {assertTrue(find(compose.activity.window.decorView)?.videoSurfaceView is TextureView)}
    }
}
