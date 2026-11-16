package dev.cinema.crunchyroll

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import dev.cinema.crunchyroll.data.Anime
import dev.cinema.crunchyroll.data.MotionLevel
import dev.cinema.crunchyroll.design.*
import dev.cinema.crunchyroll.ui.Action
import dev.cinema.crunchyroll.ui.IconAction
import dev.cinema.crunchyroll.ui.ShowCard
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class HapticFeedbackUiTest {
    @get:Rule val compose = createComposeRule()
    private var hapticsEnabled by mutableStateOf(true)
    private var television by mutableStateOf(false)
    private var callbackCount = 0
    private lateinit var view: RecordingHapticView
    private lateinit var feedback: CinemaHaptics

    private fun setup(deviceTv: Boolean = false) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration = Configuration(context.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_TYPE_MASK.inv()) or
                if (deviceTv) Configuration.UI_MODE_TYPE_TELEVISION else Configuration.UI_MODE_TYPE_NORMAL
        }
        view = RecordingHapticView(context.createConfigurationContext(configuration))
        val show = Anime("fixture", "Series fixture", "", "", 2026, emptyList(), R.drawable.frieren)
        compose.setContent {
            CinemaTheme(MotionLevel.Reduced, television, hapticsEnabled) {
                val parent = LocalView.current.rootView as ViewGroup
                DisposableEffect(parent) {
                    parent.addView(view, ViewGroup.LayoutParams(0, 0))
                    onDispose { parent.removeView(view) }
                }
                CompositionLocalProvider(LocalView provides view) {
                    feedback = rememberCinemaHaptics()
                    Column {
                        Box(Modifier.size(48.dp).testTag("generic").cinemaClick("Generic", true, { callbackCount++ }))
                        Action("View episodes", modifier = Modifier.testTag("episodes"), haptic = CinemaHaptic.SeriesOpen) { callbackCount++ }
                        IconAction("Details", Glyph.Info, modifier = Modifier.testTag("details"), haptic = CinemaHaptic.SeriesOpen) { callbackCount++ }
                        ShowCard(show, "fixture", "", modifier = Modifier.testTag("series")) { callbackCount++ }
                    }
                }
            }
        }
    }

    @Test fun seriesControlsEmitOneCustomEffectWithoutTheGenericClick() {
        setup()
        compose.onNodeWithTag("generic").performClick()
        compose.onNodeWithTag("episodes").performClick()
        compose.onNodeWithTag("details").performClick()
        compose.onNodeWithTag("series").performClick()
        compose.runOnIdle {
            val opening = if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CONTEXT_CLICK
            assertEquals(listOf(HapticFeedbackConstants.TEXT_HANDLE_MOVE, opening, opening, opening), view.effects)
            assertEquals(4, callbackCount)
            assertTrue("No system or view preference override flags", view.flags.all { it == 0 })
        }
    }

    @Test fun currentPreferenceAndTelevisionPolicyApplyToClicksAndAnExistingScrubEmitter() {
        setup()
        lateinit var original: CinemaHaptics
        compose.runOnIdle { original = feedback; hapticsEnabled = false }
        compose.onNodeWithTag("episodes").performClick()
        compose.runOnIdle {
            original.perform(CinemaHaptic.TimelineTick)
            assertTrue(view.effects.isEmpty())
            hapticsEnabled = true
            television = true
        }
        compose.onNodeWithTag("episodes").performClick()
        compose.runOnIdle {
            original.perform(CinemaHaptic.TimelineTick)
            assertTrue(view.effects.isEmpty())
            television = false
        }
        compose.runOnIdle {
            assertSame(original, feedback)
            original.perform(CinemaHaptic.TimelineTick)
            val tick = if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_FREQUENT_TICK else HapticFeedbackConstants.CLOCK_TICK
            assertEquals(listOf(tick), view.effects)
            assertEquals(2, callbackCount)
        }
    }

    @Test fun anActualTelevisionConfigurationCannotBeOverriddenByTheComposition() {
        setup(deviceTv = true)
        compose.onNodeWithTag("episodes").performClick()
        compose.runOnIdle {
            feedback.perform(CinemaHaptic.TimelineTick)
            assertTrue(view.effects.isEmpty())
            assertEquals(1, callbackCount)
        }
    }

    @Test fun platformEffectsHaveDistinctRecipesAndVersionAppropriateFallbacks() {
        for (sdk in listOf(26, 29, 30, 33, 34, 37)) {
            val opening = platformHapticEffect(CinemaHaptic.SeriesOpen, sdk)
            val tick = platformHapticEffect(CinemaHaptic.TimelineTick, sdk)
            assertEquals(if (sdk >= 27) HapticFeedbackConstants.TEXT_HANDLE_MOVE else HapticFeedbackConstants.CLOCK_TICK,
                platformHapticEffect(CinemaHaptic.Click, sdk))
            assertEquals(if (sdk >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CONTEXT_CLICK, opening)
            assertEquals(if (sdk >= 34) HapticFeedbackConstants.SEGMENT_FREQUENT_TICK else HapticFeedbackConstants.CLOCK_TICK, tick)
            assertNotEquals(opening, tick)
            assertNotEquals(platformHapticEffect(CinemaHaptic.Click, sdk), opening)
        }
    }

    private class RecordingHapticView(context: Context) : View(context) {
        val effects = mutableListOf<Int>()
        val flags = mutableListOf<Int>()
        override fun performHapticFeedback(feedbackConstant: Int, flags: Int): Boolean {
            effects += feedbackConstant
            this.flags += flags
            return true
        }
    }
}
