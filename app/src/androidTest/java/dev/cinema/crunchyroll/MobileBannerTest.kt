package dev.cinema.crunchyroll

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import dev.cinema.crunchyroll.data.*
import dev.cinema.crunchyroll.data.account.AccountState
import dev.cinema.crunchyroll.ui.CinemaViewModel
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Home uses landscape artwork while mobile details retain the complete portrait poster. */
class MobileBannerTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()

    @Test fun mobileHomeUsesCompactLandscapeArtworkAndDetailsKeepThePoster() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val poster=File(instrumentation.targetContext.cacheDir,"jujutsu-kaisen-poster.jpg")
        instrumentation.context.assets.open("jujutsu-kaisen-poster.jpg").use {input ->poster.outputStream().use {input.copyTo(it)}}
        val hero=File(instrumentation.targetContext.cacheDir,"landscape-hero.png")
        Bitmap.createBitmap(800,450,Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.RED)
            hero.outputStream().use {compress(Bitmap.CompressFormat.PNG,100,it)}
            recycle()
        }
        val logo=File(instrumentation.targetContext.cacheDir,"title-logo.png")
        Bitmap.createBitmap(640,160,Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.GREEN)
            logo.outputStream().use {compress(Bitmap.CompressFormat.PNG,100,it)}
            recycle()
        }
        val show=Anime("banner-fixture","JUJUTSU KAISEN","","Poster layout preview.",2020,listOf("Anime"),R.drawable.ic_cinema,
            seasons=4,posterUrl=poster.toURI().toString(),heroUrl=hero.toURI().toString(),logoUrl=logo.toURI().toString())
        val repository=object: CatalogueRepository {
            override val shows=listOf(show)
            override suspend fun search(query: String,category: String)=shows
            override fun episodes(showId: String,season: Int)=emptyList<Episode>()
        }
        lateinit var vm: CinemaViewModel
        compose.runOnUiThread {vm=ViewModelProvider(compose.activity)[CinemaViewModel::class.java]}
        compose.waitUntil(30_000) {vm.account.value !is AccountState.Restoring && vm.ready.value}
        if(vm.isConnected) {
            compose.runOnUiThread {vm.logout()}
            compose.waitUntil(15_000) {!vm.isConnected}
        }
        compose.runOnUiThread {
            vm.enterDemo();vm.preferences {it.copy(motion=MotionLevel.Reduced)}
            // Keep fixture injection confined to instrumentation rather than adding a production override.
            CinemaViewModel::class.java.getDeclaredMethod("setCatalogue",CatalogueRepository::class.java).apply {isAccessible=true}.invoke(vm,repository)
            vm.navigate("Home")
        }
        compose.waitUntil(15_000) {compose.onAllNodesWithTag("home-banner").fetchSemanticsNodes().isNotEmpty()}
        compose.waitForIdle()
        assertLogoLoaded()
        val banner=compose.onNodeWithTag("home-banner").fetchSemanticsNode().boundsInRoot
        assertTrue("Mobile Home should use a shallow 16:9 landscape banner",kotlin.math.abs(banner.width/banner.height-16f/9)<.02f)
        compose.waitUntil(10_000) {
            val pixels=compose.onNodeWithTag("home-banner").captureToImage().toPixelMap()
            val color=pixels[pixels.width/2,pixels.height/4]
            color.red>.8f && color.green<.1f && color.blue<.1f
        }
        compose.onNode(hasText("JUJUTSU KAISEN") and hasAnyAncestor(hasTestTag("home-banner"))).assertDoesNotExist()
        val logoBounds=compose.onNodeWithTag("series-title-logo").fetchSemanticsNode().boundsInRoot
        assertTrue("Logo stays inside the compact hero",logoBounds.left>=banner.left && logoBounds.right<=banner.right && logoBounds.bottom<=banner.bottom)
        val copy=compose.onNodeWithTag("home-banner-copy").fetchSemanticsNode().boundsInRoot
        assertTrue("Actions must stay below the landscape artwork",copy.top>=banner.bottom-1)
        val viewport=compose.onNodeWithTag("home-viewport").fetchSemanticsNode().boundsInRoot
        val controls=compose.onNodeWithTag("spotlight-controls").fetchSemanticsNode().boundsInRoot
        assertTrue("The compact spotlight must leave room for the catalogue",controls.bottom-viewport.top<viewport.height*.65f)
        assertTrue("The spotlight footer must clear bottom navigation",controls.bottom<=viewport.bottom+1 && copy.bottom<=controls.top+1)
        compose.onNodeWithText("Continue Watching").assertIsDisplayed()
        compose.onNodeWithTag("continue-watching").assertIsDisplayed()
        val play=compose.onNodeWithContentDescription("Play demo").fetchSemanticsNode().boundsInRoot
        val pixels=compose.onRoot().captureToImage().toPixelMap()
        val button=pixels[(play.left+play.width*.1f).toInt(),(play.top+play.height*.8f).toInt()]
        assertTrue("The compact primary action must retain its orange background: $button",button.red>.8f && button.green in 0.3f..0.65f && button.blue<.3f)
        capture("mobile-home.png")
        compose.onNodeWithContentDescription("Details for JUJUTSU KAISEN").performScrollTo().performClick()
        compose.waitUntil(10_000) {compose.onAllNodesWithTag("series-banner").fetchSemanticsNodes().isNotEmpty()}
        compose.waitForIdle()
        compose.onNodeWithTag("series-title-logo").assertDoesNotExist()
        compose.onNodeWithTag("series-heading").assertDoesNotExist()
        val detail=compose.onNodeWithTag("series-banner").fetchSemanticsNode().boundsInRoot
        assertTrue("Details must preserve the full poster",kotlin.math.abs(detail.width/detail.height-2f/3)<.02f)
        capture("mobile-details.png")
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) {compose.onAllNodesWithTag("series-banner").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("series-title-logo").assertDoesNotExist()
        compose.runOnUiThread {compose.activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE}
        compose.waitUntil(10_000) {compose.activity.resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE}
        compose.onNodeWithTag("series-title-logo").assertDoesNotExist()
        compose.onNodeWithText("JUJUTSU KAISEN").assertExists()
        compose.onNodeWithContentDescription("Back to catalogue").performClick()
        compose.waitUntil(10_000) {compose.onAllNodesWithTag("home-banner").fetchSemanticsNodes().isNotEmpty()}
        assertLogoLoaded()
    }

    private fun assertLogoLoaded() {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("series-title-logo").fetchSemanticsNodes().any {
                it.config.getOrElse(SemanticsProperties.StateDescription) {""}=="Title artwork loaded"
            }
        }
        compose.onNodeWithTag("series-title-logo").assertIsDisplayed()
        compose.onNode(hasContentDescription("JUJUTSU KAISEN") and hasAnyAncestor(hasTestTag("series-title-logo")),useUnmergedTree=true).assertIsDisplayed()
        val pixels=compose.onNodeWithTag("series-title-logo").captureToImage().toPixelMap()
        val color=pixels[pixels.width/2,pixels.height/2]
        assertTrue("Home renders the actual logo image, not a text substitute: $color",color.green>.9f && color.red<.1f && color.blue<.1f)
    }

    private fun capture(name: String) {
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        // Flush the final Compose draw before asking Android to capture the entire display.
        compose.onRoot().captureToImage()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.takeScreenshot().let {bitmap ->
            File(instrumentation.targetContext.getExternalFilesDir(null),name).outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
            }
            bitmap.recycle()
        }
    }
}
