package dev.cinema.crunchyroll

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import dev.cinema.crunchyroll.data.MotionLevel
import dev.cinema.crunchyroll.data.account.AccountState
import dev.cinema.crunchyroll.ui.CinemaViewModel
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class InterfaceNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun profileDestinationsKeepTheCorrectNavigationSelection() {
        lateinit var vm: CinemaViewModel
        compose.runOnUiThread { vm = ViewModelProvider(compose.activity)[CinemaViewModel::class.java] }
        compose.waitUntil(30_000) { vm.ready.value && vm.account.value !is AccountState.Restoring }
        assertFalse("Use a demo emulator", vm.isConnected)
        compose.runOnUiThread {
            vm.enterDemo()
            vm.preferences { it.copy(motion = MotionLevel.Reduced) }
            vm.navigate("Profile")
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Who’s watching?").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Settings", substring = false).performScrollTo().performClick()
        compose.onNodeWithContentDescription("Profile").assertIsSelected()
        compose.onNodeWithContentDescription("Home").assertIsNotSelected()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Motion intensity").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Profile").assertIsSelected()
        compose.onNodeWithText("Back to profile").performClick()
        compose.onNodeWithText("History",substring=false).performScrollTo().performClick()
        compose.onNodeWithContentDescription("My Content").assertIsSelected()
        compose.onNodeWithTag("content-tab-History").assertIsSelected()
        compose.onNodeWithContentDescription("Profile").assertIsNotSelected()
        compose.onNodeWithContentDescription("Browse").performClick()
        compose.onNodeWithContentDescription("Browse").assertIsSelected()
        compose.onNodeWithContentDescription("Profile").assertIsNotSelected()
    }
}
