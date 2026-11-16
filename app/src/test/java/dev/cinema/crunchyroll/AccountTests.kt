package dev.cinema.crunchyroll

import dev.cinema.crunchyroll.data.account.*
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.CountDownLatch
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test

class AccountTests {
    private class MemoryVault: TokenVault {
        var value: StoredSession?=null
        var fail=false
        override suspend fun read()=value
        override suspend fun write(session: StoredSession) {if(fail) throw IOException();value=session}
        override suspend fun clear() {value=null}
    }
    private fun token(access: String="access-fixture",refresh: String="refresh-fixture")="""{"access_token":"$access","refresh_token":"$refresh","account_id":"account-fixture","profile_id":"profile-fixture","expires_in":3600}"""
    private fun MockWebServer.reply(body: String,status: Int=200) {enqueue(MockResponse().setResponseCode(status).setHeader("Content-Type","application/json").setBody(body))}
    private fun MockWebServer.loginResponses() {
        reply(token());reply("""{"profile_id":"profile-fixture","profile_name":"Anime night"}""")
        reply("""{"profiles":[{"profile_id":"profile-fixture","profile_name":"Anime night"}]}""")
    }

    @Test fun `password login persists refresh only and authenticated requests use bearer token`() = runBlocking {
        MockWebServer().use {server ->
            server.start();server.loginResponses()
            val vault=MemoryVault();val session=AccountSession(CrunchyrollHttp(server.url("/")),vault)
            session.login("viewer@example.invalid","test-password-fixture")
            val summary=(session.state.value as AccountState.SignedIn).account
            assertEquals("Anime night",summary.name)
            assertEquals("refresh-fixture",vault.value?.refreshToken)
            val request=server.takeRequest()
            assertEquals("/auth/v1/token",request.path)
            assertEquals(NativeClientProtocol.SIGN_IN_USER_AGENT,request.getHeader("User-Agent"))
            val form=request.body.readUtf8()
            assertTrue(form.contains("grant_type=password"))
            assertTrue(form.contains("username=viewer%40example.invalid"))
            assertFalse(vault.value.toString().contains("refresh-fixture"))
            repeat(2) {val api=server.takeRequest();assertEquals("Bearer access-fixture",api.getHeader("Authorization"));assertEquals(NativeClientProtocol.USER_AGENT,api.getHeader("User-Agent"))}
            server.reply("""{"data":[]}""")
            session.request("content/v2/discover/browse")
            assertEquals("Bearer access-fixture",server.takeRequest().getHeader("Authorization"))
            session.logout();assertNull(vault.value);assertTrue(session.state.value is AccountState.SignedOut)
        }
    }

    @Test fun `rejected login never creates a saved session or exposes the server body`() = runBlocking {
        MockWebServer().use {server ->
            server.start();server.reply("""{"error":"invalid_grant","detail":"private-server-detail"}""",400)
            val vault=MemoryVault();val session=AccountSession(CrunchyrollHttp(server.url("/")),vault)
            session.login("viewer@example.invalid","incorrect-test-fixture")
            assertNull(vault.value)
            val message=(session.state.value as AccountState.SignedOut).message.orEmpty()
            assertTrue(message.contains("rejected"));assertFalse(message.contains("private-server-detail"))
        }
    }

    @Test fun `secure persistence failure prevents signed-in success`() = runBlocking {
        MockWebServer().use {server ->
            server.start();server.reply(token())
            val vault=MemoryVault().apply {fail=true}
            val session=AccountSession(CrunchyrollHttp(server.url("/")),vault)
            session.login("viewer@example.invalid","fixture")
            assertTrue(session.state.value is AccountState.SignedOut);assertNull(vault.value)
        }
    }

    @Test fun `restoration rotates the refresh token and keeps device and profile identity`() = runBlocking {
        MockWebServer().use {server ->
            server.start();server.reply(token(refresh="rotated-fixture"));server.reply("{}");server.reply("{}")
            val vault=MemoryVault().apply {value=StoredSession("old-fixture","device-fixture","account-fixture","profile-fixture")}
            val session=AccountSession(CrunchyrollHttp(server.url("/")),vault)
            session.restore()
            assertTrue(session.state.value is AccountState.SignedIn)
            val form=server.takeRequest().body.readUtf8()
            assertTrue(form.contains("grant_type=refresh_token_profile_id"));assertTrue(form.contains("device_id=device-fixture"))
            assertEquals("rotated-fixture",vault.value?.refreshToken)
            assertEquals("profile-fixture",vault.value?.profileId)
        }
    }

    @Test fun `password endpoint requires sign-in headers while refresh retains native compatibility headers`() = runBlocking {
        MockWebServer().use {server ->
            server.dispatcher=object: Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val password=request.body.readUtf8().contains("grant_type=password")
                    val expected=if(password) NativeClientProtocol.SIGN_IN_USER_AGENT else NativeClientProtocol.USER_AGENT
                    return if(request.getHeader("User-Agent")==expected) MockResponse().setBody(token())
                    else MockResponse().setResponseCode(401).setBody("""{"error":"invalid_grant","code":"auth.obtain_access_token.invalid_credentials"}""")
                }
            }
            server.start();val http=CrunchyrollHttp(server.url("/"))
            assertEquals("access-fixture",http.token(mapOf("grant_type" to "password","username" to "viewer@example.invalid","password" to "fixture"),"device-fixture").accessToken)
            assertEquals("access-fixture",http.token(mapOf("grant_type" to "refresh_token","refresh_token" to "refresh-fixture"),"device-fixture").accessToken)
        }
    }

    @Test fun `login retries reuse installation identity without retaining failed credentials`() = runBlocking {
        MockWebServer().use {server ->
            server.start();server.reply("""{"error":"invalid_grant"}""",401)
            server.loginResponses()
            val vault=MemoryVault();val session=AccountSession(CrunchyrollHttp(server.url("/")),vault)
            session.login("viewer@example.invalid","incorrect-fixture")
            assertNull(vault.value)
            val first=server.takeRequest().body.readUtf8().substringAfter("device_id=").substringBefore('&')
            session.login("viewer@example.invalid","valid-fixture")
            val second=server.takeRequest().body.readUtf8().substringAfter("device_id=").substringBefore('&')
            assertEquals(first,second)
            assertTrue(session.state.value is AccountState.SignedIn)
        }
    }

    @Test fun `parallel unauthorized calls share a single token refresh`() = runBlocking {
        MockWebServer().use {server ->
            val grants=AtomicInteger()
            server.dispatcher=object: Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = when(request.path) {
                    "/auth/v1/token" -> MockResponse().setBody(token(access="access-${grants.incrementAndGet()}"))
                    "/accounts/v1/me/profile","/accounts/v1/me/multiprofile" -> MockResponse().setBody("{}")
                    else -> if(request.getHeader("Authorization")=="Bearer access-1") MockResponse().setResponseCode(401).setBody("{}") else MockResponse().setBody("{}").setResponseCode(200)
                }
            }
            server.start();val session=AccountSession(CrunchyrollHttp(server.url("/")),MemoryVault())
            session.login("viewer@example.invalid","fixture")
            listOf(async {session.request("catalogue")},async {session.request("catalogue")},async {session.request("catalogue")}).awaitAll()
            assertEquals(2,grants.get())
        }
    }

    @Test fun `cancelled sign-in cannot become signed in later`() = runBlocking {
        MockWebServer().use {server ->
            server.start();server.enqueue(MockResponse().setBody(token()).setHeadersDelay(2,TimeUnit.SECONDS))
            val vault=MemoryVault();val session=AccountSession(CrunchyrollHttp(server.url("/")),vault)
            val job=launch {session.login("viewer@example.invalid","fixture")}
            withContext(Dispatchers.IO) {server.takeRequest(3,TimeUnit.SECONDS)}
            job.cancelAndJoin()
            assertTrue(session.state.value is AccountState.SignedOut);assertNull(vault.value)
        }
    }

    @Test fun `catalogue mapper preserves service images and never invents languages`() {
        val item=Json.parseToJsonElement("""{"id":"show-fixture","title":"Fixture series","type":"series","description":"A description","series_metadata":{"season_count":2,"series_launch_year":2024,"audio_locales":["ja-JP"]},"images":{"poster_wide":[[{"source":"https://example.invalid/small.jpg","width":640},{"source":"https://example.invalid/large.jpg","width":1280}]],"poster_tall":[[{"source":"https://example.invalid/poster.jpg","width":600}]]}}""")
        val show=ConnectedCatalogue.mapSeries(item)!!
        assertEquals("https://example.invalid/large.jpg",show.artworkUrl)
        assertEquals(listOf("Japanese"),show.languages)
        assertEquals(2,show.seasons)
        assertNull(ConnectedCatalogue.mapSeries(Json.parseToJsonElement("""{"id":"chapter","type":"episode","title":"Not a series"}""")))
    }

    @Test fun `fresh password login cannot inherit the previous account profile`() = runBlocking {
        MockWebServer().use {server ->
            server.start();server.reply(token());server.reply("{}");server.reply("{}")
            val vault=MemoryVault().apply {value=StoredSession("old","old-device","account-fixture","profile-fixture")}
            val session=AccountSession(CrunchyrollHttp(server.url("/")),vault)
            session.restore()
            server.reply("""{"access_token":"new-access","refresh_token":"new-refresh","account_id":"different-account","expires_in":3600}""")
            server.reply("{}");server.reply("{}")
            session.login("different@example.invalid","fixture")
            assertEquals("different-account",vault.value?.accountId)
            assertNull(vault.value?.profileId)
            assertNotEquals("old-device",vault.value?.deviceId)
        }
    }

    @Test fun `expired session rejected during proactive refresh returns to sign in`() = runBlocking {
        MockWebServer().use {server ->
            server.start();server.loginResponses()
            val vault=MemoryVault();var clock=0L
            val session=AccountSession(CrunchyrollHttp(server.url("/")),vault) {clock}
            session.login("viewer@example.invalid","fixture")
            clock=4_000_000
            server.reply("""{"error":"invalid_grant"}""",400)
            try {session.request("catalogue");fail("Expected rejected refresh")} catch(_: ServiceException) {}
            assertTrue(session.state.value is AccountState.SignedOut)
            assertNull(vault.value)
        }
    }

    @Test fun `connected watchlist uses account API and commits actual mutations`() = runBlocking {
        MockWebServer().use {server ->
            server.start();server.loginResponses()
            val session=AccountSession(CrunchyrollHttp(server.url("/")),MemoryVault())
            session.login("viewer@example.invalid","fixture")
            repeat(3) {server.takeRequest()}
            val repo=ConnectedCatalogue(session)
            server.reply("""{"data":[{"panel":{"id":"live-series","title":"Live fixture","type":"series"}}]}""")
            assertEquals("live-series",repo.watchlist().first().id)
            assertTrue(server.takeRequest().path!!.startsWith("/content/v2/discover/account-fixture/watchlist"))
            server.reply("",204);repo.setWatchlist("live-series",false)
            val removed=server.takeRequest();assertEquals("DELETE",removed.method)
            assertTrue(removed.path!!.startsWith("/content/v2/account-fixture/watchlist/live-series"))
            server.reply("{}",200);repo.setWatchlist("live-series",true)
            val added=server.takeRequest();assertEquals("POST",added.method)
            assertEquals("live-series",Json.parseToJsonElement(added.body.readUtf8()).jsonObject["content_id"]?.jsonPrimitive?.content)
        }
    }

    @Test fun `unauthorized request cannot retry against a newly selected profile`() = runBlocking {
        MockWebServer().use {server ->
            val started=CountDownLatch(1);val release=CountDownLatch(1);val catalogueCalls=AtomicInteger()
            server.dispatcher=object: Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = when {
                    request.path=="/auth/v1/token" -> {
                        val switching=request.body.readUtf8().contains("profile_id=guest-fixture")
                        MockResponse().setBody(if(switching) token(access="guest-access").replace("profile-fixture","guest-fixture") else token())
                    }
                    request.path=="/accounts/v1/me/profile" -> MockResponse().setBody("""{"profile_id":"profile-fixture","profile_name":"Main"}""")
                    request.path=="/accounts/v1/me/multiprofile" -> MockResponse().setBody("""{"profiles":[{"profile_id":"profile-fixture","profile_name":"Main"},{"profile_id":"guest-fixture","profile_name":"Guest"}]}""")
                    else -> {catalogueCalls.incrementAndGet();started.countDown();release.await(5,TimeUnit.SECONDS);MockResponse().setResponseCode(401).setBody("{}")}
                }
            }
            server.start();val session=AccountSession(CrunchyrollHttp(server.url("/")),MemoryVault())
            session.login("viewer@example.invalid","fixture")
            val oldRequest=async {runCatching {session.request("catalogue")}}
            assertTrue(withContext(Dispatchers.IO) {started.await(3,TimeUnit.SECONDS)})
            session.switchProfile("guest-fixture");release.countDown()
            assertEquals(409,(oldRequest.await().exceptionOrNull() as? ServiceException)?.status)
            assertEquals(1,catalogueCalls.get())
        }
    }
}
