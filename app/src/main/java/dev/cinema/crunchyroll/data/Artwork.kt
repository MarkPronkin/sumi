package dev.cinema.crunchyroll.data

/** Portrait detail headers prefer posters; Home and wide layouts prefer landscape hero artwork. */
fun Anime.imageUrls(poster: Boolean=false,hero: Boolean=false): List<String> = when {
    poster && hero -> listOf(posterUrl,heroUrl,artworkUrl)
    poster -> listOf(posterUrl,artworkUrl)
    hero -> listOf(heroUrl,artworkUrl,posterUrl)
    else -> listOf(artworkUrl,posterUrl)
}.filterNotNull().filter {it.isNotBlank()}.distinct()

internal fun spotlightNeighbours(shows: List<Anime>, selected: String, direction: Int): List<String> {
    val index = shows.indexOfFirst { it.id == selected }
    if (index < 0 || shows.size < 2) return emptyList()
    val step = if (direction < 0) -1 else 1
    return listOf(step, -step).map { shows[Math.floorMod(index + it, shows.size)].id }.distinct()
}

/** Alternate newest watched and saved shows so either collection can use the warming budget. */
internal fun libraryArtworkUrls(watched: List<Anime>, saved: List<Anime>): List<String> {
    val urls = linkedSetOf<String>()
    fun add(show: Anime?) {
        show?.posterUrl?.takeIf { it.isNotBlank() }?.let(urls::add)
        show?.logoUrl?.takeIf { it.isNotBlank() }?.let(urls::add)
    }
    for (i in 0 until maxOf(watched.size, saved.size)) {
        add(watched.getOrNull(i))
        add(saved.getOrNull(i))
    }
    return urls.toList()
}
