package dev.cinema.crunchyroll

import dev.cinema.crunchyroll.updates.GitHubReleases
import dev.cinema.crunchyroll.updates.UpdateException
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GitHubReleasesTests {
    @get:Rule val server = MockWebServer()
    @get:Rule val folder = TemporaryFolder()

    private val releases = GitHubReleases(OkHttpClient.Builder().addInterceptor { chain ->
        val request = chain.request()
        val url = server.url(request.url.encodedPath).newBuilder().encodedQuery(request.url.encodedQuery).build()
        chain.proceed(request.newBuilder().url(url).build())
    }.build())

    @Test
    fun latestReleaseUsesNumericVersionOrderAndPrefersTheUniversalApk() = runBlocking {
        release("v0.7.10", """${asset("split.apk")},${asset("sumi.apk", "sha256:$abcSha256")}""")

        val update = requireNotNull(releases.latestUpdate("0.7.9"))
        assertEquals("0.7.10", update.version)
        assertEquals("https://github.com/MarkPronkin/sumi/releases/download/v0.7.10/sumi.apk", update.url.toString())
        assertEquals(abcSha256, update.sha256)

        val request = server.takeRequest()
        assertEquals("/repos/MarkPronkin/sumi/releases/latest", request.path)
        assertNull(request.getHeader("Authorization"))
    }

    @Test
    fun currentAndOlderReleasesDoNotOfferAnUpdate() = runBlocking {
        release("v0.7.4", "")
        release("v0.7.3", "")

        assertNull(releases.latestUpdate("0.7.4"))
        assertNull(releases.latestUpdate("0.7.4"))
    }

    @Test
    fun alphaCanUpdateToTheStableReleaseOfTheSameVersion() = runBlocking {
        release("v0.1.0", asset("sumi.apk"))

        assertEquals("0.1.0", releases.latestUpdate("0.1.0-alpha")?.version)
    }

    @Test
    fun missingStableReleaseOffersTheHighestPublishedAlphaOrBeta() = runBlocking {
        val page = listOf(
            prerelease("v0.2.0-beta.2"),
            prerelease("v0.3.0-alpha", draft = true),
            prerelease("v0.3.0-rc.1"),
            prerelease("v0.2.0-beta.10"),
            prerelease("v0.2.0-alpha.11"),
        )
        noStableRelease(page)

        val update = requireNotNull(releases.latestUpdate("0.2.0-alpha.12"))
        assertEquals("0.2.0-beta.10", update.version)
        assertEquals("https://github.com/MarkPronkin/sumi/releases/download/v0.2.0-beta.10/sumi.apk", update.url.toString())
        assertEquals("/repos/MarkPronkin/sumi/releases/latest", server.takeRequest().path)
        assertEquals("/repos/MarkPronkin/sumi/releases?per_page=100&page=1", server.takeRequest().path)
    }

    @Test
    fun fallbackNeverOffersTheInstalledVersionOrADowngrade() = runBlocking {
        for (installed in listOf("0.2.0-beta", "0.2.0-beta.1", "0.2.0")) {
            noStableRelease(listOf(prerelease("v0.2.0-beta"), prerelease("v0.1.0-alpha")))
            assertNull(releases.latestUpdate(installed))
        }
    }

    @Test
    fun fallbackChecksLaterPagesAndCanSelectAnAlpha() = runBlocking {
        noStableRelease((1..100).map { prerelease("v0.1.0-beta.$it") })
        server.enqueue(MockResponse().setBody("[${prerelease("v0.2.0-alpha")}]"))

        assertEquals("0.2.0-alpha", releases.latestUpdate("0.1.0-beta.100")?.version)
    }

    @Test
    fun missingPublicReleaseAndAmbiguousApksReportDistinctErrors() = runBlocking {
        noStableRelease(emptyList())
        release("v0.7.5", """${asset("phone.apk")},${asset("tv.apk")}""")

        assertEquals(R.string.update_no_public_release, updateError { releases.latestUpdate("0.7.4") })
        assertEquals(R.string.update_no_apk, updateError { releases.latestUpdate("0.7.4") })
    }

    @Test
    fun downloadChecksBothAssetLengthAndSha256() = runBlocking {
        release("v0.7.5", asset("sumi-0.7.5.apk", "sha256:$abcSha256"))
        val update = requireNotNull(releases.latestUpdate("0.7.4"))
        val destination = folder.newFile()
        val progress = mutableListOf<Int>()

        server.enqueue(MockResponse().setBody("abc"))
        releases.download(update, destination, progress::add)
        assertEquals("abc", destination.readText())
        assertEquals(100, progress.last())

        server.enqueue(MockResponse().setBody("abd"))
        assertEquals(R.string.update_corrupt_download, updateError { releases.download(update, destination) {} })

        server.enqueue(MockResponse().setBody("ab"))
        assertEquals(R.string.update_corrupt_download, updateError {
            releases.download(update.copy(sha256 = null), destination) {}
        })
    }

    private fun release(tag: String, assets: String) {
        server.enqueue(MockResponse().setBody("""{"tag_name":"$tag","assets":[$assets]}"""))
    }

    private fun noStableRelease(firstPage: List<String>) {
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setBody(firstPage.joinToString(prefix = "[", postfix = "]")))
    }

    private fun prerelease(tag: String, draft: Boolean = false): String =
        """{"tag_name":"$tag","prerelease":true,"draft":$draft,"assets":[${asset("sumi.apk")}]}"""

    private fun asset(name: String, digest: String? = null): String {
        val digestJson = digest?.let { """"$it"""" } ?: "null"
        return """{"name":"$name","state":"uploaded","size":3,"digest":$digestJson}"""
    }

    private suspend fun updateError(block: suspend () -> Unit): Int {
        try {
            block()
        } catch (error: UpdateException) {
            return error.messageId
        }
        throw AssertionError("Expected an update error")
    }

    private companion object {
        const val abcSha256 = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
    }
}
