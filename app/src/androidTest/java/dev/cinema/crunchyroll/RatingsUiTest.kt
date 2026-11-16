package dev.cinema.crunchyroll

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.cinema.crunchyroll.data.*
import dev.cinema.crunchyroll.design.*
import dev.cinema.crunchyroll.ui.ShowRatingsSection
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class RatingsUiTest {
    @get:Rule val compose=createComposeRule()

    @Test fun sourceScoresAndMalRankAreVisibleWithTheirOriginalScales() {
        compose.setContent {
            CinemaTheme(MotionLevel.Reduced,false,false) {
                Box(Modifier.width(380.dp).background(Oled).padding(16.dp)) {
                    ShowRatingsSection(demoRatings("frieren"),demo=true) {}
                }
            }
        }
        compose.onNodeWithText("Crunchyroll",useUnmergedTree=true).assertIsDisplayed()
        compose.onNodeWithText("MyAnimeList",useUnmergedTree=true).assertIsDisplayed()
        compose.onNodeWithText("IMDb",useUnmergedTree=true).assertIsDisplayed()
        compose.onNodeWithText("4.9",useUnmergedTree=true).assertIsDisplayed()
        compose.onNodeWithText("9.30",useUnmergedTree=true).assertIsDisplayed()
        compose.onNodeWithText("8.9",useUnmergedTree=true).assertIsDisplayed()
        compose.onNodeWithText("/5",useUnmergedTree=true).assertIsDisplayed()
        compose.onNodeWithText("Rank #1",useUnmergedTree=true).assertIsDisplayed()
        compose.onNodeWithText("Sample ratings · Demo data").assertIsDisplayed()
        compose.onNodeWithContentDescription("Retry ratings").assertDoesNotExist()
        val cards=RatingSource.entries.map {compose.onNodeWithTag("rating-${it.name}").fetchSemanticsNode().boundsInRoot}
        assertTrue("All three cards should fit on the same row: $cards",cards.all {kotlin.math.abs(it.top-cards.first().top)<1})
    }

    @Test fun loadingMissingAndFailedSourcesAreDistinctAndRetryWorks() {
        var retries=0
        compose.setContent {
            CinemaTheme(MotionLevel.Reduced,false,false) {
                Box(Modifier.width(360.dp).background(Oled).padding(16.dp)) {
                    ShowRatingsSection(ShowRatings("fixture",mapOf(
                        RatingSource.Crunchyroll to RatingState.Loading,
                        RatingSource.MyAnimeList to RatingState.Unavailable,
                        RatingSource.Imdb to RatingState.Error)),demo=false) {retries++}
                }
            }
        }
        compose.onNodeWithText("Loading…",useUnmergedTree=true).assertIsDisplayed()
        compose.onNodeWithText("Unavailable",useUnmergedTree=true).assertIsDisplayed()
        compose.onNodeWithText("Couldn’t load",useUnmergedTree=true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Retry ratings").performClick()
        compose.runOnIdle {assertEquals(1,retries)}
        compose.onNodeWithText("Sample ratings",substring=true).assertDoesNotExist()
    }

    @Test fun largeTextWrapsCardsWithoutTruncatingScoresOrRank() {
        compose.setContent {
            CinemaTheme(MotionLevel.Reduced,false,false) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density,2f)) {
                    Box(Modifier.width(320.dp).verticalScroll(rememberScrollState()).background(Oled).padding(16.dp)) {
                        ShowRatingsSection(demoRatings("frieren"),demo=true) {}
                    }
                }
            }
        }
        compose.onRoot().captureToImage().asAndroidBitmap().let {bitmap ->
            java.io.File(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),"ratings-large-text.png")
                .outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
        }
        val bounds=compose.onNodeWithTag("show-ratings").fetchSemanticsNode().boundsInRoot
        listOf("Crunchyroll","MyAnimeList","IMDb","4.9","9.30","8.9","Rank #1").forEach {text ->
            val node=compose.onNodeWithText(text,useUnmergedTree=true)
            node.performScrollTo().assertIsDisplayed()
            val child=node.fetchSemanticsNode().boundsInRoot
            assertTrue("$text must stay within the ratings section",child.left>=bounds.left && child.right<=bounds.right)
            node.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) {action ->
                val layouts=mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                action(layouts)
                // Intrinsic text widths can differ from the rounded layout size by a fraction of a pixel.
                assertTrue("$text must not be truncated",layouts.none {layout ->
                    layout.didOverflowHeight || (0 until layout.lineCount).any {layout.getLineRight(it)>layout.size.width+1}
                })
            }
        }
    }
}
