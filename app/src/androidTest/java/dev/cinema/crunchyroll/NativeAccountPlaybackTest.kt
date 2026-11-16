package dev.cinema.crunchyroll

import android.net.LocalSocket
import android.net.LocalSocketAddress
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import dev.cinema.crunchyroll.data.account.*
import dev.cinema.crunchyroll.playback.PlaybackEngine
import dev.cinema.crunchyroll.ui.CinemaViewModel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Opt-in real-account check. Session material travels over an ADB-local socket, never test arguments or logs. */
class NativeAccountPlaybackTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun licensedEpisodeRendersAndQualityChangesWithoutLeavingTheApp() {
        val credentialSocket=InstrumentationRegistry.getArguments().getString("cinemaCredentialSocket")
        val socketName=credentialSocket ?: InstrumentationRegistry.getArguments().getString("cinemaLiveSocket")
        val restoreOnly=InstrumentationRegistry.getArguments().getString("cinemaRestoreOnly")=="true"
        val keepSession=InstrumentationRegistry.getArguments().getString("cinemaKeepSession")=="true"
        assumeTrue("Live account session not supplied",restoreOnly || !socketName.isNullOrBlank())
        val socket=LocalSocket()
        val vault=KeystoreTokenVault(compose.activity.application)
        var engine: PlaybackEngine?=null
        var cleanupList: (() -> Unit)?=null
        val json=Json {ignoreUnknownKeys=true}
        try {
            if(!restoreOnly) socket.connect(LocalSocketAddress(socketName,LocalSocketAddress.Namespace.ABSTRACT))
            lateinit var vm: CinemaViewModel
            compose.runOnUiThread {vm=ViewModelProvider(compose.activity)[CinemaViewModel::class.java]}
            if(restoreOnly) {
                assertTrue("A saved encrypted session must exist for process-restart verification",runBlocking {vault.read()}!=null)
                try {compose.waitUntil(45_000) {vm.account.value is AccountState.SignedIn}}
                catch(_: Exception) {throw AssertionError("Process restart session restore failed; safe trace: ${vm.authenticationEvents.value}")}
            } else if(credentialSocket!=null) {
                val fields=try {json.parseToJsonElement(socket.inputStream.bufferedReader().readLine()).jsonObject}
                    catch(_: Exception) {throw AssertionError("Couldn’t read secure sign-in test input.")}
                compose.runOnUiThread {vm.logout()}
                compose.waitUntil(15_000) {vm.account.value is AccountState.SignedOut}
                compose.runOnUiThread {vm.openLogin()}
                compose.waitUntil(10_000) {compose.onAllNodesWithContentDescription("Account email").fetchSemanticsNodes().isNotEmpty()}
                try {
                    compose.onNodeWithContentDescription("Account email").performSemanticsAction(SemanticsActions.SetText) {it(AnnotatedString(fields.getValue("email").jsonPrimitive.content))}
                    compose.onNodeWithContentDescription("Account password").performSemanticsAction(SemanticsActions.SetText) {it(AnnotatedString(fields.getValue("password").jsonPrimitive.content))}
                } catch(_: Exception) {throw AssertionError("Couldn’t fill the sign-in screen securely.")}
                compose.onNodeWithContentDescription("Sign in to Crunchyroll").performScrollTo().performClick()
                compose.waitUntil(45_000) {vm.account.value !is AccountState.SigningIn && vm.authenticationEvents.value.any {it.grant=="password"}}
                assertTrue("Fresh UI sign-in failed; safe authentication trace: ${vm.authenticationEvents.value}",vm.account.value is AccountState.SignedIn)
                compose.activityRule.scenario.recreate()
                compose.waitUntil(15_000) {compose.onAllNodesWithContentDescription("Home").fetchSemanticsNodes().isNotEmpty()}
            } else {
                val session=json.decodeFromString<StoredSession>(socket.inputStream.bufferedReader().readLine())
                runBlocking {vault.write(session)}
                compose.runOnUiThread {vm.retrySession()}
                compose.waitUntil(45_000) {vm.account.value is AccountState.SignedIn}
                assertTrue("Native account session must restore",vm.account.value is AccountState.SignedIn)
            }
            compose.waitUntil(45_000) {!vm.connectedLoading.value && !vm.historyLoading.value && !vm.watchlistLoading.value && vm.catalogue is ConnectedCatalogue}
            assertNull(vm.historyError.value);assertNull(vm.watchlistError.value)
            assertTrue("Service history must map to playable episodes",vm.connectedHistory.value.isNotEmpty())
            assertTrue("Saved series must survive episode-panel mapping",vm.connectedWatchlist.value.isNotEmpty())
            println("VERIFIED: account history and saved series loaded")
            compose.runOnUiThread {vm.navigate("History")}
            compose.waitUntil(10_000) {compose.onAllNodesWithTag("account-history").fetchSemanticsNodes().isNotEmpty()}
            compose.runOnUiThread {vm.navigate("My List")}
            compose.waitUntil(10_000) {compose.onAllNodesWithTag("account-watchlist").fetchSemanticsNodes().isNotEmpty()}
            compose.waitUntil(30_000) {!vm.watchlistLoading.value}
            val original=vm.connectedWatchlist.value.map {it.id}.toSet()
            val candidate=vm.catalogue.shows.firstOrNull {it.id !in original}
            if(candidate!=null) {
                val owner=vm.catalogue as ConnectedCatalogue
                cleanupList={runBlocking {owner.setWatchlist(candidate.id,false)}}
                compose.runOnUiThread {vm.show(candidate.id,"live-list-test")}
                compose.waitUntil(45_000) {!vm.detailLoading.value}
                compose.onNodeWithContentDescription("Add to Watchlist").performScrollTo().performClick()
                compose.waitUntil(15_000) {vm.connectedWatchlist.value.any {it.id==candidate.id} && candidate.id !in vm.busy.value}
                compose.runOnUiThread {vm.back();vm.navigate("My List")}
                compose.waitUntil(30_000) {!vm.watchlistLoading.value && vm.connectedWatchlist.value.any {it.id==candidate.id}}
                val remove="Remove ${candidate.title} from Watchlist"
                compose.onNodeWithTag("account-watchlist").performScrollToNode(hasContentDescription(remove))
                compose.onNodeWithContentDescription(remove).performClick()
                compose.waitUntil(15_000) {candidate.id !in vm.busy.value && vm.connectedWatchlist.value.none {it.id==candidate.id}}
                compose.runOnUiThread {vm.refreshWatchlist()}
                compose.waitUntil(30_000) {!vm.watchlistLoading.value}
                assertTrue(vm.connectedWatchlist.value.none {it.id==candidate.id})
                cleanupList=null
                println("VERIFIED: watchlist add, reload and remove restored original membership")
            }
            compose.runOnUiThread {vm.show("GG5H5XQX4","native-live-test")}
            compose.waitUntil(45_000) {!vm.detailLoading.value && (vm.catalogue.episodes("GG5H5XQX4",1).isNotEmpty() || vm.detailError.value!=null)}
            assertNull(vm.detailError.value)
            compose.waitUntil(30_000) {compose.onAllNodesWithTag("series-title-logo").fetchSemanticsNodes().any {
                it.config.getOrElse(SemanticsProperties.StateDescription) {""}=="Title artwork loaded"
            }}
            compose.onNodeWithTag("series-title-logo").captureToImage().asAndroidBitmap().let {bitmap ->
                java.io.File(compose.activity.getExternalFilesDir(null),"native-title-logo.png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
            }
            compose.onNodeWithTag("series-banner").captureToImage().asAndroidBitmap().let {bitmap ->
                java.io.File(compose.activity.getExternalFilesDir(null),"series-banner.png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
            }
            val seasons=vm.catalogue.show("GG5H5XQX4")!!.seasonNumbers
            if(seasons.size>1) {
                compose.onNodeWithTag("details-list").performScrollToIndex(2)
                val before=compose.onNodeWithTag("season-selector").fetchSemanticsNode().boundsInRoot.top
                compose.onNodeWithText("Season ${seasons.last()}").performClick()
                compose.waitUntil(45_000) {!vm.detailLoading.value && vm.episodeSelection.value.displayedSeason==seasons.last()}
                val after=compose.onNodeWithTag("season-selector").fetchSemanticsNode().boundsInRoot.top
                assertTrue("Real season loading must retain selector position",kotlin.math.abs(before-after)<8)
                println("VERIFIED: real season selection retained scroll anchor")
            }
            val episode=vm.catalogue.episodes("GG5H5XQX4",1).first()
            val originalProgress=(vm.catalogue as ConnectedCatalogue).progress(episode.id)
            val expected=originalProgress?.takeIf {!it.complete}?.positionMs ?: 0
            compose.runOnUiThread {vm.play(episode.id);engine=ViewModelProvider(compose.activity)[PlaybackEngine::class.java]}
            val player=engine!!
            compose.waitUntil(90_000) {player.state.value.renderedVideo || player.state.value.error!=null || compose.onAllNodesWithText("Couldn’t load your resume position").fetchSemanticsNodes().isNotEmpty()}
            assertTrue("Player preparation must reach the renderer; native=${player.state.value.native}, styled=${player.state.value.styledSubtitles}",player.state.value.renderedVideo || player.state.value.error!=null)
            assertNull(player.state.value.error)
            assertTrue("Android CDM must load platform license keys",player.state.value.drmKeysLoaded)
            assertTrue(player.state.value.native)
            compose.waitUntil(20_000) {player.state.value.position>2000}
            assertTrue("Episode must start from its saved cloud playhead",player.state.value.position>=expected)
            println("VERIFIED: cloud resume and native rendered video")
            val styled=player.state.value.subtitleChoices.firstOrNull {it.styled && it.language.startsWith("en")}
            if(styled!=null) {
                assertTrue("Authored ASS styling should use the provider-rendered track",player.state.value.styledSubtitles)
                var frames=player.state.value.renderedFrames
                compose.runOnUiThread {player.selectSubtitle("off")}
                compose.waitUntil(20_000) {player.state.value.renderedFrames>frames && player.state.value.playing && !player.state.value.styledSubtitles}
                frames=player.state.value.renderedFrames
                compose.runOnUiThread {player.selectSubtitle(styled.language)}
                compose.waitUntil(20_000) {player.state.value.renderedFrames>frames && player.state.value.playing && player.state.value.styledSubtitles}
                assertNull(player.state.value.error)
                println("VERIFIED: provider-styled subtitles, Off and language switch preserve playback")
            }
            assertTrue(player.state.value.qualityHeights.isNotEmpty())
            if(compose.onAllNodesWithContentDescription("Quality: Auto").fetchSemanticsNodes().isEmpty()) compose.onRoot().performTouchInput {click(center)}
            compose.onNodeWithContentDescription("Quality: Auto").performClick()
            compose.onNodeWithTag("quality-dialog").captureToImage().asAndroidBitmap().let {bitmap ->
                java.io.File(compose.activity.getExternalFilesDir(null),"native-quality-menu.png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
            }
            val target=player.state.value.qualityHeights.min()
            compose.onNodeWithText("${target}p",substring=false).performScrollTo().performClick()
            compose.waitUntil(10_000) {player.state.value.selectedHeight==target}
            val seekTarget=(expected+30_000).coerceAtMost((player.state.value.duration-3000).coerceAtLeast(0))
            compose.runOnUiThread {player.seek(seekTarget)}
            compose.waitUntil(25_000) {player.state.value.position>seekTarget+1000 && player.state.value.currentHeight==target && player.state.value.playing}
            assertNull(player.state.value.error)
            assertTrue(player.state.value.renderedVideo)
            compose.onNodeWithContentDescription("Quality: ${target}p").performClick()
            compose.onNodeWithText("Auto",substring=false).performClick()
            compose.waitUntil(5000) {player.state.value.selectedHeight==0}
            assertNull(player.state.value.error)
            compose.runOnUiThread {player.close();vm.back()}
            compose.waitUntil(15_000) {player.cleanupResult.value=="closed" || player.cleanupResult.value=="failed"}
            assertEquals("closed",player.cleanupResult.value)
            compose.waitUntil(15_000) {!player.progressSyncPending()}
            val backend=vm.nativePlaybackService()!!
            runBlocking {
                val lease=backend.open(episode.id,"ja")
                try {
                    try {backend.renew(lease,30_000)} catch(e: Exception) {throw AssertionError("Playback lease renewal: ${e.accountMessage()}")}
                    try {backend.saveProgress(lease,episode.id,originalProgress?.positionMs ?: seekTarget)} catch(e: Exception) {throw AssertionError("Account playhead sync: ${e.accountMessage()}")}
                } finally {backend.close(lease)}
            }
        } finally {
            compose.runOnUiThread {engine?.close()}
            runCatching {cleanupList?.invoke()}
            // Return the rotated refresh session only to the local broker, then erase this test installation's copy.
            if(credentialSocket==null && !restoreOnly) runCatching {runBlocking {vault.read()}?.let {socket.outputStream.write((json.encodeToString(it)+"\n").toByteArray());socket.outputStream.flush()}}
            if(!keepSession) runBlocking {vault.clear()}
            socket.close()
        }
    }
}
