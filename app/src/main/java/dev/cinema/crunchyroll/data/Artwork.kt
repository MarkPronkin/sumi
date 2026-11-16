package dev.cinema.crunchyroll.data

/** Portrait detail headers prefer posters; Home and wide layouts prefer landscape hero artwork. */
fun Anime.imageUrls(poster: Boolean=false,hero: Boolean=false): List<String> = when {
    poster && hero -> listOf(posterUrl,heroUrl,artworkUrl)
    poster -> listOf(posterUrl,artworkUrl)
    hero -> listOf(heroUrl,artworkUrl,posterUrl)
    else -> listOf(artworkUrl,posterUrl)
}.filterNotNull().filter {it.isNotBlank()}.distinct()
