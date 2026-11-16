package dev.cinema.crunchyroll

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import dev.cinema.crunchyroll.data.MotionLevel
import dev.cinema.crunchyroll.design.CinemaTheme
import dev.cinema.crunchyroll.playback.PlayerSeekGestureLayer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PlayerSeekGestureTest {
    @get:Rule val compose = createComposeRule()
    private val seeks = mutableListOf<Int>()
    private var toggles = 0
    private var buttons = 0
    private var sliderChanges = 0

    private fun setup(seekEnabled: Boolean = true, withControls: Boolean = false, rtl: Boolean = false) {
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides if(rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
            CinemaTheme(MotionLevel.Reduced, false, false) {
                Box(Modifier.fillMaxSize()) {
                    PlayerSeekGestureLayer(15, seekEnabled, false, {toggles++}, {seeks+=it})
                    if(withControls) {
                        Button(onClick={buttons++}, modifier=Modifier.align(Alignment.CenterStart).testTag("test-button")) {Text("Button")}
                        var value by remember {mutableFloatStateOf(.5f)}
                        Slider(value, onValueChange={value=it;sliderChanges++},
                            modifier=Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp).testTag("test-slider"))
                    }
                }
            }
            }
        }
    }

    @Test fun sideDoubleTapsSeekOnceInTheCorrectDirectionAndShowTimeFeedback() {
        setup()
        val layer = compose.onNodeWithTag("player-gesture-layer")
        layer.performTouchInput {doubleClick(Offset(width * .15f, height * .5f))}
        compose.runOnIdle {assertEquals(listOf(-1), seeks);assertEquals(0, toggles)}
        compose.onNodeWithText("−15 s").assertIsDisplayed()
        layer.performTouchInput {advanceEventTime(400);doubleClick(Offset(width * .85f, height * .5f))}
        compose.runOnIdle {assertEquals(listOf(-1, 1), seeks);assertEquals(0, toggles)}
        compose.onNodeWithText("+15 s").assertIsDisplayed()
    }

    @Test fun singleTapsToggleControlsAndCenterDoubleTapsNeverSeek() {
        setup()
        val layer = compose.onNodeWithTag("player-gesture-layer")
        layer.performTouchInput {click(Offset(width * .15f, height * .5f))}
        compose.waitUntil(3000) {toggles==1}
        layer.performTouchInput {advanceEventTime(400);doubleClick(center)}
        compose.runOnIdle {assertTrue(seeks.isEmpty());assertTrue(toggles>1)}
    }

    @Test fun tapsOnDifferentSidesDoNotBecomeASeek() {
        setup()
        compose.onNodeWithTag("player-gesture-layer").performTouchInput {
            click(Offset(width * .15f, height * .5f))
            advanceEventTime(100)
            click(Offset(width * .85f, height * .5f))
        }
        compose.waitUntil(3000) {toggles>=1}
        compose.runOnIdle {assertTrue(seeks.isEmpty())}
    }

    @Test fun physicalLeftStillRewindsInRightToLeftLayouts() {
        setup(rtl=true)
        compose.onNodeWithTag("player-gesture-layer").performTouchInput {
            doubleClick(Offset(width * .15f, height * .5f))
        }
        compose.runOnIdle {assertEquals(listOf(-1), seeks)}
    }

    @Test fun controlsAndScrubbingConsumeTheirOwnTouches() {
        setup(withControls=true)
        compose.onNodeWithTag("test-button").performTouchInput {doubleClick(center)}
        compose.onNodeWithTag("test-slider").performTouchInput {
            swipe(Offset(width * .2f, height * .5f), Offset(width * .8f, height * .5f))
        }
        compose.runOnIdle {
            assertEquals(2, buttons)
            assertTrue(sliderChanges>0)
            assertTrue(seeks.isEmpty())
            assertEquals(0, toggles)
        }
    }

    @Test fun unreadyPlaybackCannotSeekByTouchOrAccessibility() {
        setup(seekEnabled=false)
        val layer = compose.onNodeWithTag("player-gesture-layer")
        layer.performTouchInput {doubleClick(Offset(width * .85f, height * .5f))}
        compose.runOnIdle {assertTrue(seeks.isEmpty())}
        assertTrue(layer.fetchSemanticsNode().config[SemanticsActions.CustomActions].isEmpty())
    }

    @Test fun accessibilityActionsUseTheConfiguredInterval() {
        setup()
        val actions = compose.onNodeWithTag("player-gesture-layer").fetchSemanticsNode().config[SemanticsActions.CustomActions]
        compose.runOnIdle {
            assertEquals(listOf("Rewind 15 seconds", "Forward 15 seconds"), actions.map {it.label})
            assertTrue(actions[0].action())
            assertTrue(actions[1].action())
        }
        compose.runOnIdle {assertEquals(listOf(-1, 1), seeks)}
    }
}
