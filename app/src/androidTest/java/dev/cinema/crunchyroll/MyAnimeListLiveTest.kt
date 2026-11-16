package dev.cinema.crunchyroll

import androidx.test.platform.app.InstrumentationRegistry
import dev.cinema.crunchyroll.data.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import kotlin.system.measureTimeMillis

/** Explicit network check, excluded from the default instrumentation suite. No account is required. */
class MyAnimeListLiveTest {
    @Test fun currentProviderReturnsRealMyAnimeListScoresAndRanksOnAndroid() = runBlocking {
        val repo=RatingsRepository()
        val shows=listOf(
            Anime("live-frieren","Frieren: Beyond Journey’s End","","",2023,emptyList(),0),
            Anime("live-solo","Solo Leveling","","",2024,emptyList(),0),
            Anime("live-dandadan","DAN DA DAN","","",2024,emptyList(),0),
        )
        val results=shows.map {show ->
            var state: RatingState=RatingState.Loading
            val elapsed=measureTimeMillis {state=withTimeout(22_000) {repo.load(show,RatingSource.MyAnimeList)}}
            assertTrue("${show.title} must return a real MAL rating, received $state",state is RatingState.Available)
            val rating=(state as RatingState.Available).rating
            assertTrue("${show.title} score must be in the MAL scale",rating.score?.let {it>0 && it<=10}==true)
            assertTrue("${show.title} must include its overall MAL rank",(rating.rank ?: 0)>0)
            buildJsonObject {
                put("title",show.title);put("score",rating.score);put("rank",rating.rank);put("elapsed_ms",elapsed)
            }
        }
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null),"live-myanimelist.json").writeText(buildJsonObject {
            put("provider","https://api.tenrai.org/v1/")
            put("verified_at",java.time.Instant.now().toString())
            put("results",JsonArray(results))
        }.toString())
    }
}
