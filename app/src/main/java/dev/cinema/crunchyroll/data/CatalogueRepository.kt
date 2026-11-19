package dev.cinema.crunchyroll.data

import dev.cinema.crunchyroll.R
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow

interface CatalogueRepository {
    val shows: List<Anime>
    suspend fun search(query: String, category: String): List<Anime>
    fun episodes(showId: String, season: Int): List<Episode>
    fun show(id: String): Anime? = shows.find { it.id == id }
    fun episode(id: String): Episode? = shows.asSequence().flatMap { show ->
        (1..show.seasons).asSequence().flatMap { episodes(show.id, it).asSequence() }
    }.find { it.id == id }
    fun next(episode: Episode): Episode? = episodes(episode.showId, episode.season)
        .firstOrNull { it.number == episode.number + 1 }
        ?: if (episode.season < (show(episode.showId)?.seasons ?: 0)) episodes(episode.showId, episode.season + 1).firstOrNull() else null
}

fun mergedHistory(remote: List<HistoryEntry>,local: Map<String,Progress>,catalogue: CatalogueRepository): List<HistoryEntry> {
    val result=reconcileHistory(remote).associateBy {it.episode.id}.toMutableMap()
    local.forEach {(id,progress) ->
        val old=result[id]
        val current = resumeProgress(progress, old?.progress)
        if (current == null) result.remove(id)
        else if (old != null) result[id] = old.copy(progress = current)
        else if (current.complete || current.positionMs > 0) catalogue.episode(id)?.let {episode ->catalogue.show(episode.showId)?.let {show ->result[id]=HistoryEntry(show,episode,current)}}
    }
    return result.values.sortedByDescending {it.progress.updatedAt}
}

class DemoCatalogue : CatalogueRepository {
    override val shows = listOf(
        Anime("frieren", "FRIEREN", "Beyond Journey’s End", "The adventure may be over, but life goes on. An elven mage discovers what it means to connect with the people whose lives pass in the blink of an eye.", 2023, listOf("Fantasy", "Adventure", "Drama"), R.drawable.frieren, R.drawable.frieren_poster, 2),
        Anime("solo", "SOLO LEVELING", "Arise. A new adventure awaits.", "In a world of hunters and monsters, the weakest hunter gets a second chance. Follow a journey from the lowest rank to unimaginable power.", 2024, listOf("Action", "Fantasy"), R.drawable.solo, seasons = 2),
        Anime("dandadan", "DAN DA DAN", "A little supernatural. A lot unexpected.", "A ghost believer and an alien enthusiast make a bet that sends them into a world of paranormal encounters, unexpected friendships, and chaos.", 2024, listOf("Action", "Comedy", "Supernatural"), R.drawable.dandadan, seasons = 2),
    )
    override suspend fun search(query: String, category: String): List<Anime> {
        delay(160)
        return shows.filter {
            (query.isBlank() || "${it.title} ${it.subtitle} ${it.genres.joinToString()}".contains(query.trim(), ignoreCase = true)) &&
                (category == "All" || category in it.genres)
        }
    }
    override fun episodes(showId: String, season: Int): List<Episode> {
        val titles = when (showId) {
            "frieren" -> listOf("The Journey Begins", "A Quiet Encounter", "Beyond the Horizon", "A New Companion")
            "solo" -> listOf("A New Beginning", "The Challenge", "A Hidden Power", "Level Up")
            else -> listOf("An Unexpected Meeting", "Something Strange", "Unlikely Allies", "Into the Unknown")
        }
        return titles.mapIndexed { index, title -> Episode("$showId-s$season-e${index + 1}", showId, season, index + 1, "Demo chapter ${index + 1} · $title") }
    }
}

/** Cancels both the debounce and the in-flight repository request when newer input arrives. */
@OptIn(ExperimentalCoroutinesApi::class)
fun searchResults(requests: Flow<SearchRequest>, repository: CatalogueRepository): Flow<LoadState<List<Anime>>> =
    requests.flatMapLatest { request ->
        flow<LoadState<List<Anime>>> {
            emit(LoadState.Loading)
            if (request.query.isNotEmpty()) delay(280)
            emit(LoadState.Content(repository.search(request.query, request.category)))
        }.catch { emit(LoadState.Error(if(it is dev.cinema.crunchyroll.data.account.ServiceException) it.reason else "Couldn’t load results. Try again.")) }
    }
