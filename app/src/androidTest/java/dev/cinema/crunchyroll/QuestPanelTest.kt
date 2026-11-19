package dev.cinema.crunchyroll

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import dev.cinema.crunchyroll.ui.CinemaWindowDialog
import dev.cinema.crunchyroll.ui.QuestPanel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class QuestPanelTest {
    @get:Rule val compose = createComposeRule()

    @Test fun resizingKeepsTheLargestCenteredWidescreenViewportAndBoundsDialogs() {
        val hostSize = mutableStateOf(DpSize(240.dp, 260.dp))
        val showDialog = mutableStateOf(false)
        compose.setContent {
            Box(Modifier.size(hostSize.value).testTag("host")) {
                QuestPanel {
                    Box(Modifier.fillMaxSize().testTag("viewport"))
                    if (showDialog.value) CinemaWindowDialog(onDismissRequest = {}) {
                        Box(Modifier.fillMaxSize().testTag("dialog-content"))
                    }
                }
            }
        }
        for (size in listOf(DpSize(240.dp, 260.dp), DpSize(300.dp, 120.dp), DpSize(256.dp, 144.dp))) {
            compose.runOnIdle { hostSize.value = size }
            val host = compose.onNodeWithTag("host").fetchSemanticsNode().boundsInRoot
            val viewport = compose.onNodeWithTag("viewport").fetchSemanticsNode().boundsInRoot
            assertEquals(minOf(host.width, host.height * 16f / 9f), viewport.width, 1f)
            assertEquals(viewport.width * 9f / 16f, viewport.height, 1f)
            assertEquals(host.center.x, viewport.center.x, 1f)
            assertEquals(host.center.y, viewport.center.y, 1f)
            compose.runOnIdle { showDialog.value = true }
            val dialog = compose.onNodeWithTag("dialog-content").fetchSemanticsNode().boundsInRoot
            assertTrue(dialog.width > 0 && dialog.width <= viewport.width + 1)
            assertEquals(viewport.height, dialog.height, 1f)
            compose.runOnIdle { showDialog.value = false }
        }
    }
}
