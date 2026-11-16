package dev.cinema.crunchyroll.data

import java.util.Locale

enum class RatingSource(val label: String,val maximum: Int,val decimals: Int) {
    Crunchyroll("Crunchyroll",5,1), MyAnimeList("MyAnimeList",10,2), Imdb("IMDb",10,1)
}

data class ShowRating(val score: Double?,val rank: Int?=null) {
    fun formatted(source: RatingSource): String? = score?.let {String.format(Locale.ROOT,"%.${source.decimals}f",it)}
}

sealed interface RatingState {
    data object Loading: RatingState
    data object Unavailable: RatingState
    data object Error: RatingState
    data class Available(val rating: ShowRating): RatingState
}

data class ShowRatings(val showId: String="",val values: Map<RatingSource,RatingState> = emptyMap()) {
    operator fun get(source: RatingSource): RatingState = values[source] ?: RatingState.Loading
}

/** Explicit demo fixtures, never presented as current service scores. */
fun demoRatings(showId: String): ShowRatings {
    val scores=when(showId) {
        "frieren" -> listOf(ShowRating(4.9),ShowRating(9.3,1),ShowRating(8.9))
        "solo" -> listOf(ShowRating(4.9),ShowRating(8.3,200),ShowRating(8.3))
        "dandadan" -> listOf(ShowRating(4.8),ShowRating(8.5,100),ShowRating(8.4))
        else -> return ShowRatings(showId,RatingSource.entries.associateWith {RatingState.Unavailable})
    }
    return ShowRatings(showId,RatingSource.entries.zip(scores.map {RatingState.Available(it)}).toMap())
}
