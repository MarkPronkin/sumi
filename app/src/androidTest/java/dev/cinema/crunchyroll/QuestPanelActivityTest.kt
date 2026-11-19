package dev.cinema.crunchyroll

import android.content.pm.ActivityInfo
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.core.view.allViews
import androidx.lifecycle.ViewModelProvider
import androidx.media3.ui.PlayerView
import dev.cinema.crunchyroll.data.account.AccountState
import dev.cinema.crunchyroll.ui.CinemaViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class QuestPanelActivityTest {
    @get:Rule val compose = createAndroidComposeRule<QuestPanelActivity>()

    @Test fun browsingAndPlaybackShareTheLandscapePanel() {
        val vm = compose.runOnIdle { ViewModelProvider(compose.activity)[CinemaViewModel::class.java] }
        compose.waitUntil(15_000) { vm.ready.value && vm.account.value !is AccountState.Restoring }
        compose.runOnUiThread {
            vm.enterDemo()
            vm.show("frieren")
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("details-list").fetchSemanticsNodes().isNotEmpty() }
        compose.runOnIdle {
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, compose.activity.requestedOrientation)
            vm.play("frieren-s1-e1", true)
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("next-chapter").fetchSemanticsNodes().isNotEmpty() }
        compose.runOnIdle {
            val player = compose.activity.window.decorView.allViews.filterIsInstance<PlayerView>().single()
            assertTrue(player.width > 0)
            assertEquals(player.width * 9f / 16f, player.height.toFloat(), 1f)
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, compose.activity.requestedOrientation)
            vm.back()
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("details-list").fetchSemanticsNodes().isNotEmpty() }
        compose.runOnIdle { assertEquals(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, compose.activity.requestedOrientation) }
    }
}
