package dev.cinema.crunchyroll

import dev.cinema.crunchyroll.data.account.*
import dev.cinema.crunchyroll.playback.*
import java.io.IOException
import java.util.Base64
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.MockResponse

class NativePlaybackTests {
    @Test fun `keepalive sends current position in query and multipart body`() = runBlocking {
        MockWebServer().use {server ->
            server.start();server.enqueue(MockResponse().setResponseCode(204))
            CrunchyrollHttp(base=server.url("/"),playbackBase=server.url("/")).keepAlive("v1/token/episode-fixture/token-fixture/keepAlive","access-fixture",30_999)
            val request=server.takeRequest()
            assertEquals("PATCH",request.method)
            assertTrue(request.path!!.endsWith("?playhead=30"))
            assertTrue(request.getHeader("Content-Type")!!.startsWith("multipart/form-data"))
            val body=request.body.readUtf8()
            assertTrue(body.contains("name=\"position\""));assertTrue(body.contains("\r\n30\r\n"))
        }
    }
    @Test fun `localized title logo templates become valid image URLs`() {
        val obj=Json.parseToJsonElement("""{"id":"series","title":"Series","type":"series","images_localized":{"content_logo_monochromatic":{"url":"https://imgsrv.crunchyroll.com/cdn-cgi/image/{:param}/display/keyart/logo","locale":"en-US"},"hero_background":{"url":"https://imgsrv.crunchyroll.com/cdn-cgi/image/{:param}/display/keyart/hero"}}}""")
        val series=ConnectedCatalogue.mapSeries(obj)!!
        assertTrue(series.logoUrl!!.contains("width=640,fit=contain,format=webp"))
        assertFalse(series.logoUrl!!.contains("{:param}"))
        assertTrue(series.artworkUrl!!.contains("width=1280"))
    }
    @Test fun `stream descriptor uses real manifest license subtitles and session lease metadata`() {
        val body=Json.parseToJsonElement("""{"url":"https://cdn.example.invalid/manifest.mpd?signature=fixture","token":"video-token-fixture","audioLocale":"ja-JP","session":{"renewSeconds":270,"usesStreamLimits":true},"drm":{"name":"widevine","drmUrl":"https://www.crunchyroll.com/license/v1/license/widevine?specconform=true"},"subtitles":{"en-US":{"language":"en-US","url":"https://cdn.example.invalid/english.ass","format":"ass"},"none":{"language":"none"}},"versions":[{"guid":"audio-guid","audio_locale":"ja-JP"}]}""").jsonObject
        val stream=NativePlaybackService.parse("episode-fixture",body,PlaybackAuthorization("access-fixture","account-fixture",1))
        assertTrue(stream.manifestUrl.contains("playbackGuid=video-token-fixture"))
        assertTrue(stream.manifestUrl.contains("accountid=account-fixture"))
        assertEquals(1,stream.subtitles.size)
        assertEquals("ass",stream.subtitles.first().format)
        assertEquals(270,stream.renewSeconds)
        assertEquals("ja-JP",stream.audio.first().language)
        assertFalse(stream.toString().contains("video-token-fixture"))
    }
    @Test fun `license transport accepts binary and JSON wrapped platform licenses`() {
        val binary=byteArrayOf(8,18,24,42)
        assertArrayEquals(binary,NativeWidevineCallback.decodeLicenseEnvelope(binary))
        val wrapped="""{"license":"${Base64.getEncoder().encodeToString(binary)}"}""".toByteArray()
        assertArrayEquals(binary,NativeWidevineCallback.decodeLicenseEnvelope(wrapped))
        try {NativeWidevineCallback.decodeLicenseEnvelope("""{"error":"private-license-fixture"}""".toByteArray());fail("Expected invalid license response")}
        catch(e: IOException) {assertFalse(e.message.orEmpty().contains("private-license-fixture"))}
    }
}
