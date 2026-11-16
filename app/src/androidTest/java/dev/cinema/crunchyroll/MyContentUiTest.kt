package dev.cinema.crunchyroll

import android.content.res.Configuration
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import dev.cinema.crunchyroll.data.*
import dev.cinema.crunchyroll.data.account.AccountState
import dev.cinema.crunchyroll.ui.CinemaViewModel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Uses the same primary navigation and tab controls on phone, tablet, and TV emulators. */
class MyContentUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()

    private fun demo(): CinemaViewModel {
        lateinit var vm: CinemaViewModel
        compose.runOnUiThread {vm=ViewModelProvider(compose.activity)[CinemaViewModel::class.java]}
        compose.waitUntil(30_000) {vm.ready.value && vm.account.value !is AccountState.Restoring}
        assertFalse("Use a demo emulator",vm.isConnected)
        compose.runOnUiThread {vm.selectProfile("main")}
        compose.waitUntil(10_000) {vm.local.value.activeProfile=="main"}
        compose.runOnUiThread {
            vm.enterDemo()
            vm.preferences {it.copy(motion=MotionLevel.Reduced,watchlist=setOf("solo"),progress=mapOf(
                "frieren-s1-e1" to Progress(100_000,100_000,10),
                "frieren-s1-e2" to Progress(30_000,100_000,30),
                "solo-s1-e1" to Progress(100_000,100_000,20),
                "dandadan-s1-e1" to Progress(0,100_000,40)))}
            vm.openContent(ContentView.WatchedShows)
        }
        compose.waitUntil(10_000) {compose.onAllNodesWithTag("watched-show-frieren").fetchSemanticsNodes().isNotEmpty()}
        return vm
    }

    private fun selectTab(view: ContentView) {
        val node=compose.onNodeWithTag("content-tab-${view.name}")
        activate(node)
        node.assertIsSelected()
    }

    private fun activate(node: SemanticsNodeInteraction) {
        val tv=compose.activity.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK==Configuration.UI_MODE_TYPE_TELEVISION
        if(tv) {
            node.performSemanticsAction(SemanticsActions.RequestFocus) {it()}
            node.performKeyInput {pressKey(Key.DirectionCenter)}
        } else node.performClick()
    }

    @Test fun watchedShowsGroupEpisodesAndTabsKeepNavigationAndResumeBehavior() {
        val vm=demo()
        compose.onNodeWithContentDescription("My Content").assertIsSelected()
        compose.onAllNodesWithTag("watched-show-frieren").assertCountEquals(1)
        compose.onNodeWithTag("content-watched").performScrollToNode(hasTestTag("watched-show-solo"))
        compose.onNodeWithTag("watched-show-solo").assertExists()
        compose.onNodeWithTag("watched-show-dandadan").assertDoesNotExist()
        selectTab(ContentView.History)
        compose.onNodeWithTag("content-history").performScrollToNode(hasTestTag("history-episode-frieren-s1-e2"))
        compose.onNodeWithTag("history-episode-frieren-s1-e2").assertIsDisplayed()
        compose.onNodeWithTag("content-history").performScrollToNode(hasTestTag("history-episode-frieren-s1-e1"))
        compose.onNodeWithTag("history-episode-frieren-s1-e1").assertExists()
        selectTab(ContentView.Watchlist)
        compose.onNodeWithText("SOLO LEVELING").assertExists()
        compose.onNodeWithContentDescription("My Content").assertIsSelected()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) {compose.onAllNodesWithTag("content-tab-Watchlist").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("content-tab-Watchlist").assertIsSelected()
        selectTab(ContentView.WatchedShows)
        compose.onNodeWithTag("content-watched").performScrollToNode(hasContentDescription("Resume FRIEREN season 1 episode 2"))
        compose.onNodeWithContentDescription("Resume FRIEREN season 1 episode 2").performClick()
        compose.waitUntil(10_000) {vm.playerEpisode.value=="frieren-s1-e2"}
        compose.runOnUiThread {vm.back()}
        compose.waitUntil(10_000) {compose.onAllNodesWithTag("content-tab-WatchedShows").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("content-tab-WatchedShows").assertIsSelected()
    }

    @Test fun oldRoutesAndProfileHistoryOpenContentTabsAndDemoUndoKeepsItsProfile() {
        val vm=demo()
        compose.runOnUiThread {vm.navigate("My List")}
        compose.onNodeWithTag("content-tab-Watchlist").assertIsSelected()
        assertEquals("My Content",vm.destination.value)
        activate(compose.onNodeWithContentDescription("Remove SOLO LEVELING from Watchlist"))
        compose.waitUntil(10_000) {"solo" !in vm.local.value.profiles.getValue("main").watchlist}
        compose.waitUntil(10_000) {compose.onAllNodesWithContentDescription("Undo").fetchSemanticsNodes().isNotEmpty()}
        compose.runOnUiThread {vm.selectProfile("guest")}
        compose.waitUntil(10_000) {vm.local.value.activeProfile=="guest"}
        val guestBefore=vm.local.value.profiles["guest"] ?: ProfileData()
        activate(compose.onNodeWithContentDescription("Undo"))
        compose.waitUntil(10_000) {"solo" in vm.local.value.profiles.getValue("main").watchlist}
        assertEquals(guestBefore.watchlist,vm.local.value.profiles["guest"]?.watchlist ?: emptySet<String>())
        compose.runOnUiThread {vm.selectProfile("main");vm.navigate("History")}
        compose.onNodeWithTag("content-tab-History").assertIsSelected()
        compose.onNodeWithContentDescription("My Content").assertIsSelected()
        compose.onNodeWithContentDescription("Profile").performClick()
        compose.onNodeWithText("History",substring=false).performScrollTo().performClick()
        compose.onNodeWithTag("content-tab-History").assertIsSelected()
        compose.onNodeWithContentDescription("My Content").assertIsSelected()
    }

    @Test fun historyScrollPositionSurvivesTabChangesAndRecreation() {
        val vm=demo()
        val progress=(1..2).flatMap {vm.catalogue.episodes("frieren",it)}.mapIndexed {index,episode ->
            episode.id to Progress(20_000,100_000,index.toLong()+1)
        }.toMap()
        compose.runOnUiThread {vm.preferences {it.copy(progress=progress)}}
        compose.waitUntil(10_000) {vm.local.value.current.progress==progress}
        selectTab(ContentView.History)
        compose.onNodeWithTag("content-history").performScrollToIndex(5)
        val anchor=compose.onNodeWithTag("history-episode-frieren-s1-e4")
        anchor.assertIsDisplayed()
        val top=anchor.fetchSemanticsNode().boundsInRoot.top
        selectTab(ContentView.Watchlist)
        selectTab(ContentView.History)
        assertEquals(top,anchor.fetchSemanticsNode().boundsInRoot.top,2f)
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) {compose.onAllNodesWithTag("history-episode-frieren-s1-e4").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("content-tab-History").assertIsSelected()
        assertEquals(top,anchor.fetchSemanticsNode().boundsInRoot.top,2f)
    }
}
