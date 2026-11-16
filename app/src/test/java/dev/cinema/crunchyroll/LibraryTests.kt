package dev.cinema.crunchyroll

import dev.cinema.crunchyroll.data.*
import dev.cinema.crunchyroll.data.account.*
import dev.cinema.crunchyroll.playback.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test

class LibraryTests {
    private class MemoryVault: TokenVault {
        var value: StoredSession?=null
        override suspend fun read()=value
        override suspend fun write(session: StoredSession) {value=session}
        override suspend fun clear() {value=null}
    }
    private fun MockWebServer.reply(text: String)=enqueue(MockResponse().setBody(text).setHeader("Content-Type","application/json"))
    private suspend fun authenticated(server: MockWebServer): AccountSession {
        server.reply("""{"access_token":"access-fixture","refresh_token":"refresh-fixture","account_id":"account-fixture","profile_id":"profile-fixture","expires_in":3600}""")
        server.reply("{}");server.reply("{}")
        return AccountSession(CrunchyrollHttp(server.url("/")),MemoryVault()).also {it.login("viewer@example.invalid","fixture");repeat(3) {server.takeRequest()}}
    }
    @Test fun `watchlist episode panels resolve to actual series instead of disappearing`() = runBlocking {
        MockWebServer().use {server ->
            server.start();val repo=ConnectedCatalogue(authenticated(server))
            server.reply("""{"data":[{"panel":{"id":"episode-fixture","type":"episode","title":"An episode","episode_metadata":{"series_id":"series-fixture","series_title":"Real series"}}}]}""")
            server.reply("""{"data":[{"id":"series-fixture","type":"series","title":"Real series","series_metadata":{"season_count":2}}]}""")
            val shows=repo.watchlist()
            assertEquals(listOf("series-fixture"),shows.map {it.id})
            assertEquals("Real series",shows.single().title)
            assertTrue(server.takeRequest().path!!.contains("/watchlist"))
            assertTrue(server.takeRequest().path!!.contains("/cms/objects/series-fixture"))
        }
    }
    @Test fun `history maps episode metadata progress timestamps and pagination`() = runBlocking {
        MockWebServer().use {server ->
            server.start();val repo=ConnectedCatalogue(authenticated(server))
            server.reply("""{"total":3,"data":[{"id":"ep-one","parent_id":"series-fixture","date_played":"2026-11-16T00:00:00Z","playhead":96,"fully_watched":false,"panel":{"id":"ep-one","type":"episode","title":"Episode one","episode_metadata":{"series_id":"series-fixture","series_title":"Series","season_number":2,"episode_number":4,"duration_ms":100000}}},{"id":"ep-two","parent_id":"series-fixture","playhead":20,"fully_watched":true,"panel":{"id":"ep-two","type":"episode","title":"Episode two","episode_metadata":{"series_id":"series-fixture","season_number":2,"episode_number":5,"duration_ms":100000}}}]}""")
            server.reply("""{"data":[{"id":"series-fixture","type":"series","title":"Series"}]}""")
            val result=repo.history(1,2)
            assertTrue(result.hasMore)
            assertEquals(2,result.items.size)
            assertEquals(4,result.items.first().episode.number)
            assertEquals(96_000,result.items.first().progress.positionMs)
            assertFalse(result.items.first().progress.complete)
            assertTrue(result.items.first().progress.updatedAt>0)
            assertTrue(result.items.last().progress.complete)
            assertNotNull(repo.episode("ep-one"))
        }
    }
    @Test fun `playhead query preserves cloud position and watched state`() = runBlocking {
        MockWebServer().use {server ->
            server.start();val repo=ConnectedCatalogue(authenticated(server))
            server.reply("""{"data":[{"content_id":"episode-fixture","playhead":47,"fully_watched":false,"last_modified":"2026-11-16T00:00:00Z"}]}""")
            val result=repo.playheads(listOf(Episode("episode-fixture","series",1,1,"Episode",120_000)))
            assertEquals(47_000,result.getValue("episode-fixture").positionMs)
            assertTrue(result.getValue("episode-fixture").updatedAt>0)
            assertTrue(server.takeRequest().path!!.contains("content_ids=episode-fixture"))
        }
    }
    @Test fun `continue watching resolves and caches the next episode without opening series details`() = runBlocking {
        MockWebServer().use {server ->
            server.start();val repo=ConnectedCatalogue(authenticated(server))
            server.reply("""{"data":[{"playhead":30,"panel":{"id":"current","type":"episode","title":"Current","episode_metadata":{"series_id":"series","series_title":"Series","season_number":2,"episode_number":12,"duration_ms":1400000}}}]}""")
            server.reply("""{"data":[{"id":"series","type":"series","title":"Series"}]}""")
            val current=repo.history().items.single().episode
            repeat(2) {server.takeRequest()}
            assertNull(repo.next(current))
            assertTrue(repo.episodes("series",2).isEmpty())
            server.reply("""{"data":[{"panel":{"id":"next","type":"episode","title":"Next season","episode_metadata":{"series_id":"series","season_number":4,"episode_number":1,"duration_ms":1420000}}}]}""")
            val next=repo.loadNext(current,"ja")!!
            assertEquals("next",next.id)
            assertEquals(4,next.season)
            assertEquals(next,repo.snapshot().episode("next"))
            assertNotNull(repo.show(next.showId))
            val request=server.takeRequest()
            assertEquals("/content/v2/discover/up_next/current",request.requestUrl!!.encodedPath)
            assertEquals("ja-JP",request.requestUrl!!.queryParameter("preferred_audio_language"))
            assertEquals(6,server.requestCount)
        }
    }
    @Test fun `up next ignores recommendations and never loops to the current episode`() = runBlocking {
        MockWebServer().use {server ->
            server.start();val repo=ConnectedCatalogue(authenticated(server))
            val episode=Episode("current","series",1,1,"Current")
            server.reply("""{"data":[{"panel":{"id":"current","type":"episode","episode_metadata":{"series_id":"series"}}},{"panel":{"id":"other","type":"episode","episode_metadata":{"series_id":"another-series"}}}]}""")
            assertNull(repo.loadNext(episode,"en"))
            server.reply("""{"data":[]}""")
            assertNull(repo.loadNext(episode,"en"))
            // A transport failure is distinct from a completed series, so the UI can offer Retry.
            server.enqueue(MockResponse().setResponseCode(503))
            try {repo.loadNext(episode,"en");fail("Expected a retryable lookup failure")}
            catch(e: ServiceException) {assertEquals(503,e.status)}
            repeat(3) {server.takeRequest()}
        }
    }
    @Test fun `unsynced local progress wins while acknowledged progress follows the cloud`() {
        val local=Progress(60_000,100_000,20,true)
        val remote=Progress(20_000,100_000,30)
        assertEquals(local,resumeProgress(local,remote))
        assertEquals(remote,resumeProgress(local.copy(needsSync=false),remote))
        assertEquals(local,resumeProgress(local,null))
        assertEquals(local.copy(needsSync=false),resumeProgress(local.copy(needsSync=false),remote.copy(updatedAt=10)))
        assertFalse(Progress(96_000,100_000,0).complete)
    }
    @Test fun `styled subtitles use provider manifests and Off returns to the original`() {
        val body=Json.parseToJsonElement("""{"url":"https://cdn.example.invalid/original.mpd","token":"token-fixture","audioLocale":"ja-JP","hardSubs":{"en-US":{"url":"https://cdn.example.invalid/styled-english.mpd"},"none":{"url":"https://cdn.example.invalid/original.mpd"}},"subtitles":{"en-US":{"language":"en-US","url":"https://cdn.example.invalid/english.ass","format":"ass"}}}""").jsonObject
        val stream=NativePlaybackService.parse("episode-fixture",body,PlaybackAuthorization("access-fixture","account-fixture",1))
        assertTrue(stream.subtitleChoices().single().styled)
        assertTrue(stream.manifestFor("en-US").contains("styled-english.mpd"))
        assertTrue(stream.manifestFor("off").contains("original.mpd"))
    }
    @Test fun `banner poster clean background and color logo remain separate`() {
        val body=Json.parseToJsonElement("""{"id":"series","title":"Series","type":"series","images":{"poster_wide":[[{"source":"https://cdn.example.invalid/banner.jpg","width":1280}]],"poster_tall":[[{"source":"https://cdn.example.invalid/poster.jpg","width":600}]]},"images_localized":{"hero_background":{"url":"https://imgsrv.crunchyroll.com/cdn-cgi/image/{:param}/display/keyart/hero"},"content_logo_polychromatic":{"url":"https://cdn.example.invalid/color-logo.png"},"content_logo_monochromatic":{"url":"https://cdn.example.invalid/white-logo.png"}}}""")
        val show=ConnectedCatalogue.mapSeries(body)!!
        assertEquals("https://cdn.example.invalid/banner.jpg",show.artworkUrl)
        assertTrue(show.heroUrl!!.contains("hero"))
        assertEquals("https://cdn.example.invalid/poster.jpg",show.posterUrl)
        assertEquals("https://cdn.example.invalid/color-logo.png",show.logoUrl)
        assertEquals(show.posterUrl,show.imageUrls(poster=true,hero=true).first())
        assertEquals(show.heroUrl,show.imageUrls(hero=true).first())
    }

    @Test fun `selected spotlight artwork survives browse refresh and partial watchlist metadata`() = runBlocking {
        MockWebServer().use {server ->
            server.start();val repo=ConnectedCatalogue(authenticated(server))
            val cards="""{"data":[{"id":"one","type":"series","title":"One"},{"id":"two","type":"series","title":"Two"}]}"""
            server.reply(cards)
            repo.home()
            assertTrue(server.takeRequest().path!!.contains("/browse"))
            assertEquals(4,server.requestCount)
            server.reply("""{"data":[{"id":"two","type":"series","title":"Two","images":{"poster_tall":[[{"source":"https://cdn.example.invalid/portrait.jpg","width":600}]]},"images_localized":{"content_logo_polychromatic":{"url":"https://cdn.example.invalid/logo.png"}}}]}""")
            repo.featured("two")
            assertTrue(server.takeRequest().path!!.contains("/cms/series/two"))
            repo.featured("two")
            assertEquals(5,server.requestCount)
            server.reply("""{"data":[{"panel":{"id":"two","type":"series","title":"Two"}}]}""")
            val saved=repo.watchlist().single()
            assertEquals("https://cdn.example.invalid/portrait.jpg",saved.posterUrl)
            assertEquals("https://cdn.example.invalid/logo.png",saved.logoUrl)
            server.takeRequest()
            server.reply(cards)
            repo.home()
            assertEquals(saved.posterUrl,repo.show("two")!!.posterUrl)
            assertEquals(saved.logoUrl,repo.show("two")!!.logoUrl)
            assertEquals("one",repo.shows.first().id)
            server.takeRequest()
            server.reply("""{"data":[{"id":"two","type":"series","title":"Two","images_localized":{"content_logo_polychromatic":{"url":"https://cdn.example.invalid/updated-logo.png"}}}]}""")
            repo.featured("two")
            assertEquals("https://cdn.example.invalid/updated-logo.png",repo.show("two")!!.logoUrl)
            assertEquals(saved.posterUrl,repo.show("two")!!.posterUrl)
        }
    }
}
