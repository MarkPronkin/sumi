package dev.cinema.crunchyroll

import dev.cinema.crunchyroll.data.*
import dev.cinema.crunchyroll.data.account.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class MyContentTests {
    private val catalogue=DemoCatalogue()
    private fun entry(showId: String,number: Int,time: Long,complete: Boolean=false): HistoryEntry {
        val show=catalogue.show(showId)!!
        return HistoryEntry(show,catalogue.episodes(showId,1)[number-1],Progress(if(complete) 100_000 else 20_000,100_000,time))
    }

    @Test fun `watched shows group by series with latest order completed episodes and resume`() {
        val older=entry("frieren",1,10)
        val newest=entry("frieren",2,40,true)
        val other=entry("solo",1,30,true)
        val result=watchedShows(listOf(older,other,newest,older.copy(progress=older.progress.copy(updatedAt=5))))
        assertEquals(listOf("frieren","solo"),result.map {it.show.id})
        assertEquals(2,result.first().playedEpisodes)
        assertEquals(newest,result.first().latest)
        assertEquals(older,result.first().resume)
        assertNull(result.last().resume)
        // Series identity, rather than title, owns grouping.
        assertEquals(2,watchedShows(listOf(older,other.copy(show=other.show.copy(title=older.show.title)))).size)
    }

    @Test fun `overlapping pages retain newest episode checkpoint regardless of page order`() {
        val latest=entry("frieren",1,100)
        val stale=latest.copy(progress=latest.progress.copy(positionMs=1_000,updatedAt=5))
        val another=entry("solo",1,60)
        val pages=HistoryPages().withPage(1,HistoryPage(listOf(latest),true))
            .withPage(2,HistoryPage(listOf(stale,another),true))
        assertEquals(listOf(latest,another),pages.items)
        assertEquals(2,pages.lastPage)
        assertTrue(pages.hasMore)
    }

    @Test fun `refreshing first page retains older series and cursor until loaded window is reconciled`() {
        val head=entry("frieren",1,40)
        val older=entry("solo",1,10)
        val pages=HistoryPages().withPage(1,HistoryPage(listOf(head),true))
            .withPage(2,HistoryPage(listOf(older),true))
        val refreshed=pages.withPage(1,HistoryPage(listOf(head.copy(progress=head.progress.copy(updatedAt=50))),true))
        assertEquals(listOf("frieren","solo"),watchedShows(refreshed.items).map {it.show.id})
        assertEquals(2,refreshed.lastPage)
        assertTrue(refreshed.hasMore)
        val terminal=refreshed.withPage(1,HistoryPage(listOf(head),false))
        assertEquals(listOf(head),terminal.items)
        assertEquals(1,terminal.lastPage)
        assertFalse(terminal.hasMore)
    }

    @Test fun `same series on a whole page does not hide pagination to older watched series`() {
        val first=HistoryPage(listOf(entry("frieren",1,40)),true)
        val second=HistoryPage(listOf(entry("frieren",2,30),entry("frieren",3,20)),true)
        val pages=HistoryPages().withPage(1,first).withPage(2,second)
        assertEquals(1,watchedShows(pages.items).size)
        assertTrue(pages.hasMore)
        val all=pages.withPage(3,HistoryPage(listOf(entry("dandadan",1,10,true)),false))
        assertEquals(listOf("frieren","dandadan"),watchedShows(all.items).map {it.show.id})
        assertFalse(all.hasMore)
    }

    @Test fun `history merges local checkpoints orders recent first and ignores unplayed or unknown local ids`() {
        val remote=entry("solo",1,20)
        val localEpisode=catalogue.episodes("frieren",1)[1]
        val unsynced=remote.progress.copy(positionMs=40_000,updatedAt=10,needsSync=true)
        val local=mapOf(remote.episode.id to unsynced,localEpisode.id to Progress(100_000,100_000,30),
            "dandadan-s1-e1" to Progress(0,100_000,50),"unknown" to Progress(50_000,100_000,60))
        val result=mergedHistory(listOf(remote),local,catalogue)
        assertEquals(listOf(localEpisode.id,remote.episode.id),result.map {it.episode.id})
        assertEquals(unsynced,result.last().progress)
        assertTrue(result.first().progress.complete)
    }

    @Test fun `account and demo collections use only the selected profile checkpoints`() {
        val accountKey="account:one:profile"
        val state=LocalState(activeProfile=accountKey,profiles=mapOf(
            "main" to ProfileData(watchlist=setOf("solo"),progress=mapOf("solo-s1-e1" to Progress(1,100,30))),
            accountKey to ProfileData(progress=mapOf("frieren-s1-e1" to Progress(100,100,10)))))
        assertEquals(listOf("frieren"),watchedShows(mergedHistory(emptyList(),state.current.progress,catalogue)).map {it.show.id})
        assertTrue(state.current.watchlist.isEmpty())
        assertEquals(listOf("solo"),watchedShows(mergedHistory(emptyList(),state.copy(activeProfile="main").current.progress,catalogue)).map {it.show.id})
    }

    @Test fun `manual edits win over stale pages and new playback restores a removed episode`() {
        val partial = entry("frieren", 2, 10)
        val other = entry("frieren", 1, 5)
        val watched = Progress.watched(0, 20)
        val id = partial.episode.id
        val marked = mergedHistory(listOf(partial, other), mapOf(id to watched), catalogue)
        assertTrue(marked.first().progress.complete)
        assertEquals(1f, marked.first().progress.fraction)
        assertEquals(other, watchedShows(marked).single().resume)
        assertEquals(id, mergedHistory(emptyList(), mapOf(id to watched), catalogue).single().episode.id)

        val removed = Progress(0, 0, 30, historyEdit = HistoryEdit.Remove)
        for (timestamp in listOf(0L, 10L, 30L)) {
            val stale = partial.copy(progress = partial.progress.copy(updatedAt = timestamp))
            assertNull(resumeProgress(removed, stale.progress))
            assertEquals(listOf(other), mergedHistory(listOf(stale, other), mapOf(id to removed), catalogue))
        }
        val replayed = partial.copy(progress = partial.progress.copy(updatedAt = 40))
        assertEquals(listOf(replayed, other), mergedHistory(listOf(replayed, other), mapOf(id to removed), catalogue))
    }

    @Test fun `legacy saved destinations select the matching content tab`() {
        assertEquals(ContentView.Watchlist,ContentView.fromLegacyRoute("My List"))
        assertEquals(ContentView.Watchlist,ContentView.fromLegacyRoute("Watchlist"))
        assertEquals(ContentView.History,ContentView.fromLegacyRoute("History"))
        assertNull(ContentView.fromLegacyRoute("Profile"))
        assertEquals(ContentView.WatchedShows,ContentView.fromKey("obsolete"))
        ContentView.entries.forEach {assertEquals(it,ContentView.fromKey(it.name))}
    }

    @Test fun `pagination distinguishes terminal metadata from nonblank false and zero values`() {
        listOf("null","false","0","1","\"\"").forEach {next ->
            assertFalse("Terminal next_page=$next",ConnectedCatalogue.historyHasNext(Json.parseToJsonElement("""{"total":200,"meta":{"next_page":$next}}"""),1,50,50))
        }
        listOf("true","2","\"/history?page=2\"").forEach {next ->
            assertTrue(ConnectedCatalogue.historyHasNext(Json.parseToJsonElement("""{"meta":{"next_page":$next}}"""),1,50,50))
        }
        assertFalse(ConnectedCatalogue.historyHasNext(Json.parseToJsonElement("""{"total":0}"""),1,50,50))
        assertTrue(ConnectedCatalogue.historyHasNext(Json.parseToJsonElement("{}"),1,50,50))
        assertFalse(ConnectedCatalogue.historyHasNext(Json.parseToJsonElement("{}"),1,50,12))
        assertFalse(ConnectedCatalogue.historyHasNext(Json.parseToJsonElement("""{"total":100}"""),2,50,50))
    }

    @Test fun `account later history pages hydrate watched series absent from home and preserve latest progress`() = runBlocking {
        MockWebServer().use {server ->
            server.start()
            fun reply(body: String) {server.enqueue(MockResponse().setBody(body).setHeader("Content-Type","application/json"))}
            val vault=object: TokenVault {
                override suspend fun read(): StoredSession?=null
                override suspend fun write(session: StoredSession) {}
                override suspend fun clear() {}
            }
            reply("""{"access_token":"access-fixture","refresh_token":"refresh-fixture","account_id":"account-fixture","profile_id":"profile-fixture","expires_in":3600}""")
            reply("{}");reply("{}")
            val session=AccountSession(CrunchyrollHttp(server.url("/")),vault)
            session.login("viewer@example.invalid","fixture")
            repeat(3) {server.takeRequest()}
            val repo=ConnectedCatalogue(session)
            fun history(episode: String,show: String,time: Long,position: Int) = """{"playhead":$position,"date_played":$time,"panel":{"id":"$episode","type":"episode","title":"Episode","episode_metadata":{"series_id":"$show","series_title":"$show","season_number":1,"episode_number":1,"duration_ms":100000}}}"""
            reply("""{"meta":{"next_page":2},"data":[${history("one","recent",100,40)}]}""")
            reply("""{"data":[{"id":"recent","title":"Recent series","type":"series"}]}""")
            val first=repo.history(1,1)
            reply("""{"meta":{"next_page":null},"data":[${history("one","recent",50,10)},${history("two","older",40,100)}]}""")
            reply("""{"data":[{"id":"older","title":"Older series","type":"series"}]}""")
            val second=repo.history(2,1)
            val pages=HistoryPages().withPage(1,first).withPage(2,second)
            assertEquals(listOf("recent","older"),watchedShows(pages.items).map {it.show.id})
            assertEquals("Older series",repo.show("older")!!.title)
            assertNotNull(repo.episode("two"))
            assertTrue(repo.shows.isEmpty()) // The watched catalogue must not depend on homeIds.
            assertEquals(40_000,repo.progress("one")!!.positionMs)
            assertFalse(pages.hasMore)
            assertEquals("1",server.takeRequest().requestUrl!!.queryParameter("page"))
            server.takeRequest()
            assertEquals("2",server.takeRequest().requestUrl!!.queryParameter("page"))
            server.takeRequest()
            Unit
        }
    }
}
