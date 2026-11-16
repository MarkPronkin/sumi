package dev.cinema.crunchyroll

import dev.cinema.crunchyroll.data.account.CrunchyrollHttp
import dev.cinema.crunchyroll.playback.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class SkipEventTests {
    @Test fun `provider seconds become ordered recap intro and titles segments`() {
        val segments=SkipEventService.parse(Json.parseToJsonElement("""{
            "credits":{"start":1200.125,"end":"1290.5"},
            "intro":{"start":60.001,"end":150.75},
            "recap":{"start":0,"end":30},
            "preview":{"start":1290.5,"end":1320}
        }"""))
        assertEquals(listOf(SkipKind.Recap,SkipKind.Intro,SkipKind.Credits),segments.map {it.kind})
        assertEquals(60_001,segments[1].startMs)
        assertEquals(150_750,segments[1].endMs)
        assertEquals(1_290_500,segments[2].endMs)
    }

    @Test fun `invalid markers are ignored independently without guessing timestamps`() {
        val bodies=listOf("""{"intro":{"start":-1,"end":20}}""",
            """{"intro":{"start":20,"end":20},"credits":{"start":40,"end":30}}""",
            """{"intro":{"start":0,"end":"NaN"},"credits":{"start":0,"end":"Infinity"}}""",
            """{"intro":{"start":0,"end":1e300}}""", """{"intro":[],"recap":null}""", "[]", "null")
        bodies.forEach {assertTrue(SkipEventService.parse(Json.parseToJsonElement(it)).isEmpty())}
        assertEquals(listOf(SkipKind.Credits),SkipEventService.parse(Json.parseToJsonElement(
            """{"intro":{"start":"invalid","end":90},"credits":{"start":1200,"end":1290}}""")).map {it.kind})
    }

    @Test fun `skip visibility includes the start excludes the end and respects stream duration`() {
        val segment=SkipSegment(SkipKind.Intro,10_000,100_000)
        assertFalse(segment.contains(9_999,200_000))
        assertTrue(segment.contains(10_000,200_000))
        assertTrue(segment.contains(99_999,200_000))
        assertFalse(segment.contains(100_000,200_000))
        assertFalse(segment.contains(0,0))
        assertTrue(segment.contains(49_999,50_000))
        assertFalse(segment.contains(50_000,50_000))
        assertFalse(segment.contains(10_000,5_000))
    }

    @Test fun `modern markers are requested without account headers`() = runBlocking {
        MockWebServer().use {server ->
            server.start()
            server.enqueue(MockResponse().setBody("""{"intro":{"start":10.5,"end":100.5}}"""))
            val service=SkipEventService(CrunchyrollHttp(staticBase=server.url("/")))
            assertEquals(100_500,service.load("episode-fixture").single().endMs)
            val request=server.takeRequest()
            assertEquals("/skip-events/production/episode-fixture.json",request.path)
            assertNull(request.getHeader("Authorization"))
            assertEquals(1,server.requestCount)
        }
    }

    @Test fun `missing modern metadata falls back to legacy intro timestamps`() = runBlocking {
        MockWebServer().use {server ->
            server.start()
            server.enqueue(MockResponse().setResponseCode(404))
            server.enqueue(MockResponse().setBody("""{"startTime":12.25,"endTime":102.75}"""))
            val service=SkipEventService(CrunchyrollHttp(staticBase=server.url("/")))
            assertEquals(SkipSegment(SkipKind.Intro,12_250,102_750),service.load("episode-fixture").single())
            server.takeRequest()
            val request=server.takeRequest()
            assertEquals("/datalab-intro-v2/episode-fixture.json",request.path)
            assertNull(request.getHeader("Authorization"))
        }
    }

    @Test fun `malformed and unavailable metadata leave playback without skip markers`() = runBlocking {
        MockWebServer().use {server ->
            server.start()
            server.enqueue(MockResponse().setBody("not-json"))
            server.enqueue(MockResponse().setResponseCode(503))
            assertTrue(SkipEventService(CrunchyrollHttp(staticBase=server.url("/"))).load("episode-fixture").isEmpty())
        }
    }

    @Test fun `leaving an episode cancels marker fetching instead of starting fallback requests`() = runBlocking {
        MockWebServer().use {server ->
            server.start()
            server.enqueue(MockResponse().setBody("{}").setBodyDelay(1,TimeUnit.SECONDS))
            val service=SkipEventService(CrunchyrollHttp(staticBase=server.url("/")))
            try {withTimeout(200) {service.load("episode-fixture")};fail("Expected cancellation")}
            catch(_: TimeoutCancellationException) { /* Parent playback cancellation must propagate. */ }
            assertEquals(1,server.requestCount)
        }
    }
}
