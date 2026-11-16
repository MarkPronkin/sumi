package dev.cinema.crunchyroll

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import dev.cinema.crunchyroll.data.MotionLevel
import dev.cinema.crunchyroll.design.LocalMotion
import dev.cinema.crunchyroll.design.Oled
import dev.cinema.crunchyroll.playback.PlaybackStartAnimation
import dev.cinema.crunchyroll.playback.PlaybackStartFeedback
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class PlaybackStartFeedbackTest {
    private var durationScale = 1f
    @get:Rule val compose = createComposeRule(effectContext = object : MotionDurationScale {
        override val scaleFactor: Float get() = durationScale
    })

    /** Only the Player API used by the feedback observer; no streams or network. */
    private class PlayIntentFixture {
        private val listeners = mutableListOf<Player.Listener>()
        private var play = false
        var episodeId = "episode"
        val player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { proxy, method, args ->
            when (method.name) {
                "getCurrentMediaItem" -> MediaItem.Builder().setMediaId(episodeId).build()
                "getPlayWhenReady" -> play
                "getPlaybackState" -> Player.STATE_READY
                "addListener" -> { listeners.add(args!![0] as Player.Listener); null }
                "removeListener" -> { listeners.remove(args!![0] as Player.Listener); null }
                "hashCode" -> System.identityHashCode(this)
                "equals" -> args!![0] === proxy
                "toString" -> "PlayIntentFixture"
                else -> error("Unexpected Player call: ${method.name}")
            }
        } as Player

        fun intent(playing: Boolean) {
            play = playing
            listeners.toList().forEach { it.onPlayWhenReadyChanged(playing, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) }
        }

        fun bufferAndRecover() {
            listeners.toList().forEach {
                it.onIsPlayingChanged(false)
                it.onPlaybackStateChanged(Player.STATE_BUFFERING)
                it.onPlaybackStateChanged(Player.STATE_READY)
                it.onIsPlayingChanged(true)
            }
        }
    }

    private class ResumedOwner : LifecycleOwner {
        override val lifecycle = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
    }

    @Test fun entryAndResumeAreFiniteAndTheOverlayDoesNotInterceptTouchesOrResizeTheSurface() {
        val fixture = PlayIntentFixture()
        val owner = ResumedOwner()
        var touches = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalMotion provides MotionLevel.Maximal, LocalLifecycleOwner provides owner) {
                Box(Modifier.fillMaxSize().background(Oled)) {
                    Box(Modifier.fillMaxSize().testTag("surface-fixture").clickable { touches++ })
                    PlaybackStartFeedback("main:episode", "episode", fixture.player, true, Modifier.align(Alignment.Center))
                }
            }
        }
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithTag("playback-start-feedback").assertExists()
        val bounds = compose.onNodeWithTag("surface-fixture").fetchSemanticsNode().boundsInRoot
        compose.mainClock.advanceTimeBy(800)
        compose.onNodeWithTag("playback-start-feedback").assertDoesNotExist()
        compose.runOnUiThread { fixture.intent(true) }
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithTag("playback-start-feedback").assertDoesNotExist()
        compose.runOnUiThread { fixture.intent(false); fixture.intent(true) }
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithTag("playback-start-feedback").assertExists()
        compose.onNodeWithTag("surface-fixture").performTouchInput { click(center) }
        compose.runOnIdle { assertEquals(1, touches) }
        assertEquals(bounds, compose.onNodeWithTag("surface-fixture").fetchSemanticsNode().boundsInRoot)
        compose.mainClock.advanceTimeBy(800)
        compose.runOnUiThread { fixture.bufferAndRecover() }
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithTag("playback-start-feedback").assertDoesNotExist()
    }

    @Test fun restorationDoesNotReplayEntryAndAnExistingPauseCanStillResume() {
        val fixture = PlayIntentFixture()
        val owner = ResumedOwner()
        var registry by mutableStateOf(SaveableStateRegistry(null) { true })
        var contentVisible by mutableStateOf(true)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalMotion provides MotionLevel.Standard, LocalLifecycleOwner provides owner,
                LocalSaveableStateRegistry provides registry) {
                if (contentVisible) Box(Modifier.fillMaxSize()) {
                    PlaybackStartFeedback("main:episode", "episode", fixture.player, true, Modifier.align(Alignment.Center))
                }
            }
        }
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithTag("playback-start-feedback").assertExists()
        var saved: Map<String, List<Any?>> = emptyMap()
        compose.runOnIdle {
            saved = registry.performSave()
            contentVisible = false
        }
        // Drive disposal explicitly: the standard restoration helper coalesces its
        // remove/re-add updates when the test animation clock is paused.
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithTag("playback-start-feedback").assertDoesNotExist()
        compose.runOnIdle { registry = SaveableStateRegistry(saved) { true }; contentVisible = true }
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithTag("playback-start-feedback").assertDoesNotExist()
        compose.runOnUiThread { fixture.intent(true) }
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithTag("playback-start-feedback").assertExists()
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithTag("playback-start-feedback").assertDoesNotExist()
    }

    @Test fun changingEpisodeGetsOneFreshEntryWithoutReusingThePreviousAnimationTrigger() {
        val fixture = PlayIntentFixture()
        val owner = ResumedOwner()
        var episodeId by mutableStateOf("episode")
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalMotion provides MotionLevel.Standard, LocalLifecycleOwner provides owner) {
                Box(Modifier.fillMaxSize()) {
                    PlaybackStartFeedback("main:$episodeId", episodeId, fixture.player, true, Modifier.align(Alignment.Center))
                }
            }
        }
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithTag("playback-start-feedback").assertDoesNotExist()
        compose.runOnUiThread { fixture.episodeId = "next-episode"; episodeId = "next-episode" }
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithTag("playback-start-feedback").assertExists()
        compose.mainClock.advanceTimeBy(400)
        compose.runOnUiThread { fixture.intent(true); fixture.bufferAndRecover() }
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithTag("playback-start-feedback").assertDoesNotExist()
    }

    @Test fun reducedMotionOnlyFadesWithoutChangingTheDrawnBadgeGeometry() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalMotion provides MotionLevel.Reduced) {
                Box(Modifier.fillMaxSize().background(Oled)) {
                    PlaybackStartAnimation(1, entry = false, modifier = Modifier.align(Alignment.Center))
                }
            }
        }
        compose.mainClock.advanceTimeBy(64)
        val first = orangeBounds()
        compose.mainClock.advanceTimeBy(32)
        assertEquals(first, orangeBounds())
        compose.mainClock.advanceTimeBy(200)
        compose.onNodeWithTag("playback-start-feedback").assertDoesNotExist()
    }

    private fun orangeBounds(): List<Int> {
        val pixels = compose.onNodeWithTag("playback-start-feedback").captureToImage().toPixelMap()
        var brightest = 0f
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) brightest = maxOf(brightest, pixels[x, y].red)
        assertTrue("The play badge is visible", brightest > .1f)
        val points = buildList {
            for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                if (pixels[x, y].red > brightest * .6f) add(x to y)
            }
        }
        return listOf(points.minOf { it.first }, points.minOf { it.second }, points.maxOf { it.first }, points.maxOf { it.second })
    }

    @Test fun errorsCancelFeedbackAndDisabledSystemAnimationsSkipIt() {
        var trigger by mutableIntStateOf(1)
        var enabled by mutableStateOf(true)
        compose.mainClock.autoAdvance = false
        compose.setContent { PlaybackStartAnimation(trigger, entry = false, enabled = enabled) }
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithTag("playback-start-feedback").assertExists()
        compose.runOnUiThread { enabled = false }
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithTag("playback-start-feedback").assertDoesNotExist()
        compose.runOnUiThread { durationScale = 0f; trigger++; enabled = true }
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithTag("playback-start-feedback").assertDoesNotExist()
    }
}
