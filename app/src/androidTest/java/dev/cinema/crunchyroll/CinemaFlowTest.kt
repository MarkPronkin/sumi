package dev.cinema.crunchyroll

import androidx.compose.ui.test.*
import android.content.res.Configuration
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import dev.cinema.crunchyroll.playback.PlaybackEngine
import dev.cinema.crunchyroll.ui.CinemaViewModel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Runs against the actual Compose UI on touch and TV emulators. */
class CinemaFlowTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()

    private fun enterDemo() {
        lateinit var vm: CinemaViewModel
        compose.runOnUiThread {vm=ViewModelProvider(compose.activity)[CinemaViewModel::class.java]}
        compose.waitUntil(30_000) {vm.account.value !is dev.cinema.crunchyroll.data.account.AccountState.Restoring}
        if(vm.isConnected) {
            compose.runOnUiThread {vm.logout()}
            compose.waitUntil(15_000) {!vm.isConnected && vm.catalogue is dev.cinema.crunchyroll.data.DemoCatalogue}
        }
        compose.runOnUiThread {
            vm.enterDemo()
            vm.preferences {it.copy(motion=dev.cinema.crunchyroll.data.MotionLevel.Maximal)}
            vm.navigate("Home")
        }
        compose.waitUntil(15_000) {vm.local.value.enteredDemo && vm.local.value.current.motion==dev.cinema.crunchyroll.data.MotionLevel.Maximal}
        compose.waitUntil(10_000) {compose.onAllNodesWithContentDescription("Home").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithContentDescription("Home").performClick()
        compose.waitUntil(10_000) {compose.onAllNodesWithTag("home-banner").fetchSemanticsNodes().isNotEmpty()}
    }

    @Test fun browseDetailsListAndRecreation() {
        enterDemo()
        compose.onNodeWithContentDescription("Browse",useUnmergedTree=false).performClick()
        compose.onNodeWithContentDescription("Search anime").performTextInput("solo")
        compose.waitUntil(10_000) {compose.onAllNodesWithText("SOLO LEVELING").fetchSemanticsNodes().isNotEmpty()}
        val tv=compose.activity.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION
        if(tv) {
            compose.onNodeWithText("SOLO LEVELING").performSemanticsAction(SemanticsActions.RequestFocus) {it()}
            compose.onNodeWithText("SOLO LEVELING").performKeyInput {pressKey(Key.DirectionCenter)}
        } else compose.onNodeWithText("SOLO LEVELING").performClick()
        compose.waitUntil(10_000) {compose.onAllNodesWithText("Play sample video").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("details-list").performScrollToIndex(2)
        compose.onNodeWithText("Episodes").assertExists()
        compose.onNodeWithTag("details-list").performScrollToIndex(0)
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) {compose.onAllNodesWithText("Play sample video").fetchSemanticsNodes().isNotEmpty()}
        val added=compose.onAllNodesWithContentDescription("Remove from Watchlist").fetchSemanticsNodes().isNotEmpty()
        if(!added) compose.onNodeWithContentDescription("Add to Watchlist").performClick()
        compose.waitUntil(10_000) {compose.onAllNodesWithContentDescription("Remove from Watchlist").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithContentDescription("Back to catalogue").performClick()
        compose.waitUntil(10_000) {compose.onAllNodesWithContentDescription("Search anime").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithContentDescription("Search anime").assertTextContains("solo")
        if(tv) compose.onNodeWithText("SOLO LEVELING").assertIsFocused()
        compose.onNodeWithContentDescription("My Content",useUnmergedTree=false).performClick()
        compose.onNodeWithTag("content-tab-Watchlist").performClick()
        compose.onNodeWithText("SOLO LEVELING").assertExists()
        compose.onNodeWithContentDescription("Remove SOLO LEVELING from Watchlist").performClick()
        compose.waitUntil(10_000) {compose.onAllNodesWithText("SOLO LEVELING").fetchSemanticsNodes().isEmpty()}
    }

    @Test fun realSamplePlaybackPauseSeekAndRecreation() {
        enterDemo()
        compose.onNodeWithText("Play demo").performClick()
        lateinit var engine: PlaybackEngine
        compose.runOnUiThread {engine=ViewModelProvider(compose.activity)[PlaybackEngine::class.java]}
        compose.waitUntil(45_000) {engine.state.value.ready || engine.state.value.error!=null}
        assertNull(engine.state.value.error)
        assertTrue(engine.state.value.duration>0)
        compose.waitUntil(15_000) {engine.state.value.renderedVideo}
        compose.waitUntil(15_000) {engine.state.value.position>=1500}
        compose.runOnUiThread {engine.player.pause();engine.seek(30_000)}
        compose.waitUntil(5000) {engine.state.value.position>=29_000 && !engine.state.value.playing}
        compose.activityRule.scenario.recreate()
        compose.waitUntil(15_000) {compose.onAllNodesWithText("Play").fetchSemanticsNodes().isNotEmpty()}
        assertTrue(engine.state.value.position>=29_000)
        assertFalse("Recreation must preserve pause",engine.state.value.playing)
        compose.onNodeWithText("Play",substring=false).performClick()
        compose.waitUntil(10_000) {engine.state.value.playing}
        compose.runOnUiThread {engine.player.pause()}
        compose.onNodeWithContentDescription("Exit playback").performClick()
        compose.waitUntil(10_000) {compose.onAllNodesWithTag("home-banner").fetchSemanticsNodes().isNotEmpty()}
        compose.runOnUiThread {assertTrue(engine.player.mediaItemCount==0)}
    }

    @Test fun spotlightChangesSeriesAndKeepsDetailsAndPlayBoundToItsSelection() {
        enterDemo()
        lateinit var vm: CinemaViewModel
        compose.runOnUiThread {vm=ViewModelProvider(compose.activity)[CinemaViewModel::class.java]}
        val initial=vm.featuredId.value
        compose.onNodeWithContentDescription("Next featured series").performScrollTo().performClick()
        compose.waitUntil(10_000) {vm.featuredId.value!=initial}
        val selected=vm.featuredId.value
        compose.onNodeWithContentDescription("Details for ${vm.catalogue.show(selected)!!.title}").performScrollTo().performClick()
        compose.waitUntil(10_000) {vm.detail.value==selected}
        compose.onNodeWithContentDescription("Back to catalogue").performClick()
        compose.waitUntil(10_000) {vm.detail.value==null}
        assertEquals(selected,vm.featuredId.value)
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) {compose.onAllNodesWithContentDescription("Previous featured series").fetchSemanticsNodes().isNotEmpty()}
        compose.runOnUiThread {vm=ViewModelProvider(compose.activity)[CinemaViewModel::class.java]}
        assertEquals(selected,vm.featuredId.value)
        compose.onNodeWithContentDescription("Previous featured series").performScrollTo().performClick()
        compose.waitUntil(10_000) {vm.featuredId.value==initial}
        compose.onNodeWithContentDescription("Refresh Home").performScrollTo().performClick()
        compose.waitUntil(10_000) {vm.featuredId.value!=initial}
        val refreshed=vm.featuredId.value
        compose.onNodeWithText("Play demo").performScrollTo().performClick()
        compose.waitUntil(10_000) {vm.playerEpisode.value!=null}
        assertEquals(refreshed,vm.catalogue.episode(vm.playerEpisode.value!!)!!.showId)
        compose.runOnUiThread {vm.back()}
    }

    @Test fun spotlightDissolvesBetweenPostersAndCanReverseMidTransition() {
        enterDemo()
        val vm = compose.runOnIdle { ViewModelProvider(compose.activity)[CinemaViewModel::class.java] }
        val next = compose.onNodeWithContentDescription("Next featured series")
        val previous = compose.onNodeWithContentDescription("Previous featured series")
        next.performScrollTo()
        compose.waitForIdle()

        // Sample the upper artwork, clear of captions and buttons on both layouts.
        fun artworkSamples(): List<Int> {
            val bitmap = compose.onNodeWithTag("home-banner").captureToImage().asAndroidBitmap()
            return (1..8).flatMap { x ->
                (1..3).map { y -> bitmap.getPixel(x * bitmap.width / 10, y * bitmap.height / 12) }
            }
        }

        val first = artworkSamples()
        compose.mainClock.autoAdvance = false
        try {
            next.performClick()
            compose.mainClock.advanceTimeBy(176)
            val middle = artworkSamples()
            val selected = vm.featuredId.value
            compose.onNodeWithContentDescription("Play demo").assertIsDisplayed()
            compose.mainClock.advanceTimeBy(800)
            val last = artworkSamples()
            assertTrue("The poster must have a distinct intermediate frame",
                middle.indices.count { middle[it] != first[it] && middle[it] != last[it] } > middle.size / 2)

            previous.performClick()
            compose.mainClock.advanceTimeBy(112)
            next.performClick()
            compose.mainClock.advanceTimeBy(48)
            compose.onNodeWithContentDescription("Details for ${vm.catalogue.show(selected)!!.title}").performClick()
            compose.mainClock.advanceTimeBy(1200)
            assertEquals(selected, vm.detail.value)
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    @Test fun welcomeOffersSignInAndKeepsDemoInsideAppInfo() {
        enterDemo()
        compose.runOnUiThread { ViewModelProvider(compose.activity)[CinemaViewModel::class.java].leaveDemo() }
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("About Sumi").fetchSemanticsNodes().isNotEmpty() }
        val about = compose.onNodeWithContentDescription("About Sumi")
        val demo = compose.onNodeWithText("Explore the demo")
        val signIn = compose.onNodeWithContentDescription("Sign in")
        signIn.assertIsDisplayed()
        demo.assertDoesNotExist()

        compose.mainClock.autoAdvance = false
        try {
            signIn.performClick()
            compose.mainClock.advanceTimeBy(64)
            compose.onNodeWithText("Welcome to\nSumi.").assertExists()
            compose.onNodeWithContentDescription("Account email").assertExists()
            compose.mainClock.advanceTimeBy(1000)
        } finally {
            compose.mainClock.autoAdvance = true
        }
        compose.onNodeWithText("Welcome to\nSumi.").assertDoesNotExist()
        compose.onNodeWithContentDescription("Account email").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        signIn.assertIsDisplayed()

        about.performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText(BuildConfig.VERSION_NAME).assertIsDisplayed()
        compose.onNodeWithText(BuildConfig.VERSION_CODE.toString()).assertIsDisplayed()
        compose.onNodeWithContentDescription("Close").performClick()
        demo.assertDoesNotExist()
        signIn.assertIsDisplayed()

        about.performClick()
        demo.performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("home-banner").fetchSemanticsNodes().isNotEmpty() }
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("home-banner").fetchSemanticsNodes().isNotEmpty() }
        about.assertDoesNotExist()
    }

    @Test fun loginEntryValidatesInputAndDoesNotSavePasswordAcrossRecreation() {
        enterDemo()
        compose.onNodeWithContentDescription("Profile").performClick()
        compose.onNodeWithText("Sign in to Crunchyroll").performClick()
        compose.waitUntil(10_000) {compose.onAllNodesWithContentDescription("Account email").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithContentDescription("Account email").performTextInput("viewer@example.invalid")
        compose.onNodeWithContentDescription("Account password").performTextInput("test-only-not-a-real-password")
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) {compose.onAllNodesWithContentDescription("Account password").fetchSemanticsNodes().isNotEmpty()}
        val value=compose.onNodeWithContentDescription("Account password").fetchSemanticsNode().config[SemanticsProperties.EditableText].text
        assertEquals("",value)
        compose.onNodeWithContentDescription("Sign in to Crunchyroll",substring=false).performScrollTo().performClick()
        compose.onNodeWithText("Enter your account email and password.").assertExists()
        compose.onNodeWithContentDescription("Back",substring=false).performClick()
        compose.waitUntil(10_000) {compose.onAllNodesWithText("Who’s watching?").fetchSemanticsNodes().isNotEmpty()}
    }

    @Test fun rapidSeriesOpenBackAndRetargetReturnsToTheCorrectScreen() {
        enterDemo()
        lateinit var vm: CinemaViewModel
        compose.runOnUiThread {vm=ViewModelProvider(compose.activity)[CinemaViewModel::class.java]}
        compose.mainClock.autoAdvance=false
        try {
            compose.runOnUiThread {vm.show(vm.featuredId.value,"featured-${vm.featuredId.value}",roundedArtwork=false)}
            compose.mainClock.advanceTimeBy(112)
            compose.runOnUiThread {vm.back()}
            compose.mainClock.advanceTimeBy(64)
            compose.runOnUiThread {vm.show("solo","home-solo")}
            compose.mainClock.advanceTimeBy(1200)
            if(compose.activity.resources.configuration.screenWidthDp<600)
                compose.onNodeWithTag("series-banner").assertContentDescriptionEquals("SOLO LEVELING")
            else compose.onNodeWithText("SOLO LEVELING").assertExists()
            compose.runOnUiThread {vm.back()}
            compose.mainClock.advanceTimeBy(1200)
            compose.onNodeWithTag("home-banner").assertExists()
        } finally {compose.mainClock.autoAdvance=true}
    }

    @Test fun sharedArtworkDoesNotCoverThePlayButtonDuringOpening() {
        enterDemo()
        lateinit var vm: CinemaViewModel
        compose.runOnUiThread {vm=ViewModelProvider(compose.activity)[CinemaViewModel::class.java]}
        compose.mainClock.autoAdvance=false
        try {
            compose.runOnUiThread {vm.show(vm.featuredId.value,"featured-${vm.featuredId.value}",roundedArtwork=false)}
            compose.mainClock.advanceTimeBy(288)
            val bounds=compose.onNodeWithContentDescription("Play sample video").fetchSemanticsNode().boundsInRoot
            val pixels=compose.onRoot().captureToImage().toPixelMap()
            val x=(bounds.left+bounds.width*.9f).toInt().coerceIn(0,pixels.width-1)
            // Sample the button's lower padding, away from the label and icon.
            val y=(bounds.top+bounds.height*.8f).toInt().coerceIn(0,pixels.height-1)
            val color=pixels[x,y]
            assertTrue("Artwork must not paint over the orange play button: $color",color.red>0.8f && color.green in 0.3f..0.65f && color.blue<0.3f)
        } finally {compose.mainClock.autoAdvance=true}
    }

    @Test fun artworkOriginShapeSurvivesRecreationForCardsAndSpotlight() {
        enterDemo()
        lateinit var vm: CinemaViewModel
        compose.runOnUiThread {vm=ViewModelProvider(compose.activity)[CinemaViewModel::class.java]}
        compose.onNodeWithContentDescription("Browse").performClick()
        compose.onNodeWithContentDescription("Search anime").performTextInput("solo")
        compose.waitUntil(10_000) {compose.onAllNodesWithText("SOLO LEVELING").fetchSemanticsNodes().isNotEmpty()}
        val tv=compose.activity.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK==Configuration.UI_MODE_TYPE_TELEVISION
        if(tv) {
            compose.onNodeWithText("SOLO LEVELING").performSemanticsAction(SemanticsActions.RequestFocus) {it()}
            compose.onNodeWithText("SOLO LEVELING").performKeyInput {pressKey(Key.DirectionCenter)}
        } else compose.onNodeWithText("SOLO LEVELING").performClick()
        compose.waitUntil(10_000) {vm.detail.value=="solo"}
        assertTrue("Catalogue artwork starts with rounded corners",vm.artworkOriginRounded.value)
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) {compose.onAllNodesWithTag("series-banner").fetchSemanticsNodes().isNotEmpty()}
        assertTrue("Returning after recreation must recover the card shape",vm.artworkOriginRounded.value)
        compose.onNodeWithContentDescription("Back to catalogue").performClick()
        compose.onNodeWithContentDescription("Home").performClick()
        compose.waitUntil(10_000) {compose.onAllNodesWithTag("home-banner").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithContentDescription("Details for ${vm.catalogue.show(vm.featuredId.value)!!.title}").performScrollTo().performClick()
        compose.waitUntil(10_000) {vm.detail.value!=null}
        assertFalse("Full-width spotlight artwork starts square",vm.artworkOriginRounded.value)
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) {compose.onAllNodesWithTag("series-banner").fetchSemanticsNodes().isNotEmpty()}
        assertFalse("Square spotlight shape survives recreation",vm.artworkOriginRounded.value)
        compose.onNodeWithContentDescription("Back to catalogue").performClick()
        compose.onNodeWithTag("home-banner").assertExists()
    }

    @Test fun showRatingsFollowTheSelectedShowAndSurviveRecreation() {
        enterDemo()
        lateinit var vm: CinemaViewModel
        compose.runOnUiThread {vm=ViewModelProvider(compose.activity)[CinemaViewModel::class.java];vm.show("frieren","home-hero")}
        compose.waitUntil(10_000) {vm.ratings.value.showId=="frieren"}
        compose.onNodeWithTag("details-list").performScrollToIndex(1)
        compose.onNodeWithTag("show-ratings").assertIsDisplayed()
        compose.onNodeWithText("9.30",useUnmergedTree=true).assertIsDisplayed()
        compose.onNodeWithText("Rank #1",useUnmergedTree=true).assertIsDisplayed()
        captureLayout("ratings-layout.png")
        compose.runOnUiThread {vm.show("solo","home-solo")}
        compose.waitUntil(10_000) {vm.ratings.value.showId=="solo"}
        compose.onNodeWithTag("details-list").performScrollToIndex(1)
        compose.onNodeWithText("8.30",useUnmergedTree=true).assertIsDisplayed()
        compose.onNodeWithText("Rank #200",useUnmergedTree=true).assertIsDisplayed()
        compose.onNodeWithText("9.30",useUnmergedTree=true).assertDoesNotExist()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) {compose.onAllNodesWithTag("details-list").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("details-list").performScrollToIndex(1)
        compose.onNodeWithText("8.30",useUnmergedTree=true).assertIsDisplayed()
        compose.onNodeWithText("Rank #200",useUnmergedTree=true).assertIsDisplayed()
    }

    @Test fun seasonSelectionKeepsTheEpisodeSectionAnchored() {
        enterDemo()
        captureLayout("home-layout.png")
        lateinit var vm: CinemaViewModel
        compose.runOnUiThread {vm=ViewModelProvider(compose.activity)[CinemaViewModel::class.java];vm.show("frieren","home-hero")}
        compose.waitUntil(10_000) {compose.onAllNodesWithTag("details-list").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("details-list").performScrollToIndex(2)
        val before=compose.onNodeWithTag("season-selector").fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithText("Season 2").performClick()
        compose.waitForIdle()
        val after=compose.onNodeWithTag("season-selector").fetchSemanticsNode().boundsInRoot.top
        assertTrue("Season selection must not bring the hero back into view",kotlin.math.abs(after-before)<8)
        val tv=compose.activity.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK==Configuration.UI_MODE_TYPE_TELEVISION
        compose.onNodeWithTag("details-list").performScrollToIndex(0)
        val banner=compose.onNodeWithTag("series-banner").fetchSemanticsNode().boundsInRoot
        val screen=compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue("The banner must fill the available width",kotlin.math.abs(banner.width-screen.width)<2)
        if(!tv && compose.activity.resources.configuration.screenWidthDp<600) {
            assertTrue("Phone artwork must show the full 2:3 poster",kotlin.math.abs(banner.width/banner.height-2f/3)<.03f)
            compose.onNodeWithTag("series-heading").assertDoesNotExist()
            compose.onNodeWithTag("series-title-logo").assertDoesNotExist()
        } else {
            assertTrue("Tablet and TV artwork must remain landscape",banner.width>banner.height)
            val back=compose.onNodeWithContentDescription("Back to catalogue").fetchSemanticsNode().boundsInRoot
            val heading=compose.onNodeWithText("DEMO SERIES",substring=true).fetchSemanticsNode().boundsInRoot
            assertTrue("Wide-screen heading must clear Back and the status-bar inset",heading.top>=back.bottom)
        }
        captureLayout("details-layout.png")
    }

    private fun captureLayout(name: String) {
        compose.onRoot().captureToImage().asAndroidBitmap().let {bitmap ->
            java.io.File(compose.activity.getExternalFilesDir(null),name).outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
            }
        }
    }
}
