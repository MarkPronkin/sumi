package dev.cinema.crunchyroll

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import dev.cinema.crunchyroll.data.MotionLevel
import dev.cinema.crunchyroll.design.*
import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Pixel checks include the shared overlay, where the parent card's clip no longer applies. */
class SharedArtworkCornerTest {
    @get:Rule val compose = createComposeRule()
    private var expanded by mutableStateOf(false)
    private var density = 1f

    private fun setup(roundedOrigin: Boolean = true) {
        compose.setContent {
            density = LocalDensity.current.density
            CinemaTheme(MotionLevel.Maximal, false, false) {
                Box(Modifier.fillMaxSize().background(Color.Black).testTag("corner-stage")) {
                    SharedTransitionLayout(Modifier.fillMaxSize().padding(32.dp)) {
                        val shared = this
                        AnimatedContent(expanded, transitionSpec = {
                            EnterTransition.None togetherWith ExitTransition.None
                        }, label = "corner-fixture") { detail ->
                            CompositionLocalProvider(
                                LocalSharedScope provides shared,
                                LocalSceneScope provides this,
                                LocalArtworkOriginRounded provides roundedOrigin,
                            ) {
                                Box(Modifier.size(if(detail) 260.dp else 120.dp, if(detail) 330.dp else 180.dp)
                                    .clip(RoundedCornerShape(if(!detail && roundedOrigin) ArtworkCardCorner else 0.dp))) {
                                    Box(Modifier.fillMaxSize().sharedArt("fixture", rounded = !detail && roundedOrigin).background(Color.White))
                                }
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
    }

    /** Distance from the top-left edge to the first visible artwork pixel, in dp. */
    private fun cornerInset(): Float {
        val pixels = compose.onNodeWithTag("corner-stage").captureToImage().toPixelMap()
        val left = (32 * density).roundToInt()
        val y = left + 1
        val x = (left until left + (40 * density).roundToInt()).first { pixels[it, y].red > .1f }
        return (x - left) / density
    }

    private fun advance(millis: Long): Float {
        compose.mainClock.advanceTimeBy(millis)
        return cornerInset()
    }

    @Test fun cornersMorphGraduallyInBothDirections() {
        setup()
        val card = cornerInset()
        compose.runOnUiThread { expanded = true }
        val opening = listOf(advance(32), advance(128), advance(1200))
        assertTrue("Card starts rounded: $card", card > 5f)
        assertTrue("Opening keeps rounded corners in the overlay: $opening", opening[0] > 3f)
        assertTrue("Corners gradually straighten: $opening", opening[1] < opening[0] && opening[1] > opening[2])
        assertTrue("Expanded artwork finishes square: $opening", opening[2] < 1f)
        compose.runOnUiThread { expanded = false }
        val closing = listOf(advance(32), advance(128), advance(1200))
        assertTrue("Return gradually restores rounded corners: $closing", closing[0] < closing[1] && closing[1] < closing[2])
        assertTrue("Returned card retains its original shape: $closing", abs(closing[2] - card) < 1f)
    }

    @Test fun reversingMidAnimationDoesNotResetTheCorners() {
        setup()
        compose.runOnUiThread { expanded = true }
        val before = advance(128)
        compose.runOnUiThread { expanded = false }
        val after = advance(16)
        assertTrue("Reversal must keep the in-flight radius: $before -> $after", abs(after - before) < 2f)
        assertTrue("Reversal finishes at the rounded card", advance(1200) > 5f)
    }

    @Test fun squareSpotlightDoesNotAcquireRoundedCorners() {
        setup(roundedOrigin = false)
        compose.runOnUiThread { expanded = true }
        listOf(32L, 128L, 1200L).forEach { assertTrue("Square artwork stays square", advance(it) < 1f) }
        compose.runOnUiThread { expanded = false }
        listOf(32L, 128L, 1200L).forEach { assertTrue("Square artwork stays square on return", advance(it) < 1f) }
    }
}
