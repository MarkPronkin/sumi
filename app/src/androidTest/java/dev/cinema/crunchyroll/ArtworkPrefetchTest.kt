package dev.cinema.crunchyroll

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.decode.DataSource
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.size.Precision
import dev.cinema.crunchyroll.data.account.*
import dev.cinema.crunchyroll.data.spotlightNeighbours
import dev.cinema.crunchyroll.ui.ArtworkPrefetcher
import dev.cinema.crunchyroll.ui.PrefetchSpotlight
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(DelicateCoilApi::class)
class ArtworkPrefetchTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val base = "https://artwork.invalid/${UUID.randomUUID()}"
    private val jpeg by lazy { context.resources.openRawResource(R.drawable.solo).use { it.readBytes() } }

    private fun Request.respond(body: ResponseBody) =
        Response.Builder().request(this).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(body).build()

    @Test fun carouselWarmsNextBannerAndLogoBeforeSelectionOnMeteredNetwork() {
        val downloads = CopyOnWriteArrayList<String>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            if (request.url.host == "artwork.invalid") {
                downloads.add(request.url.toString())
                request.respond(jpeg.toResponseBody("image/jpeg".toMediaType()))
            } else {
                val json = when (request.url.encodedPath) {
                    "/auth/v1/token" -> """{"access_token":"fixture","refresh_token":"fixture","account_id":"fixture","expires_in":3600}"""
                    "/content/v2/discover/browse" -> """{"data":[{"id":"a","title":"A"},{"id":"b","title":"B"},{"id":"c","title":"C"}]}"""
                    "/content/v2/cms/series/b" -> """{"id":"b","title":"B","images_localized":{"hero_background":{"url":"$base/banner"},"content_logo_polychromatic":{"url":"$base/logo"}}}"""
                    else -> "{}"
                }
                request.respond(json.toResponseBody("application/json".toMediaType()))
            }
        }.build()
        val vault = object : TokenVault {
            override suspend fun read(): StoredSession? = null
            override suspend fun write(session: StoredSession) {}
            override suspend fun clear() {}
        }
        val session = AccountSession(CrunchyrollHttp(client = client), vault)
        val repo = ConnectedCatalogue(session)
        val shows = runBlocking { session.login("fixture", "fixture"); repo.home() }
        val original = SingletonImageLoader.get(context)
        val loader = artworkImageLoader(context) { client }
        SingletonImageLoader.setUnsafe(loader)
        val enabled = mutableStateOf(true)
        try {
            val prefetcher = ArtworkPrefetcher(context) { allowMetered -> allowMetered }
            compose.setContent {
                if (enabled.value) PrefetchSpotlight(repo, prefetcher, spotlightNeighbours(shows, "a", 1), 600)
            }
            compose.waitUntil(10_000) {
                loader.memoryCache?.keys?.map { it.key }?.containsAll(listOf("$base/banner", "$base/logo")) == true
            }
            compose.runOnIdle { enabled.value = false }
            for (url in listOf("$base/banner", "$base/logo")) {
                val result = runBlocking {
                    loader.execute(ImageRequest.Builder(context).data(url).size(300, 150).precision(Precision.INEXACT).build())
                }
                assertEquals(DataSource.MEMORY_CACHE, (result as SuccessResult).dataSource)
            }
            assertEquals(listOf("$base/banner", "$base/logo"), downloads.toList())
        } finally {
            compose.runOnIdle { enabled.value = false }
            SingletonImageLoader.setUnsafe(original)
            loader.shutdown()
        }
    }

    @Test fun visiblePosterJoinsLibraryDownloadAndFreshLoaderReadsItFromDisk() = runBlocking {
        val url = "$base/poster"
        val calls = CopyOnWriteArrayList<String>()
        val started = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            calls.add(chain.request().url.toString())
            started.complete(Unit)
            check(release.await(10, TimeUnit.SECONDS))
            chain.request().respond(jpeg.toResponseBody("image/jpeg".toMediaType()))
        }.build()
        val original = SingletonImageLoader.get(context)
        val loader = artworkImageLoader(context) { client }
        SingletonImageLoader.setUnsafe(loader)
        try {
            val prefetcher = ArtworkPrefetcher(context) { true }
            val warm = launch { prefetcher.library(listOf(url)) }
            withTimeout(10_000) { started.await() }
            val request = ImageRequest.Builder(context).data(url).size(240, 360).build()
            val visible = async { loader.execute(request) }
            delay(200)
            release.countDown()
            warm.join()
            assertEquals(DataSource.DISK, (visible.await() as SuccessResult).dataSource)
            val fresh = artworkImageLoader(context) { client }
            try {
                assertEquals(DataSource.DISK, (fresh.execute(request) as SuccessResult).dataSource)
            } finally { fresh.shutdown() }
            assertEquals(listOf(url), calls.toList())
        } finally {
            release.countDown()
            SingletonImageLoader.setUnsafe(original)
            loader.shutdown()
        }
    }

    @Test fun librarySkipsMeteredConnectionsAndExistingFilesAndBoundsEachSession() = runBlocking {
        val urls = (0..30).map { "$base/$it" }
        val calls = CopyOnWriteArrayList<String>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            calls.add(chain.request().url.toString())
            val response = chain.request().respond(jpeg.toResponseBody("image/jpeg".toMediaType()))
            if (chain.request().url.toString() == urls[25]) response.newBuilder().code(429).build() else response
        }.build()
        val original = SingletonImageLoader.get(context)
        val loader = artworkImageLoader(context) { client }
        SingletonImageLoader.setUnsafe(loader)
        try {
            var unmetered = false
            val prefetcher = ArtworkPrefetcher(context) { allowMetered -> allowMetered || unmetered }
            prefetcher.library(urls)
            assertTrue(calls.isEmpty())
            loader.execute(ImageRequest.Builder(context).data(urls.first()).size(60, 90).build())
            unmetered = true
            prefetcher.library(urls)
            prefetcher.library(urls)
            assertEquals(urls.take(25), calls.toList()) // One displayed poster plus 24 speculative misses.
            assertFalse(loader.memoryCache!!.keys.any { it.key == urls[1] })

            val nextSession = ArtworkPrefetcher(context) { true }
            nextSession.library(urls)
            nextSession.library(urls)
            assertEquals(urls.take(26), calls.toList()) // Resume at the first miss and back off on the 429.
        } finally {
            SingletonImageLoader.setUnsafe(original)
            loader.shutdown()
        }
    }
}
