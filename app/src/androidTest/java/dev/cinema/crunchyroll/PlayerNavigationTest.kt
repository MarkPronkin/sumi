@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package dev.cinema.crunchyroll

import android.content.res.Configuration
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import dev.cinema.crunchyroll.data.*
import dev.cinema.crunchyroll.data.account.AccountState
import dev.cinema.crunchyroll.playback.*
import dev.cinema.crunchyroll.ui.CinemaViewModel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PlayerNavigationTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private lateinit var vm: CinemaViewModel
    private lateinit var engine: PlaybackEngine

    private fun prepareDemo() {
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
            vm.preferences {it.copy(upscaler=Upscaler.Off,motion=MotionLevel.Reduced,autoplay=true)}
        }
        compose.waitUntil(10_000) {vm.local.value.enteredDemo && vm.local.value.current.motion==MotionLevel.Reduced}
    }

    private fun waitForPlayback() {
        compose.waitUntil(45_000) {engine.state.value.ready || engine.state.value.error!=null}
        assertNull(engine.state.value.error)
        assertTrue(engine.state.value.duration>100_000)
        compose.runOnUiThread {engine.player.pause()}
    }

    private fun activate(node: SemanticsNodeInteraction) {
        val tv=compose.activity.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK==Configuration.UI_MODE_TYPE_TELEVISION
        node.assertIsDisplayed()
        if(tv) {
            node.performSemanticsAction(SemanticsActions.RequestFocus) {it()}
            node.performKeyInput {pressKey(Key.DirectionCenter)}
        } else node.performClick()
    }

    private fun waitForSkip(kind: SkipKind): SemanticsNodeInteraction {
        val tag="skip-${kind.name.lowercase()}"
        compose.waitUntil(15_000) {engine.state.value.ready && compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()}
        return compose.onNodeWithTag(tag)
    }

    @Test fun configuredSkipPersistsPerProfileAndControlsTheActualPlayer() {
        prepareDemo()
        compose.runOnUiThread {vm.navigate("Settings")}
        activate(compose.onNodeWithTag("skip-interval-15").performScrollTo())
        compose.waitUntil(10_000) {vm.local.value.current.skipIntervalSeconds==15}
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("skip-interval-15").performScrollTo().assertIsSelected()
        compose.runOnUiThread {vm.selectProfile("guest")}
        compose.waitUntil(10_000) {vm.local.value.activeProfile=="guest"}
        compose.runOnUiThread {vm.preferences {it.copy(skipIntervalSeconds=5)}}
        compose.waitUntil(10_000) {vm.local.value.current.skipIntervalSeconds==5}
        compose.runOnUiThread {vm.selectProfile("main")}
        compose.waitUntil(10_000) {vm.local.value.activeProfile=="main" && vm.local.value.current.skipIntervalSeconds==15}
        compose.runOnUiThread {vm.play("frieren-s1-e1",true)}
        waitForPlayback()
        compose.runOnUiThread {
            assertEquals(15_000L,engine.player.seekBackIncrement)
            assertEquals(15_000L,engine.player.seekForwardIncrement)
            engine.seek(60_000)
        }
        val tv=compose.activity.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK==Configuration.UI_MODE_TYPE_TELEVISION
        if(!tv) {
            val layer=compose.onNodeWithTag("player-gesture-layer")
            layer.performSemanticsAction(SemanticsActions.OnClick) {it()}
            compose.waitUntil(5000) {compose.onAllNodesWithTag("player-forward").fetchSemanticsNodes().isEmpty()}
            layer.performTouchInput {doubleClick(Offset(width*.85f,height*.5f))}
            compose.waitUntil(5000) {engine.state.value.position==75_000L}
            layer.performTouchInput {advanceEventTime(400);doubleClick(Offset(width*.15f,height*.5f))}
            compose.waitUntil(5000) {engine.state.value.position==60_000L}
            layer.performSemanticsAction(SemanticsActions.OnClick) {it()}
        }
        activate(compose.onNodeWithTag("player-forward"))
        compose.waitUntil(5000) {engine.state.value.position==75_000L}
        assertFalse("Seeking must preserve pause",engine.state.value.playing)
        compose.activityRule.scenario.recreate()
        compose.onNodeWithContentDescription("Forward 15 seconds").assertIsDisplayed()
        compose.runOnUiThread {
            assertEquals(15_000L,engine.player.seekForwardIncrement)
            assertFalse(engine.player.playWhenReady)
            engine.close();vm.back()
        }
    }

    @Test fun continueWatchingCanPlayTheNextChapterWithoutOpeningDetails() {
        prepareDemo()
        compose.runOnUiThread {vm.saveProgress("main","frieren-s1-e1",30_000,634_000)}
        compose.waitUntil(10_000) {vm.local.value.current.progress["frieren-s1-e1"]?.positionMs==30_000L}
        compose.onNodeWithTag("continue-watching").performScrollTo()
        val card=compose.onNode(hasText("S1 E1",substring=true) and hasAnyAncestor(hasTestTag("continue-watching")))
        activate(card)
        waitForPlayback()
        assertEquals("frieren-s1-e1",vm.playerEpisode.value)
        assertTrue(engine.state.value.position>=29_000)
        assertNull(vm.detail.value)
        activate(compose.onNodeWithTag("next-chapter"))
        compose.waitUntil(15_000) {vm.playerEpisode.value=="frieren-s1-e2" && engine.ownsEpisode("main","frieren-s1-e2")}
        waitForPlayback()
        assertNull(vm.detail.value)
        compose.onNodeWithTag("next-chapter").assertIsDisplayed()
        compose.runOnUiThread {engine.close();vm.back()}
    }

    @Test fun skipButtonsWorkWithHiddenControlsAndPreservePauseAndThePostCreditsScene() {
        prepareDemo()
        compose.runOnUiThread {vm.play("frieren-s1-e1",true)}
        waitForPlayback()
        // Explicit test markers on the sample film exercise the actual player seek path.
        val segments=listOf(SkipSegment(SkipKind.Recap,10_000,20_000),
            SkipSegment(SkipKind.Intro,30_000,40_000),SkipSegment(SkipKind.Credits,50_000,60_000))
        compose.runOnUiThread {engine.state.value=engine.state.value.copy(skipSegments=segments);engine.seek(15_000)}
        waitForSkip(SkipKind.Recap).assertIsDisplayed()
        compose.onNodeWithTag("skip-intro").assertDoesNotExist()
        compose.onRoot().performTouchInput {click(center)}
        compose.waitUntil(5000) {compose.onAllNodesWithTag("next-chapter").fetchSemanticsNodes().isEmpty()}
        activate(compose.onNodeWithTag("skip-recap"))
        compose.waitUntil(5000) {engine.state.value.position==20_000L}
        assertFalse(engine.state.value.playing)
        for((segment,position) in segments.drop(1).zip(listOf(35_000L,55_000L))) {
            compose.runOnUiThread {engine.seek(position)}
            activate(waitForSkip(segment.kind))
            compose.waitUntil(5000) {engine.state.value.position==segment.endMs}
            assertFalse(engine.state.value.playing)
        }
        assertFalse("Skipping titles must preserve the scene after the credits",engine.state.value.ended)
        assertTrue(engine.state.value.position<engine.state.value.duration)
        compose.onNodeWithTag("skip-credits").assertDoesNotExist()
        compose.runOnUiThread {engine.seek(15_000)}
        waitForSkip(SkipKind.Recap).assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        waitForSkip(SkipKind.Recap).assertIsDisplayed()
        assertFalse("Recreation must preserve pause and loaded skip markers",engine.state.value.playing)
        compose.onNodeWithTag("next-chapter").assertIsDisplayed()
        compose.runOnUiThread {engine.close();vm.back()}
    }
}
