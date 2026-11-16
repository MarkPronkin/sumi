package dev.cinema.crunchyroll

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import dev.cinema.crunchyroll.data.MotionLevel
import dev.cinema.crunchyroll.data.account.AccountState
import dev.cinema.crunchyroll.ui.CinemaViewModel
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

/** Repeatable screenshots of the real app. Run on a dedicated demo emulator. */
class InterfaceScreenshotsTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun captureInterface() {
        lateinit var vm: CinemaViewModel
        compose.runOnUiThread { vm = ViewModelProvider(compose.activity)[CinemaViewModel::class.java] }
        compose.waitUntil(30_000) { vm.ready.value && vm.account.value !is AccountState.Restoring }
        assertFalse("Capture on a demo emulator, never a signed-in device", vm.isConnected)
        compose.runOnUiThread {
            vm.enterDemo()
            vm.selectProfile("main")
            val motion=InstrumentationRegistry.getArguments().getString("screenshotMotion")?.let { MotionLevel.valueOf(it) } ?: MotionLevel.Reduced
            vm.preferences { it.copy(motion = motion, watchlist = setOf("frieren", "solo"), progress = emptyMap(),audio="ja",subtitles="en",autoplay=true,haptics=true) }
            vm.search("")
            vm.filter("All")
            vm.navigate("Home")
            repeat(vm.catalogue.shows.size) { if (vm.featuredId.value != "frieren") vm.advanceFeatured() }
        }
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("home-banner").fetchSemanticsNodes().isNotEmpty() }
        capture("home")
        compose.onNodeWithContentDescription("Browse").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("SOLO LEVELING").fetchSemanticsNodes().isNotEmpty() }
        capture("browse")
        compose.onNodeWithContentDescription("My Content").performClick()
        compose.onNodeWithTag("content-tab-Watchlist").performClick()
        compose.onNodeWithText("FRIEREN").assertExists()
        capture("library")
        compose.onNodeWithContentDescription("Profile").performClick()
        compose.onNodeWithText("Who’s watching?").assertExists()
        capture("profile")
        compose.onNodeWithText("Settings", substring = false).performScrollTo().performClick()
        compose.onNodeWithText("Motion intensity").assertExists()
        capture("settings")
        compose.onNodeWithText("Back to profile").performClick()
        compose.onNodeWithContentDescription("Browse").performClick()
        compose.runOnUiThread { vm.show("frieren", "browse-frieren") }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("details-list").fetchSemanticsNodes().isNotEmpty() }
        capture("details")
        compose.onNodeWithTag("details-list").performScrollToIndex(1)
        capture("episodes")
        compose.onNodeWithTag("details-list").performScrollToIndex(0)
        compose.onNodeWithContentDescription("Back to catalogue").performClick()
    }

    private fun capture(name: String) {
        compose.mainClock.advanceTimeBy(1200)
        compose.waitForIdle()
        compose.onRoot().captureToImage()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val phase = InstrumentationRegistry.getArguments().getString("screenshotPhase") ?: "current"
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "interface-$phase").apply { mkdirs() }
        instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
            File(directory, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
