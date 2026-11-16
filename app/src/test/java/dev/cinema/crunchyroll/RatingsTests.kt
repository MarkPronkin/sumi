package dev.cinema.crunchyroll

import dev.cinema.crunchyroll.data.*
import dev.cinema.crunchyroll.data.account.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale
import java.util.concurrent.TimeUnit

class RatingsTests {
    private val show=Anime("series-fixture","Frieren: Beyond Journey’s End","","",2023,emptyList(),0)
    private fun MockWebServer.reply(body: String)=enqueue(MockResponse().setBody(body).setHeader("Content-Type","application/json"))
    private fun MockWebServer.repository(now: ()->Long=System::currentTimeMillis)=RatingsRepository(url("/mal/"),url("/cinemeta/"),now=now)
    private fun json(body: String)=Json.parseToJsonElement(body)
    private fun RatingState.value()=(this as RatingState.Available).rating

    @Test fun `Crunchyroll uses community average instead of the viewers vote`() {
        val rating=RatingsRepository.parseCrunchyroll(json("""{"average":"4.9","total":12000,"rating":"1s"}""")).value()
        assertEquals(4.9,rating.score!!,0.001)
        assertEquals("4.9",rating.formatted(RatingSource.Crunchyroll))
        assertEquals(RatingState.Unavailable,RatingsRepository.parseCrunchyroll(json("""{"average":"4.9","total":0}""")))
    }

    @Test fun `invalid or missing community scores never become zero ratings`() {
        listOf("null","[]","{}","""{"rating":"5s"}""","""{"average":"NaN"}""",
            """{"average":"Infinity"}""","""{"average":6}""","""{"average":-1}""","""{"average":0}""").forEach {
            assertEquals(it,RatingState.Unavailable,RatingsRepository.parseCrunchyroll(json(it)))
        }
    }

    @Test fun `MAL uses the exact English alias and original year with actual score and rank`() = runBlocking {
        MockWebServer().use {server ->
            server.start()
            server.reply("""{"data":[
                {"mal_id":60000,"title":"Frieren: Beyond Journey's End Season 2","year":2026,"type":"TV","score":9.8,"rank":1},
                {"mal_id":52991,"title":"Sousou no Frieren","titles":[{"type":"English","title":"Frieren: Beyond Journey's End"}],"year":2023,"type":"TV","score":9.31,"rank":1,"rating":"PG-13"}
            ]}""")
            val rating=server.repository().load(show,RatingSource.MyAnimeList).value()
            assertEquals(9.31,rating.score!!,0.001);assertEquals(1,rating.rank)
            val request=server.takeRequest()
            assertEquals("/mal/anime",request.requestUrl!!.encodedPath)
            assertEquals(show.title,request.requestUrl!!.queryParameter("q"))
            assertNull(request.getHeader("Authorization"));assertNull(request.getHeader("Cookie"))
        }
    }

    @Test fun `default MAL lookup uses the active Tenrai service instead of the retired Jikan host`() = runBlocking {
        val client=OkHttpClient.Builder().addInterceptor {chain ->
            val request=chain.request()
            assertEquals("api.tenrai.org",request.url.host)
            assertEquals("/v1/anime",request.url.encodedPath)
            assertEquals(show.title,request.url.queryParameter("q"))
            assertEquals("25",request.url.queryParameter("limit"))
            assertNull(request.header("Authorization"));assertNull(request.header("Cookie"))
            // Actual Tenrai response fields, trimmed to the fields used by the app.
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""{"data":[{"mal_id":52991,"title":"Sousou no Frieren","title_english":"Frieren: Beyond Journey's End","year":2023,"type":"TV","score":9.24,"rank":1}]}""".toResponseBody()).build()
        }.build()
        val rating=RatingsRepository(client=client).load(show,RatingSource.MyAnimeList).value()
        assertEquals(9.24,rating.score!!,0.001)
        assertEquals(1,rating.rank)
    }

    @Test fun `ambiguous anime and remakes with a different year are not assigned a rating`() = runBlocking {
        MockWebServer().use {server ->
            server.start()
            server.reply("""{"data":[{"mal_id":1,"title":"Frieren: Beyond Journey's End","year":2023,"type":"TV","score":9},{"mal_id":2,"title":"Frieren: Beyond Journey's End","year":2023,"type":"ONA","score":8}]}""")
            assertEquals(RatingState.Unavailable,server.repository().load(show,RatingSource.MyAnimeList))
            server.reply("""{"data":[{"mal_id":1,"title":"Frieren: Beyond Journey's End","year":2024,"type":"TV","score":9}]}""")
            assertEquals(RatingState.Unavailable,server.repository().load(show,RatingSource.MyAnimeList))
        }
    }

    @Test fun `MAL null score and unranked entries stay unavailable`() = runBlocking {
        MockWebServer().use {server ->
            server.start()
            server.reply("""{"data":[{"mal_id":52991,"title":"Frieren: Beyond Journey's End","aired":{"prop":{"from":{"year":2023}}},"type":"TV","score":null,"rank":0,"rating":"R - 17+"}]}""")
            assertEquals(RatingState.Unavailable,server.repository().load(show,RatingSource.MyAnimeList))
        }
    }

    @Test fun `IMDb search is validated by exact title year id and animation metadata`() = runBlocking {
        MockWebServer().use {server ->
            server.start()
            server.reply("""{"metas":[{"id":"tt111","name":"Some other Frieren","releaseInfo":"2023"},{"id":"tt22248376","name":"Frieren: Beyond Journey's End","releaseInfo":"2023–"}]}""")
            server.reply("""{"meta":{"id":"tt22248376","genres":["Animation","Adventure"],"imdbRating":"8.9"}}""")
            assertEquals(8.9,server.repository().load(show,RatingSource.Imdb).value().score!!,0.001)
            val search=server.takeRequest()
            assertEquals("/cinemeta/catalog/series/top/search=${show.title}.json",java.net.URLDecoder.decode(search.requestUrl!!.encodedPath,"UTF-8"))
            assertEquals("/cinemeta/meta/series/tt22248376.json",server.takeRequest().path)
            assertNull(search.getHeader("Authorization"))
        }
    }

    @Test fun `IMDb never accepts a similarly named result or live action adaptation`() = runBlocking {
        MockWebServer().use {server ->
            server.start()
            server.reply("""{"metas":[{"id":"tt111","name":"Frieren Season 2","releaseInfo":"2023"}]}""")
            assertEquals(RatingState.Unavailable,server.repository().load(show,RatingSource.Imdb))
            server.reply("""{"metas":[{"id":"tt222","name":"Frieren: Beyond Journey's End","releaseInfo":"2023"}]}""")
            server.reply("""{"meta":{"id":"tt222","genres":["Drama"],"imdbRating":"9.9"}}""")
            assertEquals(RatingState.Unavailable,server.repository().load(show,RatingSource.Imdb))
        }
    }

    @Test fun `successful scores are cached and refreshed after expiration`() = runBlocking {
        MockWebServer().use {server ->
            server.start();var time=0L;val repo=server.repository {time}
            val body="""{"data":[{"mal_id":1,"title":"Frieren: Beyond Journey's End","year":2023,"type":"TV","score":9.2,"rank":2}]}"""
            server.reply(body)
            val first=repo.load(show,RatingSource.MyAnimeList)
            assertEquals(first,repo.load(show,RatingSource.MyAnimeList));assertEquals(1,server.requestCount)
            time=6*60*60_000L+1
            server.reply(body.replace("9.2","9.3"))
            assertEquals(9.3,repo.load(show,RatingSource.MyAnimeList).value().score!!,0.001)
            assertEquals(2,server.requestCount)
        }
    }

    @Test fun `provider failure stays retryable and other scores still load`() = runBlocking {
        MockWebServer().use {server ->
            server.start();val repo=server.repository()
            server.enqueue(MockResponse().setResponseCode(429))
            assertEquals(RatingState.Error,repo.load(show,RatingSource.MyAnimeList))
            assertEquals(4.8,repo.load(show,RatingSource.Crunchyroll) {json("""{"average":4.8,"total":42}""")}.value().score!!,0.001)
            server.reply("""{"data":[{"mal_id":1,"title":"Frieren: Beyond Journey's End","year":2023,"type":"TV","score":8.5,"rank":100}]}""")
            assertEquals(8.5,repo.load(show,RatingSource.MyAnimeList).value().score!!,0.001)
        }
    }

    @Test fun `malformed successful responses expose retry instead of claiming no rating exists`() = runBlocking {
        MockWebServer().use {server ->
            server.start();val repo=server.repository()
            server.reply("not-json");assertEquals(RatingState.Error,repo.load(show,RatingSource.MyAnimeList))
            server.reply("{}");assertEquals(RatingState.Error,repo.load(show,RatingSource.Imdb))
            server.reply("""{"metas":[]}""");assertEquals(RatingState.Unavailable,repo.load(show,RatingSource.Imdb))
        }
    }

    @Test fun `leaving a show cancels its in-flight lookup and does not cache the cancellation`() = runBlocking {
        MockWebServer().use {server ->
            server.start();val repo=server.repository()
            server.reply("""{"metas":[{"id":"tt222","name":"Frieren: Beyond Journey's End","releaseInfo":"2023"}]}""")
            server.enqueue(MockResponse().setBody("""{"meta":{"id":"tt222","genres":["Animation"],"imdbRating":"8.9"}}""").setBodyDelay(1,TimeUnit.SECONDS))
            try {withTimeout(300) {repo.load(show,RatingSource.Imdb)};fail("Expected cancellation")}
            catch(_: TimeoutCancellationException) { /* Cancellation must propagate to OkHttp. */ }
            assertEquals(2,server.requestCount)
            server.reply("""{"metas":[]}""")
            assertEquals(RatingState.Unavailable,repo.load(show,RatingSource.Imdb))
        }
    }

    @Test fun `movie listings use movie searches and never match a television series`() = runBlocking {
        MockWebServer().use {server ->
            server.start()
            server.reply("""{"metas":[]}""")
            assertEquals(RatingState.Unavailable,server.repository().load(show.copy(isMovie=true),RatingSource.Imdb))
            assertTrue(server.takeRequest().path!!.startsWith("/cinemeta/catalog/movie/"))
        }
    }

    @Test fun `scores retain source precision regardless of device locale`() {
        val old=Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals("9.31",ShowRating(9.31,1).formatted(RatingSource.MyAnimeList))
            assertEquals("8.9",ShowRating(8.94).formatted(RatingSource.Imdb))
        } finally {Locale.setDefault(old)}
    }

    @Test fun `connected review lookup uses the authenticated account and series type`() = runBlocking {
        MockWebServer().use {server ->
            server.start()
            val vault=object: TokenVault {
                override suspend fun read(): StoredSession?=null
                override suspend fun write(session: StoredSession) {}
                override suspend fun clear() {}
            }
            server.reply("""{"access_token":"access-fixture","refresh_token":"refresh-fixture","account_id":"account-fixture","expires_in":3600}""")
            server.reply("{}");server.reply("{}")
            val session=AccountSession(CrunchyrollHttp(server.url("/")),vault)
            session.login("viewer@example.invalid","fixture");repeat(3) {server.takeRequest()}
            val repo=ConnectedCatalogue(session)
            server.reply("""{"average":"4.9","total":42,"rating":"2s"}""")
            assertEquals(4.9,RatingsRepository.parseCrunchyroll(repo.rating(show)).value().score!!,0.001)
            val request=server.takeRequest()
            assertEquals("/content-reviews/v2/user/account-fixture/rating/series/series-fixture",request.requestUrl!!.encodedPath)
            assertEquals("Bearer access-fixture",request.getHeader("Authorization"))
            server.reply("{}");repo.rating(show.copy(isMovie=true))
            assertTrue(server.takeRequest().path!!.contains("/rating/movie_listing/"))
        }
    }
}
