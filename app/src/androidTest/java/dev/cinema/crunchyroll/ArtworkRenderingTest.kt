package dev.cinema.crunchyroll

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import dev.cinema.crunchyroll.data.Anime
import dev.cinema.crunchyroll.ui.Art
import dev.cinema.crunchyroll.ui.SeriesTitle
import dev.cinema.crunchyroll.design.CinemaTheme
import dev.cinema.crunchyroll.data.MotionLevel
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.SemanticsProperties
import java.io.File
import org.junit.Rule
import org.junit.Test

/** Checks the pixels loaded by Coil, so a tall viewport cannot hide a landscape-source regression. */
class ArtworkRenderingTest {
    @get:Rule val compose=createComposeRule()

    @Test fun titleLogoUsesReadableFallbackForMissingOrFailedArtwork() {
        val cache=InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        var show by mutableStateOf(Anime("missing-logo","Readable title","","",2026,listOf("Anime"),R.drawable.ic_cinema))
        compose.setContent {
            CinemaTheme(MotionLevel.Reduced,false,false) {
                SeriesTitle(show,fontSize=32.sp,logoOnPhone=true,compact=true)
            }
        }
        compose.onNodeWithText("Readable title").assertIsDisplayed()
        compose.onNodeWithTag("series-title-logo").assertDoesNotExist()
        compose.runOnIdle {show=show.copy(title="Unavailable logo",logoUrl=File(cache,"nonexistent-title-logo.png").toURI().toString())}
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("series-title-logo").fetchSemanticsNodes().any {
                it.config.getOrElse(SemanticsProperties.StateDescription) {""}=="Title artwork unavailable"
            }
        }
        compose.onNodeWithText("Unavailable logo",useUnmergedTree=true).assertIsDisplayed()
        val logo=File(cache,"replacement-title-logo.png")
        Bitmap.createBitmap(400,100,Bitmap.Config.ARGB_8888).apply {
            eraseColor(AndroidColor.GREEN)
            logo.outputStream().use {compress(Bitmap.CompressFormat.PNG,100,it)}
            recycle()
        }
        compose.runOnIdle {show=show.copy(title="Next title",logoUrl=logo.toURI().toString())}
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("series-title-logo").fetchSemanticsNodes().any {
                it.config.getOrElse(SemanticsProperties.StateDescription) {""}=="Title artwork loaded"
            }
        }
        compose.onNode(hasContentDescription("Next title") and hasAnyAncestor(hasTestTag("series-title-logo")),useUnmergedTree=true).assertIsDisplayed()
        compose.onNodeWithText("Unavailable logo",useUnmergedTree=true).assertDoesNotExist()
    }

    @Test fun mobileLoadsPortraitWhileWideLoadsHeroAndBrokenPortraitFallsBack() {
        val cache=InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        fun fixture(name: String,color: Int,width: Int,height: Int): String {
            val file=File(cache,name)
            Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888).apply {
                eraseColor(color)
                file.outputStream().use {compress(Bitmap.CompressFormat.PNG,100,it)}
                recycle()
            }
            return file.toURI().toString()
        }
        val portrait=fixture("portrait-fixture.png",AndroidColor.RED,160,240)
        val wide=fixture("wide-fixture.png",AndroidColor.BLUE,320,180)
        val show=Anime("art-fixture","Artwork fixture","","",2026,listOf("Anime"),R.drawable.ic_cinema,
            posterUrl=portrait,heroUrl=wide,artworkUrl=wide)
        var mobile by mutableStateOf(true)
        var broken by mutableStateOf(false)
        compose.setContent {
            Box(Modifier.size(200.dp,250.dp).testTag("art-fixture")) {
                Art(if(broken) show.copy(posterUrl=File(cache,"absent-portrait.png").toURI().toString()) else show,
                    Modifier.fillMaxSize(),poster=mobile,hero=true)
            }
        }
        fun awaitColor(red: Boolean) {
            compose.waitUntil(10_000) {
                val pixels=compose.onNodeWithTag("art-fixture").captureToImage().toPixelMap()
                val color=pixels[pixels.width/2,pixels.height/2]
                if(red) color.red>.9f && color.blue<.1f else color.blue>.9f && color.red<.1f
            }
        }
        awaitColor(red=true)
        compose.runOnIdle {mobile=false}
        awaitColor(red=false)
        compose.runOnIdle {mobile=true;broken=true}
        awaitColor(red=false)
    }
}
