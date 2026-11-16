package dev.cinema.crunchyroll

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.inspector.WindowInspector
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.filters.SdkSuppress
import dev.cinema.crunchyroll.data.DemoCatalogue
import dev.cinema.crunchyroll.data.MotionLevel
import dev.cinema.crunchyroll.data.Upscaler
import dev.cinema.crunchyroll.data.account.AccountState
import dev.cinema.crunchyroll.playback.PlaybackEngine
import dev.cinema.crunchyroll.ui.CinemaViewModel
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test

@SdkSuppress(minSdkVersion=29)
class PlayerSystemBarsTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private lateinit var vm: CinemaViewModel
    private lateinit var engine: PlaybackEngine

    private fun enterPlayer(previousOrientation: Int=ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
        assumeFalse(compose.activity.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK==Configuration.UI_MODE_TYPE_TELEVISION)
        assumeFalse(compose.activity.resources.configuration.smallestScreenWidthDp>=600)
        compose.runOnUiThread {
            vm=ViewModelProvider(compose.activity)[CinemaViewModel::class.java]
            engine=ViewModelProvider(compose.activity)[PlaybackEngine::class.java]
        }
        compose.waitUntil(30_000) {vm.account.value !is AccountState.Restoring}
        if(vm.isConnected) {
            compose.runOnUiThread {vm.logout()}
            compose.waitUntil(15_000) {!vm.isConnected && vm.catalogue is DemoCatalogue}
        }
        compose.runOnUiThread {
            vm.selectProfile("main");vm.enterDemo();vm.navigate("Home")
            vm.preferences {it.copy(upscaler=Upscaler.Off,motion=MotionLevel.Reduced,qualityHeight=0)}
        }
        compose.waitUntil(10_000) {vm.local.value.enteredDemo && vm.local.value.current.motion==MotionLevel.Reduced}
        compose.runOnUiThread {compose.activity.requestedOrientation=previousOrientation}
        compose.waitUntil(10_000) {compose.activity.resources.configuration.orientation==Configuration.ORIENTATION_PORTRAIT}
        waitForBars(hidden=false)
        compose.runOnUiThread {vm.play("frieren-s1-e1",true)}
        compose.waitUntil(15_000) {compose.onAllNodesWithTag("next-chapter").fetchSemanticsNodes().isNotEmpty()}
        waitForLandscape()
        compose.runOnUiThread {engine.player.pause()}
        waitForBars(hidden=true)
    }

    private fun waitForLandscape() {
        compose.waitUntil(10_000) {compose.activity.resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE}
        compose.runOnUiThread {assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,compose.activity.requestedOrientation)}
    }

    private fun exitPlayer(previousOrientation: Int=ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
        compose.onNodeWithContentDescription("Exit playback").performClick()
        compose.waitUntil(10_000) {vm.playerEpisode.value==null && compose.activity.resources.configuration.orientation==Configuration.ORIENTATION_PORTRAIT}
        compose.runOnUiThread {assertEquals(previousOrientation,compose.activity.requestedOrientation)}
        waitForBars(hidden=false)
    }

    /** Check the actual focused window, including the separate window owned by a dialog. */
    private fun waitForBars(hidden: Boolean) {
        var lastObservation=""
        try {
            compose.waitUntil(10_000) {
                var matches=false
                compose.runOnUiThread {
                    val window=WindowInspector.getGlobalWindowViews().firstOrNull {it.isAttachedToWindow && it.hasWindowFocus()}
                    val insets=window?.let {ViewCompat.getRootWindowInsets(it)}
                    lastObservation="window=$window status=${insets?.isVisible(WindowInsetsCompat.Type.statusBars())} navigation=${insets?.isVisible(WindowInsetsCompat.Type.navigationBars())}"
                    matches=insets!=null && if(hidden)
                        !insets.isVisible(WindowInsetsCompat.Type.statusBars()) && !insets.isVisible(WindowInsetsCompat.Type.navigationBars())
                    else insets.isVisible(WindowInsetsCompat.Type.statusBars()) && insets.isVisible(WindowInsetsCompat.Type.navigationBars())
                }
                matches
            }
        } catch(error: ComposeTimeoutException) {
            throw AssertionError("Expected hidden=$hidden; $lastObservation",error)
        }
    }

    @Test fun playbackHidesBarsAfterResumeRecreationAndEpisodeChangeAndRestoresThemOnExit() {
        enterPlayer()
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        waitForLandscape()
        waitForBars(hidden=true)
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) {compose.onAllNodesWithTag("next-chapter").fetchSemanticsNodes().isNotEmpty()}
        waitForLandscape()
        waitForBars(hidden=true)
        compose.onNodeWithTag("next-chapter").performClick()
        compose.waitUntil(15_000) {vm.playerEpisode.value=="frieren-s1-e2" && engine.ownsEpisode("main","frieren-s1-e2")}
        compose.waitForIdle()
        waitForLandscape()
        waitForBars(hidden=true)
        compose.runOnUiThread {engine.player.pause()}
        exitPlayer()
    }

    @Test fun playerMenusKeepTheirOwnWindowAndPlaybackImmersive() {
        enterPlayer()
        compose.waitUntil(45_000) {engine.state.value.ready || engine.state.value.error!=null}
        assertNull(engine.state.value.error)
        for((label,tag) in listOf("Tracks" to "tracks-dialog","Quality: Auto" to "quality-dialog",
            "Upscaler: Off" to "upscaler-dialog","Display info" to "display-info")) {
            compose.onNodeWithContentDescription(label).performClick()
            compose.onNodeWithTag(tag).assertIsDisplayed()
            compose.runOnUiThread {assertTrue("The menu must have its own focused window",!compose.activity.window.decorView.hasWindowFocus())}
            waitForBars(hidden=true)
            compose.onNodeWithContentDescription("Done").performScrollTo().performClick()
            compose.onNodeWithTag(tag).assertDoesNotExist()
            waitForBars(hidden=true)
            waitForLandscape()
        }
        exitPlayer()
    }

    @Test fun repeatedPlayerEntriesRestoreTheOriginalAutomaticOrientationPolicy() {
        enterPlayer(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)
        compose.activityRule.scenario.recreate()
        waitForLandscape()
        exitPlayer(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)
        compose.runOnUiThread {vm.play("frieren-s1-e2",true)}
        compose.waitUntil(15_000) {compose.onAllNodesWithTag("next-chapter").fetchSemanticsNodes().isNotEmpty()}
        waitForLandscape()
        compose.runOnUiThread {engine.player.pause()}
        exitPlayer(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)
    }
}
