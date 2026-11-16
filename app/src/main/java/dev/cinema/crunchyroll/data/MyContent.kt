package dev.cinema.crunchyroll.data

enum class ContentView(val label: String) {
    WatchedShows("Watched shows"), Watchlist("Watchlist"), History("History");

    companion object {
        fun fromKey(key: String?) = entries.firstOrNull {it.name==key} ?: WatchedShows
        fun fromLegacyRoute(route: String): ContentView? = when(route) {
            "My List", "Watchlist" -> Watchlist
            "History" -> History
            else -> null
        }
    }
}

/** One row per played episode, newest checkpoint first. Equal/absent dates keep provider order. */
fun reconcileHistory(entries: Iterable<HistoryEntry>): List<HistoryEntry> {
    val episodes=linkedMapOf<String,HistoryEntry>()
    entries.forEach {entry ->
        val previous=episodes[entry.episode.id]
        if(previous==null || entry.progress.updatedAt>previous.progress.updatedAt) episodes[entry.episode.id]=entry
    }
    return episodes.values.sortedByDescending {it.progress.updatedAt}
}

/** Retain loaded pages during refresh; a confirmed terminal page removes the old trailing window. */
data class HistoryPages(private val pages: Map<Int,HistoryPage> = emptyMap()) {
    val lastPage: Int get()=pages.keys.maxOrNull() ?: 0
    val hasMore: Boolean get()=pages[lastPage]?.hasMore ?: false
    val items: List<HistoryEntry> get()=reconcileHistory(pages.toSortedMap().values.flatMap {it.items})

    fun withPage(number: Int,page: HistoryPage): HistoryPages {
        require(number>0)
        val retained=if(page.hasMore) pages else pages.filterKeys {it<=number}
        return HistoryPages(retained+(number to page))
    }
}

data class WatchedShow(val show: Anime,val latest: HistoryEntry,val playedEpisodes: Int,val resume: HistoryEntry?)

/** Group by series identity, including completed episodes and series absent from the home catalogue. */
fun watchedShows(history: List<HistoryEntry>): List<WatchedShow> = reconcileHistory(history)
    .groupBy {it.show.id}.values.map {episodes ->
        val latest=episodes.first()
        WatchedShow(latest.show,latest,episodes.size,episodes.firstOrNull {it.progress.positionMs>0 && !it.progress.complete})
    }
