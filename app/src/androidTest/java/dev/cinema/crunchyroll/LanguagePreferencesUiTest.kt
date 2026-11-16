package dev.cinema.crunchyroll

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import dev.cinema.crunchyroll.data.LanguagePreferences
import dev.cinema.crunchyroll.data.MotionLevel
import dev.cinema.crunchyroll.design.CinemaTheme
import dev.cinema.crunchyroll.ui.LanguagePreferenceSelector
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LanguagePreferencesUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun longListScrollsAndReopensAtTheSavedLanguage() {
        var saved = "ja"
        compose.setContent {
            var value by remember { mutableStateOf(saved) }
            CinemaTheme(MotionLevel.Reduced, false, false) {
                LanguagePreferenceSelector("Preferred audio", value, LanguagePreferences.audioOptions) {
                    saved = it; value = it
                }
            }
        }
        compose.onNodeWithContentDescription("Preferred audio: Japanese").performClick()
        compose.onNodeWithTag("language-options").performScrollToNode(hasContentDescription("Malay"))
        compose.onNodeWithContentDescription("Malay").performClick()
        compose.runOnIdle { assertEquals("ms-MY", saved) }
        compose.onNodeWithTag("language-options").assertDoesNotExist()
        compose.onNodeWithContentDescription("Preferred audio: Malay").performClick()
        compose.onNodeWithContentDescription("Malay").assertIsDisplayed().assertIsSelected()
    }

    @Test fun subtitlesOffRemainsSelectableAndSaved() {
        var saved = "en"
        compose.setContent {
            var value by remember { mutableStateOf(saved) }
            CinemaTheme(MotionLevel.Reduced, false, false) {
                LanguagePreferenceSelector("Preferred subtitles", value, LanguagePreferences.subtitleOptions) {
                    saved = it; value = it
                }
            }
        }
        compose.onNodeWithContentDescription("Preferred subtitles: English").performClick()
        compose.onNodeWithTag("language-options").performScrollToNode(hasContentDescription("Off"))
        compose.onNodeWithContentDescription("Off").performClick()
        compose.runOnIdle { assertEquals("off", saved) }
        compose.onNodeWithContentDescription("Preferred subtitles: Off").performClick()
        compose.onNodeWithContentDescription("Off").assertIsSelected()
    }

    @Test fun tvFocusStartsAtSavedRegionalLanguageAndDpadCanSelectTheNextOption() {
        var saved = "pt-PT"
        compose.setContent {
            var value by remember { mutableStateOf(saved) }
            CinemaTheme(MotionLevel.Reduced, true, false) {
                LanguagePreferenceSelector("Preferred audio", value, LanguagePreferences.audioOptions) {
                    saved = it; value = it
                }
            }
        }
        compose.onNodeWithContentDescription("Preferred audio: Portuguese (Portugal)").performClick()
        compose.onNodeWithContentDescription("Portuguese (Portugal)").assertIsFocused().assertIsSelected()
            .performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithContentDescription("French").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals("fr-FR", saved) }
        compose.onNodeWithTag("language-options").assertDoesNotExist()
    }
}
