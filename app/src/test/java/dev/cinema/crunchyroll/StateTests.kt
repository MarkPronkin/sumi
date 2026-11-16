package dev.cinema.crunchyroll

import dev.cinema.crunchyroll.data.*
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StateTests {
    @Test fun `new query cancels an older request and never displays its result`() = runTest {
        val started=mutableListOf<String>()
        val cancelled=mutableListOf<String>()
        val repo=object: CatalogueRepository {
            override val shows=emptyList<Anime>()
            override fun episodes(showId: String,season: Int)=emptyList<Episode>()
            override suspend fun search(query: String,category: String): List<Anime> {
                started+=query
                try {delay(if(query=="slow") 2000 else 50)} catch(e: CancellationException) {cancelled+=query;throw e}
                return listOf(Anime(query,query,"","",2026,emptyList(),0))
            }
        }
        val request=MutableStateFlow(SearchRequest("slow"))
        val seen=mutableListOf<LoadState<List<Anime>>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {searchResults(request,repo).toList(seen)}
        advanceTimeBy(400);runCurrent()
        request.value=SearchRequest("latest")
        advanceTimeBy(400);runCurrent()
        assertEquals(listOf("slow","latest"),started)
        assertTrue("slow" in cancelled)
        assertEquals(listOf("latest"),seen.filterIsInstance<LoadState.Content<List<Anime>>>().flatMap {it.value.map(Anime::id)})
    }

    @Test fun `failed search can be retried with the same query`() = runTest {
        var attempts=0
        val repo=object: CatalogueRepository {
            override val shows=emptyList<Anime>()
            override fun episodes(showId: String,season: Int)=emptyList<Episode>()
            override suspend fun search(query: String,category: String): List<Anime> {if(attempts++==0) throw IOException();return emptyList()}
        }
        val request=MutableStateFlow(SearchRequest())
        val seen=mutableListOf<LoadState<List<Anime>>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {searchResults(request,repo).toList(seen)}
        runCurrent()
        assertTrue(seen.last() is LoadState.Error)
        request.value=SearchRequest(revision=1);runCurrent()
        assertEquals(LoadState.Content(emptyList<Anime>()),seen.last())
        assertEquals(2,attempts)
    }

    @Test fun `watchlist failure preserves state and retry commits to the captured profile`() = runTest {
        val store=MemoryStore()
        val actions=WatchlistActions(store)
        store.fail=true
        try {actions.set("main","frieren",true);fail("Expected persistence failure")} catch(_: IOException) {}
        assertTrue(store.state.value.current.watchlist.isEmpty())
        store.fail=false
        store.update {it.copy(activeProfile="guest")}
        actions.set("main","frieren",true)
        assertEquals(setOf("frieren"),store.state.value.profiles.getValue("main").watchlist)
        assertTrue(store.state.value.current.watchlist.isEmpty())
        actions.set("main","frieren",false)
        assertTrue(store.state.value.profiles.getValue("main").watchlist.isEmpty())
    }

    @Test fun `progress and preferences stay isolated by profile`() {
        val state=LocalState().updateProfile("main") {it.copy(motion=MotionLevel.Reduced,progress=mapOf("demo" to Progress(500,1000,1)))}
        val guest=state.copy(activeProfile="guest")
        assertEquals(MotionLevel.Maximal,guest.current.motion)
        assertTrue(guest.current.progress.isEmpty())
        assertEquals(.5f,state.current.progress.getValue("demo").fraction)
        assertFalse(state.current.progress.getValue("demo").complete)
        assertEquals(0f,Progress(50,0,0).fraction)
        assertEquals(1f,Progress(200,100,0).fraction)
    }

    @Test fun `next episode stays in a valid series and ends at the final chapter`() {
        val repo=DemoCatalogue()
        val first=repo.episodes("frieren",1).first()
        assertEquals(2,repo.next(first)?.number)
        assertEquals(2,repo.next(repo.episodes("frieren",1).last())?.season)
        assertNull(repo.next(repo.episodes("frieren",2).last()))
        assertNull(repo.episode("unknown"))
    }

    private class MemoryStore: UserStore {
        override val state=MutableStateFlow(LocalState())
        var fail=false
        override suspend fun update(transform: (LocalState)->LocalState) {
            if(fail) throw IOException("Simulated storage failure")
            state.value=transform(state.value)
        }
    }
}
