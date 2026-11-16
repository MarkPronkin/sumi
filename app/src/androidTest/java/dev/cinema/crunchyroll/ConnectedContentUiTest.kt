package dev.cinema.crunchyroll

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import dev.cinema.crunchyroll.data.*
import dev.cinema.crunchyroll.design.CinemaTheme
import dev.cinema.crunchyroll.design.LocalConnected
import dev.cinema.crunchyroll.ui.CinemaViewModel
import dev.cinema.crunchyroll.ui.MyContentScreen
import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Rule
import org.junit.Test

/** Isolated signed-in rendering fixture; no credentials or live service mutation are required. */
class ConnectedContentUiTest {
    @get:Rule val compose=createComposeRule()
    private val viewModels=ViewModelStore()
    @After fun clear() {compose.runOnUiThread {viewModels.clear()}}

    private fun model(key: String,restored: Map<String,Any?> = emptyMap()): CinemaViewModel {
        val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        val factory=object: ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T: ViewModel> create(modelClass: Class<T>): T = CinemaViewModel(app,SavedStateHandle(restored)) as T
        }
        return ViewModelProvider(viewModels,factory)[key,CinemaViewModel::class.java]
    }

    @Test fun legacySavedDestinationsRestoreTheMatchingContentView() {
        compose.runOnUiThread {
            listOf("My List" to ContentView.Watchlist,"History" to ContentView.History).forEach {(route,view) ->
                val vm=model(route,mapOf("destination" to route))
                assertEquals("My Content",vm.destination.value)
                assertEquals(view.name,vm.contentView.value)
            }
        }
    }

    @Test fun remoteContentKeepsPaginationReachableInWatchedShowsAndHistory() {
        val catalogue=DemoCatalogue()
        val show=catalogue.show("frieren")!!
        val episodes=catalogue.episodes(show.id,1)
        val remote=listOf(
            HistoryEntry(show,episodes[1],Progress(30_000,100_000,30)),
            HistoryEntry(show,episodes[0],Progress(100_000,100_000,20)))
        val accountKey="account:fixture:profile"
        val state=LocalState(activeProfile=accountKey,profiles=mapOf(accountKey to ProfileData(),
            "main" to ProfileData(progress=mapOf("solo-s1-e1" to Progress(10_000,100_000,40)))))
        lateinit var vm: CinemaViewModel
        compose.runOnUiThread {
            vm=model("content-fixture")
            vm.connectedHistory.value=remote
            vm.historyHasMore.value=true
            vm.connectedWatchlist.value=listOf(catalogue.show("solo")!!)
        }
        compose.setContent {
            CinemaTheme(MotionLevel.Reduced,false,false) {
                CompositionLocalProvider(LocalConnected provides true) {MyContentScreen(vm,state,"")}
            }
        }
        compose.onAllNodesWithTag("watched-show-frieren").assertCountEquals(1)
        compose.onNodeWithTag("watched-show-solo").assertDoesNotExist()
        compose.onNodeWithTag("account-watched").performScrollToNode(hasContentDescription("Load more"))
        compose.onNodeWithContentDescription("Load more").assertIsEnabled()
        compose.onNodeWithTag("content-tab-History").performClick()
        compose.onNodeWithTag("account-history").performScrollToNode(hasTestTag("history-episode-frieren-s1-e1"))
        compose.onNodeWithTag("history-episode-frieren-s1-e1").assertExists()
        compose.onNodeWithTag("account-history").performScrollToNode(hasContentDescription("Load more"))
        compose.onNodeWithContentDescription("Load more").assertIsEnabled()
        compose.onNodeWithTag("content-tab-Watchlist").performClick()
        compose.onNodeWithText("SOLO LEVELING").assertExists()
    }
}
