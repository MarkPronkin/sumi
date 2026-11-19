package dev.cinema.crunchyroll

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
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
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.intercept.Interceptor
import kotlinx.coroutines.CompletableDeferred
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Checks the pixels loaded by Coil, so a tall viewport cannot hide a landscape-source regression. */
class ArtworkRenderingTest {
    @get:Rule val compose=createComposeRule()

    @OptIn(DelicateCoilApi::class)
    @Test fun pendingPosterAndTitleShimmerUntilResolvedAndRespectReducedMotion() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val originalLoader = SingletonImageLoader.get(context)
        val release = CompletableDeferred<Unit>()
        val loader = ImageLoader.Builder(context).components {
            add(Interceptor { chain ->
                release.await()
                chain.proceed()
            })
        }.build()
        val resource = "android.resource://${context.packageName}"
        val show = Anime("pending", "Waiting for artwork", "", "", 2026, listOf("Anime"), R.drawable.ic_cinema,
            posterUrl = "$resource/${R.drawable.frieren_poster}", logoUrl = "$resource/${R.drawable.ic_sumi_mark}")
        var motion by mutableStateOf(MotionLevel.Maximal)
        fun frame(tag: String) = compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap()

        SingletonImageLoader.setUnsafe(loader)
        compose.mainClock.autoAdvance = false
        try {
            compose.setContent {
                CinemaTheme(motion, false, false) {
                    Column {
                        Art(show, Modifier.size(140.dp, 210.dp).testTag("poster"), poster = true)
                        SeriesTitle(show, 32.sp, logoOnPhone = true, compact = true)
                    }
                }
            }
            compose.mainClock.advanceTimeBy(32)
            compose.onNodeWithText(show.title, useUnmergedTree = true).assertIsDisplayed()
            val poster = frame("poster")
            val title = frame("series-title-logo")
            compose.mainClock.advanceTimeBy(880)
            assertFalse("The pending poster has a moving highlight", poster.sameAs(frame("poster")))
            assertFalse("The readable title has a moving highlight", title.sameAs(frame("series-title-logo")))

            compose.runOnIdle { motion = MotionLevel.Reduced }
            compose.mainClock.advanceTimeBy(32)
            val stillPoster = frame("poster")
            val stillTitle = frame("series-title-logo")
            compose.mainClock.advanceTimeBy(880)
            assertTrue(stillPoster.sameAs(frame("poster")))
            assertTrue(stillTitle.sameAs(frame("series-title-logo")))

            release.complete(Unit)
            compose.mainClock.autoAdvance = true
            compose.waitUntil(10_000) {
                compose.onNodeWithTag("series-title-logo").fetchSemanticsNode().config
                    .getOrElse(SemanticsProperties.StateDescription) { "" } == "Title artwork loaded"
                    && !stillPoster.sameAs(frame("poster"))
            }
            compose.onNodeWithText(show.title, useUnmergedTree = true).assertDoesNotExist()
        } finally {
            release.complete(Unit)
            compose.mainClock.autoAdvance = true
            SingletonImageLoader.setUnsafe(originalLoader)
            loader.shutdown()
        }
    }

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
