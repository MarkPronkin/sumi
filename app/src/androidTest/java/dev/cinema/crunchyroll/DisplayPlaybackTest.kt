@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package dev.cinema.crunchyroll

import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.media3.ui.PlayerView
import dev.cinema.crunchyroll.data.account.AccountState
import dev.cinema.crunchyroll.playback.*
import dev.cinema.crunchyroll.ui.CinemaViewModel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DisplayPlaybackTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()

    @Test fun defaultPlaybackRendersClearAndLicensedWidevineWithoutAccountProgress() {
        lateinit var vm: CinemaViewModel
        lateinit var engine: PlaybackEngine
        compose.runOnUiThread {
            vm=ViewModelProvider(compose.activity)[CinemaViewModel::class.java]
            engine=ViewModelProvider(compose.activity)["display-check",PlaybackEngine::class.java]
        }
        compose.waitUntil(30_000) {vm.account.value !is AccountState.Restoring}
        compose.runOnUiThread {vm.enterDemo();vm.navigate("Display check")}
        compose.waitUntil(15_000) {compose.onAllNodesWithTag("display-check").fetchSemanticsNodes().isNotEmpty()}
        listOf("Automatic","Standard","Spatial compatibility").forEach {compose.onNodeWithText(it).assertDoesNotExist()}
        val progress=vm.local.value.current.progress
        compose.runOnUiThread {
            fun find(view: View): PlayerView? {
                if(view is PlayerView) return view
                if(view is ViewGroup) for(i in 0 until view.childCount) find(view.getChildAt(i))?.let {return it}
                return null
            }
            assertTrue("Default playback must attach a real TextureView",find(compose.activity.window.decorView)?.videoSurfaceView is TextureView)
        }
        for(label in listOf("Play clear test","Play Widevine test")) {
            compose.onNodeWithContentDescription(label).performScrollTo().performClick()
            compose.waitUntil(60_000) {engine.state.value.renderedVideo || engine.state.value.error!=null}
            assertNull(engine.state.value.errorCode,engine.state.value.error)
            assertTrue("$label must render actual video",engine.state.value.renderedVideo)
            compose.waitUntil(15_000) {engine.state.value.position>1000}
            if(label.contains("Widevine")) {
                assertTrue(engine.state.value.drmKeysLoaded)
                assertEquals("L3",engine.state.value.drmSecurityLevel)
                assertTrue(engine.state.value.videoDecoder.isNotBlank())
                val lowest=engine.state.value.qualityHeights.minOrNull() ?: 0
                if(lowest>0) {
                    compose.runOnUiThread {engine.selectQuality(lowest)}
                    compose.waitUntil(30_000) {engine.state.value.currentHeight==lowest}
                }
                compose.runOnUiThread {
                    engine.configureUpscaling(dev.cinema.crunchyroll.data.Upscaler.Fsr1,50,
                        if(lowest<1080) VideoDimensions(1920,1080) else VideoDimensions(3840,2160))
                }
                compose.waitUntil(30_000) {engine.state.value.upscaling.rendered || engine.state.value.error!=null}
                assertNull(engine.state.value.error)
                assertTrue("Licensed Widevine video must pass through EASU and RCAS",engine.state.value.upscaling.active)
                assertTrue(engine.state.value.upscaling.rendered)
            }
            compose.onNodeWithContentDescription("Stop",substring=false).performScrollTo().performClick()
            compose.waitUntil(10_000) {!engine.state.value.playing && !engine.state.value.renderedVideo}
            compose.runOnUiThread {assertEquals(0,engine.player.mediaItemCount)}
        }
        assertEquals("Display checks must not add watch history",progress,vm.local.value.current.progress)
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) {compose.onAllNodesWithTag("display-check").fetchSemanticsNodes().isNotEmpty()}
        listOf("Automatic","Standard","Spatial compatibility").forEach {compose.onNodeWithText(it).assertDoesNotExist()}
    }
}
