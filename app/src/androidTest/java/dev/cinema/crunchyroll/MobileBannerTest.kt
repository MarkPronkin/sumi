package dev.cinema.crunchyroll

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import dev.cinema.crunchyroll.data.*
import dev.cinema.crunchyroll.data.account.AccountState
import dev.cinema.crunchyroll.ui.CinemaViewModel
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Home uses landscape artwork while mobile details retain the complete portrait poster. */
class MobileBannerTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()

    @Test fun mobileHomeUsesCompactLandscapeArtworkAndDetailsKeepThePoster() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val poster=File(instrumentation.targetContext.cacheDir,"jujutsu-kaisen-poster.jpg")
        instrumentation.context.assets.open("jujutsu-kaisen-poster.jpg").use {input ->poster.outputStream().use {input.copyTo(it)}}
        val hero=File(instrumentation.targetContext.cacheDir,"landscape-hero.png")
        Bitmap.createBitmap(800,450,Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.RED)
            hero.outputStream().use {compress(Bitmap.CompressFormat.PNG,100,it)}
            recycle()
        }
        val logo=File(instrumentation.targetContext.cacheDir,"title-logo.png")
        Bitmap.createBitmap(640,160,Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.GREEN)
            logo.outputStream().use {compress(Bitmap.CompressFormat.PNG,100,it)}
            recycle()
        }
        val show=Anime("banner-fixture","JUJUTSU KAISEN","","Poster layout preview.",2020,listOf("Anime"),R.drawable.ic_cinema,
            seasons=4,posterUrl=poster.toURI().toString(),heroUrl=hero.toURI().toString(),logoUrl=logo.toURI().toString())
        showFixture(show, MotionLevel.Reduced)
        assertLogoLoaded()
        val banner=compose.onNodeWithTag("home-banner").fetchSemanticsNode().boundsInRoot
        assertTrue("Mobile Home should use a shallow 16:9 landscape banner",kotlin.math.abs(banner.width/banner.height-16f/9)<.02f)
        compose.waitUntil(10_000) {
            val pixels=compose.onNodeWithTag("home-banner").captureToImage().toPixelMap()
            val color=pixels[pixels.width/2,pixels.height/4]
            color.red>.8f && color.green<.1f && color.blue<.1f
        }
        compose.onNode(hasText("JUJUTSU KAISEN") and hasAnyAncestor(hasTestTag("home-banner"))).assertDoesNotExist()
        val logoBounds=compose.onNodeWithTag("series-title-logo").fetchSemanticsNode().boundsInRoot
        assertTrue("Logo stays inside the compact hero",logoBounds.left>=banner.left && logoBounds.right<=banner.right && logoBounds.bottom<=banner.bottom)
        val copy=compose.onNodeWithTag("home-banner-copy").fetchSemanticsNode().boundsInRoot
        assertTrue("Actions must stay below the landscape artwork",copy.top>=banner.bottom-1)
        val viewport=compose.onNodeWithTag("home-viewport").fetchSemanticsNode().boundsInRoot
        val controls=compose.onNodeWithTag("spotlight-controls").fetchSemanticsNode().boundsInRoot
        assertTrue("The compact spotlight must leave room for the catalogue",controls.bottom-viewport.top<viewport.height*.65f)
        assertTrue("The spotlight footer must clear bottom navigation",controls.bottom<=viewport.bottom+1 && copy.bottom<=controls.top+1)
        compose.onNodeWithText("Continue Watching").assertIsDisplayed()
        compose.onNodeWithTag("continue-watching").assertIsDisplayed()
        val play=compose.onNodeWithContentDescription("Play demo").fetchSemanticsNode().boundsInRoot
        val pixels=compose.onRoot().captureToImage().toPixelMap()
        val button=pixels[(play.left+play.width*.1f).toInt(),(play.top+play.height*.8f).toInt()]
        assertTrue("The compact primary action must retain its orange background: $button",button.red>.8f && button.green in 0.3f..0.65f && button.blue<.3f)
        capture("mobile-home.png")
        compose.onNodeWithContentDescription("Details for JUJUTSU KAISEN").performScrollTo().performClick()
        compose.waitUntil(10_000) {compose.onAllNodesWithTag("series-banner").fetchSemanticsNodes().isNotEmpty()}
        compose.waitForIdle()
        compose.onNodeWithTag("series-title-logo").assertDoesNotExist()
        compose.onNodeWithTag("series-heading").assertDoesNotExist()
        val detail=compose.onNodeWithTag("series-banner").fetchSemanticsNode().boundsInRoot
        assertTrue("Details must preserve the full poster",kotlin.math.abs(detail.width/detail.height-2f/3)<.02f)
        capture("mobile-details.png")
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) {compose.onAllNodesWithTag("series-banner").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("series-title-logo").assertDoesNotExist()
        compose.runOnUiThread {compose.activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE}
        compose.waitUntil(10_000) {compose.activity.resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE}
        compose.onNodeWithTag("series-title-logo").assertDoesNotExist()
        compose.onNodeWithText("JUJUTSU KAISEN").assertExists()
        compose.onNodeWithContentDescription("Back to catalogue").performClick()
        compose.waitUntil(10_000) {compose.onAllNodesWithTag("home-banner").fetchSemanticsNodes().isNotEmpty()}
        assertLogoLoaded()
    }

    @Test fun portraitPosterStaysBrightBetweenTheWatchlistAndDetails() {
        val config = compose.activity.resources.configuration
        val isTv = config.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION
        assumeTrue(config.screenWidthDp < 600 && !isTv)
        val show = greenPosterFixture()
        val vm = showFixture(show, MotionLevel.Maximal)
        compose.runOnIdle {
            vm.toggleList(show.id, true)
            vm.openContent(ContentView.Watchlist)
        }

        // Sample the root because shared artwork is drawn outside the card and detail nodes.
        fun brightPosterArea(): Int {
            val frame = compose.onRoot().captureToImage().toPixelMap()
            var samples = 0
            for (y in 0 until frame.height step 12) {
                for (x in 0 until frame.width step 12) {
                    val pixel = frame[x, y]
                    if (pixel.green > .95f && pixel.red < .05f && pixel.blue < .05f) samples++
                }
            }
            return samples
        }

        val card = compose.onNodeWithContentDescription("Open ${show.title}")
        compose.waitUntil(10_000) { card.isDisplayed() && brightPosterArea() > 100 }
        val cardCenter = card.fetchSemanticsNode().boundsInRoot.center.x.toInt()
        fun transitionFrames(): List<Int> {
            val start = compose.mainClock.currentTime
            return listOf(32L, 80L, 128L, 192L, 288L).map { elapsed ->
                compose.mainClock.advanceTimeBy(start + elapsed - compose.mainClock.currentTime)
                if (elapsed in 128L..192L) assertSoftPosterEdge(cardCenter)
                brightPosterArea()
            }
        }

        val cardArea = brightPosterArea()
        compose.mainClock.autoAdvance = false
        try {
            card.performClick()
            val opening = transitionFrames()
            compose.mainClock.advanceTimeBy(1000)
            val detailArea = brightPosterArea()
            assertTrue("The poster must stay visible throughout opening: $cardArea -> $opening",
                opening.all { it >= cardArea * .90f })
            assertTrue("The poster must expand through intermediate sizes: $opening -> $detailArea",
                opening.any { it > cardArea * 1.2f && it < detailArea * .9f })
            compose.onNodeWithContentDescription("Back to catalogue").performClick()
            val closing = transitionFrames()
            assertTrue("The poster must stay visible throughout return: $closing -> $cardArea",
                closing.all { it >= cardArea * .90f })
        } finally {
            compose.mainClock.autoAdvance = true
            compose.runOnIdle { vm.toggleList(show.id, false) }
        }
    }

    @Test fun spotlightKeepsItsBottomFadeWhileOpeningDetails() {
        val show = greenPosterFixture()
        showFixture(show, MotionLevel.Maximal)
        val banner = compose.onNodeWithTag("home-banner")
        compose.waitUntil(10_000) {
            val frame = banner.captureToImage().toPixelMap()
            frame[frame.width * 4 / 5, frame.height / 4].green > .9f
        }
        val bounds = banner.fetchSemanticsNode().boundsInRoot
        val x = (bounds.left + bounds.width * .72f).toInt()
        compose.mainClock.autoAdvance = false
        try {
            compose.onNodeWithContentDescription("Details for ${show.title}").performClick()
            listOf(80L, 96L, 128L).forEach {
                compose.mainClock.advanceTimeBy(it)
                assertSoftPosterEdge(x)
            }
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    @Test fun returningToPosterCardClearsDetailTextWhileThePosterMoves() {
        val show = greenPosterFixture()
        val vm = showFixture(show, MotionLevel.Maximal)
        compose.runOnIdle {
            vm.preferences { it.copy(watchlist = it.watchlist + show.id) }
            vm.openContent(ContentView.Watchlist)
        }
        try {
            val card = compose.onNode(hasText(show.title) and hasAnyAncestor(hasTestTag("content-watchlist")))
            val isTv = compose.activity.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION
            checkTextOnReturn(card, open = {
                if (isTv) {
                    card.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
                    card.performKeyInput { pressKey(Key.DirectionCenter) }
                } else card.performClick()
            }) { compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() } }
        } finally {
            compose.runOnIdle { vm.preferences { it.copy(watchlist = it.watchlist - show.id) } }
        }
    }

    @Test fun returningToSpotlightClearsDetailTextWhileThePosterMoves() {
        val show = greenPosterFixture()
        showFixture(show, MotionLevel.Maximal)
        checkTextOnReturn(compose.onNodeWithContentDescription("Details for ${show.title}")) {
            compose.onNodeWithContentDescription("Back to catalogue").performClick()
        }
    }

    private fun checkTextOnReturn(entry: SemanticsNodeInteraction, open: () -> Unit = { entry.performClick() }, goBack: () -> Unit) {
        compose.waitUntil(10_000) { entry.isDisplayed() }
        val catalogue = compose.onRoot().captureToImage().toPixelMap()
        open()
        compose.waitUntil(10_000) { compose.onNodeWithContentDescription("Back to catalogue").isDisplayed() }
        compose.onNodeWithText("Ratings").assertIsDisplayed()
        val detail = compose.onRoot().captureToImage().toPixelMap()
        val glyphs = listOf("seasons", "Ratings").associateWith { label ->
            val bounds = compose.onNodeWithText(label, substring = true).fetchSemanticsNode().boundsInRoot
            buildList {
                for (y in bounds.top.toInt() until bounds.bottom.toInt()) {
                    for (x in bounds.left.toInt() until bounds.right.toInt()) {
                        // The green fixture has no blue. Isolate bright glyphs over dark catalogue pixels.
                        if (detail[x, y].blue > .55f && catalogue[x, y].blue < .12f) {
                            add(IntOffset(x, y))
                        }
                    }
                }
                assertTrue("Missing reference pixels for $label", size > 20)
            }
        }
        compose.mainClock.autoAdvance = false
        try {
            goBack()
            for (delta in listOf(96L, 240L)) {
                compose.mainClock.advanceTimeBy(delta)
                entry.assertIsDisplayed()
                val returning = compose.onRoot().captureToImage().toPixelMap()
                for ((label, pixels) in glyphs) {
                    val remaining = pixels.count { returning[it.x, it.y].blue > .25f }
                    assertTrue("$label still visible on return ($remaining/${pixels.size} pixels)", remaining < pixels.size * .1f)
                }
            }
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    private fun assertSoftPosterEdge(x: Int) {
        val frame = compose.onRoot().captureToImage().toPixelMap()
        fun greenAt(y: Int): Float {
            val pixel = frame[x, y]
            return pixel.green - maxOf(pixel.red, pixel.blue)
        }
        val column = 0 until frame.height
        val peak = column.maxOf { greenAt(it) }
        assertTrue("The green artwork must be visible", peak > .25f)
        // Outgoing controls can cross this column. Inspect the bottom of the image itself.
        val bottom = column.last { greenAt(it) > .03f }
        val aboveEdge = greenAt(bottom - 2)
        assertTrue("The moving image should fade to black at y=$bottom, green=$aboveEdge",
            aboveEdge < peak * .15f)
    }

    private fun greenPosterFixture(): Anime {
        val poster = File(compose.activity.cacheDir, "green-series-poster.png")
        Bitmap.createBitmap(960, 1440, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.GREEN)
            poster.outputStream().use { compress(Bitmap.CompressFormat.PNG, 100, it) }
            recycle()
        }
        val url = android.net.Uri.fromFile(poster).toString()
        return DemoCatalogue().shows.first().copy(id = "poster-transition", posterUrl = url, heroUrl = url)
    }

    private fun showFixture(show: Anime, motion: MotionLevel): CinemaViewModel {
        val repository=object: CatalogueRepository {
            override val shows=listOf(show)
            override suspend fun search(query: String,category: String)=shows
            override fun episodes(showId: String,season: Int)=emptyList<Episode>()
        }
        lateinit var vm: CinemaViewModel
        compose.runOnUiThread {vm=ViewModelProvider(compose.activity)[CinemaViewModel::class.java]}
        compose.waitUntil(30_000) {vm.account.value !is AccountState.Restoring && vm.ready.value}
        if(vm.isConnected) {
            compose.runOnUiThread {vm.logout()}
            compose.waitUntil(15_000) {!vm.isConnected}
        }
        compose.runOnUiThread {
            vm.enterDemo();vm.preferences {it.copy(motion=motion)}
            // Keep fixture injection confined to instrumentation rather than adding a production override.
            CinemaViewModel::class.java.getDeclaredMethod("setCatalogue",CatalogueRepository::class.java).apply {isAccessible=true}.invoke(vm,repository)
            vm.navigate("Home")
        }
        compose.waitUntil(15_000) {compose.onAllNodesWithTag("home-banner").fetchSemanticsNodes().isNotEmpty()}
        compose.waitForIdle()
        return vm
    }

    private fun assertLogoLoaded() {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("series-title-logo").fetchSemanticsNodes().any {
                it.config.getOrElse(SemanticsProperties.StateDescription) {""}=="Title artwork loaded"
            }
        }
        compose.onNodeWithTag("series-title-logo").assertIsDisplayed()
        compose.onNode(hasContentDescription("JUJUTSU KAISEN") and hasAnyAncestor(hasTestTag("series-title-logo")),useUnmergedTree=true).assertIsDisplayed()
        val pixels=compose.onNodeWithTag("series-title-logo").captureToImage().toPixelMap()
        val color=pixels[pixels.width/2,pixels.height/2]
        assertTrue("Home renders the actual logo image, not a text substitute: $color",color.green>.9f && color.red<.1f && color.blue<.1f)
    }

    private fun capture(name: String) {
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        // Flush the final Compose draw before asking Android to capture the entire display.
        compose.onRoot().captureToImage()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.takeScreenshot().let {bitmap ->
            File(instrumentation.targetContext.getExternalFilesDir(null),name).outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
            }
            bitmap.recycle()
        }
    }
}
